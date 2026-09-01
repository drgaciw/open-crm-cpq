/**
 * Prefer message; append known bundle errorCodes in banners.
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
  if (
    code === 'BUNDLE_MAX_ITEMS_EXCEEDED' ||
    code === 'BUNDLE_MIN_ITEMS_REQUIRED'
  ) {
    return `${message} [${code}]`;
  }
  return message;
}
