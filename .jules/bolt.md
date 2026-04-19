## 2026-04-18 - Added `trackBy` to Angular Material Tables
**Learning:** By default, Angular `mat-table` tracks rows by object reference. When data arrays are replaced (e.g., via Signals or RxJS), it triggers a full re-render, degrading performance and causing focus loss.
**Action:** Always add a `[trackBy]` function (like `trackById`) to `mat-table` (or any `*ngFor` mapping) that returns a unique primitive identifier (like `id`) instead of relying on default object tracking.
