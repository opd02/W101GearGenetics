"""Publish a portable, request-specific runtime from existing extracted inputs.

Developer tool only: the published launcher needs Java and PowerShell, not Python
or the extraction databases. Original captures and databases are never modified.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import tempfile

from build_pet_catalog import build as build_pets
from prepare_mock_catalog import prepare

ROOT = Path(__file__).resolve().parents[1]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def publish(database, staging, resources, request_path, profiles, jar, output):
    request_bytes = request_path.read_bytes()
    request = json.loads(request_bytes)
    if not jar.is_file():
        raise ValueError('Build the executable JAR with mvn package first')
    # Use the same builders, filters and ordering as Run-DeathStormOptimizer.ps1.
    with tempfile.TemporaryDirectory(prefix='geargenetics-portable-') as scratch:
        pets = build_pets(staging, resources, profiles, Path(scratch) / 'pet-catalog.json')
        catalog = prepare(database, request, pets, include_pins=True, include_unresolved_gear=True)
    raw = (json.dumps(catalog, ensure_ascii=False, allow_nan=False) + '\n').encode('utf-8')
    compressed = gzip.compress(raw, compresslevel=9, mtime=0)
    executable = jar.read_bytes()
    capture = json.loads((resources / 'capture.json').read_text(encoding='utf-8'))
    manifest = {
        'schemaVersion': 1,
        'minimumJava': 22,
        'coverage': {
            'school': request['wizard']['school'],
            'maximumLevel': request['wizard']['level'],
            'slots': request['requiredSlots'],
            'gearPool': 'BROAD',
            'includePins': True,
        },
        'counts': {
            'gear': len(catalog['gear']),
            'jewels': len(catalog['jewels']),
            'petTalents': len(catalog.get('petTalents', [])),
            'petProfiles': len(pets['gear']),
        },
        'files': {
            'optimizer.jar': {'sha256': digest(executable), 'bytes': len(executable)},
            'catalog.json.gz': {'sha256': digest(compressed), 'bytes': len(compressed)},
            'catalog.json': {'sha256': digest(raw), 'bytes': len(raw)},
        },
        'source': {
            'executableSha256': capture['executableSha256'],
            'rootSha256': capture['archives']['Root']['sha256'],
            'petResourcesCapturedAt': capture['finishedAt'],
            'requestSha256': digest(request_bytes),
            'petProfilesSha256': digest(profiles.read_bytes()),
        },
        'validation': 'Provisional WAD records, hypothetical pets and proportional scaling; '
                      'retail stats, availability and formulas remain unverified. '
                      'Base-stat assumptions are specified in the request.',
    }
    output.mkdir(parents=True, exist_ok=True)
    # Publish the manifest last so an interrupted update fails launcher hash checks.
    (output / 'catalog.json.gz').write_bytes(compressed)
    shutil.copyfile(jar, output / 'optimizer.jar')
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'output': str(output), 'counts': manifest['counts'],
                      'totalBytes': len(compressed) + len(executable)}, indent=2))
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', type=Path, default=ROOT / 'target/wizard101/optimizer.sqlite')
    parser.add_argument('--staging', type=Path, default=ROOT / 'target/wizard101/staging.sqlite')
    parser.add_argument('--resource-capture', type=Path, default=ROOT / 'target/pet-resources-20261102')
    parser.add_argument('--request', type=Path, default=ROOT / 'examples/death-storm-request.json')
    parser.add_argument('--pet-profiles', type=Path, default=ROOT / 'examples/pet-profiles.json')
    parser.add_argument('--jar', type=Path, default=ROOT / 'target/GearGenetics-1.0-SNAPSHOT.jar')
    parser.add_argument('--output', type=Path, default=ROOT / 'runtime/storm-death')
    args = parser.parse_args()
    publish(args.database, args.staging, args.resource_capture, args.request,
            args.pet_profiles, args.jar, args.output)


if __name__ == '__main__':
    main()
