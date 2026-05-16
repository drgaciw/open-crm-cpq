/** Admin backlog stub for OpenL table upload and validation status (gated on pricing-service contracts). */
export const CPQ_RULES_ADMIN_BOUNDARY = 'crm-cpq-rules-admin' as const;

export type CpqRulesAdminBoundary = typeof CPQ_RULES_ADMIN_BOUNDARY;

export type OpenLTableValidationStatus = 'READY' | 'REJECTED';

export interface OpenLTableValidationSummary {
  tableName: string;
  status: OpenLTableValidationStatus;
  issues: string[];
}

/** Placeholder until gateway exposes POST /api/v1/pricing/openl/tables/validate. */
export const OPENL_TABLE_VALIDATE_ROUTE = '/api/v1/pricing/openl/tables/validate';
