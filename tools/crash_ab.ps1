# Crash A/B: launches a game N times per arm (the per-game config lines of the
# arm, '\n' separated, after the frame log's [GPU] lines), no input, and
# records per launch whether the guest crashed within -Seconds and at which
# PC (the guest crash report in xe.log). Keeps and restores the game's config.
# Before each launch it waits until nobody has touched the phone since the
# last input it injected (at least 120 s for the first), and a launch the app
# was paused in (the phone used meanwhile) is reported as such.
param([Parameter(Mandatory = $true)][string]$Name,
      [Parameter(Mandatory = $true)][string]$Game,
      [Parameter(Mandatory = $true)][string]$TitleId,
      [Parameter(Mandatory = $true)][string[]]$Arms,
      [int]$Runs = 3, [int]$Seconds = 120)
$ErrorActionPreference = "Continue"
Set-Location C:\Users\Administrator\Desktop\xendroid-texload
$adb = "C:\Users\Administrator\Downloads\scrcpy-win64-v4.1\adb.exe"
$serial = $env:XENDROID_ADB_SERIAL
$pkg = "xendroid.compose.fork.opt"
$dir = "/sdcard/Android/data/$pkg/files/compose"
$cfg = "$dir/config/$TitleId.config.toml"
$out = "performance-tests\$Name"
New-Item -ItemType Directory -Force $out | Out-Null
function Sh([string]$cmd) { & $adb -s $serial shell $cmd }
# Milliseconds since the phone's last user activity (injected input counts).
function SinceActivity() {
  $line = Sh "dumpsys power | grep -E 'lastUserActivityTime=' | head -1"
  if ($line -match '\((\d+) ms ago\)') { [int64]$Matches[1] } else { [int64]-1 }
}
# Waits until the last activity is no newer than our own last input.
$lastInput = $null
function WaitIdle() {
  do {
    & $adb connect $serial | Out-Null
    $ago = SinceActivity
    $need = if ($lastInput) { [int64](((Get-Date) - $lastInput).TotalMilliseconds) - 5000 } else { 120000 }
    $need = [math]::Min($need, 120000)
    if ($ago -ge $need) { return }
    "phone in use ($ago ms since its last activity), waiting"
    Start-Sleep -Seconds 30
  } while ($true)
}
& $adb connect $serial | Out-Null
Sh "[ -f $cfg ] && cp $cfg $cfg.crbak"
foreach ($arm in $Arms) {
  $label, $extra = $arm -split '=', 2
  for ($r = 1; $r -le $Runs; $r++) {
    Sh "am force-stop $pkg"
    Start-Sleep -Seconds 2
    Sh "rm -f $dir/xe.log"
    $text = '[GPU]\nlog_gpu_frame_time_breakdown = true\nlog_gpu_frame_time_breakdown_passes = false'
    if ($extra) { $text += "\n$extra" }
    Sh "printf '$text\n' > $cfg"
    WaitIdle
    Sh "input keyevent KEYCODE_WAKEUP"
    $t0 = Get-Date
    $lastInput = $t0
    Sh "am start -n $pkg/xendroid.compose.EmulatorHostActivity -a xendroid.intent.action.xendroid --es game_uri '/storage/emulated/0/Download/Xbox 360/Games/$Game.iso' > /dev/null"
    $alive = $true
    do {
      Start-Sleep -Seconds 5
      & $adb connect $serial | Out-Null
      $alive = (Sh "pidof ${pkg}:emu") -ne $null
    } until (-not $alive -or ((Get-Date) - $t0).TotalSeconds -gt $Seconds)
    $log = "$out\$label-run$r-xe.log"
    & $adb -s $serial pull "$dir/xe.log" $log 2>&1 | Out-Null
    $crash = Select-String -Path $log -Pattern 'Guest crashed at PC (0x[0-9A-F]+)' -ErrorAction SilentlyContinue | Select-Object -First 1
    $frames = (Select-String -Path $log -Pattern 'GpuFrame' -ErrorAction SilentlyContinue | Measure-Object).Count
    $pc = if ($crash) { $crash.Matches[0].Groups[1].Value } else { "-" }
    $paused = (Select-String -Path $log -Pattern 'EMULATOR PAUSED' -ErrorAction SilentlyContinue | Measure-Object).Count
    $note = if ($paused) { " (INVALID: app paused $paused times, the phone was used)" } else { "" }
    "$label run ${r}: alive $alive after $([int]((Get-Date) - $t0).TotalSeconds) s, crash PC $pc, $frames frame reports$note"
    Sh "am force-stop $pkg"
  }
}
Sh "if [ -f $cfg.crbak ]; then mv $cfg.crbak $cfg; else rm -f $cfg; fi"
"CRASH_AB_DONE"
