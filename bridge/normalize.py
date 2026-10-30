"""Offline bridge: explicit item records -> Java catalog v1. No client attachment."""
from __future__ import annotations
import argparse
import copy
import json
import math
from pathlib import Path

SCHOOLS = {"FIRE", "ICE", "STORM", "MYTH", "LIFE", "DEATH", "BALANCE", "UNIVERSAL"}
SCOPED = {"DAMAGE", "RESISTANCE", "ACCURACY", "PIERCE", "CRITICAL_RATING", "BLOCK_RATING",
          "PIP_CONVERSION_RATING", "FLAT_DAMAGE", "FLAT_RESISTANCE"}
GLOBAL = {"HEALTH", "MANA", "POWER_PIP_CHANCE", "SHADOW_PIP_RATING", "SHADOW_PIP_CHANCE",
          "ARCHMASTERY_RATING", "INCOMING_HEALING", "OUTGOING_HEALING", "ENERGY", "STUN_RESISTANCE"}
SLOTS = {"HAT", "ROBE", "BOOTS", "WAND", "ATHAME", "AMULET", "RING", "DECK", "PET", "MOUNT"}
TYPES = {"CIRCLE", "TEAR", "SQUARE", "TRIANGLE", "SWORD_PIN", "SHIELD_PIN", "POWER_PIN", "PET"}
COMMON = {"id", "name", "level", "schools", "itemCards", "source", "gameVersion", "importedAt", "complete", "category", "extensions"}
GEAR = COMMON | {"slot", "sockets", "setIds", "pet"}
JEWEL = COMMON | {"type", "bonusSchools", "gearSlots", "grantedTalentIds"}


def normalize(document: dict) -> dict:
    if not isinstance(document.get("items"), list):
        raise ValueError("Input requires an items array of explicitly decoded item records")
    result = {"schemaVersion": 1, "gear": [], "jewels": [], "setBonuses": copy.deepcopy(document.get("setBonuses", [])),
              "baseStats": copy.deepcopy(document.get("baseStats", {})),
              "extensions": copy.deepcopy(document.get("extensions", {}))}
    for key, value in document.items():
        if key not in {"items", "source", "gameVersion", "capturedAt", "setBonuses", "baseStats", "extensions"}:
            result["extensions"][key] = copy.deepcopy(value)
    ids = set()
    for original in document["items"]:
        row = copy.deepcopy(original)
        kind = row.pop("kind", None)
        if kind not in {"gear", "jewel"}:
            raise ValueError("Record kind must be gear or jewel")
        allowed = GEAR if kind == "gear" else JEWEL
        item = {key: value for key, value in row.items() if key in allowed}
        extra = item.setdefault("extensions", {})
        for key, value in row.items():
            if key not in allowed and key != "stats":
                extra[key] = value
        for field in ("id", "name", "level", "schools", "complete"):
            if field not in item:
                raise ValueError(f"Missing {field}: {item.get('id')}")
        if not isinstance(item["id"], str) or not item["id"].strip() or item["id"] in ids:
            raise ValueError("IDs must be nonempty unique strings")
        ids.add(item["id"])
        if type(item["level"]) is not int or item["level"] < 1 or type(item["complete"]) is not bool:
            raise ValueError("Positive integer level and explicit boolean complete required")
        if not isinstance(item["schools"], list) or not set(item["schools"]) <= SCHOOLS - {"UNIVERSAL"}:
            raise ValueError("Invalid school eligibility; empty means unrestricted")
        if kind == "gear" and (item.get("slot") not in SLOTS or not isinstance(item.get("sockets"), list)):
            raise ValueError("Gear needs a slot and explicit socket array")
        if kind == "jewel" and item.get("type") not in TYPES:
            raise ValueError("Jewel needs a known socket type")
        item.setdefault("source", document.get("source", "unknown"))
        item.setdefault("gameVersion", document.get("gameVersion", "unknown"))
        item.setdefault("importedAt", document.get("capturedAt", "unknown"))
        stats = row.get("stats")
        if not isinstance(stats, list):
            raise ValueError("Every record needs an explicit stats array (may be empty)")
        values = {}
        unsupported = []
        for stat in stats:
            stat_type, school, value = stat.get("stat"), stat.get("school"), stat.get("value")
            if stat_type not in SCOPED | GLOBAL:
                unsupported.append(copy.deepcopy(stat))
                continue
            if school not in SCHOOLS or (stat_type in GLOBAL and school != "UNIVERSAL"):
                raise ValueError(f"Invalid stat scope: {stat}")
            if type(value) not in (int, float) or not math.isfinite(value):
                raise ValueError(f"Finite numeric bonus required: {stat}")
            key = f"{stat_type}:{school}"
            if key in values:
                raise ValueError(f"Duplicate stat key {key}; resolve duplicate definitions explicitly")
            values[key] = value
        if unsupported:
            extra["unsupportedStats"] = unsupported
            item["complete"] = False
        item["baseStats" if kind == "gear" else "statBonus"] = {"values": values}
        result["gear" if kind == "gear" else "jewels"].append(item)
    return result


def reject_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON field {key}")
        result[key] = value
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    document = json.loads(args.input.read_text(encoding="utf-8"), object_pairs_hook=reject_duplicate_keys)
    result = normalize(document)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(f"Exported {len(result['gear'])} gear and {len(result['jewels'])} jewels; Java validates the full model on import.")


if __name__ == "__main__":
    main()
