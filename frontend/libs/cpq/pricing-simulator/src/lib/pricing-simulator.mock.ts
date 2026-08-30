import {
  PricingContext,
  PricingResult,
} from '@frontend/shared-models';

/**
 * Vitest fixture shaped like POST /api/v1/pricing/execute (and /simulate).
 * Mirrors golden fixture 01-list-price-baseline; happy path uses HTTP.
 */
export const MOCK_PRICING_EXECUTE_OK: PricingResult = {
  success: true,
  approvalState: 'APPROVED',
  approvalTier: 'NONE',
  executionTimeMs: 12,
  explanationCodes: ['LIST_PRICE_BASELINE'],
  lineResults: [
    {
      productExternalId: 'PROD-001',
      lineItemType: 'SUBSCRIPTION',
      quantity: 5,
      listPrice: 100,
      unitPrice: 100,
      discountRate: 0,
      discountAmount: 0,
      surchargeAmount: 0,
      finalPrice: 500,
      explanationCodes: ['LIST_PRICE'],
    },
  ],
  waterfall: {
    listPrice: 100,
    basePrice: 100,
    netPrice: 500,
    approvalState: 'APPROVED',
    steps: [
      {
        stepType: 'LIST_PRICE',
        description: 'List price from price book',
        amount: 100,
        runningTotal: 100,
      },
      {
        stepType: 'NET_PRICE',
        description: 'Net after quantity',
        amount: 500,
        runningTotal: 500,
      },
    ],
    explanationCodes: ['LIST_PRICE_BASELINE'],
  },
  financialMetrics: {
    arr: 6000,
    tcv: 6000,
    acv: 500,
    clv: 12000,
    churnScore: 20,
    discountRate: 0,
    totalDiscount: 0,
    totalSurcharge: 0,
    priceMultiplier: 1,
    estimatedMarginPct: 40,
  },
};

/** Default form seed aligned with the mock OK fixture. */
export const MOCK_PRICING_CONTEXT: PricingContext = {
  customerExternalId: 'CUST-GOLD-001',
  quoteExternalId: 'Q-SIM-001',
  region: 'NA',
  currency: 'USD',
  durationInMonths: 12,
  subscriptionType: 'NEW',
  lineItems: [
    {
      productExternalId: 'PROD-001',
      lineItemType: 'SUBSCRIPTION',
      quantity: 5,
    },
  ],
};

/**
 * Builds a Vitest OK result from the submitted context.
 * Adjusts line product/qty/totals; keeps success=true for fixture assertions.
 */
export function mockExecuteOk(context: PricingContext): PricingResult {
  const line = context.lineItems[0];
  const quantity = line?.quantity ?? 1;
  const listPrice = line?.listPriceOverride ?? 100;
  const unitPrice = listPrice;
  const finalPrice = unitPrice * quantity;
  const duration = context.durationInMonths ?? 12;
  const productExternalId = line?.productExternalId ?? 'PROD-001';

  return {
    ...MOCK_PRICING_EXECUTE_OK,
    success: true,
    executionTimeMs: 8,
    lineResults: [
      {
        productExternalId,
        lineItemType: line?.lineItemType ?? 'SUBSCRIPTION',
        quantity,
        listPrice,
        unitPrice,
        discountRate: 0,
        discountAmount: 0,
        surchargeAmount: 0,
        finalPrice,
        explanationCodes: ['LIST_PRICE'],
      },
    ],
    waterfall: {
      listPrice,
      basePrice: listPrice,
      netPrice: finalPrice,
      approvalState: 'APPROVED',
      steps: [
        {
          stepType: 'LIST_PRICE',
          description: 'List price (mock)',
          amount: listPrice,
          runningTotal: listPrice,
        },
        {
          stepType: 'NET_PRICE',
          description: 'Net after quantity (mock)',
          amount: finalPrice,
          runningTotal: finalPrice,
        },
      ],
      explanationCodes: ['LIST_PRICE_BASELINE'],
    },
    financialMetrics: {
      // Stub metrics: treat finalPrice as period ACV; ARR annualizes by months.
      arr: (finalPrice / Math.max(duration, 1)) * 12,
      tcv: finalPrice * (duration / 12),
      acv: finalPrice,
      clv: finalPrice * 2,
      churnScore: 20,
      discountRate: 0,
      totalDiscount: 0,
      totalSurcharge: 0,
      priceMultiplier: 1,
      estimatedMarginPct: 40,
    },
  };
}
