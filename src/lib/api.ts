const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string | undefined;

function isSameOriginStorefrontHost(hostname: string) {
  const host = hostname.toLowerCase();
  return host === "minisnmore.com" || host.endsWith(".minisnmore.com");
}

export const AUTH_TOKEN_KEY = "mnm_backend_jwt";

type RequestOptions = RequestInit & {
  auth?: boolean;
  unwrap?: boolean;
};

export class ApiError extends Error {
  status: number;
  body: unknown;

  constructor(message: string, status: number, body: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

function getApiBaseUrl() {
  const configuredBaseUrl = API_BASE_URL?.replace(/\/+$/, "") ?? "";

  // Nginx exposes the API on the same public host as the storefront. Keep
  // browser requests same-origin even if a bundle is accidentally built with
  // another environment's VITE_API_BASE_URL.
  if (
    typeof window !== "undefined" &&
    isSameOriginStorefrontHost(window.location.hostname)
  ) {
    return "";
  }

  return configuredBaseUrl;
}

function networkErrorMessage(path: string, error: unknown) {
  const base = getApiBaseUrl();
  const target = base ? `${base}${path.startsWith("/") ? path : `/${path}`}` : path;
  const hint = base
    ? "Check that the API gateway is running (dev profile, port 8080) and VITE_API_BASE_URL points at it."
    : "Check that the API gateway is running on port 8080 (the Vite dev proxy forwards /api there).";
  const detail = error instanceof Error && error.message ? ` (${error.message})` : "";
  return `Cannot reach the API at ${target}. ${hint}${detail}`;
}

function buildUrl(path: string) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${getApiBaseUrl()}${normalizedPath}`;
}

export function getAuthToken() {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(AUTH_TOKEN_KEY);
}

export function setAuthToken(token: string | null) {
  if (typeof window === "undefined") return;
  if (token) window.localStorage.setItem(AUTH_TOKEN_KEY, token);
  else window.localStorage.removeItem(AUTH_TOKEN_KEY);
}

function authHeaders(auth?: boolean) {
  const headers = new Headers();
  if (auth) {
    const token = getAuthToken();
    if (token) headers.set("Authorization", `Bearer ${token}`);
  }
  return headers;
}

async function parseBody(response: Response) {
  const contentType = response.headers.get("content-type") ?? "";
  if (response.status === 204) return null;
  if (contentType.includes("json")) return response.json();
  return response.text();
}

const FIELD_LABELS: Record<string, string> = {
  slug: "Slug",
  name: "Name",
  title: "Title",
  content: "Content",
  body: "Body",
  coverUrl: "Cover image",
  imageUrl: "Image",
  linkUrl: "Link URL",
  brandName: "Brand name",
  gstRate: "GST rate",
  shippingStandardInr: "Standard shipping",
  shippingExpressInr: "Express shipping",
  freeShippingMinInr: "Free shipping threshold",
  pricesIncludeGst: "Product prices include GST",
  description: "Description",
  sku: "SKU",
  price: "Selling price (₹)",
  stock: "Stock",
  fabricType: "Fabric type",
  detailsKicker: "Details card label",
  compareAtPrice: "MRP (₹)",
  ageMinMonths: "Age min (months)",
  ageMaxMonths: "Age max (months)",
  sortOrder: "Sort order",
  sizesJson: "Sizes",
  colorsJson: "Colors",
  imagesJson: "Images",
  badge: "Badge",
  featured: "Featured",
  trending: "Trending",
  newArrival: "New arrival",
  bestSeller: "Best seller",
  active: "Active",
  published: "Published",
  publishedAt: "Published date",
  excerpt: "Excerpt",
  subtitle: "Subtitle",
  ctaLabel: "CTA label",
  discountType: "Discount type",
  expiresAt: "Expiry date",
  sectionKey: "Section key",
  pageKey: "Page key",
  visible: "Visible",
  legalEntityName: "Legal entity name",
  logoUrl: "Logo",
  logoHorizontalUrl: "Horizontal logo",
  contactEmail: "Contact email",
  contactPhone: "Contact phone",
  careLabel: "Care label",
  whatsappNumber: "WhatsApp number",
  instagramUrl: "Instagram URL",
  facebookUrl: "Facebook URL",
  youtubeUrl: "YouTube URL",
  twitterUrl: "Twitter URL",
  footerTagline: "Footer tagline",
  footerCopyright: "Footer copyright",
  ecatalogueUrl: "E-catalogue URL",
  ecatalogueLabel: "E-catalogue label",
  orgId: "Zoho organisation ID",
  clientId: "Zoho client ID",
  clientSecret: "Zoho client secret",
  refreshToken: "Zoho refresh token",
  dataCenter: "Zoho data centre",
  categoryId: "Category",
  fabric_type: "Fabric type",
  details_kicker: "Details card label",
  compare_at_price: "MRP (₹)",
  age_min_months: "Age min (months)",
  age_max_months: "Age max (months)",
  sort_order: "Sort order",
  cover_url: "Cover image",
  image_url: "Image",
  link_url: "Link URL",
  cta_label: "CTA label",
  discount_type: "Discount type",
  discount_value: "Discount value",
  min_order: "Minimum order",
  max_discount: "Maximum discount",
  bogo_buy_qty: "BOGO buy quantity",
  bogo_get_qty: "BOGO get quantity",
  expires_at: "Expiry date",
  section_key: "Section key",
  page_key: "Page key",
  brand_name: "Brand name",
  contact_email: "Contact email",
  contact_phone: "Contact phone",
  gst_rate: "GST rate",
  shipping_standard_inr: "Standard shipping",
  shipping_express_inr: "Express shipping",
  free_shipping_min_inr: "Free shipping threshold",
  prices_include_gst: "Product prices include GST",
  org_id: "Zoho organisation ID",
  client_id: "Zoho client ID",
  client_secret: "Zoho client secret",
  refresh_token: "Zoho refresh token",
  code: "Code",
  discountValue: "Discount value",
  maxDiscount: "Maximum discount",
  email: "Email",
  phone: "Contact number",
  address: "Address",
  comment: "Comments",
  login: "Login",
  password: "Password",
  username: "Email",
  data_center: "Zoho data centre",
};

const GENERIC_HTTP_TITLES = new Set([
  "bad request",
  "unauthorized",
  "forbidden",
  "not found",
  "conflict",
  "internal server error",
  "method argument not valid",
  "service unavailable",
  "too many requests",
  "payload too large",
  "duplicate value",
  "linked records exist",
  "invalid data",
]);

const GENERIC_DETAIL_TEXT = new Set([
  "this change conflicts with existing data.",
  "this change conflicts with existing data. please review and try again.",
  "this value is already in use. please choose a different one.",
  "a required field was missing. check all required fields and try again.",
  "one of the values is too long for its field. shorten the text and try again.",
  "failure during data access",
  "unexpected runtime exception",
  "unable to convert http message",
]);

function humanizeFieldName(field: string) {
  return field
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/_/g, " ")
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function humanizeConstraintMessage(message: string) {
  const trimmed = message.trim();
  const lower = trimmed.toLowerCase();
  const replacements: Record<string, string> = {
    "must not be null": "is required",
    "must not be blank": "is required",
    "must not be empty": "is required",
    "may not be null": "is required",
    "may not be blank": "is required",
    "may not be empty": "is required",
  };
  if (replacements[lower]) return replacements[lower];

  const sizeAtLeast = lower.match(/^size must be between (\d+) and (\d+)$/);
  if (sizeAtLeast) return `must be between ${sizeAtLeast[1]} and ${sizeAtLeast[2]} characters`;

  const minSize = lower.match(/^size must be at least (\d+)$/);
  if (minSize) return `must be at least ${minSize[1]} characters`;

  const maxSize = lower.match(/^size must be at most (\d+)$/);
  if (maxSize) return `must be at most ${maxSize[1]} characters`;

  return trimmed;
}

function formatFieldErrorLine(field: string, message: string) {
  const label = FIELD_LABELS[field] ?? humanizeFieldName(field);
  const trimmed = message.trim();
  if (!trimmed) return `${label} is required`;

  const lowerTrimmed = trimmed.toLowerCase();
  const lowerLabel = label.toLowerCase();
  if (lowerTrimmed.startsWith(lowerLabel)) return trimmed;
  if (/^[A-Z]/.test(trimmed) && /\s/.test(trimmed) && trimmed.length >= 24) return trimmed;

  return `${label}: ${humanizeConstraintMessage(trimmed)}`;
}

function isUnsafeErrorText(text: string) {
  return (
    /jhipster|problem-with-message|exception|stack|org\.|java\.|sql|jdbc|hibernate|trace|failure during data access/i.test(
      text,
    ) || /^(\d{3}\s+[A-Z_]+)/.test(text)
  );
}

function sanitizeRazorpayRefundDetail(detail: string): string | null {
  const trimmed = detail.trim();
  if (!trimmed) return null;

  const jsonStart = trimmed.indexOf("{");
  const payload = jsonStart >= 0 ? trimmed.slice(jsonStart) : trimmed;
  if (!payload.startsWith("{")) return null;

  try {
    const parsed = JSON.parse(payload) as { error?: { description?: string; code?: string } };
    const description = parsed.error?.description?.trim();
    if (!description) return null;

    const normalized = description.toLowerCase();
    if (normalized.includes("enough balance") || normalized.includes("insufficient balance")) {
      return (
        "Refund could not be processed because your Razorpay account does not have enough balance. " +
        "Add funds in the Razorpay Dashboard or wait for recent payments to settle, then try again."
      );
    }
    if (normalized.includes("already been refunded") || normalized.includes("fully refunded")) {
      return "This payment has already been refunded.";
    }
    if (normalized.includes("not captured")) {
      return "Refund cannot be issued because the payment has not been captured yet.";
    }
    return description;
  } catch {
    return null;
  }
}

function sanitizeProblemDetail(detail: string): string | null {
  const trimmed = detail.trim();
  if (!trimmed) return null;

  const razorpayMessage = sanitizeRazorpayRefundDetail(trimmed);
  if (razorpayMessage) return razorpayMessage;

  const withoutHttpPrefix = trimmed.replace(/^Razorpay refund failed:\s*HTTP\s+\d+\s*/i, "").trim();
  if (withoutHttpPrefix && withoutHttpPrefix !== trimmed) {
    const fromStripped = sanitizeRazorpayRefundDetail(withoutHttpPrefix);
    if (fromStripped) return fromStripped;
  }

  return null;
}

function isGenericHttpTitle(title: string) {
  return GENERIC_HTTP_TITLES.has(title.trim().toLowerCase());
}

function isGenericDetail(text: string) {
  const normalized = text.trim().toLowerCase().replace(/\.+$/, "");
  for (const entry of GENERIC_DETAIL_TEXT) {
    if (entry.replace(/\.+$/, "") === normalized) return true;
  }
  return false;
}

export function statusFallbackMessage(status: number) {
  const fallbackByStatus: Record<number, string> = {
    400: "Please check the entered details and try again.",
    401: "Authentication failed. Please sign in again.",
    403: "You do not have permission to perform this action.",
    404: "Requested data was not found.",
    409: "This action conflicts with existing data. Please review and try again.",
    413: "Uploaded file is too large.",
    429: "Too many requests. Please wait a moment and try again.",
    500: "Something went wrong on the server. Please try again.",
    502: "Server is temporarily unavailable. Please try again.",
    503: "Service is temporarily unavailable. Please try again.",
    504: "Request timed out. Please try again.",
  };
  return fallbackByStatus[status] ?? "Request could not be completed. Please try again.";
}

export function extractFieldErrorLines(body: unknown): string[] {
  if (!body || typeof body !== "object") return [];
  const errors = (body as Record<string, unknown>).fieldErrors;
  if (!Array.isArray(errors) || errors.length === 0) return [];

  return errors
    .map((entry) => {
      if (!entry || typeof entry !== "object") return null;
      const field = (entry as Record<string, unknown>).field;
      const message = (entry as Record<string, unknown>).message;
      if (typeof field !== "string" || typeof message !== "string" || !message.trim()) return null;
      return formatFieldErrorLine(field, message);
    })
    .filter((part): part is string => !!part);
}

/** Spring WebFlux often embeds the real reason as: 400 BAD_REQUEST "Human message" */
export function extractEmbeddedProblemReason(detail: string): string | null {
  const trimmed = detail.trim();
  if (!trimmed) return null;
  const embedded = trimmed.match(/^\d{3}\s+[\w\s]+\s+"((?:\\.|[^"\\])*)"/);
  if (!embedded?.[1]) return null;
  return embedded[1].replace(/\\"/g, '"').replace(/\\\\/g, "\\");
}

function safeProblemText(value: unknown) {
  if (typeof value !== "string") return null;
  const sanitized = value.trim();
  if (!sanitized) return null;

  const razorpaySanitized = sanitizeProblemDetail(sanitized);
  if (razorpaySanitized) return razorpaySanitized;

  if (isUnsafeErrorText(sanitized)) return null;
  if (isGenericHttpTitle(sanitized) || isGenericDetail(sanitized)) return null;
  return sanitized;
}

function fieldErrorsMessage(body: unknown): string | null {
  const lines = extractFieldErrorLines(body);
  return lines.length ? lines.join("\n") : null;
}

export function formatProblemBody(status: number, body: unknown): string {
  if (typeof body === "string") {
    const text = body.trim();
    if (text && !isUnsafeErrorText(text) && status < 500 && !isGenericHttpTitle(text)) return text;
    return statusFallbackMessage(status);
  }

  if (body && typeof body === "object") {
    const record = body as Record<string, unknown>;
    const validationMessage = fieldErrorsMessage(record);
    if (validationMessage) return validationMessage;

    if (typeof record.detail === "string") {
      const sanitized = sanitizeProblemDetail(record.detail);
      if (sanitized) return sanitized;

      const embedded = extractEmbeddedProblemReason(record.detail);
      if (embedded) return embedded;
    }

    const detail = safeProblemText(record.detail);
    const title = safeProblemText(record.title);
    if (detail) return detail;
    if (title) return title;
  }

  return statusFallbackMessage(status);
}

function errorMessage(status: number, body: unknown) {
  return formatProblemBody(status, body);
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { auth, unwrap = true, headers: initHeaders, body, ...init } = options;
  const headers = authHeaders(auth);
  new Headers(initHeaders).forEach((value, key) => headers.set(key, value));

  const hasBody = body !== undefined && body !== null;
  if (hasBody && !(body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  let response: Response;
  try {
    const method = (init.method ?? "GET").toUpperCase();
    response = await fetch(buildUrl(path), {
      ...init,
      cache: init.cache ?? (method === "GET" ? "no-store" : undefined),
      headers,
      body:
        hasBody && !(body instanceof FormData) && typeof body !== "string"
          ? JSON.stringify(body)
          : body,
    });
  } catch (error) {
    throw new Error(networkErrorMessage(path, error));
  }
  const parsed = await parseBody(response);

  if (!response.ok) {
    throw new ApiError(errorMessage(response.status, parsed), response.status, parsed);
  }

  if (
    unwrap &&
    parsed &&
    typeof parsed === "object" &&
    "success" in (parsed as Record<string, unknown>) &&
    "data" in (parsed as Record<string, unknown>)
  ) {
    return (parsed as { data: T }).data;
  }

  return parsed as T;
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: "POST", body: body as BodyInit }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: "PUT", body: body as BodyInit }),
  delete: <T>(path: string, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: "DELETE" }),
};

export function backendOAuthUrl(provider: "google" | "apple" | "facebook") {
  return buildUrl(`/oauth2/authorization/${provider}`);
}
