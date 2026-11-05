"""Prepare request-specific filtered or broad gear pools from the extracted database."""
import argparse
from contextlib import closing
import json
from pathlib import Path
import re
import sqlite3

TEST_MARKERS = re.compile(r'(?:^|[/_\s-])(?:qa|test|debug|polymorph|transformation|stats|godmode|developer)(?:$|[/_\s-])', re.IGNORECASE)


def prepare(database, request, pet_catalog=None, include_pins=False, include_unresolved_gear=False):
    school, level = request['wizard']['school'], request['wizard']['level']
    slots = set(request['requiredSlots'])
    catalog = {'schemaVersion': 1, 'gear': [], 'jewels': [], 'setBonuses': [], 'baseStats': {},
               'extensions': {'demoPool': 'All levels through the requested level; no recognizable test/polymorph markers',
                              'gearPool': 'BROAD' if include_unresolved_gear else 'FILTERED',
                              'includeUnresolvedGear': include_unresolved_gear,
                              'sourceDatabase': str(database), 'includePins': include_pins,
                              'characterLevel': level, 'scaledLevel': request['wizard'].get('scaledLevel'),
                              'validation': 'Provisional archive data; not a full-catalog search'}}
    with closing(sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True)) as db:
        for kind, payload in db.execute('SELECT kind,payload FROM items WHERE level<=?', (level,)):
            item = json.loads(payload)
            if item['schools'] and school not in item['schools']:
                continue
            if item['category'] == 'qa-or-test':
                continue
            if TEST_MARKERS.search(item['name'] + ' ' + item.get('extensions', {}).get('member', '')):
                continue
            issues = item.get('extensions', {}).get('issues', [])
            if kind == 'gear':
                if item['slot'] not in slots or item['slot'] == 'PET':
                    continue
                if not include_unresolved_gear and any(issue != 'unsupported-effect:StartingPipEffectInfo' for issue in issues):
                    continue
                catalog['gear'].append(item)
            elif kind == 'jewel':
                is_pin = item['type'] in {'SWORD_PIN', 'SHIELD_PIN', 'POWER_PIN'}
                if is_pin and include_pins:
                    if any(issue != 'pin-holder-restrictions-unverified' for issue in issues):
                        continue
                    # Upgrade the old staging projection without altering its database.
                    item['gearSlots'] = ['HAT', 'ROBE', 'BOOTS']
                    item['extensions']['issues'] = []
                    item['extensions']['pinCompatibilitySource'] = 'https://akamaieu.wizard101.com/game/community/updatenotes/may2024'
                    catalog['jewels'].append(item)
                elif not issues and item['type'] in {'CIRCLE', 'TEAR', 'SQUARE', 'TRIANGLE'}:
                    if item['extensions'].get('member', '').startswith('ObjectData/Jewels/Jewel/'):
                        catalog['jewels'].append(item)
    if pet_catalog is not None:
        if pet_catalog.get('schemaVersion') != 1:
            raise ValueError('Unsupported pet catalog schema')
        if any(p.get('slot') != 'PET' for p in pet_catalog['gear']) or any(j.get('type') != 'PET' for j in pet_catalog['jewels']):
            raise ValueError('Pet catalog may only contain PET gear and PET jewels')
        capture = pet_catalog.get('extensions', {}).get('capture', {})
        if capture.get('executableSha256'):
            expected = 'exe-sha256:' + capture['executableSha256']
            if any(g.get('gameVersion') != expected for g in catalog['gear'] if g.get('source') == 'wizard101-wad'):
                raise ValueError('Pet and gear catalogs come from different client revisions')
        if 'PET' in slots:
            catalog['gear'].extend(pet_catalog['gear'])
            catalog['jewels'].extend(pet_catalog['jewels'])
            catalog['petTalents'] = pet_catalog.get('petTalents', [])
            catalog['extensions']['petConfigurations'] = pet_catalog.get('extensions', {})
    ids = [item['id'] for item in catalog['gear'] + catalog['jewels']]
    if len(ids) != len(set(ids)):
        raise ValueError('Duplicate IDs while merging pet catalog')
    covered = {item['slot'] for item in catalog['gear']}
    if slots - covered:
        raise ValueError('Mock pool has no candidates for: ' + ', '.join(sorted(slots - covered)))
    return catalog


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--db', type=Path, required=True)
    parser.add_argument('--request', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--include-pins', action='store_true')
    parser.add_argument('--include-unresolved-gear', action='store_true',
                        help='Retain provisional gear with unresolved mappings; unknown effects are not scored. Jewel filters remain unchanged.')
    parser.add_argument('--pet-catalog', type=Path)
    args = parser.parse_args()
    request = json.loads(args.request.read_text(encoding='utf-8'))
    pets = json.loads(args.pet_catalog.read_text(encoding='utf-8')) if args.pet_catalog else None
    catalog = prepare(args.db, request, pets, args.include_pins, args.include_unresolved_gear)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(catalog, ensure_ascii=False) + '\n', encoding='utf-8')
    print('Mock pool: {} gear and {} jewels, levels 1-{}. No lower-level cutoff; all records provisional.'.format(
        len(catalog['gear']), len(catalog['jewels']), request['wizard']['level']))
    if args.include_unresolved_gear:
        unresolved = sum(bool(g.get('extensions', {}).get('issues')) for g in catalog['gear'])
        print('BROAD gear pool: {} records retain unresolved issues. Only decoded numeric bonuses are scored; unknown effects, requirements and set bonuses remain unverified.'.format(unresolved))
    if request['wizard'].get('scaledLevel') is not None:
        print('Character level {} selects gear; targets will be evaluated after scaling to level {}.'.format(
            request['wizard']['level'], request['wizard']['scaledLevel']))


if __name__ == '__main__':
    main()
