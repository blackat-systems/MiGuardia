"""Stage12 AVD boundary/protocol adversaries. All transports are local fakes."""
import copy
from datetime import datetime, timezone, timedelta
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import stage12_avd as a


class Stage12AvdTest(unittest.TestCase):
    def config(self):
        return {"name": a.NAME, "root": a.ROOT, "console_port": 5588, "serial": "emulator-5588",
                "server_host": "127.0.0.1", "server_port": 5048, "cores": 2, "ram_mib": 2048,
                "userdata_gib": 6, "resolution": "720x1600", "density": 320}

    def snapshot(self):
        return {"schema": "stage12-avd-preflight-v1", "utc": "2026-10-23T19:00:00Z", "contract_sha256": a.CONTRACT,
                "resource": self.config(), "root_state": "ABSENT", "capacity": {"free_ram_bytes": 6*a.GIB, "free_disk_bytes": 100*a.GIB},
                "listeners": [], "processes": [], "image": {"path": a.IMAGE, "api": 36, "abi": "x86_64", "tag": "default",
                    "revision": 2, "build": "BE2A.250530.026.D1", "metadata_sha256": "c"*64, "full_images_hashed": False},
                "tools": {"adb": {"path": a.SDK+"/platform-tools/adb.exe", "sha256": a.ADB_HASH, "version": "37.0.1-15733141"},
                    "emulator": {"path": a.SDK+"/emulator/emulator.exe", "sha256": a.EMULATOR_HASH, "version": "37.2.12.0-16428233"},
                    "avdmanager": {"path": a.SDK+"/cmdline-tools/22.0/bin/avdmanager.bat", "metadata_revision": "22.0", "sha256": "d"*64}},
                "acceleration": {"status": "NOT_MEASURED", "detail": "No acceleration command run in this block"},
                "restriction_proof": {"one_device_usb_only": True, "adb_usb_zero": True, "adb_emu_zero": True, "adb_mdns_zero": True,
                    "raw_client_no_autostart_mock": True, "native_emulator_no_autostart_proven": False,
                    "restricted_server_runtime_verified": False, "main_review": False}}

    def validate(self, value):
        return a.validate_snapshot(value, datetime(2026,10,23,19,0,30,tzinfo=timezone.utc))

    def test_valid_preparation_keeps_android_blocked_and_specs_are_inert(self):
        result = self.validate(self.snapshot())
        self.assertFalse(result["android_admitted"])
        self.assertIn("NATIVE_EMULATOR_NO_AUTOSTART_PROVEN", result["blocking_gates"])
        self.assertIn("MAIN_REVIEW", result["blocking_gates"])
        self.assertEqual("0", result["server_spec"]["environment"]["ADB_USB"])
        self.assertEqual("0", result["server_spec"]["environment"]["ADB_EMU"])
        self.assertEqual("0", result["server_spec"]["environment"]["ADB_MDNS"])
        self.assertEqual(["server", "nodaemon"], result["server_spec"]["argv"][-2:])
        self.assertIn(a.STUB, result["emulator_spec"]["argv"])
        self.assertIn("-adb-path", result["emulator_spec"]["argv"])
        self.assertNotIn("-no-direct-adb", result["emulator_spec"]["argv"])

    def test_wrong_root_name_serial_common_server_alias_and_size_are_rejected(self):
        for changes in ({"root": a.ROOT+"/../other"}, {"root": a.ROOT+"/"}, {"root": a.ROOT.replace("/", "\\")},
                        {"name": "MiGuardia_Stage11"}, {"serial": "physical-device-fixture"}, {"server_port": 5037},
                        {"server_host": "localhost"}, {"console_port": True}, {"ram_mib": True}, {"density": 240}):
            with self.subTest(changes=changes), self.assertRaises(a.ValidationError): a.resource({**self.config(), **changes})

    def test_alternate_requires_record_before_start(self):
        config = {**self.config(), "console_port": 5590, "serial": "emulator-5590"}
        with self.assertRaises(a.ValidationError): a.resource(config)
        config["alternate_declaration"] = {"recorded_before_start": True, "reason": "5588 collision recorded before resource action"}
        self.assertEqual(5590, a.resource(config)["console_port"])

    def test_stale_observation_wrong_tools_image_ports_and_capacity_are_rejected(self):
        edits = [lambda v:v.update(utc="2026-10-23T18:00:00Z"), lambda v:v.update(utc="2026-10-23T20:00:00Z"),
                 lambda v:v["tools"]["adb"].update(sha256="0"*64), lambda v:v["image"].update(abi="arm64-v8a"),
                 lambda v:v["capacity"].update(free_ram_bytes=True), lambda v:v["capacity"].update(free_disk_bytes=1),
                 lambda v:v["listeners"].append({"port":5048,"pid":1,"address":"127.0.0.1"}),
                 lambda v:v["restriction_proof"].update(adb_usb_zero=False), lambda v:v["restriction_proof"].update(main_review="true")]
        for edit in edits:
            value=self.snapshot();edit(value)
            with self.assertRaises(a.ValidationError):self.validate(value)

    def test_existing_process_is_recorded_but_never_killed_or_reused_by_name(self):
        value=self.snapshot()
        process={"pid":21264,"parent_pid":18104,"image":a.SDK+"/platform-tools/adb.exe",
                 "command_line":"adb -L tcp:5037 fork-server server --reply-fd 724","created_utc":"2026-10-22T22:08:35-03:00"}
        value["processes"]=[process]
        self.assertFalse(self.validate(value)["android_admitted"])
        process["command_line"]="adb -L tcp:5048 server nodaemon"
        with self.assertRaises(a.ValidationError):self.validate(value)
        process.update(image=a.SDK+"/emulator/emulator.exe",command_line="emulator -avd Stage10")
        with self.assertRaises(a.ValidationError):self.validate(value)

    def test_owned_pid_requires_exact_launch_receipt_not_process_name_or_recycled_pid(self):
        launched={"pid":1234,"parent_pid":4321,"image":a.SDK+"/platform-tools/adb.exe",
                  "argv":a.server_spec(self.config())["argv"],"created_utc":"2026-10-23T19:00:00Z",
                  "binary_sha256":a.ADB_HASH,"session_id":"12000000-0000-4000-8000-000000000001"}
        self.assertEqual(1234,a.validate_owned_process(copy.deepcopy(launched),launched,"server",self.config())["pid"])
        for changes in ({"pid":1235},{"created_utc":"2026-10-23T19:00:01Z"},{"binary_sha256":"0"*64},
                        {"argv":["adb.exe","start-server"]},{"parent_pid":True}):
            with self.assertRaises(a.ValidationError):a.validate_owned_process({**launched,**changes},launched,"server",self.config())

    def test_destination_absence_foreign_owner_and_exact_owned_marker(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/"new"
            with self.assertRaises(a.ValidationError):a.root_ownership(root)
            with patch.object(a,"ROOT",str(root).replace("\\","/")):
                self.assertEqual("ABSENT",a.root_ownership(root)["state"])
                root.mkdir()
                with self.assertRaises(a.ValidationError):a.root_ownership(root)
                session="12000000-0000-4000-8000-000000000001"
                owner={"schema":"stage12-avd-owner-v1","name":a.NAME,"root":a.ROOT,"session_id":session,"contract_sha256":a.CONTRACT}
                (root/".stage12-owner.json").write_text(json.dumps(owner),encoding="utf-8")
                self.assertEqual("OWNED",a.root_ownership(root,session)["state"])
                with self.assertRaises(a.ValidationError):a.root_ownership(root,"12000000-0000-4000-8000-000000000002")

    def test_socket_absent_timeout_partial_and_wrong_version_have_no_launch_or_fallback(self):
        for exception in (ConnectionRefusedError(),TimeoutError()):
            connector=Mock(side_effect=exception)
            with self.assertRaises(a.ValidationError):a.probe_no_autostart(connector,self.config())
            connector.assert_called_once_with(("127.0.0.1",5048),2.0)
        for responses in ([b"FAIL"],[b"OKAY",b"0004",b"0028"],[b"OK",b""]):
            stream=Mock();stream.recv.side_effect=responses
            with self.assertRaises(a.ValidationError):a.probe_no_autostart(Mock(return_value=stream),self.config())
            stream.close.assert_called_once()

    def test_local_input_inspection_checks_metadata_and_existing_components_without_sdk_execution(self):
        with tempfile.TemporaryDirectory() as temp:
            sdk=Path(temp); image=sdk/"system-images/android-36/default/x86_64";image.mkdir(parents=True)
            for rel in ("platform-tools/adb.exe","emulator/emulator.exe","cmdline-tools/22.0/bin/avdmanager.bat"):
                target=sdk/rel;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(b"mock-tool")
            (sdk/"cmdline-tools/22.0/source.properties").write_bytes(b"Pkg.Revision=22.0\r\n")
            (image/"source.properties").write_bytes(b"Pkg.Revision=2\r\nAndroidVersion.ApiLevel=36\r\nSystemImage.Abi=x86_64\r\nSystemImage.TagId=default\r\n")
            (image/"build.prop").write_bytes(b"ro.build.id=BE2A.250530.026.D1\r\n")
            (image/"package.xml").write_bytes(b"synthetic-metadata")
            stub=sdk/"Stage12AdbCliRefused.exe";stub.write_bytes(b"mock-stub")
            for name in ("system.img","vendor.img","ramdisk.img","kernel-ranchu"):(image/name).write_bytes(b"synthetic")
            with patch.object(a,"SDK",str(sdk).replace("\\","/")),patch.object(a,"IMAGE",str(image).replace("\\","/")),\
                 patch.object(a,"ADB_HASH",a.digest(b"mock-tool")),patch.object(a,"EMULATOR_HASH",a.digest(b"mock-tool")),\
                 patch.object(a,"STUB",str(stub).replace("\\","/")),patch.object(a,"STUB_HASH",a.digest(b"mock-stub")):
                result=a.inspect_existing_inputs()
                self.assertFalse(result["full_images_hashed"])
                self.assertEqual(9,result["image_component_bytes"]["system.img"])
                (image/"ramdisk.img").unlink()
                with self.assertRaises(a.ValidationError):a.inspect_existing_inputs()

    def test_reparse_input_and_ancestor_is_rejected_before_opening(self):
        observed=SimpleNamespace(st_mode=0o040755,st_file_attributes=0x400)
        with patch.object(Path,"lstat",return_value=observed),self.assertRaises(a.ValidationError):
            a.no_reparse(Path("C:/only-stage12"))

    def test_exact_socket_frames_only_and_fragmented_valid_success(self):
        stream=Mock();stream.recv.side_effect=[b"OK",b"AY",b"0004",b"00",b"29"]
        connector=Mock(return_value=stream)
        self.assertEqual("DEDICATED_SOCKET_PROTOCOL_41_ONLY_NOT_OWNERSHIP_OR_DEVICE_PROOF",a.probe_no_autostart(connector,self.config()))
        stream.sendall.assert_called_once_with(b"000chost:version")
        stream.close.assert_called_once()
        self.assertIn(b"emulator-5588",a.request_frame("host:transport:emulator-5588",self.config()))
        for service in ("host:devices", "host:kill", "host:transport:physical-device-fixture", "shell:pm clear"):
            with self.assertRaises(a.ValidationError):a.request_frame(service,self.config())

    def test_registration_and_inventory_need_fresh_exact_owned_server_guard(self):
        config=self.config();now=datetime(2026,10,23,19,0,1,tzinfo=timezone.utc)
        launched={"pid":1234,"parent_pid":4321,"image":a.SDK+"/platform-tools/adb.exe",
                  "argv":a.server_spec(config)["argv"],"created_utc":"2026-10-23T19:00:00Z",
                  "binary_sha256":a.ADB_HASH,"session_id":"12000000-0000-4000-8000-000000000001"}
        guard={"observed_utc":"2026-10-23T19:00:00Z","current":copy.deepcopy(launched),"launched":launched,
               "environment":a.server_spec(config)["environment"],"listener":{"address":"127.0.0.1","port":5048,"pid":1234}}
        self.assertEqual(b"0012host:emulator:5589",a.request_frame("host:emulator:5589",config,guard,now))
        self.assertEqual(b"000ehost:devices-l",a.request_frame("host:devices-l",config,guard,now))
        for service in ("host:emulator:5589","host:devices-l","host:emulator:5587","host:emulator:5591"):
            with self.assertRaises(a.ValidationError):a.request_frame(service,config)
        for edit in (lambda g:g["environment"].update(ADB_USB="1"),lambda g:g["listener"].update(pid=9999),
                     lambda g:g.update(observed_utc="2026-10-23T18:59:57Z"),lambda g:g["current"].update(pid=9999)):
            bad=copy.deepcopy(guard);edit(bad)
            with self.assertRaises(a.ValidationError):a.request_frame("host:devices-l",config,bad,now)

    def test_own_inventory_is_empty_before_and_exactly_one_online_serial_after_without_leaking_extras(self):
        self.assertEqual(0,a.parse_owned_inventory(b"",self.config(),"before")["count"])
        self.assertEqual(1,a.parse_owned_inventory(b"emulator-5588\tdevice product:aosp model:s12\n",self.config(),"after")["count"])
        for body,phase in ((b"emulator-5588\tdevice\n","before"),(b"","after"),
                           (b"emulator-5588\toffline\n","after"),(b"UNEXPECTED_PRIVATE_SERIAL\tdevice\n","after"),
                           (b"emulator-5588\tdevice\nUNEXPECTED_PRIVATE_SERIAL\tdevice\n","after")):
            with self.assertRaises(a.ValidationError) as caught:a.parse_owned_inventory(body,self.config(),phase)
            self.assertNotIn("UNEXPECTED_PRIVATE_SERIAL",str(caught.exception))


if __name__=="__main__":
    unittest.main()
