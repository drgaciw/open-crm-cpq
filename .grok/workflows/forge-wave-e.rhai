let meta = #{
    name: "forge-wave-e",
    description: "Wave E: CSRF edge-only ADR, align pricing/pricebook csrf.disable, Accept config ADR, Sentinel hygiene",
    when_to_use: "After PM authorizes forge for docs/forge/14-wave-e-architecture.md",
    phases: [
        #{ title: "Implement", detail: "E1 ADR, E2 CSRF align, E3 config ADR Accept in parallel" },
        #{ title: "Hygiene", detail: "E4 GitHub Sentinel/Bolt closeout" },
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
    prompt: "Implement Wave E1 per docs/forge/14-wave-e-architecture.md on an isolated worktree branch docs/forge-e1-csrf-edge-adr: create docs/ADR-004-csrf-edge-boundary.md (gateway-only browser Cookie CSRF; domain services csrf.disable+STATELESS intentional; reject Jules #402/#404). Cross-link docs/CRM_GATEWAY_ENV.md. Write docs/forge/impl/e1-csrf-adr.md status=done. Commit locally; no push. Do not change Java SecurityConfigs (that is E2).",
    label: "impl:E1",
    capability_mode: "read-write",
    isolation_worktree: true,
    model: "grok-4.5",
    output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave E2 per docs/forge/14-wave-e-architecture.md on branch fix/forge-e2-align-csrf-disable: change pricing-service and pricebook-service SecurityConfig to csrf.disable() + STATELESS (match crm-service). Run GitNexus impact on those SecurityConfigs first; UNKNOWN risk requires rg confirmation that CookieCsrfTokenRepository remains gateway-only after change. Add short Javadoc. Write docs/forge/impl/e2-align-csrf-disable.md. Prefer JDK 21 for mvn. Commit locally; no push. Do not touch gateway CSRF.",
    label: "impl:E2",
    capability_mode: "read-write",
    isolation_worktree: true,
    model: "grok-4.5",
    output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave E3 per docs/forge/14-wave-e-architecture.md on branch docs/forge-e3-accept-config-adr: set docs/backlog/ADR-configuration-api-reconciliation.md Status to Accepted; freeze open-question defaults from the Wave E architecture (stateless evaluate endpoint preferred; DTO in libs/common; sync REST rule fetch; retain product-service validate until parity). Do NOT implement S2B-11 shim or change Java. Write docs/forge/impl/e3-adr-accept.md status=done. Commit locally; no push.",
    label: "impl:E3",
    capability_mode: "read-write",
    isolation_worktree: true,
    model: "grok-4.5",
    output_schema: impl_schema,
});
let results = parallel(jobs);

phase("Hygiene");
let e4 = agent(
    "Implement Wave E4 per docs/forge/14-wave-e-architecture.md: write docs/forge/impl/e4-sentinel-hygiene.md with comment templates and the PR number lists to close (Bolt N+1 superseded by C6; #402/#404 superseded by E1/E2; #414/#416 legacy/FP; #415 obsolete). If gh CLI is available, comment+close those PRs; otherwise document exact gh commands for the orchestrator. Commit docs locally; no push of product code. Do not reopen held tracks.",
    #{ label: "impl:E4", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Verify");
let vjobs = [];
for r in results {
    if r != () && r.success && r.output != () {
        let pid = r.output.pr_id;
        vjobs.push(#{
            prompt: "Adversarially verify Wave E claim for " + pid + ". Read the tree with grep/read_file. ok=true only with concrete evidence: E1 ADR-004 exists and rejects enable-all CSRF; E2 CookieCsrfTokenRepository only on gateway SecurityConfig among services; E3 config ADR Status Accepted with no S2B-11 Java. Evidence required.",
            label: "verify:" + pid,
            capability_mode: "read-only",
            model: "grok-4.5",
            output_schema: verify_schema,
        });
    }
}
if e4 != () && e4.success && e4.output != () {
    vjobs.push(#{
        prompt: "Verify E4 hygiene doc exists and lists #402 #404 and Bolt N+1 family. ok=true with path evidence.",
        label: "verify:E4",
        capability_mode: "read-only",
        model: "grok-4.5",
        output_schema: verify_schema,
    });
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave E implement+verify", results: results, e4: e4, verdicts: verdicts });
