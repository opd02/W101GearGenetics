# GearGenetics project map

Last reviewed: 2026-11-06. This describes the local working tree, including WAD, pin, pet, scaling, name-selection and portable runtime work. Current source and JSON take precedence over historical reports.

## Purpose and current state

Wizard101 gear optimization using a Java genetic algorithm, with exact enumeration for small search spaces. Players specify school/level, independent school stat targets, mandatory requirements, preferences, locks, exclusions, and optional owned inventory. The engine returns ranked alternatives and explains target deficits.

The Java optimizer works independently of the CLI and import tools. Offline game archives now feed a provisional catalog. Pins, predefined pets and optional automatic five-talent pets, including star jewels and attribute modifiers such as Mighty, are implemented. The active expanded example is a level-180 Storm-primary wizard using Death pins, evaluated at scaled combat level 170, now using AUTO mode with a 20-talent pool. All target values were retained and now mean post-scaling stats. The original mock and synthetic examples remain unscaled and Death-primary.

The main remaining gap is establishing gameplay accuracy and catalog coverage: archive decoding does not verify obtainability, displayed stats, formulas, or complete game mechanics. No desktop GUI, live game importer, combat simulator, or raid-team optimizer is implemented.

## Architecture and entry points

Paths below are relative to the repository root unless linked.

| Area | Main files | Responsibility |
| --- | --- | --- |
| CLI | `src/main/java/me/opd/Main.java` | Request creation, optimization, catalog import/export, saved profiles, inventory, listing, result display |
| Public engine | `src/main/java/me/opd/Optimization/Optimizer.java` | `optimize(catalog, request, progress)` dispatches GA or exact search |
| Request and legal domains | `Optimization/OptimizationRequest.java`, `WizardProfile.java`, `SearchSpace.java` under `src/main/java/me/opd/` | Validate targets and inputs; compile eligible gear/socket choices; enforce locks, inventory, and repair |
| Archive player-item screening | `src/main/java/me/opd/Optimization/PlayerItemFilter.java` | Reject internal/dev/stat-holder markers, unresolved names and unresolved eligibility in both pool modes, including direct engine runs; preserve raw records and report reasons; see [PLAYER_ITEM_FILTER.md](PLAYER_ITEM_FILTER.md) |
| Friendly gear selection | `src/main/java/me/opd/Optimization/GearSelector.java`, `GearNameResolver.java` | Exact names with optional level/slot; compile on a request copy to existing IDs; flag ambiguity; CLI `find` supports discovery |
| Search | `src/main/java/me/opd/GA/`, `GeneticUtils.java`, `Individual.java` | Seeded initialization, tournament selection, elitism, crossover, mutation, genome and socket choices |
| Scoring and results | `src/main/java/me/opd/Fitness.java`, `Optimization/FitnessEvaluator.java`, `FitnessBreakdown.java`, `OptimizationResult.java` | Rank feasibility, mandatory deficit, then soft objective; cache evaluations; deduplicate and compare alternatives |
| Raw calculation | `src/main/java/me/opd/Optimization/StatCalculator.java`, `DataReading/StatBlock.java` | Aggregate equipment, sockets, pets, set increments, and base stats; expose school-effective totals |
| Level scaling | `src/main/java/me/opd/Optimization/StatScaling.java`, `src/main/resources/me/opd/scaling/retail-20261031.json`, `bridge/build_scaling_table.py` | Versioned proportional caps; optional request-supplied ceilings retaining ratios; current-level eligibility and post-scaling scoring; expose raw and target views |
| Socket search improvement | `src/main/java/me/opd/Optimization/SocketOptimizer.java` | Refine half the initial population and each gear mutation against actual targets, so holders are assessed with useful pins/jewels; retain random exploration |
| CPU efficiency | `SearchSpace.java`, `DataReading/StatBlock.java`, `Fitness.java`, `SocketOptimizer.java` | Indexed legal-domain membership, cached stat keys, ranking-only socket scores and additive probes; preserve ordered search and exact arithmetic; see [CPU_PERFORMANCE.md](CPU_PERFORMANCE.md) |
| Catalog model | `src/main/java/me/opd/DataReading/` | Gear, schools, stat types, slots, catalogs, sets, legacy CSV migration |
| Sockets and pets | `src/main/java/me/opd/JewelHandeling/`, `PetHandeling/` | Shape/holder/age compatibility, talent duplication, pet attributes/effects, explicit rounding policy |
| Automatic pet planning | `src/main/java/me/opd/Optimization/PetSearchConfig.java`, `PetTalentResolver.java`, `AutoPetSearch.java` | Optional 5..20 named talent pool, manifested locks in both modes, lazy canonical pet holders and jewel domains, single-talent mutation/crossover, bounded exact enumeration |
| Persistence | `src/main/java/me/opd/Storage/` | Strict normalized JSON; transactional SQLite catalogs; separate request profiles and ownership |
| Offline archive decoder | `bridge/WadCatalog/Program.cs` | C# adapter using external Imcodec DLLs; manifest/template/resource capture with provenance |
| Catalog projection | `bridge/build_wad_catalog.py` | Stage raw captures, project stats/restrictions/sockets, retain mapping issues, export normalized catalog |
| Pet projection | `bridge/build_pet_catalog.py` | Resolve supported talent resources and star jewels; build explicit pet profiles; check capture revision consistency |
| Run pool preparation | `bridge/prepare_mock_catalog.py` | Filter provisional database by request; BROAD retains unresolved gear, FILTERED omits it; optionally merge pins/pets |
| Portable Storm/Death runtime | `Run-StormDeathPortable.ps1`, `runtime/storm-death/`, `bridge/build_portable_runtime.py` | Tracked executable and compressed prepared catalog; run a fresh checkout with Java 22+ and PowerShell, without extraction inputs; see [runtime guide](../runtime/README.md) |
| Other adapters | `bridge/normalize.py`, `bridge/wad_manifest.py` | Generic input normalization and the earlier optional WizWalker manifest prototype |

Paths abbreviated after a directory in the table refer to sibling files within that area. Existing package spellings `JewelHandeling` and `PetHandeling` are intentional references to the current tree.

## Data flow

```text
Installed Wizard101 WADs
  -> C# WadCatalog capture (decoded NDJSON, resources, hashes, coverage)
  -> build_wad_catalog.py
  -> staging.sqlite + normalized catalog.json
  -> Java CLI import-json
  -> optimizer.sqlite

Matching supplemental pet-resource capture + examples/pet-profiles.json
  -> build_pet_catalog.py + staging.sqlite
  -> pet-catalog.json

optimizer.sqlite + request + optional pet-catalog.json
  -> prepare_mock_catalog.py
  -> filtered catalog
  -> Java Optimizer -> SearchSpace -> GA or EXACT
  -> raw stat calculation -> target ranking -> result JSON
```

`examples/catalog.json` bypasses archive extraction and supplies a synthetic fixture. The original `GearDB.csv`, `JewelDB.csv`, and `PetTraitsDB.csv` remain preserved legacy inputs; migrated records are incomplete.

Raw captures, databases, development builds, caches and results live under ignored `target/` and are absent from a fresh checkout. A curated executable and compressed Storm-primary catalog are intentionally tracked under `runtime/storm-death/`; `Run-StormDeathPortable.ps1` uses these directly and reconstructs its disposable catalog cache. The catalog covers levels through 180, eight equipment slots plus PET, BROAD gear, pins and pets. The current request remains authoritative. Raw captures and full generated catalogs are too large to use as routine orientation documents.

## Run and validate

Java requires JDK 22+ and Maven 3.9+. Normal optimization needs neither Python nor a running game. Python 3.8+ is used for offline catalog tools and launchers; extraction additionally needs .NET 10 and compatible external Imcodec DLLs. The local decoder dependency path and reproduction details are in [WAD_CATALOG.md](WAD_CATALOG.md).

From the repository root:

```powershell
mvn test
mvn package
java -jar target/GearGenetics-1.0-SNAPSHOT.jar help
java -jar target/GearGenetics-1.0-SNAPSHOT.jar optimize --request examples/offschool-request.json --catalog examples/catalog.json --output target/results.json
python -m unittest discover -s bridge -p 'test_*.py'
```

| Workflow | Launcher and editable inputs | Default result |
| --- | --- | --- |
| Fresh-checkout Storm/Death | `Run-StormDeathPortable.ps1`, `examples/death-storm-request.json`, tracked `runtime/storm-death/` | `target/wizard101/storm-death-portable-results.json` |
| Eight-slot WAD demo | `Run-MockOptimizer.ps1`, `examples/wad-mock-request.json` | `target/wizard101/mock-results.json` |
| Expanded Death/Storm search | `Run-DeathStormOptimizer.ps1`, `examples/death-storm-request.json`, `examples/pet-profiles.json` | `target/wizard101/death-storm-expanded-results.json` |

For a fresh checkout, run `powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-StormDeathPortable.ps1` with Java 22+; it requires no build, Python, game installation or extraction databases. See [runtime prerequisites, coverage and refresh](../runtime/README.md). The database-backed development paths remain `Run-DeathStormOptimizer.ps1` and the mock launcher: these require an already-built JAR and existing extracted databases; the expanded launcher also needs matching pet resources. All launchers regenerate default outputs and support `-Output` and `-JavaPath`.

CLI exit 0 means the search completed; it can still have unmet mandatory targets. Inspect solution feasibility. Errors return 2. GA results do not establish global optimality or prove infeasibility.

Portable runtime validation on 2026-11-06: rebuilt executable; all 74 Java and 28 Python tests passed. A temporary checkout ran with only portable inputs and Java/Windows tools on PATH. The full unchanged 750 / 700 AUTO request returned five feasible alternatives, passed all 35 mandatory-target comparisons and reproduced the previous screened ranked builds, stats, fitness, warnings, 97,576 evaluations and all 701 history entries (unordered set serialization ignored). Missing/corrupt bundle inputs, coverage/Java validation, cache repair/reuse and launch-directory handling were checked. See [runtime details](../runtime/README.md#validation-on-2026-11-06). Raw extraction artifacts remain local and preserved.

The expanded launcher now defaults to `-GearPool BROAD` and prepares `target/wizard101/catalog.json` from the source `optimizer.sqlite`. It retains provisional gear with unresolved mappings, including the current AMULET lock, Howling Werewolf's Collar (`unsupported-stat-table:Mastery_Death`). School/current-level/slot and recognizable test filters remain; jewels/pins retain their existing mapping filters. Unknown effects/requirements/sets are not resolved or scored by broadening the pool. Use `-GearPool FILTERED` for the earlier behavior; the standalone mock launcher keeps that default. Historical results below used the earlier pool without the collar lock.

Java now applies [player-item screening](PLAYER_ITEM_FILTER.md) independently of both pool modes and `allowIncompleteItems`: internal/dev/monster/stat-holder markers, unlocalized records and unresolved eligibility cannot enter search domains. Named gear with unresolved effects, including the collar, can remain in BROAD. The catalog stays intact for name resolution and provenance; its prepared counts precede this screening. Result warnings summarize rejection reasons. Earlier broad results are historical: the 700-generation Death/Storm result selected an internal BiblioTransform stat hat and must not be used as evidence of an obtainable build. Rerun with the rebuilt JAR; passing screening still does not establish retail availability.

Screened reruns on 2026-11-05 returned five feasible alternatives each: Fire/Balance at population 750 / 250 generations and Death/Storm at 750 / 700. Their supplied catalogs had 6,380 / 6,381 rejected records respectively. Every mandatory target, objective and selected gear/jewel ID was audited; the Storm collar lock was retained and requests were unchanged. Corrected outputs use `target/wizard101/fire-balance-screened-results.json` and `death-storm-screened-results.json`; original normal outputs remain historical. All 68 Java and 25 Python tests passed, and the JAR was rebuilt. Full audits and limits are in [PLAYER_ITEM_FILTER.md](PLAYER_ITEM_FILTER.md).

The broad-pool collar run returned five feasible alternatives, all using the locked amulet; all seven mandatory targets were checked. All 25 Python tests passed and both launchers passed syntax checks. Results and details are in [DEATH_STORM_RUN.md](DEATH_STORM_RUN.md#broad-pool-with-required-collar-2026-11-05).

Validation observed during the 2026-11-04 review:

- Latest Python run: all 21 tests passed, including scaling-table units/validation and character-level pool eligibility.
- Existing packaged JAR, JDK 22, synthetic exact-search request: succeeded with four evaluated configurations and a feasible best result. Output: `target/project-map-exact-results-20261104.json`.
- Latest Java suite: 48 tests passed, including eight scaling tests and eight gear-name tests for resolution, ambiguous versions, normalized names, eligibility/ownership, exclusions, saved profiles, CLI discovery/optimization and interactive entry. The JAR was then packaged successfully. Maven is not on the shell PATH, but IntelliJ's bundled Maven works: `C:\Program Files\JetBrains\IntelliJ IDEA 2022.3.2\plugins\maven\lib\maven3\bin\mvn.cmd`. Set `JAVA_HOME` to JDK 22 before using it.
- No live retail verification or new archive extraction was performed during this review.

CPU tuning on 2026-11-05: all 51 Java and 21 Python tests passed and the JAR was rebuilt. Differential tests compare the previous scoring/socket implementations with the tuned paths. The full current Death/Storm seeded run returned the same ranked builds, stats, scores, evaluation count and generation history. Profiling and benchmark details are in [CPU_PERFORMANCE.md](CPU_PERFORMANCE.md).

Automatic pet planning on 2026-11-05: all 61 Java and 22 Python tests passed. The launcher smoke run retained named talent locks. At the current population 500 / generations 280 settings, full PREDEFINED runs retained the previous JAR's results and took about 65 seconds; AUTO took 67.5 seconds and returned five feasible alternatives. An independent audit recomputed pet bonuses, raw totals, scaling and all targets. Inputs and results are retained under `target/auto-pets-20261105/`; details and switching instructions are in [AUTO_PETS.md](AUTO_PETS.md).

Request-supplied scaled ceilings on 2026-11-05: optional `wizard.scaledCapOverrides` retains bundled ratios but replaces selected saturation ceilings, with explicit result metadata/warnings. Omitted/empty settings preserve prior behavior and saved-result compatibility. The Death/Ice request uses user-reported 235 Death / 243 Ice ceilings and user-approved 220 / 228 mandatory minimums, preserving 45/45 pierce, 20 Ice / 15 Death accuracy, 100 pips and the user's Collar of the Betrayed lock. The GA-only run remained 2.264286 Ice damage short; targeted socket refinement produced four model-feasible alternatives at 222.466667 Death / 233.43125 Ice. Use `target/wizard101/death-ice-refined-results.json`; its method is explicitly saved-candidate socket refinement. All 74 Java and 25 Python tests passed and the JAR was rebuilt. The bundled table is unchanged, and this calibration is not verified retail scaling. See [DEATH_ICE_RUN.md](DEATH_ICE_RUN.md) and [LEVEL_SCALING.md](LEVEL_SCALING.md#request-supplied-post-scaling-ceilings).

## Current Death/Storm example

[death-storm-request.json](../examples/death-storm-request.json) configures a STORM-primary wizard at `level: 180`, `scaledLevel: 170` and requires post-scaling Death damage 340, Storm damage 107, Death/Storm pierce 54/36, Storm/Death accuracy 30/15, and power-pip chance 100. Its post-scaling soft objective combines `HEALTH:UNIVERSAL MAXIMIZE` (weight 1, scale 1000) with eight `MAXIMIZE` preferences for Death and Storm resistance, accuracy, damage and pierce (each weight 1, scale 100). Mandatory requirements rank first, then the combined soft score. No excess penalties or user upper requirements were added; scaling applies caps. Power-pip chance remains minimum-only. It selects eight equipment slots plus PET, locks Howling Werewolf's Collar in AMULET, and has no owned-only restriction or mount. Base power-pip chance is still mocked as 40; natural base health is omitted and should be supplied at character level 180 for accurate health saturation/tradeoffs.

The previous unscaled level-170 combined-objective result had 7,634 modeled health and score 15.3626. It is preserved as `target/wizard101/storm-primary-unscaled-170-combined-results.json`, with its matching archived catalog. Standard launcher outputs now use level-180 eligibility with level-170 post-scaling scoring. Full results are in [DEATH_STORM_RUN.md](DEATH_STORM_RUN.md).

An earlier filtered 180 -> 170 run returned five candidates meeting all seven mandatory requirements after scaling. The first uses five level-180 items and has post-scaling Death/Storm damage 341.838275/163.128409, pierce 56.56625 each, accuracy 21/45, power-pip chance 100 and modeled health 7,373.434489. The 21,966-record gear pool includes 155 level-180 entries. An independent audit verified all 16 unchanged targets, projections and objective against the original decoded cap rows. Raw and post-scaling totals are both available in the result JSON. Later broad-pool results require the player-item screening described above; historical feasibility does not certify an obtainable build.

The request currently locks Howling Werewolf's Collar in AMULET and excludes `Arcane Smoothie`, `Sacred Yarn Staff` and `Gorgonsbane Xiphos` by name. Locks accept strings or `{name,level}` objects keyed by slot; exclusions accept strings or `{name,level,slot}` and remove all matching versions. Matching ignores case, whitespace and apostrophe style; ambiguous/unknown locks are errors. Existing ID rules remain compatible. The `find` command searches partial names in a catalog or SQLite database. See [GEAR_SELECTION.md](GEAR_SELECTION.md).

The packaged name-selection demonstration locked the level-180 Headmistress' Dynamic Cap and excluded all 18 Arcane Smoothie versions, returning three feasible post-scaling candidates over 60 generations. Artifacts are at `target/named-gear-demo-*`, separate from the main trial. Saved requests retained names instead of compiled IDs.

The earlier health-only objective run reached 6,659.8 modeled health and is preserved at `target/wizard101/storm-primary-health-only-results.json`. Combined preferences can trade health for other stats; the current run happened to improve health too because the heuristic search followed a different trajectory.

Before the health preference, a feasible Storm-primary result selected six Death pins but had only 3,718 modeled health and left health sockets empty because health was not scored. That result is preserved as `target/wizard101/storm-primary-without-health-objective-results.json`. Adding a `HEALTH:UNIVERSAL` base override changes displayed totals but not health-based ordering when that base is constant across builds.

The earlier Death-primary result (142.625 Death damage) used the wrong primary school and is preserved as `target/wizard101/death-primary-expanded-*`. Correcting the primary alone, before socket refinement, produced 183.675 Death damage; that result is saved as `target/wizard101/storm-primary-random-sockets-results.json`. Historical results and the old eight-slot bound do not establish feasibility or bounds for the intended expanded Storm-primary pool.

## Invariants and unresolved work

- Keep universal and school-specific bonuses distinct. Add universal contributions in effective views; do not flatten them into a single primary-school stat.
- Keep equipment and base/character totals separate. Base values require exact school/level data or explicit overrides; omitted override values use the exact source or zero.
- Preserve socket positions, including empty sockets. Enforce holder shape, school/level eligibility, locks, and owned jewel counts through the existing domains.
- Pet configurations have up to five manifested talents plus a separate jewel. Attribute modifiers affect all supported talent calculations. Current modifier support requires fully trained profiles. Rounding is explicit and remains unverified against retail behavior. Hypothetical pets are excluded in owned-only mode.
- `petSearch` defaults to PREDEFINED when omitted. The Death/Storm example explicitly selects AUTO and includes 20 named choices; switch only `petSearch.mode` to PREDEFINED to use its seven existing profiles. `lockedTalents` applies to manifested talents in both modes. AUTO requires a named fully trained Mega/Ultra template and standalone catalog `petTalents`, generates exactly five unique talents, and rejects owned-only mode. Generated holders encode canonical talent IDs for cache keys and diversity; catalog records are not mutated. See [AUTO_PETS.md](AUTO_PETS.md).
- All archive-derived records remain provisional (`complete=false`). Explicit opt-in permits them for demos; no independently verified complete retail catalog is bundled. The original extraction report lists 60,646 gear and 4,416 jewel/pin projections, with zero independently verified complete records.
- The engine supports generic additive set thresholds, but extracted set references have not been resolved into usable thresholds for the current WAD workflow. Mounts remain excluded from the expanded example.
- Additional damage/resistance curves, rating conversions, conditional effects, combat/PvP legality, and socket unlock state/cost remain unresolved. Item cards are metadata rather than simulated combat behavior.
- Opt-in level scaling is implemented with `wizard.scaledLevel`; omitting it preserves raw behavior. The versioned proportional model and local `LevelScaledData.xml` records are documented in [LEVEL_SCALING.md](LEVEL_SCALING.md). Scoring and socket refinement use post-scaling totals, while equipment eligibility and base lookup use `wizard.level`. Raw totals remain separate. Rounding, floor-field semantics and actual raid runtime still need validation. Scaled school-specific targets require explicit schools; unsupported stats/bands and mismatched WAD client revisions fail clearly.
- No verified school/level base progression table is bundled. Test-name filtering does not establish retail availability.
- Preserve source data and revision hashes. Supplemental pet resources and staged gear must match the client revision. SQLite imports upsert; they do not reconcile stale records automatically.

Next useful work: independently check representative gear, pins, pets, modifiers, and star jewels against observed game values; replace mocked base values; resolve relevant extracted sets and mounts; then assess search coverage/convergence while retaining the requested targets.

## Documentation guide

| Document | Best use |
| --- | --- |
| [README.md](../README.md) | Overall setup, engine features, CLI usage |
| [PINS_AND_PETS.md](PINS_AND_PETS.md) | Current pet profiles, pins, jewels, formulas, capture checks, validation boundaries |
| [AUTO_PETS.md](AUTO_PETS.md) | Automatic versus predefined mode, the 20-talent pool, named locks, template attributes, star jewels and search behavior |
| [DEATH_STORM_RUN.md](DEATH_STORM_RUN.md) | Active target build and historical search results |
| [DEATH_ICE_RUN.md](DEATH_ICE_RUN.md) | Death-primary 180 -> 170 request, user-supplied 235/243 damage ceilings with 220/228 minimums, 45/45 pierce and run history |
| [MOCK_RUN.md](MOCK_RUN.md) | Original eight-slot mock workflow and base-stat assumptions |
| [WAD_CATALOG.md](WAD_CATALOG.md) | Implemented extraction, provenance, counts, staging schema, reproduction |
| [CATALOG_REFRESH.md](CATALOG_REFRESH.md), [portable runtime](../runtime/README.md) | Database provenance, patch refresh, and running a fresh checkout without extraction tools |
| [IMPORT_FORMAT.md](IMPORT_FORMAT.md) | Normalized JSON/storage contract |
| [GEAR_SELECTION.md](GEAR_SELECTION.md) | Name-based locks/exclusions, version qualifiers, discovery and profile behavior |
| [PLAYER_ITEM_FILTER.md](PLAYER_ITEM_FILTER.md) | Archive screening rules, BROAD/FILTERED distinction, contaminated historical runs and validation |
| [CPU_PERFORMANCE.md](CPU_PERFORMANCE.md) | CPU tuning, exact behavior comparisons, benchmark setup and local artifacts |
| [LEVEL_SCALING.md](LEVEL_SCALING.md) | Public scaling rule, extracted Storm-primary 180/170 caps, projections, provenance and remaining validation |
| [CATALOG_EXTRACTION.md](CATALOG_EXTRACTION.md), [WIZWALKER.md](WIZWALKER.md) | Earlier extraction research and live-import limitations |
| [AUDIT.md](AUDIT.md), [IMPLEMENTATION.md](IMPLEMENTATION.md) | Historical architecture decisions and initial implementation validation |
| [prompt.txt](../prompt.txt) | Original requested scope and long-term goals |

Some historical reports predate real extraction and dynamic pet support. In particular, `IMPLEMENTATION.md` still lists WAD decoding and Mighty/pet formulas as unimplemented. Consult current source and the newer WAD/pin/pet reports before relying on those statements. Formula implementation still does not imply retail validation.

## Recall and Obsidian

This Markdown file is the shared project map, with [AGENTS.md](../AGENTS.md) as the agent entry point. Keep it concise and update it when workflows, architecture, or verification status materially change. Keep detailed contracts and run history in their linked documents.

Obsidian can view these same local Markdown notes; it need not maintain another copy of the project map. An Obsidian vault is a local folder of Markdown files ([official storage documentation](https://obsidian.md/help/data-storage)). Codex discovers project `AGENTS.md` guidance at startup ([official guidance](https://learn.chatgpt.com/docs/agent-configuration/agents-md)). This arrangement provides a file to reread across sessions; an Obsidian graph alone does not supply persistent model recall. No Obsidian vault, plugin, or application configuration was changed during this review.
