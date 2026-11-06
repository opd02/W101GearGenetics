# Portable Storm/Death optimizer

This tracked runtime lets a fresh checkout run the current **Storm-primary, level-180, scaled-to-170** Death/Storm request. It includes the executable with its Java dependencies and scaling table, plus a compressed, prepared BROAD catalog containing gear, jewels, pins, seven predefined pet profiles and supported AUTO talent definitions.

## Run on another Windows computer

Install Java 22 or newer. Windows PowerShell 5.1 or PowerShell 7 is sufficient. From the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-StormDeathPortable.ps1
```

No Maven build, Python, .NET SDK, Imlight, Imcodec, game installation or extracted database is required. The launcher finds Java through `JAVA_HOME`, then `PATH`, then the original machine's conventional JDK 22 path. You can select Java explicitly:

```powershell
.\Run-StormDeathPortable.ps1 -JavaPath 'C:\Path\To\jdk-22\bin\java.exe'
```

It uses [examples/death-storm-request.json](../examples/death-storm-request.json) directly; targets, locks and search settings are not copied or weakened. The default is the full seeded 750-population / 700-generation AUTO search. Allow time for candidate preparation before generation progress appears. The default Java heap limit is 3 GB; `-JavaHeap 2g` changes that limit if needed.

The launcher verifies bundle hashes, unpacks the catalog into disposable `target/portable/storm-death/`, and writes results to `target/wizard101/storm-death-portable-results.json`. A missing or changed cache is reconstructed from the tracked compressed catalog. `mvn clean` can remove the cache and results without removing this bundle. Use `-Output` to choose another result file.

Inspect `solutions[].fitness.feasible` and mandatory target deficits. A completed search does not guarantee feasibility. Archive-derived stats and availability, pet calculations and proportional scaling remain provisional. The supplied request models 40 base power-pip chance and omits natural base health.

## What is tracked

| File | Purpose |
| --- | --- |
| `storm-death/optimizer.jar` | Ready-to-run executable, bundled dependencies and scaling table |
| `storm-death/catalog.json.gz` | Prepared catalog; original per-record provenance and unresolved issues retained |
| `storm-death/manifest.json` | SHA-256 hashes, counts, source revision and catalog coverage |

These are ordinary Git files, not Git LFS pointers or downloads. They are an intentional distributable snapshot; `target/` remains ignored. Original raw captures, SQLite databases, profiling files and historical results stay separate and are not required on the laptop. They are preserved locally, not deleted by bundle creation.

## Editing the request and coverage

You can change targets, exclusions, locks and search effort, or switch AUTO/PREDEFINED using the bundled pet profiles/talents. `-Request` accepts another request within the same coverage. The catalog covers **STORM primary, levels through 180**, the eight equipment slots and PET, with BROAD gear and supported pins/jewels. The launcher rejects a different primary school, a higher level or additional slots because those candidates were not included. The Java engine still applies level eligibility, player-item screening, ownership and request rules.

Changing `examples/pet-profiles.json` does not alter already bundled pet attributes or profiles. Rebuild the bundle to publish those changes. Other primary schools and refreshed game revisions require a newly prepared bundle. Existing `Run-DeathStormOptimizer.ps1` and `Run-MockOptimizer.ps1` remain the database-backed development paths, including FILTERED mode and other schools.

## Refresh the bundle on the source machine

Use existing matching extraction inputs; a new game extraction is unnecessary unless updating the game-data snapshot. From the repository root:

```powershell
mvn package -Dmaven.jar.forceCreation=true
python bridge/build_portable_runtime.py
```

The builder runs the same pet and BROAD pool preparation as the expanded launcher, preserves record ordering and original metadata, and replaces only the three published runtime files. The forced JAR recreation avoids shading an already shaded executable during repeated packaging. Use the [local Maven fallback](../AGENTS.md#local-commands-and-artifacts) if Maven is not on PATH.

Builder arguments include `--database`, `--staging`, `--resource-capture`, `--request`, `--pet-profiles`, `--jar` and `--output`. Pet/gear revisions must match. After Java or bundled scaling changes, rebuild and republish the JAR so the portable runtime uses the new code. After profile or pool changes, republish the catalog. Review and commit all three runtime files together with relevant source/request changes. The manifest records the request/profile hashes used during preparation; the launcher intentionally permits subsequent target/search edits within catalog coverage.

For a game patch, first follow [CATALOG_REFRESH.md](../docs/CATALOG_REFRESH.md), including scaling revision checks, then republish and validate the runtime. The runtime builder does not capture game files, reconcile removed records, or update scaling automatically.

## Validation on 2026-11-06

The executable was rebuilt with all 74 Java tests passing. All 28 Python tests passed, including bundle integrity/coverage and a launcher run from a temporary checkout with no extraction inputs and a restricted PATH. Additional manual checks covered missing/corrupt bundle files, unsupported Java, invalid heap settings, out-of-coverage requests, corrupt-cache repair and warm-cache reuse. Relative input/output paths resolve against the launcher's repository, including when called from another directory.

The unchanged 750 / 700 AUTO request completed using an isolated copy of the portable inputs in 270.2 seconds on the source machine. It returned five feasible alternatives over 97,576 unique evaluations. All 35 mandatory-target comparisons passed, and gear, jewels, pets, stats, fitness, warnings and all 701 history entries matched the previous screened result; only unordered `schools`/`gearSlots` set serialization differed. Full run and audit artifacts remain local under `target/portable-verification*`. This establishes packaging/search compatibility, not retail gameplay accuracy.
