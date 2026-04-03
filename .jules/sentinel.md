## 2024-05-14 - Fix Hardcoded DB Credentials
**Vulnerability:** Several microservices had the database password hardcoded as 'postgres' in their application.yml, which risks exposing credentials if the config is deployed to production without overriding.
**Learning:** Configuration files should always use environment variable fallbacks (e.g., ${DB_PASSWORD:postgres}) for sensitive data to ensure secure deployment.
**Prevention:** Standardize configuration templates and use linters to detect hardcoded secrets in property files.
