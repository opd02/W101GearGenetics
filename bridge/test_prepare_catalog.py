import copy
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

from prepare_mock_catalog import prepare


class PrepareCatalogTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.database = Path(self.temporary.name) / 'items.sqlite'
        self.request = {'wizard': {'school': 'STORM', 'level': 180, 'scaledLevel': 170},
                        'requiredSlots': ['AMULET']}
        self.collar = {'id': 'collar', 'name': "Howling Werewolf's Collar", 'slot': 'AMULET',
                       'level': 100, 'schools': ['STORM'], 'category': 'availability-unverified',
                       'complete': False, 'baseStats': {'values': {'DAMAGE:DEATH': 4, 'ACCURACY:DEATH': 2}},
                       'extensions': {'issues': ['unsupported-stat-table:Mastery_Death']}}
        with closing(sqlite3.connect(self.database)) as db, db:
            db.execute('CREATE TABLE items(kind TEXT, level INTEGER, payload TEXT)')

    def insert(self, kind, item):
        with closing(sqlite3.connect(self.database)) as db, db:
            db.execute('INSERT INTO items VALUES(?,?,?)', (kind, item['level'], json.dumps(item)))

    def test_broad_pool_retains_collar_stats_issues_and_incomplete_status(self):
        self.insert('gear', self.collar)
        ordinary = copy.deepcopy(self.collar)
        ordinary.update(id='ordinary', name='Ordinary Amulet', extensions={'issues': []})
        self.insert('gear', ordinary)
        self.assertEqual([g['id'] for g in prepare(self.database, self.request)['gear']], ['ordinary'])
        broad = prepare(self.database, self.request, include_unresolved_gear=True)
        self.assertEqual(broad['gear'][0], self.collar)
        self.assertEqual(broad['extensions']['gearPool'], 'BROAD')
        self.assertEqual(len(broad['gear']), 2)

    def test_broad_pool_preserves_level_school_slot_and_test_filters(self):
        self.insert('gear', self.collar)
        for changes in ({'level': 181}, {'schools': ['DEATH']}, {'slot': 'HAT'}, {'slot': 'PET'},
                        {'category': 'qa-or-test'}, {'name': 'Debug Amulet'},
                        {'extensions': {'member': 'ObjectData/test/Amulet.xml'}}):
            item = copy.deepcopy(self.collar)
            item.update(changes)
            self.insert('gear', item)
        broad = prepare(self.database, self.request, include_unresolved_gear=True)
        self.assertEqual(broad['gear'], [self.collar])

    def test_broad_gear_does_not_relax_jewel_or_pin_filters(self):
        self.insert('gear', self.collar)
        jewel = {'id': 'unresolved-jewel', 'name': 'Jewel', 'level': 100, 'schools': [],
                 'category': 'jewel', 'type': 'SQUARE',
                 'extensions': {'issues': ['unsupported-stat-table:Unknown'],
                                'member': 'ObjectData/Jewels/Jewel/Example.xml'}}
        self.insert('jewel', jewel)
        pin = copy.deepcopy(jewel)
        pin.update(id='unresolved-pin', type='SWORD_PIN')
        self.insert('jewel', pin)
        broad = prepare(self.database, self.request, include_pins=True, include_unresolved_gear=True)
        self.assertEqual(broad['jewels'], [])


if __name__ == '__main__':
    unittest.main()
