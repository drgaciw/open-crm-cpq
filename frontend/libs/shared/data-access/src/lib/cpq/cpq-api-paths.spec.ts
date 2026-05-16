import { describe, expect, it } from 'vitest';
import { CpqApiPaths } from './cpq-api-paths';

describe('CpqApiPaths', () => {
  it('uses the gateway CPQ prefix for every published route', () => {
    for (const path of Object.values(CpqApiPaths)) {
      expect(path.startsWith('/api/v1/cpq/')).toBe(true);
    }
  });

  it('maps pricing commands to the pricing aggregate owner alias', () => {
    expect(CpqApiPaths.pricing).toBe('/api/v1/cpq/pricing');
    expect(CpqApiPaths.ruleSets).toBe('/api/v1/cpq/rule-sets');
  });

  it('maps quote flow commands to quote and configuration aliases', () => {
    expect(CpqApiPaths.quotes).toBe('/api/v1/cpq/quotes');
    expect(CpqApiPaths.configurations).toBe('/api/v1/cpq/configurations');
  });
});
