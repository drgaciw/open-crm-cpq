// Unit tests for the JEV Renovate advisory. Run: node --test scripts/ci/jev-renovate-advisory.test.mjs
import assert from 'node:assert/strict';
import test from 'node:test';
import {
  buildState,
  checksPending,
  dependencyName,
  ecosystem,
  finalVerdict,
  gateCoverage,
  guardReasons,
  parseVersions,
  renderComment,
  MARKER,
  updateType,
} from './jev-renovate-advisory.mjs';

const green = [
  { name: 'Backend Unit (PR gate)', conclusion: 'success' },
  { name: 'Frontend (PR gate)', conclusion: 'success' },
  { name: 'Frontend npm audit (PR gate)', conclusion: 'success' },
];

test('parses Renovate version pairs, with or without range prefixes', () => {
  assert.deepEqual(parseVersions('| [x](u) | `3.21.0` -> `3.22.0` |'), { from: '3.21.0', to: '3.22.0' });
  assert.deepEqual(parseVersions('| `^8.10.0` → `^9.0.0` |'), { from: '8.10.0', to: '9.0.0' });
  assert.deepEqual(parseVersions('no table'), { from: null, to: null });
});

test('group PRs report their most severe row, not the first one', () => {
  const body = [
    '| Package | Change |',
    '| [@analogjs/vite-plugin-angular](u) | [`2.9.6` → `2.9.7`](d) |',
    '| [@angular/core](u) | [`22.2.5` → `22.3.0`](d) |',
  ].join('\n');
  assert.deepEqual(parseVersions(body), { from: '22.2.5', to: '22.3.0' });
});

test('parses pinned Python specifiers and ignores release-note text outside the table', () => {
  const body = '| [twilio](u) | `==8.13.0` → `==9.13.0` |\n- Renamed `AdminGetCohort` -> `FetchAdminCohort`';
  assert.deepEqual(parseVersions(body), { from: '8.13.0', to: '9.13.0' });
});

test('pending required checks are recognised so the PR is skipped, not held', () => {
  assert.equal(checksPending({ a: 'success', b: 'queued' }), true);
  assert.equal(checksPending({ a: 'success', b: 'missing' }), true);
  assert.equal(checksPending({ a: 'success', b: 'failure' }), false);
});

test('classifies update types, treating pre-1.0 minors and pre-releases as risky', () => {
  assert.equal(updateType('3.21.0', '3.22.0'), 'minor');
  assert.equal(updateType('2.1.0', '2.1.4'), 'patch');
  assert.equal(updateType('8.10.0', '9.0.0'), 'major');
  assert.equal(updateType('0.1.278', '1.0.0'), 'major');
  assert.equal(updateType('0.3.300', '0.4.0'), 'major');
  assert.equal(updateType('2.0.0-beta.85', '2.0.0-beta.87'), 'prerelease');
  assert.equal(updateType(null, '1.0.0'), 'unknown');
});

test('extracts the dependency name from Renovate titles', () => {
  assert.equal(dependencyName('fix(deps): update dependency org.apache.commons:commons-lang3 to v3.22.0'), 'org.apache.commons:commons-lang3');
  assert.equal(dependencyName('chore(deps): update dependency typescript to v7'), 'typescript');
  assert.equal(dependencyName('fix(deps): update angular ecosystem'), 'angular ecosystem');
});

test('maps changed files to the required gates that cover them', () => {
  assert.deepEqual(gateCoverage(['backend/pom.xml', 'frontend/package.json']), {
    coveredBy: ['Backend Unit (PR gate)', 'Frontend (PR gate)'],
    uncovered: [],
  });
  assert.deepEqual(gateCoverage(['cpq_beta/yarn.lock']).uncovered, ['cpq_beta/yarn.lock']);
  assert.equal(gateCoverage(['.github/workflows/ci.yml']).uncovered.length, 1);
  assert.equal(ecosystem(['backend/pom.xml']), 'maven');
  assert.equal(ecosystem(['cpq_beta/pyproject.toml']), 'python');
});

test('a green, gated minor update has no guard reasons', () => {
  const pr = { title: 'fix(deps): update dependency org.apache.commons:commons-lang3 to v3.22.0', body: '| x | `3.21.0` -> `3.22.0` |' };
  const state = buildState(pr, [{ filename: 'backend/pom.xml' }], green);
  assert.deepEqual(guardReasons(state), []);
});

test('guards force a hold for majors, owner-decision packages, uncovered paths and red checks', () => {
  const major = buildState({ title: 'update dependency twilio to v9', body: '| x | `8.10.0` -> `9.0.0` |' }, [{ filename: 'cpq_beta/pyproject.toml' }], green);
  assert.match(guardReasons(major).join(';'), /major version update/);
  assert.match(guardReasons(major).join(';'), /not built by any required gate/);

  const boot = buildState(
    { title: 'fix(deps): update dependency org.springframework.boot:spring-boot-starter-parent to v4.2.0', body: '| x | `4.1.2` -> `4.2.0` |' },
    [{ filename: 'backend/pom.xml' }],
    green,
  );
  assert.match(guardReasons(boot).join(';'), /owner decision/);

  const red = buildState({ title: 'update dependency vite to v8.3.8', body: '| x | `8.3.7` -> `8.3.8` |' }, [{ filename: 'frontend/package.json' }], [
    { name: 'Frontend (PR gate)', conclusion: 'failure' },
  ]);
  assert.match(guardReasons(red).join(';'), /Frontend \(PR gate\)=failure/);
  assert.match(guardReasons(red).join(';'), /Backend Unit \(PR gate\)=missing/);
});

test('JEV can only make the verdict more cautious', () => {
  const merge = { choice: 'MERGE_CANDIDATE', confidence: 0.95 };
  assert.equal(finalVerdict(merge, [], 0.7), 'MERGE_CANDIDATE');
  assert.equal(finalVerdict(merge, ['major version update'], 0.7), 'HOLD_FOR_REVIEW');
  assert.equal(finalVerdict({ choice: 'HOLD_FOR_REVIEW', confidence: 0.9 }, [], 0.7), 'HOLD_FOR_REVIEW');
  assert.equal(finalVerdict({ choice: 'MERGE_CANDIDATE', confidence: 0.5 }, [], 0.7), 'ABSTAIN');
  assert.equal(finalVerdict({ choice: 'MERGE_CANDIDATE', confidence: 0.5 }, ['x'], 0.7), 'HOLD_FOR_REVIEW');
  assert.equal(finalVerdict(null, [], 0.7), 'ABSTAIN');
});

test('the comment carries the marker, the advisory label, the guard reasons and the pinned model', () => {
  const state = buildState({ title: 'update dependency twilio to v9', body: '| x | `8.10.0` -> `9.0.0` |' }, [{ filename: 'cpq_beta/pyproject.toml' }], green);
  const body = renderComment({
    verdict: 'HOLD_FOR_REVIEW',
    answer: { choice: 'MERGE_CANDIDATE', confidence: 0.8, probabilities: { MERGE_CANDIDATE: 0.8, HOLD_FOR_REVIEW: 0.15, ABSTAIN: 0.05 } },
    reasons: guardReasons(state),
    state,
    model: 'typesafe/jev-1.13',
  });
  assert.ok(body.startsWith(MARKER));
  assert.match(body, /advisory only, not a decision/);
  assert.match(body, /Forced to hold by guard: major version update/);
  assert.match(body, /typesafe\/jev-1\.13/);
});
