import { PREVIEW_CSRF_COOKIE } from "./preview-identity.ts";

export interface PreviewRequest {
  readonly method: string;
  readonly url: string;
}

export interface PreviewContext {
  readonly spriteName: string;
  readonly now: string;
}

export type PreviewDecision =
  | {
      readonly _tag: "Empty";
      readonly status: number;
      readonly headers: Readonly<Record<string, string>>;
    }
  | {
      readonly _tag: "Json";
      readonly status: number;
      readonly body: unknown;
      readonly headers: Readonly<Record<string, string>>;
    }
  | { readonly _tag: "Html"; readonly status: number; readonly body: string }
  | { readonly _tag: "File"; readonly relativePath: string }
  | { readonly _tag: "Spa" }
  | { readonly _tag: "Reject" };

const NO_STORE = {
  "cache-control": "no-store",
  "x-content-type-options": "nosniff",
  "x-opencrm-preview": "stub",
} as const;

const COLLECTIONS = new Set([
  "leads",
  "organizations",
  "contacts",
  "deals",
  "quotes",
  "activities",
  "accounts",
  "products",
  "price-books",
]);

const LEAD_STATS = {
  NEW: 0,
  CONTACTED: 0,
  QUALIFIED: 0,
  UNQUALIFIED: 0,
  CONVERTED: 0,
  LOST: 0,
} as const;

const EMPTY_PAGE = {
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
  hasPrevious: false,
} as const;

const OAUTH_HTML =
  "<!doctype html><html><head><meta charset=\"utf-8\"><title>Preview sign-in</title></head><body><p>This preview Sprite has no identity provider. API stubs are read-only.</p></body></html>";

/**
 * Route one request. API stubs never persist and never echo the request path
 * into HTML. Static files are a relative path under the packed `ui/` tree.
 */
export function routePreview(request: PreviewRequest, context: PreviewContext): PreviewDecision {
  const method = request.method.toUpperCase();
  const rawPath = stripQueryAndHash(request.url);
  if (hasUnsafePath(rawPath)) return { _tag: "Reject" };
  let pathname: string;
  try {
    pathname = new URL(rawPath, "http://preview.local").pathname;
  } catch {
    return { _tag: "Reject" };
  }

  if (pathname === "/api/v1/csrf") {
    if (method === "GET" || method === "HEAD") {
      return {
        _tag: "Empty",
        status: 204,
        headers: { ...NO_STORE, "set-cookie": PREVIEW_CSRF_COOKIE },
      };
    }
    return readOnly(context.now);
  }

  if (pathname === "/api/v1/preview") {
    if (method === "GET" || method === "HEAD") {
      return json(200, envelope(context, { preview: true, spriteName: context.spriteName, readOnly: true }));
    }
    return readOnly(context.now);
  }

  if (pathname === "/logout") {
    if (method === "POST" || method === "GET" || method === "HEAD") {
      return { _tag: "Empty", status: 204, headers: NO_STORE };
    }
    return readOnly(context.now);
  }

  if (pathname === "/oauth2" || pathname.startsWith("/oauth2/") || pathname.startsWith("/login/oauth2")) {
    if (method === "GET" || method === "HEAD" || method === "POST") {
      return { _tag: "Html", status: 200, body: OAUTH_HTML };
    }
    return readOnly(context.now);
  }

  if (pathname === "/api" || pathname.startsWith("/api/")) {
    if (method === "OPTIONS") {
      return { _tag: "Empty", status: 204, headers: NO_STORE };
    }
    if (method !== "GET" && method !== "HEAD") return readOnly(context.now);
    return apiGet(pathname, context);
  }

  return staticDecision(pathname);
}

export function contentTypeForRelative(relativePath: string): string {
  const extension = relativePath.slice(relativePath.lastIndexOf(".") + 1).toLowerCase();
  switch (extension) {
    case "html":
      return "text/html; charset=utf-8";
    case "js":
    case "mjs":
      return "text/javascript; charset=utf-8";
    case "css":
      return "text/css; charset=utf-8";
    case "json":
    case "map":
      return "application/json; charset=utf-8";
    case "svg":
      return "image/svg+xml";
    case "png":
      return "image/png";
    case "jpg":
    case "jpeg":
      return "image/jpeg";
    case "gif":
      return "image/gif";
    case "webp":
      return "image/webp";
    case "ico":
      return "image/x-icon";
    case "woff":
      return "font/woff";
    case "woff2":
      return "font/woff2";
    case "txt":
      return "text/plain; charset=utf-8";
    case "wasm":
      return "application/wasm";
    default:
      return "application/octet-stream";
  }
}

function stripQueryAndHash(url: string): string {
  const hashless = url.split("#")[0] ?? url;
  return hashless.split("?")[0] ?? hashless;
}

function hasUnsafePath(url: string): boolean {
  const lower = url.toLowerCase();
  return (
    url.includes("\\") ||
    url.includes("\0") ||
    url.includes("..") ||
    lower.includes("%2e") ||
    lower.includes("%5c") ||
    lower.includes("%00")
  );
}

function apiGet(pathname: string, context: PreviewContext): PreviewDecision {
  const parts = pathname.split("/").filter((part) => part.length > 0);
  if (parts[0] !== "api" || parts[1] !== "v1" || parts[2] === undefined) {
    return notFound(context.now);
  }
  const collection = parts[2];
  if (!COLLECTIONS.has(collection)) return notFound(context.now);
  if (parts.length === 3) {
    return json(200, envelope(context, [], EMPTY_PAGE));
  }
  if (parts.length === 4 && parts[3] === "stats") {
    return json(200, envelope(context, collection === "leads" ? LEAD_STATS : {}));
  }
  return notFound(context.now);
}

function staticDecision(pathname: string): PreviewDecision {
  let decoded: string;
  try {
    decoded = decodeURIComponent(pathname);
  } catch {
    return { _tag: "Reject" };
  }
  if (decoded.includes("\0") || decoded.includes("\\")) return { _tag: "Reject" };
  const trimmed = decoded.replace(/^\/+/, "");
  if (trimmed === "" || trimmed.endsWith("/")) return { _tag: "Spa" };
  const segments = trimmed.split("/");
  if (segments.some((segment) => segment === "" || segment === "." || segment === "..")) {
    return { _tag: "Reject" };
  }
  const leaf = segments[segments.length - 1];
  if (leaf === undefined || !leaf.includes(".")) return { _tag: "Spa" };
  return { _tag: "File", relativePath: segments.join("/") };
}

function envelope(context: PreviewContext, data: unknown, pagination?: typeof EMPTY_PAGE): unknown {
  return {
    success: true,
    message: "preview stub",
    data,
    timestamp: context.now,
    ...(pagination === undefined ? {} : { pagination }),
  };
}

function json(status: number, body: unknown): PreviewDecision {
  return { _tag: "Json", status, body, headers: NO_STORE };
}

function readOnly(now: string): PreviewDecision {
  return {
    _tag: "Json",
    status: 405,
    headers: { ...NO_STORE, allow: "GET, HEAD, OPTIONS" },
    body: {
      success: false,
      message: "Preview stubs are read-only",
      errorCode: "PREVIEW_STUB_READ_ONLY",
      timestamp: now,
    },
  };
}

function notFound(now: string): PreviewDecision {
  return {
    _tag: "Json",
    status: 404,
    headers: NO_STORE,
    body: {
      success: false,
      message: "Preview stub has no record",
      errorCode: "PREVIEW_STUB_NOT_FOUND",
      timestamp: now,
    },
  };
}
