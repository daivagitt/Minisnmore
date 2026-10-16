import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ShopProvider, useShop } from "./shop";
import { listMyCart, listMyWishlist, replaceMyCart, replaceMyWishlist } from "@/lib/account.functions";

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

const vestLine = {
  product: { id: "vest133", name: "vest133" },
  qty: 1,
  size: "0-3m",
  color: "Cream",
  unitPrice: 499,
};

function AccountProbe() {
  const { wishlist, cart, accountSyncReady } = useShop();
  return (
    <div>
      <span data-testid="wishlist">{wishlist.join(",")}</span>
      <span data-testid="cart">{cart.map((line) => line.product.id).join(",")}</span>
      <span data-testid="ready">{String(accountSyncReady)}</span>
    </div>
  );
}

describe("ShopProvider server sync", () => {
  beforeEach(() => {
    localStorage.clear();
    authState.user = null;
    authState.loading = false;
    vi.mocked(listMyWishlist).mockResolvedValue({ product_ids: ["39351", "999901"] });
    vi.mocked(listMyCart).mockResolvedValue({ items: [], coupon: null });
    vi.mocked(replaceMyWishlist).mockImplementation(async (ids) => ({ product_ids: ids }));
    vi.mocked(replaceMyCart).mockImplementation(async (items, coupon) => ({ items, coupon }));
  });

  it("adopts server wishlist even when local cache was cleared before sync completes", async () => {
    localStorage.setItem("mnm_wish_u_30600", JSON.stringify(["39351", "999901"]));
    authState.user = { id: 30600 };

    render(
      <ShopProvider>
        <AccountProbe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("ready")).toHaveTextContent("true"));
    expect(screen.getByTestId("wishlist")).toHaveTextContent("39351,999901");
    expect(listMyWishlist).toHaveBeenCalled();
  });

  it("adopts server wishlist when local cache is missing", async () => {
    authState.user = { id: 30600 };

    render(
      <ShopProvider>
        <AccountProbe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("ready")).toHaveTextContent("true"));
    expect(screen.getByTestId("wishlist")).toHaveTextContent("39351,999901");
    expect(replaceMyWishlist).not.toHaveBeenCalledWith([]);
  });

  it("does not restore server wishlist when local cache was explicitly emptied", async () => {
    localStorage.setItem("mnm_wish_u_30600", JSON.stringify([]));
    authState.user = { id: 30600 };

    render(
      <ShopProvider>
        <AccountProbe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("ready")).toHaveTextContent("true"));
    expect(screen.getByTestId("wishlist")).toHaveTextContent("");
    expect(replaceMyWishlist).toHaveBeenCalledWith([]);
  });

  it("does not restore server cart when local cache was explicitly emptied", async () => {
    localStorage.setItem("mnm_cart_u_30600", JSON.stringify([]));
    localStorage.setItem("mnm_wish_u_30600", JSON.stringify([]));
    authState.user = { id: 30600 };
    vi.mocked(listMyWishlist).mockResolvedValue({ product_ids: ["vest133"] });
    vi.mocked(listMyCart).mockResolvedValue({ items: [vestLine], coupon: null });

    render(
      <ShopProvider>
        <AccountProbe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("ready")).toHaveTextContent("true"));
    expect(screen.getByTestId("cart")).toHaveTextContent("");
    expect(screen.getByTestId("wishlist")).toHaveTextContent("");
    expect(replaceMyCart).toHaveBeenCalledWith([], null);
    expect(replaceMyWishlist).toHaveBeenCalledWith([]);
  });
});
