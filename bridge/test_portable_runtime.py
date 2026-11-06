"""Check the committed distribution, independently of local extraction artifacts."""
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import uuid
import zipfile


ROOT = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / 'runtime/storm-death'


class PortableRuntimeTests(unittest.TestCase):
    @unittest.skipUnless(os.name == 'nt' and shutil.which('powershell'), 'Requires Windows PowerShell')
    def test_launcher_runs_from_another_directory_without_extraction_inputs(self):
        candidates = [shutil.which('java'), str(Path(os.environ.get('JAVA_HOME', '')) / 'bin/java.exe'),
                      r'C:\Program Files\Java\jdk-22\bin\java.exe']
        java = None
        for candidate in candidates:
            if candidate and Path(candidate).is_file():
                version = subprocess.run([candidate, '-version'], capture_output=True, text=True)
                match = re.search(r'version\s+"(\d+)', version.stdout + version.stderr)
                if version.returncode == 0 and match and int(match[1]) >= 22:
                    java = str(Path(candidate).resolve())
                    break
        if not java:
            self.skipTest('Requires Java 22+')
        request = json.loads((ROOT / 'examples/death-storm-request.json').read_text(encoding='utf-8'))
        request['search'].update(populationSize=20, generations=1)
        request['topK'] = 1
        output = Path('target') / ('portable-test-' + uuid.uuid4().hex + '.json')
        leaked_output = ROOT / output
        with tempfile.TemporaryDirectory(prefix='geargenetics-checkout-') as scratch:
            checkout = Path(scratch)
            shutil.copytree(BUNDLE, checkout / 'runtime/storm-death')
            shutil.copyfile(ROOT / 'Run-StormDeathPortable.ps1', checkout / 'Run-StormDeathPortable.ps1')
            (checkout / 'examples').mkdir()
            (checkout / 'examples/death-storm-request.json').write_text(json.dumps(request), encoding='utf-8')
            env = dict(os.environ, JAVA_HOME=str(Path(java).parent.parent))
            env['PATH'] = os.pathsep.join([str(Path(os.environ['SystemRoot']) / 'System32'),
                                          os.environ['SystemRoot'], str(Path(java).parent)])
            try:
                run = subprocess.run([shutil.which('powershell'), '-NoProfile', '-ExecutionPolicy', 'Bypass',
                                      '-File', str(checkout / 'Run-StormDeathPortable.ps1'),
                                      '-Output', str(output), '-JavaPath', java],
                                     cwd=ROOT, env=env, capture_output=True, text=True, timeout=60)
                self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
                self.assertTrue((checkout / output).is_file(), run.stdout + run.stderr)
                self.assertFalse(leaked_output.exists(), 'Result path resolved against the caller directory')
                result = json.loads((checkout / output).read_text(encoding='utf-8'))
                self.assertEqual(len(result['history']), 2)
            finally:
                if leaked_output.exists():
                    leaked_output.unlink()

    def test_published_files_have_matching_hashes_and_self_contained_executable(self):
        manifest = json.loads((BUNDLE / 'manifest.json').read_text(encoding='utf-8'))
        for name in ('optimizer.jar', 'catalog.json.gz'):
            raw = (BUNDLE / name).read_bytes()
            self.assertEqual(len(raw), manifest['files'][name]['bytes'], name)
            self.assertEqual(hashlib.sha256(raw).hexdigest(), manifest['files'][name]['sha256'], name)
        with zipfile.ZipFile(BUNDLE / 'optimizer.jar') as jar:
            for name in ('me/opd/Main.class', 'com/fasterxml/jackson/databind/ObjectMapper.class',
                         'org/sqlite/JDBC.class', 'me/opd/scaling/retail-20261031.json'):
                self.assertIn(name, jar.namelist())
            self.assertIn(b'Main-Class: me.opd.Main', jar.read('META-INF/MANIFEST.MF'))

    def test_catalog_covers_default_request_and_keeps_matching_scaling_and_pet_data(self):
        manifest = json.loads((BUNDLE / 'manifest.json').read_text(encoding='utf-8'))
        raw = gzip.decompress((BUNDLE / 'catalog.json.gz').read_bytes())
        self.assertEqual(hashlib.sha256(raw).hexdigest(), manifest['files']['catalog.json']['sha256'])
        self.assertEqual(len(raw), manifest['files']['catalog.json']['bytes'])
        catalog = json.loads(raw)
        request = json.loads((ROOT / 'examples/death-storm-request.json').read_text(encoding='utf-8'))
        self.assertEqual(request['wizard']['school'], manifest['coverage']['school'])
        self.assertLessEqual(request['wizard']['level'], manifest['coverage']['maximumLevel'])
        self.assertTrue(set(request['requiredSlots']).issubset(manifest['coverage']['slots']))
        for section in ('gear', 'jewels', 'petTalents'):
            self.assertEqual(len(catalog[section]), manifest['counts'][section])
        self.assertEqual(catalog['extensions']['gearPool'], 'BROAD')
        self.assertTrue(catalog['extensions']['includePins'])
        self.assertTrue(set(request['requiredSlots']).issubset({g['slot'] for g in catalog['gear']}))
        self.assertTrue(any(g['name'] == "Howling Werewolf's Collar" for g in catalog['gear']))
        self.assertTrue(any(j['type'] == 'SWORD_PIN' for j in catalog['jewels']))
        self.assertTrue(any(j['type'] == 'PET' for j in catalog['jewels']))
        talents = {t['name'] for t in catalog['petTalents']}
        pets = [g for g in catalog['gear'] if g['slot'] == 'PET']
        self.assertEqual(len(pets), manifest['counts']['petProfiles'])
        pet_search = request.get('petSearch', {})
        if pet_search.get('mode') == 'AUTO':
            self.assertTrue(set(pet_search['talentPool']).issubset(talents))
            self.assertTrue(any(g['name'] == pet_search['template'] for g in pets))
        self.assertTrue(all(not item['complete'] for item in catalog['gear'] + catalog['jewels']))
        with zipfile.ZipFile(BUNDLE / 'optimizer.jar') as jar:
            scaling = json.loads(jar.read('me/opd/scaling/retail-20261031.json'))
        self.assertEqual(scaling['source']['executableSha256'], manifest['source']['executableSha256'])
        self.assertEqual(scaling['source']['rootSha256'], manifest['source']['rootSha256'])
        expected_version = 'exe-sha256:' + manifest['source']['executableSha256']
        self.assertTrue(all(item['gameVersion'] == expected_version for item in catalog['gear'] + catalog['jewels']))


if __name__ == '__main__':
    unittest.main()
