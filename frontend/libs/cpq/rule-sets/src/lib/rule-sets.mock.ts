import { RuleDto, RuleSetDto } from '@frontend/shared-models';

/** Vitest fixtures only — not used at runtime. */
export const MOCK_RULE_SETS: RuleSetDto[] = [
  {
    id: 1,
    uuid: 'rs-uuid-1',
    name: 'Standard Pricing',
    description: 'Default DROOLS pricing rules',
    status: 'DRAFT',
    ruleSetVersion: 1,
    engineType: 'DROOLS',
    effectiveDate: '2026-01-01',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-15T00:00:00Z',
  },
  {
    id: 2,
    uuid: 'rs-uuid-2',
    name: 'Enterprise Discount',
    description: 'Active enterprise discounts',
    status: 'ACTIVE',
    ruleSetVersion: 2,
    engineType: 'DROOLS',
    effectiveDate: '2026-02-01',
    createdAt: '2026-02-01T00:00:00Z',
    updatedAt: '2026-02-10T00:00:00Z',
  },
  {
    id: 3,
    uuid: 'rs-uuid-3',
    name: 'Legacy Volume',
    description: 'Deprecated volume rules',
    status: 'DEPRECATED',
    ruleSetVersion: 1,
    engineType: 'DROOLS',
    createdAt: '2025-06-01T00:00:00Z',
    updatedAt: '2026-03-01T00:00:00Z',
  },
];

export const MOCK_RULES: RuleDto[] = [
  {
    id: 10,
    uuid: 'rule-uuid-10',
    ruleSetId: 1,
    name: 'List price floor',
    description: 'Reject below floor',
    ruleType: 'PRICING',
    priority: 100,
    status: 'DRAFT',
    content: 'rule "List price floor"\nwhen\n  $line : Line()\nthen\n  // noop\nend',
    createdAt: '2026-01-02T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
  },
  {
    id: 11,
    uuid: 'rule-uuid-11',
    ruleSetId: 1,
    name: 'Partner discount',
    ruleType: 'PRICING',
    priority: 200,
    status: 'ACTIVE',
    content: 'rule "Partner discount"\nwhen\n  $ctx : Context()\nthen\n  // noop\nend',
    createdAt: '2026-01-03T00:00:00Z',
    updatedAt: '2026-01-03T00:00:00Z',
  },
  {
    id: 20,
    uuid: 'rule-uuid-20',
    ruleSetId: 2,
    name: 'Enterprise tier',
    ruleType: 'PRICING',
    priority: 150,
    status: 'ACTIVE',
    content: 'rule "Enterprise tier"\nwhen\n  $c : Customer()\nthen\n  // noop\nend',
    createdAt: '2026-02-02T00:00:00Z',
    updatedAt: '2026-02-02T00:00:00Z',
  },
];

export function findMockRuleSet(id: number): RuleSetDto | undefined {
  return MOCK_RULE_SETS.find((r) => r.id === id);
}

export function findMockRulesForSet(ruleSetId: number): RuleDto[] {
  return MOCK_RULES.filter((r) => r.ruleSetId === ruleSetId);
}
