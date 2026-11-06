# Raid level-scaling research

Reviewed 2026-11-04. Opt-in post-scaling calculation and target scoring are now implemented using the versioned proportional cap model described below. Exact retail rounding, floor semantics and server behavior remain unverified.

## Publicly documented behavior

[KingsIsle's advanced-combat update notes](https://eu.wizard101.com/game/community/updatenotes/september2024) confirm that higher-level wizards have their stats scaled to the level supported by the content. Spell level gating is a separate part of advanced combat.

[Final Bastion's original level-scaling guide](https://finalbastion.com/w101-pvp-guides/w101-general-pvp-guides/wizard101-level-scaling/) describes preserving a stat's fraction of its natural level-band maximum and applying the destination band's maximum, with the fraction limited to one:

```text
projected stat = min(unscaled character stat / source-band maximum, 1)
                 * destination-band maximum
```

Its numerical example and developer-supplied tables date to 2023. The relationship is public; those historical tables should not be treated as current constants. This is a stat-by-stat calculation, not multiplication by target level / character level. It also implies saturation: bonuses above the applicable source-band cap do not keep increasing the scaled stat under this model.

## Direct local evidence

The installed retail `Root.wad` contains `LevelScaledData.xml`. It was read without changing game files or attaching to a process, decompressed with length validation, then decoded using the existing Imcodec DLLs. A small diagnostic helper under ignored `target/scaling-inspect/` handled decoding; production extraction code was not changed.

The decoded file has 126 records: seven primary schools at 18 level bands, 10 through 180. Each primary-school record includes separate values for the bonus/damage schools, plus global values. For example, a Storm-primary wizard's Death damage cap is not the same as a Death-primary wizard's Death damage cap.

Provenance of the installed snapshot:

- Client executable SHA-256: `544ab726ef7b920a5ccb67aa3d06b245db7e0012317d2fe7856d58c17c31cf7e`.
- `Root.wad` SHA-256: `4e09b3da242a6f5650624497359dfaebf6cd54e72a0ae1ee21bdcf7436b42c66`.
- Uncompressed `LevelScaledData.xml` SHA-256: `518dc84777d35fb1bde8932887ab973603e3e7a22742d6456f95858240b8d1ef`.
- These executable/archive hashes match the project's earlier retail catalog capture. This is the installed client revision, not a claim that today's remote server uses identical values.

Local diagnostic outputs are `target/LevelScaledData.bin`, `target/level-scaled-data.json`, and `target/raid-scaling-research.json`. The complete decoded game file is intentionally kept under ignored `target/`; selected factual values are recorded below for durable recall.

## Storm-primary: level 180 to 170

Values below are taken from the **Storm-primary** records; percentage fractions were multiplied by 100. The example column uses the public capped-ratio model. These are hypothetical full unscaled character stats, not a new optimizer run or actual raid observation.

| Stat | Level-180 cap | Level-170 cap | Example unscaled stat | Projected at 170 |
| --- | ---: | ---: | ---: | ---: |
| Health | 11,975 | 11,218 | 10,000 | 9,367.85 |
| Storm damage | 220 | 218 | 200 | 198.18 |
| Death damage | 371 | 368 | 350 | 347.17 |
| Storm resistance | 66 | 56 | 60 | 50.91 |
| Death resistance | 66 | 56 | 60 | 50.91 |
| Storm pierce | 60 | 59 | 60 | 59 |
| Death pierce | 60 | 59 | 60 | 59 |
| Storm accuracy | 67 | 67 | 35 | 35 |
| Death accuracy | 57 | 57 | 20 | 20 |
| Power-pip chance | 134 | 134 | 100 | 100 |

For comparison, the captured Death-primary profile's native Death damage values are 216 at 180 and 213 at 170. Applying those to a Storm wizard's Death damage would give the wrong result. These captured constants are not established runtime maxima: the user reports 235 Death / 243 Ice inside level-170 content. The Death/Ice request now uses explicit user cap overrides rather than presenting the captured 213 / 221 values as verified gameplay limits.

For the current trial's desired post-scaling thresholds, the basic ratio model requires approximately these unscaled level-180 stats:

| Desired stat inside level-170 content | Approximate required unscaled stat at 180 |
| --- | ---: |
| 340 Death damage | 342.771739 |
| 107 Storm damage | 107.981651 |
| 54 Death pierce | 54.915254 |
| 36 Storm pierce | 36.610169 |

The requested accuracy and power-pip thresholds are unchanged by these two bands' cap ratios. Do not rely on a rounded boundary value without checking the game's rounding behavior.

## Request-supplied post-scaling ceilings

Optional `wizard.scaledCapOverrides` replaces selected saturation ceilings while retaining the bundled source/destination ratios. This is an explicit scenario calibration using user-supplied values; it does not rewrite the captured table or establish why its values differ from observed gameplay. Existing requests without overrides keep their exact prior arithmetic.

The Death/Ice request uses:

```json
"wizard": {
  "school": "DEATH",
  "level": 180,
  "scaledLevel": 170,
  "scaledCapOverrides": {
    "values": {"DAMAGE:DEATH": 235, "DAMAGE:ICE": 243}
  },
  "baseOverrides": {"values": {"POWER_PIP_CHANCE:UNIVERSAL": 40}}
}
```

For an overridden key the formula is `min(rawEffective / bundledSourceCap * bundledDestinationCap, userCeiling)`. All gear, jewels, pets and base contributions are aggregated before this calculation. The Death/Ice ratios remain 213/216 for Death damage and 221/224 for Ice damage. Its user-approved minimums are now 220 / 228, up to 15 below the 235 / 243 ceilings. The modeled raw totals needed to meet those minimums are approximately 223.098592 / 231.095023; saturating the ceilings requires about 238.309859 / 246.298643. Targets remain in post-scaling units. Replacing a ceiling does not grant free damage or multiply all raw damage by a larger destination cap.

Overrides require scaling to be enabled, positive finite values, supported keys and explicit schools for school-specific stats. A key with a zero source/destination cap cannot be overridden because its ratio is unavailable. Other stats, raw totals, current-level eligibility and client-revision checks retain their existing behavior. Null/omitted or empty overrides retain the default model; no override is inferred from target values.

JSON/SQLite profiles and CLI saved requests preserve the optional StatBlock. Python pool preparation still uses character level, school and slots and passes the request to Java unchanged. Result `scaling.capOverrides` records the explicit ceilings and uses model `PROPORTIONAL_WITH_USER_CAPS_UNROUNDED`; warnings state that ratios are retained and the calibration is not independently verified. The ordinary model label and JSON descriptor remain unchanged when no overrides are supplied.

## What still needs verification

- The public ratio description and client data provide a concrete projection model, but a controlled before/after raid or level-preview observation is still needed for exact runtime validation.
- `m_statsFloorPercent` exists in the same resource. For the displayed percentage stats it contains zero for damage, accuracy, pierce and power pips, and `0.05` for resistance; it also has nonzero values for several ratings. The field's runtime meaning and order of application have not been established here. The resistance projections above are the basic public ratio calculation, not proof of floor behavior.
- Display rounding versus internal precision remains unverified. Damage/resistance curves, critical/block conversions, and combat damage are separate issues; the table does not simulate a spell hit.
- The resource alone does not establish which band every current raid selects, nor rule out server-side overrides or changes after the captured client revision. Apply the example only to content confirmed to scale to 170.
- Natural base health must be included in the full unscaled health input. The existing trial has no verified base health override and therefore does not provide a complete natural health total for this projection.

## Using post-scaling targets in GearGenetics

An actual level-170 wizard and a level-180 wizard fighting in level-170 content are different requests. Set `wizard.level` to the current character level and `wizard.scaledLevel` to the desired combat level:

```json
"wizard": {
  "school": "STORM",
  "level": 180,
  "scaledLevel": 170,
  "baseOverrides": {"values": {"POWER_PIP_CHANCE:UNIVERSAL": 40}},
  "ownedOnly": false
}
```

All existing target values now mean **post-scaling stats**, including mandatory minimums/maximums and optional maximization preferences. Gear and jewel eligibility, owned-item checks and natural base-stat lookup use the current level. Sockets and dynamic pet jewels are evaluated after their raw effects are aggregated, using the same scaled score as the main search. Values above a cap cannot inflate the objective.

The Death/Storm preset now uses 180 -> 170; its complete target list was retained unchanged. Other existing presets omit `scaledLevel` and retain raw behavior. The interactive `init` command prompts for current and optional scaled levels. Explicitly selecting the same band applies caps; omitting scaling does not. Missing table bands and unsupported target scopes fail clearly. There is no extrapolation above the table's level-180 band.

The result reports both levels, cap-table version and source hashes in `scaling`. Per-solution `rawEquipmentStats` and `rawCharacterStats` remain unscaled; `targetStats`, effective school totals and fitness component `actual` values reflect the scaled view. School-specific `UNIVERSAL` targets are rejected in this mode: use named schools, since each school's total can have a different cap. Raw universal bonuses are still preserved and included before each school is scaled. Global health/power-pip targets use `UNIVERSAL` as before.

The table is bundled at `src/main/resources/me/opd/scaling/retail-20261031.json` and loaded by `StatScaling`. Normal optimization requires neither the game files nor Python. It contains normalized values plus source/floor metadata; floor metadata is retained but deliberately not applied while its semantics are unknown. WAD catalog revision checks prevent using these caps with mismatched client data.

To regenerate the bundled resource from the previously decoded capture and provenance report:

```powershell
python bridge/build_scaling_table.py --decoded target/level-scaled-data.json --provenance target/raid-scaling-research.json --output src/main/resources/me/opd/scaling/retail-20261031.json
```

The builder explicitly maps supported school/stat fields and units, preserves primary/off-school distinctions, retains floor fractions separately, and rejects duplicate/invalid rows. Java and Python regression tests cover these contracts. The decoder diagnostic mentioned above is still separate from production archive extraction.

The existing natural-base-stat and pet/capture limitations still apply. This implementation makes constraints correct for the declared proportional model; it does not claim independent verification of live raid internals.
