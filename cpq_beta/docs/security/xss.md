# XSS and HTML Rendering Policy

## Policy
- Do not return raw HTML from backend API responses unless content is sanitized and explicitly trusted.
- Avoid `v-html` for untrusted or user-controlled data in Vue templates.
- Prefer structured data (text, URLs, enum/icon keys) over HTML snippets.

## Login Provider Icons
- Backend should return `icon_url` only when URL scheme is `http` or `https`.
- Frontend should render provider icons with `<img :src="icon_url">`, not `v-html`.

## Unsafe Patterns
- `v-html="..."` with data from API or database fields.
- Backend string templates that embed HTML from user-configurable fields.

## Safer Alternatives
- Use text rendering or Vue components.
- Whitelist URL schemes and trusted hosts where possible.
- Sanitize rich text before rendering if HTML support is required.
