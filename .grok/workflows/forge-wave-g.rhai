let meta = #{
    name: "forge-wave-g",
    description: "Wave G: S2B-8 rule core, S2C-12 approval BFF, S2D-3a preflight + FE CPQ HTTP",
    when_to_use: "After PM authorizes forge for docs/forge/16-wave-g-architecture.md",
    phases: [
        #{ title: "Day1", detail: "G1 G3 G5 G6 G7 G8 parallel" },
        #{ title: "Day2", detail: "G2 after G1, G4 after G3" },
        #{ title: "Closeout", detail: "G9 docs" },
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
    prompt: "Implement Wave G1 per docs/forge/16-wave-g-architecture.md: extract/share rule evaluation core from StatelessConfigurationEvaluator; extend RECOMMENDS (soft), DEFAULT (apply), VALIDATES (hard); enrich ProductConfigurationRuleDto with conditionExpression; keep F2 evaluate endpoint green and REQUIRES/EXCLUDES parity. Do NOT rewrite ConstraintSolver yet (G2). Do NOT delete product validate. Prefer JDK 21. Write docs/forge/impl/g1-rule-core.md. Commit locally; no push.",
    label: "impl:G1", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave G3 per docs/forge/16-wave-g-architecture.md: add POST /api/v1/approvals (create/submit) on approval-routing for quote BFF; map tier/type; unit tests; leave Kafka DiscountApprovalEventConsumer intact; do not edit quote-service. Write docs/forge/impl/g3-routing-submit.md. Commit locally; no push. Prefer JDK 21.",
    label: "impl:G3", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave G5 per docs/forge/16-wave-g-architecture.md: quote-service preflight via gateway POST /api/v1/configuration/validate parsing ConfigurationValidationResult; retarget RestClient to gateway URL (not product :8083); wire into submitForReview; block on valid=false with BusinessException. No inline REQUIRES/EXCLUDES. Prefer JDK 21. Write docs/forge/impl/g5-quote-preflight.md. Commit locally; no push. Avoid editing ApprovalService (G4).",
    label: "impl:G5", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave G6 per docs/forge/16-wave-g-architecture.md: FE catalog-list (and detail if present) use existing ProductService HttpClient instead of MOCK_CATALOG_PRODUCTS happy path; keep mocks for tests; Vitest green; write docs/forge/impl/g6-fe-catalog-http.md. Commit locally; no push.",
    label: "impl:G6", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave G7 per docs/forge/16-wave-g-architecture.md: FE quotes-list (and detail if present) use existing QuoteService HttpClient instead of MOCK_QUOTES happy path; Vitest green; write docs/forge/impl/g7-fe-quotes-http.md. Commit locally; no push.",
    label: "impl:G7", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave G8 per docs/forge/16-wave-g-architecture.md: FE pricing simulator-page calls PricingExecutionService instead of mockExecuteOk happy path; error empty-state; Vitest; write docs/forge/impl/g8-fe-simulator-http.md. Commit locally; no push.",
    label: "impl:G8", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
let day1 = parallel(jobs);

phase("Day2");
let g2 = agent(
    "Implement Wave G2 per docs/forge/16-wave-g-architecture.md: wire ConstraintSolver to shared rule core from G1 (product rules via ProductConfigurationRuleClient); remove color/option_a placeholder; session soft RECOMMENDS vs hard REQUIRES/EXCLUDES/VALIDATES; session tests. Prefer JDK 21. Write docs/forge/impl/g2-constraint-solver.md. Commit locally; no push.",
    #{ label: "impl:G2", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);
let g4 = agent(
    "Implement Wave G4 HIGH-risk per docs/forge/16-wave-g-architecture.md: quote ApprovalService BFF → approval-routing with SINGLE-WRITER to routing for approval records + Quote status projection only; dual-read pending; NO silent dual-write of ApprovalRequest to both DBs; keep /api/v1/quotes/**/approvals* paths. Tests must fail on conflicting dual-write. Prefer JDK 21. Write docs/forge/impl/g4-approval-bff.md. Commit locally; no push.",
    #{ label: "impl:G4", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Closeout");
let g9 = agent(
    "Implement Wave G9: update CPQ_API_CONTRACT_MATRIX for S2B-8/S2C-12/S2D-3a; forge status COMPLETE; docs/forge/impl/g9-wave-g-docs.md. Commit locally; no push.",
    #{ label: "impl:G9", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Verify");
let vjobs = [];
for r in day1 {
    if r != () && r.success && r.output != () {
        vjobs.push(#{
            prompt: "Adversarially verify " + r.output.pr_id + " with file reads/rg. ok=true only with concrete evidence matching Wave G acceptance.",
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            model: "grok-4.5",
            output_schema: verify_schema,
        });
    }
}
if g4 != () && g4.success {
    vjobs.push(#{
        prompt: "Verify G4: no silent dual-write — quote ApprovalService must not save local ApprovalRequest AND call routing create for the same submit without dual-read design. ok=true only with code evidence of single-writer.",
        label: "verify:G4",
        capability_mode: "read-only",
        model: "grok-4.5",
        output_schema: verify_schema,
    });
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave G three-track forge", day1: day1, g2: g2, g4: g4, g9: g9, verdicts: verdicts });
