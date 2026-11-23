import {
  MOCK_BULK_CONTEXTS,
  MOCK_PRICING_CONTEXT,
  MOCK_PRICING_EXECUTE_OK,
  mockBulkPartialResponse,
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

  it('mockBulkPartialResponse is one success and one failure over two contexts', () => {
    expect(MOCK_BULK_CONTEXTS).toHaveLength(2);
    expect(MOCK_BULK_CONTEXTS[0]?.quoteExternalId).toBe('Q-SIM-001');
    expect(MOCK_BULK_CONTEXTS[1]?.quoteExternalId).toBe('Q-SIM-002');

    const response = mockBulkPartialResponse();

    expect(response.totalRequests).toBe(2);
    expect(response.successfulRequests).toBe(1);
    expect(response.failedRequests).toBe(1);
    expect(response.results).toHaveLength(2);
    expect(response.results[0]?.success).toBe(true);
    expect(response.results[0]?.lineResults?.[0]?.productExternalId).toBe(
      'PROD-001',
    );
    expect(response.results[1]?.success).toBe(false);
    expect(response.results[1]?.errorMessage).toBe('No matching price book');
  });
});
