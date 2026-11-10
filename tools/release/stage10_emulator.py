"""One opt-in QA6->QA8 rehearsal in the dedicated Stage10 AVD. No physical target."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

import stage10_capture as old
import stage10_emulator_capture as capture

NAME = 'MiGuardia_Stage10_API36_20261022_R2'
PORT = 5586
SERIAL = 'emulator-5586'
ROOT = Path('C:/Users/User/.cache/miguardia-stage10-avd/20261022-r2')
SDK = Path('C:/Users/User/AppData/Local/Android/Sdk')
JBR = Path('C:/Users/User/.cache/miguardia-toolchains/jbrsdk-25.0.2-b329.117/runtime/jbrsdk-25.0.2-windows-x64-b329.117')
QA = old.QA
COMPONENT = QA + '/com.blackatsystems.miguardia.MainActivity'
RUNNER = QA + '.test/com.blackatsystems.miguardia.upgrade.Stage10UpgradeRunner'
require = capture.require
AVD_SETTINGS = {'hw.ramSize':'2560','hw.cpu.ncore':'2','disk.dataPartition.size':'6442450944',
                'hw.lcd.width':'1080','hw.lcd.height':'2400','hw.lcd.density':'360',
                'fastboot.forceColdBoot':'yes','fastboot.forceFastBoot':'no'}


def launch_command():
    return [str(SDK / 'emulator/emulator.exe'), '-avd', NAME, '-port', str(PORT), '-no-snapshot', '-no-window', '-no-audio', '-memory', '2560', '-cores', '2', '-gpu', 'auto']


def active_scenario(spec):
    capture.scenario(spec)
    require(spec['consent'] == capture.CONSENT_R2, 'Only the authorized R2 scenario may execute')
    return spec


def require_viewport(size, density):
    require(size.strip() == 'Physical size: 1080x2400' and density.strip() == 'Physical density: 360',
            'Observed R2 viewport differs or has an Android override')


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def save(path, value):
    with Path(path).open('x', encoding='utf-8') as f:
        json.dump(value, f, ensure_ascii=False, indent=2)
        f.write('\n')


def target_identity(serial, avd_name, qemu, api, abi):
    require(serial == SERIAL and avd_name == NAME, 'Dedicated serial/AVD required')
    require(qemu == '1' and api == '36' and abi == 'x86_64', 'Physical/wrong Android target rejected')


def environment():
    env = os.environ.copy()
    env.update(JAVA_HOME=str(JBR), ANDROID_HOME=str(SDK), ANDROID_USER_HOME=str(ROOT / 'user'),
               ANDROID_EMULATOR_HOME=str(ROOT / 'emulator'), ANDROID_AVD_HOME=str(ROOT / 'avd'))
    return env


def adb_environment(inherited=None):
    env = dict(os.environ if inherited is None else inherited)
    for key in ('ADB_SERVER_SOCKET','ANDROID_ADB_SERVER_ADDRESS','ANDROID_ADB_SERVER_PORT','ADB_SERVER_PORT','ANDROID_SERIAL','ADB_TRACE'):
        env.pop(key, None)
    return env


def adb_argv(args):
    return [str(SDK / 'platform-tools/adb.exe'), '-H', '127.0.0.1', '-P', '5037', '-s', SERIAL, *args]


def require_avd_config(before, after):
    def parse(data):
        result = {}
        for line in data.decode('utf-8').splitlines():
            if not line.strip():
                continue
            key, separator, value = line.partition('=')
            key, value = key.strip(), value.strip()
            require(separator and key and key not in result, 'Malformed/duplicate AVD configuration')
            result[key] = value
        return result
    original, current = parse(before), parse(after)
    require(set(original) == set(current), 'AVD configuration fields changed')
    differences = {key for key in original if original[key] != current[key]}
    require(not differences or (differences == {'disk.dataPartition.size'} and original['disk.dataPartition.size'] == '4G' and current['disk.dataPartition.size'] == '6442450944'), 'Unreviewed AVD configuration change')


def gui_action(xml, guide_context=False, calendar_context=False, fixture_context=False):
    """Navigate observed QA controls; Calendar needs month, fixture and real cells."""
    root = ET.fromstring(xml)
    nodes = [n for n in root.iter('node') if n.get('package') == QA]
    texts = [n.get('text', '') for n in nodes]
    descriptions = [n.get('content-desc', '') for n in nodes]
    observed_guide = any(t.startswith(('Primeros pasos', 'Recorrido ·')) for t in texts) or any(t in ('Siguiente', 'Ver recorrido', 'Finalizar') for t in texts) or 'Recorrido activo. Los controles señalados no se activan.' in descriptions
    guide = guide_context or observed_guide
    days = [d for d in descriptions if re.search(r'\b\d{1,2} de [a-záéíóú]+ de \d{4}\b', d, re.I)]
    calendar_context = calendar_context or ('Mes anterior' in descriptions and 'Mes siguiente' in descriptions)
    fixture_context = fixture_context or any('FIC' in d for d in descriptions)
    if not observed_guide and calendar_context and len(set(days)) >= 7 and fixture_context:
        return None
    calendar_surface = calendar_context or ('Abrir menú' in descriptions and any(d.startswith('Hoy,') and 'FIC' in d for d in descriptions))
    if not observed_guide and calendar_surface:
        containers = []
        for n in nodes:
            if n.get('scrollable') == 'true' and n.get('enabled') == 'true':
                bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.get('bounds',''))
                if bounds:
                    x1,y1,x2,y2 = map(int,bounds.groups())
                    if 0 <= x1 < x2 <= 4096 and 0 <= y1 < y2 <= 4096 and y2-y1 >= 100:
                        containers.append((x1,y1,x2,y2))
        require(len(containers) == 1, 'Unambiguous observed vertical Calendar container required')
        x1,y1,x2,y2 = containers[0]; x=(x1+x2)//2
        return x,y1+3*(y2-y1)//4,x,y1+(y2-y1)//4
    if not guide:
        descriptions = [n.get('content-desc', '') for n in nodes]
        days = [d for d in descriptions if re.search(r'\b\d{1,2} de [a-záéíóú]+ de \d{4}\b', d, re.I)]
        require('Mes anterior' in descriptions and 'Mes siguiente' in descriptions and len(set(days)) >= 20 and any('FIC' in d for d in days), 'Actual Calendar grid with fixture data not demonstrated')
        return None
    matches = [n for n in nodes if n.get('text') in ('Siguiente', 'Ver recorrido', 'Finalizar') and n.get('enabled') == 'true']
    if not matches:
        scroll = [n for n in nodes if n.get('scrollable') == 'true' and n.get('enabled') == 'true']
        require(len(scroll) == 1, 'Unambiguous observed guide scroll container required')
        bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', scroll[0].get('bounds', ''))
        require(bounds is not None, 'Missing observed scroll bounds')
        x1, y1, x2, y2 = map(int, bounds.groups())
        require(0 <= x1 < x2 <= 4096 and 0 <= y1 < y2 <= 4096 and y2-y1 >= 100, 'Invalid observed scroll bounds')
        x = (x1+x2)//2
        return x, y1+3*(y2-y1)//4, x, y1+(y2-y1)//4
    require(len(matches) == 1, 'Unambiguous observed guide control required')
    coordinates = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', matches[0].get('bounds', ''))
    require(coordinates is not None, 'Missing observed guide bounds')
    x1, y1, x2, y2 = map(int, coordinates.groups())
    require(0 <= x1 < x2 <= 4096 and 0 <= y1 < y2 <= 4096, 'Invalid observed button bounds')
    return (x1 + x2) // 2, (y1 + y2) // 2


class Device:
    def __init__(self, output, owner, runner_hash, serial=SERIAL):
        require(serial == SERIAL, 'Physical/default transports forbidden')
        require(Path(owner['root']).resolve() == ROOT.resolve() and owner['name'] == NAME and owner['serial'] == SERIAL, 'Wrong owned AVD')
        require(type(owner['pid']) is int and owner['pid'] > 0, 'Missing launched process')
        require(owner.get('command') == launch_command(), 'Owned launch command differs')
        self.output, self.owner, self.runner_hash = Path(output), owner, runner_hash
        self.sequence, self.fingerprint = 0, None

    def host_guard(self):
        config = ROOT / 'avd' / (NAME + '.avd') / 'config.ini'
        original = self.output / 'config-before-boot.ini'
        require(original.is_file() and digest(original) == self.owner['config_sha256'], 'Original AVD configuration evidence differs')
        require(config.is_file(), 'Owned AVD config missing')
        require_avd_config(original.read_bytes(), config.read_bytes())
        # PID bound to the listening console port, before any Android command.
        script = f"$c = Get-NetTCPConnection -State Listen -LocalPort {PORT} -ErrorAction Stop; $p = Get-CimInstance Win32_Process; @($p | Where-Object {{ $_.ProcessId -in $c.OwningProcess }} | Select-Object ProcessId,ParentProcessId,Name,ExecutablePath,CommandLine) | ConvertTo-Json -Compress"
        result = subprocess.run(['powershell.exe','-NoProfile','-Command',script], capture_output=True, timeout=15, creationflags=subprocess.CREATE_NO_WINDOW)
        require(result.returncode == 0 and result.stdout.strip(), 'Owned emulator console port unavailable')
        owners = json.loads(result.stdout)
        if isinstance(owners, dict):
            owners = [owners]
        require(len(owners) == 1, 'Ambiguous emulator port ownership')
        owner = owners[0]
        require(owner['ProcessId'] == self.owner['pid'] or owner['ParentProcessId'] == self.owner['pid'], 'Console port belongs to another process')
        require(owner['Name'] in ('emulator.exe','qemu-system-x86_64.exe','qemu-system-x86_64-headless.exe') and NAME in owner['CommandLine'], 'Unexpected emulator process identity')
        require(Path(owner['ExecutablePath']).is_file(), 'Missing emulator executable identity')
        # Compare actual file identity, not a resolved Store path against CIM's logical alias.
        expected_executable = SDK / 'emulator' / ('emulator.exe' if owner['Name'] == 'emulator.exe' else 'qemu/windows-x86_64/' + owner['Name'])
        require(os.path.samefile(expected_executable, owner['ExecutablePath']), 'Console executable outside selected SDK')
        owner['executable_verified_by_file_identity'] = True
        return owner

    def command(self, args, timeout=60, allow_failure=False):
        # No arbitrary command-line interface: only the methods below form commands.
        self.host_guard()
        argv = adb_argv(args)
        self.sequence += 1
        name = f'adb-{self.sequence:03d}'
        save(self.output / (name + '-start.json'), {'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(), 'argv':argv})
        try:
            result = subprocess.run(argv, env=adb_environment(), capture_output=True, timeout=timeout, creationflags=subprocess.CREATE_NO_WINDOW)
        except subprocess.TimeoutExpired as error:
            stdout, stderr = error.stdout or b'', error.stderr or b''
            (self.output / (name + '.stdout')).write_bytes(stdout)
            (self.output / (name + '.stderr')).write_bytes(stderr)
            save(self.output / (name + '-result.json'), {'exit_code':None,'outcome':'UNCERTAIN_TIMEOUT','timeout_seconds':timeout,
                'stdout_sha256':hashlib.sha256(stdout).hexdigest(),'stderr_sha256':hashlib.sha256(stderr).hexdigest(),'retried':False})
            raise ValueError('ADB timeout with uncertain outcome; retained partial output, no retry') from error
        (self.output / (name + '.stdout')).write_bytes(result.stdout)
        (self.output / (name + '.stderr')).write_bytes(result.stderr)
        save(self.output / (name + '-result.json'), {'exit_code':result.returncode,'stdout_sha256':hashlib.sha256(result.stdout).hexdigest()})
        require(allow_failure or result.returncode == 0, 'ADB operation failed; retain output and stop')
        return result.stdout

    def guard(self):
        avd = self.command(['emu', 'avd', 'name']).decode().splitlines()[0].strip()
        props = [self.command(['shell', 'getprop', key]).decode().strip() for key in ('ro.kernel.qemu', 'ro.build.version.sdk', 'ro.product.cpu.abi')]
        target_identity(SERIAL, avd, *props)
        fingerprint = self.command(['shell', 'getprop', 'ro.build.fingerprint']).decode().strip()
        require(fingerprint and (self.fingerprint is None or fingerprint == self.fingerprint), 'AVD fingerprint changed')
        self.fingerprint = fingerprint

    def installed_hash(self, package, expected):
        require(package in (QA, QA + '.test'), 'Only QA identities allowed')
        text = self.command(['shell', 'pm', 'path', package]).decode().strip()
        require('\n' not in text and re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~-]+/base\.apk', text), 'One regular base APK required')
        data = self.command(['exec-out', 'cat', text.removeprefix('package:')])
        require(hashlib.sha256(data).hexdigest() == expected, 'Installed artifact differs')

    def install(self, apk, expected, update=False, test=False, not_before=None):
        self.guard()
        require(digest(apk) == expected, 'Local APK changed')
        require(expected == (self.runner_hash if test else old.APKS[8 if update else 6]), 'Undeclared install artifact')
        if update:
            require(type(not_before) is int and not_before > 0, 'Explicit Android expiry boundary required before update')
            # The package timestamp and this guard use the Android clock, not the host clock.
            for attempt in range(12):
                now = int(self.command(['shell', 'date', '+%s']).strip()) * 1000
                if now >= not_before + 1000:
                    break
                require(not_before - now < 10000, 'Android clock changed unexpectedly')
                time.sleep(1)
            else:
                raise ValueError('Android expiry boundary was not reached')
        flags = (['-t'] if test else []) + (['-r'] if update else [])
        text = self.command(['install', *flags, str(Path(apk).resolve())], timeout=180).decode()
        require('Success' in text, 'Install did not succeed; no uninstall/reset fallback')
        self.installed_hash(QA + '.test' if test else QA, expected)

    def stop_qa(self):
        self.guard()
        self.command(['shell', 'am', 'force-stop', QA])

    def capture(self, spec, operation, label, *, baseline=None, baseline_runner_hash=None):
        active_scenario(spec)
        self.guard()
        self.installed_hash(QA, old.APKS[8 if operation == 'capture-v8' else 6])
        self.installed_hash(QA + '.test', self.runner_hash)
        identity = str(uuid.uuid4())
        args = {'stage10EmulatorOperation':operation,'stage10Consent':spec['consent'],'stage10ScenarioId':spec['id'],
                'stage10StartedAt':str(spec['started_at']),'stage10ShortEnd':str(spec['short_end']),
                'stage10LongEnd':str(spec['long_end']),'stage10CaptureId':identity}
        if baseline is not None:
            require(operation == 'capture-v8', 'Baseline binding is only valid for V8 capture')
            bound = capture.profile_baseline(baseline, spec, baseline_runner_hash)
            args.update({'stage10BaselineBundleSha256':bound['bundle_sha256'],
                'stage10BaselineProfileSha256':bound['profile_sha256'],
                'stage10BaselineFirstInstallTime':str(bound['first_install_time']),
                'stage10BaselineLastUpdateTime':str(bound['last_update_time']),
                'stage10BaselineCapturedAt':str(bound['captured_at_epoch_millis'])})
        command = ['shell', 'am', 'instrument', '-w', '-r']
        for k, v in args.items():
            command.extend(['-e', k, v])
        result = self.command([*command, RUNNER], timeout=180).decode()
        require('INSTRUMENTATION_CODE: -1' in result and 'stage10Status=CAPTURE_COMPLETE' in result and f'stage10CaptureId={identity}' in result, 'Instrumentation did not attest success')
        match = re.search(r'stage10BundleSha256=([a-f0-9]{64})', result)
        require(match is not None, 'Missing bundle hash')
        data = self.command(['exec-out', 'run-as', QA, 'cat', f'cache/stage10-{identity}.zip'])
        require(hashlib.sha256(data).hexdigest() == match[1], 'Capture transport changed')
        bundle = self.output / (label + '.zip')
        with bundle.open('xb') as f:
            f.write(data)
        return capture.ingest(bundle, self.output / label, spec, self.runner_hash,
            baseline=baseline, baseline_runner_hash=baseline_runner_hash)

    def open_calendar(self, label, complete_guide):
        self.guard()
        self.command(['shell', 'am', 'start', '-W', '-n', COMPONENT])
        guide_context = False
        calendar_context = fixture_context = False
        for step in range(32):
            time.sleep(2)
            data = self.command(['exec-out', 'uiautomator', 'dump', '/dev/tty']).decode()
            begin, end = data.find('<?xml'), data.rfind('</hierarchy>')
            require(begin >= 0 and end >= begin, 'Missing UI hierarchy')
            xml = data[begin:end + len('</hierarchy>')]
            (self.output / f'{label}-ui-{step:02d}.xml').write_text(xml, encoding='utf-8')
            nodes = list(ET.fromstring(xml).iter('node'))
            guide_context = guide_context or any(n.get('package') == QA and n.get('text','').startswith(('Primeros pasos', 'Recorrido ·')) for n in nodes)
            descriptions = [n.get('content-desc','') for n in nodes if n.get('package') == QA]
            calendar_context = calendar_context or ('Mes anterior' in descriptions and 'Mes siguiente' in descriptions)
            fixture_context = fixture_context or any('FIC' in d for d in descriptions)
            action = gui_action(xml, guide_context, calendar_context, fixture_context)
            if action is None:
                image = self.command(['exec-out', 'screencap', '-p'])
                require(image.startswith(b'\x89PNG\r\n\x1a\n'), 'Missing Calendar screenshot')
                (self.output / (label + '-calendar.png')).write_bytes(image)
                save(self.output / (label + '-calendar.json'), {'calendar_grid_observed':True,'guide_absent':True,'fingerprint':self.fingerprint})
                return
            observed_guide = any(n.get('package') == QA and (n.get('text','').startswith(('Primeros pasos','Recorrido ·')) or n.get('text') in ('Siguiente','Ver recorrido','Finalizar') or n.get('content-desc') == 'Recorrido activo. Los controles señalados no se activan.') for n in nodes)
            require(complete_guide or not observed_guide, 'Guide unexpectedly returned after update')
            if len(action) == 4:
                self.command(['shell', 'input', 'swipe', *map(str, action), '250'])
            else:
                self.command(['shell', 'input', 'tap', str(action[0]), str(action[1])])
        raise ValueError('Guide did not reach actual Calendar')


def create_and_start(output):
    require(not ROOT.exists(), 'AVD root already exists; never recreate, clear or reuse automatically')
    require((SDK / 'emulator/emulator.exe').is_file(), 'Emulator missing')
    probe_env = environment()
    probe_env.update(ANDROID_USER_HOME=str(output/'accel-home'), ANDROID_EMULATOR_HOME=str(output/'accel-home'))
    accel = subprocess.run([str(SDK / 'emulator/emulator.exe'), '-accel-check'], env=probe_env, capture_output=True, timeout=30)
    save(output / 'acceleration-before-avd.json', {'exit_code':accel.returncode,'stdout':accel.stdout.decode(errors='replace'),'stderr':accel.stderr.decode(errors='replace')})
    require(accel.returncode == 0 and b'WHPX' in accel.stdout and b'usable' in accel.stdout, 'WHPX unavailable')
    # Bind only as a local conflict probe; close both sockets before launch.
    with socket.socket() as one, socket.socket() as two:
        one.bind(('127.0.0.1', PORT)); two.bind(('127.0.0.1', PORT+1))
    ROOT.mkdir(parents=True, exist_ok=False)
    for name in ('user', 'emulator', 'avd'):
        (ROOT / name).mkdir()
    avd = ROOT / 'avd' / (NAME + '.avd')
    args = [str(SDK / 'cmdline-tools/22.0/bin/avdmanager.bat'), 'create', 'avd', '-n', NAME,
            '-k', 'system-images;android-36;default;x86_64', '-p', str(avd)]
    created = subprocess.run(args, input=b'no\n', env=environment(), capture_output=True, timeout=120)
    (output / 'avd-create.stdout').write_bytes(created.stdout); (output / 'avd-create.stderr').write_bytes(created.stderr)
    require(created.returncode == 0 and (avd / 'config.ini').is_file(), 'AVD creation failed; preserve and stop')
    config = (avd / 'config.ini').read_text()
    for key, value in AVD_SETTINGS.items():
        config = re.sub(r'^' + re.escape(key) + r'=.*\n?', '', config, flags=re.M)
        config += f'{key}={value}\n'
    (avd / 'config.ini').write_text(config)
    (output / 'config-before-boot.ini').write_bytes((avd / 'config.ini').read_bytes())
    require((ROOT / 'avd' / (NAME + '.ini')).is_file(), 'AVD registered outside owned root')
    config_hash = digest(output/'config-before-boot.ini')
    save(output/'avd-configuration-seal.json', {'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'name':NAME,'root':str(ROOT),'serial':SERIAL,'settings':AVD_SETTINGS,'config_sha256':config_hash,'before_emulator_launch':True})
    command = launch_command()
    with (output / 'emulator.stdout').open('xb') as log, (output / 'emulator.stderr').open('xb') as error:
        process = subprocess.Popen(command, env=environment(), stdout=log, stderr=error, creationflags=subprocess.CREATE_NO_WINDOW)
    owner = {'root':str(ROOT),'name':NAME,'serial':SERIAL,'pid':process.pid,'command':command,'config_sha256':config_hash}
    save(output / 'avd-owner.json', owner)
    return owner


def run(output, qa6, qa8, runner):
    output = Path(output).resolve()
    require(not output.exists() and output.parent.is_dir(), 'New rehearsal output required; no automatic replay')
    require(digest(qa6) == old.APKS[6] and digest(qa8) == old.APKS[8], 'Product APK changed')
    output.mkdir()
    runner_hash = digest(runner)
    save(output/'inputs.json', {'qa6':str(qa6),'qa8':str(qa8),'runner':str(runner),'runner_sha256':runner_hash})
    owner = create_and_start(output)
    return perform_sequence(output, qa6, qa8, runner_hash, owner)


def continue_before_install(output, qa6, qa8, runner):
    output = Path(output).resolve()
    require(output.is_dir() and not list(output.glob('adb-*-start.json')) and not (output/'scenario.json').exists(), 'Continuation only before all Android operations')
    failure = json.loads((output/'failure.json').read_text())
    require(failure['type'] == 'ValueError' and failure['message'] == 'Owned AVD config changed', 'Different checkpoint; do not replay')
    inputs = json.loads((output/'inputs.json').read_text())
    require(digest(qa6) == old.APKS[6] and digest(qa8) == old.APKS[8] and digest(runner) == inputs['runner_sha256'], 'Continuation inputs changed')
    owner = json.loads((output/'avd-owner.json').read_text())
    Device(output, owner, inputs['runner_sha256']).host_guard()
    save(output/'continuation-before-install.json', {'checkpoint':'CONFIG_SERIALIZATION_AND_EXACT_6G_MINIMUM_REVIEWED','same_owner':owner,'created_new_avd':False})
    return perform_sequence(output, qa6, qa8, inputs['runner_sha256'], owner)


def perform_sequence(output, qa6, qa8, runner_hash, owner):
    runner = Path(json.loads((output/'inputs.json').read_text())['runner'])
    device = Device(output, owner, runner_hash)
    for attempt in range(45):
        try:
            checked_owner = device.host_guard()
            save(output/'console-owner.json', checked_owner)
            break
        except ValueError:
            if attempt == 44:
                raise
            time.sleep(2)
    # Explicit target only, after creating the exclusive AVD and process.
    device.command(['wait-for-device'], timeout=180)
    for _ in range(60):
        if device.command(['shell', 'getprop', 'sys.boot_completed']).strip() == b'1':
            break
        time.sleep(2)
    else:
        raise ValueError('Dedicated AVD did not finish boot')
    device.guard()
    size = device.command(['shell','wm','size']).decode()
    density = device.command(['shell','wm','density']).decode()
    require_viewport(size,density)
    save(output/'viewport-observed.json', {'size':size,'density':density,'settings_changed_through_adb':False})
    for package in (QA, QA + '.test'):
        require(not device.command(['shell', 'pm', 'path', package], allow_failure=True).strip(), 'Fresh AVD already contains QA; no clearing')
    device.install(qa6, old.APKS[6]); device.install(runner, runner_hash, test=True)
    # Choose the next real minute after install. No system/AVD clock writes.
    observed = int(device.command(['shell', 'date', '+%s']).strip()) * 1000
    require(abs(observed - time.time() * 1000) < 10000, 'Host/AVD clock disagreement')
    start = (observed // 60000 + 1) * 60000
    spec = active_scenario({'id':str(uuid.uuid4()),'consent':capture.CONSENT_R2,'started_at':start,'short_end':start+1800000,'long_end':start+172800000})
    save(output/'scenario.json', spec)
    while time.time()*1000 < start:
        time.sleep(max(0, min(10, (start-time.time()*1000)/1000)))
    seed = device.capture(spec, 'seed-v6', 'seed')
    return finish_sequence(device, output, qa8, runner_hash, owner, spec, seed, 'v6')


def finish_sequence(device, output, qa8, runner_hash, owner, spec, seed, label):
    device.open_calendar(label, True)
    device.stop_qa()
    before = device.capture(spec, 'capture-v6', 'baseline')
    require(seed['snapshot']['tables'] == before['snapshot']['tables'] and seed['snapshot']['portable_preferences'] == before['snapshot']['portable_preferences'], 'Guide changed fixture business data/preferences')
    device.stop_qa()
    save(output/'baseline-sealed.json', {'scenario_sha256':digest(output/'scenario.json'),'bundle_sha256':before['bundle_sha256'],'source_fixture_sha256':before['receipt']['source_fixture_sha256']})
    while time.time()*1000 < spec['short_end'] + 2000:
        print('Baseline sealed; V6 stopped; waiting for natural expiry.', flush=True)
        time.sleep(max(0, min(30, (spec['short_end']+2000-time.time()*1000)/1000)))
    device.install(qa8, old.APKS[8], update=True, not_before=spec['short_end'])
    neutral = device.capture(spec, 'capture-v8', 'after-upgrade')
    first = capture.compare(output/'baseline', output/'after-upgrade', spec, runner_hash)
    save(output/'comparison-after-upgrade.json', first)
    device.open_calendar('v8', False)
    device.stop_qa()
    device.capture(spec, 'capture-v8', 'after-opening')
    final = capture.compare(output/'baseline', output/'after-opening', spec, runner_hash, final=True)
    save(output/'comparison-after-opening.json', final)
    save(output/'completed.json', {'state':'ANDROID_SEQUENCE_COMPLETED_PENDING_MAIN_AUDIT','comparison':final,'owner':owner,'no_deletion_or_reseed':True})
    return final


def resume_after_seed(output, qa6, qa8, runner):
    output = Path(output).resolve()
    spec = active_scenario(json.loads((output/'scenario.json').read_text(encoding='utf-8')))
    require(time.time()*1000 < spec['short_end']-180000, 'Insufficient original scenario window; no reseed')
    require(not (output/'baseline').exists() and not (output/'resume-owner.json').exists(), 'Seed checkpoint can resume only once before baseline')
    failure = json.loads((output/'failure-continuation.json').read_text())
    require(failure['message'] == 'Unambiguous observed guide control required', 'Different seed checkpoint')
    released = json.loads((output/'resource-release-continuation.json').read_text())
    require(released.get('owned_emulator_shutdown_requested') is True and released.get('userdata_preserved') is True, 'Prior owned AVD shutdown not recorded')
    inputs = json.loads((output/'inputs.json').read_text())
    require(digest(qa6) == old.APKS[6] and digest(qa8) == old.APKS[8] and digest(runner) == inputs['runner_sha256'], 'Resume inputs changed')
    seed = capture.ingest(output/'seed.zip', output/'seed-revalidated-for-resume', spec, inputs['runner_sha256'])
    owner = json.loads((output/'avd-owner.json').read_text())
    Device(output,owner,inputs['runner_sha256'])  # Validate R2 ownership before launching a saved command.
    require(digest(output/'config-before-boot.ini') == owner['config_sha256'], 'Original config changed')
    require_avd_config((output/'config-before-boot.ini').read_bytes(), (ROOT/'avd'/(NAME+'.avd')/'config.ini').read_bytes())
    with socket.socket() as one, socket.socket() as two:
        one.bind(('127.0.0.1',PORT)); two.bind(('127.0.0.1',PORT+1))
    with (output/'emulator-resume.stdout').open('xb') as log, (output/'emulator-resume.stderr').open('xb') as error:
        process = subprocess.Popen(owner['command'],env=environment(),stdout=log,stderr=error,creationflags=subprocess.CREATE_NO_WINDOW)
    owner = owner | {'pid':process.pid,'previous_pid':owner['pid'],'same_userdata':True}
    save(output/'resume-owner.json',owner)
    device = Device(output,owner,inputs['runner_sha256'])
    device.sequence = max(int(p.name.split('-')[1]) for p in output.glob('adb-*-start.json'))
    device.fingerprint = seed['receipt']['fingerprint']
    for attempt in range(45):
        try:
            device.host_guard()
            break
        except ValueError:
            if attempt == 44: raise
            time.sleep(2)
    device.command(['wait-for-device'],timeout=120)
    for _ in range(60):
        if device.command(['shell','getprop','sys.boot_completed']).strip() == b'1': break
        time.sleep(2)
    else: raise ValueError('Same AVD did not resume boot')
    device.guard()
    device.installed_hash(QA,old.APKS[6]); device.installed_hash(QA+'.test',inputs['runner_sha256'])
    save(output/'resume-after-seed.json',{'same_scenario':spec,'no_install_or_seed':True,'seed_bundle_sha256':digest(output/'seed.zip')})
    return finish_sequence(device,output,qa8,inputs['runner_sha256'],owner,spec,seed,'v6-resumed')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--qa6', type=Path, required=True); parser.add_argument('--qa8', type=Path, required=True)
    parser.add_argument('--runner', type=Path, required=True)
    parser.add_argument('--execute-one-authorized-avd', action='store_true', required=True)
    parser.add_argument('--continue-before-install', action='store_true')
    parser.add_argument('--resume-after-seed', action='store_true')
    args = parser.parse_args()
    require(not (args.continue_before_install and args.resume_after_seed), 'Choose one checkpoint')
    owned_output = not args.output.exists() or args.continue_before_install or args.resume_after_seed
    suffix = '-resume' if args.resume_after_seed else ('-continuation' if args.continue_before_install else '')
    try:
        action = resume_after_seed if args.resume_after_seed else (continue_before_install if args.continue_before_install else run)
        print(json.dumps(action(args.output, args.qa6, args.qa8, args.runner), ensure_ascii=False))
    except Exception as error:
        if owned_output and args.output.is_dir():
            save(args.output/('failure'+suffix+'.json'), {'type':type(error).__name__,'message':str(error),'reseed_or_reinstall_allowed':False})
        raise
    finally:
        owner_file = args.output/('resume-owner.json' if args.resume_after_seed else 'avd-owner.json')
        if owned_output and owner_file.is_file():
            try:
                owner = json.loads(owner_file.read_text())
                inputs = json.loads((args.output/'inputs.json').read_text())
                endpoint = Device(args.output, owner, inputs['runner_sha256'])
                # Continue the command journal after the main coordinator's last item.
                endpoint.sequence = max([int(p.name.split('-')[1]) for p in args.output.glob('adb-*-start.json')], default=0)
                endpoint.guard()
                endpoint.command(['emu','kill'])
                save(args.output/('resource-release'+suffix+'.json'), {'owned_emulator_shutdown_requested':True,'userdata_preserved':True,'no_snapshot_or_clear':True})
            except Exception as error:
                save(args.output/('resource-release'+suffix+'.json'), {'owned_emulator_shutdown_requested':False,'reason':str(error),'manual_checkpoint_required':True})


if __name__ == '__main__':
    main()
