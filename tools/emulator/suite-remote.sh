#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Runs on akane-linux for tools/emulator/suite.sh.
#   suite-remote.sh check <avd>     exit 75 when akane-linux is busy, an emulator already runs, or the disk
#                                   cannot hold <avd>'s data partition (the emulator refuses to boot then)
#   suite-remote.sh start <avd>     the same checks, then boots <avd> detached and prints its serial
#   suite-remote.sh stop <serial>   shuts down that emulator, the one start booted
set -uo pipefail
A=$HOME/Android/Sdk/platform-tools/adb
dir=$HOME/.cache/enforcedoze-suite
mkdir -p "$dir"
log() { echo "[$(date +%T)] $*" >> "$dir/suite-remote.log"; echo "$*" >&2; }
source "$(dirname "$0")/lib.sh"

check() { # <avd>
  local avd=$1 need free
  load_check
  if "$A" devices </dev/null | grep -q '^emulator-'; then
    log "an emulator is already running; not touching it"
    exit 75
  fi
  need=$(sed -n 's/^disk.dataPartition.size=\([0-9]*\)G$/\1/p' "$HOME/.android/avd/$avd.avd/config.ini")
  free=$(df -B1 --output=avail "$HOME" | tail -1)
  if [[ -n $need ]] && ((free < need * 1024 * 1024 * 1024)); then
    log "only $((free / 1048576)) MiB free; $avd needs $need GiB free to boot"
    exit 75
  fi
}

case ${1:-} in
  check) check "$2" ;;
  start)
    avd=$2
    check "$avd"
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
  *) echo "usage: suite-remote.sh check <avd> | start <avd> | stop <serial>" >&2; exit 2 ;;
esac
