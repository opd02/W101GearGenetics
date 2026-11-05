"""Build explicit pet configurations and resolved star-jewel definitions from offline resources.

No breeding/ownership claims are inferred. Archive-derived calculations remain provisional.
"""
import argparse
import copy
import json
import math
import sqlite3
from collections import Counter
from contextlib import closing
from pathlib import Path

from build_wad_catalog import SCHOOLS, json_text, localization, project, records, typename

ATTRIBUTES = {'STRENGTH', 'INTELLECT', 'AGILITY', 'WILL', 'POWER'}
# coefficient field -> optimizer stat, conversion to output units, global scope
EFFECT_STATS = {
    'm_damageBonusPercent': ('DAMAGE', 100, False),
    'm_damageBonusFlat': ('FLAT_DAMAGE', 1, False),
    'm_accuracyBonusPercent': ('ACCURACY', 100, False),
    'm_armorPiercingBonusPercent': ('PIERCE', 100, False),
    'm_damageReducePercent': ('RESISTANCE', 100, False),
    'm_damageReduceFlat': ('FLAT_RESISTANCE', 1, False),
    'm_hitPointBonus': ('HEALTH', 1, True),
    'm_manaBonus': ('MANA', 1, True),
    'm_powerPipBonusPercent': ('POWER_PIP_CHANCE', 100, True),
    'm_energyBonus': ('ENERGY', 1, True),
    'm_criticalHitRating': ('CRITICAL_RATING', 1, False),
    'm_blockRating': ('BLOCK_RATING', 1, False),
    'm_healBonusPercent': ('OUTGOING_HEALING', 100, True),
    'm_healIncBonusPercent': ('INCOMING_HEALING', 100, True),
    'm_stunResistancePercent': ('STUN_RESISTANCE', 100, True),
}
UNSUPPORTED_STATS = {
    'm_accuracyReducePercent', 'm_petActChance', 'm_manaReducePercent', 'm_expPercent',
    'm_goldPercent', 'm_accuracyRating', 'm_powerPipRating', 'm_damageResistanceRating',
    'm_archmastery', 'm_archmasteryBonusPercent', 'm_balanceMastery', 'm_deathMastery',
    'm_fireMastery', 'm_iceMastery', 'm_lifeMastery', 'm_mythMastery', 'm_stormMastery',
    'm_fishingLuckBonusPercent', 'm_shadowPipBonusPercent', 'm_wispBonusPercent',
    'm_pipConversionRating', 'm_shadowPipRating',
}


def finite(value):
    return type(value) in (int, float) and math.isfinite(value)


def talent_definition(row, effects, names):
    data = row['decoded']
    name = data.get('m_talentName')
    display = names.get(data.get('m_displayName', '').casefold())
    issues = []
    if not name or not display:
        issues.append('missing-talent-name')
    if data.get('m_bRetired'):
        issues.append('retired-talent')
    if data.get('m_behaviors'):
        issues.append('unsupported-talent-behaviors')
    trait = {'id': 'w101:talent:' + str(name), 'name': display or str(name),
             'statBonus': {'values': {}}, 'itemCards': [], 'attributeModifiers': {}, 'effects': []}
    for modifier in data.get('m_maxStatList', []):
        key, value = modifier.get('m_name', '').upper(), modifier.get('m_value')
        if key not in ATTRIBUTES or not finite(value) or value < 0:
            issues.append('unsupported-attribute-modifier')
        else:
            trait['attributeModifiers'][key] = trait['attributeModifiers'].get(key, 0) + value
    for info in data.get('m_effectList', []):
        if typename(info) != 'StatisticEffectInfo':
            issues.append('unsupported-talent-effect:' + typename(info))
            continue
        effect = effects.get(info.get('m_effectName'))
        if not effect or typename(effect) != 'PetBoostPlayerStatEffectTemplate' or info.get('m_lookupIndex') != -1:
            issues.append('unsupported-pet-effect:' + str(info.get('m_effectName')))
            continue
        keys = [effect.get(k, '').upper() for k in ['m_primaryStat1', 'm_primaryStat2', 'm_secondaryStat']]
        modifier = effect.get('m_secondaryModifier')
        if not all(k in ATTRIBUTES for k in keys) or not finite(modifier) or modifier < 0:
            issues.append('unsupported-pet-effect-attributes')
            continue
        if any(effect.get(field, 0) for field in UNSUPPORTED_STATS):
            issues.append('unsupported-pet-stat')
            continue
        coefficients = {}
        for field, (stat, factor, global_stat) in EFFECT_STATS.items():
            raw = effect.get(field, 0)
            if not finite(raw) or raw < 0:
                issues.append('invalid-pet-coefficient:' + field)
                continue
            if not raw:
                continue
            school = 'UNIVERSAL' if global_stat or effect.get('m_buffAll') else effect.get('m_school', '').upper()
            if school not in SCHOOLS | {'UNIVERSAL'}:
                issues.append('unsupported-pet-school')
                continue
            coefficients[stat + ':' + school] = round(raw * factor, 12)
        if not coefficients:
            issues.append('empty-pet-effect')
        trait['effects'].append(dict(zip(['primaryStat1', 'primaryStat2', 'secondaryStat'], keys),
                                     secondaryModifier=modifier, coefficients={'values': coefficients}))
    if not trait['effects'] and not trait['attributeModifiers']:
        issues.append('no-supported-talent-bonus')
    return name, trait, sorted(set(issues))


def load_definitions(resources):
    effects, canonical, tables, names = {}, {}, {}, {}
    for row in resources:
        if 'error' in row:
            continue
        member, data = row['member'], row['decoded']
        if member == 'GameEffectData/PetTalentEffects.xml':
            effects = {e['m_effectName']: e for e in data['m_effectTemplates']}
        elif member == 'GameEffectData/CanonicalStatEffects.xml':
            canonical = {e['m_effectName']: e for e in data['m_effectTemplates']}
        elif member.startswith('GameEffectRuleData/'):
            tables[data['m_tableName']] = data
        elif member.startswith('Locale/en-US/'):
            try:
                names.update(localization(data))
            except ValueError:
                pass  # Raw resource/error survives in the capture; missing talent names reject that talent.
    talents, raw, excluded = {}, {}, {}
    for row in resources:
        if 'error' in row or not isinstance(row.get('decoded'), dict) or typename(row['decoded']) != 'PetTalentTemplate':
            continue
        key, trait, issues = talent_definition(row, effects, names)
        raw[row['member']] = row
        if key in excluded:
            continue
        if issues or key in talents and talents[key] != trait:
            excluded[key or row['member']] = issues or ['conflicting-talent-definitions']
            talents.pop(key, None)
        else:
            talents[key] = trait
    return talents, raw, excluded, canonical, tables, names


def pet_configuration(profile, talents, body, capture):
    if not isinstance(profile.get('hypothetical'), bool):
        raise ValueError('Pet profile must explicitly declare hypothetical true/false')
    ids = profile.get('talentIds', [])
    if len(ids) > 5 or len(ids) != len(set(ids)):
        raise ValueError('Pet profile needs at most five distinct manifested talents')
    try:
        traits = [copy.deepcopy(talents[key]) for key in ids]
    except KeyError as error:
        raise ValueError('Unresolved pet talent: ' + str(error))
    age = profile['age']
    if type(age) is not int or not 0 <= age <= 7 or len(ids) > max(0, min(5, age - 1)):
        raise ValueError('Pet age does not permit its manifested talents')
    attributes = profile['attributes']
    maximum = profile.get('maximumAttributes', attributes)
    if set(attributes) != ATTRIBUTES or set(maximum) != ATTRIBUTES:
        raise ValueError('Supply all five trained and maximum pet attributes')
    if any(not finite(attributes[k]) or not finite(maximum[k]) or not 0 <= attributes[k] <= maximum[k] for k in ATTRIBUTES):
        raise ValueError('Invalid trained/maximum pet attributes')
    fully = profile.get('fullyTrained', False)
    if type(fully) is not bool or fully and attributes != maximum:
        raise ValueError('fullyTrained requires unmodified attributes at their declared maximums')
    if not fully and any(t['attributeModifiers'] for t in traits):
        raise ValueError('Attribute modifiers currently require a fullyTrained profile')
    if profile.get('rounding') not in ['RAW', 'NEAREST_WHOLE']:
        raise ValueError('Choose an explicit RAW or NEAREST_WHOLE rounding policy')
    data = body['decoded']
    if 'Pet' not in data.get('m_adjectiveList', []) or data.get('m_itemSetBonusTemplateID') or data.get('m_equipEffects'):
        raise ValueError('Pet body has unsupported intrinsic effects/set rules or is not a pet')
    behaviors = [b for b in data.get('m_behaviors', []) if b and b.get('m_behaviorName') == 'PetItemBehavior']
    if len(behaviors) != 1:
        raise ValueError('Pet body needs one PetItemBehavior')
    cards = []
    for level in behaviors[0].get('m_Levels', []):
        if level.get('m_level') == age:
            for suffix in ['', '2', '3']:
                name = level.get('m_powerCardName' + suffix)
                if name:
                    cards.append('w101:spell-name:' + name)
    return {'id': profile['id'], 'name': profile['name'], 'slot': 'PET', 'level': 1, 'schools': [],
            'baseStats': {'values': {}}, 'sockets': [{'type': 'PET', 'allowedSchools': []}],
            'setIds': [], 'itemCards': cards, 'complete': False, 'category': 'explicit-pet-configuration',
            'source': 'wizard101-wad-pet-profile', 'gameVersion': 'exe-sha256:' + capture['executableSha256'],
            'importedAt': capture['finishedAt'],
            'pet': {'name': profile['name'], 'hypothetical': profile['hypothetical'], 'age': age,
                    'attributes': attributes, 'maximumAttributes': maximum, 'fullyTrained': fully,
                    'rounding': profile['rounding'], 'traits': traits},
            'extensions': {'bodyTemplateId': body['id'], 'bodyMember': body['member'],
                           'profile': profile, 'validation': 'Provisional attribute formula; no retail rounding verification',
                           'availability': 'hypothetical; breeding not established' if profile['hypothetical'] else 'user-declared owned'}}


def build(staging, resource_capture, profiles_path, output, replace=False):
    if output.exists() and not replace:
        raise ValueError('Choose a new pet-catalog output path to preserve earlier results')
    capture = json.loads((resource_capture / 'capture.json').read_text(encoding='utf-8'))
    resources = list(records(resource_capture / 'resources.ndjson'))
    talents, raw, excluded, canonical, tables, names = load_definitions(resources)
    if not talents:
        raise ValueError('No supported pet talent definitions; recapture with the updated decoder')
    catalog = {'schemaVersion': 1, 'gear': [], 'jewels': [], 'petTalents': [talents[key] for key in sorted(talents)], 'setBonuses': [], 'baseStats': {},
               'extensions': {'capture': capture, 'validation': 'Provisional pet calculations; all complete=false'}}
    profile_doc = json.loads(profiles_path.read_text(encoding='utf-8'))
    if profile_doc.get('schemaVersion') != 1 or not isinstance(profile_doc.get('pets'), list):
        raise ValueError('Pet profiles need schemaVersion=1 and a pets array')
    jewel_exclusions = Counter()
    with closing(sqlite3.connect(staging.resolve().as_uri() + '?mode=ro', uri=True)) as db:
        previous = json.loads(db.execute('SELECT json FROM raw_capture WHERE id=1').fetchone()[0])
        if previous['executableSha256'] != capture['executableSha256'] or previous['archives']['Root']['sha256'] != capture['archives']['Root']['sha256']:
            raise ValueError('Pet resources and staging database are from different client/archive revisions')
        for entry in profile_doc['pets']:
            profile = dict(profile_doc.get('defaults', {}), **entry)
            row = db.execute('SELECT json FROM raw_templates WHERE template_id=?', (profile['bodyTemplateId'],)).fetchone()
            if not row:
                raise ValueError('Unknown pet body template ' + profile['bodyTemplateId'])
            catalog['gear'].append(pet_configuration(profile, talents, json.loads(row[0]), capture))
        for (payload,) in db.execute("SELECT json FROM raw_templates WHERE member LIKE '%Jewel%'"):
            row = json.loads(payload)
            if 'PetJewel' not in row['decoded'].get('m_adjectiveList', []):
                continue
            projected, issues = project(row, names, canonical, tables, capture)
            if not projected:
                continue
            item = projected[1]
            issues = [issue for issue in issues if issue != 'pet-jewel-talent-resolution-required']
            behaviors = [b for b in row['decoded'].get('m_behaviors', []) if b and b.get('m_behaviorName') == 'PetJewelBehavior']
            if len(behaviors) != 1:
                issues.append('missing-pet-jewel-behavior')
            else:
                behavior = behaviors[0]
                ids, age = behavior.get('m_petTalentName', []), behavior.get('m_minPetLevel')
                if not ids or len(ids) != len(set(ids)) or any(key not in talents for key in ids):
                    issues.append('unsupported-jewel-talent')
                elif type(age) is not int or not 4 <= age <= 7:
                    issues.append('unsupported-pet-jewel-age')
                else:
                    item.update(petTalents=[copy.deepcopy(talents[key]) for key in ids],
                                grantedTalentIds=[talents[key]['id'] for key in ids], minimumPetAge=age, gearSlots=['PET'])
            if issues:
                jewel_exclusions.update(issues)
                continue
            item['extensions']['issues'] = []
            item['extensions']['validation'] = 'Provisional pet talent/age mapping; retail verification pending'
            catalog['jewels'].append(item)
    ids = [item['id'] for item in catalog['gear'] + catalog['jewels']]
    if len(ids) != len(set(ids)):
        raise ValueError('Duplicate pet configuration/jewel ID')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json_text(catalog) + '\n', encoding='utf-8')
    report = {'supportedTalents': len(talents), 'rawTalentDefinitions': len(raw), 'excludedTalents': excluded,
              'excludedJewelReasons': dict(jewel_exclusions), 'pets': len(catalog['gear']), 'petJewels': len(catalog['jewels'])}
    report_path = output.with_suffix('.report.json')
    report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    # Source definitions survive in a separate artifact, including unsupported/retired talents.
    output.with_suffix('.talents.json').write_text(json_text({'rawDefinitions': raw, 'supported': talents, 'excluded': excluded}) + '\n', encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k != 'excludedTalents'}, indent=2))
    return catalog


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--staging', type=Path, required=True)
    parser.add_argument('--resource-capture', type=Path, required=True)
    parser.add_argument('--profiles', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--replace', action='store_true', help='Replace generated pet catalog/report/talent artifacts at the output path')
    args = parser.parse_args()
    build(args.staging, args.resource_capture, args.profiles, args.output, args.replace)


if __name__ == '__main__':
    main()
