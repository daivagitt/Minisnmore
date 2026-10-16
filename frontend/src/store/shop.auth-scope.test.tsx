import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ShopProvider, useShop } from "./shop";
import type { Product } from "@/data/products";

const authState = vi.hoisted(() => ({
  user: null as { id: number } | null,
  loading: false,
}));

vi.mock("@/hooks/useAuth", () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => children,
  useAuth: () => ({
    user: authState.user,
    loading: authState.loading,
    session: null,
    isAdmin: false,
    signIn: vi.fn(),
    signUp: vi.fn(),
    signOut: vi.fn(),
    signInWithOAuth: vi.fn(),
    completeOAuthSignIn: vi.fn(),
  }),
}));

vi.mock("@/lib/account.functions", () => ({
  listMyWishlist: vi.fn(async () => ({ product_ids: [] })),
  syncMyWishlist: vi.fn(async (ids: string[]) => ({ product_ids: ids })),
  replaceMyWishlist: vi.fn(async (ids: string[]) => ({ product_ids: ids })),
  addMyWishlistItem: vi.fn(async () => ({ ok: true })),
  removeMyWishlistItem: vi.fn(async () => ({ ok: true })),
  listMyCart: vi.fn(async () => ({ items: [], coupon: null })),
  replaceMyCart: vi.fn(async (items: unknown[], coupon: unknown) => ({ items, coupon })),
}));

const product: Product = {
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
  sku: "",
  description: "Soft romper",
};

function CountProbe() {
  const { cartCount } = useShop();
  return <span data-testid="count">{cartCount}</span>;
}

describe("ShopProvider auth scope", () => {
  beforeEach(() => {
    localStorage.clear();
    authState.user = null;
    authState.loading = false;
  });

  it("does not copy signed-in cart into guest storage on sign-out", async () => {
    const line = { product, qty: 3, size: "0-3m", color: "Cream" };
    localStorage.setItem("mnm_cart_u_7", JSON.stringify([line]));
    localStorage.setItem("mnm_cart_guest", "[]");

    authState.user = { id: 7 };
    const { rerender } = render(
      <ShopProvider>
        <CountProbe />
      </ShopProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("3"));

    authState.user = null;
    rerender(
      <ShopProvider>
        <CountProbe />
      </ShopProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    expect(localStorage.getItem("mnm_cart_guest")).toBe("[]");
    expect(localStorage.getItem("mnm_cart_u_7")).toContain('"qty":3');
  });

  it("defers hydration while auth is loading", async () => {
    localStorage.setItem(
      "mnm_cart_u_9",
      JSON.stringify([{ product, qty: 4, size: "0-3m", color: "Cream" }]),
    );
    authState.loading = true;
    authState.user = { id: 9 };

    const { rerender } = render(
      <ShopProvider>
        <CountProbe />
      </ShopProvider>,
    );
    expect(screen.getByTestId("count")).toHaveTextContent("0");

    authState.loading = false;
    rerender(
      <ShopProvider>
        <CountProbe />
      </ShopProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("4"));
  });

  it("does not persist cart data for the previous user after switching accounts", async () => {
    const line = { product, qty: 1, size: "0-3m", color: "Cream" };
    localStorage.setItem("mnm_cart_u_1", JSON.stringify([line]));
    localStorage.setItem("mnm_cart_u_2", "[]");

    authState.user = { id: 1 };
    const { rerender } = render(
      <ShopProvider>
        <CountProbe />
      </ShopProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("1"));

    authState.user = { id: 2 };
    rerender(
      <ShopProvider>
        <CountProbe />
      </ShopProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    expect(localStorage.getItem("mnm_cart_u_2")).toBe("[]");
    expect(localStorage.getItem("mnm_cart_u_1")).toContain('"qty":1');
  });

  it("clears guest cart when it was polluted with the signed-in cart on sign-out", async () => {
    const line = { product, qty: 2, size: "0-3m", color: "Cream" };
    const payload = JSON.stringify([line]);
    localStorage.setItem("mnm_cart_u_3", payload);
    localStorage.setItem("mnm_cart_guest", payload);
    localStorage.setItem("mnm_wish_u_3", JSON.stringify(["p1"]));
    localStorage.setItem("mnm_wish_guest", JSON.stringify(["p1"]));

    authState.user = { id: 3 };
    const { rerender } = render(
      <ShopProvider>
        <CountProbe />
      </ShopProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("2"));

    authState.user = null;
    rerender(
      <ShopProvider>
        <CountProbe />
      </ShopProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    expect(localStorage.getItem("mnm_cart_guest")).toBe("[]");
    expect(localStorage.getItem("mnm_wish_guest")).toBe("[]");
  });
});
