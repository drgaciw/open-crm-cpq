"""Offline OMS triage scoring. Reads local, de-identified records; never calls a provider."""
import argparse
import hashlib
import json
import math
from collections import Counter
from pathlib import Path

CHOICES = frozenset({
    "CHECK_SOURCE", "CHECK_FULFILLMENT_EVIDENCE", "CHECK_ACTIVATION_ACK",
    "CHECK_FINANCE_ACK", "CHECK_DELIVERY_STATUS", "ESCALATE_INTEGRATION",
    "INSUFFICIENT_INFORMATION",
})
ABSTAIN = "INSUFFICIENT_INFORMATION"


def wilson_interval(correct, count):
    """Two-sided 95% Wilson interval; undefined when there are no suggestions."""
    if not count:
        return None
    z = 1.959963984540054
    p = correct / count
    denominator = 1 + z * z / count
    center = (p + z * z / (2 * count)) / denominator
    radius = z * math.sqrt(p * (1 - p) / count + z * z / (4 * count * count)) / denominator
    return [max(0, center - radius), min(1, center + radius)]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def probability(value):
    return type(value) in (int, float) and math.isfinite(value) and 0 <= value <= 1


def summary(rows, answer):
    shown = [(row, answer(row)) for row in rows if answer(row) is not None]
    correct = sum(code == row["expected"] for row, code in shown)
    return {
        "cases": len(rows), "suggestions": len(shown), "correct": correct,
        "abstentions": len(rows) - len(shown),
        "coverage": len(shown) / len(rows) if rows else None,
        "precision": correct / len(shown) if shown else None,
        "precision95Wilson": wilson_interval(correct, len(shown)),
    }


def evaluate_oms_triage(bundle):
    require(type(bundle) is dict and bundle.get("schemaVersion") == 1, "Expected schemaVersion 1")
    require(bundle.get("dataKind") in ("synthetic", "held_out"), "Invalid dataKind")
    evaluator = bundle.get("evaluator", {})
    require(type(evaluator) is dict, "evaluator must be an object")
    require(type(evaluator.get("model")) is str and bool(evaluator["model"].strip()), "model is required")
    require(evaluator.get("rubricVersion") == "oms-exception-triage-v1", "Unsupported rubric")
    require(evaluator.get("predictionSource") in ("scripted", "recorded"), "Invalid predictionSource")
    require(type(evaluator.get("thresholdFrozenBeforeTest")) is bool, "Declare threshold freeze")
    threshold = evaluator.get("threshold")
    require(probability(threshold), "threshold must be a finite probability")
    review = bundle.get("labelReview", {})
    require(type(review) is dict and type(review.get("independent")) is bool, "Declare independent label review")
    require(type(review.get("reviewer")) is str and bool(review["reviewer"].strip()), "reviewer pseudonym required")
    categories = bundle.get("pilotCategories")
    require(type(categories) is list and bool(categories)
            and all(type(c) is str and bool(c.strip()) for c in categories), "pilotCategories must be nonempty")
    require(len(set(categories)) == len(categories), "Duplicate pilot category")
    cases = bundle.get("cases")
    require(type(cases) is list and bool(cases), "A nonempty case corpus is required")
    ids, lineage_splits = set(), {}
    for index, row in enumerate(cases):
        require(type(row) is dict, f"Case {index}: expected object")
        for key in ("id", "lineage"):
            require(type(row.get(key)) is str and bool(row[key].strip()), f"Case {index}: {key} required")
        require(row["id"] not in ids, f"Case {index}: duplicate case ID")
        ids.add(row["id"])
        split = row.get("split")
        require(split in ("development", "test"), f"Case {index}: invalid split")
        require(lineage_splits.setdefault(row["lineage"], split) == split,
                f"Case {index}: lineage appears in development and test")
        require(row.get("category") in categories, f"Case {index}: category outside frozen pilot")
        require(type(row.get("expected")) is str and row["expected"] in CHOICES,
                f"Case {index}: invalid reviewed label")
        require(type(row.get("baseline")) is str and row["baseline"] in CHOICES,
                f"Case {index}: invalid category-rule baseline")
        prediction = row.get("prediction")
        require(type(prediction) is dict, f"Case {index}: prediction required, including failed attempts")
        status = prediction.get("status")
        require(status in ("OK", "ABSTAINED", "ERROR"), f"Case {index}: invalid prediction status")
        if status == "OK":
            require(type(prediction.get("answer")) is str and prediction["answer"] in CHOICES,
                    f"Case {index}: forbidden or unknown answer code")
            require(probability(prediction.get("confidence")), f"Case {index}: invalid confidence")
        else:
            require(prediction.get("answer") is None and prediction.get("confidence") is None,
                    f"Case {index}: failed/abstained attempt must have null answer and confidence")

    rows = [row for row in cases if row["split"] == "test"]
    require(bool(rows), "At least one test case is required")

    def displayed(row):
        p = row["prediction"]
        return p["answer"] if p["status"] == "OK" and p["confidence"] >= threshold and p["answer"] != ABSTAIN else None

    def baseline(row):
        return row["baseline"] if row["baseline"] != ABSTAIN else None

    metrics = summary(rows, displayed)
    confusion = {code: Counter() for code in sorted(CHOICES)}
    bins = [[] for _ in range(10)]
    for row in rows:
        answer = displayed(row)
        confusion[row["expected"]][answer or "ABSTAINED"] += 1
        if answer is not None:
            confidence = row["prediction"]["confidence"]
            bins[min(9, int(confidence * 10))].append((confidence, answer == row["expected"]))
    accepted = [row for row in rows if displayed(row) is not None]
    missing = sorted(set(categories) - {row["category"] for row in rows})
    lineage_count = len({row["lineage"] for row in rows})
    return {
        "schemaVersion": 1,
        "dataKind": bundle["dataKind"],
        "evaluator": evaluator,
        "labelReviewDeclared": review,
        "test": metrics,
        "testLineages": lineage_count,
        "developmentCasesExcluded": len(cases) - len(rows),
        "providerErrors": sum(row["prediction"]["status"] == "ERROR" for row in rows),
        "baseline": summary(rows, baseline),
        "baselineOnModelDisplayedCases": summary(accepted, baseline),
        "confusionMatrix": {code: dict(counts) for code, counts in confusion.items()},
        "byCategory": {category: summary([r for r in rows if r["category"] == category], displayed)
                       for category in categories},
        "displayedConfidenceBins": [{"lower": i / 10, "upper": (i + 1) / 10, "count": len(values),
                                    "meanConfidence": sum(v[0] for v in values) / len(values),
                                    "observedPrecision": sum(v[1] for v in values) / len(values)}
                                   for i, values in enumerate(bins) if values],
        "proposedNumericalCriteria": {
            "atLeast200TestCases": len(rows) >= 200,
            "everyPilotCategoryPresent": not missing,
            "displayedPrecisionAtLeast90Percent": metrics["precision"] is not None and metrics["precision"] >= .9,
        },
        "missingPilotCategories": missing,
        "qualityEvidencePrerequisitesDeclared": {
            "heldOutData": bundle["dataKind"] == "held_out",
            "recordedPredictions": evaluator["predictionSource"] == "recorded",
            "thresholdFrozenBeforeTest": evaluator["thresholdFrozenBeforeTest"],
            "independentLabelReview": review["independent"],
        },
        "releaseApproved": False,
        "limitations": [
            "Local metadata declarations do not verify independent labeling, holdout integrity or provider identity.",
            "Synthetic/scripted results are harness evidence only; numerical criteria do not approve rollout.",
            "Wilson intervals assume independent cases; repeated lineage requires cluster-aware statistical review.",
            "No minimum coverage or per-category sample-size gate is set; review abstention and category counts.",
            "Safety regressions, latency, cost and deployment gates require separate evidence.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("corpus", type=Path)
    args = parser.parse_args()
    try:
        raw = args.corpus.read_bytes()
        result = evaluate_oms_triage(json.loads(raw.decode("utf-8")))
        result["corpusSha256"] = hashlib.sha256(raw).hexdigest()
    except (ValueError, OSError, TypeError, KeyError) as error:
        parser.exit(2, f"Invalid evaluation corpus: {error}\n")
    print(json.dumps(result, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
