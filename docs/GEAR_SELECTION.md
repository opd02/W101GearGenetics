# Lock and exclude gear by name

The optimizer already enforced ID-based locks and exclusions. Name selectors now resolve to those same constraints before search, so selection rules apply to every generation, exact search, sockets and post-scaling evaluation.

The Death/Storm request includes empty `lockedGear` and `excludedGear` sections ready to edit. Add preferences there and run the usual launcher. No names are locked or excluded by default.

## Plain names

```json
"lockedGear": {
  "HAT": "Headmistress' Dynamic Cap",
  "ROBE": "Shocking Abomination Suit"
},
"excludedGear": [
  "Arcane Smoothie"
]
```

This requires the named hat and robe and excludes every catalog gear record named Arcane Smoothie. Other slots and all unlocked sockets remain searchable. Gear locks do not fix the jewels on that piece; existing `lockedJewels` can separately lock socket IDs.

Names must be exact rather than partial, but capitalization, surrounding/repeated whitespace and straight/curly apostrophes are normalized. Names containing commas are accepted as JSON strings. No fuzzy match silently selects or excludes a different item.

## Multiple levels or versions

If an exact name matches several eligible records, a lock stops with a list of matching levels and identifiers. Supply the level of the item you intend to lock:

```json
"lockedGear": {
  "HAT": {"name": "Headmistress' Dynamic Cap", "level": 180}
},
"excludedGear": [
  {"name": "Arcane Smoothie", "level": 160, "slot": "WAND"}
]
```

`level` means the item's requirement, not the wizard's current or scaled combat level. `slot` is an optional qualifier; lock slots are already provided by the map key. In exclusions, omitting level/slot removes every gear version with that exact name across the supplied catalog. An unknown exclusion name is an error, so typos do not silently leave an unwanted item eligible.

Name locks respect character school/level, ownership, completeness, source/category exclusions and hypothetical-pet restrictions. They cannot force an otherwise ineligible record into the search. The normal socket/lock compatibility checks still apply afterward. Names are resolved within the supplied catalog; launcher filters may already have removed unresolved, ineligible or excluded categories of source records.

If multiple distinct records still share the same name, level and slot, the optimizer reports that ambiguity instead of guessing. The existing `lockedItems` ID map remains available for that uncommon case. If both a name and ID lock are supplied for one slot, they must agree; an explicit matching ID can distinguish otherwise ambiguous records. Locking an excluded item is an error.

## Search names without looking up IDs

After the WAD launcher has prepared its filtered catalog:

```powershell
& 'C:\Program Files\Java\jdk-22\bin\java.exe' -jar target/GearGenetics-1.0-SNAPSHOT.jar find --catalog target/wizard101/death-storm-expanded-catalog.json --name "Abomination" --school STORM --level 180
```

`find` accepts a case-insensitive partial name and optional `--slot`, `--school`, `--level` and `--limit` filters. Its level filter is the maximum character/item level. It displays full names, slots, item levels, eligibility schools, source, completeness and IDs for reference. Copy the name and optional item level into the request; an ID is unnecessary for an unambiguous name.

Use `--db target/wizard101/optimizer.sqlite` instead of `--catalog ...` to search the broader database. Discovery does not make an incomplete/unresolved item usable: optimization still applies the supplied request and catalog filters. Results are limited to 20 by default; use `--limit` up to 1000 or refine the query.

## Interactive and saved requests

The `init` command now asks for locks as a JSON object and exclusions as a JSON array, accepting the same string or qualified-object formats. Socket and owned-inventory prompts retain their existing ID format. JSON request files using `lockedItems`, `lockedJewels`, `excludedIds`, `excludedSources` or `excludedCategories` remain compatible.

Name resolution works on an internal copy. It does not replace the names in your input request with IDs, mutate caller constraints, or change the catalog. Profiles saved in JSON/SQLite and `--save-request` retain the name selections. Each search resolves those names against its current catalog and reports successful locks/exclusion counts in the result notes. A renamed/missing/ambiguous item after a catalog update must be corrected rather than being silently replaced.

## Validation

Eight Java tests cover normalized exact matching, ambiguous versions, optional level/slot qualifiers, all-version exclusions, ownership/eligibility, conflicting constraints, unknown names, JSON/SQLite round trips, CLI discovery/optimization and interactive names containing commas. They pass alongside all existing optimizer/scaling/socket tests: 48 Java tests total. All 21 Python tests also pass.

A packaged-JAR check against the local extracted catalog locked the level-180 Headmistress' Dynamic Cap by name and excluded Arcane Smoothie by name (18 source records). A separate 60-generation 180 -> 170 demonstration returned three feasible builds honoring both constraints. Its request/result/saved-request files are under ignored `target/named-gear-demo-*`; the main trial's result and unconstrained preset were preserved. The saved request retained friendly selectors and empty compiled ID maps. The packaged `find` command also located Shocking Abomination Suit by partial/exact text search.
