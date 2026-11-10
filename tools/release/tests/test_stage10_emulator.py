"""Offline guards and corruption probes; these tests do not execute ADB or an AVD."""
from contextlib import closing
import copy
import json
from pathlib import Path
import sqlite3
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import stage10_emulator as device
import stage10_emulator_capture as capture
import stage10_capture as old
import stage10_runtime_compare as wire
from test_stage10_runtime_compare import raw_preferences, blob
from test_upgrade_v6_v8 import create_source, materialize_target, UUID

SPEC = {'id':'3cbd51f6-3091-4272-a1a2-a2efbe488aee','consent':capture.CONSENT,'started_at':1792713600000,
        'short_end':1792714500000,'long_end':1792886400000}
SPEC_R2 = SPEC | {'consent':capture.CONSENT_R2,'short_end':SPEC['started_at']+1800000}


class Stage10EmulatorGuardsTest(unittest.TestCase):
    def test_only_exact_first_boot_ini_format_and_six_gib_expansion_are_allowed(self):
        original = b'abi.type=x86_64\r\nimage.sysdir.1=system-images/android-36/default/x86_64/\r\ndisk.dataPartition.size=4G\r\nhw.cpu.ncore=2\r\n'
        after = b'abi.type = x86_64\r\r\nimage.sysdir.1 = system-images/android-36/default/x86_64/\r\r\ndisk.dataPartition.size = 6442450944\r\r\nhw.cpu.ncore = 2\r\r\n'
        device.require_avd_config(original,after)
        for wrong in (after.replace(b'x86_64',b'arm64'),after.replace(b'6442450944',b'999'),after.replace(b'ncore = 2',b'ncore = 4'),after+b'new.key=yes\n',after+b'abi.type=x86_64\n'):
            with self.assertRaises(ValueError): device.require_avd_config(original,wrong)

    def test_continuation_refuses_any_prior_android_command(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary); (root/'adb-001-start.json').write_text('{}')
            with patch.object(device.subprocess,'run') as command:
                with self.assertRaises(ValueError): device.continue_before_install(root,root/'qa6',root/'qa8',root/'runner')
                command.assert_not_called()

    def test_remote_adb_overrides_are_removed_and_endpoint_is_explicit_local(self):
        inherited = {'PATH':'keep','ADB_SERVER_SOCKET':'tcp:remote:9000','ANDROID_ADB_SERVER_ADDRESS':'remote',
                     'ANDROID_ADB_SERVER_PORT':'9000','ADB_SERVER_PORT':'9000','ANDROID_SERIAL':'physical','ADB_TRACE':'all'}
        self.assertEqual(device.adb_environment(inherited), {'PATH':'keep'})
        self.assertEqual(device.adb_argv(['get-state'])[1:], ['-H','127.0.0.1','-P','5037','-s',device.SERIAL,'get-state'])
        self.assertEqual(inherited['ADB_SERVER_SOCKET'],'tcp:remote:9000')

    def test_timeout_preserves_partial_output_and_never_retries(self):
        with tempfile.TemporaryDirectory() as temporary:
            instance = device.Device.__new__(device.Device)
            instance.output, instance.sequence = Path(temporary), 0
            timeout = device.subprocess.TimeoutExpired(['adb'],1,output=b'partial success unclear',stderr=b'partial error')
            with patch.object(instance,'host_guard'), patch.object(device.subprocess,'run',side_effect=timeout) as command:
                with self.assertRaisesRegex(ValueError,'uncertain outcome'): instance.command(['get-state'],timeout=1)
                self.assertEqual(command.call_count,1)
            self.assertEqual((instance.output/'adb-001.stdout').read_bytes(),b'partial success unclear')
            self.assertEqual((instance.output/'adb-001.stderr').read_bytes(),b'partial error')
            result=json.loads((instance.output/'adb-001-result.json').read_text())
            self.assertEqual(result['outcome'],'UNCERTAIN_TIMEOUT'); self.assertFalse(result['retried'])

    def test_physical_serial_name_api_and_abi_cannot_be_selected(self):
        device.target_identity(device.SERIAL, device.NAME, '1', '36', 'x86_64')
        original = [device.SERIAL, device.NAME, '1', '36', 'x86_64']
        for index, value in enumerate(('physical-device-fixture', 'OtherAVD', '0', '35', 'arm64-v8a')):
            values = original.copy(); values[index] = value
            with self.assertRaises(ValueError): device.target_identity(*values)

    def test_bad_owner_is_rejected_before_subprocess(self):
        with patch.object(device.subprocess, 'run') as command:
            with self.assertRaises(ValueError): device.Device(Path('.'), {'root':'C:/wrong','name':device.NAME,'serial':device.SERIAL,'pid':1}, 'a'*64)
            with self.assertRaises(ValueError): device.Device(Path('.'), {}, 'a'*64, serial='physical')
            command.assert_not_called()

    def test_existing_avd_root_has_no_destructive_fallback(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(device, 'ROOT', Path(temporary)), patch.object(device.subprocess, 'run') as command:
            with self.assertRaises(ValueError): device.create_and_start(Path(temporary))
            command.assert_not_called()

    def test_activity_title_without_real_grid_is_not_calendar(self):
        xml = '<hierarchy><node package="'+old.QA+'" text="Calendario"/></hierarchy>'
        with self.assertRaises(ValueError): device.gui_action(xml)

    def test_guide_tap_requires_observed_unambiguous_qa_control(self):
        xml = '<hierarchy><node package="'+old.QA+'" text="Primeros pasos · 1 de 3"/><node package="'+old.QA+'" text="Siguiente" enabled="true" bounds="[10,20][110,80]"/></hierarchy>'
        self.assertEqual(device.gui_action(xml), (60,50))
        with self.assertRaises(ValueError): device.gui_action(xml.replace(old.QA,'com.blackatsystems.miguardia'))
        with self.assertRaises(ValueError): device.gui_action(xml.replace('enabled="true"','enabled="false"'))

    def test_valid_grid_contains_real_day_descriptions_and_fixture(self):
        descriptions = ['Mes anterior','Mes siguiente'] + [f'jueves {day} de octubre de 2026, FIC' for day in range(1,29)]
        xml = '<hierarchy>' + ''.join(f'<node package="{old.QA}" content-desc="{d}"/>' for d in descriptions) + '</hierarchy>'
        self.assertIsNone(device.gui_action(xml))
        with self.assertRaises(ValueError): device.gui_action(xml.replace('FIC','empty'))

    def test_offscreen_guide_scroll_uses_observed_container_and_retains_context(self):
        xml = '<hierarchy><node package="'+old.QA+'" text="Primeros pasos · 1 de 3"/><node package="'+old.QA+'" scrollable="true" enabled="true" bounds="[0,24][320,592]"/></hierarchy>'
        self.assertEqual(device.gui_action(xml),(160,450,160,166))
        next_page = '<hierarchy><node package="'+old.QA+'" text="Siguiente" enabled="true" bounds="[10,20][110,80]"/></hierarchy>'
        self.assertEqual(device.gui_action(next_page,True),(60,50))
        with self.assertRaises(ValueError): device.gui_action(xml.replace('scrollable="true"','scrollable="false"'))

    def test_calendar_scroll_excludes_small_horizontal_grid_and_requires_observed_week(self):
        prefix='<hierarchy><node package="'+old.QA+'" content-desc="Mes anterior"/><node package="'+old.QA+'" content-desc="Mes siguiente"/>'
        containers='<node package="'+old.QA+'" enabled="true" scrollable="true" bounds="[0,120][320,592]"/><node package="'+old.QA+'" enabled="true" scrollable="true" bounds="[24,565][296,592]"/>'
        self.assertEqual(device.gui_action(prefix+containers+'</hierarchy>'),(160,474,160,238))
        cells='<hierarchy>'+''.join(f'<node package="{old.QA}" content-desc="jueves {d} de octubre de 2026"/>' for d in range(1,8))+'</hierarchy>'
        self.assertIsNone(device.gui_action(cells,False,True,True))
        for month,fixture in ((False,True),(True,False)):
            with self.assertRaises(ValueError): device.gui_action(cells,False,month,fixture)
        with self.assertRaises(ValueError): device.gui_action(prefix+containers+containers+'</hierarchy>')

    def test_today_fixture_can_scroll_toward_calendar_but_guide_overlay_cannot_pass(self):
        xml='<hierarchy><node package="'+old.QA+'" content-desc="Abrir menú"/><node package="'+old.QA+'" content-desc="Hoy, 22/10/2026. FIC, Objetivo ficticio"/><node package="'+old.QA+'" enabled="true" scrollable="true" bounds="[0,120][320,592]"/></hierarchy>'
        self.assertEqual(device.gui_action(xml),(160,474,160,238))
        with self.assertRaises(ValueError): device.gui_action(xml.replace('FIC','empty'))
        cells=''.join(f'<node package="{old.QA}" content-desc="jueves {d} de octubre de 2026, FIC"/>' for d in range(1,29))
        overlay='<node package="'+old.QA+'" text="Recorrido · 1 de 8"/><node package="'+old.QA+'" text="Siguiente" enabled="true" bounds="[10,20][110,80]"/>'
        self.assertEqual(device.gui_action('<hierarchy>'+cells+overlay+'</hierarchy>',True,True,True),(60,50))

    def test_guide_accessibility_description_without_text_blocks_calendar_acceptance(self):
        cells=''.join(f'<node package="{old.QA}" content-desc="jueves {d} de octubre de 2026, FIC"/>' for d in range(1,8))
        overlay='<node package="'+old.QA+'" content-desc="Recorrido activo. Los controles señalados no se activan."/>'
        xml='<hierarchy>'+overlay+cells+'</hierarchy>'
        with self.assertRaises(ValueError): device.gui_action(xml,True,True,True)
        scroll='<node package="'+old.QA+'" enabled="true" scrollable="true" bounds="[24,48][296,568]"/>'
        xml='<hierarchy>'+overlay+scroll+cells+'</hierarchy>'
        self.assertEqual(device.gui_action(xml,True,True,True),(160,438,160,178))
        instance=device.Device.__new__(device.Device)
        with tempfile.TemporaryDirectory() as temporary, patch.object(instance,'guard'), patch.object(device.time,'sleep'):
            instance.output=Path(temporary)
            with patch.object(instance,'command',side_effect=[b'',('<?xml version="1.0"?>'+xml).encode()]) as command:
                with self.assertRaisesRegex(ValueError,'Guide unexpectedly returned after update'): instance.open_calendar('v8',False)
                self.assertEqual(command.call_count,2)
                self.assertFalse((instance.output/'v8-calendar.json').exists())

    def test_seed_resume_rejects_expired_scenario_before_any_process(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary); (root/'scenario.json').write_text(json.dumps(SPEC_R2))
            with patch.object(device.time,'time',return_value=SPEC_R2['short_end']/1000), patch.object(device.subprocess,'Popen') as process:
                with self.assertRaisesRegex(ValueError,'Insufficient original'): device.resume_after_seed(root,root/'qa6',root/'qa8',root/'runner')
                process.assert_not_called()

    def test_r2_namespace_viewport_and_launch_reject_first_avd(self):
        self.assertEqual((device.NAME,device.SERIAL,device.PORT),('MiGuardia_Stage10_API36_20261022_R2','emulator-5586',5586))
        self.assertEqual(device.ROOT.as_posix(),'C:/Users/User/.cache/miguardia-stage10-avd/20261022-r2')
        self.assertEqual({k:device.AVD_SETTINGS[k] for k in ('hw.lcd.width','hw.lcd.height','hw.lcd.density')},
                         {'hw.lcd.width':'1080','hw.lcd.height':'2400','hw.lcd.density':'360'})
        owner={'root':str(device.ROOT),'name':device.NAME,'serial':device.SERIAL,'pid':1,'command':device.launch_command()}
        device.Device(Path('.'),owner,'a'*64)
        for changed in ({'name':'MiGuardia_Stage10_API36_20261022'},{'serial':'emulator-5584'},
                        {'root':'C:/Users/User/.cache/miguardia-stage10-avd/20261022'},
                        {'command':[v.replace('5586','5584') for v in device.launch_command()]}):
            with self.assertRaises(ValueError): device.Device(Path('.'),owner|changed,'a'*64)
        config=''.join(f'{k}={v}\n' for k,v in device.AVD_SETTINGS.items()).encode()
        with self.assertRaises(ValueError): device.require_avd_config(config,config.replace(b'height=2400',b'height=640'))

    def test_r2_window_is_distinct_and_only_r2_can_execute(self):
        self.assertEqual(capture.scenario(SPEC),SPEC)
        self.assertEqual(device.active_scenario(SPEC_R2),SPEC_R2)
        with self.assertRaises(ValueError): device.active_scenario(SPEC)
        with self.assertRaises(ValueError): capture.scenario(SPEC|{'short_end':SPEC_R2['short_end']})
        with self.assertRaises(ValueError): capture.scenario(SPEC_R2|{'short_end':SPEC['short_end']})

    def test_r2_observed_viewport_rejects_small_or_overridden_screen(self):
        device.require_viewport('Physical size: 1080x2400\r\n','Physical density: 360\r\n')
        for size,density in [('Physical size: 320x640','Physical density: 160'),
                             ('Physical size: 1080x2400\nOverride size: 320x640','Physical density: 360'),
                             ('Physical size: 1080x2400','Physical density: 360\nOverride density: 240')]:
            with self.assertRaises(ValueError): device.require_viewport(size,density)

    def test_clock_spec_is_exact_not_a_new_date_on_retry(self):
        self.assertEqual(capture.scenario(SPEC), SPEC)
        for change in ({'short_end':SPEC['short_end']+1},{'long_end':True},{'consent':'old'},{'new_key':1}):
            with self.assertRaises(ValueError): capture.scenario(SPEC | change)

    def test_live_discard_raw_cannot_be_masked_by_gateway(self):
        before = raw_preferences([capture.SHORT,capture.LONG], True)
        def compare(current, portable, now=SPEC['short_end']):
            return capture.notification_delta(before,raw_preferences(current,True),[capture.SHORT,capture.LONG],portable,SPEC,now)
        self.assertEqual(compare([capture.LONG],[capture.LONG]),[capture.SHORT])
        for current, portable, now in (([],[],SPEC['short_end']),([capture.LONG],[capture.LONG],SPEC['short_end']-1),([capture.LONG],[capture.LONG],SPEC['long_end']),([capture.LONG],[capture.SHORT,capture.LONG],SPEC['short_end'])):
            with self.assertRaises(ValueError): compare(current,portable,now)

    def test_annex_cannot_hide_unknown_file_private_key_or_wrong_install(self):
        good = struct.pack('>iiqq',1,2,100,0)
        capture.annex('files/profileInstalled',good,100)
        for path,data,updated in [('files/unknown',good,100),('files/profileInstalled',good,101),('files/profileInstalled',good+b'x',100),('shared_prefs/notification_deferred_actions.xml',b'<map><string name="private">x</string></map>',100)]:
            with self.assertRaises(ValueError): capture.annex(path,data,updated)

    def test_inherited_profile_requires_exact_baseline_bytes_and_current_layout(self):
        data=struct.pack('>iiqq',1,2,100,0)
        baseline={'last_update_time':100,'profile_sha256':capture.strict.digest(data)}
        capture.annex('files/profileInstalled',data,200,baseline)
        capture.annex('files/profileInstalled',struct.pack('>iiqq',1,3,200,0),200,baseline)
        for raw in (struct.pack('>iiqq',1,3,100,0),struct.pack('>iiqq',1,2,99,0),struct.pack('>iiqq',1,2,100,1),data+b'x'):
            with self.assertRaises(ValueError):capture.annex('files/profileInstalled',raw,200,baseline)
        with self.assertRaises(ValueError):capture.annex('files/profileInstalled',data,200)
        with self.assertRaises(ValueError):capture.annex('files/profileInstalled',data,200,baseline|{'profile_sha256':'f'*64})

    def test_recovery_binding_rejects_reinstall_identity_time_or_scenario_changes(self):
        binding={'bundle_sha256':'a'*64,'profile_sha256':'b'*64,'first_install_time':SPEC_R2['started_at']-1000,
            'last_update_time':SPEC_R2['started_at']-1000,'captured_at_epoch_millis':SPEC_R2['started_at']+1000}
        receipt={'profile_baseline':binding,'operation':'capture-v8','first_install_time':binding['first_install_time'],
            'last_update_time':SPEC_R2['short_end'],'captured_at_epoch_millis':SPEC_R2['short_end']+1000}
        capture.require_profile_binding(receipt,SPEC_R2,binding)
        for change in ({'first_install_time':binding['first_install_time']+1},
                       {'last_update_time':SPEC_R2['short_end']-1}, {'operation':'capture-v6'},
                       {'profile_baseline':binding|{'bundle_sha256':'c'*64}}):
            with self.assertRaises(ValueError):capture.require_profile_binding(receipt|change,SPEC_R2,binding)
        with self.assertRaises(ValueError):capture.require_profile_binding(receipt,SPEC,binding)
        with self.assertRaises(ValueError):capture.require_profile_binding(receipt,SPEC_R2,None)


class Stage10EmulatorCaptureTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='stage10-avd-host-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name); self.index = 0
        self.source = self.root/'v6.db'; self.target = self.root/'v8.db'
        self.preferences = self.root/'preferences.json'
        create_source(self.source, self.root/'source-private', self.preferences)
        self.png = (Path(__file__).resolve().parents[3]/'app/src/androidTest/assets/stage10/synthetic.png').read_bytes()
        with closing(sqlite3.connect(self.source)) as db, db:
            db.execute('UPDATE schedule_photos SET storageKey=?,byteSize=?,pixelWidth=800,pixelHeight=480,mimeType=?',(old.PHOTO,len(self.png),'image/png'))
            # Host-only fixture has 27 initial rows; add 13 relational rows for coverage.
            for table, ids in [('objectives',[f'40000000-0000-4000-8000-{i:012d}' for i in range(1,6)]),
                               ('shifts',[capture.SHORT[6:],capture.LONG[6:]]),('shift_work_snapshots',[capture.SHORT[6:],capture.LONG[6:]]),
                               ('shift_notification_configs',[capture.SHORT[6:],capture.LONG[6:]]),('shift_notification_reminders',[capture.SHORT[6:],capture.LONG[6:]])]:
                row = db.execute('SELECT * FROM '+table).fetchone()
                for identity in ids:
                    values = list(row); values[0] = identity
                    if table == 'objectives':
                        values[1] = 'Synthetic objective ' + identity
                        values[2] = identity[-6:]
                    db.execute('INSERT INTO '+table+' VALUES ('+','.join('?' for _ in values)+')',values)
            for key,end in ((capture.SHORT,SPEC['short_end']),(capture.LONG,SPEC['long_end'])):
                db.execute('UPDATE shifts SET endEpochMillis=? WHERE id=?',(end,key[6:]))
        materialize_target(self.source,self.target)

    def bundle(self, room, mutate=None, extra=None):
        self.index += 1
        ids = [capture.SHORT,capture.LONG] if room == 6 else [capture.LONG]
        files = {name:('SYNTHETIC_UNIT_ONLY:'+name).encode() for name in capture.BASE_RAW}
        files['private/files/schedule_photos/'+old.PHOTO] = self.png
        files['private/'+wire.RAW] = raw_preferences(ids, True)
        files[capture.ONBOARDING] = blob(1, blob(1,b'completed_version')+blob(2,b'\x18\x01'))
        files[capture.MARKER] = ('\n'.join(map(str,(SPEC['consent'],SPEC['id'],SPEC['started_at'],SPEC['short_end'],SPEC['long_end'],old.SOURCE,'906f8a853793dcdedccd16566c8b17af2b73836bc05daa695dc97e2504152d90','5aa8334f217a14c229b9ec7b2c6ae02ae39c5ab94328492363e6458cc14e6e26')))+'\n').encode()
        files['database.db'] = (self.source if room == 6 else self.target).read_bytes()
        values = json.loads(self.preferences.read_text())
        next(v for v in values if v['key']==wire.DISMISSED)['values'] = ids
        files['portable-preferences.json'] = json.dumps(values).encode(); files['fixture-source.bin'] = b'HOST_ONLY_TEST_FIXTURE'
        r = {'format':capture.FORMAT,'criterion':SPEC['consent'],'operation':'capture-v6' if room==6 else 'capture-v8','room_version':room,
             'scenario_id':SPEC['id'],**{k:SPEC[k] for k in ('started_at','short_end','long_end')},
             'target_package':old.QA,'test_package':old.QA+'.test','certificate_sha256':old.CERT,'target_apk_sha256':old.APKS[room],
             'runner_apk_sha256':'a'*64,'capture_id':str(__import__('uuid').uuid4()),'neutral_application':True,'database_closed_before_copy':True,
             'single_target_process_verified':True,'export_owner':'TARGET_QA_CACHE','portable_source':'TARGET_GATEWAY_AND_EXPLICIT_PERSISTED_KEYS',
             'api':36,'fingerprint':'synthetic-host-unit-only','onboarding_completed':True,'wal_checkpoint':[0,0,0],
             'captured_at_epoch_millis':SPEC['started_at']+10000 if room==6 else SPEC['short_end']+1000,
             'first_install_time':SPEC['started_at']-120000,'last_update_time':SPEC['started_at']-120000 if room==6 else SPEC['short_end']+50,'source_fixture_sha256':capture.strict.digest(files['fixture-source.bin']),
             'technical_files_not_portable':{},'platform_runtime_files':{}}
        if mutate: mutate(files,r)
        r['files'] = {n:{'bytes':len(b),'sha256':capture.strict.digest(b)} for n,b in files.items()}
        bundle = self.root/f'bundle-{self.index}.zip'
        with zipfile.ZipFile(bundle,'w') as z:
            for name,data in files.items(): z.writestr(name,data)
            z.writestr('receipt.json',json.dumps(r))
            if extra: z.writestr(*extra)
        return bundle

    def pair(self, mutate=None):
        a,b = self.root/f'a-{self.index}',self.root/f'b-{self.index}'
        capture.ingest(self.bundle(6),a,SPEC,'a'*64)
        capture.ingest(self.bundle(8,mutate),b,SPEC,'a'*64)
        return a,b

    def test_full_host_pair_compares_real_sql_raw_photo_and_expiry(self):
        result = capture.compare(*self.pair(),SPEC,'a'*64,final=True)
        self.assertEqual(result['original_rows'],40); self.assertEqual(result['removed'],[capture.SHORT])

    def test_r2_complete_pair_keeps_first_profile_separate(self):
        for path in (self.source,self.target):
            with closing(sqlite3.connect(path)) as db,db:
                db.execute('UPDATE shifts SET endEpochMillis=? WHERE id=?',(SPEC_R2['short_end'],capture.SHORT[6:]))
        with patch.dict(SPEC,SPEC_R2,clear=True):
            result=capture.compare(*self.pair(),SPEC,'a'*64,final=True)
            self.assertEqual(result['original_rows'],40)
            self.assertEqual(result['removed'],[capture.SHORT])

    def test_recovery_compares_two_explicit_readers_and_binds_original_archive(self):
        for path in (self.source,self.target):
            with closing(sqlite3.connect(path)) as db,db:
                db.execute('UPDATE shifts SET endEpochMillis=? WHERE id=?',(SPEC_R2['short_end'],capture.SHORT[6:]))
        with patch.dict(SPEC,SPEC_R2,clear=True):
            profile=struct.pack('>iiqq',1,2,SPEC['started_at']-120000,0)
            def add_profile(files,r):
                files['annex/files/profileInstalled']=profile
                r['technical_files_not_portable']={'files/profileInstalled':capture.strict.digest(profile)}
            baseline=self.root/'baseline';original=self.root/'baseline.zip'
            original.write_bytes(self.bundle(6,add_profile).read_bytes())
            capture.ingest(original,baseline,SPEC,'a'*64)
            binding=capture.profile_baseline(baseline,SPEC,'a'*64)
            def recovered(files,r):
                add_profile(files,r);r['profile_baseline']=binding;r['runner_apk_sha256']='b'*64
            after=self.root/'recovered'
            capture.ingest(self.bundle(8,recovered),after,SPEC,'b'*64,baseline=baseline,baseline_runner_hash='a'*64)
            result=capture.compare(baseline,after,SPEC,'a'*64,final=True,after_runner_hash='b'*64)
            self.assertEqual(result['original_rows'],40);self.assertEqual(result['removed'],[capture.SHORT])
            with self.assertRaises(ValueError):capture.compare(baseline,after,SPEC,'a'*64,final=True)
            def modified_cache(files,r):
                recovered(files,r)
                files['annex/files/profileInstalled']=struct.pack('>iiqq',1,3,SPEC['started_at']-120000,0)
                r['technical_files_not_portable']['files/profileInstalled']=capture.strict.digest(files['annex/files/profileInstalled'])
            with self.assertRaises(ValueError):capture.ingest(self.bundle(8,modified_cache),self.root/'bad-cache',SPEC,'b'*64,baseline=baseline,baseline_runner_hash='a'*64)
            with self.assertRaises(ValueError):capture.ingest(self.bundle(8,recovered),self.root/'unbound',SPEC,'b'*64)
            (baseline/'annex/files/profileInstalled').write_bytes(profile+b'changed')
            with self.assertRaises(ValueError):capture.profile_baseline(baseline,SPEC,'a'*64)

    def test_baseline_profile_sidecar_not_in_archived_receipt_is_rejected(self):
        with closing(sqlite3.connect(self.source)) as db,db:
            db.execute('UPDATE shifts SET endEpochMillis=? WHERE id=?',(SPEC_R2['short_end'],capture.SHORT[6:]))
        with patch.dict(SPEC,SPEC_R2,clear=True):
            baseline=self.root/'baseline';original=self.root/'baseline.zip'
            original.write_bytes(self.bundle(6).read_bytes())
            capture.ingest(original,baseline,SPEC,'a'*64)
            sidecar=baseline/'annex/files/profileInstalled';sidecar.parent.mkdir(parents=True)
            sidecar.write_bytes(struct.pack('>iiqq',1,2,SPEC['started_at']-120000,0))
            with self.assertRaises(ValueError):capture.profile_baseline(baseline,SPEC,'a'*64)

    def test_changed_durable_raw_and_preference_are_not_normalized(self):
        def raw(files,r): files['private/shared_prefs/miguardia_display_preferences.xml'] = b'changed'
        with self.assertRaises(ValueError): capture.compare(*self.pair(raw),SPEC,'a'*64)
        def preference(files,r):
            v = json.loads(files['portable-preferences.json']); next(i for i in v if i['key']=='profile.display_name')['values']=['changed']
            files['portable-preferences.json']=json.dumps(v).encode()
        with self.assertRaises(ValueError): capture.compare(*self.pair(preference),SPEC,'a'*64)

    def test_business_row_loss_is_rejected_even_with_rehashed_receipt(self):
        with closing(sqlite3.connect(self.target)) as db, db: db.execute("UPDATE shift_notes SET body='changed'")
        with self.assertRaisesRegex(ValueError,'typed row'): capture.compare(*self.pair(),SPEC,'a'*64)

    def test_late_capture_cannot_hide_upgrade_before_android_expiry(self):
        def early_update(files,r): r['last_update_time'] = SPEC['short_end'] - 6000
        with self.assertRaisesRegex(ValueError,'update preceded expiry'):
            capture.compare(*self.pair(early_update),SPEC,'a'*64)

    def test_guide_loss_wrong_runner_and_extra_zip_entry_fail(self):
        def guide(files,r): files[capture.ONBOARDING]=blob(1,blob(1,b'completed_version')+blob(2,b'\x18\x00'))
        with self.assertRaises(ValueError): capture.ingest(self.bundle(6,guide),self.root/'bad-guide',SPEC,'a'*64)
        with self.assertRaises(ValueError): capture.ingest(self.bundle(6),self.root/'bad-runner',SPEC,'b'*64)
        with self.assertRaises(ValueError): capture.ingest(self.bundle(6,extra=('../escape',b'x')),self.root/'bad-entry',SPEC,'a'*64)


if __name__ == '__main__': unittest.main()
