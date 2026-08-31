let meta = #{
    name: "forge-wave-n-hygiene",
    description: "Wave N hygiene: quote errorCode banner and matrix completeness",
    when_to_use: "After PM picks hygiene A for docs/forge/25-wave-n-hygiene.md",
    phases: [
        #{ title: "Day1", detail: "H1-H2 parallel" },
        #{ title: "Verify", detail: "Adversarial verify" },
    ],
};

let impl_schema = #{
    "type": "object",
    "required": ["pr_id", "status", "files_touched", "tests_run", "notes"],
    "properties": #{
        "pr_id": #{ "type": "string" },
        "status": #{ "type": "string" },
        "files_touched": #{ "type": "array", "maxItems": 40, "items": #{ "type": "string" } },
        "tests_run": #{ "type": "array", "maxItems": 15, "items": #{ "type": "string" } },
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
    prompt: "H1: quotes-detail httpErrorMessage must append ApiResponse errorCode like N1 bookHttpErrorMessage. Spec mock { message: Cannot delete an accepted quote, errorCode: QUOTE_ACCEPTED_NO_DELETE }. Keep ACCEPTED delete disabled. No shell/Java. nx test cpq-quotes. Commit no push.",
    label: "impl:H1", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
jobs.push(#{
    prompt: "H2 docs-only: matrix DELETE /api/v1/quotes/{id} N3; N1 notes on price-book PUT/DELETE; dual-read J-N not J-M. No YAML flip. No 00-orchestrator-status. Commit no push.",
    label: "impl:H2", capability_mode: "read-write", isolation_worktree: true, model: "grok-4.5", output_schema: impl_schema,
});
let day1 = parallel(jobs);

phase("Verify");
let vjobs = [];
for r in day1 {
    if r != () && r.success && r.output != () {
        vjobs.push(#{
            prompt: "Verify " + r.output.pr_id + " vs docs/forge/25-wave-n-hygiene.md. Fail if dual-read YAML flipped or spec still fakes errorCode inside message only.",
            label: "verify:" + r.output.pr_id,
            capability_mode: "read-only",
            model: "grok-4.5",
            output_schema: verify_schema,
        });
    }
}
let verdicts = [];
if vjobs.len() > 0 { verdicts = parallel(vjobs); }

complete(#{ summary: "Wave N hygiene", day1: day1, verdicts: verdicts });
