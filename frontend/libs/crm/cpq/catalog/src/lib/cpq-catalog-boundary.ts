/** Feature boundary for product catalog flows (gateway /api/v1/cpq/products*). */
export const CPQ_CATALOG_BOUNDARY = 'crm-cpq-catalog' as const;

export type CpqCatalogBoundary = typeof CPQ_CATALOG_BOUNDARY;
