## 2026-04-02 - Ensure CSRF tokens are not disabled

**Vulnerability:** The API Gateway disabled CSRF using `.csrf(ServerHttpSecurity.CsrfSpec::disable)`.
**Learning:** Spring Security disables CSRF protection by default for Token based APIS, but for session cookies or other forms of stateless/cookie mechanisms without explicit CSRF mechanisms it's not secure. The SPA requires the API Gateway token repository to use HTTP Only false in `CookieServerCsrfTokenRepository` to read the token.
**Prevention:** Rather than using `csrf.disable()`, properly configure it to use `CookieServerCsrfTokenRepository.withHttpOnlyFalse()` and `ServerCsrfTokenRequestAttributeHandler` for API Gateways serving Single Page Applications over WebFlux.
