
## 2024-05-18 - Replace `new Function` with `sval` in dynamic evaluations
**Vulnerability:** Evaluated dynamic JavaScript expressions using `new Function(...)` which leads to RCE/XSS.
**Learning:** Using `new Function(...)` on dynamic expressions opens vectors for executing malicious user code. We need a sandboxed environment to execute these securely without accessing the global execution context.
**Prevention:** Use `sval` with its default sandbox mode and selectively import safe globals instead of falling back to unsafe evaluation methods like `eval` or `new Function()`.
