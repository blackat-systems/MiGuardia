"""Synthetic dictionaries only: no real UID/provider/font, device or SDK proof."""
import ast
import copy
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import stage12_normal_bootstrap as n


class NormalBootstrapProtocolTest(unittest.TestCase):
    def expected(self, state="ABSENT"):
        # 12345 and com.example... are MOCK identities, never real platform admission.
        target = {"package": n.QA, "user_id": 0, "uid": 12345, "certificate_sha256": n.CERT,
                  "apk_sha256": "a" * 64, "uid_observation_sha256": "c" * 64}
        emoji = {"state": state, "query_action": n.QUERY, "system_only": True,
                 "discovery_result": n.EMOJI_DISCOVERY[state],
                 "discovery_sha256": "d" * 64, "provider": None, "fonts": []}
        if state == "IDENTIFIED_ADMITTED":
            emoji["provider"] = {"package": "com.example.stage12.systemfonts",
                "class": "com.example.stage12.systemfonts.FontsProvider", "authority": "com.example.stage12.fonts",
                "uid": 1000, "certificate_sha256": "e" * 64, "system_app": True,
                "identity_observation_sha256": "f" * 64, "local_only_proof_sha256": "0" * 64}
            emoji["fonts"] = [{"uri": "content://com.example.stage12.fonts/font/7",
                "local_path": "/system/fonts/MockStage12.ttf", "bytes": 17, "sha256": "1" * 64}]
        return {"schema": "stage12-normal-bootstrap-expected-v1", "authority_sha256": n.AUTHORITY,
            "run_id": "12000000-0000-4000-8000-000000000001", "round": 1,
            "qa_sha256": "a" * 64, "runner_sha256": "b" * 64, "candidate_role": n.ROLE,
            "avd": {"name": n.AVD, "api": 36, "serial": "emulator-5588"}, "target": target,
            "runner": {"package": n.TEST, "class": n.RUNNER, "certificate_sha256": n.CERT,
                       "apk_sha256": "b" * 64, "identity_observation_sha256": "8" * 64},
            "root_id": "12000000-0000-4000-8000-000000000001",
            "art_resource": {"id": n.ART_ID, "path": n.ART_PATH, "package": n.QA, "user_id": 0, "uid": 12345},
            "emoji": emoji}

    def witness(self, expected):
        observed = {k: copy.deepcopy(v) for k, v in expected.items() if k not in {"schema", "art_resource", "emoji"}}
        observed["schema"] = "stage12-normal-bootstrap-testimony-v1"
        observed["providers"] = {"fileprovider_enabled": True, "startup_enabled": True}
        snap = {"status": "HASHED", "exists": True, "metadata": {"regular": True, "symlink": False, "uid": 12345, "bytes": 42}, "sha256": "2" * 64}
        observed["art"] = {"case_id": "S12-B03", "subcase_id": "normal-art", "resource": copy.deepcopy(expected["art_resource"]),
            "shared_across_rounds": True, "before": copy.deepcopy(snap), "after": copy.deepcopy(snap),
            "operations": ["READ_METADATA_OR_HASH_ONLY"]}
        plan = expected["emoji"]
        admitted = plan["state"] == "IDENTIFIED_ADMITTED"
        observed["emoji"] = {"case_id": "S12-B03", "subcase_id": "normal-emoji", "state": plan["state"],
            "discovery_result": plan["discovery_result"],
            "discovery_sha256": plan["discovery_sha256"], "provider": copy.deepcopy(plan["provider"]),
            "reads": copy.deepcopy(plan["fonts"]), "font_branch_executed": admitted,
            "operations": ["SYSTEM_PROVIDER_DISCOVERY"] + (["EXACT_CONTENT_URI_READ", "EXACT_LOCAL_FONT_READ"] if admitted else [])}
        basename = "stage12-" + expected["run_id"] + "-readback.bin"
        observed["fileprovider"] = {"case_id": "S12-B03", "subcase_id": "normal-fileprovider", "root_id": expected["run_id"],
            "events": [{"event": name, "elapsed_ns": i + 1, "process_fresh": True} for i, name in enumerate(n.EVENTS)],
            "provider_read": {"uri": "content://" + n.QA + ".fileprovider/report_artifacts/" + basename,
                              "bytes": len(n.READBACK_BYTES), "sha256": n.digest(n.READBACK_BYTES)},
            "independent_read": {"reader": "OWN_FILE_INPUT", "relative_path": "files/reports/artifacts/" + basename,
                                 "bytes": len(n.READBACK_BYTES), "sha256": n.digest(n.READBACK_BYTES)},
            "escape_checks": [{"probe": name, "uri": uri, "outcome": "CANONICAL_ESCAPE_REJECTED",
                               "exception_class": "java.lang.SecurityException", "guard_message": "Resolved path jumped beyond configured root",
                               "core_classes_sha256": n.CORE_CLASSES_SHA256}
                              for name, uri in [("TRAVERSAL_URI", "content://" + n.QA + ".fileprovider/report_artifacts/..%2F..%2F..%2Fcanaries%2Ffile.canary"),
                                  ("OWN_SYMLINK_OUTSIDE_PROVIDER_ROOT", "content://" + n.QA + ".fileprovider/report_artifacts/stage12-" + expected["run_id"] + "-escape.bin")]],
            "canary": {"relative_path": "canaries/file.canary", "before_sha256": n.digest(n.CANARY_BYTES), "after_sha256": n.digest(n.CANARY_BYTES)}}
        observed["business"] = {"tables": {"status": "NOT_MEASURED", "reason": "Technical resources only"}, "claims": []}
        return observed

    def assert_rejected(self, expected=None, observed=None):
        expected = expected if expected is not None else self.expected()
        observed = observed if observed is not None else self.witness(expected)
        with self.assertRaises(n.ValidationError):
            n.validate_testimony(observed, expected)

    def test_three_emoji_states_are_distinct_and_never_business_acceptance(self):
        for state in sorted(n.EMOJI_STATES):
            with self.subTest(state=state):
                expected = self.expected(state)
                result = n.validate_testimony(self.witness(expected), expected)
                self.assertEqual(state, result["emoji_state"])
                for key in ("runtime_verified", "business_evidence", "normal_bootstrap_proven", "round_accepted", "compilation_state_equal", "r4_timing_evidence"):
                    self.assertIs(False, result[key])
                self.assertEqual(state == "DISCOVERY_NOT_ADMITTED", "EMOJI_BRANCH_NOT_ADMITTED" in result["limits"])

    def test_art_change_absence_and_unreadable_are_preserved_without_repair(self):
        expected = self.expected()
        observed = self.witness(expected)
        observed["art"]["after"]["sha256"] = "3" * 64
        self.assertTrue(n.validate_testimony(observed, expected)["art_observed_change"])
        self.assertEqual("3" * 64, observed["art"]["after"]["sha256"])
        observed["art"]["before"] = {"status": "ABSENT", "exists": False, "absence_basis": "ENOENT_CONFIRMED"}
        self.assertTrue(n.validate_testimony(observed, expected)["art_observed_change"])
        for metadata in (None, observed["art"]["after"]["metadata"]):
            observed["art"]["after"] = {"status": "UNREADABLE", "metadata": metadata, "reason": "EACCES"}
            self.assertIn("ART_UNREADABLE_NO_PERMISSION_ESCALATION", n.validate_testimony(observed, expected)["limits"])
        for basis in (None, "FILE_EXISTS_FALSE", "EACCES", "EPERM"):
            observed["art"]["before"] = {"status": "ABSENT", "exists": False, "absence_basis": basis}
            self.assert_rejected(expected, observed)

    def test_wrong_art_id_package_user_uid_and_every_path_alias_reject(self):
        for key, values in {"id": ["ART_*", "ROOM_ISOLATED"], "package": [n.PUBLIC, n.TEST],
            "user_id": [1, False], "uid": [12346, True], "path": [n.ART_PATH + ".bak", n.ART_PATH.replace("/0/", "/1/"),
            n.ART_PATH.replace(n.QA, n.PUBLIC), "/data/misc/profiles/cur/0/../0/" + n.QA + "/primary.prof", n.ART_PATH + "/", "*"]}.items():
            for value in values:
                with self.subTest(key=key, value=value):
                    expected = self.expected(); expected["art_resource"][key] = value
                    self.assert_rejected(expected)

    def test_identity_requires_user0_verified_qa_uid_certificate_pins_and_dedicated_avd(self):
        mutations = [("target", "package", n.PUBLIC), ("target", "user_id", False), ("target", "uid", 1000),
            ("target", "uid", 112345), ("target", "certificate_sha256", "e" * 64), ("target", "apk_sha256", "b" * 64),
            ("target", "uid_observation_sha256", ""), ("avd", "name", "Stage11"), ("avd", "api", 35), ("avd", "serial", "Samsung"),
            ("avd", "serial", ["emulator-5588"])]
        for section, key, value in mutations:
            with self.subTest(section=section, key=key):
                expected = self.expected(); expected[section][key] = value
                self.assert_rejected(expected)
        for key, value in [("authority_sha256", "a" * 64), ("run_id", "uuid-*"), ("round", True),
                           ("candidate_role", "INTERMEDIATE_GUARDS_ONLY"), ("root_id", "other")]:
            expected = self.expected(); expected[key] = value
            self.assert_rejected(expected)
        for key, value in [("package", n.PUBLIC), ("class", "androidx.test.runner.AndroidJUnitRunner"),
                           ("certificate_sha256", "9" * 64), ("apk_sha256", "a" * 64)]:
            expected = self.expected(); expected["runner"][key] = value
            self.assert_rejected(expected)

    def test_observed_identity_cannot_drift_from_expected_or_coerce_boolean_user(self):
        for section, key, value in [("target", "user_id", False), ("target", "uid", 12346), ("avd", "serial", "emulator-5590")]:
            expected = self.expected(); observed = self.witness(expected); observed[section][key] = value
            self.assert_rejected(expected, observed)
        for key in ("qa_sha256", "runner_sha256"):
            expected = self.expected(); observed = self.witness(expected); observed[key] = "0" * 64
            self.assert_rejected(expected, observed)

    def test_wrong_provider_authority_signature_system_flag_and_generic_reads_reject(self):
        expected = self.expected("IDENTIFIED_ADMITTED")
        for key, value in [("package", n.QA), ("class", "*"), ("authority", "*"), ("authority", n.QA + ".fileprovider"),
            ("certificate_sha256", "unverified"), ("system_app", False), ("local_only_proof_sha256", ""), ("uid", 12345)]:
            with self.subTest(key=key):
                plan = copy.deepcopy(expected); plan["emoji"]["provider"][key] = value
                self.assert_rejected(plan)
        for key, value in [("certificate_sha256", "0" * 64), ("authority", "com.other.fonts"), ("uid", 1001)]:
            observed = self.witness(expected); observed["emoji"]["provider"][key] = value
            self.assert_rejected(expected, observed)
        for operation in ("GENERIC_RESOLVER", "NETWORK", "DOWNLOAD"):
            observed = self.witness(expected); observed["emoji"]["operations"].append(operation)
            self.assert_rejected(expected, observed)
        for key, value in (("uri", "content://*"), ("sha256", "9" * 64), ("local_path", "/data/local/tmp/font.ttf")):
            observed = self.witness(expected); observed["emoji"]["reads"][0][key] = value
            self.assert_rejected(expected, observed)
        for key, value in [("uri", "https://fonts.example/font.ttf"), ("uri", "content://com.example.stage12.fonts/*"),
            ("uri", "content://com.example.stage12.fonts/../font"), ("uri", "content://com.example.stage12.fonts/font%2F7"),
            ("local_path", "/data/local/tmp/download.ttf"), ("local_path", "/system/fonts/../secret.ttf"), ("local_path", "/system/fonts/*")]:
            plan = copy.deepcopy(expected); plan["emoji"]["fonts"][0][key] = value
            self.assert_rejected(plan)

    def test_emoji_discovery_or_absence_cannot_execute_font_branch(self):
        for state in ("DISCOVERY_NOT_ADMITTED", "ABSENT"):
            expected = self.expected(state)
            for change in ("reads", "executed", "network", "provider"):
                observed = self.witness(expected)
                if change == "reads": observed["emoji"]["reads"] = [{"uri": "content://*"}]
                elif change == "executed": observed["emoji"]["font_branch_executed"] = True
                elif change == "network": observed["emoji"]["operations"].append("NETWORK_OR_DOWNLOAD")
                else: observed["emoji"]["provider"] = {"authority": "unverified"}
                self.assert_rejected(expected, observed)
        expected = self.expected("ABSENT"); expected["emoji"]["discovery_result"] = "PERMISSION_DENIED"
        self.assert_rejected(expected)

    def test_first_uri_requires_fresh_process_root_and_admission_order(self):
        for change in ("disabled", "old-root", "old-process", "reorder", "missing", "same-time"):
            expected = self.expected(); observed = self.witness(expected); fp = observed["fileprovider"]
            if change == "disabled": observed["providers"]["startup_enabled"] = False
            elif change == "old-root": fp["root_id"] = "another-root"
            elif change == "old-process": fp["events"][0]["process_fresh"] = False
            elif change == "reorder": fp["events"][0], fp["events"][2] = fp["events"][2], fp["events"][0]
            elif change == "missing": fp["events"].pop()
            else: fp["events"][2]["elapsed_ns"] = fp["events"][1]["elapsed_ns"]
            self.assert_rejected(expected, observed)

    def test_uri_independent_readback_escape_before_io_and_canary_are_all_required(self):
        for change in ("uri", "reader", "path", "bytes", "digest", "escape-open", "escape-uri", "bool-open", "guard", "jar", "missing-escape", "canary"):
            expected = self.expected(); observed = self.witness(expected); fp = observed["fileprovider"]
            if change == "uri": fp["provider_read"]["uri"] = "content://" + n.PUBLIC + ".fileprovider/report_artifacts/foreign"
            elif change == "reader": fp["independent_read"]["reader"] = "SAME_PROVIDER_CALLBACK"
            elif change == "path": fp["independent_read"]["relative_path"] = "../received"
            elif change == "bytes": fp["independent_read"]["bytes"] += 1
            elif change == "digest": fp["provider_read"]["sha256"] = "0" * 64
            elif change == "escape-open": fp["escape_checks"][0]["opens"] = 1
            elif change == "escape-uri": fp["escape_checks"][0]["uri"] = "content://*/anything"
            elif change == "bool-open": fp["escape_checks"][0]["opens"] = False
            elif change == "guard": fp["escape_checks"][0]["guard_message"] = "Excluded resolver operation"
            elif change == "jar": fp["escape_checks"][0]["core_classes_sha256"] = "0" * 64
            elif change == "missing-escape": fp["escape_checks"].pop()
            else: fp["canary"]["after_sha256"] = "0" * 64
            self.assert_rejected(expected, observed)

    def test_no_ids_or_not_measured_can_be_promoted_to_business_normal_or_r4(self):
        for claim in ("ROOM_ISOLATED", "NORMAL_BOOTSTRAP", "R4", "COMPILATION_STATE_EQUAL", "C001", "OEM_HUMAN"):
            expected = self.expected(); observed = self.witness(expected); observed["business"]["claims"] = [claim]
            self.assert_rejected(expected, observed)
        for section in ("art", "emoji", "fileprovider"):
            for key, value in (("case_id", "C001"), ("subcase_id", "*")):
                expected = self.expected(); observed = self.witness(expected); observed[section][key] = value
                self.assert_rejected(expected, observed)
        for change in ("MEASURED", "empty-reason", "restore", "not-shared", "bad-metadata"):
            expected = self.expected(); observed = self.witness(expected)
            if change == "MEASURED": observed["business"]["tables"]["status"] = change
            elif change == "empty-reason": observed["business"]["tables"]["reason"] = " "
            elif change == "restore": observed["art"]["operations"].append("RESTORE")
            elif change == "not-shared": observed["art"]["shared_across_rounds"] = False
            else: observed["art"]["after"]["metadata"]["symlink"] = True
            self.assert_rejected(expected, observed)

    def test_strict_json_extra_keys_and_pure_import_boundary(self):
        for raw in (b'{"a":1,"a":2}', b'{"a":NaN}', b'{"a":1e999}', b'{"a":-1e999}', b'\xff', b'[] trailing'):
            with self.assertRaises(n.ValidationError): n.strict_loads(raw)
        expected = self.expected(); observed = self.witness(expected); observed["accepted"] = True
        self.assert_rejected(expected, observed)
        observed = self.witness(expected)
        self.assertEqual(observed, n.strict_loads(json.dumps(observed).encode()))
        source = Path(n.__file__).read_text(encoding="utf-8")
        imports = {node.module for node in ast.walk(ast.parse(source)) if isinstance(node, ast.ImportFrom)}
        imports |= {alias.name for node in ast.walk(ast.parse(source)) if isinstance(node, ast.Import) for alias in node.names}
        self.assertEqual({"__future__", "hashlib", "json", "math", "re", "uuid"}, imports)

    def test_unreadable_diagnostic_change_never_becomes_measured_art_change(self):
        expected = self.expected(); observed = self.witness(expected)
        observed["art"]["before"] = {"status": "UNREADABLE", "metadata": None, "reason": "EACCES"}
        observed["art"]["after"] = {"status": "UNREADABLE", "metadata": None, "reason": "EPERM"}
        result = n.validate_testimony(observed, expected)
        self.assertIsNone(result["art_observed_change"])
        self.assertTrue(result["art_observation_changed"])

    def test_protocol_matches_validator_and_pending_template_cannot_admit_real_run(self):
        # Public normative fields from the 2026-10-23 protocol, maintained as an
        # independent oracle rather than generated from the validator. Observed
        # identities/admissions are omitted: this synthetic template grants none.
        fixture = Path(__file__).resolve().parent / "fixtures" / "stage12-normal-bootstrap-protocol-v1.json"
        protocol = json.loads(fixture.read_text(encoding="utf-8"))
        self.assertEqual(n.AUTHORITY, protocol["authority"]["sha256"])
        self.assertEqual(n.SOURCE_REVIEW, protocol["source_review"]["sha256"])
        self.assertEqual(set(self.expected()), set(protocol["schemas"]["expected"]["required"]))
        self.assertEqual(set(self.witness(self.expected())), set(protocol["schemas"]["testimony"]["required"]))
        self.assertEqual(n.EMOJI_STATES, {state["state"] for state in protocol["emoji"]["states"]})
        self.assertEqual(n.ART_PATH, protocol["art"]["resource"]["path"])
        self.assertEqual(n.READBACK_BYTES, protocol["fileprovider"]["fixture"]["utf8"].encode())
        self.assertEqual(len(n.READBACK_BYTES), protocol["fileprovider"]["fixture"]["bytes"])
        self.assertEqual(n.digest(n.READBACK_BYTES), protocol["fileprovider"]["fixture"]["sha256"])
        self.assertIsNone(protocol["art"]["uid"])
        self.assertIsNone(protocol["emoji"]["identified_provider"])
        pending = protocol["pending_expected"]
        self.assertEqual(set(protocol["schemas"]["expected"]["required"]), set(pending))
        for key in ("run_id", "round", "qa_sha256", "runner_sha256", "root_id"):
            self.assertIsNone(pending[key])
        for key in ("name", "api", "serial"):
            self.assertIsNone(pending["avd"][key])
        for key in ("user_id", "uid", "certificate_sha256", "apk_sha256", "uid_observation_sha256"):
            self.assertIsNone(pending["target"][key])
        for key in ("certificate_sha256", "apk_sha256", "identity_observation_sha256"):
            self.assertIsNone(pending["runner"][key])
        self.assertIsNone(pending["art_resource"]["user_id"])
        self.assertIsNone(pending["art_resource"]["uid"])
        self.assertIsNone(pending["emoji"]["discovery_sha256"])
        self.assertIsNone(pending["emoji"]["provider"])
        with self.assertRaises(n.ValidationError): n.validate_expected(pending)


if __name__ == "__main__":
    unittest.main()
