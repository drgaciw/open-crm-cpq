/**
 * Product configuration-rule DTOs colocated in the catalog lib (Wave P3).
 * Mirrors product-service ConfigRuleType / ConfigurationRuleDto / ConfigRuleCreateRequest.
 * Not re-exported from shared-models/src/index.ts.
 */

export const CONFIG_RULE_TYPES = [
  'REQUIRES',
  'EXCLUDES',
  'RECOMMENDS',
  'DEFAULT',
  'VALIDATES',
] as const;

export type ConfigRuleType = (typeof CONFIG_RULE_TYPES)[number];

export interface ConfigurationRuleDto {
  id: number;
  uuid: string;
  name: string;
  description?: string;
  ruleType: ConfigRuleType;
  sourceProductId: number;
  targetProductId?: number;
  conditionExpression?: string;
  errorMessage?: string;
  priority: number;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
  version?: number;
}

export interface ConfigRuleCreateRequest {
  name: string;
  description?: string;
  ruleType: ConfigRuleType;
  sourceProductId: number;
  targetProductId?: number;
  conditionExpression?: string;
  errorMessage?: string;
  priority?: number;
}
