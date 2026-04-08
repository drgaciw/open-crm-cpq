## 2024-06-03 - [Fix RCE/XSS in custom script evaluation]
**Vulnerability:** Evaluated dynamic JavaScript (like Custom Actions) in the frontend using `new Function()`, leading to potential XSS and RCE if the scripts are injected or manipulated.
**Learning:** `new Function()` in browser environments is equivalent to `eval()` and should never be used on untrusted user inputs.
**Prevention:** Always use a sandboxed JavaScript interpreter like `sval` to evaluate untrusted scripts on the frontend.
