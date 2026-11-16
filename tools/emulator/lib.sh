#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Sourced by every tools/emulator/*-remote.sh on akane-linux, after it defines `A` (adb) and `log`.
# The owner's rules for akane-linux: wait while a render, build or another emulator runs (exit 75, nothing ran);
# never touch an emulator that is already running, even ed-api37; shut down only an emulator the run started.
# Under tools/emulator/suite.sh, SUITE_SERIAL names the emulator the suite started and checked: the phase uses it
# and leaves it running for the next phase.

load_check() {
  [[ -n ${SUITE_SERIAL:-} ]] && return 0
  local cores load heavy
  cores=$(nproc)
  load=$(awk '{print $1}' /proc/loadavg)
  heavy=$(ps -eo pcpu=,comm= --sort=-pcpu | awk '$1 > 50 && $2 != "ps" { print $2 " " $1 "%" }' | head -3 | tr '\n' ' ')
  log "load $load on $cores cores; busy processes: ${heavy:-none}; $(free -h | awk '/^Mem:/ { print "memory used " $3 " of " $2 }')"
  if awk -v l="$load" -v c="$cores" 'BEGIN { exit !(l > c * 0.5) }' || [[ -n $heavy ]]; then
    log "busy: not starting the emulator"
    exit 75
  fi
}

# acquire_emulator <avd> <emulator log>: sets ANDROID_SERIAL (device safety: every adb call goes to the emulator,
# never to a phone connected over adb) and started_emulator, then waits for the boot.
acquire_emulator() {
  local avd=$1 emulator_log=$2
  started_emulator=0
  if [[ -n ${SUITE_SERIAL:-} ]]; then
    export ANDROID_SERIAL=$SUITE_SERIAL
    log "using the suite's emulator ($SUITE_SERIAL)"
  else
    if "$A" devices </dev/null | grep -q '^emulator-'; then
      log "an emulator is already running ($("$A" emu avd name </dev/null | head -1 | tr -d '\r')); not touching it"
      exit 75
    fi
    export ANDROID_SERIAL=emulator-5554
    log "starting $avd"
    nohup "$HOME/Android/Sdk/emulator/emulator" -avd "$avd" -no-window -no-audio -no-boot-anim \
      -no-snapshot-save -gpu swiftshader_indirect > "$emulator_log" 2>&1 &
    started_emulator=1
  fi
  timeout 300 "$A" wait-for-device </dev/null
  timeout 300 "$A" shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done' </dev/null
  log "booted $("$A" shell getprop ro.build.fingerprint </dev/null | tr -d '\r')"
}
