# Death-primary / Death and Ice damage

[Request](../examples/death-ice-request.json) for a Death-primary wizard at current level 180, evaluated after scaling to 170. Gear and sockets use level-180 eligibility. All target values are post-scaling.

| Mandatory stat | Minimum |
| --- | ---: |
| Death damage | 220 |
| Ice damage | 228 |
| Death pierce | 45 |
| Ice pierce | 45 |
| Ice accuracy | 20 |
| Death accuracy | 15 |
| Power-pip chance | 100 |

The user specified 235 Death / 243 Ice as the desired scaled maxima, then allowed damage to be up to 15 below each cap. The current mandatory damage minimums are therefore 220 Death / 228 Ice, with strong `MAXIMIZE` preferences rewarding damage above those floors. Optional `wizard.scaledCapOverrides` explicitly supplies 235 / 243 as scenario ceilings while retaining the original proportional ratios; the bundled table is unchanged. The original captured table limited this profile to 213 / 221, so weighting alone could never reach the requested values. This calibration records the user's inputs rather than claiming independently verified runtime scaling. See [the exact override formula](LEVEL_SCALING.md#request-supplied-post-scaling-ceilings).

The nine soft preferences keep the other trials' health weight 1 / scale 1000 and Death/Ice resistance, accuracy and pierce weight 1 / scale 100. Damage uses weight 10 / scale 100 in both schools to emphasize the requested maximum-damage goal. Once mandatory requirements are met, the objective is:

```text
health / 1000
  + 10 * (Death damage + Ice damage) / 100
  + (Death resistance + Ice resistance + Death accuracy + Ice accuracy
     + Death pierce + Ice pierce) / 100
```

All seven mandatory minimums rank ahead of these preferences. Once they are met, damage continues to earn its strong reward up to the supplied ceilings alongside health, resistance and other preferences. Before feasibility, the search minimizes normalized mandatory deficits. Power-pip chance is minimum-only. AUTO selects five distinct manifested talents from the 20-entry Death/Ice pool; star jewels are separate. The named Death damage / Mighty template supplies body/attributes/rounding, while its original manifested talents are replaced. The user's Collar of the Betrayed AMULET lock is preserved; other gear, sockets and talents are unlocked. No copied named exclusions remain, owned-only mode is off and MOUNT is omitted. Search effort is population 750 / 250 generations.

Run sequentially with the other launchers because prepared catalog/pet artifacts are shared:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-DeathStormOptimizer.ps1 -Request examples/death-ice-request.json -GearPool BROAD -Output target/wizard101/death-ice-calibrated-results.json
```

The BROAD pool retains named gear with unmodeled effects; the Java [player-item filter](PLAYER_ITEM_FILTER.md) independently rejects identified internal/dev/stat-holder records, unlocalized items and uncertain eligibility. FILTERED is available with `-GearPool FILTERED` for stricter effect-mapping coverage, potentially changing search results. Neither mode verifies obtainability.

The request preserves the other trials' modeled 40 base power-pip chance and omits natural base health. Health totals are therefore modeled contributions, not complete character health. Gear availability, unresolved effects, pet formulas/rounding and proportional scaling remain provisional. GA results do not establish a global maximum.

## Current validation with user-supplied ceilings, 2026-11-05

All 74 Java tests and 25 Python tests passed, and the executable JAR was rebuilt. Six cap-override tests cover default/empty compatibility, exact original arithmetic, universal aggregation, ceiling replacement without changing ratios, immutable table data, unsupported/invalid/zero-ratio inputs, preserved client checks, GA/exact/socket scoring, JSON/SQLite/saved-profile/CLI round trips, older result descriptors and the existing public descriptor constructor.

The ordinary 750-population / 250-generation GA run with the approved 220 / 228 minimums evaluated 76,116 builds in 112.4 seconds. Its best reached 224.044444 Death / 225.735714 Ice; it met every other mandatory target but missed Ice by 2.264286. It is retained at `target/wizard101/death-ice-calibrated-results.json` with its matching run log. A completed run is not equivalent to feasibility.

Targeted socket refinement of the saved strict-goal candidates and this relaxed GA run produced **four feasible alternatives**. Each probe held that candidate's gear and five manifested talents fixed and used the engine's compatible socket domains and `SocketOptimizer` against the current request. The original request received no additional gear/talent/socket locks. The key change on the best candidate was replacing its deck's +11 Death damage jewel with a +11 Ice damage jewel. The best refined result is:

| Post-scaling stat | Mandatory minimum | Best refined result |
| --- | ---: | ---: |
| Death damage | 220 | 222.466667 |
| Ice damage | 228 | 233.43125 |
| Death pierce | 45 | 46.222222 |
| Ice pierce | 45 | 47.111111 |
| Ice accuracy | 20 | 28 |
| Death accuracy | 15 | 20 |
| Power-pip chance | 100 | 103 |

Damage is 12.533333 / 9.56875 below the user-supplied 235 / 243 caps. Modeled health is 7,274.636506, Death/Ice resistance is 41.720930 each, and the objective is 55.112180110749144. The collar lock is preserved. These are scenario-model results using the supplied ceilings and retained ratios, not independently verified raid observations.

Use `target/wizard101/death-ice-refined-results.json` for the matching builds. Its method is explicitly `SAVED_GA_SOCKET_REFINEMENT`, with an empty GA history; it is not presented as a fresh automatic GA run. The helper evaluated 15 distinct configurations. A separate Python audit independently recomputed every projected stat from raw totals, bundled ratios and user ceilings, and verified all seven mandatory targets, objectives, ranks, collar lock and preserved preference/talent configuration in all four alternatives.

Snapshots, the previous JAR, build log, source-only refinement helper and audit reports/scripts are under `target/death-ice-cap-update-20261105-233232/`. With those local artifacts, reproduce refinement after preparing the Death catalog using JDK 22 source-file mode:

```powershell
& 'C:\Program Files\Java\jdk-22\bin\java.exe' -Xmx1500m --class-path target/GearGenetics-1.0-SNAPSHOT.jar target/death-ice-cap-update-20261105-233232/RefineDeathIce.java target/wizard101/catalog.json examples/death-ice-request.json target/death-ice-cap-update-20261105-233232/calibrated-700-strict-results.json target/wizard101/death-ice-calibrated-results.json target/wizard101/death-ice-refined-results.json
python target/death-ice-cap-update-20261105-233232/verify_results.py target/wizard101/death-ice-refined-results.json
```

The earlier strict 235 / 243 minimum trial did not find a feasible build, even at 700 generations: it reached 233.313889 / 222.578571 while meeting the other five minimums. This does not prove impossibility. Its archived request/results/logs use `calibrated-700-strict-*` names; the user's later 15-point tolerance defines the current request.

## Historical lower-ceiling run, 2026-11-05

Before the user's damage-cap correction, the request used seed 7, population 750 and 250 generations, returning five alternatives meeting its then-five mandatory requirements. The launcher completed in 102.6 seconds with 62,696 unique evaluations. The best objective was 53.67801002500176. All five alternatives shared 210.633333 Death damage and 221 Ice damage under the old ceilings. Those ceilings were not verified gameplay maxima and this result does not meet the current damage goals. The request, result and old JAR are archived under `target/death-ice-cap-update-20261105-233232/`.

| Post-scaling stat | Requirement | Best result |
| --- | --- | ---: |
| Death damage | Maximize; model cap 213 | 210.633333 |
| Ice damage | Maximize; model cap 221 | 221 |
| Death pierce | At least 45 | 47.111111 |
| Ice pierce | At least 45 | 49.777778 |
| Ice accuracy | At least 20 | 23.35 |
| Death accuracy | At least 15 | 25.35 |
| Power-pip chance | At least 100 | 104 |
| Death resistance | Maximize | 47.162791 |
| Ice resistance | Maximize | 47.162791 |
| Modeled health | Maximize | 8,115.531989 |

The best uses Cursed Scholomari Cap, Fitzhume's Raider Gear, Scary Abomination Stompers, Withering Hanami Katana, Scary Abomination Athame, Collar of the Betrayed, Scary Abomination Ring and Grave Crimefighter's Notes, plus the AUTO pet. The amulet's mastery effect remains unmodeled. Full sockets and pet talents are in `target/wizard101/death-ice-results.json`; the log is `target/wizard101/death-ice-run.log`.

Final validation checked strict JSON and duplicate keys, all requested minimums, nine weighted preferences, all five alternatives' target actuals and objectives, ranking, levels, seed/history and retained gear metadata. The Java engine reported rejecting 6,418 archive records before compiling legal domains. No Java/Python implementation changed for this request, so no JAR rebuild or repeated code suite was needed.

An initial trial with the other presets' equal damage weights met the old minimums but traded substantial damage for health (152.255556 Death / 197.716071 Ice damage and 11,138.630933 modeled health). Raising only the two damage weights to 10 expressed the stronger damage preference but could not overcome the old model ceilings. That diagnostic result/log is preserved under `target/wizard101/death-ice-balanced-preferences-*`. The normal `death-ice-results.json` is historical; use the calibrated output for the current request.
