import { CPQ_CATALOG_LIB } from './catalog';

describe('cpq-catalog scaffold', () => {
  it('exports library marker', () => {
    expect(CPQ_CATALOG_LIB).toBe('cpq-catalog');
  });
});
