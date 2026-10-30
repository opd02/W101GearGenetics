# Normalized JSON and storage contract v1

Executable fixtures: [catalog](../examples/catalog.json), [GA request](../examples/offschool-request.json), [exact request](../examples/offschool-exact-request.json). Fields are case-sensitive and enums uppercase. Duplicate JSON keys and unknown properties fail. Put unsupported data under extensions.

## Catalog and stats

Envelope: schemaVersion=1, gear array, jewels array, setBonuses array, baseStats map, extensions object.

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

Manifested traits need ID, name, resolved statBonus and optional itemCards. At most five unique IDs are allowed. A pet jewel occupies its separate PET socket. Jewel grantedTalentIds can declare talent-definition IDs; compatibility rejects overlap with manifested talents. Importers must supply these IDs to validate talent duplication; resolved numeric bonuses alone cannot identify a talent. Do not double count talent bonuses inside Gear.baseStats. Importers supply actual resolved values; Mighty/attribute formulas and arbitrary talent synthesis are unsupported. Hypothetical configurations must be explicit and cannot enter owned-only search.

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
