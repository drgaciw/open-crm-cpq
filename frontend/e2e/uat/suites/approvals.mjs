/**
 * Approvals inbox suite: shell from the spec, then list / Refresh / row workflows.
 */
import { inbox } from '../specs/approvals.mjs';
import { runShell } from '../runners/shell.mjs';
import { runInboxList, runInboxRows } from '../workflows/approvals-inbox.mjs';

export default {
  id: 'approvals',
  backend: 'approvalsBff',

  async run(ctx) {
    await runShell(ctx, inbox);
    await runInboxList(ctx);
    await runInboxRows(ctx);
  },
};
