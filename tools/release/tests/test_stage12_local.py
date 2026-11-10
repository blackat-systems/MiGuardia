"""Local, mocked/tempfile adversaries; never contacts an Android device."""
import copy
import hashlib
import io
import json
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import stage12_local as s


class Stage12LocalTest(unittest.TestCase):
    def expected(self):
        return {"selector": "com.blackatsystems.miguardia.stage12.Stage12GuardTest#isolated",
                "allowed_selectors": ["com.blackatsystems.miguardia.stage12.Stage12GuardTest#isolated"],
                "run_id": "12000000-0000-4000-8000-000000000001", "qa_sha256": "a" * 64,
                "runner_sha256": "b" * 64, "mode": "neutral",
                "assertions": [{"case_id": "S12-B03", "subcase_id": "S12-B03-canary", "round": 0,
                                "assertion_key": "canary-unchanged", "level": "FILES_ISOLATED",
                                "evidence_kind": "BYTE_INVENTORY", "requires_tables": False}]}

    def arguments(self, expected=None):
        expected = expected or self.expected()
        return {"class": expected["selector"], "stage12Consent": s.CONSENT,
                "stage12RunId": expected["run_id"], "stage12QaSha256": expected["qa_sha256"],
                "stage12RunnerSha256": expected["runner_sha256"], "stage12Mode": expected["mode"]}

    def inventory(self):
        return {"count": 1, "entries": {"received-canary": {"bytes": 3, "sha256": "c" * 64}}}

    def bundle(self, expected=None, mutate=None, extra=None):
        expected = expected or self.expected()
        pair = {"before": self.inventory(), "after": self.inventory()}
        isolation = {"schema": "stage12-isolation-v1", "root_id": expected["run_id"],
                     "mode": expected["mode"], "files": copy.deepcopy(pair), "preferences": copy.deepcopy(pair),
                     "tables": {"status": "NOT_MEASURED", "reason": "Neutral byte inventory only"},
                     "resources": {"scoped": True, "identities": []}}
        receipt = {"schema": "stage12-receipt-v1", "run_id": expected["run_id"],
                   "assertions": [dict(a, result="PASS") for a in expected["assertions"]]}
        manifest = {"schema": "stage12-evidence-v1", "run_id": expected["run_id"],
                    "qa_sha256": expected["qa_sha256"], "runner_sha256": expected["runner_sha256"],
                    "selector": expected["selector"], "expected_methods": [expected["selector"]],
                    "counts": {"expected": 1, "started": 1, "passed": 1, "failed": 0, "skipped": 0}, "files": []}
        if mutate:
            mutate(manifest, receipt, isolation)
        payload = {"receipt.json": json.dumps(receipt).encode(), "isolation.json": json.dumps(isolation).encode()}
        payload.update(extra or {})
        if not manifest["files"]:
            manifest["files"] = [{"path": key, "bytes": len(value), "sha256": s.digest(value)} for key, value in payload.items()]
        payload["bundle.json"] = json.dumps(manifest).encode()
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w", compression=zipfile.ZIP_STORED) as archive:
            for name, content in payload.items():
                archive.writestr(name, content)
        return data.getvalue()

    def transcript(self, data=None, expected=None):
        expected = expected or self.expected()
        data = data if data is not None else self.bundle(expected)
        klass, method = expected["selector"].split("#")
        lines = []
        for status in (1, 0):
            lines += [f"INSTRUMENTATION_STATUS: class={klass}", f"INSTRUMENTATION_STATUS: test={method}",
                      "INSTRUMENTATION_STATUS: numtests=1", f"INSTRUMENTATION_STATUS_CODE: {status}"]
        proof = {"stage12Status": "COMPLETE", "stage12RunId": expected["run_id"],
                 "stage12Bundle": "stage12-" + expected["run_id"] + ".zip",
                 "stage12BundleSha256": s.digest(data), "stage12QaSha256": expected["qa_sha256"],
                 "stage12RunnerSha256": expected["runner_sha256"]}
        lines += [f"INSTRUMENTATION_RESULT: {key}={value}" for key, value in proof.items()]
        return "\n".join(lines + ["INSTRUMENTATION_CODE: -1"]) + "\n"

    def validate(self, data=None, text=None, expected=None, rc=0, timeout=False):
        expected = expected or self.expected()
        data = data if data is not None else self.bundle(expected)
        return s.validate_local_result(text if text is not None else self.transcript(data, expected), rc, timeout, data, expected)

    def test_valid_bundle_keeps_unmeasured_tables_explicit_and_does_not_approve_cases(self):
        result = self.validate()
        self.assertEqual("LOCAL_FORMAT_AND_TRANSPORT_VALID", result["validation"])
        self.assertEqual("NOT_GRANTED", result["case_approval"])
        self.assertEqual("NOT_MEASURED", result["isolation"]["table_evidence"])
        data = self.bundle(); self.validate(data, self.transcript(data).replace("\n", "\r\n"))

    def test_exit_failure_failure_with_exit_zero_timeout_and_partial_output_are_rejected(self):
        data = self.bundle(); good = self.transcript(data)
        cases = [(good, 1, False), ("Failure\n" + good, 0, False), (good, 0, True),
                 (good.rsplit("INSTRUMENTATION_CODE:", 1)[0], 0, False),
                 (good.replace("STATUS_CODE: 0", "STATUS_CODE: -2"), 0, False),
                 (good.replace("STATUS_CODE: 0", "STATUS_CODE: -3"), 0, False),
                 (good.replace("numtests=1", "numtests=2"), 0, False), (good + "late\n", 0, False)]
        for text, code, timeout in cases:
            with self.subTest(text=text[-80:], code=code, timeout=timeout), self.assertRaises(s.ValidationError):
                self.validate(data, text, rc=code, timeout=timeout)

    def test_missing_duplicate_wrong_hash_and_wrong_candidate_proofs_are_rejected(self):
        data = self.bundle(); good = self.transcript(data)
        proof_line = next(line for line in good.splitlines() if "stage12BundleSha256=" in line)
        wrong = [good.replace(proof_line + "\n", ""), good.replace(proof_line, proof_line + "\n" + proof_line),
                 good.replace(s.digest(data), "0" * 64), good.replace("stage12QaSha256=" + "a" * 64, "stage12QaSha256=" + "b" * 64),
                 good.replace("stage12Status=COMPLETE", "stage12Status=PARTIAL")]
        for text in wrong:
            with self.subTest(text=text[-120:]), self.assertRaises(s.ValidationError):
                self.validate(data, text)
        with self.assertRaises(s.ValidationError):
            self.validate(b"broken", good)
        broken = b"broken zip"
        with self.assertRaises(s.ValidationError):
            self.validate(broken, self.transcript(broken))

    def test_wrong_selector_missing_or_duplicate_method_completion_are_rejected(self):
        data = self.bundle(); text = self.transcript(data)
        for bad in [text.replace("test=isolated", "test=other"),
                    text.replace("INSTRUMENTATION_STATUS_CODE: 1", "INSTRUMENTATION_STATUS_CODE: 0"),
                    text.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0")]:
            with self.assertRaises(s.ValidationError): self.validate(data, bad)

    def test_status_exception_unknown_record_and_premature_proof_are_rejected(self):
        data = self.bundle(); good = self.transcript(data)
        injected = [good.replace("INSTRUMENTATION_STATUS_CODE: 0", "INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: changed\nINSTRUMENTATION_STATUS_CODE: 0"),
                    "INSTRUMENTATION_FUTURE_RECORD: ambiguous\n" + good,
                    " \tINSTRUMENTATION_STATUS_CODE: -2\n" + good,
                    "INSTRUMENTATION_RESULT: stream=premature\n" + good,
                    good.replace("INSTRUMENTATION_STATUS_CODE: 0", "INSTRUMENTATION_STATUS: current=2\nINSTRUMENTATION_STATUS_CODE: 0"),
                    good.replace("INSTRUMENTATION_STATUS_CODE: 0", "INSTRUMENTATION_STATUS_CODE: zero")]
        for bad in injected:
            with self.subTest(tail=bad[-100:]), self.assertRaises(s.ValidationError): self.validate(data, bad)

    def test_local_input_size_limit_is_checked_before_parsing(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "received.txt"; source.write_bytes(b"four")
            self.assertEqual(b"four", s.read_bounded(source, 4))
            with self.assertRaises(s.ValidationError): s.read_bounded(source, 3)

    def test_raw_exception_stream_cannot_hide_behind_success_status(self):
        data = self.bundle(); good = self.transcript(data)
        for error in ("java.lang.AssertionError: received canary changed",
                      "java.lang.Error: unexpected error",
                      "java.io.IOException: incomplete readback",
                      "Caused by: android.os.DeadObjectException"):
            text = good.replace("INSTRUMENTATION_STATUS_CODE: 0", error + "\nINSTRUMENTATION_STATUS_CODE: 0")
            with self.subTest(error=error), self.assertRaises(s.ValidationError):
                self.validate(data, text)

    def test_arguments_are_exact_allowlisted_and_modes_have_no_aliases(self):
        expected = self.expected(); good = self.arguments(expected)
        self.assertEqual(good, s.validate_arguments(good, expected))
        changes = [{**good, "serial": "not-allowed"}, {**good, "stage12Mode": "NORMAL"},
                   {**good, "stage12Consent": "old"}, {**good, "class": good["class"] + ",other"},
                   {key: value for key, value in good.items() if key != "stage12RunId"}]
        for arguments in changes:
            with self.assertRaises(s.ValidationError): s.validate_arguments(arguments, expected)
        with self.assertRaises(s.ValidationError): s.validate_arguments(good, {**expected, "allowed_selectors": []})

    def test_existing_ui_method_needs_exact_reviewed_allowlist_not_namespace_only(self):
        selected = "com.blackatsystems.miguardia.TransientConfirmationComposeTest#weatherNoticeLeavesCloseVisibleAndTouchableAtEveryThemeAndFontScale"
        expected = self.expected(); expected.update(selector=selected, allowed_selectors=[selected])
        self.assertEqual(selected, s.validate_arguments(self.arguments(expected), expected)["class"])
        unreviewed = self.arguments(expected); unreviewed["class"] = selected.replace("#weather", "#other")
        with self.assertRaises(s.ValidationError): s.validate_arguments(unreviewed, expected)
        expected["allowed_selectors"] = ["com.blackatsystems.miguardia.*#*"]
        with self.assertRaises(s.ValidationError): s.validate_arguments(self.arguments(expected), expected)

    def test_bundle_counts_and_isolation_counts_are_strict_integers_not_strings_or_bools(self):
        for value in (True, "1", 2, -1):
            data = self.bundle(mutate=lambda m, r, i: m["counts"].update(expected=value))
            with self.subTest(value=value), self.assertRaises(s.ValidationError): self.validate(data)
        for value in (True, "1", 0):
            data = self.bundle(mutate=lambda m, r, i: i["files"]["before"].update(count=value))
            with self.subTest(value=value), self.assertRaises(s.ValidationError): self.validate(data)

    def test_receipt_cannot_omit_duplicate_skip_or_downgrade_an_assertion(self):
        edits = [lambda m, r, i: r.update(assertions=[]),
                 lambda m, r, i: r["assertions"].append(copy.deepcopy(r["assertions"][0])),
                 lambda m, r, i: r["assertions"][0].update(result="SKIP"),
                 lambda m, r, i: r["assertions"][0].update(level="DOMAIN_JVM"),
                 lambda m, r, i: r["assertions"][0].update(evidence_kind="UNEXPECTED"),
                 lambda m, r, i: r["assertions"][0].update(requires_tables=True)]
        for edit in edits:
            data = self.bundle(mutate=edit)
            with self.assertRaises(s.ValidationError): self.validate(data)

    def test_tables_not_measured_never_becomes_sqlite_conservation(self):
        expected = self.expected(); expected["assertions"][0].update(level="ROOM_ISOLATED", requires_tables=True)
        data = self.bundle(expected)
        with self.assertRaises(s.ValidationError): self.validate(data, expected=expected)
        def measured(m, r, i):
            tables = {"count": 1, "entries": {"shifts": {"rows": 2, "sha256": "d" * 64}}}
            i["tables"] = {"status": "MEASURED", "before": tables, "after": copy.deepcopy(tables)}
        data = self.bundle(expected, measured)
        self.assertEqual("MEASURED", self.validate(data, expected=expected)["isolation"]["table_evidence"])

    def test_changed_received_files_preferences_tables_or_mode_are_rejected_without_repair(self):
        edits = [lambda m, r, i: i["files"]["after"]["entries"]["received-canary"].update(sha256="d" * 64),
                 lambda m, r, i: i["preferences"]["after"]["entries"]["received-canary"].update(bytes=4),
                 lambda m, r, i: i.update(mode="quiet"),
                 lambda m, r, i: i["resources"].update(scoped=False),
                 lambda m, r, i: i["resources"].update(identities=["PendingIntent:29082026"]),
                 lambda m, r, i: i["resources"].update(identities=["duplicate", "duplicate"])]
        for edit in edits:
            data = self.bundle(mutate=edit)
            unchanged = bytes(data)
            with self.assertRaises(s.ValidationError): self.validate(data)
            self.assertEqual(unchanged, data)

    def test_zip_paths_unlisted_files_size_and_hash_fail_closed_without_extraction(self):
        for name in ("../escape", "/absolute", "a\\b", "C:drive", "a/./b", "a/../b", "trailing."):
            data = self.bundle(extra={name: b"own"})
            with self.subTest(name=name), self.assertRaises(s.ValidationError): self.validate(data)
        for item in ({"path": "receipt.json", "bytes": 1, "sha256": "0" * 64},
                     {"path": "bundle.json", "bytes": 1, "sha256": "0" * 64}):
            data = self.bundle(mutate=lambda m, r, i: m.update(files=[item]))
            with self.assertRaises(s.ValidationError): self.validate(data)
        with patch.object(s, "MAX_MEMBER", 1), self.assertRaises(s.ValidationError): self.validate()

    def test_duplicate_and_symlink_zip_entries_are_rejected(self):
        data = self.bundle()
        for duplicate in (True, False):
            out = io.BytesIO()
            with zipfile.ZipFile(io.BytesIO(data)) as original, zipfile.ZipFile(out, "w") as archive:
                for info in original.infolist(): archive.writestr(info.filename, original.read(info))
                info = zipfile.ZipInfo("receipt.json" if duplicate else "symbolic")
                if not duplicate: info.external_attr = (stat.S_IFLNK | 0o777) << 16
                with __import__("warnings").catch_warnings():
                    __import__("warnings").simplefilter("ignore", UserWarning)
                    archive.writestr(info, b"target")
            bad = out.getvalue()
            with self.assertRaises(s.ValidationError): self.validate(bad)

    def test_raw_zip_name_cannot_hide_traversal_after_a_nul(self):
        original = self.bundle(); out = io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(original)) as source, zipfile.ZipFile(out, "w") as target:
            for info in source.infolist():
                name = "receipt.jsonXXXXXXXX" if info.filename == "receipt.json" else info.filename
                target.writestr(name, source.read(info))
        malformed = out.getvalue().replace(b"receipt.jsonXXXXXXXX", b"receipt.json\x00/../xxx")
        with self.assertRaises(s.ValidationError): self.validate(malformed)

    def test_crc_failure_and_member_content_hash_mismatch_are_distinct_closed_failures(self):
        original = self.bundle(extra={"artifacts/canary.txt": b"UNIQUE_CANARY_VALUE"})
        corrupted = original.replace(b"UNIQUE_CANARY_VALUE", b"UNIQUE_CANARY_VALUF", 1)
        self.assertNotEqual(original, corrupted)
        with self.assertRaises(s.ValidationError): self.validate(corrupted)
        out = io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(original)) as source, zipfile.ZipFile(out, "w") as target:
            for info in source.infolist():
                content = source.read(info)
                if info.filename == "artifacts/canary.txt": content = b"REPLACED_SAME_SIZE_"
                target.writestr(info.filename, content)
        with self.assertRaises(s.ValidationError): self.validate(out.getvalue())

    def test_archive_expansion_limit_is_checked_before_reading_member_content(self):
        original = self.bundle(extra={"artifacts/compressed.txt": b"0" * 100000})
        out = io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(original)) as source, zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as target:
            for info in source.infolist(): target.writestr(info.filename, source.read(info))
        with self.assertRaises(s.ValidationError): self.validate(out.getvalue())

    def test_json_duplicate_keys_nonfinite_and_oversize_are_rejected(self):
        for value in (b'{"a":1,"a":2}', b'{"a":NaN}', b'{"a":Infinity}', b'{"a":1e999}', b'not-json'):
            with self.assertRaises(s.ValidationError): s.strict_json(value)
        with self.assertRaises(s.ValidationError): s.strict_json(b'{}', 1)

    def sdk_fixture(self, root, role="runner"):
        apk = root / "fixture.apk"
        with zipfile.ZipFile(apk, "w") as archive: archive.writestr("AndroidManifest.xml", b"synthetic")
        aapt, java, jar = root / "aapt2.exe", root / "java.exe", root / "lib/apksigner.jar"
        jar.parent.mkdir(); aapt.write_bytes(b"mock"); java.write_bytes(b"mock"); jar.write_bytes(b"mock")
        package = s.TEST_PACKAGE if role == "runner" else s.QA
        xml = f'E: manifest\n  A: package="{package}"\n'
        if role == "runner":
            xml += f'  E: instrumentation\n    A: android:name="{s.RUNNER}"\n    A: android:targetPackage="{s.QA}"\n'
        badging = f"package: name='{package}' versionCode='8' versionName='3.0-fixture'\n"
        cert = "Signer #1 certificate SHA-256 digest: " + s.CERTIFICATE + "\n"
        return apk, aapt, java, jar, [xml, badging, cert]

    def inspect(self, fixture, outputs=None, code=0, role="runner", expected_hash=None):
        apk, aapt, java, jar, good = fixture
        results = [subprocess.CompletedProcess([], code, stdout=text, stderr="") for text in (outputs or good)]
        with patch.object(s.subprocess, "run", side_effect=results) as run:
            result = s.inspect_local_apk(apk, expected_hash or s.digest(apk.read_bytes()), role, aapt, java, jar)
            self.assertEqual(3, run.call_count)
            for call in run.call_args_list:
                self.assertIn(Path(call.args[0][0]).name, {"aapt2.exe", "java.exe"})
                self.assertFalse(call.kwargs["shell"])
                self.assertNotIn("install", call.args[0])
            return result

    def test_local_preinstall_requires_exact_hash_package_target_runner_and_certificate(self):
        with tempfile.TemporaryDirectory() as temp:
            fixture = self.sdk_fixture(Path(temp)); good = fixture[-1]
            self.assertEqual(s.RUNNER, self.inspect(fixture)["runner"])
            changes = [(0, good[0].replace(s.TEST_PACKAGE, s.QA)),
                       (0, good[0].replace('android:targetPackage="' + s.QA + '"', 'android:targetPackage="wrong"')),
                       (0, good[0].replace(s.RUNNER, "androidx.test.runner.AndroidJUnitRunner")),
                       (2, good[2].replace(s.CERTIFICATE, "d" * 64)),
                       (1, good[1].replace(s.TEST_PACKAGE, "wrong"))]
            for index, value in changes:
                outputs = list(good); outputs[index] = value
                with self.subTest(index=index), self.assertRaises(s.ValidationError): self.inspect(fixture, outputs)
            with patch.object(s.subprocess, "run") as run, self.assertRaises(s.ValidationError):
                s.inspect_local_apk(*fixture[:1], "0" * 64, "runner", *fixture[1:4])
            run.assert_not_called()

    def test_sdk_failure_timeout_and_wrong_tool_never_turn_into_identity_proof(self):
        with tempfile.TemporaryDirectory() as temp:
            fixture = self.sdk_fixture(Path(temp))
            with self.assertRaises(s.ValidationError): self.inspect(fixture, code=1)
            with patch.object(s.subprocess, "run", side_effect=subprocess.TimeoutExpired("mock", 30)), self.assertRaises(s.ValidationError):
                s.inspect_local_apk(fixture[0], s.digest(fixture[0].read_bytes()), "runner", *fixture[1:4])
            wrong = Path(temp) / "other.exe"; wrong.write_bytes(b"mock")
            with patch.object(s.subprocess, "run") as run, self.assertRaises(s.ValidationError):
                s.inspect_local_apk(fixture[0], s.digest(fixture[0].read_bytes()), "runner", wrong, fixture[2], fixture[3])
            run.assert_not_called()

    def test_qa_product_metadata_is_distinct_from_the_instrumentation_runner(self):
        with tempfile.TemporaryDirectory() as temp:
            fixture = self.sdk_fixture(Path(temp), "qa")
            self.assertEqual(s.QA, self.inspect(fixture, role="qa")["package"])
            xml = fixture[-1][0] + '  E: instrumentation\n    A: android:name="bad"\n'
            with self.assertRaises(s.ValidationError): self.inspect(fixture, [xml, *fixture[-1][1:]], role="qa")

    def plan(self):
        rows = []
        for identifier in s.IDS:
            cases = [identifier + "-base"]
            if identifier == "C001": cases = ["C001-" + suffix for suffix in s.CALENDAR_ORACLES]
            if identifier == "C002": cases += [f"C002-extra-{i}" for i in range(62)]
            row = {"id": identifier, "requirement": "Requirement " + identifier, "state": "SIN_EJECUTAR",
                   "execution_admission": {"allowed": False}, "assertion_map": {"coverage": "PARTIAL"},
                   "assertions": [], "subcases": []}
            for case in cases:
                key = case + ".ui"
                row["assertions"].append({"key": key, "subcase_id": case, "required_level": "UI_COMPOSE", "mandatory": True, "execution": "NONE"})
                row["subcases"].append({"id": case, "state": "SIN_EJECUTAR", "oracle": {"expected": "explicit condition", "assertion_keys": [key]}})
            rows.append(row)
        by = {r["id"]: r for r in rows}
        for case in by["C001"]["subcases"]:
            month, count, days = s.CALENDAR_ORACLES[case["id"][5:]]
            case["oracle"]["calendar"] = {"month": month, "rows": count, "days": days}
        by["C090"]["identity_delete_oracle"] = {"cancel_keeps_both": True, "only_difference": "UUID", "cas_required": True}
        by["W023"]["resource_gate"] = {"forbidden_pending_intent_request_code": 29082026, "admitted": False}
        by["V013"]["no_global_talkback_activation"] = True
        for key in ("C058", "V016"):
            by[key]["capture_policy"] = "QA_WINDOW_OR_NODE_ONLY_NO_GLOBAL_SCREENSHOT_NO_GLOBAL_XML"
        by["X015"]["received_conservation"] = dict(tables={}, preferences={}, files={})
        for index, level in enumerate(("DOMAIN_JVM", "REMOTE_VIEWS_REAL")):
            by["W010"]["assertions"].append(dict(by["W010"]["assertions"][0], key=f"W010-level-{index}", required_level=level))
        by["W010"]["subcases"][0]["oracle"]["assertion_keys"] = [a["key"] for a in by["W010"]["assertions"]]
        plan = {"rows": rows, "round_policy": {"required_complete_rounds": 2, "same_final_candidate": True,
                 "base_subcases_per_round": 506, "required_case_rounds": 1012}}
        contract = "\n".join(f'| {r["id"]} | {r["requirement"]} |' for r in rows)
        plan["contract_sha256"] = s.digest(contract.encode("utf-8"))
        return plan, contract

    def test_plan_requires_441_ids_506_same_base_subcases_two_rounds_and_per_assertion_levels(self):
        plan, contract = self.plan(); baseline = copy.deepcopy(plan)
        self.assertEqual(1012, s.validate_plan(plan, baseline, contract)["required_case_rounds"])
        def move_subcases(p):
            for key in ("subcases", "assertions"):
                p["rows"][2][key], p["rows"][3][key] = p["rows"][3][key], p["rows"][2][key]
        edits = [lambda p: p["rows"].pop(), lambda p: p["rows"][0]["subcases"].pop(),
                 move_subcases,
                 lambda p: p["rows"][0]["assertions"][0].update(required_level="ACTIVITY_AND_OR_ROOM"),
                 lambda p: p["rows"][0]["subcases"][0]["oracle"]["calendar"].update(month="2026-02"),
                 lambda p: p["rows"][0]["subcases"][0]["oracle"].update(assertion_keys=[p["rows"][0]["assertions"][1]["key"]]),
                 lambda p: p.update(contract_sha256="0" * 64),
                 lambda p: p["round_policy"].update(required_complete_rounds=1),
                 lambda p: next(r for r in p["rows"] if r["id"] == "C090")["assertion_map"].update(coverage="FULL"),
                 lambda p: p["rows"][0]["execution_admission"].update(allowed=True)]
        for edit in edits:
            bad = copy.deepcopy(plan); edit(bad)
            with self.assertRaises(s.ValidationError): s.validate_plan(bad, baseline, contract)


if __name__ == "__main__":
    unittest.main()
