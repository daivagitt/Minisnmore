import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import {
  ADMIN_NAME_MAX_LEN,
  PRODUCT_NAME_MAX_LEN,
  bulkSave,
  disambiguateProductImportSlugs,
  fromBannerDto,
  fromBlogPostDto,
  fromCategoryDto,
  fromCouponDto,
  fromHomeSectionDto,
  fromPageVisibilityDto,
  fromPolicyDto,
  fromProductDto,
  getProductValidationErrors,
  parseSpreadsheetImageUrls,
  formatLedgerlySyncFailures,
  formatLedgerlySyncSummary,
  getAdminSiteSettings,
  getSiteSettings,
  getLedgerlyIntegrationStatus,
  getLedgerlySettings,
  pullLedgerlyInventory,
  pullLedgerlyProducts,
  retryLedgerlyOrders,
  setLedgerlyPullProductsAutoCreateCategory,
  syncAllLedgerlyProducts,
  syncLedgerlyOrderByNumber,
  syncLedgerlyProduct,
  testLedgerlyConnection,
  listBanners,
  listCategories,
  listProducts,
  saveBanner,
  saveBlogPost,
  savePolicy,
  saveCategory,
  saveCoupon,
  saveProduct,
  saveSiteSettings,
  saveLedgerlySettings,
  uploadAdminImage,
} from "./backend-data";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
    api: {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

describe("spreadsheet image URLs", () => {
  it("parses multiple URLs from a single cell", () => {
    const urls = parseSpreadsheetImageUrls(
      "https://cdn.example.com/a.jpg | https://cdn.example.com/b.jpg",
    );
    expect(urls).toEqual(["https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg"]);
  });

  it("adds https for bare host paths and strips Excel quotes", () => {
    const urls = parseSpreadsheetImageUrls('"cdn.example.com/products/1.png"');
    expect(urls).toEqual(["https://cdn.example.com/products/1.png"]);
  });

  it("accepts public R2 URLs for storefront display", () => {
    const url =
      "https://pub-f227d5520be64479a318380ac346334d.r2.dev/uat/products/sample.png";
    expect(parseSpreadsheetImageUrls(url)).toEqual([url]);
  });

  it("ignores placeholder template URLs", () => {
    expect(parseSpreadsheetImageUrls("https://…/1.jpg")).toEqual([]);
  });
});

describe("backend data mappers", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("normalizes category, product, and blog DTOs into storefront rows", () => {
    expect(
      fromCategoryDto({
        id: 4,
        slug: "newborn",
        name: "Newborn",
        description: undefined,
        imageUrl: "/cat.jpg",
      }),
    ).toEqual({
      id: "4",
      slug: "newborn",
      name: "Newborn",
      description: null,
      image_url: "/cat.jpg",
      sort_order: null,
      active: true,
    });

    expect(
      fromProductDto({
        id: 9,
        slug: "romper",
        name: "Romper",
        price: 1299,
        compareAtPrice: "1499" as unknown as number,
        sizesJson: "[\"0-3m\"]",
        colorsJson: "not-json",
        imagesJson: "[\"/a.jpg\"]",
        newArrival: true,
        category: { id: 7, slug: "baby", name: "Baby" },
      }),
    ).toMatchObject({
      id: "9",
      slug: "romper",
      price: 1299,
      compare_at_price: 1499,
      currency: "INR",
      sizes: ["0-3m"],
      colors: [],
      images: ["/a.jpg"],
      new_arrival: true,
      category_id: "7",
      category_slug: "baby",
      category_name: "Baby",
      active: true,
    });

    expect(
      fromBlogPostDto({
        id: 3,
        slug: "care",
        title: "Care",
        published: true,
        publishedAt: "2026-05-23",
      }),
    ).toEqual({
      id: "3",
      slug: "care",
      title: "Care",
      excerpt: null,
      content: null,
      cover_url: null,
      published: true,
      published_at: "2026-05-23",
    });
  });

  it("trims blog post strings from API", () => {
    expect(
      fromBlogPostDto({
        slug: " hello-world ",
        title: " Hello ",
        excerpt: " Intro ",
        content: " Body ",
        coverUrl: " https://cdn.example/cover.jpg ",
      }),
    ).toMatchObject({
      slug: "hello-world",
      title: "Hello",
      excerpt: "Intro",
      content: "Body",
      cover_url: "https://cdn.example/cover.jpg",
    });
  });

  it("normalizes content, visibility, policy, and coupon DTOs", () => {
    expect(
      fromBannerDto({
        id: 2,
        title: "Hero",
        subtitle: undefined,
        imageUrl: "/hero.jpg",
        active: undefined,
      }),
    ).toMatchObject({ id: "2", subtitle: null, image_url: "/hero.jpg", active: true });

    expect(
      fromHomeSectionDto({
        id: 1,
        sectionKey: "hero",
        payloadJson: "{\"title\":\"Bloom\"}",
        enabled: false,
      }),
    ).toMatchObject({ id: "1", key: "hero", enabled: false, payload: { title: "Bloom" } });

    expect(fromPageVisibilityDto({ id: 5, pageKey: "shop", visible: true })).toEqual({
      id: "5",
      page_key: "shop",
      visible: true,
    });

    expect(fromPolicyDto({ id: 6, slug: "privacy", title: "Privacy" })).toEqual({
      id: "6",
      slug: "privacy",
      title: "Privacy",
      body: null,
    });

    expect(
      fromCouponDto({
        id: 8,
        code: "WELCOME10",
        discountType: "FLAT",
        discountValue: 100,
        minOrder: 500,
        active: true,
      }),
    ).toMatchObject({ id: "8", discount_type: "flat", max_discount: null });
  });
});

describe("backend data API calls", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("uses public endpoints for storefront lists and admin endpoints for admin lists", async () => {
    mockedApi.get.mockResolvedValueOnce([{ id: 1, slug: "cat", name: "Cat" }]);
    await expect(listCategories()).resolves.toHaveLength(1);
    expect(mockedApi.get).toHaveBeenCalledWith("/api/v1/categories");

    mockedApi.get.mockResolvedValueOnce([{ id: 2, slug: "admin-cat", name: "Admin" }]);
    await expect(listCategories(true)).resolves.toHaveLength(1);
    expect(mockedApi.get).toHaveBeenLastCalledWith("/api/v1/admin/categories", { auth: true });

    mockedApi.get.mockResolvedValueOnce([{ slug: "romper", name: "Romper", price: 10 }]);
    await expect(listProducts()).resolves.toHaveLength(1);
    expect(mockedApi.get).toHaveBeenLastCalledWith("/api/v1/products");

    mockedApi.get.mockResolvedValueOnce([{ title: "Banner" }]);
    await expect(listBanners()).resolves.toHaveLength(1);
    expect(mockedApi.get).toHaveBeenLastCalledWith("/api/v1/banners");
  });

  it("serializes create and update payloads for admin saves", async () => {
    mockedApi.post.mockResolvedValueOnce({ id: 11, slug: "cat", name: "Cat", active: true });
    await saveCategory({
      slug: "cat",
      name: "Cat",
      description: "",
      image_url: "",
      sort_order: 2,
      active: true,
    });
    expect(mockedApi.post).toHaveBeenCalledWith(
      "/api/v1/admin/categories",
      expect.objectContaining({ description: null, imageUrl: null, sortOrder: 2 }),
      { auth: true },
    );

    expect(() =>
      saveCategory({
        slug: "0000",
        name: "0000",
        description: "",
        image_url: "",
        sort_order: 0,
        active: true,
      }),
    ).toThrow(/letter/i);
    expect(mockedApi.post).toHaveBeenCalledTimes(1);

    mockedApi.put.mockResolvedValueOnce({ id: 12, slug: "romper", name: "Romper", price: 10 });
    await saveProduct({
      id: "12",
      slug: "romper",
      name: "Romper",
      sku: "ROMPER-12",
      fabric_type: "Cotton",
      description: "Soft organic cotton romper for everyday comfort.",
      story: null,
      category_id: "3",
      price: 10,
      compare_at_price: null,
      currency: "",
      age_min_months: 0,
      age_max_months: 24,
      sizes: ["0-3m"],
      colors: ["cream"],
      images: ["/a.jpg"],
      badge: null,
      stock: 4,
      sort_order: 1,
      featured: false,
      trending: true,
      new_arrival: false,
      best_seller: false,
      active: true,
    });
    expect(mockedApi.put).toHaveBeenCalledWith(
      "/api/v1/admin/products/12",
      expect.objectContaining({
        currency: "INR",
        category: { id: 3 },
        sizesJson: "[\"0-3m\"]",
      }),
      { auth: true },
    );

    const emptyProduct = {
      slug: "",
      name: "",
      sku: "",
      fabric_type: "",
      description: null,
      story: null,
      category_id: null,
      price: null,
      compare_at_price: null,
      currency: "INR",
      age_min_months: null,
      age_max_months: null,
      sizes: [],
      colors: [],
      images: [],
      badge: null,
      stock: null,
      sort_order: null,
      featured: false,
      trending: false,
      new_arrival: false,
      best_seller: false,
      active: true,
    };
    const emptyErrors = getProductValidationErrors(emptyProduct);
    expect(emptyErrors).toEqual(
      expect.arrayContaining([
        "Product name is required.",
        "Product slug is required.",
        "SKU is required.",
        "Category is required.",
        "Price is required.",
        "Stock is required.",
        "Age min (months) is required.",
        "Age max (months) is required.",
        "Fabric type is required.",
      ]),
    );
    expect(() => saveProduct(emptyProduct)).toThrow(/Product name is required/);
    expect(mockedApi.put).toHaveBeenCalledTimes(1);

    mockedApi.post.mockResolvedValueOnce({ id: 13, title: "Banner" });
    await saveBanner({
      title: "Banner",
      subtitle: "",
      image_url: "/b.jpg",
      link_url: "",
      cta_label: "",
      sort_order: 0,
      active: true,
    });
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      "/api/v1/admin/banners",
      expect.objectContaining({ subtitle: null, linkUrl: null, ctaLabel: null }),
      { auth: true },
    );

    mockedApi.post.mockResolvedValueOnce({
      code: "SAVE",
      discountType: "PERCENT",
      discountValue: 10,
      minOrder: 0,
      active: true,
    });
    await saveCoupon({
      code: "SAVE",
      discount_type: "percent",
      discount_value: 10,
      min_order: 0,
      max_discount: null,
      expires_at: null,
      active: true,
    });
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      "/api/v1/admin/coupons",
      expect.objectContaining({ discountType: "PERCENT" }),
      { auth: true },
    );
  });

  it("calls Ledgerly sync admin endpoints", async () => {
    mockedApi.post.mockResolvedValueOnce({ successCount: 3, failureCount: 0 });
    await expect(syncAllLedgerlyProducts()).resolves.toEqual({ successCount: 3, failureCount: 0 });
    expect(mockedApi.post).toHaveBeenCalledWith("/api/v1/admin/ledgerly/sync-products", undefined, {
      auth: true,
    });

    mockedApi.post.mockResolvedValueOnce({ successCount: 2, failureCount: 1 });
    await expect(pullLedgerlyInventory()).resolves.toEqual({ successCount: 2, failureCount: 1 });
    expect(mockedApi.post).toHaveBeenCalledWith("/api/v1/admin/ledgerly/pull-inventory", undefined, {
      auth: true,
    });

    mockedApi.post.mockResolvedValueOnce({ successCount: 4, failureCount: 0 });
    await expect(pullLedgerlyProducts()).resolves.toEqual({ successCount: 4, failureCount: 0 });
    expect(mockedApi.post).toHaveBeenCalledWith("/api/v1/admin/ledgerly/pull-products", undefined, {
      auth: true,
    });

    mockedApi.post.mockResolvedValueOnce({ successCount: 1, failureCount: 0 });
    await expect(retryLedgerlyOrders()).resolves.toEqual({ successCount: 1, failureCount: 0 });

    expect(formatLedgerlySyncSummary({ successCount: 2, failureCount: 1 }, "Inventory")).toBe(
      "Inventory: 2 succeeded, 1 failed",
    );
    expect(formatLedgerlySyncSummary({ successCount: 5, failureCount: 0 }, "Products")).toBe(
      "Products: 5 succeeded",
    );
    expect(formatLedgerlySyncSummary({ successCount: 3, failureCount: 0, skippedCount: 2 }, "Inventory")).toBe(
      "Inventory: 3 succeeded, 2 skipped",
    );
    expect(
      formatLedgerlySyncFailures({
        successCount: 0,
        failureCount: 1,
        failures: [{ productName: "Dress", message: "No stock" }],
      }),
    ).toEqual(["Dress: No stock"]);
    expect(
      formatLedgerlySyncFailures({
        successCount: 0,
        skippedCount: 1,
        skipped: [{ sku: "X", message: "No linked store product" }],
      }),
    ).toEqual(["Skipped — X: No linked store product"]);
  });

  it("calls Ledgerly status and settings admin endpoints", async () => {
    mockedApi.get.mockResolvedValueOnce({
      serverConfigured: true,
      connected: false,
      pullProductsAutoCreateCategory: true,
    });
    await expect(getLedgerlyIntegrationStatus()).resolves.toMatchObject({
      serverConfigured: true,
      connected: false,
    });
    expect(mockedApi.get).toHaveBeenCalledWith("/api/v1/admin/ledgerly/integration-status", { auth: true });

    mockedApi.get.mockResolvedValueOnce({ connected: true, message: "OK" });
    await expect(testLedgerlyConnection()).resolves.toMatchObject({ connected: true, message: "OK" });

    mockedApi.put.mockResolvedValueOnce({ pullProductsAutoCreateCategory: false });
    await expect(setLedgerlyPullProductsAutoCreateCategory(false)).resolves.toMatchObject({
      pullProductsAutoCreateCategory: false,
    });
    expect(mockedApi.put).toHaveBeenCalledWith(
      "/api/v1/admin/ledgerly/pull-products-auto-create-category",
      { enabled: false },
      { auth: true },
    );

    mockedApi.post.mockResolvedValueOnce({ ledgerlyItemId: "ll-9", ledgerlySyncStatus: "SYNCED" });
    await expect(syncLedgerlyProduct(9)).resolves.toMatchObject({ ledgerlyItemId: "ll-9" });
    expect(mockedApi.post).toHaveBeenCalledWith(
      "/api/v1/admin/ledgerly/sync-products/9",
      undefined,
      { auth: true },
    );

    mockedApi.post.mockResolvedValueOnce({ ok: true, orderNumber: "ORD-1" });
    await expect(syncLedgerlyOrderByNumber("ORD-1")).resolves.toMatchObject({ orderNumber: "ORD-1" });
    expect(mockedApi.post).toHaveBeenCalledWith(
      "/api/v1/admin/ledgerly/sync-orders/by-number/ORD-1",
      undefined,
      { auth: true },
    );
  });

  it("maps settings and Ledgerly payloads between camelCase and snake_case", async () => {
    mockedApi.get.mockResolvedValueOnce({ brandName: "Mini", contactEmail: "care@example.com" });
    await expect(getSiteSettings()).resolves.toMatchObject({
      brand_name: "Mini",
      contact_email: "care@example.com",
    });

    mockedApi.get.mockResolvedValueOnce({
      brandName: "Admin",
      gstRate: 12,
      shippingStandardInr: 120,
      shippingExpressInr: 200,
      freeShippingMinInr: 3000,
      pricesIncludeGst: false,
    });
    await expect(getAdminSiteSettings()).resolves.toMatchObject({
      brand_name: "Admin",
      gst_rate: 12,
      shipping_standard_inr: 120,
      shipping_express_inr: 200,
      free_shipping_min_inr: 3000,
      prices_include_gst: false,
    });
    expect(mockedApi.get).toHaveBeenLastCalledWith("/api/v1/admin/site-settings", { auth: true });

    mockedApi.put.mockResolvedValueOnce({ ok: true });
    await saveSiteSettings({
      brand_name: "Mini",
      contact_email: null,
      gst_rate: 5,
      prices_include_gst: true,
      shipping_standard_inr: 99,
      shipping_express_inr: 150,
      free_shipping_min_inr: 2500,
    });
    expect(mockedApi.put).toHaveBeenCalledWith(
      "/api/v1/admin/site-settings",
      expect.objectContaining({
        brandName: "Mini",
        contactEmail: null,
        gstRate: 5,
        pricesIncludeGst: true,
        shippingStandardInr: 99,
        shippingExpressInr: 150,
        freeShippingMinInr: 2500,
      }),
      { auth: true },
    );

    mockedApi.get.mockResolvedValueOnce({ apiBaseUrl: "https://ledgerly.example/api", orgId: "org-1", connected: true });
    await expect(getLedgerlySettings()).resolves.toMatchObject({
      apiBaseUrl: "https://ledgerly.example/api",
      orgId: "org-1",
      connected: true,
    });

    mockedApi.put.mockResolvedValueOnce({ ok: true });
    await saveLedgerlySettings({
      api_base_url: "https://ledgerly.example/api",
      api_token: "lt_live_token",
      org_id: "org-1",
      connected: true,
    });
    expect(mockedApi.put).toHaveBeenLastCalledWith(
      "/api/v1/admin/ledgerly/settings",
      expect.objectContaining({
        api_base_url: "https://ledgerly.example/api",
        api_token: "lt_live_token",
        org_id: "org-1",
        connected: true,
      }),
      { auth: true },
    );
  });

  it("rejects unsupported image types before calling the upload API", async () => {
    await expect(uploadAdminImage("products", new File(["x"], "photo.dng"))).rejects.toThrow(
      /Image type not accepted/i,
    );
    expect(mockedApi.post).not.toHaveBeenCalled();
  });

  it("uploads admin images via backend multipart endpoint", async () => {
    const file = new File(["image-bytes"], "romper.png", { type: "image/png" });
    mockedApi.post.mockResolvedValueOnce({
      publicUrl: "https://cdn.example.com/romper.png",
    });

    await expect(uploadAdminImage("products", file)).resolves.toBe(
      "https://cdn.example.com/romper.png",
    );

    const [path, body, options] = mockedApi.post.mock.calls.at(-1)!;
    expect(path).toBe("/api/v1/admin/uploads");
    expect(options).toEqual({ auth: true });
    expect(body).toBeInstanceOf(FormData);
  });

  it("falls back to presign when backend multipart fails", async () => {
    const file = new File(["image-bytes"], "romper.png", { type: "image/png" });
    mockedApi.post
      .mockRejectedValueOnce(new Error("backend down"))
      .mockResolvedValueOnce({
        uploadUrl: "https://r2.example.com/bucket/romper.png?sig=abc",
        publicUrl: "https://cdn.example.com/romper.png",
        method: "PUT",
        headers: { "Content-Type": "image/png" },
        maxUploadBytes: 8_192_000,
      });
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce({
      ok: true,
      status: 200,
    } as Response);

    await expect(uploadAdminImage("products", file)).resolves.toBe(
      "https://cdn.example.com/romper.png",
    );
    expect(mockedApi.post).toHaveBeenCalledTimes(2);
    fetchMock.mockRestore();
  });

  it("surfaces backend upload failures", async () => {
    mockedApi.post.mockRejectedValue(new Error("Upload failed"));
    await expect(uploadAdminImage("branding", new File(["x"], "logo.png"))).rejects.toThrow(
      "Upload failed",
    );
  });

  it("rejects presign responses without upload targets", async () => {
    mockedApi.post
      .mockRejectedValueOnce(new Error("backend unavailable"))
      .mockResolvedValueOnce({ maxUploadBytes: 1000 });
    await expect(uploadAdminImage("products", new File(["x"], "bad.png"))).rejects.toThrow(
      /upload could not be prepared/i,
    );
  });

  it("rejects presign responses with invalid maxUploadBytes", async () => {
    mockedApi.post
      .mockRejectedValueOnce(new Error("backend unavailable"))
      .mockResolvedValueOnce({
        uploadUrl: "https://r2.example.com/x",
        publicUrl: "https://cdn.example.com/x",
        maxUploadBytes: 0,
      });
    await expect(uploadAdminImage("products", new File(["x"], "bad.png"))).rejects.toThrow(
      /invalid or missing maxUploadBytes/i,
    );
  });

  it("rejects invalid policies and banners before calling the API", async () => {
    await expect(
      savePolicy({
        slug: "000000000000000000",
        title: "@@@",
        body: "12345678901234567890",
      }),
    ).rejects.toThrow(/letter/i);
    expect(() =>
      saveBanner({
        title: "@@@",
        subtitle: null,
        image_url: "#$%",
        link_url: null,
        cta_label: null,
        sort_order: 0,
        active: true,
      }),
    ).toThrow(/letter|URL/i);
    expect(mockedApi.post).not.toHaveBeenCalled();
  });

  it("keeps 50-character limits for shared admin labels but allows longer product names", () => {
    const longName = "A".repeat(ADMIN_NAME_MAX_LEN + 1);
    expect(() =>
      saveCategory({
        slug: "test-category",
        name: longName,
        description: null,
        image_url: null,
        sort_order: 0,
        active: true,
      }),
    ).toThrow(/50 characters or fewer/i);
    expect(
      getProductValidationErrors({
        name: longName,
        slug: "test-product",
        sku: "SKU-1",
        category_id: "1",
        price: 100,
        stock: 1,
        age_min_months: 0,
        age_max_months: 12,
        images: ["/img.jpg"],
      } as Parameters<typeof getProductValidationErrors>[0]),
    ).not.toEqual(expect.arrayContaining(["Product name must be 50 characters or fewer."]));
    expect(
      getProductValidationErrors({
        name: "A".repeat(PRODUCT_NAME_MAX_LEN + 1),
        slug: "test-product",
        sku: "SKU-1",
        category_id: "1",
        price: 100,
        stock: 1,
        age_min_months: 0,
        age_max_months: 12,
        images: ["/img.jpg"],
      } as Parameters<typeof getProductValidationErrors>[0]),
    ).toEqual(expect.arrayContaining([`Product name must be ${PRODUCT_NAME_MAX_LEN} characters or fewer.`]));
    expect(mockedApi.post).not.toHaveBeenCalled();
  });

  it("rejects invalid blog posts before calling the API", () => {
    const invalid = {
      title: "@@##$%^^",
      slug: "00000000000000000000000000000000000000000000000000",
      content: "0546912694",
      cover_url: "#$%^^^&",
      published: false,
      excerpt: null,
      published_at: null,
    };
    expect(() => saveBlogPost(invalid)).toThrow(/letter/i);
    expect(mockedApi.post).not.toHaveBeenCalled();
    expect(mockedApi.put).not.toHaveBeenCalled();
  });

  it("rejects external banner link URLs before calling the API", () => {
    expect(() =>
      saveBanner({
        title: "Banner",
        subtitle: "",
        image_url: "/b.jpg",
        link_url: "https://google.com",
        cta_label: "Shop",
        sort_order: 0,
        active: true,
      }),
    ).toThrow(/on-site path/i);
    expect(mockedApi.post).not.toHaveBeenCalled();
  });

  it("bulk saves rows while preserving existing ids by unique key", async () => {
    const save = vi.fn(async (row: { id?: string; slug: string }) => row);
    await expect(
      bulkSave(
        [
          { slug: "romper" },
          { slug: "dress" },
        ],
        [{ id: "9", slug: "romper" }],
        "slug",
        save,
      ),
    ).resolves.toEqual({ inserted: 2, errors: [] });
    expect(save).toHaveBeenNthCalledWith(1, { id: "9", slug: "romper" });
    expect(save).toHaveBeenNthCalledWith(2, { slug: "dress" });
  });

  it("bulk save updates duplicate keys within the same batch", async () => {
    const save = vi.fn(async (row: { id?: string; slug: string; sku?: string }) => ({
      id: row.id ?? "new",
      ...row,
    }));
    const result = await bulkSave(
      [
        { slug: "romper", sku: "A" },
        { slug: "romper", sku: "B" },
      ],
      [],
      "slug",
      save,
    );
    expect(result).toEqual({ inserted: 2, errors: [] });
    expect(save).toHaveBeenNthCalledWith(1, { slug: "romper", sku: "A" });
    expect(save).toHaveBeenNthCalledWith(2, { id: "new", slug: "romper", sku: "B" });
  });

  it("disambiguates duplicate import slugs using sku", () => {
    const { rows, warnings } = disambiguateProductImportSlugs(
      [
        { slug: "front-open-vest", sku: "FOVSL", name: "Vest A" },
        { slug: "front-open-vest", sku: "FOVHSS", name: "Vest B" },
      ],
      [],
    );
    expect(rows.map((r) => r.slug)).toEqual(["front-open-vest", "front-open-vest-fovhss"]);
    expect(warnings).toHaveLength(1);
    expect(warnings[0]).toMatch(/front-open-vest-fovhss/i);
  });

  it("bulk save continues after a row failure", async () => {
    const save = vi
      .fn()
      .mockRejectedValueOnce(new Error("duplicate slug"))
      .mockResolvedValueOnce({ id: "2", slug: "dress" });
    const result = await bulkSave([{ slug: "romper" }, { slug: "dress" }], [], "slug", save);
    expect(result.inserted).toBe(1);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]).toMatch(/romper/i);
  });
});
