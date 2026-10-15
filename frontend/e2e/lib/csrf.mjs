/** Cookie-authenticated API writes need the same CSRF token and Origin as the browser. */
export function createCsrfOptions(request, { base }) {
  const origin = new URL(base).origin;
  let bootstrap;

  async function cookieFor(url) {
    const { cookies } = await request.storageState();
    const now = Date.now() / 1000;
    const cookie = cookies.filter((cookie) => {
      const domain = cookie.domain.replace(/^\./, '');
      const domainMatches = cookie.domain.startsWith('.')
        ? url.hostname === domain || url.hostname.endsWith(`.${domain}`)
        : url.hostname === domain;
      const path = cookie.path || '/';
      const pathMatches = url.pathname === path ||
        (url.pathname.startsWith(path) && (path.endsWith('/') || url.pathname[path.length] === '/'));
      return cookie.name === 'XSRF-TOKEN' && cookie.value && domainMatches && pathMatches &&
        !cookie.httpOnly && (!cookie.secure || url.protocol === 'https:') &&
        (cookie.expires === -1 || cookie.expires > now);
    }).sort((a, b) => b.path.length - a.path.length)[0];
    return cookie ? decodeURIComponent(cookie.value) : undefined;
  }

  return async (method, target, options = {}) => {
    const url = new URL(target);
    if (['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method.toUpperCase()) ||
        url.origin !== origin || !url.pathname.startsWith('/api/')) return options;

    let token = await cookieFor(url);
    if (!token) {
      if (!bootstrap) {
        bootstrap = (async () => {
          const response = await request.get(new URL('/api/v1/csrf', origin).href, {
            timeout: options.timeout,
            failOnStatusCode: false,
            maxRedirects: 0,
          });
          if (!response.ok()) throw new Error(`CSRF bootstrap failed: HTTP ${response.status()}`);
        })().finally(() => { bootstrap = undefined; });
      }
      await bootstrap;
      token = await cookieFor(url);
      if (!token) throw new Error('CSRF bootstrap did not issue a usable XSRF-TOKEN cookie');
    }
    // APIRequestContext forwards custom headers across redirects. Never let this token leave the origin.
    return { ...options, maxRedirects: 0, headers: { ...options.headers, Origin: origin, 'X-XSRF-TOKEN': token } };
  };
}
