#!/usr/bin/env python3
"""Create a client Modrinth pack from the validated, privacy-safe Prism release."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("prism_package", ROOT / "tools/package-prism.py")
prism = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prism)


def package(source: Path) -> Path:
    info = prism.validate_package(source)
    with zipfile.ZipFile(source) as archive:
        api_path = "minecraft/mods/" + info["fabricApi"]["file"]
        api_bytes = archive.read(api_path)
        api_hash = hashlib.sha512(api_bytes).hexdigest()
        request = urllib.request.Request(
            f"https://api.modrinth.com/v2/version_file/{api_hash}?algorithm=sha512",
            headers={"User-Agent": "70869/T3Craft-release (https://github.com/70869/t3craft)"})
        with urllib.request.urlopen(request, timeout=30) as response:
            api_version = json.load(response)
        api_file = next(f for f in api_version["files"] if f["hashes"]["sha512"] == api_hash)
        if not api_file["url"].startswith("https://cdn.modrinth.com/"):
            raise ValueError("Fabric API download must use the Modrinth CDN")
        if info["minecraft"] not in api_version["game_versions"]:
            raise ValueError("Fabric API does not support the release Minecraft version")
        files = []
        version = info["mod"]["version"]
        for key, url in (("mod", f"{prism.REPOSITORY}/releases/download/v{version}/{info['mod']['file']}"),
                         ("fabricApi", api_file["url"])):
            name = info[key]["file"]
            data = archive.read("minecraft/mods/" + name)
            files.append({"path": "mods/" + name,
                          "hashes": {algorithm: hashlib.new(algorithm, data).hexdigest()
                                     for algorithm in ("sha1", "sha512")},
                          "env": {"client": "required", "server": "unsupported"},
                          "downloads": [url], "fileSize": len(data)})
        index = {"formatVersion": 1, "game": "minecraft", "versionId": version,
                 "name": "T3Craft", "summary": "Your real T3 Code workspace inside an authored Minecraft campus.",
                 "files": files, "dependencies": {"minecraft": info["minecraft"],
                                                   "fabric-loader": info["loader"]["version"]}}
        output = source.parent / f"T3Craft-{version}.mrpack"
        with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as pack:
            pack.writestr("modrinth.index.json", json.dumps(index, indent=2) + "\n")
            for name in archive.namelist():
                if name.startswith(prism.WORLD_PREFIX):
                    pack.writestr("client-overrides/" + name.removeprefix("minecraft/"), archive.read(name))
            pack.writestr("client-overrides/t3craft-world-template.json", archive.read("world-template.json"))
            for name in ("LICENSE", "THIRD-PARTY-NOTICES.md"):
                pack.writestr("client-overrides/t3craft-notices/" + name, (ROOT / name).read_bytes())
        with zipfile.ZipFile(output) as pack:
            for name in archive.namelist():
                if name.startswith(prism.WORLD_PREFIX):
                    if pack.read("client-overrides/" + name.removeprefix("minecraft/")) != archive.read(name):
                        raise ValueError("Modrinth world differs from the verified Prism world")
        checksum_file = source.parent / "SHA256SUMS.txt"
        lines = [line for line in checksum_file.read_text().splitlines() if not line.endswith("  " + output.name)]
        lines.append(f"{prism.sha256(output.read_bytes())}  {output.name}")
        checksum_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path, help="Validated T3Craft-Prism.zip")
    args = parser.parse_args()
    print(package(args.package))
