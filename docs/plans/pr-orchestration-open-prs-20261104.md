# PR orchestration — open PR disposition (2026-11-04)

Agent run disposition for eight open PRs on `drgaciw/open-crm-cpq`. Ephemeral run links are Cursor cloud agent URLs.

## Merge queue (actionable)

| PR | Disposition | Owner action |
|----|-------------|--------------|
| [#773](https://github.com/drgaciw/open-crm-cpq/pull/773) | **Ready** — Backend Unit (PR gate) passed | Merge when approved |
| [#780](https://github.com/drgaciw/open-crm-cpq/pull/780) | **Ready (docs)** — Cloud Agent section in `AGENTS.md` | Merge when approved |
| [#781](https://github.com/drgaciw/open-crm-cpq/pull/781) | **Blocked** — GitHub Actions billing (job never started) | Fix billing; re-run PR check. Optional: sync `AGENT.md` Activities hold with ADR lift in architecture docs. |
| [#782](https://github.com/drgaciw/open-crm-cpq/pull/782) | **Blocked** — same billing failure on docs-only ADR | Fix billing; re-run |

## Close / supersede (UAT stack)

| PR | Disposition |
|----|-------------|
| [#774](https://github.com/drgaciw/open-crm-cpq/pull/774)–[#777](https://github.com/drgaciw/open-crm-cpq/pull/777) | **Superseded by merged [#778](https://github.com/drgaciw/open-crm-cpq/pull/778)** on `drgamtd/drg-651-follow-up-to-drg-624-handle-null-start-date` |

**Canonical landing path:** one PR **integration base → `main`** (includes #778 rollup `1e4ab0d9`), not sequential merge of #774–#777.

## Branch hygiene

Wave-2 rebase onto `main` for #774–#777 was rolled back; remote heads restored to pre-rebase SHAs (`53af4418`, `951138c8`, `c6690899`, `4d98d75f`). Do not re-run main-rebase on those PRs without retargeting strategy.
