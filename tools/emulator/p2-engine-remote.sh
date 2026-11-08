#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Runs on akane-linux; started by tools/emulator/p2-engine.sh. Arguments: AVD, scenario list. The engine
# is driven through the debug build's DebugCommandReceiver; every check reads the engine's own status
# and the device itself. Every change to the emulator is undone at the end; the AVD is never wiped, and
# it is shut down only if this script started it (AGENTS.md).
set -uo pipefail

avd=$1 scenarios=$2
dir=$HOME/.cache/enforcedoze-p2
cache=$HOME/.cache/enforcedoze-shizuku
out=$dir/out
mkdir -p "$out"
A=$HOME/Android/Sdk/platform-tools/adb
adb() { "$A" "$@" </dev/null; }

pkg=io.github.akane599.enforcedoze
receiver=$pkg/io.github.akane599.enforcedoze.app.DebugCommandReceiver
starter_activity=$pkg/io.github.akane599.enforcedoze.app.DebugStartActivity
silent_pkg=io.github.akane599.enforcedoze.silentaudio
shizuku_pkg=moe.shizuku.privileged.api
shizuku_apk=$cache/shizuku-v13.7.0-thedjchi.apk
shizuku_marker=$cache/shizuku-installed-by-p2
call_number=5551234
foreign="inactive_to=1800000"

log() { echo "[$(date +%T)] $*" | tee -a "$out/summary.txt"; }
failures=0
check() { # <description> <command...>: one PASS or FAIL line in summary.txt
  local what=$1; shift
  if "$@"; then log "PASS $what"; else log "FAIL $what"; failures=$((failures + 1)); fi
}

# The reply of one debug command (the broadcast's result data).
cmd() {
  adb shell am broadcast -n "$receiver" --es cmd "$1" ${2:+--es arg "$2"} |
    sed -n '/^Broadcast completed: result=0, data="/,$p' | sed '1s/^Broadcast completed: result=0, data="//; $s/"$//'
}
status_has() { cmd status | grep -qE "$1"; }
# Polls a condition once a second for up to <seconds>.
until_true() { # <seconds> <command...>
  local limit=$1; shift
  for _ in $(seq 1 "$limit"); do "$@" && return 0; sleep 1; done
  return 1
}
lever_is() { status_has "^lever $1 $2 "; }
both_are() { lever_is FORCE_IDLE "$1" && lever_is SENSORS "$1"; }
device_forced() { adb shell dumpsys deviceidle | grep -q "mForceIdle=$1"; }
sensor_mode() { adb shell dumpsys sensorservice | grep -m1 -q "^Mode : $1"; }
constants() { adb shell settings get global device_idle_constants | tr -d '\r'; }
constants_are() { [[ $(constants) == "$1" ]]; }
lifted_on_device() { device_forced false && sensor_mode NORMAL; }
engaged_on_device() { device_forced true && sensor_mode RESTRICTED; }
# The session the recorder stored for a screen off at or after <epoch seconds>.
session_since() { # <epoch seconds>
  local reply start
  reply=$(cmd last-session)
  start=$(sed -n 's/^start=\([0-9]*\) .*/\1/p' <<<"$reply")
  [[ -n $start ]] && ((start / 1000 >= $1 - 2)) && echo "$reply"
}
screen_off() { adb shell input keyevent KEYCODE_SLEEP; }
screen_on() {
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard
}
deep_state() { adb shell dumpsys deviceidle | sed -n 's/^  mState=\([A-Z_]*\).*/\1/p' | tr -d '\r'; }
no_motion_sensor() { adb shell dumpsys deviceidle | grep -q 'mUseMotionSensor=true mMotionSensor=null'; }
# ADR 0016: without a motion sensor the system schedules no alarm before IDLE, so play those alarms at the
# profile's walk timers (inactive_to, idle_after_inactive_to, sensing_to, locating_to) until IDLE.
step_walk() {
  local timer
  for timer in 30 30 10 5; do
    sleep "$timer"
    [[ $(deep_state) == IDLE ]] && return 0
    adb shell cmd deviceidle step deep >/dev/null
  done
  # LOCATING without a location fix may need one more step.
  for _ in 1 2; do
    [[ $(deep_state) == IDLE ]] && return 0
    adb shell cmd deviceidle step deep >/dev/null
  done
  [[ $(deep_state) == IDLE ]]
}
events_since() { adb shell logcat -d -b events -v epoch -T "$1.000" | grep -E ' device_idle(_light)?:'; }

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
    # Native libraries not extracted: run the starter from /data/local/tmp instead.
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
if ! adb devices | grep -q '^emulator-'; then
  log "starting $avd"
  nohup "$HOME/Android/Sdk/emulator/emulator" -avd "$avd" -no-window -no-audio -no-boot-anim \
    -no-snapshot-save -gpu swiftshader_indirect > "$out/emulator.log" 2>&1 &
  started_emulator=1
fi
timeout 300 "$A" wait-for-device </dev/null
timeout 300 "$A" shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done' </dev/null
log "booted $(adb shell getprop ro.build.fingerprint | tr -d '\r')"

# What the device showed before this run; the cleanup puts it back and the restore scenario compares.
baseline_constants=$(constants)
adb shell dumpsys deviceidle whitelist > "$out/allowlist-before.txt"
log "before: device_idle_constants=$baseline_constants"

preexisting_shizuku=$(adb shell pm path "$shizuku_pkg" | tr -d '\r')
[[ -n $preexisting_shizuku && -f $shizuku_marker ]] && preexisting_shizuku=
preexisting_silent=$(adb shell pm path "$silent_pkg" | tr -d '\r')

cleanup() {
  log "cleanup"
  adb shell dumpsys battery reset
  adb shell cmd uimode car no >/dev/null
  adb emu gsm cancel "$call_number" >/dev/null 2>&1
  screen_on
  if adb shell pidof "$pkg" >/dev/null 2>&1; then
    # Restore stock needs the worker for the levers it set; start the server again if a test stopped it.
    [[ -z $(adb shell pidof shizuku_server | tr -d '\r') ]] && start_server
    cmd restore-stock > "$out/cleanup-restore.txt"
    cmd stop >/dev/null
  fi
  kill_server
  # Backstops for a run cut short; each only puts back a default.
  adb shell cmd deviceidle unforce >/dev/null
  adb shell dumpsys sensorservice enable >/dev/null
  if [[ $(constants) != "$baseline_constants" ]]; then
    if [[ $baseline_constants == null ]]; then adb shell settings delete global device_idle_constants >/dev/null
    else adb shell settings put global device_idle_constants "$baseline_constants" >/dev/null; fi
  fi
  adb uninstall "$pkg" >/dev/null 2>&1
  [[ -z $preexisting_silent ]] && adb uninstall "$silent_pkg" >/dev/null 2>&1
  if [[ -z $preexisting_shizuku ]]; then adb uninstall "$shizuku_pkg" >/dev/null 2>&1 && rm -f "$shizuku_marker"; fi
  log "after: device_idle_constants=$(constants)"
  if ((started_emulator)); then log "shutting down the emulator"; adb emu kill >/dev/null; fi
}
trap cleanup EXIT

setup() {
  # A clean install, so no book, snapshot or loop-guard count from an earlier run is carried over.
  adb uninstall "$pkg" >/dev/null 2>&1
  adb install -t "$dir/app.apk" >/dev/null || { log "app install failed"; return 1; }
  for p in android.permission.WRITE_SECURE_SETTINGS android.permission.DUMP android.permission.POST_NOTIFICATIONS; do
    adb shell pm grant "$pkg" "$p"
  done
  adb shell appops set "$pkg" GET_USAGE_STATS allow
  [[ -n $preexisting_silent ]] || adb install -t "$dir/silent.apk" >/dev/null || { log "silent-audio install failed"; return 1; }
  if [[ -z $preexisting_shizuku ]]; then
    touch "$shizuku_marker"
    adb install -r "$shizuku_apk" >/dev/null || { log "Shizuku install failed"; return 1; }
  fi
  adb shell pm grant "$pkg" moe.shizuku.manager.permission.API_V23
  screen_on
  adb shell am start -W -n "$starter_activity" >/dev/null
  check "the engine starts in its foreground service" until_true 20 status_has '^started=true'
  # The Shizuku server hands its binder to running processes of apps that hold API_V23 (ADR 0011).
  check "the Shizuku server starts" start_server
  cmd profile test-classic >/dev/null
  check "the worker can write forced idle" until_true 20 lever_is FORCE_IDLE ARMED
  cmd capture-stock > "$out/capture-stock.txt"
  check "stock.json is captured" grep -q '^ok=true' "$out/capture-stock.txt"
  adb shell run-as "$pkg" cat files/engine/stock.json > "$out/stock.json"
  adb shell run-as "$pkg" cat files/engine/previous.json > "$out/previous.json"
  adb shell dumpsys battery unplug
  cmd min-session 240 >/dev/null # SPEC 13.2 step 3: the 4-minute test threshold
}

# SPEC 13.2: screen off for 5 minutes with a compressed profile, then the verdict.
night() { # <profile id>
  local profile=$1 t0 session=""
  cmd profile "$profile" > "$out/night-$profile-profile.txt"
  check "$profile: constants applied" until_true 15 lever_is CONSTANTS APPLIED
  t0=$(date +%s)
  screen_off
  if [[ $profile == test-classic ]]; then
    check "$profile: forced idle and sensor restriction engage at screen off" until_true 15 both_are APPLIED
    check "$profile: the device shows forced idle" device_forced true
    check "$profile: the device shows restricted sensors" sensor_mode RESTRICTED
  elif no_motion_sensor; then
    log "$profile: no motion sensor, so the walk to IDLE is stepped at the profile's timers (ADR 0016)"
    check "$profile: the stepped walk reaches IDLE" step_walk
  fi
  sleep $((t0 + 300 - $(date +%s)))
  screen_on
  # The recorder stores the session once the screen is on and the event log has been read.
  for _ in $(seq 1 30); do
    session=$(session_since "$t0") && break
    sleep 1
  done
  echo "$session" > "$out/night-$profile-session.txt"
  events_since "$t0" > "$out/night-$profile-events.txt"
  cmd status > "$out/night-$profile-status.txt"
  log "$profile: $(head -1 <<<"$session")"
  check "$profile: verdict Held" grep -q '^verdict=HELD ' <<<"$session"
  local windows
  windows=$(sed -n 's/.*maintenance=\([0-9]*\).*/\1/p' <<<"$session")
  check "$profile: at least 2 maintenance windows ($windows)" test "${windows:-0}" -ge 2
  check "$profile: deep idle in the event log" grep -q 'device_idle: \[5,' "$out/night-$profile-events.txt"
  if [[ $profile == test-classic ]]; then
    check "$profile: released at screen on" until_true 15 both_are ARMED
    check "$profile: the device no longer forces idle" device_forced false
    check "$profile: sensors back to normal" sensor_mode NORMAL
  fi
}

# One hold-off (SPEC 5.5, 13.3) with Test Classic engaged: it lifts B and C, and its end restores them.
holdoff() { # <name> <start command> <end command> [seconds the hold-off lasts on its own]
  local name=$1 start=$2 end=$3 lasts=${4:-}
  check "$name: engaged before the hold-off" until_true 15 both_are APPLIED
  eval "$start"
  check "$name: forced idle and sensors lifted" until_true 15 both_are HELD_OFF
  check "$name: the device shows the lift" lifted_on_device
  if [[ -n $lasts ]]; then sleep "$lasts"; else eval "$end"; fi
  check "$name: restored when it ends" until_true 20 both_are APPLIED
  check "$name: the device shows them again" engaged_on_device
}

# An incoming call wakes the screen, as it does on a phone, so the levers wait for the next screen off
# instead of showing as held off; the checks are the CALL hold-off and the device itself.
call_holdoff() {
  check "call: engaged before the hold-off" until_true 15 both_are APPLIED
  adb emu gsm call "$call_number" >/dev/null
  check "call: the call hold-off is active" until_true 15 status_has '^holdOffs=\[CALL\]'
  check "call: the device shows the lift" until_true 15 lifted_on_device
  adb emu gsm cancel "$call_number" >/dev/null
  check "call: the hold-off ends with the call" until_true 15 status_has '^holdOffs=\[\]'
  screen_off
  check "call: engaged again at the next screen off" until_true 20 both_are APPLIED
  check "call: the device shows them again" engaged_on_device
}

holdoffs() {
  cmd profile test-classic >/dev/null
  screen_off
  holdoff charging "adb shell dumpsys battery set ac 1" "adb shell dumpsys battery unplug"
  holdoff car-mode "adb shell cmd uimode car yes" "adb shell cmd uimode car no"
  holdoff audio "adb shell am broadcast -n $silent_pkg/.PlayReceiver --ei seconds 20 >/dev/null &" "" 25
  call_holdoff
  cmd events 40 > "$out/holdoffs-events.txt"
  cmd actions 60 > "$out/holdoffs-actions.txt"
  screen_on
}

# A foreign writer (SPEC 5.6, 5.7, 13.3): caught, logged and re-applied twice; the third time the loop
# guard trips and the foreign string stays until the owner answers.
drift() {
  local ours
  cmd profile test-everyday >/dev/null
  until_true 15 lever_is CONSTANTS APPLIED
  ours=$(constants)
  for round in 1 2; do
    adb shell settings put global device_idle_constants "$foreign"
    check "drift $round: re-applied" until_true 15 constants_are "$ours"
  done
  adb shell settings put global device_idle_constants "$foreign"
  check "drift 3: the loop guard trips" until_true 15 status_has '^holdOffs=.* guarded=\[CONSTANTS\]'
  sleep 3
  check "drift 3: the foreign string is left alone" constants_are "$foreign"
  cmd events 20 > "$out/drift-events.txt"
  check "drift: three drift events are logged" test "$(grep -c 'kind=DRIFT' "$out/drift-events.txt")" -ge 3
  check "drift: the guard trip is logged" grep -q 'kind=LOOP_GUARD' "$out/drift-events.txt"
  cmd clear-guard CONSTANTS > "$out/drift-cleared.txt"
  check "drift: clearing the guard re-applies" until_true 15 constants_are "$ours"
}

# Privilege lost while idle is forced (ADR 0015): the banner flag is set, constants stay, and forced idle
# is released as soon as a worker is back.
privilege() {
  cmd profile test-classic >/dev/null
  screen_off
  check "privilege: engaged" until_true 15 both_are APPLIED
  kill_server
  screen_on
  check "privilege: forced idle without a worker is flagged" until_true 15 status_has 'forcedWithoutWorker=true'
  check "privilege: constants still applied" lever_is CONSTANTS APPLIED
  cmd status > "$out/privilege-lost-status.txt"
  start_server
  check "privilege: released once the worker is back" until_true 30 device_forced false
  check "privilege: the flag clears" until_true 15 status_has 'forcedWithoutWorker=false'
}

# SPEC 4.4: the global string stays for 10 minutes and survives a reboot, and the engine comes back on
# its own from BOOT_COMPLETED without logging drift.
drifts() { cmd events 200 | grep -c 'kind=DRIFT'; }
persistence() {
  local ours before
  cmd profile test-everyday >/dev/null
  until_true 15 lever_is CONSTANTS APPLIED
  ours=$(constants)
  before=$(drifts)
  log "persistence: waiting 10 minutes"
  sleep 600
  check "persistence: the string is unchanged after 10 minutes" constants_are "$ours"
  check "persistence: no drift in those 10 minutes" test "$(drifts)" -eq "$before"
  log "persistence: rebooting"
  adb reboot
  wait_device || return 1
  timeout 300 "$A" shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done' </dev/null
  check "persistence: the string survives the reboot" constants_are "$ours"
  check "persistence: the engine starts from BOOT_COMPLETED" until_true 120 status_has '^started=true'
  check "persistence: constants read back as applied" until_true 30 lever_is CONSTANTS APPLIED
  check "persistence: no drift across the reboot" test "$(drifts)" -eq "$before"
  cmd status > "$out/persistence-after-reboot.txt"
  # A reboot plugs the emulator back in and stops the Shizuku server; put both back for what follows.
  adb shell dumpsys battery unplug
  check "persistence: the Shizuku server starts again" start_server
}

# Restore stock (SPEC 5.2, ADR 0012) must leave the device as stock.json describes.
restore() {
  cmd restore-stock > "$out/restore-stock.txt"
  check "restore: the job is ok" grep -q '^ok=true' "$out/restore-stock.txt"
  check "restore: no key differs from stock.json" grep -q 'stockDifferences=\[\]' "$out/restore-stock.txt"
  check "restore: the global string is as before the run" constants_are "$baseline_constants"
  check "restore: forced idle released" device_forced false
  check "restore: sensors normal" sensor_mode NORMAL
  adb shell dumpsys deviceidle whitelist > "$out/allowlist-after.txt"
  check "restore: the allowlist is as before" diff -q "$out/allowlist-before.txt" "$out/allowlist-after.txt"
}

for scenario in $scenarios; do
  log "== $scenario"
  case $scenario in
    setup) setup || { log "setup failed"; exit 1; } ;;
    night-everyday) night test-everyday ;;
    night-classic) night test-classic ;;
    holdoffs) holdoffs ;;
    drift) drift ;;
    privilege) privilege ;;
    persistence) persistence ;;
    restore) restore ;;
    *) log "unknown scenario $scenario"; failures=$((failures + 1)) ;;
  esac
done

cmd actions 200 > "$out/actions.txt"
cmd events 200 > "$out/events.txt"
log "failures: $failures"
((failures == 0))
