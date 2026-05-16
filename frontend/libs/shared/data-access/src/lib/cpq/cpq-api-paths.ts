/**
 * Gateway-published CPQ API paths. Requests use /api/v1/cpq/...; the gateway
 * rewrites to aggregate-owning services (see CpqGatewayRoutes on the backend).
 */
export const CpqApiPaths = {
  pricing: '/api/v1/cpq/pricing',
  pricingCustomers: '/api/v1/cpq/pricing-customers',
  ruleSets: '/api/v1/cpq/rule-sets',
  rules: '/api/v1/cpq/rules',
  priceBooks: '/api/v1/cpq/price-books',
  products: '/api/v1/cpq/products',
  productBundles: '/api/v1/cpq/product-bundles',
  productOptions: '/api/v1/cpq/product-options',
  productAttributes: '/api/v1/cpq/product-attributes',
  productConfiguration: '/api/v1/cpq/configuration',
  quotes: '/api/v1/cpq/quotes',
  configurations: '/api/v1/cpq/configurations',
  approvals: '/api/v1/cpq/approvals',
  documents: '/api/v1/cpq/documents',
  subscriptions: '/api/v1/cpq/subscriptions',
} as const;

export type CpqApiPath = (typeof CpqApiPaths)[keyof typeof CpqApiPaths];
