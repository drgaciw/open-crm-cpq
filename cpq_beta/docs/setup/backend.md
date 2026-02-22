# Backend Setup

## Requirements
- Python `>=3.10`
- Frappe Bench environment
- A site with this app installed

## Install
1. Get the app into your bench:
```bash
bench get-app crm
```
2. Install on a site:
```bash
bench --site <site-name> install-app crm
```
3. Start services:
```bash
bench start
```

## Twilio Configuration
1. Open **Twilio Settings** and enable integration.
2. Set Account SID and Auth Token.
3. Configure webhook URLs generated from Twilio settings.
4. Ensure public HTTPS routing is in place for webhook endpoints.

## Common Issues
- Permission errors on API calls: verify user roles and whitelisted method access.
- Twilio webhook rejections: confirm request signature validation passes and auth token is correct.
