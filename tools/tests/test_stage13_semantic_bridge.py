"""Portable regression cases for the stalled Stage 12.1 conservation bridge."""

import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import types
import unittest


MODULE = Path(__file__).resolve().parents[1] / "stage13_semantic_bridge.py"
SPEC = importlib.util.spec_from_file_location("stage13_semantic_bridge", MODULE)
bridge = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bridge)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def fixture():
    return {
        "databases": {"synthetic.db": {
            "logical": {"user_version": 8, "schema_objects": [["table", "shift", "shift", "CREATE TABLE shift (id INTEGER)"]],
                        "tables": {"shift": {"strict": False, "columns": [[0, "id", "INTEGER", 1]],
                                             "rows": [[["INTEGER", 1]]]}}},
            "physical": {"": {"bytes": 100, "sha256": "1" * 64}},
            "wal_validation": {"valid": True}, "counts": {"shift": 1},
            "derived_copy": "C:/synthetic/old/copy.db", "comparison_scope": "COMPLETE",
        }},
        "preferences": {"synthetic.xml": {"enabled": True, "count": 1, "ratio": 1.0}},
        "files": {"files/synthetic.png": {"type": "FILE", "bytes": 10, "sha256": "2" * 64}},
        "roles": {"databases": "PRESENT", "shared_prefs": "PRESENT", "files": "PRESENT"},
        "projection_scope": "COMPLETE_CAPTURED_T1_PRODUCT_ROOTS",
        "coverage_status": "CAPTURE_PROFILE_COMPLETE_PENDING_FINAL_EFFECT_MAP", "ROOM_complete": False,
    }


class SemanticBridgeTests(unittest.TestCase):
    def setUp(self):
        self.recovered = fixture()
        self.current = copy.deepcopy(self.recovered)
        self.current["databases"]["synthetic.db"]["derived_copy"] = "C:/synthetic/new/copy.db"
        self.calls = []

        def capture(before, after):
            self.calls.append("capture")
            return {"captured_surface_equal": True}

        def sqlite(before, after):
            self.calls.append("sqlite")
            before_hash = hashlib.sha256(canonical(before["logical"])).hexdigest()
            after_hash = hashlib.sha256(canonical(after["logical"])).hexdigest()
            return {"logical_equal": before["logical"] == after["logical"],
                    "physical_equal": before["physical"] == after["physical"],
                    "before_logical_sha256": before_hash, "after_logical_sha256": after_hash}

        self.custody = types.SimpleNamespace(compare_captures=capture, compare_sqlite=sqlite)

    def compare(self):
        return bridge.compare_recovered_projection(self.custody, self.recovered, self.current)

    def test_copy_path_is_auxiliary_and_objects_and_hashes_are_preserved(self):
        before, after = canonical(self.recovered), canonical(self.current)
        self.assertNotEqual(hashlib.sha256(before).digest(), hashlib.sha256(after).digest())
        result = self.compare()
        self.assertFalse(result["hashes_normalized"])
        self.assertTrue(result["full_source_objects_preserved"])
        self.assertEqual(before, canonical(self.recovered))
        self.assertEqual(after, canonical(self.current))
        self.assertEqual(["capture", "sqlite"], self.calls)

    def test_business_and_type_mutations_are_rejected(self):
        mutations = {
            "row": lambda d: d["logical"]["tables"]["shift"]["rows"].clear(),
            "schema": lambda d: d["logical"]["schema_objects"][0].__setitem__(3, "CREATE TABLE different (id TEXT)"),
            "version": lambda d: d["logical"].__setitem__("user_version", 9),
            "version_float": lambda d: d["logical"].__setitem__("user_version", 8.0),
            "column_bool": lambda d: d["logical"]["tables"]["shift"]["columns"][0].__setitem__(0, False),
            "strict_int": lambda d: d["logical"]["tables"]["shift"].__setitem__("strict", 0),
            "cell_storage": lambda d: d["logical"]["tables"]["shift"]["rows"][0][0].__setitem__(0, "TEXT"),
            "physical_sha": lambda d: d["physical"][""].__setitem__("sha256", "0" * 64),
            "physical_bytes_float": lambda d: d["physical"][""].__setitem__("bytes", 100.0),
            "wal": lambda d: d["wal_validation"].__setitem__("valid", False),
            "count": lambda d: d["counts"].__setitem__("shift", 2),
            "scope": lambda d: d.__setitem__("comparison_scope", "PARTIAL"),
            "path_type": lambda d: d.__setitem__("derived_copy", None),
            "extra_field": lambda d: d.__setitem__("new_field", "changed"),
        }
        for name, mutate in mutations.items():
            with self.subTest(name=name):
                self.current = copy.deepcopy(self.recovered)
                mutate(self.current["databases"]["synthetic.db"])
                with self.assertRaisesRegex(bridge.ProjectionMismatch, bridge.ERROR_CODE):
                    self.compare()

    def test_nondatabase_mutations_are_rejected(self):
        mutations = {
            "preference_value": lambda p: p["preferences"]["synthetic.xml"].__setitem__("count", 2),
            "preference_bool_int": lambda p: p["preferences"]["synthetic.xml"].__setitem__("enabled", 1),
            "preference_int_float": lambda p: p["preferences"]["synthetic.xml"].__setitem__("count", 1.0),
            "preference_float_int": lambda p: p["preferences"]["synthetic.xml"].__setitem__("ratio", 1),
            "file_sha": lambda p: p["files"]["files/synthetic.png"].__setitem__("sha256", "0" * 64),
            "file_bytes_float": lambda p: p["files"]["files/synthetic.png"].__setitem__("bytes", 10.0),
            "role": lambda p: p["roles"].__setitem__("files", "ABSENT"),
            "room_bool_int": lambda p: p.__setitem__("ROOM_complete", 0),
            "scope": lambda p: p.__setitem__("projection_scope", "PARTIAL"),
            "coverage": lambda p: p.__setitem__("coverage_status", "DIFFERENT"),
            "missing_db": lambda p: p["databases"].clear(),
            "extra_root_field": lambda p: p.__setitem__("new_field", True),
        }
        for name, mutate in mutations.items():
            with self.subTest(name=name):
                self.current = copy.deepcopy(self.recovered)
                mutate(self.current)
                with self.assertRaises(bridge.ProjectionMismatch):
                    self.compare()

    def test_path_exclusion_is_only_at_database_entry(self):
        self.recovered["databases"]["synthetic.db"]["logical"]["derived_copy"] = "business value"
        self.current = copy.deepcopy(self.recovered)
        self.current["databases"]["synthetic.db"]["logical"]["derived_copy"] = "different business value"
        with self.assertRaises(bridge.ProjectionMismatch):
            self.compare()

    def test_existing_capture_comparator_remains_required(self):
        self.custody.compare_captures = lambda *_: {"captured_surface_equal": False}
        with self.assertRaises(bridge.ProjectionMismatch):
            self.compare()

    def test_existing_sqlite_comparator_remains_required(self):
        self.custody.compare_sqlite = lambda *_: {"logical_equal": True, "physical_equal": False,
                                                "before_logical_sha256": "a", "after_logical_sha256": "a"}
        with self.assertRaises(bridge.ProjectionMismatch):
            self.compare()

    def test_non_json_and_nonfinite_values_are_rejected(self):
        for invalid in (float("nan"), float("inf"), (1, 2), {1: "value"}):
            with self.subTest(kind=type(invalid).__name__):
                self.recovered["extension"] = invalid
                self.current["extension"] = invalid
                with self.assertRaises(bridge.ProjectionMismatch):
                    self.compare()


if __name__ == "__main__":
    unittest.main()
