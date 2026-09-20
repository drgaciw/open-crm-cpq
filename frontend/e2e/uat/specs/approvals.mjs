/**
 * Approvals inbox — pending quote approvals served by the quote-service BFF.
 * Source: frontend/libs/cpq/approvals/src/lib/approvals-inbox/approvals-inbox.component.ts
 */
import { validateSpec } from '../contract.mjs';

export const inbox = validateSpec({
  id: 'approvals',
  path: '/approvals',
  heading: 'Approvals',
  subtitle: 'Pending quote approvals via quote-service BFF',
  backend: 'approvalsBff',
  sidebar: 'Approvals',
  actions: [
    { name: 'Refresh', in: 'header' },
    {
      name: 'Approve', in: 'row',
      confirm: {
        kind: 'mat', title: 'Approve request',
        message: /Approve approval #\d+ for quote \d+ \(.+\)\?/,
        confirmText: 'Approve', cancelText: 'Cancel',
      },
    },
    {
      // Two dialogs in sequence: window.prompt first, then the Material confirm (H-CPQ-Q-005).
      name: 'Reject', in: 'row',
      confirm: { kind: 'prompt', message: /^Reject approval #\d+ for quote \d+\. Optional reason:$/ },
      opensDialog: 'Reject request',
      hypothesis: 'H-CPQ-Q-005',
    },
    { name: 'Open quote', in: 'row', navigatesTo: '/quotes/:id' },
  ],
  tables: [{ columns: ['ID', 'Quote', 'Tier', 'Requested by', 'Requested', 'Comments', 'Actions'], rowRole: 'tr' }],
  emptyState: {
    title: 'No pending approvals',
    message: 'Approvals waiting for a decision will appear here.',
  },
  notes: 'The list stays empty while approval-routing (:8085) is down — row flows are BLOCKED then.',
});

export default { inbox };
