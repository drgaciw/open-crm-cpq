## 2024-04-01 - Fix Hardcoded Credentials in application.yml
**Vulnerability:** Hardcoded database credentials (`postgres:postgres`) were found in multiple `application.yml` files (`approval-routing`, `contract-asset-management`, `configuration-engine`, `document-generation`).
**Learning:** Hardcoded credentials in configuration files are a critical security risk as they expose sensitive information in the codebase.
**Prevention:** Always use environment variable fallbacks (e.g., `${DB_USERNAME:postgres}`) for sensitive configuration properties to allow secure injection in production environments while maintaining local development convenience.
