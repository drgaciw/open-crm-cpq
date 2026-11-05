"""Scoring arithmetic and evidence-boundary regression tests, not model predictions."""
import copy
import math
import unittest
from evaluate_jev import evaluate_oms_triage, wilson_interval


def corpus():
    return {
        "schemaVersion": 1, "dataKind": "synthetic",
        "evaluator": {"model": "scripted-test-v1", "rubricVersion": "oms-exception-triage-v1",
                      "predictionSource": "scripted", "threshold": .9, "thresholdFrozenBeforeTest": True},
        "labelReview": {"independent": False, "reviewer": "fixture-author"},
        "pilotCategories": ["FINANCE_UNKNOWN"],
        "cases": [{"id": "a", "lineage": "a", "split": "test", "category": "FINANCE_UNKNOWN",
                   "expected": "CHECK_FINANCE_ACK", "baseline": "CHECK_FINANCE_ACK",
                   "prediction": {"status": "OK", "answer": "CHECK_FINANCE_ACK", "confidence": .9}}],
    }


class EvaluationTest(unittest.TestCase):
    def test_errors_low_confidence_and_insufficient_count_as_abstentions(self):
        data = corpus()
        for ident, prediction in [
            ("b", {"status": "ERROR", "answer": None, "confidence": None}),
            ("c", {"status": "OK", "answer": "CHECK_SOURCE", "confidence": .89}),
            ("d", {"status": "OK", "answer": "INSUFFICIENT_INFORMATION", "confidence": 1}),
            ("e", {"status": "OK", "answer": "CHECK_SOURCE", "confidence": .95}),
        ]:
            row = copy.deepcopy(data["cases"][0])
            row.update(id=ident, lineage=ident, prediction=prediction)
            data["cases"].append(row)
        result = evaluate_oms_triage(data)
        self.assertEqual(result["test"]["cases"], 5)
        self.assertEqual(result["test"]["coverage"], .4)
        self.assertEqual(result["test"]["precision"], .5)
        self.assertEqual(result["providerErrors"], 1)
        self.assertEqual(result["confusionMatrix"]["CHECK_FINANCE_ACK"]["ABSTAINED"], 3)
        self.assertEqual(result["baseline"]["precision"], 1)
        self.assertEqual(result["baselineOnModelDisplayedCases"]["cases"], 2)

    def test_zero_coverage_has_undefined_precision(self):
        data = corpus()
        data["cases"][0]["prediction"] = {"status": "ABSTAINED", "answer": None, "confidence": None}
        result = evaluate_oms_triage(data)
        self.assertIsNone(result["test"]["precision"])
        self.assertIsNone(result["test"]["precision95Wilson"])
        self.assertFalse(result["proposedNumericalCriteria"]["displayedPrecisionAtLeast90Percent"])

    def test_development_rows_excluded(self):
        data = corpus()
        row = copy.deepcopy(data["cases"][0])
        row.update(id="dev", lineage="dev", split="development", expected="CHECK_SOURCE")
        data["cases"].append(row)
        result = evaluate_oms_triage(data)
        self.assertEqual(result["test"]["precision"], 1)
        self.assertEqual(result["developmentCasesExcluded"], 1)

    def test_lineage_leakage_and_duplicate_ids_rejected(self):
        for duplicate in (True, False):
            with self.subTest(duplicate=duplicate):
                data = corpus()
                row = copy.deepcopy(data["cases"][0])
                if not duplicate:
                    row.update(id="b", split="development")
                data["cases"].append(row)
                with self.assertRaises(ValueError):
                    evaluate_oms_triage(data)

    def test_forbidden_actions_and_invalid_confidence_rejected(self):
        for field, value in [("answer", "RETRY_UNKNOWN"), ("confidence", math.nan),
                             ("confidence", math.inf), ("confidence", 1.01), ("confidence", True)]:
            with self.subTest(field=field, value=value):
                data = corpus()
                data["cases"][0]["prediction"][field] = value
                with self.assertRaises(ValueError):
                    evaluate_oms_triage(data)

    def test_error_cannot_carry_action(self):
        data = corpus()
        data["cases"][0]["prediction"]["status"] = "ERROR"
        with self.assertRaises(ValueError):
            evaluate_oms_triage(data)

    def test_missing_category_reported_and_empty_corpus_rejected(self):
        data = corpus()
        data["pilotCategories"].append("PENDING_DELIVERY")
        result = evaluate_oms_triage(data)
        self.assertEqual(result["missingPilotCategories"], ["PENDING_DELIVERY"])
        self.assertFalse(result["proposedNumericalCriteria"]["everyPilotCategoryPresent"])
        data["cases"] = []
        with self.assertRaises(ValueError):
            evaluate_oms_triage(data)

    def test_perfect_200_synthetic_cases_never_approve_release(self):
        data = corpus()
        row = data["cases"][0]
        data["cases"] = [dict(row, id=str(i), lineage=str(i)) for i in range(200)]
        result = evaluate_oms_triage(data)
        self.assertTrue(all(result["proposedNumericalCriteria"].values()))
        self.assertFalse(result["releaseApproved"])
        self.assertFalse(result["qualityEvidencePrerequisitesDeclared"]["heldOutData"])
        self.assertFalse(result["qualityEvidencePrerequisitesDeclared"]["recordedPredictions"])

    def test_held_out_declaration_cannot_certify_rollout(self):
        data = corpus()
        data["dataKind"] = "held_out"
        data["evaluator"]["predictionSource"] = "recorded"
        data["evaluator"]["thresholdFrozenBeforeTest"] = False
        result = evaluate_oms_triage(data)
        self.assertFalse(result["qualityEvidencePrerequisitesDeclared"]["thresholdFrozenBeforeTest"])
        self.assertFalse(result["releaseApproved"])

    def test_wilson_interval_known_balanced_sample(self):
        low, high = wilson_interval(50, 100)
        self.assertAlmostEqual(low, .40383, places=5)
        self.assertAlmostEqual(high, .59617, places=5)


if __name__ == "__main__":
    unittest.main()
