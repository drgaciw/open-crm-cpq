## 2024-05-18 - [Remove new Function eval with Sandboxed Sval]
**Vulnerability:** Dynamic arbitrary code execution risk due to `new Function` used to evaluate form dependencies based on `eval:` script expressions.
**Learning:** The Vue component `SectionFields.vue` was using `new Function(...variable_names, code)` directly, exposing a critical dynamic execution sink, similar to `eval()`. This vulnerability pattern violates our AST grep checks.
**Prevention:** Adopt sandboxed JavaScript interpreters like `sval` for evaluating dynamic scripts securely within a limited context (e.g., exposing only safe Math, Date, etc.) without allowing breakout code execution.
