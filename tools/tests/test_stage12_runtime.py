"""Pure preflight/adversarial tests; never starts SDK/native jobs or connects."""
import importlib.util
import copy
from contextlib import ExitStack, contextmanager
import sys
import types
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[1]/'release/stage12_runtime.py'
spec = importlib.util.spec_from_file_location('stage12_runtime_test_subject', SOURCE)
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)
job_spec = importlib.util.spec_from_file_location('stage12_runtime_error_test_subject', SOURCE.with_name('stage12_winjob.py'))
job_errors = importlib.util.module_from_spec(job_spec)
job_spec.loader.exec_module(job_errors)


class PrivateRuntimeTests(unittest.TestCase):
    @staticmethod
    def native_observation(image='\\Device\\HarddiskVolume3\\Owned\\emulator.exe'):
        return {'pid':20,'creation_filetime':42,'image':image,'image_format':'NATIVE_SYSTEM',
          'native_queries':[{'api':api,'flags':flags,'success':True,'win32_error':0,'characters':len(image),'raw_image':image}
                            for api,flags in [('QueryFullProcessImageNameW',1),('K32GetProcessImageFileNameW',None)]]}

    @staticmethod
    def native_metadata(image='\\Device\\HarddiskVolume3\\Owned\\emulator.exe'):
        return {'volume_serial':1,'file_index':2,'bytes':6,'attributes':32,'links':1,'last_write_filetime':4,
          'native_final_path':image,'native_final_path_flags':2,'native_final_path_length':len(image)}

    def test_clean_environment_is_allowlisted_and_all_roles_explicit(self):
        with patch.dict('os.environ', {'JAVA_TOOL_OPTIONS': 'untrusted', 'ADB_SERVER_SOCKET': 'tcp:5037'}, clear=False):
            for role in ['canary', 'emulator', 'server']:
                env = runtime.child_environment(role)
                self.assertNotIn('JAVA_TOOL_OPTIONS', env)
                self.assertEqual('5048', env['ANDROID_ADB_SERVER_PORT'])
                self.assertEqual('0', env['ADB_USB'])
                self.assertEqual('0', env['ADB_EMU'])
                self.assertEqual('0', env['ADB_MDNS'])
                self.assertEqual('C:/Windows/System32;C:/Windows', env['PATH'])
                if role != 'server':
                    self.assertEqual(runtime.SDK.as_posix(), env['ANDROID_HOME'])

    def test_unknown_role_fails(self):
        with self.assertRaisesRegex(runtime.Refused, 'UNKNOWN_PROCESS_ROLE'):
            runtime.child_environment('fallback')

    def test_altered_sdk_environment_rejected_before_process(self):
        env = runtime.child_environment('emulator')
        env['ANDROID_HOME'] = runtime.ORIGINAL_SDK.as_posix()
        with self.assertRaisesRegex(runtime.Refused, 'CHILD_ENVIRONMENT_CHANGED'):
            runtime.validate_environment(env, 'emulator')

    def test_real_adb_on_os_path_rejected(self):
        with patch.object(Path, 'exists', return_value=True):
            with self.assertRaisesRegex(runtime.Refused, 'REAL_ADB_ON_PATH'):
                runtime.validate_environment(runtime.child_environment('emulator'), 'emulator')

    def test_duplicate_json_and_oversized_json_rejected(self):
        with tempfile.TemporaryDirectory(prefix='stage12-pure-') as temp:
            p = Path(temp)/'test.json'; p.write_text('{"key":1,"key":2}', encoding='utf-8')
            with self.assertRaisesRegex(runtime.Refused, 'DUPLICATE_JSON_KEY'):
                runtime.read_json(p)
            p.write_bytes(b' '*(16*1024*1024+1))
            with self.assertRaisesRegex(runtime.Refused, 'JSON_TOO_LARGE'):
                runtime.read_json(p)

    def test_tree_hash_and_empty_directories_cover_complete_copy(self):
        with tempfile.TemporaryDirectory(prefix='stage12-pure-') as temp:
            p=Path(temp); (p/'empty').mkdir(); (p/'file').write_bytes(b'original')
            one=runtime.tree_inventory(p)
            self.assertEqual(['empty'], one['directories'])
            self.assertEqual(8, one['bytes'])
            (p/'file').write_bytes(b'altered!')
            self.assertNotEqual(one, runtime.tree_inventory(p))

    def test_json_write_is_exclusive(self):
        with tempfile.TemporaryDirectory(prefix='stage12-pure-') as temp:
            p=Path(temp)/'receipt.json'; runtime.write_json(p, {'once':True})
            with self.assertRaises(FileExistsError):
                runtime.write_json(p, {'twice':True})
            self.assertEqual({'once':True}, json.loads(p.read_text(encoding='utf-8')))

    def test_foreign_owner_rejected_before_sdk_or_copy(self):
        with patch.object(runtime,'read_json',return_value={'resource_id':'foreign'}):
            with self.assertRaisesRegex(runtime.Refused, 'EVIDENCE_OWNER_MISMATCH'):
                runtime.owner_check()

    def test_canaries_are_exact_no_avd_and_inert_stub_is_expected113(self):
        for spec in runtime.canary_specs():
            runtime.validate_canary_spec(spec)
            self.assertNotIn('-avd', spec['argv'])
        self.assertEqual(113, runtime.canary_specs()[0]['expected_exit'])
        bad=dict(runtime.canary_specs()[0], argv=['adb.exe', 'devices'])
        with self.assertRaisesRegex(runtime.Refused, 'UNREVIEWED_CANARY_COMMAND'):
            runtime.validate_canary_spec(bad)

    def test_private_sdk_binary_change_rejected_before_native_launch(self):
        expected={'emulator_package':{'files':[{'path':'emulator.exe','sha256':'good'}]}}
        with patch.object(runtime,'owner_check'), patch.object(runtime,'validate_avd_route'), patch.object(runtime,'validate_image_inputs'), patch.object(runtime,'no_links'), patch.object(runtime,'read_json',
            return_value={'resource_id':runtime.OWNER_UUID,'root':str(runtime.RUNTIME)}), patch.object(runtime,'tree_inventory',
            return_value={'files':[{'path':'emulator.exe','sha256':'changed'}]}):
            with self.assertRaisesRegex(runtime.Refused, 'PRIVATE_EMULATOR_BYTES_CHANGED'):
                runtime.verify_private_sdk(expected)

    def test_private_sdk_extra_adb_dll_or_binary_refused(self):
        package={'files':[]}; expected={'emulator_package':package}
        with patch.object(runtime,'owner_check'), patch.object(runtime,'validate_avd_route'), patch.object(runtime,'validate_image_inputs'), patch.object(runtime,'no_links'), patch.object(runtime,'read_json',
            return_value={'resource_id':runtime.OWNER_UUID,'root':str(runtime.RUNTIME)}), patch.object(runtime,'tree_inventory',
            return_value=package), patch.object(Path,'iterdir',return_value=iter([Path('adb.exe'),Path('AdbWinApi.dll')])):
            with self.assertRaisesRegex(runtime.Refused, 'EXTRA_PRIVATE_PLATFORM_TOOLS'):
                runtime.verify_private_sdk(expected)

    def test_private_sdk_changed_stub_refused(self):
        package={'files':[]}; expected={'emulator_package':package}
        with patch.object(runtime,'owner_check'), patch.object(runtime,'validate_avd_route'), patch.object(runtime,'validate_image_inputs'), patch.object(runtime,'no_links'), patch.object(runtime,'read_json',
            return_value={'resource_id':runtime.OWNER_UUID,'root':str(runtime.RUNTIME)}), patch.object(runtime,'tree_inventory',
            return_value=package), patch.object(Path,'iterdir',return_value=iter([Path('adb.exe')])), patch.object(runtime,'sha',
            side_effect=lambda p: runtime.ADB_SHA if p == runtime.ORIGINAL_SDK/'platform-tools/adb.exe' else 'wrong'):
            with self.assertRaisesRegex(runtime.Refused, 'INERT_STUB_CHANGED'):
                runtime.verify_private_sdk(expected)

    def test_listener_guards_refuse_foreign_pid_nonloopback_split_engines(self):
        server={'address':'127.0.0.1','port':5048,'pid':10}
        valid=[server,{'address':'127.0.0.1','port':5588,'pid':20},{'address':'127.0.0.1','port':5589,'pid':20}]
        runtime.validate_listener_owners(valid,10,{20},'engine')
        for bad in [[dict(server,pid=99)], [dict(server,address='0.0.0.0')],
                    [server,dict(valid[1],pid=30),valid[2]],
                    [server,dict(valid[1],address='0.0.0.0'),valid[2]], [server]]:
            with self.subTest(bad=bad), self.assertRaises(runtime.Refused):
                runtime.validate_listener_owners(bad,10,{20,30},'engine')
        with self.assertRaisesRegex(runtime.Refused,'ENGINE_ENDPOINT_EXISTS_BEFORE_OWN_BOOT'):
            runtime.validate_listener_owners(valid,10,set(),'server')

    def test_inventory_no_foreign_serial_is_accepted(self):
        runtime.validate_inventory(b'', 'before')
        runtime.validate_inventory(b'emulator-5588\tdevice product:sdk\n', 'after')
        for raw in [b'emulator-5588\toffline\n',b'foreign\tdevice\n',b'emulator-5588\tdevice\nforeign\tdevice\n',b'']:
            with self.subTest(raw=raw),self.assertRaises(runtime.Refused):
                runtime.validate_inventory(raw,'after')

    def test_file_metadata_pair_requires_real_single_link_regular_identity(self):
        one=dict(volume_serial=1,file_index=2,bytes=3,attributes=32,links=1,last_write_filetime=4)
        self.assertTrue(runtime.same_file_identity(one,dict(one)))
        for altered in [dict(one,file_index=9),dict(one,links=2),dict(one,attributes=1024),dict(one,bytes=0),dict(one,volume_serial=True)]:
            self.assertFalse(runtime.same_file_identity(one,altered))

    def test_close_failure_receipt_preserved_and_not_reported_closed(self):
        class Broken:
            close_receipt={'owned_job':True,'remaining':1}
            def close(self): raise RuntimeError('native close failure')
        record=runtime.close_job_record(Broken())
        self.assertFalse(runtime.close_proven(record))
        self.assertEqual({'owned_job':True,'remaining':1},record['native_close_receipt'])

    def test_unreviewed_shell_or_host_refused_before_connector(self):
        def forbidden(*args): raise AssertionError('No socket allowed')
        client=runtime.PrivateClient(forbidden,None,connector=forbidden)
        with self.assertRaisesRegex(runtime.Refused,'UNREVIEWED_HOST_SERVICE'):
            client.host('host:transport-any')
        with self.assertRaisesRegex(runtime.Refused,'UNREVIEWED_FIRST_BOOT_SHELL'):
            client.shell('pm clear anything')

    def test_registration_protocol_expects_eof_not_invented_okay(self):
        class Connection:
            def __init__(self): self.sent=[]
            def __enter__(self): return self
            def __exit__(self,*args): return False
            def settimeout(self,*args): pass
            def sendall(self,data): self.sent.append(data)
            def shutdown(self,*args): pass
            def recv(self,*args): return b''
        class Journal:
            def event(self,*args,**kwargs): pass
        connection=Connection(); guards=[]
        client=runtime.PrivateClient(lambda:guards.append(1),Journal(),connector=lambda *args:connection)
        self.assertEqual(b'',client.host('host:emulator:5589'))
        self.assertEqual([b'0012host:emulator:5589'],connection.sent)
        self.assertEqual(2,len(guards))

    def test_missing_new_admission_rejected_before_boot(self):
        with self.assertRaisesRegex(runtime.Refused,'NEW_MAIN_RUNTIME_ADMISSION_REQUIRED'):
            runtime.validate_runtime_admission({'schema':'old-consumed-g2'},Path('unused'))

    def test_partial_smart_socket_and_oversized_payload_refused(self):
        class Empty:
            def recv(self,size): return b''
            def settimeout(self,seconds): pass
        with self.assertRaisesRegex(runtime.Refused,'PARTIAL_SMART_SOCKET_REPLY'):
            runtime.exact_read(Empty(),4)
        with self.assertRaisesRegex(runtime.Refused,'SOCKET_READ_SIZE_REFUSED'):
            runtime.exact_read(Empty(),8193)

    def test_console_eof_and_drip_feed_cannot_loop_forever(self):
        class Console:
            def __init__(self,chunks): self.chunks=iter(chunks)
            def recv(self,size): return next(self.chunks,b'')
            def settimeout(self,seconds): pass
        with self.assertRaisesRegex(runtime.Refused,'OWN_CONSOLE_EOF_BEFORE_OK'):
            runtime.console_read_ok(Console([b'Android Console: partial\r\n',b'']))
        with patch.object(runtime.time,'monotonic',side_effect=[0,1,2,4]):
            with self.assertRaisesRegex(runtime.Refused,'ABSOLUTE_OWN_CONSOLE_DEADLINE'):
                runtime.console_read_ok(Console([b'a',b'b',b'c']))
        self.assertEqual(b'Android Console\r\nOK\r\n',runtime.console_read_ok(Console([b'Android Console\r\n',b'OK\r\n'])))

    def test_smart_socket_drip_feed_has_absolute_deadline(self):
        class Drip:
            def recv(self,size): return b'x'
            def settimeout(self,seconds): pass
        with patch.object(runtime.time,'monotonic',side_effect=[1,2,6]):
            with self.assertRaisesRegex(runtime.Refused,'ABSOLUTE_SMART_SOCKET_DEADLINE'):
                runtime.exact_read(Drip(),8,deadline=5)

    def test_current_avd_index_change_fails_before_native_job(self):
        with tempfile.TemporaryDirectory(prefix='stage12-pure-') as temp:
            root=Path(temp); p=root/'config.ini'; p.write_text('path=original',encoding='utf-8')
            manifest={'original_avd_files_before_first_boot':[{'path':'config.ini','bytes':p.stat().st_size,'sha256':runtime.sha(p)}]}
            p.write_text('path=foreign!',encoding='utf-8')
            with patch.object(runtime,'AVD_ROOT',root):
                with self.assertRaisesRegex(runtime.Refused,'ORIGINAL_AVD_CONFIG_OR_INDEX_CHANGED'):
                    runtime.validate_avd_route(manifest)

    def test_auxiliary_endpoint_roles_are_not_generic_loopback_permission(self):
        proof=lambda pid,path:{'process':{'pid':pid},'approved_path':str(runtime.SDK/'emulator'/path)}
        proofs=[proof(20,'qemu/windows-x86_64/qemu-system-x86_64.exe'),proof(21,'netsimd.exe'),proof(22,'crashpad_handler.exe')]
        runtime.validate_auxiliary_endpoints([{'address':'127.0.0.1','port':5589,'pid':20},{'address':'::1','port':50000,'pid':21}],proofs)
        for bad in [{'address':'127.0.0.1','port':50001,'pid':22},
                    {'address':'127.0.0.1','port':8554,'pid':20},
                    {'address':'0.0.0.0','port':50000,'pid':21}]:
            with self.subTest(bad=bad),self.assertRaises(runtime.Refused):
                runtime.validate_auxiliary_endpoints([bad],proofs)

    def test_final_status_requires_cleanup_and_conservation_before_pass(self):
        success={'status':'BOOT_IDENTITY_SYNC_OBSERVED_PENDING_CLOSE',
          'emulator_job_close':{'empty_confirmed':True,'handles_closed':True},
          'server_job_close':{'empty_confirmed':True,'handles_closed':True},
          'reserved_listeners_after_close':[],'original_images_unchanged':True,'post_observation_errors':[]}
        self.assertEqual('FIRST_BOOT_IDENTITY_SYNC_CLOSE_PASSED_NO_INSTALL',runtime.finalize_first_boot_status(dict(success))['status'])
        for key,bad in [('server_job_close',{'empty_confirmed':False}),('original_images_unchanged',False),
                        ('reserved_listeners_after_close',None),('post_observation_errors',[{'error':'test'}])]:
            changed=dict(success); changed[key]=bad
            self.assertEqual('FAILED_PRESERVED_NO_AUTOMATIC_RETRY',runtime.finalize_first_boot_status(changed)['status'])

    def test_checkpoint_rejects_mutated_sdk_copy_receipt(self):
        with tempfile.TemporaryDirectory(prefix='stage12-pure-') as temp:
            root=Path(temp); p=root/'sdk-copy.json'; p.write_text('original',encoding='utf-8')
            pins=[{'path':'sdk-copy.json','bytes':p.stat().st_size,'sha256':runtime.sha(p)}]
            for name in ['one.json','two.json','three.json']:
                q=root/name; q.write_text('{}',encoding='utf-8')
                pins.append({'path':name,'bytes':q.stat().st_size,'sha256':runtime.sha(q)})
            data={'schema':'stage12-g3-r2-checkpoint-v1','resource_owner_uuid':runtime.OWNER_UUID,'boot_protocol':runtime.boot_spec(),
                  'pins':pins}
            p.write_text('mutated!',encoding='utf-8')
            with patch.object(runtime,'read_json',return_value=data),patch.object(runtime,'BASE',root):
                with self.assertRaisesRegex(runtime.Refused,'CHECKPOINT_PIN_CHANGED'):
                    runtime.validate_checkpoint(root/'checkpoint.json')

    def test_acceleration_parser_rejects_unusable_or_negated_substrings(self):
        runtime.validate_acceleration_text('accel:\n0\nWHPX(10.0.26300) is installed and usable.\n')
        for text in ['WHPX is unusable','WHPX is not usable','WHPX(10.0.26300) is not installed and usable.',
                     'WHPX(10.0.26300) is installed and unusable.']:
            with self.subTest(text=text),self.assertRaises(runtime.Refused):
                runtime.validate_acceleration_text(text)

    def test_local_peer_must_belong_to_job_server_or_controller(self):
        own={'address':'127.0.0.1','port':50000,'remote_address':'127.0.0.1','remote_port':60000,'pid':20,'state':5}
        peer={'address':'127.0.0.1','port':60000,'remote_address':'127.0.0.1','remote_port':50000,'pid':21,'state':5}
        runtime.validate_local_peers([own],[own,peer],{20,21},[])
        with self.assertRaisesRegex(runtime.Refused,'FOREIGN_LOCAL_PEER_TO_OWN_VM_OR_HELPER'):
            runtime.validate_local_peers([own],[own,dict(peer,pid=999)],{20,21},[])
        with self.assertRaisesRegex(runtime.Refused,'LOCAL_PEER_NOT_PROVEN_OWNED'):
            runtime.validate_local_peers([own],[own],{20,21},[])
        with self.assertRaisesRegex(runtime.Refused,'OWN_NATIVE_CLIENT_COMMON_ADB_CONNECTION_OBSERVED'):
            runtime.validate_local_peers([dict(own,remote_port=5037)],[],{20},[])
        for remote in ['127.0.0.2','::ffff:127.0.0.1']:
            altered=dict(own,remote_address=remote)
            foreign=dict(peer,address=remote,pid=999)
            with self.subTest(remote=remote),self.assertRaisesRegex(runtime.Refused,'FOREIGN_LOCAL_PEER_TO_OWN_VM_OR_HELPER'):
                runtime.validate_local_peers([altered],[altered,foreign],{20,21},[])

    def test_original_image_changed_fails_before_launch(self):
        with tempfile.TemporaryDirectory(prefix='stage12-pure-') as temp:
            root=Path(temp); items=[]
            for name in ['source.properties','package.xml','build.prop','system.img','vendor.img','ramdisk.img','kernel-ranchu']:
                p=root/name; p.write_bytes(b'original')
                items.append({'path':str(p),'bytes':p.stat().st_size,'sha256':runtime.sha(p)})
            with patch.object(runtime,'IMAGE',root):
                runtime.validate_image_inputs({'image_files_readonly_inputs':items})
                (root/'system.img').write_bytes(b'changed!')
                with self.assertRaisesRegex(runtime.Refused,'ORIGINAL_IMAGE_CHANGED_BEFORE_LAUNCH'):
                    runtime.validate_image_inputs({'image_files_readonly_inputs':items})

    def test_native_image_pair_only_reads_approved_dos_path_no_nt_conversion(self):
        with tempfile.TemporaryDirectory(prefix='stage12-pure-') as temp:
            approved=Path(temp)/'emulator.exe';approved.write_bytes(b'pinned')
            calls=[]
            def metadata(path):
                calls.append(path)
                self.assertEqual(approved,path)
                return self.native_metadata()
            proof=runtime.prove_image(self.native_observation(),{str(approved):runtime.sha(approved)},metadata)
            self.assertEqual([approved,approved],calls)
            self.assertFalse(proof['observed_native_path_opened_or_converted'])
            self.assertFalse(proof['process_section_file_id_claimed'])

    def test_native_queries_mismatch_failure_truncation_or_relative_rejected(self):
        for mutation in ['different_image','wrong_length','failed_query','relative','one_api']:
            observed=self.native_observation()
            if mutation=='different_image': observed['native_queries'][1]['raw_image']+='wrong'
            if mutation=='wrong_length': observed['native_queries'][1]['characters']-=1
            if mutation=='failed_query': observed['native_queries'][1]['win32_error']=5
            if mutation=='relative': observed=self.native_observation('emulator.exe')
            if mutation=='one_api': observed['native_queries'].pop()
            with self.subTest(mutation=mutation),self.assertRaises(runtime.Refused):
                runtime.prove_image(observed,{},lambda path:self.fail('No file read before valid native query pair'))

    def test_native_foreign_volume_or_same_basename_never_maps_to_approved_path(self):
        with tempfile.TemporaryDirectory(prefix='stage12-pure-') as temp:
            approved=Path(temp)/'emulator.exe';approved.write_bytes(b'pinned')
            for image in ['\\Device\\HarddiskVolume4\\Owned\\emulator.exe','\\Device\\HarddiskVolume3\\Foreign\\emulator.exe']:
                with self.subTest(image=image),patch.object(runtime,'sha',side_effect=AssertionError('No read of foreign native image')):
                    with self.assertRaisesRegex(runtime.Refused,'NATIVE_PROCESS_PATH_OUTSIDE_DECLARED_PINNED_DOS_FILE_IDENTITIES'):
                        runtime.prove_image(self.native_observation(image),{str(approved):'pin'},lambda path:self.native_metadata())

    def test_native_matching_name_still_requires_pinned_hash_and_stable_file_id(self):
        with tempfile.TemporaryDirectory(prefix='stage12-pure-') as temp:
            approved=Path(temp)/'emulator.exe';approved.write_bytes(b'pinned')
            with self.assertRaisesRegex(runtime.Refused,'APPROVED_PROCESS_BINARY_CHANGED'):
                runtime.prove_image(self.native_observation(),{str(approved):'wrong'},lambda path:self.native_metadata())
            metadata=[self.native_metadata(),dict(self.native_metadata(),file_index=99)]
            with self.assertRaisesRegex(runtime.Refused,'PINNED_DOS_FILE_NATIVE_IDENTITY_CHANGED'):
                runtime.prove_image(self.native_observation(),{str(approved):runtime.sha(approved)},lambda path:metadata.pop(0))

    def test_native_handle_path_flags_lengths_and_reparse_metadata_checked(self):
        for metadata in [dict(self.native_metadata(),native_final_path_flags=0),
                         dict(self.native_metadata(),native_final_path_length=2),
                         dict(self.native_metadata(),native_final_path='emulator.exe')]:
            with self.subTest(metadata=metadata),self.assertRaises(runtime.Refused):
                runtime.validate_native_file_name(metadata)

    def test_fast_completed_canary_retains_measured_pre_resume_origin_without_live_fallback(self):
        initial=dict(self.native_observation(),running=True,image_source='CURRENT_NATIVE_OBSERVATION')
        class Job:
            roots=[initial]
        class Child:
            pid=20
            def identity(self):
                return dict(initial,running=False,native_queries=[],image_source='RETAINED_PRE_EXIT_OBSERVATION')
        observed=runtime.original_handle_image_observation(Job(),Child())
        self.assertEqual(initial,observed['image_measurement'])
        self.assertFalse(observed['current_liveness_claimed'])
        self.assertFalse(observed['current_original_handle']['running'])
        class FailedLiveChild(Child):
            def identity(self): raise runtime.Refused('LIVE_NATIVE_IMAGE_UNIDENTIFIABLE')
        with self.assertRaisesRegex(runtime.Refused,'LIVE_NATIVE_IMAGE_UNIDENTIFIABLE'):
            runtime.original_handle_image_observation(Job(),FailedLiveChild())

    def test_console_selects_only_announced_exact_private_home_or_avd_user(self):
        with tempfile.TemporaryDirectory(prefix='stage12-pure-') as temp:
            candidates=(Path(temp)/'profile/.emulator_console_auth_token',Path(temp)/'avd-user/.emulator_console_auth_token')
            for candidate in candidates:
                candidate.parent.mkdir(); candidate.write_bytes(b'SYNTHETIC_OWN_AUTH_TOKEN\n')
            with patch.object(runtime,'console_token_candidates',return_value=candidates), patch.object(Path,'home',side_effect=AssertionError('No real home lookup')):
                for candidate in candidates:
                    for spelling in {candidate.as_posix(),str(candidate),candidate.parent.as_posix()+'\\'+candidate.name,str(candidate.parent)+'/'+candidate.name}:
                        banner=("Android Console: Authentication required\r\nAndroid Console: you can find your <auth_token> in\r\n'"+spelling+"'\r\nOK\r\n").encode('utf-8')
                        self.assertEqual(candidate,runtime.select_console_token_path(banner))
                        self.assertEqual(b'SYNTHETIC_OWN_AUTH_TOKEN',runtime.read_private_console_token(banner))

    def test_console_foreign_relative_expanded_or_ambiguous_route_rejected_before_any_file_io(self):
        candidates=(Path('C:/PrivateRuntime/profile/.emulator_console_auth_token'),Path('C:/PrivateAvd/user/.emulator_console_auth_token'))
        banners=[b"'C:/Users/RealUser/.emulator_console_auth_token'\r\nOK\r\n",
                 b"'../.emulator_console_auth_token'\r\nOK\r\n",
                 b"'%USERPROFILE%/.emulator_console_auth_token'\r\nOK\r\n",
                 b"'C:/PrivateRuntime/profile/../foreign/.emulator_console_auth_token'\r\nOK\r\n",
                 ("'"+candidates[0].as_posix()+"'\r\n'"+candidates[1].as_posix()+"'\r\nOK\r\n").encode(),
                 b'Android Console: missing route\r\nOK\r\n']
        with patch.object(runtime,'console_token_candidates',return_value=candidates),patch.object(runtime,'no_links',side_effect=AssertionError('No filesystem metadata before route approval')) as metadata,patch.object(Path,'read_bytes',side_effect=AssertionError('No foreign read')) as read:
            for banner in banners:
                with self.subTest(banner=banner),self.assertRaises(runtime.Refused):
                    runtime.read_private_console_token(banner)
            metadata.assert_not_called();read.assert_not_called()

    def test_console_reparse_refused_before_token_read(self):
        candidate=Path('C:/PrivateRuntime/profile/.emulator_console_auth_token')
        banner=("'"+candidate.as_posix()+"'\r\nOK\r\n").encode()
        with patch.object(runtime,'console_token_candidates',return_value=(candidate,)),patch.object(runtime,'no_links',side_effect=runtime.Refused('REPARSE_REFUSED')),patch.object(Path,'read_bytes',side_effect=AssertionError('No token read')) as read:
            with self.assertRaisesRegex(runtime.Refused,'REPARSE_REFUSED'):
                runtime.read_private_console_token(banner)
            read.assert_not_called()

    def test_console_invalid_token_value_never_enters_error_text(self):
        with tempfile.TemporaryDirectory(prefix='stage12-pure-') as temp:
            candidate=Path(temp)/'.emulator_console_auth_token';secret=b'SYNTHETIC SECRET WITH SPACES';candidate.write_bytes(secret)
            banner=("'"+candidate.as_posix()+"'\r\nOK\r\n").encode()
            with patch.object(runtime,'console_token_candidates',return_value=(candidate,)):
                with self.assertRaises(runtime.Refused) as raised:
                    runtime.read_private_console_token(banner)
                self.assertNotIn(secret.decode(),str(raised.exception))

    def test_first_boot_joberror_preserves_full_context_and_closes_both_jobs_independently(self):
        # Exercise the real first_boot try/except/finally, journal, cleanup and
        # final result. Native construction/launch/socket APIs are replaced only
        # at their boundary; an actual JobError reaches the orchestration path.
        for emulator_close_fails in [False,True]:
            with self.subTest(emulator_close_fails=emulator_close_fails),tempfile.TemporaryDirectory(prefix='stage12-pure-') as temp:
                evidence=Path(temp);unit=evidence/'console-helper-policy';unit.mkdir();close_calls=[];created=[]
                partial={'pid':321,'creation_filetime':123456789,'running_before':True,'running_after':True,
                         'image':None,'raw_status':'LIVE_NATIVE_IMAGE_UNIDENTIFIABLE',
                         'member_context':{'enumerated_pid':321,'in_owned_job':True,'retained_handle':True}}
                injected=job_errors.JobError('NATIVE_IMAGE_IDENTITY_NOT_PROVEN',win32_error=5,receipt=partial)
                class Child:
                    pid=321
                    def identity(self): raise injected
                    def poll(self): return None
                class Job:
                    def __init__(self,**kwargs):
                        self.role='server' if not created else 'emulator';created.append(self)
                        self._children={321:Child()};self._member_handles={};self._pending_handles=set();self._job_closed=False
                    @property
                    def roots(self): return []
                    def launch(self,*args):return Child()
                    def members(self): raise injected
                    def close(self):
                        close_calls.append(self.role)
                        if self.role=='emulator' and emulator_close_fails:
                            raise job_errors.JobError('OWN_JOB_CLOSE_UNCONFIRMED',receipt={'empty_confirmed':False,'errors':['injected_close_failure']})
                        return {'empty_confirmed':True,'handles_closed':True}
                fake_native=types.SimpleNamespace(OwnedJob=Job,NativeImageBackend=lambda:None,
                                                  native_file_identity=lambda path:self.fail('No native metadata after injected identity failure'))
                admission=runtime.BASE/'.local-evidence/stage12/main/pure-test-admission.json'
                with ExitStack() as stack:
                    stack.enter_context(patch.dict(sys.modules,{'stage12_winjob':fake_native}))
                    for name,value in [('EVIDENCE',evidence),('no_links',lambda path:None),('sha',lambda path:'test-pin'),
                        ('read_json',lambda path:{'inventory':{'files':[]},'status':'PASSED_NO_AVD_NO_SERVER',
                                                'root':unit.as_posix(),'resource_owner_uuid':runtime.OWNER_UUID}),
                        ('validate_runtime_admission',lambda *args:None),
                        ('load_console_pin',lambda *args:{}),
                        ('verify_private_sdk',lambda:{'image_files_readonly_inputs':[]}),
                        ('host_preflight',lambda:{'reserved_listeners':[]}),
                        ('validate_environment',lambda *args:None),('listeners',lambda *args,**kwargs:[]),
                        ('tree_inventory',lambda path:{'files':[]})]:
                        stack.enter_context(patch.object(runtime,name,value))
                    with self.assertRaisesRegex(runtime.Refused,'FIRST_BOOT_FAILED_PRESERVED'):
                        runtime.first_boot(admission,'test-pin')
                result=json.loads((unit/'first-boot-r1/result.json').read_text(encoding='utf-8'))
                events=[json.loads(line) for line in (unit/'first-boot-r1/events.jsonl').read_text(encoding='utf-8').splitlines()]
                self.assertEqual(['emulator','server'],close_calls)
                self.assertEqual(2,len(created))
                self.assertEqual('FAILED_PRESERVED_NO_AUTOMATIC_RETRY',result['status'])
                self.assertEqual('JobError',result['exception']['error_type'])
                self.assertEqual('NATIVE_IMAGE_IDENTITY_NOT_PROVEN',result['exception']['code'])
                self.assertEqual(5,result['exception']['win32_error'])
                self.assertEqual(partial,result['exception']['native_receipt'])
                failed=[event for event in events if event['event']=='failed']
                self.assertEqual(1,len(failed))
                self.assertEqual(5,failed[0]['win32_error'])
                self.assertEqual(partial,failed[0]['native_receipt'])
                self.assertTrue(result['server_job_close']['handles_closed'])
                self.assertEqual(not emulator_close_fails,result['emulator_job_close']['empty_confirmed'])
                self.assertTrue((unit/'first-boot-r1/cleanup.json').is_file())


class ConsolePolicyTests(unittest.TestCase):
    @staticmethod
    def pin():
        image='\\Device\\HarddiskVolume3\\Windows\\System32\\conhost.exe'
        metadata=dict(PrivateRuntimeTests.native_metadata(image),links=2,metadata_stable=True)
        return {'schema':'stage12-exact-os-console-file-pin-v1',
                'path':getattr(runtime,'OS_CONSOLE',Path('C:/Windows/System32/conhost.exe')).as_posix(),
                'sha256':getattr(runtime,'OS_CONSOLE_SHA','c86146188b78e484f32cc29e2331689659943b47689a85570342044f499938ac'),'metadata':metadata}

    @staticmethod
    def member(pid=22):
        image='\\Device\\HarddiskVolume3\\Windows\\System32\\conhost.exe'
        return dict(PrivateRuntimeTests.native_observation(image),pid=pid,running=True,in_owned_job=True)

    @staticmethod
    def manifest():
        names=['emulator.exe','emulator-check.exe','qemu/windows-x86_64/qemu-system-x86_64.exe',
               'qemu/windows-x86_64/qemu-system-x86_64-headless.exe','netsimd.exe','crashpad_handler.exe',
               'qemu-img.exe','mksdcard.exe','bin64/e2fsck.exe','bin64/resize2fs.exe','bin64/tune2fs.exe']
        return {'image_files_readonly_inputs':[], 'emulator_package':{'files':[{'path':n,'sha256':'sdk-pin'} for n in names]}}

    def test_exact_console_hardlink_pin_accepted_but_sdk_link1_unchanged(self):
        pin=self.pin();calls=[]
        def metadata(path):
            calls.append(path);self.assertEqual(runtime.OS_CONSOLE,path);return copy.deepcopy(pin['metadata'])
        with patch.object(runtime,'read_json',return_value=pin),patch.object(runtime,'no_links'),patch.object(runtime,'sha',return_value=runtime.OS_CONSOLE_SHA):
            self.assertEqual(pin,runtime.load_console_pin(metadata))
            proof=runtime.prove_live_member(self.member(),'server',self.manifest(),pin,metadata)
        self.assertEqual('OS_CONSOLE',proof['role']);self.assertEqual('NONE',proof['endpoint_permission'])
        self.assertFalse(proof['observed_native_path_opened_or_converted'])
        self.assertFalse(runtime.same_file_identity(pin['metadata'],pin['metadata']))
        self.assertEqual([runtime.OS_CONSOLE]*4,calls)

    def test_console_pin_wrong_path_hash_schema_or_missing_metadata_refused_before_file(self):
        for mutation in ['path','sha256','schema','metadata']:
            pin=self.pin()
            if mutation=='metadata':pin.pop('metadata')
            else:pin[mutation]='FOREIGN'
            with self.subTest(mutation=mutation),patch.object(runtime,'read_json',return_value=pin):
                with self.assertRaisesRegex(runtime.Refused,'OS_CONSOLE_PIN_NOT_EXACT'):
                    runtime.load_console_pin(lambda p:self.fail('No file before exact pin'))

    def test_console_exact_metadata_adversaries_refuse(self):
        original=self.pin()['metadata']
        for key,value in [('volume_serial',9),('file_index',9),('links',1),('links',0),('bytes',9),
                          ('last_write_filetime',9),('attributes',32|0x400),('attributes',32|0x10),
                          ('native_final_path','\\Device\\ForeignVolume\\Windows\\System32\\conhost.exe'),
                          ('native_final_path_flags',0),('native_final_path_length',1),('metadata_stable',False)]:
            changed=dict(original,**{key:value})
            with self.subTest(key=key,value=value):
                try:matches=runtime.console_metadata_matches(changed,original)
                except runtime.Refused:matches=False
                self.assertFalse(matches)

    def test_console_hash_and_after_handle_metadata_changes_refuse(self):
        pin=self.pin();member=self.member()
        with patch.object(runtime,'no_links'),patch.object(runtime,'sha',return_value='ALTERED'):
            with self.assertRaisesRegex(runtime.Refused,'OS_CONSOLE_MEMBER_HASH_CHANGED'):
                runtime.prove_live_member(member,'emulator',self.manifest(),pin,lambda p:copy.deepcopy(pin['metadata']))
        for key in ['file_index','volume_serial','links','last_write_filetime']:
            values=[copy.deepcopy(pin['metadata']),dict(pin['metadata'],**{key:99})]
            with self.subTest(key=key),patch.object(runtime,'no_links'),patch.object(runtime,'sha',return_value=runtime.OS_CONSOLE_SHA):
                with self.assertRaisesRegex(runtime.Refused,'OS_CONSOLE_MEMBER_FILE_CHANGED'):
                    runtime.prove_live_member(member,'emulator',self.manifest(),pin,lambda p:values.pop(0))

    def test_console_reparse_refused_before_metadata_hash_read(self):
        with patch.object(runtime,'no_links',side_effect=runtime.Refused('REPARSE_REFUSED')),patch.object(runtime,'sha',side_effect=AssertionError('No binary read')):
            with self.assertRaisesRegex(runtime.Refused,'REPARSE_REFUSED'):
                runtime.prove_live_member(self.member(),'server',self.manifest(),self.pin(),lambda p:self.fail('No metadata'))

    def test_console_native_foreign_samebasename_is_not_adopted_or_opened(self):
        pin=self.pin();approved=runtime.ORIGINAL_SDK/'platform-tools/adb.exe';calls=[]
        def metadata(path):
            calls.append(path);self.assertEqual(approved,path)
            return PrivateRuntimeTests.native_metadata('\\Device\\HarddiskVolume3\\SDK\\adb.exe')
        for image in ['\\Device\\HarddiskVolume4\\Windows\\System32\\conhost.exe',
                      '\\Device\\HarddiskVolume3\\Foreign\\conhost.exe','\\Device\\HarddiskVolume3\\Windows\\System32\\other.exe']:
            observed=dict(PrivateRuntimeTests.native_observation(image),running=True,in_owned_job=True)
            with self.subTest(image=image),patch.object(runtime,'no_links'),patch.object(runtime,'sha',side_effect=AssertionError('No foreign image hash')):
                with self.assertRaisesRegex(runtime.Refused,'NATIVE_PROCESS_PATH_OUTSIDE_DECLARED_PINNED_DOS_FILE_IDENTITIES'):
                    runtime.prove_live_member(observed,'server',self.manifest(),pin,metadata)
        self.assertEqual([approved]*3,calls)

    def test_console_requires_live_native_owned_handle_birth_and_both_queries(self):
        for key,value in [('running',False),('in_owned_job',False),('pid',0),('creation_filetime',0),('image_format','WIN32')]:
            with self.subTest(key=key),self.assertRaisesRegex(runtime.Refused,'LIVE_NATIVE_OWNED_MEMBER_REQUIRED'):
                runtime.prove_live_member(dict(self.member(),**{key:value}),'server',self.manifest(),self.pin(),lambda p:self.fail('No file read'))
        observed=self.member();observed['native_queries'][1]['characters']=1
        with self.assertRaisesRegex(runtime.Refused,'NATIVE_IMAGE_QUERY_PAIR_DISAGREES_OR_IS_PARTIAL'):
            runtime.prove_live_member(observed,'server',self.manifest(),self.pin(),lambda p:self.fail('No file read'))
        with self.assertRaisesRegex(runtime.Refused,'OS_POLICY_ROLE_NOT_ADMITTED'):
            runtime.prove_live_member(self.member(),'canary',self.manifest(),self.pin(),lambda p:self.fail('No file read'))

    def test_console_tcp_connections_any_state_refused_and_never_motor(self):
        proof={'role':'OS_CONSOLE','process':self.member(),'approved_path':runtime.OS_CONSOLE.as_posix()}
        for state in [2,3,4,5,8]:
            with self.subTest(state=state),self.assertRaisesRegex(runtime.Refused,'OS_CONSOLE_TCP_ENDPOINT_REFUSED'):
                runtime.validate_console_tcp([{'pid':22,'state':state,'port':12345}],[proof])
        engine_path=(runtime.SDK/'emulator/qemu/windows-x86_64/qemu-system-x86_64.exe').as_posix()
        self.assertEqual(set(),runtime.engine_member_pids([dict(proof,approved_path=engine_path)]))
        self.assertEqual({22},runtime.engine_member_pids([dict(proof,role='SDK',approved_path=engine_path)]))
        runtime.validate_console_tcp([{'pid':99,'state':5}],[proof])

    def test_server_exact_root_and_individually_proved_os_role_not_count(self):
        root=dict(self.member(20),creation_filetime=42);console=self.member(22)
        rootproof={'role':'SDK','process':root,'approved_path':str(runtime.ORIGINAL_SDK/'platform-tools/adb.exe'),'approved_sha256':runtime.ADB_SHA}
        cap={'members':[root,console],'root_origin':{'image_measurement':root},'root_proof':rootproof,
             'proofs':[rootproof,{'role':'OS_CONSOLE','process':console}], 'root_poll':None,
             'reserved_listeners':[{'address':'127.0.0.1','port':5048,'pid':20}]}
        runtime.validate_server_capture(cap,20,42)
        for mutation in ['missing_root','wrong_birth','wrong_root_pid','stale_root','extra_sdk','missing_proof','wrong_root_file','helper_listener']:
            changed=copy.deepcopy(cap)
            if mutation=='missing_root':changed['members']=[console]
            if mutation=='wrong_birth':changed['members'][0]['creation_filetime']=999
            if mutation=='wrong_root_pid':changed['root_proof']['process']['pid']=999
            if mutation=='stale_root':changed['root_origin']['image_measurement']['running']=False
            if mutation=='extra_sdk':changed['proofs'][1]['role']='SDK'
            if mutation=='missing_proof':changed['proofs'].pop()
            if mutation=='wrong_root_file':changed['root_proof']['approved_path']=runtime.OS_CONSOLE.as_posix()
            if mutation=='helper_listener':changed['reserved_listeners'][0]['pid']=22
            with self.subTest(mutation=mutation),self.assertRaises(runtime.Refused):runtime.validate_server_capture(changed,20,42)

    def test_capture_partial_native_error_keeps_other_observations_before_raise(self):
        error=job_errors.JobError('NATIVE_QUERY_FAILED',win32_error=5,receipt={'pid':22,'creation_filetime':42,'member_context':{'in_owned_job':True}})
        class Child:
            pid=20
            def identity(self):raise error
            def poll(self):return None
        class Job:
            _children={20:object()};_member_handles={22:object()};_pending_handles=set();_job_closed=False
            roots=[]
            def members(self):raise error
        with tempfile.TemporaryDirectory(prefix='stage12-console-pure-') as temp:
            journal=runtime.Journal(Path(temp))
            try:
                with patch.object(runtime,'listeners',return_value=[]):
                    with self.assertRaises(job_errors.JobError) as raised:
                        runtime.capture_job(Job(),Child(),'server',self.manifest(),self.pin(),lambda p:self.fail('No metadata'),journal)
                self.assertIs(error,raised.exception)
            finally:journal.close()
            events=[json.loads(s) for s in (Path(temp)/'events.jsonl').read_text(encoding='utf-8').splitlines()]
            captured=events[-1]['snapshot'];self.assertEqual('job-capture-before-policy',events[-1]['event'])
            self.assertEqual([],captured['own_connections']);self.assertEqual([],captured['reserved_listeners'])
            errors=captured['observation_errors'];self.assertEqual(2,len(errors))
            self.assertTrue(all(e['native_receipt']==error.receipt and e['win32_error']==5 for e in errors))

    def test_close_keeps_full_native_error_context(self):
        error=job_errors.JobError('OWN_JOB_CLOSE_UNCONFIRMED',win32_error=6,receipt={'empty_confirmed':False,'handles_closed':False})
        class Job:
            def close(self):raise error
        record=runtime.close_job_record(Job())
        self.assertEqual(runtime.exception_record(error),record['exception']);self.assertFalse(runtime.close_proven(record))

    def test_completed_launcher_keeps_origin_and_same_tcp_table_without_foreign_logs(self):
        image='\\Device\\HarddiskVolume3\\Owned\\emulator.exe'
        initial=dict(PrivateRuntimeTests.native_observation(image),running=True,image_source='CURRENT_NATIVE_OBSERVATION')
        class Child:
            pid=20
            last=copy.deepcopy(initial)
            def identity(self):
                self.last=dict(initial,running=False,native_queries=[],image_source='RETAINED_PRE_EXIT_OBSERVATION')
                return copy.deepcopy(self.last)
            def poll(self):return 0
        child=Child()
        class Job:
            _children={20:child};_member_handles={};_pending_handles=set();_job_closed=False
            @property
            def roots(self):return [copy.deepcopy(child.last)]
            def members(self):return []
        own={'pid':20,'address':'127.0.0.1','port':50000,'state':5}
        foreign={'pid':999,'address':'FOREIGN_TABLE_ROW_DO_NOT_LOG','port':60000,'state':5}
        tcp_calls=[]
        def ports(*args,**kwargs):
            if kwargs.get('connections'):
                tcp_calls.append(kwargs);return [copy.deepcopy(own),copy.deepcopy(foreign)]
            return []
        metadata=PrivateRuntimeTests.native_metadata(image)
        with tempfile.TemporaryDirectory(prefix='stage12-console-launcher-') as temp:
            journal=runtime.Journal(Path(temp))
            try:
                with patch.object(runtime,'listeners',ports),patch.object(runtime,'no_links'),patch.object(runtime,'sha',return_value='sdk-pin'):
                    first,table1=runtime.capture_job(Job(),child,'emulator',self.manifest(),self.pin(),lambda p:metadata,journal)
                    second,table2=runtime.capture_job(Job(),child,'emulator',self.manifest(),self.pin(),lambda p:metadata,journal,first['root_origin'])
            finally:journal.close()
            self.assertEqual([],child.last['native_queries'])
            self.assertEqual(2,len(second['root_origin']['image_measurement']['native_queries']))
            self.assertFalse(second['root_origin']['current_liveness_claimed'])
            self.assertEqual([own],second['own_connections']);self.assertEqual([own,foreign],table2)
            self.assertEqual(2,len(tcp_calls));self.assertNotIn('FOREIGN_TABLE_ROW_DO_NOT_LOG',(Path(temp)/'events.jsonl').read_text(encoding='utf-8'))

    def test_real_first_boot_accepts_proved_console_and_reaches_mock_vm_boundary(self):
        # Causal regression: old81 source stops at len1; new source must get as
        # far as the deliberately failing fake emulator launch, never a real VM.
        with tempfile.TemporaryDirectory(prefix='stage12-console-causal-') as temp:
            evidence=Path(temp);unit=evidence/'console-helper-policy';unit.mkdir();created=[];closed=[]
            image='\\Device\\HarddiskVolume3\\SDK\\adb.exe'
            root=dict(PrivateRuntimeTests.native_observation(image),pid=701,creation_filetime=42,running=True,in_owned_job=True)
            console=self.member(702);pin=self.pin();manifest=self.manifest()
            marker=job_errors.JobError('PURE_EMULATOR_LAUNCH_BOUNDARY_REACHED',receipt={'no_real_vm':True})
            class Child:
                pid=701
                def identity(self):return copy.deepcopy(root)
                def poll(self):return None
            class Job:
                def __init__(self,**kwargs):
                    self.role='server' if not created else 'emulator';created.append(self)
                    self._children={701:Child()} if self.role=='server' else {};self._member_handles={702:object()} if self.role=='server' else {}
                    self._pending_handles=set();self._job_closed=False
                @property
                def roots(self):return [copy.deepcopy(root)] if self.role=='server' else []
                def launch(self,*args):
                    if self.role=='emulator':raise marker
                    return Child()
                def members(self):return [copy.deepcopy(root),copy.deepcopy(console)]
                def close(self):closed.append(self.role);return {'empty_confirmed':True,'handles_closed':True}
            class Client:
                def __init__(self,*args):pass
                def host(self,service):return b'0029' if service=='host:version' else b''
            def read_json(path):return {'inventory':{'files':[]},'status':'PASSED_NO_AVD_NO_SERVER',
                                         'root':unit.as_posix(),'resource_owner_uuid':runtime.OWNER_UUID}
            def ports(*args,**kwargs):
                if closed:return []
                row={'address':'127.0.0.1','port':5048,'pid':701}
                return [dict(row,remote_address='0.0.0.0',remote_port=0,state=2)] if kwargs.get('connections') else [row]
            def sdk_proof(observation,allowed,metadata):
                return {'process':observation,'approved_path':next(iter(allowed)), 'approved_sha256':next(iter(allowed.values()))}
            fake=types.SimpleNamespace(OwnedJob=Job,NativeImageBackend=lambda:None,
                                       native_file_identity=lambda path:copy.deepcopy(pin['metadata']))
            with ExitStack() as stack:
                stack.enter_context(patch.dict(sys.modules,{'stage12_winjob':fake}))
                for name,value in [('EVIDENCE',evidence),('no_links',lambda p:None),
                    ('sha',lambda p:runtime.OS_CONSOLE_SHA if p==getattr(runtime,'OS_CONSOLE',None) else 'test-pin'),
                    ('read_json',read_json),('validate_runtime_admission',lambda *a:None),('verify_private_sdk',lambda:manifest),
                    ('load_console_pin',lambda *a:pin),('host_preflight',lambda:{'reserved_listeners':[]}),
                    ('validate_environment',lambda *a:None),('listeners',ports),('tree_inventory',lambda p:{'files':[]}),
                    ('prove_image',sdk_proof),('PrivateClient',Client)]:
                    stack.enter_context(patch.object(runtime,name,value,create=True))
                admission=runtime.BASE/'.local-evidence/stage12/main/pure-console-causal.json'
                with self.assertRaisesRegex(runtime.Refused,'FIRST_BOOT_FAILED_PRESERVED'):runtime.first_boot(admission,'test-pin')
            result_path=unit/'first-boot-r1/result.json'
            if not result_path.exists():result_path=evidence/'first-boot/result.json'
            result=json.loads(result_path.read_text(encoding='utf-8'))
            self.assertEqual('PURE_EMULATOR_LAUNCH_BOUNDARY_REACHED',result['exception']['code'])
            self.assertEqual(['emulator','server'],closed);self.assertEqual(2,len(created))
            self.assertEqual('FAILED_PRESERVED_NO_AUTOMATIC_RETRY',result['status'])

    @contextmanager
    def full_boot_double(self, mode='normal'):
        with tempfile.TemporaryDirectory(prefix='stage12-full-boot-pure-') as temp,ExitStack() as stack:
            evidence=Path(temp);unit=evidence/'console-helper-policy';unit.mkdir()
            state=types.SimpleNamespace(emulator=False,killed=False,closed=[],startup_count=0,shutdown_count=0,registered=False)
            pin=self.pin();manifest=self.manifest()
            paths={701:runtime.ORIGINAL_SDK/'platform-tools/adb.exe',711:runtime.SDK/'emulator/emulator.exe',
                   712:runtime.SDK/'emulator/qemu/windows-x86_64/qemu-system-x86_64.exe',713:runtime.SDK/'emulator/netsimd.exe'}
            def identity(pid):
                image='\\Device\\SyntheticSdk\\'+paths[pid].name
                return dict(PrivateRuntimeTests.native_observation(image),pid=pid,creation_filetime=pid*10,
                            running=True,in_owned_job=True,image_source='CURRENT_NATIVE_OBSERVATION')
            class Child:
                def __init__(self,pid):self.pid=pid;self.last=identity(pid)
                def identity(self):
                    live=not state.killed or self.pid==701
                    if mode=='server_not_live' and self.pid==701:live=False
                    self.last=dict(identity(self.pid),running=live)
                    if not live:self.last.update(native_queries=[],image_source='RETAINED_PRE_EXIT_OBSERVATION')
                    return copy.deepcopy(self.last)
                def poll(self):return None if not state.killed or self.pid==701 else 0
            class Job:
                def __init__(self,**kwargs):
                    self.role='server' if not hasattr(state,'server_job') else 'emulator'
                    if self.role=='server':state.server_job=self
                    self.child=Child(701 if self.role=='server' else 711)
                    self._children={};self._member_handles={};self._pending_handles=set();self._job_closed=False
                @property
                def roots(self):return [copy.deepcopy(self.child.last)] if self._children else []
                def launch(self,*args):
                    self._children[self.child.pid]=self.child
                    if self.role=='emulator':state.emulator=True
                    return self.child
                def members(self):
                    if self.role=='server':values=[self.child.identity(),dict(ConsolePolicyTests.member(702),creation_filetime=7020)]
                    elif state.killed:
                        state.shutdown_count+=1
                        values=[identity(713)] if mode=='shutdown_peer' and state.shutdown_count==1 else []
                    else:
                        state.startup_count+=1
                        values=[self.child.identity(),identity(712),identity(713),dict(ConsolePolicyTests.member(714),creation_filetime=7140)]
                    self._member_handles={m['pid']:object() for m in values if m['pid']!=self.child.pid}
                    return copy.deepcopy(values)
                def close(self):state.closed.append(self.role);return {'empty_confirmed':True,'handles_closed':True}
            def tcp_rows():
                rows=[{'address':'127.0.0.1','port':5048,'pid':701,'remote_address':'0.0.0.0','remote_port':0,'state':2}]
                bad=(mode=='startup_peer' and state.startup_count==1 and not state.killed) or (mode=='shutdown_peer' and state.killed and state.shutdown_count==1)
                if state.emulator and not state.killed and not bad:
                    rows += [{'address':'127.0.0.1','port':p,'pid':999 if mode=='foreign_engine' else 712,
                              'remote_address':'0.0.0.0','remote_port':0,'state':2} for p in [5588,5589]]
                if bad:
                    rows += [{'address':'127.0.0.1','port':50000,'pid':713,'remote_address':'127.0.0.1','remote_port':60000,'state':5},
                             {'address':'127.0.0.1','port':60000,'pid':999,'remote_address':'127.0.0.1','remote_port':50000,'state':5}]
                return rows
            def ports(ports=runtime.PORTS,pids=None,connections=False):
                if state.closed:return []
                if mode=='initial_tcp_error':raise job_errors.JobError('PURE_TCP_TABLE_FAILED',win32_error=5,receipt={'query':'tcp'})
                rows=tcp_rows()
                if not connections:rows=[{k:v for k,v in row.items() if k in ['address','port','pid']} for row in rows if row['state']==2]
                return [row for row in rows if (ports is None or row['port'] in ports) and (pids is None or row['pid'] in pids)]
            class Client:
                def __init__(self,guard,journal):self.guard=guard
                def host(self,service):
                    self.guard()
                    if service=='host:emulator:5589':state.registered=True
                    value=b'0029' if service=='host:version' else (b'emulator-5588 device\n' if service=='host:devices-l' and state.emulator else b'')
                    self.guard();return value
                def shell(self,name):
                    self.guard();return '1' if name=='boot_complete' else ('STAGE12_SYNC_OK' if name=='sync' else runtime.PROPERTY_EXPECTATIONS[name])
            class Console:
                def __init__(self):self.replies=[b'Android Console Authentication required\r\nOK\r\n',b'OK\r\n']
                def settimeout(self,value):pass
                def recv(self,size):return self.replies.pop(0) if self.replies else b''
                def sendall(self,data):
                    if data==b'kill\n':state.killed=True
                def close(self):pass
            def read_json(path):return {'inventory':{'files':[]},'status':'PASSED_NO_AVD_NO_SERVER',
                                        'root':unit.as_posix(),'resource_owner_uuid':runtime.OWNER_UUID}
            def sdk_proof(observation,allowed,metadata):
                approved=str(paths[observation['pid']])
                if approved not in allowed:raise runtime.Refused('PURE_WRONG_SDK_ROOT')
                return {'process':observation,'approved_path':approved,'approved_sha256':allowed[approved]}
            fake=types.SimpleNamespace(OwnedJob=Job,NativeImageBackend=lambda:None,native_file_identity=lambda p:copy.deepcopy(pin['metadata']))
            stack.enter_context(patch.dict(sys.modules,{'stage12_winjob':fake}))
            stack.enter_context(patch.object(runtime.socket,'create_connection',return_value=Console()))
            for name,value in [('EVIDENCE',evidence),('no_links',lambda p:None),
                ('sha',lambda p:runtime.OS_CONSOLE_SHA if p==runtime.OS_CONSOLE else 'test-pin'),('read_json',read_json),
                ('validate_runtime_admission',lambda *a:None),('verify_private_sdk',lambda:manifest),('load_console_pin',lambda *a:pin),
                ('host_preflight',lambda:{'reserved_listeners':[]}),('validate_environment',lambda *a:None),('listeners',ports),
                ('tree_inventory',lambda p:{'files':[]}),('prove_image',sdk_proof),('PrivateClient',Client),
                ('read_private_console_token',lambda banner:b'SYNTHETIC_ONLY')]:stack.enter_context(patch.object(runtime,name,value))
            yield state,evidence,unit

    def test_complete_mock_boot_keeps_server_valid_after_own_engine_ports_and_closes(self):
        with self.full_boot_double() as (state,evidence,unit):
            try:runtime.first_boot(runtime.BASE/'.local-evidence/stage12/main/pure-full-boot.json','test-pin')
            except runtime.Refused:
                failed=json.loads((unit/'first-boot-r1/result.json').read_text(encoding='utf-8'))
                self.fail('Complete mock boot rejected: '+failed.get('exception',{}).get('code','UNKNOWN'))
            result=json.loads((unit/'first-boot-r1/result.json').read_text(encoding='utf-8'))
            self.assertEqual('FIRST_BOOT_IDENTITY_SYNC_CLOSE_PASSED_NO_INSTALL',result['status'])
            self.assertTrue(result['guest_sync_confirmed']);self.assertEqual(['emulator','server'],state.closed)
            self.assertTrue(state.registered)

    def test_foreign_engine_still_refused_before_registration_after_server_phase_split(self):
        with self.full_boot_double('foreign_engine') as (state,evidence,unit):
            with self.assertRaisesRegex(runtime.Refused,'FIRST_BOOT_FAILED_PRESERVED'):
                runtime.first_boot(runtime.BASE/'.local-evidence/stage12/main/pure-full-boot.json','test-pin')
            result=json.loads((unit/'first-boot-r1/result.json').read_text(encoding='utf-8'))
            self.assertEqual('ENGINE_ENDPOINT_NOT_OWNED_OR_NOT_LOOPBACK',result['exception']['code'])
            self.assertFalse(state.registered);self.assertEqual(['emulator','server'],state.closed)

    def test_foreign_peer_observed_at_startup_and_shutdown_is_rejected_with_evidence(self):
        for phase in ['startup_peer','shutdown_peer']:
            with self.subTest(phase=phase),self.full_boot_double(phase) as (state,evidence,unit):
                with self.assertRaisesRegex(runtime.Refused,'FIRST_BOOT_FAILED_PRESERVED'):
                    runtime.first_boot(runtime.BASE/'.local-evidence/stage12/main/pure-full-boot.json','test-pin')
                result=json.loads((unit/'first-boot-r1/result.json').read_text(encoding='utf-8'))
                self.assertEqual('FOREIGN_LOCAL_PEER_TO_OWN_VM_OR_HELPER',result['exception']['code'])
                self.assertEqual(['emulator','server'],state.closed)
                events=[json.loads(s) for s in (unit/'first-boot-r1/events.jsonl').read_text(encoding='utf-8').splitlines()]
                phase_name='startup' if phase=='startup_peer' else 'shutdown'
                self.assertTrue(any(e.get('phase')==phase_name and any(row['pid']==999 for row in e.get('correlated_local_peers',[])) for e in events))

    def test_initial_tcp_error_keeps_member_observations_before_closing(self):
        with self.full_boot_double('initial_tcp_error') as (state,evidence,unit):
            with self.assertRaisesRegex(runtime.Refused,'FIRST_BOOT_FAILED_PRESERVED'):
                runtime.first_boot(runtime.BASE/'.local-evidence/stage12/main/pure-full-boot.json','test-pin')
            result=json.loads((unit/'first-boot-r1/result.json').read_text(encoding='utf-8'))
            self.assertEqual('PURE_TCP_TABLE_FAILED',result['exception']['code'])
            events=[json.loads(s) for s in (unit/'first-boot-r1/events.jsonl').read_text(encoding='utf-8').splitlines()]
            self.assertTrue(any(e.get('operation')=='members' for e in events));self.assertFalse(state.emulator)
            self.assertEqual(['emulator','server'],state.closed)

    def test_nonrunning_server_origin_never_claims_current_liveness(self):
        with self.full_boot_double('server_not_live') as (state,evidence,unit):
            with self.assertRaisesRegex(runtime.Refused,'FIRST_BOOT_FAILED_PRESERVED'):
                runtime.first_boot(runtime.BASE/'.local-evidence/stage12/main/pure-full-boot.json','test-pin')
            events=[json.loads(s) for s in (unit/'first-boot-r1/events.jsonl').read_text(encoding='utf-8').splitlines()]
            cap=next(e['snapshot'] for e in events if e['event']=='job-capture-before-policy')
            self.assertFalse(cap['root_origin']['current_liveness_claimed']);self.assertFalse(state.emulator)
            self.assertEqual(['emulator','server'],state.closed)

    def test_console_listener_uses_one_tcp_sample_and_cannot_disappear_between_views(self):
        root=dict(PrivateRuntimeTests.native_observation('\\Device\\SyntheticSdk\\adb.exe'),running=True,in_owned_job=True)
        console=self.member();calls=[]
        class Child:
            pid=20
            def identity(self):return root
            def poll(self):return None
        class Job:
            roots=[root];_children={20:Child()};_member_handles={22:object()};_pending_handles=set();_job_closed=False
            def members(self):return [root,console]
        def ports(*args,**kwargs):
            calls.append(kwargs)
            if kwargs.get('connections'):
                return [{'address':'127.0.0.1','port':p,'pid':pid,'remote_address':'0.0.0.0','remote_port':0,'state':2}
                        for p,pid in [(5048,20),(50000,22)]] if len(calls)==1 else []
            return [{'address':'127.0.0.1','port':5048 if len(calls)==1 else 50000,'pid':20 if len(calls)==1 else 22}]
        with tempfile.TemporaryDirectory(prefix='stage12-console-tcp-') as temp:
            journal=runtime.Journal(Path(temp))
            try:
                with patch.object(runtime,'listeners',ports),patch.object(runtime,'prove_image',return_value={'process':root}), \
                     patch.object(runtime,'prove_live_member',side_effect=lambda m,*a:{'process':m,'role':'OS_CONSOLE' if m['pid']==22 else 'SDK'}):
                    with self.assertRaisesRegex(runtime.Refused,'OS_CONSOLE_TCP_ENDPOINT_REFUSED'):
                        runtime.capture_job(Job(),Child(),'server',self.manifest(),self.pin(),lambda p:self.fail('No native file'),journal)
            finally:journal.close()
            self.assertEqual(1,len(calls));self.assertTrue(calls[0]['connections'])
            cap=json.loads((Path(temp)/'events.jsonl').read_text(encoding='utf-8').splitlines()[-1])['snapshot']
            self.assertTrue(any(row['pid']==22 for row in cap['own_listeners']))

    def test_server_phase_allows_own_engine_after_initial_gate_only(self):
        root=dict(self.member(20),creation_filetime=42)
        proof={'role':'SDK','process':root,'approved_path':str(runtime.ORIGINAL_SDK/'platform-tools/adb.exe'),'approved_sha256':runtime.ADB_SHA}
        cap={'members':[root],'root_origin':{'image_measurement':root},'root_proof':proof,'proofs':[proof],
             'root_poll':None,'reserved_listeners':[{'address':'127.0.0.1','port':5048,'pid':20}]+
               [{'address':'127.0.0.1','port':p,'pid':30} for p in [5588,5589]]}
        runtime.validate_server_capture(cap,20,42)
        with self.assertRaisesRegex(runtime.Refused,'ENGINE_ENDPOINT_EXISTS_BEFORE_OWN_BOOT'):
            runtime.validate_server_capture(cap,20,42,before_engine=True)


if __name__ == '__main__':
    unittest.main()
