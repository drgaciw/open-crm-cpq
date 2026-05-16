import { CPQ_CATALOG_BOUNDARY } from './cpq-catalog-boundary';

describe('CPQ catalog boundary', () => {
  it('exposes a stable library boundary id', () => {
    expect(CPQ_CATALOG_BOUNDARY).toBe('crm-cpq-catalog');
  });
});
