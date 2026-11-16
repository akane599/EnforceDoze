# Releases

SPEC 12.6, ADR 0006 and ADR 0039. Releases are built and signed on build-server, and the owner publishes
them by hand. Publishing is public, so the agent never does it without the owner's yes.

## Signing

- Release APKs are signed with Coulomb's release key (ADR 0006), so Coulomb can read the evidence
  provider through its signature permission (SPEC 11.3).
- The keystore and its password file stay outside the repository on build-server, both mode 600:
  `~/.local/share/akane-battery/signing/`.
- `tools/release/build-release.sh` signs with `apksigner`, which reads the password from its file.
  The password never passes through Gradle, its caches or any output.
- The certificate's SHA-256 digest, checked after every signing:

  `ba:fc:4d:ab:3d:e0:d6:21:6d:e0:35:41:1a:3b:7a:43:cb:e1:95:64:fb:16:9f:51:0d:bb:78:5a:3f:08:9c:27`

  Coulomb's release builds must carry the same digest. Otherwise the provider refuses Coulomb.

## Building a release

1. Set `versionName` and `versionCode` in `app/build.gradle.kts`. The code is
   `major × 10000 + minor × 100 + patch`, so 1.0.0 is 10000.
2. Add the version's section to `CHANGELOG.md`.
3. Run the full CI and `tools/emulator/suite.sh`. Both must be green.
4. `tools/release/build-release.sh` writes `build/release/EnforceDoze.apk` and prints its version,
   its signer digest and the APK's own SHA-256.
5. Tag `v<version>` on `main`.

## Publishing (the owner)

A GitHub Release on `akane599/EnforceDoze` for the tag, with:
- the asset `EnforceDoze.apk`, always under this name so Obtainium can track it;
- the version's `CHANGELOG.md` section as the notes;
- the APK's SHA-256.
