# Level-180 Storm wizard / Death pins, scaled to 170

[Saved request](../examples/death-storm-request.json) for a **level-180 Storm-primary wizard using Death pins, scaled to level 170**. `wizard.level: 180` controls gear/jewel eligibility and base-stat lookup; `wizard.scaledLevel: 170` controls evaluation. All target entries were retained unchanged and now mean **post-scaling stats**. The seven thresholds below are mandatory minimums. Health and extra Death/Storm resistance, accuracy, damage and pierce are open-ended post-scaling preferences. Calculation uses the versioned proportional cap model; additional combat curves, runtime rounding and unresolved floor behavior are not applied. See [LEVEL_SCALING.md](LEVEL_SCALING.md).

| Stat | Minimum |
| --- | ---: |
| Death damage | 340 |
| Storm damage | 107 |
| Death pierce | 54 |
| Storm pierce | 36 |
| Storm accuracy | 30 |
| Death accuracy | 15 |
| Power-pip chance | 100 |

Howling Werewolf's Collar is locked in AMULET; other equipment and pet talents are unlocked. Owned-only mode is off. Mandatory deficits are normalized by their respective target values with equal weights. The current soft objective combines health with extra Death/Storm resistance, accuracy, damage and pierce. Earlier runs below used fewer soft objectives and did not lock the collar.

## Broad pool with required collar (2026-11-05)

The expanded launcher now defaults to `-GearPool BROAD`, preparing `target/wizard101/catalog.json` from `target/wizard101/optimizer.sqlite`. The pool contains 30,552 gear records and 2,359 jewels/pins/star jewels. Gear with unresolved mappings is retained, including the level-100 Howling Werewolf's Collar (`w101:template:1338316`); its Death Mastery effect remains unmodeled, while its decoded 4 Death damage, 2 Death accuracy and two square sockets are available. Existing school/current-level/slot/test-item and jewel filters remain. Issues and incomplete status are preserved. `-GearPool FILTERED` restores the earlier gear mapping filter and excludes the collar.

The full unchanged seed-7, population-500, 280-generation request completed in 67.8 seconds with 59,340 unique evaluations. All five returned alternatives use the required collar and meet all seven mandatory post-scaling targets. The first has Death/Storm damage 340.226415/189.66, pierce 55.066667/57.033333, accuracy 18.35/30.35, power-pip chance 101, modeled health 7,408.844960 and objective 15.179806080974396. A result check verified the collar ID and every mandatory target against the unchanged request in all five alternatives; this is software/search validation, not retail formula validation.

Results: `target/wizard101/howling-werewolf-collar-results.json`; launcher log: `target/wizard101/howling-werewolf-collar-run.log`. The prior normal result was preserved. All 25 Python tests passed, including broad/filtered pool coverage and preservation of eligibility/test/jewel filters. Both PowerShell scripts passed syntax checks. No Java source changed or JAR rebuild was needed.

## Current search coverage

The current request searches eight equipment slots plus a pet, with ordinary jewels, eligible pins and supported star jewels. It now uses AUTO mode to find five manifested talents from 20 named choices. Seven explicit hypothetical profiles remain available by switching `petSearch.mode` to PREDEFINED. Named manifested-talent locks work in both modes; see [automatic pet search and mode switching](AUTO_PETS.md). Mounts remain excluded and extracted set bonuses remain unresolved. The source data and hypothetical pet attributes are provisional. See [pin/pet rules and setup](PINS_AND_PETS.md).

The 2026-11-05 AUTO trial at population 500 / 280 generations returned five feasible alternatives in 67.5 seconds, versus 65.0 seconds for PREDEFINED. The best AUTO pet manifests Sharp-Shot, Pain-Giver, Death-Dealer, Health Bounty and Spell-Proof with an Armor Breaker Opal; the full objective is 15.38909809159373 and modeled post-scaling health is 7,474.419908. Inputs, full results and independent audit are saved under `target/auto-pets-20261105/`. See [measurement details](AUTO_PETS.md#validation-and-measured-example). Earlier runs below use predefined pets and earlier search settings.

Level-180 gear and jewels remain eligible even though combat targets are evaluated at 170. The CLI prints unscaled equipment/character totals and post-scaling totals separately. Saved results include `scaling` metadata and per-solution `targetStats`; fitness component `actual` values use the latter. Socket selection and the main GA/exact search use the same post-scaling score.

The request inherits the existing demo's **mocked 40% base power-pip chance**. Other base contributions are zero. Supply verified natural base stats at character level 180 before treating totals as a gameplay build. Base health is currently omitted and this can affect health saturation/tradeoffs. All archive-derived candidates remain provisional, and availability is unverified.

## Reproduce

From the project root, with Python and JDK 22+ available:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-DeathStormOptimizer.ps1
```

The rebuilt JAR, extracted databases and matching supplemental pet-resource capture must already exist; see [PINS_AND_PETS.md](PINS_AND_PETS.md). The launcher uses separate catalog/result paths and preserves the earlier mock run and eight-slot baseline.

## Current preferences

Eight optional `MAXIMIZE` entries reward Death and Storm resistance, accuracy, damage and pierce. Existing mandatory minimum entries are retained separately, including power-pip chance. All eight new entries use `weight: 1`, `scale: 100` and no target value. Health remains a ninth optional maximization entry with weight 1 and scale 1000. There are no preferred-value penalties or mandatory upper bounds.

Once mandatory feasibility/deficit is tied, the objective below uses **post-scaling** values:

```text
health / 1000
  + (Death resistance + Storm resistance
     + Death accuracy + Storm accuracy
     + Death damage + Storm damage
     + Death pierce + Storm pierce) / 100
```

Each additional post-scaling point in one percentage stat/school is worth 10 post-scaling health under these starting weights. These are effective school values: a universal bonus contributes to each school before that school's scaling/cap. More accuracy earns a reward above the minimum until its applicable cap. Power-pip chance above 100 is still neutral. No excess penalties or user-specified upper requirements were added, but the scaling model enforces caps, so raw bonuses above saturation do not keep earning rewards. This combined objective permits health/stat tradeoffs; it does not maximize each stat independently. Edit `weight` and `scale` in the request to change those tradeoffs.

## Health preference

The request includes `{"stat":"HEALTH","school":"UNIVERSAL","mode":"MAXIMIZE","mandatory":false,"weight":1,"scale":1000}`. There is no health target value and no cap. Health contributes to the combined soft objective after mandatory feasibility/deficit, so it cannot compensate for missing required damage, pierce, accuracy or power-pip chance. In a feasible pool it rewards every additional health point, including legal health jewels and healthier gear, alongside the other preferences.

Health jewels must still fit the socket shape, holder restrictions, wizard eligibility and inventory rules. Health is not forcibly inserted into a socket needed for a mandatory stat. The current capture has eligible +525 square and +155 tear health jewels for this Storm wizard. Reported health excludes natural base health because the request has not supplied a verified base health value; its base override still contains only mocked power-pip chance.

The no-health-objective result is preserved at `target/wizard101/storm-primary-without-health-objective-results.json`. A new numerical regression compares the GA with exhaustive search: it selects the strongest compatible health tear and square while retaining a mandatory pierce circle, and rejects an infeasible alternative with arbitrarily high health. The JSON health preference omits `value`.

## Current level-180 to level-170 result, 2026-11-04

Executed with the rebuilt JAR, character level 180, scaled combat level 170, seed 7, population 250 and 250 generations. The pool contains 21,966 gear/configuration records, including 155 level-180 records, and 2,359 socket items. All 16 target entries were retained unchanged from the unscaled trial. The run evaluated 27,875 cached configurations, reached feasibility at generation 15 and completed in 175.5 seconds.

All five returned candidates meet all seven mandatory requirements **after scaling**. The first scores 15.016754 under the combined post-scaling objective.

| Stat | Mandatory minimum after scaling | Unscaled character stat | At combat level 170 |
| --- | ---: | ---: | ---: |
| Death damage | 340 | 344.625 | 341.838275 |
| Storm damage | 107 | 164.625 | 163.128409 |
| Death pierce | 54 | 57.525 | 56.56625 |
| Storm pierce | 36 | 57.525 | 56.56625 |
| Storm accuracy | 30 | 45 | 45 |
| Death accuracy | 15 | 21 | 21 |
| Power-pip chance | 100 | 100 | 100 |
| Death resistance | None | 47.28 | 40.116364 |
| Storm resistance | None | 47.28 | 40.116364 |
| Health | None | 7,871 | 7,373.434489 |

| Slot | First candidate | Required item level |
| --- | --- | ---: |
| Hat | Headmistress' Dynamic Cap | 180 |
| Robe | Shocking Abomination Suit | 180 |
| Boots | Daemonic Wraps of Surging | 170 |
| Wand | Arcane Smoothie | 160 |
| Athame | Shocking Abomination Athame | 180 |
| Amulet | Electric Monstrous Badge | 180 |
| Ring | Fractured Aeon Ring | 160 |
| Deck | Fitzhume's Stormchasing Cards | 180 |
| Pet | Hypothetical dual-school damage / pierce | Explicit configuration |

Five selected items require level 180; the 170 combat level did not exclude them. Items below 180 also remain eligible, so the search is free to use a lower-level piece when its complete scaled result ranks better. Positional socket/talent details, raw totals, target totals, table version and source hashes are saved at `target/wizard101/death-storm-expanded-results.json`.

Validation: all 40 Java tests and 21 Python tests passed, followed by successful JAR packaging and the full launcher. An independent audit recomputed the projections and objective directly from the original decoded Storm-primary 180/170 cap rows, checked all five candidates and verified that target values, modes, weights, scales and mandatory flags were unchanged. The model remains proportional and unrounded, with unresolved floor semantics and live retail verification outstanding. Natural base health is still omitted from this preset, as noted above.

## Historical unscaled level-170 combined-objective result, 2026-11-04

Before post-scaling support, the full launcher completed with an actual level-170 wizard, seed 7, population 250 and 250 generations. It evaluated 25,729 cached configurations, first reached feasibility at generation 10 and finished in 127.5 seconds. All five alternatives met the seven mandatory thresholds and had objective 15.3626. This unscaled result is preserved at `target/wizard101/storm-primary-unscaled-170-combined-results.json`, with its catalog at `target/wizard101/storm-primary-unscaled-170-combined-catalog.json`.

| Stat | Mandatory minimum | Best-ranked result |
| --- | ---: | ---: |
| Death damage | 340 | 343.15 |
| Storm damage | 107 | 189.15 |
| Death pierce | 54 | 54 |
| Storm pierce | 36 | 53 |
| Storm accuracy | 30 | 32 |
| Death accuracy | 15 | 15 |
| Power-pip chance | 100 | 100 |
| Death resistance | None | 43.28 |
| Storm resistance | None | 43.28 |
| Health | None | 7,634 |

The first candidate filled all three power-pin sockets with Death Accurate Pin (100), contributing 5 Death accuracy each. Health sockets contained four +525 square jewels and two +155 tears. Armor was Laurel of Gorgon Volatility, Stormy Night Mire Coat and Shocking Crimefighter's Shoes. Other gear was Xibalba Crying Sky Sword, Fractured Aeon Athame, Eternal Fractal Charm, Fierce Dream Reaver Ring and Xibalba Crying Sky Deck, with the hypothetical dual-school damage / Mighty pet profile. Full positional details are retained in the archived result above. The standard `death-storm-expanded-results.json` now contains the scaled run.

Independent output checks confirmed all mandatory thresholds, all nine maximization components, descending result ordering and the combined objective recalculated directly from raw character totals. No engine change or new tests were needed for this request-only update. The prior 32-test Java suite remains the last code validation. This is a heuristic result in the provisional raw-stat model; health still excludes unprovided natural base health.

## Historical health-only objective result, 2026-11-04

The full launcher completed with the same primary school, catalog pool, seven mandatory thresholds, seed 7, population 250 and 250 generations. It evaluated 30,064 cached configurations, first reached feasibility at generation 11 and finished in 121.9 seconds. All five returned alternatives meet every mandatory threshold and have 6,659.8 modeled health, compared with 3,718 in the earlier result that did not score health. This is the best found by the heuristic run, not a proof of maximum health over the complete catalog or game.

| Stat | Requirement | Best-ranked result |
| --- | ---: | ---: |
| Death damage | At least 340 | 341.625 |
| Storm damage | At least 107 | 157.05 |
| Death pierce | At least 54 | 54.525 |
| Storm pierce | At least 36 | 39.525 |
| Storm accuracy | At least 30 | 34.525 |
| Death accuracy | At least 15 | 15 |
| Power-pip chance | At least 100 | 100 |
| Health | Maximize, no threshold | 6,659.8 |

| Slot   | Gear/configuration                         | Selected socket items (zero-based positions)                                                              |
| ------ | ------------------------------------------ | --------------------------------------------------------------------------------------------------------- |
| Hat    | Fierce Dream Stalker Hood (170)            | 1: Death Punishing Pin (170); 2: Death Punishing Pin (160)                                                |
| Robe   | Squall King's Plate (160)                  | 0 and 2: Death Punishing Pin (160)                                                                        |
| Boots  | Daemonic Wraps of Surging (170)            | 0 and 2: Death Punishing Pin (160); 1: Balance Conversion Pin (140)                                       |
| Wand   | Consultant Hazmat Meter (132)              | 0: Unearthly Health Amethyst +525                                                                         |
| Athame | Fractured Aeon Athame (160)                | 0: Flawless Health Opal +155; 1: Wild Onyx +6%; 2: Polished Piercing Onyx +6%; 3: Plain Accurate Onyx +7% |
| Amulet | Shocking August Sage Ampul (170)           | 0: Unearthly Damage Onyx +11%; 1: Opaque Accurate Onyx +8%                                                |
| Ring   | Fierce Dream Reaver Ring (170)             | 0: Flawless Health Opal +155; 1: Polished Piercing Onyx +6%; 2: Unearthly Health Amethyst +525            |
| Deck   | Xibalba Crying Sky Deck (170)              | 0: Wild Onyx +6%                                                                                          |
| Pet    | Hypothetical Storm accuracy / Death damage | 0: Health Bounty Opal                                                                                     |

All tear and square sockets in every returned alternative are filled with the strongest eligible health jewel in this pool: two +155 tears and two +525 squares, adding 1,360 health. The star jewel adds a further modeled health contribution. Unlisted positions are empty. The unused power-pin choices do not affect the current targets or health objective; an unrelated pin can remain on a tied build without receiving any scoring reward.

This health-only objective result is preserved at `target/wizard101/storm-primary-health-only-results.json`. The standard `target/wizard101/death-storm-expanded-results.json` is replaced by runs with the current combined preferences. Independent output checks verified all seven requirements, health-based ranking and maximum eligible health in every tear/square socket of all five alternatives. Maven package passed with all 32 Java tests, including the health socket regression. No engine or game-formula change was needed: health was already supported, and the preset explicitly asked to maximize it. The raw/provisional/base-stat limitations above still apply.

## Corrected Storm-primary result before health preference, 2026-11-04

The original request selected the wrong primary school. Changing it to Storm made the appropriate Death pins eligible, but the unchanged random-socket GA still returned only 183.675 Death damage and 53.64 Death pierce. That result used two Death pins and missed better combinations across the armor slots. It is preserved at `target/wizard101/storm-primary-random-sockets-results.json`.

The search now refines compatible socket choices against the actual targets for half the initial population and each new holder selected by gear mutation. Other starts and socket mutations remain random. No pins are forced and no target is lowered. The added regression failed before this change and now passes for both school directions, including initialization and gear mutation; another regression covers owned copies, locks, empty sockets and mandatory maximums.

The rebuilt launcher completed with JDK 22, seed 7, population 250 and 250 generations. The corrected pool has 21,795 gear/configuration records and 2,314 socket items: 1,959 ordinary jewels, 203 pins and 152 star jewels, including seven explicit pet profiles. It evaluated 30,092 cached configurations, reached feasibility by generation 20, and finished in 64.1 seconds. Internal socket-refinement probes are additional scoring work and are not included in the cached configuration count.

| Stat | Minimum | Best-ranked result |
| --- | ---: | ---: |
| Death damage | 340 | 340.8 |
| Storm damage | 107 | 142.6 |
| Death pierce | 54 | 54 |
| Storm pierce | 36 | 36 |
| Storm accuracy | 30 | 32 |
| Death accuracy | 15 | 16.35 |
| Power-pip chance | 100 | 101 |

All seven mandatory targets were met in this raw-stat model. This historical request had no soft objective, so feasible builds tied on target score; it did not maximize extra damage or health. The current request adds the open-ended health preference described above.

| Slot | Gear/configuration | Selected socket items (zero-based positions) |
| --- | --- | --- |
| Hat | Wild Skyfarer's Douli Hat (150) | 1: Death Punishing Pin (170); 2: Death Punishing Pin (160) |
| Robe | Stormy Robe of the Nullity (160) | 0: Death Punishing Pin (160); 2: Death Punishing Pin (150) |
| Boots | Shocking August Sage Shoes (170) | 0: Death Punishing Pin (170); 2: Death Punishing Pin (170) |
| Wand | Festive Wrapping Paper (140) | Empty |
| Athame | Umbra Queen's Shocked Dirk (170) | 1: Polished Piercing Onyx +6%; 2: Clear Piercing Onyx +5%; 3: Polished Accurate Onyx +10% |
| Amulet | Striking City Dweller Amulet (160) | 0: Wild Onyx +6%; 1: Opaque Accurate Amethyst +8% |
| Ring | Ring of the Seeker (100) | 1: Plain Piercing Onyx +3% |
| Deck | Xibalba Crying Sky Deck (170) | 0: Wild Onyx +6% |
| Pet | Hypothetical five damage talents | 0: Death-Shot Onyx |

All unlisted socket positions are empty. The six selected Death Punishing pins contribute **270 Death damage and 24 Death pierce**. The selected pet manifests Death-Dealer, Death-Giver, Death-Boon, Pain-Giver and Pain-Bringer, with Death-Shot supplied by its star jewel. Its fractional effects use the explicit RAW policy.

This no-health-objective result is preserved at `target/wizard101/storm-primary-without-health-objective-results.json`. The standard `target/wizard101/death-storm-expanded-results.json` is replaced by the current health-preferring run, with its filtered pool at `target/wizard101/death-storm-expanded-catalog.json`. This is a feasible software-model candidate using provisional archive records, hypothetical pet attributes, and the mocked 40 base power-pip chance. Retail availability, pet formula/rounding, and observed aggregate stats still require verification. No damage cap or combat scaling has been applied.

Validation: Maven package and all 31 Java tests passed; all 18 Python tests passed. The full launcher completed successfully with the rebuilt JAR.

## Historical Death-primary expanded result

Executed after the socket-initialization fix using JDK 22, seed 7, population 250 and 250 generations. The pool contains 21,793 gear/configuration records and 2,324 socket items: 1,959 ordinary jewels, 213 pins and 152 star jewels. It includes seven pet profiles. The search evaluated 27,393 unique configurations and took 42.7 seconds locally.

| Stat | Target | Best-ranked result |
| --- | ---: | ---: |
| Death damage | 340 | 142.625 |
| Storm damage | 107 | 110.05 |
| Death pierce | 54 | 54.525 |
| Storm pierce | 36 | 38.525 |
| Storm accuracy | 30 | 30.525 |
| Death accuracy | 15 | 24 |
| Power-pip chance | 100 | 100.56 |

All targets except Death damage were met by this candidate. Its pet uses Death-Dealer, Pain-Giver, Storm-Sniper, Armor Breaker and Mighty with a Pip o'Plenty star jewel. Fractional pet contributions use the explicit RAW policy, not a verified in-game display rounding rule. The best-ranked candidate happened to use gear without pin sockets; pins were available to the search, and integration tests independently verify their selection, locks and compatibility.

The earlier Death-primary result is preserved at `target/wizard101/death-primary-expanded-results.json`, with its catalog at `target/wizard101/death-primary-expanded-catalog.json`. The launcher now uses the corrected Storm-primary request and replaces the standard `death-storm-expanded-*` outputs. The historical result is a heuristic candidate within a provisional subset, not a matching recommendation or proof that the requested complete in-game build is impossible. Other pet configurations, mounts, sets, source accuracy and search convergence remain material limitations.

## Historical Death-primary eight-slot baseline

Executed using JDK 22, seed 7, population 250, and 250 generations over 21,786 gear records and 1,959 ordinary jewels. The search evaluated 24,247 distinct configurations and returned five alternatives. None met all mandatory targets.

| Stat | Target | Best-ranked result | Shortfall |
| --- | ---: | ---: | ---: |
| Death damage | 340 | 122 | 218 |
| Storm damage | 107 | 95 | 12 |
| Death pierce | 54 | 52 | 2 |
| Storm pierce | 36 | 36 | 0 |
| Storm accuracy | 30 | 30 | 0 |
| Death accuracy | 15 | 18 | 0 |
| Power-pip chance | 100 | 100 | 0 |

The GA ranks by total normalized mandatory deficit. This result is a partial diagnostic candidate, not a recommended matching loadout or a proven optimum. The five alternatives share these target totals but differ in other outcomes.

A separate per-slot upper-bound check found that **Death damage cannot exceed 242 within this particular filtered eight-slot pool**, even before requiring any of the other targets. The bound optimistically allows each gear piece and every matching-shape jewel to maximize Death damage independently and ignores additional compatibility constraints; that makes it an upper bound. It excludes pets, mounts, pins, unresolved sets and filtered-out records, so it is not a bound on a complete in-game build. Increasing generations alone cannot close the gap in this pool.

This bound applies only to the historical eight-slot pool. The new workflow includes pins and explicit pet effects. Further work should verify representative item/pet observations, expand pet profiles as needed, and resolve mount handling and relevant set bonuses while preserving the requested targets.
