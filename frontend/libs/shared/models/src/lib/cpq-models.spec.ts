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
  DOCUMENT_TYPES,
  DOCUMENT_TYPE_LABELS,
  QUOTE_STATUS_LABELS,
  QUOTE_STATUSES,
  isDocumentType,
  isQuoteStatus,
} from './quote.model';
import {
  APPROVAL_REQUEST_STATUSES,
  APPROVAL_REQUEST_STATUS_LABELS,
  APPROVAL_TIERS,
  APPROVAL_TIER_LABELS,
  isApprovalRequestStatus,
  isApprovalTier,
} from './approval.model';
import {
  ADJUSTMENT_TYPES,
  ADJUSTMENT_TYPE_LABELS,
  SELECTION_TYPES,
  SELECTION_TYPE_LABELS,
  isAdjustmentType,
  isSelectionType,
} from './product-option.model';
import {
  ATTRIBUTE_DATA_TYPES,
  ATTRIBUTE_DATA_TYPE_LABELS,
  isAttributeDataType,
} from './product-attribute.model';

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

  describe('DocumentType', () => {
    it('isDocumentType guards known quote document types', () => {
      expect(isDocumentType('PROPOSAL')).toBe(true);
      expect(isDocumentType('ORDER_FORM')).toBe(true);
      expect(isDocumentType('SOW')).toBe(true);
      expect(isDocumentType('QUOTE_PDF')).toBe(false);
      expect(isDocumentType(null)).toBe(false);
    });

    it('provides a label for every document type', () => {
      for (const type of DOCUMENT_TYPES) {
        expect(DOCUMENT_TYPE_LABELS[type]).toBeTruthy();
      }
    });
  });

  describe('ApprovalRequestDto unions', () => {
    it('isApprovalTier / isApprovalRequestStatus guard known values', () => {
      expect(isApprovalTier('DEAL_DESK')).toBe(true);
      expect(isApprovalTier('MANAGER')).toBe(false);
      expect(isApprovalRequestStatus('PENDING')).toBe(true);
      expect(isApprovalRequestStatus('NONE')).toBe(false);
    });

    it('provides a label for every tier and approval status', () => {
      for (const tier of APPROVAL_TIERS) {
        expect(APPROVAL_TIER_LABELS[tier]).toBeTruthy();
      }
      for (const status of APPROVAL_REQUEST_STATUSES) {
        expect(APPROVAL_REQUEST_STATUS_LABELS[status]).toBeTruthy();
      }
    });
  });

  describe('Product option / attribute unions (Wave O2)', () => {
    it('isSelectionType / isAdjustmentType guard known values', () => {
      expect(isSelectionType('SINGLE')).toBe(true);
      expect(isSelectionType('MULTIPLE')).toBe(true);
      expect(isSelectionType('REQUIRED_SINGLE')).toBe(true);
      expect(isSelectionType('MULTI')).toBe(false);
      expect(isAdjustmentType('ABSOLUTE')).toBe(true);
      expect(isAdjustmentType('PERCENTAGE')).toBe(true);
      expect(isAdjustmentType('FIXED')).toBe(false);
    });

    it('provides a label for every selection and adjustment type', () => {
      for (const type of SELECTION_TYPES) {
        expect(SELECTION_TYPE_LABELS[type]).toBeTruthy();
      }
      for (const type of ADJUSTMENT_TYPES) {
        expect(ADJUSTMENT_TYPE_LABELS[type]).toBeTruthy();
      }
    });

    it('isAttributeDataType guards known values and labels', () => {
      expect(isAttributeDataType('STRING')).toBe(true);
      expect(isAttributeDataType('ENUM')).toBe(true);
      expect(isAttributeDataType('INTEGER')).toBe(false);
      for (const type of ATTRIBUTE_DATA_TYPES) {
        expect(ATTRIBUTE_DATA_TYPE_LABELS[type]).toBeTruthy();
      }
    });
  });
});
