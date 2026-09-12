"""Compare explicitly supplied fictitious upgrade snapshots. No ADB, network or writes to inputs.
Requires consistent copied SQLite/WAL and selected private files. Hashes are not device attestation.
"""
from contextlib import closing
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile

SCHEMA = Path(__file__).resolve().parents[2] / 'core/database/schemas/com.blackatsystems.miguardia.core.database.MiGuardiaV2Database/6.json'
REQUIRED_POPULATED = {'shifts', 'shift_work_snapshots', 'recurring_plans', 'recurring_plan_revisions',
    'recurring_occurrences', 'shift_actual_records', 'shift_extra_intervals', 'independent_extra_work_records',
    'availability_windows', 'medical_leaves', 'schedule_photos', 'shift_notes', 'work_configuration_roots',
    'work_configuration_revisions', 'explicit_day_statuses', 'vacations', 'shift_notification_configs'}
HASH = re.compile('[a-f0-9]{64}')

def digest(value):
    return hashlib.sha256(value).hexdigest()

def schema():
    data = json.loads(SCHEMA.read_text(encoding='utf-8'))['database']
    assert data['version'] == 6 and len(data['entities']) == 27
    return data

def quoted(name):
    return '"' + name.replace('"', '""') + '"'

def signatures(db, names):
    return {name: {'columns': [list(row) for row in db.execute('PRAGMA table_info(' + quoted(name) + ')')],
                   'foreign_keys': [list(row) for row in db.execute('PRAGMA foreign_key_list(' + quoted(name) + ')')]}
            for name in names}

def expected_schema():
    with closing(sqlite3.connect(':memory:')) as db:
        for entity in schema()['entities']:
            db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        return signatures(db, sorted(e['tableName'] for e in schema()['entities']))

def validate_identity(identity):
    if set(identity) != {'application_id', 'certificate_sha256', 'version_code', 'artifact_sha256'}:
        raise ValueError('Exact public identity fields required')
    if identity['application_id'] not in {'com.blackatsystems.miguardia', 'com.blackatsystems.miguardia.qa'}:
        raise ValueError('Unexpected package')
    if type(identity['version_code']) is not int or not 1 <= identity['version_code'] <= 2100000000:
        raise ValueError('Invalid version code')
    for field in ('certificate_sha256', 'artifact_sha256'):
        if not isinstance(identity[field], str) or not HASH.fullmatch(identity[field]):
            raise ValueError('Invalid public hash')

def validate_snapshot(value):
    if value.get('format') != 2 or value.get('fictitious') is not True or value.get('schema_sha256') != digest(SCHEMA.read_bytes()):
        raise ValueError('Unsupported or unbound snapshot')
    validate_identity(value['identity'])
    expected = expected_schema()
    if value.get('schema') != expected or set(value['tables']) != set(expected):
        raise ValueError('Exact V6 tables, columns and foreign keys required')
    for name, table in value['tables'].items():
        if set(table) != {'count', 'sha256'} or type(table['count']) is not int or table['count'] < 0 or not HASH.fullmatch(table['sha256']):
            raise ValueError('Invalid table evidence')
        if name in REQUIRED_POPULATED and table['count'] == 0:
            raise ValueError('Missing populated fictitious scenario: ' + name)
    if set(value.get('private_files', {})) != {'files', 'shared_prefs', 'no_backup'}:
        raise ValueError('Explicit files/preferences/residual roots required')
    for root, files in value['private_files'].items():
        if not isinstance(files, dict) or (root != 'no_backup' and not files):
            raise ValueError('Missing photos/files or preferences evidence')
        if any(not HASH.fullmatch(name) or not HASH.fullmatch(content) for name, content in files.items()):
            raise ValueError('Only hashed private names/content are accepted')

def capture(database, private_root, identity):
    validate_identity(identity)
    if not private_root.is_dir():
        raise ValueError('Missing explicit private-file snapshot')
    # SQLite mode=ro can still create/change SHM for a WAL database. Read only a
    # private copy, retaining committed WAL transactions but rebuilding its SHM.
    # The operator must supply an already consistent, inactive DB/WAL snapshot.
    with tempfile.TemporaryDirectory(prefix='miguardia-upgrade-') as directory:
        copied = Path(directory) / 'snapshot.db'
        shutil.copyfile(database, copied)
        wal = Path(str(database) + '-wal')
        if wal.exists():
            shutil.copyfile(wal, Path(str(copied) + '-wal'))
        return _capture_private_copy(copied, private_root, identity)

def _capture_private_copy(database, private_root, identity):
    db = sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True)
    try:
        if db.execute('PRAGMA user_version').fetchone()[0] != 6:
            raise ValueError('Room version must be 6')
        if db.execute('PRAGMA integrity_check').fetchall() != [('ok',)] or db.execute('PRAGMA foreign_key_check').fetchall():
            raise ValueError('SQLite integrity or foreign key inconsistency')
        names = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table') ORDER BY name")]
        actual_schema = signatures(db, names)
        if actual_schema != expected_schema():
            raise ValueError('Database does not match protected V6 schema')
        tables = {}
        for name in names:
            rows = []
            for row in db.execute('SELECT * FROM ' + quoted(name)):
                typed = [['blob', v.hex()] if isinstance(v, bytes) else [type(v).__name__, v] for v in row]
                rows.append(digest(json.dumps(typed, ensure_ascii=False, separators=(',', ':')).encode()))
            tables[name] = {'count': len(rows), 'sha256': digest(json.dumps(sorted(rows)).encode())}
        files = {}
        for name in ('files', 'shared_prefs', 'no_backup'):
            root = private_root / name
            if not root.is_dir() or root.is_symlink():
                raise ValueError('Supply each selected private root explicitly')
            files[name] = {}
            for path in sorted(root.rglob('*')):
                if path.is_symlink():
                    raise ValueError('Symlink in supplied snapshot')
                if path.is_file():
                    files[name][digest(path.relative_to(root).as_posix().encode())] = digest(path.read_bytes())
        result = {'format': 2, 'fictitious': True, 'identity': identity, 'schema_sha256': digest(SCHEMA.read_bytes()),
                  'schema': actual_schema, 'tables': tables, 'private_files': files}
        validate_snapshot(result)
        return result
    finally:
        db.close()

def compare(before, after):
    validate_snapshot(before)
    validate_snapshot(after)
    a, b = before['identity'], after['identity']
    if a['application_id'] != b['application_id'] or a['certificate_sha256'] != b['certificate_sha256'] or b['version_code'] <= a['version_code'] or a['artifact_sha256'] == b['artifact_sha256']:
        raise ValueError('Two distinct complete apps, same package/certificate and increasing code required')
    if before['tables'] != after['tables'] or before['private_files'] != after['private_files']:
        raise ValueError('Data or selected private files changed; investigate before acceptance')
    return {'verdict': 'LOGICAL_SNAPSHOTS_EQUAL', 'tables': 27,
            'limit': 'Operator must bind consistent snapshots to installed complete artifacts. Row counts do not prove valid business scenarios. Keystore, UI lock, notifications, Widget and actual photo usability require device QA.'}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    command = sub.add_parser('capture')
    command.add_argument('--database', type=Path, required=True)
    command.add_argument('--private-files', type=Path, required=True)
    command.add_argument('--identity', type=Path, required=True)
    command.add_argument('--fictitious-data', action='store_true', required=True)
    command.add_argument('--output', type=Path, required=True)
    command = sub.add_parser('compare')
    command.add_argument('before', type=Path)
    command.add_argument('after', type=Path)
    command.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError('Output must be new')
    result = capture(args.database, args.private_files, json.loads(args.identity.read_text())) if args.command == 'capture' else compare(json.loads(args.before.read_text()), json.loads(args.after.read_text()))
    args.output.write_text(json.dumps(result, indent=2), encoding='utf-8')

if __name__ == '__main__':
    main()
