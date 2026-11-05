import copy
from contextlib import closing
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from build_wad_catalog import build, localization, project, requirements, resolve_stat


CAPTURE = {"executableSha256": "test", "finishedAt": "2026-10-31T00:00:00Z",
           "discovered": 1, "items": 1}
EFFECTS = {"fire": {"m_statTableName": "Damage_Fire"},
           "all": {"m_statTableName": "Damage_AllSchools"},
           "rating": {"m_statTableName": "CriticalHit_Fire"},
           "pips": {"m_statTableName": "PowerPips_AllSchools"}}
TABLES = {"Damage_Fire": {"m_statVector": [0.25]},
          "Damage_AllSchools": {"m_statVector": [0.10]},
          "CriticalHit_Fire": {"m_statVector": [123]},
          "PowerPips_AllSchools": {"m_statVector": [0.30]}}


def info(name, index=0):
    return {"$type": "Imcodec.ObjectProperty.TypeCache.StatisticEffectInfo, Imcodec.ObjectProperty",
            "m_effectName": name, "m_lookupIndex": index}


def row():
    return {"id": "123", "archive": "Root", "member": "ObjectData/test.xml", "sha256": "test",
            "type": "WizItemTemplate", "decoded": {"m_templateID": 123, "m_objectName": "test",
            "m_displayName": "Items_001", "m_school": "Fire", "m_adjectiveList": ["Hat"],
            "m_equipEffects": [info("fire"), info("all"), info("rating"), info("pips")],
            "m_behaviors": [{"m_behaviorName": "JewelSocketBehavior", "m_jewelSockets": [
                {"m_socketType": "SOCKETTYPE_TEAR", "m_bLockable": True}]}]}}


class WadCatalogTests(unittest.TestCase):
    def test_fraction_units_and_rating_units_are_distinct(self):
        projected, issues = project(row(), {"items_001": "Test Hat"}, EFFECTS, TABLES, CAPTURE)
        item = projected[1]
        self.assertEqual(item["baseStats"]["values"], {
            "DAMAGE:FIRE": 25, "DAMAGE:UNIVERSAL": 10,
            "CRITICAL_RATING:FIRE": 123, "POWER_PIP_CHANCE:UNIVERSAL": 30})
        self.assertFalse(item["complete"])
        self.assertEqual(issues, [])
        self.assertEqual(item["sockets"], [{"type": "TEAR", "allowedSchools": []}])
        self.assertTrue(item["extensions"]["rawSocketDefinitions"][0]["m_bLockable"])

    def test_invalid_indices_cannot_silently_use_negative_python_index(self):
        for index in (-1, 1, True):
            with self.assertRaisesRegex(ValueError, "invalid-lookup-index"):
                resolve_stat(info("fire", index), EFFECTS, TABLES)
        with self.assertRaisesRegex(ValueError, "missing-effect"):
            resolve_stat(info("absent"), EFFECTS, TABLES)

    def test_school_metadata_is_not_school_eligibility(self):
        source = row()
        source["decoded"]["m_adjectiveList"] = ["Jewel", "CircleJewel"]
        item = project(source, {"items_001": "Test Jewel"}, EFFECTS, TABLES, CAPTURE)[0][1]
        self.assertEqual(item["schools"], [])
        self.assertEqual(item["type"], "CIRCLE")
        self.assertEqual(item["statBonus"]["values"]["DAMAGE:FIRE"], 25)

    def test_requirements_conjunction_and_unknown_logic(self):
        tree = {"m_requirements": [
            {"$type": "Types.ReqMagicLevel, Assembly", "m_numericValue": 170.0,
             "m_operatorType": "OPERATOR_GREATER_THAN_EQ", "m_magicSchool": ""},
            {"$type": "Types.ReqSchoolOfFocus, Assembly", "m_magicSchool": "Death"}]}
        self.assertEqual(requirements(tree), (170, ["DEATH"], []))
        alternate = copy.deepcopy(tree)
        alternate["m_operator"] = "ROP_OR"
        self.assertIn("unsupported-requirement-logic", requirements(alternate)[2])
        tree["m_requirements"][0]["m_numericValue"] = float("nan")
        self.assertIn("invalid-level-requirement", requirements(tree)[2])

    def test_level_school_metadata_and_negated_focus_are_distinct(self):
        tree = {"m_requirements": [
            {"$type": "Types.ReqMagicLevel, Assembly", "m_numericValue": 10.0,
             "m_operatorType": "OPERATOR_GREATER_THAN_EQ", "m_magicSchool": "Fire"},
            {"$type": "Types.ReqSchoolOfFocus, Assembly", "m_magicSchool": "Balance"}]}
        level, schools, issues = requirements(tree)
        self.assertEqual((level, schools), (10, ["BALANCE"]))
        self.assertIn("level-school-semantics-unverified:FIRE", issues)
        tree["m_requirements"][1]["m_applyNOT"] = True
        self.assertEqual(set(requirements(tree)[1]), {"FIRE", "ICE", "STORM", "MYTH", "LIFE", "DEATH"})

    def test_power_pin_shape_and_socket_mapping(self):
        source = row()
        source["decoded"]["m_adjectiveList"] = ["Jewel", "PinSquarePip"]
        self.assertEqual(project(source, {"items_001": "Pin"}, EFFECTS, TABLES, CAPTURE)[0][1]["type"], "POWER_PIN")
        source = row()
        source["decoded"]["m_behaviors"][0]["m_jewelSockets"][0]["m_socketType"] = "SOCKETTYPE_PINSQUAREPIP"
        self.assertEqual(project(source, {"items_001": "Hat"}, EFFECTS, TABLES, CAPTURE)[0][1]["sockets"][0]["type"], "POWER_PIN")

    def test_localization_preserves_record_boundaries(self):
        self.assertEqual(localization("\ufeff1:Items\r\n001\r\n\r\nTest Hat\r\n"),
                         {"items_001": "Test Hat"})
        with self.assertRaises(ValueError):
            localization("1:Items\n\n001\n\nTest Hat\n")

    def test_unsupported_effect_and_set_are_preserved_as_issues(self):
        source = row()
        source["decoded"]["m_equipEffects"].append({"$type": "Types.StartingPipEffectInfo, Assembly"})
        source["decoded"]["m_itemSetBonusTemplateID"] = 99
        item, issues = project(source, {"items_001": "Test Hat"}, EFFECTS, TABLES, CAPTURE)
        self.assertIn("unsupported-effect:StartingPipEffectInfo", issues)
        self.assertIn("unresolved-set:99", issues)
        self.assertFalse(item[1]["complete"])
        self.assertEqual(item[1]["extensions"]["sourceSetTemplateId"], "99")

    def test_staging_retains_raw_payload_and_exports_incomplete(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "capture.json").write_text(json.dumps(CAPTURE), encoding="utf-8")
            resources = [{"member": "GameEffectData/CanonicalStatEffects.xml", "decoded": {
                "m_effectTemplates": [dict(value, m_effectName=key) for key, value in EFFECTS.items()]}}]
            resources += [{"member": "GameEffectRuleData/" + key + ".xml",
                           "decoded": dict(value, m_tableName=key)} for key, value in TABLES.items()]
            resources += [{"member": "Locale/en-US/Items.lang", "decoded": "1:Items\n001\n\nTest Hat\n"}]
            for filename, values in [("resources.ndjson", resources), ("templates.ndjson", [row()]),
                                     ("coverage.ndjson", [{"id": "123", "status": "decoded"}])]:
                (root / filename).write_text("".join(json.dumps(value) + "\n" for value in values), encoding="utf-8")
            report = build(root, root / "staging.sqlite", root / "catalog.json")
            self.assertEqual(report["counts"]["gear"], 1)
            with closing(sqlite3.connect(str(root / "staging.sqlite"))) as db:
                self.assertEqual(json.loads(db.execute("SELECT json FROM raw_templates").fetchone()[0]), row())
                self.assertEqual(db.execute("SELECT value FROM resolved_stats WHERE stat='DAMAGE' AND school='FIRE'").fetchone()[0], 25)
                self.assertEqual(db.execute("PRAGMA integrity_check").fetchone()[0], "ok")
            catalog = json.loads((root / "catalog.json").read_text(encoding="utf-8"))
            self.assertFalse(catalog["gear"][0]["complete"])
            with self.assertRaisesRegex(ValueError, "new database"):
                build(root, root / "staging.sqlite", root / "catalog.json")


if __name__ == "__main__":
    unittest.main()
