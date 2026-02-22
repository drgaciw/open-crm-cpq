# Frontend Setup

## Requirements
- Node + Yarn
- Backend site running (usually on `:8000`)

## Install and Run
1. Install frontend dependencies:
```bash
cd frontend
yarn
```
2. Start dev server:
```bash
yarn dev
```

## Build
```bash
cd frontend
yarn build
```

The build copies `frontend` HTML entry output into:
- `crm/public/frontend/index.html`
- `crm/www/crm.html`

## Notes
- Vite dev server defaults to `:8080`.
- Proxy/base URL behavior is defined in `frontend/vite.config.js`.
