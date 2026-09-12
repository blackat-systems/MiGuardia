#!/usr/bin/env python3
"""Plan and run explicitly authorized MiGuardia instrumentation, using only stdlib.

Planning and catalog checks never invoke adb. Execution never installs, clears or
uninstalls packages. Each invocation begins and ends with a force-stop of the
allowlisted test target; this isolates processes, not persisted fixture data.
Raw device output is parsed in memory and is never written to evidence files.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
RUNNER = "androidx.test.runner.AndroidJUnitRunner"
MODULES = {
    "app": {
        "source": "app/src/androidTest",
        "target": "com.blackatsystems.miguardia.qa",
        "test_package": "com.blackatsystems.miguardia.qa.test",
        "apks": {
            "com.blackatsystems.miguardia.qa": "app/build/outputs/apk/qa/app-qa.apk",
            "com.blackatsystems.miguardia.qa.test": "app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk",
        },
    },
    "database": {
        "source": "core/database/src/androidTest",
        "target": "com.blackatsystems.miguardia.core.database.test",
        "test_package": "com.blackatsystems.miguardia.core.database.test",
        "apks": {
            "com.blackatsystems.miguardia.core.database.test": "core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk",
        },
    },
}
PERMISSIONS = {
    "qa-mutations", "disposable-data", "beta-fixture", "backup-restore",
    "system-effects", "real-alarms", "temporary-session", "signed-fixtures",
    "existing-session",
}
# Catalog entries may select these exact opt-ins, never arbitrary shell/runner args.
INSTRUMENT_ARGS = {
    "updates28TemporarySession": "authorized",
    "updates28LocalDownload": "authorized",
    "updatesPublicSaf": "authorized",
    "publicColdRoot": "authorized",
    "hoursReviewRace": "ALLOW_ISOLATED_REPOSITORIES",
    "updates28FixtureDirectory": "updates28-fixtures",
    "support27TemporarySession": "authorized",
    "creditsTemporarySession": "authorized",
}
CLASS_RE = re.compile(r"[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)+")
METHOD_RE = re.compile(r"[A-Za-z_]\w*")
SERIAL_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")


class QaError(ValueError):
    """An invalid or unsafe plan, reported without arbitrary command output."""


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def discover(root: Path) -> tuple[dict, dict]:
    classes, support = {}, {}
    for module, config in MODULES.items():
        for path in sorted((root / config["source"]).rglob("*.kt")):
            source = path.read_text(encoding="utf-8-sig")
            source_hash = hashlib.sha256(source.encode("utf-8")).hexdigest()
            relative = path.relative_to(root).as_posix()
            if not re.search(r"@Test\b", source):
                support[relative] = source_hash
                continue
            package = re.search(r"^package\s+([\w.]+)", source, re.M)
            names = re.findall(r"^class\s+(\w+)", source, re.M)
            methods = re.findall(r"@Test(?:\([^)]*\))?\s*(?:@[^\n]+\s*)*fun\s+(\w+)\s*\(", source)
            if not package or len(names) != 1 or len(methods) != len(re.findall(r"@Test\b", source)):
                raise QaError(f"Test declarations require catalog parser review: {relative}")
            name = package[1] + "." + names[0]
            if name in classes:
                raise QaError(f"Duplicate test class: {name}")
            classes[name] = {"module": module, "source": relative, "sha256": source_hash, "methods": methods}
    for path in sorted((root / "app/src/testSupport").rglob("*.kt")):
        support[path.relative_to(root).as_posix()] = hashlib.sha256(path.read_text(encoding="utf-8-sig").encode("utf-8")).hexdigest()
    return classes, support


def load_catalog(root: Path = ROOT) -> dict:
    try:
        catalog = json.loads((root / "tools/qa-groups.json").read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise QaError("Cannot read tools/qa-groups.json") from exc
    validate_catalog(catalog, root)
    return catalog


def validate_catalog(catalog: dict, root: Path) -> None:
    actual, support = discover(root)
    if catalog.get("version") != 1 or catalog.get("source_hash_normalization") != "UTF-8/LF/no-BOM" or catalog.get("support_sources") != support:
        raise QaError("Catalog version or shared fixtures changed; review and update their classification/hashes")
    declared = catalog.get("classes", {})
    if set(actual) != set(declared):
        missing = sorted(set(actual) - set(declared))
        extra = sorted(set(declared) - set(actual))
        raise QaError(f"Instrumentation coverage differs; missing={missing}, absent={extra}")
    groups = catalog.get("groups", {})
    memberships = {name: [] for name in actual}
    for name, entry in declared.items():
        if not CLASS_RE.fullmatch(name) or entry != actual[name]:
            raise QaError(f"Changed test source/methods require classification review: {name}")
    for key, group in groups.items():
        if not re.fullmatch(r"[a-z][a-z0-9-]*", key):
            raise QaError("Invalid group identifier")
        if group.get("module") not in MODULES or group.get("process") != "fresh-per-invocation":
            raise QaError(f"Invalid target/process policy: {key}")
        if not group.get("fixture_state") or not group.get("hazards"):
            raise QaError(f"Missing fixture state or effects: {key}")
        requirements = group.get("requires", [])
        if not set(requirements) <= PERMISSIONS or len(set(requirements)) != len(requirements):
            raise QaError(f"Invalid permissions: {key}")
        args = group.get("instrument_args", {})
        if any(INSTRUMENT_ARGS.get(k) != v for k, v in args.items()):
            raise QaError(f"Unapproved instrumentation argument: {key}")
        invocations = group.get("invocations", [])
        if not invocations:
            raise QaError(f"Empty group: {key}")
        covered_methods = {}
        for target in invocations:
            parts = target.split("#")
            if len(parts) > 2 or not CLASS_RE.fullmatch(parts[0]):
                raise QaError(f"Unsafe test selector: {key}")
            cls = parts[0]
            if cls not in actual or actual[cls]["module"] != group["module"]:
                raise QaError(f"Unknown or cross-package class: {key}")
            methods = actual[cls]["methods"] if len(parts) == 1 else [parts[1]]
            if any(not METHOD_RE.fullmatch(m) or m not in actual[cls]["methods"] for m in methods):
                raise QaError(f"Unknown test method: {key}")
            prior = covered_methods.setdefault(cls, set())
            if prior.intersection(methods):
                raise QaError(f"Repeated test selection: {key}")
            prior.update(methods)
        for cls, methods in covered_methods.items():
            if methods != set(actual[cls]["methods"]):
                raise QaError(f"Group must account for every method in {cls}")
            source = (root / actual[cls]["source"]).read_text(encoding="utf-8-sig")
            if any(marker in source for marker in ("preparePublicRootAndMarkOnboardingCompletedForTest(", "preparePublicRootAndResetOnboardingForTest(")):
                if "disposable-data" not in requirements:
                    raise QaError(f"Public onboarding fixture lacks disposable-data authorization: {cls}")
            if "BetaQaAccessFixture.activate" in source:
                raise QaError(f"Retired beta activation fixture cannot gate the public application: {cls}")
            if "clearAllDataForInstrumentation" in source and "disposable-data" not in requirements:
                raise QaError(f"QA data clearing lacks disposable-data authorization: {cls}")
            if cls.endswith(".PublicColdRootTest") and (not group.get("manual_only") or not {"qa-mutations", "disposable-data", "beta-fixture"} <= set(requirements)):
                raise QaError("Cold residual preparation requires manual disposable QA and explicit beta fixture authorization")
            if cls.endswith(".PublicUpgradeBackupFixtureTest") and (not group.get("manual_only") or not {"qa-mutations", "disposable-data", "backup-restore"} <= set(requirements)):
                raise QaError("Upgrade data preparation requires supervised disposable QA and backup/restore authorization")
            if cls.endswith(".HoursExtraRestorationRaceInstrumentedTest") and (not {"qa-mutations", "disposable-data"} <= set(requirements) or args.get("hoursReviewRace") != "ALLOW_ISOLATED_REPOSITORIES"):
                raise QaError("Hours restoration diagnosis requires disposable QA and explicit isolated repositories")
            if cls.endswith(".BetaClosedRootTest") and args:
                raise QaError("Closed-root group cannot enable instrumentation fixtures")
            memberships[cls].append(key)
    if any(len(items) != 1 for items in memberships.values()):
        raise QaError("Every instrumentation class must belong to exactly one explicit group")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--check-catalog", action="store_true", help="Validate every class, method and fixture without adb")
    result.add_argument("--list", action="store_true", help="List plans and their effects without adb")
    result.add_argument("--group", help="One catalog group; no implicit all-tests execution")
    result.add_argument("--class", dest="test_class", help="Optional single class within the selected group")
    result.add_argument("--execute", action="store_true", help="Run the reviewed plan; default only prints it")
    result.add_argument("--authorized-device-use", action="store_true", help="Declare specific authorization for this device run")
    result.add_argument("--serial", help="Exact authorized adb serial (also supports emulator serials)")
    result.add_argument("--adb", default="adb", help="Existing adb executable; never installed by this tool")
    result.add_argument("--timeout", type=int, default=600, help="Per-class seconds, 30..1800")
    for permission in sorted(PERMISSIONS):
        result.add_argument("--allow-" + permission, action="store_true", help="Declare specific authorization: " + permission)
    return result


def make_plan(catalog: dict, args: argparse.Namespace) -> dict:
    if not args.group or args.group not in catalog["groups"]:
        raise QaError("Select one existing --group; use --list")
    if not 30 <= args.timeout <= 1800:
        raise QaError("Timeout must be between 30 and 1800 seconds")
    if args.serial is not None and not SERIAL_RE.fullmatch(args.serial):
        raise QaError("Invalid explicit device serial")
    group = catalog["groups"][args.group]
    selected = group["invocations"]
    if args.test_class:
        if not CLASS_RE.fullmatch(args.test_class):
            raise QaError("--class must be one exact catalog class, without method, comma or shell syntax")
        selected = [x for x in selected if x.split("#")[0] == args.test_class]
        if not selected:
            raise QaError("Class is not in the selected group")
    granted = {x for x in PERMISSIONS if getattr(args, "allow_" + x.replace("-", "_"))}
    missing = set(group["requires"]) - granted
    if args.execute:
        if not args.authorized_device_use or not args.serial:
            raise QaError("Execution requires --execute, --authorized-device-use and an exact --serial")
        if group.get("manual_only"):
            raise QaError("Supervised plan only: " + group["manual_only"])
        if missing:
            raise QaError("Specific authorization missing: " + ", ".join("--allow-" + x for x in sorted(missing)))
    config = MODULES[group["module"]]
    invocations = []
    for target in selected:
        cls, _, method = target.partition("#")
        methods = [method] if method else catalog["classes"][cls]["methods"]
        invocations.append({"selector": target, "expected_methods": methods})
    return {
        "mode": "execute" if args.execute else "plan", "group": args.group,
        "target_package": config["target"], "test_package": config["test_package"],
        "runner": RUNNER, "module": group["module"], "serial": args.serial,
        "fixture_state": group["fixture_state"], "hazards": group["hazards"],
        "process": group["process"], "requires": group["requires"],
        "missing_authorization": sorted(missing), "instrument_args": group.get("instrument_args", {}),
        "manual_only": group.get("manual_only"), "invocations": invocations,
        "note": "Process isolation does not restore persisted data, preferences, permissions or fixtures. No install/clear/uninstall is performed.",
    }


def parse_instrumentation(output: str, returncode: int, selector: str, methods: list[str]) -> dict:
    cls = selector.split("#")[0]
    statuses, bundle = {}, {}
    unexpected, signals = False, []
    terminal = None
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, _, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            if key in {"class", "test"}:
                bundle[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            try:
                code = int(line.split(":", 1)[1])
            except ValueError:
                signals.append("malformed-status")
                code = None
            if code in {0, -1, -2, -3, -4} and "class" in bundle and "test" in bundle:
                method = bundle["test"]
                if bundle["class"] != cls or method not in methods or method in statuses:
                    unexpected = True
                else:
                    statuses[method] = "passed" if code == 0 else "skipped" if code in {-3, -4} else "failed"
            elif code in {-1, -2}:
                signals.append("runner-error")
            bundle = {}
        elif line.startswith("INSTRUMENTATION_CODE: "):
            try:
                terminal = int(line.split(":", 1)[1])
            except ValueError:
                signals.append("malformed-terminal")
        if any(marker in line for marker in ("INSTRUMENTATION_FAILED", "INSTRUMENTATION_ABORTED", "FAILURES!!!", "shortMsg=")):
            signals.append("instrumentation-failure")
    counts = {name: list(statuses.values()).count(name) for name in ("passed", "failed", "skipped")}
    pending = [method for method in methods if method not in statuses]
    failed = returncode != 0 or counts["failed"] > 0 or bool(signals) or unexpected or terminal != -1
    verdict = "FAILED" if failed else "PENDING" if pending or counts["skipped"] else "PASSED"
    return {"selector": selector, "verdict": verdict, **counts, "pending": len(pending),
            "methods": statuses, "pending_methods": pending, "exit_code": returncode,
            "terminal_code": terminal, "unexpected_result": unexpected,
            "signals": sorted(set(signals)),
            "output_sha256": hashlib.sha256(output.encode("utf-8")).hexdigest()}


class Adb:
    """Narrow command interface. No user-provided shell command is accepted."""
    def __init__(self, executable: str, serial: str):
        if not SERIAL_RE.fullmatch(serial):
            raise QaError("Invalid serial")
        resolved = shutil.which(executable)
        if not resolved or not Path(resolved).is_file():
            raise QaError("Existing adb executable not found")
        if Path(resolved).name.lower() not in {"adb", "adb.exe"}:
            raise QaError("--adb must resolve to the adb executable, not a shell or another program")
        self.prefix = [str(Path(resolved).resolve()), "-s", serial]

    def run(self, tail: list[str], timeout: int = 30) -> subprocess.CompletedProcess:
        # Every caller supplies an allowlisted constant command and validated values.
        return subprocess.run(self.prefix + tail, capture_output=True, text=True,
                              encoding="utf-8", errors="replace", timeout=timeout, shell=False)

    def required(self, tail: list[str]) -> str:
        result = self.run(tail)
        if result.returncode:
            raise QaError("Device metadata/preparation command failed; raw device output was not saved")
        return result.stdout.strip()

    def force_stop(self, package: str) -> None:
        if package not in {v["target"] for v in MODULES.values()}:
            raise QaError("Package is not an authorized QA target")
        self.required(["shell", "am", "force-stop", package])

    def metadata(self, plan: dict) -> dict:
        if self.required(["get-state"]) != "device":
            raise QaError("Authorized device is not ready")
        api = self.required(["shell", "getprop", "ro.build.version.sdk"])
        if not api.isdigit() or int(api) < 26:
            raise QaError("Unsupported or unknown Android API")
        registered = self.required(["shell", "pm", "list", "instrumentation", plan["target_package"]])
        expected = f"instrumentation:{plan['test_package']}/{RUNNER} (target={plan['target_package']})"
        if expected not in registered.splitlines():
            raise QaError("Expected QA instrumentation target and runner are not installed")
        packages = {}
        for package in dict.fromkeys([plan["target_package"], plan["test_package"]]):
            paths = self.required(["shell", "pm", "path", package]).splitlines()
            if len(paths) != 1 or not paths[0].startswith("package:"):
                raise QaError("Expected one installed universal QA APK for each allowlisted package")
            path = paths[0][8:]
            if not re.fullmatch(r"/data/app/[A-Za-z0-9_~=/+.-]+\.apk", path):
                raise QaError("Unexpected installed QA APK path")
            result = self.run(["shell", "sha256sum", path])
            digest = re.fullmatch(r"([0-9a-fA-F]{64})[ \t]+\*?" + re.escape(path), result.stdout.strip()) if result.returncode == 0 else None
            if not digest:
                raise QaError("Installed QA APK hash is unavailable or ambiguous; one universal APK and an exact sha256sum result are required")
            packages[package] = {"installed": True, "apk_sha256": digest[1].lower()}
        def prop(name: str) -> str:
            value = self.required(["shell", "getprop", name])
            return value[:120] if re.fullmatch(r"[\w .()+/-]{1,120}", value) else "unavailable"
        return {"api": int(api), "manufacturer": prop("ro.product.manufacturer"),
                "model": prop("ro.product.model"), "packages": packages}


def run_instrumentation(adb: Adb, plan: dict, invocation: dict, timeout: int) -> dict:
    target = plan["target_package"]
    tail = ["shell", "am", "instrument", "-w", "-r", "-e", "class", invocation["selector"]]
    for key, value in sorted(plan["instrument_args"].items()):
        tail += ["-e", key, value]
    tail += [plan["test_package"] + "/" + RUNNER]
    adb.force_stop(target)
    try:
        result = adb.run(tail, timeout=timeout)
        return parse_instrumentation(result.stdout + "\n" + result.stderr, result.returncode,
                                     invocation["selector"], invocation["expected_methods"])
    finally:
        # A timeout also stops the QA process; no further test is attempted.
        adb.force_stop(target)


def execute(plan: dict, args: argparse.Namespace, catalog: dict, root: Path = ROOT) -> tuple[dict, Path]:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    directory = root / ".local-evidence" / "qa" / stamp
    evidence_root = root.resolve() / ".local-evidence"
    if not directory.resolve().is_relative_to(evidence_root):
        raise QaError("Evidence location escapes the project")
    directory.mkdir(parents=True, exist_ok=False)
    manifest = {"started_at": stamp, "plan": plan, "verdict": "PENDING", "results": [],
                "catalog_sha256": sha256(root / "tools/qa-groups.json"),
                "source_hash_normalization": catalog["source_hash_normalization"],
                "source_hashes": {k: v["sha256"] for k, v in catalog["classes"].items()},
                "support_sources": catalog["support_sources"], "apk_artifacts": [],
                "data_preservation": "NOT_MEASURED; runner only captures package/APK/device metadata",
                "apk_identity_verified": False,
                "evidence_limit": "Matching local and installed universal APK hashes identify bytes, not a proof that the source was freshly built; split APKs are unsupported"}
    adb = None
    try:
        for package, relative in MODULES[plan["module"]]["apks"].items():
            path = root / relative
            artifact = {"package": package, "path": relative, "sha256": None}
            manifest["apk_artifacts"].append(artifact)
            if not path.is_file():
                raise QaError("Required local QA APK is missing; build the selected module before authorized device execution")
            artifact["sha256"] = sha256(path)
        adb = Adb(args.adb, args.serial)
        manifest["before"] = adb.metadata(plan)
        for artifact in manifest["apk_artifacts"]:
            installed = manifest["before"].get("packages", {}).get(artifact["package"], {})
            digest = installed.get("apk_sha256")
            if installed.get("installed") is not True or not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest):
                raise QaError("Required installed QA APK hash is missing or invalid")
            if digest != artifact["sha256"]:
                raise QaError("Installed QA APK differs from the local APK; instrumentation was not started")
        manifest["apk_identity_verified"] = True
        group = catalog["groups"][plan["group"]]
        required_device = group.get("device", {})
        for key, expected in required_device.items():
            if str(manifest["before"].get(key, "")).lower() != str(expected).lower():
                raise QaError("Selected group does not match its required device/API")
        for invocation in plan["invocations"]:
            result = run_instrumentation(adb, plan, invocation, args.timeout)
            manifest["results"].append(result)
            (directory / f"invocation-{len(manifest['results']):03}.json").write_text(
                json.dumps(result, indent=2) + "\n", encoding="utf-8")
            if result["verdict"] != "PASSED":
                break
        states = [x["verdict"] for x in manifest["results"]]
        manifest["verdict"] = "FAILED" if "FAILED" in states else "PASSED" if len(states) == len(plan["invocations"]) and all(x == "PASSED" for x in states) else "PENDING"
    except (QaError, OSError, subprocess.SubprocessError) as exc:
        # Never persist arbitrary subprocess output, tracebacks or exception text.
        manifest["verdict"] = "FAILED"
        manifest["failure_kind"] = type(exc).__name__
        if isinstance(exc, QaError):
            manifest["failure"] = str(exc)
    finally:
        if adb is not None:
            try:
                manifest["after"] = adb.metadata(plan)
                manifest["package_metadata_unchanged"] = manifest.get("before") == manifest["after"]
                if not manifest["package_metadata_unchanged"]:
                    manifest["verdict"] = "FAILED"
            except (QaError, OSError, subprocess.SubprocessError):
                manifest["after"] = {"status": "unavailable"}
                manifest["verdict"] = "FAILED"
        manifest["unexecuted_invocations"] = len(plan["invocations"]) - len(manifest["results"])
        manifest["counts"] = {
            key: sum(result[key] for result in manifest["results"])
            for key in ("passed", "failed", "skipped", "pending")
        }
        manifest["counts"]["pending"] += sum(
            len(invocation["expected_methods"])
            for invocation in plan["invocations"][len(manifest["results"]):]
        )
        manifest["finished_at"] = datetime.now(timezone.utc).isoformat()
        (directory / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return manifest, directory


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        catalog = load_catalog()
        if (args.check_catalog or args.list) and args.execute:
            raise QaError("Catalog/list operations cannot be combined with execution")
        if args.check_catalog:
            print(json.dumps({"catalog": "VALID", "classes": len(catalog["classes"]), "groups": len(catalog["groups"])}))
            return 0
        if args.list:
            print(json.dumps(catalog["groups"], indent=2))
            return 0
        plan = make_plan(catalog, args)
        if not args.execute:
            print(json.dumps(plan, indent=2))
            return 0
        manifest, directory = execute(plan, args, catalog)
        print(json.dumps({"verdict": manifest["verdict"], "evidence": directory.relative_to(ROOT).as_posix(),
                          "unexecuted_invocations": manifest["unexecuted_invocations"]}))
        return {"PASSED": 0, "FAILED": 1, "PENDING": 2}[manifest["verdict"]]
    except QaError as exc:
        print("QA: " + str(exc), file=sys.stderr)
        return 2
    except (OSError, subprocess.SubprocessError):
        print("QA: local tool/evidence operation failed; no raw output or private paths were saved", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
