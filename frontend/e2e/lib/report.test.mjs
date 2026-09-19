/**
 * Unit tests for the evidence report. Run with: npm run e2e:test
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { createReport } from './report.mjs';

const quiet = (options = {}) => createReport('unit', { write: false, log: false, ...options });

test('stays backward compatible: pass/fail/info and a failure count from finish', () => {
  const r = quiet();
  r.pass('a');
  r.fail('b', 'boom');
  r.info('c', 'note', 'low');
  assert.deepEqual(r.entries.map((e) => e.status), ['PASS', 'FAIL', 'INFO']);
  assert.equal(r.finish(), 1);
});

test('a failure linked to a hypothesis is expected and does not fail the run', () => {
  const r = quiet();
  r.fail('deals.persist.stage', 'stage dropped', { hypothesis: 'H-CRM-003' });
  assert.equal(r.entries[0].status, 'EXPECTED_FAIL');
  assert.equal(r.entries[0].hypothesis, 'H-CRM-003');
  assert.equal(r.finish(), 0);
});

test('strict mode removes the tolerance for expected failures', () => {
  const r = quiet({ strict: true });
  r.fail('x', 'still broken', { hypothesis: 'H-1' });
  assert.equal(r.finish(), 1);
});

test('blocked, inconclusive and gap never count as failures', () => {
  const r = quiet({ strict: true });
  r.blocked('quotes.submit', 'configuration-engine down');
  r.inconclusive('jev.lead', 'confidence 0.62 < 0.9');
  r.gap('kanban.dnd', 'no drag and drop', { hypothesis: 'H-CRM-005' });
  assert.deepEqual(r.entries.map((e) => e.status), ['BLOCKED', 'INCONCLUSIVE', 'GAP']);
  assert.equal(r.finish(), 0);
});

test('the document summarises counts per outcome and carries residue and extras', () => {
  const r = quiet();
  r.pass('a');
  r.pass('b');
  r.fail('c', 'x');
  r.blocked('d', 'down');
  r.residue({ type: 'lead', id: 7, reason: 'CONVERTED leads cannot be deleted' });
  const doc = r.document({ runId: 'U1' });
  assert.equal(doc.name, 'unit');
  assert.equal(doc.runId, 'U1');
  assert.deepEqual(doc.summary, { PASS: 2, FAIL: 1, BLOCKED: 1 });
  assert.equal(doc.passed, 2);
  assert.equal(doc.failed, 1);
  assert.equal(doc.residue.length, 1);
});

test('evidence paths are recorded on the entry', () => {
  const r = quiet();
  r.fail('x', 'bad', { evidence: { screenshot: 'x.png', url: '/deals' } });
  assert.deepEqual(r.entries[0].evidence, { screenshot: 'x.png', url: '/deals' });
});

test('hypothesisVerdicts maps each linked hypothesis to what the run observed', () => {
  const r = quiet();
  r.fail('c1', 'reproduced', { hypothesis: 'H-1' });
  r.pass('c2', '', { hypothesis: 'H-2' });
  r.blocked('c3', 'env', { hypothesis: 'H-3' });
  assert.deepEqual(r.document().hypothesisVerdicts, {
    'H-1': 'CONFIRMED',
    'H-2': 'REFUTED',
    'H-3': 'BLOCKED',
  });
});
