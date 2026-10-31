"""Classify the approved Stage10 expiry case without altering strict Stage9 evidence."""
import argparse
from contextlib import closing
import json
from pathlib import Path
import sqlite3

import stage10_capture as transport
import upgrade_v6_v8 as strict

DISMISSED = 'notifications.dismissed_event_keys'
KNOWN = 'shift:30000000-0000-4000-8000-000000000009'
RAW = 'files/datastore/notification_preferences.preferences_pb.preferences_pb'
TECHNICAL = {
    'files/profileInstalled': 'e75f27e7b4159e6d5875c738acecc092060912606c435305af8c92c68456ba5e',
    'shared_prefs/notification_deferred_actions.xml': '3325d2a819fdd8062c2cdc48a09b995c9b012915bcdf88b1cf9742a7f057c793',
}
BASE_KEYS = {'enabled', 'precise_timing', 'persistent_while_active', 'privacy', 'attention_mode', 'global_reminder_minutes', 'dismissed_shift_ids'}
OPERATIONAL = {'installed_boundary_keys': b'\x32\x00', 'installed_exact_mode': b'\x08\x00', 'displayed_shift_ids': b'\x32\x00'}
PLATFORM_PATH = 'shared_prefs/android.app.ActivityThread.IDS.xml'
PLATFORM_SHA256 = 'dec264c49b5f4f8b1526a8d84d803fc55102be8b02c3b4a7f0ee6ac973ca9ece'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def fields(data):
    """Bounded wire-0/2 decoder for this exact Preferences protobuf, not repair."""
    require(isinstance(data, bytes) and len(data) <= 65536, 'Bounded RAW protobuf required')
    offset = 0

    def integer():
        nonlocal offset
        value = 0
        for shift in range(0, 70, 7):
            require(offset < len(data), 'Truncated varint')
            byte = data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if byte < 128:
                require(shift == 0 or byte != 0, 'Noncanonical varint')
                return value
        raise ValueError('Oversized varint')

    result = []
    while offset < len(data):
        tag = integer()
        number, wire = tag >> 3, tag & 7
        require(number > 0 and wire in (0, 2), 'Unknown protobuf field/wire')
        if wire == 0:
            value = integer()
        else:
            size = integer()
            require(offset + size <= len(data), 'Truncated protobuf field')
            value = data[offset:offset + size]
            offset += size
        result.append((number, wire, value))
    return result


def preference_map(data):
    result = {}
    for number, wire, entry in fields(data):
        require((number, wire) == (1, 2), 'Unknown preference-map field')
        pair = fields(entry)
        require(len(pair) == 2 and [(n, w) for n, w, _ in pair] == [(1, 2), (2, 2)], 'Malformed preference entry')
        key = pair[0][2].decode('utf-8')
        require(key not in result, 'Duplicate RAW preference key')
        require(len(fields(pair[1][2])) == 1, 'Ambiguous preference value')
        result[key] = pair[1][2]
    return result


def string_set(value):
    outer = fields(value)
    require(len(outer) == 1 and outer[0][:2] == (6, 2), 'Dismissals must be an explicit string set')
    values = []
    for number, wire, data in fields(outer[0][2]):
        require((number, wire) == (1, 2), 'Unknown string-set field')
        values.append(data.decode('utf-8'))
    require(len(values) == len(set(values)), 'Duplicate RAW dismissal')
    return set(values)


def notification_delta(before, after, before_portable, after_portable, captured_at, effective_end):
    a, b = preference_map(before), preference_map(after)
    require(set(a) == BASE_KEYS and set(b) == BASE_KEYS | set(OPERATIONAL), 'Unknown/missing notification RAW key')
    for key in BASE_KEYS - {'dismissed_shift_ids'}:
        require(a[key] == b[key], 'Durable notification RAW entry changed: ' + key)
    for key, value in OPERATIONAL.items():
        require(b[key] == value, 'Operational notification value is not the demonstrated empty/default')
    previous, current = string_set(a['dismissed_shift_ids']), string_set(b['dismissed_shift_ids'])
    require(previous == {KNOWN} and current <= previous, 'New/unknown dismissal identity')
    require(previous == set(before_portable) and current == set(after_portable), 'Gateway export masks different RAW dismissals')
    removed = previous - current
    require(not removed or captured_at >= effective_end, 'A still-live dismissal was lost')
    return {'removed': sorted(removed), 'effective_end_epoch_millis': effective_end, 'captured_at_epoch_millis': captured_at,
            'operational_added': sorted(OPERATIONAL), 'other_notification_entries_byte_identical': True}


def load_capture(directory, version):
    directory = Path(directory)
    wrapper = json.loads((directory / 'capture-receipt.json').read_text())
    receipt = wrapper['android_receipt']
    require(transport.validate_receipt(receipt) == version, 'Capture version differs')
    actual_paths = {p.relative_to(directory).as_posix() for p in (directory / 'private').rglob('*') if p.is_file()}
    require(actual_paths == transport.PERSISTENT, 'Unexpected/missing selected private RAW')
    for name in transport.SELECTED:
        path = directory / name
        require(not transport.linked(path), 'Linked capture input')
        data = path.read_bytes()
        require({'bytes': len(data), 'sha256': transport.sha(data)} == receipt['files'][name], 'Capture input differs from Android RAW receipt')
    snapshot = strict.capture(directory / 'database.db', directory / 'private', directory / 'portable-preferences.json', version)
    require(snapshot == json.loads((directory / 'snapshot.json').read_text()), 'Snapshot no longer matches actual inputs')
    return snapshot, receipt, json.loads((directory / 'portable-preferences.json').read_text())


def compare(before_directory, after_directory, technical_annex, platform_annex=None):
    before_directory, after_directory, technical_annex = map(Path, (before_directory, after_directory, technical_annex))
    before, old_receipt, old_values = load_capture(before_directory, 6)
    after, receipt, values = load_capture(after_directory, 8)
    require(receipt['operation'] == transport.EXPIRY_OPERATION, 'Explicit approved criterion operation required')
    require(receipt.get('technical_files_not_portable') == TECHNICAL, 'Technical-annex identities changed')
    for name, expected in TECHNICAL.items():
        path = technical_annex / name
        require(not transport.linked(path) and transport.sha(path.read_bytes()) == expected, 'Technical RAW annex changed')
    platform = receipt.get('platform_runtime_files', {})
    if platform:
        require(platform == {PLATFORM_PATH: PLATFORM_SHA256} and platform_annex is not None, 'Unknown/unbound platform annex')
        path = Path(platform_annex) / PLATFORM_PATH
        require(not transport.linked(path) and path.stat().st_size == 108 and transport.sha(path.read_bytes()) == PLATFORM_SHA256, 'Platform RAW annex changed')
    for table in before['tables']:
        require(before['tables'][table] == after['tables'][table], 'Original typed rows changed')
    for key in ('timeline_sha256', 'photo_associations', 'historical_references'):
        require(before[key] == after[key], 'Durable association/history changed')
    for key in strict.PREFERENCE_TYPES:
        if key != DISMISSED:
            require(before['portable_preferences'][key] == after['portable_preferences'][key], 'Durable portable preference changed: ' + key)
    notification_hash = strict.digest(RAW.removeprefix('files/').encode())
    for category in ('files', 'shared_prefs', 'no_backup'):
        a, b = before['private_files'][category], after['private_files'][category]
        require(set(a) == set(b), 'Selected file added/removed')
        for key in a:
            if category != 'files' or key != notification_hash:
                require(a[key] == b[key], 'Selected durable RAW changed')
    old_map, new_map = ({v['key']: v for v in items} for items in (old_values, values))
    with closing(sqlite3.connect((before_directory / 'database.db').resolve().as_uri() + '?mode=ro&immutable=1', uri=True)) as db:
        planned = db.execute('SELECT endEpochMillis FROM shifts WHERE id=?', (KNOWN[6:],)).fetchall()
        actual = db.execute('SELECT actualEndEpochMillis FROM shift_actual_records WHERE shiftId=?', (KNOWN[6:],)).fetchall()
        require(len(planned) == 1 and len(actual) <= 1, 'Dismissal source missing/ambiguous')
        effective_end = (actual or planned)[0][0]
        require(type(effective_end) is int, 'Exact effective end required')
    delta = notification_delta((before_directory / 'private' / RAW).read_bytes(), (after_directory / 'private' / RAW).read_bytes(),
                               old_map[DISMISSED]['values'], new_map[DISMISSED]['values'], receipt['captured_at_epoch_millis'], effective_end)
    try:
        strict_result = strict.compare(before, after)
    except ValueError as error:
        require(str(error) == 'Logical timeline, selected file, association or portable preference changed', 'Unexpected strict rejection')
        strict_result = {'verdict': 'STRICT_COMPARISON_REJECTED_PRESERVED', 'reason': str(error)}
    return {'verdict': 'THIS_CASE_PRESERVED_UNDER_APPROVED_EXPIRY_CRITERION', 'criterion': transport.EXPIRY_CRITERION,
            'strict_comparison': strict_result, 'notification_delta': delta, 'durable_portable_preferences_equal': 16,
            'other_selected_raw_equal': 6, 'original_tables': len(before['tables']), 'original_rows': sum(v['count'] for v in before['tables'].values()),
            'source_snapshot_sha256': strict.digest(strict.canonical(before)), 'target_snapshot_sha256': strict.digest(strict.canonical(after)),
            'technical_annex_hashes': TECHNICAL, 'platform_runtime_files': platform, 'actual_gateway_export': True,
            'limit': 'This physical fixture contained an expired dismissal. No new upgrade/live-dismissal case, restore, performance or distribution is established. No historic writer event is traced.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('before', type=Path)
    parser.add_argument('after', type=Path)
    parser.add_argument('--technical-annex', type=Path, required=True)
    parser.add_argument('--platform-annex', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    require(not args.output.exists(), 'Output must be new')
    result = compare(args.before, args.after, args.technical_annex, args.platform_annex)
    with args.output.open('x', encoding='utf-8') as f:
        json.dump(result, f, indent=2)
        f.write('\n')
    print(json.dumps({'verdict': result['verdict'], 'original_rows': result['original_rows']}))


if __name__ == '__main__':
    main()
