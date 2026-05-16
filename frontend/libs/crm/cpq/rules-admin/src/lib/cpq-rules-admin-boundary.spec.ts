import {
  CPQ_RULES_ADMIN_BOUNDARY,
  OPENL_TABLE_VALIDATE_ROUTE,
} from './cpq-rules-admin-boundary';

describe('cpq-rules-admin boundary', () => {
  it('declares the rules-admin feature boundary', () => {
    expect(CPQ_RULES_ADMIN_BOUNDARY).toBe('crm-cpq-rules-admin');
  });

  it('documents the OpenL validation endpoint contract', () => {
    expect(OPENL_TABLE_VALIDATE_ROUTE).toBe(
      '/api/v1/pricing/openl/tables/validate'
    );
  });
});
