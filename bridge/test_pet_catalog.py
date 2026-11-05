import copy
import json
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

from build_pet_catalog import build, load_definitions, pet_configuration, talent_definition
from build_wad_catalog import project
from prepare_mock_catalog import prepare
from test_wad_catalog import CAPTURE, EFFECTS, TABLES, row


def resource(member, data):
    return {'archive': 'Root', 'member': member, 'sha256': 'synthetic', 'decoded': data}


def effect():
    return {'$type': 'Types.PetBoostPlayerStatEffectTemplate, Assembly',
            'm_effectName': 'PetTalentDeathDamage03', 'm_primaryStat1': 'Strength',
            'm_primaryStat2': 'Will', 'm_secondaryStat': 'Power', 'm_secondaryModifier': 0.5,
            'm_buffAll': False, 'm_school': 'Death', 'm_damageBonusPercent': 0.00015}


def talent():
    return resource('TalentData/dealer.xml', {'$type': 'Types.PetTalentTemplate, Assembly',
        'm_talentName': 'dealer', 'm_displayName': 'PetTalents_001', 'm_maxStatList': [],
        'm_effectList': [{'$type': 'Types.StatisticEffectInfo, Assembly',
                          'm_lookupIndex': -1, 'm_effectName': 'PetTalentDeathDamage03'}]})


def body():
    return {'id': '42', 'member': 'ObjectData/Pets/test.xml', 'decoded': {
        'm_adjectiveList': ['Pet'], 'm_behaviors': [{'m_behaviorName': 'PetItemBehavior',
          'm_Levels': [{'m_level': 7, 'm_powerCardName': 'Test Card'}]}]}}


def profile():
    return {'id': 'plan:pet:test', 'name': 'Synthetic planning pet', 'bodyTemplateId': '42',
            'hypothetical': True, 'age': 7, 'fullyTrained': True, 'rounding': 'RAW',
            'talentIds': ['dealer'],
            'attributes': {'STRENGTH': 255, 'INTELLECT': 250, 'AGILITY': 260, 'WILL': 260, 'POWER': 250}}


class PetCatalogTests(unittest.TestCase):
    def test_talent_definition_keeps_school_coefficients_and_raw_attribute_roles(self):
        key, trait, issues = talent_definition(talent(), {'PetTalentDeathDamage03': effect()}, {'pettalents_001': 'Death-Dealer'})
        self.assertEqual((key, issues), ('dealer', []))
        self.assertEqual(trait['effects'][0]['coefficients']['values'], {'DAMAGE:DEATH': 0.015})
        self.assertEqual(trait['effects'][0]['primaryStat1'], 'STRENGTH')
        all_effect = effect(); all_effect['m_buffAll'] = True
        _, all_trait, _ = talent_definition(talent(), {'PetTalentDeathDamage03': all_effect}, {'pettalents_001': 'Universal'})
        self.assertEqual(all_trait['effects'][0]['coefficients']['values'], {'DAMAGE:UNIVERSAL': 0.015})

    def test_unsupported_retired_or_conflicting_definitions_are_not_usable(self):
        names = resource('Locale/en-US/PetTalents.lang', '1:PetTalents\n001\n\nDealer\n')
        effects = resource('GameEffectData/PetTalentEffects.xml', {'m_effectTemplates': [effect()]})
        bad = talent(); bad['decoded']['m_bRetired'] = True
        supported, raw, excluded, *_ = load_definitions([names, effects, bad])
        self.assertFalse(supported); self.assertIn('retired-talent', excluded['dealer'])
        self.assertEqual(len(raw), 1)
        conflict = talent(); conflict['member'] = 'TalentData/other.xml'; conflict['decoded']['m_maxStatList'] = [{'m_name': 'Strength', 'm_value': 65}]
        supported, _, excluded, *_ = load_definitions([names, effects, talent(), conflict])
        self.assertFalse(supported); self.assertIn('conflicting-talent-definitions', excluded['dealer'])
        bad_effect = effect(); bad_effect['m_fishingLuckBonusPercent'] = 1
        _, _, issues = talent_definition(talent(), {'PetTalentDeathDamage03': bad_effect}, {'pettalents_001': 'Dealer'})
        self.assertIn('unsupported-pet-stat', issues)

    def test_pet_profiles_reject_illegal_talents_ages_and_attribute_assumptions(self):
        _, trait, _ = talent_definition(talent(), {'PetTalentDeathDamage03': effect()}, {'pettalents_001': 'Dealer'})
        cfg = pet_configuration(profile(), {'dealer': trait}, body(), CAPTURE)
        self.assertTrue(cfg['pet']['hypothetical']); self.assertFalse(cfg['complete'])
        self.assertEqual(cfg['itemCards'], ['w101:spell-name:Test Card'])
        for change in [{'talentIds': ['dealer', 'dealer']}, {'talentIds': ['unknown']},
                       {'age': 1}, {'hypothetical': None}, {'rounding': 'guess'},
                       {'maximumAttributes': {'STRENGTH': 255}}]:
            bad = dict(profile(), **change)
            with self.assertRaises(ValueError):
                pet_configuration(bad, {'dealer': trait}, body(), CAPTURE)

    def test_pin_projection_keeps_focus_separate_from_bonus_school_and_holder_slots(self):
        source = row(); source['decoded']['m_adjectiveList'] = ['Jewel', 'PinSquareSword']
        source['decoded']['m_equipRequirements'] = {'$type': 'Types.ReqSchoolOfFocus', 'm_magicSchool': 'Death'}
        item, issues = project(source, {'items_001': 'Synthetic pin'}, EFFECTS, TABLES, CAPTURE)
        self.assertEqual(issues, [])
        self.assertEqual(item[1]['schools'], ['DEATH'])
        self.assertEqual(item[1]['gearSlots'], ['HAT', 'ROBE', 'BOOTS'])
        self.assertIn('FIRE', item[1]['bonusSchools'])
        self.assertFalse(item[1]['complete'])

    def test_resource_to_pet_and_star_jewel_pipeline_and_revision_guard(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); capture_dir = root / 'capture'; capture_dir.mkdir()
            capture = dict(CAPTURE, archives={'Root': {'sha256': 'same-root'}})
            (capture_dir / 'capture.json').write_text(json.dumps(capture))
            resources = [talent(), resource('GameEffectData/PetTalentEffects.xml', {'m_effectTemplates': [effect()]}),
                         resource('Locale/en-US/PetTalents.lang', '1:PetTalents\n001\n\nDealer\n'),
                         resource('Locale/en-US/Items.lang', '1:Items\n001\n\nTest Jewel\n')]
            (capture_dir / 'resources.ndjson').write_text('\n'.join(json.dumps(r) for r in resources))
            profiles = root / 'profiles.json'; profiles.write_text(json.dumps({'schemaVersion': 1, 'pets': [profile()]}))
            jewel = row(); jewel['decoded'].update(m_adjectiveList=['Jewel', 'PetJewel'], m_equipEffects=[],
                m_behaviors=[{'m_behaviorName': 'PetJewelBehavior', 'm_minPetLevel': 7, 'm_petTalentName': ['dealer']}])
            jewel['member'] = 'ObjectData/Jewels/Jewel/test.xml'
            staging = root / 'staging.sqlite'
            with closing(sqlite3.connect(staging)) as db, db:
                db.execute('CREATE TABLE raw_capture(id INTEGER, json TEXT)')
                db.execute('CREATE TABLE raw_templates(template_id TEXT, member TEXT, json TEXT)')
                db.execute('INSERT INTO raw_capture VALUES(1,?)', (json.dumps(capture),))
                db.executemany('INSERT INTO raw_templates VALUES(?,?,?)', [(r['id'], r['member'], json.dumps(r)) for r in [body(), jewel]])
            output = root / 'pets.json'
            catalog = build(staging, capture_dir, profiles, output)
            self.assertEqual(len(catalog['gear']), 1); self.assertEqual(len(catalog['jewels']), 1)
            self.assertEqual([t['id'] for t in catalog['petTalents']], ['w101:talent:dealer'])
            star = catalog['jewels'][0]
            self.assertEqual(star['minimumPetAge'], 7)
            self.assertEqual(star['grantedTalentIds'], ['w101:talent:dealer'])
            self.assertEqual(star['petTalents'][0]['effects'][0]['coefficients']['values'], {'DAMAGE:DEATH': 0.015})
            self.assertTrue(output.with_suffix('.talents.json').exists())
            with self.assertRaisesRegex(ValueError, 'new pet-catalog'):
                build(staging, capture_dir, profiles, output)
            capture['archives']['Root']['sha256'] = 'different-root'
            (capture_dir / 'capture.json').write_text(json.dumps(capture))
            with self.assertRaisesRegex(ValueError, 'different client/archive'):
                build(staging, capture_dir, profiles, root / 'other.json')

    def test_pool_keeps_pins_opt_in_and_rejects_other_pin_issues_and_pet_revision_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / 'optimizer.sqlite'
            gear = {'id': 'hat', 'name': 'Hat', 'slot': 'HAT', 'level': 170, 'schools': [],
                    'category': 'gear', 'source': 'wizard101-wad', 'gameVersion': 'exe-sha256:test', 'extensions': {'issues': []}}
            pin = {'id': 'pin', 'name': 'Pin', 'level': 170, 'type': 'SWORD_PIN', 'schools': ['DEATH'],
                   'category': 'jewel', 'extensions': {'issues': ['pin-holder-restrictions-unverified']}}
            bad = copy.deepcopy(pin); bad['id'] = 'bad'; bad['extensions']['issues'].append('unknown-requirement')
            with closing(sqlite3.connect(db_path)) as db, db:
                db.execute('CREATE TABLE items(kind TEXT, level INTEGER, payload TEXT)')
                db.executemany('INSERT INTO items VALUES(?,?,?)', [(kind, 170, json.dumps(x)) for kind, x in [('gear', gear), ('jewel', pin), ('jewel', bad)]])
            request = {'wizard': {'school': 'DEATH', 'level': 170}, 'requiredSlots': ['HAT']}
            self.assertFalse(prepare(db_path, request)['jewels'])
            expanded = prepare(db_path, request, include_pins=True)
            self.assertEqual([j['id'] for j in expanded['jewels']], ['pin'])
            self.assertEqual(expanded['jewels'][0]['gearSlots'], ['HAT', 'ROBE', 'BOOTS'])
            pets = {'schemaVersion': 1, 'gear': [], 'jewels': [], 'extensions': {'capture': {'executableSha256': 'different'}}}
            with self.assertRaisesRegex(ValueError, 'different client'):
                prepare(db_path, request, pets, True)

    def test_prepared_pool_retains_standalone_talents_and_accepts_older_pet_catalogs(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / 'optimizer.sqlite'
            with closing(sqlite3.connect(db_path)) as db, db:
                db.execute('CREATE TABLE items(kind TEXT, level INTEGER, payload TEXT)')
            request = {'wizard': {'school': 'STORM', 'level': 180}, 'requiredSlots': ['PET']}
            pets = {'schemaVersion': 1, 'gear': [{'id': 'pet', 'slot': 'PET'}], 'jewels': [],
                    'petTalents': [{'id': 'talent', 'name': 'Example'}]}
            self.assertEqual(prepare(db_path, request, pets)['petTalents'], pets['petTalents'])
            del pets['petTalents']
            self.assertEqual(prepare(db_path, request, pets)['petTalents'], [])


if __name__ == '__main__':
    unittest.main()
