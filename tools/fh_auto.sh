#!/system/bin/sh
# Forza Horizon benchmark driver that runs ON the phone (one adb call starts
# it; nothing waits on the PC). A state machine driven by the emulator's own
# log: boot -> title (pressstart.wmv) -> Start accepted (profileschema /
# forza_tone.wmv) -> in game (draws/frame) -> car parked at the reference spot
# (draws/frame stable) -> runtime A/B of one debug property. The emulator logs
# every property switch, so the arms are cut out of xe.log afterwards.
#
# Usage: fh_auto.sh <launch 0|1|2> <property> <values> <arm seconds> [config]
#   launch 1 relaunches the game with the per-game config lines in [config]
#   ('\n' separated); 2 only waits for the running game to reach the parked
#   scene; 0 starts the arms right away.
#   values: space separated, e.g. "0 1 0 1 0 1".
#   Property "sustain": values = minutes, arm = sample period (thermal runs).
#   Env COOL=<deg C>: with launch 1, first wait until the GPU is below that.
#     Caution: on the POCO F7 a launch after minutes with the game closed
#     (and the screen timing out) once ran with the GPU ~2.5x slower from the
#     title screen on - check the VkFrameSync GPU times before trusting it.
#   Env PASSES=true: with launch 1, also log per-render-pass and per-resolve
#     GPU times (the timestamps serialize the passes, absolute times grow).
PKG=xendroid.compose.fork.opt
DIR=/sdcard/Android/data/$PKG/files/compose
LOG=$DIR/xe.log
OUT=/data/local/tmp/fh_auto.status
LAUNCH=$1; PROP=$2; VALUES=$3; ARM=$4; EXTRA=$5

say() { echo "[$(date +%T)] $*" | tee -a $OUT; }
fail() { say "FAIL: $*"; say "FH_AUTO_DONE"; exit 1; }
has() { grep -qE "$1" $LOG 2>/dev/null; }
# Waits until the log matches $1, up to $2 seconds, polling every 0.5 s.
wait_log() {
  t=0
  while ! has "$1"; do
    sleep 0.5; t=$((t + 1))
    [ $t -ge $(($2 * 2)) ] && return 1
  done
  return 0
}
last_draws() {
  grep 'GpuFrame' $LOG 2>/dev/null | tail -n $1 | sed -n 's/.*draws=\([0-9]*\).*/\1/p'
}
# Last $1 per-second reports all within $2 per mille of their mean and above
# $3 draws. Parked: ~2920-2960 draws with +-0.3% jitter; driving: 3000-3650.
stable() {
  c=0; min=999999; max=0; sum=0
  for v in $(last_draws $1); do
    c=$((c + 1)); sum=$((sum + v))
    [ $v -lt $min ] && min=$v
    [ $v -gt $max ] && max=$v
  done
  [ $c -lt $1 ] && return 1
  mean=$((sum / c))
  [ $mean -lt $3 ] && return 1
  [ $(((max - min) * 1000)) -le $(($2 * mean)) ]
}
gpu_temp() {
  dumpsys thermalservice | grep -A 40 'Current temperatures from HAL' |
    sed -n 's/.*mValue=\([0-9.]*\), mType=[0-9]*, mName=GPU0,.*/\1/p' | head -1
}
gpu_cooling() {
  dumpsys thermalservice | grep -A 60 'Current cooling devices from HAL' |
    sed -n 's/.*mValue=\([0-9]*\), mType=[0-9]*, mName=gpu}.*/\1/p' | head -1
}
# KGSL power state ("?" where sysfs is not readable): current and maximum
# clock (Hz), the power level limits (0 = fastest) and force_clk_on, which
# reads 1 while the KGSL power control is forced on (the adrenotools turbo of
# adrenotools_force_max_clocks). Also logged with the app stopped before a
# launch, to see whether a forced state outlives the app.
gpu_clock() {
  s=""
  for f in gpuclk max_gpuclk thermal_pwrlevel max_pwrlevel min_pwrlevel \
           force_clk_on devfreq/governor; do
    # '?' quoted: unquoted, the shell globs it (Android's root has /d).
    s="$s ${f#devfreq/}=$(cat /sys/class/kgsl/kgsl-3d0/$f 2>/dev/null || echo '?')"
  done
  echo "${s# }"
}

: > $OUT
say "start launch=$LAUNCH prop=$PROP values='$VALUES' arm=${ARM}s"

# COOL=<deg C>: before launching, wait (game closed) until the GPU cools down,
# so sustained runs start from the same thermal state.
if [ -n "$COOL" ] && [ "$LAUNCH" = 1 ]; then
  am force-stop $PKG
  while :; do
    tp=$(gpu_temp)
    say "cooling down: GPU0 ${tp} C (target $COOL)"
    [ -n "$tp" ] && [ ${tp%.*} -lt $COOL ] && break
    sleep 30
  done
fi

if [ "$LAUNCH" = 1 ]; then
  # debug.xendroid.* overrides last until reboot and win over the per-game
  # config 30 frames into the game - clear any left by earlier runs.
  for p in $(getprop | sed -n 's/^\[\(debug\.xendroid\.[^]]*\)\]: \[..*\]$/\1/p'); do
    say "clearing stale $p=$(getprop $p)"
    setprop $p ""
  done
  am force-stop $PKG
  sleep 1
  say "GPU power with the app stopped: $(gpu_clock)"
  rm -f $LOG
  # Env PASSES=true: per-render-pass GPU timestamps (VkPassTime lines; they
  # serialize the passes, so absolute times grow).
  printf "[GPU]\nlog_gpu_frame_time_breakdown = true\nlog_gpu_frame_time_breakdown_passes = ${PASSES:-false}\n" > $DIR/config/4D5309C9.config.toml
  [ -n "$EXTRA" ] && printf "$EXTRA\n" >> $DIR/config/4D5309C9.config.toml
  input keyevent KEYCODE_WAKEUP
  am start -n $PKG/xendroid.compose.EmulatorHostActivity -a xendroid.intent.action.xendroid \
    --es game_uri '/storage/emulated/0/Download/Xbox 360/Games/Forza Horizon.iso' > /dev/null
  say "launched"
  wait_log 'pressstart\.wmv' 150 || fail "title screen not reached"
  say "title screen"
  sleep 3
  # Power state check: the title screen is a fixed, light GPU load (~2.5-4.5
  # ms per frame cool to warm on the POCO F7). A launch once ran the GPU ~2.5x
  # slower from here on (8.8 ms) with no thermal throttling reported.
  tg=$(grep 'VkFrameSync' $LOG | tail -1 | sed -n 's/.*gpu exec avg=\([0-9.]*\)ms.*/\1/p')
  say "title screen GPU ${tg:-?} ms/frame, $(gpu_clock)"
  if [ -n "$tg" ] && awk "BEGIN { exit !($tg > 6.5) }"; then
    say "WARNING: GPU slow at the title screen - power state suspect, results not comparable"
  fi
  n=0
  while ! has 'profileschema|forza_tone\.wmv'; do
    n=$((n + 1)); [ $n -gt 8 ] && fail "Start not accepted"
    input tap 1386 640; sleep 1.5
    input swipe 1764 1149 1764 1149 300
    wait_log 'profileschema|forza_tone\.wmv' 6 && break
    say "Start retry $n"
  done
  say "Start accepted"
  # Start leads to the SINGLE PLAYER / MULTIPLAYER menu ("A SELECT", single
  # player highlighted); A there starts the forza_tone.wmv loading video.
  n=0
  while ! has 'forza_tone\.wmv'; do
    n=$((n + 1)); [ $n -gt 8 ] && fail "single player not selected"
    sleep 2
    has 'forza_tone\.wmv' && break
    input tap 1386 640; sleep 1.5
    input swipe 2451 708 2451 708 300
    wait_log 'forza_tone\.wmv' 6 && break
    say "A retry $n"
  done
  say "single player selected"
fi

# launch 2: the game is already booting/loading - only wait for the scene.
if [ "$LAUNCH" -ge 1 ]; then
  t=0
  while ! stable 2 1000 2500; do
    sleep 1; t=$((t + 1)); [ $t -gt 240 ] && fail "world not loaded"
  done
  say "in game"
  # The intro drive ends with the car parked; require stability and a floor.
  t=0
  while [ $t -lt 60 ] || ! stable 6 15 2600; do
    sleep 1; t=$((t + 1)); [ $t -gt 300 ] && fail "scene never settled"
  done
  say "scene stable after ${t}s: $(last_draws 1) draws/frame"
fi

# Sustained mode: <property> "sustain", <values> = minutes, <arm> = sample
# period in seconds. One line per sample: guest fps over the last report,
# GPU temperature and the GPU cooling device level (thermal throttling).
if [ "$PROP" = sustain ]; then
  end=$(($(date +%s) + VALUES * 60))
  while [ $(date +%s) -lt $end ]; do
    sleep $ARM
    line=$(grep 'GpuFrame' $LOG | tail -1)
    iv=$(echo "$line" | sed -n 's/.*interval avg=\([0-9.]*\)ms.*/\1/p')
    say "sample interval_ms=$iv gpu_temp=$(gpu_temp) gpu_cooling=$(gpu_cooling) $(gpu_clock)"
  done
  say "FH_AUTO_DONE"
  exit 0
fi

i=0
EMU=$(pidof $PKG:emu)
rm -f /data/local/tmp/fh_arm_*_top.txt /data/local/tmp/fh_ab_*.png
for v in $VALUES; do
  i=$((i + 1))
  setprop $PROP $v
  say "arm $i $PROP=$v"
  # Battery charge used over the arm (the sysfs current is not readable by the
  # shell; the counter moves in ~1 mAh steps). Only meaningful on battery: on
  # external power the charger's varying input and the gauge's update bursts
  # dominate it (0 to 3100 mA between identical 36 s arms on USB).
  c0=$(dumpsys battery | sed -n 's/.*Charge counter: \([0-9]*\).*/\1/p')
  t0=$(date +%s)
  sleep $((ARM - 4))
  c1=$(dumpsys battery | sed -n 's/.*Charge counter: \([0-9]*\).*/\1/p')
  t1=$(date +%s)
  if [ -n "$c0" ] && [ -n "$c1" ] && [ $t1 -gt $t0 ]; then
    note=""
    [ "$(dumpsys battery | grep -cE '(AC|USB|Wireless|Dock) powered: true')" -gt 0 ] &&
      note=" (on external power: not the load)"
    say "arm $i power: charge used $(((c0 - c1) / 1000)) mAh in $((t1 - t0)) s = $(((c0 - c1) * 36 / 10 / (t1 - t0))) mA net$note"
  fi
  # Per-thread CPU over the arm's last seconds (guest busy-wait, GPU Commands).
  [ -n "$EMU" ] && top -H -b -n 1 -d 3 -p $EMU -o TID,%CPU,CMD -s 2 2>/dev/null |
    head -16 > /data/local/tmp/fh_arm_${i}_top.txt
  # The arm's image, after its measured window (for visual A/Bs of switches).
  screencap -p /data/local/tmp/fh_ab_${i}_${v}.png 2>/dev/null
  say "arm $i end: $(gpu_clock)"
done
# Don't leave the override set for later launches (the running game keeps the
# last arm's value).
setprop $PROP ""
say "FH_AUTO_DONE"
