# W101GearGenetics

A local Java gear optimizer supporting all seven schools independently, off-school targets, mandatory requirements, health/stat preferences, named gear locks/exclusions, sockets/pins, predefined or automatic pets with talent locks, optional level scaling, owned inventory, seeded genetic search and ranked alternatives. The existing Maven project and equipment classes have been evolved in place.

For a new development session, start with [AGENTS.md](AGENTS.md) and the [project map](docs/PROJECT_MAP.md). The map identifies the current architecture and feature documents; current source and request files take precedence over historical reports. Use [Create a new target gearset](#create-a-new-target-gearset) for new request files and [Feature development and verification](#feature-development-and-verification) for implementation work.

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

## Create a new target gearset

Each target gearset is a reusable request JSON file. Create a new file rather than changing an existing preset you want to keep. For a WAD-backed build with pins and pets, start from the current expanded example:

```powershell
Copy-Item -LiteralPath examples/death-storm-request.json -Destination examples/my-raid-request.json
```

Choose a descriptive filename for the actual build. The [off-school synthetic example](examples/offschool-request.json) and [eight-slot WAD mock](examples/wad-mock-request.json) are other starting points, with different assumptions and included slots.

Edit the new file, reviewing these fields together:

| Field | What to set |
| --- | --- |
| `wizard.school` | The wizard's primary school, which controls gear and pin eligibility; bonus targets can belong to other schools. |
| `wizard.level` | Current character level for equipment eligibility and base stats. |
| `wizard.scaledLevel` | Optional combat level; omit it for raw stats. When present, enter the desired **post-scaling** target values. |
| `wizard.baseOverrides` | Verified current-level base stats, or explicit modeled values. The Death/Storm example supplies only mocked 40 base power-pip chance and no natural base health. |
| `requiredSlots` | The slots to search. The expanded example includes eight equipment slots and PET, without MOUNT. |
| `targets` | Replace the copied array with the desired hard requirements and soft preferences. See the example below. |
| `lockedGear`, `excludedGear`, `lockedJewels` | Keep only restrictions intended for this build. The current Death/Storm exclusions are specific to that request. Gear selectors accept names; socket locks use jewel IDs. |
| `petSearch` | AUTO searches five manifested talents from 5..20 named choices using fixed template attributes; PREDEFINED selects whole configured pets. Review the template, pool and `lockedTalents` for this build. |
| `wizard.ownedOnly` and inventory | Use PREDEFINED for actual owned pets; AUTO generates hypothetical pets. |
| `allowIncompleteItems` | The current WAD examples explicitly allow provisional archive records. Preserve or change this deliberately. |
| `search`, `topK`, `minDifferentSlots` | Seed, search effort, requested alternatives and diversity. These control searching/reporting rather than target values. |

For example, these target entries require 340 Death damage and reward additional health without a health threshold:

```json
"targets": [
  {"stat":"DAMAGE","school":"DEATH","mode":"MINIMUM","value":340,"mandatory":true,"weight":1,"scale":340},
  {"stat":"HEALTH","school":"UNIVERSAL","mode":"MAXIMIZE","mandatory":false,"weight":1,"scale":1000}
]
```

Use the requested numbers and schools for the new build. Global stats use `UNIVERSAL`; scaled school-specific targets require an explicit school. Additional nonmandatory `MAXIMIZE` entries reward extra damage, pierce, resistance or accuracy. Weights/scales determine their tradeoffs; no excess penalty is required. See [target modes](#desired-stats), [gear locks/exclusions](#lock-or-exclude-gear-by-name), [pet modes and talent locks](docs/AUTO_PETS.md), and [scaling](docs/LEVEL_SCALING.md).

Clear inherited locks/exclusions or replace them intentionally. In AUTO mode, changing the template changes the fixed body/attributes/rounding while its original manifested talents are replaced. Adapt the 20-talent Death/Storm pool for other schools or goals. In PREDEFINED mode, edit [pet-profiles.json](examples/pet-profiles.json) to add whole pet configurations. `lockedTalents` applies in both modes.

Run the new request with a distinct result filename:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-DeathStormOptimizer.ps1 -Request examples/my-raid-request.json -Output target/wizard101/my-raid-results.json
```

The launcher prepares the gear/pin/pet catalog for that request's school, current level and slots. A filtered catalog from a different request may omit eligible items, so regenerate it for the new request. `-Output` keeps results separate; shared generated catalog/pet files are still replaced. Run these launchers sequentially. For the no-pet mock workflow, use `Run-MockOptimizer.ps1` instead; see its section below for assumptions.

Inspect `feasible` and the per-target `actual` values/deficits in the result. With scaling enabled, those values are post-scaling and raw totals are also retained. Exit 0 means the search completed, including when requirements remain unmet. A GA result is a candidate, not a proof of optimality or impossibility. Save the request and matching result together when comparing future runs. Requests use strict JSON, without comments or unrecognized fields; explain assumptions in accompanying Markdown.

## Run the mock optimizer with extracted WAD data

Open PowerShell in the project directory and run:

```powershell
cd 'C:\Users\Ran The Master\IdeaProjects\GearGenetics'
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-MockOptimizer.ps1
```

The launcher requires Python 3.8+, JDK 22+, the built JAR and the locally extracted database at `target/wizard101/optimizer.sqlite`. The game can stay closed. If the JAR is missing, build it with `mvn package`; if the database is missing, follow [the WAD extraction/import instructions](docs/WAD_CATALOG.md). Generated files under `target/` are not included in a fresh checkout.

The preset uses a **level-170 Death wizard** with these mandatory minimums:

| Stat | Minimum |
| --- | ---: |
| Death damage | 200% |
| Universal resistance | 35% |
| Death accuracy | 20% |
| Power-pip chance | 100% |
| Health | 8,000 |

It prefers at least **30% Death pierce**, then rewards additional Death damage and health. Base stats are mocked as **4,000 health and 40% power-pip chance**. Pets and mounts are omitted. The WAD records are provisional and explicitly enabled for this mock run.

The pool includes eligible gear from **levels 1 through 170**, with recognizable test/polymorph records and unresolved mappings filtered out. No 150-170 lower-level cutoff is applied. The tested run took **12.4 seconds on this machine**; runtime can vary.

The console prints generation progress, five ranked alternatives and target deficits. Results are saved to [target/wizard101/mock-results.json](target/wizard101/mock-results.json), replacing the previous result at that path. A completed run can still have unmet requirements: the trial reached **195/200 Death damage** while meeting the other mandatory targets. Check `feasible` and the per-target results.

To change the targets, school, level, mocked base stats or search settings, edit [examples/wad-mock-request.json](examples/wad-mock-request.json) and rerun the command. Increase `search.generations` or `search.populationSize` for a longer search; genetic results are heuristic.

To use another request or save results separately:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-MockOptimizer.ps1 -Request examples/wad-mock-request.json -Output target/wizard101/my-results.json
```

See [mock run details](docs/MOCK_RUN.md) for candidate filters and modeling limits.

## Death / Storm targets with pins and pets

The **level-180 Storm-primary wizard using Death pins, scaled to level 170** request includes 340 Death damage, 107 Storm damage, 54/36 Death/Storm pierce, 30/15 Storm/Death accuracy and 100 power-pip chance as mandatory **post-scaling** minimums. The wizard's primary school and current level control gear and pin eligibility; target schools and combat level do not lower equipment eligibility.

Set the two levels in [the request](examples/death-storm-request.json):

```json
"wizard": {
  "school": "STORM",
  "level": 180,
  "scaledLevel": 170,
  "baseOverrides": {"values": {"POWER_PIP_CHANCE:UNIVERSAL": 40}},
  "ownedOnly": false
}
```

Keep every target value as the stat you want **inside** the level-170 content. The optimizer evaluates the complete build after scaling, including socket and pet choices. For example, 54 raw pierce at level 180 scales to 53.1, so it fails a mandatory 54 post-scaling target. Omit `scaledLevel` for an unscaled request. The interactive `init` command also prompts for both levels. The bundled cap table requires no game installation during normal optimization. See [scaling details](docs/LEVEL_SCALING.md).

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-DeathStormOptimizer.ps1
```

This uses the existing local WAD databases plus a supplemental talent-resource capture, rebuilds seven explicit hypothetical pet configurations and supported talent definitions, includes eligible pins and star jewels, and writes `target/wizard101/death-storm-expanded-results.json`. The request now uses `petSearch.mode: "AUTO"` to search five-talent combinations from an editable 20-talent Death/Storm pool. Change that value to `"PREDEFINED"` to select among the seven existing [pet profiles](examples/pet-profiles.json). Use `petSearch.lockedTalents: ["Mighty", "Death-Dealer"]` to require manifested talents by name in either mode. See [switching modes, pool editing and talent locks](docs/AUTO_PETS.md). Targets and all other search settings remain in [the request](examples/death-storm-request.json). Mounts and unresolved sets remain excluded; base power-pip chance is still mocked. Pet formula/rounding and retail availability need independent verification. See [setup, rules and validation](docs/PINS_AND_PETS.md) and [historical results](docs/DEATH_STORM_RUN.md).

The earlier health-only objective run met all seven requirements and reached 6,659.8 modeled health, with six Death Punishing pins, two +155 tear health jewels and two +525 square health jewels. It is preserved at `target/wizard101/storm-primary-health-only-results.json`. The current request also rewards extra stats as described below, so its best build can trade some health for those bonuses. See [the run report](docs/DEATH_STORM_RUN.md) for results. Target-based socket refinement helps the GA evaluate new armor with useful compatible pins/jewels instead of relying only on random socket combinations.

The current request **maximizes post-scaling health plus Death and Storm resistance, accuracy, damage and pierce after mandatory requirements**. The eight school/stat preferences use weight 1 and scale 100; health uses weight 1 and scale 1000. Each additional post-scaling percentage point in one school/stat scores the same as 10 post-scaling health. Universal bonuses contribute to both school views before scaling. Power-pip chance remains a minimum only. No excess penalties or user-specified upper requirements were added; the scaling model enforces its source/destination caps, so bonuses beyond saturation stop earning rewards.

The soft scores are added together, allowing tradeoffs among these preferences; mandatory requirements always rank first. Eligible health jewels improve a build when they fill compatible sockets without losing more valuable bonuses. Health has no requested minimum or upper target; optional level scaling still applies its health caps. The search remains heuristic rather than a guarantee of a global maximum.

The earlier unscaled level-170 combined-objective run met all seven requirements with 7,634 modeled health and is preserved at `target/wizard101/storm-primary-unscaled-170-combined-results.json`. The current launcher evaluates level-180 gear at scaled level 170. See [the run report](docs/DEATH_STORM_RUN.md) for current and historical results.

To give a new request the same health preference, add this object to its `targets` array:

```json
{"stat":"HEALTH","school":"UNIVERSAL","mode":"MAXIMIZE","mandatory":false,"weight":1,"scale":1000}
```

No `value` is needed for `MAXIMIZE`. `scale: 1000` expresses the soft score in thousands of health; it is not a health goal or limit. Adjust weights/scales in the request to change the tradeoffs with other soft objectives. Supply natural base stats at the **current character level**, before scaling. The current preset supplies only a mocked base power-pip chance, so health omits natural base health until a verified `HEALTH:UNIVERSAL` override is supplied. Base health is included before cap/scaling calculations and can affect saturation and tradeoffs.

## Lock or exclude gear by name

Edit `lockedGear` and `excludedGear` in the request:

```json
"lockedGear": {
  "HAT": "Headmistress' Dynamic Cap",
  "ROBE": {"name": "Shocking Abomination Suit", "level": 180}
},
"excludedGear": ["Arcane Smoothie"]
```

Names match exactly, ignoring case, extra whitespace and straight/curly apostrophe differences. A lock must identify one eligible record; if a name has multiple versions, add an item `level`. A plain-name exclusion removes every version with that name. Unknown names and conflicting locks/exclusions are reported as errors. The current Death/Storm preset has no gear locks and excludes `Arcane Smoothie`, `Sacred Yarn Staff` and `Gorgonsbane Xiphos`; review these when copying it for a different build.

Use the new `find` command to search partial names and see available versions:

```powershell
java -jar target/GearGenetics-1.0-SNAPSHOT.jar find --catalog target/wizard101/death-storm-expanded-catalog.json --name "Abomination" --school STORM --level 180
```

Existing ID fields remain compatible. See [name selection details](docs/GEAR_SELECTION.md) for scoped exclusions, ambiguous records, interactive entry and saved profiles.

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
- MAXIMIZE: reward the evaluated total divided by scale, using post-scaling totals when enabled.
- IGNORE: no influence.

Distances are divided by a positive scale and multiplied by a positive weight. Choose scales meaningful to units, e.g. 1,000 health versus 100 damage. Ranking compares **feasibility, smaller mandatory deficit, then larger soft objective**, in order. Soft score cannot make an infeasible build beat a feasible one. The output exposes this tuple and per-target components.

Damage, resistance, accuracy, piercing, critical/block ratings, pip-conversion rating and flat damage/resistance are school-aware. Global stats use UNIVERSAL. Percent values use percentage points: 100 means 100%. Shadow rating and chance remain different types. Raw item bonuses are retained separately from character totals. Optional level scaling uses the documented versioned cap model; additional combat curves and rating conversions are not applied.

Wizard profile fields include school, level, optional baseOverrides, ownedOnly, ownedItemIds and ownedQuantities. Base stats come from exact SCHOOL:level catalog entries. Manual overrides replace named source values. Without a source, manual entries are used and unspecified values are zero. Missing source plus no override is an error. An explicit empty values object intentionally supplies zero base stats. No real progression table is bundled.

Request JSON is reusable directly; `optimize --save-request copy.json` saves the input request, retaining friendly gear and talent selections.

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

IDs are stable catalog identifiers, not names. Socket indices start at zero. Locking gear fixes its identity; lock sockets separately to preserve jewels/pins. In default `PREDEFINED` mode, pets carry their resolved manifested talents and are selected as whole entities. Optional `AUTO` mode generates five-talent combinations using a named template; manifested talents can be locked by name in either mode. See [automatic pet search](docs/AUTO_PETS.md). Hypothetical pets cannot enter owned-only runs.

All ten slots are required by default. To intentionally optimize a partial set, list only those slots under requiredSlots. Omitted slots contribute nothing; such a result is not a complete character build.

ownedItemIds provides one copy per ID. ownedQuantities overrides counts, including zero. Locks reserve jewel copies before other sockets, and available quantities cannot be exceeded. Use distinct IDs for different owned pet/configuration variants.

Locks and restrictions are enforced in candidate generation, initialization, crossover, mutation, repair, exact search and final validation. Conflicts, ineligible locked items, empty selected pools and incompatible sockets produce errors.

## Search and alternatives

The search object configures seed, populationSize, generations, mutationRate, crossoverRate, tournamentSize, elites, method (GA or EXACT) and exactLimit. Defaults preserve the prior local population 250, mutation 0.5 and tournament 7, adding elitism.

The same request/catalog/seed reproduces results. Candidate domains are prefiltered once; full equipment/socket signatures cache evaluations. Progress and best-ever history report feasibility, deficit and objective. Pet selection has no unbounded loops.

topK defaults to 10. Equal stat/card/set outcomes are collapsed; minDifferentSlots controls minimum gear/socket differences between alternatives. Fewer results may be returned. Diversity filtering retains the best-ranked build. No unsafe dominance pruning discards candidates needed for maximum targets or set bonuses.

Exact search includes compatible socket choices and empty sockets. It refuses a Cartesian upper bound above exactLimit before evaluating builds. AUTO first materializes its bounded pet combinations for that check; use small pools for exact runs. Ownership may reduce the actual count.

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

The offline WAD catalog pipeline has now captured **80,162 real item templates**, projecting **60,646 gear records and 4,416 jewels/pins** into the existing Java catalog format and SQLite. All remain `complete=false` pending item-card and mechanics verification. See [the implemented workflow, output locations and coverage](docs/WAD_CATALOG.md). The older [WizWalker manifest prototype](docs/WIZWALKER.md) only lists archive members.

## Building a complete item catalog with Imlight, Imcodec and WizWalker

The proposed catalog pipeline uses two separate data feeds:

| Feed | Source | Purpose |
| --- | --- | --- |
| Item catalog | Installed game archives decoded with Imcodec | Discover item templates, restrictions and bonuses |
| Owned inventory | Running client through a verified WizWalker reader | Identify the player's equipment, jewels and individual pets |

[Imlight](https://github.com/Revive101/Imlight) and its [Imcodec dependency](https://github.com/Jooty/Imcodec) supplied the bulk extraction path. Earlier research covered Imlight revision 8c75ea02bbde4f9329ae6bae3ac91fc5fcb201b7 and Imcodec revision 4f37063167dd50c49f30d22bc4ccb3e8f8aa0632. **An initial offline importer is now tested against this installation using the earlier working PersonalServer decoder. A fully verified, obtainable retail catalog remains unfinished.** See [current implementation and actual decoder provenance](docs/WAD_CATALOG.md).

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

Real templates now resolve through item definitions and stat tables into the existing JSON/SQLite format. The bulk import remains a provisional staging catalog; retail item-card comparisons must precede promoting records to complete and using them for verified optimization.

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


## Feature development and verification

Read [AGENTS.md](AGENTS.md), the [project map](docs/PROJECT_MAP.md), the active request and the relevant source before changing behavior. The current working tree includes substantial uncommitted implementation; preserve it. Historical architecture reports can predate working features.

The map links the modules for request validation, search/operators, scoring/scaling, pet planning, import and persistence. When adding a request/catalog field, cover its default behavior, strict JSON, saved profiles, SQLite, relevant Python builders/launchers and user documentation. Keep existing catalog records immutable during search. Exercise the feature with locks, socket positions/quantities, school scope and scaling where relevant. For optional features, existing requests should retain their behavior when the feature is disabled or omitted.

For performance-only changes, compare seeded builds, exact numeric scores, evaluation counts and history; retain RNG calls, candidate order and floating-point arithmetic. The existing CPU optimizations and reference comparisons are documented in [CPU_PERFORMANCE.md](docs/CPU_PERFORMANCE.md). [AUTO_PETS.md](docs/AUTO_PETS.md) records the pet-mode tests and full-run comparisons.

Run the checks appropriate to the change and rebuild the JAR after code changes:

```powershell
mvn test
mvn package
python -m unittest discover -s bridge -p 'test_*.py'
```

Maven is not currently on this machine's PATH. This local fallback works with the cached dependencies:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
$projectMaven = 'C:\Program Files\JetBrains\IntelliJ IDEA 2022.3.2\plugins\maven\lib\maven3\bin\mvn.cmd'
& $projectMaven -o -B -ntp test
& $projectMaven -o -B -ntp package
```

Omit `-o` when dependencies need downloading. Last verified on 2026-11-05: **61 Java tests and 22 Python tests passed**. These are recorded results, not a substitute for checking a new implementation. Documentation-only edits do not require rebuilding Java. Update this README and the relevant feature document after changing workflows, and update [PROJECT_MAP.md](docs/PROJECT_MAP.md) after significant architecture/workflow changes.

Generated assets under ignored `target/` are local: a fresh checkout does not contain WAD captures, databases, prepared catalogs, JARs or saved runs. Ordinary new-target work can reuse existing source databases/captures and regenerate the request-specific pool; it does not require new archive extraction. Preserve source CSVs, archives and matching capture hashes. Follow [WAD_CATALOG.md](docs/WAD_CATALOG.md) when rebuilding those inputs is actually needed.

## Architecture, validation and roadmap

DataReading extends existing equipment/stat models with typed schools, catalog, sets and CSV compatibility. JewelHandeling and PetHandeling extend the original classes. Optimization owns requests, validation, search domains, base lookup, raw calculations, exact search and results. GA/GeneticUtils/Individual/Fitness retain the genetic search structure with seeded operators and target scoring. Storage provides strict JSON/SQLite. Main is a CLI adapter; Optimizer.optimize works independently of UI/import method. Python is an optional adapter, never the optimization engine.

mvn test covers all-school/off-school aggregation, numeric fitness, locks across generations, eligibility/exclusions/inventory quantities, sockets/pins, sets, pets, missing/single pools, reproducibility, exhaustive comparison, CSV quoting, JSON/SQLite transactions and CLI round trips. See [audit](docs/AUDIT.md) and [deliverables/test results](docs/IMPLEMENTATION.md).

Optional proportional level scaling and cap-aware target scoring are implemented through `wizard.scaledLevel`. Additional damage/resistance curves, rating conversions, arbitrary conditional/nonadditive effects, combat/PvP rules and socket unlock state/cost remain unimplemented; scaling rounding/floor behavior still needs retail verification. Pet attribute effects and Mighty are supported for explicit fully trained profiles through a provisional archive-derived calculation model; retail formula/rounding validation remains outstanding. Cards are retained/displayed metadata. No comprehensive verified catalog, progression table or live importer is bundled.

Next priorities: validate the extracted gear against retail item cards, resolve remaining effects/requirements/sets/pets and availability, and supply verified base stats. Add verified versioned game formulas in StatCalculator when available.

A small desktop UI can wrap the same APIs: school/level/base editor; slot selectors with lock/exclude and positional socket controls; school/mode/mandatory/weight/scale target table; owned/all-known toggle; search controls/progress; ranked comparison tabs. Use a background worker and the same JSON profiles. No large UI framework is needed for this MVP.

Future four-person raid search can reuse catalog, constraints, raw calculations and results with a team request/objective layer. No combat simulator is included.
