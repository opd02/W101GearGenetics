# Normalized JSON and storage contract v1

Executable fixtures: [catalog](../examples/catalog.json), [GA request](../examples/offschool-request.json), [exact request](../examples/offschool-exact-request.json). Fields are case-sensitive and enums uppercase. Duplicate JSON keys and unknown properties fail. Put unsupported data under extensions.

## Catalog and stats

Envelope: schemaVersion=1, gear array, jewels array, setBonuses array, baseStats map, extensions object. Optional `petTalents` contains supported standalone `PetTrait` definitions with unique IDs, for automatic pet planning; omitted means an empty list. JSON and SQLite preserve these definitions independently of gear and jewel records.

Complete Gear records explicitly provide stable ID, name, slot, positive level, schools, baseStats, sockets, complete, source and gameVersion. Empty schools means unrestricted; UNIVERSAL is a stat scope rather than a wizard eligibility school. Use distinct IDs for level-scaled or pet variants with different resolved values; preserve source template/instance IDs in extensions.

A StatBlock is:
```json
{"values":{"DAMAGE:UNIVERSAL":40,"DAMAGE:STORM":150,"HEALTH:UNIVERSAL":500}}
```

School-aware types: DAMAGE, RESISTANCE, ACCURACY, PIERCE, CRITICAL_RATING, BLOCK_RATING, PIP_CONVERSION_RATING, FLAT_DAMAGE, FLAT_RESISTANCE.

Global types (UNIVERSAL only): HEALTH, MANA, POWER_PIP_CHANCE, SHADOW_PIP_RATING, SHADOW_PIP_CHANCE, ARCHMASTERY_RATING, INCOMING_HEALING, OUTGOING_HEALING, ENERGY, STUN_RESISTANCE.

Values are finite raw additive bonuses. Percentage points use 100 for 100%; source fraction/percentage units must be resolved by the importer. No rating conversion occurs. Universal plus school bonuses are summed only in effective views.

Unknown typed stat keys are rejected. Preserve unsupported definitions inside extensions.unsupportedStats and mark the record incomplete until resolved. Extensions round-trip but do not affect score.

## Sockets and pets

Gear sockets are ordered objects with type and optional allowedSchools. Empty allowedSchools means unrestricted. Types: CIRCLE, TEAR, SQUARE, TRIANGLE, SWORD_PIN, SHIELD_PIN, POWER_PIN, PET.

Jewels/pins have ID, name, level, school eligibility, type, statBonus, optional bonusSchools/gearSlots restrictions, cards and provenance. Compatibility checks shape, holder slot and affected bonus schools (including actual stat scopes), plus wizard eligibility. Importers must supply accurate pin restrictions; socket unlock cost/state is not modeled.

Result jewel arrays preserve null entries for empty sockets. LockedJewels keys use SLOT:zero-based-index.

A pet is a PET-slot Gear with:
```json
{"pet":{"name":"Owned pet","hypothetical":false,"traits":[]}}
```

Manifested traits need ID, name, statBonus and optional itemCards. At most five unique IDs are allowed. A pet jewel occupies its separate PET socket. Jewel grantedTalentIds and petTalents declare talent-definition IDs; compatibility rejects overlap with manifested talents, including multi-talent jewels. Do not double count talent bonuses inside Gear.baseStats. Hypothetical configurations must be explicit and cannot enter owned-only search.

Pets may now supply age (-1 unknown, or native stages 0 egg through 7 ultra), trained attributes, maximumAttributes, fullyTrained and an explicit rounding policy (RAW or NEAREST_WHOLE). Trait effects use primaryStat1, primaryStat2, secondaryStat, secondaryModifier and a typed StatBlock of coefficients. Trait attributeModifiers change attributes before any effects are evaluated. These modifiers currently require a fullyTrained profile with all five unmodified trained/max values equal. RAW preserves fractions; optional NEAREST_WHOLE rounds each talent's combined dynamic contribution per stat with halves upward. This policy requires independent retail verification.

PET jewels may contain petTalents and minimumPetAge, separately from their wizard-level requirement. Imported age requirements must be met and known-stage pets must reach Ancient to use the socket. Unknown ages retain compatibility with legacy pre-resolved jewels that supply no age requirement. New dynamic jewels are evaluated against the selected pet's attributes, so Mighty from a jewel also affects manifested talents. Fixed observed statBonus values remain supported without dynamic effects. See [PINS_AND_PETS.md](PINS_AND_PETS.md) for source mapping, example profiles and validation limits.

Optional request `petSearch` selects `mode: "PREDEFINED"` (default) or `"AUTO"`. AUTO requires a unique `template` PET gear name selector and `talentPool` of 5–20 supported exact talent names or IDs from catalog `petTalents`. The fully trained Mega/Ultra template retains body/attributes/rounding while its manifested traits are replaced with five distinct pool definitions. Optional `lockedTalents` requires up to five manifested talent names/IDs in either mode; PREDEFINED filters existing pets, while AUTO requires locks to belong to the pool. Star-jewel talents are separate and cannot duplicate manifested locks. AUTO is hypothetical and rejects owned-only requests. See [AUTO_PETS.md](AUTO_PETS.md) for switching modes, examples, locks and exact-search behavior.

## Sets, cards, provenance and completeness

Gear setIds reference catalog setBonuses records:
```json
{"setId":"source:set","pieces":2,"stats":{"values":{"DAMAGE:UNIVERSAL":8}},"itemCards":[]}
```

Reached thresholds add once. Threshold records are incremental, not cumulative; a three-piece record must not repeat the two-piece bonus. Duplicate thresholds and missing referenced rules are rejected. Include referenced set rules in the import document. Conditional/nonadditive effects remain unsupported extensions.

itemCards retains stable spell/effect identifiers as metadata. They do not inject hidden combat/fitness behavior. Category/source are stable strings for exclusion filters. GameVersion/importedAt preserve provenance; storage stamps missing import times. Source capture time may be retained separately in extensions.

Complete is an importer assertion, not proof of correctness. Complete JSON requires explicit level/restriction/stat/socket-or-type/source/version fields. DTO defaults and legacy records are incomplete. Incomplete records are skipped unless the request opts in and can never overwrite a complete stored record.

## Base data and owned inventory

Catalog baseStats maps exact SCHOOL:level keys to StatBlock records; there is no interpolation. Wizard baseOverrides replaces named values. If no source exists, overrides are explicit manual base input; omitted entries are zero. An empty override intentionally requests zero base stats. Missing source and no override fails.

OwnedItemIds represents one copy per ID; ownedQuantities overrides counts, including zero. Ownership is separate from global catalog membership. Locked jewel copies are reserved first, and a physical copy cannot fill multiple sockets. Different owned pet/equipment variants need distinct IDs/resolved records.

## Gear name constraints

Optional `lockedGear` is a map from selected GearSlot keys to exact name selectors. Optional `excludedGear` is an array of selectors. A selector can be a string (`"Item Name"`) or an object (`{"name":"Item Name","level":180,"slot":"HAT"}`). `name` is required and nonblank, `level` is an optional positive item requirement, and `slot` is an optional qualifier. A lock's explicit slot qualifier must agree with its map key. Unknown selector properties fail.

Matching normalizes Unicode compatibility, straight/curly apostrophes, whitespace and case. It does not perform fuzzy/substring constraint matching. A lock must resolve to exactly one eligible gear record after school/character-level, inventory, completeness and existing exclusion filters. Multiple matches are errors with candidates listed. Exclusions expand to all matching gear IDs; unknown names are errors. Object qualifiers narrow exclusions by level/slot. These fields select gear records, including explicit PET configurations, not jewel definitions.

The engine compiles selections on a copied request into existing `lockedItems` and `excludedIds`; caller data and saved name preferences remain intact. Name and ID rules can coexist but cannot conflict. Existing socket compatibility and locked-copy checks remain authoritative after resolution. JSON/SQLite profiles preserve the selectors. See [GEAR_SELECTION.md](GEAR_SELECTION.md) for examples and the `find` CLI.

## Character level and post-scaling targets

`wizard.level` is the current character level and always controls gear/jewel eligibility and exact base-stat lookup. Optional `wizard.scaledLevel` selects the combat level used to evaluate **all** mandatory and optional targets. For example, `{"school":"STORM","level":180,"scaledLevel":170}` admits gear/jewels through level 180 but scores them after level-170 scaling. Existing target values stay in the requested post-scaling units.

Omit `scaledLevel` or use null to retain raw additive scoring. A supplied level must be positive and no higher than the character level. Supplying the same band deliberately enables cap enforcement even without a lower destination band. The bundled versioned table has bands 10 through 180, each covering ten levels; missing bands fail instead of being extrapolated. The current trial uses character level 180 and combat level 170.

The provisional model aggregates gear, jewels, pet effects, set increments and character-level base values first, then computes each target as `min(rawEffective/sourceCap,1) * destinationCap`. Universal bonuses are included in each school's raw effective value before applying that school's cap. Scaled school-specific targets must name a school; `UNIVERSAL` is rejected for those targets because there is no unique universal/specific decomposition after separate school caps. Use explicit school targets, including all seven if needed. Global targets such as health still use `UNIVERSAL`.

Supported scaled stats are the nine school-aware types above, plus HEALTH, MANA, POWER_PIP_CHANCE, SHADOW_PIP_RATING, ARCHMASTERY_RATING, INCOMING_HEALING and OUTGOING_HEALING. Other scored types fail clearly when scaling is enabled. Raw fields are retained even when they lack a scaling rule. Zero source/destination caps yield zero for that stat. WAD data from a client revision different from the bundled table is rejected.

Optional `wizard.scaledCapOverrides` is a StatBlock of user-supplied post-scaling saturation ceilings, for example `{"values":{"DAMAGE:DEATH":235,"DAMAGE:ICE":243}}`. Nonempty overrides require `scaledLevel`; values must be positive and finite, with supported explicit-school keys (global stats use UNIVERSAL). Keys with zero bundled source/destination caps are rejected. For an overridden key, evaluation is `min(rawEffective / bundledSourceCap * bundledDestinationCap, override)`: the ratio is unchanged, and only the ceiling is replaced. These scenario inputs do not alter the bundled table or claim verified retail rules. Null/omitted or empty overrides retain default behavior. All request/profile round trips preserve them.

Result JSON adds a top-level `scaling` descriptor (null when disabled) containing both levels, table version, model and source hashes. Each solution retains `rawEquipmentStats` and `rawCharacterStats`, and adds `targetStats`, which contains the values used to evaluate targets. `effectiveSchoolStats` and fitness component `actual` values use the post-scaling view when enabled. In this view school-specific entries are complete school totals with no added universal component; raw universal bonuses remain in the raw fields. Alternative deduplication also uses the evaluated view. Saved request profiles retain `scaledLevel` through JSON and SQLite.

With nonempty cap overrides, `scaling.capOverrides` records their key/value map and `model` is `PROPORTIONAL_WITH_USER_CAPS_UNROUNDED`. Source hashes still identify the bundled ratio table; the warning distinguishes user ceilings from captured values. The optional descriptor field is omitted and the original model label retained without overrides.

The model is unrounded and does not apply the unresolved floor-percent metadata or additional combat curves/rating conversions. Those limitations and natural-base-health omissions are reported in warnings. See [LEVEL_SCALING.md](LEVEL_SCALING.md) for data provenance, reproduction and validation boundaries.

## SQLite schema and imports

| Table | Purpose |
| --- | --- |
| schema_info | Version guard |
| items | ID, kind, slot, level, completeness, JSON; indexed slot/level |
| set_rules | Set-ID/threshold and JSON |
| base_stats | Exact school/level and raw JSON |
| catalog_extensions | Unknown envelope metadata |
| profiles | Named request JSON |
| ownership | Profile/item quantities |

Imports validate/upsert within a transaction. IDs cannot change kind; complete records cannot regress to incomplete. Existing extension keys survive updates when absent from incoming data. Absent items/rules are not deleted, and profiles/inventory are untouched. Catalog export omits player preferences; profile export and resolved-request saving retain those independently.

Queries first filter indexed slot/level, then typed school eligibility/stat minimums. Payloads preserve extensibility without a fixed column for every stat.

## Python adapter input

The optional standard-library normalizer consumes source, gameVersion, capturedAt and items. Items use kind=gear or jewel, the metadata above, and stats arrays:
```json
{"stat":"DAMAGE","school":"STORM","value":60}
```

It creates StatBlock maps, retains unknown metadata/bonuses as extensions, flags unsupported stats incomplete, and rejects duplicate IDs/known-stat definitions, missing fields and invalid known units/scopes. Java performs full cross-record validation. See [the fixture](../bridge/fixtures/items.json).

A WAD manifest or aggregate character snapshot is not a gear catalog. A future decoder must supply explicit individual records through this contract.
