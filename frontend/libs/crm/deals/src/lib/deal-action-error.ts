/** BC-13-01. The crm-service that emits this code lands on another branch. */
export const OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE = 'OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE';

const WON_REQUIRES_ACCEPTED_QUOTE_MESSAGE =
  'This opportunity cannot be marked Won until a linked quote is Accepted. Accept the quote, then mark the opportunity Won.';

interface HttpErrorBody {
  message?: string;
  errorCode?: string;
}

interface HttpErrorShape {
  errorCode?: string;
  message?: string;
  error?: HttpErrorBody | string;
}

function asErrorShape(err: unknown): HttpErrorShape | null {
  if (err == null || typeof err !== 'object') {
    return null;
  }
  return err as HttpErrorShape;
}

export function readDealErrorCode(err: unknown): string | undefined {
  const shape = asErrorShape(err);
  if (!shape) {
    return undefined;
  }
  if (shape.errorCode) {
    return shape.errorCode;
  }
  const body = shape.error;
  if (body && typeof body === 'object' && body.errorCode) {
    return body.errorCode;
  }
  return undefined;
}

/**
 * Banner text for deal actions. The accepted-quote win rule is always actionable,
 * even when the response body has no message yet.
 */
export function dealActionErrorMessage(err: unknown, fallback: string): string {
  const code = readDealErrorCode(err);
  if (code === OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE) {
    return `${WON_REQUIRES_ACCEPTED_QUOTE_MESSAGE} [${code}]`;
  }
  const shape = asErrorShape(err);
  const body = shape?.error;
  const bodyMessage = body && typeof body === 'object' ? body.message : undefined;
  const message = bodyMessage || shape?.message || fallback;
  if (code) {
    return `${message} [${code}]`;
  }
  return message;
}
