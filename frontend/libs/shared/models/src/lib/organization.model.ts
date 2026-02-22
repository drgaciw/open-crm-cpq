/**
 * Mirrors: com.opencrm.crm.organization.OrganizationType
 */
export type OrganizationType =
  | 'PROSPECT'
  | 'CUSTOMER'
  | 'PARTNER'
  | 'VENDOR'
  | 'COMPETITOR'
  | 'OTHER';

/**
 * Mirrors: com.opencrm.crm.organization.OrganizationDto
 * Returned by GET /api/v1/organizations, GET /api/v1/organizations/:id
 */
export interface OrganizationDto {
  id: number;
  uuid: string;
  name: string;
  website?: string;
  websiteDomain?: string;
  industry?: string;
  employeeCount?: number;
  annualRevenue?: number;
  phone?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
  description?: string;
  linkedinUrl?: string;
  type?: OrganizationType;
  ownerId?: string;
  territory?: string;
  contactCount?: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

/**
 * Mirrors: com.opencrm.crm.organization.OrganizationCreateRequest
 * Sent to POST /api/v1/organizations
 */
export interface OrganizationCreateRequest {
  name: string;                  // @NotBlank
  website?: string;
  websiteDomain?: string;
  industry?: string;
  employeeCount?: number;
  annualRevenue?: number;
  phone?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
  description?: string;
  linkedinUrl?: string;
  type?: OrganizationType;
  ownerId?: string;
  territory?: string;
}

/**
 * Mirrors: com.opencrm.crm.organization.OrganizationUpdateRequest
 * Sent to PUT /api/v1/organizations/:id
 */
export interface OrganizationUpdateRequest {
  name?: string;
  website?: string;
  websiteDomain?: string;
  industry?: string;
  employeeCount?: number;
  annualRevenue?: number;
  phone?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
  description?: string;
  linkedinUrl?: string;
  type?: OrganizationType;
  ownerId?: string;
  territory?: string;
}
