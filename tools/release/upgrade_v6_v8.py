"""Validate and compare explicitly supplied SYNTHETIC, inactive V6 -> V8 copies.

This separate host tool does not migrate a database, decode Copias, inspect an
installed app or execute Room. It never uses ADB. Use `capture --help` for inputs.
Only hashed selected names/values are written. Hashes are evidence, not attestation.
The historical upgrade_snapshot.py V6 -> V6 contract remains independent.
"""
from contextlib import closing
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile
import uuid

ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = ROOT / 'core/database/schemas/com.blackatsystems.miguardia.core.database.MiGuardiaV2Database'
MIGRATIONS = ROOT / 'core/database/src/main/java/com/blackatsystems/miguardia/core/database/MiGuardiaV2Database.kt'
PREFERENCES = ROOT / 'app/src/main/java/com/blackatsystems/miguardia/backup/PortablePreferencesGateway.kt'
FORMAT = 'miguardia-synthetic-v6-v8/1'
HASH = re.compile(r'[a-f0-9]{64}')
PHOTO_KEY = re.compile(r'(?:(?:note|cancel)_)?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:_[0-9a-f]{8})?\.(?:jpg|jpeg|png|webp)')
NEW_TABLES = {'shift_note_attachments': 7, 'shift_situations': 8}
# Types from PortablePreferencesGateway.capture. Values are supplied explicitly;
# this tool never manufactures Android defaults or claims to run its decoder.
PREFERENCE_TYPES = {
    'profile.display_name': 'TEXT', 'display.theme': 'TEXT', 'display.zoom_percent': 'LONG',
    'summary.ordered_families': 'TEXT_LIST', 'summary.hidden_families': 'TEXT_LIST',
    'summary.intro_seen': 'BOOLEAN', 'notifications.enabled': 'BOOLEAN',
    'notifications.precise_timing': 'BOOLEAN', 'notifications.reminder_minutes': 'TEXT_LIST',
    'notifications.persistent': 'BOOLEAN', 'notifications.privacy': 'TEXT',
    'notifications.attention': 'TEXT', 'notifications.dismissed_event_keys': 'TEXT_LIST',
    'weather.enabled': 'BOOLEAN', 'weather.unit': 'TEXT',
    'weather.include_notifications': 'BOOLEAN', 'weather.explanation_accepted': 'BOOLEAN',
}
LIMIT = ('HOST_LOGICAL_ONLY: no Android/Room migration, installed upgrade, Copias restore, '
         'SAF, image decoding, Android preference application, Keystore, notifications or OEM QA. '
         'Inputs must be explicitly supplied synthetic inactive consistent DB/WAL copies. '
         'Hashes bind selected inputs; they do not attest provenance or business validity. '
         'Undeclared historical references are compared as typed values; current target existence is not inferred.')
INSTALLATION = 'NOT_ATTESTED: no installed package, artifact, signer or versionCode inspected'


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode('utf-8')


def digest(value):
    return hashlib.sha256(value).hexdigest()


def quoted(name):
    return '"' + name.replace('"', '""') + '"'


def schema(version):
    if type(version) is not int or version not in (6, 7, 8):
        raise ValueError('Explicit Room 6, 7 or 8 contract required')
    result = json.loads((SCHEMAS / f'{version}.json').read_text(encoding='utf-8'))['database']
    if result['version'] != version or len(result['entities']) != version + 21:
        raise ValueError('Unexpected exported Room contract')
    return result


def source_contract():
    versions = {v: schema(v) for v in (6, 7, 8)}
    prior = {e['tableName']: e for e in versions[6]['entities']}
    for version in (7, 8):
        current = {e['tableName']: e for e in versions[version]['entities']}
        additions = {name for name, added in NEW_TABLES.items() if added <= version}
        if set(current) != set(prior) | additions or any(current[name] != entity for name, entity in prior.items()):
            raise ValueError('Unreviewed migration delta; original V6 entities must be unchanged')
    v7 = {e['tableName']: e for e in versions[7]['entities']}
    v8 = {e['tableName']: e for e in versions[8]['entities']}
    if v7['shift_note_attachments'] != v8['shift_note_attachments']:
        raise ValueError('Unreviewed change to V7 attachment contract')
    return {
        'schema_sha256': {str(v): digest((SCHEMAS / f'{v}.json').read_bytes()) for v in versions},
        'migration_source_sha256': digest(MIGRATIONS.read_bytes()),
        'portable_preferences_source_sha256': digest(PREFERENCES.read_bytes()),
        'allowed_differences': [
            {'table': name, 'introduced_in': version, 'expected_rows': 0,
             'basis': f'Room {version}.json and MIGRATION_{version - 1}_{version}'}
            for name, version in NEW_TABLES.items()
        ],
        'original_columns_changed': [],
        'original_values_changed': [],
    }


def ddl_tokens(sql):
    """Tokenize DDL without erasing literals or constraint semantics.

    SQLite does not expose CHECK, column collation or FK deferrability through
    the PRAGMAs below. Compare their DDL as well. This deliberately is not a SQL
    equivalence engine: unsupported syntax fails closed, and only formatting,
    comments, ASCII case and identifier quoting are normalized.
    """
    if not isinstance(sql, str):
        raise ValueError('Missing explicit SQLite DDL')
    result, index = [], 0
    while index < len(sql):
        char = sql[index]
        if char.isspace():
            index += 1
            continue
        if sql.startswith('--', index):
            end = sql.find('\n', index + 2)
            index = len(sql) if end < 0 else end + 1
            continue
        if sql.startswith('/*', index):
            end = sql.find('*/', index + 2)
            if end < 0:
                raise ValueError('Unterminated SQL comment')
            index = end + 2
            continue
        if char in "'\"`[":
            end_quote = ']' if char == '[' else char
            content, index = '', index + 1
            while index < len(sql):
                if sql[index] == end_quote:
                    # Bracket quoting has no doubled-closing-bracket escape in
                    # SQLite; the other quote styles double their closing quote.
                    if char != '[' and index + 1 < len(sql) and sql[index + 1] == end_quote:
                        content += end_quote
                        index += 2
                        continue
                    index += 1
                    break
                content += sql[index]
                index += 1
            else:
                raise ValueError('Unterminated SQL quote')
            folded = content.translate(str.maketrans('ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'))
            result.append(('literal', content) if char == "'" else ('word', folded))
            continue
        number = re.match(r'(?:0[xX][0-9a-fA-F]+|(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?)', sql[index:])
        if number:
            result.append(('number', number.group()))
            index += len(number.group())
            continue
        word = re.match(r'[A-Za-z_][A-Za-z_0-9$]*', sql[index:])
        if word:
            result.append(('word', word.group().lower()))
            index += len(word.group())
            continue
        operator = next((op for op in ('->>', '||', '<<', '>>', '<=', '>=', '==', '!=', '<>', '->') if sql.startswith(op, index)), None)
        if operator:
            result.append(('symbol', operator))
            index += len(operator)
            continue
        if char in '(),.;+-*/%&|~<>=!':
            result.append(('symbol', char))
            index += 1
            continue
        raise ValueError('Unsupported SQLite DDL token')
    while result and result[-1] == ('symbol', ';'):
        result.pop()
    return result


def canonical_ddl(sql, kind):
    tokens = ddl_tokens(sql)
    create = [('word', 'create')]
    if kind == 'index' and tokens[1:2] == [('word', 'unique')]:
        create.append(('word', 'unique'))
    create.append(('word', kind))
    if kind not in ('table', 'index') or tokens[:len(create)] != create:
        raise ValueError('Unexpected SQLite DDL declaration')
    start = len(create)
    if tokens[start:start + 3] == [('word', 'if'), ('word', 'not'), ('word', 'exists')]:
        del tokens[start:start + 3]
    if kind == 'index':
        # Index term order, expression/collation and partial WHERE predicates
        # are significant. Do not sort or otherwise simplify them.
        return {'tokens': [list(token) for token in tokens]}
    try:
        opening = tokens.index(('symbol', '('))
    except ValueError as error:
        raise ValueError('Explicit CREATE TABLE column definitions required') from error
    depth, definitions, current, closing = 0, [], [], None
    for position in range(opening + 1, len(tokens)):
        token = tokens[position]
        if token == ('symbol', '('):
            depth += 1
        elif token == ('symbol', ')'):
            if depth == 0:
                definitions.append(current)
                closing = position
                break
            depth -= 1
        if token == ('symbol', ',') and depth == 0:
            definitions.append(current)
            current = []
        else:
            current.append(token)
    if closing is None or any(not definition for definition in definitions):
        raise ValueError('Malformed SQLite table definitions')
    # ALTER TABLE ADD COLUMN can place a new column after a table-level PK in
    # sqlite_master SQL. Declaration placement is not meaningful; table_xinfo
    # separately requires exact real column order, types, defaults and PK order.
    return {'header': [list(token) for token in tokens[:opening]],
            'definitions': [[list(token) for token in definition] for definition in sorted(definitions)],
            'tail': [list(token) for token in tokens[closing + 1:]]}


def signatures(db, names):
    result = {}
    for name in sorted(names):
        indices = []
        for item in db.execute('PRAGMA index_list(' + quoted(name) + ')'):
            # index_list sequence is creation order, not part of logical identity.
            index_sql = db.execute("SELECT sql FROM sqlite_master WHERE type='index' AND name=?", (item[1],)).fetchone()[0]
            indices.append({'name': item[1], 'unique': item[2], 'origin': item[3], 'partial': item[4],
                            'columns': [list(r) for r in db.execute('PRAGMA index_xinfo(' + quoted(item[1]) + ')')],
                            'ddl': canonical_ddl(index_sql, 'index') if index_sql is not None else None})
        table_sql = db.execute("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", (name,)).fetchone()[0]
        result[name] = {
            'ddl': canonical_ddl(table_sql, 'table'),
            'columns': [list(r) for r in db.execute('PRAGMA table_xinfo(' + quoted(name) + ')')],
            'foreign_keys': sorted([list(r) for r in db.execute('PRAGMA foreign_key_list(' + quoted(name) + ')')]),
            'indices': sorted(indices, key=lambda i: i['name']),
        }
    return result


def expected_schema(version):
    with closing(sqlite3.connect(':memory:')) as db:
        for entity in schema(version)['entities']:
            db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            for index in entity.get('indices', []):
                db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        return signatures(db, [e['tableName'] for e in schema(version)['entities']])


def is_hash(value):
    return isinstance(value, str) and HASH.fullmatch(value) is not None


def exact(value, fields, label):
    if not isinstance(value, dict) or set(value) != set(fields):
        raise ValueError('Incomplete or unexpected ' + label)


def portable_preferences(value):
    if not isinstance(value, list) or len(value) != len(PREFERENCE_TYPES):
        raise ValueError('Exactly 17 explicitly exported portable preferences required')
    result = {}
    for item in value:
        exact(item, ('key', 'type', 'values'), 'portable preference')
        key, kind, values = item['key'], item['type'], item['values']
        if not isinstance(key, str) or key not in PREFERENCE_TYPES or key in result or kind != PREFERENCE_TYPES[key]:
            raise ValueError('Unknown, duplicate or incorrectly typed portable preference')
        if not isinstance(values, list) or any(not isinstance(v, str) for v in values):
            raise ValueError('Portable preference values must be explicit strings')
        if kind == 'BOOLEAN' and (len(values) != 1 or values[0] not in ('true', 'false')):
            raise ValueError('Invalid portable boolean')
        if kind == 'LONG' and (len(values) != 1 or not re.fullmatch(r'-?(?:0|[1-9][0-9]*)', values[0]) or
                               not -(2**63) <= int(values[0]) < 2**63):
            raise ValueError('Invalid portable long')
        if kind == 'TEXT' and len(values) != 1 and not (key == 'profile.display_name' and not values):
            raise ValueError('Incomplete portable text')
        if kind == 'TEXT_LIST' and len(values) > (16384 if key == 'notifications.dismissed_event_keys' else 64):
            raise ValueError('Portable list exceeds contract bound')
        result[key] = {'type': kind, 'sha256': digest(canonical(values)), 'count': len(values)}
    return result


def private_files(private_root):
    def linked(path):
        return path.is_symlink() or getattr(path, 'is_junction', lambda: False)()
    if not private_root.is_dir() or linked(private_root):
        raise ValueError('Explicit selected private roots required')
    result = {}
    for name in ('files', 'shared_prefs', 'no_backup'):
        root = private_root / name
        if not root.is_dir() or linked(root):
            raise ValueError('All three selected private roots must exist explicitly')
        result[name] = {}
        pending = [root]
        while pending:
            for entry in sorted(os.scandir(pending.pop()), key=lambda e: e.name):
                path = Path(entry.path)
                # Reject before descent: never follow a junction/reparse subtree.
                if linked(path):
                    raise ValueError('Links/reparse directories in selected private snapshot')
                if entry.is_file(follow_symlinks=False):
                    content = path.read_bytes()
                    result[name][digest(path.relative_to(root).as_posix().encode())] = {
                        'sha256': digest(content), 'bytes': len(content)}
                elif entry.is_dir(follow_symlinks=False):
                    pending.append(path)
                else:
                    raise ValueError('Unsupported private input')
    return result


def cell(value, field):
    if value is None:
        if field.get('notNull', False):
            raise ValueError('Unexpected NULL in required cell')
        return ['null', None]
    kind = field['affinity']
    accepted = {'TEXT': str, 'INTEGER': int, 'REAL': float, 'BLOB': bytes}
    if kind not in accepted or type(value) is not accepted[kind]:
        raise ValueError('SQLite storage type differs from exported Room affinity')
    if isinstance(value, float) and not math.isfinite(value):
        raise ValueError('Non-finite SQLite real')
    column = field['columnName']
    if kind == 'TEXT' and (column == 'id' or column.endswith('Id')) and column != 'zoneId':
        try:
            valid = str(uuid.UUID(value)) == value
        except (ValueError, AttributeError):
            valid = False
        if not valid:
            raise ValueError('Noncanonical UUID identity/reference')
    return [kind.lower(), value.hex() if isinstance(value, bytes) else value]


def database_evidence(db, version, files):
    if db.execute('PRAGMA user_version').fetchone()[0] != version:
        raise ValueError('Room user_version differs from explicit capture version')
    try:
        consistent = db.execute('PRAGMA integrity_check').fetchall() == [('ok',)] and not db.execute('PRAGMA foreign_key_check').fetchall()
    except sqlite3.DatabaseError as error:
        raise ValueError('SQLite integrity or foreign key contract inconsistency') from error
    if not consistent:
        raise ValueError('SQLite integrity or foreign key inconsistency')
    try:
        identity = db.execute('SELECT id, identity_hash FROM room_master_table ORDER BY id').fetchall()
    except sqlite3.DatabaseError as error:
        raise ValueError('Missing Room identity table') from error
    if identity != [(42, schema(version)['identityHash'])]:
        raise ValueError('Room identity does not match exact exported version')
    if db.execute("SELECT count(*) FROM sqlite_master WHERE type IN ('view','trigger')").fetchone()[0]:
        raise ValueError('Unexpected view or trigger')
    names = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table')")]
    actual = signatures(db, names)
    if actual != expected_schema(version):
        raise ValueError('Exact Room tables/columns/keys/indices required')
    tables, historical_references = {}, {}
    for entity in schema(version)['entities']:
        name, fields = entity['tableName'], entity['fields']
        rows = db.execute('SELECT ' + ','.join(quoted(f['columnName']) for f in fields) + ' FROM ' + quoted(name)).fetchall()
        typed = [[cell(v, f) for v, f in zip(row, fields)] for row in rows]
        tables[name] = {'count': len(rows), 'sha256': digest(canonical(sorted(digest(canonical(row)) for row in typed)))}
        # All 27 original tables must exercise actual rows, not only a count subset.
        if name not in NEW_TABLES and not rows:
            raise ValueError('Missing populated synthetic coverage: ' + name)
        if name in NEW_TABLES and rows:
            raise ValueError('Pure V6 upgrade must leave new tables empty')
        pk = entity['primaryKey']['columnNames']
        fk = {column for key in entity.get('foreignKeys', []) for column in key['columns']}
        columns = [f['columnName'] for f in fields]
        references = [c for c in columns if c.endswith('Id') and c != 'zoneId' and c not in pk and c not in fk]
        if references:
            historical_references[name] = {}
            for column in references:
                # Historical snapshots deliberately have some UUID references without
                # a declared FK. Preserve every NULL/value keyed to its original row;
                # do not invent a new product constraint requiring a live target.
                selected = [[row[columns.index(key)] for key in pk + [column]] for row in typed]
                historical_references[name][column] = {
                    'count': len(rows), 'sha256': digest(canonical(sorted(digest(canonical(row)) for row in selected)))}
    roots = db.execute('SELECT timelineId, singletonSlot FROM work_configuration_roots').fetchall()
    if len(roots) != 1 or roots[0][1] != 1:
        raise ValueError('Single explicit logical timeline required')
    photos = {}
    for record_id, key, byte_size in db.execute('SELECT id,storageKey,byteSize FROM schedule_photos'):
        if not isinstance(key, str) or not PHOTO_KEY.fullmatch(key) or type(byte_size) is not int or byte_size <= 0:
            raise ValueError('Invalid schedule photo association')
        name_hash = digest(('schedule_photos/' + key).encode())
        selected = files['files'].get(name_hash)
        if selected is None or selected['bytes'] != byte_size or name_hash in photos:
            raise ValueError('Photo file absent, truncated or multiply associated')
        photos[name_hash] = {'record_sha256': digest(record_id.encode()), **selected}
    return actual, tables, digest(roots[0][0].encode()), photos, historical_references


def validate_snapshot(value):
    exact(value, ('format', 'fictitious', 'room_version', 'room_identity_hash', 'contract', 'schema',
                  'tables', 'timeline_sha256', 'private_files', 'photo_associations',
                  'portable_preferences', 'historical_references', 'installation_provenance', 'inputs', 'limit'), 'V6/V8 snapshot')
    version = value['room_version']
    if value['format'] != FORMAT or value['fictitious'] is not True or type(version) is not int or version not in (6, 8):
        raise ValueError('Explicit synthetic V6 or V8 snapshot required')
    if value['contract'] != source_contract() or value['room_identity_hash'] != schema(version)['identityHash']:
        raise ValueError('Snapshot is not bound to current exact Room/migration/preference contracts')
    expected = expected_schema(version)
    if value['schema'] != expected or not isinstance(value['tables'], dict) or set(value['tables']) != set(expected):
        raise ValueError('Incomplete or changed schema evidence')
    for name, evidence in value['tables'].items():
        exact(evidence, ('count', 'sha256'), 'table evidence')
        if type(evidence['count']) is not int or evidence['count'] < 0 or not is_hash(evidence['sha256']):
            raise ValueError('Invalid typed row evidence')
        if name not in NEW_TABLES and not evidence['count']:
            raise ValueError('Missing populated synthetic scenario')
        if name in NEW_TABLES and evidence != {'count': 0, 'sha256': digest(canonical([]))}:
            raise ValueError('New tables must have proven empty contents')
    if not is_hash(value['timeline_sha256']) or value['limit'] != LIMIT or value['installation_provenance'] != INSTALLATION:
        raise ValueError('Incomplete timeline or scope evidence')
    expected_references = {}
    for entity in schema(version)['entities']:
        pk = entity['primaryKey']['columnNames']
        fk = {column for key in entity.get('foreignKeys', []) for column in key['columns']}
        columns = [f['columnName'] for f in entity['fields']]
        references = [c for c in columns if c.endswith('Id') and c != 'zoneId' and c not in pk and c not in fk]
        if references:
            expected_references[entity['tableName']] = references
    exact(value['historical_references'], expected_references, 'historical reference tables')
    for name, columns in expected_references.items():
        exact(value['historical_references'][name], columns, 'historical reference columns')
        for evidence in value['historical_references'][name].values():
            exact(evidence, ('count', 'sha256'), 'historical reference evidence')
            if type(evidence['count']) is not int or evidence['count'] != value['tables'][name]['count'] or not is_hash(evidence['sha256']):
                raise ValueError('Incomplete historical reference evidence')
    exact(value['private_files'], ('files', 'shared_prefs', 'no_backup'), 'private file roots')
    for files in value['private_files'].values():
        if not isinstance(files, dict):
            raise ValueError('Missing explicit private-file evidence')
        for name, item in files.items():
            exact(item, ('sha256', 'bytes'), 'selected file')
            if not is_hash(name) or not is_hash(item['sha256']) or type(item['bytes']) is not int or item['bytes'] < 0:
                raise ValueError('Invalid selected file hash/size')
    if not isinstance(value['photo_associations'], dict) or len(value['photo_associations']) != value['tables']['schedule_photos']['count']:
        raise ValueError('Incomplete photo associations')
    for name, photo in value['photo_associations'].items():
        exact(photo, ('record_sha256', 'sha256', 'bytes'), 'photo association')
        if not is_hash(name) or not is_hash(photo['record_sha256']) or value['private_files']['files'].get(name) != {k: photo[k] for k in ('sha256', 'bytes')}:
            raise ValueError('Unbound photo association')
    exact(value['portable_preferences'], PREFERENCE_TYPES, '17 portable preference evidences')
    for key, item in value['portable_preferences'].items():
        exact(item, ('type', 'sha256', 'count'), 'preference evidence')
        if item['type'] != PREFERENCE_TYPES[key] or not is_hash(item['sha256']) or type(item['count']) is not int or item['count'] < 0:
            raise ValueError('Invalid portable preference evidence')
        if item['type'] in ('BOOLEAN', 'LONG') and item['count'] != 1:
            raise ValueError('Incomplete scalar preference evidence')
        if item['type'] == 'TEXT' and item['count'] not in ((0, 1) if key == 'profile.display_name' else (1,)):
            raise ValueError('Incomplete text preference evidence')
    exact(value['inputs'], ('database_sha256', 'wal_sha256', 'portable_preferences_sha256'), 'input hashes')
    if not is_hash(value['inputs']['database_sha256']) or not is_hash(value['inputs']['portable_preferences_sha256']) or not (value['inputs']['wal_sha256'] is None or is_hash(value['inputs']['wal_sha256'])):
        raise ValueError('Invalid input provenance hashes')


def capture(database, private_root, preference_file, version):
    """Read only a private DB/WAL copy; detect changes to the selected input set."""
    if type(version) is not int or version not in (6, 8):
        raise ValueError('Capture version must be explicitly 6 or 8')
    database, private_root, preference_file = map(Path, (database, private_root, preference_file))
    if database.is_symlink() or preference_file.is_symlink():
        raise ValueError('Direct inactive files required')
    wal = Path(str(database) + '-wal')
    if wal.is_symlink():
        raise ValueError('Direct inactive WAL required')
    def input_hashes():
        return {'database_sha256': digest(database.read_bytes()),
                'wal_sha256': digest(wal.read_bytes()) if wal.exists() else None,
                'portable_preferences_sha256': digest(preference_file.read_bytes())}
    inputs = input_hashes()
    files = private_files(private_root)
    preferences = portable_preferences(json.loads(preference_file.read_text(encoding='utf-8')))
    with tempfile.TemporaryDirectory(prefix='mg-v6-v8-') as directory:
        copied = Path(directory) / 'snapshot.db'
        shutil.copyfile(database, copied)
        if wal.exists():
            shutil.copyfile(wal, str(copied) + '-wal')
        with closing(sqlite3.connect(copied.resolve().as_uri() + '?mode=ro', uri=True)) as db:
            actual, tables, timeline, photos, references = database_evidence(db, version, files)
    if inputs != input_hashes() or files != private_files(private_root):
        raise ValueError('Inputs changed during capture; provide inactive consistent copies')
    result = {'format': FORMAT, 'fictitious': True, 'room_version': version,
              'room_identity_hash': schema(version)['identityHash'], 'contract': source_contract(),
              'schema': actual, 'tables': tables, 'timeline_sha256': timeline,
              'private_files': files, 'photo_associations': photos, 'portable_preferences': preferences,
              'historical_references': references, 'installation_provenance': INSTALLATION,
              'inputs': inputs, 'limit': LIMIT}
    validate_snapshot(result)
    return result


def compare(before, after):
    validate_snapshot(before)
    validate_snapshot(after)
    if (before['room_version'], after['room_version']) != (6, 8):
        raise ValueError('This explicit comparison requires V6 source and V8 target')
    original = {name: after['tables'][name] for name in before['tables']}
    if original != before['tables']:
        raise ValueError('An original V6 row, identity, association, value or type changed')
    for key in ('timeline_sha256', 'private_files', 'photo_associations', 'portable_preferences', 'historical_references'):
        if before[key] != after[key]:
            raise ValueError('Logical timeline, selected file, association or portable preference changed')
    return {'verdict': 'V6_TO_V8_LOGICAL_CONTENT_PRESERVED', 'source_room_version': 6, 'target_room_version': 8,
            'original_tables': len(before['tables']), 'target_tables': len(after['tables']),
            'portable_preferences': len(before['portable_preferences']), 'photos': len(before['photo_associations']),
            'allowed_differences': source_contract()['allowed_differences'],
            'source_snapshot_sha256': digest(canonical(before)), 'target_snapshot_sha256': digest(canonical(after)),
            'contract': source_contract(), 'installation_provenance': INSTALLATION, 'limit': LIMIT}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    command = sub.add_parser('capture', description='Capture explicit inactive synthetic DB/WAL, three private roots and portable preference export. Does not read Android.')
    command.add_argument('--database', type=Path, required=True)
    command.add_argument('--private-files', type=Path, required=True)
    command.add_argument('--portable-preferences', type=Path, required=True,
                         help='JSON list of exactly 17 {key,type,values} records exported by fixture; no defaults')
    command.add_argument('--room-version', type=int, choices=(6, 8), required=True)
    command.add_argument('--fictitious-data', action='store_true', required=True)
    command.add_argument('--inactive-consistent-copy', action='store_true', required=True)
    command.add_argument('--output', type=Path, required=True)
    command = sub.add_parser('compare', description='Compare V6 source with V8 target, never claims installed upgrade.')
    command.add_argument('before', type=Path)
    command.add_argument('after', type=Path)
    command.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError('Output must be new')
    result = capture(args.database, args.private_files, args.portable_preferences, args.room_version) if args.command == 'capture' else compare(
        json.loads(args.before.read_text(encoding='utf-8')), json.loads(args.after.read_text(encoding='utf-8')))
    # Exclusive creation also closes the check/write race; never overwrite inputs.
    with args.output.open('x', encoding='utf-8') as target:
        target.write(json.dumps(result, indent=2, ensure_ascii=False) + '\n')


if __name__ == '__main__':
    main()
