"""Safety, provenance and plan checks. Never invokes Gradle or another checkout."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("miguardia_verify", Path(__file__).resolve().parents[1] / "verify.py")
verify = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verify)


class VerifyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="miguardia verify ")
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def xml(self, directory, name="unit.SampleTest", tests=2, failures=0, skipped=0):
        directory.mkdir(parents=True, exist_ok=True)
        path = directory / ("TEST-" + name + ".xml")
        path.write_text(f'<testsuite name="{name}" tests="{tests}" failures="{failures}" errors="0" skipped="{skipped}"/>', encoding="utf-8")
        return path

    def report(self, outcome="EXECUTED", before=None, names=None, skipped=0):
        task = ":app:testDebugUnitTest"
        directory = verify.report_directory(self.root, task)
        for name, count in names or [("unit.SampleTest", 2)]:
            self.xml(directory, name, count, skipped=skipped)
        return verify.collect_reports(self.root, task, outcome, before or {}, self.root / "evidence")

    def build_files(self):
        for kind, relative, _ in verify.BUILD_ARTIFACTS:
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b'<issues><issue severity="Warning"/></issues>' if kind == "lint" else b"synthetic APK " + relative.encode())

    def artifact_manifest(self, outcome="EXECUTED", before=None):
        outcomes = {producer: outcome for _, _, producer in verify.BUILD_ARTIFACTS}
        return verify.collect_build_artifacts(self.root, outcomes, before or {}, self.root / "artifacts")

    def test_profiles_are_bounded_and_local_has_only_three_debug_jvm_tasks(self):
        self.assertEqual(verify.stages("local")[0]["tasks"], [
            ":core:domain:testDebugUnitTest", ":core:database:testDebugUnitTest", ":app:testDebugUnitTest"])
        self.assertEqual([s["name"] for s in verify.stages("full")], ["local", "interop", "build"])
        self.assertEqual(verify.stages("interop")[0]["tasks"], [":app:testDebugUnitTest"])
        self.assertTrue(all("connected" not in t and "install" not in t for s in verify.stages("full") for t in s["tasks"]))

    def test_default_incremental_fresh_tasks_and_explicit_rebuild_are_distinct(self):
        stage = verify.stages("local")[0]
        default = verify.gradle_arguments(stage, False, False)
        self.assertNotIn("--rerun", default)
        self.assertNotIn("--rerun-tasks", default)
        fresh = verify.gradle_arguments(stage, True, False)
        self.assertEqual(fresh.count("--rerun"), 3)
        for task in stage["tasks"]:
            self.assertEqual(fresh[fresh.index(task) + 1], "--rerun")
        rebuild = verify.gradle_arguments(stage, True, True)
        self.assertIn("--rerun-tasks", rebuild)
        self.assertNotIn("--rerun", rebuild)
        self.assertIn("-Pmiguardia.tests=interop", verify.gradle_arguments(verify.stages("interop")[0], False, False))

    def test_plan_executes_nothing_and_does_not_require_node_or_website_for_local(self):
        output = self.root / "no writes"
        with patch.object(verify, "capture") as command, patch.object(verify.subprocess, "Popen") as launch, contextlib.redirect_stdout(io.StringIO()) as text:
            self.assertEqual(0, verify.main(["--profile", "local", "--output", str(output), "--plan"]))
        self.assertEqual(json.loads(text.getvalue())["profile"], "local")
        self.assertFalse(output.exists())
        command.assert_not_called()
        launch.assert_not_called()

    def test_full_without_explicit_website_is_an_error_even_in_plan(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
            verify.main(["--profile", "full", "--plan"])
        self.assertEqual(error.exception.code, 2)

    def test_evidence_can_never_be_written_inside_read_only_website(self):
        output = self.root / "website" / "evidence"
        with contextlib.redirect_stderr(io.StringIO()), patch.object(verify, "snapshot") as snapshot:
            self.assertEqual(2, verify.main(["--profile", "interop", "--website", str(self.root / "website"), "--output", str(output)]))
        snapshot.assert_not_called()
        self.assertFalse(output.exists())

    def test_existing_evidence_is_preserved(self):
        marker = self.root / "original.txt"
        marker.write_text("retain me")
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(2, verify.main(["--profile", "local", "--output", str(self.root)]))
        self.assertEqual(marker.read_text(), "retain me")

    def test_concurrent_verifier_is_rejected_without_touching_its_lock(self):
        lock = self.root / ".local-evidence/optimization/.verify.lock"
        lock.parent.mkdir(parents=True)
        lock.write_text("other owner", encoding="utf-8")
        with patch.object(verify, "ROOT", self.root), patch.object(verify, "run_stage") as stage, \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, verify.main(["--profile", "local", "--output", str(self.root / "run")]))
        stage.assert_not_called()
        self.assertEqual(lock.read_text(encoding="utf-8"), "other owner")
        self.assertEqual(json.loads((self.root / "run/result.json").read_text())["status"], "preflight_failed")

    def test_website_preflight_checks_contract_files_and_git(self):
        with self.assertRaises(verify.VerificationError):
            verify.validate_website(None)
        with self.assertRaises(verify.VerificationError):
            verify.validate_website(str(self.root))
        for relative in verify.WEBSITE_REQUIRED:
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("synthetic")
        with patch.object(verify, "git", return_value="head") as command:
            self.assertEqual(self.root.resolve(), verify.validate_website(str(self.root)))
        command.assert_called_once_with(self.root.resolve(), "rev-parse", "HEAD")

    def test_node_probe_rejects_unsupported_runtime_with_recorded_exit_code(self):
        node = self.root / "node.exe"
        node.write_bytes(b"synthetic executable")
        failed = subprocess.CompletedProcess([], 9, "", "flag not supported")
        with patch.object(verify, "capture", return_value=failed) as command, self.assertRaises(verify.VerificationError):
            verify.node_preflight(node, self.root, self.root)
        self.assertIn("--experimental-transform-types", command.call_args.args[0])
        self.assertEqual(json.loads((self.root / "node-preflight.json").read_text())["returncode"], 9)

    def test_node_probe_requires_cryptography_and_typescript_capability_evidence(self):
        node = self.root / "node.exe"
        node.write_bytes(b"synthetic executable")
        for payload in ('{}', 'not json', '{"typescript_transform":true,"webcrypto_ecdsa":false}'):
            with patch.object(verify, "capture", return_value=subprocess.CompletedProcess([], 0, payload, "")), self.assertRaises(verify.VerificationError):
                verify.node_preflight(node, self.root, self.root)
        good = '{"version":"v24.test","typescript_transform":true,"webcrypto_ecdsa":true}'
        with patch.object(verify, "capture", return_value=subprocess.CompletedProcess([], 0, good, "")):
            self.assertEqual(verify.node_preflight(node, self.root, self.root)["version"], "v24.test")

    def test_shell_script_runtime_is_rejected(self):
        script = self.root / "node.cmd"
        script.write_text("not an executable")
        with patch.object(verify.shutil, "which", return_value=str(script)), self.assertRaises(verify.VerificationError):
            verify.resolve_executable(str(script), "node")

    def test_capture_preserves_argument_boundaries_and_disables_shell(self):
        argv = ["native.exe", "a path & another", "$(never execute)"]
        with patch.object(verify.subprocess, "run", return_value=subprocess.CompletedProcess(argv, 0, "", "")) as run:
            verify.capture(argv, self.root)
        self.assertEqual(run.call_args.args[0], argv)
        self.assertFalse(run.call_args.kwargs["shell"])

    def test_git_input_allowlist_omits_private_outputs_and_includes_actual_contract(self):
        for name in (".env", ".local-signing/debug.keystore", "app/build/results.xml", ".local-evidence/result.json"):
            self.assertFalse(verify.relevant_input(name, "android"))
        self.assertTrue(verify.relevant_input("app/src/test/resources/updates/webcrypto-oracle.mjs", "android"))
        self.assertTrue(verify.relevant_input("core/database/schemas/current/6.json", "android"))
        self.assertTrue(verify.relevant_input("lib/miguardia-beta/storage/memory.ts", "website"))
        self.assertTrue(verify.relevant_input("tests/helpers.mjs", "website"))
        self.assertFalse(verify.relevant_input(".env.production", "website"))

    def test_lint_and_packaging_rule_changes_are_captured_in_input_snapshot(self):
        names = ("app/lint.xml", "app/proguard-rules.pro")
        (self.root / "app").mkdir()
        for name in names:
            (self.root / name).write_text("initial", encoding="utf-8")
        def git_output(root, *args):
            return "\0".join(names) + "\0" if args[0] == "ls-files" else "synthetic"
        with patch.object(verify, "git", side_effect=git_output):
            original = verify.snapshot(self.root, "android")
            self.assertEqual(set(original["inputs_sha256"]), set(names))
            for name in names:
                (self.root / name).write_text("changed", encoding="utf-8")
                current = verify.snapshot(self.root, "android")
                self.assertNotEqual(original["inputs_sha256"][name], current["inputs_sha256"][name])

    def test_task_parser_distinguishes_executed_reused_failed_and_skipped(self):
        value = verify.task_outcomes("\n".join(["> Task :app:testDebugUnitTest", "> Task :a:test UP-TO-DATE",
            "> Task :b:test FROM-CACHE", "> Task :c:test FAILED", "> Task :d:test SKIPPED", "BUILD SUCCESSFUL"]))
        self.assertEqual(value, {":app:testDebugUnitTest": "EXECUTED", ":a:test": "UP-TO-DATE", ":b:test": "FROM-CACHE", ":c:test": "FAILED", ":d:test": "SKIPPED"})

    def test_old_xml_is_never_counted_as_a_new_execution(self):
        directory = verify.report_directory(self.root, ":app:testDebugUnitTest")
        self.xml(directory)
        before = verify.xml_fingerprints(directory)
        report = verify.collect_reports(self.root, ":app:testDebugUnitTest", "EXECUTED", before, self.root / "evidence")
        self.assertEqual(report["executed_tests"], 0)
        self.assertEqual(report["reports"][0]["freshness"], "stale_unattributed")
        with self.assertRaises(verify.VerificationError):
            verify.validate_reports("local", [report])

    def test_reused_results_are_valid_but_not_counted_as_new_tests(self):
        report = self.report(outcome="UP-TO-DATE")
        self.assertEqual(report["executed_tests"], 0)
        self.assertEqual(report["reused_result_tests"], 2)
        verify.validate_reports("local", [report])

    def test_partial_skips_are_reported_separately_from_effective_executions(self):
        for outcome in ("EXECUTED", "UP-TO-DATE", "FROM-CACHE"):
            with self.subTest(outcome=outcome):
                report = self.report(outcome=outcome, skipped=1)
                self.assertEqual(report["reported_tests"], 2)
                self.assertEqual(report["skipped"], 1)
                self.assertEqual(report["reports"][0]["effective_tests"], 1)
                self.assertEqual(report["executed_tests"], 1 if outcome == "EXECUTED" else 0)
                self.assertEqual(report["reused_result_tests"], 0 if outcome == "EXECUTED" else 1)
                verify.validate_reports("local", [report])

    def test_entirely_skipped_local_tests_never_pass_as_effective_verification(self):
        for outcome in ("EXECUTED", "UP-TO-DATE", "FROM-CACHE"):
            with self.subTest(outcome=outcome):
                report = self.report(outcome=outcome, skipped=2)
                self.assertEqual(report["reported_tests"], 2)
                self.assertEqual(report["executed_tests"] + report["reused_result_tests"], 0)
                self.assertEqual(report["skipped"], 2)
                with self.assertRaises(verify.VerificationError):
                    verify.validate_reports("local", [report])

    def test_impossible_xml_counts_are_rejected(self):
        path = self.xml(self.root, tests=2, skipped=3)
        with self.assertRaises(verify.VerificationError):
            verify.xml_counts(path)

    def test_fresh_xml_is_counted_and_copied_with_hash(self):
        report = self.report()
        self.assertEqual(report["executed_tests"], 2)
        self.assertEqual(report["reused_result_tests"], 0)
        copied = self.root / "evidence" / "TEST-unit.SampleTest.xml"
        self.assertTrue(copied.is_file())
        self.assertEqual(report["reports"][0]["sha256"], verify.sha256(copied))

    def test_no_source_or_task_failure_never_succeeds_from_existing_xml(self):
        for outcome in ("NO-SOURCE", "FAILED", "NOT_REPORTED", "SKIPPED"):
            report = self.report(outcome=outcome)
            with self.assertRaises(verify.VerificationError):
                verify.validate_reports("local", [report])

    def test_interop_requires_public_24_historical_2_and_no_skips(self):
        names = sorted(verify.INTEROP_CLASSES)
        report = self.report(names=list(verify.INTEROP_COUNTS.items()))
        verify.validate_reports("interop", [report])
        with self.assertRaises(verify.VerificationError):
            verify.validate_reports("local", [report])
        report["skipped"] = 1
        with self.assertRaises(verify.VerificationError):
            verify.validate_reports("interop", [report])
        report["skipped"] = 0
        report["suites"] = [names[0]]
        with self.assertRaises(verify.VerificationError):
            verify.validate_reports("interop", [report])

    def test_build_manifest_captures_exact_five_apks_and_copies_lint_with_hashes(self):
        self.build_files()
        manifest = self.artifact_manifest()
        self.assertEqual(len(manifest), 6)
        self.assertEqual(sum(item["kind"] == "apk" for item in manifest), 5)
        for item in manifest:
            self.assertTrue(item["exists"])
            self.assertGreater(item["size_bytes"], 0)
            self.assertEqual(item["sha256"], verify.sha256(self.root / item["path"]))
            self.assertEqual(item["freshness"], "generated")
        self.assertEqual({p.name for p in (self.root / "artifacts").iterdir()}, {"manifest.json", "lint-results-debug.xml"})
        lint = next(item for item in manifest if item["kind"] == "lint")
        self.assertEqual(lint["issues_by_severity"], {"Warning": 1})
        verify.validate_build_artifacts(manifest)

    def test_build_existing_outputs_are_attributed_without_claiming_regeneration(self):
        self.build_files()
        before = verify.artifact_fingerprints(self.root)
        for outcome, expected in (("UP-TO-DATE", "reused"), ("FROM-CACHE", "restored_from_cache"), ("EXECUTED", "unchanged_after_execution")):
            with self.subTest(outcome=outcome):
                manifest = self.artifact_manifest(outcome, before)
                self.assertEqual({item["freshness"] for item in manifest}, {expected})
                self.assertTrue(all(item["changed_during_stage"] is False for item in manifest))
                verify.validate_build_artifacts(manifest)

    def test_build_missing_or_empty_apk_rejects_even_successful_producer(self):
        self.build_files()
        path = self.root / verify.BUILD_ARTIFACTS[0][1]
        path.unlink()
        missing = self.artifact_manifest()
        self.assertFalse(missing[0]["exists"])
        with self.assertRaises(verify.VerificationError):
            verify.validate_build_artifacts(missing)
        path.write_bytes(b"")
        empty = self.artifact_manifest()
        self.assertEqual(empty[0]["size_bytes"], 0)
        with self.assertRaises(verify.VerificationError):
            verify.validate_build_artifacts(empty)

    def test_build_old_outputs_without_successful_producer_never_pass(self):
        self.build_files()
        for outcome in ("NOT_REPORTED", "FAILED", "SKIPPED", "NO-SOURCE"):
            with self.subTest(outcome=outcome):
                manifest = self.artifact_manifest(outcome)
                self.assertTrue(all(item["exists"] for item in manifest))
                self.assertEqual({item["freshness"] for item in manifest}, {"stale_unattributed"})
                with self.assertRaises(verify.VerificationError):
                    verify.validate_build_artifacts(manifest)

    def test_build_missing_malformed_or_error_lint_report_rejects(self):
        self.build_files()
        path = self.root / verify.BUILD_ARTIFACTS[-1][1]
        path.unlink()
        with self.assertRaises(verify.VerificationError):
            verify.validate_build_artifacts(self.artifact_manifest())
        for contents in ("not XML", '<other/>', '<issues><issue severity="Error"/></issues>', '<issues><issue severity="Fatal"/></issues>'):
            with self.subTest(contents=contents):
                path.write_text(contents, encoding="utf-8")
                with self.assertRaises(verify.VerificationError):
                    verify.validate_build_artifacts(self.artifact_manifest())
    def test_source_mutation_during_run_invalidates_green_command(self):
        wrapper = self.root / "gradle/wrapper/gradle-wrapper.jar"
        wrapper.parent.mkdir(parents=True)
        wrapper.write_bytes(b"synthetic")
        java = self.root / "java.exe"
        java.write_bytes(b"synthetic")
        before = {"head": "a", "inputs_sha256": {"source": "old"}}
        after = {"head": "a", "inputs_sha256": {"source": "changed"}}
        with patch.object(verify, "ROOT", self.root), patch.object(verify, "java_executable", return_value=java), \
                patch.object(verify, "snapshot", side_effect=[before, after]), \
                patch.object(verify, "run_stage", return_value={"status": "passed"}), contextlib.redirect_stdout(io.StringIO()), \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, verify.main(["--profile", "local", "--output", str(self.root / "run")]))
        result = json.loads((self.root / "run/result.json").read_text())
        self.assertEqual(result["status"], "inputs_changed")
        self.assertEqual(result["changed_inputs"], ["android"])
        self.assertFalse((self.root / ".local-evidence/optimization/.verify.lock").exists())
        self.assertEqual(result["runner"]["sha256"], verify.sha256(Path(verify.__file__)))


if __name__ == "__main__":
    unittest.main()
