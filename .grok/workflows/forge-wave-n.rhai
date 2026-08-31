let meta = #{
    name: "forge-wave-n",
    description: "Wave N: price-book header write-back, pricing-customers portal, quote soft-delete",
    when_to_use: "After PM authorizes forge for docs/forge/23-wave-n-architecture.md",
    phases: [
        #{ title: "Day1", detail: "N1-N3 parallel" },
        #{ title: "Closeout", detail: "N4 docs" },
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
    prompt: "Implement Wave N1 per docs/forge/23-wave-n-architecture.md. FE libs/cpq/price-books only. Header Edit/Save via PriceBookService.update (PriceBookUpdateRequest) and ConfirmDialog Delete via PriceBookService.delete then /price-books. Reuse isPriceBookPublished. Disable header writes when published. Banner PRICE_BOOK_PUBLISHED_IMMUTABLE. Do not regress M2 entry CRUD. No shell. No Java. Vitest NX_DAEMON=false nx test cpq-price-books. docs/forge/impl/n1-fe-price-book-header.md. GitNexus impact --file. Commit locally; no push. Dual-read stays true.",
    label: "impl:N1", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave N2 per docs/forge/23-wave-n-architecture.md. New FE lib frontend/libs/cpq/pricing-customers (@frontend/cpq-pricing-customers). List/detail/create/update/delete via existing PricingCustomerService on /api/v1/pricing-customers only (optional segment filter). Wire app.routes + sidebar Pricing customers. tsconfig path + frontend/package.json test:cpq. Never /api/v1/customers or CRM contacts. N2 owns shell. Vitest NX_DAEMON=false nx test cpq-pricing-customers. docs/forge/impl/n2-fe-pricing-customers.md. GitNexus impact. Commit locally; no push. Do not flip dual-read.",
    label: "impl:N2", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave N3 per docs/forge/23-wave-n-architecture.md. FE quotes-detail only. ConfirmDialog soft-delete via QuoteService.delete then /quotes. Disable when ACCEPTED. Banner QUOTE_ACCEPTED_NO_DELETE. quote.service.spec DELETE coverage. No BE Java. No shell. Do not rewrite header edit or clone copy. Vitest NX_DAEMON=false nx test cpq-quotes. docs/forge/impl/n3-fe-quote-delete.md. GitNexus impact delete --file quote.service.ts. Commit locally; no push.",
    label: "impl:N3", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
let day1 = parallel(jobs);

phase("Closeout");
let n4 = agent(
    "Implement Wave N4: docs/forge/impl/n4-wave-n-docs.md closeout; matrix notes for price-book header FE, pricing-customers FE, quote DELETE FE. Dual-read default stays true. Do not edit docs/forge/00-orchestrator-status.md. Commit; no push.",
    #{ label: "impl:N4", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Verify");
let vjobs = [];
for r in day1 {
    if r != () && r.success && r.output != () {
        vjobs.push(#{
            prompt: "Adversarially verify " + r.output.pr_id + " against docs/forge/23-wave-n-architecture.md with rg/read_file. ok=true only with evidence. Fail if dual-read default flipped, /api/v1/customers used for pricing customers, ACCEPTED quote delete enabled, published price-book writes allowed, M2 entry CRUD removed, or N1/N3 edited app.routes/sidebar.",
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            model: "grok-4.5",
            output_schema: verify_schema,
        });
    }
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave N forge", day1: day1, n4: n4, verdicts: verdicts });
