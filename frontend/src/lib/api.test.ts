import { beforeEach, describe, expect, it, vi } from "vitest";

async function loadApi(baseUrl = "https://api.example.com/") {
  vi.resetModules();
  vi.stubEnv("VITE_API_BASE_URL", baseUrl);
  return import("./api");
}

describe("api client", () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it("builds JSON requests, unwraps backend envelopes, and attaches auth tokens", async () => {
    const { api, setAuthToken } = await loadApi();
    setAuthToken("jwt-token");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: { id: 12 } }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await expect(api.post("/api/v1/products", { name: "Romper" }, { auth: true })).resolves.toEqual({
      id: 12,
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "https://api.example.com/api/v1/products",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ name: "Romper" }),
      }),
    );
    const headers = fetchMock.mock.calls[0][1]?.headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer jwt-token");
    expect(headers.get("Content-Type")).toBe("application/json");
  });

  it("returns raw payloads when unwrap is disabled", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: { id: 1 } }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await expect(api.get("/api/account", { unwrap: false })).resolves.toEqual({
      success: true,
      data: { id: 1 },
    });
  });

  it("disables browser HTTP caching for GET requests", async () => {
    const { api } = await loadApi("https://api.example.com");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await api.get("/api/v1/products");

    expect(fetchMock).toHaveBeenCalledWith(
      "https://api.example.com/api/v1/products",
      expect.objectContaining({ method: "GET", cache: "no-store" }),
    );
  });

  it("normalizes API errors from problem details", async () => {
    const { ApiError, api } = await loadApi();
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ detail: "Invalid credentials" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await expect(api.post("/api/authenticate", {})).rejects.toMatchObject({
      name: "ApiError",
      message: "Invalid credentials",
      status: 401,
    } satisfies Partial<InstanceType<typeof ApiError>>);
  });

  it("uses status fallback for non-json API errors", async () => {
    const { api } = await loadApi();
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("plain failure", {
        status: 500,
        headers: { "Content-Type": "text/plain" },
      }),
    );

    await expect(api.get("/api/fails")).rejects.toThrow("Something went wrong on the server. Please try again.");
  });

  it("falls back to a status message when error bodies are empty", async () => {
    const { api } = await loadApi();
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("", { status: 503 }));

    await expect(api.get("/api/fails")).rejects.toThrow("Service is temporarily unavailable. Please try again.");
  });

  it("passes through form data and supports put/delete helpers", async () => {
    const { api } = await loadApi();
    const form = new FormData();
    form.set("file", new Blob(["x"]), "file.txt");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 204 }));

    await expect(api.put("/api/upload", form)).resolves.toBeNull();
    await expect(api.delete("/api/items/1")).resolves.toBeNull();

    const headers = fetchMock.mock.calls[0][1]?.headers as Headers;
    expect(headers.has("Content-Type")).toBe(false);
    expect(fetchMock).toHaveBeenLastCalledWith(
      "https://api.example.com/api/items/1",
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("wraps network failures with an actionable message", async () => {
    const { api } = await loadApi("");
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(api.get("/api/v1/admin/home-sections", { auth: true })).rejects.toThrow(
      "Cannot reach the API at /api/v1/admin/home-sections",
    );
  });

  it("falls back to same-origin API paths when the API base URL is missing", async () => {
    const { api } = await loadApi("");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ login: "admin" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await expect(api.get("/api/account", { unwrap: false })).resolves.toEqual({ login: "admin" });
    expect(fetchMock).toHaveBeenCalledWith("/api/account", expect.objectContaining({ method: "GET" }));
  });

  it("keeps deployed storefront API calls same-origin when a bundle has the wrong environment URL", async () => {
    const originalWindow = globalThis.window;
    vi.stubGlobal("window", {
      ...originalWindow,
      location: {
        ...originalWindow.location,
        hostname: "minisnmore.com",
      },
    });
    const { api, backendOAuthUrl } = await loadApi("https://uat.minisnmore.com");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await api.get("/api/v1/categories");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/categories",
      expect.objectContaining({ method: "GET" }),
    );
    expect(backendOAuthUrl("google")).toBe("/oauth2/authorization/google");
  });

  it("keeps UAT storefront API calls same-origin when a bundle has the production URL", async () => {
    const originalWindow = globalThis.window;
    vi.stubGlobal("window", {
      ...originalWindow,
      location: {
        ...originalWindow.location,
        hostname: "uat.minisnmore.com",
      },
    });
    const { api } = await loadApi("https://minisnmore.com");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await api.get("/api/authenticate");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/authenticate",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("builds backend OAuth URLs from the configured API origin", async () => {
    const { backendOAuthUrl } = await loadApi("https://uat.minisnmore.com/");
    expect(backendOAuthUrl("google")).toBe("https://uat.minisnmore.com/oauth2/authorization/google");
    expect(backendOAuthUrl("apple")).toBe("https://uat.minisnmore.com/oauth2/authorization/apple");
    expect(backendOAuthUrl("facebook")).toBe("https://uat.minisnmore.com/oauth2/authorization/facebook");
  });

  it("no-ops auth token helpers when window is unavailable", async () => {
    const win = globalThis.window;
    // @ts-expect-error test-only environment shim
    delete globalThis.window;
    const { getAuthToken, setAuthToken } = await loadApi();

    expect(getAuthToken()).toBeNull();
    expect(() => setAuthToken("server-token")).not.toThrow();
    expect(() => setAuthToken(null)).not.toThrow();

    globalThis.window = win;
  });

  it("removes stored auth tokens", async () => {
    const { AUTH_TOKEN_KEY, setAuthToken } = await loadApi();
    localStorage.setItem(AUTH_TOKEN_KEY, "token");

    setAuthToken(null);

    expect(localStorage.getItem(AUTH_TOKEN_KEY)).toBeNull();
  });

  it("maps JHipster problem details for storage and validation failures", async () => {
    const { ApiError, api } = await loadApi();
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 503,
          title: "Service Unavailable",
          detail: '503 SERVICE_UNAVAILABLE "S3 storage is not configured"',
          path: "/api/v1/admin/uploads/presign",
        }),
        { status: 503, headers: { "Content-Type": "application/problem+json" } },
      ),
    );

    await expect(
      api.post("/api/v1/admin/uploads/presign", { folder: "products", filename: "a.png" }),
    ).rejects.toSatisfy((error: unknown) => {
      expect(error).toMatchObject({ name: "ApiError", status: 503 });
      expect((error as InstanceType<typeof ApiError>).message).toBe(
        "S3 storage is not configured",
      );
      return true;
    });
  });

  it("does not attach authorization when auth is requested without a stored token", async () => {
    const { api } = await loadApi();
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await api.get("/api/v1/admin/products", { auth: true });

    const headers = fetchMock.mock.calls[0][1]?.headers as Headers;
    expect(headers.get("Authorization")).toBeNull();
  });

  it("maps field validation errors to readable messages", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          status: 400,
          title: "Method argument not valid",
          fieldErrors: [
            { objectName: "siteSetting", field: "shippingStandardInr", message: "must be greater than or equal to 0" },
            { objectName: "siteSetting", field: "gstRate", message: "must be less than or equal to 100" },
          ],
        }),
        { status: 400, headers: { "Content-Type": "application/problem+json" } },
      ),
    );

    await expect(api.put("/api/v1/admin/site-settings", {})).rejects.toThrow(
      "Standard shipping: must be greater than or equal to 0\nGST rate: must be less than or equal to 100",
    );
  });

  it("formats field labels for custom, blank, and prefixed validation messages", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          fieldErrors: [
            { field: "custom_field", message: "must not be null" },
            { field: "title", message: "" },
            { field: "body", message: "Body: must include at least 20 characters" },
            {
              field: "description",
              message: "Description must clearly explain the return policy for customers",
            },
          ],
        }),
        { status: 400, headers: { "Content-Type": "application/problem+json" } },
      ),
    );

    await expect(api.post("/api/v1/admin/policies", {})).rejects.toThrow(
      /Custom Field: is required[\s\S]*Body: must include at least 20 characters[\s\S]*Description must clearly explain/,
    );
  });

  it("keeps custom validation messages without duplicating the field label", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          status: 400,
          title: "Method argument not valid",
          fieldErrors: [{ objectName: "policy", field: "body", message: "Body must be at least 20 characters" }],
        }),
        { status: 400, headers: { "Content-Type": "application/problem+json" } },
      ),
    );

    await expect(api.post("/api/v1/admin/policies", {})).rejects.toThrow("Body must be at least 20 characters");
  });

  it("prefers alert titles when detail is generic", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          status: 409,
          title: "A policy page with this URL slug already exists.",
          detail: "This change conflicts with existing data.",
        }),
        { status: 409, headers: { "Content-Type": "application/json" } },
      ),
    );

    await expect(api.post("/api/v1/admin/policies", {})).rejects.toThrow(
      "A policy page with this URL slug already exists.",
    );
  });

  it("uses plain-text error bodies when present", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("Policy not found", {
        status: 404,
        headers: { "Content-Type": "text/plain" },
      }),
    );

    await expect(api.get("/api/v1/admin/policies/1")).rejects.toThrow("Policy not found");
  });

  it("normalizes paths and uses status-based user-safe messages", async () => {
    const { backendOAuthUrl, api } = await loadApi("https://api.example.com");
    expect(backendOAuthUrl("google")).toBe("https://api.example.com/oauth2/authorization/google");

    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ detail: "Validation failed" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      }),
    );
    await expect(api.get("api/bad")).rejects.toThrow("Validation failed");

    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ title: "Conflict" }), {
        status: 409,
        headers: { "Content-Type": "application/json" },
      }),
    );
    await expect(api.get("/api/conflict")).rejects.toThrow("This action conflicts with existing data. Please review and try again.");
  });

  it("uses alert titles when detail is missing", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          status: 400,
          title: "Login name already used!",
          message: "error.userexists",
          params: "userManagement",
        }),
        { status: 400, headers: { "Content-Type": "application/problem+json" } },
      ),
    );

    await expect(api.post("/api/register", {})).rejects.toThrow("Login name already used!");
  });

  it("prefers safe detail over generic http titles", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          status: 401,
          title: "Unauthorized",
          detail: "Bad credentials",
        }),
        { status: 401, headers: { "Content-Type": "application/json" } },
      ),
    );

    await expect(api.post("/api/authenticate", {})).rejects.toThrow("Bad credentials");
  });

  it("passes through safe business detail messages", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          status: 409,
          detail:
            "Cannot delete this category because 2 products are still assigned to it. Reassign or remove those products first.",
        }),
        { status: 409, headers: { "Content-Type": "application/json" } },
      ),
    );
    await expect(api.delete("/api/v1/admin/categories/4952")).rejects.toThrow(
      "Cannot delete this category because 2 products are still assigned to it. Reassign or remove those products first.",
    );
  });

  it("blocks jhipster/internal detail messages and falls back safely", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          status: 500,
          detail:
            "Failure during data access. ProblemDetailWithCause[type='https://www.jhipster.tech/problem/problem-with-message']",
        }),
        { status: 500, headers: { "Content-Type": "application/json" } },
      ),
    );
    await expect(api.get("/api/fails")).rejects.toThrow("Something went wrong on the server. Please try again.");
  });

  it("returns parsed JSON bodies that are not wrapped in success envelopes", async () => {
    const { api } = await loadApi();
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ id_token: "jwt-123" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await expect(api.post("/api/authenticate", {}, { unwrap: true })).resolves.toEqual({ id_token: "jwt-123" });
  });

  it("humanizes snake_case fields and blank constraint messages", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          fieldErrors: [
            { field: "gst_rate", message: "must not be blank" },
            { field: "sectionKey", message: "size must be at least 20" },
          ],
        }),
        { status: 400, headers: { "Content-Type": "application/json" } },
      ),
    );

    await expect(api.put("/api/v1/admin/site-settings", {})).rejects.toThrow(
      "GST rate: is required\nSection key: must be at least 20 characters",
    );
  });

  it("formats size-between validation messages with character counts", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          fieldErrors: [{ field: "title", message: "size must be between 2 and 50" }],
        }),
        { status: 400, headers: { "Content-Type": "application/json" } },
      ),
    );

    await expect(api.post("/api/v1/admin/policies", {})).rejects.toThrow(
      "Title: must be between 2 and 50 characters",
    );
  });

  it("ignores malformed field error entries during extraction", async () => {
    const { extractFieldErrorLines } = await loadApi();
    expect(
      extractFieldErrorLines({
        fieldErrors: [null, { field: 1, message: "bad" }, { field: "title", message: "   " }],
      }),
    ).toEqual([]);
  });

  it("keeps validation messages that already include the field label", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          fieldErrors: [
            { field: "title", message: "Title must be unique" },
            { field: "slug", message: "Slug: must match the pattern" },
          ],
        }),
        { status: 400, headers: { "Content-Type": "application/problem+json" } },
      ),
    );

    await expect(api.post("/api/v1/admin/policies", {})).rejects.toThrow(
      /Title must be unique[\s\S]*Slug: must match the pattern/,
    );
  });

  it("uses title-only problem bodies when detail is generic", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          title: "Conflict",
          detail: "This change conflicts with existing data.",
        }),
        { status: 409, headers: { "Content-Type": "application/json" } },
      ),
    );

    await expect(api.post("/api/v1/admin/policies", {})).rejects.toThrow(
      "This action conflicts with existing data. Please review and try again.",
    );
  });

  it("falls back to status messaging for empty problem objects", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({}), {
        status: 404,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await expect(api.get("/api/v1/admin/policies/404")).rejects.toThrow("Requested data was not found.");
  });

  it("exports helpers for field errors and unknown status fallbacks", async () => {
    const { extractEmbeddedProblemReason, extractFieldErrorLines, formatProblemBody, statusFallbackMessage } =
      await loadApi();

    expect(
      extractEmbeddedProblemReason(
        '400 BAD_REQUEST "Image type not accepted. Use JPG, PNG, WebP, or GIF only."',
      ),
    ).toBe("Image type not accepted. Use JPG, PNG, WebP, or GIF only.");

    expect(extractFieldErrorLines(null)).toEqual([]);
    expect(extractFieldErrorLines({ fieldErrors: [{ field: "fabricType", message: "must not be null" }] })).toEqual([
      "Fabric type: is required",
    ]);
    expect(extractFieldErrorLines({ fieldErrors: [{ field: "fooBar", message: "must not be null" }] })).toEqual([
      "Foo Bar: is required",
    ]);
    expect(statusFallbackMessage(418)).toBe("Request could not be completed. Please try again.");
    expect(formatProblemBody(409, {
      title: "This coupon code is already in use.",
      detail: "This change conflicts with existing data.",
    })).toBe("This coupon code is already in use.");
    expect(formatProblemBody(500, "Failure during data access")).toBe(
      "Something went wrong on the server. Please try again.",
    );
    expect(formatProblemBody(404, null)).toBe("Requested data was not found.");
    expect(
      formatProblemBody(400, {
        title: "Bad Request",
        detail: '400 BAD_REQUEST "Image is too large. Maximum size is 8 MB."',
      }),
    ).toBe("Image is too large. Maximum size is 8 MB.");
  });

  it("preserves label-prefixed and long-form validation messages", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          fieldErrors: [
            { field: "title", message: "title must be unique in the catalog" },
            { field: "slug", message: "Slug: must use lowercase letters only" },
            {
              field: "custom_field",
              message: "Something Completely Different That Starts Uppercase And Has Spaces",
            },
          ],
        }),
        { status: 400, headers: { "Content-Type": "application/problem+json" } },
      ),
    );

    await expect(api.post("/api/v1/admin/policies", {})).rejects.toThrow(
      /title must be unique in the catalog[\s\S]*Slug: must use lowercase letters only[\s\S]*Something Completely Different/,
    );
  });

  it("humanizes max-length validation messages", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          fieldErrors: [{ field: "title", message: "size must be at most 50" }],
        }),
        { status: 400, headers: { "Content-Type": "application/problem+json" } },
      ),
    );

    await expect(api.post("/api/v1/admin/policies", {})).rejects.toThrow(
      "Title: must be at most 50 characters",
    );
  });

  it("sanitizes Razorpay refund problem details via formatProblemBody", async () => {
    const { formatProblemBody } = await loadApi();

    expect(
      formatProblemBody(400, {
        detail:
          'Razorpay refund failed: {"error":{"description":"The account does not have enough balance to process this refund"}}',
      }),
    ).toBe(
      "Refund could not be processed because your Razorpay account does not have enough balance. " +
        "Add funds in the Razorpay Dashboard or wait for recent payments to settle, then try again.",
    );

    expect(
      formatProblemBody(400, {
        detail: '{"error":{"description":"Payment has already been refunded in full"}}',
      }),
    ).toBe("This payment has already been refunded.");

    expect(
      formatProblemBody(400, {
        detail: '{"error":{"description":"Refund cannot be issued because payment is not captured"}}',
      }),
    ).toBe("Refund cannot be issued because the payment has not been captured yet.");

    expect(
      formatProblemBody(400, {
        detail: '{"error":{"description":"Refund amount exceeds captured amount"}}',
      }),
    ).toBe("Refund amount exceeds captured amount");

    expect(
      formatProblemBody(400, {
        detail: 'Razorpay refund failed: {"error":{"description":"not valid json"',
      }),
    ).toBe('Razorpay refund failed: {"error":{"description":"not valid json"');

    expect(
      formatProblemBody(400, {
        detail:
          'Razorpay refund failed: HTTP 400 {"error":{"description":"Insufficient balance for refund"}}',
      }),
    ).toBe(
      "Refund could not be processed because your Razorpay account does not have enough balance. " +
        "Add funds in the Razorpay Dashboard or wait for recent payments to settle, then try again.",
    );
  });

  it("merges custom headers into api.post requests", async () => {
    const { api } = await loadApi("https://api.example.com");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: { ok: true } }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await api.post(
      "/api/account/reset-password/init",
      JSON.stringify("reset@example.com"),
      {
        unwrap: false,
        headers: { "Content-Type": "application/json", "X-Request-Id": "req-42" },
      },
    );

    const headers = fetchMock.mock.calls[0][1]?.headers as Headers;
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(headers.get("X-Request-Id")).toBe("req-42");
  });

  it("humanizes additional constraint message patterns", async () => {
    const { api } = await loadApi("https://api.example.com");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          fieldErrors: [
            { field: "price", message: "must be greater than 0" },
            { field: "qty", message: "must be less than or equal to 10" },
            { field: "name", message: "must match \"^[a-z]+$\"" },
          ],
        }),
        { status: 400, headers: { "Content-Type": "application/problem+json" } },
      ),
    );

    await expect(api.post("/api/v1/admin/products", {})).rejects.toThrow(
      "Selling price (₹): must be greater than 0\nQty: must be less than or equal to 10\nName: must match \"^[a-z]+$\"",
    );
  });
});
