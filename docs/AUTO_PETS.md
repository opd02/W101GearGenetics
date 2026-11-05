# Automatic and predefined pets

The optimizer supports two pet modes. The Death/Storm example now uses **AUTO** with 20 named talents. Requests without a `petSearch` section keep the existing **PREDEFINED** behavior. Targets, level scaling, gear and socket constraints apply in both modes.

## Switch modes

Edit [examples/death-storm-request.json](../examples/death-storm-request.json). Change only this field:

```json
"mode": "AUTO"
```

to:

```json
"mode": "PREDEFINED"
```

Keep the rest of `petSearch` in place so switching back is easy. `template` and `talentPool` are ignored in PREDEFINED mode. `lockedTalents` applies in **both** modes; clear it to `[]` if no talents should be required.

Run the same launcher for either mode:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-DeathStormOptimizer.ps1
```

It rebuilds the pet catalog, including supported standalone talent definitions, then runs the selected mode. Build the updated JAR with `mvn package` first. Existing database/capture prerequisites are unchanged; see [PINS_AND_PETS.md](PINS_AND_PETS.md). Direct Java callers using older generated catalogs must rebuild them to obtain `petTalents` before enabling AUTO.

To keep a separate result:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-DeathStormOptimizer.ps1 -Output target/wizard101/my-auto-pets.json
```

## Configure automatic search and locks

The full example section is:

```json
"petSearch": {
  "mode": "AUTO",
  "template": "Hypothetical Death damage / Mighty",
  "talentPool": [
    "Death-Dealer", "Death-Giver", "Death-Boon",
    "Storm-Dealer", "Storm-Giver", "Storm-Boon",
    "Pain-Giver", "Pain-Bringer", "Armor Breaker", "Armor Piercer",
    "Spell-Proof", "Spell-Defying", "Death-Sniper", "Storm-Sniper", "Sharp-Shot",
    "Mighty", "Thinkin' Cap", "Powerful", "Health Bounty", "Pip o'Plenty"
  ],
  "lockedTalents": []
}
```

To require Mighty and Death-Dealer among the five manifested talents, change the last line to:

```json
"lockedTalents": ["Mighty", "Death-Dealer"]
```

Locks are unordered requirements, not numbered talent slots. Names match exactly after case, surrounding/repeated whitespace and apostrophe normalization, just like gear names. IDs also work when names are ambiguous. Unknown names, duplicate selections (including name/ID aliases for the same talent), ambiguous names and impossible locks fail clearly.

AUTO selects **exactly five distinct manifested talents** from `talentPool`. The pool must contain 5–20 distinct supported definitions. All locked talents must be in that pool, and at most five may be locked. Lock all five to hold the manifested combination fixed while searching gear and the star jewel.

The initial pool covers Death/Storm damage and accuracy, universal damage/resistance/accuracy, pierce, health, power-pip chance and attribute modifiers. Edit the names to change the pool; it is a configurable starting point, not an assertion that these are the best 20 talents for every target. Supported names and their formulas are in generated `target/wizard101/pet-catalog.talents.json` under `supported`. The catalog builder currently exports 154 supported definitions, while the request limits AUTO to 20 choices.

The named template supplies the pet body, body cards, base stats, age, unmodified trained/maximum attributes, socket restrictions and rounding policy. **Its predefined talents are replaced.** The template must resolve to one eligible PET record and be fully trained at Mega or Ultra with all five attributes. Qualifiers such as `{"name":"...","level":1}` are accepted. Existing PET gear locks/exclusions still restrict which template is eligible; choose a matching template when also locking PET gear. To change attributes or rounding, edit the template profile in [examples/pet-profiles.json](../examples/pet-profiles.json).

Automatic configurations are hypothetical. AUTO is rejected with `wizard.ownedOnly: true`; use PREDEFINED with actual owned pet records for that workflow. This feature searches modeled stat combinations; it does not simulate hatching, inheritance, breeding probability or obtainability.

## Star jewels are separate

`lockedTalents` requires **manifested** talents. A talent granted by the star jewel does not fulfill that lock. The optimizer chooses a compatible star jewel separately, and it may provide a supported talent outside the manifested pool.

Existing `lockedJewels` rules work unchanged. For example, `"PET:0"` fixes the star jewel by its existing jewel ID. Every talent granted by a locked jewel is reserved and excluded from manifested choices. Requiring the same talent in `lockedTalents` is a conflict, reported before search. Reserving jewel talents must leave at least five available manifested talents. Unlocked jewels are checked against every generated combination, including multi-talent jewels and attribute modifiers.

Mighty and other attribute modifiers, whether manifested or jeweled, recalculate affected talents before scoring and level scaling. Existing rounding policies are retained.

## Predefined pets and talent locks

PREDEFINED selects whole PET records from the catalog. In the supplied launcher, those are the seven configurations in [examples/pet-profiles.json](../examples/pet-profiles.json). Edit or add profiles there as before. No new talent combination is generated in this mode.

You can still use:

```json
"petSearch": {
  "mode": "PREDEFINED",
  "lockedTalents": ["Mighty", "Death-Dealer"]
}
```

Only predefined pets already manifesting both talents remain eligible. If none match the locks plus gear, ownership and socket rules, the request fails instead of silently ignoring a lock. Missing `petSearch` is equivalent to PREDEFINED with no talent locks and preserves the existing seeded search.

## Search, results and cost

The GA samples combinations, swaps one unlocked talent during pet mutations, and combines parent talent sets during crossover. It constructs and caches pet holders and compatible jewel domains as needed; it does not evaluate all combinations during normal GA search. Five talents from 20 permit 15,504 combinations before locks and jewel restrictions. Locks shrink that domain.

Generated IDs encode the template and canonical talent IDs, so different combinations have separate fitness-cache entries and talent-only changes count as PET-slot differences between alternatives. Results and console output include the selected five talent names, their definitions, the star jewel, fixed attributes, rounding, and raw/post-scaling stats. Input catalog records and preset profiles are not mutated.

`EXACT` also works for small pools: it enumerates every allowed combination and jewel choice, subject to the existing `search.exactLimit`. Expanding the pet domain happens before that bound is checked; keep pools small for exact searches. GA remains the practical choice for the full gear catalog. Both GA quality and runtime depend on the enlarged domain; more generations may help but do not prove optimality.

The standalone catalog field is optional `petTalents: [PetTrait, ...]`. Definitions require unique IDs and use the same supported effect/modifier format as manifested traits. JSON and SQLite preserve them, and saved request profiles retain mode, friendly names, pool and locks. Existing catalogs without the field remain valid for PREDEFINED.

## Validation and measured example

On 2026-11-05, all **61 Java tests and 22 Python tests passed** and the executable JAR was rebuilt. Ten automatic-pet tests cover GA/exact agreement, independent expected pet bonuses after scaling, 20-talent mutations/crossovers, manifested and star-jewel locks, all-five locks, illegal/ambiguous selections, cache identity, deterministic runs, catalog immutability, JSON/SQLite/profile persistence and CLI output. The real PowerShell launcher also completed a smoke run with Mighty and Death-Dealer locked; every returned pet retained both manifested talents.

The full current Death/Storm request uses seed 7, population 500, 280 generations and three elites. Its targets, exclusions and other settings were held constant between modes. Fresh Java 22.0.2 JVMs with `-Xmx3g` and `--quiet` produced:

| Run | Elapsed | Unique build evaluations | Best objective | Feasible alternatives |
| --- | ---: | ---: | ---: | ---: |
| Previous JAR, predefined pets | 64.40 s | 45,906 | 15.159632312316168 | 5 |
| New JAR, PREDEFINED | 65.02 s | 45,906 | 15.159632312316168 | 5 |
| New JAR, AUTO with 20 talents | 67.52 s | 59,805 | 15.38909809159373 | 5 |

PREDEFINED retained the same ranked equipment, jewels, stats, detailed scores, warnings and all 281 history entries as the previous JAR. Comparison normalized only existing unordered metadata sets; numbers and search/result ordering were otherwise exact. AUTO was about **4% longer** than PREDEFINED in this single paired observation and found a higher objective in this run. That is not a general runtime or search-quality guarantee. Timings include catalog loading/search/output, but exclude Python catalog preparation and builds; no other optimizer/test run was active during timing.

The first AUTO candidate manifests **Sharp-Shot, Pain-Giver, Death-Dealer, Health Bounty and Spell-Proof**, with **Armor Breaker Opal** separately jeweled. It meets all seven post-scaling mandatory requirements and has modeled post-scaling health **7,474.419908**. An independent Python audit recomputed equipment aggregation, pet/jewel attribute effects, all scaling projections and objective values for all five candidates, and checked pool membership and duplicate rules. This verifies implementation consistency, not retail formula accuracy.

Frozen inputs, both JARs, full outputs/logs, timings, the baseline comparison and audit are under ignored `target/auto-pets-20261105/`. The full AUTO output is `auto.json`; the locked launcher smoke output is `launcher-smoke.json`. They are separate from normal launcher results.
