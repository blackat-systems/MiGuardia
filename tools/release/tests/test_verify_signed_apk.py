"""Hermetic policy/SDK-boundary tests; synthetic ZIPs are not signed APKs."""
from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "verify_signed_apk.py"
spec = importlib.util.spec_from_file_location("signed_verification", SCRIPT)
signed = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = signed
spec.loader.exec_module(signed)


def official_output(v1=False):
    return "\n".join(["Verifies", f"Verified using v1 scheme (JAR signing): {str(v1).lower()}",
                      "Verified using v2 scheme (APK Signature Scheme v2): true",
                      "Verified using v3 scheme (APK Signature Scheme v3): true",
                      "Verified using v3.1 scheme (APK Signature Scheme v3.1): false",
                      "Verified using v3.2 scheme (APK Signature Scheme v3.2): false",
                      "Verified using v4 scheme (APK Signature Scheme v4): false",
                      "Verified for SourceStamp: false", "Number of signers: 1",
                      "Signer #1 certificate DN: CN=Synthetic test only",
                      "Signer #1 certificate SHA-256 digest: " + signed.PIN.lower(),
                      "Signer #1 public key SHA-256 digest: " + "a" * 64])


def entry(name, value="b"):
    return {"name": name, "size": 1, "crc32": "00000001", "sha256": value * 64, "compression": 0}


class SignatureTests(unittest.TestCase):
    def test_api26_27_v3_reference_warning_is_explicit_and_recorded(self):
        warning = "WARNING: APK Signature Scheme v2 signer #1: APK Signature Scheme v2 signer: 0 references unknown APK signature scheme ID: 3"
        output = official_output().replace("v3): true", "v3): false") + "\n" + warning
        result = signed.signature_result(0, output, allow_legacy_v3_reference=True)
        self.assertEqual([warning], result["accepted_warnings"])
        with self.assertRaises(signed.unsigned.ReleaseError):
            signed.signature_result(0, output)

    def test_legacy_exception_cannot_hide_other_failures(self):
        warning = "WARNING: APK Signature Scheme v2 signer #1: APK Signature Scheme v2 signer: 0 references unknown APK signature scheme ID: 3"
        base = official_output() + "\n" + warning
        for output in (base + "\n" + warning, base.replace("ID: 3", "ID: 4"),
                       base + "\nWARNING: other", base + "\nERROR: other",
                       base.replace(signed.PIN.lower(), "0" * 64),
                       base.replace("v2): true", "v2): false")):
            with self.subTest(output=output), self.assertRaises(signed.unsigned.ReleaseError):
                signed.signature_result(0, output, allow_legacy_v3_reference=True)
        with self.assertRaises(signed.unsigned.ReleaseError):
            signed.signature_result(0, base, required_scheme="3", allow_legacy_v3_reference=True)

    def test_single_pinned_signer_v2_is_accepted(self):
        result = signed.signature_result(0, official_output())
        self.assertEqual(signed.PIN.lower(), result["certificate_sha256"])
        self.assertTrue(result["schemes"]["2"])

    def test_current_sdk37_signer_labels_and_disabled_hybrid_are_accepted(self):
        for label in ("V2 Signer:", "V3.0 Signer:"):
            result = signed.signature_result(0, official_output().replace("Signer #1", label))
            self.assertEqual(signed.PIN.lower(), result["certificate_sha256"])
        for scheme in ("v3.1", "v3.2", "v4"):
            modified = official_output().replace(f"{scheme}): false", f"{scheme}): true")
            # Replace the full current label for v4, whose description differs.
            modified = modified.replace("v4): false", "v4): true")
            with self.assertRaises(signed.unsigned.ReleaseError):
                signed.signature_result(0, modified)

    def test_wrong_certificate_and_debug_are_rejected(self):
        for pin in ("0" * 64, "b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617"):
            with self.subTest(pin=pin), self.assertRaises(signed.unsigned.ReleaseError):
                signed.signature_result(0, official_output().replace(signed.PIN.lower(), pin))

    def test_failure_warning_and_unrecognized_output_are_rejected(self):
        for code, text in ((1, official_output()), (0, "DOES NOT VERIFY"),
                           (0, official_output() + "\nWARNING: synthetic"),
                           (0, official_output() + "\nERROR: synthetic")):
            with self.subTest(code=code, text=text), self.assertRaises(signed.unsigned.ReleaseError):
                signed.signature_result(code, text)

    def test_extra_signer_certificate_or_rotation_is_rejected(self):
        for extra in ("\nSigner #2 certificate SHA-256 digest: " + "c" * 64,
                      "\nSigner #1 certificate SHA-256 digest: " + signed.PIN.lower(),
                      "\nSigner #1 certificate lineage: present", "\nSigner #2 public key: synthetic"):
            with self.subTest(extra=extra), self.assertRaises(signed.unsigned.ReleaseError):
                signed.signature_result(0, official_output() + extra)
        with self.assertRaises(signed.unsigned.ReleaseError):
            signed.signature_result(0, official_output().replace("Number of signers: 1", "Number of signers: 2"))

    def test_v2_required_and_source_stamp_not_accepted(self):
        for text in (official_output().replace("scheme (APK Signature Scheme v2): true", "scheme (APK Signature Scheme v2): false"),
                     official_output().replace("Verified for SourceStamp: false", "Verified for SourceStamp: true"),
                     official_output() + "\nVerified using v2 scheme (APK Signature Scheme v2): true"):
            with self.assertRaises(signed.unsigned.ReleaseError):
                signed.signature_result(0, text)

    def test_distribution_rejects_v1_and_uses_platform_specific_scheme(self):
        with self.assertRaises(signed.unsigned.ReleaseError):
            signed.signature_result(0, official_output(v1=True))
        modern = official_output().replace("v2): true", "v2): false")
        self.assertTrue(signed.signature_result(0, modern, required_scheme="3")["schemes"]["3"])
        with self.assertRaises(signed.unsigned.ReleaseError):
            signed.signature_result(0, modern, required_scheme="2")


class PayloadTests(unittest.TestCase):
    def setUp(self):
        self.base = {"entries": [entry("classes.dex"), entry("META-INF/services/example"), entry("META-INF/license.txt")]}

    def test_v2_without_added_zip_entries_preserves_all_meta_inf(self):
        self.assertTrue(signed.payload_comparison(self.base, copy.deepcopy(self.base), False)["payload_equal"])

    def test_only_verified_complete_jar_triplet_may_be_added(self):
        for suffix in ("RSA", "DSA", "EC"):
            added = [entry("META-INF/MANIFEST.MF"), entry("META-INF/CERT.SF"), entry("META-INF/CERT." + suffix)]
            candidate = {"entries": self.base["entries"] + added}
            self.assertEqual(3, len(signed.payload_comparison(self.base, candidate, True)["signature_entries"]))
            with self.assertRaises(signed.unsigned.ReleaseError):
                signed.payload_comparison(self.base, candidate, False)

    def test_meta_inf_changes_are_not_hidden_as_signatures(self):
        for name in ("META-INF/license.txt", "META-INF/services/example", "classes.dex"):
            candidate = copy.deepcopy(self.base)
            next(item for item in candidate["entries"] if item["name"] == name)["sha256"] = "c" * 64
            with self.assertRaises(signed.unsigned.ReleaseError):
                signed.payload_comparison(self.base, candidate, True)

    def test_added_removed_and_incomplete_signature_entries_rejected(self):
        for extra in ([entry("META-INF/extra.txt")], [entry("META-INF/CERT.SF")],
                      [entry("META-INF/MANIFEST.MF"), entry("META-INF/CERT.SF"), entry("META-INF/OTHER.RSA")],
                      [entry("META-INF/MANIFEST.MF"), entry("META-INF/CERT.SF"), entry("META-INF/CERT.RSA"), entry("asset")]):
            with self.assertRaises(signed.unsigned.ReleaseError):
                signed.payload_comparison(self.base, {"entries": self.base["entries"] + extra}, True)
        with self.assertRaises(signed.unsigned.ReleaseError):
            signed.payload_comparison(self.base, {"entries": self.base["entries"][:-1]}, False)

    def test_existing_manifest_cannot_be_replaced_or_exempted(self):
        before = {"entries": [entry("META-INF/MANIFEST.MF")]}
        after = {"entries": [entry("META-INF/MANIFEST.MF", "c"), entry("META-INF/CERT.SF"), entry("META-INF/CERT.RSA")]}
        with self.assertRaises(signed.unsigned.ReleaseError):
            signed.payload_comparison(before, after, True)

    def test_canonical_zip_rejects_duplicate_traversal_and_size_limit(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "synthetic.zip"
            for names in (("a", "a"), ("../a",), ("META-INF/../a",)):
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore", UserWarning)
                    with zipfile.ZipFile(path, "w") as archive:
                        for name in names:
                            archive.writestr(name, "test")
                with self.assertRaises(signed.unsigned.ReleaseError):
                    signed.unsigned.canonical_zip(path, 1024 * 1024)
            with self.assertRaises(signed.unsigned.ReleaseError):
                signed.unsigned.canonical_zip(path, 1)


class CertificateInventoryTests(unittest.TestCase):
    """Structural bytes only; no fake cryptographic approval or installed APK."""
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "synthetic.zip"
        self.cert = b"synthetic certificate bytes, not a real DER certificate"
        patcher = patch.object(signed, "PIN", hashlib.sha256(self.cert).hexdigest())
        patcher.start()
        self.addCleanup(patcher.stop)

    @staticmethod
    def lp(data):
        return struct.pack("<I", len(data)) + data

    def signer_value(self, version=2, certs=None, attributes=b"", signer_count=1, reserved=b""):
        lp = self.lp
        certs = lp(self.cert) if certs is None else certs
        sdk = struct.pack("<II", 28, 0x7fffffff) if version == 3 else b""
        data = lp(b"opaque digest records") + lp(certs) + sdk + lp(attributes) + reserved
        signer = lp(data) + sdk + lp(b"opaque signatures") + lp(b"opaque public key")
        return lp(lp(signer) * signer_count)

    def archive(self, blocks, size_delta=0):
        with zipfile.ZipFile(self.path, "w") as archive:
            archive.writestr("classes.dex", b"not a dex")
        raw = self.path.read_bytes()
        eocd = raw.rfind(b"PK\x05\x06")
        central = struct.unpack_from("<I", raw, eocd + 16)[0]
        pairs = b"".join(struct.pack("<Q", len(value) + 4) + struct.pack("<I", block_id) + value
                         for block_id, value in blocks)
        size = len(pairs) + 24
        block = struct.pack("<Q", size + size_delta) + pairs + struct.pack("<Q", size) + b"APK Sig Block 42"
        result = bytearray(raw[:central] + block + raw[central:])
        struct.pack_into("<I", result, eocd + len(block) + 16, central + len(block))
        self.path.write_bytes(result)

    def test_exact_single_certificate_v2_and_v3_inventory(self):
        self.archive([(0x7109871a, self.signer_value()), (0xf05368c0, self.signer_value(3)), (0x42726577, bytes(16))])
        result = signed.signing_certificates(self.path)
        self.assertEqual({"2", "3"}, set(result))
        self.assertEqual(1, result["2"]["certificate_count"])
        self.assertEqual(hashlib.sha256(self.cert).hexdigest(), result["3"]["certificate_sha256"])

    def test_sdk37_empty_reserved_v2_field_but_no_other_trailing_bytes(self):
        self.archive([(0x7109871a, self.signer_value(reserved=bytes(4)))])
        self.assertEqual({"2"}, set(signed.signing_certificates(self.path)))
        for reserved in (bytes(8), b"\x01\x00\x00\x00", b"\x00"):
            self.archive([(0x7109871a, self.signer_value(reserved=reserved))])
            with self.assertRaises(signed.unsigned.ReleaseError):
                signed.signing_certificates(self.path)

    def test_extra_certificate_foreign_certificate_and_multiple_signers_rejected(self):
        for value in (self.signer_value(certs=self.lp(self.cert) * 2),
                      self.signer_value(certs=self.lp(b"foreign")), self.signer_value(signer_count=2)):
            self.archive([(0x7109871a, value)])
            with self.assertRaises(signed.unsigned.ReleaseError):
                signed.signing_certificates(self.path)

    def test_unknown_duplicate_missing_v2_and_nonzero_padding_rejected(self):
        v2 = (0x7109871a, self.signer_value())
        for blocks in ([v2, v2], [v2, (0x12345678, b"extra")], [(0xf05368c0, self.signer_value(3))],
                       [v2, (0x42726577, b"x")]):
            self.archive(blocks)
            with self.assertRaises(signed.unsigned.ReleaseError):
                signed.signing_certificates(self.path)

    def test_rotation_and_duplicate_attribute_rejected(self):
        for attributes in (self.lp(struct.pack("<I", 0x3ba06f8c)),
                           self.lp(struct.pack("<I", 0xbeeff00d)) * 2):
            self.archive([(0x7109871a, self.signer_value(attributes=attributes))])
            with self.assertRaises(signed.unsigned.ReleaseError):
                signed.signing_certificates(self.path)

    def test_truncated_nested_lengths_and_signing_sizes_rejected(self):
        value = self.signer_value()
        for truncated in (b"", value[:3], value[:-1], struct.pack("<I", 0xffffffff) + value[4:]):
            self.archive([(0x7109871a, truncated)])
            with self.assertRaises(signed.unsigned.ReleaseError):
                signed.signing_certificates(self.path)
        self.archive([(0x7109871a, value)], size_delta=1)
        with self.assertRaises(signed.unsigned.ReleaseError):
            signed.signing_certificates(self.path)
        self.archive([(0x7109871a, value)])
        with patch.object(signed, "MAX_SIGNING_BLOCK", 24), self.assertRaises(signed.unsigned.ReleaseError):
            signed.signing_certificates(self.path)


class VerificationBoundaryTests(unittest.TestCase):
    """Mock official tool decoding/verification, retaining actual input hashing."""
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.first, self.second, self.approval, self.java = [self.root / name for name in ("unsigned.apk", "signed.apk", "approval.json", "java")]
        for path in (self.first, self.second, self.java):
            path.write_bytes(path.name.encode())
        for name in ("aapt2.exe", "aapt2", "zipalign.exe", "zipalign", "lib/apksigner.jar"):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"synthetic tool")
        self.policy_hash = signed.unsigned.sha256(signed.unsigned.POLICY_PATH)
        self.apk_hash = signed.unsigned.sha256(self.first)
        self.report = {"verdict": "UNSIGNED_CANDIDATE_CHECKS_PASSED", "errors": [],
                       "policy_sha256": self.policy_hash, "artifact": {"sha256": self.apk_hash}}
        self.approval.write_text(json.dumps(self.report), encoding="utf-8")
        self.inventory = {"entries": [entry("classes.dex")], "signing_block_present": True}
        self.fresh = {**self.report, "metadata": {"synthetic": True}, "badging": {"synthetic": True},
                      "zip": {**self.inventory, "signing_block_present": False},
                      "native_elf": {"fixture": {"pt_load_16_kib": True}}}
        self.commands = []
        for name, value in (("inspect", self.fresh), ("canonical_zip", self.inventory),
                            ("parse_xmltree", {}), ("manifest_metadata", self.fresh["metadata"]),
                            ("parse_badging", self.fresh["badging"]), ("validate_metadata", []),
                            ("validate_zip", []), ("inspect_native_elf", self.fresh["native_elf"])):
            patcher = patch.object(signed.unsigned, name, return_value=value)
            patcher.start()
            self.addCleanup(patcher.stop)
        patcher = patch.object(signed, "signing_certificates", return_value={"2": {}, "3": {}})
        patcher.start()
        self.addCleanup(patcher.stop)
        patcher = patch.object(signed.unsigned, "capture", side_effect=self.capture)
        patcher.start()
        self.addCleanup(patcher.stop)

    def capture(self, command, *args, **kwargs):
        self.commands.append(command)
        text = official_output() if "verify" in command else "Verification successful"
        return subprocess.CompletedProcess(command, 0, text, "")

    def run_verifier(self, **changes):
        args = dict(unsigned_apk=self.first, signed_apk=self.second, unsigned_hash=self.apk_hash,
                    approval=self.approval, approval_hash=signed.unsigned.sha256(self.approval),
                    build_tools=self.root, java=self.java)
        return signed.verify(**(args | changes))

    def test_complete_policy_boundary_and_explicit_api_intervals(self):
        result = self.run_verifier()
        self.assertEqual("SIGNED_ARTIFACT_CHECKS_PASSED", result["verdict"])
        self.assertEqual(signed.unsigned.sha256(self.second), result["artifact"]["sha256"])
        self.assertEqual(6, len(self.commands))
        intervals = [command for command in self.commands if "--min-sdk-version" in command]
        self.assertEqual(["26", "28"], [command[command.index("--min-sdk-version") + 1] for command in intervals])
        self.assertEqual(["27", "37"], [command[command.index("--max-sdk-version") + 1] for command in intervals])

    def test_unsigned_hash_or_report_hash_mismatch_fails_before_sdk(self):
        for change in ({"unsigned_hash": "0" * 64}, {"approval_hash": "0" * 64}):
            with self.assertRaises(signed.unsigned.ReleaseError):
                self.run_verifier(**change)
        self.assertFalse(self.commands)

    def test_explicit_frozen_policy_is_bound_by_hash_and_approval(self):
        policy = signed.unsigned.load_policy()
        policy["version_code"] = 2
        policy["version_name"] = "2.0.0-beta.1"
        path = self.root / "frozen-policy.json"
        path.write_text(json.dumps(policy), encoding="utf-8")
        policy_hash = signed.unsigned.sha256(path)
        self.approval.write_text(json.dumps(self.report | {"policy_sha256": policy_hash}), encoding="utf-8")
        result = self.run_verifier(policy_file=path, policy_hash=policy_hash)
        self.assertEqual(policy_hash, result["input_sha256"]["policy"])
        self.assertEqual(policy_hash, self.fresh["policy_sha256"])
        for value in (None, "0" * 64):
            with self.assertRaises(signed.unsigned.ReleaseError):
                self.run_verifier(policy_file=path, policy_hash=value)

    def test_policy_alone_cannot_change_approved_input_identity(self):
        path = self.root / "foreign-policy.json"
        path.write_text(json.dumps(signed.unsigned.load_policy() | {"version_code": 999}), encoding="utf-8")
        with self.assertRaises(signed.unsigned.ReleaseError):
            self.run_verifier(policy_file=path, policy_hash=signed.unsigned.sha256(path))

    def test_report_rejects_failed_verdict_policy_and_foreign_artifact(self):
        for change in ({"verdict": "FAILED"}, {"errors": ["bad"]}, {"policy_sha256": "0" * 64},
                       {"artifact": {"sha256": "0" * 64}}):
            self.approval.write_text(json.dumps(self.report | change), encoding="utf-8")
            with self.assertRaises(signed.unsigned.ReleaseError):
                self.run_verifier()

    def test_cannot_use_same_file_for_unsigned_and_signed(self):
        with self.assertRaises(signed.unsigned.ReleaseError):
            self.run_verifier(signed_apk=self.first)

    def test_fresh_unsigned_inspection_cannot_be_bypassed(self):
        with patch.object(signed.unsigned, "inspect", return_value={"verdict": "FAILED"}):
            with self.assertRaises(signed.unsigned.ReleaseError):
                self.run_verifier()

    def test_missing_signing_block_rejected(self):
        with patch.object(signed.unsigned, "canonical_zip", return_value={**self.inventory, "signing_block_present": False}):
            with self.assertRaises(signed.unsigned.ReleaseError):
                self.run_verifier()

    def test_metadata_native_and_alignment_failures_rejected(self):
        for name, value in (("validate_metadata", ["bad"]), ("parse_badging", {"different": True}),
                            ("inspect_native_elf", {"fixture": {"pt_load_16_kib": False}})):
            with patch.object(signed.unsigned, name, return_value=value), self.assertRaises(signed.unsigned.ReleaseError):
                self.run_verifier()
        def capture(command, *args, **kwargs):
            result = self.capture(command)
            if "-P" in command:
                result.returncode = 1
            return result
        with patch.object(signed.unsigned, "capture", side_effect=capture), self.assertRaises(signed.unsigned.ReleaseError):
            self.run_verifier()

    def test_input_mutation_by_tool_is_rejected(self):
        def mutate(command, *args, **kwargs):
            self.first.write_bytes(b"changed")
            return self.capture(command)
        with patch.object(signed.unsigned, "capture", side_effect=mutate), self.assertRaises(signed.unsigned.ReleaseError):
            self.run_verifier()

    def test_only_one_interval_wrong_signer_rejects_entire_verification(self):
        def capture(command, *args, **kwargs):
            result = self.capture(command)
            if "--min-sdk-version" in command and command[command.index("--min-sdk-version") + 1] == "26":
                result.stdout = official_output().replace(signed.PIN.lower(), "0" * 64)
            return result
        with patch.object(signed.unsigned, "capture", side_effect=capture), self.assertRaises(signed.unsigned.ReleaseError):
            self.run_verifier()

    def test_newer_platform_selection_of_v3_does_not_require_v2_output_true(self):
        def capture(command, *args, **kwargs):
            result = self.capture(command)
            if "verify" in command and not ("--min-sdk-version" in command and
                    command[command.index("--min-sdk-version") + 1] == "26"):
                result.stdout = official_output().replace("v2): true", "v2): false")
            return result
        with patch.object(signed.unsigned, "capture", side_effect=capture):
            self.assertEqual("SIGNED_ARTIFACT_CHECKS_PASSED", self.run_verifier()["verdict"])
        with patch.object(signed, "signing_certificates", return_value={"2": {}}):
            with self.assertRaises(signed.unsigned.ReleaseError):
                self.run_verifier()


if __name__ == "__main__":
    unittest.main()
