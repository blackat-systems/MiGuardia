"""Bounded Stage12 private SDK preparation and admitted first-boot operator.

No common ADB client/daemon, no device discovery, no global environment changes.
Host canaries never carry an AVD argument. Boot needs a new exact MAIN admission.
"""
from __future__ import annotations

import argparse
import ctypes
from datetime import datetime, timezone
import hashlib
import importlib.util
import ipaddress
import json
import os
from pathlib import Path
import re
import shutil
import socket
import struct
import sys
import time
import uuid

BASE = Path(__file__).resolve().parents[2]
EVIDENCE = BASE / '.local-evidence/stage12/20261024-resume'
RUNTIME = Path('C:/Users/User/.cache/miguardia-stage12-runtime/20261024')
SDK = RUNTIME / 'sdk'
ORIGINAL_SDK = Path('C:/Users/User/AppData/Local/Android/Sdk')
AVD_ROOT = Path('C:/Users/User/.cache/miguardia-stage12-avd/20261023')
AVD_NAME = 'MiGuardia_Stage12_API36_20261023'
SERIAL = 'emulator-5588'
IMAGE = ORIGINAL_SDK / 'system-images/android-36/default/x86_64'
STUB_SOURCE = BASE / '.local-evidence/stage12/20261023-local2/avd/Stage12AdbCliRefused.exe'
STUB_SHA = '998bdab32c547fc8b1e221eb5e9979b538068f16e14bce11f55e34c9c5330ad1'
ADB_SHA = 'b4a6b455702684652cccf7b46258b29e653538904359a58fd4931cf3ef286b3f'
EMULATOR_SHA = '653dfa9797ea524723c3eef98fd532776de9a056a1de2ef389dce782ab92037d'
CONTRACT = '54eec4dbf9612af033f15aafede8e1704ffaf21ff1a5c78544a184bff5f45b0e'
AUTHORITY = '821edfaf18174b702b555bc742c81cb6ef96d2ca6e757e281f4ea0f74c883a9d'
OWNER_UUID = '38902a30-9ea2-4566-89d7-ca0e40625492'
OLD_AVD_UUID = 'e9171d3e-8a1d-497c-86a2-28811fa8c567'
PORTS = {5048, 5588, 5589}
GIB = 1024 ** 3
OS_CONSOLE = Path('C:/Windows/System32/conhost.exe')
OS_CONSOLE_SHA = 'c86146188b78e484f32cc29e2331689659943b47689a85570342044f499938ac'


class Refused(RuntimeError):
    pass


def require(value, code):
    if not value:
        raise Refused(code)


def utc():
    return datetime.now(timezone.utc).isoformat()


def sha(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def no_links(path):
    for item in [path, *path.parents]:
        try:
            info = item.lstat()
        except FileNotFoundError:
            continue
        require(not item.is_symlink() and not getattr(info, 'st_file_attributes', 0) & 0x400,
                'REPARSE_REFUSED')


def read_json(path):
    no_links(path)
    require(path.stat().st_size <= 16 * 1024 * 1024, 'JSON_TOO_LARGE')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'DUPLICATE_JSON_KEY')
            result[key] = value
        return result
    def invalid_constant(value):
        raise Refused('NONFINITE_JSON_CONSTANT')
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique, parse_constant=invalid_constant)


def write_json(path, data):
    no_links(path)
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(data, indent=2, sort_keys=True, ensure_ascii=False)+'\n')


def owner_check():
    no_links(EVIDENCE)
    owner = read_json(EVIDENCE/'.owner.json')
    require(owner['resource_id'] == OWNER_UUID and Path(owner['root']) == EVIDENCE and
            Path(owner['runtime_root']) == RUNTIME, 'EVIDENCE_OWNER_MISMATCH')
    require(sha(BASE/'docs/prompts/STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0.md') == CONTRACT,
            'CURRENT_CONTRACT_CHANGED')
    require(sha(BASE/'.local-evidence/stage12/main/resume-emulator-20261024.json') == AUTHORITY,
            'RESUME_RECEIPT_CHANGED')
    old = read_json(AVD_ROOT/'.stage12-owner.json')
    require(old['schema'] == 'stage12-avd-owner-v1' and old['name'] == AVD_NAME and
            Path(old['root']) == AVD_ROOT and old['session_id'] == OLD_AVD_UUID,
            'ORIGINAL_AVD_OWNER_MISMATCH')
    return owner


def tree_inventory(root):
    no_links(root)
    require(root.is_dir(), 'TREE_MISSING')
    files, directories, folded = [], [], set()
    for path in sorted(root.rglob('*')):
        no_links(path)
        relative = path.relative_to(root).as_posix()
        require(relative.casefold() not in folded, 'TREE_CASE_COLLISION')
        folded.add(relative.casefold())
        if path.is_dir():
            directories.append(relative)
        else:
            require(path.is_file(), 'SPECIAL_FILE_REFUSED')
            files.append({'path': relative, 'bytes': path.stat().st_size, 'sha256': sha(path)})
    return {'files': files, 'directories': directories, 'bytes': sum(x['bytes'] for x in files)}


def listeners(ports=PORTS, pids=None, connections=False):
    """Read the OS TCP table, retain only declared resource ports; never connect."""
    library = ctypes.WinDLL('iphlpapi', use_last_error=True)
    fn = library.GetExtendedTcpTable
    fn.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_ulong), ctypes.c_bool,
                   ctypes.c_ulong, ctypes.c_int, ctypes.c_ulong]
    fn.restype = ctypes.c_ulong
    result = []
    for family, row_size in [(2, 24), (23, 56)]:
        size = ctypes.c_ulong()
        table_class = 5 if connections else 3
        status = fn(None, ctypes.byref(size), False, family, table_class, 0)
        require(status in (0, 122) and 0 < size.value <= 8*1024*1024, 'TCP_TABLE_SIZE_FAILED')
        buffer = ctypes.create_string_buffer(size.value)
        require(fn(buffer, ctypes.byref(size), False, family, table_class, 0) == 0, 'TCP_TABLE_READ_FAILED')
        raw = buffer.raw
        count = struct.unpack_from('<I', raw)[0]
        require(4+count*row_size <= len(raw), 'TCP_TABLE_TRUNCATED')
        for index in range(count):
            offset = 4+index*row_size
            if family == 2:
                state, address, port_raw, remote_raw, remote_port_raw, pid = struct.unpack_from('<6I', raw, offset)
                address = socket.inet_ntoa(struct.pack('<I', address))
                remote = socket.inet_ntoa(struct.pack('<I', remote_raw))
            else:
                address = socket.inet_ntop(socket.AF_INET6, raw[offset:offset+16])
                port_raw = struct.unpack_from('<I', raw, offset+20)[0]
                pid = struct.unpack_from('<I', raw, offset+52)[0]
                state = struct.unpack_from('<I', raw, offset+48)[0]
                remote = socket.inet_ntop(socket.AF_INET6, raw[offset+24:offset+40])
                remote_port_raw = struct.unpack_from('<I', raw, offset+44)[0]
            port = socket.ntohs(port_raw & 0xffff)
            if (ports is None or port in ports) and (pids is None or pid in pids):
                record = {'address': address, 'port': port, 'pid': pid}
                if connections:
                    record.update(remote_address=remote, remote_port=socket.ntohs(remote_port_raw & 0xffff), state=state)
                result.append(record)
    return sorted(result, key=lambda x: (x['port'], x['address'], x['pid']))


def host_preflight():
    class Memory(ctypes.Structure):
        _fields_ = [('length', ctypes.c_ulong), ('load', ctypes.c_ulong)] + [
            (name, ctypes.c_ulonglong) for name in ['total', 'available', 'page_total', 'page_available',
                                                   'virtual_total', 'virtual_available', 'extended']]
    memory = Memory(); memory.length = ctypes.sizeof(Memory)
    fn = ctypes.WinDLL('kernel32', use_last_error=True).GlobalMemoryStatusEx
    fn.argtypes = [ctypes.POINTER(Memory)]; fn.restype = ctypes.c_bool
    require(fn(ctypes.byref(memory)), 'RAM_OBSERVATION_FAILED')
    disk = shutil.disk_usage(Path('C:/Users/User/.cache'))
    observed = {'utc': utc(), 'free_ram_bytes': memory.available, 'free_disk_bytes': disk.free,
                'reserved_listeners': listeners()}
    require(memory.available >= 4*GIB and disk.free >= 12*GIB, 'HOST_CAPACITY_TOO_LOW')
    require(not observed['reserved_listeners'], 'RESERVED_PORT_OCCUPIED')
    return observed


def child_environment(role):
    require(role in {'canary', 'emulator', 'server'}, 'UNKNOWN_PROCESS_ROLE')
    env = {'SystemRoot': 'C:/Windows', 'WINDIR': 'C:/Windows',
           'COMSPEC': 'C:/Windows/System32/cmd.exe', 'SystemDrive': 'C:',
           'PATH': 'C:/Windows/System32;C:/Windows',
           'USERPROFILE': (RUNTIME/'profile').as_posix(), 'HOME': (RUNTIME/'profile').as_posix(),
           'APPDATA': (RUNTIME/'profile/AppData/Roaming').as_posix(),
           'LOCALAPPDATA': (RUNTIME/'profile/AppData/Local').as_posix(),
           'TEMP': (RUNTIME/'tmp').as_posix(), 'TMP': (RUNTIME/'tmp').as_posix(),
           'TMPDIR': (RUNTIME/'tmp').as_posix(),
           'XDG_RUNTIME_DIR': (RUNTIME/'tmp').as_posix(),
           'ANDROID_HOME': SDK.as_posix(), 'ANDROID_SDK_ROOT': SDK.as_posix(),
           'ANDROID_AVD_HOME': (AVD_ROOT/'avd').as_posix(),
           'ANDROID_USER_HOME': (AVD_ROOT/'user').as_posix(),
           'ANDROID_EMULATOR_HOME': (AVD_ROOT/'user').as_posix(),
           'ADB_VENDOR_KEYS': (AVD_ROOT/'keys').as_posix(),
           'ADB_USB': '0', 'ADB_EMU': '0', 'ADB_MDNS': '0', 'ADB_MDNS_AUTO_CONNECT': '0',
           'ADB_REJECT_KILL_SERVER': '1', 'ADB_SERVER_SOCKET': 'tcp:127.0.0.1:5048',
           'ANDROID_ADB_SERVER_ADDRESS': '127.0.0.1', 'ANDROID_ADB_SERVER_PORT': '5048',
           'ANDROID_SERIAL': SERIAL}
    # Server is an explicit callsite of original pinned ADB, never placed in PATH/SDK.
    if role == 'server':
        env['ANDROID_HOME'] = ORIGINAL_SDK.as_posix()
        env['ANDROID_SDK_ROOT'] = ORIGINAL_SDK.as_posix()
    return env


def validate_environment(env, role):
    require(env == child_environment(role), 'CHILD_ENVIRONMENT_CHANGED')
    for directory in env['PATH'].split(';'):
        require(not (Path(directory)/'adb.exe').exists(), 'REAL_ADB_ON_PATH')
    require(env['ANDROID_ADB_SERVER_PORT'] == '5048', 'NATIVE_ADB_PORT_CHANGED')


def verify_private_sdk(expected=None):
    owner_check()
    no_links(RUNTIME)
    owner = read_json(RUNTIME/'.owner.json')
    require(owner['resource_id'] == OWNER_UUID and Path(owner['root']) == RUNTIME,
            'RUNTIME_OWNER_MISMATCH')
    manifest = read_json(EVIDENCE/'sdk-copy.json') if expected is None else expected
    validate_avd_route(manifest)
    validate_image_inputs(manifest)
    no_links(ORIGINAL_SDK/'platform-tools/adb.exe')
    require(sha(ORIGINAL_SDK/'platform-tools/adb.exe') == ADB_SHA, 'ORIGINAL_ADB_PIN_CHANGED_BEFORE_LAUNCH')
    actual = tree_inventory(SDK/'emulator')
    require(actual == manifest['emulator_package'], 'PRIVATE_EMULATOR_BYTES_CHANGED')
    platform = SDK/'platform-tools'
    require(sorted(p.name for p in platform.iterdir()) == ['adb.exe'], 'EXTRA_PRIVATE_PLATFORM_TOOLS')
    no_links(platform/'adb.exe')
    require(sha(platform/'adb.exe') == STUB_SHA, 'INERT_STUB_CHANGED')
    require(sha(SDK/'emulator/emulator.exe') == EMULATOR_SHA, 'EMULATOR_LAUNCHER_CHANGED')
    validate_environment(child_environment('emulator'), 'emulator')
    return manifest


def validate_image_inputs(manifest):
    names = {'source.properties','package.xml','build.prop','system.img','vendor.img','ramdisk.img','kernel-ranchu'}
    items = manifest['image_files_readonly_inputs']
    require(len(items) == len(names) and {Path(item['path']).name for item in items} == names,
            'ORIGINAL_IMAGE_MANIFEST_NOT_EXACT')
    for item in items:
        path = Path(item['path'])
        require(path.parent == IMAGE, 'ORIGINAL_IMAGE_INPUT_PATH_CHANGED')
        no_links(path)
        require(path.is_file() and path.stat().st_size == item['bytes'] and sha(path) == item['sha256'],
                'ORIGINAL_IMAGE_CHANGED_BEFORE_LAUNCH')


def parse_ini(path):
    no_links(path)
    require(path.stat().st_size <= 1024*1024, 'AVD_INI_TOO_LARGE')
    values = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        if not line.strip() or line.lstrip().startswith(('#', ';')):
            continue
        require('=' in line, 'AVD_INI_MALFORMED')
        key, value = line.split('=', 1)
        key, value = key.strip(), value.strip()
        require(key and key not in values, 'AVD_INI_DUPLICATE_KEY')
        values[key] = value
    return values


def validate_avd_route(manifest):
    for item in manifest['original_avd_files_before_first_boot']:
        relative = Path(item['path'])
        require(not relative.is_absolute() and '..' not in relative.parts, 'AVD_MANIFEST_PATH_ESCAPES')
        path = AVD_ROOT/relative; no_links(path)
        require(path.is_file() and path.stat().st_size == item['bytes'] and sha(path) == item['sha256'],
                'ORIGINAL_AVD_CONFIG_OR_INDEX_CHANGED')
    avd_dir = AVD_ROOT/'avd'/(AVD_NAME+'.avd')
    index = parse_ini(AVD_ROOT/'avd'/(AVD_NAME+'.ini'))
    require(Path(index.get('path', '')) == avd_dir and index.get('target') == 'android-36',
            'AVD_INDEX_NOT_OWN_ROUTE')
    config = parse_ini(avd_dir/'config.ini')
    require(config.get('image.sysdir.1') == 'system-images/android-36/default/x86_64/' and
            config.get('hw.cpu.arch') == 'x86_64' and config.get('hw.cpu.ncore') == '2' and
            config.get('hw.ramSize') == '2048' and config.get('disk.dataPartition.size') == '6G',
            'AVD_RESOURCE_OR_IMAGE_CONFIG_CHANGED')
    no_links(avd_dir)


def prepare():
    owner = owner_check()
    no_links(RUNTIME)
    require(not RUNTIME.exists() and not (EVIDENCE/'sdk-copy.json').exists(), 'PREPARATION_ALREADY_EXISTS')
    observed = host_preflight()
    source = ORIGINAL_SDK/'emulator'
    original = tree_inventory(source)
    require(sha(source/'emulator.exe') == EMULATOR_SHA, 'SOURCE_EMULATOR_CHANGED')
    no_links(STUB_SOURCE)
    require(sha(STUB_SOURCE) == STUB_SHA, 'SOURCE_STUB_CHANGED')
    require(observed['free_disk_bytes'] >= 12*GIB+original['bytes'], 'SPACE_AFTER_COPY_TOO_LOW')
    require(sha(ORIGINAL_SDK/'platform-tools/adb.exe') == ADB_SHA, 'SOURCE_ADB_CHANGED')
    image_metadata = []
    for name in ['source.properties', 'package.xml', 'build.prop', 'system.img', 'vendor.img', 'ramdisk.img', 'kernel-ranchu']:
        path = IMAGE/name; no_links(path)
        image_metadata.append({'path': path.as_posix(), 'bytes': path.stat().st_size,
                               'sha256': sha(path), 'mtime_ns': path.stat().st_mtime_ns})
    avd_files = []
    for path in sorted(AVD_ROOT.rglob('*')):
        no_links(path)
        if path.is_file():
            require(path.name not in {'adbkey', 'adbkey.pub', '.emulator_console_auth_token'}, 'UNEXPECTED_PRIOR_PRIVATE_KEY_BEFORE_FIRST_BOOT')
            avd_files.append({'path': path.relative_to(AVD_ROOT).as_posix(), 'bytes': path.stat().st_size, 'sha256': sha(path)})
    RUNTIME.mkdir(parents=True, exist_ok=False)
    write_json(RUNTIME/'.owner.json', {'schema': 'stage12-private-runtime-owner-v1',
      'resource_id': owner['resource_id'], 'root': RUNTIME.as_posix(),
      'evidence': EVIDENCE.as_posix(), 'created_utc': utc(), 'contract_sha256': CONTRACT})
    SDK.mkdir(); (SDK/'platform-tools').mkdir()
    for relative in ['tmp', 'cwd', 'profile/AppData/Local', 'profile/AppData/Roaming']:
        (RUNTIME/relative).mkdir(parents=True, exist_ok=False)
    shutil.copytree(source, SDK/'emulator', copy_function=shutil.copy2, symlinks=False)
    shutil.copy2(STUB_SOURCE, SDK/'platform-tools/adb.exe')
    copied = tree_inventory(SDK/'emulator')
    require(copied == original, 'EMULATOR_COPY_DIFFERS')
    require(tree_inventory(source) == original, 'SOURCE_CHANGED_DURING_COPY')
    receipt = {'schema': 'stage12-private-sdk-copy-v1', 'utc': utc(),
      'resource_id': OWNER_UUID, 'runtime_root': RUNTIME.as_posix(),
      'source_emulator_root': source.as_posix(), 'emulator_package': original,
      'stub': {'source': STUB_SOURCE.as_posix(), 'copy': (SDK/'platform-tools/adb.exe').as_posix(), 'sha256': STUB_SHA},
      'image_files_readonly_inputs': image_metadata, 'full_image_bytes_hashed': True,
      'original_avd_files_before_first_boot': avd_files, 'original_avd_owner': read_json(AVD_ROOT/'.stage12-owner.json'),
      'host_before_copy': observed, 'source_and_copy_equal': True,
      'sdk_execution_count': 0, 'vm_boot_count': 0, 'server_start_count': 0,
      'operator_sha256': sha(Path(__file__)),
      'environments': {r: child_environment(r) for r in ['canary', 'emulator', 'server']}}
    write_json(EVIDENCE/'sdk-copy.json', receipt)
    verify_private_sdk(receipt)
    print(json.dumps({'status': 'PRIVATE_SDK_COPIED_NO_EXECUTION', 'files': len(original['files']),
                      'bytes': original['bytes'], 'receipt_sha256': sha(EVIDENCE/'sdk-copy.json')}, ensure_ascii=False), flush=True)


def canary_specs(include_diagnostic=False):
    executable = str(SDK/'emulator/emulator.exe')
    result = [
      {'name': 'inert-adb-version', 'argv': [str(SDK/'platform-tools/adb.exe'), 'version'], 'expected_exit': 113},
      {'name': 'emulator-version', 'argv': [executable, '-version', '-no-metrics', '-crash-report-mode', 'disabled'], 'expected_exit': 0},
      {'name': 'emulator-accel-check', 'argv': [executable, '-accel-check', '-no-metrics', '-crash-report-mode', 'disabled'], 'expected_exit': 0},
      {'name': 'emulator-help-gpu', 'argv': [executable, '-help-gpu', '-no-metrics', '-crash-report-mode', 'disabled'], 'expected_exit': 0},
    ]
    if include_diagnostic:
        result.append({'name': 'emulator-version-original-diagnostic',
          'argv': [executable, '-version'], 'expected_exit': 0})
    return result


def validate_canary_spec(spec):
    require(spec in canary_specs(include_diagnostic=True), 'UNREVIEWED_CANARY_COMMAND')
    require(not any(x in {'-avd', '-qemu', 'server', 'start-server', 'devices'} for x in spec['argv']),
            'CANARY_MUST_NOT_BOOT_OR_DISCOVER')


def executable_allowlist(role, manifest):
    require(role in {'canary', 'emulator', 'server'}, 'UNKNOWN_PROCESS_ROLE')
    known = {item['path']: item['sha256'] for item in manifest['emulator_package']['files']}
    # Legitimate installed package helpers are declared before boot, never accepted by basename alone.
    names = ['emulator.exe', 'emulator-check.exe', 'qemu/windows-x86_64/qemu-system-x86_64.exe',
             'qemu/windows-x86_64/qemu-system-x86_64-headless.exe', 'netsimd.exe', 'crashpad_handler.exe',
             'qemu-img.exe', 'mksdcard.exe', 'bin64/e2fsck.exe', 'bin64/resize2fs.exe', 'bin64/tune2fs.exe']
    allowed = {str(SDK/'emulator'/name): known[name] for name in names}
    allowed[str(SDK/'platform-tools/adb.exe')] = STUB_SHA
    if role == 'server':
        return {str(ORIGINAL_SDK/'platform-tools/adb.exe'): ADB_SHA}
    return allowed


def same_file_identity(one, two):
    # An explicit pair of native handles, never generic path rewriting or basename equivalence.
    keys = ['volume_serial', 'file_index', 'bytes', 'attributes', 'links', 'last_write_filetime']
    return (all(type(one.get(k)) is int and type(two.get(k)) is int and one[k] == two[k] for k in keys)
            and one['volume_serial'] > 0 and one['file_index'] > 0 and one['bytes'] > 0
            and one['last_write_filetime'] > 0 and one['links'] == 1
            and not one['attributes'] & (0x400 | 0x10))


def prove_image(observation, allowed, file_identity):
    require(type(observation) is dict and type(observation.get('pid')) is int and observation['pid'] > 0 and
            type(observation.get('creation_filetime')) is int and observation['creation_filetime'] > 0,
            'PROCESS_IDENTITY_INCOMPLETE')
    observed = observation.get('image')
    require(type(observed) is str and observed, 'PROCESS_IMAGE_MISSING')
    if observation.get('image_format') == 'NATIVE_SYSTEM':
        validate_native_queries(observation)
        for name, expected in allowed.items():
            approved = Path(name); no_links(approved)
            before = file_identity(approved)
            validate_native_file_name(before)
            if observed != before['native_final_path']:
                continue
            require(sha(approved) == expected, 'APPROVED_PROCESS_BINARY_CHANGED')
            after = file_identity(approved)
            validate_native_file_name(after)
            require(same_file_identity(before, after) and
                    before['native_final_path'] == after['native_final_path'] == observed,
                    'PINNED_DOS_FILE_NATIVE_IDENTITY_CHANGED')
            return {'process': observation, 'approved_path': name, 'approved_sha256': expected,
                    'proof_kind': 'TWO_NATIVE_PROCESS_QUERIES_EXACT_PINNED_DOS_HANDLE_PATH',
                    'approved_dos_metadata_before': before, 'approved_dos_metadata_after': after,
                    'observed_native_path_opened_or_converted': False,
                    'process_section_file_id_claimed': False}
        raise Refused('NATIVE_PROCESS_PATH_OUTSIDE_DECLARED_PINNED_DOS_FILE_IDENTITIES')
    require(observation.get('image_format', 'WIN32') == 'WIN32', 'UNKNOWN_PROCESS_IMAGE_FORMAT')
    # Open an observed image for metadata ONLY; read content solely via a known pinned path.
    observed_file = file_identity(Path(observed))
    for name, expected in allowed.items():
        approved = Path(name)
        no_links(approved)
        approved_file = file_identity(approved)
        if same_file_identity(approved_file, observed_file):
            require(sha(approved) == expected, 'APPROVED_PROCESS_BINARY_CHANGED')
            require(same_file_identity(approved_file, file_identity(approved)) and
                    same_file_identity(observed_file, file_identity(Path(observed))), 'PROCESS_FILE_CHANGED_DURING_PROOF')
            return {'process': observation, 'approved_path': name, 'approved_sha256': expected,
                    'approved_native_metadata': approved_file, 'observed_native_metadata': observed_file}
    raise Refused('PROCESS_OUTSIDE_DECLARED_EXACT_FILE_IDENTITIES')


def validate_native_queries(observation):
    image = observation['image']
    require(type(image) is str and image.startswith('\\Device\\') and not any(ord(c) < 32 for c in image), 'NATIVE_PROCESS_IMAGE_INVALID')
    queries = observation.get('native_queries')
    require(type(queries) is list and len(queries) == 2, 'TWO_NATIVE_IMAGE_QUERIES_REQUIRED')
    for query, api, flags in zip(queries, ['QueryFullProcessImageNameW', 'K32GetProcessImageFileNameW'], [1, None]):
        require(type(query) is dict and query.get('api') == api and
                ((type(query.get('flags')) is int and query['flags'] == 1) if flags == 1 else query.get('flags') is None) and
                query.get('success') is True and type(query.get('win32_error')) is int and query['win32_error'] == 0 and
                type(query.get('characters')) is int and query['characters'] == len(image) and
                query.get('raw_image') == image, 'NATIVE_IMAGE_QUERY_PAIR_DISAGREES_OR_IS_PARTIAL')


def validate_native_file_name(metadata):
    name = metadata.get('native_final_path')
    require(type(name) is str and name.startswith('\\Device\\') and not any(ord(c) < 32 for c in name) and
            type(metadata.get('native_final_path_flags')) is int and metadata['native_final_path_flags'] == 2 and
            type(metadata.get('native_final_path_length')) is int and metadata['native_final_path_length'] == len(name),
            'PINNED_DOS_HANDLE_NATIVE_PATH_NOT_MEASURED_FULLY')


def console_metadata_matches(actual, expected):
    # Only the explicit OS role has this measured hardlink policy. SDK stays link1.
    validate_native_file_name(actual)
    validate_native_file_name(expected)
    keys = ['volume_serial', 'file_index', 'bytes', 'attributes', 'links', 'last_write_filetime',
            'native_final_path', 'native_final_path_flags', 'native_final_path_length']
    return (all(type(actual.get(k)) is type(expected.get(k)) and actual[k] == expected[k] for k in keys)
            and all(type(actual[k]) is int and actual[k] > 0 for k in
                    ['volume_serial', 'file_index', 'bytes', 'links', 'last_write_filetime'])
            and type(actual['attributes']) is int and not actual['attributes'] & (0x400 | 0x10)
            and actual.get('metadata_stable') is True and expected.get('metadata_stable') is True)


def load_console_pin(file_identity):
    pin = read_json(EVIDENCE/'console-helper-policy/os-console-pin.json')
    require(pin.get('schema') == 'stage12-exact-os-console-file-pin-v1' and
            pin.get('path') == OS_CONSOLE.as_posix() and pin.get('sha256') == OS_CONSOLE_SHA and
            type(pin.get('metadata')) is dict,
            'OS_CONSOLE_PIN_NOT_EXACT')
    no_links(OS_CONSOLE)
    before = file_identity(OS_CONSOLE)
    require(console_metadata_matches(before, pin['metadata']), 'OS_CONSOLE_METADATA_NOT_PINNED')
    require(sha(OS_CONSOLE) == OS_CONSOLE_SHA, 'OS_CONSOLE_HASH_CHANGED')
    after = file_identity(OS_CONSOLE)
    require(console_metadata_matches(after, before) and console_metadata_matches(after, pin['metadata']),
            'OS_CONSOLE_FILE_CHANGED_DURING_PREFLIGHT')
    return pin


def prove_live_member(observation, role, manifest, console_pin, file_identity):
    require(role in {'server', 'emulator'}, 'OS_POLICY_ROLE_NOT_ADMITTED')
    require(observation.get('in_owned_job') is True and observation.get('running') is True and
            observation.get('image_format') == 'NATIVE_SYSTEM' and
            type(observation.get('pid')) is int and observation['pid'] > 0 and
            type(observation.get('creation_filetime')) is int and observation['creation_filetime'] > 0,
            'LIVE_NATIVE_OWNED_MEMBER_REQUIRED')
    validate_native_queries(observation)
    if observation['image'] != console_pin['metadata']['native_final_path']:
        return dict(prove_image(observation, executable_allowlist(role, manifest), file_identity), role='SDK')
    require(console_pin['path'] == OS_CONSOLE.as_posix() and console_pin['sha256'] == OS_CONSOLE_SHA,
            'OS_CONSOLE_PIN_NOT_EXACT')
    no_links(OS_CONSOLE)
    before = file_identity(OS_CONSOLE)
    require(console_metadata_matches(before, console_pin['metadata']) and
            before['native_final_path'] == observation['image'], 'OS_CONSOLE_MEMBER_FILE_IDENTITY_DIFFERS')
    require(sha(OS_CONSOLE) == OS_CONSOLE_SHA, 'OS_CONSOLE_MEMBER_HASH_CHANGED')
    after = file_identity(OS_CONSOLE)
    require(console_metadata_matches(after, before) and console_metadata_matches(after, console_pin['metadata']),
            'OS_CONSOLE_MEMBER_FILE_CHANGED')
    return {'role': 'OS_CONSOLE', 'process': observation, 'approved_path': OS_CONSOLE.as_posix(),
            'approved_sha256': OS_CONSOLE_SHA, 'approved_dos_metadata_before': before,
            'approved_dos_metadata_after': after, 'endpoint_permission': 'NONE',
            'observed_native_path_opened_or_converted': False, 'process_section_file_id_claimed': False}


def validate_console_tcp(connections, proofs):
    console_pids = {p['process']['pid'] for p in proofs if p.get('role') == 'OS_CONSOLE'}
    require(not any(row['pid'] in console_pids for row in connections), 'OS_CONSOLE_TCP_ENDPOINT_REFUSED')


def capture_job(job, child, role, manifest, console_pin, file_identity, journal, prior_origin=None):
    """Flush independent observations and full errors before wrapper policy checks."""
    snapshot = {'role': role, 'proofs': [], 'observation_errors': []}
    first_error = None

    def observe(name, operation):
        nonlocal first_error
        try:
            value = operation()
            snapshot[name] = value
            journal.event('job-observation', role=role, operation=name, value=value)
            return value
        except Exception as error:
            first_error = first_error or error
            record = {'operation': name, **exception_record(error)}
            snapshot['observation_errors'].append(record)
            journal.event('job-observation-error', role=role, **record)
            return None

    def root_origin():
        if role == 'server':
            current = child.identity()
            return {'image_measurement': current, 'current_liveness_claimed': current['running']}
        if prior_origin is None:
            return original_handle_image_observation(job, child)
        current = child.identity()
        measured = prior_origin['image_measurement']
        require(current['pid'] == measured['pid'] and current['creation_filetime'] == measured['creation_filetime'],
                'EMULATOR_ORIGINAL_HANDLE_BIRTH_CHANGED')
        return {'image_measurement': current if current['running'] else measured,
                'current_original_handle': current, 'current_liveness_claimed': current['running'],
                'image_measurement_phase': 'CURRENT_SAME_ORIGINAL_HANDLE' if current['running'] else
                    'PREVIOUSLY_MEASURED_ORIGINAL_HANDLE_ORIGIN_NOT_CURRENT_LIVENESS'}

    observe('retained_roots', lambda: job.roots)
    origin = observe('root_origin', root_origin)
    if origin is not None:
        observe('root_proof', lambda: prove_image(origin['image_measurement'], executable_allowlist(role, manifest), file_identity))
    members = observe('members', job.members)
    if members is not None:
        for index, member in enumerate(members):
            proof = observe('member-proof-'+str(index),
                            lambda item=member: prove_live_member(item, role, manifest, console_pin, file_identity))
            if proof is not None:
                snapshot['proofs'].append(proof)
    state = observe('handle_bookkeeping', lambda: {
        'source': 'PINNED_HELPER_BOOKKEEPING_NOT_NEW_NATIVE_MEASUREMENT',
        'root_pids': sorted(job._children), 'member_pids': sorted(job._member_handles),
        'job_handle_retained': not job._job_closed, 'pending_handles': len(job._pending_handles)})
    pids = set(state['root_pids']+state['member_pids']) if state is not None else {child.pid}
    complete_tcp = None
    def own_tcp():
        nonlocal complete_tcp
        complete_tcp = listeners(ports=None, connections=True)
        return [row for row in complete_tcp if row['pid'] in pids]
    own_connections = observe('tcp-query', own_tcp)
    def listener_view(owned):
        require(type(complete_tcp) is list, 'TCP_SNAPSHOT_UNAVAILABLE')
        return [{key: row[key] for key in ['address', 'port', 'pid']} for row in complete_tcp
                if row['state'] == 2 and (row['pid'] in pids if owned else row['port'] in PORTS)]
    observe('reserved_listeners', lambda: listener_view(False))
    observe('own_listeners', lambda: listener_view(True))
    # Only current own handle PIDs are persisted; no unrelated host TCP inventory.
    snapshot['own_connections'] = own_connections
    observe('root_poll', child.poll)
    journal.event('job-capture-before-policy', snapshot=snapshot)
    if first_error is not None:
        raise first_error
    require(len({m['pid'] for m in members}) == len(members), 'DUPLICATE_JOB_MEMBER_PID')
    validate_console_tcp(own_connections, snapshot['proofs'])
    return snapshot, complete_tcp


def validate_server_capture(snapshot, child_pid, initial_birth, *, require_listener=True, before_engine=False):
    roots = [m for m in snapshot['members'] if m['pid'] == child_pid]
    origin = snapshot['root_origin']['image_measurement']
    proof = snapshot['root_proof']
    require(len(roots) == 1 and roots[0]['creation_filetime'] == initial_birth and
            origin['pid'] == child_pid and origin['creation_filetime'] == initial_birth and
            origin['running'] is True and snapshot['root_poll'] is None and
            Path(proof['approved_path']) == ORIGINAL_SDK/'platform-tools/adb.exe' and
            proof['approved_sha256'] == ADB_SHA and proof['process']['pid'] == child_pid and
            proof['process']['creation_filetime'] == initial_birth, 'PRIVATE_SERVER_ROOT_IDENTITY_DIFFERS')
    require(len(snapshot['proofs']) == len(snapshot['members']) and
            {p['process']['pid'] for p in snapshot['proofs']} == {m['pid'] for m in snapshot['members']},
            'PRIVATE_SERVER_MEMBER_PROOFS_INCOMPLETE')
    require(all(p['process']['pid'] == child_pid or p.get('role') == 'OS_CONSOLE' for p in snapshot['proofs']),
            'PRIVATE_SERVER_NON_ROOT_ROLE_REFUSED')
    rows = [row for row in snapshot['reserved_listeners'] if row['port'] == 5048]
    if require_listener or rows:
        require(rows == [{'address': '127.0.0.1', 'port': 5048, 'pid': child_pid}], 'PRIVATE_SERVER_LISTENER_DIFFERS')
    if before_engine:
        require(not any(row['port'] in {5588, 5589} for row in snapshot['reserved_listeners']),
                'ENGINE_ENDPOINT_EXISTS_BEFORE_OWN_BOOT')


def validate_emulator_capture(snapshot, complete_tcp, server_capture, journal, phase, *, ready=False):
    """Apply the same role/peer policy at startup, ready transport and shutdown."""
    members = snapshot['members']+server_capture['members']
    pids = {m['pid'] for m in members}
    own = snapshot['own_connections']
    correlated = [peer for peer in complete_tcp if any(
        row['state'] in {3,4,5} and is_loopback(row['remote_address']) and
        peer['address'] == row['remote_address'] and peer['port'] == row['remote_port'] and
        peer['remote_address'] == row['address'] and peer['remote_port'] == row['port'] and
        peer['state'] in {3,4,5} for row in own)]
    journal.event('emulator-network-before-policy', phase=phase,
                  native_connections=[row for row in complete_tcp if row['pid'] in pids],
                  correlated_local_peers=correlated)
    validate_console_tcp(complete_tcp, snapshot['proofs']+server_capture['proofs'])
    validate_auxiliary_endpoints(snapshot['own_listeners'], snapshot['proofs'])
    engines = engine_member_pids(snapshot['proofs'])
    server_pid = server_capture['root_origin']['image_measurement']['pid']
    root_listener = {'address':'127.0.0.1','port':5048,'pid':server_pid}
    require([row for row in snapshot['reserved_listeners'] if row['port'] == 5048] == [root_listener],
            'PRIVATE_SERVER_LISTENER_DIFFERS')
    engine_rows = [row for row in snapshot['reserved_listeners'] if row['port'] in {5588,5589}]
    require(all(row['pid'] in engines and row['address'] in {'127.0.0.1','::1'} for row in engine_rows) and
            len({row['pid'] for row in engine_rows}) <= 1, 'ENGINE_ENDPOINT_NOT_OWNED_OR_NOT_LOOPBACK')
    if ready:
        validate_listener_owners(snapshot['reserved_listeners'], server_pid, engines, 'engine')
    validate_local_peers(own, complete_tcp, pids | {os.getpid()}, snapshot['own_listeners']+[root_listener])
    return engines


def validate_listener_owners(observed, server_pid, engine_pids, phase):
    require(phase in {'server', 'engine'}, 'UNKNOWN_LISTENER_PHASE')
    expected_server = {'address': '127.0.0.1', 'port': 5048, 'pid': server_pid}
    require([x for x in observed if x['port'] == 5048] == [expected_server], 'PRIVATE_SERVER_LISTENER_DIFFERS')
    engine = [x for x in observed if x['port'] in {5588, 5589}]
    if phase == 'server':
        require(not engine, 'ENGINE_ENDPOINT_EXISTS_BEFORE_OWN_BOOT')
    else:
        require({x['port'] for x in engine} == {5588, 5589} and len({x['pid'] for x in engine}) == 1 and all(
            x['pid'] in engine_pids and x['address'] in {'127.0.0.1', '::1'} for x in engine),
            'ENGINE_ENDPOINT_NOT_OWNED_OR_NOT_LOOPBACK')


def close_job_record(job):
    # Always retain a failed cleanup receipt, and let another owned job close too.
    try:
        if job is None:
            return {'not_created': True, 'empty_confirmed': True, 'handles_closed': True}
        return job.close()
    except Exception as error:
        return {'empty_confirmed': False, 'handles_closed': False, 'error_type': type(error).__name__,
                'native_close_receipt': getattr(error, 'receipt', None) or getattr(job, 'close_receipt', None),
                'exception': exception_record(error)}


def close_proven(record):
    return record.get('empty_confirmed') is True and record.get('handles_closed') is True


def original_handle_image_observation(job, child):
    """A fast completed canary retains its measured suspended original identity.

    Never substitutes an identity after a query failure on a live process. The
    two records remain separate so historical image data cannot imply liveness.
    """
    before = [item for item in job.roots if item['pid'] == child.pid]
    require(len(before) == 1, 'ORIGINAL_ROOT_RECORD_NOT_UNIQUE')
    current = child.identity()  # live failures propagate, with no fallback
    require(current['pid'] == before[0]['pid'] and
            current['creation_filetime'] == before[0]['creation_filetime'], 'ORIGINAL_ROOT_BIRTH_CHANGED')
    if current['running'] is False and current.get('image_source') == 'RETAINED_PRE_EXIT_OBSERVATION':
        return {'image_measurement': before[0], 'current_original_handle': current,
                'image_measurement_phase': 'SUSPENDED_PRE_RESUME_RETAINED_ORIGINAL_HANDLE',
                'current_liveness_claimed': False}
    require(current['running'] is True, 'CANARY_CURRENT_IMAGE_NOT_MEASURED')
    return {'image_measurement': current, 'current_original_handle': current,
            'image_measurement_phase': 'CURRENT_SAME_ORIGINAL_HANDLE', 'current_liveness_claimed': True}


def validate_acceleration_text(text):
    require(type(text) is str and re.search(r'(?m)^WHPX\([0-9.]+\) is installed and usable\.?\s*$', text) is not None,
            'ACCELERATION_NOT_PROVEN_NO_IMPLICIT_TCG')
    return 'WHPX_INSTALLED_AND_USABLE_POSITIVE_LINE'


def exception_record(error):
    return {'error_type': type(error).__name__, 'code': str(error),
            'win32_error': getattr(error, 'win32_error', None), 'native_receipt': getattr(error, 'receipt', None)}


def run_canaries(attempt=None, only=None):
    manifest = verify_private_sdk()
    before = host_preflight()
    require(attempt is None or re.fullmatch(r'r[1-9][0-9]?', attempt), 'INVALID_CANARY_ATTEMPT_ID')
    require(only is None or only in {s['name'] for s in canary_specs(include_diagnostic=True)}, 'UNKNOWN_CANARY_SELECTOR')
    root = EVIDENCE/('host-canaries' if attempt is None else 'host-canaries-'+attempt)
    require(not root.exists(), 'CANARY_ATTEMPT_EXISTS_PRESERVE_NO_OVERWRITE')
    root.mkdir()
    from stage12_winjob import OwnedJob, NativeImageBackend, native_file_identity
    results = []
    env = child_environment('canary'); validate_environment(env, 'canary')
    try:
        selected_specs = canary_specs() if only is None else [s for s in canary_specs(include_diagnostic=True) if s['name'] == only]
        for spec in selected_specs:
            validate_canary_spec(spec)
            require(not listeners(), 'RESERVED_PORT_OPENED_BEFORE_HOST_CANARY')
            command = dict(spec, environment=env, cwd=(RUNTIME/'cwd').as_posix(), timeout_seconds=30,
                           identity_protocol='NATIVE_SYSTEM_TWO_APIS_TO_PINNED_DOS_FILE_HANDLE_V1',
                           operator_sha256=sha(Path(__file__)), winjob_sha256=sha(Path(__file__).with_name('stage12_winjob.py')))
            write_json(root/(spec['name']+'-command.json'), command)
            job = OwnedJob(backend=NativeImageBackend()); child = None; started = time.monotonic()
            record = dict(name=spec['name'], utc=utc(), status='RUNNING')
            try:
                child = job.launch(spec['argv'], env, RUNTIME/'cwd', root/(spec['name']+'-stdout.txt'), root/(spec['name']+'-stderr.txt'))
                record['original_handle_identity'] = original_handle_image_observation(job, child)
                record['identity_proof'] = prove_image(record['original_handle_identity']['image_measurement'], executable_allowlist('canary', manifest), native_file_identity)
                record['returncode'] = child.wait(30)
                record['members_before_close'] = job.members()
                record['member_image_proofs'] = [prove_image(member, executable_allowlist('canary', manifest), native_file_identity)
                                                for member in record['members_before_close']]
                record['status'] = 'PASSED' if record['returncode'] == spec['expected_exit'] else 'UNEXPECTED_EXIT'
            except Exception as error:
                record['status'] = 'FAILED_PRESERVED'
                record['exception'] = exception_record(error)
                raise
            finally:
                record['close'] = close_job_record(job)
                record['elapsed_seconds'] = round(time.monotonic()-started, 3)
                record['reserved_listeners_after'] = listeners()
                for stream in ['stdout', 'stderr']:
                    path = root/(spec['name']+'-'+stream+'.txt')
                    if path.exists():
                        record[stream] = {'path': path.relative_to(BASE).as_posix(), 'bytes': path.stat().st_size, 'sha256': sha(path)}
                write_json(root/(spec['name']+'-result.json'), record)
                results.append(record)
            require(record['status'] == 'PASSED' and close_proven(record['close']) and
                    not record['reserved_listeners_after'], 'HOST_CANARY_FAILED_NO_BOOT')
        if only is not None:
            write_json(root/'subset-result.json', {'utc': utc(), 'status': 'HOST_SUBSET_PASSED_NO_BOOT',
              'selected': only, 'results': results, 'sdk_manifest_sha256': sha(EVIDENCE/'sdk-copy.json')})
            print(json.dumps({'status':'HOST_SUBSET_PASSED_NO_BOOT','selected':only,'root':root.as_posix()}), flush=True)
            return
        version = (root/'emulator-version-stdout.txt').read_text(encoding='utf-8', errors='replace') + (root/'emulator-version-stderr.txt').read_text(encoding='utf-8', errors='replace')
        require('37.2.12.0' in version and '16428233' in version, 'COPIED_EMULATOR_VERSION_DIFFERS')
        accel = (root/'emulator-accel-check-stdout.txt').read_text(encoding='utf-8', errors='replace') + (root/'emulator-accel-check-stderr.txt').read_text(encoding='utf-8', errors='replace')
        validate_acceleration_text(accel)
        gpu = (root/'emulator-help-gpu-stdout.txt').read_text(encoding='utf-8', errors='replace') + (root/'emulator-help-gpu-stderr.txt').read_text(encoding='utf-8', errors='replace')
        require('swiftshader' in gpu.lower(), 'SOFTWARE_GPU_PROFILE_NOT_DOCUMENTED')
        write_json(EVIDENCE/'host-canaries.json', {'schema': 'stage12-private-sdk-host-canaries-v1',
          'utc': utc(), 'status': 'PASSED_NO_AVD_NO_SERVER', 'before': before,
          'commands': results, 'acceleration_profile': 'WHPX; explicit -accel on; no TCG fallback',
          'graphics_profile': 'explicit -gpu swiftshader; software graphics distinct from CPU acceleration',
          'sdk_manifest_sha256': sha(EVIDENCE/'sdk-copy.json'), 'vm_boot_count': 0,
          'server_start_count': 0, 'all_owned_jobs_closed': True})
    except Exception as error:
        write_json(root/'adverse.json', {'utc': utc(), **exception_record(error), 'results': results,
          'no_automatic_retry': True, 'vm_boot_count': 0, 'server_start_count': 0})
        raise
    print(json.dumps({'status': 'HOST_CANARIES_PASSED_NO_BOOT', 'receipt_sha256': sha(EVIDENCE/'host-canaries.json')}), flush=True)


def boot_spec():
    return {'server_argv': [str(ORIGINAL_SDK/'platform-tools/adb.exe'), '-L', 'tcp:5048',
              '--one-device', SERIAL, 'server', 'nodaemon'],
      'emulator_argv': [str(SDK/'emulator/emulator.exe'), '-avd', AVD_NAME,
        '-datadir', str(AVD_ROOT/'avd'/(AVD_NAME+'.avd')),
        '-data', str(AVD_ROOT/'avd'/(AVD_NAME+'.avd')/'userdata-qemu.img'),
        '-sysdir', str(IMAGE), '-ports', '5588,5589', '-adb-path', str(SDK/'platform-tools/adb.exe'),
        '-no-window', '-no-snapshot-load', '-no-snapshot-save', '-no-snapstorage',
        '-no-audio', '-no-metrics', '-crash-report-mode', 'disabled',
        '-cores', '2', '-memory', '2048', '-accel', 'on', '-gpu', 'swiftshader'],
      'server_timeout_seconds': 8, 'boot_timeout_seconds': 300, 'shutdown_timeout_seconds': 60,
      'resource_owner_uuid': OWNER_UUID, 'serial': SERIAL, 'server_port': 5048,
      'identity_protocol': 'NATIVE_SYSTEM_TWO_APIS_TO_PINNED_DOS_FILE_HANDLE_V1',
      'os_console_role': {'path': OS_CONSOLE.as_posix(), 'sha256': OS_CONSOLE_SHA,
        'pin_file': (EVIDENCE/'console-helper-policy/os-console-pin.json').as_posix(),
        'policy': 'Explicit OS_CONSOLE member only; exact measured pinned hardlink count/metadata/NT/hash and dual native live own-job identity. Never ADB root/SDK motor/transport. No TCP endpoint in sampled table; SDK link1 stays unchanged.'},
      'prospective_unit': {'checkpoint': (EVIDENCE/'console-helper-policy/checkpoint.json').as_posix(),
        'claim': (EVIDENCE/'console-helper-policy/first-boot-r1-claim.json').as_posix(),
        'run': (EVIDENCE/'console-helper-policy/first-boot-r1').as_posix()},
      'console_token_route': {'private_candidates': [p.as_posix() for p in console_token_candidates()],
        'selection': 'Exactly one quoted path announced by the console banner; exact full predeclared spellings only, no real home/expansion/foreign/ambiguous/reparse path; select before any file read.',
        'secrecy': 'Token bytes exist only for the auth socket send, never in argv/errors/journal/evidence.'},
      'native_identity_limit': 'Two native process APIs and exact native path from pinned approved DOS file handles, stable file metadata/hash; does not open/convert observed NT path or claim a kernel process-section file-ID measurement. Historical flags0 error5 remains an adverse.',
      'console_port': 5588, 'adb_endpoint_port': 5589,
      'sdk_root': SDK.as_posix(), 'avd_root': AVD_ROOT.as_posix(), 'sysdir': IMAGE.as_posix(),
      'no_snapshot_wipe_reseed_restore': True,
      'cpu_acceleration': 'WHPX explicit on, fail rather than TCG fallback',
      'graphics': 'swiftshader software GPU',
      'grpc': 'Not enabled: installed -help-ports documents -ports requires explicit -grpc; no -grpc argument supplied',
      'auxiliary_endpoints': 'Predeclared optional netsimd local virtual-radio endpoints may use own dynamically allocated loopback TCP ports only; bind addresses, exact owning image/file hash/PID/birth and job are recorded before acceptance. Any non-loopback listener or non-declared helper aborts; no attach to a pre-existing auxiliary service.',
      'helpers': [
        {'path': (SDK/'emulator/emulator-check.exe').as_posix(), 'role': 'Optional installed acceleration checker; same job/env, no AVD of its own'},
        {'path': (SDK/'emulator/qemu/windows-x86_64/qemu-system-x86_64.exe').as_posix(), 'role': 'CPU/VM motor; inherits same job/env, owns5588/5589'},
        {'path': (SDK/'emulator/qemu/windows-x86_64/qemu-system-x86_64-headless.exe').as_posix(), 'role': 'Installed headless variant of the same x86_64 motor; mutually selected by launcher, same ownership checks'},
        *[{'path': (SDK/'emulator'/name).as_posix(), 'role': 'Installed disk preparation helper for Stage12-owned first-boot overlays/userdata only; no independent VM/server'}
          for name in ['qemu-img.exe', 'mksdcard.exe', 'bin64/e2fsck.exe', 'bin64/resize2fs.exe', 'bin64/tune2fs.exe']],
        {'path': (SDK/'emulator/netsimd.exe').as_posix(), 'role': 'Optional local virtual radio helper, same job/env/private user and temp; no phone API'},
        {'path': (SDK/'emulator/crashpad_handler.exe').as_posix(), 'role': 'Optional installed local crash helper; reporting explicitly disabled; same job and private directories'},
        {'path': (SDK/'platform-tools/adb.exe').as_posix(), 'role': 'Inert refusal only, intentional exit113; never originalADB in emulator job'},
      ],
      'first_boot_actions_only': ['owned private server version+empty inventory',
        'owned engine port proofs before explicit registration', 'unique selected serial and API/AOSP/ABI/AVD properties',
        'guest sync', 'owned console authenticated graceful kill', 'job and server close', 'original image hash conservation'],
      'not_admitted': ['QA install', 'instrumentation', 'business writes', 'capture', 'common ADB', 'Samsung']}


class Journal:
    def __init__(self, root):
        self.root = root
        self.path = root/'events.jsonl'
        self.stream = self.path.open('x', encoding='utf-8', newline='\n')

    def event(self, name, **data):
        self.stream.write(json.dumps(dict(utc=utc(), event=name, **data), ensure_ascii=False, sort_keys=True)+'\n')
        self.stream.flush()

    def close(self):
        self.stream.close()


def exact_read(connection, size, deadline=None):
    require(type(size) is int and 0 <= size <= 8192, 'SOCKET_READ_SIZE_REFUSED')
    data = b''
    deadline = time.monotonic()+5 if deadline is None else deadline
    while len(data) < size:
        remaining = deadline-time.monotonic()
        require(remaining > 0, 'ABSOLUTE_SMART_SOCKET_DEADLINE')
        connection.settimeout(min(2, remaining))
        part = connection.recv(size-len(data))
        require(bool(part), 'PARTIAL_SMART_SOCKET_REPLY')
        data += part
    return data


def send_service(connection, service):
    data = service.encode('ascii')
    require(0 < len(data) <= 1024, 'SERVICE_SIZE_REFUSED')
    connection.sendall(f'{len(data):04x}'.encode('ascii')+data)
    require(exact_read(connection, 4) == b'OKAY', 'DEDICATED_SERVER_SERVICE_REJECTED')


def read_payload(connection):
    deadline = time.monotonic()+5
    length = exact_read(connection, 4, deadline)
    require(all(c in b'0123456789abcdefABCDEF' for c in length), 'INVALID_SMART_SOCKET_LENGTH')
    return exact_read(connection, int(length, 16), deadline)


def console_read_ok(connection, cap=8192, seconds=3):
    data = b''
    deadline = time.monotonic()+seconds
    while b'OK\r\n' not in data:
        remaining = deadline-time.monotonic()
        require(remaining > 0, 'ABSOLUTE_OWN_CONSOLE_DEADLINE')
        connection.settimeout(min(2, remaining))
        part = connection.recv(1024)
        require(bool(part), 'OWN_CONSOLE_EOF_BEFORE_OK')
        data += part
        require(len(data) <= cap and b'KO' not in data, 'OWN_CONSOLE_REPLY_INVALID')
    return data


def console_token_candidates():
    return (RUNTIME/'profile/.emulator_console_auth_token', AVD_ROOT/'user/.emulator_console_auth_token')


def select_console_token_path(banner):
    """Select only an exact private path announced by the console, before I/O."""
    require(type(banner) is bytes and len(banner) <= 8192 and b'\0' not in banner,
            'OWN_CONSOLE_TOKEN_ROUTE_BANNER_INVALID')
    try:
        text = banner.decode('utf-8')
    except UnicodeError as error:
        raise Refused('OWN_CONSOLE_TOKEN_ROUTE_ENCODING_INVALID') from error
    routes = []
    for line in text.splitlines():
        line = line.strip()
        if len(line) >= 2 and line[0] in {'"', "'"} and line[-1] == line[0]:
            routes.append(line[1:-1])
    require(len(routes) == 1, 'OWN_CONSOLE_TOKEN_ROUTE_MISSING_OR_AMBIGUOUS')
    permitted = {}
    for candidate in console_token_candidates():
        # Generate full spellings only from the two known private paths. Never
        # normalize, expand or construct a Path from a route supplied by a banner.
        spellings = {candidate.as_posix(), str(candidate),
                     candidate.parent.as_posix()+'\\'+candidate.name,
                     str(candidate.parent)+'/'+candidate.name}
        for spelling in spellings:
            require(spelling not in permitted or permitted[spelling] == candidate,
                    'PRIVATE_TOKEN_CANDIDATES_AMBIGUOUS')
            permitted[spelling] = candidate
    require(routes[0] in permitted, 'OWN_CONSOLE_TOKEN_ROUTE_OUTSIDE_PRIVATE_CANDIDATES')
    selected = permitted[routes[0]]
    no_links(selected)
    return selected


def read_private_console_token(banner):
    selected = select_console_token_path(banner)
    require(selected.is_file() and selected.stat().st_size <= 1024, 'OWN_CONSOLE_TOKEN_INVALID')
    token = selected.read_bytes().strip()
    require(0 < len(token) <= 256 and all(32 < value < 127 for value in token), 'OWN_CONSOLE_TOKEN_INVALID')
    return token


def validate_inventory(payload, phase):
    require(phase in {'before', 'after'}, 'UNKNOWN_INVENTORY_PHASE')
    require(type(payload) is bytes and len(payload) <= 8192, 'INVENTORY_TOO_LARGE')
    try:
        lines = payload.decode('utf-8').splitlines()
    except UnicodeError as error:
        raise Refused('INVENTORY_INVALID_ENCODING') from error
    if phase == 'before':
        require(not lines, 'PRIVATE_SERVER_NOT_EMPTY')
    else:
        require(len(lines) == 1 and lines[0].split()[:2] == [SERIAL, 'device'],
                'PRIVATE_SERVER_HAS_UNEXPECTED_TRANSPORT_OR_STATE')
    return {'phase': phase, 'count': len(lines), 'expected_only': True,
            'payload_sha256': hashlib.sha256(payload).hexdigest()}


PROPERTY_EXPECTATIONS = {
    'ro.build.version.sdk': '36', 'ro.product.cpu.abi': 'x86_64',
    'ro.build.id': 'BE2A.250530.026.D1', 'ro.kernel.qemu': '1',
    'ro.boot.qemu.avd_name': AVD_NAME,
}
SHELL_COMMANDS = {'boot_complete': 'getprop sys.boot_completed', 'sync': 'sync && echo STAGE12_SYNC_OK'}
SHELL_COMMANDS.update({name: 'getprop '+name for name in PROPERTY_EXPECTATIONS})


class PrivateClient:
    def __init__(self, guard, journal, connector=socket.create_connection):
        self.guard, self.journal, self.connector = guard, journal, connector

    def host(self, service):
        require(service in {'host:version', 'host:devices-l', 'host:emulator:5589'}, 'UNREVIEWED_HOST_SERVICE')
        self.guard()
        with self.connector(('127.0.0.1', 5048), 2) as connection:
            connection.settimeout(2)
            if service == 'host:emulator:5589':
                # Official adb.cpp emulator handler sends NO status reply. Observe EOF,
                # then prove the selected inventory independently; never manufacture OKAY.
                # https://android.googlesource.com/platform/packages/modules/adb/+/fdbd8a5f26c58722ddf414afc591fcdbca5389d4/adb.cpp
                data = service.encode('ascii')
                connection.sendall(f'{len(data):04x}'.encode('ascii')+data)
                connection.shutdown(socket.SHUT_WR)
                require(connection.recv(1) == b'', 'UNEXPECTED_REGISTRATION_REPLY')
                payload = b''
            else:
                send_service(connection, service)
                payload = read_payload(connection)
        self.guard()
        self.journal.event('private-host-exchange', service=service, payload_bytes=len(payload),
                           payload_sha256=hashlib.sha256(payload).hexdigest())
        return payload

    def shell(self, name):
        require(name in SHELL_COMMANDS, 'UNREVIEWED_FIRST_BOOT_SHELL')
        self.guard()
        with self.connector(('127.0.0.1', 5048), 5) as connection:
            connection.settimeout(5)
            send_service(connection, 'host:transport:'+SERIAL)
            send_service(connection, 'shell:'+SHELL_COMMANDS[name])
            payload = b''
            deadline = time.monotonic()+10
            while True:
                remaining = deadline-time.monotonic()
                require(remaining > 0, 'ABSOLUTE_SELECTED_SHELL_DEADLINE')
                connection.settimeout(min(5, remaining))
                part = connection.recv(4096)
                if not part:
                    break
                payload += part
                require(len(payload) <= 8192, 'FIRST_BOOT_SHELL_OUTPUT_TOO_LARGE')
        self.guard()
        text = payload.decode('utf-8').strip()
        self.journal.event('selected-own-property-or-sync', selector=name, observed=text)
        return text


def validate_runtime_admission(admission, checkpoint):
    require(type(admission) is dict and admission.get('schema') == 'stage12-g3-r2-runtime-admission-v1',
            'NEW_MAIN_RUNTIME_ADMISSION_REQUIRED')
    require(admission.get('contract_sha256') == CONTRACT and admission.get('resource_owner_uuid') == OWNER_UUID and
            admission.get('operator_sha256') == sha(Path(__file__)) and
            admission.get('winjob_sha256') == sha(Path(__file__).with_name('stage12_winjob.py')) and
            admission.get('checkpoint_sha256') == sha(checkpoint) and
            admission.get('allowed_action') == 'FIRST_BOOT_IDENTITY_SYNC_CLOSE_ONLY' and
            admission.get('runtime_boot_admitted') is True, 'MAIN_ADMISSION_DOES_NOT_BIND_THIS_CHECKPOINT')
    validate_checkpoint(checkpoint)


def validate_checkpoint(path):
    data = read_json(path)
    require(data.get('schema') == 'stage12-g3-r2-checkpoint-v1' and
            data.get('resource_owner_uuid') == OWNER_UUID and data.get('boot_protocol') == boot_spec(),
            'CHECKPOINT_PROTOCOL_OR_OWNER_DIFFERS')
    require(type(data.get('pins')) is list and 4 <= len(data['pins']) <= 32 and
            len({item['path'] for item in data['pins']}) == len(data['pins']), 'CHECKPOINT_PINS_NOT_EXACT_UNIQUE')
    for item in data['pins']:
        relative = Path(item['path'])
        require(not relative.is_absolute() and '..' not in relative.parts, 'CHECKPOINT_PIN_PATH_ESCAPES')
        target = BASE/relative; no_links(target)
        require(target.is_file() and target.stat().st_size == item['bytes'] and sha(target) == item['sha256'],
                'CHECKPOINT_PIN_CHANGED')
    pinned = {item['path'] for item in data['pins']}
    require({(EVIDENCE/name).relative_to(BASE).as_posix() for name in ['sdk-copy.json','host-canaries.json','image-source-complete.json']} <= pinned,
            'SDK_COPY_AND_CANARIES_NOT_BOUND_IN_CHECKPOINT')
    require({(EVIDENCE/'console-helper-policy'/name).relative_to(BASE).as_posix()
             for name in ['os-console-pin.json', '.owner.json']} <= pinned,
            'OS_CONSOLE_PIN_AND_UNIT_OWNER_NOT_BOUND')
    require(read_json(EVIDENCE/'host-canaries.json')['sdk_manifest_sha256'] == sha(EVIDENCE/'sdk-copy.json'),
            'CANARIES_NOT_FROM_PINNED_SDK_COPY')
    image_complete = read_json(EVIDENCE/'image-source-complete.json')
    require(image_complete['path'] == IMAGE.as_posix() and
            tree_inventory(IMAGE) == image_complete['inventory'], 'COMPLETE_INSTALLED_IMAGE_INPUTS_CHANGED')
    spec = importlib.util.spec_from_file_location('stage12_current_boot_inputs', BASE/'tools/verify.py')
    verify = importlib.util.module_from_spec(spec); spec.loader.exec_module(verify)
    current = verify.snapshot(BASE, 'android')
    expected = read_json(EVIDENCE/'entry.json')['snapshot']
    require(current['head'] == '932eaeb60c6b6e68975534788e3e6501c967b1ae' and
            current['branch'] == 'codex/miguardia-3.0' and
            current['inputs_sha256'] == expected['inputs_sha256'], 'ANDROID_SOURCE_INPUT_CHANGED_BEFORE_FIRST_BOOT')


def validate_auxiliary_endpoints(observed, proofs):
    role = {p['process']['pid']: Path(p['approved_path']) for p in proofs}
    engines = {SDK/'emulator/qemu/windows-x86_64/qemu-system-x86_64.exe',
               SDK/'emulator/qemu/windows-x86_64/qemu-system-x86_64-headless.exe'}
    for item in observed:
        require(item['address'] in {'127.0.0.1','::1'} and item['pid'] in role,
                'OWN_VM_OR_HELPER_NON_LOOPBACK_LISTENER')
        if role[item['pid']] in engines:
            require(item['port'] in {5588,5589}, 'UNDECLARED_ENGINE_TCP_LISTENER')
        else:
            require(role[item['pid']] == SDK/'emulator/netsimd.exe' and 1024 <= item['port'] <= 65535 and
                    item['port'] not in {5037,5048,5588,5589}, 'UNDECLARED_HELPER_TCP_LISTENER')


def engine_member_pids(proofs):
    paths = {SDK/'emulator/qemu/windows-x86_64/qemu-system-x86_64.exe',
             SDK/'emulator/qemu/windows-x86_64/qemu-system-x86_64-headless.exe'}
    return {p['process']['pid'] for p in proofs if p.get('role') == 'SDK' and Path(p['approved_path']) in paths}


def validate_local_peers(owned_connections, complete_tcp, owned_pids, owned_listeners):
    for item in owned_connections:
        require(item['port'] != 5037 and item['remote_port'] != 5037,
                'OWN_NATIVE_CLIENT_COMMON_ADB_CONNECTION_OBSERVED')
        if item['state'] not in {3,4,5} or not is_loopback(item['remote_address']):
            continue
        peers = [peer for peer in complete_tcp if peer['address'] == item['remote_address'] and
                 peer['port'] == item['remote_port'] and peer['remote_address'] == item['address'] and
                 peer['remote_port'] == item['port'] and peer['state'] in {3,4,5}]
        if peers:
            require(all(peer['pid'] in owned_pids for peer in peers), 'FOREIGN_LOCAL_PEER_TO_OWN_VM_OR_HELPER')
        else:
            # An in-progress outbound connect may only target an already proved
            # own listening endpoint; never adopt a cached auxiliary service.
            require(item['state'] in {3,4} and any(listener['address'] == item['remote_address'] and
                    listener['port'] == item['remote_port'] and listener['pid'] in owned_pids
                    for listener in owned_listeners), 'LOCAL_PEER_NOT_PROVEN_OWNED')


def is_loopback(value):
    address = ipaddress.ip_address(value)
    return address.is_loopback or (isinstance(address, ipaddress.IPv6Address) and
                                  address.ipv4_mapped is not None and address.ipv4_mapped.is_loopback)


def finalize_first_boot_status(result):
    failures = []
    if result['status'] != 'BOOT_IDENTITY_SYNC_OBSERVED_PENDING_CLOSE':
        failures.append('BOOT_IDENTITY_OR_SYNC_NOT_COMPLETE')
    if not close_proven(result['emulator_job_close']): failures.append('EMULATOR_CLOSE_UNCONFIRMED')
    if not close_proven(result['server_job_close']): failures.append('SERVER_CLOSE_UNCONFIRMED')
    if result['reserved_listeners_after_close'] != []: failures.append('RESERVED_PORTS_NOT_PROVEN_FREE')
    if result['original_images_unchanged'] is not True: failures.append('ORIGINAL_IMAGES_NOT_PROVEN_UNCHANGED')
    if result['post_observation_errors']: failures.append('POST_OBSERVATION_FAILED')
    result['finalization_failures'] = failures
    result['status'] = 'FAILED_PRESERVED_NO_AUTOMATIC_RETRY' if failures else 'FIRST_BOOT_IDENTITY_SYNC_CLOSE_PASSED_NO_INSTALL'
    return result


def first_boot(admission_path, admission_sha):
    require(admission_path is not None and admission_sha is not None, 'ADMISSION_PATH_AND_SHA_REQUIRED')
    no_links(admission_path)
    require(admission_path.parent == BASE/'.local-evidence/stage12/main' and sha(admission_path) == admission_sha,
            'MAIN_ADMISSION_PATH_OR_SHA_DIFFERS')
    unit = EVIDENCE/'console-helper-policy'
    checkpoint = unit/'checkpoint.json'
    validate_runtime_admission(read_json(admission_path), checkpoint)
    manifest = verify_private_sdk()
    from stage12_winjob import OwnedJob, NativeImageBackend, native_file_identity
    console_pin = load_console_pin(native_file_identity)
    owner = read_json(unit/'.owner.json')
    require(owner.get('resource_owner_uuid') == OWNER_UUID and owner.get('root') == unit.as_posix(),
            'PROSPECTIVE_BOOT_UNIT_OWNER_DIFFERS')
    canaries = read_json(EVIDENCE/'host-canaries.json')
    require(canaries['status'] == 'PASSED_NO_AVD_NO_SERVER', 'HOST_CANARIES_NOT_PASSED')
    require(not (unit/'first-boot-r1-claim.json').exists() and not (unit/'first-boot-r1').exists(),
            'FIRST_BOOT_ALREADY_CLAIMED_NO_AUTOMATIC_RETRY')
    observed = host_preflight()
    run_id = str(uuid.uuid4())
    write_json(unit/'first-boot-r1-claim.json', {'utc': utc(), 'run_uuid': run_id,
       'resource_owner_uuid': OWNER_UUID, 'admission_sha256': admission_sha, 'checkpoint_sha256': sha(checkpoint)})
    root = unit/'first-boot-r1'; root.mkdir(exist_ok=False)
    journal = Journal(root)
    server_job = emulator_job = None
    server = emulator = None
    result = {'schema': 'stage12-private-sdk-first-boot-v1', 'utc': utc(), 'run_uuid': run_id,
              'resource_owner_uuid': OWNER_UUID, 'status': 'RUNNING', 'host_before': observed,
              'admission_sha256': admission_sha, 'checkpoint_sha256': sha(checkpoint),
              'install_or_instrumentation_count': 0, 'properties': {}}
    engine_ready = False
    try:
        server_job = OwnedJob(backend=NativeImageBackend()); emulator_job = OwnedJob(backend=NativeImageBackend())
        spec = boot_spec()
        server_env = child_environment('server'); validate_environment(server_env, 'server')
        server = server_job.launch(spec['server_argv'], server_env, RUNTIME/'cwd', root/'server-stdout.txt', root/'server-stderr.txt')
        server_initial = None

        def server_guard(*, require_listener=True, before_engine=False):
            nonlocal server_initial
            captured, _ = capture_job(server_job, server, 'server', manifest, console_pin, native_file_identity, journal)
            if server_initial is None:
                roots = captured['retained_roots']
                require(len(roots) == 1 and roots[0]['pid'] == server.pid, 'SERVER_ORIGINAL_ROOT_NOT_UNIQUE')
                server_initial = roots[0]
            validate_server_capture(captured, server.pid, server_initial['creation_filetime'],
                                    require_listener=require_listener, before_engine=before_engine)
            return captured

        deadline = time.monotonic()+spec['server_timeout_seconds']
        while True:
            starting = server_guard(require_listener=False, before_engine=True)
            if any(row['port'] == 5048 for row in starting['reserved_listeners']):
                validate_server_capture(starting, server.pid, server_initial['creation_filetime'], before_engine=True)
                break
            require(time.monotonic() < deadline, 'PRIVATE_SERVER_LISTENER_START_TIMEOUT')
            time.sleep(0.1)
        client = PrivateClient(server_guard, journal)
        require(client.host('host:version') == b'0029', 'PRIVATE_ADB_PROTOCOL_VERSION_DIFFERS')
        journal.event('empty-before', inventory=validate_inventory(client.host('host:devices-l'), 'before'))
        server_guard(before_engine=True)
        emulator_env = child_environment('emulator'); validate_environment(emulator_env, 'emulator')
        emulator = emulator_job.launch(spec['emulator_argv'], emulator_env, RUNTIME/'cwd', root/'emulator-stdout.txt', root/'emulator-stderr.txt')
        launcher_origin = None
        deadline = time.monotonic()+spec['boot_timeout_seconds']
        last_progress = 0

        def engine_guard():
            nonlocal launcher_origin
            server_capture = server_guard()
            captured, complete_tcp = capture_job(emulator_job, emulator, 'emulator', manifest, console_pin,
                                                 native_file_identity, journal, launcher_origin)
            launcher_origin = launcher_origin or captured['root_origin']
            members = captured['members']
            require(bool(members), 'EMULATOR_JOB_EMPTY')
            return validate_emulator_capture(captured, complete_tcp, server_capture, journal, 'engine', ready=True)

        while time.monotonic() < deadline:
            server_capture = server_guard()
            startup, tcp = capture_job(emulator_job, emulator, 'emulator', manifest, console_pin,
                                      native_file_identity, journal, launcher_origin)
            launcher_origin = launcher_origin or startup['root_origin']
            require(bool(startup['members']), 'EMULATOR_EXITED_BEFORE_ENDPOINTS')
            validate_emulator_capture(startup, tcp, server_capture, journal, 'startup')
            if {x['port'] for x in startup['reserved_listeners']} >= PORTS:
                engine_guard(); engine_ready = True; break
            if time.monotonic()-last_progress >= 10:
                print(json.dumps({'phase': 'waiting_owned_engine_endpoints', 'remaining_seconds': round(deadline-time.monotonic())}), flush=True)
                last_progress = time.monotonic()
            time.sleep(0.5)
        require(engine_ready, 'OWN_ENGINE_ENDPOINT_TIMEOUT')
        client = PrivateClient(engine_guard, journal)
        # This controller's registration is forbidden until BOTH engine endpoints are proved owned.
        client.host('host:emulator:5589')
        online = False
        while time.monotonic() < deadline:
            engine_guard()
            inventory = client.host('host:devices-l')
            try:
                validated = validate_inventory(inventory, 'after')
                online = True
                journal.event('unique-own-inventory', inventory=validated)
                break
            except Refused:
                # Only an empty list or the exact owned serial in offline state is a boot wait.
                lines = inventory.decode('utf-8').splitlines()
                require(not lines or (len(lines) == 1 and lines[0].split()[:2] == [SERIAL, 'offline']),
                        'UNEXPECTED_TRANSPORT_DURING_BOOT')
            time.sleep(0.5)
        require(online, 'OWN_TRANSPORT_ONLINE_TIMEOUT')
        for name, expected in PROPERTY_EXPECTATIONS.items():
            engine_guard()
            actual = client.shell(name); result['properties'][name] = actual
            require(actual == expected, 'OWN_AVD_PROPERTY_DIFFERS_'+name)
        complete = False
        while time.monotonic() < deadline:
            engine_guard()
            if client.shell('boot_complete') == '1':
                complete = True; break
            if time.monotonic()-last_progress >= 10:
                print(json.dumps({'phase': 'waiting_own_android_boot_complete', 'remaining_seconds': round(deadline-time.monotonic())}), flush=True)
                last_progress = time.monotonic()
            time.sleep(1)
        require(complete, 'ANDROID_BOOT_COMPLETE_TIMEOUT')
        require(client.shell('sync') == 'STAGE12_SYNC_OK', 'GUEST_SYNC_NOT_CONFIRMED')
        result['guest_sync_confirmed'] = True
        # Token is read only from this pre-owned AVD; never persisted in logs/receipts/argv.
        engine_guard()
        console = socket.create_connection(('127.0.0.1', 5588), 2)
        try:
            console.settimeout(2)
            banner = console_read_ok(console)
            require(b'Android Console' in banner and b'Authentication required' in banner, 'OWN_CONSOLE_AUTH_EXPECTATION_DIFFERS')
            token = read_private_console_token(banner)
            console.sendall(b'auth '+token+b'\n'); token = b''
            console_read_ok(console, cap=4096)
            engine_guard()
            console.sendall(b'kill\n')
            result['own_console_graceful_kill_requested'] = True
        finally:
            console.close()
        close_deadline = time.monotonic()+spec['shutdown_timeout_seconds']
        closing = None
        while time.monotonic() < close_deadline:
            server_capture = server_guard()
            closing, tcp = capture_job(emulator_job, emulator, 'emulator', manifest, console_pin,
                                      native_file_identity, journal, launcher_origin)
            validate_emulator_capture(closing, tcp, server_capture, journal, 'shutdown')
            if not closing['members']:
                break
            time.sleep(0.5)
        result['emulator_members_before_job_close'] = closing['members'] if closing is not None else None
        require(type(result['emulator_members_before_job_close']) is list and
                not result['emulator_members_before_job_close'], 'GRACEFUL_EMULATOR_SHUTDOWN_TIMEOUT')
        result['status'] = 'BOOT_IDENTITY_SYNC_OBSERVED_PENDING_CLOSE'
    except Exception as error:
        result['status'] = 'FAILED_PRESERVED_NO_AUTOMATIC_RETRY'
        result['exception'] = exception_record(error)
        journal.event('failed', **result['exception'])
    finally:
        result['emulator_job_close'] = close_job_record(emulator_job)
        result['server_job_close'] = close_job_record(server_job)
        # Persist native cleanup before optional post-observations/hashing, so an
        # I/O/observation failure cannot erase proof or prevent another job close.
        write_json(root/'cleanup.json', {'utc': utc(), 'emulator': result['emulator_job_close'],
                                        'server': result['server_job_close']})
        result['post_observation_errors'] = []
        try:
            result['reserved_listeners_after_close'] = listeners()
        except Exception as error:
            result['reserved_listeners_after_close'] = None
            result['post_observation_errors'].append({'operation': 'listeners', 'error_type': type(error).__name__})
        result['finished_utc'] = utc()
        result['image_hashes_after'] = []
        try:
            result['image_hashes_after'] = [{'path': item['path'], 'sha256': sha(Path(item['path']))}
                                          for item in manifest['image_files_readonly_inputs']]
            result['original_images_unchanged'] = all(item['sha256'] == after['sha256'] for item, after in
                                                    zip(manifest['image_files_readonly_inputs'], result['image_hashes_after']))
            complete_before = read_json(EVIDENCE/'image-source-complete.json')['inventory']
            result['complete_original_image_inventory_after'] = tree_inventory(IMAGE)
            result['original_image_package_unchanged'] = result['complete_original_image_inventory_after'] == complete_before
            result['original_images_unchanged'] = result['original_images_unchanged'] and result['original_image_package_unchanged']
        except Exception as error:
            result['original_images_unchanged'] = False
            result['post_observation_errors'].append({'operation': 'image_hashes', 'error_type': type(error).__name__})
        journal.close()
        finalize_first_boot_status(result)
        write_json(root/'result.json', result)
    require(result['status'] == 'FIRST_BOOT_IDENTITY_SYNC_CLOSE_PASSED_NO_INSTALL' and
            close_proven(result['emulator_job_close']) and close_proven(result['server_job_close']) and
            result['reserved_listeners_after_close'] == [] and not result['post_observation_errors'] and
            result['original_images_unchanged'], 'FIRST_BOOT_FAILED_PRESERVED')
    print(json.dumps({'status': result['status'], 'result_sha256': sha(root/'result.json')}), flush=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=['prepare', 'canaries', 'first-boot'])
    parser.add_argument('--admission', type=Path)
    parser.add_argument('--admission-sha256')
    parser.add_argument('--attempt')
    parser.add_argument('--only-canary')
    args = parser.parse_args(argv)
    try:
        if args.operation == 'prepare':
            prepare()
        elif args.operation == 'canaries':
            run_canaries(args.attempt, args.only_canary)
        elif args.operation == 'first-boot':
            first_boot(args.admission, args.admission_sha256)
        return 0
    except Exception as error:
        print(json.dumps({'status': 'REFUSED_OR_FAILED', **exception_record(error)}), flush=True)
        raise


if __name__ == '__main__':
    raise SystemExit(main())
