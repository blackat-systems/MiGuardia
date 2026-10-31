from contextlib import closing
import json
from pathlib import Path
import sqlite3
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import stage10_capture as transport
import stage10_runtime_compare as policy
import test_stage10_capture as capture_tests
from test_upgrade_v6_v8 import UUID


def varint(value):
    data = bytearray()
    while value > 127:
        data.append((value & 127) | 128)
        value >>= 7
    data.append(value)
    return bytes(data)


def blob(number, data):
    return varint(number * 8 + 2) + varint(len(data)) + data


def text_set(values):
    return blob(6, b''.join(blob(1, value.encode()) for value in values))


def raw_preferences(dismissed, operational=False, change=None):
    values = {'enabled': b'\x08\x00', 'precise_timing': b'\x08\x00', 'persistent_while_active': b'\x08\x00',
              'privacy': blob(5, b'REDUCED'), 'attention_mode': blob(5, b'VIBRATION_ONLY'),
              'global_reminder_minutes': text_set(['60', '180']), 'dismissed_shift_ids': text_set(dismissed)}
    if operational:
        values.update(policy.OPERATIONAL)
    if change:
        change(values)
    return b''.join(blob(1, blob(1, name.encode()) + blob(2, value)) for name, value in values.items())


class Stage10RuntimeCompareTest(unittest.TestCase):
    def setUp(self):
        self.fx = capture_tests.Stage10CaptureTest()
        self.fx.setUp()
        self.addCleanup(self.fx.doCleanups)
        self.root = self.fx.root
        self.counter = 0
        for path in (self.fx.source, self.fx.target):
            with closing(sqlite3.connect(path)) as db, db:
                tables = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")]
                for table in tables:
                    columns = {r[1] for r in db.execute('PRAGMA table_info(' + policy.strict.quoted(table) + ')')}
                    if 'shiftId' in columns:
                        db.execute('UPDATE ' + policy.strict.quoted(table) + ' SET shiftId=? WHERE shiftId=?', (policy.KNOWN[6:], UUID))
                    for fk in db.execute('PRAGMA foreign_key_list(' + policy.strict.quoted(table) + ')').fetchall():
                        if fk[2] == 'shifts':
                            db.execute('UPDATE ' + policy.strict.quoted(table) + ' SET ' + policy.strict.quoted(fk[3]) + '=? WHERE ' + policy.strict.quoted(fk[3]) + '=?', (policy.KNOWN[6:], UUID))
                db.execute('UPDATE shifts SET id=?,endEpochMillis=100 WHERE id=?', (policy.KNOWN[6:], UUID))
                db.execute('UPDATE shift_actual_records SET actualEndEpochMillis=200 WHERE shiftId=?', (policy.KNOWN[6:],))
                self.assertEqual(db.execute('PRAGMA foreign_key_check').fetchall(), [])
        self.annex = self.root / 'technical'
        profile = self.annex / 'files/profileInstalled'
        profile.parent.mkdir(parents=True)
        profile.write_bytes(bytes.fromhex('0000000100050000000001a16595561d0000000000000000'))
        queue = self.annex / 'shared_prefs/notification_deferred_actions.xml'
        queue.parent.mkdir()
        queue.write_bytes(b"<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map />\n")

    def pair(self, at=200, change=None, current=None):
        self.counter += 1
        current = [] if current is None else current
        directories = []
        for version in (6, 8):
            def mutate(contents, receipt):
                ids = [policy.KNOWN] if version == 6 else current
                contents['private/' + policy.RAW] = raw_preferences(ids, version == 8)
                values = json.loads(contents['portable-preferences.json'])
                next(v for v in values if v['key'] == policy.DISMISSED)['values'] = ids
                contents['portable-preferences.json'] = json.dumps(values).encode()
                if version == 8:
                    receipt.update(operation=transport.EXPIRY_OPERATION, criterion=transport.EXPIRY_CRITERION,
                                   captured_at_epoch_millis=at, technical_files_not_portable=policy.TECHNICAL)
                    if change:
                        change(contents, receipt)
            directory = self.root / ('pair-' + str(self.counter) + '-' + str(version))
            transport.ingest(self.fx.bundle(version, mutate), directory)
            directories.append(directory)
        return directories

    def compare(self, pair):
        return policy.compare(*pair, self.annex)

    def test_classified_expiry_preserves_original_strict_rejection(self):
        result = self.compare(self.pair())
        self.assertEqual(result['verdict'], 'THIS_CASE_PRESERVED_UNDER_APPROVED_EXPIRY_CRITERION')
        self.assertEqual(result['strict_comparison']['verdict'], 'STRICT_COMPARISON_REJECTED_PRESERVED')
        self.assertEqual(result['notification_delta']['effective_end_epoch_millis'], 200)

    def test_live_key_survives_until_actual_end_not_planned_end(self):
        with self.assertRaisesRegex(ValueError, 'still-live'):
            self.compare(self.pair(at=199))
        self.assertEqual(self.compare(self.pair(at=199, current=[policy.KNOWN]))['notification_delta']['removed'], [])
        self.assertEqual(self.compare(self.pair(at=200))['notification_delta']['removed'], [policy.KNOWN])

    def test_new_id_and_changed_operational_values_fail(self):
        with self.assertRaisesRegex(ValueError, 'identity'):
            self.compare(self.pair(current=['shift:11111111-1111-4111-8111-111111111111']))
        def mutate(contents, receipt):
            contents['private/' + policy.RAW] = raw_preferences([], True, lambda values: values.update(installed_exact_mode=b'\x08\x01'))
        with self.assertRaisesRegex(ValueError, 'Operational'):
            self.compare(self.pair(change=mutate))

    def test_durable_preference_and_other_raw_losses_fail(self):
        def preference(contents, receipt):
            values = json.loads(contents['portable-preferences.json'])
            next(v for v in values if v['key'] == 'profile.display_name')['values'] = ['changed']
            contents['portable-preferences.json'] = json.dumps(values).encode()
        with self.assertRaisesRegex(ValueError, 'Durable portable'):
            self.compare(self.pair(change=preference))
        def raw(contents, receipt):
            contents['private/shared_prefs/miguardia_display_preferences.xml'] = b'changed'
        with self.assertRaisesRegex(ValueError, 'durable RAW'):
            self.compare(self.pair(change=raw))

    def test_changed_raw_boolean_cannot_hide_behind_same_gateway_default(self):
        def mutate(contents, receipt):
            contents['private/' + policy.RAW] = raw_preferences([], True, lambda values: values.update(enabled=b'\x08\x01'))
        with self.assertRaisesRegex(ValueError, 'Durable notification RAW'):
            self.compare(self.pair(change=mutate))

    def test_unknown_duplicate_malformed_or_wrong_type_protobuf_fails(self):
        before = raw_preferences([policy.KNOWN])
        normal = raw_preferences([], True)
        invalid = [normal + blob(1, blob(1, b'enabled') + blob(2, b'\x08\x00')),
                   normal + blob(1, blob(1, b'unknown') + blob(2, b'\x08\x00')),
                   normal[:-1], b'\x0d\x00\x00\x00\x00',
                   raw_preferences([], True, lambda values: values.update(dismissed_shift_ids=blob(5, b'')))]
        for value in invalid:
            with self.subTest(value=value[:20]), self.assertRaises(ValueError):
                policy.notification_delta(before, value, [policy.KNOWN], [], 200, 200)

    def test_typed_row_change_and_extra_private_file_fail(self):
        def mutate(contents, receipt):
            path = self.root / ('mutated-' + str(self.counter) + '.db')
            path.write_bytes(contents['database.db'])
            with closing(sqlite3.connect(path)) as db, db:
                db.execute("UPDATE objectives SET fullName='changed'")
            contents['database.db'] = path.read_bytes()
        with self.assertRaisesRegex(ValueError, 'typed rows'):
            self.compare(self.pair(change=mutate))
        pair = self.pair()
        (pair[1] / 'private/no_backup/unknown').write_bytes(b'unknown')
        with self.assertRaisesRegex(ValueError, 'Unexpected/missing'):
            self.compare(pair)

    def test_platform_counter_requires_exact_raw_annex_and_no_unknown_name(self):
        annex = self.root / 'platform'
        path = annex / policy.PLATFORM_PATH
        path.parent.mkdir(parents=True)
        path.write_bytes(b'<?xml version=\'1.0\' encoding=\'utf-8\' standalone=\'yes\' ?>\n<map>\n    <int name="IDSCount" value="1" />\n</map>\n')
        def mutate(contents, receipt):
            receipt['platform_runtime_files'] = {policy.PLATFORM_PATH: policy.PLATFORM_SHA256}
        pair = self.pair(change=mutate)
        with self.assertRaisesRegex(ValueError, 'platform annex'):
            self.compare(pair)
        result = policy.compare(*pair, self.annex, annex)
        self.assertEqual(result['platform_runtime_files'], {policy.PLATFORM_PATH: policy.PLATFORM_SHA256})
        path.write_bytes(path.read_bytes().replace(b'value="1"', b'value="2"'))
        with self.assertRaisesRegex(ValueError, 'Platform RAW'):
            policy.compare(*pair, self.annex, annex)


if __name__ == '__main__':
    unittest.main()
