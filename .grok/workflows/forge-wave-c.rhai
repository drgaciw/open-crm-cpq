let meta = #{
    name: "forge-wave-c",
    description: "Wave C safe CPQ/CRM polish: quotes stub, data-access, shared-ui, pricing sim, FE tests, pricebook N+1",
    when_to_use: "After Wave A/B on main; safe polish without HIGH ADR items",
    phases: [
        #{ title: "Plan", detail: "Confirm Wave C scope vs GitHub backlog" },
        #{ title: "Implement", detail: "Six parallel worktree implementers" },
        #{ title: "Verify", detail: "Adversarial verify each deliverable" },
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

phase("Plan");
let plan = agent(
    "Confirm Wave C from docs/forge/12-wave-c-plan.md is still correct vs main. List the six IDs C1-C6. Do not invent HIGH CORS/S2B-11 work.",
    #{ label: "wave-c-plan-check", capability_mode: "read-only" }
);
log("plan check done");

phase("Implement");
let jobs = [];
let ids = ["C1", "C2", "C3", "C4", "C5", "C6"];
let titles = [
    "quotes list stub",
    "CPQ data-access HTTP clients",
    "shared-ui on CRM leads/contacts lists",
    "pricing simulator stub",
    "FE test hardening Wave B",
    "PriceBook N+1 fix",
];
let scopes = [
    "frontend/libs/cpq/quotes",
    "frontend/libs/cpq data-access",
    "frontend/libs/crm/leads and contacts",
    "frontend/libs/cpq/pricing-simulator",
    "frontend nx tests deals catalog shared-models",
    "pricebook-service PriceBookInternalResolutionService",
];
let i = 0;
while i < ids.len() {
    let ip = "";
    ip += "Implement Wave C " + ids[i] + ": " + titles[i] + " in scope " + scopes[i] + ". ";
    ip += "Repo open-crm-cpq. Read docs/forge/12-wave-c-plan.md. Worktree isolation. ";
    ip += "No activities API, S2B-11, S2C-12, or CORS wildcard rewrites. ";
    ip += "Commit locally; write docs/forge/impl/" + ids[i] + ".md status done|partial|blocked. Return schema fields.";
    jobs.push(#{
        prompt: ip,
        label: "impl:" + ids[i],
        capability_mode: "read-write",
        isolation_worktree: true,
        output_schema: impl_schema,
    });
    i += 1;
}
let results = parallel(jobs);

phase("Verify");
let vjobs = [];
for r in results {
    if r != () && r.success && r.output != () {
        let vp = "";
        vp += "Verify " + r.output.pr_id + " status=" + r.output.status + ". ok=true only with code evidence. Notes: " + r.output.notes;
        vjobs.push(#{
            prompt: vp,
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            output_schema: verify_schema,
        });
    }
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave C implement+verify finished", results: results, verdicts: verdicts });
