"""Build the mock CPQ cost export (version 1) from benchmark heuristics.

Each catalog ProductType gets one mock product. Costs are fixed per billing
unit (F-1: never a percentage of the selling price). The benchmark shares
below are only used once, at a reference list price, to size those fixed
amounts. Run: python3 build_mock_cost_export.py <outdir>
"""
import csv
import sys
from decimal import Decimal as D, ROUND_HALF_UP

EFFECTIVE_FROM = "2026-10-01"
VERSION = 1
APPROVED_BY = "Danny (project owner, Finance approver)"
CURRENCY = "USD"
FLOOR = D("0.35")

# Benchmark shares of revenue (see sources in the assumptions doc).
HOSTING = D("0.12")       # 8-15% scaled SaaS, 12-22% under $20M ARR
THIRD_PARTY = D("0.02")   # embedded APIs / components
PAYMENT = D("0.012")      # Stripe Billing 0.7% + Invoicing 0.4% + ACH 0.8% capped $5
SUPPORT_CS_SRE = D("0.11")  # support 2-4% + CS 4-7% + SRE 2-4%

# (productExternalId, productType, billingUnit, refListPrice, recurring?, components{component: share-or-absolute})
# A share is applied to refListPrice; an absolute ("abs") is used as-is.
PRODUCTS = [
    ("MOCK-SAAS-SEAT-NAMED", "SAAS_SEAT_NAMED", "seat", D("40"), True,
     {"DIRECT": HOSTING + THIRD_PARTY + PAYMENT, "COST_TO_SERVE": SUPPORT_CS_SRE}),
    ("MOCK-SAAS-SEAT-CONCURRENT", "SAAS_SEAT_CONCURRENT", "seat", D("80"), True,
     {"DIRECT": HOSTING + THIRD_PARTY + PAYMENT, "COST_TO_SERVE": SUPPORT_CS_SRE}),
    ("MOCK-SAAS-FLAT-RATE", "SAAS_FLAT_RATE", "subscription", D("1200"), True,
     {"DIRECT": HOSTING + THIRD_PARTY + PAYMENT, "COST_TO_SERVE": SUPPORT_CS_SRE}),
    ("MOCK-SAAS-USAGE-METERED", "SAAS_USAGE_METERED", "rated unit (1,000 calls)", D("0.50"), False,
     {"USAGE_UNIT": D("0.20")}),
    ("MOCK-ONPREM-PERPETUAL", "ONPREM_PERPETUAL", "licence", D("10000"), False,
     {"PASS_THROUGH": D("0.05")}),
    ("MOCK-ONPREM-CORE", "ONPREM_CORE", "core", D("2000"), False,
     {"PASS_THROUGH": D("0.05")}),
    ("MOCK-ONPREM-SUBSCRIPTION", "ONPREM_SUBSCRIPTION", "seat", D("30"), True,
     {"DIRECT": THIRD_PARTY + PAYMENT + D("0.03"), "COST_TO_SERVE": D("0.08")}),
    ("MOCK-ONPREM-SUPPORT", "ONPREM_SUPPORT", "licence", D("166.6667"), True,
     {"COST_TO_SERVE": D("0.35")}),
    ("MOCK-HYBRID-PORTABLE", "HYBRID_PORTABLE", "seat", D("50"), True,
     {"DIRECT": HOSTING + D("0.04") + PAYMENT, "COST_TO_SERVE": SUPPORT_CS_SRE}),
    ("MOCK-SUPPORT-BASIC", "SUPPORT_BASIC", "subscription", D("500"), True,
     {"COST_TO_SERVE": D("0.30")}),
    ("MOCK-SUPPORT-PREMIUM", "SUPPORT_PREMIUM", "subscription", D("1500"), True,
     {"COST_TO_SERVE": D("0.35")}),
    ("MOCK-SUPPORT-ENTERPRISE", "SUPPORT_ENTERPRISE", "subscription", D("5000"), True,
     {"COST_TO_SERVE": D("0.45")}),
    ("MOCK-PROFESSIONAL-SERVICES", "PROFESSIONAL_SERVICES", "hour", D("200"), False,
     {"LABOUR": ("abs", D("100"))}),
    ("MOCK-TRAINING", "TRAINING", "hour", D("150"), False,
     {"LABOUR": ("abs", D("85"))}),
    ("MOCK-ADD-ON-API", "ADD_ON_API", "subscription", D("250"), True,
     {"DIRECT": D("0.15") + PAYMENT, "COST_TO_SERVE": D("0.03")}),
    ("MOCK-ADD-ON-STORAGE", "ADD_ON_STORAGE", "rated unit (GB-month)", D("0.10"), False,
     {"USAGE_UNIT": D("0.40")}),
    ("MOCK-ADD-ON-ANALYTICS", "ADD_ON_ANALYTICS", "seat", D("15"), True,
     {"DIRECT": D("0.20") + PAYMENT, "COST_TO_SERVE": D("0.05")}),
    ("MOCK-ADD-ON-SECURITY", "ADD_ON_SECURITY", "seat", D("10"), True,
     {"DIRECT": D("0.08") + D("0.10") + PAYMENT, "COST_TO_SERVE": D("0.04")}),
    ("MOCK-ADD-ON-INTEGRATION", "ADD_ON_INTEGRATION", "connector", D("750"), True,
     {"PASS_THROUGH": ("abs", D("300")), "COST_TO_SERVE": D("0.05")}),
    ("MOCK-HARDWARE", "HARDWARE", "unit", D("1000"), False,
     {"PASS_THROUGH": D("0.80")}),
    # Acceptance fixture product (AC-02 named seat, list $40) so ramp tests get real margin.
    ("PROD-SEAT", "SAAS_SEAT_NAMED", "seat", D("40"), True,
     {"DIRECT": HOSTING + THIRD_PARTY + PAYMENT, "COST_TO_SERVE": SUPPORT_CS_SRE}),
]
# Deliberately absent: HYBRID_MIGRATION_RAMP (costed by its components, F-1)
# and CUSTOM (costed per deal; fails closed until someone costs it).


def q4(x):
    return x.quantize(D("0.0001"), rounding=ROUND_HALF_UP)


def main(outdir):
    export_rows, check_rows = [], []
    for pid, ptype, unit, price, recurring, comps in PRODUCTS:
        total = D(0)
        for comp, spec in comps.items():
            cost = spec[1] if isinstance(spec, tuple) else price * spec
            cost = q4(cost)
            total += cost
            export_rows.append({
                "productExternalId": pid, "component": comp, "billingUnit": unit,
                "unitCost": f"{cost:.4f}", "currency": CURRENCY,
                "effectiveFrom": EFFECTIVE_FROM, "version": VERSION,
                "approvedBy": APPROVED_BY,
            })
        margin = (price - total) / price
        floor_price = total / (1 - FLOOR)
        max_disc = 1 - floor_price / price
        check_rows.append((pid, ptype, unit, price, total, margin, floor_price, max_disc))

    with open(f"{outdir}/cpq-cost-export-v1.csv", "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(export_rows[0]))
        w.writeheader()
        w.writerows(export_rows)

    with open(f"{outdir}/margin-check.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["productExternalId", "productType", "billingUnit", "refListPrice",
                    "unitCost", "marginAtList", "netPriceAt35Floor", "maxDiscountBeforeFloor"])
        for pid, ptype, unit, price, total, m, fp, md in check_rows:
            w.writerow([pid, ptype, unit, f"{price:.4f}", f"{total:.4f}",
                        f"{m:.3f}", f"{fp:.4f}", f"{md:.3f}"])
    for r in check_rows:
        print(f"{r[0]:28} cost {r[4]:>10.4f}  margin@list {r[5]:6.1%}  max disc {r[7]:6.1%}")
    print(len(export_rows), "export rows")


if __name__ == "__main__":
    main(sys.argv[1])
