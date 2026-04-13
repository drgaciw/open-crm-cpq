## 2024-04-13 - [CRITICAL] Prevent Remote Code Execution via new Function()
**Vulnerability:** Found `new Function(...)` used for dynamically evaluating JavaScript expressions in `cpq_beta/frontend/src/components/SectionFields.vue`. This allowed arbitrary JavaScript execution which could lead to XSS or RCE.
**Learning:** `new Function()` operates in the global scope and allows any Javascript execution. Relying on it to execute expressions is dangerous. It's safer to sandbox scripts.
**Prevention:** Used `sval`, a sandboxed JavaScript interpreter. Imported safe globals to maintain existing functionality without compromising security. Never use `eval()` or `new Function()` on user-influenced expressions.
