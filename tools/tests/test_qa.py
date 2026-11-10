"""Safety/selection/result tests. All device execution is mocked; never calls adb."""
import contextlib
import copy
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("qa_under_test", ROOT / "tools/qa.py")
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)


def status(cls, method, code):
    return f"INSTRUMENTATION_STATUS: class={cls}\nINSTRUMENTATION_STATUS: test={method}\nINSTRUMENTATION_STATUS_CODE: {code}\n"


class CatalogAndPlanTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = qa.load_catalog(ROOT)

    def args(self, *argv):
        return qa.parser().parse_args(argv)

    def test_every_actual_class_and_shared_fixture_is_accounted_for(self):
        classes, fixtures = qa.discover(ROOT)
        self.assertEqual(self.catalog["classes"], classes)
        self.assertEqual(self.catalog["support_sources"], fixtures)
        self.assertGreater(len(classes), 90)

    def test_unknown_class_and_changed_source_fail_catalog(self):
        catalog = copy.deepcopy(self.catalog)
        catalog["classes"].pop(next(iter(catalog["classes"])))
        with self.assertRaises(qa.QaError):
            qa.validate_catalog(catalog, ROOT)
        catalog = copy.deepcopy(self.catalog)
        next(iter(catalog["classes"].values()))["sha256"] = "0" * 64
        with self.assertRaises(qa.QaError):
            qa.validate_catalog(catalog, ROOT)

    def test_bypassing_fixture_authorization_in_catalog_is_rejected(self):
        catalog = copy.deepcopy(self.catalog)
        catalog["groups"]["app-public-fixtures"]["requires"].remove("disposable-data")
        with self.assertRaises(qa.QaError):
            qa.validate_catalog(catalog, ROOT)

    def test_arbitrary_instrumentation_arguments_are_rejected(self):
        catalog = copy.deepcopy(self.catalog)
        catalog["groups"]["app-rendering"]["instrument_args"] = {"class": "other.package.Test;reboot"}
        with self.assertRaises(qa.QaError):
            qa.validate_catalog(catalog, ROOT)

    def test_final_review_requires_existing_synthetic_session_and_read_only_opt_in(self):
        for field in ("qa-mutations", "existing-session", "finalQaReadOnly"):
            catalog = copy.deepcopy(self.catalog)
            group = catalog["groups"]["final-qa-read-only"]
            if field == "finalQaReadOnly":
                group["instrument_args"].pop(field)
            else:
                group["requires"].remove(field)
            with self.subTest(field=field), self.assertRaisesRegex(qa.QaError, "Final QA review"):
                qa.validate_catalog(catalog, ROOT)

    def test_default_plan_and_catalog_modes_never_invoke_adb(self):
        with mock.patch.object(qa, "Adb", side_effect=AssertionError("No adb in plan")), mock.patch.object(qa.subprocess, "run", side_effect=AssertionError("No subprocess")):
            for argv in (["--group", "public-root"], ["--check-catalog"], ["--list"]):
                with self.subTest(argv=argv), contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(0, qa.main(argv))

    def test_execute_requires_exact_serial_and_authorization(self):
        for argv in (["--execute"], ["--execute", "--serial", "emulator-5554"], ["--execute", "--authorized-device-use"]):
            with self.subTest(argv=argv), self.assertRaises(qa.QaError):
                qa.make_plan(self.catalog, self.args("--group", "app-rendering", *argv))

    def test_reject_shell_syntax_serial_class_and_unknown_group(self):
        for argv in (["--serial", "phone;reboot"], ["--serial", "phone$(id)"], ["--class", "com.example.Test,com.production.Test"], ["--class", "com.example.Test#method"], ["--timeout", "0"]):
            with self.subTest(argv=argv), self.assertRaises(qa.QaError):
                qa.make_plan(self.catalog, self.args("--group", "app-rendering", *argv))
        with self.assertRaises(qa.QaError):
            qa.make_plan(self.catalog, self.args("--group", "all"))

    def test_dangerous_group_needs_separate_opt_ins(self):
        argv = ["--group", "notifications-alarm", "--execute", "--serial", "emulator-5554", "--authorized-device-use", "--allow-qa-mutations"]
        with self.assertRaisesRegex(qa.QaError, "real-alarms"):
            qa.make_plan(self.catalog, self.args(*argv))

    def test_supervised_marker_harness_cannot_start_unattended(self):
        for group in ("updates-oem-supervised", "support-browser-supervised", "updates-temporary-session", "credits-supervised", "upgrade-backup-preparation"):
            argv = ["--group", group, "--execute", "--serial", "test-serial", "--authorized-device-use"]
            argv += ["--allow-" + flag for flag in qa.PERMISSIONS]
            with self.subTest(group=group), self.assertRaisesRegex(qa.QaError, "Supervised plan only"):
                qa.make_plan(self.catalog, self.args(*argv))

    def test_closed_root_is_not_mixed_with_beta_fixture_group(self):
        closed = qa.make_plan(self.catalog, self.args("--group", "public-root"))
        self.assertEqual(1, len(closed["invocations"]))
        self.assertEqual({}, closed["instrument_args"])
        self.assertNotIn("beta-fixture", closed["requires"])
        self.assertTrue(closed["invocations"][0]["selector"].endswith(".BetaClosedRootTest"))

    def test_upgrade_preparation_cannot_drop_supervision_or_isolation(self):
        for field in ("manual_only", "qa-mutations", "disposable-data", "backup-restore"):
            catalog = copy.deepcopy(self.catalog)
            group = catalog["groups"]["upgrade-backup-preparation"]
            if field == "manual_only":
                group.pop(field)
            else:
                group["requires"].remove(field)
            with self.subTest(field=field), self.assertRaisesRegex(qa.QaError, "Upgrade data preparation"):
                qa.validate_catalog(catalog, ROOT)

    def test_hours_restoration_diagnosis_cannot_drop_isolation(self):
        for field in ("qa-mutations", "disposable-data", "hoursReviewRace"):
            catalog = copy.deepcopy(self.catalog)
            group = catalog["groups"]["hours-restoration-race"]
            if field == "hoursReviewRace":
                group["instrument_args"].pop(field)
            else:
                group["requires"].remove(field)
            with self.subTest(field=field), self.assertRaisesRegex(qa.QaError, "Hours restoration diagnosis"):
                qa.validate_catalog(catalog, ROOT)

    def test_supervised_credits_preserves_beta_residues_without_beta_cleanup(self):
        group = self.catalog["groups"]["credits-supervised"]
        self.assertIn("Public root", group["fixture_state"])
        self.assertIn("No beta-file cleanup is permitted", group["manual_only"])
        self.assertIn("cleanup-complete", group["manual_only"])
        self.assertEqual(["qa-mutations", "temporary-session", "system-effects"], group["requires"])
        source = (ROOT / "app/src/androidTest/java/com/blackatsystems/miguardia/WeatherCreditsSessionInstrumentedTest.kt").read_text(encoding="utf-8")
        fingerprint = source.split("private fun privateFilesFingerprint", 1)[1].split("private fun await", 1)[0]
        self.assertIn("app.noBackupFilesDir", fingerprint)
        self.assertNotIn("beta-v1.preferences_pb", fingerprint)
        self.assertNotIn("restore-beta-file-absence-after-stop", source)

    def test_cold_beta_methods_are_ordered_in_separate_invocations(self):
        plan = qa.make_plan(self.catalog, self.args("--group", "beta-cold-process"))
        self.assertEqual(2, len(plan["invocations"]))
        self.assertTrue(plan["invocations"][0]["selector"].endswith("#prepareDurablePendingOperationForProcessDeath"))
        self.assertTrue(plan["invocations"][1]["selector"].endswith("#reopenAfterProcessDeathVerifiesOfflineAndRetransmitsExactlyOnce"))
        self.assertTrue(all(len(x["expected_methods"]) == 1 for x in plan["invocations"]))

    def test_class_filter_cannot_cross_group(self):
        with self.assertRaises(qa.QaError):
            qa.make_plan(self.catalog, self.args("--group", "app-rendering", "--class", "com.blackatsystems.miguardia.beta.BetaClosedRootTest"))


class InstrumentationParserTests(unittest.TestCase):
    cls = "com.blackatsystems.miguardia.SampleTest"

    def parse(self, output, code=0, methods=None):
        return qa.parse_instrumentation(output, code, self.cls, methods or ["first"])

    def test_success_requires_expected_method_and_terminal(self):
        out = status(self.cls, "first", 1) + status(self.cls, "first", 0) + "INSTRUMENTATION_CODE: -1\n"
        result = self.parse(out)
        self.assertEqual("PASSED", result["verdict"])
        self.assertEqual(1, result["passed"])
        self.assertEqual("FAILED", self.parse(out.replace("INSTRUMENTATION_CODE: -1", ""))["verdict"])

    def test_exit_zero_cannot_hide_test_or_runner_failure(self):
        for suffix in (status(self.cls, "first", -2), "INSTRUMENTATION_FAILED: Process crashed", "INSTRUMENTATION_RESULT: shortMsg=Process crashed", "FAILURES!!!"):
            with self.subTest(suffix=suffix):
                result = self.parse(suffix + "\nINSTRUMENTATION_CODE: -1")
                self.assertEqual("FAILED", result["verdict"])

    def test_assumption_and_ignore_are_pending_never_green(self):
        for code in (-3, -4):
            result = self.parse(status(self.cls, "first", code) + "INSTRUMENTATION_CODE: -1")
            self.assertEqual("PENDING", result["verdict"])
            self.assertEqual(1, result["skipped"])

    def test_no_tests_or_missing_tests_are_pending(self):
        self.assertEqual("PENDING", self.parse("OK (0 tests)\nINSTRUMENTATION_CODE: -1")["verdict"])
        result = self.parse(status(self.cls, "first", 0) + "INSTRUMENTATION_CODE: -1", methods=["first", "second"])
        self.assertEqual("PENDING", result["verdict"])
        self.assertEqual(["second"], result["pending_methods"])

    def test_unknown_or_duplicate_results_fail(self):
        for content in (status("com.unexpected.Test", "first", 0), status(self.cls, "first", 0) * 2):
            self.assertEqual("FAILED", self.parse(content + "INSTRUMENTATION_CODE: -1")["verdict"])

    def test_custom_harness_stage_does_not_count_as_test(self):
        out = "INSTRUMENTATION_STATUS: stage=PRIVATE OPINION\nINSTRUMENTATION_STATUS_CODE: 0\n"
        result = self.parse(out + status(self.cls, "first", 0) + "INSTRUMENTATION_CODE: -1")
        self.assertEqual("PASSED", result["verdict"])
        self.assertNotIn("PRIVATE OPINION", json.dumps(result))


class MockedExecutionTests(unittest.TestCase):
    def prepare_execution(self, root, module="app"):
        catalog = qa.load_catalog(ROOT)
        group = "beta-cold-process" if module == "app" else next(key for key, value in catalog["groups"].items() if value["module"] == "database")
        args = qa.parser().parse_args(["--group", group, "--serial", "mock-serial"])
        plan = qa.make_plan(catalog, args)
        (root / "tools").mkdir()
        (root / "tools/qa-groups.json").write_text("{}", encoding="utf-8")
        packages = {}
        for package, relative in qa.MODULES[module]["apks"].items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(package.encode("ascii"))
            packages[package] = {"installed": True, "apk_sha256": qa.sha256(path)}
        metadata = {"api": 36, "model": "Mock", "packages": packages}
        return catalog, args, plan, metadata

    def test_fresh_process_before_and_after_every_class(self):
        adb = mock.Mock()
        cls = "com.blackatsystems.miguardia.SampleTest"
        adb.run.return_value = subprocess.CompletedProcess([], 0, status(cls, "first", 0) + "INSTRUMENTATION_CODE: -1", "")
        plan = {"target_package": qa.MODULES["app"]["target"], "test_package": qa.MODULES["app"]["test_package"], "instrument_args": {}}
        result = qa.run_instrumentation(adb, plan, {"selector": cls, "expected_methods": ["first"]}, 30)
        self.assertEqual("PASSED", result["verdict"])
        self.assertEqual([mock.call.force_stop(plan["target_package"]), mock.call.run(mock.ANY, timeout=30), mock.call.force_stop(plan["target_package"])], adb.mock_calls)
        command = adb.run.call_args.args[0]
        self.assertNotIn("clear", command)
        self.assertNotIn("install", command)
        self.assertIn(qa.MODULES["app"]["test_package"] + "/" + qa.RUNNER, command)

    def test_timeout_stops_qa_and_propagates_for_failed_manifest(self):
        adb = mock.Mock()
        adb.run.side_effect = subprocess.TimeoutExpired("adb", 30)
        plan = {"target_package": qa.MODULES["app"]["target"], "test_package": qa.MODULES["app"]["test_package"], "instrument_args": {}}
        with self.assertRaises(subprocess.TimeoutExpired):
            qa.run_instrumentation(adb, plan, {"selector": "com.example.Test", "expected_methods": ["x"]}, 30)
        self.assertEqual(2, adb.force_stop.call_count)

    def test_force_stop_cannot_touch_production(self):
        adb = object.__new__(qa.Adb)
        with mock.patch.object(adb, "required") as required, self.assertRaises(qa.QaError):
            adb.force_stop("com.blackatsystems.miguardia")
        required.assert_not_called()

    def test_adb_option_cannot_select_a_shell(self):
        with mock.patch.object(qa.shutil, "which", return_value=str(Path(sys.executable))), self.assertRaises(qa.QaError):
            qa.Adb("python", "test-serial")

    def test_wrong_runner_target_fails_before_instrumentation(self):
        adb = object.__new__(qa.Adb)
        with mock.patch.object(adb, "required", side_effect=["device", "36", "instrumentation:com.production.test/evil.Runner (target=com.production)"]):
            with self.assertRaisesRegex(qa.QaError, "runner"):
                adb.metadata({"target_package": qa.MODULES["app"]["target"], "test_package": qa.MODULES["app"]["test_package"]})

    def test_malicious_installed_apk_path_is_not_sent_to_shell(self):
        adb = object.__new__(qa.Adb)
        target = qa.MODULES["app"]["target"]
        package = qa.MODULES["app"]["test_package"]
        registered = f"instrumentation:{package}/{qa.RUNNER} (target={target})"
        with mock.patch.object(adb, "required", side_effect=["device", "36", registered, "package:/data/app/a.apk;reboot"]), mock.patch.object(adb, "run") as run:
            with self.assertRaises(qa.QaError):
                adb.metadata({"target_package": target, "test_package": package})
            run.assert_not_called()

    def test_missing_or_ambiguous_installed_digest_fails_closed(self):
        adb = object.__new__(qa.Adb)
        target = qa.MODULES["database"]["target"]
        registered = f"instrumentation:{target}/{qa.RUNNER} (target={target})"
        path = "/data/app/qa/base.apk"
        digest = "a" * 64
        for returncode, output in ((1, ""), (0, ""), (0, digest + "  /different.apk"), (0, digest + "  " + path + "\n" + digest + "  " + path)):
            with self.subTest(returncode=returncode, output=output), mock.patch.object(adb, "required", side_effect=["device", "36", registered, "package:" + path]), mock.patch.object(adb, "run", return_value=subprocess.CompletedProcess([], returncode, output, "")):
                with self.assertRaisesRegex(qa.QaError, "hash is unavailable or ambiguous"):
                    adb.metadata({"target_package": target, "test_package": target})

    def test_split_installed_apks_are_not_supported(self):
        adb = object.__new__(qa.Adb)
        target = qa.MODULES["database"]["target"]
        registered = f"instrumentation:{target}/{qa.RUNNER} (target={target})"
        with mock.patch.object(adb, "required", side_effect=["device", "36", registered, "package:/data/app/qa/base.apk\npackage:/data/app/qa/split.apk"]), mock.patch.object(adb, "run") as run:
            with self.assertRaisesRegex(qa.QaError, "one installed universal QA APK"):
                adb.metadata({"target_package": target, "test_package": target})
            run.assert_not_called()

    def test_metadata_accepts_exact_single_apk_digest(self):
        adb = object.__new__(qa.Adb)
        target = qa.MODULES["database"]["target"]
        registered = f"instrumentation:{target}/{qa.RUNNER} (target={target})"
        path = "/data/app/qa/base.apk"
        with mock.patch.object(adb, "required", side_effect=["device", "36", registered, "package:" + path, "Mock", "Phone"]), mock.patch.object(adb, "run", return_value=subprocess.CompletedProcess([], 0, "A" * 64 + "  " + path + "\n", "")):
            result = adb.metadata({"target_package": target, "test_package": target})
        self.assertEqual("a" * 64, result["packages"][target]["apk_sha256"])

    def test_missing_local_apk_prevents_any_adb_call(self):
        for missing_package in qa.MODULES["app"]["apks"]:
            with self.subTest(package=missing_package), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                catalog, args, plan, _ = self.prepare_execution(root)
                (root / qa.MODULES["app"]["apks"][missing_package]).unlink()
                with mock.patch.object(qa, "Adb") as adb, mock.patch.object(qa, "run_instrumentation") as run:
                    manifest, _ = qa.execute(plan, args, catalog, root)
                self.assertEqual("FAILED", manifest["verdict"])
                self.assertFalse(manifest["apk_identity_verified"])
                self.assertIn("local QA APK is missing", manifest["failure"])
                adb.assert_not_called()
                run.assert_not_called()

    def test_mismatched_or_missing_installed_apk_prevents_instrumentation(self):
        for package in qa.MODULES["app"]["apks"]:
            for bad_digest in (None, "invalid", "0" * 64):
                with self.subTest(package=package, digest=bad_digest), tempfile.TemporaryDirectory() as temporary:
                    root = Path(temporary)
                    catalog, args, plan, metadata = self.prepare_execution(root)
                    metadata["packages"][package]["apk_sha256"] = bad_digest
                    device = mock.Mock()
                    device.metadata.return_value = metadata
                    with mock.patch.object(qa, "Adb", return_value=device), mock.patch.object(qa, "run_instrumentation") as run:
                        manifest, _ = qa.execute(plan, args, catalog, root)
                    self.assertEqual("FAILED", manifest["verdict"])
                    self.assertFalse(manifest["apk_identity_verified"])
                    run.assert_not_called()
                    device.force_stop.assert_not_called()

    def test_matching_apks_allow_instrumentation_for_app_and_database(self):
        for module in qa.MODULES:
            with self.subTest(module=module), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                catalog, args, plan, metadata = self.prepare_execution(root, module)
                device = mock.Mock()
                device.metadata.return_value = metadata
                results = []
                for invocation in plan["invocations"]:
                    cls = invocation["selector"].split("#")[0]
                    output = "".join(status(cls, method, 0) for method in invocation["expected_methods"]) + "INSTRUMENTATION_CODE: -1"
                    results.append(qa.parse_instrumentation(output, 0, invocation["selector"], invocation["expected_methods"]))
                with mock.patch.object(qa, "Adb", return_value=device), mock.patch.object(qa, "run_instrumentation", side_effect=results) as run:
                    manifest, _ = qa.execute(plan, args, catalog, root)
                self.assertEqual("PASSED", manifest["verdict"])
                self.assertTrue(manifest["apk_identity_verified"])
                self.assertEqual(len(plan["invocations"]), run.call_count)
                self.assertIn("not a proof that the source was freshly built", manifest["evidence_limit"])

    def test_manifest_omits_raw_device_output_and_counts_unexecuted(self):
        catalog = qa.load_catalog(ROOT)
        args = qa.parser().parse_args(["--group", "beta-cold-process", "--serial", "mock-serial"])
        plan = qa.make_plan(catalog, args)
        first = plan["invocations"][0]
        result = qa.parse_instrumentation("private secret\nINSTRUMENTATION_FAILED: bad", 0, first["selector"], first["expected_methods"])
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            _, _, _, metadata = self.prepare_execution(root)
            device = mock.Mock()
            device.metadata.return_value = metadata
            with mock.patch.object(qa, "Adb", return_value=device), mock.patch.object(qa, "run_instrumentation", return_value=result):
                manifest, directory = qa.execute(plan, args, catalog, root)
            self.assertEqual("FAILED", manifest["verdict"])
            self.assertEqual(1, manifest["unexecuted_invocations"])
            self.assertEqual(2, manifest["counts"]["pending"])
            self.assertTrue(directory.is_relative_to(root / ".local-evidence/qa"))
            text = (directory / "manifest.json").read_text(encoding="utf-8")
            self.assertNotIn("private secret", text)
            self.assertNotIn("INSTRUMENTATION_FAILED: bad", text)


if __name__ == "__main__":
    unittest.main()
