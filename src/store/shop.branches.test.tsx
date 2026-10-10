import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ShopProvider, useShop, type CartItem } from "./shop";
import type { Product } from "@/data/products";
import { FIT_LIMITS } from "@/lib/product-fit";
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

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
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
  sizes: ["0-3m", "3-6m"],
  rating: 5,
  reviewCount: 1,
  category: "Newborn",
  categorySlug: "",
  categoryName: "",
  sku: "ROMPER",
  description: "Soft romper",
  stock: 12,
};

const variantProduct: Product = {
  ...product,
  id: "p-var",
  sku: "HORIZON",
  itemStructure: "VARIANTS",
  colors: [
    { name: "Navy", hex: "#2c3e58" },
    { name: "Ivory", hex: "#f2eee8" },
  ],
  sizes: ["S", "M"],
  variants: [
    { sku: "HORIZON-S-NAVY", size: "S", color: "Navy", price: 799, compareAt: 999, stock: 3, active: true },
    { sku: "HORIZON-M-NAVY", size: "M", color: "Navy", price: 959, compareAt: 1199, stock: 4, active: true },
  ],
};

function cartLine(overrides: Partial<CartItem> = {}): CartItem {
  return {
    product,
    qty: 1,
    size: "0-3m",
    color: "Cream",
    unitPrice: 500,
    ...overrides,
  };
}

describe("ShopProvider branch coverage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    authState.user = { id: 42 };
    authState.loading = false;
    vi.mocked(listMyWishlist).mockResolvedValue({ product_ids: [] });
    vi.mocked(listMyCart).mockResolvedValue({ items: [], coupon: null });
    vi.mocked(replaceMyWishlist).mockImplementation(async (ids) => ({ product_ids: ids }));
    vi.mocked(replaceMyCart).mockImplementation(async (items, coupon) => ({ items, coupon }));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("uploads local wishlist when the server wishlist is empty", async () => {
    localStorage.setItem("mnm_wish_u_42", JSON.stringify(["p1", "p2"]));
    localStorage.setItem("mnm_cart_u_42", JSON.stringify([]));

    render(
      <ShopProvider>
        <span data-testid="wishlist-probe" />
      </ShopProvider>,
    );

    await waitFor(() => expect(replaceMyWishlist).toHaveBeenCalledWith(["p1", "p2"]));
  });

  it("uploads local cart when the server cart is empty", async () => {
    const line = cartLine({ qty: 2 });
    localStorage.setItem("mnm_cart_u_42", JSON.stringify([line]));
    localStorage.setItem("mnm_wish_u_42", JSON.stringify([]));

    render(
      <ShopProvider>
        <span />
      </ShopProvider>,
    );

    await waitFor(() => expect(replaceMyCart).toHaveBeenCalledWith([line], null));
  });

  it("adopts server cart and coupon when the server has items", async () => {
    const serverLine = cartLine({ qty: 4 });
    vi.mocked(listMyCart).mockResolvedValue({
      items: [serverLine],
      coupon: { code: "SAVE10", discount: 10 },
    });
    vi.mocked(listMyWishlist).mockResolvedValue({ product_ids: ["server-wish"] });
    localStorage.setItem("mnm_cart_u_42", JSON.stringify([cartLine()]));
    localStorage.setItem("mnm_wish_u_42", JSON.stringify(["local-wish"]));

    function Probe() {
      const shop = useShop();
      return (
        <div>
          <span data-testid="count">{shop.cartCount}</span>
          <span data-testid="coupon">{shop.appliedCoupon?.code ?? "none"}</span>
          <span data-testid="wishlist">{shop.wishlist.join(",")}</span>
        </div>
      );
    }

    render(
      <ShopProvider>
        <Probe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("4"));
    expect(screen.getByTestId("coupon")).toHaveTextContent("SAVE10");
    expect(screen.getByTestId("wishlist")).toHaveTextContent("server-wish");
  });

  it("keeps local cache when account sync fails", async () => {
    vi.mocked(listMyWishlist).mockRejectedValue(new Error("offline"));
    vi.mocked(listMyCart).mockRejectedValue(new Error("offline"));
    localStorage.setItem("mnm_cart_u_42", JSON.stringify([cartLine({ qty: 3 })]));
    localStorage.setItem("mnm_wish_u_42", JSON.stringify(["local-only"]));

    function Probe() {
      const shop = useShop();
      return (
        <div>
          <span data-testid="count">{shop.cartCount}</span>
          <span data-testid="wishlist">{shop.wishlist.join(",")}</span>
          <span data-testid="ready">{String(shop.accountSyncReady)}</span>
        </div>
      );
    }

    render(
      <ShopProvider>
        <Probe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("ready")).toHaveTextContent("true"));
    expect(screen.getByTestId("count")).toHaveTextContent("3");
    expect(screen.getByTestId("wishlist")).toHaveTextContent("local-only");
  });

  it("preserves user edits made while account sync is in flight", async () => {
    let resolveSync: (() => void) | undefined;
    const syncGate = new Promise<void>((resolve) => {
      resolveSync = resolve;
    });
    vi.mocked(listMyWishlist).mockImplementation(() => syncGate.then(() => ({ product_ids: [] })));
    vi.mocked(listMyCart).mockImplementation(() => syncGate.then(() => ({ items: [], coupon: null })));
    localStorage.setItem("mnm_cart_u_42", JSON.stringify([]));
    localStorage.setItem("mnm_wish_u_42", JSON.stringify([]));

    function Probe() {
      const shop = useShop();
      return (
        <div>
          <span data-testid="count">{shop.cartCount}</span>
          <button type="button" onClick={() => shop.addToCart(product)}>add</button>
        </div>
      );
    }

    render(
      <ShopProvider>
        <Probe />
      </ShopProvider>,
    );

    await userEvent.click(screen.getByRole("button", { name: "add" }));
    resolveSync?.();
    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("1"));
  });

  it("rolls back wishlist when debounced server persist fails", async () => {
    vi.mocked(listMyWishlist).mockResolvedValue({ product_ids: ["p1"] });
    const { toast } = await import("sonner");

    function Probe() {
      const shop = useShop();
      return (
        <div>
          <span data-testid="wishlist">{shop.wishlist.join(",")}</span>
          <button type="button" onClick={() => shop.toggleWish("p1")}>toggle</button>
        </div>
      );
    }

    render(
      <ShopProvider>
        <Probe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("wishlist")).toHaveTextContent("p1"));
    vi.mocked(replaceMyWishlist).mockRejectedValueOnce(new Error("offline"));

    await userEvent.click(screen.getByRole("button", { name: "toggle" }));

    await waitFor(
      () =>
        expect(toast.error).toHaveBeenCalledWith("Could not update your wishlist. Please try again."),
      { timeout: 2000 },
    );
    expect(screen.getByTestId("wishlist")).toHaveTextContent("p1");
  });

  it("toasts when cart persist fails twice", async () => {
    const { toast } = await import("sonner");
    vi.mocked(replaceMyCart).mockRejectedValue(new Error("offline"));

    function Probe() {
      const shop = useShop();
      return (
        <div>
          <span data-testid="count">{shop.cartCount}</span>
          <button type="button" onClick={() => shop.addToCart(product)}>add</button>
        </div>
      );
    }

    render(
      <ShopProvider>
        <Probe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    await userEvent.click(screen.getByRole("button", { name: "add" }));
    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith("Could not update your bag. Please try again."),
    );
  });

  it("merges cart lines by variant SKU and clamps quantity to available stock", async () => {
    const { toast } = await import("sonner");
    const stock = variantProduct.variants!.find((v) => v.sku === "HORIZON-M-NAVY")!.stock;
    function Probe() {
      const shop = useShop();
      return (
        <div>
          <span data-testid="count">{shop.cartCount}</span>
          <button
            type="button"
            onClick={() =>
              shop.addToCart(variantProduct, {
                size: "M",
                color: "Navy",
                variantSku: "HORIZON-M-NAVY",
                qty: FIT_LIMITS.qtyMax + 5,
              })
            }
          >
            add-variant
          </button>
          <button
            type="button"
            onClick={() =>
              shop.addToCart(variantProduct, {
                size: "M",
                color: "Navy",
                variantSku: "HORIZON-M-NAVY",
                qty: 1,
              })
            }
          >
            add-same-variant
          </button>
          <button
            type="button"
            onClick={() => shop.removeFromCart("p-var", undefined, undefined, "HORIZON-M-NAVY")}
          >
            remove-by-sku
          </button>
        </div>
      );
    }

    render(
      <ShopProvider>
        <Probe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    await userEvent.click(screen.getByRole("button", { name: "add-variant" }));
    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent(String(stock)));

    await userEvent.click(screen.getByRole("button", { name: "add-same-variant" }));
    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent(String(stock)));
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(`Only ${stock} left in stock`));

    await userEvent.click(screen.getByRole("button", { name: "remove-by-sku" }));
    expect(screen.getByTestId("count")).toHaveTextContent("0");
  });

  it("no-ops remove and quantity updates for missing cart lines", async () => {
    function Probe() {
      const shop = useShop();
      return (
        <div>
          <span data-testid="count">{shop.cartCount}</span>
          <button type="button" onClick={() => shop.promptRemoveFromCart("missing")}>prompt-missing</button>
          <button type="button" onClick={() => shop.updateQty("missing", 2)}>qty-missing</button>
        </div>
      );
    }

    render(
      <ShopProvider>
        <Probe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    await userEvent.click(screen.getByRole("button", { name: "prompt-missing" }));
    await userEvent.click(screen.getByRole("button", { name: "qty-missing" }));
    expect(screen.queryByTestId("remove-dialog")).toBeNull();
    expect(screen.getByTestId("count")).toHaveTextContent("0");
  });

  it("warns when duplicate wishlist toggles are batched in one click", async () => {
    const { toast } = await import("sonner");

    function Probe() {
      const shop = useShop();
      return (
        <div>
          <span data-testid="wishlist">{shop.wishlist.join(",")}</span>
          <button
            type="button"
            onClick={() => {
              shop.toggleWish("p1", "Romper");
              shop.toggleWish("p1", "Romper");
            }}
          >
            double-toggle
          </button>
        </div>
      );
    }

    render(
      <ShopProvider>
        <Probe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("wishlist")).toHaveTextContent(""));
    await userEvent.click(screen.getByRole("button", { name: "double-toggle" }));
    expect(toast.warning).toHaveBeenCalled();
    expect(screen.getByTestId("wishlist")).toHaveTextContent("p1");
  });

  it("persists and clears applied coupons for signed-in users", async () => {
    function Probe() {
      const shop = useShop();
      return (
        <div>
          <span data-testid="coupon">{shop.appliedCoupon?.code ?? "none"}</span>
          <button type="button" onClick={() => shop.setAppliedCoupon({ code: "WELCOME", discount: 50 })}>
            apply-coupon
          </button>
          <button type="button" onClick={() => shop.setAppliedCoupon(null)}>clear-coupon</button>
        </div>
      );
    }

    render(
      <ShopProvider>
        <Probe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("coupon")).toHaveTextContent("none"));
    await userEvent.click(screen.getByRole("button", { name: "apply-coupon" }));
    await waitFor(() => expect(screen.getByTestId("coupon")).toHaveTextContent("WELCOME"));
    expect(localStorage.getItem("mnm_coupon_u_42")).toContain("WELCOME");

    await userEvent.click(screen.getByRole("button", { name: "clear-coupon" }));
    await waitFor(() => expect(screen.getByTestId("coupon")).toHaveTextContent("none"));
    expect(localStorage.getItem("mnm_coupon_u_42")).toBeNull();
  });

  it("syncs cart with catalog and skips state updates when nothing changed", async () => {
    const line = cartLine();
    localStorage.setItem("mnm_cart_u_42", JSON.stringify([line]));
    localStorage.setItem("mnm_wish_u_42", JSON.stringify([]));

    function Probe() {
      const shop = useShop();
      return (
        <div>
          <span data-testid="count">{shop.cartCount}</span>
          <button type="button" onClick={() => shop.syncCartWithCatalog([product])}>sync-again</button>
        </div>
      );
    }

    render(
      <ShopProvider>
        <Probe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("1"));
    await userEvent.click(screen.getByRole("button", { name: "sync-again" }));
    expect(screen.getByTestId("count")).toHaveTextContent("1");
  });

  it("drops invalid persisted cart rows and coerces coupon discounts", async () => {
    localStorage.setItem(
      "mnm_cart_u_42",
      JSON.stringify([{ bad: true }, cartLine({ unitPrice: Number.NaN })]),
    );
    localStorage.setItem("mnm_coupon_u_42", JSON.stringify({ code: "EDGE", discount: Number.NaN }));
    localStorage.setItem("mnm_wish_u_42", JSON.stringify([]));

    function Probe() {
      const shop = useShop();
      return (
        <div>
          <span data-testid="count">{shop.cartCount}</span>
          <span data-testid="coupon-discount">{shop.appliedCoupon?.discount ?? "none"}</span>
        </div>
      );
    }

    render(
      <ShopProvider>
        <Probe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("1"));
    expect(screen.getByTestId("coupon-discount")).toHaveTextContent("0");
  });

  it("removes unavailable products during catalog sync", async () => {
    localStorage.setItem("mnm_cart_u_42", JSON.stringify([cartLine(), cartLine({ product: { ...product, id: "gone" } })]));
    localStorage.setItem("mnm_wish_u_42", JSON.stringify([]));

    function Probe() {
      const shop = useShop();
      return (
        <div>
          <span data-testid="count">{shop.cartCount}</span>
          <button type="button" onClick={() => shop.syncCartWithCatalog([product])}>sync</button>
        </div>
      );
    }

    render(
      <ShopProvider>
        <Probe />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("2"));
    await userEvent.click(screen.getByRole("button", { name: "sync" }));
    expect(screen.getByTestId("count")).toHaveTextContent("1");
  });
});
