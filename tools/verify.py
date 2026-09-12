#!/usr/bin/env python3
"""Local verification with explicit cross-project interoperability and evidence.

Uses only the Python standard library. It never installs APKs or starts devices.
The Java invocation uses this repository's Gradle wrapper JAR without a shell.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
LOCAL_TASKS = (":core:domain:testDebugUnitTest", ":core:database:testDebugUnitTest", ":app:testDebugUnitTest")
BUILD_TASKS = (":app:lintDebug", ":app:assembleDebug", ":app:assembleQa", ":app:assembleRelease",
               ":app:assembleQaAndroidTest", ":core:database:assembleDebugAndroidTest")
BUILD_ARTIFACTS = (
    ("apk", "app/build/outputs/apk/debug/app-debug.apk", ":app:packageDebug"),
    ("apk", "app/build/outputs/apk/qa/app-qa.apk", ":app:packageQa"),
    ("apk", "app/build/outputs/apk/release/app-release-unsigned.apk", ":app:packageRelease"),
    ("apk", "app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk", ":app:packageQaAndroidTest"),
    ("apk", "core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk", ":core:database:packageDebugAndroidTest"),
    ("lint", "app/build/reports/lint-results-debug.xml", ":app:lintReportDebug"),
)
INTEROP_COUNTS = {"com.blackatsystems.miguardia.beta.BetaWebCryptoTest": 2,
                  "com.blackatsystems.miguardia.updates.PublicDistributionInteropTest": 24}
INTEROP_CLASSES = frozenset(INTEROP_COUNTS)
WEBSITE_REQUIRED = ("lib/miguardia-beta/crypto.ts", "lib/miguardia-beta/validation.ts", "tests/helpers.mjs", "package.json", "docs/MIGUARDIA_PUBLIC_DISTRIBUTION_API_V1.md", "tests/fixtures/public-distribution/android-oracles.json")
TASK_LINE = re.compile(r"^> Task (:[^\s]+)(?: (UP-TO-DATE|FROM-CACHE|SKIPPED|NO-SOURCE|FAILED))?\s*$")
NODE_PROBE = """
import assert from 'node:assert/strict';
import { stripTypeScriptTypes } from 'node:module';
const code = stripTypeScriptTypes('enum Mode { OK = 1 }; export default Mode.OK', {mode:'transform'});
assert.equal((await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'))).default, 1);
const keys = await crypto.subtle.generateKey({name:'ECDSA',namedCurve:'P-256'}, true, ['sign','verify']);
const bytes = new TextEncoder().encode('miguardia-interop-preflight');
const signature = await crypto.subtle.sign({name:'ECDSA',hash:'SHA-256'}, keys.privateKey, bytes);
assert.equal(signature.byteLength, 64);
assert.ok(await crypto.subtle.verify({name:'ECDSA',hash:'SHA-256'}, keys.publicKey, signature, bytes));
process.stdout.write(JSON.stringify({version:process.version,typescript_transform:true,webcrypto_ecdsa:true}));
"""


class VerificationError(Exception):
    pass


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def timestamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def capture(argv: list[str], cwd: Path, *, env: dict[str, str] | None = None, timeout: int = 30) -> subprocess.CompletedProcess:
    return subprocess.run(argv, cwd=cwd, env=env, shell=False, check=False, capture_output=True,
                          text=True, encoding="utf-8", errors="replace", timeout=timeout)


def git(root: Path, *args: str) -> str:
    result = capture(["git", "-C", str(root), *args], root)
    if result.returncode:
        raise VerificationError(f"Git could not inspect {root.name}: {' '.join(args)}")
    return result.stdout.rstrip("\r\n")


def relevant_input(path: str, kind: str) -> bool:
    if kind == "website":
        return path in {"package.json", "package-lock.json", "tsconfig.json", "tests/helpers.mjs"} or path.startswith(("lib/miguardia-beta/", "lib/miguardia-public/", "tests/fixtures/public-distribution/")) or path == "docs/MIGUARDIA_PUBLIC_DISTRIBUTION_API_V1.md"
    return (path in {"build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat", "tools/verify.py",
                     "app/lint.xml", "app/proguard-rules.pro"}
            or path.startswith("gradle/")
            or (path.startswith(("app/", "core/")) and ("/src/" in path or "/schemas/" in path or path.endswith("build.gradle.kts"))))


def snapshot(root: Path, kind: str) -> dict:
    # Allowlist source inputs; never read .env, signing material, private evidence or build directories.
    names = git(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z").split("\0")
    hashes = {}
    for name in sorted(set(names)):
        path = root / name
        if name and relevant_input(name, kind) and path.is_file():
            if not path.resolve().is_relative_to(root.resolve()):
                raise VerificationError(f"Relevant source escapes the {kind} checkout: {name}")
            hashes[name] = sha256(path)
    return {"root": str(root), "head": git(root, "rev-parse", "HEAD"),
            "branch": git(root, "branch", "--show-current"),
            "status": git(root, "status", "--porcelain=v1", "--untracked-files=all").splitlines(), "inputs_sha256": hashes}


def resolve_executable(value: str | None, name: str) -> Path:
    resolved = shutil.which(value or name)
    if resolved is None:
        raise VerificationError(f"Executable unavailable: {name}. Provide its path explicitly.")
    path = Path(resolved).resolve()
    if not path.is_file() or path.suffix.lower() in {".cmd", ".bat", ".ps1"}:
        raise VerificationError(f"{name} must be a native executable, not a shell script.")
    return path


def java_executable() -> Path:
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home.strip('"')) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if not candidate.is_file():
            raise VerificationError("JAVA_HOME does not contain a Java executable.")
        return candidate.resolve()
    return resolve_executable(None, "java")


def validate_website(value: str | None) -> Path:
    if not value:
        raise VerificationError("Interop requires --website PATH identifying the real read-only website checkout.")
    path = Path(value).expanduser().resolve()
    if not path.is_dir():
        raise VerificationError("The explicit website path does not exist.")
    missing = [name for name in WEBSITE_REQUIRED if not (path / name).is_file()]
    if missing:
        raise VerificationError("Website contract inputs missing: " + ", ".join(missing))
    git(path, "rev-parse", "HEAD")
    return path


def node_preflight(node: Path, root: Path, output: Path) -> dict:
    env = os.environ.copy()
    env.pop("NODE_OPTIONS", None)
    argv = [str(node), "--experimental-transform-types", "--input-type=module", "--eval", NODE_PROBE]
    result = capture(argv, root, env=env)
    record = {"argv": argv, "returncode": result.returncode, "stdout": result.stdout, "stderr": result.stderr,
              "binary_sha256": sha256(node)}
    write_json(output / "node-preflight.json", record)
    if result.returncode:
        raise VerificationError("Node lacks the required TypeScript transform or ECDSA WebCrypto capabilities; see node-preflight.json.")
    try:
        capabilities = json.loads(result.stdout)
        if capabilities.get("typescript_transform") is not True or capabilities.get("webcrypto_ecdsa") is not True:
            raise ValueError("missing capabilities")
    except (ValueError, AttributeError) as error:
        raise VerificationError("Node preflight did not return valid capability evidence.") from error
    return {"path": str(node), "sha256": record["binary_sha256"], **capabilities}


def stages(profile: str) -> list[dict]:
    selected = ("local", "interop", "build") if profile == "full" else (profile,)
    return [{"name": name, "tasks": list(LOCAL_TASKS if name == "local" else (":app:testDebugUnitTest",) if name == "interop" else BUILD_TASKS)}
            for name in selected]


def gradle_arguments(stage: dict, fresh_tests: bool, rebuild: bool) -> list[str]:
    args = ["--no-daemon", "--stacktrace", "--max-workers=1", "--console=plain",
            "-Pmiguardia.tests=" + ("interop" if stage["name"] == "interop" else "local")]
    for task in stage["tasks"]:
        args.append(task)
        if fresh_tests and task.endswith("UnitTest") and not rebuild:
            args.append("--rerun")  # Only this Test task; compilation remains incremental.
    if rebuild:
        args.append("--rerun-tasks")
    return args


def task_outcomes(log: str) -> dict[str, str]:
    result = {}
    for line in log.splitlines():
        match = TASK_LINE.match(line.strip())
        if match:
            result[match[1]] = match[2] or "EXECUTED"
    return result


def report_directory(root: Path, task: str) -> Path:
    parts = task.strip(":").split(":")
    return root.joinpath(*parts[:-1], "build", "test-results", parts[-1])


def xml_fingerprints(directory: Path) -> dict[str, tuple[int, str]]:
    return {path.name: (path.stat().st_mtime_ns, sha256(path)) for path in directory.glob("TEST-*.xml")}


def xml_counts(path: Path) -> dict:
    document = ET.parse(path).getroot()
    suites = [document] if document.tag == "testsuite" else list(document.iter("testsuite"))
    counts = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    names = []
    for suite in suites:
        names.append(suite.get("name", ""))
        suite_counts = {}
        for key in counts:
            value = int(suite.get(key, "0"))
            if value < 0:
                raise VerificationError("Negative test count in XML report.")
            suite_counts[key] = value
            counts[key] += value
        if suite_counts["skipped"] + suite_counts["failures"] + suite_counts["errors"] > suite_counts["tests"]:
            raise VerificationError("Inconsistent test counts in XML report.")
    return {**counts, "effective_tests": counts["tests"] - counts["skipped"], "suites": names}


def collect_reports(root: Path, task: str, outcome: str, before: dict, destination: Path) -> dict:
    directory = report_directory(root, task)
    evidence = {"task": task, "outcome": outcome, "reports": [], "reported_tests": 0, "executed_tests": 0, "reused_result_tests": 0,
                "failures": 0, "errors": 0, "skipped": 0, "suites": []}
    destination.mkdir(parents=True, exist_ok=True)
    for path in sorted(directory.glob("TEST-*.xml")):
        fingerprint = (path.stat().st_mtime_ns, sha256(path))
        changed = before.get(path.name) != fingerprint
        freshness = "executed" if outcome in {"EXECUTED", "FAILED"} and changed else "reused" if outcome in {"UP-TO-DATE", "FROM-CACHE"} else "stale_unattributed"
        counts = xml_counts(path)
        shutil.copy2(path, destination / path.name)
        evidence["reports"].append({"file": path.name, "sha256": fingerprint[1], "freshness": freshness, **counts})
        if freshness == "stale_unattributed":
            continue
        evidence["reported_tests"] += counts["tests"]
        evidence["executed_tests" if freshness == "executed" else "reused_result_tests"] += counts["effective_tests"]
        for key in ("failures", "errors", "skipped"):
            evidence[key] += counts[key]
        evidence["suites"].extend(counts["suites"])
    return evidence


def validate_reports(name: str, reports: list[dict]) -> None:
    for report in reports:
        if report["outcome"] not in {"EXECUTED", "UP-TO-DATE", "FROM-CACHE"}:
            raise VerificationError(f"No successful test task evidence: {report['task']} ({report['outcome']}).")
        if report["executed_tests"] + report["reused_result_tests"] <= 0:
            raise VerificationError(f"No attributable effective tests (excluding skipped): {report['task']}.")
        if any(item["freshness"] == "stale_unattributed" for item in report["reports"]):
            raise VerificationError(f"Unattributed stale test XML remains: {report['task']}.")
        if report["failures"] or report["errors"]:
            raise VerificationError(f"Failed tests: {report['task']}.")
    suites = {suite for report in reports for suite in report["suites"]}
    if name == "interop":
        actual = {suite: item["effective_tests"] for report in reports for item in report["reports"] for suite in item["suites"]}
        if suites != INTEROP_CLASSES or actual != INTEROP_COUNTS:
            raise VerificationError("Interop requires 24 public oracles and 2 isolated historical WebCrypto tests.")
        if any(report["skipped"] for report in reports):
            raise VerificationError("Interop skipped a contract test; this is not a successful verification.")
    elif name == "local" and suites & INTEROP_CLASSES:
        raise VerificationError("Local test results unexpectedly contain cross-project interoperability tests.")


def artifact_fingerprint(path: Path) -> tuple[int, int, str] | None:
    if not path.is_file():
        return None
    stat = path.stat()
    return stat.st_mtime_ns, stat.st_size, sha256(path)


def artifact_fingerprints(root: Path) -> dict:
    return {relative: artifact_fingerprint(root / relative) for _, relative, _ in BUILD_ARTIFACTS}


def collect_build_artifacts(root: Path, outcomes: dict, before: dict, destination: Path) -> list[dict]:
    """Inventory exact outputs and their producer; existence alone is not compilation."""
    destination.mkdir(parents=True, exist_ok=True)
    manifest = []
    for kind, relative, producer in BUILD_ARTIFACTS:
        path = root / relative
        outcome = outcomes.get(producer, "NOT_REPORTED")
        entry = {"kind": kind, "path": relative, "producer_task": producer, "producer_outcome": outcome,
                 "exists": path.is_file(), "freshness": "missing", "size_bytes": None, "sha256": None}
        manifest.append(entry)
        current = artifact_fingerprint(path)
        if current is None:
            continue
        changed = before.get(relative) != current
        entry.update(size_bytes=current[1], sha256=current[2], changed_during_stage=changed)
        if outcome == "EXECUTED":
            entry["freshness"] = "generated" if changed else "unchanged_after_execution"
        elif outcome == "FROM-CACHE":
            entry["freshness"] = "restored_from_cache"
        elif outcome == "UP-TO-DATE":
            entry["freshness"] = "reused"
        else:
            entry["freshness"] = "stale_unattributed"
        if kind == "lint":
            shutil.copy2(path, destination / path.name)
            entry["evidence_file"] = path.name
            try:
                document = ET.parse(path).getroot()
                if document.tag != "issues":
                    raise VerificationError("Unexpected lint XML root.")
                severities = {}
                for issue in document.findall("issue"):
                    severity = issue.get("severity", "Unknown")
                    severities[severity] = severities.get(severity, 0) + 1
                entry["issues_by_severity"] = severities
            except (ET.ParseError, VerificationError) as error:
                entry["parse_error"] = str(error)
    write_json(destination / "manifest.json", manifest)
    return manifest


def validate_build_artifacts(manifest: list[dict]) -> None:
    for entry in manifest:
        if not entry["exists"] or not entry["size_bytes"]:
            raise VerificationError(f"Required build artifact is missing or empty: {entry['path']}.")
        if entry["freshness"] in {"missing", "stale_unattributed"}:
            raise VerificationError(f"Build artifact has no successful producer evidence: {entry['path']}.")
        if entry.get("parse_error"):
            raise VerificationError(f"Lint report cannot be parsed: {entry['parse_error']}")
        if any(entry.get("issues_by_severity", {}).get(severity, 0) for severity in ("Error", "Fatal")):
            raise VerificationError("Lint report contains errors or fatal issues.")


def run_stage(root: Path, output: Path, stage: dict, java: Path, env: dict[str, str], fresh: bool, rebuild: bool) -> dict:
    directory = output / stage["name"]
    directory.mkdir()
    tests = [task for task in stage["tasks"] if task.endswith("UnitTest")]
    before = {task: xml_fingerprints(report_directory(root, task)) for task in tests}
    artifacts_before = artifact_fingerprints(root) if stage["name"] == "build" else {}
    argv = [str(java), "-Xmx64m", "-Xms64m", "-Dorg.gradle.appname=gradlew", "-jar",
            str(root / "gradle/wrapper/gradle-wrapper.jar"), *gradle_arguments(stage, fresh, rebuild)]
    result = {"name": stage["name"], "argv": argv, "started_utc": timestamp(), "status": "running"}
    write_json(directory / "command.json", result)
    start = time.monotonic()
    print(f"{stage['name']}: verification running; log {directory / 'gradle.log'}", flush=True)
    with (directory / "gradle.log").open("w", encoding="utf-8") as log:
        child = subprocess.Popen(argv, cwd=root, env=env, shell=False, stdout=log, stderr=subprocess.STDOUT)
        try:
            result["returncode"] = child.wait()
        except BaseException:
            child.terminate()
            try:
                child.wait(timeout=10)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait()
            raise
    result["elapsed_seconds"] = round(time.monotonic() - start, 3)
    outcomes = task_outcomes((directory / "gradle.log").read_text(encoding="utf-8", errors="replace"))
    result["task_outcomes"] = outcomes
    result["tests"] = [collect_reports(root, task, outcomes.get(task, "NOT_REPORTED"), before[task],
                                      directory / "xml" / task.strip(":").replace(":", "_")) for task in tests]
    if stage["name"] == "build":
        result["artifacts"] = collect_build_artifacts(root, outcomes, artifacts_before, directory / "artifacts")
    try:
        if result["returncode"]:
            raise VerificationError(f"Gradle stage {stage['name']} failed with exit code {result['returncode']}.")
        if tests:
            validate_reports(stage["name"], result["tests"])
        elif any(outcomes.get(task) not in {"EXECUTED", "UP-TO-DATE", "FROM-CACHE"} for task in stage["tasks"]):
            raise VerificationError("Not every requested build task completed successfully.")
        if stage["name"] == "build":
            validate_build_artifacts(result["artifacts"])
        result["status"] = "passed"
    except VerificationError as error:
        result["status"] = "failed"
        result["error"] = str(error)
    write_json(directory / "command.json", result)
    return result


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser(description=__doc__)
    value.add_argument("--profile", required=True, choices=("local", "interop", "build", "full"))
    value.add_argument("--website", help="Explicit read-only website checkout; required for interop/full")
    value.add_argument("--node", help="Native Node executable; otherwise resolve Node on PATH")
    value.add_argument("--output", help="New evidence directory; existing paths are never overwritten")
    value.add_argument("--plan", action="store_true", help="Print task plan without executing tools or writing evidence")
    value.add_argument("--fresh-tests", action="store_true", help="Rerun JVM Test tasks only; leave compilation incremental")
    value.add_argument("--rebuild", action="store_true", help="Explicitly rerun all scheduled tasks and their dependencies")
    return value


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    requires_interop = args.profile in {"interop", "full"}
    if requires_interop and not args.website:
        parser().error("interop/full require --website PATH; the contract may not be silently skipped")
    if args.profile == "build" and args.fresh_tests:
        parser().error("--fresh-tests requires a profile with JVM tests")
    output = Path(args.output).expanduser().resolve() if args.output else ROOT / ".local-evidence/optimization" / timestamp()
    selected = stages(args.profile)
    plan = {"profile": args.profile, "root": str(ROOT), "website": args.website if requires_interop else None,
            "node": args.node if requires_interop else None, "output": str(output), "fresh_tests": args.fresh_tests,
            "rebuild": args.rebuild, "stages": [{**stage, "gradle_arguments": gradle_arguments(stage, args.fresh_tests, args.rebuild)} for stage in selected]}
    if args.plan:
        print(json.dumps(plan, indent=2, ensure_ascii=False))
        return 0
    if requires_interop and output.is_relative_to(Path(args.website).expanduser().resolve()):
        print("Evidence must be outside the read-only website checkout.", file=sys.stderr)
        return 2
    if output.exists():
        print("Evidence directory already exists; select a new --output path.", file=sys.stderr)
        return 2
    output.mkdir(parents=True)
    result = {"plan": plan, "started_utc": timestamp(), "status": "preflight", "stages": [],
              "runner": {"path": "tools/verify.py", "sha256": sha256(Path(__file__).resolve())}}
    original = {}
    website = None
    lock = ROOT / ".local-evidence/optimization/.verify.lock"
    lock_owner = json.dumps({"pid": os.getpid(), "started_utc": result["started_utc"]})
    owns_lock = False
    try:
        lock.parent.mkdir(parents=True, exist_ok=True)
        try:
            descriptor = os.open(lock, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        except FileExistsError as error:
            raise VerificationError("Another verifier owns .local-evidence/optimization/.verify.lock. Inspect that run before removing a stale lock.") from error
        owns_lock = True
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            stream.write(lock_owner)
        if not (ROOT / "gradle/wrapper/gradle-wrapper.jar").is_file():
            raise VerificationError("Repository Gradle wrapper JAR is missing.")
        java = java_executable()
        result["java"] = {"path": str(java), "sha256": sha256(java)}
        original["android"] = snapshot(ROOT, "android")
        env = os.environ.copy()
        env.pop("MIGUARDIA_BETA_WEBSITE", None)
        env.pop("NODE_BINARY", None)
        if requires_interop:
            website = validate_website(args.website)
            if website == ROOT:
                raise VerificationError("Website and Android must be separate checkouts.")
            original["website"] = snapshot(website, "website")
            node = resolve_executable(args.node, "node")
            result["node"] = node_preflight(node, ROOT, output)
            env["MIGUARDIA_BETA_WEBSITE"] = str(website)
            env["NODE_BINARY"] = str(node)
            env.pop("NODE_OPTIONS", None)
        result["inputs_before"] = original
        write_json(output / "result.json", result)
        for stage in selected:
            record = run_stage(ROOT, output, stage, java, env, args.fresh_tests, args.rebuild)
            result["stages"].append(record)
            write_json(output / "result.json", result)
            if record["status"] != "passed":
                raise VerificationError(record["error"])
        result["status"] = "passed"
    except (VerificationError, OSError, ValueError, ET.ParseError, subprocess.SubprocessError) as error:
        result["status"] = "failed" if result["stages"] else "preflight_failed"
        result["error"] = str(error)
    except KeyboardInterrupt:
        result["status"] = "interrupted"
        result["error"] = "Verification interrupted; no successful completion claimed."
    finally:
        result["inputs_before"] = original
        try:
            after = {kind: snapshot(ROOT if kind == "android" else website, kind) for kind in original}
            result["inputs_after"] = after
            changed = [kind for kind in original if original[kind]["head"] != after[kind]["head"] or original[kind]["inputs_sha256"] != after[kind]["inputs_sha256"]]
            result["changed_inputs"] = changed
            if changed and result["status"] == "passed":
                result["status"] = "inputs_changed"
                result["error"] = "Source inputs changed during verification; results do not describe one stable candidate."
        except (VerificationError, OSError, subprocess.SubprocessError) as error:
            result["snapshot_error"] = str(error)
            if result["status"] == "passed":
                result["status"] = "inputs_unverified"
        result["finished_utc"] = timestamp()
        write_json(output / "result.json", result)
        if owns_lock and lock.exists() and lock.read_text(encoding="utf-8") == lock_owner:
            lock.unlink()
    print(f"{result['status']}: {output / 'result.json'}")
    if result.get("error"):
        print(result["error"], file=sys.stderr)
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
