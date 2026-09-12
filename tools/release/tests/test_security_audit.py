import unittest, importlib.util, pathlib
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
