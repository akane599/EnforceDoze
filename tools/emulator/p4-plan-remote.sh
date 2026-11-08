#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Runs on akane-linux; started by tools/emulator/p4-plan.sh. Arguments: AVD, scenario list. The engine is
# driven through the debug build's DebugCommandReceiver; every check reads the app's reply and the device
# itself. Every change to the emulator is undone at the end; the AVD is never wiped, and it is shut down only
# if this script started it (AGENTS.md).
set -uo pipefail

avd=$1 scenarios=$2
dir=$HOME/.cache/enforcedoze-p4
cache=$HOME/.cache/enforcedoze-shizuku
out=$dir/out
mkdir -p "$out"
A=$HOME/Android/Sdk/platform-tools/adb
adb() { "$A" "$@" </dev/null; }

pkg=io.github.akane599.enforcedoze
receiver=$pkg/io.github.akane599.enforcedoze.app.DebugCommandReceiver
starter_activity=$pkg/io.github.akane599.enforcedoze.app.DebugStartActivity
shizuku_pkg=moe.shizuku.privileged.api
shizuku_apk=$cache/shizuku-v13.7.0-thedjchi.apk
shizuku_marker=$cache/shizuku-installed-by-p4
alarm_tag='enforcedoze:night'

log() { echo "[$(date +%T)] $*" | tee -a "$out/summary.txt"; }
failures=0
check() { # <description> <command...>: one PASS or FAIL line in summary.txt
  local what=$1; shift
  if "$@"; then log "PASS $what"; else log "FAIL $what"; failures=$((failures + 1)); fi
}

cmd() { # the reply of one debug command (the broadcast's result data)
  adb shell am broadcast -n "$receiver" --es cmd "$1" ${2:+--es arg "$2"} |
    sed -n '/^Broadcast completed: result=0, data="/,$p' | sed '1s/^Broadcast completed: result=0, data="//; $s/"$//'
}
status_has() { cmd status | grep -qE "$1"; }
until_true() { # <seconds> <command...>: polls once a second
  local limit=$1; shift
  for _ in $(seq 1 "$limit"); do "$@" && return 0; sleep 1; done
  return 1
}
lever_is() { status_has "^lever $1 $2 "; }
slot_is() { status_has "^plan .* slot=$1 "; }
locked_is() { status_has "^started=.* locked=$1$"; } # the keyguard as the engine last heard it
profile_is() { status_has " profile=$1 "; }
constants() { adb shell settings get global device_idle_constants | tr -d '\r'; }
constants_are() { [[ $(constants) == "$1" ]]; }
sensor_mode() { adb shell dumpsys sensorservice | grep -m1 -q "^Mode : $1"; }
device_forced() { adb shell dumpsys deviceidle | grep -q "mForceIdle=$1"; }
screen_off() { adb shell input keyevent KEYCODE_SLEEP; }
wake_locked() { adb shell input keyevent KEYCODE_WAKEUP; } # the lock screen, no unlock
unlock() { adb shell wm dismiss-keyguard; }                # ACTION_USER_PRESENT
interactive() { adb shell dumpsys power | grep -q 'mWakefulness=Awake'; }
deep_state() { adb shell dumpsys deviceidle | sed -n 's/^  mState=\([A-Z_]*\).*/\1/p' | tr -d '\r'; }
# ADR 0016: without a motion sensor the system schedules no alarm before IDLE; step the walk.
step_to_idle() {
  for _ in 1 2 3 4 5 6; do
    [[ $(deep_state) == IDLE ]] && return 0
    sleep 5
    adb shell cmd deviceidle step deep >/dev/null
  done
  [[ $(deep_state) == IDLE ]]
}

# The device's wall clock: epoch seconds, and a local HH:MM:SS for <epoch seconds> in the device's zone.
device_epoch() { adb shell date +%s | tr -d '\r'; }
device_offset() { # +0300 -> 10800
  local z sign
  z=$(adb shell date +%z | tr -d '\r'); sign=${z:0:1}
  echo $((${sign}1 * (10#${z:1:2} * 3600 + 10#${z:3:2} * 60)))
}
local_time() { TZ=UTC date -u -d "@$(($1 + offset))" +%H:%M:%S; }
wait_until() { while (($(device_epoch) < $1)); do sleep 5; done; }

# The app's pending window-start alarms as "<type> <origWhen ms>", one per line (dumpsys alarm prints
# `RTC #n: Alarm{<hash> type <n> origWhen <wall ms> ...}` then `tag=*alarm*:<tag>`; `*walarm*` is a wakeup).
night_alarms() {
  adb shell dumpsys alarm | grep -B1 -E "tag=\*w?alarm\*:$alarm_tag\$" |
    sed -n "s/.*Alarm{[0-9a-f]* type \([0-9]*\) origWhen \([0-9]*\) .*$pkg}.*/\1 \2/p"
}
# Exactly one alarm, non-wakeup RTC (type 1), set for <epoch seconds> (to the second).
alarm_at() {
  local alarms
  alarms=$(night_alarms)
  [[ $(wc -l <<<"$alarms") == 1 && $alarms == "1 $(($1 * 1000))" ]] && \
    adb shell dumpsys alarm | grep -A1 "tag=\*alarm\*:$alarm_tag" | grep -q 'type=RTC .*window=+10m0s0ms'
}
no_alarm_after() { # none set for later than <epoch seconds>
  local type when
  while read -r type when; do
    [[ -z $when ]] && continue
    ((when / 1000 > $1)) && return 1
  done <<<"$(night_alarms)"
  return 0
}
no_alarms() { [[ -z $(night_alarms) ]]; }
switch_logged() { # <profile> <reason>
  cmd events 30 | grep -q "kind=PROFILE, .*profileId=$1, reason=$2"
}

wait_device() { timeout 120 "$A" wait-for-device </dev/null || { log "the emulator did not come back"; return 1; }; }
server_running() { [[ -n $(adb shell pidof shizuku_server | tr -d '\r') ]]; }
kill_server() {
  local pids
  pids=$(adb shell pidof shizuku_server | tr -d '\r')
  [[ -n $pids ]] && adb shell kill $pids
  sleep 1
}
start_server() {
  local path starter
  path=$(adb shell pm path "$shizuku_pkg" | sed 's/^package://; s#/base.apk##' | tr -d '\r')
  starter="$path/lib/x86_64/libshizuku.so"
  if ! adb shell ls "$starter" >/dev/null 2>&1; then
    adb pull "$path/base.apk" "$dir/manager.apk" >/dev/null
    unzip -p "$dir/manager.apk" lib/x86_64/libshizuku.so > "$dir/libshizuku.so"
    adb push "$dir/libshizuku.so" /data/local/tmp/libshizuku.so >/dev/null
    adb shell chmod 755 /data/local/tmp/libshizuku.so
    starter=/data/local/tmp/libshizuku.so
  fi
  adb shell "$starter" >> "$out/shizuku-starter.txt" 2>&1
  until_true 20 server_running
}

started_emulator=0
if adb devices | grep -q '^emulator-'; then
  # Only the project's own AVD (ADR 0021): an emulator another project started is left alone.
  running=$(adb emu avd name | head -1 | tr -d '\r')
  [[ $running == "$avd" ]] || { log "a different emulator ($running) is running; leaving it alone"; exit 1; }
else
  log "starting $avd"
  nohup "$HOME/Android/Sdk/emulator/emulator" -avd "$avd" -no-window -no-audio -no-boot-anim \
    -no-snapshot-save -gpu swiftshader_indirect > "$out/emulator.log" 2>&1 &
  started_emulator=1
fi
timeout 300 "$A" wait-for-device </dev/null
timeout 300 "$A" shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done' </dev/null
log "booted $(adb shell getprop ro.build.fingerprint | tr -d '\r')"
offset=$(device_offset)
log "device zone $(adb shell getprop persist.sys.timezone | tr -d '\r'), offset ${offset}s"

baseline_constants=$(constants)
baseline_timeout=$(adb shell settings get system screen_off_timeout | tr -d '\r')
# `locksettings get-disabled` prints true when the lock screen is None (LockSettingsShellCommand, android-17.0.0_r1).
baseline_lock_disabled=$(adb shell locksettings get-disabled | tr -d '\r')
log "before: device_idle_constants=$baseline_constants screen_off_timeout=$baseline_timeout lock_disabled=$baseline_lock_disabled"
preexisting_shizuku=$(adb shell pm path "$shizuku_pkg" | tr -d '\r')
[[ -n $preexisting_shizuku && -f $shizuku_marker ]] && preexisting_shizuku=
app_installed=0

cleanup() {
  log "cleanup"
  adb shell dumpsys battery reset
  wake_locked; unlock
  if ((app_installed)) && adb shell pidof "$pkg" >/dev/null 2>&1; then
    server_running || start_server
    cmd restore-stock > "$out/cleanup-restore.txt"
    cmd stop >/dev/null
  fi
  ((app_installed)) && kill_server
  if ((app_installed)); then
    adb shell cmd deviceidle unforce >/dev/null
    adb shell dumpsys sensorservice enable >/dev/null
  fi
  if [[ $(constants) != "$baseline_constants" ]]; then
    if [[ $baseline_constants == null ]]; then adb shell settings delete global device_idle_constants >/dev/null
    else adb shell settings put global device_idle_constants "$baseline_constants" >/dev/null; fi
  fi
  if [[ $baseline_timeout == null ]]; then adb shell settings delete system screen_off_timeout >/dev/null
  else adb shell settings put system screen_off_timeout "$baseline_timeout" >/dev/null; fi
  [[ $baseline_lock_disabled == true ]] && adb shell locksettings set-disabled true >/dev/null
  adb uninstall "$pkg" >/dev/null 2>&1
  if [[ -z $preexisting_shizuku ]] && ((app_installed)); then
    adb uninstall "$shizuku_pkg" >/dev/null 2>&1 && rm -f "$shizuku_marker"
  fi
  log "after: device_idle_constants=$(constants) screen_off_timeout=$(adb shell settings get system screen_off_timeout | tr -d '\r')" \
    "lock_disabled=$(adb shell locksettings get-disabled | tr -d '\r')"
  if ((started_emulator)); then log "shutting down the emulator"; adb emu kill >/dev/null; fi
}
trap cleanup EXIT

everyday="" night=""
setup() {
  app_installed=1
  adb uninstall "$pkg" >/dev/null 2>&1
  adb install -t "$dir/app.apk" >/dev/null || { log "app install failed"; return 1; }
  for p in android.permission.WRITE_SECURE_SETTINGS android.permission.DUMP android.permission.POST_NOTIFICATIONS; do
    adb shell pm grant "$pkg" "$p"
  done
  if [[ -z $preexisting_shizuku ]]; then
    touch "$shizuku_marker"
    adb install -r "$shizuku_apk" >/dev/null || { log "Shizuku install failed"; return 1; }
  fi
  adb shell pm grant "$pkg" moe.shizuku.manager.permission.API_V23
  # Thirty minutes, so the screen stays on through the alarm's 10-minute window; put back in cleanup.
  adb shell settings put system screen_off_timeout 1800000
  # A Swipe lock screen, so waking and unlocking are two steps as on a phone; with None, SystemUI sends
  # USER_PRESENT at every wake. Put back in cleanup.
  adb shell locksettings set-disabled false >/dev/null
  wake_locked; unlock
  adb shell am start -W -n "$starter_activity" >/dev/null
  check "the engine starts in its foreground service" until_true 20 status_has '^started=true'
  check "the Shizuku server starts" start_server
  cmd capture-stock > "$out/capture-stock.txt"
  check "stock.json is captured" grep -q '^ok=true' "$out/capture-stock.txt"
  # The two profiles' strings as lever A writes them, to compare the device against.
  cmd profile night > "$out/setup-night.txt"
  check "Night applies" until_true 15 lever_is CONSTANTS APPLIED
  night=$(constants)
  cmd profile everyday > "$out/setup-everyday.txt"
  check "Everyday applies" until_true 15 lever_is CONSTANTS APPLIED
  everyday=$(constants)
  check "Everyday and Night write different strings" test "$everyday" != "$night"
}

# Day to night with the screen on: the alarm itself switches (SPEC 6.5).
alarm() {
  local now start end
  now=$(device_epoch); start=$((now + 90)); end=$((start + 1800))
  cmd plan "everyday,night,$(local_time $start),$(local_time $end)" > "$out/alarm-plan.txt"
  check "alarm: the plan starts in the day slot with Everyday" until_true 10 slot_is DAY
  check "alarm: Everyday is on the device" constants_are "$everyday"
  adb shell dumpsys alarm > "$out/alarm-set.txt"
  check "alarm: one non-wakeup RTC alarm at the window start, 10-minute window" alarm_at $start
  check "alarm: the screen is on" interactive
  wait_until $start
  # setWindow delivers within 10 minutes of the start (AlarmManager.setWindow, android-17.0.0_r1).
  check "alarm: the night slot comes with the alarm, screen still on" until_true 660 slot_is NIGHT
  log "alarm: switched $(($(device_epoch) - start)) s after the window start"
  check "alarm: Night is on the device" until_true 15 constants_are "$night"
  check "alarm: the switch is logged as SCHEDULE" switch_logged night SCHEDULE
  check "alarm: the next night's alarm is set, a day later" until_true 15 alarm_at $((start + 86400))
  adb shell dumpsys alarm > "$out/alarm-next.txt"
}

# Night to day: an unlock inside the window keeps the night, the lock screen past the end keeps it, and
# the first unlock after the end brings the day (SPEC 6.5).
unlock_scenario() {
  local now start end
  now=$(device_epoch); start=$((now - 60)); end=$((now + 150))
  cmd plan "everyday,night,$(local_time $start),$(local_time $end)" > "$out/unlock-plan.txt"
  check "unlock: a plan set inside its window applies the night at once" until_true 10 slot_is NIGHT
  check "unlock: Night is on the device" until_true 15 constants_are "$night"
  screen_off; sleep 10; wake_locked
  check "unlock: the engine sees the lock screen" until_true 10 locked_is true
  unlock
  check "unlock: the engine sees the unlock" until_true 10 locked_is false
  check "unlock: an unlock inside the window keeps the night" slot_is NIGHT
  screen_off
  wait_until $((end + 5))
  wake_locked
  check "unlock: past the end, the engine sees the lock screen" until_true 10 locked_is true
  check "unlock: past the end, the lock screen alone keeps the night" slot_is NIGHT
  check "unlock: Night is still on the device" constants_are "$night"
  unlock
  check "unlock: past the end, the engine sees the unlock" until_true 10 locked_is false
  check "unlock: the first unlock after the end brings the day" until_true 15 slot_is DAY
  check "unlock: Everyday is on the device" until_true 15 constants_are "$everyday"
  check "unlock: the switch is logged as SCHEDULE" switch_logged everyday SCHEDULE
  adb shell dumpsys activity broadcasts history | grep -A6 'act=android.intent.action.USER_PRESENT' > "$out/unlock-broadcasts.txt" || true
  adb logcat -d -v time --pid="$(adb shell pidof io.github.akane599.enforcedoze | tr -d '\r')" > "$out/unlock-logcat.txt" || true
}

# Day to night with the screen off: the alarm finds the phone dozing, and the night begins at the next
# maintenance window, with no alarm set while the screen is off (SPEC 1.3, 6.5).
maintenance() {
  local now start end
  adb shell dumpsys battery unplug
  now=$(device_epoch); start=$((now + 150)); end=$((start + 1800))
  cmd plan "everyday,night,$(local_time $start),$(local_time $end)" > "$out/maintenance-plan.txt"
  check "maintenance: the plan starts in the day slot" until_true 10 slot_is DAY
  check "maintenance: the alarm is set for the window start" alarm_at $start
  screen_off
  check "maintenance: deep idle is reached" step_to_idle
  wait_until $((start + 20))
  check "maintenance: past the window start in deep idle, the day slot stays" slot_is DAY
  check "maintenance: Everyday is still on the device" constants_are "$everyday"
  adb shell dumpsys alarm > "$out/maintenance-deferred.txt"
  adb shell cmd deviceidle step deep > "$out/maintenance-step.txt" # IDLE -> IDLE_MAINTENANCE
  log "maintenance: $(cat "$out/maintenance-step.txt" | tr -d '\r')"
  check "maintenance: the maintenance window starts the night, screen off" until_true 20 slot_is NIGHT
  check "maintenance: Night is on the device" until_true 15 constants_are "$night"
  check "maintenance: the sensors are restricted for the night" until_true 15 sensor_mode RESTRICTED
  check "maintenance: the switch is logged as SCHEDULE" switch_logged night SCHEDULE
  adb shell dumpsys alarm > "$out/maintenance-alarms-screen-off.txt"
  check "maintenance: no later alarm was set while the screen was off" no_alarm_after $((start + 60))
  wake_locked; unlock
  check "maintenance: at screen on the next night's alarm is set" until_true 30 alarm_at $((start + 86400))
  adb shell dumpsys battery reset
}

restore() {
  cmd plan everyday > /dev/null
  check "restore: a plan without a night cancels the alarm" until_true 10 no_alarms
  cmd restore-stock > "$out/restore-stock.txt"
  check "restore: the job is ok" grep -q '^ok=true' "$out/restore-stock.txt"
  check "restore: the global string is as before the run" constants_are "$baseline_constants"
  check "restore: forced idle released" device_forced false
  check "restore: sensors normal" sensor_mode NORMAL
}
for scenario in $scenarios; do
  log "== $scenario"
  case $scenario in
    setup) setup || { log "setup failed"; exit 1; } ;;
    alarm) alarm ;;
    unlock) unlock_scenario ;;
    maintenance) maintenance ;;
    restore) restore ;;
    *) log "unknown scenario $scenario"; failures=$((failures + 1)) ;;
  esac
done

if ((app_installed)); then
  cmd status > "$out/status-final.txt"
  cmd actions 200 > "$out/actions.txt"
  cmd events 200 > "$out/events.txt"
fi
log "failures: $failures"
((failures == 0))
