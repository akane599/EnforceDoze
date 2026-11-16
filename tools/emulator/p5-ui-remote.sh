#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Runs on akane-linux; started by tools/emulator/p5-ui.sh. Argument: AVD. Exit 75 means the machine was busy and
# nothing ran (the owner's rule: no emulator while another heavy job runs; try again 15 minutes later).
# Every change is undone; the AVD is never wiped, and it is shut down only if this script started it (AGENTS.md).
set -uo pipefail

avd=$1
dir=$HOME/.cache/enforcedoze-p5
cache=$HOME/.cache/enforcedoze-shizuku
out=$dir/out
mkdir -p "$out"
A=$HOME/Android/Sdk/platform-tools/adb
adb() { "$A" "$@" </dev/null; }
pkg=io.github.akane599.enforcedoze
bench_pkg=io.github.akane599.enforcedoze.benchmark
shizuku_pkg=moe.shizuku.privileged.api
shizuku_apk=$cache/shizuku-v13.7.0-thedjchi.apk
shizuku_marker=$cache/shizuku-installed-by-p5
plan_tile=$pkg/io.github.akane599.enforcedoze.app.PlanTileService
sensors_tile=$pkg/io.github.akane599.enforcedoze.app.SensorsTileService
tiles_added=0

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
server_running() { [[ -n $(adb shell pidof shizuku_server | tr -d '\r') ]]; }
# As in p4-plan-remote.sh: the starter from the installed APK, or extracted from it when not unpacked.
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

# The owner's load rule (2026-11-16): wait while a render, build or other heavy job runs.
cores=$(nproc)
load=$(awk '{print $1}' /proc/loadavg)
heavy=$(ps -eo pcpu=,comm= --sort=-pcpu | awk '$1 > 50 && $2 != "ps" { print $2 " " $1 "%" }' | head -3 | tr '\n' ' ')
log "load $load on $cores cores; busy processes: ${heavy:-none}; $(free -h | awk '/^Mem:/ { print "memory used " $3 " of " $2 }')"
if awk -v l="$load" -v c="$cores" 'BEGIN { exit !(l > c * 0.5) }' || [[ -n $heavy ]]; then
  log "busy: not starting the emulator"
  exit 75
fi

# Device safety: every adb call goes to the emulator, never to a phone that happens to be connected over adb.
ANDROID_SERIAL=$("$A" devices </dev/null | awk '/^emulator-[0-9]+/ { print $1; exit }')
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}

started_emulator=0
# An emulator that is already running belongs to someone else, even when it is ed-api37 (another session or the
# owner): leave it alone and try again later, like a busy machine.
if adb devices | grep -q '^emulator-'; then
  log "an emulator is already running ($(adb emu avd name | head -1 | tr -d '\r')); not touching it"
  exit 75
fi
log "starting $avd"
nohup "$HOME/Android/Sdk/emulator/emulator" -avd "$avd" -no-window -no-audio -no-boot-anim \
  -no-snapshot-save -gpu swiftshader_indirect > "$out/emulator.log" 2>&1 &
started_emulator=1
timeout 300 "$A" wait-for-device </dev/null
timeout 300 "$A" shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done' </dev/null
log "booted $(adb shell getprop ro.build.fingerprint | tr -d '\r')"

preexisting_shizuku=$(adb shell pm path "$shizuku_pkg" | tr -d '\r')
[[ -n $preexisting_shizuku && -f $shizuku_marker ]] && preexisting_shizuku=
baseline_sync=$(adb shell device_config get_sync_disabled_for_tests | tr -d '\r')
baseline_constants=$(adb shell settings get global device_idle_constants | tr -d '\r')

cleanup() {
  log "cleanup"
  if ((tiles_added)); then
    for t in $plan_tile $sensors_tile; do adb shell cmd statusbar remove-tile "$t"; done
    adb shell cmd statusbar collapse
  fi
  local pids
  pids=$(adb shell pidof shizuku_server | tr -d '\r')
  [[ -n $pids ]] && adb shell kill $pids
  adb uninstall "$bench_pkg" >/dev/null 2>&1
  adb uninstall "$pkg" >/dev/null 2>&1
  if [[ -z $preexisting_shizuku ]]; then adb uninstall "$shizuku_pkg" >/dev/null 2>&1 && rm -f "$shizuku_marker"; fi
  [[ $(adb shell device_config get_sync_disabled_for_tests | tr -d '\r') == "$baseline_sync" ]] ||
    adb shell device_config set_sync_disabled_for_tests "$baseline_sync" >/dev/null
  if [[ $(adb shell settings get global device_idle_constants | tr -d '\r') != "$baseline_constants" ]]; then
    if [[ $baseline_constants == null ]]; then adb shell settings delete global device_idle_constants >/dev/null
    else adb shell settings put global device_idle_constants "$baseline_constants" >/dev/null; fi
  fi
  log "after: sync=$(adb shell device_config get_sync_disabled_for_tests | tr -d '\r')" \
    "device_idle_constants=$(adb shell settings get global device_idle_constants | tr -d '\r')"
  if ((started_emulator)); then log "shutting down the emulator"; adb emu kill >/dev/null; fi
}
trap cleanup EXIT

# 1. Cold start of the release build (SPEC 10.5 optimize): timeToInitialDisplayMs median under 500 ms.
adb uninstall "$pkg" >/dev/null 2>&1
adb install -r "$dir/app-benchmark.apk" >/dev/null && adb install -r "$dir/benchmark.apk" >/dev/null || log "benchmark install failed"
adb shell am instrument -w -r -e androidx.benchmark.suppressErrors EMULATOR \
  -e class io.github.akane599.enforcedoze.benchmark.StartupBenchmark \
  "$bench_pkg/androidx.test.runner.AndroidJUnitRunner" > "$out/benchmark-instrument.txt" 2>&1
media=/sdcard/Android/media/$bench_pkg
for f in $(adb shell ls "$media" 2>/dev/null | tr -d '\r' | grep -E '\.json$'); do adb pull "$media/$f" "$out/$f" >/dev/null; done
json=$(ls "$out"/*benchmarkData.json 2>/dev/null | head -1)
median=""
if [[ -n $json ]]; then
  median=$(python3 -I -c 'import json,sys
d=json.load(open(sys.argv[1]))
for b in d["benchmarks"]:
    m=b["metrics"].get("timeToInitialDisplayMs")
    if m: print(round(m["median"]))' "$json" | head -1)
fi
log "cold start timeToInitialDisplayMs median: ${median:-none}"
check "cold start under 500 ms" test -n "$median" -a "${median:-999999}" -lt 500
adb uninstall "$bench_pkg" >/dev/null 2>&1
adb uninstall "$pkg" >/dev/null 2>&1

# 2. Smoke run of the debug build under Shizuku: onboarding opens and the durable grants arrive (SPEC 4.1 T1).
adb install -t "$dir/app.apk" >/dev/null || log "app install failed"
if [[ -z $preexisting_shizuku ]]; then touch "$shizuku_marker"; adb install -r "$shizuku_apk" >/dev/null; fi
adb shell pm grant "$pkg" moe.shizuku.manager.permission.API_V23
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am start -W -n "$pkg/io.github.akane599.enforcedoze.app.ui.MainActivity" > "$out/start.txt"
# The server hands its binder to running processes of apps that hold API_V23, so it starts after the app (ADR 0011);
# the app then sees Shizuku arrive with the engine still off (first run).
app_running() { [[ -n $(adb shell pidof "$pkg" | tr -d '\r') ]]; }
until_true 20 app_running
start_server
check "Shizuku server running" server_running
sleep 15
adb exec-out screencap -p > "$out/onboarding.png"
granted() { adb shell dumpsys package "$pkg" | grep -q "$1: granted=true"; }
check "DUMP granted through the worker" granted android.permission.DUMP
check "WRITE_SECURE_SETTINGS granted through the worker" granted android.permission.WRITE_SECURE_SETTINGS
check "usage access set through the worker" bash -c "'$A' shell appops get $pkg GET_USAGE_STATS </dev/null | grep -q allow"
# The tiles (SPEC 9.2): added to Quick Settings, shown, the sensors tile's confirmation shown and cancelled, then
# removed. `cmd statusbar add-tile|remove-tile|click-tile|expand-settings|collapse` are StatusBarShellCommand's
# (android-17.0.0_r1).
tiles_added=1
for t in $plan_tile $sensors_tile; do adb shell cmd statusbar add-tile "$t"; done
adb shell cmd statusbar expand-settings
sleep 4
# The two tiles are the first two round tiles: "Sensors off" (a crossed circle) and "Doze plan" (three bars).
adb exec-out screencap -p > "$out/tiles.png"
# `uiautomator dump` (frameworks/base/cmds/uiautomator, android-17.0.0_r1) lists what SystemUI shows.
adb shell uiautomator dump /sdcard/ed-qs.xml >/dev/null 2>&1
adb shell cat /sdcard/ed-qs.xml > "$out/tiles-ui.xml" 2>/dev/null
adb shell rm -f /sdcard/ed-qs.xml
check "both tiles are in Quick Settings" bash -c "grep -q 'content-desc=\"Doze plan' '$out/tiles-ui.xml' && grep -q 'content-desc=\"Sensors off' '$out/tiles-ui.xml'"
adb shell cmd statusbar click-tile "$sensors_tile"
sleep 3
adb exec-out screencap -p > "$out/tile-sensors-confirm.png"
adb shell input keyevent KEYCODE_BACK
privacy_off() { adb shell dumpsys sensorservice | grep -qE '^Sensor Privacy *: *disabled'; }
check "sensors still on after the cancelled confirmation" privacy_off
adb shell cmd statusbar collapse
adb logcat -b crash -d > "$out/crash.txt"
check "no crash" test ! -s "$out/crash.txt"
adb logcat -d -s DayZero UsedApps AndroidRuntime > "$out/app-log.txt"
log "done: $failures failed"
exit $((failures > 0))
