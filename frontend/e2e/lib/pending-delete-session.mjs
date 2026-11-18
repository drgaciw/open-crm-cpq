/** Test-only credential preflight; never returns or includes cookie values in diagnostics. */
export function requireDistinctGatewaySessions(seller, approver, {
  base, cookieName = 'OPENCRM_SESSION', now = Date.now() / 1000,
} = {}) {
  if (typeof cookieName !== 'string' || !/^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/.test(cookieName)) {
    throw new Error('BLOCKED: UAT_SESSION_COOKIE_NAME must be an explicit valid cookie name');
  }
  const url = new URL(base);
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('BLOCKED: HTTP(S) test origin required');
  const session = (state) => {
    const applicable = (Array.isArray(state?.cookies) ? state.cookies : []).filter((cookie) => {
      if (cookie.name !== cookieName || typeof cookie.domain !== 'string') return false;
      const domain = cookie.domain.toLowerCase();
      const matchesHost = domain.startsWith('.')
        ? url.hostname === domain.slice(1) || url.hostname.endsWith(domain)
        : url.hostname === domain;
      // Match the browser-facing ingress origin, never the internal gateway URL.
      // Chromium allows Secure cookies on localhost, as documented for local login.
      const transport = !cookie.secure || url.protocol === 'https:' || url.hostname === 'localhost';
      const live = cookie.expires === -1 || cookie.expires > now;
      return matchesHost && transport && live && !cookie.partitionKey;
    });
    // GatewaySessionConfiguration fixes path('/'). Reject ambiguous same-name cookies
    // instead of guessing which value the browser/gateway will select on API requests.
    if (applicable.length !== 1 || applicable[0].path !== '/'
        || typeof applicable[0].value !== 'string' || !applicable[0].value.trim()) {
      throw new Error('BLOCKED: one nonempty, unexpired gateway session cookie with matching host, transport and root path is required per login');
    }
    return applicable[0].value;
  };
  if (session(seller) === session(approver)) {
    throw new Error('BLOCKED: seller and approver must use distinct gateway session cookies');
  }
  // This proves only distinct scoped credentials, not distinct principals or roles.
}
