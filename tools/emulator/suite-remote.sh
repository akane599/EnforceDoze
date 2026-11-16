#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Runs on akane-linux for tools/emulator/suite.sh.
#   suite-remote.sh start <avd>     the owner's load rule, then boots <avd> detached and prints its serial;
#                                   exit 75 when busy or when any emulator already runs (nothing started)
#   suite-remote.sh stop <serial>   shuts down that emulator, the one start booted
set -uo pipefail
A=$HOME/Android/Sdk/platform-tools/adb
dir=$HOME/.cache/enforcedoze-suite
mkdir -p "$dir"
log() { echo "[$(date +%T)] $*" >> "$dir/suite-remote.log"; echo "$*" >&2; }
source "$(dirname "$0")/lib.sh"

case ${1:-} in
  start)
    avd=$2
    load_check
    if "$A" devices </dev/null | grep -q '^emulator-'; then
      log "an emulator is already running; not touching it"
      exit 75
    fi
    serial=emulator-5554
    log "starting $avd"
    # Detached, so it outlives this ssh session; every phase then runs against it.
    setsid nohup "$HOME/Android/Sdk/emulator/emulator" -avd "$avd" -no-window -no-audio -no-boot-anim \
      -no-snapshot-save -gpu swiftshader_indirect </dev/null > "$dir/emulator.log" 2>&1 &
    timeout 300 "$A" -s "$serial" wait-for-device </dev/null
    timeout 300 "$A" -s "$serial" shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done' </dev/null
    [[ $("$A" -s "$serial" emu avd name </dev/null | head -1 | tr -d '\r') == "$avd" ]] || { log "not $avd at $serial"; exit 1; }
    log "booted $avd at $serial"
    echo "$serial"
    ;;
  stop)
    serial=$2
    name=$("$A" -s "$serial" emu avd name </dev/null | head -1 | tr -d '\r')
    [[ $name == ed-api37 ]] || { log "$serial is ${name:-gone}, not ed-api37; not touching it"; exit 0; }
    log "shutting down $serial"
    "$A" -s "$serial" emu kill </dev/null >/dev/null
    ;;
  *) echo "usage: suite-remote.sh start <avd> | stop <serial>" >&2; exit 2 ;;
esac
