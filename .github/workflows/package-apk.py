#!/usr/bin/env python3
"""Stage the universal debug APK with its version, revision and public build details."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil


def main():
    output_dir = Path("app/build/outputs/apk/debug")
    metadata = json.loads((output_dir / "output-metadata.json").read_text())
    elements = metadata["elements"]
    if len(elements) != 1 or elements[0].get("filters"):
        raise ValueError("Expected one universal debug APK")
    apk = elements[0]
    if apk["outputFile"] != "app-debug.apk":
        raise ValueError("Unexpected APK filename; signature verification must match the download")
    source = output_dir / apk["outputFile"]
    revision = os.environ["GITHUB_SHA"]
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("Expected a full Git commit SHA")
    version = re.sub(r"[^A-Za-z0-9._-]", "_", apk["versionName"])
    name = f"EnforceDoze-{version}-debug-{revision[:7]}.apk"
    destination = Path("apk-download")
    destination.mkdir(exist_ok=True)
    target = destination / name
    shutil.copyfile(source, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (destination / "SHA256SUMS.txt").write_text(f"{digest}  {name}\n")
    signature = (Path(os.environ["RUNNER_TEMP"]) / "enforcedoze-apk-signature.txt").read_text()
    (destination / "BUILD-INFO.txt").write_text(
        f"APK: {name}\n"
        f"Application ID: {metadata['applicationId']}\n"
        f"Version: {apk['versionName']} ({apk['versionCode']})\n"
        f"Commit: {revision}\n"
        f"Ref: {os.environ['GITHUB_REF']}\n"
        f"Build: {os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}"
        f"/actions/runs/{os.environ['GITHUB_RUN_ID']}\n"
        f"Signing: {os.environ['APK_SIGNING']}\n"
        f"SHA-256: {digest}\n\n{signature}"
    )
    with Path(os.environ["GITHUB_OUTPUT"]).open("a") as output:
        output.write(f"apk_path={target.as_posix()}\napk_name={name}\napk_sha256={digest}\n")


if __name__ == "__main__":
    main()
