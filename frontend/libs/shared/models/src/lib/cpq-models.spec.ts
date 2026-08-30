import {
  CATALOG_PRODUCT_TYPES,
  CATALOG_PRODUCT_TYPE_LABELS,
  isCatalogProductType,
} from './product.model';
import {
  PRICING_PRODUCT_TYPES,
  PRICING_PRODUCT_TYPE_LABELS,
  isPricingProductType,
} from './pricing.model';
import {
  QUOTE_STATUS_LABELS,
  QUOTE_STATUSES,
  isQuoteStatus,
} from './quote.model';

describe('CPQ shared models', () => {
  describe('CatalogProductType vs PricingProductType', () => {
    it('keeps catalog and pricing ProductType sets distinct', () => {
      const catalogOnly = CATALOG_PRODUCT_TYPES.filter(
        (t) => !(PRICING_PRODUCT_TYPES as readonly string[]).includes(t)
      );
      const pricingOnly = PRICING_PRODUCT_TYPES.filter(
        (t) => !(CATALOG_PRODUCT_TYPES as readonly string[]).includes(t)
      );

      expect(catalogOnly.length).toBeGreaterThan(0);
      expect(pricingOnly.length).toBeGreaterThan(0);
      expect(catalogOnly).toContain('HARDWARE');
      expect(catalogOnly).toContain('SAAS_FLAT_RATE');
      expect(pricingOnly).toContain('SAAS_MAU');
      expect(pricingOnly).toContain('HYBRID_MIGRATION_RAMP');
    });

    it('isCatalogProductType accepts catalog values and rejects pricing-only values', () => {
      expect(isCatalogProductType('HARDWARE')).toBe(true);
      expect(isCatalogProductType('SAAS_FLAT_RATE')).toBe(true);
      expect(isCatalogProductType('SAAS_MAU')).toBe(false);
      expect(isCatalogProductType('HYBRID_MIGRATION_RAMP')).toBe(false);
      expect(isCatalogProductType(null)).toBe(false);
      expect(isCatalogProductType(42)).toBe(false);
    });

    it('isPricingProductType accepts pricing values and rejects catalog-only values', () => {
      expect(isPricingProductType('SAAS_MAU')).toBe(true);
      expect(isPricingProductType('HYBRID_MIGRATION_RAMP')).toBe(true);
      expect(isPricingProductType('HARDWARE')).toBe(false);
      expect(isPricingProductType('CUSTOM')).toBe(false);
      expect(isPricingProductType(undefined)).toBe(false);
    });

    it('provides a label for every catalog and pricing ProductType', () => {
      for (const type of CATALOG_PRODUCT_TYPES) {
        expect(CATALOG_PRODUCT_TYPE_LABELS[type]).toBeTruthy();
      }
      for (const type of PRICING_PRODUCT_TYPES) {
        expect(PRICING_PRODUCT_TYPE_LABELS[type]).toBeTruthy();
      }
    });
  });

  describe('QuoteStatus', () => {
    it('isQuoteStatus guards known statuses', () => {
      expect(isQuoteStatus('DRAFT')).toBe(true);
      expect(isQuoteStatus('ACCEPTED')).toBe(true);
      expect(isQuoteStatus('WON')).toBe(false);
      expect(isQuoteStatus('')).toBe(false);
    });

    it('provides a label for every quote status', () => {
      for (const status of QUOTE_STATUSES) {
        expect(QUOTE_STATUS_LABELS[status]).toBeTruthy();
      }
    });
  });
});
