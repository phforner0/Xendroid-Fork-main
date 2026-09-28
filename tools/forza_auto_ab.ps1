# A/B runs of Forza Horizon on the POCO F7 with no manual steps and no fixed
# waits on the PC: pushes tools/fh_auto.sh, which runs on the phone as a state
# machine over the emulator log (title -> Start -> single player -> in game ->
# car parked -> arms), waits for it with one adb call and pulls xe.log.
#
# Runtime A/B of one debug property (arms in one game session, cut out of
# xe.log by the emulator's own property-switch log lines):
#   .\forza_auto_ab.ps1 -Launch 1 -Property debug.xendroid.spin_park `
#     -Values "1 2 1 2 1 2" -Marker "spin_park_mode = (\d+)" -Name park2 `
#     -Config '[CPU]\nspin_park_guest_functions = \042829F04A8\042'
# -Launch 2 attaches to a game that is already loading; 0 starts the arms now.
#
# Restart A/B for options read at startup (one launch per arm, "label=config"
# per arm, the config being per-game config lines):
#   .\forza_auto_ab.ps1 -Name signs1 -ArmSeconds 40 -Passes -RestartArms @(
#     'base=', 'branch=spirv_texture_sign_branch = true',
#     'base=', 'branch=spirv_texture_sign_branch = true')
# Lines without a [section] header land in [GPU]. The arms are compared with
# tools/forza_passres.py over the last ArmSeconds - 5 seconds of each launch.
#
# -Config adds per-game config lines on relaunch, '\n' separated (printf
#   escapes; \042 is a double quote). With -RestartArms it is appended after
#   every arm's lines.
# -Passes also logs per-render-pass, per-resolve and other GPU work times
#   (VkPassTime, VkResolveTime, VkMiscTime); the timestamps serialize passes,
#   so compare relatively.
# The per-arm thread CPU usage (top -H at the end of each arm) is summarized
# for the guest threads and the GPU command processor thread.
param(
  [int]$Launch = 1,
  [string]$Property = "",
  [string]$Values = "0 1 0 1 0 1",
  [int]$ArmSeconds = 40,
  [string]$Config = "",
  [string]$Marker = "",
  [int]$Skip = 8,
  [Parameter(Mandatory = $true)][string]$Name,
  [string[]]$RestartArms = @(),
  [switch]$Passes,
  [string]$Adb = "C:\Users\Administrator\Downloads\scrcpy-win64-v4.1\adb.exe",
  [string]$Serial = "e11d1729",
  [string]$Python = "C:\Users\Administrator\AppData\Local\Python\bin\python.exe"
)
$out = Join-Path (Split-Path $PSScriptRoot -Parent) "performance-tests\$Name"
New-Item -ItemType Directory -Force $out | Out-Null
$lf = Join-Path $out "fh_auto.sh"
(Get-Content (Join-Path $PSScriptRoot "fh_auto.sh") -Raw).Replace("`r`n", "`n") |
  Set-Content -NoNewline -Encoding ascii $lf
& $Adb -s $Serial push $lf /data/local/tmp/fh_auto.sh | Out-Null
$envPrefix = if ($Passes) { "PASSES=true " } else { "" }
$xeLog = "/sdcard/Android/data/xendroid.compose.fork.opt/files/compose/xe.log"

# Runs fh_auto.sh on the phone and waits for it; returns the status lines.
function Invoke-Driver([int]$launch, [string]$property, [string]$values,
                       [string]$config) {
  & $Adb -s $Serial shell "rm -f /data/local/tmp/fh_auto.status; ${envPrefix}nohup sh /data/local/tmp/fh_auto.sh $launch $property '$values' $ArmSeconds '$config' > /data/local/tmp/fh_auto.out 2>&1 &"
  # One long-lived adb call; the phone does the polling.
  & $Adb -s $Serial shell "while ! grep -q FH_AUTO_DONE /data/local/tmp/fh_auto.status 2>/dev/null; do sleep 2; done; cat /data/local/tmp/fh_auto.status"
}

function Show-Top([string]$file) {
  $tops = & $Adb -s $Serial shell "for f in /data/local/tmp/fh_arm_*_top.txt; do echo == `$f; cat `$f; done"
  $tops | Out-File -Encoding utf8 $file
  $arm = ""
  foreach ($l in $tops) {
    if ($l -match 'fh_arm_(\d+)_top') { $arm = $Matches[1]; continue }
    if ($l -match '^\s*\d+\s+([\d.]+)\s+(Guest CPU \d|GPU Commands.*)\s*$') {
      "arm $arm  $($Matches[2].PadRight(18)) $($Matches[1])%"
    }
  }
}

if ($RestartArms.Count) {
  $logs = @()
  $i = 0
  foreach ($arm in $RestartArms) {
    $i++
    $label, $armConfig = $arm -split '=', 2
    # The arm's lines first, so header-less ones stay in [GPU].
    $cfg = if ($Config -and $armConfig) { "$armConfig\n$Config" } else { "$armConfig$Config" }
    "=== arm $i ($label) ==="
    Invoke-Driver 1 debug.xendroid.arm "$i" $cfg |
      Tee-Object -FilePath (Join-Path $out "driver-status-arm$i.txt") |
      Where-Object { $_ -match 'title screen GPU|WARNING|scene stable|FAIL' }
    $armLog = Join-Path $out "arm$i-$label-xe.log"
    & $Adb -s $Serial pull $xeLog $armLog | Out-Null
    Show-Top (Join-Path $out "top-arm$i.txt")
    $logs += "$label=$armLog"
  }
  & $Python (Join-Path $PSScriptRoot "forza_passres.py") --last ([Math]::Max(5, $ArmSeconds - 5)) $logs
  return
}

if (-not $Property -or -not $Marker) {
  throw "-Property and -Marker are required without -RestartArms"
}
Invoke-Driver $Launch $Property $Values $Config |
  Tee-Object -FilePath (Join-Path $out "driver-status.txt")
& $Adb -s $Serial pull $xeLog (Join-Path $out "xe.log") | Out-Null
Show-Top (Join-Path $out "top.txt")
& $Python (Join-Path $PSScriptRoot "forza_segstats.py") (Join-Path $out "xe.log") $Marker $Skip (Join-Path $out "arms.json")
