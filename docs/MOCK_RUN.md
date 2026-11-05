# Run the mock WAD-backed optimization

From the project directory in PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-MockOptimizer.ps1
```

Edit `examples/wad-mock-request.json` to change the targets, school, level or search settings. The launcher prepares a local mock catalog from `target/wizard101/optimizer.sqlite`, prints generation progress, returns five alternatives and writes `target/wizard101/mock-results.json`. It prints the elapsed time at completion. Python and JDK 22+ are required; the launcher selects the installed JDK 22 or accepts `-JavaPath`.

The preset is a level-170 Death wizard with these mandatory raw-stat minimums:

| Stat | Minimum |
| --- | ---: |
| Death damage | 200% |
| Universal resistance | 35% |
| Death accuracy | 20% |
| Power-pip chance | 100% |
| Health | 8,000 |

It prefers at least 30% Death pierce, then rewards additional Death damage and health. Base stats are deliberately mocked as 4,000 health and 40% power-pip chance; replace them with your wizard's verified base values when moving beyond this demo. Eight gear slots are included. Pets and mounts are omitted.

The candidate pool covers **all levels from 1 through the requested level**, without a lower-level cutoff. No 150-170 performance restriction is applied. It excludes recognizable test/debug/polymorph/stat-placeholder names/paths, records with unresolved mappings, and nonstandard/pet/pin jewels. Starting-pip metadata is allowed on gear because starting pips are not among the scored stats. Pin sockets can remain empty. These filters improve the mock run; they do not verify retail obtainability or completeness.

`allowIncompleteItems=true` explicitly permits these provisional WAD records for this mock request. The full source database is preserved. The genetic search is heuristic, raw totals do not apply game caps, and owned-only mode is off, so repeated jewel copies are allowed. The generated mock catalog is a subset of the extracted data, not a verified gameplay catalog.

Optional paths:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Run-MockOptimizer.ps1 -Request examples/wad-mock-request.json -Output target/wizard101/my-results.json
```
