import { ApprovalRequestDto } from '@frontend/shared-models';

/** Vitest fixtures only — not used by runtime HTTP inbox. */
export const MOCK_PENDING_APPROVALS: ApprovalRequestDto[] = [
  {
    id: 10,
    uuid: 'apr-10',
    quoteId: 7,
    tier: 'DEAL_DESK',
    status: 'PENDING',
    requestedBy: 'agent-011',
    comments: 'Margin below 35%',
    requestedAt: '2026-08-29T12:00:00Z',
    createdAt: '2026-08-29T12:00:00Z',
  },
  {
    id: 11,
    uuid: 'apr-11',
    quoteId: 8,
    tier: 'CFO',
    status: 'PENDING',
    requestedBy: 'rep-42',
    requestedAt: '2026-08-30T09:15:00Z',
    createdAt: '2026-08-30T09:15:00Z',
  },
];

export function findMockPendingApproval(
  id: number,
): ApprovalRequestDto | undefined {
  return MOCK_PENDING_APPROVALS.find((a) => a.id === id);
}
