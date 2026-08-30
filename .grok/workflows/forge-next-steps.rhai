let meta = #{
    name: "forge-next-steps",
    description: "Plan-first forge wave across CRM cutover, CPQ contracts, FE CPQ UI, and gateway/CI; pause for approval before implement",
    when_to_use: "Orchestrate Open CRM/CPQ next-step waves with discovery then gated implementation",
    phases: [
        #{ title: "Discover", detail: "Parallel track discovery + blast-radius notes" },
        #{ title: "Synthesize", detail: "Merge plans into ordered wave backlog" },
        #{ title: "Approve", detail: "Human gate before write work" },
        #{ title: "Implement", detail: "Parallel implementers in worktrees" },
        #{ title: "Verify", detail: "Adversarial verify each deliverable" },
    ],
};

let plan_schema = #{
    "type": "object",
    "required": ["track", "summary", "findings", "next_prs", "risks", "blocked_on"],
    "properties": #{
        "track": #{ "type": "string" },
        "summary": #{ "type": "string" },
        "findings": #{
            "type": "array",
            "maxItems": 12,
            "items": #{
                "type": "object",
                "required": ["area", "gap", "evidence"],
                "properties": #{
                    "area": #{ "type": "string" },
                    "gap": #{ "type": "string" },
                    "evidence": #{ "type": "string" },
                },
            },
        },
        "next_prs": #{
            "type": "array",
            "maxItems": 6,
            "items": #{
                "type": "object",
                "required": ["title", "scope", "acceptance", "risk"],
                "properties": #{
                    "title": #{ "type": "string" },
                    "scope": #{ "type": "string" },
                    "acceptance": #{ "type": "string" },
                    "risk": #{ "type": "string" },
                },
            },
        },
        "risks": #{ "type": "array", "maxItems": 8, "items": #{ "type": "string" } },
        "blocked_on": #{ "type": "array", "maxItems": 6, "items": #{ "type": "string" } },
    },
};

let wave_schema = #{
    "type": "object",
    "required": ["ordered_prs", "parallel_batches", "do_not_touch", "summary"],
    "properties": #{
        "summary": #{ "type": "string" },
        "ordered_prs": #{
            "type": "array",
            "maxItems": 12,
            "items": #{
                "type": "object",
                "required": ["id", "title", "track", "depends_on", "acceptance"],
                "properties": #{
                    "id": #{ "type": "string" },
                    "title": #{ "type": "string" },
                    "track": #{ "type": "string" },
                    "depends_on": #{ "type": "array", "items": #{ "type": "string" } },
                    "acceptance": #{ "type": "string" },
                },
            },
        },
        "parallel_batches": #{
            "type": "array",
            "maxItems": 6,
            "items": #{
                "type": "object",
                "required": ["batch", "pr_ids"],
                "properties": #{
                    "batch": #{ "type": "integer" },
                    "pr_ids": #{ "type": "array", "items": #{ "type": "string" } },
                },
            },
        },
        "do_not_touch": #{ "type": "array", "maxItems": 12, "items": #{ "type": "string" } },
    },
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

fn track_prompt(track_id, focus) {
    let p = "";
    p += "You are a plan-only forge scout for Open CRM/CPQ track: " + track_id + ". ";
    p += "MODE: discovery + impact analysis ONLY. Do not edit files, do not commit, do not push. ";
    p += "Use read_file, grep, list_dir, and git tools to inspect the real repo at /home/username01/Projects/open-crm-cpq. ";
    p += "Follow GitNexus rules when available: gitnexus_query / gitnexus_context / gitnexus_impact before proposing symbol edits. ";
    p += "Focus: " + focus + " ";
    p += "Read AGENT.md, docs/CRM_CUTOVER_CHECKLIST.md, docs/CRM_API_PARITY.md, docs/backlog/CPQ_API_CONTRACT_MATRIX.md as relevant. ";
    p += "Return structured plan with concrete evidence paths (file:line or command output). Empty findings only if you inspected and found no gaps.";
    p
}

let mode = if args == () { "plan" } else if args.mode == () { "plan" } else { args.mode };
let max_impl = if args == () { 4 } else if args.max_impl == () { 4 } else { args.max_impl };

phase("Discover");
let jobs = [];
jobs.push(#{
    prompt: track_prompt("crm-api-parity", "CRM API vs FE parity gaps, missing activities API decision, Lead/Deal/Contact/Organization completeness, OpenAPI accuracy."),
    label: "scout:crm-api",
    capability_mode: "read-only",
    output_schema: plan_schema,
});
jobs.push(#{
    prompt: track_prompt("crm-frontend", "Angular CRM libs UX/data-access gaps vs API parity matrix; broken callers; missing actions (qualify/convert/pipeline); test coverage."),
    label: "scout:crm-fe",
    capability_mode: "read-only",
    output_schema: plan_schema,
});
jobs.push(#{
    prompt: track_prompt("quote-deal-e2e", "Quote accepted Redis path → deal WON; QuoteAcceptedCrmMessage contract; idempotency; staging smoke script gaps."),
    label: "scout:quote-e2e",
    capability_mode: "read-only",
    output_schema: plan_schema,
});
jobs.push(#{
    prompt: track_prompt("cpq-contracts", "CPQ_API_CONTRACT_MATRIX vs product/pricing/quote/pricebook/configuration-engine/approval controllers; gateway stubs; missing tests for agent-contract routes."),
    label: "scout:cpq",
    capability_mode: "read-only",
    output_schema: plan_schema,
});
jobs.push(#{
    prompt: track_prompt("fe-cpq-ui", "Frontend CPQ UI scaffold readiness: Nx apps/libs, shared UI, quote/catalog/pricing screens missing vs CRM; PRICING_IA_BACKLOG.md."),
    label: "scout:fe-cpq",
    capability_mode: "read-only",
    output_schema: plan_schema,
});
jobs.push(#{
    prompt: track_prompt("gateway-ci", "Gateway CRM/CPQ routes, CRM_SERVICE_URI, ci-contract.yaml parity, Jenkins AI assist boundaries, flaky/missing integration tests."),
    label: "scout:gateway-ci",
    capability_mode: "read-only",
    output_schema: plan_schema,
});

let scout_results = parallel(jobs);
let plans = [];
let i = 0;
for r in scout_results {
    if r != () && r.success && r.output != () {
        plans.push(r.output);
    } else {
        log("scout slot " + i.to_string() + " failed or empty; treating as unverified");
    }
    i += 1;
}
if plans.len() == 0 {
    complete(#{ summary: "All scouts failed", plans: [] });
}

phase("Synthesize");
let synth_prompt = "";
synth_prompt += "Synthesize these track plans into one ordered wave backlog for Open CRM/CPQ. ";
synth_prompt += "Prefer CRM cutover hardening first, then CPQ contract gaps, then FE CPQ scaffold, interleaved with gateway/CI safety. ";
synth_prompt += "Mark dependencies so parallel batches are conflict-free (different services/libs). ";
synth_prompt += "Input plans JSON follows:\n";
synth_prompt += json_encode(plans);
let synth = agent(synth_prompt, #{
    label: "synthesize-wave",
    capability_mode: "read-only",
    output_schema: wave_schema,
});
if synth.success != true || synth.output == () {
    pause("verification", "Wave synthesis failed; resume after inspecting scout outputs.");
}

let wave_out = synth.output;
let summary_text = if wave_out.summary == () { "Wave synthesis produced no summary" } else { wave_out.summary };
let ordered = if wave_out.ordered_prs == () || type_of(wave_out.ordered_prs) != "array" { [] } else { wave_out.ordered_prs };
let batches = if wave_out.parallel_batches == () || type_of(wave_out.parallel_batches) != "array" { [] } else { wave_out.parallel_batches };

let report = "# Forge wave plan\n\n" + summary_text + "\n\n";
report += "## Ordered PRs\n";
for pr in ordered {
    let pid = if pr.id == () { "?" } else { pr.id };
    let ptr = if pr.track == () { "?" } else { pr.track };
    let ptitle = if pr.title == () { "?" } else { pr.title };
    let pacc = if pr.acceptance == () { "" } else { pr.acceptance };
    report += "- **" + pid + "** [" + ptr + "] " + ptitle + " — " + pacc + "\n";
}
report += "\n## Parallel batches\n";
for b in batches {
    let bnum = if b.batch == () { 0 } else { b.batch };
    let ids = if b.pr_ids == () { [] } else { b.pr_ids };
    report += "- Batch " + bnum.to_string() + ": ";
    report += json_encode(ids) + "\n";
}
let report_path = write_scratch_file("forge-wave-plan.md", report);
log("Wrote " + report_path);

phase("Approve");
if mode != "plan" {
    await_user("user", "Review forge wave plan (scratch forge-wave-plan.md). Resume to IMPLEMENT the first parallel batch, or stop.");
}

if mode == "plan" {
    complete(#{
        summary: "Plan-only complete; implementation gated.",
        report: report_path,
        wave: wave_out,
        plans: plans,
    });
}

phase("Implement");
let batch0 = [];
if batches.len() > 0 {
    let first_batch = batches[0];
    if first_batch.pr_ids != () && type_of(first_batch.pr_ids) == "array" {
        batch0 = first_batch.pr_ids;
    }
}
let impl_jobs = [];
let count = 0;
for pr in ordered {
    if count >= max_impl {
        break;
    }
    let in_batch = false;
    if batch0.len() == 0 {
        in_batch = true;
    } else {
        for id in batch0 {
            if id == pr.id { in_batch = true; }
        }
    }
    if in_batch {
        let ip = "";
        ip += "Implement PR " + pr.id + ": " + pr.title + " for track " + pr.track + ". ";
        ip += "Acceptance: " + pr.acceptance + ". ";
        ip += "Repo: /home/username01/Projects/open-crm-cpq. ";
        ip += "Before editing any symbol run gitnexus_impact when available and warn on HIGH/CRITICAL. ";
        ip += "Follow AGENT.md conventions. Prefer small scoped changes. Run relevant unit tests. ";
        ip += "Do not push or open PRs. Commit only if clearly requested in args; default: leave working tree changes. ";
        ip += "Return files_touched, tests_run, status done|partial|blocked.";
        impl_jobs.push(#{
            prompt: ip,
            label: "impl:" + pr.id,
            capability_mode: "read-write",
            isolation_worktree: true,
            output_schema: impl_schema,
        });
        count += 1;
    }
}
if impl_jobs.len() == 0 {
    complete(#{ summary: "No implement jobs admitted", wave: synth.output, report: report_path });
}
let impl_results = parallel(impl_jobs);

phase("Verify");
let vjobs = [];
for r in impl_results {
    if r != () && r.success && r.output != () {
        let vp = "";
        vp += "Adversarially verify implementation claim for " + r.output.pr_id + " status=" + r.output.status + ". ";
        vp += "Inspect files_touched and tests_run. Set ok=true only with concrete evidence from the codebase. ";
        vp += "Claim notes: " + r.output.notes;
        vjobs.push(#{
            prompt: vp,
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            output_schema: verify_schema,
        });
    }
}
let verdicts = [];
if vjobs.len() > 0 {
    verdicts = parallel(vjobs);
}

complete(#{
    summary: "Forge wave finished implement+verify for first batch",
    report: report_path,
    wave: synth.output,
    implementations: impl_results,
    verdicts: verdicts,
});
