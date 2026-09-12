#!/usr/bin/env python3
"""Inspect an unsigned MiGuardia V2 APK, or compare two ZIP payloads. Stdlib only.

No signing, builds, installation, network, SDK downloads or credential handling.
SDK executables are invoked with separated arguments and shell=False.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import struct
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
from verify import capture, java_executable, resolve_executable, sha256, VerificationError

POLICY_PATH = Path(__file__).with_name("policy.json")
ANDROID = "http://schemas.android.com/apk/res/android:"
COMPONENTS = {"activity", "activity-alias", "receiver", "provider", "service"}
MAX_ENTRIES = 20_000
MAX_EXPANDED_BYTES = 512 * 1024 * 1024


class ReleaseError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ReleaseError(message)


def load_policy() -> dict:
    return json.loads(POLICY_PATH.read_text(encoding="utf-8"))


def parse_value(value: str):
    if value.startswith('"'):
        try:
            parsed, consumed = json.JSONDecoder().raw_decode(value)
        except ValueError as exc:
            raise ReleaseError("Malformed string in SDK manifest dump") from exc
        require(not value[consumed:].strip() or value[consumed:].strip().startswith("(Raw:"),
                "Unexpected SDK string suffix")
        return parsed
    value = value.split(" (Raw:", 1)[0].strip()
    if value in {"true", "false"}:
        return value == "true"
    if re.fullmatch(r"-?(?:0x[0-9a-fA-F]+|[0-9]+)", value):
        return int(value, 16 if "0x" in value else 10)
    return value  # Resource references remain references, never coerced to false.


def parse_xmltree(text: str) -> dict:
    """Parse aapt2's indentation tree, preserving explicit Android namespaces."""
    stack: list[tuple[int, dict]] = []
    roots = []
    for line in text.splitlines():
        element = re.fullmatch(r"(\s*)E: ([\w.-]+)(?: \(.*\))?", line)
        if element:
            indent = len(element[1])
            while stack and stack[-1][0] >= indent:
                stack.pop()
            node = {"tag": element[2], "attributes": {}, "children": []}
            (stack[-1][1]["children"] if stack else roots).append(node)
            stack.append((indent, node))
            continue
        attribute = re.fullmatch(r"(\s*)A: (.+?)=(.*)", line)
        if attribute:
            require(bool(stack) and len(attribute[1]) > stack[-1][0], "Orphan manifest attribute")
            key = re.sub(r"\(0x[0-9a-fA-F]+\)$", "", attribute[2])
            if key.startswith(ANDROID):
                key = "android:" + key[len(ANDROID):]
            attributes = stack[-1][1]["attributes"]
            require(key not in attributes, "Duplicate manifest attribute")
            attributes[key] = parse_value(attribute[3])
            continue
        require(not line.strip() or line.lstrip().startswith("N:"), "Unrecognized SDK manifest output")
    require(len(roots) == 1 and roots[0]["tag"] == "manifest", "Expected one manifest root")
    return roots[0]


def direct(node: dict, tag: str) -> list[dict]:
    return [child for child in node["children"] if child["tag"] == tag]


def bool_attribute(attributes: dict, key: str, default: bool | None = None) -> bool:
    value = attributes.get("android:" + key, default)
    if type(value) is bool:
        return value
    if type(value) is int and value in {0, 1, -1, 0xffffffff}:
        return value != 0
    raise ReleaseError("Missing or unresolved manifest boolean: " + key)


def full_name(value, package: str) -> str:
    require(isinstance(value, str) and bool(value), "Missing component name")
    return package + value if value.startswith(".") else package + "." + value if "." not in value else value


def manifest_metadata(root: dict) -> dict:
    attrs = root["attributes"]
    package = attrs.get("package")
    require(isinstance(package, str) and bool(package), "Missing manifest package")
    apps, sdk = direct(root, "application"), direct(root, "uses-sdk")
    require(len(apps) == 1 and len(sdk) == 1, "Expected one application and uses-sdk")
    app = apps[0]["attributes"]
    sdk_attrs = sdk[0]["attributes"]
    components = []
    for child in apps[0]["children"]:
        if child["tag"] in COMPONENTS:
            values = child["attributes"]
            components.append({"kind": child["tag"], "name": full_name(values.get("android:name"), package),
                               "exported": bool_attribute(values, "exported"),
                               "permission": values.get("android:permission", app.get("android:permission")),
                               "authorities": values.get("android:authorities")})
    permissions = []
    declared_permissions = []
    for child in root["children"]:
        if child["tag"] == "permission":
            values = child["attributes"]
            require(set(values) <= {"android:name", "android:protectionLevel"} and
                    isinstance(values.get("android:name"), str), "Unsupported declared permission attributes")
            level = values.get("android:protectionLevel", 0)  # Android's omitted protection defaults to normal.
            require(type(level) is int, "Unresolved declared permission protection level")
            declared_permissions.append({"name": values["android:name"], "protection_level": level})
        if child["tag"].startswith("uses-permission"):
            values = child["attributes"]
            require(child["tag"] == "uses-permission" and set(values) == {"android:name"},
                    "Permission declaration differs from unrestricted baseline")
            require(isinstance(values["android:name"], str), "Invalid permission name")
            permissions.append(values["android:name"])
    split_attributes = {"split", "android:isFeatureSplit", "android:isSplitRequired", "android:configForSplit",
                        "android:requiredSplitTypes", "android:splitTypes"}
    all_nodes = [root]
    split_indicators = []
    while all_nodes:
        node = all_nodes.pop()
        split_indicators.extend(key for key in node["attributes"] if key in split_attributes)
        if node["tag"] == "uses-split":
            split_indicators.append("uses-split")
        if node["tag"] == "meta-data" and node["attributes"].get("android:name", "").startswith("com.android.vending.splits"):
            split_indicators.append("vending-splits")
        all_nodes.extend(node["children"])
    return {"package": package, "version_code": attrs.get("android:versionCode"),
            "version_code_major": attrs.get("android:versionCodeMajor", 0),
            "version_name": attrs.get("android:versionName"), "compile_sdk": attrs.get("android:compileSdkVersion"),
            "min_sdk": sdk_attrs.get("android:minSdkVersion"), "target_sdk": sdk_attrs.get("android:targetSdkVersion"),
            "debuggable": bool_attribute(app, "debuggable", False), "test_only": bool_attribute(app, "testOnly", False),
            "allow_backup": bool_attribute(app, "allowBackup"), "cleartext": bool_attribute(app, "usesCleartextTraffic"),
            "extract_native_libs": bool_attribute(app, "extractNativeLibs"),
            "permissions": permissions, "declared_permissions": declared_permissions,
            "application_permission": app.get("android:permission"), "components": components, "splits": split_indicators,
            "instrumentation_count": len(direct(root, "instrumentation"))}


def parse_badging(text: str) -> dict:
    package_lines = re.findall(r"^package: (.*)$", text, re.MULTILINE)
    require(len(package_lines) == 1, "Missing or repeated badging package")
    values = dict(re.findall(r"([A-Za-z]+)='([^']*)'", package_lines[0]))
    def scalar(pattern: str) -> str:
        matches = re.findall(pattern, text, re.MULTILINE)
        require(len(matches) == 1, "Missing or repeated SDK badging field")
        return matches[0]
    try:
        return {"package": values["name"], "version_code": int(values["versionCode"]),
                "version_name": values["versionName"],
                "min_sdk": int(scalar(r"^(?:minSdkVersion|sdkVersion):'([0-9]+)'\s*$")),
                "target_sdk": int(scalar(r"^targetSdkVersion:'([0-9]+)'\s*$")),
                "abis": re.findall(r"'([^']+)'", scalar(r"^native-code:(.*)$")),
                "split": "split" in values,
                "application_debuggable": bool(re.search(r"^application-debuggable\s*$", text, re.MULTILINE))}
    except (KeyError, ValueError) as exc:
        raise ReleaseError("Invalid SDK badging identity") from exc


def validate_metadata(metadata: dict, badging: dict, policy: dict) -> list[str]:
    errors = []
    for key in ("package", "version_code", "version_name", "min_sdk", "target_sdk", "compile_sdk"):
        if metadata.get(key) != policy[key] or (key in badging and badging[key] != policy[key]):
            errors.append("identity:" + key)
    for key in ("version_code_major", "debuggable", "test_only", "allow_backup", "cleartext", "extract_native_libs", "instrumentation_count"):
        if metadata[key] not in (False, 0):
            errors.append("manifest:" + key)
    if metadata["splits"] or badging["split"]:
        errors.append("manifest:splits")
    if badging["application_debuggable"]:
        errors.append("badging:debuggable")
    permissions = metadata["permissions"]
    if len(permissions) != len(set(permissions)) or sorted(permissions) != sorted(policy["permissions"]):
        errors.append("manifest:permissions")
    if sorted(metadata["declared_permissions"], key=lambda item: item["name"]) != sorted(policy["declared_permissions"], key=lambda item: item["name"]):
        errors.append("manifest:declared-permissions")
    if metadata["application_permission"] != policy["application_permission"]:
        errors.append("manifest:application-permission")
    sort_key = lambda item: (item["kind"], item["name"])
    if sorted(metadata["components"], key=sort_key) != sorted(policy["components"], key=sort_key):
        errors.append("manifest:components")
    if sorted(badging["abis"]) != sorted(policy["abis"]):
        errors.append("badging:abis")
    return errors


def validate_apk_path(path: Path, max_bytes: int) -> None:
    require(path.is_file() and 0 < path.stat().st_size <= max_bytes, "APK absent, empty or larger than allowed")


def canonical_zip(path: Path, max_bytes: int) -> dict:
    validate_apk_path(path, max_bytes)
    entries = []
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        require(0 < len(infos) <= MAX_ENTRIES, "Invalid ZIP entry count")
        require(sum(info.file_size for info in infos) <= MAX_EXPANDED_BYTES, "ZIP expanded-size limit exceeded")
        names = set()
        for info in infos:
            name = info.orig_filename
            require(name == info.filename, "ZIP name was truncated or normalized by the reader")
            require(name not in names, "Duplicate ZIP entry")
            names.add(name)
            require(name and "\\" not in name and "\x00" not in name and not name.startswith("/") and
                    not re.match(r"^[A-Za-z]:", name) and all(part not in {".", "..", ""} for part in name.rstrip("/").split("/")),
                    "Unsafe ZIP entry name")
            require(not info.flag_bits & 1, "Encrypted ZIP entry")
            require(info.compress_type in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED}, "Unsupported APK ZIP compression")
            require((info.external_attr >> 16) & 0o170000 != 0o120000, "ZIP symbolic link")
            digest = hashlib.sha256()
            size = 0
            with archive.open(info) as content:
                for chunk in iter(lambda: content.read(1024 * 1024), b""):
                    size += len(chunk)
                    require(size <= info.file_size and size <= MAX_EXPANDED_BYTES, "ZIP size mismatch")
                    digest.update(chunk)
            require(size == info.file_size, "ZIP size mismatch")
            entries.append({"name": name, "size": size, "crc32": f"{info.CRC:08x}",
                            "sha256": digest.hexdigest(), "compression": info.compress_type})
        with path.open("rb") as raw:
            raw.seek(max(0, archive.start_dir - 16))
            signing_block_magic = raw.read(16) == b"APK Sig Block 42"
    entries.sort(key=lambda item: item["name"])
    canonical = [{key: item[key] for key in ("name", "size", "crc32", "sha256")} for item in entries]
    return {"entries": entries, "canonical_sha256": hashlib.sha256(json.dumps(canonical, ensure_ascii=True,
            separators=(",", ":")).encode("utf-8")).hexdigest(), "signing_block_present": signing_block_magic}


def validate_zip(inventory: dict, policy: dict) -> list[str]:
    errors = []
    entries = inventory["entries"]
    names = {entry["name"] for entry in entries}
    if not {"AndroidManifest.xml", "classes.dex", "resources.arsc"}.issubset(names):
        errors.append("zip:incomplete-apk")
    if any(name.endswith((".apk", ".apks", ".aab")) or name == "BundleConfig.pb" for name in names):
        errors.append("zip:nested-apk-or-bundle")
    expected = {f"lib/{abi}/{library}" for abi in policy["abis"] for library in policy["native_libraries"]}
    native = {name for name in names if name.startswith("lib/") and not name.endswith("/")}
    if native != expected:
        errors.append("zip:native-library-set")
    if any(entry["name"] in native and (entry["compression"] != zipfile.ZIP_STORED or entry["size"] <= 0) for entry in entries):
        errors.append("zip:native-library-storage")
    if inventory["signing_block_present"] or any(re.fullmatch(r"META-INF/[^/]+\.(?:SF|RSA|DSA|EC)", name, re.IGNORECASE) for name in names):
        errors.append("zip:unexpected-signing-material")
    if any(PurePosixPath(name).suffix.lower() in {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key"} or
           PurePosixPath(name).name in {".env", "local.properties", "google-services.json"} for name in names):
        errors.append("zip:forbidden-secret-file-name")
    return errors


def parse_elf(data: bytes, abi: str) -> dict:
    """Inspect ELF headers only. This is not a loader, linker or signature check."""
    expected = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183), "x86": (1, 3), "x86_64": (2, 62)}
    require(abi in expected and len(data) >= 16 and data[:4] == b"\x7fELF", "Invalid native ELF header")
    elf_class, machine = expected[abi]
    require(data[4] == elf_class and data[5] == 1 and data[6] == 1, "Native ELF class/endianness/version differs from ABI")
    header_format = "<HHIIIIIHHHHHH" if elf_class == 1 else "<HHIQQQIHHHHHH"
    header_size = 16 + struct.calcsize(header_format)
    require(len(data) >= header_size, "Truncated ELF header")
    header = struct.unpack_from(header_format, data, 16)
    require(header[0] == 3 and header[1] == machine and header[2] == 1 and header[7] == header_size,
            "Unexpected native ELF type/machine/header")
    phoff, phsize, phcount = header[4], header[8], header[9]
    fmt = "<IIIIIIII" if elf_class == 1 else "<IIQQQQQQ"
    require(phsize == struct.calcsize(fmt) and 0 < phcount <= 1024 and phoff >= header_size and
            phoff + phsize * phcount <= len(data), "Invalid ELF program-header table")
    loads, relro = [], []
    for index in range(phcount):
        values = struct.unpack_from(fmt, data, phoff + index * phsize)
        if elf_class == 1:
            kind, offset, address, _, size, memory_size, flags, alignment = values
        else:
            kind, flags, offset, address, _, size, memory_size, alignment = values
        require(offset + size <= len(data), "ELF segment exceeds file")
        item = {"offset": offset, "virtual_address": address, "file_size": size,
                "memory_size": memory_size, "flags": flags, "p_align": alignment}
        if kind == 1:  # PT_LOAD
            require(size <= memory_size, "ELF load file size exceeds memory size")
            loads.append(item)
        elif kind == 0x6474e552:  # PT_GNU_RELRO: its p_align is not the PT_LOAD page alignment.
            relro.append(item)
    require(bool(loads), "Native ELF has no PT_LOAD segment")
    aligned = all(item["p_align"] >= 16384 and item["p_align"] & (item["p_align"] - 1) == 0 and
                  (item["virtual_address"] - item["offset"]) % item["p_align"] == 0 for item in loads)
    return {"abi": abi, "elf_class": 32 if elf_class == 1 else 64, "machine": machine,
            "pt_load": loads, "pt_gnu_relro": relro, "pt_load_16_kib": aligned}


def inspect_native_elf(apk: Path, policy: dict) -> dict:
    records = {}
    with zipfile.ZipFile(apk) as archive:
        for abi in policy["abis"]:
            for library in policy["native_libraries"]:
                name = f"lib/{abi}/{library}"
                require(name in archive.namelist(), "Missing expected ELF library")
                require(archive.getinfo(name).file_size <= 8 * 1024 * 1024, "Native library exceeds inspection limit")
                records[name] = parse_elf(archive.read(name), abi)
    return records


def unsigned_result(returncode: int, output: str) -> str:
    """A broken Java/SDK command or invalid signature is not proof of unsignedness."""
    if returncode == 0:
        return "UNEXPECTED_VALID_SIGNATURE"
    errors = [line.strip() for line in output.splitlines() if line.strip().startswith("ERROR:")]
    if returncode == 1 and "DOES NOT VERIFY" in output.splitlines() and errors == ["ERROR: Missing META-INF/MANIFEST.MF"]:
        return "EXPECTED_UNSIGNED_REJECTION"
    return "UNEXPECTED_VERIFIER_FAILURE"


def sdk_file(directory: Path, name: str) -> Path:
    candidate = (directory / name).resolve()
    require(candidate.is_relative_to(directory.resolve()) and candidate.is_file(), "Required SDK tool is absent")
    return candidate


def inspect(apk: Path, build_tools: Path, java: Path, policy: dict) -> dict:
    validate_apk_path(apk, policy["max_apk_bytes"])
    before = sha256(apk)
    inventory = canonical_zip(apk, policy["max_apk_bytes"])
    aapt = sdk_file(build_tools, "aapt2.exe" if sys.platform == "win32" else "aapt2")
    align = sdk_file(build_tools, "zipalign.exe" if sys.platform == "win32" else "zipalign")
    signer = sdk_file(build_tools, "lib/apksigner.jar")
    commands = {
        "badging": [str(aapt), "dump", "badging", str(apk)],
        "manifest": [str(aapt), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"],
        "alignment": [str(align), "-c", "-P", "16", "-v", "4", str(apk)],
        "signature": [str(java), "-jar", str(signer), "verify", "--verbose", "--print-certs", str(apk)],
    }
    results = {name: capture(command, ROOT, timeout=90) for name, command in commands.items()}
    require(results["badging"].returncode == 0 and results["manifest"].returncode == 0, "SDK could not decode APK metadata")
    metadata = manifest_metadata(parse_xmltree(results["manifest"].stdout))
    badging = parse_badging(results["badging"].stdout)
    errors = validate_metadata(metadata, badging, policy) + validate_zip(inventory, policy)
    native_elf = inspect_native_elf(apk, policy)
    if not all(item["pt_load_16_kib"] for item in native_elf.values()):
        errors.append("elf:pt-load-16-kib")
    aligned = results["alignment"].returncode == 0 and "Verification successful" in results["alignment"].stdout
    if not aligned:
        errors.append("alignment:16-kib")
    signature = results["signature"]
    state = unsigned_result(signature.returncode, signature.stdout + "\n" + signature.stderr)
    if state != "EXPECTED_UNSIGNED_REJECTION":
        errors.append("signature:" + state)
    after = sha256(apk)
    if before != after:
        errors.append("artifact:changed-during-inspection")
    return {"verdict": "UNSIGNED_CANDIDATE_CHECKS_PASSED" if not errors else "FAILED", "errors": errors,
            "artifact": {"name": apk.name, "size": apk.stat().st_size, "sha256": after},
            "policy_sha256": sha256(POLICY_PATH), "baseline_commit": policy["baseline_commit"],
            "metadata": metadata, "badging": badging, "zip": inventory, "native_elf": native_elf,
            "zipalign_16_kib": aligned, "signature": state,
            "tools": {name: {"sha256": sha256(path), "name": path.name} for name, path in
                      (("aapt2", aapt), ("zipalign", align), ("apksigner_jar", signer), ("java", java))},
            "commands": commands, "tool_exit_codes": {name: result.returncode for name, result in results.items()},
            "limit": "Unsigned inspection only; not installable, signed, published, physically tested or a proof of absence of all embedded secrets."}


def compare(first: Path, second: Path, policy: dict) -> dict:
    validate_apk_path(first, policy["max_apk_bytes"])
    validate_apk_path(second, policy["max_apk_bytes"])
    before = {"first": sha256(first), "second": sha256(second)}
    left = canonical_zip(first, policy["max_apk_bytes"])
    right = canonical_zip(second, policy["max_apk_bytes"])
    after = {"first": sha256(first), "second": sha256(second)}
    require(before == after, "Artifact changed during ZIP comparison")
    a = {item["name"]: item for item in left["entries"]}
    b = {item["name"]: item for item in right["entries"]}
    changed = [name for name in sorted(a.keys() & b.keys()) if any(a[name][key] != b[name][key] for key in ("size", "crc32", "sha256"))]
    payload_equal = left["canonical_sha256"] == right["canonical_sha256"]
    identical = before["first"] == before["second"]
    verdict = "BYTE_IDENTICAL" if identical else "ZIP_CONTENT_IDENTICAL_NOT_BYTE_REPRODUCIBLE" if payload_equal else "ZIP_CONTENT_DIFFERS"
    return {"verdict": verdict, "sha256": before, "byte_identical": identical, "canonical_content_equal": payload_equal,
            "canonical_sha256": {"first": left["canonical_sha256"], "second": right["canonical_sha256"]},
            "only_first": sorted(a.keys() - b.keys()), "only_second": sorted(b.keys() - a.keys()), "changed": changed,
            "first_entries": left["entries"], "second_entries": right["entries"],
            "limit": "Compares every entry name, size, CRC and decompressed bytes. Ignores ZIP order, timestamps, compression, offsets and signing blocks; inspect each APK separately. It does not rebuild or compare source inputs."}


def emit(result: dict, output: Path | None) -> None:
    result["recorded_at"] = datetime.now(timezone.utc).isoformat()
    encoded = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if output:
        # Reject links/junctions on the supplied path before resolving it. Otherwise
        # even a link to another location inside the allowed root could redirect output.
        for part in (output.absolute(), *output.absolute().parents):
            require(not part.is_symlink(), "Evidence path must not contain symbolic links")
            if part.exists():
                require(not getattr(part.lstat(), "st_file_attributes", 0) & 0x400,
                        "Evidence path must not contain Windows reparse points")
        target = output.resolve()
        require(target.is_relative_to((ROOT / ".local-evidence" / "release").resolve()) and target.suffix == ".json",
                "Evidence must be a new JSON file under .local-evidence/release")
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("x", encoding="utf-8") as stream:
            stream.write(encoded)
        print(json.dumps({"verdict": result["verdict"], "evidence": target.relative_to(ROOT).as_posix()}))
    else:
        print(encoded, end="")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="operation", required=True)
    inspection = sub.add_parser("inspect", help="Inspect unsigned Release; never sign")
    inspection.add_argument("apk", type=Path)
    inspection.add_argument("--build-tools", type=Path, required=True, help="Existing SDK build-tools directory")
    inspection.add_argument("--java", help="Existing native Java executable; defaults to JAVA_HOME/PATH")
    inspection.add_argument("--output", type=Path)
    comparison = sub.add_parser("compare", help="Compare existing APK ZIP contents; no SDK/build invocation")
    comparison.add_argument("first", type=Path)
    comparison.add_argument("second", type=Path)
    comparison.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    try:
        policy = load_policy()
        if args.operation == "inspect":
            java = resolve_executable(args.java, "java") if args.java else java_executable()
            result = inspect(args.apk.resolve(), args.build_tools.resolve(), java, policy)
            success = result["verdict"] == "UNSIGNED_CANDIDATE_CHECKS_PASSED"
        else:
            result = compare(args.first.resolve(), args.second.resolve(), policy)
            success = result["canonical_content_equal"]
        emit(result, args.output)
        return 0 if success else 1
    except (ReleaseError, VerificationError) as exc:
        print("Release inspection: " + str(exc), file=sys.stderr)
    except (OSError, subprocess.SubprocessError, zipfile.BadZipFile, EOFError, UnicodeError, json.JSONDecodeError):
        print("Release inspection: local input/tool/evidence failure; no raw output or private data printed", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
