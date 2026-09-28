# Interleaved on-device A/B of the runtime switches, same scene, no restart.
# Each arm is two digits: <resolve_clear_in_guest_pass><spirv_specialize_no_alpha>.
# Example: .\forza_ab_run.ps1 -Arms "00 11 00 11" -Duration 45 -Warmup 20 -Prefix r1
# Start/end thermal dumps per arm are saved by benchmark_forza.py; summarize the
# arms with tools/forza_ab_report.py <prefix>.
param(
  [string]$Arms = "00 11 00 11",
  [int]$Duration = 45,
  [int]$Warmup = 20,
  [string]$Prefix = "ab"
)
$ErrorActionPreference = "Continue"
$ArmList = @($Arms -split '[\s,]+' | Where-Object { $_ })
foreach ($a in $ArmList) {
  if ($a -notmatch '^[01]{2}$') { throw "Invalid arm '$a' (expected two digits 0/1)" }
}
$adb = "C:\Users\Administrator\Downloads\scrcpy-win64-v4.1\adb.exe"
$py = "C:\Users\Administrator\AppData\Local\Python\bin\python.exe"
$serial = "e11d1729"
$pkg = "xendroid.compose.fork.opt"
$project = "C:\Users\Administrator\Desktop\Xendroid-Fork-main"
$i = 0
foreach ($arm in $ArmList) {
  $i++
  $rc = $arm.Substring(0, 1); $na = $arm.Substring(1, 1)
  & $adb -s $serial shell setprop debug.xendroid.resolve_clear_in_guest_pass $rc
  & $adb -s $serial shell setprop debug.xendroid.spirv_specialize_no_alpha $na
  Write-Output "[$(Get-Date -Format HH:mm:ss)] arm $i = $arm (resolve_clear_in_guest_pass=$rc spirv_specialize_no_alpha=$na), warmup ${Warmup}s"
  Start-Sleep -Seconds $Warmup
  $label = "$Prefix-$i-rc$rc-na$na"
  & $py "$project\tools\benchmark_forza.py" --adb $adb --serial $serial --package $pkg --duration $Duration --label $label | Select-String -Pattern '"presented_fps"|"presented_frame_ms_median"|"presented_frame_ms_p95"|guest_fps'
  # Per-thread CPU of the emulator process while this arm is still active.
  $emuPid = (& $adb -s $serial shell "pidof $pkg`:emu").Trim()
  if ($emuPid) {
    & $adb -s $serial shell "top -H -b -n 2 -d 2 -p $emuPid -o TID,%CPU,CPU,NAME -s 2 | head -40" | Out-File -Encoding utf8 "$project\performance-tests\$label\top-threads.txt"
  }
}
Write-Output "AB_DONE"
