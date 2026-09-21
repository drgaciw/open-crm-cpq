/**
 * Unit tests for the decision layer. Run with: node --test e2e/lib/
 * No test framework dependency — these use node:test, available on Node 18+.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  createDecider,
  validateAnswer,
  aggregateConfidence,
  choice,
  noul,
  score,
  InvalidAnswerError,
  ConfidenceGuardError,
  AuthenticationError,
  RateLimitError,
} from './decide.mjs';

const CHOICE_Q = choice('Pick one', { a: 'Option A', b: 'Option B' });
const okChoice = {
  type: 'choice',
  choice: 'a',
  probabilities: { a: 0.8, b: 0.2 },
  confidence: 0.95,
};

/** Minimal Response stand-in for the injected fetch. */
const jsonResponse = (body, { status = 200, headers = {} } = {}) => ({
  ok: status >= 200 && status < 300,
  status,
  headers: { get: (k) => headers[k.toLowerCase()] ?? null },
  json: async () => body,
  text: async () => JSON.stringify(body),
});

// ── validateAnswer ────────────────────────────────────────────────────────────

test('accepts a well-formed choice answer', () => {
  assert.equal(validateAnswer(okChoice, CHOICE_Q, 'next').choice, 'a');
});

test('rejects a choice outside the offered criteria', () => {
  const answer = { ...okChoice, choice: 'c' };
  assert.throws(() => validateAnswer(answer, CHOICE_Q, 'next'), InvalidAnswerError);
});

test('rejects a partial probability distribution', () => {
  const answer = { ...okChoice, probabilities: { a: 1.0 } };
  assert.throws(() => validateAnswer(answer, CHOICE_Q, 'next'), InvalidAnswerError);
});

test('rejects probabilities that do not sum to 1', () => {
  const answer = { ...okChoice, probabilities: { a: 0.3, b: 0.3 } };
  assert.throws(() => validateAnswer(answer, CHOICE_Q, 'next'), InvalidAnswerError);
});

test('accepts probabilities within the 0.02 tolerance', () => {
  const answer = { ...okChoice, probabilities: { a: 0.8, b: 0.21 } };
  assert.equal(validateAnswer(answer, CHOICE_Q, 'next').choice, 'a');
});

test('rejects confidence outside [0,1]', () => {
  const answer = { ...okChoice, confidence: 1.4 };
  assert.throws(() => validateAnswer(answer, CHOICE_Q, 'next'), InvalidAnswerError);
});

test('rejects an answer whose type does not match the question', () => {
  assert.throws(
    () => validateAnswer({ type: 'noul', noul: 0.9 }, CHOICE_Q, 'next'),
    InvalidAnswerError,
  );
});

test('accepts a noul answer without a confidence field', () => {
  const q = noul('Is it urgent?');
  assert.equal(validateAnswer({ type: 'noul', noul: 0.92 }, q, 'urgent').noul, 0.92);
});

test('rejects a score outside the legend range', () => {
  const q = score('How frustrated?', ['Calm', 'Annoyed', 'Furious']);
  const answer = {
    type: 'score',
    score: 3.5,
    probabilities: { 0: 0.1, 1: 0.2, 2: 0.7 },
    confidence: 0.8,
  };
  assert.throws(() => validateAnswer(answer, q, 'frustration'), InvalidAnswerError);
});

// ── aggregateConfidence ───────────────────────────────────────────────────────

test('aggregate confidence is the minimum, not the product', () => {
  const answers = {
    one: { type: 'choice', confidence: 0.87 },
    two: { type: 'choice', confidence: 0.78 },
  };
  assert.equal(aggregateConfidence(answers), 0.78);
});

test('a noul near 0.5 counts as maximally uncertain', () => {
  assert.equal(aggregateConfidence({ a: { type: 'noul', noul: 0.5 } }), 0);
  assert.equal(aggregateConfidence({ a: { type: 'noul', noul: 1 } }), 1);
});

// ── batching ──────────────────────────────────────────────────────────────────

test('sends one HTTP call for a batch of questions', async () => {
  let calls = 0;
  let sentBody;
  const decider = createDecider({
    transport: 'typesafe',
    apiKey: 'k',
    fetchImpl: async (_url, init) => {
      calls++;
      sentBody = JSON.parse(init.body);
      return jsonResponse({
        answers: {
          first: okChoice,
          second: { type: 'noul', noul: 0.99 },
        },
      });
    },
  });

  const answers = await decider.ask('page state', {
    first: CHOICE_Q,
    second: noul('Done?'),
  });

  assert.equal(calls, 1, 'two questions must cost one round trip');
  assert.equal(Object.keys(sentBody.questions).length, 2);
  assert.equal(answers.first.choice, 'a');
  assert.equal(answers.second.noul, 0.99);
});

// ── retry / errors ────────────────────────────────────────────────────────────

test('retries a 429 and then succeeds', async () => {
  let calls = 0;
  const decider = createDecider({
    transport: 'typesafe',
    apiKey: 'k',
    maxRetries: 2,
    fetchImpl: async () => {
      calls++;
      return calls === 1
        ? jsonResponse({ error: 'slow down' }, { status: 429, headers: { 'retry-after': '0' } })
        : jsonResponse({ answers: { next: okChoice } });
    },
  });

  const answers = await decider.ask('s', { next: CHOICE_Q });
  assert.equal(calls, 2);
  assert.equal(answers.next.choice, 'a');
});

test('does not retry a 401 and surfaces AuthenticationError', async () => {
  let calls = 0;
  const decider = createDecider({
    transport: 'typesafe',
    apiKey: 'k',
    maxRetries: 3,
    fetchImpl: async () => {
      calls++;
      return jsonResponse({ error: 'bad key' }, { status: 401 });
    },
  });

  await assert.rejects(() => decider.ask('s', { next: CHOICE_Q }), AuthenticationError);
  assert.equal(calls, 1, 'auth failures must not be retried');
});

test('gives up after maxRetries on persistent 429', async () => {
  let calls = 0;
  const decider = createDecider({
    transport: 'typesafe',
    apiKey: 'k',
    maxRetries: 1,
    fetchImpl: async () => {
      calls++;
      return jsonResponse({}, { status: 429, headers: { 'retry-after': '0' } });
    },
  });

  await assert.rejects(() => decider.ask('s', { next: CHOICE_Q }), RateLimitError);
  assert.equal(calls, 2, 'initial attempt plus one retry');
});

test('requires an API key', () => {
  assert.throws(
    () => createDecider({ transport: 'typesafe', apiKey: '' , fetchImpl: async () => {} }),
    AuthenticationError,
  );
});

// ── confidence guard ──────────────────────────────────────────────────────────

test('requireChoice refuses to act below the threshold', async () => {
  const decider = createDecider({
    transport: 'typesafe',
    apiKey: 'k',
    minConfidence: 0.9,
    fetchImpl: async () =>
      jsonResponse({ answers: { next: { ...okChoice, confidence: 0.42 } } }),
  });

  await assert.rejects(() => decider.requireChoice('s', CHOICE_Q), ConfidenceGuardError);
});

test('requireChoice refuses the explicit stop option even at high confidence', async () => {
  const q = choice('Pick', { a: 'A', stop: 'No action' });
  const decider = createDecider({
    transport: 'typesafe',
    apiKey: 'k',
    fetchImpl: async () =>
      jsonResponse({
        answers: {
          next: { type: 'choice', choice: 'stop', probabilities: { a: 0.01, stop: 0.99 }, confidence: 0.99 },
        },
      }),
  });

  await assert.rejects(() => decider.requireChoice('s', q), ConfidenceGuardError);
});

test('requireChoice returns the answer when confident', async () => {
  const decider = createDecider({
    transport: 'typesafe',
    apiKey: 'k',
    fetchImpl: async () => jsonResponse({ answers: { next: okChoice } }),
  });

  const answer = await decider.requireChoice('s', CHOICE_Q);
  assert.equal(answer.choice, 'a');
});
