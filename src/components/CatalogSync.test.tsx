import { render, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CatalogSync } from "@/components/CatalogSync";
import { ShopProvider, useShop } from "@/store/shop";

const authState = vi.hoisted(() => ({
  user: { id: 30600 } as { id: number } | null,
  loading: false,
}));

const productsState = vi.hoisted(() => ({
  products: [] as { id: string }[],
  loaded: false,
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

vi.mock("@/hooks/useProducts", () => ({
  useProducts: () => productsState.products,
  useProductsLoaded: () => productsState.loaded,
}));

vi.mock("@/lib/storefront", () => ({
  loadProducts: vi.fn(async () => []),
}));

vi.mock("@/lib/account.functions", () => ({
  listMyWishlist: vi.fn(async () => ({ product_ids: ["39351", "999901"] })),
  replaceMyWishlist: vi.fn(async (ids: string[]) => ({ product_ids: ids })),
  listMyCart: vi.fn(async () => ({ items: [], coupon: null })),
  replaceMyCart: vi.fn(async (items: unknown[], coupon: unknown) => ({ items, coupon })),
}));

function WishlistProbe() {
  const { wishlist } = useShop();
  return <span data-testid="wishlist">{wishlist.join(",")}</span>;
}

describe("CatalogSync", () => {
  beforeEach(() => {
    localStorage.clear();
    productsState.products = [];
    productsState.loaded = false;
    localStorage.setItem("mnm_wish_u_30600", JSON.stringify(["39351", "999901"]));
  });

  it("does not prune wishlist before live products load", async () => {
    productsState.products = [{ id: "p1" }, { id: "p2" }];
    productsState.loaded = false;

    const { rerender } = render(
      <ShopProvider>
        <CatalogSync />
        <WishlistProbe />
      </ShopProvider>,
    );

    await waitFor(() => {
      expect(localStorage.getItem("mnm_wish_u_30600")).toContain("39351");
    });

    rerender(
      <ShopProvider>
        <CatalogSync />
        <WishlistProbe />
      </ShopProvider>,
    );

    expect(localStorage.getItem("mnm_wish_u_30600")).toContain("39351");
  });

  it("prunes invalid wishlist ids once live catalog and account sync are ready", async () => {
    productsState.products = [{ id: "39351" }];
    productsState.loaded = true;

    render(
      <ShopProvider>
        <CatalogSync />
        <WishlistProbe />
      </ShopProvider>,
    );

    await waitFor(() => {
      expect(localStorage.getItem("mnm_wish_u_30600")).toBe(JSON.stringify(["39351"]));
    });
  });

  it("clears wishlist when the live catalog is empty", async () => {
    productsState.products = [];
    productsState.loaded = true;

    render(
      <ShopProvider>
        <CatalogSync />
        <WishlistProbe />
      </ShopProvider>,
    );

    await waitFor(() => {
      expect(localStorage.getItem("mnm_wish_u_30600")).toBe(JSON.stringify([]));
    });
  });
});
