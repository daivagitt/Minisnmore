import { describe, expect, it } from "vitest";
import { getSubscribePageValidationErrors } from "@/lib/admin-subscribe-validation";
import {
  getBlogPostValidationErrors,
  getCategoryValidationErrors,
  getCouponValidationErrors,
  getHomeSectionValidationErrors,
  getPolicyValidationErrors,
  getSiteSettingsValidationErrors,
} from "@/lib/backend-data";

describe("admin validation helpers", () => {
  it("collects category required field errors", () => {
    const errors = getCategoryValidationErrors({
      name: "",
      slug: "",
      sort_order: null,
      active: true,
    });
    expect(errors).toEqual(
      expect.arrayContaining([
        "Category name is required.",
        "Category slug is required.",
        "Sort order is required.",
      ]),
    );
  });

  it("rejects placeholder category image URLs", () => {
    const errors = getCategoryValidationErrors({
      name: "Newborn",
      slug: "newborn",
      sort_order: 1,
      image_url: "https://…/cover.jpg",
      active: true,
    });
    expect(errors).toEqual([
      "Category image must be a real uploaded image or valid site path, not a placeholder example.",
    ]);
  });

  it("collects coupon required field errors", () => {
    const errors = getCouponValidationErrors({
      code: "",
      discount_type: "percent",
      discount_value: null,
      min_order: null,
      max_discount: null,
      expires_at: null,
      active: true,
    });
    expect(errors).toEqual(expect.arrayContaining(["Coupon code is required.", "Discount value is required."]));
  });

  it("collects blog post required field errors", () => {
    const errors = getBlogPostValidationErrors({
      slug: "",
      title: "",
      content: "",
      excerpt: "",
      cover_url: "",
      published: false,
      published_at: null,
    });
    expect(errors).toEqual(
      expect.arrayContaining([
        "Blog title is required.",
        "Blog slug is required.",
        "Blog content is required.",
      ]),
    );
  });

  it("collects policy required field errors", () => {
    const errors = getPolicyValidationErrors({ slug: "", title: "", body: "" });
    expect(errors).toEqual(
      expect.arrayContaining([
        "Policy slug is required.",
        "Policy title is required.",
        "Policy body is required.",
      ]),
    );
  });

  it("collects home section sort order when enabled", () => {
    const errors = getHomeSectionValidationErrors({
      key: "hero",
      title: "",
      subtitle: "",
      enabled: true,
      sort_order: null,
    });
    expect(errors).toEqual(
      expect.arrayContaining(["Section title is required.", "Sort order is required."]),
    );
  });

  it("collects site settings required field errors", () => {
    const errors = getSiteSettingsValidationErrors({
      brand_name: "",
      gst_rate: null,
      shipping_standard_inr: null,
      shipping_express_inr: null,
      free_shipping_min_inr: null,
    });
    expect(errors).toEqual(
      expect.arrayContaining([
        "Brand name is required.",
        "GST rate is required.",
        "Standard shipping is required.",
        "Express shipping is required.",
        "Free shipping threshold is required.",
      ]),
    );
  });

  it("collects subscribe page plan errors but skips blank badge rows", () => {
    const errors = getSubscribePageValidationErrors({
      brand_name: "Mini",
      subscribe_badges_json: JSON.stringify([{ icon: "sparkles", text: "" }]),
      subscribe_plans_json: JSON.stringify([{ id: "", name: "Essentials", price: null }]),
      subscribe_features_json: "[]",
    });
    expect(errors).toEqual(
      expect.arrayContaining(["Plan 1: Plan ID is required.", "Plan 1: Price (INR) is required."]),
    );
    expect(errors.some((e) => e.startsWith("Badge"))).toBe(false);
  });

  it("requires text for started subscribe badges", () => {
    const errors = getSubscribePageValidationErrors({
      brand_name: "Mini",
      subscribe_badges_json: JSON.stringify([{ icon: "heart", text: "" }]),
      subscribe_plans_json: "[]",
      subscribe_features_json: "[]",
    });
    expect(errors).toEqual(["Badge 1: Text is required."]);
  });
});
