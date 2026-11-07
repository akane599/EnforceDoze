#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Runs on akane-linux; started by tools/emulator/p1-privilege.sh. Arguments: AVD, Shizuku build
# (thedjchi|official), scenario list. Every change to the emulator is undone at the end; the AVD is
# never wiped, and it is shut down only if this script started it (AGENTS.md).
set -uo pipefail

avd=$1 shizuku=$2 scenarios=$3
dir=$HOME/.cache/enforcedoze-p1
cache=$HOME/.cache/enforcedoze-shizuku
out=$dir/out
mkdir -p "$out/capabilities"
A=$HOME/Android/Sdk/platform-tools/adb
adb() { "$A" "$@" </dev/null; }

pkg=io.github.akane599.enforcedoze.privilege.test
runner=$pkg/androidx.test.runner.AndroidJUnitRunner
shizuku_pkg=moe.shizuku.privileged.api
case $shizuku in
  thedjchi) shizuku_apk=$cache/shizuku-v13.7.0-thedjchi.apk ;;
  official) shizuku_apk=$cache/shizuku-v13.6.0.r1086.2650830c-release.apk ;;
  *) echo "unknown Shizuku build $shizuku"; exit 2 ;;
esac
[[ -f $shizuku_apk ]] || { echo "missing $shizuku_apk"; exit 2; }

log() { echo "[$(date +%T)] $*"; }

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

# A Shizuku manager that is already on the shared AVD is used as it is and left installed.
preexisting_shizuku=$(adb shell pm path "$shizuku_pkg" | tr -d '\r')
if [[ -n $preexisting_shizuku ]]; then
  log "using the Shizuku already installed: $(adb shell dumpsys package "$shizuku_pkg" | grep -m1 versionName | tr -d ' \r')"
fi

kill_server() {
  local pids
  pids=$(adb shell pidof shizuku_server | tr -d '\r')
  [[ -n $pids ]] && adb shell kill $pids
  sleep 1
}

cleanup() {
  log "cleanup"
  adb root >/dev/null 2>&1; sleep 2; adb wait-for-device
  kill_server
  # The tests undo their own changes; these two put back the defaults if a test was cut short.
  adb shell cmd deviceidle unforce >/dev/null
  adb shell dumpsys sensorservice enable >/dev/null
  adb uninstall "$pkg" >/dev/null 2>&1
  [[ -z $preexisting_shizuku ]] && adb uninstall "$shizuku_pkg" >/dev/null 2>&1
  adb unroot >/dev/null 2>&1; sleep 2; adb wait-for-device
  if ((started_emulator)); then log "shutting down the emulator"; adb emu kill >/dev/null; fi
}
trap cleanup EXIT

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
  adb shell "$starter" > "$out/starter-$1.txt" 2>&1
  for _ in $(seq 1 20); do
    [[ -n $(adb shell pidof shizuku_server | tr -d '\r') ]] && break
    sleep 1
  done
  log "shizuku_server: $(adb shell ps -A -o USER,PID,NAME | grep shizuku_server | tr -s ' ')"
}

instrument() { # <scenario> [start server after the test process is up]
  local name=$1 with_server=${2:-}
  adb shell am instrument -w -r -e scenario "$name" "$runner" > "$out/instrument-$name.txt" 2>&1 &
  local inst=$!
  if [[ -n $with_server ]]; then
    # The server hands its binder to running processes of apps that hold API_V23, so start it once
    # the instrumented process exists (ADR 0011).
    for _ in $(seq 1 30); do
      [[ -n $(adb shell pidof "$pkg" | tr -d '\r') ]] && break
      sleep 1
    done
    start_server "$name"
  fi
  wait $inst
  local summary
  summary=$(grep -E '^(OK|FAILURES!!!|Tests run)' "$out/instrument-$name.txt" | tr '\n' ' ')
  log "$name: ${summary:-no summary, see instrument-$name.txt}"
  adb pull "/sdcard/Android/data/$pkg/files/capabilities/." "$out/capabilities/" >/dev/null 2>&1
  grep -q '^OK ' "$out/instrument-$name.txt"
}

install_test_apk() {
  adb uninstall "$pkg" >/dev/null 2>&1
  adb install -t "$dir/test.apk" >/dev/null || { log "install failed"; return 1; }
}

status=0
for scenario in $scenarios; do
  log "== scenario $scenario"
  case $scenario in
    none)
      install_test_apk && instrument none || status=1
      ;;
    grants)
      install_test_apk || { status=1; continue; }
      for p in android.permission.DUMP android.permission.WRITE_SECURE_SETTINGS android.permission.READ_LOGS; do
        adb shell pm grant "$pkg" "$p"
      done
      adb shell appops set "$pkg" GET_USAGE_STATS allow
      instrument grants || status=1
      ;;
    shell | root)
      kill_server
      if [[ $scenario == root ]]; then adb root >/dev/null; sleep 3; adb wait-for-device; fi
      log "adb shell runs as: $(adb shell id -u | tr -d '\r')"
      if [[ -z $preexisting_shizuku ]]; then
        adb install -r "$shizuku_apk" >/dev/null || { log "Shizuku install failed"; status=1; continue; }
      fi
      install_test_apk || { status=1; continue; }
      adb shell pm grant "$pkg" moe.shizuku.manager.permission.API_V23
      instrument "$scenario" with-server || status=1
      kill_server
      if [[ $scenario == root ]]; then adb unroot >/dev/null; sleep 3; adb wait-for-device; fi
      ;;
    *) log "unknown scenario $scenario"; status=1 ;;
  esac
done

adb logcat -d -b main -s ShizukuServer:* ShizukuServiceManager:* Shizuku:* > "$out/logcat-shizuku.txt" 2>&1
exit $status
