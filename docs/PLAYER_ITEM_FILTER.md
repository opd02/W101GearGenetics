# Player-item screening

The Java optimizer screens `source: "wizard101-wad"` gear and socket items before compiling legal domains. This is independent of `allowIncompleteItems` and the launcher's BROAD/FILTERED effect-mapping choice. It protects GA, exact search, direct catalog/SQLite runs and calls to `Optimizer.optimize`, including runs using older prepared catalogs.

## Why internal gear appeared

The earlier Death/Storm runs used FILTERED pools that dropped gear with unresolved mappings. BROAD was introduced to retain Howling Werewolf's Collar, whose Death Mastery effect is unmodeled. That also admitted other unresolved records. The Python name filter recognized separated words such as `polymorph`, `transformation` and `stats`, but missed camel-case `BiblioTransform` and `StatHat`.

The saved 700-generation Death/Storm run did select `HG_Dog_BiblioTransform_L170_StatHat` (`w101:template:1655216`). The earlier 280-generation collar run did not select it; that shorter result did not establish that the broad domain was clean. The Fire request exposed the same problem through `GR_AZ_Parasaur_Priest_A_StatHat` and unlocalized `Damage-Amulet-All-*` records. Optimizing longer cannot correct an invalid candidate domain.

## Rules and reporting

`PlayerItemFilter` rejects archive records for these reasons, in this order:

| Reason | Evidence |
| --- | --- |
| `non-player-metadata` | Explicit QA/test/NPC/developer/non-player category or availability marker. |
| `internal-item-marker` | Internal words in the name, archive member path or optional `rawObjectName`, including QA, test, debug, dev/developer, godmode, NPC, polymorph/transform/transformation, stat/stats, Mdeck, DoNotUse and MonsterItems. Word boundaries and camel-case boundaries are recognized; broad substrings such as `monster` are not banned. |
| `unresolved-display-name` | Missing/blank `displayNameKey` or an `unresolved-display-name` issue. Unlocalized records are withheld for review; this is not a claim that every such record is NPC gear. |
| `unresolved-eligibility` | Invalid level, unsupported equipment requirements/logic, conflicting school requirements, or unverified level-school semantics. Unknown restrictions cannot silently become unrestricted level-one gear. |

Named gear with unmodeled numeric/conditional effects or unresolved sets may still enter BROAD when the other rules pass. All original issues and `complete=false` status remain. Howling Werewolf's Collar remains searchable. Ordinary names such as Electric Monster Zapper and Testament Wand are not rejected by substring matching. Synthetic/manual catalogs and explicitly supplied hypothetical pet profiles retain their existing contracts; they are not treated as raw archive projections.

`SearchSpace.rejectedItems` maps rejected IDs to reasons. Result `warnings` include rejection counts by reason and deterministic examples. Name exclusions resolve against the original catalog, so existing internal-item blacklists remain compatible. Name and ID locks cannot override screening; rejected locks fail with a diagnostic. No raw catalog record is mutated, deleted or marked complete. The prepared JSON can therefore still contain rejected records; only the legal search domains exclude them.

## Using the fix

Rebuild with JDK 22 and `mvn package`, then use the normal launchers. No request field or target change is required. The expanded launcher can keep BROAD so the collar is retained. Direct Java callers must use the updated engine. Old result JSON files are historical and are not repaired automatically.

The filter reduces known contamination; it is not a verified obtainable-item allowlist. A localized, apparently ordinary internal or obsolete record can still need investigation, and conservative screening may withhold legitimate gear until its localization or requirements are resolved. The long-term guarantee requires independently verified, versioned availability/eligibility records. Setting `allowIncompleteItems: false` already restricts searches to declared-complete records, but the extracted catalog currently contains no independently verified complete retail records.

## Validation

Seven Java regression tests cover both internal hats, camel-case and monster paths, unlocalized amulets, uncertain requirements, the collar, harmless names, immutable inputs, inflated internal stats, GA/exact behavior, name/ID/socket locks, compatible existing name exclusions and same-name ambiguity. All 68 Java tests and 25 Python tests passed on 2026-11-05, and Maven package rebuilt the executable JAR.

Full launcher reruns used unchanged request snapshots and distinct result paths:

| Request | Population / generations | Rejected archive records | Unique evaluations | Alternatives meeting every mandatory target | Best objective / modeled health |
| --- | --- | ---: | ---: | ---: | --- |
| Fire/Balance | 750 / 250 | 6,380 | 82,025 | 5 | 14.47364307515524 / 7,332.284008 |
| Death/Storm | 750 / 700 | 6,381 | 97,576 | 5 | 14.948007976712356 / 7,437.885294 |

Fire screening rejected 4,742 internal-marker records, 287 unresolved-name records and 1,351 unresolved-eligibility records; Storm rejected one additional uncertain-eligibility record. These counts describe each supplied catalog before ordinary request locks/exclusions, not proven counts of NPC gear. Full domain audits confirmed that no rejected record entered any legal gear domain, and the locked collar was retained. Independent result checks verified every mandatory target, the combined objective, rank order, scaling metadata, seed/history, collar lock and absence of rejected gear/jewels in all alternatives. Request files remained byte-for-byte unchanged, including the user's two Fire exclusions and 250-generation setting.

Results: `target/wizard101/fire-balance-screened-results.json` and `target/wizard101/death-storm-screened-results.json`; logs use matching `*-screened-run.log` names. Runs took 98.9 and 292.6 seconds respectively. Original normal result files were preserved. Snapshots, original JAR, complete per-item rejection reports, audit helper and independent verification are under ignored `target/player-item-filter-20261105-215452/`. No archive extraction or retail verification was performed. Search feasibility remains feasibility within the provisional modeled domain.
