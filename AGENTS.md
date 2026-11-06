# GearGenetics working guide

## Start each session

1. Read [docs/PROJECT_MAP.md](docs/PROJECT_MAP.md) for architecture, workflows, limitations and document links, then inspect the current request and relevant source. Current source and JSON take precedence over historical run reports.
2. Check `git status --short`. Preserve existing implementation and documentation, including any untracked source files; do not assume the Git commit represents the current application.
3. For a new target gearset, follow [README: Create a new target gearset](README.md#create-a-new-target-gearset). For feature work, use [README: Feature development and verification](README.md#feature-development-and-verification).

## Current capabilities and starting points

- Java 22 optimizer with seeded GA and bounded exact search; `Optimizer.optimize` is the public engine and `Main` is the CLI. Python prepares/imports catalogs rather than running the optimizer.
- Independent school targets, mandatory requirements, soft objectives, positional jewels/pins, health maximization, sets, named gear locks/exclusions, owned inventory and ranked alternatives are implemented.
- Optional `wizard.scaledLevel` evaluates targets after scaling the complete build. `wizard.level` still controls eligibility and base-stat lookup. See [LEVEL_SCALING.md](docs/LEVEL_SCALING.md).
- Pets support `PREDEFINED` (whole existing pets, the default when `petSearch` is omitted) and `AUTO` (five distinct manifested talents from 5..20 supported definitions). Both support named `lockedTalents`; star jewels are separate. AUTO uses fixed template attributes and hypothetical pets, and rejects owned-only mode. See [AUTO_PETS.md](docs/AUTO_PETS.md).
- The active [Death/Storm request](examples/death-storm-request.json) is **STORM primary**, current level 180, scaled level 170, with Death pins and an AUTO pool of 20 talents. Its filename does not imply Death primary. Read the file for current targets, exclusions and search settings; do not restore older values from reports.
- [examples/pet-profiles.json](examples/pet-profiles.json) supplies seven predefined configurations and the AUTO template's body, attributes and rounding. [PINS_AND_PETS.md](docs/PINS_AND_PETS.md) documents the offline resource pipeline.
- [GEAR_SELECTION.md](docs/GEAR_SELECTION.md) covers exact gear names and qualifiers. [IMPORT_FORMAT.md](docs/IMPORT_FORMAT.md) is the JSON/storage contract. DTOs under `src/main/java/me/opd/Optimization/` and `JsonIO` are authoritative for request fields and validation.

## Creating target gearsets

- When asked for a new target gearset, write a descriptively named `examples/<purpose>-request.json`, validate it and provide its run command and any search results. Start from the closest working request. Keep existing presets intact unless the user asks to change one, and use a separate `-Output` result path.
- Distinguish wizard primary school from target bonus schools. Pin eligibility follows the wizard and holder rules; do not swap the primary school merely to request secondary-school damage.
- Set current and optional scaled levels explicitly. When scaling is enabled, target values mean post-scaling values; do not pre-convert them to raw requirements. School-specific scaled targets need explicit schools. Global stats such as HEALTH and POWER_PIP_CHANCE use UNIVERSAL.
- Optional `wizard.scaledCapOverrides` supplies explicit user ceilings while retaining bundled proportional ratios; it does not rewrite or verify the captured table. Only use supplied modeling inputs, keep the assumption visible in result metadata/documentation, and preserve omitted-field behavior. The Death/Ice request uses user-reported post-scaling damage caps 235 Death / 243 Ice and user-approved minimums 15 below those caps (220/228); the bundled profile's old 213/221 ceilings are not verified retail maxima. See [LEVEL_SCALING.md](docs/LEVEL_SCALING.md#request-supplied-post-scaling-ceilings).
- Replace the copied `targets` array with the intended requirements/preferences. Use mandatory MINIMUM/MAXIMUM for hard constraints and nonmandatory MAXIMIZE for open-ended rewards. The established health preference is MAXIMIZE, weight 1, scale 1000, without a required value. The current Death/Storm preferences use scale 100 for damage/pierce/resistance/accuracy. These are examples, not automatic requirements for every new request. Do not add excess penalties unless requested.
- Review inherited `requiredSlots`, base overrides, gear/socket/talent locks, exclusions, ownership and pet settings. Keep only intentional restrictions. Retune the AUTO talent pool for the requested schools and goals; the Death/Storm pool is not universal. PREDEFINED can retain the AUTO fields for easy mode switching; its talent locks still apply.
- Prefer friendly gear/talent names. Qualify ambiguous gear names by level; only use IDs where needed. Socket locks currently use jewel IDs under `lockedJewels` with `SLOT:0` keys. Talent locks require manifested talents and cannot be fulfilled by a star jewel.
- Supply verified base stats at the current level when available. The active example mocks only 40 base power-pip chance and omits natural base health. Do not invent progression values or silently present omitted base health as a full character total.
- Use the launcher to prepare a catalog for the new request. Generated pools are filtered by school, level and slots; do not reuse a previous request's filtered catalog without checking coverage. `-Output` preserves a distinct result, but launchers still replace their shared generated catalog/pet artifacts; run them sequentially.
- The expanded launcher defaults to `-GearPool BROAD`, retaining unresolved gear from `optimizer.sqlite` in `target/wizard101/catalog.json`; the standalone mock defaults to FILTERED. Howling Werewolf's Collar is locked in the active request and requires BROAD because its Death Mastery mapping is unsupported. Unknown effects remain unmodeled; preserve issues and incomplete status.
- Java applies [PLAYER_ITEM_FILTER.md](docs/PLAYER_ITEM_FILTER.md) independently of pool mode/completeness: archive internal/dev/stat-holder markers, unresolved display names and unresolved eligibility are rejected before search. Prepared catalogs retain rejected records for name resolution/provenance; result warnings summarize rejections. Both named and ID locks respect screening. Earlier broad results can contain NPC/stat-holder gear (the 700-generation Death/Storm result did); use rerun results from the rebuilt JAR. Passing screening still does not verify obtainability.
- Requests are strict JSON: no comments, duplicate keys or invented metadata fields. Validate the request and run an appropriate search. Review feasibility and every mandatory target; exit 0 alone does not mean requirements were met. A GA failure to find a feasible build is not proof of impossibility. Exact search is for small domains within `exactLimit`.
- Preserve the user's target values and restrictions unless a change is requested. Report remaining deficits when a search does not meet them; do not weaken requirements just to return a feasible result.

## Adding features

- Follow the architecture map and current tests. Keep catalog records immutable during search and preserve universal versus school-specific contributions, positional empty sockets, ownership quantities, locks, pet/jewel duplication rules, modifier recalculation and raw versus scaled views.
- Preserve omitted-field/default behavior when adding optional features. If request/catalog fields change, consider strict JSON, saved profiles, SQLite round trips, Python builders/preparation, launchers, CLI results and documentation together.
- For performance-only changes, preserve RNG calls, candidate ordering, arithmetic, tie-breaking, evaluation counts and history. Keep validation before fitness-cache lookup. The CPU work deliberately avoids repeated domain scans, stat-key allocations and detailed explanations/map copies for discarded socket probes; see [CPU_PERFORMANCE.md](docs/CPU_PERFORMANCE.md).
- Add meaningful tests for changed behavior, run the relevant checks and rebuild the executable JAR when code changes. Compare seeded results when compatibility matters. Report heuristic results separately from gameplay/formula validation.
- Update README and the relevant feature document. Update PROJECT_MAP after significant architecture/workflow changes; update this guide when startup assumptions change. Keep run history in linked documents instead of duplicating it here.

## Local commands and artifacts

For an immediate Storm-primary Death/Storm run on a fresh checkout, use `powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-StormDeathPortable.ps1`. The intentional tracked runtime under `runtime/storm-death/` includes the executable and compressed BROAD gear/pin/pet catalog; only Java 22+ and PowerShell are needed. See [runtime/README.md](runtime/README.md) for coverage, parameters and `bridge/build_portable_runtime.py` refresh instructions. Rebuild/republish after relevant code, scaling, profile or catalog changes. Existing database-backed launchers remain available for other schools and extraction workflows.

From the repository root:

```powershell
mvn test
mvn package
python -m unittest discover -s bridge -p 'test_*.py'
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-DeathStormOptimizer.ps1 -Request examples/death-storm-request.json -Output target/wizard101/death-storm-expanded-results.json
```

Maven is not currently on this machine's PATH. The verified local fallback is:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
$projectMaven = 'C:\Program Files\JetBrains\IntelliJ IDEA 2022.3.2\plugins\maven\lib\maven3\bin\mvn.cmd'
& $projectMaven -o -B -ntp test
& $projectMaven -o -B -ntp package
```

`-o` works with the locally cached dependencies; a fresh checkout may need an online dependency restore. Last verified on 2026-11-06: 74 Java tests and 28 Python tests passed, including portable bundle integrity/coverage and an isolated launcher run. The unchanged full portable Storm/Death run reproduced prior screened builds and history; see [runtime validation](runtime/README.md#validation-on-2026-11-06). Documentation-only changes do not require rebuilding Java.

Development JARs, databases, captures, caches and results live under ignored `target/` and are absent from a fresh checkout. The user-authorized portable executable and compressed catalog under `runtime/storm-death/` are deliberately tracked; keep their manifest/hashes synchronized using the bundle builder. Do not commit other large generated artifacts or recreate/re-extract them unnecessarily when local inputs already exist. Preserve original CSVs, source archives and capture revision hashes with separate backups outside disposable build output. WAD-derived records, pet formulas/rounding, mocked base values and the proportional scaling model retain the validation limits described in the feature documents; do not promote them to verified retail facts.

The project map and this guide are the recall files to reread in future sessions. Obsidian may view the same Markdown files; a separate vault or duplicated notes are unnecessary.
