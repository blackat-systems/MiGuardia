"""Stage11 only: reuse the protected R2, explicit transport, no product installation."""
import argparse
import datetime
import hashlib
import io
import json
import os
from pathlib import Path
import re
import socket
import sqlite3
import subprocess
import sys
import struct
import tarfile
import tempfile
import time
import uuid
import zipfile

import stage10_emulator as r2

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import qa

QA_HASH = '2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3'
OLD_RUNNER = 'f8deb223ca743ee89ac59ec685003ffbbefc9d2213886738264209fc9f4e5230'
RUNNER = r2.QA + '.test/com.blackatsystems.miguardia.backup.Stage11RestoreRunner'
CONSENT = 'STAGE11_SYNTHETIC_R2_20261023'
PROJECT = Path(__file__).resolve().parents[2]
APPROVED_RUNNERS = {
    'compiled-04': 'c47466e2607766f8a54263c75ee62dc15baa6618029b234d1362ba709de4b030',
    'compiled-05': '1e3beb2854c0f625f8cbb984890de60303291bc9643ae04076f7e8b3449c688b',
    'compiled-06': '3db95ad7fcfc8e4d1a2966d4b53409d3e2b4afc5f2029b18dfdd675436225285',
}
CERTIFICATE = 'b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617'
APPROVED_QAS = {'r1-candidate/Stage11-R1-QA.apk': '494a131086380424e45db3fd2af5707ed1be95b6e56397003bf22beec218669a'}
CLASSES = [
    'com.blackatsystems.miguardia.backup.Stage11RestoreInstrumentedTest',
    'com.blackatsystems.miguardia.backup.BackupRecoveryInstrumentedTest',
    'com.blackatsystems.miguardia.backup.BackupPhotoFilesInstrumentedTest',
    'com.blackatsystems.miguardia.backup.BackupPhotoMutationGateInstrumentedTest',
    'com.blackatsystems.miguardia.backup.PortablePreferencesGatewayInstrumentedTest',
    'com.blackatsystems.miguardia.Stage4NoteRestoreConcurrencyTest',
]
require = r2.require
save = r2.save


def focus(text):
    current = re.findall(r'mCurrentFocus=Window\{[^\n]+?\s(?:u\d+\s+)?([a-zA-Z0-9_.]+)/', text)
    require(len(current) == 1 and current[0] in {r2.QA, 'com.android.launcher3'}, 'Foreign or ambiguous focus; no UI action authorized')


def protocol(text, returncode, selector, methods):
    result = qa.parse_instrumentation(text, returncode, selector, methods)
    isolation = re.findall(r'INSTRUMENTATION_RESULT: stage11Isolation=(\S+)', text)
    if isolation != ['PASS']:
        result['verdict'] = 'FAILED'
        result['signals'].append('stage11-isolation-not-attested')
    names = re.findall(r'^INSTRUMENTATION_RESULT: stage11Bundle=([^\r\n]*)\r?$', text, re.M)
    hashes = re.findall(r'^INSTRUMENTATION_RESULT: stage11BundleSha256=([^\r\n]*)\r?$', text, re.M)
    valid = len(names) == len(hashes) == 1 and re.fullmatch(r'stage11-[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}\.zip', names[0]) and re.fullmatch(r'[a-f0-9]{64}', hashes[0])
    if not valid:
        result['verdict'] = 'FAILED'
        result['signals'].append('missing-duplicate-or-malformed-bundle-proof')
    else:
        result.update(bundle=names[0], bundle_sha256=hashes[0])
        if result['verdict'] == 'PASSED':
            result['verdict'] = 'PENDING_EVIDENCE'
    return result


def attest_transport(result, data):
    require(hashlib.sha256(data).hexdigest() == result['bundle_sha256'], 'Evidence transport changed')
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        require(archive.testzip() is None, 'Damaged Android evidence ZIP')
        names = archive.namelist()
        require(len(names) == len(set(names)) and all(not n.startswith('/') and '..' not in Path(n).parts for n in names), 'Unsafe/duplicate evidence entries')
        isolation = json.loads(archive.read('isolation.json'))
        require(isolation['neutral_application'] is True and isolation['isolation'] == 'PASS', 'Missing neutral isolation evidence')
        require(isolation['protected_before'] == isolation['protected_after'], 'Protected evidence differs')
        require(result['bundle'] == 'stage11-' + str(uuid.UUID(isolation['root_id'])) + '.zip', 'Evidence root identity differs')
    result['evidence_transport_verified'] = True
    if result['verdict'] == 'PENDING_EVIDENCE':
        result['verdict'] = 'PASSED'
    return result


def validate_artifact(runner, approved_paths, package, target=None):
    runner = Path(runner).resolve()
    root = PROJECT / '.local-evidence/stage11/restore-android-20261023'
    approved = [(root / name, digest) for name, digest in approved_paths.items()]
    matches = [digest for path, digest in approved if runner == path.resolve()]
    require(len(matches) == 1 and runner.is_file(), 'Runner path is not an approved checkpoint')
    require(r2.digest(runner) == matches[0], 'Approved runner hash differs')
    signer = subprocess.run([str(r2.JBR / 'bin/java.exe'), '-jar', str(r2.SDK / 'build-tools/36.0.0/lib/apksigner.jar'),
        'verify', '--print-certs', str(runner)], capture_output=True, timeout=30, creationflags=subprocess.CREATE_NO_WINDOW)
    certs = re.findall(rb'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})', signer.stdout)
    require(signer.returncode == 0 and certs == [CERTIFICATE.encode()], 'Approved runner certificate differs')
    manifest = subprocess.run([str(r2.SDK / 'build-tools/36.0.0/aapt2.exe'), 'dump', 'xmltree', '--file', 'AndroidManifest.xml', str(runner)],
        capture_output=True, timeout=30, creationflags=subprocess.CREATE_NO_WINDOW)
    xml = manifest.stdout.decode('utf8')
    require(manifest.returncode == 0 and re.findall(r'^\s*A: package="([^"]+)"', xml, re.M) == [package], 'Approved package differs')
    if target is not None:
        require(re.findall(r'android:targetPackage[^\n]*="([^"]+)" \(Raw:', xml) == [target], 'Runner target differs')
        require('="com.blackatsystems.miguardia.backup.Stage11RestoreRunner"' in xml, 'Neutral runner component missing')
    else:
        require('android:targetPackage' not in xml, 'Product candidate must not be instrumentation')
    return matches[0]


def validate_runner(runner):
    return validate_artifact(runner, {name+'/Stage11-Restore-Runner.apk':digest for name,digest in APPROVED_RUNNERS.items()}, r2.QA+'.test', r2.QA)


def current_qa(output):
    path = Path(output) / 'qa-r1-installed.json'
    if not path.exists(): return QA_HASH
    digest = json.loads(path.read_text())['qa_sha256']
    require(digest in APPROVED_QAS.values(), 'Unapproved QA receipt')
    return digest


def profile_update_only(before, after, package_metadata):
    differences = {name for name in set(before)|set(after) if before.get(name) != after.get(name)}
    require(differences <= {'files/profileInstalled'}, 'Durable data changed outside the technical profile cache')
    require(semantic_database(before) == semantic_database(after), 'Received semantic data changed')
    if not differences: return {'profile_cache_changed':False,'all_other_files_identical':True}
    old, new = before['files/profileInstalled'], after['files/profileInstalled']
    require(hashlib.sha256(old).hexdigest() == '5952d49fd15175d47dec38481a46e32555ef00c2191a38d073b69731ce8feb41' and
            hashlib.sha256(new).hexdigest() == '42d57c4f8bddc57b8c89be6f64c77f81c2c25d425fe8748eb354a373856dd261',
            'Profile bytes are not the exact original and observed R1 update')
    require(len(old) == len(new) == 24 and old[:8] == new[:8] and old[16:] == new[16:], 'Profile cache schema/result/size changed')
    a, b = struct.unpack('>iiqq',old), struct.unpack('>iiqq',new)
    require(a[:2] == b[:2] == (1,327680) and a[3] == b[3] == 0 and b[2] > a[2], 'Unexpected profile cache transition')
    values = re.findall(r'lastUpdateTime=([^\r\n]+)',package_metadata)
    require(len(values) == 1, 'Unique observed package update required')
    second = int(datetime.datetime.strptime(values[0],'%Y-%m-%d %H:%M:%S').replace(tzinfo=datetime.timezone.utc).timestamp())
    require(b[2]//1000 == second, 'Profile timestamp is not the observed QA package update')
    return {'profile_cache_changed':True,'all_other_files_identical':True,'schema_result_size_unchanged':True,
        'old_timestamp':a[2],'new_timestamp':b[2],'package_update_utc_second':values[0],
        'old_sha256':hashlib.sha256(old).hexdigest(),'new_sha256':hashlib.sha256(new).hexdigest(),
        'package_metadata_precision':'seconds; cache carries milliseconds within that observed second'}


def current_runner(output):
    records = []
    for path in Path(output).glob('runner-installed*.json'):
        record = json.loads(path.read_text())
        records.append((record.get('installation_index', 1), record))
    require(bool(records), 'No approved runner installed')
    return max(records, key=lambda item:item[0])[1]


class Device(r2.Device):
    def guard(self):
        super().guard()
        focus(self.command(['shell', 'dumpsys', 'window']).decode())


def open_device(output, runner_hash):
    output = Path(output)
    owner = json.loads((output / 'avd-owner.json').read_text())
    d = Device(output, owner, runner_hash)
    old = list(output.glob('adb-*-start.json'))
    d.sequence = max([int(p.name.split('-')[1]) for p in old], default=0)
    return d


def launch(output):
    output = Path(output)
    require(not (output / 'avd-owner.json').exists(), 'Already launched; no automatic replay')
    manifest = json.loads((output.parent / 'backup-verified.json').read_text())
    require(manifest['count'] == 32 and manifest['checkpoint_sha256'] == 'b5e71814d68050aa2dc2b0d84f45964944da94b00575a38045a9600c7a5154bf', 'Final Stage10 backup required')
    actual = {p.relative_to(r2.ROOT).as_posix() for p in r2.ROOT.rglob('*') if p.is_file()}
    require(actual == set(manifest['files']), 'AVD inventory changed before launch')
    for name, record in manifest['files'].items():
        for base in (r2.ROOT, Path(manifest['backup'])):
            path = base / name
            require(path.stat().st_size == record['bytes'] and r2.digest(path) == record['sha256'], 'Protected checkpoint/backup changed')
    with socket.socket() as one, socket.socket() as two:
        one.bind(('127.0.0.1', 5586)); two.bind(('127.0.0.1', 5587))
    config = r2.ROOT / 'avd' / (r2.NAME + '.avd') / 'config.ini'
    (output / 'config-before-boot.ini').write_bytes(config.read_bytes())
    command = r2.launch_command()
    with (output / 'emulator.stdout').open('xb') as log, (output / 'emulator.stderr').open('xb') as error:
        process = subprocess.Popen(command, env=r2.environment(), stdout=log, stderr=error, creationflags=subprocess.CREATE_NO_WINDOW)
    save(output / 'avd-owner.json', dict(root=str(r2.ROOT), name=r2.NAME, serial=r2.SERIAL,
        pid=process.pid, command=command, config_sha256=r2.digest(config)))
    for _ in range(60):
        with socket.socket() as s:
            if s.connect_ex(('127.0.0.1', 5586)) == 0:
                break
        require(process.poll() is None, 'Emulator exited; preserve state')
        time.sleep(1)
    d = open_device(output, OLD_RUNNER)
    for _ in range(60):
        boot = d.command(['shell', 'getprop', 'sys.boot_completed'], allow_failure=True).decode().strip()
        if boot == '1':
            break
        time.sleep(3)
    require(boot == '1', 'Boot not complete')
    d.guard()
    d.installed_hash(r2.QA, QA_HASH)
    d.installed_hash(r2.QA + '.test', OLD_RUNNER)
    save(output / 'boot-verified.json', {'fingerprint':d.fingerprint, 'qa_sha256':QA_HASH, 'received_runner_sha256':OLD_RUNNER})


def capture_private(d, label):
    require(re.fullmatch(r'[a-z0-9-]+', label) is not None, 'Fixed evidence label required')
    d.guard(); d.installed_hash(r2.QA, current_qa(d.output))
    data = d.command(['exec-out', 'run-as', r2.QA, 'tar', '-cf', '-', 'files', 'no_backup', 'shared_prefs', 'databases'])
    path = d.output / (label + '.tar')
    with path.open('xb') as f:
        f.write(data)
    return private_members(data)


def private_members(data):
    result = {}
    with tarfile.open(fileobj=io.BytesIO(data)) as archive:
        for member in archive:
            name = member.name.rstrip('/')
            require(not name.startswith('/') and '..' not in Path(name).parts and member.type in (tarfile.REGTYPE, tarfile.DIRTYPE), 'Unsafe private capture')
            if member.isfile():
                require(name not in result, 'Duplicate private file')
                result[name] = archive.extractfile(member).read()
    require('databases/miguardia-v2.db' in result and 'no_backup/stage10-emulator-fixture-v1.txt' in result, 'Received Stage10 synthetic data missing')
    return result


def semantic_database(files):
    with tempfile.TemporaryDirectory(prefix='miguardia-stage11-read-') as temp:
        root = Path(temp)
        for suffix in ('', '-wal', '-shm'):
            name = 'databases/miguardia-v2.db' + suffix
            if name in files:
                (root / ('data.db' + suffix)).write_bytes(files[name])
        connection = sqlite3.connect((root / 'data.db').as_uri() + '?mode=ro', uri=True)
        try:
            require(connection.execute('PRAGMA integrity_check').fetchall() == [('ok',)], 'Received DB corrupt')
            require(not connection.execute('PRAGMA foreign_key_check').fetchall(), 'Received FK invalid')
            require(connection.execute('PRAGMA user_version').fetchone() == (8,), 'Received Room is not V8')
            names = [r[0] for r in connection.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name!='room_master_table' ORDER BY name")]
            return {name: sorted(connection.execute('SELECT * FROM "' + name + '"').fetchall(), key=repr) for name in names}
        finally:
            connection.close()


def compare_received(current, stage10_zip):
    with zipfile.ZipFile(stage10_zip) as archive:
        original = {'databases/miguardia-v2.db':archive.read('database.db')}
        for name in archive.namelist():
            if name.startswith('private/'):
                relative = name[len('private/'):]
                require(current.get(relative) == archive.read(name), 'Received Stage10 durable bytes changed')
                original[relative] = archive.read(name)
    a, b = semantic_database(original), semantic_database(current)
    business = {name:rows for name, rows in b.items() if name != 'android_metadata'}
    require(a == b and len(business) == 29 and sum(map(len, business.values())) == 40, 'Received Stage10 semantic aggregate differs')
    return {'verdict':'PASS', 'tables':len(business), 'rows':sum(map(len, business.values())), 'android_metadata_compared':True, 'stage10_zip_sha256':r2.digest(stage10_zip),
        'file_sha256':{name:hashlib.sha256(data).hexdigest() for name, data in sorted(current.items())}}


def install(output, runner):
    runner_hash = validate_runner(runner)  # Before even obtaining an ADB session.
    prior = current_runner(output) if list(Path(output).glob('runner-installed*.json')) else None
    received = prior['runner_sha256'] if prior else OLD_RUNNER
    require(received != runner_hash, 'No routine reinstallation of identical runner')
    d = open_device(output, received)
    d.guard(); d.installed_hash(r2.QA, current_qa(output)); d.installed_hash(r2.QA + '.test', received)
    initial = Path(output) / ('received-after-r1-update.tar' if (Path(output)/'profile-update-accepted.json').exists() else 'received-before.tar')
    if initial.exists():
        before = private_members(initial.read_bytes())
        current = capture_private(d, 'received-before-recheck-' + str(uuid.uuid4()))
        require(before == current, 'Received input changed since rejected preflight')
    else:
        before = capture_private(d, 'received-before')
    previous = Path(output).parents[2] / 'stage10/recovery-r2-20261023/run/after-calendar.zip'
    comparison = compare_received(before, previous)
    if not (Path(output) / 'received-before.json').exists(): save(Path(output) / 'received-before.json', comparison)
    d.command(['install', '-t', '-r', str(Path(runner).resolve())], timeout=180)
    d.installed_hash(r2.QA + '.test', runner_hash)
    index = prior.get('installation_index', 1) + 1 if prior else 1
    name = 'runner-installed.json' if index == 1 else f'runner-installed-{index:02d}.json'
    save(Path(output) / name, {'runner':str(runner), 'runner_sha256':runner_hash, 'qa_sha256':current_qa(output),
        'installation_index':index, 'approved_checkpoint_verified_before_adb':True, 'certificate_sha256':CERTIFICATE})


def execute(output, selector, label):
    require(selector in CLASSES, 'Only the reviewed isolated class allowlist may execute')
    require(re.fullmatch(r'[a-z0-9-]+', label) is not None, 'Safe new execution label required')
    output = Path(output)
    runner_hash = current_runner(output)['runner_sha256']
    d = open_device(output, runner_hash)
    qa_hash = current_qa(output)
    d.guard(); d.installed_hash(r2.QA, qa_hash); d.installed_hash(r2.QA + '.test', runner_hash)
    actual, _ = qa.discover(output.parents[3])
    methods = actual[selector]['methods']
    args = {'class':selector, 'stage11Consent':CONSENT, 'stage11RunId':str(uuid.uuid4()), 'stage11RunnerSha256':runner_hash, 'stage11QaSha256':qa_hash}
    command = ['shell', 'am', 'instrument', '-w', '-r']
    for key, value in args.items():
        command += ['-e', key, value]
    raw = d.command([*command, RUNNER], timeout=900).decode('utf8', errors='replace')
    result = protocol(raw, 0, selector, methods)
    if 'bundle' in result:
        try:
            data = d.command(['exec-out', 'run-as', r2.QA, 'cat', 'cache/' + result['bundle']])
            with (output / (label + '-android-evidence.zip')).open('xb') as f: f.write(data)
            attest_transport(result, data)
        except Exception as error:
            result['verdict'] = 'FAILED'
            result['signals'].append('evidence-transport-error-' + type(error).__name__)
    save(output / (label + '-result.json'), result)
    print(json.dumps(result, ensure_ascii=False), flush=True)
    return result


def finish(output):
    output = Path(output)
    runner_hash = current_runner(output)['runner_sha256']
    d = open_device(output, runner_hash)
    after = capture_private(d, 'received-after')
    baseline = 'received-after-r1-update.tar' if (output/'profile-update-accepted.json').exists() else 'received-before.tar'
    before = private_members((output / baseline).read_bytes())
    require(before == after, 'Received durable file bytes/inventory differ after Stage11')
    previous = output.parents[2] / 'stage10/recovery-r2-20261023/run/after-calendar.zip'
    save(output / 'received-after.json', compare_received(after, previous))
    d.installed_hash(r2.QA, current_qa(output)); d.installed_hash(r2.QA + '.test', runner_hash)
    d.command(['shell', 'sync'])
    d.command(['emu', 'kill'])
    for _ in range(60):
        with socket.socket() as s:
            if s.connect_ex(('127.0.0.1', 5586)) != 0:
                break
        time.sleep(1)
    with socket.socket() as one, socket.socket() as two:
        one.bind(('127.0.0.1', 5586)); two.bind(('127.0.0.1', 5587))
    save(output / 'resources-released.json', {'avd_stopped':True, 'ports_free':[5586,5587], 'qa_sha256':current_qa(output), 'runner_sha256':runner_hash})


def first_install(text):
    values = re.findall(r'firstInstallTime=([^\r\n]+)', text)
    require(len(values) == 1 and values[0], 'Unique first installation time required')
    return values[0]


def install_qa(output, apk):
    digest = validate_artifact(apk, APPROVED_QAS, r2.QA)
    output = Path(output)
    require(not (output / 'qa-r1-installed.json').exists(), 'R1 product update is not replayable')
    runner_hash = current_runner(output)['runner_sha256']
    d = open_device(output, runner_hash)
    d.guard(); d.installed_hash(r2.QA, QA_HASH); d.installed_hash(r2.QA+'.test',runner_hash)
    before = capture_private(d,'received-before-r1-update')
    require(before == private_members((output/'received-before.tar').read_bytes()), 'Received Stage10 changed before R1 update')
    metadata_before = d.command(['shell','dumpsys','package',r2.QA]).decode()
    times_before = first_install(metadata_before)
    result = d.command(['install','-r',str(Path(apk).resolve())],timeout=180).decode()
    require('Success' in result, 'R1 QA update failed; do not reset or retry automatically')
    d.installed_hash(r2.QA,digest)
    metadata_after = d.command(['shell','dumpsys','package',r2.QA]).decode()
    times_after = first_install(metadata_after)
    require(times_before == times_after, 'First installation changed')
    # Register identity before readback so subsequent guards require the new exact QA.
    save(output/'qa-r1-installed.json',{'qa_sha256':digest,'apk':str(apk),'first_install_before':times_before,
        'first_install_after':times_after,'approved_before_install':True,'certificate_sha256':CERTIFICATE})
    after = capture_private(d,'received-after-r1-update')
    transition = profile_update_only(before,after,metadata_after)
    save(output/'profile-update-accepted.json',transition)
    comparison=compare_received(after,output.parents[2]/'stage10/recovery-r2-20261023/run/after-calendar.zip')
    save(output/'received-r1-update.json',comparison)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('action', choices=['launch', 'install', 'install-qa', 'execute', 'finish'])
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--runner', type=Path)
    p.add_argument('--qa-apk', type=Path)
    p.add_argument('--selector', choices=CLASSES)
    p.add_argument('--label')
    a = p.parse_args()
    require(sys.flags.optimize == 0, 'Python optimization forbidden')
    require(a.output.is_dir(), 'Existing Stage11 owned evidence directory required')
    if a.action == 'launch': launch(a.output)
    elif a.action == 'install': require(a.runner is not None, 'Runner required'); install(a.output, a.runner)
    elif a.action == 'install-qa': require(a.qa_apk is not None, 'QA required'); install_qa(a.output, a.qa_apk)
    elif a.action == 'execute': return 0 if execute(a.output, a.selector, a.label)['verdict'] == 'PASSED' else 1
    else: finish(a.output)


if __name__ == '__main__':
    raise SystemExit(main())
