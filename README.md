# W101GearGenetics

A local Java gear optimizer supporting all seven schools independently, off-school targets, mandatory requirements, locked gear/sockets, exclusions, owned inventory, seeded genetic search and ranked alternatives. The existing Maven project and equipment classes have been evolved in place.

**The modern example catalog is synthetic test data, not a verified in-game build or comprehensive gear database.** The original CSV files are preserved.

## Build and run

Use JDK 22+ and Maven 3.9+. Python, WizWalker and a running game are unnecessary for normal optimization.

```powershell
mvn test
mvn package
java -jar target/GearGenetics-1.0-SNAPSHOT.jar help
java -jar target/GearGenetics-1.0-SNAPSHOT.jar optimize --request examples/offschool-request.json --catalog examples/catalog.json --output target/results.json
```

The packaged JAR includes dependencies and the SQLite driver. First build needs dependency downloads. Exit status is 0 for a completed search, including one with unmet requirements, or 2 for errors. Check solution feasibility.

Create a saved JSON request interactively:

```powershell
java -jar target/GearGenetics-1.0-SNAPSHOT.jar init --output my-request.json
```

## Off-school example

[The example request](examples/offschool-request.json) selects a level-170 Death wizard and requires Storm damage >=150, Fire damage >=60, universal resistance >=40 and power-pip chance >=100. It prefers Storm accuracy >=35, then maximizes health. Its deck and an amulet jewel are locked.

With [the synthetic catalog](examples/catalog.json), the highest-ranked setup has **150 Storm damage, 60 Fire damage, 40 universal resistance, 100 power-pip chance, 35 Storm accuracy and 5,705 health**. Four meaningful alternatives exist. Damage for Fire/Ice/Storm/Myth/Life/Death/Balance is 60/40/150/40/40/40/40. Universal damage remains separately stored as 40.

The CLI displays every item, positional socket, pet talent, all seven school stat totals plus universal, raw equipment and character stats, target achievements/deficits, fitness components, set thresholds and changed slots relative to the first result.

All fixture names and values, including base stats, are invented for deterministic software validation. Replace them with verified records for gameplay use.

Verify this tiny catalog exhaustively:

```powershell
java -jar target/GearGenetics-1.0-SNAPSHOT.jar optimize --request examples/offschool-exact-request.json --catalog examples/catalog.json --output target/exact-results.json
```

An exact result is optimal only within its supplied catalog, slots, restrictions and raw calculation model. Genetic results are heuristic.

## Desired stats

Targets use typed stats and school scopes:

```json
{
  "stat": "DAMAGE",
  "school": "STORM",
  "mode": "MINIMUM",
  "value": 150,
  "mandatory": true,
  "weight": 1,
  "scale": 150
}
```

Modes:

- MINIMUM / MAXIMUM: penalize shortfall / excess; may be mandatory.
- PREFERRED: approach a value using belowPenalty and abovePenalty, both default 1. Set abovePenalty to 0 for “at least preferred.”
- MAXIMIZE: reward raw total divided by scale.
- IGNORE: no influence.

Distances are divided by a positive scale and multiplied by a positive weight. Choose scales meaningful to units, e.g. 1,000 health versus 100 damage. Ranking compares **feasibility, smaller mandatory deficit, then larger soft objective**, in order. Soft score cannot make an infeasible build beat a feasible one. The output exposes this tuple and per-target components.

Damage, resistance, accuracy, piercing, critical/block ratings, pip-conversion rating and flat damage/resistance are school-aware. Global stats use UNIVERSAL. Percent values use percentage points: 100 means 100%. Shadow rating and chance remain different types. Raw item bonuses are retained separately from character totals. No unverified caps or rating conversions are applied.

Wizard profile fields include school, level, optional baseOverrides, ownedOnly, ownedItemIds and ownedQuantities. Base stats come from exact SCHOOL:level catalog entries. Manual overrides replace named source values. Without a source, manual entries are used and unspecified values are zero. Missing source plus no override is an error. An explicit empty values object intentionally supplies zero base stats. No real progression table is bundled.

Request JSON is reusable directly; optimize --save-request copy.json saves the resolved input.

## Lock, exclude and own equipment

```json
{
  "lockedItems": {"ROBE": "catalog:robe-123"},
  "lockedJewels": {"ATHAME:1": "catalog:jewel-456"},
  "excludedIds": ["catalog:unwanted"],
  "excludedSources": ["unwanted-source"],
  "excludedCategories": ["unwanted-category"]
}
```

IDs are stable catalog identifiers, not names. Socket indices start at zero. Locking gear fixes its identity; lock sockets separately to preserve jewels/pins. Pets carry their resolved manifested talents and are selected as whole entities. Explicit hypothetical pets cannot enter owned-only runs.

All ten slots are required by default. To intentionally optimize a partial set, list only those slots under requiredSlots. Omitted slots contribute nothing; such a result is not a complete character build.

ownedItemIds provides one copy per ID. ownedQuantities overrides counts, including zero. Locks reserve jewel copies before other sockets, and available quantities cannot be exceeded. Use distinct IDs for different owned pet/configuration variants.

Locks and restrictions are enforced in candidate generation, initialization, crossover, mutation, repair, exact search and final validation. Conflicts, ineligible locked items, empty selected pools and incompatible sockets produce errors.

## Search and alternatives

The search object configures seed, populationSize, generations, mutationRate, crossoverRate, tournamentSize, elites, method (GA or EXACT) and exactLimit. Defaults preserve the prior local population 250, mutation 0.5 and tournament 7, adding elitism.

The same request/catalog/seed reproduces results. Candidate domains are prefiltered once; full equipment/socket signatures cache evaluations. Progress and best-ever history report feasibility, deficit and objective. Pet selection has no unbounded loops.

topK defaults to 10. Equal stat/card/set outcomes are collapsed; minDifferentSlots controls minimum gear/socket differences between alternatives. Fewer results may be returned. Diversity filtering retains the best-ranked build. No unsafe dominance pruning discards candidates needed for maximum targets or set bonuses.

Exact search includes compatible socket choices and empty sockets. It refuses a Cartesian upper bound above exactLimit before enumeration. Ownership may reduce the actual count.

## SQLite, profiles and imports

```powershell
java -jar target/GearGenetics-1.0-SNAPSHOT.jar import-json --db items.sqlite --catalog examples/catalog.json
java -jar target/GearGenetics-1.0-SNAPSHOT.jar optimize --request examples/offschool-request.json --db items.sqlite
java -jar target/GearGenetics-1.0-SNAPSHOT.jar list --db items.sqlite --slot HAT --school DEATH --level 170 --stat DAMAGE --stat-school STORM --minimum 60
java -jar target/GearGenetics-1.0-SNAPSHOT.jar export-json --db items.sqlite --output exported.json
java -jar target/GearGenetics-1.0-SNAPSHOT.jar profile-save --db items.sqlite --name raid --request examples/offschool-request.json
java -jar target/GearGenetics-1.0-SNAPSHOT.jar own --db items.sqlite --profile raid --id demo-hat --quantity 1
java -jar target/GearGenetics-1.0-SNAPSHOT.jar optimize --db items.sqlite --profile raid
java -jar target/GearGenetics-1.0-SNAPSHOT.jar profile-export --db items.sqlite --name raid --output raid.json
```

Imports validate/upsert by stable ID transactionally. Profiles and inventory survive reimport. Incomplete records cannot overwrite complete ones, kind changes are rejected, and prior unknown extension keys survive if absent in the update. Completeness is an importer declaration requiring verification, not independent proof of game accuracy. Absent records and obsolete set thresholds are not deleted automatically.

Items retain source, game version and timestamp; storage fills missing import timestamps. SQLite indexes slot/level and keeps versioned JSON payloads for extension. Typed school/stat filtering follows the indexed query. See [the import contract](docs/IMPORT_FORMAT.md).

Legacy CSV migration remains available:

```powershell
java -jar target/GearGenetics-1.0-SNAPSHOT.jar import-csv --db legacy.sqlite --gear GearDB.csv --jewels JewelDB.csv --traits PetTraitsDB.csv --legacy-school FIRE
```

Legacy records lack school/level/ownership metadata. The caller maps damage, accuracy, critical, pierce and pip conversion to legacy-school; resistance/block map to universal. These are explicit migration assumptions. Original universal/school splits cannot be recovered. The one-argument Java loader preserves scalar bonuses under UNIVERSAL and flags the ambiguity; prefer the explicit-school overload.

Records are incomplete and excluded unless allowIncompleteItems is true. That opt-in acknowledges unknown eligibility: legacy level 1 is an unknown-level placeholder. Verify/correct normalized records for trustworthy optimization. The original CSV values are unchanged, including label/value discrepancies. Blade/sharpen flags become unspecified item-card IDs. Supply generic verified set rules; no item-name bonuses remain. Historical pet-trait rows export to target/legacy-traits.json and are not grafted onto owned pets.

## Optional Python / WizWalker bridge

```powershell
python -m unittest discover -s bridge -p "test_*.py" -v
python bridge/normalize.py --input bridge/fixtures/items.json --output target/bridge-catalog.json
java -jar target/GearGenetics-1.0-SNAPSHOT.jar import-json --db bridge.sqlite --catalog target/bridge-catalog.json
java -jar target/GearGenetics-1.0-SNAPSHOT.jar optimize --request examples/offschool-request.json --db bridge.sqlite
```

The normalizer runs without WizWalker/game installation. It preserves unsupported stats and flags incomplete records. Java validates the exported catalog.

Codeberg source was inspected at commit 95e30503e8ce8510da9d4ceb8184e7cedb5bfef9, declaring package 1.8.2. Inventory/equipment readers and IDs exist, but a complete verified item-stat/socket/restriction decoder was not found. Aggregate stats cannot identify individual bonuses. No live attachment ships: upstream hooks write client memory, conflicting with the requested read-only boundary.

An opt-in offline WAD-manifest prototype uses verified Wad methods. It lists members, not decoded gear, and has not been validated against a real game archive. See [the source-backed feasibility report](docs/WIZWALKER.md).

## Building a complete item catalog with Imlight, Imcodec and WizWalker

The proposed catalog pipeline uses two separate data feeds:

| Feed | Source | Purpose |
| --- | --- | --- |
| Item catalog | Installed game archives decoded with Imcodec | Discover item templates, restrictions and bonuses |
| Owned inventory | Running client through a verified WizWalker reader | Identify the player's equipment, jewels and individual pets |

[Imlight](https://github.com/Revive101/Imlight) and its [Imcodec dependency](https://github.com/Jooty/Imcodec) provide a concrete path to investigate for bulk extraction. Source inspection covered Imlight revision 8c75ea02bbde4f9329ae6bae3ac91fc5fcb201b7 and Imcodec revision 4f37063167dd50c49f30d22bc4ccb3e8f8aa0632. **These capabilities have not been tested against this project's game installation; a complete retail catalog importer is not implemented.**

### What Imlight and Imcodec provide

Imcodec supports WAD extraction and Object Property deserialization. The inspected revision can also generate type definitions from the installed executable by emulating type registration, without launching or attaching to the game. Matching definitions are important: a decoder built for another client revision may not understand the current files. See [the inspected Imcodec documentation](https://github.com/Jooty/Imcodec/blob/4f37063167dd50c49f30d22bc4ccb3e8f8aa0632/README.md).

Imlight demonstrates how item statistics are resolved. Its equipment processing reads m_equipEffects; a statistic effect can contain an effect name and lookup index:

~~~text
item.m_equipEffects
    -> effect name + lookup index
    -> canonical effect definition
    -> referenced stat table
    -> numeric bonus
    -> explicit stat, school and unit mapping
    -> normalized StatBlock
~~~

The [CanonicalStatEffects implementation](https://github.com/Revive101/Imlight/blob/8c75ea02bbde4f9329ae6bae3ac91fc5fcb201b7/src/Imlight.CoreLib/Game/Effects/CanonicalStatEffects.cs) loads GameEffectData/CanonicalStatEffects.xml and resolves values through tables under GameEffectRuleData. This is a starting point for decoding damage, accuracy, resistance and other bonuses. Unit conversions must be verified per stat; multiplying every float by 100 would be incorrect.

Imlight is a server implementation whose behavior needs validation against retail data. Its missing-stat fallback can return zero, and some unsupported effects are ignored. The catalog importer should preserve unresolved definitions and mark those records incomplete.

### Proposed extraction workflow

1. **Snapshot one client revision.** Record executable and archive hashes plus decoder revision. Extract from a stable snapshot so a patch cannot mix incompatible schemas and data.
2. **Generate matching schemas and decode the template manifest.** Imlight's [CoreObjectFactory](https://github.com/Revive101/Imlight/blob/8c75ea02bbde4f9329ae6bae3ac91fc5fcb201b7/src/Imlight.CoreLib/Shared/Resources/CoreObjectFactory.cs) resolves TemplateManifest.xml IDs into archive paths. Some paths reference world-data archives, so follow the manifest across the relevant archives.
3. **Enumerate equipment templates.** Decode item subclasses, slot information, equip requirements, intrinsic effects and localized names. Investigate socket, pin and set definitions through the generated schemas. Distinguish wearable equipment from other item classes.
4. **Resolve and normalize bonuses.** Keep universal and school-specific contributions independent. Retain the original effect name, stat table, lookup index, raw value and unit conversion. Preserve unsupported effects and requirements rather than assuming zero bonuses or unrestricted eligibility.
5. **Import into the existing SQLite catalog.** Add a raw staging/history layer before normalized records so mappings can be corrected and changed templates reprocessed after patches. Keep player ownership and preferences separate.
6. **Validate a small sample, then expand.** Begin with five to ten known items covering universal/off-school bonuses, a jewel socket and an item card. Compare the decoded values with visible in-game descriptions before enumerating the entire catalog.

The first concrete milestone is one real item decoded end to end: template ID -> item definition -> resolved bonuses -> the existing JSON format -> SQLite -> optimizer. Several exact matches should precede a bulk import.

### Role of the running game and WizWalker

The inspected WizWalker inventory/equipment readers expose object lists and identifiers. They are candidate access paths for instance IDs, statistical template IDs, equipped slots and ownership counts. Instance-specific sockets, jewels, scaling and pet talents require additional verified decoding. Current-client offsets and bank/shared-bank access remain unverified.

Join each owned instance to its catalog template. Keep a stitched item's appearance ID separate from the template supplying its statistics; Imlight explicitly distinguishes them in [ItemHelper](https://github.com/Revive101/Imlight/blob/8c75ea02bbde4f9329ae6bae3ac91fc5fcb201b7/src/Imlight.CoreLib/Shared/Items/ItemHelper.cs). An owned pet's body template alone does not describe its individual talents and attributes.

Inventory enumeration does not establish the full catalog. Bulk archive decoding can discover definitions independently of which items the player owns or encounters.

The original read-only boundary still applies: standard WizWalker hook activation writes client memory. Live collection needs a verified access path consistent with that requirement. Offline Imcodec extraction can be investigated independently; no live collector is currently shipped.

### Coverage and completeness

Track these stages independently:

- Templates discovered.
- Equipment successfully decoded.
- Statistics fully resolved and validated.
- Items confirmed obtainable in the selected game revision.

Client resources can contain obsolete, test or NPC-only definitions. Decoding a template does not prove current availability or identify its drop sources. Imlight uses [SpiralDB](https://github.com/Revive101/spiraldb) for additional server-side world data; that is not proof of a complete retail item catalog.

See [the detailed catalog extraction plan](docs/CATALOG_EXTRACTION.md) for pinned source references, discovery commands, proposed storage additions and validation milestones. No Imlight server or network proxy is needed for the proposed catalog workflow.


## Architecture, validation and roadmap

DataReading extends existing equipment/stat models with typed schools, catalog, sets and CSV compatibility. JewelHandeling and PetHandeling extend the original classes. Optimization owns requests, validation, search domains, base lookup, raw calculations, exact search and results. GA/GeneticUtils/Individual/Fitness retain the genetic search structure with seeded operators and target scoring. Storage provides strict JSON/SQLite. Main is a CLI adapter; Optimizer.optimize works independently of UI/import method. Python is an optional adapter, never the optimization engine.

mvn test covers all-school/off-school aggregation, numeric fitness, locks across generations, eligibility/exclusions/inventory quantities, sockets/pins, sets, pets, missing/single pools, reproducibility, exhaustive comparison, CSV quoting, JSON/SQLite transactions and CLI round trips. See [audit](docs/AUDIT.md) and [deliverables/test results](docs/IMPLEMENTATION.md).

Unimplemented mechanics include damage curves/caps, rating conversions, Mighty/attribute-dependent pet formulas, arbitrary conditional/nonadditive effects, combat/PvP rules, dynamic level scaling and socket unlock state/cost. Cards are retained/displayed metadata. No comprehensive verified catalog, progression table or live importer is bundled.

Next priorities: verified base/gear data with provenance, offline template/stat-definition decoding and aggregate validation. Add verified versioned game formulas in StatCalculator when available.

A small desktop UI can wrap the same APIs: school/level/base editor; slot selectors with lock/exclude and positional socket controls; school/mode/mandatory/weight/scale target table; owned/all-known toggle; search controls/progress; ranked comparison tabs. Use a background worker and the same JSON profiles. No large UI framework is needed for this MVP.

Future four-person raid search can reuse catalog, constraints, raw calculations and results with a team request/objective layer. No combat simulator is included.
