# CPU tuning, 2026-11-05

This change reduces repeated work within the existing single-threaded optimizer. The search domains, RNG calls, candidate order, two-pass socket refinement, target formulas, scaling arithmetic, constraints, tie-breaking, cache semantics and result explanations are retained. No request settings or target values were changed.

## Changes

- `SearchSpace` indexes legal gear and per-socket jewel IDs instead of scanning choice lists on every validation. Identical socket domains share membership sets. The original ordered lists still drive sampling, repair and enumeration, including legal empty sockets. Validation still runs before fitness-cache lookup.
- `StatBlock` reuses canonical stat keys and a boxed zero instead of repeatedly allocating them. Valid-key membership avoids parsing the same keys during repeated pet/stat validation; invalid keys retain the existing diagnostic path.
- `Fitness.score` computes the same feasibility, deficit and objective without allocating per-target explanation components for discarded socket probes. Full evaluations still generate every explanation, using the same arithmetic implementation.
- `SocketOptimizer` scores ordinary additive jewel candidates through a private read-only view. It copies a stat map only when a candidate improves the score. Lookups preserve the original `Map.merge` addition order and absent-value behavior, including signed zero. Pet sockets retain the materialized path and recalculate talents and attribute modifiers for each candidate.

The initial Java Flight Recorder profile identified linear gear/jewel membership scans as the largest sampled CPU cost. Repeated stat-key construction, detailed fitness components and stat-map copies were the largest project allocation sites. JFR allocation weights are sampled estimates, not exact allocation counts.

## Validation

- All 51 Java tests and 21 Python tests passed; the executable JAR was rebuilt.
- The new differential tests retain independent pre-tuning fitness and socket implementations. They compare 1,000 scaled/unscaled scoring cases and 960 socket-refinement steps across seeded builds, pet modifiers, locks, owned quantities, maximum requirements and soft objectives. Numeric scores are checked exactly, including floating-point bits; no tolerance is used. Catalog contents remain unchanged.
- Validation regression cases distinguish a legal empty socket from a jewel with a missing ID, and reject equipment with a missing ID.
- Additional whole-JAR checks of the synthetic example with seeds 1, 7 and 71, plus the exact-search example, produced byte-identical results before and after tuning.
- The full expanded Death/Storm request was frozen with its current exclusions (`Arcane Smoothie`, `Sacred Yarn Staff`, `Gorgonsbane Xiphos`) and its catalog before editing production code. Both JARs ran that snapshot with seed 7, population 250, 250 generations and five returned alternatives. All ranked equipment and socket selections, raw and scaled stats, detailed scores, warnings, unique-evaluation count and all 251 history entries match exactly.

Result JSON comparison treats declared metadata sets (`gearSlots`, `schools`, `bonusSchools`, `grantedTalentIds`, `setIds`) as sets. The expanded comparisons differed only in serialized `gearSlots` order; comparing two launches of the original JAR also showed changes in `schools` order. These arrays come from existing Java sets. Ranked alternatives, socket positions and history arrays are compared in their original order, and every number is compared exactly.

## Measurement and local artifacts

Measurements use fresh Java 22.0.2 JVMs on this machine, `-Xmx3g`, the same frozen request/catalog, `--quiet`, and wall time including startup, catalog load, search and JSON output. Maven builds and tests are outside the timed runs. These are local observations, not a general hardware-independent guarantee.

| Measurement | Before | After | Speedup |
| --- | ---: | ---: | ---: |
| Without profiler | 117.21 s | 29.99 s | 3.91x |
| JFR `settings=profile` | 101.19 s | 31.12 s | 3.25x |

Each row is one paired measurement, not a statistical average. Background load and JVM behavior affect wall time; the unprofiled pair reduced elapsed time by about 74%. Both pairs returned the same substantive results, as did the two original-JAR runs compared with each other.

Local snapshots, original and rebuilt JARs, timing commands, full outputs, comparison reports, JFR recordings and diagnostic scripts are retained under ignored `target/cpu-tuning-20261105/`. They are not required to run the optimizer and are not bundled in a checkout. The normal launch command remains the same:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-DeathStormOptimizer.ps1
```

The launcher uses the rebuilt `target/GearGenetics-1.0-SNAPSHOT.jar`. Benchmark output files are separate from normal launcher results.
