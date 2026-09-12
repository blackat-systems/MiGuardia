"""Synthetic metadata/ZIP fixtures only; no SDK, Gradle, device or network required."""
from __future__ import annotations

import copy
import contextlib
import importlib.util
import json
import io
from pathlib import Path
import sys
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "inspect_apk.py"
spec = importlib.util.spec_from_file_location("release_inspection", SCRIPT)
release = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = release
spec.loader.exec_module(release)
POLICY = release.load_policy()


def manifest_fixture() -> str:
    lines = ["N: android=http://schemas.android.com/apk/res/android (line=2)"]
    def element(indent, tag):
        lines.append(" " * indent + f"E: {tag} (line=2)")
    def attribute(indent, key, value):
        prefix = release.ANDROID if key.startswith("android:") else ""
        key = key.removeprefix("android:")
        if isinstance(value, str):
            value = json.dumps(value)
        elif type(value) is bool:
            value = str(value).lower()
        lines.append(" " * indent + f"A: {prefix}{key}={value}")
    element(2, "manifest")
    for key, value in {"package": POLICY["package"], "android:versionCode": POLICY["version_code"],
                       "android:versionName": POLICY["version_name"], "android:compileSdkVersion": 37}.items():
        attribute(4, key, value)
    element(6, "uses-sdk")
    attribute(8, "android:minSdkVersion", 26)
    attribute(8, "android:targetSdkVersion", 37)
    for permission in POLICY["permissions"]:
        element(6, "uses-permission")
        attribute(8, "android:name", permission)
    for permission in POLICY["declared_permissions"]:
        element(6, "permission")
        attribute(8, "android:name", permission["name"])
        attribute(8, "android:protectionLevel", permission["protection_level"])
    element(6, "application")
    for key in ("allowBackup", "usesCleartextTraffic", "extractNativeLibs"):
        attribute(8, "android:" + key, False)
    for component in POLICY["components"]:
        element(10, component["kind"])
        for key in ("name", "exported", "permission", "authorities"):
            if component[key] is not None:
                attribute(12, "android:" + key, component[key])
    return "\n".join(lines)


BADGING = f"""package: name='{POLICY["package"]}' versionCode='{POLICY["version_code"]}' versionName='{POLICY["version_name"]}' compileSdkVersion='37'
minSdkVersion:'26'
targetSdkVersion:'37'
native-code: 'arm64-v8a' 'armeabi-v7a' 'x86' 'x86_64'
"""


class ManifestTests(unittest.TestCase):
    def setUp(self):
        self.tree = release.parse_xmltree(manifest_fixture())
        self.metadata = release.manifest_metadata(self.tree)
        self.badging = release.parse_badging(BADGING)

    def test_exact_release_and_baseline_transitives_pass(self):
        self.assertEqual([], release.validate_metadata(self.metadata, self.badging, POLICY))
        self.assertEqual(8, len(self.metadata["permissions"]))
        self.assertEqual(9, len(self.metadata["components"]))
        self.assertEqual(POLICY["declared_permissions"], self.metadata["declared_permissions"])

    def test_raw_string_resource_ids_and_hex_boolean(self):
        self.assertEqual("example", release.parse_value('"example" (Raw: "example")'))
        self.assertTrue(release.bool_attribute({"android:exported": 0xffffffff}, "exported"))
        text = manifest_fixture().replace("android:exported=true", "android:exported(0x01010010)=0xffffffff")
        self.assertEqual(self.metadata, release.manifest_metadata(release.parse_xmltree(text)))

    def test_rejects_wrong_identity_and_qa(self):
        for key, value in (("package", POLICY["package"] + ".qa"), ("version_code", 1), ("version_name", "1.0.0"),
                           ("min_sdk", 27), ("target_sdk", 36), ("compile_sdk", 36)):
            with self.subTest(key=key):
                changed = {**self.metadata, key: value}
                self.assertIn("identity:" + key, release.validate_metadata(changed, self.badging, POLICY))

    def test_badging_must_agree(self):
        self.badging["version_code"] = POLICY["version_code"] + 1
        self.assertIn("identity:version_code", release.validate_metadata(self.metadata, self.badging, POLICY))

    def test_rejects_debug_test_backup_cleartext_extract_and_instrumentation(self):
        for key in ("version_code_major", "debuggable", "test_only", "allow_backup", "cleartext", "extract_native_libs", "instrumentation_count"):
            with self.subTest(key=key):
                self.assertIn("manifest:" + key, release.validate_metadata({**self.metadata, key: 1}, self.badging, POLICY))

    def test_rejects_missing_exported_and_unresolved_boolean(self):
        application = release.direct(self.tree, "application")[0]
        del application["children"][0]["attributes"]["android:exported"]
        with self.assertRaises(release.ReleaseError):
            release.manifest_metadata(self.tree)
        with self.assertRaises(release.ReleaseError):
            release.bool_attribute({"android:debuggable": "@0x7f000001"}, "debuggable", False)

    def test_rejects_new_permission_or_duplicates(self):
        for permission in ("android.permission.REQUEST_INSTALL_PACKAGES",
                           "android.permission.ACCESS_NETWORK_STATE", POLICY["permissions"][0]):
            changed = {**self.metadata, "permissions": self.metadata["permissions"] + [permission]}
            self.assertIn("manifest:permissions", release.validate_metadata(changed, self.badging, POLICY))

    def test_rejects_absent_internal_permission_declaration(self):
        self.tree["children"] = [child for child in self.tree["children"] if child["tag"] != "permission"]
        changed = release.manifest_metadata(self.tree)
        self.assertIn("manifest:declared-permissions", release.validate_metadata(changed, self.badging, POLICY))

    def test_rejects_normal_or_implicit_normal_internal_permission(self):
        for explicit in (True, False):
            tree = copy.deepcopy(self.tree)
            values = release.direct(tree, "permission")[0]["attributes"]
            if explicit:
                values["android:protectionLevel"] = 0
            else:
                del values["android:protectionLevel"]
            changed = release.manifest_metadata(tree)
            self.assertIn("manifest:declared-permissions", release.validate_metadata(changed, self.badging, POLICY))

    def test_rejects_added_or_duplicated_permission_declaration(self):
        for name in ("example.EXTRA", POLICY["declared_permissions"][0]["name"]):
            tree = copy.deepcopy(self.tree)
            extra = copy.deepcopy(release.direct(tree, "permission")[0])
            extra["attributes"]["android:name"] = name
            tree["children"].append(extra)
            changed = release.manifest_metadata(tree)
            self.assertIn("manifest:declared-permissions", release.validate_metadata(changed, self.badging, POLICY))

    def test_rejects_changed_application_permission_and_inherited_component_permission(self):
        application = release.direct(self.tree, "application")[0]
        application["attributes"]["android:permission"] = "example.ADDED"
        changed = release.manifest_metadata(self.tree)
        self.assertEqual("example.ADDED", changed["components"][0]["permission"])
        errors = release.validate_metadata(changed, self.badging, POLICY)
        self.assertIn("manifest:application-permission", errors)
        self.assertIn("manifest:components", errors)

    def test_rejects_new_component_and_changed_export_or_protection(self):
        for operation in ("extra", "export", "permission"):
            changed = copy.deepcopy(self.metadata)
            if operation == "extra":
                changed["components"].append({"kind": "service", "name": "example.Bad", "exported": False,
                                              "permission": None, "authorities": None})
            elif operation == "export":
                changed["components"][2]["exported"] = True
            else:
                changed["components"][-1]["permission"] = None
            self.assertIn("manifest:components", release.validate_metadata(changed, self.badging, POLICY))

    def test_rejects_every_split_indicator(self):
        for key in ("split", "android:isFeatureSplit", "android:isSplitRequired", "android:configForSplit",
                    "android:requiredSplitTypes", "android:splitTypes"):
            with self.subTest(key=key):
                tree = copy.deepcopy(self.tree)
                tree["attributes"][key] = "anything"
                changed = release.manifest_metadata(tree)
                self.assertIn("manifest:splits", release.validate_metadata(changed, self.badging, POLICY))
        self.tree["children"].append({"tag": "uses-split", "attributes": {}, "children": []})
        self.assertIn("uses-split", release.manifest_metadata(self.tree)["splits"])

    def test_rejects_missing_or_extra_abi(self):
        for abis in (POLICY["abis"][:-1], POLICY["abis"] + ["riscv64"]):
            self.assertIn("badging:abis", release.validate_metadata(self.metadata, {**self.badging, "abis": abis}, POLICY))

    def test_parser_rejects_duplicate_attribute_unknown_output_and_roots(self):
        for text in (manifest_fixture() + "\n            A: android:exported=true\n            A: android:exported=false",
                     "unexpected output", manifest_fixture() + "\nE: manifest (line=200)"):
            with self.assertRaises(release.ReleaseError):
                release.parse_xmltree(text)

    def test_badging_requires_one_identity_and_numeric_sdks(self):
        for text in (BADGING + BADGING, BADGING.replace("'26'", "'Preview'"), ""):
            with self.assertRaises(release.ReleaseError):
                release.parse_badging(text)


class SignatureTests(unittest.TestCase):
    def test_exact_unsigned_rejection_expected(self):
        self.assertEqual("EXPECTED_UNSIGNED_REJECTION", release.unsigned_result(1,
            "WARNING: harmless Java warning\nDOES NOT VERIFY\nERROR: Missing META-INF/MANIFEST.MF\n"))

    def test_valid_signature_is_forbidden_in_this_stage(self):
        self.assertEqual("UNEXPECTED_VALID_SIGNATURE", release.unsigned_result(0, "Verifies"))

    def test_arbitrary_error_invalid_signature_or_tool_failure_not_unsigned(self):
        for code, text in ((1, ""), (1, "Unable to access jarfile"), (2, "DOES NOT VERIFY\nERROR: Missing META-INF/MANIFEST.MF"),
                           (1, "DOES NOT VERIFY\nERROR: Missing META-INF/MANIFEST.MF\nERROR: signature invalid")):
            self.assertEqual("UNEXPECTED_VERIFIER_FAILURE", release.unsigned_result(code, text))


class ZipTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)

    def write_zip(self, name="metadata.zip", entries=None, year=2026, compression=zipfile.ZIP_STORED):
        path = self.folder / name
        if entries is None:
            entries = {"AndroidManifest.xml": b"synthetic manifest", "classes.dex": b"synthetic classes", "resources.arsc": b"synthetic resources"}
            entries.update({f"lib/{abi}/{library}": b"synthetic native metadata" for abi in POLICY["abis"] for library in POLICY["native_libraries"]})
        with zipfile.ZipFile(path, "w") as archive:
            for filename, data in entries.items():
                info = zipfile.ZipInfo(filename, date_time=(year, 1, 1, 0, 0, 0))
                info.filename = filename  # Preserve deliberately invalid names on Windows too.
                info.compress_type = compression
                archive.writestr(info, data)
        return path

    def inventory(self, path):
        return release.canonical_zip(path, POLICY["max_apk_bytes"])

    def test_exact_library_names_and_four_abis_pass(self):
        inventory = self.inventory(self.write_zip())
        self.assertEqual([], release.validate_zip(inventory, POLICY))

    def test_rejects_incomplete_or_extra_native_libraries(self):
        inventory = self.inventory(self.write_zip())
        inventory["entries"] = [item for item in inventory["entries"] if item["name"] != "lib/x86/libandroidx.graphics.path.so"]
        self.assertIn("zip:native-library-set", release.validate_zip(inventory, POLICY))
        inventory["entries"].append({"name": "lib/x86/unexpected.so", "size": 10, "compression": 0})
        self.assertIn("zip:native-library-set", release.validate_zip(inventory, POLICY))

    def test_rejects_compressed_native_libraries(self):
        inventory = self.inventory(self.write_zip(compression=zipfile.ZIP_DEFLATED))
        self.assertIn("zip:native-library-storage", release.validate_zip(inventory, POLICY))

    def test_rejects_bundle_incomplete_and_secret_file_names(self):
        for filename, expected in (("base.apk", "zip:nested-apk-or-bundle"), ("BundleConfig.pb", "zip:nested-apk-or-bundle"),
                                   ("assets/private.pem", "zip:forbidden-secret-file-name"), ("assets/.env", "zip:forbidden-secret-file-name")):
            inventory = self.inventory(self.write_zip(entries={filename: b"synthetic"}))
            self.assertIn(expected, release.validate_zip(inventory, POLICY))
            self.assertIn("zip:incomplete-apk", release.validate_zip(inventory, POLICY))

    def test_rejects_v1_or_v2_signing_material(self):
        inventory = self.inventory(self.write_zip(entries={"META-INF/CERT.RSA": b"synthetic"}))
        self.assertIn("zip:unexpected-signing-material", release.validate_zip(inventory, POLICY))
        inventory = self.inventory(self.write_zip())
        inventory["signing_block_present"] = True
        self.assertIn("zip:unexpected-signing-material", release.validate_zip(inventory, POLICY))

    def test_rejects_unsafe_paths_and_absolute_paths(self):
        for name in ("../escape", "/absolute", "C:/absolute", "nested\\escape", "a//b", "a/./b"):
            with self.subTest(name=name), self.assertRaises(release.ReleaseError):
                self.inventory(self.write_zip(entries={name: b"synthetic"}))

    def test_rejects_duplicate_entries(self):
        path = self.write_zip(entries={"same": b"first"})
        with self.assertWarns(UserWarning), zipfile.ZipFile(path, "a") as archive:
            archive.writestr("same", b"second")
        with self.assertRaises(release.ReleaseError):
            self.inventory(path)

    def test_rejects_size_and_count_limits(self):
        path = self.write_zip()
        with self.assertRaises(release.ReleaseError):
            release.canonical_zip(path, 1)
        with patch.object(release, "MAX_ENTRIES", 1), self.assertRaises(release.ReleaseError):
            self.inventory(path)
        with patch.object(release, "MAX_EXPANDED_BYTES", 1), self.assertRaises(release.ReleaseError):
            self.inventory(path)

    def test_identical_and_timestamp_only_difference(self):
        first = self.write_zip("first.zip")
        second = self.write_zip("second.zip", year=2025)
        self.assertEqual("BYTE_IDENTICAL", release.compare(first, first, POLICY)["verdict"])
        compared = release.compare(first, second, POLICY)
        self.assertEqual("ZIP_CONTENT_IDENTICAL_NOT_BYTE_REPRODUCIBLE", compared["verdict"])
        self.assertTrue(compared["canonical_content_equal"])
        self.assertFalse(compared["byte_identical"])

    def test_comparison_reports_content_and_name_changes(self):
        first = self.write_zip("first.zip", entries={"same": b"before", "old": b"old"})
        second = self.write_zip("second.zip", entries={"same": b"after", "new": b"new"})
        compared = release.compare(first, second, POLICY)
        self.assertEqual("ZIP_CONTENT_DIFFERS", compared["verdict"])
        self.assertEqual(["same"], compared["changed"])
        self.assertEqual(["old"], compared["only_first"])
        self.assertEqual(["new"], compared["only_second"])

    def test_no_entry_extraction(self):
        self.inventory(self.write_zip())
        self.assertEqual(["metadata.zip"], sorted(item.name for item in self.folder.iterdir()))


class ToolSafetyTests(unittest.TestCase):
    def test_sdk_tool_must_exist_inside_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(release.ReleaseError):
                release.sdk_file(Path(folder), "missing")
            with self.assertRaises(release.ReleaseError):
                release.sdk_file(Path(folder), "../outside")

    def test_evidence_cannot_target_apk_sources_or_outside_root(self):
        with self.assertRaises(release.ReleaseError):
            release.emit({"verdict": "TEST"}, SCRIPT)

    def test_evidence_rejects_symbolic_link_before_resolving(self):
        with patch.object(Path, "is_symlink", return_value=True), self.assertRaises(release.ReleaseError):
            release.emit({"verdict": "TEST"}, release.ROOT / ".local-evidence/release/result.json")

    def test_evidence_is_exclusive_and_preserves_existing_file(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(release, "ROOT", Path(folder)):
            target = Path(folder) / ".local-evidence/release/result.json"
            with contextlib.redirect_stdout(io.StringIO()):
                release.emit({"verdict": "FIRST"}, target)
            before = target.read_bytes()
            with self.assertRaises(FileExistsError):
                release.emit({"verdict": "SECOND"}, target)
            self.assertEqual(before, target.read_bytes())


class ElfTests(unittest.TestCase):
    def fixture(self, abi, alignment=16384, address=0):
        bits, machine = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183), "x86": (1, 3), "x86_64": (2, 62)}[abi]
        ident = b"\x7fELF" + bytes([bits, 1, 1]) + bytes(9)
        if bits == 1:
            header = struct.pack("<HHIIIIIHHHHHH", 3, machine, 1, 0, 52, 0, 0, 52, 32, 2, 0, 0, 0)
            load = struct.pack("<IIIIIIII", 1, 0, address, 0, 116, 116, 5, alignment)
            relro = struct.pack("<IIIIIIII", 0x6474e552, 100, 100, 0, 16, 16, 4, 1)
        else:
            header = struct.pack("<HHIQQQIHHHHHH", 3, machine, 1, 0, 64, 0, 0, 64, 56, 2, 0, 0, 0)
            load = struct.pack("<IIQQQQQQ", 1, 5, 0, address, 0, 176, 176, alignment)
            relro = struct.pack("<IIQQQQQQ", 0x6474e552, 4, 160, 160, 0, 16, 16, 1)
        return ident + header + load + relro

    def test_all_abi_loads_aligned_and_relro_does_not_require_page_alignment(self):
        for abi in POLICY["abis"]:
            result = release.parse_elf(self.fixture(abi), abi)
            self.assertTrue(result["pt_load_16_kib"])
            self.assertEqual(1, result["pt_gnu_relro"][0]["p_align"])

    def test_four_kib_and_incongruent_loads_not_accepted(self):
        for alignment, address in ((4096, 0), (16384, 1), (20000, 0)):
            self.assertFalse(release.parse_elf(self.fixture("arm64-v8a", alignment, address), "arm64-v8a")["pt_load_16_kib"])

    def test_rejects_invalid_header_wrong_abi_and_truncated_program_table(self):
        for data, abi in ((b"not ELF", "x86"), (self.fixture("x86"), "arm64-v8a"),
                          (self.fixture("x86")[:-1], "x86"), (self.fixture("x86"), "unknown")):
            with self.assertRaises(release.ReleaseError):
                release.parse_elf(data, abi)


if __name__ == "__main__":
    unittest.main()
