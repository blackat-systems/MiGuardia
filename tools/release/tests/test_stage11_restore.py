import subprocess
import sys
from pathlib import Path
import unittest
import hashlib
import io
import json
import zipfile
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import stage11_restore as s


class Stage11GuardTest(unittest.TestCase):
    def evidence(self):
        data=io.BytesIO()
        with zipfile.ZipFile(data,'w') as z:
            z.writestr('isolation.json',json.dumps(dict(neutral_application=True,isolation='PASS',
                protected_before={'canary':'same'},protected_after={'canary':'same'},
                root_id='10000000-0000-4000-8000-000000000001')))
        return data.getvalue()

    def proof(self):
        return '\nINSTRUMENTATION_RESULT: stage11Bundle=stage11-10000000-0000-4000-8000-000000000001.zip\nINSTRUMENTATION_RESULT: stage11BundleSha256='+hashlib.sha256(self.evidence()).hexdigest()
    def test_foreign_or_ambiguous_focus_fails_closed(self):
        for text in ('', 'mCurrentFocus=Window{abc u0 com.bank/.Main}',
                     'mCurrentFocus=Window{a u0 com.android.launcher3/.Launcher}\nmCurrentFocus=Window{b u0 com.android.launcher3/.Launcher}'):
            with self.assertRaises(ValueError): s.focus(text)
        s.focus('mCurrentFocus=Window{abc u0 com.android.launcher3/.Launcher}')

    def test_wrong_serial_avd_api_and_physical_target_are_rejected(self):
        for args in [('device', s.r2.NAME, '1', '36', 'x86_64'),
                     (s.r2.SERIAL, 'other', '1', '36', 'x86_64'),
                     (s.r2.SERIAL, s.r2.NAME, '0', '36', 'x86_64'),
                     (s.r2.SERIAL, s.r2.NAME, '1', '33', 'x86_64')]:
            with self.assertRaises(ValueError): s.r2.target_identity(*args)

    def test_protocol_requires_all_methods_no_skips_and_isolation(self):
        def output(code=0, isolation='PASS', terminal=-1):
            return f'INSTRUMENTATION_STATUS: class=C\nINSTRUMENTATION_STATUS: test=m\nINSTRUMENTATION_STATUS_CODE: {code}\nINSTRUMENTATION_RESULT: stage11Isolation={isolation}\nINSTRUMENTATION_CODE: {terminal}'+self.proof()
        result=s.protocol(output(),0,'C',['m'])
        self.assertEqual('PENDING_EVIDENCE', result['verdict'])
        self.assertEqual('PASSED',s.attest_transport(result,self.evidence())['verdict'])
        self.assertEqual('PENDING_EVIDENCE',s.protocol(output().replace('\n','\r\n'),0,'C',['m'])['verdict'])
        for data, rc, methods in [(output(-2),0,['m']), (output(-3),0,['m']),
                                  (output(isolation='FAILED'),0,['m']), (output(),1,['m']),
                                  (output(terminal=0),0,['m']), (output(),0,['m','missing'])]:
            self.assertNotEqual('PASSED', s.protocol(data, rc, 'C', methods)['verdict'])

    def test_missing_duplicated_or_malformed_bundle_proof_never_passes(self):
        base='INSTRUMENTATION_STATUS: class=C\nINSTRUMENTATION_STATUS: test=m\nINSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_RESULT: stage11Isolation=PASS\nINSTRUMENTATION_CODE: -1'
        for proof in ['',self.proof()+self.proof(),self.proof().replace('stage11-10000000','../../other-10000000'),self.proof().replace('Sha256=','Sha256=INVALID')]:
            self.assertEqual('FAILED',s.protocol(base+proof,0,'C',['m'])['verdict'])

    def test_transport_mismatch_cannot_complete_a_green(self):
        result={'verdict':'PENDING_EVIDENCE','bundle':'stage11-10000000-0000-4000-8000-000000000001.zip','bundle_sha256':hashlib.sha256(self.evidence()).hexdigest()}
        with self.assertRaises(ValueError):s.attest_transport(result,b'not the bundle')
        self.assertEqual('PENDING_EVIDENCE',result['verdict'])

    def test_execute_persists_failed_transport_or_passed_verified_payload(self):
        import tempfile,contextlib
        from unittest.mock import Mock
        selector=s.CLASSES[0]
        raw=(f'INSTRUMENTATION_STATUS: class={selector}\nINSTRUMENTATION_STATUS: test=m\nINSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_RESULT: stage11Isolation=PASS\nINSTRUMENTATION_CODE: -1'+self.proof()).encode()
        for transport,expected in [(b'wrong bytes','FAILED'),(ValueError('transport'),'FAILED'),(self.evidence(),'PASSED')]:
            with tempfile.TemporaryDirectory() as temp:
                output=Path(temp)/'a/b/c/run';output.mkdir(parents=True)
                d=Mock();d.command.side_effect=[raw,transport]
                with patch.object(s,'current_runner',return_value={'runner_sha256':'a'*64}),patch.object(s,'open_device',return_value=d),patch.object(s.qa,'discover',return_value=({selector:{'methods':['m']}},{})),contextlib.redirect_stdout(io.StringIO()):
                    result=s.execute(output,selector,'case')
                self.assertEqual(expected,result['verdict'])
                self.assertEqual(expected,json.loads((output/'case-result.json').read_text())['verdict'])

    def test_preinstall_wrong_path_hash_package_or_certificate_never_gets_adb(self):
        import tempfile
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);runner=root/'.local-evidence/stage11/restore-android-20261023/cp/Stage11-Restore-Runner.apk'
            runner.parent.mkdir(parents=True);runner.write_bytes(b'test-apk')
            approved={'cp':hashlib.sha256(runner.read_bytes()).hexdigest()}
            good_manifest=f'A: package="{s.r2.QA}.test"\nA: android:targetPackage(0)="{s.r2.QA}" (Raw: "{s.r2.QA}")\nA: android:name(0)="com.blackatsystems.miguardia.backup.Stage11RestoreRunner"'
            good_signer=subprocess.CompletedProcess([],0,stdout=('Signer #1 certificate SHA-256 digest: '+s.CERTIFICATE).encode())
            def manifest(text):return subprocess.CompletedProcess([],0,stdout=text.encode())
            with patch.object(s,'PROJECT',root),patch.object(s,'APPROVED_RUNNERS',approved),patch.object(s,'open_device') as device:
                with self.assertRaises(ValueError):s.install(root,root/'app-qa.apk')
                runner.write_bytes(b'changed')
                with self.assertRaises(ValueError):s.install(root,runner)
                runner.write_bytes(b'test-apk')
                with patch.object(s.subprocess,'run',return_value=subprocess.CompletedProcess([],0,stdout=b'wrong cert')):
                    with self.assertRaises(ValueError):s.install(root,runner)
                with patch.object(s.subprocess,'run',side_effect=[good_signer,manifest(good_manifest.replace(s.r2.QA+'.test',s.r2.QA))]):
                    with self.assertRaises(ValueError):s.install(root,runner)
                with patch.object(s.subprocess,'run',side_effect=[good_signer,manifest(good_manifest)]):
                    self.assertEqual(approved['cp'],s.validate_runner(runner))
                device.assert_not_called()

    def test_qa_update_rejects_unapproved_path_and_instrumentation_before_adb(self):
        import tempfile
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);apk=root/'.local-evidence/stage11/restore-android-20261023/cp/QA.apk'
            apk.parent.mkdir(parents=True);apk.write_bytes(b'qa')
            paths={'cp/QA.apk':hashlib.sha256(b'qa').hexdigest()}
            signer=subprocess.CompletedProcess([],0,stdout=('Signer #1 certificate SHA-256 digest: '+s.CERTIFICATE).encode())
            xml=f'A: package="{s.r2.QA}"\nA: android:targetPackage(0)="{s.r2.QA}" (Raw: "{s.r2.QA}")'
            with patch.object(s,'PROJECT',root),patch.object(s,'APPROVED_QAS',paths),patch.object(s,'open_device') as device:
                with self.assertRaises(ValueError):s.install_qa(root,root/'other.apk')
                with patch.object(s.subprocess,'run',side_effect=[signer,subprocess.CompletedProcess([],0,stdout=xml.encode())]):
                    with self.assertRaises(ValueError):s.install_qa(root,apk)
                device.assert_not_called()

    def test_profile_update_allows_only_known_timestamp_transition(self):
        import struct,datetime
        second=int(datetime.datetime(2026,10,23,4,39,4,tzinfo=datetime.timezone.utc).timestamp())
        before={'files/profileInstalled':struct.pack('>iiqq',1,327680,1792725418882,0),'private-canary':b'same'}
        after=dict(before);after['files/profileInstalled']=struct.pack('>iiqq',1,327680,second*1000+218,0)
        metadata='lastUpdateTime=2026-10-23 04:39:04'
        with patch.object(s,'semantic_database',return_value={'table':[(1,)]}):
            self.assertTrue(s.profile_update_only(before,after,metadata)['all_other_files_identical'])
            for changed in [dict(after,**{'private-canary':b'changed'}),dict(after,**{'files/profileInstalled':struct.pack('>iiqq',2,327680,second*1000+218,0)}),dict(after,**{'files/profileInstalled':struct.pack('>iiqq',1,327680,second*1000+2000,0)})]:
                with self.assertRaises(ValueError):s.profile_update_only(before,changed,metadata)

    def test_timeout_is_not_green_and_does_not_retry(self):
        import tempfile
        with tempfile.TemporaryDirectory() as temp:
            owner=dict(root=str(s.r2.ROOT),name=s.r2.NAME,serial=s.r2.SERIAL,pid=1,command=s.r2.launch_command())
            d=s.Device(temp,owner,'a'*64)
            with patch.object(d,'host_guard'), patch('stage10_emulator.subprocess.run',side_effect=subprocess.TimeoutExpired(['adb'],1,output=b'partial')) as run:
                with self.assertRaises(ValueError): d.command(['shell','getprop'],timeout=1)
            self.assertEqual(1,run.call_count)
            self.assertIn('UNCERTAIN_TIMEOUT',(Path(temp)/'adb-001-result.json').read_text())

    def test_zero_process_exit_without_protocol_is_not_green(self):
        self.assertEqual('FAILED',s.protocol('exit zero',0,'C',['m'])['verdict'])

    def test_unreviewed_selector_never_reaches_device(self):
        with patch.object(s,'open_device') as device:
            with self.assertRaises(ValueError): s.execute(Path('unused'),'OtherClass','unsafe')
            device.assert_not_called()

    def test_android_metadata_is_compared_but_not_counted_as_business_table(self):
        import tempfile,zipfile
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'input.zip'
            with zipfile.ZipFile(path,'w') as z:z.writestr('database.db',b'db')
            semantic={f'table{i}':[(i,)] for i in range(29)}
            semantic['table0']=[(i,) for i in range(12)]
            semantic['android_metadata']=[('en_US',)]
            with patch.object(s,'semantic_database',return_value=semantic):
                result=s.compare_received({'databases/miguardia-v2.db':b'db'},path)
                self.assertEqual((29,40),(result['tables'],result['rows']))
            mutated=dict(semantic,unknown_table=[])
            with patch.object(s,'semantic_database',return_value=mutated):
                with self.assertRaises(ValueError):s.compare_received({'databases/miguardia-v2.db':b'db'},path)


if __name__ == '__main__': unittest.main()
