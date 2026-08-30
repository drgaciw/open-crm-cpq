import {
  MOCK_PRICING_CONTEXT,
  MOCK_PRICING_EXECUTE_OK,
  mockExecuteOk,
} from './pricing-simulator.mock';

describe('pricing-simulator mock', () => {
  it('exports execute OK fixture with success true', () => {
    expect(MOCK_PRICING_EXECUTE_OK.success).toBe(true);
    expect(MOCK_PRICING_EXECUTE_OK.lineResults?.length).toBeGreaterThan(0);
  });

  it('mockExecuteOk preserves success and mirrors context line', () => {
    const result = mockExecuteOk({
      ...MOCK_PRICING_CONTEXT,
      lineItems: [
        {
          productExternalId: 'SKU-42',
          quantity: 3,
          listPriceOverride: 200,
        },
      ],
    });

    expect(result.success).toBe(true);
    expect(result.lineResults?.[0]?.productExternalId).toBe('SKU-42');
    expect(result.lineResults?.[0]?.finalPrice).toBe(600);
    expect(result.waterfall?.netPrice).toBe(600);
  });
});
