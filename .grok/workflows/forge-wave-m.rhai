let meta = #{
    name: "forge-wave-m",
    description: "Wave M: quote header edit, price-book entry CRUD, clone copies line items",
    when_to_use: "After PM authorizes forge for docs/forge/22-wave-m-architecture.md",
    phases: [
        #{ title: "Day1", detail: "M1-M3 parallel" },
        #{ title: "Closeout", detail: "M4 docs" },
        #{ title: "Verify", detail: "Adversarial verify" },
    ],
};

let impl_schema = #{
    "type": "object",
    "required": ["pr_id", "status", "files_touched", "tests_run", "notes"],
    "properties": #{
        "pr_id": #{ "type": "string" },
        "status": #{ "type": "string" },
        "files_touched": #{ "type": "array", "maxItems": 60, "items": #{ "type": "string" } },
        "tests_run": #{ "type": "array", "maxItems": 25, "items": #{ "type": "string" } },
        "notes": #{ "type": "string" },
    },
};

let verify_schema = #{
    "type": "object",
    "required": ["pr_id", "ok", "reason", "evidence"],
    "properties": #{
        "pr_id": #{ "type": "string" },
        "ok": #{ "type": "boolean" },
        "reason": #{ "type": "string" },
        "evidence": #{ "type": "string" },
    },
};

phase("Day1");
let jobs = [];
jobs.push(#{
    prompt: "Implement Wave M1 per docs/forge/22-wave-m-architecture.md. FE quotes only. quote-header-edit on quotes-detail for DRAFT|REJECTED using existing QuoteService.update PUT /api/v1/quotes/{id} and QuoteUpdateRequest (do not send customerExternalId/dealType/status/totals). Reload getById. Error banner QUOTE_NOT_EDITABLE. Update clone ConfirmDialog + clone-note to say header and line items ARE copied and Calculate is still required for waterfall (do not claim waterfall/ARR copied). Add quote.service.spec PUT coverage. No BE Java. No app.routes/sidebar. Vitest nx test cpq-quotes. docs/forge/impl/m1-fe-quote-header-edit.md. GitNexus impact before symbol edits with --file. Commit locally; no push. Dual-read default stays true.",
    label: "impl:M1", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave M2 per docs/forge/22-wave-m-architecture.md. FE libs/cpq/price-books only. Entry create/update/delete via existing PriceBookService on /api/v1/price-books/{id}/entries (never /api/v1/pricing/price-books). Disable writes when published (effectiveDate present and <= today). ConfirmDialog delete. Banners for PRICE_BOOK_PUBLISHED_IMMUTABLE and DUPLICATE_PRICE_BOOK_ENTRY. Do not expand shared-ui DataTable. No shell/routes/sidebar. No quotes. Vitest nx test cpq-price-books. docs/forge/impl/m2-fe-price-book-entries.md. GitNexus impact before symbol edits. Commit locally; no push. Do not flip dual-read default.",
    label: "impl:M2", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave M3 per docs/forge/22-wave-m-architecture.md. quote-service Java only. QuoteService.clone copies non-deleted line items (new ids, commercial snapshot including product fields, qty, UoM, prices, discounts, billingFrequency, sortOrder, notes, metadata). Skip pricingExplanationCode and pricingWaterfallJson. Roll up totals like QuoteLineItemService.recalculateQuoteTotals (extendedAmount/discountAmount sums). Do NOT call Quote.recalculateTotals() (10% tax stub). Include lineItems on returned DTO. QuoteServiceCloneTest. JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2. No FE. docs/forge/impl/m3-be-quote-clone-lines.md. GitNexus impact clone --file QuoteService.java. Commit locally; no push.",
    label: "impl:M3", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
let day1 = parallel(jobs);

phase("Closeout");
let m4 = agent(
    "Implement Wave M4: docs/forge/impl/m4-wave-m-docs.md closeout; matrix notes for quote PUT FE, entry writes, clone lines. Dual-read default stays true. Do not edit docs/forge/00-orchestrator-status.md. Commit; no push.",
    #{ label: "impl:M4", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Verify");
let vjobs = [];
for r in day1 {
    if r != () && r.success && r.output != () {
        vjobs.push(#{
            prompt: "Adversarially verify " + r.output.pr_id + " against docs/forge/22-wave-m-architecture.md with rg/read_file. ok=true only with evidence. Fail if dual-read default flipped, Quote.recalculateTotals used in clone, /api/v1/pricing/price-books used, contract-asset FE added, or M3 edited quotes-detail.",
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            model: "grok-4.5",
            output_schema: verify_schema,
        });
    }
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave M forge", day1: day1, m4: m4, verdicts: verdicts });
