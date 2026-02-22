# ast-grep Structural Checks

Run from repo root after configuring ast-grep MCP path to this workspace.

## Backend Checks
- Guest-exposed methods:
  - Pattern: `@frappe.whitelist(allow_guest=True)`
- Weak exception handling:
  - Pattern: `try:\n  $$$\nexcept:\n  $$$`
- Dynamic SQL concatenation:
  - Pattern: `frappe.db.sql(query + where, $$$)`
- Assertion-based request validation:
  - Pattern: `assert $COND`

## Frontend Checks
- Raw HTML sinks:
  - Search for `v-html`
- Dynamic code execution:
  - Pattern: `new Function($$$)`
- Window opening hardening:
  - Pattern: `window.open($URL, '_blank')`
- Deprecated clipboard API:
  - Pattern: `document.execCommand('copy')`

## Example MCP call
```json
{
  "name": "ast-grep",
  "arguments": {
    "lang": "python",
    "dir": "crm",
    "pattern": "try:\n  $$$\nexcept:\n  $$$"
  }
}
```
