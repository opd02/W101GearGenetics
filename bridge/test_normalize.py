import copy
import json
import unittest
from pathlib import Path
from normalize import normalize, reject_duplicate_keys

class NormalizeTest(unittest.TestCase):
    def setUp(self):
        self.doc = json.loads((Path(__file__).parent / "fixtures/items.json").read_text(encoding="utf-8"))

    def test_complete_pipeline_fixture(self):
        result = normalize(self.doc)
        self.assertEqual(14, len(result["gear"]))
        self.assertEqual(1, len(result["jewels"]))
        self.assertEqual(60, result["gear"][0]["baseStats"]["values"]["DAMAGE:STORM"])
        self.assertTrue(result["gear"][0]["complete"])

    def test_unknown_stats_and_fields_are_preserved_and_flagged(self):
        item = self.doc["items"][0]
        item["newMetadata"] = {"keep": 1}
        item["stats"].append({"stat": "FUTURE_RATING", "school": "STORM", "value": 99})
        result = normalize(self.doc)["gear"][0]
        self.assertFalse(result["complete"])
        self.assertEqual(99, result["extensions"]["unsupportedStats"][0]["value"])
        self.assertEqual({"keep": 1}, result["extensions"]["newMetadata"])

    def test_duplicates_units_and_missing_fields_rejected(self):
        self.doc["items"].append(copy.deepcopy(self.doc["items"][0]))
        with self.assertRaises(ValueError):
            normalize(self.doc)
        self.doc["items"].pop()
        self.doc["items"][0]["stats"] = [{"stat": "HEALTH", "school": "STORM", "value": 1}]
        with self.assertRaises(ValueError):
            normalize(self.doc)
        del self.doc["items"][0]["complete"]
        with self.assertRaises(ValueError):
            normalize(self.doc)
        with self.assertRaises(ValueError):
            json.loads('{"x":1,"x":2}', object_pairs_hook=reject_duplicate_keys)

if __name__ == "__main__":
    unittest.main()
