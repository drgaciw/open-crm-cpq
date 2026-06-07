import { copyFileSync, mkdirSync } from 'node:fs'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const src = `${root}/../crm/public/frontend/index.html`
const dest = `${root}/../crm/www/crm.html`
mkdirSync(dirname(dest), { recursive: true })
copyFileSync(src, dest)
