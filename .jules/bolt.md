## 2024-04-13 - Prevent Full Re-renders in Signal-driven Angular Material Tables
**Learning:** In the modernized Angular frontend workspace, the CRM module heavily relies on Angular Signals for state management (e.g., `recentDeals.set(deals)`). When arrays are updated via signals, a new object reference is created. Without a `[trackBy]` function, `<table mat-table>` tracks rows by object reference by default. This causes the entire table to re-render from scratch on every state update, leading to DOM thrashing, potential loss of focus, and poor scrolling performance.
**Action:** Always define a `[trackBy]` function (e.g., `trackById`) for `<table mat-table>` when iterating over data arrays managed by Angular Signals or RxJS Observables that frequently emit new array references.

## 2024-04-16 - Replacing frappe.get_doc with frappe.db.get_value for Partial Data Retrieval
**Learning:** In Frappe, using `frappe.get_doc()` forces the instantiation of a complete document, traversing the full lifecycle, hooks, and fetching all child tables even if only a single field is needed. Paired with `frappe.db.exists()`, this can lead to high overhead and unnecessary `SELECT *` + N+1 queries.
**Action:** Always replace `frappe.db.exists()` combined with `frappe.get_doc()` with a single `frappe.db.get_value(doctype, filters, fieldname)` call when retrieving simple scalar values or partial fields. This collapses multiple operations into a single fast SELECT query, drastically reducing query count and avoiding document instantiation overhead.
## 2026-04-18 - Added `trackBy` to Angular Material Tables
**Learning:** By default, Angular `mat-table` tracks rows by object reference. When data arrays are replaced (e.g., via Signals or RxJS), it triggers a full re-render, degrading performance and causing focus loss.
**Action:** Always add a `[trackBy]` function (like `trackById`) to `mat-table` (or any `*ngFor` mapping) that returns a unique primitive identifier (like `id`) instead of relying on default object tracking.

## 2024-05-18 - Replacing Loop Execution of frappe.get_doc
**Learning:** Calling `frappe.get_doc()` inside loops for objects that share the same underlying references (like `reference_doctype` and `reference_name` in WhatsApp messages) leads to redundant, expensive N+1 instantiation issues.
**Action:** When a method inside a loop fetches information derived from values that are identical across all iterations, extract the expensive computation (`frappe.get_doc` or `get_from_name`) outside the loop, cache the result, and reuse it inside the loop to turn N queries into 1 query.

## 2024-05-19 - Removing Redundant N+1 frappe.db.exists Checks Before IN Queries
**Learning:** In Frappe, when compiling a list of candidate names to fetch via `frappe.get_all(..., filters={'name': ['in', names]})`, performing an N+1 `frappe.db.exists()` check in a loop beforehand is redundant. The `IN` query naturally filters out non-existent records, rendering the prior existence checks a complete waste of queries.
**Action:** When gathering a list of records to fetch with a single `frappe.get_all` `IN` query, directly pass the raw list of IDs to the query and let the database filter out any invalid or non-existent IDs implicitly, avoiding the N+1 `frappe.db.exists()` overhead.
## 2024-05-20 - Memoizing Expensive Template Bindings with Computed Signals
**Learning:** In Angular, binding methods that execute array operations (like `reduce()`, `find()`, or `Math.max()`) directly in the template causes them to be re-evaluated on every change detection cycle. This becomes a severe performance bottleneck when dealing with medium-to-large datasets.
**Action:** Always wrap derived state calculations in `computed` signals from `@angular/core`. This memoizes the result, ensuring the expensive operation is only executed when the underlying signals actually change.

## 2024-05-20 - Optimizing Template Lookups with Computed Maps
**Learning:** Using array `.find()` methods inside a template lookup method (e.g., `getStageValue(stage) { return pipeline.find(p => p.stage === stage).value }`) inside a `@for` loop results in $O(N \times M)$ complexity during change detection.
**Action:** Replace sequential template lookups by creating a `computed` Map signal in the component class. This transforms the lookup from $O(N)$ to $O(1)$ and guarantees the mapping is calculated exactly once when the source data changes.
