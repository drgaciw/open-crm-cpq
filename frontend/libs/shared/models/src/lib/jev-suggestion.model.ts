/**
 * An advisory suggestion produced by Jev (ADR-015). It never represents a decision: the existing rule
 * or the user stays authoritative, and the API omits it whenever Jev is disabled, unsure or unavailable.
 */
export interface JevSuggestion {
  /** Suggested value: a chosen key, a 0–100 score, or a probability, as text. */
  value: string;
  /** Model confidence in [0, 1]. */
  confidence: number;
  /** Distribution over the offered options or rubric levels; keys are option keys or level indices. */
  probabilities: Record<string, number>;
  rubricVersion: string;
  model: string;
  /** Always true. Present so a suggestion can never be mistaken for a decision in code review. */
  advisory: true;
  /** ISO-8601 timestamp. */
  computedAt: string;
}

/** Status of an asynchronously computed suggestion. */
export type JevSuggestionStatus = 'READY' | 'PENDING' | 'UNAVAILABLE';

export interface JevSuggestionEnvelope {
  status: JevSuggestionStatus;
  suggestion: JevSuggestion | null;
}
