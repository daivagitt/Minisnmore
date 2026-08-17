import { describe, expect, it } from "vitest";
import type { Product } from "@/data/products";
import type { CartItem } from "@/store/shop";
import { sanitizeCartWithCatalog, cartLineImage, cartLineColorLabel } from "./cart-catalog";

const base: Product = {
  id: "p1",
  slug: "romper",
  name: "Romper",
  tagline: "Soft",
  price: 500,
  image: "/romper.jpg",
  gallery: ["/romper.jpg"],
  ages: ["0-3m"],
  fabric: "Organic Cotton",
  colors: [{ name: "Cream", hex: "#fff" }],
  sizes: ["0-3m"],
  rating: 5,
  reviewCount: 1,
  category: "Newborn",
  categorySlug: "",
  categoryName: "",
  sku: "ROMPER",
  description: "Soft romper",
  stock: 5,
};

function line(overrides: Partial<CartItem> = {}): CartItem {
  return {
    product: base,
    qty: 2,
    size: "0-3m",
    color: "Cream",
    unitPrice: 500,
    ...overrides,
  };
}

describe("cart-catalog", () => {
  it("removes lines for products missing from the active catalog", () => {
    const result = sanitizeCartWithCatalog([line(), line({ product: { ...base, id: "gone", name: "Gone" } })], [base]);
    expect(result.removed).toEqual(["Gone"]);
    expect(result.cart).toHaveLength(1);
    expect(result.cart[0].product.id).toBe("p1");
  });

  it("removes inactive variant lines and caps qty to available stock", () => {
    const variantProduct: Product = {
      ...base,
      id: "p2",
      name: "Set",
      itemStructure: "VARIANTS",
      variants: [
        { sku: "SET-S-CREAM", size: "S", color: "Cream", price: 799, stock: 1, active: true },
        { sku: "SET-M-CREAM", size: "M", color: "Cream", price: 799, stock: 0, active: true },
      ],
    };
    const cart: CartItem[] = [
      line({
        product: variantProduct,
        size: "M",
        color: "Cream",
        variantSku: "SET-M-CREAM",
        qty: 1,
      }),
      line({
        product: variantProduct,
        size: "S",
        color: "Cream",
        variantSku: "SET-S-CREAM",
        qty: 3,
      }),
    ];
    const result = sanitizeCartWithCatalog(cart, [variantProduct]);
    expect(result.removed).toEqual(["Set"]);
    expect(result.adjusted).toEqual([{ name: "Set", qty: 1 }]);
    expect(result.cart).toHaveLength(1);
    expect(result.cart[0].qty).toBe(1);
  });

  it("removes simple products with zero stock", () => {
    const soldOut = { ...base, id: "p3", name: "Sold out", stock: 0 };
    const result = sanitizeCartWithCatalog([line({ product: soldOut })], [soldOut]);
    expect(result.removed).toEqual(["Sold out"]);
    expect(result.cart).toHaveLength(0);
  });

  it("resolves per-print images for cart lines", () => {
    const swaddle: Product = {
      ...base,
      id: "swaddle",
      name: "Baby Swaddle",
      image: "https://cdn.example.com/print1.jpg",
      gallery: [],
      colors: [
        { name: "BROWN SWADDLE", hex: "#d9bfa6", imageUrls: ["https://cdn.example.com/print1.jpg"] },
        { name: "GREEN SWADDLE", hex: "#d9bfa6", imageUrls: ["https://cdn.example.com/print2.jpg"] },
      ],
    };
    const print1 = line({ product: swaddle, color: "Print-1" });
    const print2 = line({ product: swaddle, color: "Print-2" });
    expect(cartLineImage(print1)).toBe("https://cdn.example.com/print1.jpg");
    expect(cartLineImage(print2)).toBe("https://cdn.example.com/print2.jpg");
    expect(cartLineColorLabel(print1)).toBe("BROWN SWADDLE");
    expect(cartLineColorLabel(print2)).toBe("GREEN SWADDLE");
  });
});
