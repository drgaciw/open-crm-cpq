/**
 * Mirrors: com.opencrm.crm.contact.ContactStatus
 */
export type ContactStatus =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'DO_NOT_CONTACT'
  | 'ARCHIVED';

/**
 * Mirrors: com.opencrm.crm.contact.ContactDto
 * Returned by GET /api/v1/contacts, GET /api/v1/contacts/:id
 *
 * NOTE: Backend returns flat organizationId + organizationName,
 * NOT a nested OrganizationDto object.
 */
export interface ContactDto {
  id: number;
  uuid: string;
  firstName: string;
  lastName: string;
  fullName?: string;
  email?: string;
  phone?: string;
  mobile?: string;
  jobTitle?: string;
  department?: string;
  organizationId?: number;
  organizationName?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
  linkedinUrl?: string;
  notes?: string;
  primary?: boolean;
  status?: ContactStatus;
  ownerId?: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

/**
 * Mirrors: com.opencrm.crm.contact.ContactCreateRequest
 * Sent to POST /api/v1/contacts
 */
export interface ContactCreateRequest {
  firstName: string; // @NotBlank
  lastName: string; // @NotBlank
  email?: string; // @Email (optional on create)
  phone?: string;
  mobile?: string;
  jobTitle?: string;
  department?: string;
  organizationId?: number;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
  linkedinUrl?: string;
  notes?: string;
  primary?: boolean;
  ownerId?: string;
}

/**
 * Mirrors: com.opencrm.crm.contact.ContactUpdateRequest
 * Sent to PUT /api/v1/contacts/:id
 */
export interface ContactUpdateRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string;
  mobile?: string;
  jobTitle?: string;
  department?: string;
  organizationId?: number;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
  linkedinUrl?: string;
  notes?: string;
  primary?: boolean;
  status?: ContactStatus;
  ownerId?: string;
}
