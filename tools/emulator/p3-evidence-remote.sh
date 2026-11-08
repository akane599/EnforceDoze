#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Runs on akane-linux; started by tools/emulator/p3-evidence.sh. Arguments: AVD, scenario list. The engine
# and the recorder are driven through the debug build's DebugCommandReceiver; every check reads the app's
# own reply and the device itself. Every change to the emulator is undone at the end; the AVD is never
# wiped, and it is shut down only if this script started it (AGENTS.md).
set -uo pipefail

avd=$1 scenarios=$2
dir=$HOME/.cache/enforcedoze-p3
cache=$HOME/.cache/enforcedoze-shizuku
out=$dir/out
mkdir -p "$out"
A=$HOME/Android/Sdk/platform-tools/adb
adb() { "$A" "$@" </dev/null; }

pkg=io.github.akane599.enforcedoze
receiver=$pkg/io.github.akane599.enforcedoze.app.DebugCommandReceiver
starter_activity=$pkg/io.github.akane599.enforcedoze.app.DebugStartActivity
privilege_test=io.github.akane599.enforcedoze.privilege.test
data_test=io.github.akane599.enforcedoze.data.test
shizuku_pkg=moe.shizuku.privileged.api
shizuku_apk=$cache/shizuku-v13.7.0-thedjchi.apk
shizuku_marker=$cache/shizuku-installed-by-p3

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
until_true() { # <seconds> <command...>: polls once a second
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
# BatteryService.dumpInternal prints this line while a shell `unplug` or `set` holds the battery state
# (line 1633 on android-17.0.0_r1, 1651 on android-16.0.0_r1).
battery_held() { adb shell dumpsys battery | grep -q 'UPDATES STOPPED'; }
battery_released() { ! battery_held; }
screen_off() { adb shell input keyevent KEYCODE_SLEEP; }
screen_on() {
  adb shell input keyevent KEYCODE_WAKEUP
  # Dismissing the keyguard is the unlock: the system sends ACTION_USER_PRESENT, which makes the night.
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
  for _ in 1 2; do
    [[ $(deep_state) == IDLE ]] && return 0
    adb shell cmd deviceidle step deep >/dev/null
  done
  [[ $(deep_state) == IDLE ]]
}
events_since() { adb shell logcat -d -b events -v epoch -T "$1.000" | grep -E ' device_idle(_light)?:'; }
# The stored session or night whose start is at or after <epoch seconds>.
reply_since() { # <command> <epoch seconds>
  local reply start
  reply=$(cmd "$1")
  start=$(grep -o -m1 'start=[0-9]*' <<<"$reply" | cut -d= -f2)
  [[ -n $start ]] && ((start / 1000 >= $2 - 2)) && echo "$reply"
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
sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
build=$(adb shell getprop ro.build.id | tr -d '\r')
log "booted $(adb shell getprop ro.build.fingerprint | tr -d '\r')"

baseline_constants=$(constants)
adb shell dumpsys deviceidle whitelist > "$out/allowlist-before.txt"
log "before: device_idle_constants=$baseline_constants"

preexisting_shizuku=$(adb shell pm path "$shizuku_pkg" | tr -d '\r')
[[ -n $preexisting_shizuku && -f $shizuku_marker ]] && preexisting_shizuku=
app_installed=0

cleanup() {
  log "cleanup"
  adb shell dumpsys battery reset
  screen_on
  if ((app_installed)) && adb shell pidof "$pkg" >/dev/null 2>&1; then
    # Restore stock needs the worker for the levers it set; start the server again if a test stopped it.
    server_running || start_server
    cmd restore-stock > "$out/cleanup-restore.txt"
    cmd stop >/dev/null
  fi
  ((app_installed)) && kill_server
  # Backstops for a run cut short; each only puts back a default.
  if ((app_installed)); then
    adb shell cmd deviceidle unforce >/dev/null
    adb shell dumpsys sensorservice enable >/dev/null
  fi
  if [[ $(constants) != "$baseline_constants" ]]; then
    if [[ $baseline_constants == null ]]; then adb shell settings delete global device_idle_constants >/dev/null
    else adb shell settings put global device_idle_constants "$baseline_constants" >/dev/null; fi
  fi
  adb uninstall "$pkg" >/dev/null 2>&1
  adb uninstall "$privilege_test" >/dev/null 2>&1
  adb uninstall "$data_test" >/dev/null 2>&1
  if [[ -z $preexisting_shizuku ]] && ((app_installed)); then
    adb uninstall "$shizuku_pkg" >/dev/null 2>&1 && rm -f "$shizuku_marker"
  fi
  log "after: device_idle_constants=$(constants)"
  if ((started_emulator)); then log "shutting down the emulator"; adb emu kill >/dev/null; fi
}
trap cleanup EXIT

setup() {
  # A clean install, so no session, night or snapshot from an earlier run is carried over.
  app_installed=1
  adb uninstall "$pkg" >/dev/null 2>&1
  adb install -t "$dir/app.apk" >/dev/null || { log "app install failed"; return 1; }
  for p in android.permission.WRITE_SECURE_SETTINGS android.permission.DUMP android.permission.POST_NOTIFICATIONS; do
    adb shell pm grant "$pkg" "$p"
  done
  adb shell appops set "$pkg" GET_USAGE_STATS allow
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
  adb shell dumpsys battery unplug # SPEC 13.2 step 1
  cmd min-session 240 >/dev/null
  # SPEC 13.2 step 3: the night report threshold is 4 minutes for the test.
  check "the night threshold is 4 minutes" grep -q '^night threshold 240 s' <<<"$(cmd night-threshold 240)"
}

# SPEC 13.2 and the P3 gate: a compressed night, then the unlock that makes its night report.
night() { # <profile id> <earlier nights expected in the comparison>
  local profile=$1 earlier=$2 t0 session="" report="" f
  f="$out/night-$profile"
  cmd profile "$profile" > "$f-profile.txt"
  check "$profile: constants applied" until_true 15 lever_is CONSTANTS APPLIED
  t0=$(date +%s)
  screen_off
  if [[ $profile == test-classic ]]; then
    check "$profile: forced idle and sensor restriction engage at screen off" until_true 15 both_are APPLIED
  elif no_motion_sensor; then
    log "$profile: no motion sensor, so the walk to IDLE is stepped at the profile's timers (ADR 0016)"
    check "$profile: the stepped walk reaches IDLE" step_walk
  fi
  sleep $((t0 + 300 - $(date +%s)))
  screen_on
  for _ in $(seq 1 30); do
    session=$(reply_since last-session "$t0") && break
    sleep 1
  done
  for _ in $(seq 1 15); do
    report=$(reply_since last-night "$t0") && break
    sleep 1
  done
  echo "$session" > "$f-session.txt"
  echo "$report" > "$f-report.txt"
  events_since "$t0" > "$f-events.txt"
  log "$profile: $(head -1 <<<"$session")"
  log "$profile: $(sed -n 2p <<<"$report")"

  # The session (SPEC 8.4) and how it was collected (SPEC 8.2).
  check "$profile: verdict Held" grep -q '^verdict=HELD ' <<<"$session"
  check "$profile: the app's own wakeups are 0" grep -q '^ownWakeups=0 ' <<<"$session"
  check "$profile: alarm stats read at both ends" grep -q ' alarmsRead=true ' <<<"$session"
  local ms
  ms=$(sed -n 's/.* collectionMs=\([0-9]*\).*/\1/p' <<<"$session")
  check "$profile: the screen-on collection took ${ms:-?} ms (under 2 s)" test "${ms:-99999}" -lt 2000
  check "$profile: sensor snapshots at screen off, a maintenance window and screen on" \
    grep -qE '^transitions=.* snapshots=\[SCREEN_OFF, (COPY, )+SCREEN_ON\]' <<<"$session"
  check "$profile: no gap in the Doze record" bash -c '! grep -q "^gap " <<<"$1"' _ "$session"

  # The night report (SPEC 8.5), every part.
  check "$profile: a night report exists at the unlock" grep -q '^night id=' <<<"$report"
  check "$profile: the verdict line is Held with 0 own wakeups" grep -qE '^verdict=HELD .* ownWakeups=0$' <<<"$report"
  check "$profile: the timeline shows deep idle and a maintenance window" \
    bash -c 'grep -q "^timeline IDLE " <<<"$1" && grep -q "^timeline IDLE_MAINTENANCE " <<<"$1"' _ "$report"
  check "$profile: compared with $earlier earlier night(s)" grep -q "^recent nights=$earlier " <<<"$report"
  check "$profile: the stock comparison is there" grep -q '^stock nights=' <<<"$report"
  check "$profile: the app is not among the wakers" bash -c '! grep -q "^app .* self=true " <<<"$1"' _ "$report"
  log "$profile: report has $(grep -c '^app ' <<<"$report") app, $(grep -c '^kernel ' <<<"$report") kernel," \
    "$(grep -c '^sensor ' <<<"$report") sensor, $(grep -c '^holdOff \|^drift ' <<<"$report") change and" \
    "$(grep -c '^action ' <<<"$report") action lines"
  if [[ $profile == test-classic ]]; then
    check "$profile: the report lists the screen-off actions" grep -qE '^action [0-9]+ FORCE_IDLE OK SCREEN_OFF$' <<<"$report"
    check "$profile: released at screen on" until_true 15 both_are ARMED
  fi
}

# SPEC 8.6: the levers run for real with the screen on and are undone, the battery included.
quick_proof() {
  local reply
  cmd profile test-everyday >/dev/null
  until_true 15 lever_is CONSTANTS APPLIED
  check "quick proof: the battery is held unplugged before" battery_held
  reply=$(cmd quick-proof)
  echo "$reply" > "$out/quick-proof.txt"
  check "quick proof: ok" grep -q '^ok=true refused=null$' <<<"$reply"
  check "quick proof: eight steps, all ok" test "$(grep -c '^step [A-Z_]* ok ' <<<"$reply")" -eq 8
  check "quick proof: deep idle confirmed" grep -q '^step DEEP_IDLE ok cmd deviceidle get deep: IDLE$' <<<"$reply"
  check "quick proof: forced idle undone on the device" device_forced false
  check "quick proof: sensors normal on the device" sensor_mode NORMAL
  check "quick proof: the battery is given back to the system" battery_released
  check "quick proof: the engine no longer marks the battery" status_has '^batteryFrozen=false$'
  cmd actions 12 > "$out/quick-proof-actions.txt"
  cmd events 6 > "$out/quick-proof-events.txt"
  check "quick proof: the battery steps are logged" test "$(grep -c 'kind=BATTERY' "$out/quick-proof-events.txt")" -eq 2
  # The reset gives the emulator its AC power back; unplug again for anything that follows.
  adb shell dumpsys battery unplug
}

# SPEC 8.7: one ZIP per session or per range.
export_zip() {
  local reply id path
  reply=$(cmd export all)
  echo "$reply" > "$out/export-all.txt"
  for entry in manifest.json sessions.json actions.csv events.csv; do
    check "export all: $entry" grep -q "^entry $entry " <<<"$reply"
  done
  check "export all: the profile files in use" grep -qE '^entry profiles/[A-Za-z0-9._-]+\.json ' <<<"$reply"
  check "export all: the raw dumps" grep -qE '^entry raw/[0-9]+/' <<<"$reply"
  path=$(sed -n 's/^path=\([^ ]*\) .*/\1/p' <<<"$reply")
  adb exec-out run-as "$pkg" cat "${path#/data/user/0/$pkg/}" > "$out/export-all.zip"
  check "export all: the ZIP is valid" python3 -I -c '
import json, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    assert z.testzip() is None
    manifest = json.loads(z.read("manifest.json"))
    sessions = json.loads(z.read("sessions.json"))
    assert manifest["sdk"] > 0 and len(sessions) >= 2, (manifest, len(sessions))
    assert all(s["session"]["verdict"] for s in sessions if s.get("night")), "a night without a verdict"
    assert sum(1 for s in sessions if s.get("night")) >= 2, "fewer than 2 nights"
    assert z.read("actions.csv").decode().startswith("time,")
' "$out/export-all.zip"
  id=$(sed -n 's/^night id=[0-9]* session=\([0-9]*\) .*/\1/p' "$out/night-test-classic-report.txt")
  reply=$(cmd export "${id:-0}")
  echo "$reply" > "$out/export-session.txt"
  check "export session $id: one session with its raw dumps" grep -qE '^entry raw/[0-9]+/' <<<"$reply"
  # Keep the listing as evidence; the ZIP itself stays in the run folder.
  unzip -l "$out/export-all.zip" > "$out/export-all-listing.txt"
  rm -f "$out/export-all.zip"
}

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

instrument() { # <name> <package> [extra am instrument args...]
  local name=$1 test_pkg=$2; shift 2
  adb shell am instrument -w -r "$@" "$test_pkg/androidx.test.runner.AndroidJUnitRunner" > "$out/instrument-$name.txt" 2>&1
  log "$name: $(grep -E '^(OK|FAILURES!!!|Tests run)' "$out/instrument-$name.txt" | tr '\n' ' ')"
  grep -q '^OK ' "$out/instrument-$name.txt"
}

# The worker contract (SPEC 13.1), now with the battery unplug and reset of the quick proof, as Shizuku.
contract() {
  kill_server
  adb uninstall "$privilege_test" >/dev/null 2>&1
  adb install -t "$dir/privilege-test.apk" >/dev/null || { log "test install failed"; failures=$((failures + 1)); return; }
  adb shell pm grant "$privilege_test" moe.shizuku.manager.permission.API_V23
  instrument contract "$privilege_test" -e scenario shell &
  local inst=$!
  # The server hands its binder to processes that already run (ADR 0011).
  until_true 30 bash -c '[[ -n $("$1" shell pidof "$2" </dev/null | tr -d "\r") ]]' _ "$A" "$privilege_test"
  start_server
  wait $inst
  check "worker contract tests pass as Shizuku" grep -q '^OK ' "$out/instrument-contract.txt"
  kill_server
  start_server # for the app's cleanup
  adb uninstall "$privilege_test" >/dev/null 2>&1
}

# Room: sessions, nights and their retention (core/data androidTest).
dao() {
  adb uninstall "$data_test" >/dev/null 2>&1
  adb install -t "$dir/data-test.apk" >/dev/null || { log "test install failed"; failures=$((failures + 1)); return; }
  check "Room DAO tests pass" instrument dao "$data_test"
  adb uninstall "$data_test" >/dev/null 2>&1
}

# ADR 0019: the emulator captures predate alarm.txt; this adds it, read-only, for the parser fixtures.
alarm_fixture() {
  adb shell dumpsys alarm > "$out/alarm-api${sdk}_${build}.txt"
  check "alarm dump for api${sdk}_${build} has its Alarm Stats" grep -q '^  Alarm Stats:' "$out/alarm-api${sdk}_${build}.txt"
}

for scenario in $scenarios; do
  log "== $scenario"
  case $scenario in
    setup) setup || { log "setup failed"; exit 1; } ;;
    night-everyday) night test-everyday 0 ;;
    night-classic) night test-classic 1 ;;
    quick-proof) quick_proof ;;
    export) export_zip ;;
    restore) restore ;;
    contract) contract ;;
    dao) dao ;;
    alarm-fixture) alarm_fixture ;;
    *) log "unknown scenario $scenario"; failures=$((failures + 1)) ;;
  esac
done

if ((app_installed)); then
  cmd actions 200 > "$out/actions.txt"
  cmd events 200 > "$out/events.txt"
fi
log "failures: $failures"
((failures == 0))
