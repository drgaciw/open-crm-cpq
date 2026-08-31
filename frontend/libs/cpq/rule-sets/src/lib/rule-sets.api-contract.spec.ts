import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Guard: portal + RuleSetService/RuleService must use /api/v1/rule-sets and
 * /api/v1/rules only — never /api/v1/configuration/rules or /api/v1/pricing/rule-sets.
 */
describe('rule-sets API contract', () => {
  const ruleSetServiceSource = readFileSync(
    join(
      __dirname,
      '../../../pricing-data-access/src/lib/rule-set.service.ts',
    ),
    'utf8',
  );
  const ruleServiceSource = readFileSync(
    join(__dirname, '../../../pricing-data-access/src/lib/rule.service.ts'),
    'utf8',
  );

  const libRoot = join(__dirname, '..');

  it('RuleSetService basePath is /api/v1/rule-sets', () => {
    expect(ruleSetServiceSource).toContain("basePath = '/api/v1/rule-sets'");
    expect(ruleSetServiceSource).not.toContain(
      "basePath = '/api/v1/pricing/rule-sets'",
    );
    expect(ruleSetServiceSource).not.toContain(
      "basePath = '/api/v1/configuration/rules'",
    );
  });

  it('RuleService basePath is /api/v1/rules and uses listByRuleSet (no bare list)', () => {
    expect(ruleServiceSource).toContain("basePath = '/api/v1/rules'");
    expect(ruleServiceSource).toContain('listByRuleSet');
    expect(ruleServiceSource).toContain('/by-rule-set/');
    expect(ruleServiceSource).not.toMatch(
      /list\s*\(\s*page\s*=\s*0[\s\S]*get<SpringPageResponse<RuleDto>>\(\s*this\.basePath\s*,/,
    );
    expect(ruleServiceSource).not.toContain('/api/v1/configuration/rules');
    expect(ruleServiceSource).not.toContain('/api/v1/pricing/rule-sets');
  });

  it('feature lib sources never reference forbidden rule APIs', () => {
    const files = [
      'lib/rule-set-list/rule-set-list.component.ts',
      'lib/rule-set-detail/rule-set-detail.component.ts',
      'lib/rule-set-create-dialog/rule-set-create-dialog.component.ts',
      'lib/rule-dialog/rule-dialog.component.ts',
      'lib/rule-sets.routes.ts',
      'index.ts',
    ];
    for (const rel of files) {
      const src = readFileSync(join(libRoot, rel), 'utf8');
      expect(src).not.toContain('/api/v1/configuration/rules');
      expect(src).not.toContain('/api/v1/pricing/rule-sets');
      expect(src).not.toContain('/api/v1/subscriptions');
    }
  });

  it('feature lib wires RuleSetService and RuleService from pricing-data-access', () => {
    const listSrc = readFileSync(
      join(libRoot, 'lib/rule-set-list/rule-set-list.component.ts'),
      'utf8',
    );
    const detailSrc = readFileSync(
      join(libRoot, 'lib/rule-set-detail/rule-set-detail.component.ts'),
      'utf8',
    );
    const ruleDialogSrc = readFileSync(
      join(libRoot, 'lib/rule-dialog/rule-dialog.component.ts'),
      'utf8',
    );
    expect(listSrc).toContain('RuleSetService');
    expect(detailSrc).toContain('RuleSetService');
    expect(detailSrc).toContain('RuleService');
    expect(detailSrc).toContain('listByRuleSet');
    expect(ruleDialogSrc).toContain('validateSyntax');
    expect(listSrc).toContain('@frontend/cpq-pricing-data-access');
    expect(detailSrc).toContain('@frontend/cpq-pricing-data-access');
  });
});
