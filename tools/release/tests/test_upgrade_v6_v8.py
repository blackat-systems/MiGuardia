"""Synthetic host evidence only; no Android, business fixture or image decoder claim."""
from contextlib import closing, contextmanager
import copy
import json
import pathlib
import re
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).parents[1]))
import upgrade_v6_v8 as u

UUID = '30000000-0000-4000-8000-000000000001'
OTHER_UUID = '30000000-0000-4000-8000-000000000002'
PHOTO = b'synthetic host photo bytes; decoder usability is not asserted'


def preference_fixture():
    values = {'TEXT': ['synthetic'], 'LONG': ['150'], 'BOOLEAN': ['false'], 'TEXT_LIST': []}
    return [{'key': key, 'type': kind, 'values': values[kind].copy()}
            for key, kind in sorted(u.PREFERENCE_TYPES.items())]


def create_source(database, private, preferences):
    """Exact schema with relational rows, not valid labor/business scenario proof."""
    for name in ('files', 'shared_prefs', 'no_backup'):
        (private / name).mkdir(parents=True)
    (private / 'files/schedule_photos').mkdir()
    (private / 'files/schedule_photos' / (UUID + '.jpg')).write_bytes(PHOTO)
    (private / 'files/profile.preferences_pb').write_bytes(b'synthetic protobuf selection')
    (private / 'shared_prefs/display.xml').write_bytes(b'<map>synthetic selection</map>')
    preferences.write_text(json.dumps(preference_fixture()), encoding='utf-8')
    with closing(sqlite3.connect(database)) as db, db:
        contract = u.schema(6)
        for entity in contract['entities']:
            name = entity['tableName']
            db.execute(entity['createSql'].replace('${TABLE_NAME}', name))
            for index in entity.get('indices', []):
                db.execute(index['createSql'].replace('${TABLE_NAME}', name))
            values = []
            for field in entity['fields']:
                column = field['columnName']
                if field['affinity'] == 'TEXT' and (column == 'id' or column.endswith('Id')) and column != 'zoneId':
                    value = UUID
                elif column == 'storageKey':
                    value = UUID + '.jpg'
                elif column == 'byteSize':
                    value = len(PHOTO)
                elif field['affinity'] == 'REAL':
                    value = -34.5 if column == 'weatherLatitude' else None
                elif not field.get('notNull', False):
                    value = None
                elif field['affinity'] == 'INTEGER':
                    value = 1
                else:
                    value = 'synthetic'
                values.append(value)
            db.execute('INSERT INTO ' + u.quoted(name) + ' VALUES (' + ','.join('?' for _ in values) + ')', values)
        for query in contract['setupQueries']:
            db.execute(query)
        db.execute('PRAGMA user_version=6')
        # Large INTEGER, Unicode, nullable text and REAL exercise typed hashes.
        db.execute('UPDATE shifts SET startEpochMillis=?', (2**53 + 1,))
        db.execute('UPDATE shift_notes SET body=?', ('Nota sintética á ñ \U0001f319',))


def materialize_target(source, target):
    """Apply additive exported SQL on host. This explicitly does NOT run Room."""
    shutil.copyfile(source, target)
    with closing(sqlite3.connect(target)) as db, db:
        for entity in u.schema(8)['entities']:
            if entity['tableName'] in u.NEW_TABLES:
                db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
                for index in entity.get('indices', []):
                    db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        db.execute('UPDATE room_master_table SET identity_hash=? WHERE id=42', (u.schema(8)['identityHash'],))
        db.execute('PRAGMA user_version=8')


class UpgradeV6V8Test(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
        self.source = self.root / 'v6.db'
        self.target = self.root / 'v8.db'
        self.private = self.root / 'private'
        self.preferences = self.root / 'portable.json'
        create_source(self.source, self.private, self.preferences)
        materialize_target(self.source, self.target)

    def tearDown(self):
        self.temp.cleanup()

    def capture(self, version=6):
        return u.capture(self.source if version == 6 else self.target, self.private, self.preferences, version)

    def mutate(self, sql, parameters=(), target=True):
        with closing(sqlite3.connect(self.target if target else self.source)) as db, db:
            db.execute(sql, parameters)

    def assert_current_contents_preserved(self):
        before = self.capture()
        self.assertEqual('V6_TO_V8_LOGICAL_CONTENT_PRESERVED', u.compare(before, self.capture(8))['verdict'])
        return before

    @contextmanager
    def independent_target_adverse(self):
        """Each database adverse starts green and restores the exact temporary DB."""
        before = self.assert_current_contents_preserved()
        original = self.target.read_bytes()
        try:
            yield before
        finally:
            self.target.write_bytes(original)
            self.assertEqual(before, self.assert_current_contents_preserved())

    @contextmanager
    def independent_file_adverse(self, path):
        """Isolate a selected synthetic file or preference document, including absence."""
        self.assertTrue(path.resolve().is_relative_to(self.root.resolve()))
        before = self.assert_current_contents_preserved()
        original = path.read_bytes() if path.exists() else None
        try:
            yield before
        finally:
            if original is None:
                path.unlink(missing_ok=True)
            else:
                path.write_bytes(original)
            self.assertEqual(before, self.assert_current_contents_preserved())

    def rebuild_table(self, name, transform, target=True):
        """Rebuild only a temporary synthetic table, preserving rows and indices."""
        with closing(sqlite3.connect(self.target if target else self.source)) as db, db:
            sql = db.execute("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", (name,)).fetchone()[0]
            indices = [r[0] for r in db.execute("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name=? AND sql IS NOT NULL", (name,))]
            rows = db.execute('SELECT * FROM ' + u.quoted(name)).fetchall()
            db.execute('DROP TABLE ' + u.quoted(name))
            db.execute(transform(sql))
            if rows:
                db.executemany('INSERT INTO ' + u.quoted(name) + ' VALUES (' + ','.join('?' for _ in rows[0]) + ')', rows)
            for index in indices:
                db.execute(index)

    def test_explicit_round_trip_proves_only_logical_contents(self):
        before, after = self.capture(), self.capture(8)
        result = u.compare(before, after)
        self.assertEqual('V6_TO_V8_LOGICAL_CONTENT_PRESERVED', result['verdict'])
        self.assertEqual((27, 29, 17, 1), tuple(result[k] for k in ('original_tables', 'target_tables', 'portable_preferences', 'photos')))
        self.assertEqual({'shift_note_attachments', 'shift_situations'}, {x['table'] for x in result['allowed_differences']})
        self.assertIn('HOST_LOGICAL_ONLY', result['limit'])
        self.assertNotIn('identity', before)  # No invented installed app/signature/versionCode.

    def test_all_original_entity_contracts_are_unchanged(self):
        old = {e['tableName']: e for e in u.schema(6)['entities']}
        for entity in u.schema(8)['entities']:
            if entity['tableName'] in old:
                self.assertEqual(old[entity['tableName']], entity)
        self.assertEqual([], u.source_contract()['original_columns_changed'])

    def test_extra_column_collation_is_rejected_even_when_pragmas_match(self):
        with closing(sqlite3.connect(self.target)) as db:
            original = u.signatures(db, ['shift_notes'])['shift_notes']
        self.rebuild_table('shift_notes', lambda sql: sql.replace('`body` TEXT NOT NULL', '`body` TEXT COLLATE NOCASE NOT NULL'))
        with closing(sqlite3.connect(self.target)) as db:
            changed = u.signatures(db, ['shift_notes'])['shift_notes']
        self.assertEqual({k: v for k, v in original.items() if k != 'ddl'},
                         {k: v for k, v in changed.items() if k != 'ddl'})
        self.assertNotEqual(original['ddl'], changed['ddl'])
        with self.assertRaisesRegex(ValueError, 'Exact Room'):
            self.capture(8)

    def test_extra_check_is_rejected_even_when_pragmas_match(self):
        with closing(sqlite3.connect(self.target)) as db:
            original = u.signatures(db, ['shift_notes'])['shift_notes']
        self.rebuild_table('shift_notes', lambda sql: sql[:-1] + ', CHECK(length(`body`)<5000))')
        with closing(sqlite3.connect(self.target)) as db:
            changed = u.signatures(db, ['shift_notes'])['shift_notes']
        self.assertEqual({k: v for k, v in original.items() if k != 'ddl'},
                         {k: v for k, v in changed.items() if k != 'ddl'})
        self.assertNotEqual(original['ddl'], changed['ddl'])
        with self.assertRaisesRegex(ValueError, 'Exact Room'):
            self.capture(8)

    def test_changed_foreign_key_deferrability_is_rejected(self):
        with closing(sqlite3.connect(self.target)) as db:
            original = u.signatures(db, ['shift_notes'])['shift_notes']
        self.rebuild_table('shift_notes', lambda sql: sql.replace('ON DELETE CASCADE', 'ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED'))
        with closing(sqlite3.connect(self.target)) as db:
            changed = u.signatures(db, ['shift_notes'])['shift_notes']
        self.assertEqual({k: v for k, v in original.items() if k != 'ddl'},
                         {k: v for k, v in changed.items() if k != 'ddl'})
        self.assertNotEqual(original['ddl'], changed['ddl'])
        with self.assertRaisesRegex(ValueError, 'Exact Room'):
            self.capture(8)

    def test_table_formatting_comments_and_identifier_quoting_are_tolerated(self):
        before = self.capture()
        def formatted(sql):
            sql = re.sub(r'`([A-Za-z_][A-Za-z_0-9]*)`', r'[\1]', sql)
            return re.sub(r'^CREATE TABLE', 'cReAtE /*synthetic formatting*/ TABLE IF NOT EXISTS', sql).replace(', ', ',\n  ')
        self.rebuild_table('shift_notes', formatted)
        self.assertEqual('V6_TO_V8_LOGICAL_CONTENT_PRESERVED', u.compare(before, self.capture(8))['verdict'])

    def test_real_add_column_from_v5_has_equivalent_table_ddl(self):
        before = self.capture()
        entity = next(e for e in json.loads((u.SCHEMAS / '5.json').read_text())['database']['entities'] if e['tableName'] == 'objectives')
        with closing(sqlite3.connect(self.target)) as db, db:
            row = db.execute('SELECT * FROM objectives').fetchone()
            db.execute('DROP TABLE objectives')
            db.execute(entity['createSql'].replace('${TABLE_NAME}', 'objectives'))
            for index in entity.get('indices', []):
                db.execute(index['createSql'].replace('${TABLE_NAME}', 'objectives'))
            db.execute('INSERT INTO objectives VALUES (' + ','.join('?' for _ in row[:-2]) + ')', row[:-2])
            # Same native SQL as MIGRATION_5_6. This exercises only host SQLite.
            db.execute('ALTER TABLE `objectives` ADD COLUMN `weatherLatitude` REAL')
            db.execute('ALTER TABLE `objectives` ADD COLUMN `weatherLongitude` REAL')
            db.execute('UPDATE objectives SET weatherLatitude=?, weatherLongitude=?', row[-2:])
            columns = [stored[1] for stored in db.execute('PRAGMA table_xinfo(objectives)')]
            coordinates = db.execute('SELECT weatherLatitude, weatherLongitude FROM objectives').fetchone()
        expected = next(e for e in u.schema(6)['entities'] if e['tableName'] == 'objectives')
        self.assertEqual([field['columnName'] for field in expected['fields']], columns)
        self.assertEqual(row[-2:], coordinates)
        self.assertEqual('V6_TO_V8_LOGICAL_CONTENT_PRESERVED', u.compare(before, self.capture(8))['verdict'])

    def test_ddl_literals_and_constraint_terms_are_not_normalized_away(self):
        baseline = "CREATE TABLE t(id TEXT DEFAULT 'a  b,--/*''quoted', CHECK(length(id)<5000))"
        self.assertEqual(u.canonical_ddl(baseline, 'table'),
                         u.canonical_ddl("CREATE /*comment*/ TABLE IF NOT EXISTS [t] (CHECK(length([id])<5000), [id] TEXT DEFAULT 'a  b,--/*''quoted');", 'table'))
        for changed in (baseline.replace('a  b', 'a b'), baseline.replace('5000', '5001'),
                        baseline.replace('TEXT DEFAULT', 'TEXT COLLATE NOCASE DEFAULT'),
                        baseline + ' WITHOUT ROWID', baseline + ' STRICT',
                        baseline[:-1] + ', CHECK(length(id)<5000))'):
            with self.subTest(sql=changed):
                self.assertNotEqual(u.canonical_ddl(baseline, 'table'), u.canonical_ddl(changed, 'table'))
        index = "CREATE INDEX idx ON t(id) WHERE id='A  B'"
        self.assertEqual(u.canonical_ddl(index, 'index'), u.canonical_ddl('create index if not exists "idx" ON [t] (`id`) WHERE [id]=\'A  B\';', 'index'))
        for changed in (index.replace('A  B', 'A B'), index.replace("='A  B'", "!='A  B'"), index.replace('t(id)', 't(id DESC)')):
            with self.subTest(index=changed):
                self.assertNotEqual(u.canonical_ddl(index, 'index'), u.canonical_ddl(changed, 'index'))
        self.assertNotEqual(u.canonical_ddl("CREATE TABLE t(id TEXT DEFAULT 'UPPER')", 'table'),
                            u.canonical_ddl("CREATE TABLE t(id TEXT DEFAULT 'upper')", 'table'))

    def test_ddl_unknown_and_unterminated_tokens_fail_closed(self):
        for sql in ("CREATE TABLE t(id TEXT DEFAULT 'unfinished)", 'CREATE TABLE t(id TEXT /*unfinished)',
                    'CREATE TABLE t(id TEXT @unknown)', 'CREATE TABLE t AS SELECT 1'):
            with self.subTest(sql=sql), self.assertRaises(ValueError):
                u.canonical_ddl(sql, 'table')

    def test_index_ddl_formatting_is_tolerated_but_changed_expression_is_rejected(self):
        before = self.capture()
        with closing(sqlite3.connect(self.target)) as db, db:
            db.execute('DROP INDEX index_shift_situations_status')
            db.execute('create index if not exists [index_shift_situations_status] ON [shift_situations] ( [status] )')
        self.assertEqual('V6_TO_V8_LOGICAL_CONTENT_PRESERVED', u.compare(before, self.capture(8))['verdict'])
        with closing(sqlite3.connect(self.target)) as db, db:
            db.execute('DROP INDEX index_shift_situations_status')
            db.execute('CREATE INDEX index_shift_situations_status ON shift_situations(lower(status))')
        with self.assertRaisesRegex(ValueError, 'Exact Room'):
            self.capture(8)

    def test_row_order_does_not_change_hash(self):
        self.mutate('INSERT INTO shift_notes SELECT ?,shiftId,body,createdAtEpochMillis,updatedAtEpochMillis FROM shift_notes', (OTHER_UUID,), target=False)
        self.mutate('INSERT INTO shift_notes SELECT ?,shiftId,body,createdAtEpochMillis,updatedAtEpochMillis FROM shift_notes', (OTHER_UUID,))
        before = self.capture()
        self.mutate('DELETE FROM shift_notes WHERE id=?', (UUID,))
        self.mutate('INSERT INTO shift_notes SELECT ?,shiftId,body,createdAtEpochMillis,updatedAtEpochMillis FROM shift_notes', (UUID,))
        self.assertEqual('V6_TO_V8_LOGICAL_CONTENT_PRESERVED', u.compare(before, self.capture(8))['verdict'])

    def test_lost_row_is_rejected(self):
        self.mutate('DELETE FROM holidays')
        with self.assertRaisesRegex(ValueError, 'Missing populated'):
            u.compare(self.capture(), self.capture(8))

    def test_all_27_tables_require_populated_coverage(self):
        before = self.capture()
        self.assertEqual(27, len(before['tables']))
        for name in before['tables']:
            changed = copy.deepcopy(before)
            changed['tables'][name]['count'] = 0
            with self.subTest(table=name), self.assertRaisesRegex(ValueError, 'Missing populated'):
                u.validate_snapshot(changed)

    def test_historical_references_without_declared_foreign_keys_are_preserved(self):
        before = self.capture()
        self.assertIn('sourceObjectiveId', before['historical_references']['shifts'])
        self.assertIn('objectiveId', before['historical_references']['schedule_photos'])
        self.assertIn('workPlaceId', before['historical_references']['recurring_plan_revisions'])
        self.mutate('UPDATE shifts SET sourceObjectiveId=?', (OTHER_UUID,))
        after = self.capture(8)  # Historical UUID need not have a live objective row.
        self.assertNotEqual(before['historical_references']['shifts'], after['historical_references']['shifts'])
        with self.assertRaises(ValueError):
            u.compare(before, after)

    def test_photo_key_formats_accepted_by_production_safe_path(self):
        original = self.private / 'files/schedule_photos' / (UUID + '.jpg')
        for name in (UUID + '.jpeg', UUID + '_1234abcd.webp'):
            candidate = original.with_name(name)
            original.rename(candidate)
            self.mutate('UPDATE schedule_photos SET storageKey=?', (name,), target=False)
            self.assertEqual(1, len(self.capture()['photo_associations']))
            candidate.rename(original)

    def test_added_row_is_rejected(self):
        self.mutate('INSERT INTO shift_notes SELECT ?,shiftId,body,createdAtEpochMillis,updatedAtEpochMillis FROM shift_notes', (OTHER_UUID,))
        with self.assertRaisesRegex(ValueError, 'original V6 row'):
            u.compare(self.capture(), self.capture(8))

    def test_lost_row_while_table_stays_populated_is_rejected(self):
        for target in (False, True):
            self.mutate('INSERT INTO holidays SELECT ?,?,name,createdAtEpochMillis,updatedAtEpochMillis FROM holidays', (OTHER_UUID, '2026-06-02'), target=target)
        before = self.capture()
        self.mutate('DELETE FROM holidays WHERE id=?', (UUID,))
        with self.assertRaisesRegex(ValueError, 'original V6 row'):
            u.compare(before, self.capture(8))

    def test_each_original_table_mutation_is_rejected(self):
        before = self.capture()
        after = self.capture(8)
        for name in before['tables']:
            changed = copy.deepcopy(after)
            changed['tables'][name]['sha256'] = 'f' * 64
            with self.subTest(table=name), self.assertRaises(ValueError):
                u.compare(before, changed)

    def test_null_and_text_mutations_are_rejected(self):
        cases = (('shift_notes', 'body', 'mutada'), ('shift_notes', 'body', 'synthetic'),
                 ('shift_notes', 'createdAtEpochMillis', -1), ('objectives', 'note', 'was NULL'))
        for table, column, value in cases:
            with self.subTest(table=table, column=column, value=value), self.independent_target_adverse() as before:
                self.mutate('UPDATE ' + u.quoted(table) + ' SET ' + u.quoted(column) + '=?', (value,))
                with self.assertRaises(ValueError):
                    u.compare(before, self.capture(8))

    def test_integer_precision_and_real_changes_are_rejected(self):
        for sql, value in (('UPDATE shifts SET startEpochMillis=?', 2**53),
                           ('UPDATE objectives SET weatherLatitude=?', -34.50001)):
            with self.subTest(sql=sql), self.independent_target_adverse() as before:
                self.mutate(sql, (value,))
                with self.assertRaises(ValueError):
                    u.compare(before, self.capture(8))

    def test_blob_or_text_in_wrong_affinity_is_rejected(self):
        for sql, value in (('UPDATE shift_notes SET body=?', b'blob'), ('UPDATE objectives SET weatherLatitude=?', 'text'),
                           ('UPDATE shifts SET startEpochMillis=?', 'not an integer')):
            with self.subTest(sql=sql), self.independent_target_adverse():
                self.mutate(sql, (value,))
                with self.assertRaisesRegex(ValueError, 'storage type'):
                    self.capture(8)

    def test_required_null_and_nonfinite_rejected_by_actual_cell_path(self):
        field = {'columnName': 'body', 'affinity': 'TEXT', 'notNull': True}
        with self.assertRaisesRegex(ValueError, 'NULL'):
            u.cell(None, field)
        with self.assertRaisesRegex(ValueError, 'Non-finite'):
            u.cell(float('inf'), {'columnName': 'weatherLatitude', 'affinity': 'REAL'})
        self.assertEqual(['null', None], u.cell(None, {'columnName': 'note', 'affinity': 'TEXT'}))

    def test_noncanonical_uuid_is_rejected(self):
        self.mutate('UPDATE holidays SET id=?', ('not-a-uuid',))
        with self.assertRaisesRegex(ValueError, 'UUID'):
            self.capture(8)

    def test_orphan_and_composite_association_are_rejected(self):
        for sql, value in (('UPDATE shift_notes SET shiftId=?', OTHER_UUID),
                           ('UPDATE shift_work_snapshots SET sector=?', 'OTHER')):
            with self.subTest(sql=sql), self.independent_target_adverse():
                self.mutate(sql, (value,))
                with self.assertRaisesRegex(ValueError, 'foreign key'):
                    self.capture(8)

    def test_valid_but_changed_association_is_rejected(self):
        self.mutate('INSERT INTO shifts SELECT ?,startEpochMillis,endEpochMillis,zoneId,localStartDate,objectiveNameSnapshot,objectiveAbbreviationSnapshot,objectiveAddressSnapshot,startTimeSnapshot,endTimeSnapshot,colorArgbSnapshot,position,status,sourceObjectiveId,createdAtEpochMillis,updatedAtEpochMillis FROM shifts', (OTHER_UUID,), target=False)
        self.target.unlink()
        materialize_target(self.source, self.target)
        before = self.capture()
        self.mutate('UPDATE shift_notes SET shiftId=?', (OTHER_UUID,))
        with self.assertRaisesRegex(ValueError, 'original V6 row'):
            u.compare(before, self.capture(8))

    def test_added_column_is_rejected(self):
        self.mutate('ALTER TABLE shifts ADD COLUMN alien TEXT')
        with self.assertRaisesRegex(ValueError, 'Exact Room'):
            self.capture(8)

    def test_missing_or_added_table_is_rejected(self):
        for sql in ('CREATE TABLE alien (id TEXT)', 'DROP TABLE shift_situations'):
            with self.subTest(sql=sql), self.independent_target_adverse():
                self.mutate(sql)
                with self.assertRaises(ValueError):
                    self.capture(8)

    def test_missing_and_extra_indices_are_rejected(self):
        for sql in ('DROP INDEX index_shift_situations_status', 'CREATE INDEX alien_index ON shifts(status)'):
            with self.subTest(sql=sql), self.independent_target_adverse():
                self.mutate(sql)
                with self.assertRaisesRegex(ValueError, 'Exact Room'):
                    self.capture(8)

    def test_unique_index_changed_to_nonunique_is_rejected(self):
        self.mutate('DROP INDEX index_shift_note_attachments_storageKey')
        self.mutate('CREATE INDEX index_shift_note_attachments_storageKey ON shift_note_attachments(storageKey)')
        with self.assertRaisesRegex(ValueError, 'Exact Room'):
            self.capture(8)

    def test_changed_primary_key_and_foreign_key_contracts_rejected(self):
        for fragment in ('PRIMARY KEY(`shiftId`)', 'FOREIGN KEY(`shiftId`) REFERENCES `shifts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE'):
            with self.subTest(fragment=fragment), self.independent_target_adverse():
                sql = next(e['createSql'] for e in u.schema(6)['entities'] if e['tableName'] == 'shift_notification_configs')
                sql = sql.replace('${TABLE_NAME}', 'shift_notification_configs')
                sql = sql.replace(', ' + fragment, '')
                with closing(sqlite3.connect(self.target)) as db, db:
                    db.execute('DROP TABLE shift_notification_configs')
                    db.execute(sql)
                    db.execute('INSERT INTO shift_notification_configs VALUES (?)', (UUID,))
                with self.assertRaises(ValueError):
                    self.capture(8)

    def test_unexpected_trigger_or_view_rejected(self):
        for sql in ('CREATE VIEW alien AS SELECT * FROM shifts',
                    'CREATE TRIGGER alien AFTER INSERT ON shifts BEGIN SELECT 1; END'):
            with self.subTest(sql=sql), self.independent_target_adverse():
                self.mutate(sql)
                with self.assertRaisesRegex(ValueError, 'view or trigger'):
                    self.capture(8)

    def test_version_number_without_identity_is_rejected(self):
        for sql, values, reason in (
            ('UPDATE room_master_table SET identity_hash=?', (u.schema(6)['identityHash'],), 'Room identity'),
            ('DROP TABLE room_master_table', (), 'Missing Room identity'),
        ):
            with self.subTest(sql=sql), self.independent_target_adverse():
                self.mutate(sql, values)
                with self.assertRaisesRegex(ValueError, reason):
                    self.capture(8)

    def test_wrong_explicit_version_and_unsupported_versions_rejected(self):
        with self.assertRaisesRegex(ValueError, 'user_version'):
            u.capture(self.source, self.private, self.preferences, 8)
        for version in (True, 5, 7, 9):
            with self.subTest(version=version), self.assertRaises(ValueError):
                u.capture(self.source, self.private, self.preferences, version)

    def test_reverse_or_same_version_comparison_rejected(self):
        before, after = self.capture(), self.capture(8)
        for a, b in ((after, before), (before, before), (after, after)):
            with self.assertRaises(ValueError):
                u.compare(a, b)

    def test_new_tables_must_be_empty(self):
        cases = (
            ('INSERT INTO shift_note_attachments VALUES (?,?,?,?,?,?,?,?,?,?)',
             (UUID, UUID, 'note_' + UUID + '.jpg', 'image/jpeg', 1, 1, 1, 0, 1, 1)),
            ('INSERT INTO shift_situations(shiftId,status,createdAtEpochMillis,updatedAtEpochMillis) VALUES (?,?,?,?)',
             (UUID, 'ABSENT', 1, 1)),
        )
        for sql, values in cases:
            with self.subTest(sql=sql), self.independent_target_adverse():
                self.mutate(sql, values)
                with self.assertRaisesRegex(ValueError, 'new tables empty'):
                    self.capture(8)

    def test_missing_photo_and_truncation_are_rejected(self):
        path = self.private / 'files/schedule_photos' / (UUID + '.jpg')
        for contents, reason in ((None, 'Photo file absent'), (PHOTO[:-1], 'truncated')):
            with self.subTest(reason=reason), self.independent_file_adverse(path):
                if contents is None:
                    path.unlink()
                else:
                    path.write_bytes(contents)
                with self.assertRaisesRegex(ValueError, reason):
                    self.capture()

    def test_changed_photo_bytes_and_unrelated_selected_files_rejected(self):
        path = self.private / 'files/schedule_photos' / (UUID + '.jpg')
        with self.independent_file_adverse(path) as before:
            path.write_bytes(b'x' * len(PHOTO))
            with self.assertRaisesRegex(ValueError, 'selected file'):
                u.compare(before, self.capture(8))
        residual = self.private / 'no_backup/residual'
        with self.independent_file_adverse(residual) as before:
            residual.write_bytes(b'synthetic residual')
            with self.assertRaises(ValueError):
                u.compare(before, self.capture(8))

    def test_traversal_photo_reference_rejected(self):
        self.mutate('UPDATE schedule_photos SET storageKey=?', ('../escape.jpg',))
        with self.assertRaisesRegex(ValueError, 'photo association'):
            self.capture(8)

    def test_missing_selected_root_rejected(self):
        (self.private / 'no_backup').rmdir()
        with self.assertRaisesRegex(ValueError, 'three selected'):
            self.capture()

    def test_no_private_paths_or_values_are_exposed(self):
        result = json.dumps(self.capture(), ensure_ascii=False)
        for forbidden in (str(self.root), UUID, 'Nota sintética', 'profile.preferences_pb', 'synthetic protobuf'):
            self.assertNotIn(forbidden, result)

    def test_all_17_preference_values_are_preserved_including_legacy_zoom(self):
        before = self.capture()
        original = preference_fixture()
        for index, item in enumerate(original):
            changed = copy.deepcopy(original)
            changed[index]['values'] = {'BOOLEAN': ['true'], 'LONG': ['100'], 'TEXT': ['changed'], 'TEXT_LIST': ['changed']}[item['type']]
            with self.subTest(preference=item['key']), self.independent_file_adverse(self.preferences) as baseline:
                self.preferences.write_text(json.dumps(changed), encoding='utf-8')
                with self.assertRaises(ValueError):
                    u.compare(baseline, self.capture(8))
        self.assertEqual('LONG', before['portable_preferences']['display.zoom_percent']['type'])

    def test_missing_duplicate_unknown_and_wrong_type_preferences_rejected(self):
        original = preference_fixture()
        cases = [original[:-1], original[:-1] + [original[0]], original + [{'key': 'beta.token', 'type': 'TEXT', 'values': ['synthetic']}]]
        changed = copy.deepcopy(original)
        changed[0]['type'] = 'TEXT_LIST'
        cases.append(changed)
        for changed in cases:
            with self.subTest(preferences=len(changed)), self.independent_file_adverse(self.preferences):
                self.preferences.write_text(json.dumps(changed), encoding='utf-8')
                with self.assertRaises(ValueError):
                    self.capture()

    def test_malformed_preference_values_rejected(self):
        for kind, values in (('BOOLEAN', ['yes']), ('BOOLEAN', []), ('LONG', ['1.5']), ('LONG', [str(2**63)]),
                             ('TEXT', []), ('TEXT_LIST', [True]), ('TEXT_LIST', ['x'] * 65)):
            changed = preference_fixture()
            item = next(x for x in changed if x['type'] == kind and x['key'] != 'profile.display_name' and x['key'] != 'notifications.dismissed_event_keys')
            item['values'] = values
            with self.subTest(kind=kind, values=values), self.independent_file_adverse(self.preferences):
                self.preferences.write_text(json.dumps(changed), encoding='utf-8')
                with self.assertRaises(ValueError):
                    self.capture()

    def test_incomplete_or_forged_receipts_rejected(self):
        before, after = self.capture(), self.capture(8)
        mutations = [lambda x: x.pop('inputs'), lambda x: x['tables'].pop('holidays'),
                     lambda x: x['schema']['shifts']['columns'][0].__setitem__(1, 'renamed'),
                     lambda x: x.__setitem__('room_identity_hash', u.schema(6)['identityHash']),
                     lambda x: x['contract']['schema_sha256'].__setitem__('8', 'a' * 64),
                     lambda x: x['inputs'].__setitem__('database_sha256', ''),
                     lambda x: x['tables']['shifts'].__setitem__('count', True),
                     lambda x: x['photo_associations'].clear(),
                     lambda x: x['portable_preferences'].pop('display.zoom_percent'),
                     lambda x: x['historical_references']['shifts'].pop('sourceObjectiveId'),
                     lambda x: x.__setitem__('installation_provenance', 'installed upgrade verified'),
                     lambda x: x['private_files'].pop('no_backup'),
                     lambda x: x.__setitem__('limit', 'Room migrated'),
                     lambda x: x['tables']['shift_situations'].__setitem__('sha256', 'f' * 64)]
        for index, mutate in enumerate(mutations):
            changed = copy.deepcopy(after)
            mutate(changed)
            with self.subTest(case=index), self.assertRaises(ValueError):
                u.compare(before, changed)

    def test_inputs_are_unchanged_and_changes_during_capture_rejected(self):
        all_files = [p for p in self.root.rglob('*') if p.is_file()]
        original = {str(p): u.digest(p.read_bytes()) for p in all_files}
        self.capture()
        self.assertEqual(original, {str(p): u.digest(p.read_bytes()) for p in all_files})
        real = u.database_evidence
        def change_during_capture(*args):
            result = real(*args)
            (self.private / 'no_backup/unexpected').write_bytes(b'changed during capture')
            return result
        with patch.object(u, 'database_evidence', side_effect=change_during_capture):
            with self.assertRaisesRegex(ValueError, 'Inputs changed'):
                self.capture()

    def test_wal_commits_are_copied_without_touching_supplied_shm(self):
        before = self.capture()
        with closing(sqlite3.connect(self.source)) as db:
            db.execute('PRAGMA journal_mode=WAL')
            db.execute('PRAGMA wal_autocheckpoint=0')
            db.execute('UPDATE shift_notes SET body=?', ('synthetic WAL-only note',))
            db.commit()
            for include_shm in (False, True):
                directory = self.root / ('with-shm' if include_shm else 'without-shm')
                directory.mkdir()
                copied = directory / 'source.db'
                for suffix in ('', '-wal', '-shm') if include_shm else ('', '-wal'):
                    shutil.copyfile(str(self.source) + suffix, str(copied) + suffix)
                original = {p.name: u.digest(p.read_bytes()) for p in directory.iterdir()}
                result = u.capture(copied, self.private, self.preferences, 6)
                self.assertNotEqual(before['tables']['shift_notes'], result['tables']['shift_notes'])
                self.assertEqual(original, {p.name: u.digest(p.read_bytes()) for p in directory.iterdir()})

    def test_cli_capture_compare_and_no_overwrite(self):
        captures = []
        for version, database in ((6, self.source), (8, self.target)):
            output = self.root / f'capture-{version}.json'
            command = [sys.executable, str(pathlib.Path(u.__file__)), 'capture', '--database', str(database),
                       '--private-files', str(self.private), '--portable-preferences', str(self.preferences),
                       '--room-version', str(version), '--fictitious-data', '--inactive-consistent-copy', '--output', str(output)]
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            captures.append(output)
            original = output.read_bytes()
            self.assertNotEqual(0, subprocess.run(command, capture_output=True).returncode)
            self.assertEqual(original, output.read_bytes())
        result_file = self.root / 'result.json'
        result = subprocess.run([sys.executable, str(pathlib.Path(u.__file__)), 'compare', *map(str, captures),
                                 '--output', str(result_file)], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('V6_TO_V8_LOGICAL_CONTENT_PRESERVED', json.loads(result_file.read_text())['verdict'])

    def test_cli_does_not_implicitly_accept_missing_consents(self):
        result = subprocess.run([sys.executable, str(pathlib.Path(u.__file__)), 'capture', '--database', str(self.source),
                                 '--private-files', str(self.private), '--portable-preferences', str(self.preferences),
                                 '--room-version', '6', '--output', str(self.root / 'never.json')], capture_output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.root / 'never.json').exists())


if __name__ == '__main__':
    unittest.main()
