/**
 * TypeSafe (Jev) decision client for browser-use agents.
 *
 * Wraps the System One evaluation endpoint so scripts can ask a *batch* of typed
 * questions in one round trip and get validated, confidence-gated answers back.
 *
 * Two transports:
 *   - 'typesafe'   POST https://api.typesafe.ai/v1/systemone   (native; TYPESAFE_API_KEY)
 *   - 'openrouter' POST https://openrouter.ai/api/alpha/decisions (OPENROUTER_API_KEY)
 *
 * The native transport is preferred: it accepts a question map, so N questions cost
 * one call instead of N. See docs.typesafe.ai/cookbooks/parallel_questions.
 *
 * Validation is deliberately strict — a browser agent acts on these answers, so a
 * malformed or out-of-distribution response must fail loudly rather than be coerced.
 */

const TYPESAFE_URL = 'https://api.typesafe.ai/v1/systemone';
const OPENROUTER_URL = 'https://openrouter.ai/api/alpha/decisions';

/** Probabilities must sum to 1 within this tolerance. */
const PROBABILITY_TOLERANCE = 0.02;

// ── Errors ────────────────────────────────────────────────────────────────────
// Mirrors the SDK's error classes so callers can branch on failure kind rather
// than string-matching an HTTP status.

export class TypeSafeError extends Error {
  constructor(message, { status, body } = {}) {
    super(message);
    this.name = new.target.name;
    this.status = status;
    this.body = body;
  }
}
export class AuthenticationError extends TypeSafeError {} // 401
export class BadRequestError extends TypeSafeError {} // 400
export class UnprocessableEntityError extends TypeSafeError {} // 422
export class RateLimitError extends TypeSafeError {} // 429
export class InternalServerError extends TypeSafeError {} // 5xx / 529
export class APITimeoutError extends TypeSafeError {}
export class APIConnectionError extends TypeSafeError {}
/** Raised when a response is well-formed HTTP but not a usable decision. */
export class InvalidAnswerError extends TypeSafeError {}
/** Raised when an answer is valid but too uncertain to act on. */
export class ConfidenceGuardError extends TypeSafeError {
  constructor(message, { questionId, confidence, threshold, choice } = {}) {
    super(message);
    this.questionId = questionId;
    this.confidence = confidence;
    this.threshold = threshold;
    this.choice = choice;
  }
}

function errorForStatus(status, body) {
  const detail = `Provider HTTP ${status}`;
  if (status === 401) return new AuthenticationError(detail, { status, body });
  if (status === 400) return new BadRequestError(detail, { status, body });
  if (status === 422) return new UnprocessableEntityError(detail, { status, body });
  if (status === 429) return new RateLimitError(detail, { status, body });
  if (status >= 500) return new InternalServerError(detail, { status, body });
  return new TypeSafeError(detail, { status, body });
}

/** 429 and 529 are the documented retryable statuses; other 5xx are worth one retry too. */
function isRetryable(error) {
  return (
    error instanceof RateLimitError ||
    error instanceof InternalServerError ||
    error instanceof APITimeoutError ||
    error instanceof APIConnectionError
  );
}

// ── Question builders ─────────────────────────────────────────────────────────

/** @param {string} instructions @param {Record<string,string>} criteria */
export const choice = (instructions, criteria) => ({ type: 'choice', instructions, criteria });
/** @param {string} instructions @param {{true?:string,false?:string}} [criteria] */
export const noul = (instructions, criteria) => ({
  type: 'noul',
  instructions,
  ...(criteria ? { criteria } : {}),
});
/** @param {string} instructions @param {string[]} criteria Ordered, ascending. */
export const score = (instructions, criteria) => ({ type: 'score', instructions, criteria });

// ── Validation ────────────────────────────────────────────────────────────────

const isUnitInterval = (n) => Number.isFinite(n) && n >= 0 && n <= 1;

/**
 * Validate a probability distribution against the exact key set it must cover.
 * Guards against the model returning a partial or renormalised distribution.
 */
function assertDistribution(probabilities, expectedKeys, questionId) {
  if (!probabilities || typeof probabilities !== 'object') {
    throw new InvalidAnswerError(`Question '${questionId}': missing probabilities`);
  }
  const keys = Object.keys(probabilities);
  if (keys.length !== expectedKeys.length) {
    throw new InvalidAnswerError(
      `Question '${questionId}': expected ${expectedKeys.length} probabilities, got ${keys.length}`,
    );
  }
  for (const key of expectedKeys) {
    if (!isUnitInterval(probabilities[key])) {
      throw new InvalidAnswerError(
        `Question '${questionId}': probability for '${key}' is not in [0,1]`,
      );
    }
  }
  const sum = Object.values(probabilities).reduce((a, b) => a + b, 0);
  if (Math.abs(sum - 1) > PROBABILITY_TOLERANCE) {
    throw new InvalidAnswerError(
      `Question '${questionId}': probabilities sum to ${sum.toFixed(4)}, expected 1 ± ${PROBABILITY_TOLERANCE}`,
    );
  }
}

/**
 * Validate one answer against the question that produced it.
 * Throws InvalidAnswerError rather than returning a partial result.
 */
export function validateAnswer(answer, question, questionId) {
  if (!answer || answer.type !== question.type) {
    throw new InvalidAnswerError(
      `Question '${questionId}': expected a '${question.type}' answer, got '${answer?.type}'`,
    );
  }

  if (question.type === 'choice') {
    if (!Object.hasOwn(question.criteria, answer.choice)) {
      throw new InvalidAnswerError(
        `Question '${questionId}': choice '${answer.choice}' is not one of the offered criteria`,
      );
    }
    if (!isUnitInterval(answer.confidence)) {
      throw new InvalidAnswerError(`Question '${questionId}': confidence is not in [0,1]`);
    }
    assertDistribution(answer.probabilities, Object.keys(question.criteria), questionId);
    return answer;
  }

  if (question.type === 'noul') {
    // Per the API reference a noul answer carries the probability itself and no
    // separate confidence field — the probability IS the signal.
    if (!isUnitInterval(answer.noul)) {
      throw new InvalidAnswerError(`Question '${questionId}': noul is not in [0,1]`);
    }
    return answer;
  }

  if (question.type === 'score') {
    if (!Number.isFinite(answer.score)) {
      throw new InvalidAnswerError(`Question '${questionId}': score is not finite`);
    }
    const maxIndex = question.criteria.length - 1;
    if (answer.score < 0 || answer.score > maxIndex) {
      throw new InvalidAnswerError(
        `Question '${questionId}': score ${answer.score} outside legend range 0..${maxIndex}`,
      );
    }
    if (!isUnitInterval(answer.confidence)) {
      throw new InvalidAnswerError(`Question '${questionId}': confidence is not in [0,1]`);
    }
    assertDistribution(
      answer.probabilities,
      question.criteria.map((_, i) => String(i)),
      questionId,
    );
    return answer;
  }

  throw new InvalidAnswerError(`Question '${questionId}': unsupported type '${question.type}'`);
}

// ── Client ────────────────────────────────────────────────────────────────────

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * @param {object} [options]
 * @param {'typesafe'|'openrouter'} [options.transport]
 * @param {string} [options.apiKey]
 * @param {string} [options.model]
 * @param {number} [options.minConfidence] Default gate for requireChoice/requireScore.
 * @param {number} [options.timeoutMs]
 * @param {number} [options.maxRetries]
 * @param {typeof fetch} [options.fetchImpl] Injected for tests.
 * @param {(event:object)=>void} [options.onTrace]
 */
export function createDecider({
  transport = process.env.TYPESAFE_TRANSPORT ||
    (process.env.TYPESAFE_API_KEY ? 'typesafe' : 'openrouter'),
  apiKey,
  model,
  minConfidence = 0.9,
  timeoutMs = 30000,
  maxRetries = 3,
  fetchImpl = globalThis.fetch,
  onTrace,
} = {}) {
  const isNative = transport === 'typesafe';
  const url = isNative ? TYPESAFE_URL : OPENROUTER_URL;
  const resolvedModel = model || (isNative ? 'jev-latest' : 'typesafe/jev-1.13');
  const resolvedKey = (
    apiKey ||
    (isNative ? process.env.TYPESAFE_API_KEY : process.env.OPENROUTER_API_KEY) ||
    ''
  ).trim();

  if (!resolvedKey) {
    const envName = isNative ? 'TYPESAFE_API_KEY' : 'OPENROUTER_API_KEY';
    throw new AuthenticationError(`${envName} is required for the '${transport}' transport`);
  }

  async function post(body) {
    let lastError;
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        const response = await fetchImpl(url, {
          method: 'POST',
          redirect: 'error',
          signal: AbortSignal.timeout(timeoutMs),
          headers: {
            Authorization: `Bearer ${resolvedKey}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(body),
        });

        if (response.ok) return await response.json();

        const text = await response.text().catch(() => '');
        const error = errorForStatus(response.status, text);
        if (!isRetryable(error) || attempt === maxRetries) throw error;

        // Honour Retry-After when present, else exponential backoff with jitter.
        const retryAfter = Number(response.headers?.get?.('retry-after'));
        const backoff = Number.isFinite(retryAfter) && retryAfter > 0
          ? retryAfter * 1000
          : 2 ** attempt * 500 + Math.random() * 250;
        lastError = error;
        await sleep(backoff);
      } catch (cause) {
        if (cause instanceof TypeSafeError && !isRetryable(cause)) throw cause;
        const wrapped =
          cause?.name === 'TimeoutError' || cause?.name === 'AbortError'
            ? new APITimeoutError(`Decision request exceeded ${timeoutMs}ms`)
            : cause instanceof TypeSafeError
              ? cause
              : new APIConnectionError(String(cause?.message || cause));
        if (attempt === maxRetries) throw wrapped;
        lastError = wrapped;
        await sleep(2 ** attempt * 500 + Math.random() * 250);
      }
    }
    throw lastError;
  }

  /**
   * Ask a batch of questions about one state. One HTTP call regardless of count.
   *
   * @param {unknown} state Serialised as JSON; page content is untrusted data.
   * @param {Record<string, object>} questions Map of id -> question builder result.
   * @returns {Promise<Record<string, object>>} Validated answers, keyed by question id.
   */
  async function ask(state, questions) {
    const ids = Object.keys(questions);
    if (ids.length === 0) throw new BadRequestError('At least one question is required');

    const startedAt = Date.now();
    const payload = {
      model: resolvedModel,
      state: typeof state === 'string' ? state : JSON.stringify(state),
      questions,
    };

    const json = await post(payload);
    const answers = json?.answers;
    if (!answers || typeof answers !== 'object') {
      throw new InvalidAnswerError('Response contained no answers object');
    }

    const validated = {};
    for (const id of ids) {
      validated[id] = validateAnswer(answers[id], questions[id], id);
    }

    onTrace?.({
      questionCount: ids.length,
      latencyMs: Date.now() - startedAt,
      usage: json.usage ?? null,
      answers: validated,
    });

    return validated;
  }

  /**
   * Ask a single choice question and return the selected key, refusing to act when
   * the model is not confident enough or has selected the explicit bail-out option.
   *
   * @param {string} stopKey Criterion meaning "take no action"; selecting it stops the run.
   */
  async function requireChoice(state, question, { id = 'next', threshold = minConfidence, stopKey = 'stop' } = {}) {
    const answers = await ask(state, { [id]: question });
    const answer = answers[id];
    if (answer.confidence < threshold) {
      throw new ConfidenceGuardError(
        `Confidence ${answer.confidence.toFixed(3)} below threshold ${threshold} for '${id}'`,
        { questionId: id, confidence: answer.confidence, threshold, choice: answer.choice },
      );
    }
    if (stopKey && answer.choice === stopKey) {
      throw new ConfidenceGuardError(`Model selected '${stopKey}' — no viable action`, {
        questionId: id,
        confidence: answer.confidence,
        threshold,
        choice: answer.choice,
      });
    }
    return answer;
  }

  return { ask, requireChoice, transport, model: resolvedModel, minConfidence };
}

/**
 * Aggregate confidence across a batch as the *minimum* across answers, not the
 * product. Per the function-calling cookbook this reports "the least certain
 * judgement in the call" and avoids the false precision of multiplying.
 * Noul answers contribute distance from 0.5, scaled to [0,1].
 */
export function aggregateConfidence(answers) {
  const values = Object.values(answers).map((a) =>
    a.type === 'noul' ? Math.abs(a.noul - 0.5) * 2 : a.confidence,
  );
  return values.length ? Math.min(...values) : 0;
}
