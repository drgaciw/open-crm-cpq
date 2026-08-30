let meta = #{
    name: "forge-wave-d",
    description: "Wave D security: gateway CORS/CSRF allow-list + remove security.open bypass; docs closeout",
    when_to_use: "After PM authorizes forge for docs/forge/13-wave-d-architecture.md",
    phases: [
        #{ title: "Implement", detail: "D1 CORS and D2 securityOpen in parallel" },
        #{ title: "Docs", detail: "D3 closeout" },
        #{ title: "Verify", detail: "Adversarial verify + rg clean checks" },
    ],
};

let impl_schema = #{
    "type": "object",
    "required": ["pr_id", "status", "files_touched", "tests_run", "notes"],
    "properties": #{
        "pr_id": #{ "type": "string" },
        "status": #{ "type": "string" },
        "files_touched": #{ "type": "array", "maxItems": 40, "items": #{ "type": "string" } },
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

phase("Implement");
let jobs = [];
jobs.push(#{
    prompt: "Implement D1 per docs/forge/13-wave-d-architecture.md: remove *.azurewebsites.net from gateway CORS and CSRF allow-lists; use FRONTEND_URL / explicit origins; add unit tests rejecting wildcards; update docs/CRM_GATEWAY_ENV.md. Write docs/forge/impl/d1-gateway-cors-csrf.md. Commit locally; no push.",
    label: "impl:D1",
    capability_mode: "read-write",
    isolation_worktree: true,
    output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement D2 per docs/forge/13-wave-d-architecture.md: remove security.open permitAll bypass from all service SecurityConfigs; fix ITs (AutoConfigureMockMvc addFilters=false / WithMockUser) without reintroducing the flag. rg must show no security.open under main sources. Write docs/forge/impl/d2-remove-security-open.md. Commit locally; no push.",
    label: "impl:D2",
    capability_mode: "read-write",
    isolation_worktree: true,
    output_schema: impl_schema,
});
let results = parallel(jobs);

phase("Docs");
let d3 = agent(
    "Implement D3: docs closeout for Wave D referencing GitHub #451 and #458; write docs/forge/impl/d3-security-closeout.md; commit locally; no push. Assume D1/D2 landed in sibling worktrees — document verification commands.",
    #{ label: "impl:D3", capability_mode: "read-write", isolation_worktree: true, output_schema: impl_schema }
);

phase("Verify");
let vjobs = [];
for r in results {
    if r != () && r.success && r.output != () {
        vjobs.push(#{
            prompt: "Verify " + r.output.pr_id + " with rg and file reads. ok=true only if wildcards/security.open removed as claimed. Evidence required.",
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            output_schema: verify_schema,
        });
    }
}
if d3 != () && d3.success && d3.output != () {
    vjobs.push(#{
        prompt: "Verify D3 docs exist and list correct issue IDs. ok=true with path evidence.",
        label: "verify:D3",
        capability_mode: "read-only",
        output_schema: verify_schema,
    });
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave D implement+verify", results: results, d3: d3, verdicts: verdicts });
