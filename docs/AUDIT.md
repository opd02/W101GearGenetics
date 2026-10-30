# Repository audit and decisions

The repository was inspected before implementation: a small Java 22 Maven project with 19 source files, three manually maintained CSVs and no tests/dependencies. Local edits changed scalar card penalties and GA defaults. Population 250, mutation 0.5 and tournament 7 were retained as configurable defaults; card penalties were superseded by user targets. Original CSVs are unchanged.

| Area | Finding | Change |
| --- | --- | --- |
| StatBlock | Scalar stats lost school/universal distinctions; one hardcoded wizard's base values | Typed stat/school map, raw/effective views, exact school/level sources and overrides |
| Gear | Only name, slot, stats, sockets | Extended existing class with IDs, eligibility, provenance, sets, effects, resolved pet and extensions |
| GearLoader | split(",") broke quoted names; fixed indices; no row validation or metadata | Commons CSV named headers, strict booleans/numbers, row errors, stable migration IDs, explicit school mapping |
| Fitness | Hardcoded weights/reference values, arbitrary card goals, soft score could dominate requirements | Normalized targets and lexicographic feasibility/deficit/objective ranking |
| Individual | Null pet/mount dereference, name-based 8-damage pair, arbitrary trait list | Positional sockets, generic set rules, talents attached to chosen pet |
| GeneticUtils | Math.random/new Random; shared jewel lists/pool records; null/empty crashes; unbounded duplicate loops | One seeded RNG, copied gene containers, precompiled domains, structural locks, bounded choices |
| PopulationInitializer | nextInt(size-1) omitted last item and crashed singleton pools; repeated filtering | Full range, precompiled domains, clear missing-slot errors |
| Pet talents | Comments expected five; initialization/crossover forced six; could loop forever | Up to five unique manifested talents, separate PET jewel socket, resolved imported values |
| Sockets | Empty sockets shifted later jewels; no shape validation during aggregation | Positional null entries and compatibility checks, locks, inventory copy counts |
| GA/Selection | No elitism or reproducibility/cache; only one result; new RNG per tournament | Seed/configuration, elitism, cached scores, progress/history, ranked alternatives |
| CopyUtils | Only old scalar fields copied; pet traits/jewel lists shared elsewhere | Independent graph copy for external callers; search copies gene containers and treats catalog read-only |
| StatBounder | Reported shadowpip as damage; seven nested loops | Explicit-school additive gear bounds, excluding socket/pet/set rules |
| Main | Fixed Windows paths, no user inputs | Packaged CLI, interactive request creation, JSON profiles, SQLite/import/export/inventory |
| CSV data | Missing school/level restrictions, universal split, IDs, sets and owned pets; some labels disagree with values | Preserved unchanged and marked incomplete on migration; no inferred authoritative records |

## Preserved architecture

Gear slots, Gear/Jewel/Socket/Pet/PetTrait types, Individual genome, initialization/tournament/crossover/mutation structure, Maven and CSV data remain. Internal method/field APIs changed where necessary for the typed model; arbitrary scalar-field source compatibility is not promised. The Java engine remains independent of Python and CLI. No combat simulator or replacement project was introduced.

Universal bonuses remain distinct and are added only in effective per-school views. Flat/percentage stats and ratings/chances use different types. Equipment/base/character totals are separate. Reached set thresholds are additive increments applied once. Item names trigger no special effects.

Catalog objects are mutable JSON DTOs. Search treats them as read-only and copies gene containers; callers must not mutate inputs during a run. CopyUtils provides an independent graph copy when needed. No speculative dominance pruning removes maximum-constrained or set-relevant candidates.

Game curves, damage caps, level progression, pet attribute formulas and upstream school-array order are not guessed. Unknown data is retained in extensions or rejected/flagged explicitly. Legacy stat mapping is documented as an assumption. Historical talent rows never become arbitrary owned-pet combinations.

SQLite uses indexed identity/slot/level columns with extensible versioned JSON payloads, plus separate profiles/ownership. Imports are transactional, protect complete records from incomplete replacement, and preserve unknown extension keys. Deletion/reconciliation of stale records/rules is deferred to explicit future operations.
