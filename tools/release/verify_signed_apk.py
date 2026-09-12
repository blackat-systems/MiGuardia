#!/usr/bin/env python3
"""Verify an already signed APK against an approved unsigned input. Never sign.

Only existing official SDK tools are executed. No credentials, devices or network.
The unsigned inspector remains unchanged and continues rejecting signed APKs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import inspect_apk as unsigned

PIN = "AB6A80E649D10A63D4524278AAAE62588C29B5FF459A91063DEC62B534C69B0D"
MAX_SIGNING_BLOCK = 16 * 1024 * 1024
LEGACY_V3_REFERENCE_WARNING = (
    "WARNING: APK Signature Scheme v2 signer #1: APK Signature Scheme v2 signer: "
    "0 references unknown APK signature scheme ID: 3"
)
require = unsigned.require


def digest_argument(value: str) -> str:
    require(bool(re.fullmatch(r"[0-9a-fA-F]{64}", value)), "Expected an explicit SHA-256")
    return value.lower()


def regular_input(path: Path) -> Path:
    for part in (path.absolute(), *path.absolute().parents):
        require(not part.is_symlink() and not (part.exists() and
                getattr(part.lstat(), "st_file_attributes", 0) & 0x400),
                "Input path contains a link or reparse point")
    require(path.is_file(), "Required regular input is absent")
    return path.resolve()


def signature_result(returncode: int, output: str, required_scheme: str = "2", *,
                     allow_legacy_v3_reference: bool = False) -> dict:
    """Reject diagnostics except the explicit SDK37 API26-27 v3 reference notice.

    apksigner performs the cryptographic verification; this parser only enforces
    the fixed release policy on its official textual result. It is not a PKI or
    APK signature verifier. New SDK output formats require explicit review.
    """
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    require(returncode == 0 and lines.count("Verifies") == 1, "Official signature verification failed")
    diagnostics = [line for line in lines if re.search(r"\b(?:WARNING|ERROR)\b", line)]
    require(not allow_legacy_v3_reference or required_scheme == "2",
            "Legacy diagnostic exception is restricted to v2 verification")
    require(not diagnostics or (allow_legacy_v3_reference and
            diagnostics == [LEGACY_V3_REFERENCE_WARNING]),
            "Official verifier reported a warning or error")
    counts = [line for line in lines if line.startswith("Number of signers:")]
    require(counts == ["Number of signers: 1"], "Expected exactly one APK signer")
    certificates = [line for line in lines if "certificate SHA-256 digest:" in line]
    match = re.fullmatch(r"(?:Signer #1|V2 Signer:|V3\.0 Signer:) certificate SHA-256 digest: ([0-9a-fA-F]{64})",
                         certificates[0]) if len(certificates) == 1 else None
    require(match is not None, "Additional or unrecognized signing certificate")
    pin = digest_argument(match[1])
    require(pin == PIN.lower(), "APK is not signed by the permanent release certificate")
    require(all(index == "1" for index in re.findall(r"Signer #(\d+)", output)),
            "Additional signer output")
    schemes = {}
    for line in lines:
        match = re.fullmatch(r"Verified using v([0-9.]+) scheme \([^\n]+\): (true|false)", line)
        if match:
            require(match[1] not in schemes, "Repeated signature scheme result")
            schemes[match[1]] = match[2] == "true"
    require(required_scheme in {"2", "3"} and schemes.get(required_scheme) is True,
            "Required platform signature scheme did not verify")
    require(schemes.get("1") is False, "Distribution does not permit JAR/v1 signatures")
    require(set(schemes) <= {"1", "2", "3", "3.1", "3.2", "4"} and
            not any(schemes.get(version, False) for version in ("3.1", "3.2", "4")),
            "Unknown or detached signature scheme requires a separate policy")
    require(not any("lineage" in line.lower() or "rotation" in line.lower() for line in lines),
            "Certificate rotation requires a separate policy")
    require(not any(line.startswith("Verified for SourceStamp:") and not line.endswith("false") for line in lines),
            "Additional source stamp authority is outside this policy")
    return {"certificate_sha256": pin, "signer_count": 1, "schemes": schemes,
            "accepted_warnings": diagnostics}


def _length_prefixed(data: bytes, offset: int) -> tuple[bytes, int]:
    require(0 <= offset <= len(data) - 4, "Truncated signing-block field")
    length = struct.unpack_from("<I", data, offset)[0]
    end = offset + 4 + length
    require(end <= len(data), "Signing-block field exceeds its container")
    return data[offset + 4:end], end


def signing_certificates(apk: Path) -> dict:
    """Inventory certificate bytes only; never verify cryptographic signatures.

    Supports one v2 signer and one v3.0 signer, each with exactly one
    DER certificate. Only those blocks plus all-zero verity padding are accepted.
    Accepts the SDK 37 v2 trailing empty reserved field (four zero bytes), never
    arbitrary trailing data. Rejects rotation, v3.1/v3.2, source stamps and unknown blocks. apksigner
    must independently authenticate the entire APK and every accepted scheme.
    """
    with zipfile.ZipFile(apk) as archive, apk.open("rb") as stream:
        central = archive.start_dir
        require(24 <= central <= 0xffffffff, "Unsupported central-directory offset")
        stream.seek(central - 24)
        footer = stream.read(24)
        require(len(footer) == 24 and footer[8:] == b"APK Sig Block 42", "Missing signing-block footer")
        size = struct.unpack_from("<Q", footer)[0]
        require(24 <= size <= MAX_SIGNING_BLOCK and size + 8 <= central, "Signing-block size exceeds bounds")
        stream.seek(central - size - 8)
        require(struct.unpack("<Q", stream.read(8))[0] == size, "Signing-block sizes disagree")
        pairs = stream.read(size - 24)
        require(len(pairs) == size - 24, "Truncated signing-block entries")
    offset, seen, certificates = 0, set(), {}
    while offset < len(pairs):
        require(offset + 8 <= len(pairs), "Truncated signing-block pair")
        length = struct.unpack_from("<Q", pairs, offset)[0]
        offset += 8
        require(4 <= length <= len(pairs) - offset, "Signing-block pair exceeds bounds")
        block_id = struct.unpack_from("<I", pairs, offset)[0]
        require(block_id not in seen, "Duplicate signing-block ID")
        seen.add(block_id)
        value = pairs[offset + 4:offset + length]
        offset += length
        if block_id == 0x42726577:  # apksigner verity padding; not executable payload.
            require(not any(value), "Nonzero signing-block padding")
            continue
        require(block_id in {0x7109871a, 0xf05368c0}, "Unsupported signing block")
        version = "2" if block_id == 0x7109871a else "3"
        signers, end = _length_prefixed(value, 0)
        require(end == len(value), "Trailing signer-sequence bytes")
        signer, end = _length_prefixed(signers, 0)
        require(end == len(signers), "Expected exactly one signer in each scheme")
        data, cursor = _length_prefixed(signer, 0)
        if version == "3":
            require(cursor + 8 <= len(signer), "Truncated v3 SDK interval")
            cursor += 8
        signatures, cursor = _length_prefixed(signer, cursor)
        public_key, cursor = _length_prefixed(signer, cursor)
        require(cursor == len(signer) and signatures and public_key, "Malformed signer record")
        digests, cursor = _length_prefixed(data, 0)
        certs, cursor = _length_prefixed(data, cursor)
        if version == "3":
            require(cursor + 8 <= len(data), "Truncated signed v3 SDK interval")
            cursor += 8
        attributes, cursor = _length_prefixed(data, cursor)
        # Existing SDK37 V2SchemeSigner emits a fourth empty length-prefixed
        # field after attributes. Inspected locally in the official JAR; it is
        # not a certificate/attribute and must contain exactly zero bytes.
        if version == "2" and data[cursor:] == bytes(4):
            cursor += 4
        require(cursor == len(data) and digests, "Malformed signed-data record")
        certificate, end = _length_prefixed(certs, 0)
        require(end == len(certs) and 0 < len(certificate) <= 64 * 1024,
                "Expected exactly one bounded certificate, not a certificate chain")
        certificate_hash = hashlib.sha256(certificate).hexdigest()
        require(certificate_hash == PIN.lower(), "Additional or foreign certificate in signing block")
        cursor, attribute_ids = 0, set()
        while cursor < len(attributes):
            attribute, cursor = _length_prefixed(attributes, cursor)
            require(len(attribute) >= 4, "Truncated signature attribute")
            attribute_id = struct.unpack_from("<I", attribute)[0]
            require(attribute_id not in attribute_ids and attribute_id != 0x3ba06f8c,
                    "Repeated attribute or certificate rotation")
            attribute_ids.add(attribute_id)
        certificates[version] = {"certificate_sha256": certificate_hash, "certificate_count": 1, "signer_count": 1}
    require("2" in certificates, "Missing v2 signing block")
    return certificates


def payload_comparison(before: dict, after: dict, jar_verified: bool) -> dict:
    """Ignore only a complete, verified JAR signature set newly added by signing.

    Every pre-existing META-INF entry remains part of the payload. The APK v2/v3
    signing block is not a ZIP entry and is verified by apksigner separately.
    """
    left = {entry["name"]: entry for entry in before["entries"]}
    right = {entry["name"]: entry for entry in after["entries"]}
    added = set(right) - set(left)
    ignored: set[str] = set()
    if added:
        require(jar_verified and "META-INF/MANIFEST.MF" in added and len(added) == 3,
                "Unexpected added ZIP entries")
        sf = [name for name in added if re.fullmatch(r"META-INF/[A-Z0-9_-]{1,8}\.SF", name)]
        require(len(sf) == 1, "Expected one JAR signature file")
        base = sf[0][:-3]
        blocks = added - {"META-INF/MANIFEST.MF", sf[0]}
        require(len(blocks) == 1 and next(iter(blocks)) in {base + ext for ext in (".RSA", ".DSA", ".EC")},
                "JAR signature block does not match its signature file")
        require(all(right[name]["size"] > 0 for name in added), "Empty signature entry")
        ignored = added
    require(set(left) == set(right) - ignored, "Payload entry set changed during signing")
    fields = ("name", "size", "crc32", "sha256")
    canonical = []
    for name in sorted(left):
        require(all(left[name][field] == right[name][field] for field in fields),
                "Payload bytes changed during signing")
        canonical.append({field: left[name][field] for field in fields})
    digest = hashlib.sha256(json.dumps(canonical, ensure_ascii=True, separators=(",", ":")).encode()).hexdigest()
    return {"canonical_sha256": digest, "entry_count": len(canonical),
            "signature_entries": sorted(ignored), "payload_equal": True}


def approved_input(report: Path, report_hash: str, apk_hash: str, policy_hash: str) -> dict:
    require(0 < report.stat().st_size <= 16 * 1024 * 1024, "Approval report size is invalid")
    require(unsigned.sha256(report) == digest_argument(report_hash), "Approval report hash differs")
    data = json.loads(report.read_text(encoding="utf-8"))
    require(isinstance(data, dict) and data.get("verdict") == "UNSIGNED_CANDIDATE_CHECKS_PASSED" and
            data.get("errors") == [] and data.get("policy_sha256") == policy_hash and
            isinstance(data.get("artifact"), dict) and data["artifact"].get("sha256") == apk_hash,
            "Approval does not identify this unsigned artifact and policy")
    return data


def verify(unsigned_apk: Path, signed_apk: Path, unsigned_hash: str, approval: Path,
           approval_hash: str, build_tools: Path, java: Path, policy_file: Path | None = None,
           policy_hash: str | None = None) -> dict:
    first, second, approval = map(regular_input, (unsigned_apk, signed_apk, approval))
    require(first != second, "Signed and unsigned artifacts must be distinct files")
    expected_hash = digest_argument(unsigned_hash)
    policy_path = regular_input(policy_file if policy_file is not None else unsigned.POLICY_PATH)
    require(0 < policy_path.stat().st_size <= 128 * 1024, "Policy size exceeds bounds")
    if policy_file is not None:
        require(policy_hash is not None and unsigned.sha256(policy_path) == digest_argument(policy_hash),
                "Explicit policy requires its exact approved SHA-256")
    else:
        require(policy_hash is None, "Policy hash requires an explicit policy file")
    policy = json.loads(policy_path.read_text(encoding="utf-8"))
    require(policy["min_sdk"] == 26 and type(policy["target_sdk"]) is int and policy["target_sdk"] >= 28,
            "Platform intervals require explicit policy review")
    java = regular_input(java)
    aapt = unsigned.sdk_file(build_tools, "aapt2.exe" if sys.platform == "win32" else "aapt2")
    align = unsigned.sdk_file(build_tools, "zipalign.exe" if sys.platform == "win32" else "zipalign")
    signer = unsigned.sdk_file(build_tools, "lib/apksigner.jar")
    inputs = {"unsigned": first, "signed": second, "approval": approval, "policy": policy_path,
              "java": java, "aapt2": regular_input(aapt), "zipalign": regular_input(align),
              "apksigner_jar": regular_input(signer)}
    initial = {name: unsigned.sha256(path) for name, path in inputs.items()}
    require(initial["unsigned"] == expected_hash, "Unsigned artifact differs from approved SHA-256")
    approved_input(approval, approval_hash, expected_hash, initial["policy"])
    fresh = unsigned.inspect(first, build_tools, java, policy)
    # The unchanged unsigned inspector labels the default policy path in its
    # result; this wrapper owns provenance when using an explicit frozen policy.
    fresh["policy_sha256"] = initial["policy"]
    require(fresh["verdict"] == "UNSIGNED_CANDIDATE_CHECKS_PASSED", "Fresh unsigned inspection failed")
    inventory = unsigned.canonical_zip(second, policy["max_apk_bytes"])
    require(inventory["signing_block_present"], "Signed APK has no APK signing block")
    certificates = signing_certificates(second)
    java_verify = [str(java), "--enable-native-access=ALL-UNNAMED", "-jar", str(signer), "verify", "--verbose", "--print-certs"]
    commands = {
        "signature": [*java_verify, str(second)],
        "api26_27": [*java_verify,
                     "--min-sdk-version", "26", "--max-sdk-version", "27", str(second)],
        "api28_current": [*java_verify,
                          "--min-sdk-version", "28", "--max-sdk-version", str(policy["target_sdk"]), str(second)],
        "badging": [str(aapt), "dump", "badging", str(second)],
        "manifest": [str(aapt), "dump", "xmltree", str(second), "--file", "AndroidManifest.xml"],
        "alignment": [str(align), "-c", "-P", "16", "-v", "4", str(second)],
    }
    results = {name: unsigned.capture(command, unsigned.ROOT, timeout=90) for name, command in commands.items()}
    signatures = {name: signature_result(results[name].returncode,
                  results[name].stdout + "\n" + results[name].stderr,
                  required_scheme="2" if name == "api26_27" else "3",
                  allow_legacy_v3_reference=name == "api26_27")
                  for name in ("signature", "api26_27", "api28_current")}
    # A verifier may select v3 instead of v2 on newer platforms. The independent
    # API26-27 invocation authenticates v2; API28-current authenticates v3.
    require(set(certificates) == {"2", "3"}, "Distribution requires exactly v2 and v3.0 signing blocks")
    require(results["badging"].returncode == 0 and results["manifest"].returncode == 0,
            "Official SDK could not decode signed metadata")
    metadata = unsigned.manifest_metadata(unsigned.parse_xmltree(results["manifest"].stdout))
    badging = unsigned.parse_badging(results["badging"].stdout)
    require(not unsigned.validate_metadata(metadata, badging, policy), "Signed metadata contradicts policy")
    require(metadata == fresh["metadata"] and badging == fresh["badging"], "Signed metadata differs from unsigned")
    comparison = payload_comparison(fresh["zip"], inventory, signatures["signature"]["schemes"]["1"])
    # Reuse every unsigned ZIP rule on the proven payload, not on signing material.
    clean_inventory = {"entries": [entry for entry in inventory["entries"]
                       if entry["name"] not in comparison["signature_entries"]], "signing_block_present": False}
    require(not unsigned.validate_zip(clean_inventory, policy), "Signed payload contradicts ZIP policy")
    native = unsigned.inspect_native_elf(second, policy)
    require(native == fresh["native_elf"] and all(item["pt_load_16_kib"] for item in native.values()),
            "Signed native payload or ELF alignment differs")
    require(results["alignment"].returncode == 0 and "Verification successful" in results["alignment"].stdout,
            "Signed ZIP alignment failed")
    final = {name: unsigned.sha256(path) for name, path in inputs.items()}
    require(initial == final, "Input or tool changed during verification")
    return {"verdict": "SIGNED_ARTIFACT_CHECKS_PASSED", "errors": [], "input_sha256": final,
            "artifact": {"name": second.name, "size": second.stat().st_size, "sha256": final["signed"]},
            "policy": {"name": policy_path.name, "sha256": final["policy"], "baseline_commit": policy["baseline_commit"]},
            "unsigned_reinspection": {"verdict": fresh["verdict"], "sha256": final["unsigned"],
                                      "policy_sha256": fresh["policy_sha256"]},
            "sdk_intervals": {"api26_27": [26, 27], "api28_current": [28, policy["target_sdk"]]},
            "emission_policy": {"v1": False, "v2": True, "v3": True, "v4": False},
            "metadata": metadata, "badging": badging, "native_elf": native,
            "payload": comparison, "signature": signatures, "certificate_inventory": certificates, "zipalign_16_kib": True,
            "tool_exit_codes": {name: result.returncode for name, result in results.items()},
            "limit": "Local signature/payload/SDK checks only. Does not sign, install, test an upgrade, publish, establish custody, or prove an uncompromised toolchain/hosting."}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--unsigned", type=Path, required=True)
    parser.add_argument("--signed", type=Path, required=True)
    parser.add_argument("--unsigned-sha256", required=True)
    parser.add_argument("--unsigned-report", type=Path, required=True)
    parser.add_argument("--unsigned-report-sha256", required=True)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--policy", type=Path, help="Explicit previously approved frozen policy; never derive from APK")
    parser.add_argument("--policy-sha256", help="Required with --policy")
    parser.add_argument("--java")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        java = unsigned.resolve_executable(args.java, "java") if args.java else unsigned.java_executable()
        result = verify(args.unsigned, args.signed, args.unsigned_sha256, args.unsigned_report,
                        args.unsigned_report_sha256, args.build_tools, java, args.policy, args.policy_sha256)
        unsigned.emit(result, args.output)
        return 0
    except (unsigned.ReleaseError, unsigned.VerificationError):
        print("Signed verification rejected input, policy, signature or evidence; no private data printed", file=sys.stderr)
    except (OSError, subprocess.SubprocessError, zipfile.BadZipFile, EOFError, UnicodeError, ValueError, KeyError, TypeError):
        print("Signed verification failed on local input/tool/evidence; no raw output printed", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
