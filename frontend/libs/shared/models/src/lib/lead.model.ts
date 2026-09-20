import { JevSuggestion } from './jev-suggestion.model';

/**
 * Mirrors: com.opencrm.crm.lead.LeadStatus
 */
export type LeadStatus =
  | 'NEW'
  | 'CONTACTED'
  | 'QUALIFIED'
  | 'UNQUALIFIED'
  | 'CONVERTED'
  | 'LOST';

/**
 * Mirrors: com.opencrm.crm.lead.LeadPriority
 */
export type LeadPriority =
  | 'LOW'
  | 'MEDIUM'
  | 'HIGH'
  | 'URGENT';

/**
 * Mirrors: com.opencrm.crm.lead.LeadDto
 * Returned by GET /api/v1/leads, GET /api/v1/leads/:id
 *
 * NOTE: Backend returns flat contactId/contactName and organizationId/organizationName,
 * NOT nested ContactDto/OrganizationDto objects.
 */
export interface LeadDto {
  id: number;
  uuid: string;
  title: string;
  contactId?: number;
  contactName?: string;
  organizationId?: number;
  organizationName?: string;
  status: LeadStatus;
  source?: string;
  sourceDetail?: string;
  estimatedValue?: number;
  currency?: string;
  score?: number;
  /** Advisory only (ADR-015); present only when Jev scored the lead. Never a decision. */
  scoreSuggestion?: JevSuggestion;
  description?: string;
  ownerId?: string;
  assignedAt?: string;
  firstContactedAt?: string;
  lastContactedAt?: string;
  convertedAt?: string;
  convertedDealId?: number;
  lostReason?: string;
  lostAt?: string;
  campaign?: string;
  priority?: LeadPriority;
  nextAction?: string;
  nextActionDate?: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

/**
 * Mirrors: com.opencrm.crm.lead.LeadCreateRequest
 * Sent to POST /api/v1/leads
 */
export interface LeadCreateRequest {
  title: string;                 // @NotBlank
  contactId?: number;
  organizationId?: number;
  source?: string;
  sourceDetail?: string;
  estimatedValue?: number;       // @PositiveOrZero
  currency?: string;             // defaults to "USD"
  description?: string;
  ownerId?: string;
  campaign?: string;
  priority?: LeadPriority;       // defaults to MEDIUM
}

/**
 * Mirrors: com.opencrm.crm.lead.LeadUpdateRequest
 * Sent to PUT /api/v1/leads/:id
 */
export interface LeadUpdateRequest {
  title?: string;
  contactId?: number;
  organizationId?: number;
  source?: string;
  sourceDetail?: string;
  estimatedValue?: number;
  currency?: string;
  description?: string;
  ownerId?: string;
  campaign?: string;
  priority?: LeadPriority;
}

/**
 * Mirrors: com.opencrm.crm.lead.LeadLoseRequest
 * Sent to POST /api/v1/leads/:id/lose
 */
export interface LeadLoseRequest {
  reason: string;                // @NotBlank
}
