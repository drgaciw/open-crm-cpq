🚨 Severity: HIGH

💡 Vulnerability: The Gateway service's `application.yml` permitted cross-origin requests and CSRF tokens from the highly permissive wildcard `https://*.azurewebsites.net`. Any user can register an Azure app service and claim a subdomain under `.azurewebsites.net`.

🎯 Impact: An attacker could host a malicious application on an Azure subdomain and bypass CORS and CSRF protections to interact with the backend API as the authenticated user.

🔧 Fix: Replaced the overly permissive wildcard with an environment variable `${FRONTEND_URL:}` for both `spring.cloud.gateway.globalcors.corsConfigurations['[/**]'].allowedOrigins` and `security.csrf.allowed-origins`.

✅ Verification:
- `cd backend && ./mvnw clean compile -DskipTests -pl services/gateway-service -am`
- `cd backend && ./mvnw test -pl services/gateway-service -am -Dskip.docker.tests=true`
