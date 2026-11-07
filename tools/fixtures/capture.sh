#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Capture raw system dumps for parser fixtures (SPEC 0.1, 8, 12.4).
#
# Usage: capture.sh [-s SERIAL] [--walk] OUT_ROOT
#
# Writes OUT_ROOT/<model>/api<SDK>_<build id>/. Run it on the machine that owns the adb server
# (akane-linux for emulators).
#
# Default mode only reads: it never changes Doze, sensor or battery state, so it is the only mode
# allowed on the owner's phone (SPEC 0.4). --walk is for emulators: it simulates unplugging, turns
# the screen off (`step` does not leave ACTIVE while the screen is on), steps deep and light Doze,
# forces idle and restricts sensors, captures each state, and restores everything in an EXIT trap
# (`unforce`, `sensorservice enable`, `battery reset`, screen on).
#
# Every command below was checked against the device's own help output on API 37
# (`cmd deviceidle help`, `dumpsys batterystats -h`, `cmd device_config help`), which this script
# also saves next to the dumps.
set -euo pipefail

serial=""
walk=false
while [ $# -gt 0 ]; do
  case "$1" in
    -s) serial="$2"; shift 2 ;;
    --walk) walk=true; shift ;;
    -h|--help) sed -n '3,17p' "$0"; exit 0 ;;
    *) break ;;
  esac
done
[ $# -eq 1 ] || { echo "usage: $0 [-s SERIAL] [--walk] OUT_ROOT" >&2; exit 2; }
out_root="$1"

adb_bin="${ADB:-adb}"
adb_args=()
[ -n "$serial" ] && adb_args=(-s "$serial")

# adb shell reads stdin; </dev/null keeps it from swallowing the caller's input.
sh_() { "$adb_bin" "${adb_args[@]}" shell "$@" </dev/null; }
prop() { sh_ getprop "$1" | tr -d '\r'; }

model="$(prop ro.product.model | tr '[:upper:] ' '[:lower:]-')"
sdk="$(prop ro.build.version.sdk)"
build="$(prop ro.build.id)"
qemu="$(prop ro.kernel.qemu)"
dir="$out_root/$model/api${sdk}_${build}"
mkdir -p "$dir"

if $walk && [ "$qemu" != "1" ]; then
  echo "--walk changes Doze and sensor state; it runs on emulators only (ro.kernel.qemu != 1)." >&2
  exit 3
fi

save() { local name="$1"; shift; sh_ "$@" > "$dir/$name" 2>&1 || true; }

{
  echo "captured_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "mode=$($walk && echo walk || echo read-only)"
  for p in ro.build.fingerprint ro.build.version.sdk ro.build.version.release ro.build.id \
    ro.build.version.incremental ro.product.model ro.product.device ro.kernel.qemu; do
    echo "$p=$(prop "$p")"
  done
} > "$dir/meta.txt"

# Help texts: the device's own list of commands and flags (SPEC 0.3 rule 2).
save help-deviceidle.txt cmd deviceidle help
save help-device_config.txt cmd device_config help
save help-batterystats.txt dumpsys batterystats -h

get_states() {
  for k in deep light force screen charging; do printf '%s=%s\n' "$k" "$(sh_ cmd deviceidle get "$k" | tr -d '\r')"; done
}

get_states > "$dir/deviceidle-get.txt"
save deviceidle.txt dumpsys deviceidle
save device_config-device_idle.txt device_config list device_idle
save settings-device_idle_constants.txt settings get global device_idle_constants
save deviceidle-whitelist.txt cmd deviceidle whitelist
save sensorservice.txt dumpsys sensorservice

if $walk; then
  restore() {
    sh_ cmd deviceidle unforce >/dev/null 2>&1 || true
    sh_ dumpsys sensorservice enable >/dev/null 2>&1 || true
    sh_ dumpsys battery reset >/dev/null 2>&1 || true
    sh_ input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  }
  trap restore EXIT

  sh_ dumpsys battery unplug >/dev/null
  sh_ input keyevent KEYCODE_SLEEP >/dev/null
  # The screen-off broadcast reaches DeviceIdleController asynchronously; wait up to 10 s for it.
  for _ in $(seq 1 20); do
    [ "$(sh_ cmd deviceidle get screen | tr -d '\r')" = "false" ] && break
    sleep 0.5
  done
  get_states > "$dir/walk-screen-off.txt"

  # Light walk first: once deep reaches IDLE, light is OVERRIDE and no longer steps.
  : > "$dir/walk-light-steps.txt"
  for _ in $(seq 1 4); do
    sh_ cmd deviceidle step light >> "$dir/walk-light-steps.txt" 2>&1
  done
  save deviceidle-stepped-light.txt dumpsys deviceidle

  # Deep walk: step until IDLE (at most 10 steps), then IDLE -> IDLE_MAINTENANCE -> IDLE.
  : > "$dir/walk-deep-steps.txt"
  for _ in $(seq 1 10); do
    sh_ cmd deviceidle step deep >> "$dir/walk-deep-steps.txt" 2>&1
    [ "$(sh_ cmd deviceidle get deep | tr -d '\r')" = "IDLE" ] && break
  done
  sh_ cmd deviceidle step deep >> "$dir/walk-deep-steps.txt" 2>&1
  save deviceidle-stepped-maintenance.txt dumpsys deviceidle
  sh_ cmd deviceidle step deep >> "$dir/walk-deep-steps.txt" 2>&1
  save deviceidle-stepped-idle.txt dumpsys deviceidle
  sh_ cmd deviceidle unforce >/dev/null

  # Forced deep idle, as EnforceDoze's classic mode does it.
  sh_ cmd deviceidle force-idle deep > "$dir/walk-force-idle.txt" 2>&1
  get_states >> "$dir/walk-force-idle.txt"
  save deviceidle-forced.txt dumpsys deviceidle
  sh_ cmd deviceidle unforce >> "$dir/walk-force-idle.txt" 2>&1
  get_states >> "$dir/walk-force-idle.txt"

  # Sensor restriction, with and without an allowed package.
  save walk-sensor-restrict.txt dumpsys sensorservice restrict
  save sensorservice-restricted.txt dumpsys sensorservice
  sh_ dumpsys sensorservice enable >/dev/null
  save walk-sensor-restrict-allow.txt dumpsys sensorservice restrict com.android.shell
  save sensorservice-restricted-allow.txt dumpsys sensorservice
  save walk-sensor-enable.txt dumpsys sensorservice enable
  save sensorservice-enabled.txt dumpsys sensorservice

  restore
  trap - EXIT
  get_states > "$dir/deviceidle-get-after.txt"
fi

# Evidence sources last, so a walk's transitions are in them.
save events.txt logcat -b events -d -v epoch
save batterystats-history.txt dumpsys batterystats --history

echo "$dir"
