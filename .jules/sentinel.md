## 2024-06-03 - [Fix RCE/XSS in custom script evaluation]
**Vulnerability:** Evaluated dynamic JavaScript (like Custom Actions) in the frontend using `new Function()`, leading to potential XSS and RCE if the scripts are injected or manipulated.
**Learning:** `new Function()` in browser environments is equivalent to `eval()` and should never be used on untrusted user inputs.
**Prevention:** Always use a sandboxed JavaScript interpreter like `sval` to evaluate untrusted scripts on the frontend.
## 2025-05-24 - Dynamic Code Evaluation via new Function in Frontend Scripts
**Vulnerability:** The Vue frontend (`cpq_beta/frontend/src/utils/index.js` and `cpq_beta/frontend/src/components/SectionFields.vue`) used `new Function()` to dynamically evaluate user-provided script strings for form/list actions and field dependency evaluation. This created a significant Remote Code Execution (RCE) / Cross-Site Scripting (XSS) risk if those string payloads were manipulated by an attacker, allowing arbitrary code to run with full access to the browser's global scope.
**Learning:** Utilizing native Javascript evaluation methods (`new Function`, `eval()`) for dynamically executing metadata configurations or expressions exposes the application to critical injection attacks. Replacing them with a sandboxed Javascript interpreter isolates execution from the `window` context.
**Prevention:** Always use safe interpretation libraries like `sval` to execute dynamic code on the client side. Ensure only safe objects (`console`, `Math`, `JSON`, `Date`) are explicitly whitelisted into the sandbox context via `interpreter.import()`, preventing access to the DOM or global environment.

## 2024-05-18 - Replace `new Function` with `sval` in dynamic evaluations
**Vulnerability:** Evaluated dynamic JavaScript expressions using `new Function(...)` which leads to RCE/XSS.
**Learning:** Using `new Function(...)` on dynamic expressions opens vectors for executing malicious user code. We need a sandboxed environment to execute these securely without accessing the global execution context.
**Prevention:** Use `sval` with its default sandbox mode and selectively import safe globals instead of falling back to unsafe evaluation methods like `eval` or `new Function()`.
## 2024-04-13 - [CRITICAL] Prevent Remote Code Execution via new Function()
**Vulnerability:** Found `new Function(...)` used for dynamically evaluating JavaScript expressions in `cpq_beta/frontend/src/components/SectionFields.vue`. This allowed arbitrary JavaScript execution which could lead to XSS or RCE.
**Learning:** `new Function()` operates in the global scope and allows any Javascript execution. Relying on it to execute expressions is dangerous. It's safer to sandbox scripts.
**Prevention:** Used `sval`, a sandboxed JavaScript interpreter. Imported safe globals to maintain existing functionality without compromising security. Never use `eval()` or `new Function()` on user-influenced expressions.
## 2024-04-14 - Replace unsafe dynamic code execution in SectionFields.vue
**Vulnerability:** Usage of `new Function` to evaluate arbitrary javascript strings for `depends_on` functionality in `cpq_beta/frontend/src/components/SectionFields.vue`. This behaves like `eval()` and could allow Cross-Site Scripting (XSS) if expressions contain untrusted input.
**Learning:** Evaluating dynamic scripts in a frontend application can expose the host environment to malicious code execution.
**Prevention:** Always use a sandboxed JS interpreter like `sval` instead of `new Function()` or `eval()` to securely evaluate dynamic expressions, explicitly limiting access to safe global objects.
## 2024-04-15 - [Critical] Unsafe dynamic script evaluation in Vue components
**Vulnerability:** Found arbitrary JavaScript execution (RCE/XSS) in `SectionFields.vue` due to evaluating dynamic `depends_on` expressions directly via `new Function(...)`. This is extremely dangerous as user-controlled data can result in remote code execution or cross-site scripting inside the browser context.
**Learning:** The project relies on evaluating conditions for UI rendering (like hiding/showing fields based on `depends_on`), but using native tools like `eval()` or `new Function()` strips away the natural Vue bounds and opens the app to massive vulnerability vectors.
**Prevention:** Instead of using `new Function()`, use the `sval` sandboxed JS interpreter (which is now correctly utilized). It must be initialized per-evaluation or securely cached, and should specifically whitelist safe globals (`console`, `Math`, `JSON`, `Date`) while keeping the sandbox intact to strictly limit what the evaluated script can do.
## 2024-05-18 - [Remove new Function eval with Sandboxed Sval]
**Vulnerability:** Dynamic arbitrary code execution risk due to `new Function` used to evaluate form dependencies based on `eval:` script expressions.
**Learning:** The Vue component `SectionFields.vue` was using `new Function(...variable_names, code)` directly, exposing a critical dynamic execution sink, similar to `eval()`. This vulnerability pattern violates our AST grep checks.
**Prevention:** Adopt sandboxed JavaScript interpreters like `sval` for evaluating dynamic scripts securely within a limited context (e.g., exposing only safe Math, Date, etc.) without allowing breakout code execution.
## 2025-02-14 - Deprecated ExecCommand API Fixed
**Vulnerability:** The codebase was utilizing `document.execCommand('copy')` as a fallback method to write data to the user's clipboard when the Clipboard API was unavailable. This method is deprecated across modern browsers and is actively flagged by the repository's structural security scanners (`ast-grep`).
**Learning:** Legacy browser APIs are sometimes maintained as fallbacks but are actively deprecated because they often lack proper permissions control compared to modern alternatives like the Async Clipboard API (`navigator.clipboard`). Modern clipboard access requires secure contexts (`HTTPS` or `localhost`), which prevents malicious sites from silently intercepting or modifying clipboard contents.
**Prevention:** Always default to modern, permission-based web APIs like `navigator.clipboard`. Do not attempt to re-implement fallbacks using deprecated APIs (`document.execCommand`); instead, catch unsupported API errors gracefully and present a meaningful error message to the user informing them that their context is insecure.
## 2026-04-21 - [Reverse Tabnabbing Vulnerability via `window.open`]
**Vulnerability:** Several Vue components (`UserDropdown.vue`, `WhatsAppArea.vue`, `WhatsappTemplateSelectorModal.vue`) used `window.open(url, '_blank')` without the `noopener,noreferrer` attributes. This left the application vulnerable to reverse tabnabbing, allowing newly opened malicious tabs to manipulate the original tab's `window.opener` object and redirect the user to a phishing site.
**Learning:** Opening untrusted links or external pages using `_blank` without explicitly cutting the context link (`noopener,noreferrer`) poses a high security risk in modern web applications. The `ast-grep` checks properly flagged these unsafe `window.open` patterns.
**Prevention:** Whenever using `window.open` with `_blank`, always append `'noopener,noreferrer'` as the third argument to ensure the newly opened tab runs in a separate process and cannot access the `window.opener` object of the originating page.

## 2024-05-13 - [Fix SQL Injection via Dynamic SQL Concatenation]
**Vulnerability:** The code used f-strings and dynamic string concatenation (`query += ...`) to construct raw SQL queries via `frappe.db.sql(query, ...)`. Even if the interpolated variables were strictly constrained by an allowlist (e.g., `doctype` restricted to `"CRM Lead"` or `"CRM Deal"`), this breaks structural security rules and sets a dangerous precedent for SQL injection.
**Learning:** Frappe's database query wrapper `frappe.db.sql` should never be passed dynamically concatenated raw strings. `ast-grep` rules specifically catch `frappe.db.sql(query + where, $$$)`.
**Prevention:** Always use explicit, hardcoded strings for raw SQL queries nested within `if/elif/else` blocks, and let the database wrapper parameterize the arguments (`%(param)s`).
## 2024-05-24 - [Fix SQL Injection via Dynamic SQL Concatenation]
**Vulnerability:** Similar to previous discoveries, `frappe.db.sql` was being called with dynamically evaluated variables (e.g., `frappe.db.sql(query, ...)` where `query` was assigned earlier as a string literal). While seemingly safe because the components were statically defined in branches, this practice violated AST Grep's structural security rule against SQL concatenation and risked leaving `query` unbound if a code path fell through, resulting in a 500 error and stack trace leakage.
**Learning:** `ast-grep` rules enforce explicit hardcoding of SQL strings directly inside `frappe.db.sql()`. Bypassing this by storing strings in variables before execution is not permitted. Additionally, failing to handle fallback branches can lead to `UnboundLocalError`, causing the app to crash and expose stack traces (CWE-209).
**Prevention:** Always place raw SQL string literals explicitly inside the `frappe.db.sql()` function call and ensure all conditional branches correctly resolve or safely fallback without executing undefined variables.
## 2024-06-28 - [Overly Permissive CORS/CSRF Configuration]
**Vulnerability:** The API Gateway configuration used an overly permissive CSRF allowed-origin wildcard (`https://*.azurewebsites.net`).
**Learning:** This is dangerous because it allows subdomain takeover or bypasses.
**Prevention:** Avoid overly permissive wildcards. Restrict allowed origins to specific known domains or use environment variables like `${FRONTEND_URL:}`.
