# Gear database provenance and game-update refresh

Reviewed: 2026-11-06. This describes the existing offline catalog pipeline and a recommended manual refresh process. Automatic patch comparison and refresh orchestration are proposed additions, not implemented features.

## Where the database comes from

The bulk database comes from installed Wizard101 game files. The running game session helped identify the retail installation path, but extraction never attached to game memory. No running game is required to repeat it.

```text
Installed Wizard101 WAD archives
  -> Imcodec decoder + our C# capture tool
  -> raw templates, supporting resources and provenance
  -> Python stat/requirement/socket normalization
  -> SQLite catalog
  -> request-specific gear pool
  -> Java optimizer
```

`TemplateManifest.xml` supplies template IDs and their archive locations. The capture tool follows those entries across Root and world-data WADs. Item stats are resolved through canonical effect definitions and stat tables; English language resources supply display names.

The original 2026-10-31 snapshot covered **27 WADs**:

| Stage | Count |
| --- | ---: |
| Manifest entries discovered | 137,423 |
| Templates decoded | 137,421 |
| Decode failures | 2 |
| Item templates retained | 80,162 |
| Provisional gear records | 60,646 |
| Provisional jewels and pins | 4,416 |
| Independently verified complete records | 0 |

The original capture is at `target/retail-capture-20261031-b/`. The staging and optimizer databases are `target/wizard101/staging.sqlite` and `target/wizard101/optimizer.sqlite`. These generated artifacts exist locally under ignored `target/` and are absent from a fresh checkout.

A prepared Storm-primary subset and executable are now deliberately tracked under `runtime/storm-death/`. Run `Run-StormDeathPortable.ps1` on a fresh checkout with Java 22+ and PowerShell; no original capture, database or extraction tool is required. See the [portable runtime guide](../runtime/README.md) for coverage and republishing. This distributable snapshot does not replace the full source catalog or archive backups.

The "total database" means the captured installed manifest's contents. It does not establish coverage of every retail item or current obtainability. Archives include obsolete, internal, NPC and test definitions. Java screens recognizable invalid player items, but all archive-derived records remain provisional (`complete=false`). Passing screening does not verify availability or gameplay accuracy.

See [WAD_CATALOG.md](WAD_CATALOG.md) for extraction commands, dependencies, source hashes, coverage and mapping limits, and [PLAYER_ITEM_FILTER.md](PLAYER_ITEM_FILTER.md) for search-time screening.

## Refreshing after a game update

The existing extraction pipeline can be rerun against an updated installation. A refresh should preserve the old snapshot and validate the new one before switching optimizer inputs.

1. **Finish patching and preserve a stable client snapshot.** Keep previous captures and databases. Each new capture records executable, decoder and opened archive hashes so its inputs can be identified. Extract from a stable snapshot rather than files that may be patched during capture.

2. **Check decoder compatibility.** A content-only patch may work with the existing Imcodec libraries. A schema-changing patch may require regenerated type definitions and rebuilt decoder libraries. External decoder binaries are not bundled with this repository. Successful deserialization alone does not prove that newly introduced fields are understood correctly.

3. **Capture and rebuild into new paths.** Run `bridge/WadCatalog`, then `bridge/build_wad_catalog.py`, then the Java CLI's `import-json` command. Follow the reproduction commands in [WAD_CATALOG.md](WAD_CATALOG.md). Rebuild the full normalized snapshot: a changed shared stat table can alter existing items even when their individual template bytes have not changed. Capture and staging tools require new output paths to preserve earlier artifacts.

4. **Compare revisions.** Review added, changed and missing template IDs, decode failures, unresolved mappings and resource issues. Compare representative obtainable items with visible in-game item cards. A dedicated automatic patch-diff report would be useful, but is not currently part of this workflow. Compare normalized records and shared resources as well as individual template hashes.

5. **Refresh related inputs.** Pet resources must match the new gear capture's executable and Root archive hashes; the pet builder rejects mismatched revisions. Recapture resources from the same stable snapshot and rebuild pet projections. See [PINS_AND_PETS.md](PINS_AND_PETS.md). Level-scaling tables need separate attention: they are bundled in the JAR, and extraction currently uses a diagnostic helper outside production archive capture. Updating gear alone does not update scaling. Revision checks can reject mismatched catalog and scaling data. See [LEVEL_SCALING.md](LEVEL_SCALING.md) for the capture and normalization workflow. A new scaling snapshot also needs appropriate version/provenance updates and a rebuilt JAR; `build_scaling_table.py` currently has a hardcoded snapshot version label.

6. **Validate, then switch launcher inputs.** Review coverage and mapping reports, check database integrity, and verify representative gear, sockets, pins and pets. If code or bundled resources change, run the relevant tests and rebuild the JAR. Point launchers at the validated new database, staging database and matching resource capture, regenerate request-specific pools, and rerun searches. Inspect feasibility and mandatory deficits; successful process exit alone does not mean a build meets its targets. Expanded launchers replace shared generated catalog/pet outputs, so run them sequentially and preserve distinct result paths.

## Import behavior and preserving user data

Java imports add or update records by stable ID, such as `w101:template:<manifest-id>`. They **do not delete records absent from the incoming catalog**. Importing a new snapshot over the old database can therefore retain stale entries. Existing complete records also cannot be overwritten by incomplete imports.

A fresh versioned database is the clearest representation of a new patch. Preserve or migrate saved profiles and owned inventory deliberately if switching databases; do not discard user data with the old catalog. Reimporting into an existing database preserves profiles and inventory, but does not solve stale-record reconciliation.

Keep original captures, archive snapshots and revision hashes so prior results remain reproducible. Unknown effects and unsupported requirements must remain visible rather than silently becoming zero bonuses or unrestricted eligibility.

## Useful next improvement

The pieces for a manual refresh already exist. A future refresh launcher could coordinate capture, revision comparison, validation and switching databases, with a report of added, changed and missing records. It should keep player ownership and preferences separate from catalog replacement and handle pet and scaling revisions explicitly.

For overall architecture and current validation limits, see [PROJECT_MAP.md](PROJECT_MAP.md).
