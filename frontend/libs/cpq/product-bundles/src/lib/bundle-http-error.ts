/**
 * Prefer message; append any errorCode in banners (H1 shape: `message [CODE]`).
 * Handles HttpErrorResponse body and ApiError from apiResponseInterceptor.
 */
export function bundleHttpErrorMessage(err: unknown, fallback: string): string {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || fallback;
  if (code) {
    return `${message} [${code}]`;
  }
  return message;
}
