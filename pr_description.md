🚨 Severity: HIGH
💡 Vulnerability: Overly broad wildcard in CORS/CSRF allowed-origins configuration (`https://*.azurewebsites.net`).
🎯 Impact: Subdomain takeover and CSRF risk if an attacker controls a subdomain of azurewebsites.net.
🔧 Fix: Replaced wildcard with environment variable `${FRONTEND_URL:}`.
✅ Verification: Confirm `application.yml` uses `${FRONTEND_URL:}` instead of the wildcard.
