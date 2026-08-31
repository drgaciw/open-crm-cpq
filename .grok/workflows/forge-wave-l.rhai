let meta = #{
    name: "forge-wave-l",
    description: "Wave L: quote documents+expire/cancel/clone, catalog delete, price-books portal",
    when_to_use: "After PM authorizes forge for docs/forge/21-wave-l-architecture.md",
    phases: [
        #{ title: "Day1", detail: "L1-L3 parallel" },
        #{ title: "Closeout", detail: "L4 docs" },
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
    prompt: "Implement Wave L1 per docs/forge/21-wave-l-architecture.md. FE quotes only. Add QuoteDocumentDto/DocumentType; QuoteService generate/list/download (blob) for /api/v1/quotes/{id}/documents/**. quote-documents-panel on quotes-detail. Expire SENT with confirm; Cancel not ACCEPTED with confirm; Clone navigate to new DRAFT and UX that BE clone does NOT copy line items. No BE Java. No app.routes/sidebar. Vitest. docs/forge/impl/l1-fe-quote-docs-terminal.md. Commit locally; no push.",
    label: "impl:L1", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave L2 per docs/forge/21-wave-l-architecture.md. FE catalog-detail Delete via ConfirmDialog then ProductService.delete then navigate to list. Error banner. No quotes/shell edits. Vitest. docs/forge/impl/l2-fe-catalog-delete.md. Commit locally; no push.",
    label: "impl:L2", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave L3 per docs/forge/21-wave-l-architecture.md. New FE lib frontend/libs/cpq/price-books. List/detail/create via existing PriceBookService on /api/v1/price-books only (never /api/v1/pricing/price-books). Show entries on detail. Wire app.routes + sidebar. Vitest. docs/forge/impl/l3-fe-price-books.md. Commit locally; no push. Do not flip dual-read default.",
    label: "impl:L3", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
let day1 = parallel(jobs);

phase("Closeout");
let l4 = agent(
    "Implement Wave L4: docs/forge/impl/l4-wave-l-docs.md closeout; matrix touch-ups. Dual-read default stays true. Commit; no push.",
    #{ label: "impl:L4", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Verify");
let vjobs = [];
for r in day1 {
    if r != () && r.success && r.output != () {
        vjobs.push(#{
            prompt: "Adversarially verify " + r.output.pr_id + " against docs/forge/21-wave-l-architecture.md with rg/read_file. ok=true only with evidence. Fail if dual-read default flipped, clone claimed to copy lines, or FE called document-generation microservice directly.",
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            model: "grok-4.5",
            output_schema: verify_schema,
        });
    }
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave L forge", day1: day1, l4: l4, verdicts: verdicts });
