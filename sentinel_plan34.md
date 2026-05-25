Bingo! `cpq_beta/crm/api/activities.py` lines 11-20:
```python
    if frappe.db.exists("CRM Deal", name):
        if not frappe.has_permission("CRM Deal", "read", name):
            frappe.throw(_("Not permitted"), frappe.PermissionError)
        return get_deal_activities(name)
    elif frappe.db.exists("CRM Lead", name):
        if not frappe.has_permission("CRM Lead", "read", name):
            frappe.throw(_("Not permitted"), frappe.PermissionError)
        return get_lead_activities(name)
    else:
        frappe.throw(_("Document not found"), frappe.DoesNotExistError)
```

This exactly matches the memory description:
"To prevent ID enumeration vulnerabilities in Frappe whitelisted APIs, do not use `frappe.db.exists()` followed by distinct error handling (e.g., throwing `PermissionError` vs `DoesNotExistError`). Use `frappe.get_list()` with a name filter to verify both existence and accessibility in a single step, and throw a unified `DoesNotExistError` if the record is missing or unauthorized."

Let's look at `cpq_beta/crm/api/activities.py`.
