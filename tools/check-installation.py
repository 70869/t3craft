#!/usr/bin/env python3
"""Exercise the Windows release installer with disposable, synthetic Prism profiles."""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def snapshot(path):
    return {p.relative_to(path).as_posix(): digest(p) for p in path.rglob("*") if p.is_file()}


def synthetic_jar(path, mod_id):
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("fabric.mod.json", json.dumps({"id": mod_id, "version": "0.1.0"}))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--package", type=Path, required=True)
    args = parser.parse_args()
    if os.name != "nt":
        raise SystemExit("Installer checks require Windows; portable ZIP checks run on every OS.")
    shell = shutil.which("powershell.exe") or shutil.which("pwsh")
    if not shell:
        raise SystemExit("PowerShell is required.")
    package = args.package.resolve()
    with zipfile.ZipFile(package) as archive:
        info = json.loads(archive.read("compatibility.json"))
    (ROOT / ".local").mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="installation-qa-", dir=ROOT / ".local") as temporary:
        prism = Path(temporary) / "Prism with spaces"
        prism.mkdir()
        (prism / "prismlauncher.cfg").write_text("InstanceDir=custom instances\n")
        instance_id = f"T3Craft-{info['minecraft']}-Native"
        instance = prism / "custom instances" / instance_id

        def run(*extra, succeeds=True, script=None):
            command = [shell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
                       str(script or ROOT / "tools/install-release.ps1"), "-PrismData", str(prism),
                       "-PackagePath", str(package), "-ExpectedSha256", digest(package), *extra]
            environment = os.environ.copy()
            # PowerShell 7's module path cannot be inherited by Windows PowerShell 5.1.
            environment.pop("PSModulePath", None)
            result = subprocess.run(command, capture_output=True, text=True, timeout=120, env=environment)
            if (result.returncode == 0) != succeeds:
                raise AssertionError(f"Unexpected installer outcome ({result.returncode}): {result.stdout}\n{result.stderr}")
            return result

        run("-WhatIf")
        assert not instance.exists(), "WhatIf created an instance"
        # Exercise the published-feed path without contacting GitHub or real profiles.
        feed = Path(temporary) / "release-feed-check.ps1"
        feed.write_text("""param($PrismData, $PackagePath, $ExpectedSha256, [switch]$RejectOrigin)
$ErrorActionPreference = 'Stop'
$fixturePackage = $PackagePath
$fixtureHash = $ExpectedSha256
$origin = if ($RejectOrigin) { 'https://example.invalid' } else { 'https://github.com/70869/t3craft' }
function Invoke-RestMethod($Uri) {
    if ($Uri -ne 'https://api.github.com/repos/70869/t3craft/releases/latest') { throw 'Unexpected release API' }
    return @{draft=$false; prerelease=$false; assets=@(
        @{name='T3Craft-Prism.zip'; browser_download_url="$origin/releases/download/v1.1.0/T3Craft-Prism.zip"},
        @{name='SHA256SUMS.txt'; browser_download_url="$origin/releases/download/v1.1.0/SHA256SUMS.txt"})}
}
function Invoke-WebRequest($Uri, $OutFile, [switch]$UseBasicParsing) {
    if ($Uri.EndsWith('/T3Craft-Prism.zip')) { Copy-Item -LiteralPath $fixturePackage -Destination $OutFile }
    else { [IO.File]::WriteAllText($OutFile, "$fixtureHash  T3Craft-Prism.zip`n") }
}
& 'INSTALLER' -PrismData $PrismData -InstanceId 'ReleaseFeedQA' -NewInstance
""".replace("INSTALLER", str(ROOT / "tools/install-release.ps1").replace("'", "''")), encoding="utf-8")
        run("-RejectOrigin", succeeds=False, script=feed)
        assert not (prism / "custom instances/ReleaseFeedQA").exists()
        run(script=feed)
        assert digest(prism / "custom instances/ReleaseFeedQA/minecraft/mods" / info["mod"]["file"]) == info["mod"]["sha256"]
        run("-NewInstance")
        assert (instance / "minecraft/mods" / info["mod"]["file"]).is_file()
        assert (instance / "minecraft/config/t3craft-updater.json").is_file()
        if "worldTemplate" in info:
            with zipfile.ZipFile(package) as archive:
                manifest = json.loads(archive.read("world-template.json"))
            for name, expected in manifest["files"].items():
                assert digest(instance / "minecraft/saves/AgentCraft HQ" / name) == expected, "New install lost the authored template"
        # Imported instances can use .minecraft instead of minecraft.
        game = instance / ".minecraft"
        (instance / "minecraft").rename(game)
        for name in ("AgentCraft HQ", "Another world"):
            world = game / "saves" / name
            world.mkdir(parents=True, exist_ok=True)
            (world / "level.dat").write_bytes(b"synthetic world - never launched")
            (world / "session.lock").write_bytes(b"lock")
        (game / "config/t3craft.json").write_text('{"fixture":"synthetic-private-config"}')
        (game / "options.txt").write_text("guiScale:3\n")
        (game / "shaderpacks").mkdir()
        (game / "shaderpacks/keep.zip").write_bytes(b"synthetic shader sentinel")
        (game / "mods" / info["mod"]["file"]).rename(game / "mods/renamed-old-mod.jar")
        (game / "mods" / info["fabricApi"]["file"]).rename(game / "mods/renamed-old-api.jar")
        synthetic_jar(game / "mods/keep-other-mod.jar", "unrelated")
        before = snapshot(instance)
        run("-WhatIf")
        assert snapshot(instance) == before, "WhatIf changed an existing instance"
        run("-NewInstance", succeeds=False)
        assert snapshot(instance) == before, "NewInstance modified an existing profile"
        bad_checksum = [shell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
                        str(ROOT / "tools/install-release.ps1"), "-PrismData", str(prism),
                        "-PackagePath", str(package), "-ExpectedSha256", "0" * 64]
        environment = os.environ.copy()
        environment.pop("PSModulePath", None)
        assert subprocess.run(bad_checksum, capture_output=True, env=environment, timeout=60).returncode != 0
        assert snapshot(instance) == before, "Corrupt-checksum update changed files"

        old_mod = game / "mods/renamed-old-mod.jar"
        original_mod = old_mod.read_bytes()
        with zipfile.ZipFile(old_mod, "w") as archive:
            archive.writestr("fabric.mod.json", json.dumps({"id": "agentcraft", "version": "99.0.0"}))
        newer = snapshot(instance)
        run(succeeds=False)
        assert snapshot(instance) == newer, "Downgrade refusal changed files"
        assert not (prism / "backups").exists(), "Invalid update should be rejected before making a backup"
        old_mod.write_bytes(original_mod)

        original_pack = (instance / "mmc-pack.json").read_bytes()
        pack = json.loads(original_pack)
        pack["components"][0]["version"] = "1.21.1"
        (instance / "mmc-pack.json").write_text(json.dumps(pack))
        mismatched = snapshot(instance)
        run(succeeds=False)
        assert snapshot(instance) == mismatched, "An incompatible update changed files"
        (instance / "mmc-pack.json").write_bytes(original_pack)

        # Hold the world using the same OS file-sharing mechanism as Minecraft.
        hold = Path(temporary) / "locked-world-check.ps1"
        hold.write_text("""param($PrismData, $PackagePath, $ExpectedSha256)
$ErrorActionPreference = 'Stop'
$lockPath = Join-Path $PrismData 'custom instances/INSTANCE/.minecraft/saves/AgentCraft HQ/session.lock'
$lock = [IO.File]::Open($lockPath, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
try {
    & 'INSTALLER' -PrismData $PrismData -PackagePath $PackagePath -ExpectedSha256 $ExpectedSha256
} finally { $lock.Dispose() }
""".replace("INSTANCE", instance_id).replace("INSTALLER", str(ROOT / "tools/install-release.ps1").replace("'", "''")))
        run(succeeds=False, script=hold)
        assert snapshot(instance) == before, "Locked-world update changed files"

        run()
        after = snapshot(instance)
        for relative, expected in before.items():
            if relative.startswith((".minecraft/saves/", ".minecraft/shaderpacks/")) or relative in (
                    ".minecraft/options.txt", ".minecraft/config/t3craft.json", "instance.cfg", "mmc-pack.json",
                    ".minecraft/mods/keep-other-mod.jar"):
                assert after[relative] == expected, f"User data changed: {relative}"
        assert not (game / "mods/renamed-old-mod.jar").exists()
        assert not (game / "mods/renamed-old-api.jar").exists()
        assert digest(game / "mods" / info["mod"]["file"]) == info["mod"]["sha256"]
        backups = list((prism / "backups/t3craft" / instance_id).iterdir())
        assert len(backups) == 1
        for name in ("AgentCraft HQ", "Another world"):
            assert digest(backups[0] / "saves" / name / "level.dat") == digest(game / "saves" / name / "level.dat")
        assert digest(backups[0] / "mods/renamed-old-mod.jar") == info["mod"]["sha256"]
        installer = package.parent / "T3Craft-Windows-Installer.zip"
        if installer.is_file():
            bundle = Path(temporary) / "Standalone installer with spaces"
            with zipfile.ZipFile(installer) as archive:
                archive.extractall(bundle)
            standalone = bundle / "tools/install-release.ps1"
            environment = os.environ.copy(); environment.pop("PSModulePath", None)
            command = [shell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(standalone),
                       "-PrismData", str(prism), "-InstanceId", "BundledInstallerQA", "-NewInstance"]
            result = subprocess.run(command, capture_output=True, text=True, timeout=120, env=environment)
            assert result.returncode == 0, result.stdout + result.stderr
            installed = prism / "custom instances/BundledInstallerQA/minecraft"
            assert digest(installed / "mods" / info["mod"]["file"]) == info["mod"]["sha256"]
            with zipfile.ZipFile(package) as archive:
                assert digest(installed / "saves/AgentCraft HQ/level.dat") == hashlib.sha256(archive.read("minecraft/saves/AgentCraft HQ/level.dat")).hexdigest()
            original = snapshot(installed)
            with (bundle / "T3Craft-Prism.zip").open("ab") as corrupt:
                corrupt.write(b"corrupt fixture payload")
            result = subprocess.run(command, capture_output=True, text=True, timeout=120, env=environment)
            assert result.returncode != 0 and "checksum mismatch" in result.stderr.lower()
            assert snapshot(installed) == original, "Corrupt bundled installer changed its profile"
            print("Standalone installer passed: bundled payload, paths with spaces, exact authored world, and corruption refusal.")
        print("Installer passed: new import, custom paths, .minecraft, preview, checksum/lock/version/downgrade refusal, all-world backups, and preserved settings/mods.")


if __name__ == "__main__":
    main()
