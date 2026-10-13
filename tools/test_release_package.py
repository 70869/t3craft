"""Release regressions: privacy, compatibility declarations, and corruption rejection."""
import importlib.util
import io
import json
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("release_package", Path(__file__).with_name("package-prism.py"))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


def jar(metadata):
    data = io.BytesIO()
    with zipfile.ZipFile(data, "w") as archive:
        archive.writestr("fabric.mod.json", json.dumps(metadata))
    return data.getvalue()


class ReleasePackageChecks(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.path = Path(self.temporary.name) / "test.zip"
        mod = jar({"id": "agentcraft", "version": "1.0.0", "depends": {
            "minecraft": "26.3", "fabricloader": ">=0.19.5", "java": ">=25", "fabric-api": ">=0.161.0+26.3"}})
        api = jar({"id": "fabric-api", "version": "0.161.0+26.3"})
        self.info = {"schemaVersion": 1, "minecraft": "26.3", "javaMinimum": 25, "t3Protocols": [1, 2],
                     "loader": {"id": "fabric", "version": "0.19.5"},
                     "mod": {"id": "agentcraft", "version": "1.0.0", "file": "t3craft-1.0.0.jar", "sha256": release.sha256(mod)},
                     "fabricApi": {"version": "0.161.0+26.3", "file": "fabric-api.jar", "sha256": release.sha256(api)}}
        self.files = {"instance.cfg": b"OverrideJavaLocation=false\nOverrideJavaArgs=true\nAutomaticJava=true\n",
                      "NOTES.txt": b"setup",
                      "mmc-pack.json": json.dumps({"formatVersion": 1, "components": [
                          {"uid": "net.minecraft", "version": "26.3"},
                          {"uid": "net.fabricmc.fabric-loader", "version": "0.19.5"}]}).encode(),
                      "minecraft/mods/t3craft-1.0.0.jar": mod, "minecraft/mods/fabric-api.jar": api}

    def write(self, extra=()):
        with zipfile.ZipFile(self.path, "w") as archive:
            archive.writestr("compatibility.json", json.dumps(self.info))
            for name, data in self.files.items():
                archive.writestr(name, data)
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                for name, data in extra:
                    archive.writestr(name, data)

    def test_valid_portable_package(self):
        self.write()
        self.assertEqual(release.validate_package(self.path)["javaMinimum"], 25)

    def add_world(self):
        names = ["level.dat", "agentcraft-anchors.json", "agentcraft-hq-plan.dat", "agentcraft-world.json",
                 "data/minecraft/world_gen_settings.dat", "dimensions/minecraft/overworld/region/r.0.0.mca"]
        self.world = {"schemaVersion": 1, "minecraft": "26.3", "worldName": "AgentCraft HQ", "layout": "modern",
                      "sourceSavedAt": "2026-10-08T13:47:50Z", "layoutRevision": 9,
                      "files": {name: release.sha256(b"clean fixture") for name in names}}
        for name in names:
            self.files[release.WORLD_PREFIX + name] = b"clean fixture"
        self.save_world_manifest()

    def save_world_manifest(self):
        data = json.dumps(self.world).encode()
        self.files["world-template.json"] = data
        self.info["worldTemplate"] = {"manifestSha256": release.sha256(data),
                                      "sourceSavedAt": self.world["sourceSavedAt"], "layoutRevision": self.world["layoutRevision"]}

    def test_clean_authored_world_is_packaged(self):
        self.add_world(); self.write()
        self.assertEqual(release.validate_package(self.path)["worldTemplate"]["layoutRevision"], 9)

    def test_world_checksum_and_provenance_rejected(self):
        self.add_world()
        self.files[release.WORLD_PREFIX + "level.dat"] = b"changed world"
        self.write()
        with self.assertRaisesRegex(ValueError, "checksum"):
            release.validate_package(self.path)
        self.files[release.WORLD_PREFIX + "level.dat"] = b"clean fixture"
        self.info["worldTemplate"]["layoutRevision"] = 8
        self.write()
        with self.assertRaisesRegex(ValueError, "provenance"):
            release.validate_package(self.path)

    def test_private_files_cannot_be_enrolled_in_world_manifest(self):
        self.add_world()
        for name in ("players/account.dat", "session.lock", "../level.dat", "config/t3craft.json",
                     "dimensions/minecraft/overworld/entities/r.0.0.mca"):
            with self.subTest(name=name):
                self.world["files"][name] = release.sha256(b"private fixture")
                self.save_world_manifest(); self.write()
                with self.assertRaisesRegex(ValueError, "private or unsafe"):
                    release.validate_package(self.path)
                del self.world["files"][name]

    def test_world_target_and_incomplete_template_rejected(self):
        self.add_world(); self.world["minecraft"] = "1.21.1"; self.save_world_manifest(); self.write()
        with self.assertRaisesRegex(ValueError, "target"):
            release.validate_package(self.path)
        self.world["minecraft"] = "26.3"; del self.world["files"]["agentcraft-hq-plan.dat"]
        self.save_world_manifest(); self.write()
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            release.validate_package(self.path)

    def test_later_authored_save_requires_a_new_export(self):
        source = Path(self.temporary.name) / "world"
        source.mkdir(); (source / "level.dat").write_bytes(b"latest authored save")
        hashes = {"level.dat": release.sha256(b"latest authored save")}
        manifest = {"sourceSha256": release.sha256(json.dumps(hashes, indent=2).encode())}
        release.verify_world_source(source, manifest)
        (source / "level.dat").write_bytes(b"newer authored save")
        with self.assertRaisesRegex(ValueError, "changed since export"):
            release.verify_world_source(source, manifest)

    def test_private_game_data_and_traversal_never_ship(self):
        for name in ("minecraft/config/t3craft.json", "minecraft/saves/world/level.dat", "accounts.json", "../escape"):
            with self.subTest(name=name):
                self.write([(name, b"synthetic")])
                with self.assertRaises(ValueError):
                    release.validate_package(self.path)

    def test_duplicate_entry_rejected(self):
        self.write([("NOTES.txt", b"duplicate")])
        with self.assertRaises(ValueError):
            release.validate_package(self.path)

    def test_dependency_mismatch_rejected_even_with_matching_checksum(self):
        data = jar({"id": "agentcraft", "version": "1.0.0", "depends": {"minecraft": "1.21.1"}})
        self.files["minecraft/mods/t3craft-1.0.0.jar"] = data
        self.info["mod"]["sha256"] = release.sha256(data)
        self.write()
        with self.assertRaisesRegex(ValueError, "dependencies"):
            release.validate_package(self.path)

    def test_corrupt_mod_rejected(self):
        self.files["minecraft/mods/t3craft-1.0.0.jar"] = b"corrupt"
        self.write()
        with self.assertRaisesRegex(ValueError, "checksum"):
            release.validate_package(self.path)

    def test_machine_specific_java_path_rejected(self):
        self.files["instance.cfg"] += b"JavaPath=C:/private/java.exe\n"
        self.write()
        with self.assertRaisesRegex(ValueError, "Java"):
            release.validate_package(self.path)

    def test_unverified_protocol_or_loader_rejected(self):
        self.info["t3Protocols"] = [3]
        self.write()
        with self.assertRaisesRegex(ValueError, "protocol"):
            release.validate_package(self.path)


if __name__ == "__main__":
    unittest.main()
