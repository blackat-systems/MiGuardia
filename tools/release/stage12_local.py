"""Stage12 LOCAL validators only. No device transport, installation or execution API.

The bundle contract authenticates local bytes against pinned inputs. It does not
approve business IDs, physical QA, or the truth of an arbitrary runner's claims.
Only the explicit local SDK inspection commands below can spawn a child process.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import math
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import sys
import uuid
import zipfile

import inspect_apk


QA = "com.blackatsystems.miguardia.qa"
TEST_PACKAGE = QA + ".test"
RUNNER = "com.blackatsystems.miguardia.stage12.Stage12Runner"
CERTIFICATE = "b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617"
CONSENT = "STAGE12_SYNTHETIC_20261023"
LEVELS = frozenset({"DOMAIN_JVM", "UI_COMPOSE", "ACTIVITY_NATIVE", "ROOM_ISOLATED",
                    "FILES_ISOLATED", "REMOTE_VIEWS_REAL", "OEM_HUMAN", "HOST_LOCAL"})
IDS = tuple([f"C{i:03}" for i in range(1, 101)] +
            [f"A{i:03}" for i in range(1, 287)] +
            [f"W{i:03}" for i in range(1, 25)] +
            [f"X{i:03}" for i in range(1, 16)] +
            [f"V{i:03}" for i in range(1, 17)])
BUG_IDS = frozenset(f"S12-B{i:02}" for i in range(1, 5))
MAX_BUNDLE = 32 * 1024 * 1024
MAX_MEMBER = 8 * 1024 * 1024
MAX_JSON = 4 * 1024 * 1024
MAX_TRANSCRIPT = 2 * 1024 * 1024
CALENDAR_ORACLES = {"mes4filas": ("2021-02", 4, 28), "mes5filas": ("2026-10", 5, 31),
                    "mes6filas": ("2026-08", 6, 31), "febrero-bisiesto": ("2028-02", 5, 29)}


class ValidationError(ValueError):
    """Closed validation failure; never a partially approved result."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_bounded(path: Path, maximum: int) -> bytes:
    require(path.is_file() and path.stat().st_size <= maximum, "Local input exceeds limit")
    with path.open("rb") as source:
        data = source.read(maximum + 1)
    require(len(data) <= maximum, "Local input grew beyond limit")
    return data


def sha(value, label="sha256") -> str:
    require(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None,
            "Invalid " + label)
    return value


def integer(value, label: str, maximum=2**63 - 1) -> int:
    require(type(value) is int and 0 <= value <= maximum, "Invalid integer: " + label)
    return value


def exact(value, required, optional=()) -> dict:
    require(type(value) is dict, "Expected object")
    require(set(required) <= value.keys() and value.keys() <= set(required) | set(optional),
            "Missing or unknown object fields")
    return value


def strict_json(data: bytes, maximum=MAX_JSON):
    require(type(data) is bytes and len(data) <= maximum, "JSON exceeds local limit")

    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, "Duplicate JSON key")
            result[key] = value
        return result

    def bad_constant(_):
        raise ValidationError("Non-finite JSON constant")

    try:
        result = json.loads(data.decode("utf-8"), object_pairs_hook=pairs, parse_constant=bad_constant)
    except (UnicodeError, ValueError, RecursionError) as error:
        raise ValidationError("Invalid bounded JSON") from error
    pending = [(result, 0)]
    while pending:
        item, depth = pending.pop()
        require(depth <= 32, "JSON nesting exceeds limit")
        if isinstance(item, dict):
            pending.extend((v, depth + 1) for v in item.values())
        elif isinstance(item, list):
            pending.extend((v, depth + 1) for v in item)
        elif isinstance(item, float):
            require(math.isfinite(item), "Non-finite JSON number")
    return result


def canonical_uuid(value) -> str:
    require(isinstance(value, str), "Missing run UUID")
    try:
        require(str(uuid.UUID(value)) == value, "Noncanonical run UUID")
    except (ValueError, AttributeError) as error:
        raise ValidationError("Invalid run UUID") from error
    return value


def safe_name(value: str) -> str:
    require(isinstance(value, str) and 0 < len(value) <= 240 and "\\" not in value,
            "Invalid evidence path")
    require(not any(ord(char) < 32 or ord(char) == 127 for char in value) and ":" not in value,
            "Invalid evidence path characters")
    parts = value.split("/")
    require(not value.startswith("/") and all(part not in {"", ".", ".."} and
            not part.endswith((".", " ")) for part in parts), "Unsafe evidence path")
    require(str(PurePosixPath(value)) == value, "Noncanonical evidence path")
    return value


def selector(value) -> str:
    require(isinstance(value, str) and re.fullmatch(
        r"com\.blackatsystems\.miguardia\.(?:[A-Za-z][A-Za-z0-9_]*\.)*[A-Za-z][A-Za-z0-9_]*#[a-z][A-Za-z0-9_]*", value),
        "One exact MiGuardia class#method required")
    return value


def validate_arguments(arguments: dict, expected: dict) -> dict:
    exact(arguments, ("class", "stage12Consent", "stage12RunId", "stage12QaSha256",
                      "stage12RunnerSha256", "stage12Mode"))
    allowed = expected.get("allowed_selectors", [])
    require(type(allowed) is list and allowed and len(allowed) == len(set(allowed)),
            "Reviewed exact selector allowlist required")
    for value in allowed:
        selector(value)
    require(selector(arguments["class"]) in allowed and arguments["class"] == expected["selector"],
            "Selector not admitted by the exact local allowlist")
    require(arguments["stage12Consent"] == CONSENT, "Consent differs")
    require(canonical_uuid(arguments["stage12RunId"]) == canonical_uuid(expected["run_id"]), "Run differs")
    require(sha(arguments["stage12QaSha256"]) == sha(expected["qa_sha256"]), "QA hash differs")
    require(sha(arguments["stage12RunnerSha256"]) == sha(expected["runner_sha256"]), "Runner hash differs")
    require(arguments["stage12Mode"] in {"neutral", "quiet"} and
            arguments["stage12Mode"] == expected["mode"], "Mode differs or has an alias")
    return dict(arguments)


def _regular_local(path: Path, names=()) -> Path:
    path = Path(path).absolute()
    for item in (path, *path.parents):
        info = item.lstat()
        require(not stat.S_ISLNK(info.st_mode) and not
                getattr(info, "st_file_attributes", 0) & 0x400, "Symlink/reparse input refused")
    require(path.is_file() and (not names or path.name.lower() in names), "Wrong local input/tool")
    return path


def _sdk_output(command: list[str]) -> str:
    # No shell, generic command parameter, environment transport or device API.
    require(Path(command[0]).name.lower() in {"aapt2", "aapt2.exe", "java", "java.exe"},
            "Only local SDK inspection is available")
    try:
        result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                                errors="strict", timeout=30, shell=False,
                                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    except (OSError, UnicodeError, subprocess.TimeoutExpired) as error:
        raise ValidationError("Local SDK inspection did not complete") from error
    require(result.returncode == 0, "Local SDK inspection failed")
    require(len(result.stdout) + len(result.stderr) <= MAX_TRANSCRIPT, "SDK output exceeds limit")
    return result.stdout


def inspect_local_apk(apk: Path, expected_sha: str, kind: str, aapt2: Path,
                      java: Path, apksigner_jar: Path) -> dict:
    """Read only a pinned local APK using three fixed SDK inspection invocations."""
    require(kind in {"qa", "runner"}, "Unknown APK role")
    apk = _regular_local(apk)
    require(0 < apk.stat().st_size <= 256 * 1024 * 1024, "APK size outside limit")
    before = apk.read_bytes()
    require(digest(before) == sha(expected_sha), "Pinned local APK hash differs")
    try:
        with zipfile.ZipFile(io.BytesIO(before)) as archive:
            require(archive.namelist().count("AndroidManifest.xml") == 1, "APK manifest missing/duplicated")
            require(len(archive.namelist()) == len(set(archive.namelist())), "Duplicate APK ZIP entries")
    except (zipfile.BadZipFile, OSError) as error:
        raise ValidationError("Invalid APK ZIP") from error
    aapt2 = _regular_local(aapt2, {"aapt2", "aapt2.exe"})
    java = _regular_local(java, {"java", "java.exe"})
    apksigner_jar = _regular_local(apksigner_jar, {"apksigner.jar"})
    require(apksigner_jar.parent.name == "lib", "Explicit SDK lib/apksigner.jar required")
    xml = _sdk_output([str(aapt2), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"])
    badging = _sdk_output([str(aapt2), "dump", "badging", str(apk)])
    signature = _sdk_output([str(java), "-jar", str(apksigner_jar), "verify", "--verbose", "--print-certs", str(apk)])
    try:
        root = inspect_apk.parse_xmltree(xml)
    except ValueError as error:
        raise ValidationError("Untrusted APK manifest metadata") from error
    attrs = root["attributes"]
    package = QA if kind == "qa" else TEST_PACKAGE
    require(attrs.get("package") == package and "split" not in attrs, "Wrong APK package or split")
    packages = re.findall(r"^package: (.+)$", badging, re.M)
    require(len(packages) == 1, "Badging package absent/duplicated")
    fields = re.findall(r"([A-Za-z]+)='([^']*)'", packages[0])
    require(len(fields) == len(dict(fields)), "Duplicate badging identity field")
    fields = dict(fields)
    require(fields.get("name") == package and "split" not in fields, "Manifest/badging identity differs")
    certs = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})\s*$", signature, re.M)
    require(certs == [CERTIFICATE], "Technical certificate missing/wrong/multiple")
    instrumentations = inspect_apk.direct(root, "instrumentation")
    if kind == "qa":
        require(not instrumentations, "Product APK must not be instrumentation")
        require(re.fullmatch(r"[1-9][0-9]*", fields.get("versionCode", "")) and
                bool(fields.get("versionName")), "Missing QA version identity")
    else:
        require(len(instrumentations) == 1, "One instrumentation required")
        instrumentation = instrumentations[0]["attributes"]
        require(instrumentation.get("android:name") == RUNNER and
                instrumentation.get("android:targetPackage") == QA, "Wrong Stage12 runner/target")
    require(digest(apk.read_bytes()) == expected_sha, "APK changed during inspection")
    return {"role": kind, "sha256": expected_sha, "package": package,
            "version_code": fields.get("versionCode") or None,
            "version_name": fields.get("versionName") or None,
            "certificate_sha256": CERTIFICATE, "runner": RUNNER if kind == "runner" else None,
            "target_package": QA if kind == "runner" else None, "local_only": True}


def parse_terminal(text: str, returncode: int, timed_out: bool, expected: dict) -> dict:
    require(type(returncode) is int and returncode == 0 and timed_out is False, "Process failed/timed out")
    require(isinstance(text, str) and len(text.encode("utf-8")) <= MAX_TRANSCRIPT, "Transcript too large")
    require(not re.search(r"\bFAILURES?\b|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed", text, re.I),
            "Failure marker in successful process output")
    # A multiline stream can contain a raw stack without a `stack=` record.
    # Such a contradiction never becomes success merely because code 0 follows.
    require(not re.search(r"(?:^|\n)\s*(?:Caused by:\s*)?(?:[A-Za-z_$][\w$]*\.)+"
                          r"(?:[A-Za-z_$][\w$]*)?(?:Exception|Error)(?::|\s|$)", text),
            "Exception/error stream contradicts successful method output")
    klass, method = selector(expected["selector"]).split("#")
    block, states, terminal, result = {}, [], [], {}
    for line in text.splitlines():
        status = re.fullmatch(r"INSTRUMENTATION_STATUS: ([A-Za-z0-9_]+)=(.*)", line)
        code = re.fullmatch(r"INSTRUMENTATION_STATUS_CODE: (-?[0-9]+)", line)
        item = re.fullmatch(r"INSTRUMENTATION_RESULT: ([A-Za-z0-9_]+)=(.*)", line)
        end = re.fullmatch(r"INSTRUMENTATION_CODE: (-?[0-9]+)", line)
        if status:
            require(not result and not terminal and status[1] not in block and
                    status[1] in {"class", "test", "numtests", "current", "id", "stream"},
                    "Duplicate/unexpected/out-of-order status field")
            block[status[1]] = status[2]
        elif code:
            require(not result and not terminal and block.get("class") == klass and block.get("test") == method and
                    block.get("numtests") == "1" and block.get("current", "1") == "1",
                    "Wrong or incomplete method accounting")
            states.append(int(code[1])); block = {}
        elif item:
            require(states == [1, 0] and not block and not terminal and item[1] not in result and
                    (item[1].startswith("stage12") or item[1] == "stream"),
                    "Duplicate/unexpected/out-of-order result field")
            result[item[1]] = item[2]
        elif end:
            require(states == [1, 0] and not block and result and not terminal, "Premature/duplicate terminal")
            terminal.append(int(end[1]))
        elif line.lstrip().startswith("INSTRUMENTATION_"):
            raise ValidationError("Malformed/unexpected instrumentation record")
    require(not block and states == [1, 0] and terminal == [-1], "Incomplete/failing/skipped method or terminal")
    require(text.strip().splitlines()[-1] == "INSTRUMENTATION_CODE: -1", "Output after terminal or partial result")
    keys = {"stage12Status", "stage12RunId", "stage12Bundle", "stage12BundleSha256",
            "stage12QaSha256", "stage12RunnerSha256"}
    require(keys <= result.keys() and result.keys() <= keys | {"stream"}, "Missing/unexpected terminal proof")
    require(result["stage12Status"] == "COMPLETE", "Runner did not complete")
    require(canonical_uuid(result["stage12RunId"]) == expected["run_id"], "Terminal run differs")
    require(result["stage12Bundle"] == "stage12-" + expected["run_id"] + ".zip", "Wrong evidence basename")
    sha(result["stage12BundleSha256"])
    require(result["stage12QaSha256"] == sha(expected["qa_sha256"]) and
            result["stage12RunnerSha256"] == sha(expected["runner_sha256"]), "Terminal candidate differs")
    return result


def _inventory(value: dict, tables=False) -> dict:
    exact(value, ("count", "entries"))
    require(type(value["entries"]) is dict and integer(value["count"], "inventory count", 100000) ==
            len(value["entries"]), "Inventory count differs")
    folded = set()
    for name, item in value["entries"].items():
        safe_name(name)
        require(name.casefold() not in folded, "Inventory case collision")
        folded.add(name.casefold())
        key = "rows" if tables else "bytes"
        exact(item, (key, "sha256"))
        integer(item[key], key); sha(item["sha256"])
    return value


def validate_isolation(value: dict, expected: dict) -> dict:
    exact(value, ("schema", "mode", "root_id", "files", "preferences", "tables", "resources"))
    require(value["schema"] == "stage12-isolation-v1" and value["mode"] in {"neutral", "quiet"} and
            value["mode"] == expected["mode"] and canonical_uuid(value["root_id"]) == expected["run_id"],
            "Isolation identity/mode differs")
    for kind in ("files", "preferences"):
        pair = exact(value[kind], ("before", "after"))
        require(_inventory(pair["before"]) == _inventory(pair["after"]), "Received " + kind + " changed")
    tables = value["tables"]
    require(type(tables) is dict, "Tables measurement missing")
    if tables.get("status") == "NOT_MEASURED":
        exact(tables, ("status", "reason"))
        require(isinstance(tables["reason"], str) and tables["reason"].strip(), "Unmeasured table reason missing")
        require(not any(a.get("requires_tables") is True or a["level"] == "ROOM_ISOLATED"
                        for a in expected["assertions"]), "Required SQLite evidence was NOT_MEASURED")
    else:
        exact(tables, ("status", "before", "after"))
        require(tables["status"] == "MEASURED" and
                _inventory(tables["before"], True) == _inventory(tables["after"], True), "Received tables changed")
    resources = exact(value["resources"], ("scoped", "identities"))
    require(resources["scoped"] is True and type(resources["identities"]) is list and
            all(type(i) is str and i for i in resources["identities"]), "Resources are not scoped")
    allowed_resources = expected.get("resource_identities", [])
    require(type(allowed_resources) is list and all(type(i) is str and i for i in allowed_resources) and
            len(allowed_resources) == len(set(allowed_resources)) and
            resources["identities"] == allowed_resources, "Unreviewed or duplicate resource identities")
    return {"mode": value["mode"], "table_evidence": tables["status"],
            "files_count": value["files"]["before"]["count"],
            "preferences_count": value["preferences"]["before"]["count"]}


def _assertion_identity(value: dict, receipt=False) -> tuple:
    keys = ("case_id", "subcase_id", "round", "assertion_key", "level", "evidence_kind")
    exact(value, keys + (("result",) if receipt else ()), ("requires_tables",))
    require(value["case_id"] in set(IDS) | BUG_IDS, "Unknown case ID")
    require(type(value["round"]) is int and value["round"] in ({0, 1, 2} if value["case_id"] in BUG_IDS else {1, 2}),
            "Wrong case round")
    require(value["level"] in LEVELS and type(value.get("requires_tables", False)) is bool,
            "Assertion level/table requirement is ambiguous")
    for key in ("subcase_id", "assertion_key", "evidence_kind"):
        require(type(value[key]) is str and 0 < len(value[key]) <= 200, "Missing assertion identity")
    if receipt:
        require(value["result"] == "PASS", "Assertion failed/skipped/incomplete")
    return tuple(value[key] for key in keys) + (value.get("requires_tables", False),)


def validate_bundle(data: bytes, proof: dict, expected: dict) -> dict:
    require(type(data) is bytes and 0 < len(data) <= MAX_BUNDLE and
            digest(data) == proof["stage12BundleSha256"], "Bundle missing/oversized/hash differs")
    payload = {}
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            infos = archive.infolist()
            require(3 <= len(infos) <= 2048, "Wrong bundle member count")
            seen, total = set(), 0
            for info in infos:
                require(info.orig_filename == info.filename, "ZIP name was normalized/truncated")
                safe_name(info.orig_filename)
                safe_name(info.filename)
                require(info.filename.casefold() not in seen and not info.is_dir(), "Duplicate/directory member")
                seen.add(info.filename.casefold())
                require(not info.flag_bits & 1 and info.compress_type in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED},
                        "Encrypted/unsupported member")
                require(stat.S_IFMT(info.external_attr >> 16) in {0, stat.S_IFREG}, "Special file member")
                total += info.file_size
                require(info.file_size <= MAX_MEMBER and total <= MAX_BUNDLE and
                        info.file_size <= max(1024, info.compress_size * 100), "Archive expansion exceeds limit")
                content = archive.read(info)
                require(len(content) == info.file_size, "Truncated member")
                payload[info.filename] = content
    except (OSError, RuntimeError, zipfile.BadZipFile, EOFError, NotImplementedError) as error:
        raise ValidationError("Invalid evidence ZIP/CRC") from error
    require({"bundle.json", "receipt.json", "isolation.json"} <= payload.keys(), "Required bundle files missing")
    manifest = strict_json(payload["bundle.json"])
    exact(manifest, ("schema", "run_id", "qa_sha256", "runner_sha256", "selector", "expected_methods", "counts", "files"))
    require(manifest["schema"] == "stage12-evidence-v1" and manifest["run_id"] == expected["run_id"] and
            manifest["qa_sha256"] == expected["qa_sha256"] and manifest["runner_sha256"] == expected["runner_sha256"] and
            manifest["selector"] == expected["selector"] and manifest["expected_methods"] == [expected["selector"]],
            "Bundle identity/selection differs")
    counts = exact(manifest["counts"], ("expected", "started", "passed", "failed", "skipped"))
    for key, target in {"expected": 1, "started": 1, "passed": 1, "failed": 0, "skipped": 0}.items():
        require(integer(counts[key], "method " + key) == target, "Counts omit/fail/duplicate methods")
    require(type(manifest["files"]) is list, "Bundle inventory missing")
    listed = {}
    for item in manifest["files"]:
        exact(item, ("path", "bytes", "sha256")); safe_name(item["path"])
        require(item["path"] not in listed and item["path"] != "bundle.json", "Duplicate/self manifest inventory")
        listed[item["path"]] = item
        require(item["path"] in payload and integer(item["bytes"], "member bytes", MAX_MEMBER) ==
                len(payload[item["path"]]) and sha(item["sha256"]) == digest(payload[item["path"]]), "Member size/hash differs")
    require(set(listed) == payload.keys() - {"bundle.json"}, "Unlisted or missing bundle content")
    receipt = strict_json(payload["receipt.json"])
    exact(receipt, ("schema", "run_id", "assertions"))
    require(receipt["schema"] == "stage12-receipt-v1" and receipt["run_id"] == expected["run_id"] and
            type(receipt["assertions"]) is list, "Receipt identity missing")
    require(type(expected["assertions"]) is list and expected["assertions"], "Expected assertions missing")
    wanted = [_assertion_identity(a) for a in expected["assertions"]]
    actual = [_assertion_identity(a, True) for a in receipt["assertions"]]
    require(len(wanted) == len(set(wanted)) and len(actual) == len(set(actual)) and sorted(actual) == sorted(wanted),
            "Assertions missing/duplicate/wrong level or evidence kind")
    isolation = validate_isolation(strict_json(payload["isolation.json"]), expected)
    return {"validation": "LOCAL_FORMAT_AND_TRANSPORT_VALID", "case_approval": "NOT_GRANTED",
            "assertion_count": len(actual), "bundle_sha256": digest(data), "isolation": isolation}


def validate_local_result(text: str, returncode: int, timed_out: bool, data: bytes, expected: dict) -> dict:
    validate_arguments({"class": expected["selector"], "stage12Consent": CONSENT,
                        "stage12RunId": expected["run_id"], "stage12QaSha256": expected["qa_sha256"],
                        "stage12RunnerSha256": expected["runner_sha256"], "stage12Mode": expected["mode"]}, expected)
    proof = parse_terminal(text, returncode, timed_out, expected)
    return validate_bundle(data, proof, expected)


def validate_plan(plan: dict, baseline: dict, contract: str, contract_sha256=None) -> dict:
    require(sha(plan["contract_sha256"]) == (sha(contract_sha256) if contract_sha256 is not None else
            digest(contract.encode("utf-8"))), "Plan contract hash differs")
    rows = plan.get("rows")
    require(type(rows) is list and len(rows) == 441 and {r["id"] for r in rows} == set(IDS), "441 unique IDs required")
    require("AND_OR" not in json.dumps(plan, ensure_ascii=False), "Ambiguous level remains")
    requirements = dict(re.findall(r"^\| ([CAWXV][0-9]{3}) \| (.+) \|\s*$", contract, re.M))
    current_subcases = [s["id"] for r in rows for s in r["subcases"]]
    original_subcases = [s["id"] for r in baseline["rows"] for s in r["subcases"]]
    require(len(current_subcases) == len(set(current_subcases)) == 506 and
            set(current_subcases) == set(original_subcases), "506 base subcases must be preserved exactly")
    require({r["id"]: {s["id"] for s in r["subcases"]} for r in rows} ==
            {r["id"]: {s["id"] for s in r["subcases"]} for r in baseline["rows"]},
            "Base subcases moved to a different requirement")
    require(plan["round_policy"] == {"required_complete_rounds": 2, "same_final_candidate": True,
            "base_subcases_per_round": 506, "required_case_rounds": 1012}, "Two fixed complete rounds required")
    by_id = {row["id"]: row for row in rows}
    for row in rows:
        require(row["requirement"] == requirements.get(row["id"]) and row["state"] == "SIN_EJECUTAR", "Changed requirement/state")
        require(row["execution_admission"]["allowed"] is False, "Local2 may not admit a business family/method")
        own_cases = {s["id"] for s in row["subcases"]}
        assertions = row["assertions"]
        require(assertions and len({a["key"] for a in assertions}) == len(assertions), "Missing/duplicate assertion keys")
        for assertion in assertions:
            require(assertion["subcase_id"] in own_cases and assertion["required_level"] in LEVELS and
                    assertion["mandatory"] is True and assertion["execution"] == "NONE", "Assertion lacks exact level/scope")
        for case in row["subcases"]:
            own_keys = {a["key"] for a in assertions if a["subcase_id"] == case["id"]}
            require(case["state"] == "SIN_EJECUTAR" and case["oracle"]["expected"] and
                    type(case["oracle"]["assertion_keys"]) is list and own_keys and
                    len(case["oracle"]["assertion_keys"]) == len(own_keys) and
                    set(case["oracle"]["assertion_keys"]) == own_keys,
                    "Subcase lacks its complete explicit oracle")
    for suffix, (month, count, days) in CALENDAR_ORACLES.items():
        case = next(s for s in by_id["C001"]["subcases"] if s["id"] == "C001-" + suffix)
        require(case["oracle"]["calendar"] == {"month": month, "rows": count, "days": days}, "Wrong C001 calendar oracle")
    require(by_id["C090"]["assertion_map"]["coverage"] == "PARTIAL", "C090 source coverage was overstated")
    require(by_id["C090"]["identity_delete_oracle"]["cancel_keeps_both"] is True and
            by_id["C090"]["identity_delete_oracle"]["only_difference"] == "UUID" and
            by_id["C090"]["identity_delete_oracle"]["cas_required"] is True, "C090 identity/CAS oracle missing")
    require(by_id["W023"]["resource_gate"]["forbidden_pending_intent_request_code"] == 29082026 and
            by_id["W023"]["resource_gate"]["admitted"] is False, "Fixed Widget PendingIntent was admitted")
    require(by_id["V013"]["no_global_talkback_activation"] is True, "Global TalkBack activation must remain forbidden")
    for key in ("C058", "V016"):
        require(by_id[key]["capture_policy"] == "QA_WINDOW_OR_NODE_ONLY_NO_GLOBAL_SCREENSHOT_NO_GLOBAL_XML", "Unsafe capture/hierarchy policy")
    require(set(by_id["X015"]["received_conservation"]) == {"tables", "preferences", "files"}, "Received conservation incomplete")
    require({"DOMAIN_JVM", "REMOTE_VIEWS_REAL"} <= {a["required_level"] for a in by_id["W010"]["assertions"]},
            "Hidden Widget needs projection and real RemoteViews assertions")
    return {"validation": "LOCAL_PLAN_VALID", "ids": 441, "base_subcases": 506,
            "required_case_rounds": 1012, "executed_cases": 0, "admitted_methods": 0}


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("validate-plan")
    p.add_argument("--plan", type=Path, required=True); p.add_argument("--baseline", type=Path, required=True)
    p.add_argument("--contract", type=Path, required=True)
    p = sub.add_parser("inspect-input")
    p.add_argument("--apk", type=Path, required=True); p.add_argument("--sha256", required=True)
    p.add_argument("--kind", choices=("qa", "runner"), required=True)
    p.add_argument("--aapt2", type=Path, required=True); p.add_argument("--java", type=Path, required=True)
    p.add_argument("--apksigner-jar", type=Path, required=True)
    p = sub.add_parser("validate-result")
    p.add_argument("--transcript", type=Path, required=True); p.add_argument("--bundle", type=Path, required=True)
    p.add_argument("--expected", type=Path, required=True); p.add_argument("--returncode", type=int, required=True)
    p.add_argument("--timed-out", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.command == "validate-plan":
            contract_bytes = read_bounded(args.contract, MAX_JSON)
            result = validate_plan(strict_json(read_bounded(args.plan, 16 * 1024 * 1024), 16 * 1024 * 1024),
                                   strict_json(read_bounded(args.baseline, 16 * 1024 * 1024), 16 * 1024 * 1024),
                                   contract_bytes.decode("utf-8-sig"), digest(contract_bytes))
        elif args.command == "inspect-input":
            result = inspect_local_apk(args.apk, args.sha256, args.kind, args.aapt2, args.java, args.apksigner_jar)
        else:
            result = validate_local_result(read_bounded(args.transcript, MAX_TRANSCRIPT).decode("utf-8"), args.returncode,
                                           args.timed_out, read_bounded(args.bundle, MAX_BUNDLE),
                                           strict_json(read_bounded(args.expected, MAX_JSON)))
        print(json.dumps(result, ensure_ascii=True, sort_keys=True))
        return 0
    except (ValidationError, OSError, UnicodeError, KeyError, TypeError, StopIteration) as error:
        print(json.dumps({"validation": "REJECTED", "error_type": type(error).__name__}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
