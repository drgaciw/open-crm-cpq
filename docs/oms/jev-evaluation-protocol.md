# OMS Jev quality evaluation handoff

This is a local scoring workflow, not a provider runner or approval to enable Jev. No independently
reviewed corpus or recorded Jev predictions has been supplied. The existing quality gate remains open.

## Collect and freeze evidence

1. OMS operations and QA select the pilot categories and de-identify historical exceptions. Retain
   source lineage locally so revisions of the same order/exception cannot cross development and test.
   The owner of the corpus must record its provenance, permitted use, selection method and date range.
2. Have domain reviewers label the best investigation step without seeing Jev predictions. Adjudicate
   disagreements and record reviewers, reasons and agreement separately in approved local storage.
   Do not insert free-text evidence or production IDs into the scoring bundle or provider state.
3. Freeze development and held-out membership, labels and a category-rule baseline before evaluation.
   The baseline must be an existing rule or a development-selected rule, never selected using test labels.
   Choose the threshold on development data, then freeze it with model and rubric versions. Preserve
   hashes of the frozen inputs; scorer output includes a SHA-256 of the exact input bundle.
4. Obtain recorded predictions only through a separately approved evaluation process. Keep failures and
   abstentions, and reconcile every held-out input to exactly one attempt. A prediction is one `OK`
   typed answer with confidence, `ABSTAINED`, or `ERROR`; the last two have null answer/confidence.
   Manually authoring answers is valid only for `synthetic` data with `scripted` prediction source.
5. Score once against the held-out set. If test results change the rubric, threshold, baseline or pilot,
   use a new held-out set for the next acceptance decision. Store raw predictions and actual evaluator
   version evidence separately; configured aliases and metadata declarations are not proof of identity.

## Run locally

The checked-in [example bundle](jev-evaluation-example.json) contains two synthetic cases to demonstrate
the schema. Its two categories are examples, not an approved pilot selection. Replace the example with
an approved local corpus; do not commit customer records, raw evidence or credentials.

```powershell
python scripts/oms/evaluate_jev.py docs/oms/jev-evaluation-example.json
python -m unittest discover -s scripts/oms -p 'test_*.py' -v
```

The scorer uses only the Python standard library and makes no network requests. It rejects duplicate
case IDs, development/test lineage overlap, unknown action codes, missing prediction records and invalid
confidence values. It cannot detect omitted source cases, fabricated provenance or mislabeled records;
QA must reconcile the input inventory and independently review those declarations.

The report includes test-only precision/coverage, abstentions, provider errors, a confusion matrix,
per-category results, 95% Wilson intervals, confidence bins and category-rule baseline results.
`baselineOnModelDisplayedCases` limits the comparison to cases where Jev would display a suggestion;
its precision still excludes baseline abstentions, so compare both coverage and precision. Errors and
low-confidence answers remain in the total denominator for coverage. `INSUFFICIENT_INFORMATION` is
counted as abstention, even with high confidence.

## Review before progressing

The proposed numerical criteria remain at least 200 held-out cases, every pilot category represented,
and at least 90% precision on displayed suggestions. These are point-estimate checks, not release
approval. Review sample uncertainty, rare-category counts, coverage and baseline improvement; decide
minimum coverage and per-category support before acceptance. Confidence bins do not establish
calibration with small samples. Repeated cases from one lineage violate the independent-observation
assumption behind Wilson intervals and need cluster-aware statistical review or one case per lineage.

`releaseApproved` is always false: the scorer cannot authorize a release. Independent labeling,
threshold freeze, corpus lineage and provider source are declarations that require review, even when
marked true. Synthetic data and scripted replies cannot satisfy the model-quality gate. Safety tests,
deployed identity, provider/data approval, aggregate budget, latency/cost and kill-switch checks remain
separate requirements in the [integration plan](../plans/oms-jev-integration-plan.md).

This script's tests are explicitly run locally; they are not added to the existing CI allowlists.
