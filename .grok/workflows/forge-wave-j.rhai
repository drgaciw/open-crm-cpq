let meta = #{
    name: "forge-wave-j",
    description: "Wave J: quote typed pricing apply, VALIDATES attr:in:, FE create+lifecycle, dual-read docs",
    when_to_use: "After PM authorizes forge for docs/forge/19-wave-j-architecture.md",
    phases: [
        #{ title: "Day1", detail: "J1-J5 parallel" },
        #{ title: "Closeout", detail: "J6 docs" },
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
    prompt: "Implement Wave J1 per docs/forge/19-wave-j-architecture.md in repo /home/username01/Projects/open-crm-cpq. Quote-service only: typed PricingServiceClient execute/simulate views; NEVER RestClient.body(JsonNode.class) — read String then Jackson 2 ObjectMapper (I2 Jackson 3 bind bug). Refactor QuoteService.calculate apply helpers to views. Preserve PRICING_* codes and decimal fallbacks. No pricing-service Maven dep. Do not edit CpqAgentToolClients. GitNexus impact PricingServiceClient and QuoteService with --file quote-service before edits. Warn HIGH/CRITICAL/UNKNOWN. JDK 21 JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2. docs/forge/impl/j1-quote-pricing-typed.md status done. Commit locally; no push.",
    label: "impl:J1", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave J2 per docs/forge/19-wave-j-architecture.md. configuration-engine ProductRuleEvaluationCore: add VALIDATES attr:key in:v1,v2 dialect. Parse ' in:' BEFORE '=' / '!='. Keep attr:key=value and attr:key!=value. Unsupported still skip. Missing key or value not in set = hard VALIDATES fail. Unit + ConstraintSolver fixtures. Do not edit product-service validate or gateway. GitNexus impact ProductRuleEvaluationCore --file configuration-engine; UNKNOWN plus rg. JDK 21. docs/forge/impl/j2-validates-attr-in.md. Commit; no push.",
    label: "impl:J2", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave J3 per docs/forge/19-wave-j-architecture.md. FE cpq-quotes only: enable New Quote with minimal reactive form matching QuoteCreateRequest (required name + customerExternalId). quotes-detail: Calculate + Submit using existing QuoteService HTTP. Disable actions by obvious status. Inline error banner. Vitest. Do not edit catalog lib or add NgRx. docs/forge/impl/j3-fe-quote-create-lifecycle.md. Commit; no push.",
    label: "impl:J3", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave J4 per docs/forge/19-wave-j-architecture.md. FE cpq-catalog only: enable New Product create form matching ProductCreateRequest required sku, name, productType, category (no status field). Navigate to detail on success. Vitest. Do not edit quotes lib. docs/forge/impl/j4-fe-catalog-create.md. Commit; no push.",
    label: "impl:J4", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave J5 per docs/forge/19-wave-j-architecture.md. DOCS ONLY: dual-read cutover checklist (empty local PENDING, flag-off soak, rollback). Default approvals.legacy-local-read-enabled MUST remain true. No Java. No application.yml flip. Matrix/ops note + docs/forge/impl/j5-dual-read-readiness.md. Commit; no push.",
    label: "impl:J5", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
let day1 = parallel(jobs);

phase("Closeout");
let j6 = agent(
    "Implement Wave J6: matrix + forge closeout docs/forge/impl/j6-wave-j-docs.md; mark Wave J complete notes in docs/forge/00-orchestrator-status.md only if J1-J5 notes exist. Do not flip dual-read default. Commit; no push.",
    #{ label: "impl:J6", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Verify");
let vjobs = [];
for r in day1 {
    if r != () && r.success && r.output != () {
        vjobs.push(#{
            prompt: "Adversarially verify " + r.output.pr_id + " with rg/read_file against docs/forge/19-wave-j-architecture.md. ok=true only with concrete evidence. Fail if body(JsonNode.class) remains on quote PricingServiceClient, if dual-read default flipped, or if product validate deleted.",
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            model: "grok-4.5",
            output_schema: verify_schema,
        });
    }
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave J forge", day1: day1, j6: j6, verdicts: verdicts });
