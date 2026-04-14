## 2024-04-14 - Replace unsafe dynamic code execution in SectionFields.vue
**Vulnerability:** Usage of `new Function` to evaluate arbitrary javascript strings for `depends_on` functionality in `cpq_beta/frontend/src/components/SectionFields.vue`. This behaves like `eval()` and could allow Cross-Site Scripting (XSS) if expressions contain untrusted input.
**Learning:** Evaluating dynamic scripts in a frontend application can expose the host environment to malicious code execution.
**Prevention:** Always use a sandboxed JS interpreter like `sval` instead of `new Function()` or `eval()` to securely evaluate dynamic expressions, explicitly limiting access to safe global objects.
