/** Prepare once per user command; retain this body unchanged for any transport retry. */
export async function approvalSubmitBody(readQuote, quoteId, tier) {
  const quote = await readQuote(`/api/v1/quotes/${quoteId}`);
  if (!Number.isSafeInteger(quote?.version) || quote.version < 0) {
    throw new Error('Approval submission requires a current non-negative quote version');
  }
  return Object.freeze({ tier, expectedVersion: quote.version });
}
