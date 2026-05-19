Wait, `backend/services/ai-service/src/main/resources/application.yml` has a hardcoded default for `AZURE_OPENAI_API_KEY`:
`api-key: ${AZURE_OPENAI_API_KEY:mock-key}`
If this is committed, any environment that doesn't set `AZURE_OPENAI_API_KEY` will try to use `mock-key`, or worse, if a real key was hardcoded, it would be a vulnerability. `mock-key` is just a mock, but removing it enforces explicit configuration.
Wait! I've been asked to fix ONE security issue.
I should look at the `.env` file or `docker-compose.yml`.
Memory says:
> To fix hardcoded credential vulnerabilities, replace default values in `docker-compose.yml` (e.g., `${VAR:-default}`) with bare environment variables (e.g., `${VAR}`) to force explicit configuration in the `.env` file.
But I ran `grep -rn ":-" backend/docker-compose.yml` and found NOTHING! The `:-` syntax doesn't exist in `backend/docker-compose.yml`.

Wait, maybe in `frontend/`?
What about missing authentication in `gateway-service` or something?
Let's check if there is missing authorization in `cpq_beta/crm/api/whatsapp.py`.
```python
@frappe.whitelist()
def send_whatsapp_template(reference_doctype, reference_name, template, to):
    if not frappe.has_permission(reference_doctype, "read", reference_name):
        frappe.throw(_("Not permitted"), frappe.PermissionError)

    doc = frappe.new_doc("WhatsApp Message")
    doc.update(
        {
            "reference_doctype": reference_doctype,
            "reference_name": reference_name,
            "message_type": "Template",
            "message": "Template message",
            "content_type": "text",
            "use_template": True,
            "template": template,
            "to": to,
        }
    )
    doc.insert(ignore_permissions=True)
    return doc.name
```
An attacker can send a template message to any number `to` they want using any template, and attach it to any `reference_doctype` and `reference_name` they have read access to.
They can use this to spam WhatsApp messages from the company's number to any phone number!
Wait! The `to` parameter is user-supplied and NOT validated. They just need read access to ONE document (like a Lead they created or are assigned to) to abuse the API.
Wait, can they send an arbitrary `message` in `create_whatsapp_message`?
```python
@frappe.whitelist()
def create_whatsapp_message(
    reference_doctype,
    reference_name,
    message,
    to,
    attach,
    reply_to,
    content_type="text",
):
    if not frappe.has_permission(reference_doctype, "read", reference_name):
        frappe.throw(_("Not permitted"), frappe.PermissionError)
```
Yes! If they have read access to one document, they can use `create_whatsapp_message` to send `message` to arbitrary `to` number! This is an SSRF/Spam/Abuse vulnerability (Arbitrary Message Sending).
Actually, this API just creates a "WhatsApp Message" document. Does creating a "WhatsApp Message" document automatically send it?
If the system has a hook on `WhatsApp Message` creation to send via API, then yes!

Let's look at `docs/dev/ast-grep-rules.md` again.
"Assertion-based request validation: assert $COND"
Wait! I searched for `assert ` and found nothing. Maybe it's `assert` inside a `try` block? I found no `assert` statements at all.

Wait! Let me look at memory again:
> To comply with the project's ast-grep security rules against dynamic SQL concatenation (frappe.db.sql(query + where, $$$)), replace f-string table name injections (e.g., f"SELECT ... FROM \`tab{doctype}\`") with explicit, hardcoded SQL strings nested inside conditional logic, even if the variable like doctype is already strictly validated against an allowlist.
Is there any f-string table name injection?
Let's check `backend/` for `SQL` concatenation.
