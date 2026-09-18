#!/usr/bin/env bash
set -euo pipefail
unsigned=${1:?Usage: sign-apk.sh UNSIGNED_APK OUTPUT_APK}
output=${2:?Provide an output APK path}
: "${RELEASE_KEYSTORE:?Set RELEASE_KEYSTORE outside the repository}"
: "${RELEASE_KEYSTORE_PASSWORD:?Set RELEASE_KEYSTORE_PASSWORD}"
: "${RELEASE_KEYSTORE_ALIAS:?Set RELEASE_KEYSTORE_ALIAS}"
: "${RELEASE_KEY_PASSWORD:?Set RELEASE_KEY_PASSWORD}"
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
: "${sdk:?Set ANDROID_HOME}"
signer="$sdk/build-tools/36.0.0/apksigner"
"$signer" sign --ks "$RELEASE_KEYSTORE" --ks-key-alias "$RELEASE_KEYSTORE_ALIAS" \
  --ks-pass env:RELEASE_KEYSTORE_PASSWORD --key-pass env:RELEASE_KEY_PASSWORD \
  --out "$output" "$unsigned"
"$signer" verify --verbose --print-certs "$output" > "${output%.apk}-certificate.txt"
