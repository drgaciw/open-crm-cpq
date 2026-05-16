import { CPQ_CONFIGURATION_BOUNDARY } from './cpq-configuration-boundary';

describe('CPQ configuration boundary', () => {
  it('exposes a stable library boundary id', () => {
    expect(CPQ_CONFIGURATION_BOUNDARY).toBe('crm-cpq-configuration');
  });
});
