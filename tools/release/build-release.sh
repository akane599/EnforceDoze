#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Builds the release APK and signs it with Coulomb's key (ADR 0006, SPEC 12.6) on build-server. Gradle builds it
# unsigned; apksigner signs it reading the password from its file, so no secret passes through Gradle, its caches
# or this script's output. The signer's certificate must match the one recorded in docs/release.md, and the result
# is build/release/EnforceDoze.apk (the stable asset name, ADR 0039). Publishing is a separate, owner-approved step.
#
#   tools/release/build-release.sh
# Environment: ENFORCEDOZE_KEYSTORE, ENFORCEDOZE_KEYSTORE_PASS_FILE and ENFORCEDOZE_KEY_ALIAS override the defaults.
set -euo pipefail
cd "$(dirname "$0")/../.."

keystore=${ENFORCEDOZE_KEYSTORE:-$HOME/.local/share/akane-battery/signing/akane-release.keystore}
pass_file=${ENFORCEDOZE_KEYSTORE_PASS_FILE:-$HOME/.local/share/akane-battery/signing/password.txt}
alias=${ENFORCEDOZE_KEY_ALIAS:-akane}
# The release key's certificate, SHA-256 (keytool -list, 2026-11-16); Coulomb signs with the same key.
expected=bafc4dab3de0d6216de035411a3b7a43cbe19564fb169f510dbb785a3f089c27

[[ -r $keystore && -r $pass_file ]] || { echo "the release keystore or its password file is not readable here" >&2; exit 1; }
sdk=${ANDROID_HOME:-$HOME/android-sdk}
tools=$(ls -d "$sdk"/build-tools/*/ | sort -V | tail -1) # the newest build tools
apksigner=${tools}apksigner

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
./gradlew -q :app:assembleRelease
unsigned=app/build/outputs/apk/release/app-release-unsigned.apk
[[ -f $unsigned ]] || { echo "no unsigned release APK at $unsigned" >&2; exit 1; }

mkdir -p build/release
out=build/release/EnforceDoze.apk
# minSdk 34: apksigner writes the v3 scheme, which every supported release reads.
"$apksigner" sign --ks "$keystore" --ks-type PKCS12 --ks-key-alias "$alias" --ks-pass "file:$pass_file" \
  --out "$out" "$unsigned"
rm -f "$out.idsig"

verify=$("$apksigner" verify --print-certs --verbose "$out")
grep -q '^Number of signers: 1$' <<<"$verify" || { echo "not exactly one signer" >&2; exit 1; }
grep -q '^Verified using v3 scheme (APK Signature Scheme v3): true$' <<<"$verify" || { echo "no v3 signature" >&2; exit 1; }
digest=$(sed -n 's/^.*Signer.*: certificate SHA-256 digest: //p' <<<"$verify" | head -1)
[[ $digest == "$expected" ]] || { echo "signed with ${digest:-nothing}, expected $expected" >&2; exit 1; }
version=$("${tools}aapt2" dump badging "$out" 2>/dev/null | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)
echo "$out: version ${version:-?}, signer SHA-256 $digest, $(sha256sum "$out" | cut -d' ' -f1)"
