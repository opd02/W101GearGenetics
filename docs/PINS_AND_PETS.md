# Pins and explicit pet configurations

The Storm-primary / Death-secondary workflow includes Death pins, seven editable hypothetical pet configurations and compatible star jewels. The current request selects a level-180 wizard with targets evaluated after scaling to level 170; gear and jewel eligibility still use 180. The request was corrected from DEATH to STORM primary on 2026-11-04. The original mock preset remains an unscaled Death-primary example.

The Death/Storm request now defaults to **automatic five-talent search** from 20 named choices. Change `petSearch.mode` from `"AUTO"` to `"PREDEFINED"` to use the seven configurations described below. Both modes accept named manifested-talent locks. See [AUTO_PETS.md](AUTO_PETS.md) for complete switching/editing instructions. The launcher exports standalone supported talent definitions as well as predefined pets and star jewels.

## Run locally

With the existing extracted databases, resource capture and rebuilt JAR:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-DeathStormOptimizer.ps1
```

Edit [pet-profiles.json](../examples/pet-profiles.json) to change the pet configurations and [death-storm-request.json](../examples/death-storm-request.json) to change targets. The launcher rebuilds the generated pet catalog on each run. It uses the existing JDK selection in `Run-MockOptimizer.ps1` and also accepts `-JavaPath`.

Generated outputs under ignored `target/`:

- `wizard101/pet-catalog.json`: normalized explicit pets, supported standalone `petTalents` and star jewels.
- `wizard101/pet-catalog.report.json`: supported/excluded talent and jewel counts/reasons.
- `wizard101/pet-catalog.talents.json`: raw talent definitions, normalized supported definitions and exclusions.
- `wizard101/catalog.json`: request-specific gear, ordinary jewels, pins and explicit pets. The expanded launcher's default `-GearPool BROAD` retains unresolved gear such as Howling Werewolf's Collar; `-GearPool FILTERED` restores the earlier gear mapping filter. Unresolved effects remain unmodeled, with issues and incomplete status preserved. Jewel/pin filters are unchanged.
- `wizard101/death-storm-expanded-results.json`: ranked builds, socket positions, talents, totals and target deficits.

These generated outputs are replaced by subsequent runs. Original source databases, the earlier eight-slot results and original captures are preserved. Supply `-Output` to save optimization results separately. Other launcher options include `-PetProfiles`, `-ResourceCapture`, `-StagingDatabase`, `-Database` and `-Request`.

## Capture missing pet resources

The updated C# decoder captures talent-definition XML resources, including non-item `PetTalentTemplate` objects. Its resource-only mode avoids decoding the complete item manifest again. Requires .NET 10 and compatible Imcodec libraries, as described in [WAD_CATALOG.md](WAD_CATALOG.md).

```powershell
dotnet build bridge/WadCatalog/WadCatalog.csproj '-p:ImcodecDir=H:\Vibe Coding\PersonalServer\local-run\server'
dotnet target/wad-decoder/bin/Debug/net10.0/WadCatalog.dll 'C:\ProgramData\KingsIsle Entertainment\Wizard101' target/pet-resources-20261102 --resources-only
```

Capture output must be a new directory. If that path already exists, use another path and pass it to the launcher through `-ResourceCapture`. Capture opens source archives for reading and never attaches to the game. The pet builder requires matching executable and Root archive hashes between the supplemental capture and original staging database. Recapture/stage a consistent revision after a patch rather than mixing versions.

The local resource-only capture retained **632 pet talent definitions**. The supported subset is currently **154 talent definitions and 152 star jewels**. Unsupported effects, retired/conflicting definitions and unresolved jewels are excluded with reasons; their source data remains in the capture/diagnostic artifacts.

## Pin rules

The source database has 1,350 pin records. The corrected level-170 Storm-primary filter admits 203 pins: 125 sword, 36 shield and 42 power pins. The earlier Death-primary filter admitted 213 pins (134 sword, 38 shield and 41 power). These are archive counts, not verification of retail availability. For example, the captured level-170 Death Punishing Pin eligible for Storm adds 46 Death damage and 4 Death pierce; the Storm Punishing Pin eligible for Death adds 9 Storm damage and 4 Storm pierce. They are different eligible records, not interchangeable stat scopes.

The current level-180 eligibility filter admits 227 pins: 143 sword, 36 shield and 48 power pins. These may be socketed according to character-level requirements, then their bonuses are aggregated and scaled with the rest of the build. See [scaling rules](LEVEL_SCALING.md) and [the current run](DEATH_STORM_RUN.md).

Pins require the matching socket shape on a hat, robe or boots. Wizard eligibility and minimum level come from explicit requirements, including negated focus-school conditions. A pin's bonus school is distinct from the wizard who may equip it. The holder mapping follows [KingsIsle's pin system description](https://akamaieu.wizard101.com/game/community/updatenotes/may2024). School-specific socket restrictions, where supplied, are also enforced.

`prepare_mock_catalog.py --include-pins` upgrades the old provisional pin projection in memory without changing its SQLite source. Only the previous generic holder-restriction flag may be cleared; pins with other unresolved issues stay excluded. Pin records remain `complete=false` pending retail description/availability checks. The general mock launcher keeps pins opt-in through `-IncludePins`.

## Pet profile contract

The profile file has `schemaVersion: 1`, optional `defaults` and a `pets` array. Entries override defaults. Each entry selects a stable configuration ID, display name, body template, age, explicit attributes and talent IDs. See the executable example for field names.

- Native age values: 0 egg, 1 baby, 2 teen, 3 adult, 4 ancient, 5 epic, 6 mega, 7 ultra. Manifested talent counts are limited by age and never exceed five.
- Attributes use `STRENGTH`, `INTELLECT`, `AGILITY`, `WILL`, `POWER`. Supply trained values before attribute modifiers, plus their unmodified maximums.
- `fullyTrained: true` requires all five trained values to equal those maximums. Attribute-modifying talents/jewels currently require this mode. This prevents pretending that increasing a cap automatically trains an unfinished pet.
- `rounding` must be explicitly `RAW` or `NEAREST_WHOLE`. The supplied profiles use `RAW`, retaining fractional modeled bonuses until retail rounding is verified.
- `hypothetical` must be explicit. The examples use a Piggle body with assumed fully trained attributes; they are proposed bred configurations, not first-generation Piggle stats or established obtainable pets. The source body's first-generation talent pool is not treated as the talent pool of every bred instance.
- The current profiles are a curated starting set, not enumeration of every legal pet. No breeding probability or inheritance simulator is included.

The star jewel remains a separate selectable/lockable socket. Its source `m_minPetLevel` is checked against pet age, independently of its wizard-level requirement. The socket unlocks at Ancient. A multi-talent jewel cannot duplicate any manifested talent. An attribute modifier from a jewel recalculates all affected manifested and jewel talents before their player bonuses are summed. Jewel inventory quantities and socket locks use the existing optimizer rules.

## Calculation and validation boundary

For supported `PetBoostPlayerStatEffectTemplate` records, the provisional model is:

```text
attribute weight = primaryStat1 + primaryStat2 + secondaryModifier * secondaryStat
player bonus = attribute weight * coefficient converted into the target stat's units
```

Attribute modifiers apply first. `RAW` retains the calculated bonus. `NEAREST_WHOLE` rounds each talent's combined contribution to a stat to the nearest integer, with halves upward; this is an explicit optional modeling policy, not a verified retail rule. Pre-resolved `statBonus` entries remain supported and are not rounded again.

Archive definitions identify effects/coefficients and attribute roles. The evaluation order and rounding still require in-game comparison; synthetic numerical tests establish software behavior, not retail correctness. Every generated record remains `complete=false`, and the request explicitly permits provisional items. No combat curves or damage caps are applied.

For owned pets with observed talent values, the normalized catalog format also accepts fixed `PetTrait.statBonus` values without dynamic effects. Keep the manifested IDs (including an observed Mighty talent with zero direct player bonus) so jewel duplication checks remain effective. Do not also add dynamic coefficients for bonuses already supplied as observed values. Hypothetical pets are excluded in owned-only mode.

The next independent validation sample should cover a sword pin, a power pin, a pet with known trained attributes, the same damage/pierce talents with an attribute modifier, and an eligible star jewel. Compare both visible individual bonuses and controlled aggregate changes. No independent retail item-card validation has been performed by this implementation.

## Checks executed

- .NET decoder build: zero warnings/errors; resource-only capture completed successfully against the local installation.
- Maven package: 29 Java tests passed, including numeric pet effects, jewel-provided Mighty, age/duplicate checks, JSON/SQLite round trips, pin locks/owned copies, GA/exact agreement and socket initialization.
- Python: 18 tests passed, including resource projection, unsupported effects, profile validation, pin focus/bonus separation, revision checks and an end-to-end pet/star-jewel fixture.
- Full launcher: completed the expanded level-170 Death/Storm search; see [the run report](DEATH_STORM_RUN.md).

Socket initialization now samples missing socket choices on new gear. Repair preserves explicit empty sockets and compatible prior jewels, reserving locked copies first. This lets the GA explore newly selected pin-bearing equipment with filled sockets immediately.

On 2026-11-04 the request was corrected to Storm primary, and target-based socket refinement was added to address poor convergence with random socket choices. Half the initial population retains random sockets; half receives up to two passes over compatible socket choices using the full target score. Gear mutations also refine the new holder's sockets before selection. Locks, wizard eligibility, owned counts, explicit empty choices, mandatory maximums and dynamic pet-jewel recalculation are respected. Empty sockets remain candidates and can be selected when they improve the score. This is local heuristic improvement, not enumeration or an optimality guarantee; trial socket scores are not included in the result's cached genome evaluation count.
