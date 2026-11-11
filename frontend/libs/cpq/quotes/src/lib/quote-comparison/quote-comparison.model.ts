/** Seller preference and persisted values only; no approval, acceptance or forecast decision. */
export interface QuoteComparison {
  dealExternalId: string;
  customerExternalId: string;
  primaryQuoteId: number | null;
  sameCurrency: boolean;
  quotes: QuoteAlternative[];
}

export interface QuoteAlternative {
  id: number;
  quoteNumber: string;
  name: string;
  status: string;
  dealType: string;
  currency: string | null;
  region: string | null;
  durationInMonths: number;
  startDate: string | null;
  endDate: string | null;
  subtotal: number | null;
  totalDiscount: number | null;
  totalTax: number | null;
  totalAmount: number | null;
  arr: number | null;
  tcv: number | null;
  acv: number | null;
  rampScheduleStale: boolean;
  margin: { status: 'UNAVAILABLE'; value: null; reason: string };
  lines: QuoteComparisonLine[];
}

export interface QuoteComparisonLine {
  productExternalId: string;
  productName: string | null;
  productType: string | null;
  lineItemType: string | null;
  quantity: number;
  unitOfMeasure: string | null;
  billingFrequency: string | null;
}
