let meta = #{
    name: "forge-wave-f",
    description: "Wave F hybrid S2B-11: DTO, CE evaluate, gateway shim, contract tests, docs",
    when_to_use: "After PM authorizes forge for docs/forge/15-wave-f-architecture.md",
    phases: [
        #{ title: "Foundation", detail: "F1 DTO already on main or implement" },
        #{ title: "Parallel", detail: "F2 CE evaluate and F4 contracts" },
        #{ title: "Gateway", detail: "F3 HIGH-risk validate shim" },
        #{ title: "Closeout", detail: "F5 matrix docs" },
        #{ title: "Verify", detail: "Adversarial verify" },
    ],
};

let impl_schema = #{
    "type": "object",
    "required": ["pr_id", "status", "files_touched", "tests_run", "notes"],
    "properties": #{
        "pr_id": #{ "type": "string" },
        "status": #{ "type": "string" },
        "files_touched": #{ "type": "array", "maxItems": 50, "items": #{ "type": "string" } },
        "tests_run": #{ "type": "array", "maxItems": 20, "items": #{ "type": "string" } },
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

phase("Foundation");
let f1 = agent(
    "Confirm F1 ConfigurationValidationResult exists under backend/libs/common. If missing, implement per docs/forge/15-wave-f-architecture.md and write docs/forge/impl/f1-validation-dto.md. Commit if you change files; no push.",
    #{ label: "impl:F1", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Parallel");
let jobs = [];
jobs.push(#{
    prompt: "Implement Wave F2 per docs/forge/15-wave-f-architecture.md: configuration-engine POST /internal/v1/configurations/evaluate accepting List<Long> product ids; sync REST fetch rules from product-service GET /api/v1/configuration/rules/product/{id}; thin REQUIRES/EXCLUDES StatelessConfigurationEvaluator returning ConfigurationValidationResult from libs/common. Do NOT rewrite session ConstraintSolver placeholder. Unit tests for empty/requires/excludes. Write docs/forge/impl/f2-ce-evaluate.md. Commit locally; no push. Prefer JDK 21.",
    label: "impl:F2",
    capability_mode: "read-write",
    isolation_worktree: true,
    model: "grok-4.5",
    output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave F4 per docs/forge/15-wave-f-architecture.md: dual-run / contract tests comparing product validate hard failures vs CE evaluate for same List<Long> fixtures; gateway predicate tests can be stubs if F3 not merged. Prefer Docker-free unit tests. Write docs/forge/impl/f4-validate-contracts.md. Commit locally; no push.",
    label: "impl:F4",
    capability_mode: "read-write",
    isolation_worktree: true,
    model: "grok-4.5",
    output_schema: impl_schema,
});
let parallel_results = parallel(jobs);

phase("Gateway");
let f3 = agent(
    "Implement Wave F3 HIGH-risk per docs/forge/15-wave-f-architecture.md: gateway route POST /api/v1/configuration/validate (and cpq alias) BEFORE broad configuration/** to configuration-engine internal evaluate; adapt to ApiResponse of ConfigurationValidationResult; CRUD rules remain product; NO domain REQUIRES/EXCLUDES in gateway Java. Retain product validate endpoint. Update CpqGatewayRoutesTest. Write docs/forge/impl/f3-gateway-validate-shim.md. Commit locally; no push. Prefer JDK 21.",
    #{ label: "impl:F3", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Closeout");
let f5 = agent(
    "Implement Wave F5: update docs/backlog/CPQ_API_CONTRACT_MATRIX.md validate row for structured shape + owners; forge closeout docs/forge/impl/f5-s2b11-docs.md; note product validate retained and S2B-8 follow-on. Commit locally; no push.",
    #{ label: "impl:F5", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Verify");
let vjobs = [];
if f1 != () && f1.success { vjobs.push(#{ prompt: "Verify F1 DTO files exist with valid+violations fields. ok=true with path evidence.", label: "verify:F1", capability_mode: "read-only", model: "grok-4.5", output_schema: verify_schema }); }
for r in parallel_results {
    if r != () && r.success && r.output != () {
        vjobs.push(#{
            prompt: "Adversarially verify " + r.output.pr_id + ". For F2 require internal evaluate endpoint and ConstraintSolver placeholder untouched. For F4 require dual-run or contract test files. Evidence required.",
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            model: "grok-4.5",
            output_schema: verify_schema,
        });
    }
}
if f3 != () && f3.success {
    vjobs.push(#{
        prompt: "Verify F3: gateway validate route more specific than configuration/**; no REQUIRES/EXCLUDES domain logic in gateway Java; product validate controller still present. ok=true only with rg evidence.",
        label: "verify:F3",
        capability_mode: "read-only",
        model: "grok-4.5",
        output_schema: verify_schema,
    });
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave F hybrid S2B-11", f1: f1, parallel_results: parallel_results, f3: f3, f5: f5, verdicts: verdicts });
