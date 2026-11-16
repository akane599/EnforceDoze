#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Runs on akane-linux; started by tools/emulator/p6-integrations.sh. Argument: AVD. Exit 75 means the machine was busy
# and nothing ran. Every change is undone; the AVD is never wiped, and it is shut down only if this script started it.
#
# Shell commands used, all in android-17.0.0_r1: `content query --uri` (frameworks/base/cmds/content, Content.java);
# `am start|broadcast|instrument` (ActivityManagerShellCommand); `settings get|put|delete global`; `input keyevent`;
# `wm dismiss-keyguard`; `dumpsys package` (its "install permissions" and "disabledComponents" sections).
set -uo pipefail

avd=$1
dir=$HOME/.cache/enforcedoze-p6
cache=$HOME/.cache/enforcedoze-shizuku
out=$dir/out
mkdir -p "$out"
A=$HOME/Android/Sdk/platform-tools/adb
adb() { "$A" "$@" </dev/null; }
pkg=io.github.akane599.enforcedoze
client_pkg=io.github.akane599.enforcedoze.evidenceclient
receiver=$pkg/io.github.akane599.enforcedoze.app.DebugCommandReceiver
automation=$pkg/io.github.akane599.enforcedoze.app.automation.AutomationReceiver
action=io.github.akane599.enforcedoze.action
starter_activity=$pkg/io.github.akane599.enforcedoze.app.DebugStartActivity
shizuku_pkg=moe.shizuku.privileged.api
shizuku_apk=$cache/shizuku-v13.7.0-thedjchi.apk
shizuku_marker=$cache/shizuku-installed-by-p6
# The owner's Tasker values, as the fixtures show them: about ten minutes to deep idle.
tasker=inactive_to=300000,idle_after_inactive_to=300000,sensing_to=60000

log() { echo "[$(date +%T)] $*" | tee -a "$out/summary.txt"; }
failures=0
check() { # <description> <command...>: one PASS or FAIL line in summary.txt
  local what=$1; shift
  if "$@"; then log "PASS $what"; else log "FAIL $what"; failures=$((failures + 1)); fi
}
until_true() { # <seconds> <command...>: polls once a second
  local limit=$1; shift
  for _ in $(seq 1 "$limit"); do "$@" && return 0; sleep 1; done
  return 1
}
cmd() { # the reply of one debug command (the broadcast's result data)
  adb shell am broadcast -n "$receiver" --es cmd "$1" ${2:+--es arg "$2"} |
    sed -n '/^Broadcast completed: result=0, data="/,$p' | sed '1s/^Broadcast completed: result=0, data="//; $s/"$//'
}
status_has() { cmd status | grep -qE "$1"; }
state_has() { cmd switch-state | grep -qE "$1"; }
events_have() { cmd events 60 | grep -qE "$1"; }
server_running() { [[ -n $(adb shell pidof shizuku_server | tr -d '\r') ]]; }
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
call() { # <ACTION> <token> [extras...]: one automation call, as a client sends it (docs/automation.md)
  local name=$1 token=$2; shift 2
  adb shell am broadcast -a "$action.$name" -n "$automation" --es token "$token" "$@" >> "$out/automation-calls.txt"
}
screen_off() { adb shell input keyevent KEYCODE_SLEEP; }
screen_on() { adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard; }

source "$(dirname "$0")/lib.sh"
load_check
acquire_emulator "$avd" "$out/emulator.log"

preexisting_shizuku=$(adb shell pm path "$shizuku_pkg" | tr -d '\r')
[[ -n $preexisting_shizuku && -f $shizuku_marker ]] && preexisting_shizuku=
baseline_sync=$(adb shell device_config get_sync_disabled_for_tests | tr -d '\r')
baseline_constants=$(adb shell settings get global device_idle_constants | tr -d '\r')
baseline_timeout=$(adb shell settings get system screen_off_timeout | tr -d '\r')

cleanup() {
  log "cleanup"
  local pids
  pids=$(adb shell pidof shizuku_server | tr -d '\r')
  [[ -n $pids ]] && adb shell kill $pids
  adb uninstall "$client_pkg" >/dev/null 2>&1
  adb uninstall "$pkg" >/dev/null 2>&1
  if [[ -z $preexisting_shizuku ]]; then adb uninstall "$shizuku_pkg" >/dev/null 2>&1 && rm -f "$shizuku_marker"; fi
  [[ $(adb shell device_config get_sync_disabled_for_tests | tr -d '\r') == "$baseline_sync" ]] ||
    adb shell device_config set_sync_disabled_for_tests "$baseline_sync" >/dev/null
  if [[ $(adb shell settings get global device_idle_constants | tr -d '\r') != "$baseline_constants" ]]; then
    if [[ $baseline_constants == null ]]; then adb shell settings delete global device_idle_constants >/dev/null
    else adb shell settings put global device_idle_constants "$baseline_constants" >/dev/null; fi
  fi
  [[ $baseline_timeout == null ]] || adb shell settings put system screen_off_timeout "$baseline_timeout"
  log "after: sync=$(adb shell device_config get_sync_disabled_for_tests | tr -d '\r')" \
    "device_idle_constants=$(adb shell settings get global device_idle_constants | tr -d '\r')"
  if ((started_emulator)); then log "shutting down the emulator"; adb emu kill >/dev/null; fi
}
trap cleanup EXIT

# 0. The simulated Tasker writes its values before EnforceDoze arrives, as on the owner's phone (SPEC 11.4).
adb uninstall "$client_pkg" >/dev/null 2>&1
adb uninstall "$pkg" >/dev/null 2>&1
adb shell settings put global device_idle_constants "$tasker"
adb shell settings put system screen_off_timeout 1800000

# 1. The provider (ADR 0035). The client goes in first, before EnforceDoze defines the permission.
adb install -t "$dir/client.apk" >/dev/null || log "client install failed"
adb install -t "$dir/app.apk" >/dev/null || log "app install failed"
client_granted() { adb shell dumpsys package "$client_pkg" | grep -q 'io.github.akane599.enforcedoze.permission.READ_EVIDENCE: granted=true'; }
check "the signature permission reaches a client installed before EnforceDoze" client_granted
for p in android.permission.WRITE_SECURE_SETTINGS android.permission.DUMP android.permission.POST_NOTIFICATIONS; do adb shell pm grant "$pkg" "$p"; done
if [[ -z $preexisting_shizuku ]]; then touch "$shizuku_marker"; adb install -r "$shizuku_apk" >/dev/null; fi
adb shell pm grant "$pkg" moe.shizuku.manager.permission.API_V23
screen_on
adb shell am start -W -n "$starter_activity" >/dev/null
check "the engine starts" until_true 20 status_has '^started=true'
check "the Shizuku server starts" start_server
# The stock capture saves previous.json first, then reads stock with the string deleted and puts it back.
cmd capture-stock > "$out/capture-stock.txt"
check "stock.json is captured" grep -q '^ok=true' "$out/capture-stock.txt"
check "previous.json holds the simulated Tasker setup" until_true 10 status_has 'previous=true'
constants_are_tasker() { [[ $(adb shell settings get global device_idle_constants | tr -d '\r') == "$tasker" ]]; }
check "the stock capture put the Tasker string back" constants_are_tasker
adb shell am instrument -w -r -e class io.github.akane599.enforcedoze.evidenceclient.EvidenceProviderTest \
  "$client_pkg/androidx.test.runner.AndroidJUnitRunner" > "$out/provider-instrument.txt" 2>&1
check "the client reads the provider (status, sessions, limit, refusals)" grep -q '^OK (' "$out/provider-instrument.txt"
adb shell content query --uri "content://$pkg.evidence/status" > "$out/provider-shell.txt" 2>&1
check "the shell cannot read the provider" grep -qiE 'permission denial|SecurityException' "$out/provider-shell.txt"
adb shell am start -W -a "$action.OPEN_REPORT" -p "$pkg" --el session_id 1 > "$out/report-alias-shell.txt" 2>&1
check "the shell cannot open the report alias" grep -qiE 'permission|SecurityException' "$out/report-alias-shell.txt"
# The usual order too: Coulomb installed while EnforceDoze is already there.
adb uninstall "$client_pkg" >/dev/null 2>&1
adb install -t "$dir/client.apk" >/dev/null || log "client reinstall failed"
check "the signature permission reaches a client installed after EnforceDoze" client_granted

# ADR 0036: upstream's open receivers are gone. `pm query-receivers` is PackageManagerShellCommand's (android-17.0.0_r1).
no_upstream_receiver() {
  local a
  for a in ADD_WHITELIST REMOVE_WHITELIST ENABLE_FORCEDOZE CHANGE_SETTING; do
    adb shell pm query-receivers --brief -a "com.akylas.enforcedoze.$a" | grep -q "$pkg" && return 1
  done
  return 0
}
check "no upstream receiver answers (allowlist, enable, settings)" no_upstream_receiver

# 2. The automation API (ADR 0033, 0034): off by default, then a token for the shell as the client.
call GET_STATUS nothing
no_automation_events() { ! events_have AUTOMATION; }
check "with the API off, a call never reaches the app" no_automation_events
cmd automation on > /dev/null
token=$(cmd automation-token com.android.shell | sed -n 's/^token=//p' | tr -d '\r')
check "a token is made" test ${#token} -eq 32
call GET_STATUS 00000000000000000000000000000000
check "a bad token is rejected and logged" until_true 10 events_have 'AUTOMATION.*rejected: unknown token'
call APPLY_PROFILE "$token" --es id everyday
check "APPLY_PROFILE with the token applies Everyday" until_true 20 events_have 'APPLY_PROFILE: done'
check "the engine runs Everyday" until_true 10 status_has 'profile=everyday'
call PAUSE "$token"
check "a second change within 30 s is dropped" until_true 10 events_have 'PAUSE: dropped'
call SENSORS_OFF "$token"
check "SENSORS_OFF without on is rejected" until_true 10 events_have 'SENSORS_OFF: rejected: on must be true or false'
call GET_STATUS "$token"
check "GET_STATUS replies" until_true 10 events_have 'GET_STATUS: replied'
cmd events 60 > "$out/automation-events.txt"
cmd automation off > /dev/null
receiver_disabled() { adb shell dumpsys package "$pkg" | sed -n '/disabledComponents:/,/enabledComponents:\|^ *[a-z]*Permissions:/p' | grep -q AutomationReceiver; }
check "turning the API off disables the receiver" receiver_disabled

# 3. The switch-over (SPEC 11.4) against the simulated Tasker.
cmd switch-state > "$out/switch-found.txt"
check "the found setup is the Tasker string" grep -qE 'changed=true constants=inactive_to,idle_after_inactive_to,sensing_to' "$out/switch-found.txt"
cmd import-setup > "$out/switch-import.txt"
check "Imported setup is saved" grep -q '^imported imported-setup' "$out/switch-import.txt"
check "the check watches while Everyday runs" until_true 10 state_has 'watching=true'
cmd min-session 20 > /dev/null
cmd switch-check > /dev/null
# Tasker is still on: at screen off it writes its string again.
screen_off; sleep 3
adb shell settings put global device_idle_constants "$tasker"
check "a foreign write fails the check" until_true 20 state_has '^check=ForeignWrite'
sleep 5; screen_on; sleep 5
# The owner turns the Tasker profiles off: nothing else writes over the next screen off and on.
cmd switch-check > /dev/null
screen_off; sleep 40; screen_on
check "a clean screen off and on completes the switch-over" until_true 30 state_has '^check=Complete'
cmd switch-state > "$out/switch-complete.txt"
# The way back: previous.json's string returns and EnforceDoze stops writing.
cmd return-to-previous > "$out/switch-return.txt"
check "Return to my previous setup puts the Tasker string back" until_true 10 constants_are_tasker
check "EnforceDoze is hands off after the way back" until_true 10 status_has 'mode=HANDS_OFF'

adb logcat -b crash -d > "$out/crash.txt"
check "no crash" test ! -s "$out/crash.txt"
log "done: $failures failed"
exit $((failures > 0))
