# WizWalker feasibility and optional bridge

Inspected 2026-10-30. The [GitHub README](https://github.com/wizwalker/wizwalker) redirects to [Codeberg](https://codeberg.org/wizwalker/wizwalker). A shallow clone of Codeberg's default branch resolved to **95e30503e8ce8510da9d4ceb8184e7cedb5bfef9**, dated 2024-12-31; pyproject declares version 1.8.2 and Python ^3.11.

This is source inspection of that actual revision. It does not establish compatibility of its memory offsets with today's client. No game account, process, client or live memory was accessed.

## Verified APIs in that source revision

- [ClientObject](https://codeberg.org/wizwalker/wizwalker/src/commit/95e30503e8ce8510da9d4ceb8184e7cedb5bfef9/wizwalker/memory/memory_objects/client_object.py): try_get_inventory_behavior() and try_get_equipment_behavior().
- [InventoryBehaviorBase](https://codeberg.org/wizwalker/wizwalker/src/commit/95e30503e8ce8510da9d4ceb8184e7cedb5bfef9/wizwalker/memory/memory_objects/inventory_behavior.py): item_list() returns CoreObjects. Bank/shared-bank traversal is unverified.
- [ClientEquipmentBehavior](https://codeberg.org/wizwalker/wizwalker/src/commit/95e30503e8ce8510da9d4ceb8184e7cedb5bfef9/wizwalker/memory/memory_objects/equipment_behavior.py): item_list(), slot_list(), public_item_list(); ClientWizEquipmentBehavior adds equipment_sets().
- [CoreObject](https://codeberg.org/wizwalker/wizwalker/src/commit/95e30503e8ce8510da9d4ceb8184e7cedb5bfef9/wizwalker/memory/memory_objects/core_object.py): global_id_full(), perm_id(), template_id_full(), debug_name(), display_key(). These identifiers/labels are distinct and must not be conflated with catalog identity or localized display names.
- [EquippedSlotInfo](https://codeberg.org/wizwalker/wizwalker/src/commit/95e30503e8ce8510da9d4ceb8184e7cedb5bfef9/wizwalker/memory/memory_objects/equipped_slot_info.py): item_id() reads a uint64 GID; item_slot_name_id() reads uint32. [EquippedItemInfo](https://codeberg.org/wizwalker/wizwalker/src/commit/95e30503e8ce8510da9d4ceb8184e7cedb5bfef9/wizwalker/memory/memory_objects/equipped_item_info.py) instead reads uint32 item_id(). Their identity semantics need runtime verification before joining records.
- [WizGameObjectTemplate](https://codeberg.org/wizwalker/wizwalker/src/commit/95e30503e8ce8510da9d4ceb8184e7cedb5bfef9/wizwalker/memory/memory_objects/game_object_template.py): template/object/display metadata; no complete typed gear-bonus model.
- [GameStats](https://codeberg.org/wizwalker/wizwalker/src/commit/95e30503e8ce8510da9d4ceb8184e7cedb5bfef9/wizwalker/memory/memory_objects/game_stats.py): character-level base/bonus/rating values and school arrays, including critical and pip conversion. Units and school-array ordering need independent verification. Aggregate stats cannot identify each item's contribution.
- [CacheHandler](https://codeberg.org/wizwalker/wizwalker/src/commit/95e30503e8ce8510da9d4ceb8184e7cedb5bfef9/wizwalker/file_readers/cache_handler.py): TemplateManifest.xml ID/name cache and language lookup; a manifest is not a decoded gear catalog.
- [Wad](https://codeberg.org/wizwalker/wizwalker/src/commit/95e30503e8ce8510da9d4ceb8184e7cedb5bfef9/wizwalker/file_readers/wad.py): Wad(path), async open(), names(), get_file_info(name), get_file(name), synchronous close(). Archive-member access does not deserialize all item templates/stat definitions.

## Distinct acquisition problems

| Problem | Evidence | Status |
| --- | --- | --- |
| Actual equipped/backpack inventory | Item lists, slot IDs, instance/template identifiers | Source paths found; no current-client runtime verification or verified bank access |
| Aggregate character stats | GameStats school arrays, ratings/base/bonus fields | Potential later validation source; cannot recover item bonuses or unseen inventory |
| Offline templates/stat definitions | WAD members, template manifest/name cache | Archive-manifest prototype provided; serialized item/stat decoder unresolved |
| Every available item | Needs decoded files plus completeness/source/version reconciliation | No verified complete catalog API found; local/cache contents do not prove completeness |

The inspected classes do not expose a complete, verified high-level record containing item bonuses, school/level restrictions, jewels/pins and pet-specific resolved talents. Lower-level behaviors may enable further research, but their existence is not a working reliable importer.

## Read-only boundary

The requested integration excludes memory modification. Upstream [hook activation](https://codeberg.org/wizwalker/wizwalker/src/commit/95e30503e8ce8510da9d4ceb8184e7cedb5bfef9/wizwalker/memory/hooks.py) writes jumps/bytecode into the client. Read-named methods after activating those hooks do not establish strictly read-only process interaction. This project does not construct a Client, activate hooks, call writes, automate gameplay, collect credentials or implement anti-detection.

Live-client access is brittle across patches and can have account/terms-of-service implications. Any later live integration requires reviewing then-current game terms and establishing an explicitly acceptable read-only access path. No claim of account safety or developer approval is made. Offline inspection avoids process attachment; asset redistribution/ownership considerations remain separate.

## Working interface and offline prototype

bridge/normalize.py uses the Python standard library to export catalog v1 from explicit decoded item records. The fixture -> normalizer -> Java validation -> SQLite -> optimizer pipeline works without the game or WizWalker. Unsupported bonuses remain visible and flag their record incomplete.

bridge/wad_manifest.py is opt-in and offline. It hashes a WAD and lists entries with verified Wad APIs. It never calls Client APIs. The optional upstream dependency is separately installed; the inspected project declares GPL-3.0-or-later. No upstream source is bundled.

After independently installing that dependency in an appropriate Windows environment:

```powershell
python bridge/wad_manifest.py --wad "C:\Path\To\Wizard101\Data\GameData\Root.wad" --output target/root-manifest.json
```

The prototype was source-checked but **not run against a real archive**. Its output is a research manifest, intentionally incompatible with gear catalog input. No serialized asset decoder or comprehensive extraction is fabricated.

Next steps: obtain representative offline files; verify their serialization/schema and stable template/stat IDs; decode explicit school/unit/restriction definitions while preserving unknown data; validate item totals against trustworthy aggregates; then build catalog and owned-inventory adapters separately.

## Follow-up: Imlight and Imcodec

Further source inspection identified a concrete candidate for offline template/stat decoding through Imcodec and Imlight's canonical-stat resolution code. See [the catalog extraction plan](CATALOG_EXTRACTION.md) for pinned revisions, verified source paths and a proposed validation sequence. This is source evidence; no game-file decoding or live-client compatibility was tested.
