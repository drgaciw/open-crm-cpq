🚨 Severity: HIGH

💡 Vulnerability: The Gateway service (`backend/services/gateway-service/src/main/resources/application.yml`) used an overly permissive wildcard domain (`https://*.azurewebsites.net`) in its allowed-origins list for both CORS and CSRF configurations. Azure App Service allows arbitrary subdomain registration.

🎯 Impact: An attacker could easily register a malicious subdomain (e.g., `https://attacker.azurewebsites.net`) and completely bypass the application's CORS and CSRF protections, allowing them to make unauthorized cross-origin requests or execute CSRF attacks against logged-in users.

🔧 Fix: Replaced the dangerous `https://*.azurewebsites.net` wildcard in both CORS and CSRF configurations with the environment variable `${FRONTEND_URL:}`. This securely enforces exact origin matching based on the deployed environment while defaulting to an empty fallback (which safely fails to match any valid origin) if unset.

✅ Verification:
1. Verified compilation via `./mvnw clean install -DskipTests -pl services/gateway-service -am`
2. Verified functionality with full test suite via `./mvnw test -pl services/gateway-service`
