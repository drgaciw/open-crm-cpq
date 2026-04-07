## 2024-05-24 - Avoid frappe.get_doc for read-only single field fetches
**Learning:** `frappe.get_doc()` does a full document instantiation with all child tables and controllers, which is extremely expensive for just reading a few fields. Calling `frappe.db.exists()` followed by `frappe.get_doc()` doubles the database query overhead.
**Action:** Use `frappe.db.get_value(doctype, filters, fieldname)` or `frappe.db.get_value(..., as_dict=True)` when you only need to read specific fields. This compiles down to a simple SELECT query without document overhead.
