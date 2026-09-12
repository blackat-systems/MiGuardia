from contextlib import closing
import copy
import pathlib
import shutil
import sqlite3
import sys
import tempfile
import unittest
sys.path.insert(0, str(pathlib.Path(__file__).parents[1]))
import upgrade_snapshot as u

class UpgradeSnapshotTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
        self.db = self.root / 'sample.db'
        self.private = self.root / 'private'
        for name in ('files', 'shared_prefs', 'no_backup'):
            (self.private / name).mkdir(parents=True)
        (self.private / 'files/photo').write_bytes(b'fictitious photo, not a decoder fixture')
        (self.private / 'shared_prefs/settings.xml').write_bytes(b'fictitious preference')
        self.identity = {'application_id': 'com.blackatsystems.miguardia.qa', 'certificate_sha256': 'a'*64,
                         'version_code': 2, 'artifact_sha256': 'b'*64}
    def tearDown(self):
        self.temp.cleanup()
    def populated(self):
        # Exact SQL schema; generic rows exercise comparator only, never claim valid labor data.
        with closing(sqlite3.connect(self.db)) as db, db:
            for entity in u.schema()['entities']:
                name = entity['tableName']
                db.execute(entity['createSql'].replace('${TABLE_NAME}', name))
                values = [None if not f.get('notNull', False) else 1 if f['affinity'] in ('INTEGER', 'REAL') else 'fixture' for f in entity['fields']]
                db.execute('INSERT INTO '+u.quoted(name)+' VALUES ('+','.join('?' for v in values)+')', values)
            for entity in u.schema()['entities']:
                for index in entity.get('indices', []): db.execute(index['createSql'].replace('${TABLE_NAME}',entity['tableName']))
            db.execute('PRAGMA user_version=6')
        return u.capture(self.db, self.private, self.identity)
    def test_rejects_empty_database(self):
        sqlite3.connect(self.db).close()
        with self.assertRaises(ValueError):
            u.capture(self.db, self.private, self.identity)
    def test_exact_schema_snapshot_and_private_changes(self):
        before = self.populated()
        after = copy.deepcopy(before)
        after['identity'].update(version_code=3, artifact_sha256='c'*64)
        self.assertEqual('LOGICAL_SNAPSHOTS_EQUAL', u.compare(before, after)['verdict'])
        after['private_files']['files']['d'*64] = 'e'*64
        with self.assertRaises(ValueError): u.compare(before, after)
    def test_compare_rejects_empty_or_forged_schema(self):
        before = self.populated()
        for mutation in ('empty', 'renamed', 'column', 'unpopulated', 'boolean_code'):
            after = copy.deepcopy(before)
            after['identity'].update(version_code=3, artifact_sha256='c'*64)
            if mutation == 'empty': after['tables'] = {}
            if mutation == 'renamed': after['tables']['foreign_table'] = after['tables'].pop('shifts')
            if mutation == 'column': after['schema']['shifts']['columns'][0][1] = 'other'
            if mutation == 'unpopulated': after['tables']['shifts']['count'] = 0
            if mutation == 'boolean_code': after['identity']['version_code'] = True
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): u.compare(before, after)
    def test_capture_rejects_added_column(self):
        self.populated()
        with closing(sqlite3.connect(self.db)) as db, db: db.execute('ALTER TABLE shifts ADD COLUMN alien TEXT')
        with self.assertRaises(ValueError): u.capture(self.db, self.private, self.identity)
    def test_wal_snapshot_is_read_without_creating_or_modifying_input_sidecars(self):
        before = self.populated()
        with closing(sqlite3.connect(self.db)) as db:
            db.execute('PRAGMA journal_mode=WAL')
            db.execute('PRAGMA wal_autocheckpoint=0')
            column = next(field['columnName'] for entity in u.schema()['entities']
                          if entity['tableName'] == 'shift_notes'
                          for field in entity['fields'] if field['affinity'] == 'TEXT')
            db.execute('UPDATE shift_notes SET '+u.quoted(column)+'=?', ('wal-only-note',))
            db.commit()
            for include_shm in (False, True):
                with self.subTest(include_shm=include_shm):
                    inputs = self.root / ('wal-with-shm' if include_shm else 'wal-without-shm')
                    inputs.mkdir()
                    copied = inputs / 'copied.db'
                    for suffix in ('', '-wal', '-shm') if include_shm else ('', '-wal'):
                        shutil.copyfile(str(self.db)+suffix, str(copied)+suffix)
                    original = {p.name:u.digest(p.read_bytes()) for p in inputs.iterdir()}
                    result = u.capture(copied, self.private, self.identity)
                    self.assertNotEqual(before['tables']['shift_notes'], result['tables']['shift_notes'])
                    self.assertEqual(original, {p.name:u.digest(p.read_bytes()) for p in inputs.iterdir()})
    def test_same_code_signer_or_same_apk_is_not_upgrade(self):
        before = self.populated()
        for updates in ({}, {'version_code':3,'certificate_sha256':'d'*64}, {'version_code':3}):
            after = copy.deepcopy(before); after['identity'].update(updates)
            with self.assertRaises(ValueError): u.compare(before, after)
