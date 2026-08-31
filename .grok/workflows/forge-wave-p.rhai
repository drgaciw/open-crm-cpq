let meta = #{
    name: "forge-wave-p",
    description: "Wave P: product-bundles portal, rule impact, catalog config-rules",
    when_to_use: "After PM authorizes forge for docs/forge/28-wave-p-architecture.md",
    phases: [
        #{ title: "Day1", detail: "P1-P3 parallel" },
        #{ title: "Closeout", detail: "P4 docs" },
        #{ title: "Verify", detail: "Adversarial verify" },
    ],
};

let impl_schema = #{
    "type": "object",
    "required": ["pr_id", "status", "files_touched", "tests_run", "notes"],
    "properties": #{
        "pr_id": #{ "type": "string" },
        "status": #{ "type": "string" },
        "files_touched": #{ "type": "array", "maxItems": 80, "items": #{ "type": "string" } },
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
    prompt: "Implement Wave P1 per docs/forge/28-wave-p-architecture.md. New FE lib frontend/libs/cpq/product-bundles (@frontend/cpq-product-bundles). List/detail/create + items add/remove + delete ConfirmDialog via /api/v1/product-bundles only. Models in the new lib not shared-models index. P1 owns app.routes + sidebar Bundles + tsconfig path + test:cpq. Never configuration/rules or subscriptions. No Java. Vitest NX_DAEMON=false nx test cpq-product-bundles. docs/forge/impl/p1-fe-product-bundles.md. GitNexus impact UNKNOWN + rg. Commit locally; no push. Dual-read stays true.",
    label: "impl:P1", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave P2 per docs/forge/28-wave-p-architecture.md. FE rule-sets only. Extract rule-impact dialog on rule-set-detail. RuleService.analyzeImpact GET /api/v1/rules/{id}/impact. Do not edit pricing-data-access. No shell. Vitest NX_DAEMON=false nx test cpq-rule-sets. docs/forge/impl/p2-fe-rule-impact.md. GitNexus impact. Commit locally; no push. Dual-read stays true.",
    label: "impl:P2", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave P3 per docs/forge/28-wave-p-architecture.md. FE catalog extracted config-rules-panel. GET /api/v1/configuration/rules/product/{id} + create + delete ConfirmDialog. Never pricing /api/v1/rules. No validate UI. No shell. No shared-models index.ts. Extract panel — do not dump into catalog-detail mega-file. Vitest NX_DAEMON=false nx test cpq-catalog. docs/forge/impl/p3-fe-catalog-config-rules.md. GitNexus UNKNOWN + rg. Commit locally; no push. Dual-read stays true.",
    label: "impl:P3", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
let day1 = parallel(jobs);

phase("Closeout");
let p4 = agent(
    "Implement Wave P4: docs/forge/impl/p4-wave-p-docs.md; ADR-006 header O3 on main still Proposed; matrix product-options + bundles FE + subscriptions DTO list; AGENT/ARCHITECTURE Wave O COMPLETE. Dual-read stays true. Do not edit docs/forge/00-orchestrator-status.md. Commit; no push.",
    #{ label: "impl:P4", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Verify");
let vjobs = [];
for r in day1 {
    if r != () && r.success && r.output != () {
        vjobs.push(#{
            prompt: "Adversarially verify " + r.output.pr_id + " against docs/forge/28-wave-p-architecture.md with rg/read_file. ok=true only with evidence. Fail if dual-read flipped, P1 used configuration/rules or subscriptions, P2/P3 edited app.routes/sidebar, P3 called pricing /api/v1/rules or validate UI, product validate deleted, or 00-orchestrator-status edited.",
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            model: "grok-4.5",
            output_schema: verify_schema,
        });
    }
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave P forge", day1: day1, p4: p4, verdicts: verdicts });
