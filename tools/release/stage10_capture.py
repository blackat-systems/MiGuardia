"""Ingest an explicitly supplied SYNTHETIC Stage10 Android capture. Never ADB.

Checks transport/declared receipt identity and keeps every selected RAW byte.
The unchanged V6/V8 comparator remains the data-conservation authority. A host
receipt does not attest an Android run; pair it with the actual instrument log.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import sqlite3
import stat
import tempfile
import uuid
import zipfile

import upgrade_v6_v8 as comparison

QA = 'com.blackatsystems.miguardia.qa'
CERT = 'b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617'
APKS = {6: '1c0d0be8f8f66a2dc65e94a2c8d022836984f210cce8f3b822cb5d555f73148a',
        8: '2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3'}
SOURCE = '8def0ccf622d3aa7e6ea2a855f071aae28505c695ad8d803888ef7f40a226ba5'
PHOTO = '30000000-0000-4000-8000-000000000014.png'
PERSISTENT = {'private/files/schedule_photos/'+PHOTO,
    'private/shared_prefs/miguardia_display_preferences.xml', 'private/no_backup/stage10-fixture-v1.txt'} | {
    f'private/files/datastore/{name}.preferences_pb.preferences_pb'
    for name in ('guard_profile','summary_preferences','notification_preferences','weather_preferences')}
SELECTED = PERSISTENT | {'database.db', 'portable-preferences.json'}
HASH = re.compile(r'[a-f0-9]{64}')
EXPIRY_OPERATION = 'capture-v8-expiry-v1'
EXPIRY_CRITERION = 'STAGE10_EXPIRED_NOTIFICATION_PRUNING_20261022_V1'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def linked(path):
    return path.is_symlink() or bool(getattr(path.lstat(), 'st_file_attributes', 0) & 0x400)


def validate_receipt(value):
    if not isinstance(value, dict) or value.get('format') != 'stage10-android-capture/1':
        raise ValueError('Unknown capture receipt')
    version = value.get('room_version')
    if type(version) is not int or version not in APKS:
        raise ValueError('Explicit V6/V8 capture required')
    if value.get('target_package') != QA or value.get('test_package') != QA+'.test' or value.get('certificate_sha256') != CERT:
        raise ValueError('Unexpected QA package/certificate declaration')
    if value.get('target_apk_sha256') != APKS[version] or not HASH.fullmatch(str(value.get('runner_apk_sha256'))):
        raise ValueError('Wrong target/runner artifact declaration')
    if value.get('source_fixture_sha256') != SOURCE or value.get('fixture_day') != '2026-10-21':
        raise ValueError('Wrong synthetic fixture declaration')
    if value.get('operation') not in (('seed-v6','capture-v6') if version == 6 else ('capture-v8', EXPIRY_OPERATION)):
        raise ValueError('Operation/version mismatch')
    if value.get('operation') == EXPIRY_OPERATION:
        if value.get('criterion') != EXPIRY_CRITERION or type(value.get('captured_at_epoch_millis')) is not int or value['captured_at_epoch_millis'] <= 0:
            raise ValueError('Explicit approved expiry criterion and observed capture time required')
    if not isinstance(value.get('capture_id'), str) or str(uuid.UUID(value['capture_id'])) != value['capture_id']:
        raise ValueError('Canonical capture UUID required')
    if value.get('neutral_application') is not True or value.get('database_closed_before_copy') is not True:
        raise ValueError('Unmet application/closed-copy declaration')
    if value.get('single_target_process_verified') is not True or value.get('export_owner') != 'TARGET_QA_CACHE':
        raise ValueError('Unmet single-target process/export ownership declaration')
    checkpoint = value.get('wal_checkpoint')
    if not isinstance(checkpoint, list) or len(checkpoint) != 3 or any(type(n) is not int for n in checkpoint) or checkpoint[0] != 0:
        raise ValueError('Missing successful WAL checkpoint')
    if value.get('portable_source') != 'TARGET_GATEWAY_AND_EXPLICIT_PERSISTED_KEYS':
        raise ValueError('Actual persisted portable capture required')
    if not isinstance(value.get('files'), dict) or set(value['files']) != SELECTED:
        raise ValueError('Selected RAW file list differs from the predeclared contract')
    for item in value['files'].values():
        if not isinstance(item, dict) or set(item) != {'sha256','bytes'} or not HASH.fullmatch(str(item['sha256'])) or type(item['bytes']) is not int or not 0 <= item['bytes'] <= 32*1024*1024:
            raise ValueError('Invalid selected file identity')
    return version


def ingest(bundle, output):
    bundle, output = Path(bundle), Path(output)
    if output.exists() or not output.parent.is_dir() or linked(output.parent):
        raise ValueError('Explicit new output under a regular existing parent required')
    if linked(bundle) or not bundle.is_file() or bundle.stat().st_size > 64*1024*1024:
        raise ValueError('Explicit bounded regular capture bundle required')
    original = bundle.read_bytes()
    with zipfile.ZipFile(bundle) as archive:
        infos = archive.infolist()
        if len(infos) != len(SELECTED)+1 or len({i.filename for i in infos}) != len(infos):
            raise ValueError('Duplicate/missing/extra capture members')
        for item in infos:
            path = PurePosixPath(item.filename)
            if '\\' in item.filename or path.is_absolute() or '..' in path.parts or path.as_posix() != item.filename or item.is_dir() or stat.S_ISLNK(item.external_attr >> 16) or item.flag_bits & 1 or item.compress_type not in (zipfile.ZIP_STORED,zipfile.ZIP_DEFLATED):
                raise ValueError('Unsafe capture archive entry')
        if {i.filename for i in infos} != SELECTED | {'receipt.json'} or sum(i.file_size for i in infos) > 64*1024*1024:
            raise ValueError('Capture entries/expanded size exceed fixed selection')
        receipt = json.loads(archive.read('receipt.json'))
        version = validate_receipt(receipt)
        contents = {name: archive.read(name) for name in SELECTED}
        for name, data in contents.items():
            if sha(data) != receipt['files'][name]['sha256'] or len(data) != receipt['files'][name]['bytes']:
                raise ValueError('RAW bytes differ from Android receipt')
        comparison.portable_preferences(json.loads(contents['portable-preferences.json']))
    if bundle.read_bytes() != original:
        raise ValueError('Bundle changed during ingestion')
    # Validate in an owned temporary sibling before publishing a fresh result.
    with tempfile.TemporaryDirectory(prefix='stage10-capture-', dir=output.parent) as temporary:
        root = Path(temporary)
        assert root.resolve().is_relative_to(output.parent.resolve())
        for name, data in contents.items():
            target = root/name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        snapshot = comparison.capture(root/'database.db', root/'private', root/'portable-preferences.json', version)
        (root/'snapshot.json').write_text(json.dumps(snapshot, indent=2)+'\n', encoding='utf-8')
        bound = {'verdict':'CAPTURE_TRANSPORT_VALIDATED_HOST_ONLY', 'bundle_sha256':sha(original),
                 'android_receipt':receipt, 'snapshot_canonical_sha256':comparison.digest(comparison.canonical(snapshot)),
                 'raw_selected_files':receipt['files'],
                 'limit':'Android identity/run not attested by host; bind instrument success and installed artifact receipts independently. Compare ALL selected raw files and actual portable values with upgrade_v6_v8.py.'}
        (root/'capture-receipt.json').write_text(json.dumps(bound,indent=2)+'\n',encoding='utf-8')
        shutil.copytree(root, output)
    return bound


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--fictitious-data', action='store_true', required=True)
    parser.add_argument('--inactive-consistent-copy', action='store_true', required=True)
    args = parser.parse_args()
    try:
        result = ingest(args.bundle, args.output)
    except (ValueError, OSError, zipfile.BadZipFile, sqlite3.Error) as error:
        parser.exit(2, f'Capture rejected: {type(error).__name__}\n')
    print(json.dumps({'verdict':result['verdict'], 'bundle_sha256':result['bundle_sha256']}))


if __name__ == '__main__':
    main()
