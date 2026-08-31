let meta = #{
    name: "forge-wave-o",
    description: "Wave O: rule-sets admin, catalog options+attrs, subscription DTO",
    when_to_use: "After PM authorizes forge for docs/forge/26-wave-o-architecture.md",
    phases: [
        #{ title: "Day1", detail: "O1-O3 parallel" },
        #{ title: "Closeout", detail: "O4 docs" },
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
    prompt: "Implement Wave O1 per docs/forge/26-wave-o-architecture.md. New FE lib frontend/libs/cpq/rule-sets (@frontend/cpq-rule-sets, project cpq-rule-sets). List/detail/create via existing RuleSetService on /api/v1/rule-sets. Activate/Deprecate ConfirmDialog. Nested rules via RuleService.listByRuleSet on /api/v1/rules (no bare list). DRL textarea create/edit + validateSyntax. O1 owns app.routes + sidebar Rule sets + tsconfig path + test:cpq. Never /api/v1/configuration/rules or /api/v1/pricing/rule-sets. No NgRx. No Java. Vitest NX_DAEMON=false nx test cpq-rule-sets. docs/forge/impl/o1-fe-rule-sets.md. GitNexus impact --file. Commit locally; no push. Dual-read stays true.",
    label: "impl:O1", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave O2 per docs/forge/26-wave-o-architecture.md. FE catalog-detail only (no shell). Option groups GET /api/v1/product-options/groups/product/{id} + create group + add option + delete group ConfirmDialog. Attribute values GET/POST /api/v1/product-attributes/products/{id}/values with definition picker GET /api/v1/product-attributes. Add shared-models DTOs. No quotes/rule-sets/bundles. GitNexus impact UNKNOWN controllers + rg. Vitest NX_DAEMON=false nx test cpq-catalog (and shared-models if types added). docs/forge/impl/o2-fe-catalog-options-attrs.md. Commit locally; no push. Dual-read stays true.",
    label: "impl:O2", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave O3 per docs/forge/26-wave-o-architecture.md. BE contract-asset-management only. SubscriptionDto + MapStruct (copy ProductMapper/QuoteMapper). Controller never returns Subscription entity. Paginated GET /api/v1/subscriptions. Keep getById/customer/status/activate/suspend/cancel/renew returning DTO. Activate still ErpAdapter.syncToErp. No FE. GitNexus impact SubscriptionController UNKNOWN — rg gateway. JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2. Tests no entity JSON. docs/forge/impl/o3-be-subscription-dto.md. Commit locally; no push. Dual-read stays true.",
    label: "impl:O3", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
let day1 = parallel(jobs);

phase("Closeout");
let o4 = agent(
    "Implement Wave O4: docs/forge/impl/o4-wave-o-docs.md closeout; matrix notes for rule-sets FE, product-options/attributes FE, subscription DTO+list. Dual-read default stays true. Do not edit docs/forge/00-orchestrator-status.md. Commit; no push.",
    #{ label: "impl:O4", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Verify");
let vjobs = [];
for r in day1 {
    if r != () && r.success && r.output != () {
        vjobs.push(#{
            prompt: "Adversarially verify " + r.output.pr_id + " against docs/forge/26-wave-o-architecture.md with rg/read_file. ok=true only with evidence. Fail if dual-read default flipped, O1 used /api/v1/configuration/rules, O2 edited app.routes/sidebar, O3 added subscriptions FE, product validate deleted, or 00-orchestrator-status edited.",
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            model: "grok-4.5",
            output_schema: verify_schema,
        });
    }
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave O forge", day1: day1, o4: o4, verdicts: verdicts });
