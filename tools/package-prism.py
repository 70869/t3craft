#!/usr/bin/env python3
"""Create the portable Prism Launcher import ZIP for a T3Craft release."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import urllib.request
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROPERTIES = ROOT / "mod" / "gradle.properties"


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=ROOT / "artifacts" / "prism")
    args = parser.parse_args()

    props = read_properties(PROPERTIES)
    version = props["mod_version"]
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise SystemExit(f"Release version must be stable semver, got {version!r}")
    archive_name = f"{props['archives_base_name']}-{version}.jar"
    mod_jar = ROOT / "mod" / "build" / "libs" / archive_name
    if not mod_jar.is_file():
        raise SystemExit(f"Build the mod first; missing {mod_jar}")

    api_version = props["fabric_api_version"]
    api_name = f"fabric-api-{api_version}.jar"
    api_url = f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{api_version}/{api_name}"
    with urllib.request.urlopen(api_url, timeout=60) as response:
        api_bytes = response.read()
    with urllib.request.urlopen(api_url + ".sha1", timeout=30) as response:
        expected_sha1 = response.read().decode("ascii").strip().split()[0].lower()
    actual_sha1 = hashlib.sha1(api_bytes).hexdigest()
    if actual_sha1 != expected_sha1:
        raise SystemExit(f"Fabric API SHA-1 mismatch: expected {expected_sha1}, got {actual_sha1}")

    instance_id = "T3Craft-26.3-Native"
    pack = {
        "formatVersion": 1,
        "components": [
            {"uid": "net.minecraft", "version": props["minecraft_version"], "important": True},
            {"uid": "net.fabricmc.fabric-loader", "version": props["loader_version"]},
        ],
    }
    instance_cfg = """[General]
ConfigVersion=1.2
InstanceType=OneSix
name=T3Craft 26.3 Native
iconKey=crafting_table
OverrideJavaLocation=false
OverrideJavaArgs=true
AutomaticJava=true
JvmArgs=-Djava.net.preferIPv4Stack=true -Dagentcraft.dev=0 -Dagentcraft.mute=0 -Dagentcraft.focus=1 -Dagentcraft.autoworld=1
OverrideMemory=true
MinMemAlloc=1024
MaxMemAlloc=4096
"""
    notes = (
        "T3Craft 1.0.0 for Minecraft 26.3. Import this ZIP in Prism Launcher. "
        "Sign in to your Microsoft account, launch the instance, and press ` to open Connections. "
        "Pair with a running T3 Code app. Java 25+ is required; Prism can manage the runtime. "
        "See https://github.com/70869/agentcraft/blob/main/docs/PRISM.md for setup and compatibility.\n"
    )

    args.output_dir.mkdir(parents=True, exist_ok=True)
    output = args.output_dir / f"{instance_id}.zip"
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("mmc-pack.json", json.dumps(pack, indent=2) + "\n")
        archive.writestr("instance.cfg", instance_cfg)
        archive.writestr("NOTES.txt", notes)
        archive.write(mod_jar, f"minecraft/mods/{archive_name}")
        archive.writestr(f"minecraft/mods/{api_name}", api_bytes)
    print(output)


if __name__ == "__main__":
    main()
