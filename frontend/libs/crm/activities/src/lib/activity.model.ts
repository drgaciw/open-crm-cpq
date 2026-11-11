export type ActivityType = 'NOTE' | 'TASK' | 'EMAIL' | 'MEETING';

/** Manually recorded timeline metadata; email and meeting references are never fetched by this client. */
export interface ActivityDto {
  id: number;
  uuid: string;
  type: ActivityType;
  title: string;
  note?: string;
  organizationId?: number;
  contactId?: number;
  dealId?: number;
  reference?: string;
  occurredAt: string;
  dueAt?: string;
  completedAt?: string;
  authorId: string;
  source: 'MANUAL';
  createdAt: string;
}

/** Author, source and completion identity are assigned by the server. At least one subject is required. */
export interface ActivityCreateRequest {
  type: ActivityType;
  title: string;
  note?: string;
  organizationId?: number;
  contactId?: number;
  dealId?: number;
  reference?: string;
  occurredAt: string;
  dueAt?: string;
}

/** The server requires at least one subject filter for a record's timeline. */
export interface ActivityTimelineQuery {
  organizationId?: number;
  contactId?: number;
  dealId?: number;
  page?: number;
  size?: number;
}
