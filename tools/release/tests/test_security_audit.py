import unittest, importlib.util, pathlib
import contextlib, io, json, sys, tempfile, zipfile, subprocess
from unittest import mock
spec=importlib.util.spec_from_file_location("audit",pathlib.Path(__file__).parents[1]/"security_audit.py")
audit=importlib.util.module_from_spec(spec); spec.loader.exec_module(audit)
class RedactedScannerTest(unittest.TestCase):
    def test_detects_but_never_returns_value(self):
        value=("gh"+"p_"+"A"*36).encode()
        result=audit.scan_bytes(value)
        self.assertEqual(result,[{"rule":"github-token","matches":1}])
        self.assertNotIn(value.decode(),str(result))
    def test_public_certificate_is_not_private_key(self):
        self.assertEqual(audit.scan_bytes(b"AB6A80E649D10A63D4524278AAAE62588C29B5FF459A91063DEC62B534C69B0D"),[])
    def test_private_marker_is_flagged_without_context(self):
        self.assertEqual(audit.scan_bytes(b"-----BEGIN "+b"PRIVATE KEY-----")[0]["rule"],"private-key-pem")

    def test_literal_matches_adjacent_text_case_and_repetition(self):
        value="FICTITIOUSDEVICE42"
        data=b"beforefictitiousdevice42after/FICTITIOUSDEVICE42/FiCtItIoUsDeViCe42suffix"
        result=audit.scan_bytes(data,[value])
        self.assertEqual(result,[{"rule":"explicit-identifier","matches":3}])
        self.assertNotIn(value,json.dumps(result).upper())

    def test_regex_characters_are_literal_and_duplicate_inputs_do_not_inflate_count(self):
        value="DEMO.[X]+42"
        data=b"prefixdemo.[x]+42suffix / DEMOaXXX42 / DEMO.[X]+42"
        self.assertEqual(audit.scan_bytes(data,[value,value.lower()]),
                         [{"rule":"explicit-identifier","matches":2}])

    def test_case_folding_is_ascii_only(self):
        self.assertEqual(audit.scan_bytes("é".encode(),["É"]),[])
        self.assertEqual(audit.scan_bytes("Éa".encode(),["ÉA"]),
                         [{"rule":"explicit-identifier","matches":1}])

    def test_explicit_literals_do_not_change_existing_secret_rules(self):
        token=("gh"+"p_"+"A"*36).encode()
        self.assertEqual(audit.scan_bytes(token+b"/fiction42",["FICTION42"]),
                         [{"rule":"github-token","matches":1},
                          {"rule":"explicit-identifier","matches":1}])
        self.assertEqual(audit.scan_bytes(b"fiction42"),[])

    def test_invalid_inputs_raise_redacted_errors(self):
        private_value="FICTITIOUSPRIVATE42"
        values=[[],"",private_value,{},[""],[" \t"],[1],[True],[None],
                [private_value,{}],["\ud800"]]
        for value in values:
            with self.subTest(value_type=type(value).__name__):
                with self.assertRaises(ValueError) as error:
                    audit.scan_bytes(b"fixture",value)
                self.assertNotIn(private_value,str(error.exception))

    def test_private_json_validation_never_exposes_contents(self):
        private_value="FICTITIOUSPRIVATE42"
        with tempfile.TemporaryDirectory() as folder:
            source=pathlib.Path(folder)/"input.json"
            for value in ([],None,{},[""],[private_value,False]):
                source.write_text(json.dumps(value),encoding="utf-8")
                with self.assertRaisesRegex(ValueError,"^Invalid private identifier file$") as error:
                    audit.load_identifiers(source)
                self.assertNotIn(private_value,str(error.exception))
            source.write_text('["'+private_value+'", invalid]',encoding="utf-8")
            with self.assertRaisesRegex(ValueError,"^Invalid private identifier file$"):
                audit.load_identifiers(source)

    def test_cli_connects_private_file_to_tree_history_and_apk_scan(self):
        value="FICTITIOUS.[DEVICE]+42"
        data=("prefix"+value.lower()+"suffix/"+value).encode()
        with tempfile.TemporaryDirectory() as folder:
            root=pathlib.Path(folder)
            tree_name="prefix"+value+"suffix.txt"
            (root/tree_name).write_bytes(data)
            source=root/"private-input.json";source.write_text(json.dumps([value]),encoding="utf-8")
            apk=root/("prefix"+value+"suffix.apk")
            with zipfile.ZipFile(apk,"w") as archive: archive.writestr(tree_name,data)
            output=root/"result.json";oid=b"1"*40
            process=mock.Mock(stdin=io.BytesIO(),stdout=io.BytesIO(oid+b" blob "+str(len(data)).encode()+b"\n"+data+b"\n"))
            process.wait.return_value=0
            arguments=["security_audit.py","--root",str(root),"--apk",str(apk),
                       "--identifiers-file",str(source),"--output",str(output)]
            stdout,stderr=io.StringIO(),io.StringIO()
            with mock.patch.object(sys,"argv",arguments), \
                 mock.patch.object(audit,"git",side_effect=[tree_name.encode()+b"\0",oid+b" fixture.txt\n"]) as git_read, \
                 mock.patch.object(audit.subprocess,"Popen",return_value=process) as batch, \
                 mock.patch.object(audit.subprocess,"check_output",side_effect=AssertionError("External command blocked")), \
                 contextlib.redirect_stdout(stdout),contextlib.redirect_stderr(stderr):
                audit.main()
            self.assertTrue(all(call.kwargs.get("redact_stderr") is True for call in git_read.call_args_list))
            self.assertEqual(batch.call_args.kwargs["stderr"],subprocess.DEVNULL)
            result=json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(result["counts"],{"tree":1,"history_blobs":1,"apk_entries":1})
            self.assertEqual([x["surface"] for x in result["signals"]],["tree","history","apk"])
            self.assertTrue(all(x["rule"]=="explicit-identifier" and x["matches"]==2 for x in result["signals"]))
            self.assertTrue(result["signals"][0]["path"].startswith("sha256:"))
            self.assertTrue(result["signals"][2]["artifact"].startswith("sha256:"))
            self.assertTrue(result["signals"][2]["entry"].startswith("sha256:"))
            emitted=output.read_text(encoding="utf-8")+stdout.getvalue()+stderr.getvalue()
            self.assertNotIn(value.upper(),emitted.upper())

    def test_cli_bad_private_input_is_redacted_and_does_not_scan(self):
        value="FICTITIOUSPRIVATE42"
        with tempfile.TemporaryDirectory() as folder:
            source=pathlib.Path(folder)/"private-input.json"
            source.write_text('["'+value+'", bad]',encoding="utf-8")
            output=pathlib.Path(folder)/"result.json"
            stdout,stderr=io.StringIO(),io.StringIO()
            with mock.patch.object(sys,"argv",["security_audit.py","--identifiers-file",str(source),"--output",str(output)]), \
                 mock.patch.object(audit,"audit",side_effect=AssertionError("Scan must not run")) as scan, \
                 contextlib.redirect_stdout(stdout),contextlib.redirect_stderr(stderr):
                with self.assertRaises(SystemExit) as error: audit.main()
            self.assertEqual(error.exception.code,2)
            scan.assert_not_called()
            self.assertFalse(output.exists())
            self.assertNotIn(value,stdout.getvalue()+stderr.getvalue())

    def test_cli_scan_error_cannot_echo_private_value_from_a_filename(self):
        value="FICTITIOUSPRIVATE42"
        with tempfile.TemporaryDirectory() as folder:
            root=pathlib.Path(folder);source=root/"private-input.json"
            source.write_text(json.dumps([value]),encoding="utf-8")
            output=root/"result.json";stdout,stderr=io.StringIO(),io.StringIO()
            with mock.patch.object(sys,"argv",["security_audit.py","--identifiers-file",str(source),"--output",str(output)]), \
                 mock.patch.object(audit,"audit",side_effect=OSError("missing-"+value+".apk")), \
                 contextlib.redirect_stdout(stdout),contextlib.redirect_stderr(stderr):
                with self.assertRaises(SystemExit) as error: audit.main()
            self.assertEqual(error.exception.code,2)
            self.assertFalse(output.exists())
            self.assertIn("Audit failed with private identifier input",stderr.getvalue())
            self.assertNotIn(value,stdout.getvalue()+stderr.getvalue())

    def test_real_cli_git_error_does_not_echo_literal_in_missing_root(self):
        value="FICTITIOUSDEVICE42"
        with tempfile.TemporaryDirectory() as folder:
            root=pathlib.Path(folder);source=root/"private-input.json"
            source.write_text(json.dumps([value]),encoding="utf-8")
            missing=root/("missing-"+value+"-repository");output=root/"result.json"
            result=subprocess.run([sys.executable,"-E","-B",str(pathlib.Path(audit.__file__)),
                                   "--root",str(missing),"--identifiers-file",str(source),
                                   "--output",str(output)],capture_output=True,timeout=15,check=False)
            self.assertEqual(result.returncode,2)
            self.assertFalse(output.exists())
            self.assertNotIn(value.encode().lower(),result.stdout.lower()+result.stderr.lower())

    def test_default_git_stderr_is_inherited(self):
        with mock.patch.object(audit.subprocess,"check_output",return_value=b"fixture") as command:
            self.assertEqual(audit.git(pathlib.Path("fixture-root"),"ls-files"),b"fixture")
        self.assertIsNone(command.call_args.kwargs["stderr"])
        process=mock.Mock(stdin=io.BytesIO(),stdout=io.BytesIO())
        with mock.patch.object(audit,"git",side_effect=[b"",b""]), \
             mock.patch.object(audit.subprocess,"Popen",return_value=process) as batch:
            result=audit.audit(pathlib.Path("fixture-root"),[])
        self.assertIsNone(batch.call_args.kwargs["stderr"])
        self.assertEqual(result["signals"],[])
