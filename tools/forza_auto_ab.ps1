# Runtime A/B of one debug property on the POCO F7 with no manual steps and no
# fixed waits on the PC: pushes tools/fh_auto.sh, which runs on the phone as a
# state machine over the emulator log (title -> Start -> single player ->
# in game -> car parked -> arms), waits for it with one adb call, pulls xe.log
# and cuts the arms out by the emulator's own property-switch log lines.
#
# Example (6 arms of 40 s, relaunching the game first):
#   .\forza_auto_ab.ps1 -Launch 1 -Property debug.xendroid.spin_park `
#     -Values "1 2 1 2 1 2" -Marker "spin_park_mode = (\d+)" -Name park2 `
#     -Config '[CPU]\nspin_park_guest_functions = \042829F04A8\042'
# -Launch 2 attaches to a game that is already loading; 0 starts the arms now.
# -Config adds per-game config lines on relaunch, '\n' separated (printf
#   escapes; \042 is a double quote).
# -Passes also logs per-render-pass and per-resolve GPU times (VkPassTime,
#   VkResolveTime); the timestamps serialize passes, so compare relatively.
# The per-arm thread CPU usage (top -H at the end of each arm) is summarized
# for the busiest guest threads and the GPU command processor thread.
param(
  [int]$Launch = 1,
  [Parameter(Mandatory = $true)][string]$Property,
  [string]$Values = "0 1 0 1 0 1",
  [int]$ArmSeconds = 40,
  [string]$Config = "",
  [Parameter(Mandatory = $true)][string]$Marker,
  [int]$Skip = 8,
  [Parameter(Mandatory = $true)][string]$Name,
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
& $Adb -s $Serial shell "rm -f /data/local/tmp/fh_auto.status; ${envPrefix}nohup sh /data/local/tmp/fh_auto.sh $Launch $Property '$Values' $ArmSeconds '$Config' > /data/local/tmp/fh_auto.out 2>&1 &"
# One long-lived adb call; the phone does the polling.
& $Adb -s $Serial shell "while ! grep -q FH_AUTO_DONE /data/local/tmp/fh_auto.status 2>/dev/null; do sleep 2; done; cat /data/local/tmp/fh_auto.status" |
  Tee-Object -FilePath (Join-Path $out "driver-status.txt")
& $Adb -s $Serial pull /sdcard/Android/data/xendroid.compose.fork.opt/files/compose/xe.log (Join-Path $out "xe.log") | Out-Null
$tops = & $Adb -s $Serial shell "for f in /data/local/tmp/fh_arm_*_top.txt; do echo == `$f; cat `$f; done"
$tops | Out-File -Encoding utf8 (Join-Path $out "top.txt")
$arm = ""
foreach ($l in $tops) {
  if ($l -match 'fh_arm_(\d+)_top') { $arm = $Matches[1]; continue }
  if ($l -match '^\s*\d+\s+([\d.]+)\s+(Guest CPU \d|GPU Commands.*)\s*$') {
    "arm $arm  $($Matches[2].PadRight(18)) $($Matches[1])%"
  }
}
& $Python (Join-Path $PSScriptRoot "forza_segstats.py") (Join-Path $out "xe.log") $Marker $Skip (Join-Path $out "arms.json")
