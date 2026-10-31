"""Offline validation of the separate, explicitly synthetic Stage10 AVD profile."""
import argparse
from contextlib import closing
import json
from pathlib import Path, PurePosixPath
import re
import sqlite3
import stat
import struct
import uuid
import zipfile

import stage10_capture as historical
import stage10_runtime_compare as wire
import upgrade_v6_v8 as strict

FORMAT = 'stage10-emulator-capture/1'
CONSENT = 'STAGE10_ONE_DEDICATED_AVD_20261022'
CONSENT_R2 = 'STAGE10_SECOND_DEDICATED_AVD_20261022_R2'
SHORT_WINDOWS = {CONSENT: 900000, CONSENT_R2: 1800000}
SHORT = 'shift:30000000-0000-4000-8000-000000000023'
LONG = 'shift:30000000-0000-4000-8000-000000000024'
ONBOARDING = 'private/files/datastore/onboarding.preferences_pb'
MARKER = 'private/no_backup/stage10-emulator-fixture-v1.txt'
BASE_RAW = (historical.PERSISTENT - {'private/no_backup/stage10-fixture-v1.txt'}) | {MARKER}
ANNEX = {'files/profileInstalled', 'shared_prefs/notification_deferred_actions.xml', wire.PLATFORM_PATH}
LIMIT = 'One new synthetic AVD installation only; no Samsung/OEM, restore, release or historic continuity.'
require = wire.require


def scenario(value):
    require(set(value) == {'id', 'started_at', 'short_end', 'long_end', 'consent'}, 'Exact scenario declaration required')
    require(value['consent'] in SHORT_WINDOWS and str(uuid.UUID(value['id'])) == value['id'], 'Unknown scenario/consent')
    require(all(type(value[k]) is int for k in ('started_at', 'short_end', 'long_end')), 'Exact integer clock required')
    start = value['started_at']
    require(start > 0 and start % 60000 == 0, 'Minute-aligned fixture clock required')
    require(value['short_end'] == start + SHORT_WINDOWS[value['consent']] and value['long_end'] == start + 172800000, 'Fixture clock window differs')
    return value


def annex(path, data, updated, baseline=None):
    require(path in ANNEX, 'Unknown technical annex')
    if path == 'files/profileInstalled':
        require(len(data) == 24, 'Profile cache size differs')
        version, result, update, size = struct.unpack('>iiqq', data)
        require(version == 1 and result in {0, 1, 2, 3, 65536, 131072, 196608, 262144, 327680}, 'Unknown profile cache schema/result')
        require(type(updated) is int and updated > 0, 'Missing current package update time')
        inherited = baseline is not None and update == baseline['last_update_time'] and updated > update and strict.digest(data) == baseline['profile_sha256']
        require(type(updated) is int and updated > 0 and (update == updated or inherited) and size >= 0, 'Profile cache install identity differs')
    elif path == 'shared_prefs/notification_deferred_actions.xml':
        require(strict.digest(data) == '3325d2a819fdd8062c2cdc48a09b995c9b012915bcdf88b1cf9742a7f057c793', 'Deferred queue is not exact empty XML')
    else:
        require(len(data) <= 128 and re.fullmatch(rb'<\?xml version=\'1.0\' encoding=\'utf-8\' standalone=\'yes\' \?>\n<map>\n    <int name="IDSCount" value="[1-9][0-9]{0,8}" />\n</map>\n', data) is not None, 'Unknown platform counter layout')


def validate_receipt(r, spec, runner_hash):
    scenario(spec)
    room, operation = r.get('room_version'), r.get('operation')
    require(type(room) is int and room in (6, 8), 'Room version differs')
    require(operation in (('seed-v6', 'capture-v6') if room == 6 else ('capture-v8',)), 'Operation/version differs')
    require(r.get('format') == FORMAT and r.get('criterion') == spec['consent'], 'Wrong capture profile')
    require(r.get('scenario_id') == spec['id'] and all(r.get(k) == spec[k] for k in ('started_at', 'short_end', 'long_end')), 'Scenario changed')
    require(r.get('target_package') == historical.QA and r.get('test_package') == historical.QA + '.test', 'Wrong package')
    require(r.get('certificate_sha256') == historical.CERT and r.get('target_apk_sha256') == historical.APKS[room], 'Wrong signer/artifact')
    require(historical.HASH.fullmatch(runner_hash) and r.get('runner_apk_sha256') == runner_hash, 'Wrong runner')
    require(str(uuid.UUID(r['capture_id'])) == r['capture_id'], 'Noncanonical capture ID')
    require(r.get('neutral_application') is True and r.get('database_closed_before_copy') is True and r.get('single_target_process_verified') is True, 'Capture process/copy contract differs')
    require(r.get('export_owner') == 'TARGET_QA_CACHE' and r.get('portable_source') == 'TARGET_GATEWAY_AND_EXPLICIT_PERSISTED_KEYS', 'Actual persisted capture required')
    require(r.get('api') == 36 and isinstance(r.get('fingerprint'), str) and r['fingerprint'], 'Missing Android identity')
    require(r.get('onboarding_completed') is (operation != 'seed-v6'), 'Wrong guide declaration')
    checkpoint = r.get('wal_checkpoint')
    require(isinstance(checkpoint, list) and len(checkpoint) == 3 and all(type(n) is int for n in checkpoint) and checkpoint[0] == 0, 'Checkpoint incomplete')
    now = r.get('captured_at_epoch_millis')
    require(type(now) is int and spec['started_at'] <= now < spec['long_end'], 'Capture outside live window')
    if operation == 'seed-v6':
        require(now < spec['started_at'] + 60000, 'Seed clock missed')
    elif room == 6:
        require(now < spec['short_end'] - 60000, 'Baseline too late')
    else:
        require(now >= spec['short_end'], 'Upgrade capture precedes expiry')
    require(all(type(r.get(k)) is int and r[k] > 0 for k in ('first_install_time', 'last_update_time')), 'Missing installed times')
    return room, operation


def profile_baseline(directory, spec, runner_hash):
    """Bind an inherited cache to the original archived post-guide V6 baseline."""
    directory = Path(directory)
    require(isinstance(runner_hash, str) and historical.HASH.fullmatch(runner_hash), 'Explicit original runner identity required')
    bound = json.loads((directory / 'capture.json').read_text(encoding='utf-8'))
    r = bound['receipt']
    require(spec['consent'] == CONSENT_R2 and validate_receipt(r, spec, runner_hash) == (6, 'capture-v6'), 'Original R2 baseline required')
    require('profile_baseline' not in r, 'A recovery cannot replace the original baseline')
    profile_name = 'annex/files/profileInstalled'
    require(profile_name in r['files'], 'Baseline profile was not archived')
    require(r['technical_files_not_portable'].get('files/profileInstalled') == r['files'][profile_name]['sha256'], 'Archived baseline profile declaration differs')
    bundle = directory.with_name(directory.name + '.zip')
    require(bundle.is_file() and not historical.linked(bundle) and bundle.stat().st_size <= 64 * 1024 * 1024 and strict.digest(bundle.read_bytes()) == bound['bundle_sha256'], 'Baseline archive differs')
    with zipfile.ZipFile(bundle) as archive:
        require(len(archive.namelist()) == len(set(archive.namelist())) and set(archive.namelist()) == set(r['files']) | {'receipt.json'}, 'Baseline archive body set differs')
        require(json.loads(archive.read('receipt.json')) == r, 'Baseline receipt differs from archive')
        for name, identity in r['files'].items():
            relative = PurePosixPath(name)
            require(not relative.is_absolute() and '..' not in relative.parts and relative.as_posix() == name and '\\' not in name, 'Unsafe baseline path')
            data = (directory / name).read_bytes()
            require(identity == {'bytes': len(data), 'sha256': strict.digest(data)} and archive.read(name) == data, 'Baseline bytes differ')
    profile = (directory / profile_name).read_bytes()
    annex('files/profileInstalled', profile, r['last_update_time'])
    require(0 < r['first_install_time'] <= r['last_update_time'] <= r['captured_at_epoch_millis'], 'Baseline package chronology differs')
    return {'bundle_sha256': bound['bundle_sha256'], 'profile_sha256': strict.digest(profile),
        'first_install_time': r['first_install_time'], 'last_update_time': r['last_update_time'],
        'captured_at_epoch_millis': r['captured_at_epoch_millis']}


def require_profile_binding(receipt, spec, baseline):
    if baseline is None:
        require('profile_baseline' not in receipt, 'Unverified profile baseline declaration')
        return
    require(receipt.get('profile_baseline') == baseline and receipt['operation'] == 'capture-v8' and spec['consent'] == CONSENT_R2, 'Profile baseline binding differs')
    require(receipt['first_install_time'] == baseline['first_install_time'] and
        baseline['last_update_time'] <= baseline['captured_at_epoch_millis'] < spec['short_end'] - 60000 and
        spec['short_end'] <= receipt['last_update_time'] <= receipt['captured_at_epoch_millis'], 'Profile binding installation/chronology differs')


def ingest(bundle, output, spec, runner_hash, *, baseline=None, baseline_runner_hash=None):
    require((baseline is None) == (baseline_runner_hash is None), 'Baseline directory and original runner must be supplied together')
    bundle, output = Path(bundle), Path(output)
    require(not output.exists() and output.parent.is_dir() and not historical.linked(output.parent), 'New regular destination required')
    require(bundle.is_file() and not historical.linked(bundle) and bundle.stat().st_size <= 64 * 1024 * 1024, 'Bounded regular capture required')
    original = bundle.read_bytes()
    with zipfile.ZipFile(bundle) as archive:
        infos = archive.infolist()
        require(len(infos) <= 18 and len({i.filename for i in infos}) == len(infos), 'Duplicate/too many entries')
        require(sum(i.file_size for i in infos) <= 64 * 1024 * 1024, 'Expanded capture too large')
        for item in infos:
            path = PurePosixPath(item.filename)
            require('\\' not in item.filename and not path.is_absolute() and '..' not in path.parts and path.as_posix() == item.filename and not item.is_dir() and not stat.S_ISLNK(item.external_attr >> 16) and not item.flag_bits & 1 and item.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED), 'Unsafe capture entry')
        r = json.loads(archive.read('receipt.json'))
        room, operation = validate_receipt(r, spec, runner_hash)
        binding = profile_baseline(baseline, spec, baseline_runner_hash) if baseline is not None else None
        require_profile_binding(r, spec, binding)
        raw = BASE_RAW | ({ONBOARDING} if operation != 'seed-v6' else set())
        annex_names = {name for name in r['files'] if name.startswith('annex/')}
        require(all(name.removeprefix('annex/') in ANNEX for name in annex_names), 'Unexpected annex path')
        selected = raw | {'database.db', 'portable-preferences.json', 'fixture-source.bin'} | annex_names
        require(set(r['files']) == selected and {i.filename for i in infos} == selected | {'receipt.json'}, 'Missing/extra selected file')
        contents = {name: archive.read(name) for name in selected}
        for name, data in contents.items():
            require(r['files'][name] == {'bytes': len(data), 'sha256': strict.digest(data)}, 'RAW receipt differs')
        require(strict.digest(contents['fixture-source.bin']) == r['source_fixture_sha256'], 'Fixture digest differs')
        expected_marker = '\n'.join(map(str, (spec['consent'], spec['id'], spec['started_at'], spec['short_end'], spec['long_end'], historical.SOURCE,
            '906f8a853793dcdedccd16566c8b17af2b73836bc05daa695dc97e2504152d90', '5aa8334f217a14c229b9ec7b2c6ae02ae39c5ab94328492363e6458cc14e6e26'))) + '\n'
        require(contents[MARKER] == expected_marker.encode(), 'Fixture marker differs')
        if operation != 'seed-v6':
            values = wire.preference_map(contents[ONBOARDING])
            require(set(values) == {'completed_version'} and wire.fields(values['completed_version']) == [(3, 0, 1)], 'Guide completion is not persisted version 1')
        declared_annex = {**r['technical_files_not_portable'], **r['platform_runtime_files']}
        require(set(declared_annex) == {n.removeprefix('annex/') for n in annex_names}, 'Annex declaration differs')
        for name in annex_names:
            path = name.removeprefix('annex/')
            annex(path, contents[name], r['last_update_time'], binding)
            require(declared_annex[path] == strict.digest(contents[name]), 'Annex digest differs')
        strict.portable_preferences(json.loads(contents['portable-preferences.json']))
    require(bundle.read_bytes() == original, 'Bundle changed during read')
    # New owned output; failures remain available, never erased or retried in place.
    output.mkdir()
    for name, data in contents.items():
        target = output / name
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open('xb') as f:
            f.write(data)
    snapshot = strict.capture(output / 'database.db', output / 'private', output / 'portable-preferences.json', room)
    require(len(snapshot['tables']) == (27 if room == 6 else 29), 'Table coverage differs')
    require(sum(v['count'] for k, v in snapshot['tables'].items() if k not in strict.NEW_TABLES) == 40, 'Fixture row coverage differs')
    with closing(sqlite3.connect((output / 'database.db').resolve().as_uri() + '?mode=ro&immutable=1', uri=True)) as db:
        for key, end in ((SHORT, spec['short_end']), (LONG, spec['long_end'])):
            require(db.execute('SELECT endEpochMillis FROM shifts WHERE id=?', (key[6:],)).fetchall() == [(end,)], 'Effective source end differs')
            require(not db.execute('SELECT 1 FROM shift_actual_records WHERE shiftId=?', (key[6:],)).fetchall(), 'Unexpected actual-end override')
    bound = {'receipt': r, 'bundle_sha256': strict.digest(original), 'snapshot': snapshot, 'limit': LIMIT}
    (output / 'capture.json').write_text(json.dumps(bound, indent=2) + '\n', encoding='utf-8')
    return bound


def notification_delta(before, after, old_portable, new_portable, spec, now):
    a, b = wire.preference_map(before), wire.preference_map(after)
    require(set(a) == set(b) == wire.BASE_KEYS | set(wire.OPERATIONAL), 'Unknown/missing notification key after normal opening')
    for key in wire.BASE_KEYS - {'dismissed_shift_ids'}:
        require(a[key] == b[key], 'Durable notification entry changed')
    for key, value in wire.OPERATIONAL.items():
        require(a[key] == b[key] == value, 'Operational notification value differs from empty/default')
    previous, current = wire.string_set(a['dismissed_shift_ids']), wire.string_set(b['dismissed_shift_ids'])
    require(old_portable == [SHORT, LONG] and new_portable in ([SHORT, LONG], [LONG]), 'Portable dismissal order/identity differs')
    require(previous == {SHORT, LONG} and current in ({SHORT, LONG}, {LONG}), 'Live/unknown dismissal changed')
    require(previous == set(old_portable) and current == set(new_portable), 'RAW/portable dismissal disagreement')
    require(now < spec['long_end'] and (current == previous or now >= spec['short_end']), 'Invalid expiry evidence')
    return sorted(previous - current)


def compare(before_directory, after_directory, spec, runner_hash, final=False, *, after_runner_hash=None):
    a_dir, b_dir = Path(before_directory), Path(after_directory)
    a, b = (json.loads((p / 'capture.json').read_text()) for p in (a_dir, b_dir))
    ar, br = a['receipt'], b['receipt']
    require(validate_receipt(ar, spec, runner_hash) == (6, 'capture-v6'), 'Real post-guide V6 baseline required')
    require(validate_receipt(br, spec, after_runner_hash or runner_hash) == (8, 'capture-v8'), 'V8 capture required')
    binding = profile_baseline(a_dir, spec, runner_hash) if 'profile_baseline' in br else None
    require_profile_binding(br, spec, binding)
    require(after_runner_hash is None or after_runner_hash == runner_hash or binding is not None, 'Changed reader requires original baseline binding')
    require(ar['captured_at_epoch_millis'] < spec['short_end'] <= br['captured_at_epoch_millis'] < spec['long_end'], 'Capture chronology differs')
    require(ar['first_install_time'] == br['first_install_time'] and ar['last_update_time'] <= br['last_update_time'], 'Installation continuity differs')
    require(ar['last_update_time'] <= ar['captured_at_epoch_millis'] < spec['short_end'] <= br['last_update_time'] <= br['captured_at_epoch_millis'], 'Android package update preceded expiry or capture chronology differs')
    require(ar['source_fixture_sha256'] == br['source_fixture_sha256'] and ar['fingerprint'] == br['fingerprint'], 'Fixture/environment changed')
    for directory, bound in ((a_dir, a), (b_dir, b)):
        r = bound['receipt']
        for name, identity in r['files'].items():
            data = (directory / name).read_bytes()
            require(identity == {'bytes': len(data), 'sha256': strict.digest(data)}, 'Capture bytes changed after ingestion')
            if name.startswith('annex/'):
                annex(name.removeprefix('annex/'), data, r['last_update_time'], binding if directory == b_dir else None)
        fresh = strict.capture(directory / 'database.db', directory / 'private', directory / 'portable-preferences.json', r['room_version'])
        require(fresh == bound['snapshot'], 'Snapshot changed')
    old, new = a['snapshot'], b['snapshot']
    require(old['tables'] == {k: new['tables'][k] for k in old['tables']}, 'Original typed row/association changed')
    for key in ('timeline_sha256', 'photo_associations', 'historical_references'):
        require(old[key] == new[key], 'Durable database/photo identity changed')
    old_values, new_values = ({x['key']: x for x in json.loads((p / 'portable-preferences.json').read_text())} for p in (a_dir, b_dir))
    for key in old_values:
        if key != wire.DISMISSED:
            require(old_values[key] == new_values[key], 'Durable preference changed')
    for name in BASE_RAW | {ONBOARDING}:
        if name != 'private/' + wire.RAW:
            require((a_dir / name).read_bytes() == (b_dir / name).read_bytes(), 'Durable RAW changed')
    removed = notification_delta((a_dir / 'private' / wire.RAW).read_bytes(), (b_dir / 'private' / wire.RAW).read_bytes(),
        old_values[wire.DISMISSED]['values'], new_values[wire.DISMISSED]['values'], spec, br['captured_at_epoch_millis'])
    if final:
        require(removed == [SHORT], 'Final opening did not demonstrate expired cleanup')
    return {'verdict': 'CAPTURE_PRESERVED_WITH_LIVE_DISMISSAL', 'original_tables': 27, 'original_rows': 40,
        'durable_preferences_equal': 16, 'other_durable_raw_equal': 7, 'removed': removed,
        'live_preserved': LONG, 'final_capture': final, 'limit': LIMIT}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scenario', type=Path, required=True)
    parser.add_argument('--runner-sha256', required=True)
    sub = parser.add_subparsers(dest='command', required=True)
    ingest_parser = sub.add_parser('ingest')
    ingest_parser.add_argument('bundle', type=Path); ingest_parser.add_argument('output', type=Path)
    ingest_parser.add_argument('--baseline', type=Path); ingest_parser.add_argument('--baseline-runner-sha256')
    compare_parser = sub.add_parser('compare')
    compare_parser.add_argument('before', type=Path); compare_parser.add_argument('after', type=Path)
    compare_parser.add_argument('--final', action='store_true'); compare_parser.add_argument('--output', type=Path, required=True)
    compare_parser.add_argument('--after-runner-sha256')
    args = parser.parse_args()
    spec = scenario(json.loads(args.scenario.read_text()))
    if args.command == 'ingest':
        result = ingest(args.bundle, args.output, spec, args.runner_sha256, baseline=args.baseline, baseline_runner_hash=args.baseline_runner_sha256)
        print(json.dumps({'captured': result['receipt']['capture_id']}))
    else:
        result = compare(args.before, args.after, spec, args.runner_sha256, args.final, after_runner_hash=args.after_runner_sha256)
        with args.output.open('x', encoding='utf-8') as f:
            json.dump(result, f, indent=2)
        print(json.dumps(result))


if __name__ == '__main__':
    main()
