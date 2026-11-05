"""Normalize decoded LevelScaledData into the versioned, bundled proportional-model caps."""
import argparse
import json
import math
from pathlib import Path

SCHOOLS = ('FIRE', 'ICE', 'STORM', 'MYTH', 'LIFE', 'DEATH', 'BALANCE')
SCHOOL_FIELDS = {
    'Accuracy': ('ACCURACY', 100), 'ArmorPiercing': ('PIERCE', 100),
    'Damage': ('DAMAGE', 100), 'ReduceDamage': ('RESISTANCE', 100),
    'FlatDamage': ('FLAT_DAMAGE', 1), 'FlatReduceDamage': ('FLAT_RESISTANCE', 1),
    'CriticalHit': ('CRITICAL_RATING', 1), 'Block': ('BLOCK_RATING', 1),
    'PipConversion': ('PIP_CONVERSION_RATING', 1),
}
GLOBAL_FIELDS = {
    'MaxHealth': ('HEALTH', 1), 'MaxMana': ('MANA', 1), 'PowerPip': ('POWER_PIP_CHANCE', 100),
    'ShadowPipRating': ('SHADOW_PIP_RATING', 1), 'IncHealing': ('INCOMING_HEALING', 100),
    'LifeHealing': ('OUTGOING_HEALING', 100), 'AllArchmastery': ('ARCHMASTERY_RATING', 1),
}


def normalize(decoded, provenance):
    mapping = {'m_canonical' + school.title() + field: (stat + ':' + school, unit)
               for field, (stat, unit) in SCHOOL_FIELDS.items() for school in SCHOOLS}
    mapping.update({'m_canonical' + field: (stat + ':UNIVERSAL', unit)
                    for field, (stat, unit) in GLOBAL_FIELDS.items()})

    def values(row, floors=False):
        result = {}
        for field, (key, unit) in mapping.items():
            value = row[field]
            if type(value) not in (int, float) or not math.isfinite(value) or value < 0:
                raise ValueError('Invalid cap/floor: ' + field)
            result[key] = round(value * (1 if floors else unit), 8)
        return result

    rows, seen = [], set()
    for row in decoded['m_levelScaledInfoList']:
        school, level = row['m_school'].upper(), row['m_level']
        if school not in SCHOOLS or type(level) is not int or level < 10 or level % 10:
            raise ValueError('Invalid primary school or level band')
        if (school, level) in seen:
            raise ValueError('Duplicate school/level band')
        seen.add((school, level))
        rows.append({'school': school, 'level': level, 'caps': values(row)})
    if not rows:
        raise ValueError('No level-scaling records')
    source = {name + 'Sha256': provenance['source'][name]['sha256'] for name in ('root', 'executable', 'member')}
    source['member'] = 'LevelScaledData.xml'
    return {'schemaVersion': 1, 'version': 'retail-20261031-proportional-v1', 'source': source,
            'floorPercent': values(decoded['m_statsFloorPercent'], floors=True),
            'rows': sorted(rows, key=lambda r: (r['school'], r['level']))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--decoded', type=Path, required=True)
    parser.add_argument('--provenance', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    table = normalize(json.loads(args.decoded.read_text(encoding='utf-8')),
                      json.loads(args.provenance.read_text(encoding='utf-8')))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(table, indent=2, allow_nan=False) + '\n', encoding='utf-8')
    print('Wrote {} school/band cap records to {}'.format(len(table['rows']), args.output))


if __name__ == '__main__':
    main()
