#!/usr/bin/env bash
# Install/start upstream Shizuku as adb shell on a disposable emulator only.
set -euo pipefail
serial=${1:?Usage: prepare-shizuku.sh emulator-SERIAL APK_PATH}
apk=${2:?Provide the downloaded Shizuku v13.6.0 APK}
case "$serial" in emulator-*) ;; *) echo 'Refusing to alter a non-emulator device' >&2; exit 2;; esac
adb -s "$serial" install -r "$apk"
abi=$(adb -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')
starter=$(mktemp)
trap 'rm -f "$starter"' EXIT
unzip -p "$apk" "lib/$abi/libshizuku.so" > "$starter"
adb -s "$serial" push "$starter" /data/local/tmp/enforcedoze_shizuku
adb -s "$serial" shell chmod 755 /data/local/tmp/enforcedoze_shizuku
# Ensure the manager has been launched once. The native starter locates its APK via pm path.
adb -s "$serial" shell am start -n moe.shizuku.privileged.api/moe.shizuku.manager.MainActivity
adb -s "$serial" shell /data/local/tmp/enforcedoze_shizuku
