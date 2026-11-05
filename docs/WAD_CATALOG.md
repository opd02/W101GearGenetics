# Offline WAD item database

**Pin/pet update, 2026-11-02:** matching pin holders and explicit pet configurations/star jewels are now implemented. The supplemental resource capture retains non-item talent definitions; generated records remain provisional. See [PINS_AND_PETS.md](PINS_AND_PETS.md) for the current workflow and validation boundary. The capture counts below describe the original database snapshot.

The first real retail extraction was completed on 2026-10-31. The optimizer itself is unchanged.

## Results

| Stage | Count |
| --- | ---: |
| Manifest entries discovered | 137,423 |
| Templates deserialized | 137,421 |
| Decode failures | 2 |
| Item templates retained, including non-equipment | 80,162 |
| Provisional gear records | 60,646 |
| Provisional jewels and pins | 4,416 |
| Projected records without a known mapping issue | 41,475 |
| Projected records with unresolved fields | 23,587 |
| Independently verified complete records | **0** |

The two failures are `ObjectData/Elixirs/Elixir-063.xml` and `Elixir-064.xml`, where the local decoder encounters an unexpected requirement/result type. Pets remain in raw storage because body templates do not determine individual talents. One unclassified jewel, non-equipment and ambiguous objects also remain in raw storage.

The capture followed the manifest through **27 installed WADs**. This measures the installed manifest, not every retail item, unregistered archive members, or current availability. QA, retired, obsolete, NPC, test and cosmetic definitions can occur in these files.

## Generated artifacts

These local game-data files are under ignored `target/` and are not committed:

- `target/retail-capture-20261031-b/`: capture provenance, decoded item/resource NDJSON, per-template outcomes and original manifest bytes.
- `target/wizard101/staging.sqlite`: all raw items, resources, manifest coverage, provisional projections and resolved stat rows.
- `target/wizard101/catalog.json`: existing Java catalog format, with every record `complete=false`.
- `target/wizard101/optimizer.sqlite`: catalog validated/imported by the existing Java importer.
- `target/wizard101/staging.report.json`: coverage, exclusions, unresolved fields and resource problems.

Earlier intermediate captures/databases remain under `target/`; the paths above identify the final outputs.

## Provenance

Source client: `C:\ProgramData\KingsIsle Entertainment\Wizard101`. Its running executable was identified by process path; extraction never attached to memory. No game process is required for extraction, normalization or database work.

- Executable SHA-256: `544ab726ef7b920a5ccb67aa3d06b245db7e0012317d2fe7856d58c17c31cf7e`.
- Retail Root.wad SHA-256: `4e09b3da242a6f5650624497359dfaebf6cd54e72a0ae1ee21bdcf7436b42c66`.
- This Root hash exactly matches the original retail source in PersonalServer's successful custom-spell build report. The custom client/server WAD was not used.
- Decoder libraries: `H:\Vibe Coding\PersonalServer\local-run\server`. The corresponding local Imcodec source checkout is `caef26830323a112248fec338e7e8ca646e43ec5`, using the previously generated `r806919_Wizard_1_610.json` schema. Capture metadata records the actual decoder assembly hash and every opened archive's hash.

Successful deserialization is compatibility evidence, not proof the schema understands every field. All projected records remain incomplete.

Source files are opened for reading; opened archive handles deny writes until capture ends. Lengths/decompression are checked and uncompressed member SHA-256 values retained. The retail manifest's header checksum did not match standard CRC32 of its compressed or uncompressed bytes. Its semantics remain unverified; this tool does **not** claim CRC validation. SHA-256 provides provenance. Retain a stable client/archive snapshot separately for patch-independent reproducibility.

## Reproduce

Requires .NET 10 SDK, Python 3.8+, compatible local Imcodec DLLs, and the existing JDK 22+/Maven Java build. No private server, proxy, game launch or memory hooks are needed. External decoder binaries are not bundled.

```powershell
dotnet build bridge/WadCatalog/WadCatalog.csproj '-p:ImcodecDir=H:\Vibe Coding\PersonalServer\local-run\server'
dotnet target/wad-decoder/bin/Debug/net10.0/WadCatalog.dll 'C:\ProgramData\KingsIsle Entertainment\Wizard101' target/new-capture
python bridge/build_wad_catalog.py --capture target/new-capture --db target/new-items/staging.sqlite --catalog target/new-items/catalog.json
java -Xmx2g -jar target/GearGenetics-1.0-SNAPSHOT.jar import-json --db target/new-items/optimizer.sqlite --catalog target/new-items/catalog.json
```

Use new capture/database/catalog paths to preserve earlier results. A capture without `capture.json` did not finish and cannot be staged. Refresh decoder definitions after schema updates; this local DLL set is not guaranteed compatible with future patches.

## Staging schema

| Table | Contents |
| --- | --- |
| `raw_capture` | Executable, decoder, archive hashes and capture summary |
| `raw_resources` | Canonical effects, stat tables, English language resources and manifest |
| `raw_coverage` | Every manifest ID and decode outcome |
| `raw_templates` | All item templates, original decoded JSON, paths and hashes |
| `provisional_items` | Gear/jewel projections, localized names, slot/shape/level and issues |
| `resolved_stats` | Queryable stat/school/value contributions |

Example SQLite queries:

```sql
SELECT id, name, slot, level, issues_json
FROM provisional_items WHERE name LIKE '%Aeon%'
ORDER BY level, name;

SELECT p.name, s.stat, s.school, s.value
FROM provisional_items p JOIN resolved_stats s USING (id)
WHERE p.template_id = '1578291';

SELECT template_id, json FROM raw_coverage WHERE status <> 'decoded';
```

Optimizer IDs are `w101:template:<manifest-id>`. Source IDs remain strings. English names come from language resources, with explicit fallback issues. Raw decoded payloads survive unsupported projection fields.

## Mapping and remaining gaps

Stats resolve via canonical effect -> table -> explicit lookup index. Table-family mappings specify units: fractional damage, accuracy, pierce, resistance, healing and pip chance become percentage points; health/mana/energy/ratings retain raw units. Flat bonuses stay flat. Universal and school contributions remain separate. Duplicate additive contributions are summed with individual traces retained. Missing definitions, invalid indices and unsupported types/scopes generate issues instead of silently becoming zero.

Eligibility comes from explicit focus-school requirements, including simple negated school exclusions. `m_school` remains metadata. Older `ReqMagicLevel.m_magicSchool` values sometimes disagree with explicit focus requirements; those semantics remain flagged rather than forcing incorrect eligibility. Arbitrary OR/NOT conditions remain unresolved.

Sockets preserve order and raw lockability. `SOCKETTYPE_PINSQUAREPIP`/`PinSquarePip` maps to `POWER_PIN`. The updated importer maps pin holders to hats/robes/boots and the supplemental pet pipeline resolves supported talent/age definitions; retail verification remains outstanding. Item spells retain source names and copy counts. Sets retain references in extensions until their thresholds are implemented. Starting pips, combat triggers, speed, mastery, fishing luck and other unsupported effects remain unresolved.

Archive-derived spot checks:

| Template | Localized name | Extracted result |
| --- | --- | --- |
| `1352577` | Cracked Damage Ruby +1 | Level 15; +1 flat Fire damage; circle; no focus restriction |
| `1578291` | Death Disabling Pin (140) | Level 140; Balance focus; +20% Death damage, +84 Death critical |
| `1363076` | The One Ring | QA; +3000 health, +500 mana, +300% universal damage/resistance; four ordered sockets |

These are not new retail item-card verification. The QA ring was previously tested in the separate PersonalServer client; that does not establish retail availability. The default optimizer excludes all these provisional records.

Next milestone: compare representative obtainable retail item cards, resolve remaining effect/requirement/set/pet mechanics, and establish availability before promoting individual records to complete.

## Validation

- C# adapter builds with zero warnings/errors.
- All 22 existing Java tests pass; the packaged CLI imports the real catalog.
- All 12 Python tests pass, covering units, off-school/universal separation, invalid indices, eligibility, negated focus, power pins, sockets, localization, unresolved effects and SQLite round trips.
- Both final SQLite databases pass `PRAGMA integrity_check`.
- Six language resource issues are separately reported: duplicate keys in five Console files and malformed alignment in one quest file. Their raw text is retained.
