import { CPQ_QUOTE_BOUNDARY } from './cpq-quote-boundary';

describe('CPQ quote boundary', () => {
  it('exposes a stable library boundary id', () => {
    expect(CPQ_QUOTE_BOUNDARY).toBe('crm-cpq-quote');
  });
});
