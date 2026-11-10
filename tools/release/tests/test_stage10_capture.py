import json
from pathlib import Path
import sys
import sqlite3
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT/'tools/release'))
import stage10_capture as capture
from test_upgrade_v6_v8 import create_source, materialize_target


class Stage10CaptureTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='stage10-unit-')
        self.root = Path(self.temporary.name)
        self.addCleanup(self.temporary.cleanup)
        self.counter = 0
        self.source = self.root/'source.db'
        self.target = self.root/'target.db'
        self.preferences = self.root/'preferences.json'
        create_source(self.source, self.root/'helper-private', self.preferences)
        self.png = (ROOT/'app/src/androidTest/assets/stage10/synthetic.png').read_bytes()
        with closing(sqlite3.connect(self.source)) as db, db:
            db.execute('UPDATE schedule_photos SET storageKey=?,byteSize=?,pixelWidth=800,pixelHeight=480,mimeType=?', (capture.PHOTO,len(self.png),'image/png'))
        materialize_target(self.source,self.target)

    def bundle(self, version=6, mutate=None, extra=None):
        self.counter += 1
        contents = {name: ('UNIT_TEST_OPAQUE_RAW:'+name).encode() for name in capture.SELECTED}
        contents['database.db'] = (self.source if version==6 else self.target).read_bytes()
        contents['private/files/schedule_photos/'+capture.PHOTO] = self.png
        preferences = json.loads(self.preferences.read_text())
        for item in preferences:
            if item['key'] in {'notifications.enabled','notifications.precise_timing','weather.enabled','weather.include_notifications'}:
                item['values'] = ['false']
        contents['portable-preferences.json'] = json.dumps(preferences).encode()
        receipt = {'format':'stage10-android-capture/1','operation':'capture-v6' if version==6 else 'capture-v8',
            'room_version':version,'fixture_day':'2026-10-21','target_package':capture.QA,'test_package':capture.QA+'.test',
            'target_apk_sha256':capture.APKS[version],'runner_apk_sha256':'a'*64,'certificate_sha256':capture.CERT,
            'source_fixture_sha256':capture.SOURCE,'capture_id':'412b9a42-a80e-437d-b3bf-0f9a50721aa1',
            'neutral_application':True,'database_closed_before_copy':True,'wal_checkpoint':[0,0,0],
            'single_target_process_verified':True,'export_owner':'TARGET_QA_CACHE',
            'portable_source':'TARGET_GATEWAY_AND_EXPLICIT_PERSISTED_KEYS',
            'synthetic_unit_only':True}
        if mutate: mutate(contents, receipt)
        receipt['files'] = {name:{'sha256':capture.sha(data),'bytes':len(data)} for name,data in contents.items()}
        target = self.root/f'capture-{self.counter}.zip'
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as archive:
            for name,data in contents.items(): archive.writestr(name,data)
            archive.writestr('receipt.json',json.dumps(receipt))
            if extra: archive.writestr(*extra)
        return target

    def ingest(self, bundle, name):
        return capture.ingest(bundle,self.root/name)

    def test_exact_raw_and_logical_pair_passes_unchanged_comparator(self):
        self.ingest(self.bundle(6),'six');self.ingest(self.bundle(8),'eight')
        snapshots=[json.loads((self.root/n/'snapshot.json').read_text()) for n in ('six','eight')]
        self.assertEqual(capture.comparison.compare(*snapshots)['verdict'],'V6_TO_V8_LOGICAL_CONTENT_PRESERVED')
        self.assertIn('not attested',json.loads((self.root/'eight/capture-receipt.json').read_text())['limit'])

    def test_invalid_raw_preference_cannot_hide_behind_same_logical_false(self):
        self.ingest(self.bundle(6),'six')
        self.ingest(self.bundle(8),'baseline-eight')
        original=json.loads((self.root/'six/snapshot.json').read_text())
        baseline=json.loads((self.root/'baseline-eight/snapshot.json').read_text())
        self.assertEqual(capture.comparison.compare(original,baseline)['verdict'],'V6_TO_V8_LOGICAL_CONTENT_PRESERVED')
        path='private/files/datastore/notification_preferences.preferences_pb.preferences_pb'
        def change(contents,receipt): contents[path]=b'INVALID_RAW_THAT_A_GATEWAY_MIGHT_NORMALIZE_TO_FALSE'
        self.ingest(self.bundle(8,change),'eight')
        snapshots=[json.loads((self.root/n/'snapshot.json').read_text()) for n in ('six','eight')]
        self.assertEqual(snapshots[0]['portable_preferences'],snapshots[1]['portable_preferences'])
        with self.assertRaisesRegex(ValueError,'selected file'):
            capture.comparison.compare(*snapshots)
        self.assertEqual((self.root/'eight'/path).read_bytes(),b'INVALID_RAW_THAT_A_GATEWAY_MIGHT_NORMALIZE_TO_FALSE')

    def test_missing_portable_preference_rejects_without_output(self):
        def change(contents,receipt): contents['portable-preferences.json']=json.dumps(json.loads(contents['portable-preferences.json'])[:-1]).encode()
        with self.assertRaises(ValueError):self.ingest(self.bundle(mutate=change),'reject')
        self.assertFalse((self.root/'reject').exists())

    def test_public_or_wrong_certificate_or_artifact_is_not_qa(self):
        for field,value in [('target_package','com.blackatsystems.miguardia'),('certificate_sha256','b'*64),('target_apk_sha256',capture.APKS[8])]:
            with self.subTest(field=field):
                with self.assertRaises(ValueError):self.ingest(self.bundle(mutate=lambda c,r:r.update({field:value})),'reject')
                self.assertFalse((self.root/'reject').exists())

    def test_busy_wal_and_non_closed_database_are_rejected(self):
        for field,value in [('wal_checkpoint',[1,2,3]),('database_closed_before_copy',False),('neutral_application',False),('single_target_process_verified',False)]:
            with self.subTest(field=field):
                with self.assertRaises(ValueError):self.ingest(self.bundle(mutate=lambda c,r:r.update({field:value})),'reject')

    def test_traversal_and_extra_members_never_escape_or_publish(self):
        for name in ('../escaped','extra.txt','/absolute'):
            with self.assertRaises(ValueError):self.ingest(self.bundle(extra=(name,b'forbidden')),'reject')
        self.assertFalse((self.root/'reject').exists())
        self.assertFalse((self.root.parent/'escaped').exists())

    def test_existing_destination_is_preserved(self):
        target=self.root/'existing';target.mkdir();(target/'keep').write_text('preserve')
        with self.assertRaises(ValueError):self.ingest(self.bundle(),'existing')
        self.assertEqual((target/'keep').read_text(),'preserve')

    def test_expiry_capture_requires_explicit_criterion_and_observed_time(self):
        def allowed(contents, receipt):
            receipt.update(operation=capture.EXPIRY_OPERATION, criterion=capture.EXPIRY_CRITERION, captured_at_epoch_millis=200)
        self.ingest(self.bundle(8, allowed), 'expiry-eight')
        for values in ({'criterion': 'unknown'}, {'captured_at_epoch_millis': None}):
            def invalid(contents, receipt):
                allowed(contents, receipt)
                receipt.update(values)
            with self.assertRaises(ValueError):
                self.ingest(self.bundle(8, invalid), 'reject')


if __name__ == '__main__':unittest.main()
from contextlib import closing
