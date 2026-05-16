import { CPQ_PRICING_BOUNDARY } from './cpq-pricing-boundary';

describe('CPQ pricing boundary', () => {
  it('exposes a stable library boundary id', () => {
    expect(CPQ_PRICING_BOUNDARY).toBe('crm-cpq-pricing');
  });
});
