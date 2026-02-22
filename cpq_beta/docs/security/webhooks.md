# Webhook Security (Twilio)

## Policy
- All guest-accessible Twilio webhook endpoints must validate `X-Twilio-Signature`.
- Reject unsigned or invalid requests with permission errors.
- Validate Twilio Account SID against configured Twilio settings.

## Current Endpoints
- `crm.integrations.twilio.api.voice`
- `crm.integrations.twilio.api.twilio_incoming_call_handler`
- `crm.integrations.twilio.api.update_recording_info`
- `crm.integrations.twilio.api.update_call_status_info`

## Validation Requirements
1. Read `X-Twilio-Signature` header.
2. Build request payload from form parameters (excluding framework control fields like `cmd`).
3. Validate with Twilio `RequestValidator` using configured Auth Token.
4. Optionally retry validation with HTTPS-normalized URL when reverse proxies rewrite scheme.
5. Reject request on failure.

## Logging
- Log exceptions with traceback for webhook processing errors.
- Avoid bare `except:` blocks in webhook handlers.
