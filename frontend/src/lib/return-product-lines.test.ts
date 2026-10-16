import { describe, expect, it } from "vitest";
import { resolveReturnProductLines } from "./return-product-lines";

describe("resolveReturnProductLines", () => {
  it("prefers line items when present", () => {
    expect(
      resolveReturnProductLines({
        items: [{ product_name: "Dress", product_image: "https://cdn.example/a.jpg" }],
        product_name: "Ignored",
      }),
    ).toEqual([{ product_name: "Dress", product_image: "https://cdn.example/a.jpg" }]);
  });

  it("falls back to top-level product fields", () => {
    expect(
      resolveReturnProductLines({
        product_name: "Dress",
        product_image: "https://cdn.example/a.jpg",
      }),
    ).toEqual([{ product_name: "Dress", product_image: "https://cdn.example/a.jpg" }]);
  });

  it("returns empty when nothing is known", () => {
    expect(resolveReturnProductLines({})).toEqual([]);
  });
});
