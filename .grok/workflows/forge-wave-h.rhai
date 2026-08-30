let meta = #{
    name: "forge-wave-h",
    description: "Wave H: VALIDATES expression, AI validate parse, CRM Bolt hygiene, TTL rule cache",
    when_to_use: "After PM authorizes forge for docs/forge/17-wave-h-architecture.md",
    phases: [
        #{ title: "Day1", detail: "H1-H6 parallel" },
        #{ title: "Day2", detail: "H7 dual-read prep + H8 docs" },
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
    prompt: "Implement Wave H1 per docs/forge/17-wave-h-architecture.md: VALIDATES conditionExpression attr:key=value dialect in ProductRuleEvaluationCore; ConstraintSolver pass session selections as attrs; skip expression when attrs empty so List<Long> agent path does not false-fail; keep targetProductId path. No gateway/product validate edits. JDK 21. docs/forge/impl/h1-validates-expression.md. Commit locally; no push.",
    label: "impl:H1", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave H2: CpqAgentToolClients parse ConfigurationValidationResult (valid+violations) from gateway validate JSON; unit fixtures. Prefer JDK 21. docs/forge/impl/h2-ai-validate-parse.md. Commit; no push.",
    label: "impl:H2", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave H3: Replace Object[] pipeline/status aggregates with JPA interface projections in DealRepository/LeadRepository + service mapping + tests. Salvage Bolt #437. docs/forge/impl/h3-jpa-projections.md. Commit; no push.",
    label: "impl:H3", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave H4: deal-kanban.component.ts single-pass O(N) column bucketing (salvage #436); Vitest. docs/forge/impl/h4-kanban-on.md. Commit; no push.",
    label: "impl:H4", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave H5: SearchInputComponent on deal-list and organization-list only (#403 remainder); Vitest. docs/forge/impl/h5-search-input.md. Commit; no push.",
    label: "impl:H5", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave H6: thin TTL cache on ProductConfigurationRuleClient (S2B-9 thin, no Kafka); config TTL; unit tests second fetch within TTL skips HTTP. docs/forge/impl/h6-rule-cache-ttl.md. Commit; no push. Prefer JDK 21.",
    label: "impl:H6", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
let day1 = parallel(jobs);

phase("Day2");
let h7 = agent(
    "Implement Wave H7: quote approval tests for legacy-local-read-enabled=false routing-only path; default remains true; docs note. docs/forge/impl/h7-dual-read-prep.md. Commit; no push.",
    #{ label: "impl:H7", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);
let h8 = agent(
    "Implement Wave H8: matrix + forge closeout docs/forge/impl/h8-wave-h-docs.md; mark Wave H complete notes. Commit; no push.",
    #{ label: "impl:H8", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Verify");
let vjobs = [];
for r in day1 {
    if r != () && r.success && r.output != () {
        vjobs.push(#{
            prompt: "Adversarially verify " + r.output.pr_id + " with rg/read_file. ok=true only with concrete evidence.",
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            model: "grok-4.5",
            output_schema: verify_schema,
        });
    }
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave H forge", day1: day1, h7: h7, h8: h8, verdicts: verdicts });
