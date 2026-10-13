#!/usr/bin/env python3
"""Check the source-checkout updater in a fresh Windows PowerShell 5.1 process."""
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import uuid
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


@contextmanager
def temporary_package(instance_id):
    directory = (ROOT / "artifacts/prism").resolve()
    paths = [directory / instance_id, directory / (instance_id + ".zip")]
    if any(path.exists() for path in paths):
        raise AssertionError("QA staging paths already exist")
    try:
        yield
    finally:
        for path in paths:
            if path.resolve().parent != directory:
                raise AssertionError("QA cleanup escaped its staging directory")
            if path.is_dir():
                shutil.rmtree(path)
            elif path.exists():
                path.unlink()


def snapshot(directory):
    return {p.relative_to(directory).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in directory.rglob("*") if p.is_file()}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--package", type=Path, required=True, help="Verified package supplying the cached Fabric API")
    args = parser.parse_args()
    shell = shutil.which("powershell.exe")
    if os.name != "nt" or not shell:
        raise SystemExit("This regression requires Windows PowerShell 5.1.")
    with zipfile.ZipFile(args.package) as archive:
        info = json.loads(archive.read("compatibility.json"))
        api = archive.read("minecraft/mods/" + info["fabricApi"]["file"])
        pack = archive.read("mmc-pack.json")
    assert hashlib.sha256(api).hexdigest() == info["fabricApi"]["sha256"]
    instance_id = "SourceUpdateQA-" + uuid.uuid4().hex
    (ROOT / ".local").mkdir(exist_ok=True)
    with temporary_package(instance_id), tempfile.TemporaryDirectory(prefix="source-update-qa-", dir=ROOT / ".local") as temporary:
        directory = Path(temporary)
        prism = directory / "Prism with spaces"
        game = prism / "custom instances" / instance_id / ".minecraft"
        world = game / "saves/AgentCraft HQ"
        world.mkdir(parents=True)
        (prism / "prismlauncher.cfg").write_text("InstanceDir=custom instances\n")
        (game.parent / "mmc-pack.json").write_bytes(pack)
        (game.parent / "instance.cfg").write_text("[General]\nname=Preserved custom name\n")
        for name, content in {"level.dat": b"synthetic world, never launched", "session.lock": b"lock",
                              "region/r.0.0.mca": b"authored blocks sentinel"}.items():
            path = world / name
            path.parent.mkdir(exist_ok=True)
            path.write_bytes(content)
        sentinels = {"config/t3craft.json": b'{"fixture":"private sentinel"}', "options.txt": b"guiScale:3\n",
                     "shaderpacks/keep.zip": b"shader sentinel", "mods/keep.jar": b"unrelated mod"}
        for name, content in sentinels.items():
            path = game / name
            path.parent.mkdir(exist_ok=True)
            path.write_bytes(content)
        old_mod = game / "mods/t3craft-0.1.0.jar"
        old_mod.write_bytes(b"old mod sentinel")
        before_world = snapshot(world)
        api_file = directory / "fabric-api.jar"
        api_file.write_bytes(api)
        harness = directory / "check.ps1"
        # Only the download is substituted; ZIP creation, install and verified backup are real.
        harness.write_text(r'''param($Repo, $Prism, $Instance, $Api)
$ErrorActionPreference = 'Stop'
function Invoke-WebRequest {
    param([switch]$UseBasicParsing, [string]$Uri, [string]$OutFile)
    if ($Uri -notlike 'https://maven.fabricmc.net/net/fabricmc/fabric-api/*') { throw 'Unexpected network request in source-update QA' }
    if ($OutFile) { Copy-Item -LiteralPath $Api -Destination $OutFile -Force }
    elseif ($Uri.EndsWith('.sha1')) { return @{ Content = (Get-FileHash -LiteralPath $Api -Algorithm SHA1).Hash.ToLowerInvariant() } }
    else { throw 'Unexpected download shape' }
}
& (Join-Path $Repo 'tools/update-hq.ps1') -PrismData $Prism -InstanceId $Instance -ModOnly
if (-not $?) { throw 'Source update failed' }
''', encoding="utf-8")
        environment = os.environ.copy()
        # Give the fresh 5.1 process its own modules, independent of the parent PowerShell 7 host.
        environment["PSModulePath"] = str(Path(os.environ["SystemRoot"]) / "System32/WindowsPowerShell/v1.0/Modules")
        print("Running disposable Windows source updater and build...", flush=True)
        result = subprocess.run([shell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
                                 str(harness), str(ROOT), str(prism), instance_id, str(api_file)],
                                capture_output=True, text=True, timeout=300, env=environment)
        print("Source updater returned; checking installed files and backups...", flush=True)
        if result.returncode:
            raise AssertionError(f"Source update failed ({result.returncode}):\n{result.stdout}\n{result.stderr}")
        assert snapshot(world) == before_world, "Source update changed the world or queued a rebuild"
        for name, content in sentinels.items():
            assert (game / name).read_bytes() == content, "Source update lost " + name
        assert (game.parent / "instance.cfg").read_text() == "[General]\nname=Preserved custom name\n"
        backups = list((prism / "backups/t3craft" / instance_id).iterdir())
        assert len(backups) == 1
        assert snapshot(backups[0] / "AgentCraft HQ") == before_world
        assert (backups[0] / "mods/t3craft-0.1.0.jar").read_bytes() == b"old mod sentinel"
        assert not old_mod.exists()
        assert (game / "mods" / info["fabricApi"]["file"]).read_bytes() == api
        props = dict(line.split("=", 1) for line in (ROOT / "mod/gradle.properties").read_text().splitlines()
                     if "=" in line and not line.startswith("#"))
        name = props["archives_base_name"] + "-" + props["mod_version"] + ".jar"
        assert (game / "mods" / name).read_bytes() == (ROOT / "mod/build/libs" / name).read_bytes()
        with zipfile.ZipFile(ROOT / "artifacts/prism" / (instance_id + ".zip")) as archive:
            assert len(archive.namelist()) == 6, "Source ZIP included stale staging data"
        print("PASS Windows PowerShell 5.1 source update: ZIP creation, verified backups, current mods, world/settings preservation")


if __name__ == "__main__":
    main()
