🚨 Severity: HIGH
💡 Vulnerability: The API Gateway permitted cross-origin requests and CSRF tokens from any subdomain under `azurewebsites.net` using a wildcard (`*.azurewebsites.net`).
🎯 Impact: An attacker who registers a malicious subdomain on Azure could bypass CORS and CSRF protections, allowing them to make unauthorized cross-origin requests or hijack sessions.
🔧 Fix: Replaced the wildcard domain with a strict environment variable fallback (`${FRONTEND_URL:}`) to restrict allowed origins to known, explicit domains.
✅ Verification: Run the gateway service test suite. The `application.yml` has been verified to start properly and the wildcard is removed.
