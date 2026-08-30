# Wave D security quick reference

Canonical plan: `docs/forge/13-wave-d-architecture.md`

## Confirm still broken

```bash
rg -n "security\\.open|SECURITY_OPEN" backend/services --glob '*.java'
rg -n "azurewebsites|\\*\\." backend/services/gateway-service
```

## Services with securityOpen (verify before D2)

crm, pricing, pricebook, product, quote, approval-routing, configuration-engine, contract-asset-management, document-generation

## Gateway files

- `backend/services/gateway-service/src/main/resources/application.yml` (`globalcors`, `security.csrf.allowed-origins`)
- `backend/services/gateway-service/src/main/java/com/opencrm/gateway/config/CsrfProtectionFilter.java`
