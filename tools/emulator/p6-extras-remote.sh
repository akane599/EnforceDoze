#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Runs on akane-linux; started by tools/emulator/p6-extras.sh. Argument: AVD. Exit 75 means the machine was busy and
# nothing ran. Every change is undone (Wi-Fi, its saved network, mobile data, the battery, root adb); the AVD is
# never wiped, and it is shut down only if this script started it.
#
# Shell commands used, all in android-17.0.0_r1: `cmd wifi status|list-networks|forget-network|connect-network|
# set-wifi-enabled` (WifiShellCommand); `cmd phone data enable` (TelephonyShellCommand); `dumpsys battery
# unplug|reset` (BatteryService); `dumpsys package` (its per-user `suspended=` field); `dumpsys notification`;
# `settings list global`; `adb root|unroot` (userdebug images only).
set -uo pipefail

avd=$1
dir=$HOME/.cache/enforcedoze-p6-extras
cache=$HOME/.cache/enforcedoze-shizuku
out=$dir/out
mkdir -p "$out"
A=$HOME/Android/Sdk/platform-tools/adb
adb() { "$A" "$@" </dev/null; }
pkg=io.github.akane599.enforcedoze
# The app to suspend: the provider test client, an ordinary user app that this run installs and removes.
target=io.github.akane599.enforcedoze.evidenceclient
clock=com.google.android.deskclock
receiver=$pkg/io.github.akane599.enforcedoze.app.DebugCommandReceiver
starter_activity=$pkg/io.github.akane599.enforcedoze.app.DebugStartActivity
shizuku_pkg=moe.shizuku.privileged.api
shizuku_apk=$cache/shizuku-v13.7.0-thedjchi.apk
shizuku_marker=$cache/shizuku-installed-by-p6-extras

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
  adb shell am broadcast -n "$receiver" --es cmd "$1" ${2:+--es arg "'$2'"} |
    sed -n '/^Broadcast completed: result=0, data="/,$p' | sed '1s/^Broadcast completed: result=0, data="//; $s/"$//'
}
status_has() { cmd status | grep -qE "$1"; }
lever() { cmd status | grep "^lever $1 "; }
lever_has() { lever "$1" | grep -qE "$2"; }
server_running() { [[ -n $(adb shell pidof shizuku_server | tr -d '\r') ]]; }
server_is_root() { [[ $(adb shell ps -o USER= -p "$(adb shell pidof shizuku_server | tr -d '\r')" | tr -d '\r ') == root ]]; }
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
root_adb() { adb root >/dev/null 2>&1; sleep 3; timeout 60 "$A" wait-for-device </dev/null; [[ $(adb shell id -u | tr -d '\r') == 0 ]]; }
screen_off() { adb shell input keyevent KEYCODE_SLEEP; }
screen_on() { adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard; }
wifi_enabled() { adb shell cmd wifi status | grep -q 'Wifi is enabled'; }
wifi_disabled() { adb shell cmd wifi status | grep -q 'Wifi is disabled'; }
wifi_connected() { adb shell cmd wifi status | grep -qi 'Wifi is connected to'; }
wifi_unconnected() { wifi_enabled && ! wifi_connected; }
suspended() { adb shell dumpsys package "$1" | grep -q 'suspended=true'; }
not_suspended() { ! suspended "$1"; }
stuck_notified() { adb shell dumpsys notification --noredact | grep -q 'EnforceDoze cannot put a setting back'; }
not_stuck_notified() { ! stuck_notified; }
network_id() { adb shell cmd wifi list-networks | awk '$2 == "AndroidWifi" { print $1; exit }' | tr -d '\r'; }

source "$(dirname "$0")/lib.sh"
load_check
acquire_emulator "$avd" "$out/emulator.log"
boot_wait() {
  timeout 300 "$A" wait-for-device </dev/null
  timeout 300 "$A" shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done' </dev/null
}
until_true 60 wifi_connected

preexisting_shizuku=$(adb shell pm path "$shizuku_pkg" | tr -d '\r')
[[ -n $preexisting_shizuku && -f $shizuku_marker ]] && preexisting_shizuku=
baseline_wifi=$(wifi_enabled && echo on || echo off)
baseline_networks=$(adb shell cmd wifi list-networks | tr -d '\r')
baseline_timeout=$(adb shell settings get system screen_off_timeout | tr -d '\r')
adb shell settings list global | grep -i mobile_data > "$out/mobile-data-before.txt"
log "baseline: wifi=$baseline_wifi $(tr '\n' ' ' < "$out/mobile-data-before.txt")"

cleanup() {
  log "cleanup"
  local pids
  pids=$(adb shell pidof shizuku_server | tr -d '\r')
  [[ -n $pids ]] && adb shell kill $pids
  adb uninstall "$pkg" >/dev/null 2>&1
  adb uninstall "$target" >/dev/null 2>&1
  if [[ -z $preexisting_shizuku ]]; then adb uninstall "$shizuku_pkg" >/dev/null 2>&1 && rm -f "$shizuku_marker"; fi
  adb shell dumpsys battery reset
  adb shell cmd phone data enable >/dev/null 2>&1
  [[ $baseline_wifi == on ]] && adb shell cmd wifi set-wifi-enabled enabled
  if [[ $baseline_networks == *AndroidWifi* && -z $(network_id) ]]; then
    adb shell cmd wifi connect-network AndroidWifi open >/dev/null
  fi
  [[ $baseline_timeout == null ]] || adb shell settings put system screen_off_timeout "$baseline_timeout"
  adb unroot >/dev/null 2>&1
  adb shell settings list global | grep -i mobile_data > "$out/mobile-data-after.txt"
  log "after: wifi=$(wifi_enabled && echo on || echo off) networks=$(network_id) $(tr '\n' ' ' < "$out/mobile-data-after.txt")"
  if ((started_emulator)); then log "shutting down the emulator"; adb emu kill >/dev/null; fi
}
trap cleanup EXIT

# 0. EnforceDoze at T3: Shizuku's server started by a root adb, so the worker runs as uid 0.
adb uninstall "$pkg" >/dev/null 2>&1
adb install -t "$dir/client.apk" >/dev/null || log "target app install failed"
adb install -t "$dir/app.apk" >/dev/null || log "app install failed"
for p in android.permission.WRITE_SECURE_SETTINGS android.permission.DUMP android.permission.POST_NOTIFICATIONS; do adb shell pm grant "$pkg" "$p"; done
if [[ -z $preexisting_shizuku ]]; then touch "$shizuku_marker"; adb install -r "$shizuku_apk" >/dev/null; fi
adb shell pm grant "$pkg" moe.shizuku.manager.permission.API_V23
adb shell settings put system screen_off_timeout 1800000
# The emulator reports AC power, which holds Doze off (SPEC 5.5): on battery for the run, reset in cleanup.
adb shell dumpsys battery unplug
screen_on
adb shell am start -W -n "$starter_activity" >/dev/null
check "the engine starts" until_true 20 status_has '^started=true'
check "adb runs as root" root_adb
check "the Shizuku server starts as root" start_server
check "the server's process is root's" server_is_root
worker_root() { cmd privilege | grep -q 'uid=0'; }
check "the worker reports uid 0 (T3)" until_true 20 worker_root
cmd profile everyday > /dev/null

# 1. The never-suspend list (ADR 0038), read on the phone.
cmd never-suspend "$target;$clock;$pkg" > "$out/never-suspend.txt"
check "an ordinary app may be suspended" grep -q "^$target: allowed" "$out/never-suspend.txt"
check "the clock and EnforceDoze are held back" bash -c "grep -q '^$clock: a system app' '$out/never-suspend.txt' && grep -q '^$pkg: EnforceDoze needs it' '$out/never-suspend.txt'"

# 2. Mobile data off while a working Wi-Fi network carries the connection, back on when it goes.
cmd extras data=on > /dev/null
check "mobile data off is armed with the screen on" until_true 10 lever_has MOBILE_DATA ARMED
screen_off
check "at screen off mobile data goes off and reads back off" until_true 20 lever_has MOBILE_DATA 'APPLIED .*seen=mobile data off'
# What the app process saw of the networks, and the system's own view, for the record.
cmd radios > "$out/radios-screen-off.txt"
lever MOBILE_DATA >> "$out/radios-screen-off.txt"
adb shell dumpsys connectivity | grep -E 'Active default network' > "$out/connectivity-screen-off.txt"
adb shell settings list global | grep -i mobile_data > "$out/mobile-data-engaged.txt"
adb shell cmd wifi set-wifi-enabled disabled
check "when Wi-Fi goes, mobile data comes back on its own" until_true 30 lever_has MOBILE_DATA 'seen=mobile data on'
network_cause() { cmd actions 5 | grep -q 'MOBILE_DATA.*NETWORK'; }
check "that write is logged with the network as its cause" network_cause
adb shell cmd wifi set-wifi-enabled enabled
until_true 60 wifi_connected
screen_on; sleep 3
# A hold-off brings it back too: charging, as the P2 run plugs the battery in.
screen_off
check "screen off again takes mobile data off" until_true 20 lever_has MOBILE_DATA 'seen=mobile data off'
adb shell dumpsys battery set ac 1
check "a hold-off (charging) brings mobile data back" until_true 20 lever_has MOBILE_DATA 'seen=mobile data on'
adb shell dumpsys battery unplug
check "the hold-off's end takes it off again" until_true 20 lever_has MOBILE_DATA 'seen=mobile data off'
screen_on
check "screen on brings mobile data back" until_true 20 lever_has MOBILE_DATA 'ARMED .*seen=mobile data on'

# 3. Wi-Fi off only when on but not connected.
cmd extras wifi=on > /dev/null
screen_off
check "a connected Wi-Fi stays on, with the reason" until_true 20 lever_has WIFI 'UNUSED .*reason=Wi-Fi is connected'
screen_on; sleep 3
id=$(network_id)
adb shell cmd wifi forget-network "$id" > /dev/null
check "Wi-Fi is on and not connected after forgetting the network" until_true 30 wifi_unconnected
screen_off
check "at screen off an unconnected Wi-Fi goes off" until_true 20 wifi_disabled
lever WIFI > "$out/wifi-engaged.txt"
screen_on
check "screen on turns Wi-Fi back on" until_true 20 wifi_enabled
wifi_release_ok() { cmd actions 10 | grep 'lever=WIFI, command=cmd wifi set-wifi-enabled enabled' | tail -1 | grep -q 'outcome=OK'; }
check "the Wi-Fi release reads back on once Wi-Fi has started" until_true 10 wifi_release_ok
adb shell cmd wifi connect-network AndroidWifi open > /dev/null
check "the emulator's network is saved and connected again" until_true 60 wifi_connected

# 4. App suspension: only the chosen app outside the never-suspend list, and only until screen on.
cmd extras "suspend=$target;$clock" > /dev/null
screen_off
check "at screen off the chosen app is suspended" until_true 20 suspended "$target"
check "the clock is never suspended" not_suspended "$clock"
lever APP_SUSPENSION > "$out/suspension-engaged.txt"
screen_on
check "screen on lifts the suspension" until_true 20 not_suspended "$target"
screen_off
check "suspended again at the next screen off" until_true 20 suspended "$target"
cmd restore-stock > "$out/restore-stock.txt"
check "Restore stock lifts it with the screen off" until_true 20 not_suspended "$target"
cmd resume > /dev/null
screen_on; sleep 3

# 5. A reboot with mobile data held off: without the root worker the app says so, and puts it back once root returns.
cmd extras data=on > /dev/null
screen_off
check "mobile data off before the reboot" until_true 20 lever_has MOBILE_DATA 'seen=mobile data off'
log "rebooting"
adb reboot
boot_wait
adb shell dumpsys battery unplug
screen_on
check "the engine starts at boot" until_true 60 status_has '^started=true'
check "a held extra without root is reported" until_true 30 status_has 'extrasStuck=\[MOBILE_DATA\]'
check "and notified outside the app" until_true 20 stuck_notified
adb shell dumpsys notification --noredact | grep -A3 'cannot put a setting back' > "$out/stuck-notification.txt"
check "adb runs as root after the reboot" root_adb
check "the Shizuku server starts as root again" start_server
check "with root back, mobile data comes back on" until_true 30 lever_has MOBILE_DATA 'seen=mobile data on'
check "nothing is stuck any more" until_true 20 status_has 'extrasStuck=\[\]'
check "the notification is gone" until_true 20 not_stuck_notified

cmd status > "$out/status.txt"
cmd actions 60 > "$out/actions.txt"
cmd events 60 > "$out/events.txt"
adb logcat -b crash -d > "$out/crash.txt"
check "no crash" test ! -s "$out/crash.txt"
log "done: $failures failed"
exit $((failures > 0))
