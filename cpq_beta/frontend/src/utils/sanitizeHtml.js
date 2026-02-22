const DEFAULT_ALLOWED_TAGS = [
  'a',
  'b',
  'blockquote',
  'br',
  'code',
  'div',
  'em',
  'i',
  'li',
  'ol',
  'p',
  'pre',
  's',
  'span',
  'strong',
  'u',
  'ul',
]

const DEFAULT_BLOCKED_TAGS = [
  'base',
  'button',
  'embed',
  'form',
  'iframe',
  'input',
  'link',
  'math',
  'meta',
  'object',
  'script',
  'select',
  'style',
  'svg',
  'textarea',
]

const DEFAULT_ALLOWED_ATTRIBUTES = {
  '*': ['aria-label', 'class', 'title'],
  a: ['href', 'rel', 'target'],
}

const SAFE_TARGETS = new Set(['_blank', '_parent', '_self', '_top'])
const SAFE_PROTOCOLS = new Set(['http:', 'https:', 'mailto:', 'tel:'])
const TEXT_NODE = 3
const ELEMENT_NODE = 1

function toLowerSet(values = []) {
  return new Set(values.map((value) => String(value).toLowerCase()))
}

function normalizeAllowedAttributes(attributes = {}) {
  const normalized = {}

  Object.entries(attributes).forEach(([key, value]) => {
    normalized[String(key).toLowerCase()] = toLowerSet(value)
  })

  return normalized
}

function escapeHtml(value) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function isSafeUrl(url) {
  if (!url) return false

  const trimmed = String(url).trim()
  if (!trimmed || trimmed.startsWith('//')) return false

  if (
    trimmed.startsWith('#') ||
    trimmed.startsWith('/') ||
    trimmed.startsWith('./') ||
    trimmed.startsWith('../')
  ) {
    return true
  }

  const baseOrigin =
    typeof window !== 'undefined' ? window.location.origin : 'https://localhost'

  try {
    const parsed = new URL(trimmed, baseOrigin)
    return SAFE_PROTOCOLS.has(parsed.protocol)
  } catch {
    return false
  }
}

function sanitizeNode(node, doc, options) {
  if (node.nodeType === TEXT_NODE) {
    return doc.createTextNode(node.textContent || '')
  }

  if (node.nodeType !== ELEMENT_NODE) {
    return null
  }

  const tag = node.tagName.toLowerCase()

  if (options.blockedTags.has(tag)) {
    return null
  }

  if (!options.allowedTags.has(tag)) {
    const fragment = doc.createDocumentFragment()
    Array.from(node.childNodes).forEach((child) => {
      const sanitizedChild = sanitizeNode(child, doc, options)
      if (sanitizedChild) {
        fragment.appendChild(sanitizedChild)
      }
    })
    return fragment
  }

  const cleanNode = doc.createElement(tag)
  const tagAllowedAttributes = options.allowedAttributes[tag] || new Set()
  const globalAllowedAttributes = options.allowedAttributes['*'] || new Set()

  Array.from(node.attributes).forEach((attribute) => {
    const name = attribute.name.toLowerCase()
    const value = attribute.value

    if (name.startsWith('on') || name === 'style') return

    if (
      !tagAllowedAttributes.has(name) &&
      !globalAllowedAttributes.has(name)
    ) {
      return
    }

    if ((name === 'href' || name === 'src') && !isSafeUrl(value)) {
      return
    }

    if (name === 'target' && !SAFE_TARGETS.has(value)) {
      return
    }

    cleanNode.setAttribute(name, value)
  })

  if (tag === 'a' && cleanNode.getAttribute('target') === '_blank') {
    const rel = new Set(
      (cleanNode.getAttribute('rel') || '')
        .split(/\s+/)
        .filter(Boolean)
        .map((value) => value.toLowerCase())
    )
    rel.add('noopener')
    rel.add('noreferrer')
    cleanNode.setAttribute('rel', Array.from(rel).join(' '))
  }

  Array.from(node.childNodes).forEach((child) => {
    const sanitizedChild = sanitizeNode(child, doc, options)
    if (sanitizedChild) {
      cleanNode.appendChild(sanitizedChild)
    }
  })

  return cleanNode
}

export function sanitizeHtml(input, customOptions = {}) {
  if (input === null || input === undefined) return ''

  const source = String(input)
  if (!source) return ''

  if (typeof DOMParser === 'undefined') {
    return escapeHtml(source)
  }

  const parser = new DOMParser()
  const parsed = parser.parseFromString(`<body>${source}</body>`, 'text/html')

  const options = {
    allowedTags: toLowerSet(customOptions.allowedTags || DEFAULT_ALLOWED_TAGS),
    blockedTags: toLowerSet(customOptions.blockedTags || DEFAULT_BLOCKED_TAGS),
    allowedAttributes: normalizeAllowedAttributes(
      customOptions.allowedAttributes || DEFAULT_ALLOWED_ATTRIBUTES
    ),
  }

  const wrapper = parsed.createElement('div')
  Array.from(parsed.body.childNodes).forEach((child) => {
    const sanitizedChild = sanitizeNode(child, parsed, options)
    if (sanitizedChild) {
      wrapper.appendChild(sanitizedChild)
    }
  })

  return wrapper.innerHTML
}
