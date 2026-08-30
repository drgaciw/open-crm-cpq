let meta = #{
    name: "forge-autonomous-loop",
    description: "Autonomous forge loop: discover remaining gaps, synthesize next batch, implement in worktrees, verify — no human gate (PM delegated)",
    when_to_use: "Continue Open CRM/CPQ forge waves after Batch 0/1 without approval pauses",
    phases: [
        #{ title: "Discover", detail: "Scan backlog vs main for unfinished forge PRs" },
        #{ title: "Synthesize", detail: "Pick next parallel batch" },
        #{ title: "Implement", detail: "Worktree implementers" },
        #{ title: "Verify", detail: "Adversarial verify" },
    ],
};

let batch_schema = #{
    "type": "object",
    "required": ["summary", "prs"],
    "properties": #{
        "summary": #{ "type": "string" },
        "prs": #{
            "type": "array",
            "maxItems": 6,
            "items": #{
                "type": "object",
                "required": ["id", "title", "scope", "acceptance"],
                "properties": #{
                    "id": #{ "type": "string" },
                    "title": #{ "type": "string" },
                    "scope": #{ "type": "string" },
                    "acceptance": #{ "type": "string" },
                },
            },
        },
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

let max_impl = if args == () { 4 } else if args.max_impl == () { 4 } else { args.max_impl };

phase("Discover");
let discover_prompt = "";
discover_prompt += "Repo /home/username01/Projects/open-crm-cpq. Batch 0 merged to main. ";
discover_prompt += "Read docs/forge/07-wave-backlog.md, docs/forge/08-batch0-results.md, docs/forge/09-workflow-wave-plan.md, docs/forge/impl/*.md. ";
discover_prompt += "Use read_file/grep/list_dir. Propose the next <=6 conflict-free PRs not yet done (skip activities API impl, skip S2B-11/S2C-12 HIGH items). ";
discover_prompt += "Prefer: proxy:8080, CRM parity docs, deal pipeline UX, CPQ models, staging smokes, gateway ERP 501 tests, pricing fixtures if missing.";
let disc = agent(discover_prompt, #{
    label: "discover-next",
    capability_mode: "read-only",
    output_schema: batch_schema,
});
if disc.success != true || disc.output == () {
    complete(#{ summary: "discover failed", prs: [] });
}

phase("Synthesize");
let report = "# Autonomous forge next batch\n\n" + disc.output.summary + "\n\n";
let prs = if disc.output.prs == () || type_of(disc.output.prs) != "array" { [] } else { disc.output.prs };
for pr in prs {
    report += "- **" + pr.id + "** " + pr.title + " — " + pr.acceptance + "\n";
}
let report_path = write_scratch_file("autonomous-next-batch.md", report);
log("Wrote " + report_path);

phase("Implement");
let impl_jobs = [];
let count = 0;
for pr in prs {
    if count >= max_impl { break; }
    let ip = "";
    ip += "Implement " + pr.id + ": " + pr.title + ". Scope: " + pr.scope + ". Acceptance: " + pr.acceptance + ". ";
    ip += "Repo open-crm-cpq. Follow AGENT.md. isolation worktree. Do not push. Commit locally when green. ";
    ip += "Return status done|partial|blocked with files_touched and tests_run.";
    impl_jobs.push(#{
        prompt: ip,
        label: "impl:" + pr.id,
        capability_mode: "read-write",
        isolation_worktree: true,
        output_schema: impl_schema,
    });
    count += 1;
}
if impl_jobs.len() == 0 {
    complete(#{ summary: "No PRs to implement", report: report_path, batch: disc.output });
}
let impl_results = parallel(impl_jobs);

phase("Verify");
let vjobs = [];
for r in impl_results {
    if r != () && r.success && r.output != () {
        let vp = "";
        vp += "Verify claim for " + r.output.pr_id + " status=" + r.output.status + ". ";
        vp += "Inspect codebase evidence. ok=true only with concrete proof. Notes: " + r.output.notes;
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

complete(#{
    summary: "Autonomous forge loop batch complete",
    report: report_path,
    batch: disc.output,
    implementations: impl_results,
    verdicts: verdicts,
});
