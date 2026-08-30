let meta = #{
    name: "forge-wave-k",
    description: "Wave K: last JsonNode landmine, FE quote lifecycle+lines, catalog update, approval inbox",
    when_to_use: "After PM authorizes forge for docs/forge/20-wave-k-architecture.md",
    phases: [
        #{ title: "Day1", detail: "K1-K4 parallel" },
        #{ title: "Closeout", detail: "K5 docs" },
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
    prompt: "Implement Wave K1 per docs/forge/20-wave-k-architecture.md in quote-service only. DELETE unused ProductServiceClient.getProductById and getProductBySku (body JsonNode). Keep validateConfiguration untouched. Drop productRestClient field/ctor/bean if unused. Update ProductServiceClientTest ctor. rg body(JsonNode) backend must be empty after. GitNexus impact ProductServiceClient --file quote ProductServiceClient.java; UNKNOWN + rg confirm unused. JDK 21 JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2. docs/forge/impl/k1-product-client-jsonnode.md. Commit locally; no push.",
    label: "impl:K1", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave K2 per docs/forge/20-wave-k-architecture.md. FE cpq-quotes quotes-detail only: status-gated Approve/Reject (IN_REVIEW|PENDING_APPROVAL), Send (APPROVED), Accept (SENT) via existing QuoteService HTTP. List quote.lineItems with edit/remove via updateLineItem/removeLineItem. Keep Calculate/Submit. No document-generation. No app.routes/sidebar edits. Vitest. docs/forge/impl/k2-fe-quote-lifecycle-lines.md. Commit; no push.",
    label: "impl:K2", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave K3 per docs/forge/20-wave-k-architecture.md. FE cpq-catalog catalog-detail Edit/Save via ProductService.update matching ProductUpdateRequest (name, description, productType, category, status, listPrice, currency). Stay on detail. Error banner. Do not edit quotes lib. Vitest. docs/forge/impl/k3-fe-catalog-update.md. Commit; no push.",
    label: "impl:K3", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "Implement Wave K4 per docs/forge/20-wave-k-architecture.md. New FE lib frontend/libs/cpq/approvals. Pending inbox against GET /api/v1/quotes/approvals/pending plus approve/reject on quote BFF. Wire app.routes and sidebar. Do NOT call approval-routing directly. Do NOT flip approvals.legacy-local-read-enabled. Prefer approval DTOs in K4 only. Vitest. docs/forge/impl/k4-fe-approval-inbox.md. Commit; no push.",
    label: "impl:K4", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
let day1 = parallel(jobs);

phase("Closeout");
let k5 = agent(
    "Implement Wave K5: docs/forge/impl/k5-wave-k-docs.md closeout; matrix touch-ups. Dual-read default stays true. Do not flip YAML. Commit; no push.",
    #{ label: "impl:K5", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema }
);

phase("Verify");
let vjobs = [];
for r in day1 {
    if r != () && r.success && r.output != () {
        vjobs.push(#{
            prompt: "Adversarially verify " + r.output.pr_id + " with rg/read_file against docs/forge/20-wave-k-architecture.md. ok=true only with evidence. Fail if body(JsonNode.class) remains, dual-read default flipped, product validate deleted, or approval-routing called from FE.",
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            model: "grok-4.5",
            output_schema: verify_schema,
        });
    }
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave K forge", day1: day1, k5: k5, verdicts: verdicts });
