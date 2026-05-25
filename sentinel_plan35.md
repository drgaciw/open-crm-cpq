To fix ID enumeration:
```python
@frappe.whitelist()
def get_activities(name):
    # Use frappe.get_list which applies user permissions automatically
    deal_exists = frappe.get_list("CRM Deal", filters={"name": name}, limit=1, pluck="name")
    if deal_exists:
        return get_deal_activities(name)

    lead_exists = frappe.get_list("CRM Lead", filters={"name": name}, limit=1, pluck="name")
    if lead_exists:
        return get_lead_activities(name)

    frappe.throw(_("Document not found or not permitted"), frappe.DoesNotExistError)
```

Wait, `get_list` checks if the document exists AND the user has permission. If it exists but they don't have permission, `get_list` returns an empty list, so it falls through to the next check, and finally throws `DoesNotExistError`. This prevents ID enumeration because an attacker cannot tell if a document exists and they don't have permission vs. the document does not exist at all!

This is exactly the security vulnerability described in the memory.

Let's make a new plan.
First, I will run `request_plan_review` for this new fix!
