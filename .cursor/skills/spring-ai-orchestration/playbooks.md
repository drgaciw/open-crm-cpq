# CPQ Orchestration Playbooks

Source: `agent-prd.md` §4, `agents/HANDOFFS.md`.

## 1. Quote creation (happy path)

```
001 Guided Product Selection
  → 002 Option & Feature Configuration
    → 015 Compatibility Rules (validate)
    → 016 Validation Rules (validate)
  → 003 Bundle Assembly [if bundle]
    → 018 Dynamic Bundle Pricing
  → 004 List Price Application
    → 005 Pricing Method → 006 Markup & Cost Integration
    → 007 Volume → 008 Segment → 009 Promo [if active]
    → 010 Deal-Specific Discount [if requested, margin-gated]
    → 011 Minimum Margin Enforcement ★ guardrail
    → 012 Profitability Alert [advisory]
    → 032 Currency/FX [if multi-currency]
    → 033 Tax Calculation
  → 017 Recommendation/Upsell [advisory sidebar]
  → 026 Win-Probability [advisory sidebar]
→ 024 Multi-Level Approval Routing [on submit]
  → 025 Exception Handling [if needed]
  → 037 Deal Desk Orchestration
→ 031 Quote Document Generation [on approval]
→ 030 Contract Lifecycle [on acceptance]
```

## 3. Renewal flow

```
028 Renewal Opportunity Detection [scheduled or quote:accepted]
  → 029 Upgrade/Downgrade Path
  → re-enter pricing: 019 / 022 / 023 / 020
  → 004 List Price Application (re-entry point)
  → … guardrail 011 → approval 024 → fulfill 030
```

## 4. Amendment flow

```
POST /api/v1/quotes/{id}/clone
  → 020 Term & Co-Terming (proration)
  → 002 Option & Feature Configuration (modify)
  → re-enter at 004 List Price Application
  → 011 Margin Enforcement
  → 024 Approval Routing
```

## 5. Event-driven activations

| Channel | Subscribers | Action |
|---------|-------------|--------|
| `quote:accepted` | CRM `QuoteAcceptedEventListener`, ai-service `QuoteAcceptedListener` | Deal WON + deal-desk workflow |
| `quote:accepted` | 028, 030 (PRD target) | Renewal tracking, contract create |
| `quote:status-changed` | 036, 037 (planned) | Audit + workflow tracking |
| `product:updated` | 035 (planned) | Price book gap analysis |
| `pricing:executed` | 012, 036 (planned) | Profitability alert, audit |

## 6. Routing: which lane to use

| Symptom / task | Lane |
|----------------|------|
| Implement or debug a single cpq-agent-* | Direct invoke via `AgentRouter` |
| Multi-step approval → document → contract | `deal-desk` workflow → 037 |
| Quote accepted, deal not WON, Redis/event DTO | **`cpq-lifecycle-tracer`** |
| Tool/gateway wiring, feature flag, ChatClient | **This specialist** (ai-service internals) |
| Graph impact before editing agent code | `gitnexus-impact-analyst` |
