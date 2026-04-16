
## 2024-04-16 - Replacing frappe.get_doc with frappe.db.get_value for Partial Data Retrieval
**Learning:** In Frappe, using `frappe.get_doc()` forces the instantiation of a complete document, traversing the full lifecycle, hooks, and fetching all child tables even if only a single field is needed. Paired with `frappe.db.exists()`, this can lead to high overhead and unnecessary `SELECT *` + N+1 queries.
**Action:** Always replace `frappe.db.exists()` combined with `frappe.get_doc()` with a single `frappe.db.get_value(doctype, filters, fieldname)` call when retrieving simple scalar values or partial fields. This collapses multiple operations into a single fast SELECT query, drastically reducing query count and avoiding document instantiation overhead.
