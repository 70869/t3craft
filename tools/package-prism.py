#!/usr/bin/env python3
"""Package releases with the reviewed clean world template; never copy a local profile."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import shutil
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = "https://github.com/70869/t3craft"
WORLD_PREFIX = "minecraft/saves/AgentCraft HQ/"


def world_file_allowed(name: str) -> bool:
    return (name in {"level.dat", "agentcraft-anchors.json", "agentcraft-hq-plan.dat", "agentcraft-world.json",
                    "data/fabric/registry_entries.dat", "data/minecraft/game_rules.dat", "data/minecraft/weather.dat",
                    "data/minecraft/world_clocks.dat", "data/minecraft/world_gen_settings.dat"}
            or bool(re.fullmatch(r"dimensions/minecraft/(overworld|the_nether|the_end)/data/minecraft/world_border\.dat", name))
            or bool(re.fullmatch(r"dimensions/minecraft/(overworld|the_nether|the_end)/(region|poi)/r\.-?\d+\.-?\d+\.mca", name)))


def validate_world_manifest(manifest: dict, minecraft: str) -> None:
    if (manifest.get("schemaVersion") != 1 or manifest.get("minecraft") != minecraft
            or manifest.get("worldName") != "AgentCraft HQ" or manifest.get("layout") != "modern"):
        raise ValueError("World template disagrees with the release target")
    files = manifest.get("files", {})
    required = {"level.dat", "agentcraft-anchors.json", "agentcraft-hq-plan.dat", "agentcraft-world.json",
                "data/minecraft/world_gen_settings.dat"}
    if not required <= files.keys() or not any("/region/" in name for name in files):
        raise ValueError("Incomplete world template")
    if any(not world_file_allowed(name) or not re.fullmatch(r"[0-9a-f]{64}", checksum) for name, checksum in files.items()):
        raise ValueError("World template contains private or unsafe files")


def load_world_template(minecraft: str) -> tuple[dict, bytes, dict[str, bytes]]:
    directory = ROOT / "release"
    manifest_bytes = (directory / "world-template.json").read_bytes()
    manifest = json.loads(manifest_bytes)
    validate_world_manifest(manifest, minecraft)
    source = (directory / "world-template.zip").read_bytes()
    if sha256(source) != manifest["sha256"]:
        raise ValueError("World template checksum mismatch; export the latest closed world")
    with zipfile.ZipFile(io.BytesIO(source)) as archive:
        names = archive.namelist()
        if len(names) != len(manifest["files"]) or set(names) != set(manifest["files"]):
            raise ValueError("World template files disagree with its manifest")
        files = {name: archive.read(name) for name in names}
    if any(sha256(data) != manifest["files"][name] for name, data in files.items()):
        raise ValueError("World template file checksum mismatch")
    return manifest, manifest_bytes, files


def verify_world_source(source: Path, manifest: dict) -> None:
    """Refuse a release using an older capture when preparing it beside the author's save."""
    hashes = {p.relative_to(source).as_posix(): sha256(p.read_bytes()) for p in sorted(source.rglob("*"))
              if p.is_file() and world_file_allowed(p.relative_to(source).as_posix())}
    fingerprint = sha256(json.dumps(hashes, indent=2).encode("utf-8"))
    if fingerprint != manifest["sourceSha256"]:
        raise ValueError("Saved source world changed since export; run worldTemplateExport before packaging the release")


def read_properties(path: Path) -> dict[str, str]:
    return dict(line.strip().split("=", 1) for line in path.read_text(encoding="utf-8").splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def jar_metadata(data: bytes) -> dict:
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        return json.loads(jar.read("fabric.mod.json"))


def validate_package(path: Path) -> dict:
    """Only an explicit file allowlist may ship; validate the actual bundled JARs."""
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        info = json.loads(archive.read("compatibility.json"))
        if info.get("schemaVersion") != 1 or info["mod"].get("id") != "agentcraft":
            raise ValueError("Unknown package schema")
        if any(not re.fullmatch(r"[A-Za-z0-9_.+\-]+\.jar", info[key]["file"]) for key in ("mod", "fabricApi")):
            raise ValueError("Unsafe bundled filename")
        mod_name = f"minecraft/mods/{info['mod']['file']}"
        api_name = f"minecraft/mods/{info['fabricApi']['file']}"
        allowed = {"mmc-pack.json", "instance.cfg", "NOTES.txt", "compatibility.json", mod_name, api_name}
        if "worldTemplate" in info:
            manifest_bytes = archive.read("world-template.json")
            if sha256(manifest_bytes) != info["worldTemplate"]["manifestSha256"]:
                raise ValueError("World manifest checksum mismatch")
            manifest = json.loads(manifest_bytes)
            validate_world_manifest(manifest, info["minecraft"])
            if any(info["worldTemplate"][key] != manifest[key] for key in ("sourceSavedAt", "layoutRevision")):
                raise ValueError("World template provenance disagrees with compatibility metadata")
            allowed.add("world-template.json")
            for name, checksum in manifest["files"].items():
                entry = WORLD_PREFIX + name
                allowed.add(entry)
                if sha256(archive.read(entry)) != checksum:
                    raise ValueError("World template file checksum mismatch")
        if len(names) != len(allowed) or set(names) != allowed or any(".." in n or "\\" in n for n in names):
            raise ValueError("Package contains unexpected, duplicate, or unsafe files")
        pack = json.loads(archive.read("mmc-pack.json"))
        components = {c["uid"]: c["version"] for c in pack["components"]}
        if pack.get("formatVersion") != 1 or components != {
            "net.minecraft": info["minecraft"], "net.fabricmc.fabric-loader": info["loader"]["version"]
        } or len(pack["components"]) != 2:
            raise ValueError("Prism components disagree with compatibility metadata")
        values = dict(line.split("=", 1) for line in archive.read("instance.cfg").decode("utf-8").splitlines() if "=" in line)
        if ("JavaPath" in values or values.get("OverrideJavaLocation") != "false"
                or values.get("AutomaticJava") != "true" or values.get("OverrideJavaArgs") != "true"):
            raise ValueError("Package must use Prism-managed Java without a local path")
        if info["loader"]["id"] != "fabric" or info["t3Protocols"] != [1, 2]:
            raise ValueError("Unsupported loader or protocol declaration")
        for key, entry in (("mod", mod_name), ("fabricApi", api_name)):
            data = archive.read(entry)
            if sha256(data) != info[key]["sha256"]:
                raise ValueError(f"Bundled {key} checksum mismatch")
            metadata = jar_metadata(data)
            expected_id = "agentcraft" if key == "mod" else "fabric-api"
            if metadata["id"] != expected_id or metadata["version"] != info[key]["version"]:
                raise ValueError(f"Bundled {key} version does not match the package")
            if key == "mod":
                dependencies = metadata["depends"]
                expected = {"minecraft": info["minecraft"], "fabricloader": f">={info['loader']['version']}",
                            "java": f">={info['javaMinimum']}", "fabric-api": f">={info['fabricApi']['version']}"}
                if any(dependencies.get(k) != v for k, v in expected.items()):
                    raise ValueError("Mod dependencies disagree with the release target; rebuild the JAR")
        return info


def package(output_dir: Path, api_jar: Path | None = None, world_source: Path | None = None) -> Path:
    props = read_properties(ROOT / "mod/gradle.properties")
    version, minecraft = props["mod_version"], props["minecraft_version"]
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Release version must be stable semver")
    mod_name = f"{props['archives_base_name']}-{version}.jar"
    mod_bytes = (ROOT / "mod/build/libs" / mod_name).read_bytes()
    world, world_manifest, world_files = load_world_template(minecraft)
    if world_source:
        verify_world_source(world_source, world)
    api_version = props["fabric_api_version"]
    api_name = f"fabric-api-{api_version}.jar"
    api_url = f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{api_version}/{api_name}"
    if api_jar:
        api_bytes = api_jar.read_bytes()
    else:
        with urllib.request.urlopen(api_url, timeout=60) as response:
            api_bytes = response.read()
        with urllib.request.urlopen(api_url + ".sha1", timeout=30) as response:
            expected = response.read().decode("ascii").strip().split()[0].lower()
        if hashlib.sha1(api_bytes).hexdigest() != expected:
            raise ValueError("Fabric API download checksum mismatch")
    java = int(props["java_version"])
    info = {"schemaVersion": 1, "minecraft": minecraft, "javaMinimum": java,
            "loader": {"id": "fabric", "version": props["loader_version"]}, "t3Protocols": [1, 2],
            "mod": {"id": "agentcraft", "version": version, "file": mod_name, "sha256": sha256(mod_bytes)},
            "fabricApi": {"version": api_version, "file": api_name, "sha256": sha256(api_bytes)}}
    info["worldTemplate"] = {"manifestSha256": sha256(world_manifest), "sourceSavedAt": world["sourceSavedAt"],
                             "layoutRevision": world["layoutRevision"]}
    pack = {"formatVersion": 1, "components": [
        {"uid": "net.minecraft", "version": minecraft, "important": True},
        {"uid": "net.fabricmc.fabric-loader", "version": props["loader_version"]}]}
    settings = f"""[General]
ConfigVersion=1.2
InstanceType=OneSix
name=T3Craft {minecraft} Native
iconKey=crafting_table
OverrideJavaLocation=false
OverrideJavaArgs=true
AutomaticJava=true
JvmArgs=-Djava.net.preferIPv4Stack=true -Dagentcraft.dev=0 -Dagentcraft.mute=0 -Dagentcraft.focus=1 -Dagentcraft.autoworld=1
OverrideMemory=true
MinMemAlloc=1024
MaxMemAlloc=4096
"""
    notes = (f"T3Craft {version} / Minecraft {minecraft} / Fabric {props['loader_version']} / Java {java}+\n\n"
             "NEW INSTALL: In Prism, Add Instance > Import this ZIP, sign in to your Minecraft account, then launch.\n"
             "Prism manages Java when supported by your launcher package. Otherwise select the Java version above.\n"
             "In Prism Settings > Java, enable Autodetect Java version and Auto-download Mojang Java.\n"
             "FIRST LAUNCH: Keep T3 Code running. Connections opens automatically; paste a link from T3 Settings > Connections.\n"
             f"WORLD: Includes the clean authored campus saved {world['sourceSavedAt']} (layout revision {world['layoutRevision']}).\n"
             "UPDATE: Close Minecraft and back up your existing instance. Keep its worlds and settings; replace only the mod and Fabric API.\n"
             "Do not import over an existing instance or downgrade a saved world to another Minecraft version.\n"
             f"Install and recovery guide: {REPOSITORY}/blob/t3craft/docs/PRISM.md\n")
    output_dir.mkdir(parents=True, exist_ok=True)
    output = output_dir / f"T3Craft-{version}-mc{minecraft}-fabric.zip"
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("mmc-pack.json", json.dumps(pack, indent=2) + "\n")
        archive.writestr("instance.cfg", settings)
        archive.writestr("NOTES.txt", notes)
        archive.writestr("compatibility.json", json.dumps(info, indent=2) + "\n")
        archive.writestr(f"minecraft/mods/{mod_name}", mod_bytes)
        archive.writestr(f"minecraft/mods/{api_name}", api_bytes)
        archive.writestr("world-template.json", world_manifest)
        for name, data in world_files.items():
            archive.writestr(WORLD_PREFIX + name, data)
    validate_package(output)
    release_files = [output]
    for name in ("T3Craft-Prism.zip", f"T3Craft-{minecraft}-Native.zip"):
        shutil.copyfile(output, output_dir / name)
        release_files.append(output_dir / name)
    (output_dir / "compatibility.json").write_text(json.dumps(info, indent=2) + "\n", encoding="utf-8")
    installer = output_dir / "T3Craft-Windows-Installer.zip"
    with zipfile.ZipFile(installer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(ROOT / "Install-T3Craft.cmd", "Install-T3Craft.cmd")
        archive.write(output_dir / "T3Craft-Prism.zip", "T3Craft-Prism.zip")
        archive.writestr("SHA256SUMS.txt", f"{sha256(output.read_bytes())}  T3Craft-Prism.zip\n")
        archive.writestr("INSTALL.txt", f"T3Craft {version}\n\nInstall Prism Launcher from https://prismlauncher.org/download/ and sign into your Minecraft account.\nExtract this ZIP and double-click Install-T3Craft.cmd. The bundled package is verified before installation.\nNew installations receive the latest authored campus. Updates preserve your worlds and settings.\nPrism downloads Minecraft and Java 25 when needed; T3 Code must run separately.\n")
        for name in ("install-release.ps1", "update-release-after-close.ps1"):
            archive.write(ROOT / "tools" / name, f"tools/{name}")
    release_files.append(installer)
    checksums = [f"{sha256(p.read_bytes())}  {p.name}" for p in sorted(release_files)]
    checksums += [f"{sha256(mod_bytes)}  {mod_name}", f"{sha256((output_dir / 'compatibility.json').read_bytes())}  compatibility.json"]
    (output_dir / "SHA256SUMS.txt").write_text("\n".join(checksums) + "\n", encoding="utf-8")
    if world_source:
        verify_world_source(world_source, world)
    return output


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=ROOT / "artifacts/prism")
    parser.add_argument("--api-jar", type=Path, help="Use a downloaded Fabric API JAR; its metadata is still validated")
    parser.add_argument("--validate-only", type=Path)
    parser.add_argument("--verify-world-source", type=Path, help="Refuse an outdated template compared with this closed authored save")
    args = parser.parse_args()
    if args.validate_only:
        info = validate_package(args.validate_only)
        print(f"Validated T3Craft {info['mod']['version']} / Minecraft {info['minecraft']} / Fabric / Java {info['javaMinimum']}+")
    else:
        print(package(args.output_dir, args.api_jar, args.verify_world_source))


if __name__ == "__main__":
    main()
