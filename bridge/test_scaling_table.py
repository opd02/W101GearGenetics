import copy
import json
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path
from build_scaling_table import normalize, SCHOOL_FIELDS, GLOBAL_FIELDS, SCHOOLS
from prepare_mock_catalog import prepare


class ScalingTableTests(unittest.TestCase):
    def fixture(self):
        row = {'m_school': 'Storm', 'm_level': 180}
        row.update({'m_canonical' + school.title() + field: 0 for school in SCHOOLS for field in SCHOOL_FIELDS})
        row.update({'m_canonical' + field: 0 for field in GLOBAL_FIELDS})
        row.update(m_canonicalDeathDamage=3.71, m_canonicalStormDamage=2.2, m_canonicalMaxHealth=11975,
                   m_canonicalDeathArmorPiercing=0.6, m_canonicalStormCriticalHit=800)
        floors = copy.deepcopy(row); floors['m_canonicalStormCriticalHit'] = 0.1
        provenance = {'source': {key: {'sha256': 'test-' + key} for key in ('root', 'executable', 'member')}}
        return {'m_levelScaledInfoList': [row], 'm_statsFloorPercent': floors}, provenance

    def test_school_scopes_percentage_units_ratings_and_floor_metadata_remain_distinct(self):
        decoded, provenance = self.fixture(); table = normalize(decoded, provenance)
        caps = table['rows'][0]['caps']
        self.assertEqual(caps['DAMAGE:DEATH'], 371)
        self.assertEqual(caps['DAMAGE:STORM'], 220)
        self.assertEqual(caps['PIERCE:DEATH'], 60)
        self.assertEqual(caps['HEALTH:UNIVERSAL'], 11975)
        self.assertEqual(caps['CRITICAL_RATING:STORM'], 800)
        self.assertEqual(table['floorPercent']['CRITICAL_RATING:STORM'], 0.1)

    def test_duplicate_invalid_and_missing_caps_are_rejected(self):
        decoded, provenance = self.fixture(); decoded['m_levelScaledInfoList'] *= 2
        with self.assertRaisesRegex(ValueError, 'Duplicate'): normalize(decoded, provenance)
        decoded, provenance = self.fixture(); decoded['m_levelScaledInfoList'][0]['m_canonicalDeathDamage'] = float('nan')
        with self.assertRaisesRegex(ValueError, 'Invalid cap'): normalize(decoded, provenance)
        decoded, provenance = self.fixture(); decoded['m_levelScaledInfoList'][0]['m_school'] = 'unknown'
        with self.assertRaisesRegex(ValueError, 'school'): normalize(decoded, provenance)

    def test_scaled_pool_still_selects_equipment_and_jewels_at_character_level(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'items.sqlite'
            gear = {'id': 'hat180', 'name': 'Hat', 'slot': 'HAT', 'level': 180, 'schools': ['STORM'],
                    'source': 'manual', 'category': 'gear', 'extensions': {'issues': []}}
            high = dict(gear, id='hat181', level=181)
            jewel = {'id': 'jewel180', 'name': 'Health jewel', 'level': 180, 'schools': [], 'type': 'TEAR',
                     'category': 'jewel', 'extensions': {'issues': [], 'member': 'ObjectData/Jewels/Jewel/Health.xml'}}
            with closing(sqlite3.connect(path)) as db, db:
                db.execute('CREATE TABLE items(kind TEXT,level INTEGER,payload TEXT)')
                db.executemany('INSERT INTO items VALUES(?,?,?)',
                               [(kind, x['level'], json.dumps(x)) for kind, x in [('gear', gear), ('gear', high), ('jewel', jewel)]])
            request = {'wizard': {'school': 'STORM', 'level': 180, 'scaledLevel': 170}, 'requiredSlots': ['HAT']}
            catalog = prepare(path, request)
            self.assertEqual([g['id'] for g in catalog['gear']], ['hat180'])
            self.assertEqual([j['id'] for j in catalog['jewels']], ['jewel180'])
            self.assertEqual(catalog['extensions']['scaledLevel'], 170)


if __name__ == '__main__':
    unittest.main()
