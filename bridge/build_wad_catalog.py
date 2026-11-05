"""Stage an offline Imcodec capture in SQLite and export provisional optimizer data.

Uses only the Python standard library. Never attaches to or modifies the game.
All exported records remain incomplete until independently verified.
"""
from __future__ import annotations

import argparse
import collections
from contextlib import closing
import json
import math
import sqlite3
from pathlib import Path

SCHOOLS = {"FIRE", "ICE", "STORM", "MYTH", "LIFE", "DEATH", "BALANCE"}
SLOTS = {"Hat": "HAT", "Robe": "ROBE", "Shoes": "BOOTS", "Weapon": "WAND",
         "Wand": "WAND", "Staff": "WAND", "Athame": "ATHAME", "Ring": "RING",
         "Amulet": "AMULET", "Deck": "DECK", "Pet": "PET", "Mount": "MOUNT"}
SHAPES = {"CircleJewel": "CIRCLE", "TearJewel": "TEAR", "SquareJewel": "SQUARE",
          "TriangleJewel": "TRIANGLE", "PetJewel": "PET", "PinSquareSword": "SWORD_PIN",
          "PinSquareShield": "SHIELD_PIN", "PinSquarePip": "POWER_PIN"}
SOCKETS = {"SOCKETTYPE_" + name: name for name in ("CIRCLE", "TEAR", "SQUARE", "TRIANGLE", "PET")}
SOCKETS.update({"SOCKETTYPE_PINSQUARESWORD": "SWORD_PIN", "SOCKETTYPE_PINSQUARESHIELD": "SHIELD_PIN",
                "SOCKETTYPE_PINSQUAREPIP": "POWER_PIN"})
# Explicit table families and units; never scale every float or infer from an effect name.
STAT_TABLES = {
    "Damage": ("DAMAGE", 100), "ReduceDamage": ("RESISTANCE", 100),
    "Accuracy": ("ACCURACY", 100), "ArmorPiercing": ("PIERCE", 100),
    "FlatDamage": ("FLAT_DAMAGE", 1), "ReduceDamageFlat": ("FLAT_RESISTANCE", 1),
    "CriticalHit": ("CRITICAL_RATING", 1), "Block": ("BLOCK_RATING", 1),
    "PipConversion": ("PIP_CONVERSION_RATING", 1),
    "MaxHealth": ("HEALTH", 1), "MaxMana": ("MANA", 1), "MaxEnergy": ("ENERGY", 1),
    "PowerPips": ("POWER_PIP_CHANCE", 100), "ShadowPips": ("SHADOW_PIP_CHANCE", 100),
    "ShadowPipRating": ("SHADOW_PIP_RATING", 1), "Archmastery": ("ARCHMASTERY_RATING", 1),
    "Healing": ("OUTGOING_HEALING", 100), "IncomingHealing": ("INCOMING_HEALING", 100),
    "StunResistance": ("STUN_RESISTANCE", 100),
}
GLOBAL_FAMILIES = {"MaxHealth", "MaxMana", "MaxEnergy", "PowerPips", "ShadowPips",
                   "ShadowPipRating", "Archmastery", "Healing", "IncomingHealing", "StunResistance"}


def json_text(value):
    return json.dumps(value, ensure_ascii=False, allow_nan=False, separators=(",", ":"))


def records(path):
    with path.open(encoding="utf-8") as stream:
        for line in stream:
            yield json.loads(line)


def typename(value):
    return value.get("$type", "unknown").split(",", 1)[0].rsplit(".", 1)[-1]


def localization(text):
    lines = text.lstrip("\ufeff").splitlines()
    if not lines or ":" not in lines[0] or (len(lines) - 1) % 3:
        raise ValueError("Expected language header followed by key/comment/text triples")
    namespace = lines[0].split(":", 1)[1]
    result = {}
    for i in range(1, len(lines), 3):
        key = (namespace + "_" + lines[i]).casefold()
        if key in result:
            raise ValueError("Duplicate localization key: " + key)
        result[key] = lines[i + 2]
    return result


def requirements(tree):
    """Resolve conjunctions and school exclusions; retain other logic as issues."""
    level, schools, issues = 1, set(SCHOOLS), []

    def walk(node):
        nonlocal level, schools
        if node is None:
            return
        kind = typename(node)
        school = node.get("m_magicSchool", "").upper()
        if (node.get("m_operator", "ROP_AND") != "ROP_AND"
                or node.get("m_applyNOT") and kind != "ReqSchoolOfFocus"):
            issues.append("unsupported-requirement-logic")
            return
        if "m_requirements" in node:
            for child in node["m_requirements"]:
                walk(child)
            return
        if kind == "ReqMagicLevel" and node.get("m_operatorType") == "OPERATOR_GREATER_THAN_EQ":
            numeric = node.get("m_numericValue")
            if (type(numeric) not in (int, float) or not math.isfinite(numeric)
                    or numeric < 1 or int(numeric) != numeric):
                issues.append("invalid-level-requirement")
                return
            level = max(level, int(numeric))
            if school:
                # Old retail templates commonly say Fire here while explicitly
                # requiring another focus school. Do not equate this field with
                # the wizard's focus school without validating its semantics.
                issues.append("level-school-semantics-unverified:" + school)
        elif kind == "ReqSchoolOfFocus" and school in SCHOOLS:
            if node.get("m_applyNOT"):
                schools.discard(school)
            else:
                schools.intersection_update({school})
        else:
            issues.append("unsupported-requirement:" + kind)

    walk(tree)
    if not schools:
        issues.append("conflicting-school-requirements")
    return level, sorted(schools) if schools != SCHOOLS else [], issues


def resolve_stat(info, effects, tables):
    name = info.get("m_effectName")
    effect = effects.get(name)
    if effect is None:
        raise ValueError("missing-effect:" + str(name))
    table_name = effect.get("m_statTableName", "")
    table = tables.get(table_name)
    if table is None:
        raise ValueError("missing-table:" + table_name)
    index = info.get("m_lookupIndex")
    vector = table.get("m_statVector", [])
    if type(index) is not int or not 0 <= index < len(vector):
        raise ValueError("invalid-lookup-index:" + str(index))
    raw = vector[index]
    if type(raw) not in (int, float) or not math.isfinite(raw):
        raise ValueError("invalid-stat-value")
    family, sep, suffix = table_name.rpartition("_")
    trace = {"effect": name, "table": table_name, "index": index, "raw": raw,
             "sourceStatType": table.get("m_statType")}
    if not sep or family not in STAT_TABLES:
        return None, trace, "unsupported-stat-table:" + table_name
    stat, multiplier = STAT_TABLES[family]
    school = "UNIVERSAL" if suffix == "AllSchools" else suffix.upper()
    if family in GLOBAL_FAMILIES:
        school = "UNIVERSAL"
    if school not in SCHOOLS | {"UNIVERSAL"}:
        return None, trace, "unsupported-stat-school:" + school
    value = round(raw * multiplier, 8)
    trace.update(multiplier=multiplier, stat=stat, school=school, value=value)
    return (stat + ":" + school, value), trace, None


def project(row, names, effects, tables, capture):
    data = row["decoded"]
    adjectives = set(data.get("m_adjectiveList", []))
    shapes = {value for key, value in SHAPES.items() if key in adjectives}
    slots = {value for key, value in SLOTS.items() if key in adjectives}
    is_jewel = "Jewel" in adjectives or bool(shapes)
    if is_jewel:
        if len(shapes) != 1:
            return None, ["unclassified-jewel-shape"]
        kind, shape, slot = "jewel", shapes.pop(), None
    elif len(slots) == 1:
        kind, shape, slot = "gear", None, slots.pop()
        if slot == "PET":
            return None, ["pet-instance-required"]
        if "Housing" in adjectives or "HousingMountObject.AdjRef" in adjectives:
            return None, ["housing-object"]
    else:
        return None, ["non-equipment-or-ambiguous-slot"]
    level, schools, issues = requirements(data.get("m_equipRequirements"))
    display_key = data.get("m_displayName", "")
    name = names.get(display_key.casefold())
    if not name:
        name = data.get("m_objectName") or row["member"]
        issues.append("unresolved-display-name")
    stats, traces, cards = {}, [], []
    for info in data.get("m_equipEffects", []):
        if info is None:
            issues.append("null-equip-effect")
            continue
        effect_type = typename(info)
        if effect_type == "StatisticEffectInfo":
            try:
                pair, trace, issue = resolve_stat(info, effects, tables)
                traces.append(trace)
                if pair:
                    key, value = pair
                    stats[key] = round(stats.get(key, 0) + value, 8)
                if issue:
                    issues.append(issue)
            except ValueError as error:
                traces.append({"unresolved": info})
                issues.append(str(error))
        elif effect_type == "ProvideSpellEffectInfo":
            # Keep source spell name and copy count, without inventing a template ID.
            cards.append("w101:spell-name:" + info.get("m_spellName", ""))
            traces.append({"card": info})
        else:
            traces.append({"unresolved": info})
            issues.append("unsupported-effect:" + effect_type)
    sockets, raw_sockets = [], []
    for behavior in data.get("m_behaviors", []):
        if behavior is None:
            issues.append("null-behavior")
            continue
        if behavior.get("m_behaviorName") != "JewelSocketBehavior":
            continue
        for socket in behavior.get("m_jewelSockets", []):
            raw_sockets.append(socket)
            mapped = SOCKETS.get(socket.get("m_socketType"))
            if mapped:
                sockets.append({"type": mapped, "allowedSchools": []})
            else:
                issues.append("unsupported-socket:" + str(socket.get("m_socketType")))
    set_id = data.get("m_itemSetBonusTemplateID", 0)
    if set_id:
        issues.append("unresolved-set:" + str(set_id))
    if shape == "PET":
        issues.append("pet-jewel-talent-resolution-required")
    if str(data.get("m_templateID")) != row["id"]:
        issues.append("manifest-template-id-mismatch")
    ext = {"templateId": row["id"], "archive": row["archive"], "member": row["member"],
           "sha256": row["sha256"], "displayNameKey": display_key,
           "rawSchoolMetadata": data.get("m_school"), "sourceSetTemplateId": str(set_id),
           "rawSocketDefinitions": raw_sockets, "resolvedEffects": traces,
           "issues": sorted(set(issues)), "validation": "retail-verification-pending",
           "availability": "unknown", "rawCapture": "raw_templates:" + row["id"]}
    item = {"id": "w101:template:" + row["id"], "name": name, "level": level,
            "schools": schools, "complete": False, "source": "wizard101-wad",
            "gameVersion": "exe-sha256:" + capture["executableSha256"],
            "importedAt": capture["finishedAt"], "itemCards": list(dict.fromkeys(cards)),
            "category": "qa-or-test" if "/QA_" in row["member"] else "availability-unverified",
            "extensions": ext}
    if kind == "gear":
        item.update(slot=slot, sockets=sockets, baseStats={"values": stats}, setIds=[])
    else:
        item.update(type=shape, statBonus={"values": stats}, gearSlots=[], bonusSchools=[])
        if shape.endswith("_PIN"):
            # KingsIsle's pin system uses matching sockets on hats, robes and shoes.
            # Wizard focus eligibility comes from m_equipRequirements, never m_school.
            item['gearSlots'] = ['HAT', 'ROBE', 'BOOTS']
            item['bonusSchools'] = sorted({key.split(':')[1] for key in stats
                                          if key.split(':')[1] != 'UNIVERSAL'})
            item['extensions']['pinCompatibilitySource'] = 'https://akamaieu.wizard101.com/game/community/updatenotes/may2024'
    return (kind, item), issues


def build(capture_dir, db_path, catalog_path):
    capture = json.loads((capture_dir / "capture.json").read_text(encoding="utf-8"))
    if db_path.exists() or catalog_path.exists():
        raise ValueError("Choose new database/catalog paths to preserve earlier captures")
    resources = list(records(capture_dir / "resources.ndjson"))
    effects, tables, names, resource_issues = {}, {}, {}, []
    for row in resources:
        member, data = row["member"], row.get("decoded")
        if "error" in row:
            resource_issues.append({"member": member, "error": row["error"]})
        elif member == "GameEffectData/CanonicalStatEffects.xml":
            effects = {effect["m_effectName"]: effect for effect in data["m_effectTemplates"]}
        elif member.startswith("GameEffectRuleData/"):
            tables[data["m_tableName"]] = data
        elif member.startswith("Locale/en-US/"):
            try:
                names.update(localization(data))
            except ValueError as error:
                resource_issues.append({"member": member, "error": str(error)})
    if not effects or not tables:
        raise ValueError("Capture is missing canonical effects or stat tables")
    db_path.parent.mkdir(parents=True, exist_ok=True)
    catalog_path.parent.mkdir(parents=True, exist_ok=True)
    catalog = {"schemaVersion": 1, "gear": [], "jewels": [], "setBonuses": [], "baseStats": {},
               "extensions": {"capture": capture, "validation": "All records provisional; complete=false"}}
    counts, issues, exclusions = collections.Counter(), collections.Counter(), collections.Counter()
    with closing(sqlite3.connect(str(db_path))) as db, db:
        db.executescript("""
            CREATE TABLE raw_capture (id INTEGER PRIMARY KEY CHECK(id=1), json TEXT NOT NULL);
            CREATE TABLE raw_resources (member TEXT PRIMARY KEY, json TEXT NOT NULL);
            CREATE TABLE raw_coverage (template_id TEXT PRIMARY KEY, status TEXT NOT NULL, json TEXT NOT NULL);
            CREATE TABLE raw_templates (template_id TEXT PRIMARY KEY, name TEXT NOT NULL,
              type TEXT NOT NULL, archive TEXT NOT NULL, member TEXT NOT NULL, sha256 TEXT NOT NULL, json TEXT NOT NULL);
            CREATE TABLE provisional_items (id TEXT PRIMARY KEY, template_id TEXT UNIQUE NOT NULL,
              kind TEXT NOT NULL, name TEXT NOT NULL, slot TEXT, level INTEGER NOT NULL,
              shape TEXT, issues_json TEXT NOT NULL, json TEXT NOT NULL);
            CREATE TABLE resolved_stats (id TEXT NOT NULL, stat TEXT NOT NULL, school TEXT NOT NULL,
              value REAL NOT NULL, PRIMARY KEY(id,stat,school));
            CREATE INDEX raw_template_name ON raw_templates(name COLLATE NOCASE);
            CREATE INDEX provisional_slot_level ON provisional_items(slot,level);
            CREATE INDEX provisional_name ON provisional_items(name COLLATE NOCASE);
            CREATE INDEX resolved_stat_value ON resolved_stats(stat,school,value);
        """)
        db.execute("INSERT INTO raw_capture VALUES(1,?)", (json_text(capture),))
        db.executemany("INSERT INTO raw_resources VALUES(?,?)", ((r["member"], json_text(r)) for r in resources))
        coverage_count = 0
        for row in records(capture_dir / "coverage.ndjson"):
            db.execute("INSERT INTO raw_coverage VALUES(?,?,?)", (row["id"], row["status"], json_text(row)))
            coverage_count += 1
        if coverage_count != capture["discovered"]:
            raise ValueError("Coverage count does not match the capture manifest")
        for row in records(capture_dir / "templates.ndjson"):
            data = row["decoded"]
            name = names.get(data.get("m_displayName", "").casefold(), data.get("m_objectName", ""))
            db.execute("INSERT INTO raw_templates VALUES(?,?,?,?,?,?,?)",
                       (row["id"], name, row["type"], row["archive"], row["member"], row["sha256"], json_text(row)))
            counts["rawItems"] += 1
            projected, reasons = project(row, names, effects, tables, capture)
            if projected is None:
                exclusions.update(reasons)
                continue
            kind, item = projected
            catalog["gear" if kind == "gear" else "jewels"].append(item)
            counts[kind] += 1
            counts["withUnresolvedFields" if reasons else "noKnownMappingIssues"] += 1
            issues.update(set(reasons))
            db.execute("INSERT INTO provisional_items VALUES(?,?,?,?,?,?,?,?,?)",
                       (item["id"], row["id"], kind, item["name"], item.get("slot"), item["level"],
                        item.get("type"), json_text(sorted(set(reasons))), json_text(item)))
            stats = item["baseStats" if kind == "gear" else "statBonus"]["values"]
            for key, value in stats.items():
                stat, school = key.split(":")
                db.execute("INSERT INTO resolved_stats VALUES(?,?,?,?)", (item["id"], stat, school, value))
        if counts["rawItems"] != capture["items"]:
            raise ValueError("Item count does not match the capture manifest")
        if db.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise ValueError("SQLite integrity check failed")
    catalog_path.write_text(json_text(catalog) + "\n", encoding="utf-8")
    report = {"capture": capture, "counts": dict(counts), "excludedFromOptimizer": dict(exclusions),
              "unresolved": dict(issues.most_common()), "resourceIssues": resource_issues,
              "database": str(db_path), "catalog": str(catalog_path), "verifiedComplete": 0,
              "limitations": ["Coverage is the installed manifest, not proof of every retail item or availability.",
                              "Decoded defaults/unknown schema fields require validation against item cards.",
                              "Pets require instance talents; sets and unsupported mechanics remain unresolved.",
                              "No records are enabled for default optimization (complete=false)."]}
    report_path = db_path.with_suffix(".report.json")
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"counts": dict(counts), "verifiedComplete": 0, "report": str(report_path)}, indent=2))
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture", type=Path, required=True)
    parser.add_argument("--db", type=Path, required=True)
    parser.add_argument("--catalog", type=Path, required=True)
    args = parser.parse_args()
    build(args.capture, args.db, args.catalog)


if __name__ == "__main__":
    main()
