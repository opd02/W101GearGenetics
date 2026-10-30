# Building a versioned Wizard101 item catalog

Research date: 2026-10-30. Source inspection only: no game process was accessed, no game files decoded, no tools built/installed and no live compatibility claimed.

Inspected revisions:

- Imlight: 8c75ea02bbde4f9329ae6bae3ac91fc5fcb201b7 (2026-10-28).
- Imcodec: 4f37063167dd50c49f30d22bc4ccb3e8f8aa0632 (2026-10-29).
- Imlight pins a different Imcodec submodule revision, 303ece87c6cef88960e27a37d8f78a93613d3593. Do not mix their build assumptions without checking compatibility.
- WizWalker remains the source revision recorded in WIZWALKER.md.

## Main finding

Imlight supplies a concrete reference for the missing template/effect decoding path. Its Imcodec dependency handles WAD archive access and Object Property deserialization. The newly inspected Imcodec revision also bootstraps types by emulating type registration from the installed executable, without launching/attaching to the client. These capabilities are verified in source, not yet against this user's installation.

A bulk catalog should come from installed client resources. A separate optional live collector should identify which catalog entries the player owns and capture per-instance details. Loaded inventory alone does not establish a complete catalog.

## Evidence and relevant source

- [Imcodec README at inspected revision](https://github.com/Jooty/Imcodec/blob/4f37063167dd50c49f30d22bc4ccb3e8f8aa0632/README.md): ImcodecClientDir bootstrap build; bootstrap all/types/messages; wad list/unpack; op file commands.
- [Imcodec type dumper](https://github.com/Jooty/Imcodec/blob/4f37063167dd50c49f30d22bc4ccb3e8f8aa0632/src/Imcodec.TypeDump/TypeDumper.cs): reads executable/runtime images, emulates registration, walks type metadata and checks the resulting manifest.
- [Imcodec bootstrap implementation](https://github.com/Jooty/Imcodec/blob/4f37063167dd50c49f30d22bc4ccb3e8f8aa0632/src/Imcodec.BuildTools/GeneratorInputBootstrapper.cs): extracts message definitions from client archives and generates typedump.json.
- [Imcodec archive commands](https://github.com/Jooty/Imcodec/blob/4f37063167dd50c49f30d22bc4ccb3e8f8aa0632/src/Imcodec.Cli/ArchiveCommands.cs): optionally deserializes archive members, preserves raw bytes on failure. A successful unpack command is not proof that every record decoded.
- [Imlight CoreObjectFactory](https://github.com/Revive101/Imlight/blob/8c75ea02bbde4f9329ae6bae3ac91fc5fcb201b7/src/Imlight.CoreLib/Shared/Resources/CoreObjectFactory.cs): TemplateManifest.xml maps template IDs to locations; some locations resolve to world-data archives rather than Root.wad.
- [Imlight ItemHelper](https://github.com/Revive101/Imlight/blob/8c75ea02bbde4f9329ae6bae3ac91fc5fcb201b7/src/Imlight.CoreLib/Shared/Items/ItemHelper.cs): WizItemTemplate lookup, slot adjectives, distinction between statistical template ID and stitched appearance/display ID.
- [Imlight CharacterEffectHelper](https://github.com/Revive101/Imlight/blob/8c75ea02bbde4f9329ae6bae3ac91fc5fcb201b7/src/Imlight.CoreLib/Game/Effects/CharacterEffectHelper.cs): iterates template.m_equipEffects and applies school-specific/universal contributions.
- [Imlight CanonicalStatEffects](https://github.com/Revive101/Imlight/blob/8c75ea02bbde4f9329ae6bae3ac91fc5fcb201b7/src/Imlight.CoreLib/Game/Effects/CanonicalStatEffects.cs): resolves StatisticEffectInfo.m_effectName and m_lookupIndex through GameEffectData/CanonicalStatEffects.xml, the effect's m_statTableName, and a stat vector.
- [Imlight GameEffectRuleData](https://github.com/Revive101/Imlight/blob/8c75ea02bbde4f9329ae6bae3ac91fc5fcb201b7/src/Imlight.CoreLib/Game/Effects/GameEffectRuleData.cs): loads WizardStatTable records under GameEffectRuleData.
- [Imlight equipment service](https://github.com/Revive101/Imlight/blob/8c75ea02bbde4f9329ae6bae3ac91fc5fcb201b7/src/Imlight.CoreLib/Game/Services/EquipmentService.cs): processes m_equipRequirements.
- [Imlight magic schools](https://github.com/Revive101/Imlight/blob/8c75ea02bbde4f9329ae6bae3ac91fc5fcb201b7/src/Imlight.CoreLib/Game/Spells/MagicSchools.cs): loads named school definitions and school indices; template and runtime index conventions differ.
- [WizType](https://github.com/wizspoil/wiztype): documents type/schema dumping with the game open. It does not itself produce a normalized item catalog.

Imlight is a private-server implementation and its behavior is evidence to test, not authoritative retail documentation. It contains zero-value fallbacks for missing stat definitions and ignores some unsupported effects. Its BuddyStatsBuilder explicitly says sockets are not modeled there. Do not promote such partially handled records to complete optimizer inputs.

## Proposed extraction pipeline

1. Pin the client revision, executable hash, archive hashes and decoder revision. Work from a stable snapshot so a patch cannot mix schemas and archives.
2. Bootstrap matching type definitions with Imcodec. Build its decoder with those definitions; refresh/rebuild after a schema-changing update. A prebuilt binary may target a different schema.
3. Decode TemplateManifest.xml, canonical effect definitions, stat tables, school definitions and relevant localization data.
4. Follow manifest entries across Root/world-data archives. Decode templates, identify WizItemTemplate subclasses and distinguish wearable equipment from furniture/other items.
5. Resolve intrinsic stats from equip effects. Retain the original effect name, table path, lookup index, raw value and unit mapping. Keep universal and per-school entries separate. Preserve cards and starting-pip/other unsupported effects.
6. Decode equipment requirements and template socket/pin/set metadata where available. Unsupported requirement trees or effects must mark a record incomplete rather than being interpreted as unrestricted/zero.
7. Normalize into bridge records, validate, and import using the existing Java JSON/SQLite interface.
8. Capture owned instances separately, join their statistical template ID to catalog entries, and attach instance sockets, jewels, pet talents, scaling and appearance metadata. Do not infer a stitched item's stats from its display template.
9. Compare a representative sample with visible retail item cards and controlled aggregate changes. Account for base stats, sets, pets, jewels and caps before attributing a discrepancy to one item.
10. Expand to all decodable equipment; publish coverage counts and unresolved references. Reprocess changed raw templates on patches without replacing ownership/preferences.

Stat resolution has this shape:

item.m_equipEffects
  -> StatisticEffectInfo(m_effectName, m_lookupIndex)
  -> canonical effect definition
  -> WizardStatTable[m_lookupIndex]
  -> explicit stat/school/unit normalization
  -> existing StatBlock

Do not multiply every float by 100. The inspected code distinguishes categories and flat effects; validate each mapping against decoded definitions and known item cards.

## Commands to validate, not executed here

For the inspected Imcodec source tree, its documented bootstrap build is:

    dotnet build Imcodec.sln -p:ImcodecClientDir="C:\Path\To\Wizard101"

This requires the source tree's .NET toolchain and local game files. Once a compatible CLI is built, documented commands include:

    imcodec bootstrap all --client "C:\Path\To\Wizard101"
    imcodec wad list "C:\Path\To\Wizard101\Data\GameData\Root.wad"
    imcodec wad unpack "C:\Path\To\Wizard101\Data\GameData\Root.wad" "C:\Extraction\Root" --deser
    imcodec op file "C:\Extraction\Root\TemplateManifest.xml" "C:\Extraction\manifest.json"

Unpack with --deser may already emit TemplateManifest_deser.json instead of raw TemplateManifest.xml. Use op file only on raw binary members. Record individual decode failures: archive extraction can succeed while deserialization fails.

These commands are discovery steps, not an implemented item-to-optimizer adapter. Do not launch Imlight's server or Imcodec's proxy for this catalog task.

## Live-client role and remaining boundary

WizWalker's inspected inventory/equipment behavior readers expose CoreObjects, template/global IDs and some labels. They are candidate access paths for an owned-inventory snapshot, not a guaranteed complete metadata API. Bank/shared-bank access, socket/pin/pet instance layouts and current-client offsets remain unverified.

The original project scope excludes client memory modification. Standard WizWalker hook activation writes memory, so an inventory collector needs a verified hook-free path or remains out of scope under that requirement. The offline Imcodec route avoids this dependency for catalog extraction. No live commands were issued during this research.

## Storage additions to consider

Keep the existing normalized catalog and optimizer. Add a raw staging/history layer for:

- template ID + client revision + archive/member path + raw decoded JSON;
- canonical effect definitions and stat tables with provenance;
- normalized variants and validation/unsupported-reason status;
- inventory instance ID + statistical template ID + instance configuration + snapshot time;
- coverage reports and source hashes.

Preserve full-width source IDs as strings until their semantics are verified. Do not copy narrowing ID casts from a server emulator blindly. Player inventory snapshots and account-private data must remain separate from shared catalog exports.

The client resources may include obsolete, test, NPC-only or otherwise unobtainable templates, and do not necessarily include server-side availability/drop metadata. Track "decoded", "valid equipment", "verified stats", and "known obtainable" separately. Imlight's SpiralDB complements server-side world data; it is not proof of a complete retail item catalog.

## First implementation milestone

Decode five to ten known non-pet items, including one with school-specific plus universal stats, one with a jewel socket and one with an item spell. Demonstrate manifest ID -> template -> resolved effects -> exact normalized stats -> existing SQLite import, preserving every unresolved field. Then scale enumeration, followed by owned inventory and variable pet instances.

No optimizer source changes or runtime tests were needed for this documentation-only research update.
