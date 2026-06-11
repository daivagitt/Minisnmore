import { render, renderHook, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
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
  sku: "",
  description: "Soft romper",
};

function Harness() {
  const shop = useShop();
  return (
    <div>
      <span data-testid="count">{shop.cartCount}</span>
      <span data-testid="total">{shop.cartTotal}</span>
      <span data-testid="open">{String(shop.cartOpen)}</span>
      <span data-testid="wishlist">{shop.wishlist.join(",")}</span>
      <button onClick={() => shop.addToCart(product)}>add</button>
      <button onClick={() => shop.addToCart(product, { size: "3-6m", color: "Cream", qty: 2 })}>add-variant</button>
      <button onClick={() => shop.updateQty("p1", 3)}>qty</button>
      <button onClick={() => shop.updateQty("p1", 4, "3-6m", "Cream")}>qty-variant</button>
      <button onClick={() => shop.updateQty("p1", 0)}>zero</button>
      <button onClick={() => shop.removeFromCart("p1", "3-6m", "Cream")}>remove-variant</button>
      <button onClick={() => shop.removeFromCart("p1")}>remove</button>
      <button onClick={() => shop.clearCart()}>clear</button>
      <button onClick={() => shop.toggleWish("p1")}>wish</button>
      <button onClick={() => shop.pruneWishlist(["p1", "p2"])}>prune</button>
      <button onClick={() => shop.pruneWishlist(["p1"])}>prune-noop</button>
      <button onClick={() => shop.setCartOpen(false)}>close cart</button>
      <button onClick={() => shop.setQuickView(product)}>quick</button>
      <button onClick={() => shop.setQuickView(null)}>clear quick</button>
    </div>
  );
}

describe("ShopProvider", () => {
  beforeEach(() => {
    localStorage.clear();
    authState.user = { id: 1 };
    authState.loading = false;
  });

  it("manages cart totals, quantities, and wishlist persistence when signed in", async () => {
    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));

    await userEvent.click(screen.getByRole("button", { name: "add" }));
    await userEvent.click(screen.getByRole("button", { name: "add" }));
    expect(screen.getByTestId("count")).toHaveTextContent("2");
    expect(screen.getByTestId("total")).toHaveTextContent("1000");
    expect(screen.getByTestId("open")).toHaveTextContent("true");

    await userEvent.click(screen.getByRole("button", { name: "qty" }));
    expect(screen.getByTestId("count")).toHaveTextContent("3");

    await userEvent.click(screen.getByRole("button", { name: "wish" }));
    expect(screen.getByTestId("wishlist")).toHaveTextContent("p1");
    expect(localStorage.getItem("mnm_wish_u_1")).toContain("p1");
    await userEvent.click(screen.getByRole("button", { name: "wish" }));
    expect(screen.getByTestId("wishlist")).toHaveTextContent("");

    await userEvent.click(screen.getByRole("button", { name: "remove" }));
    expect(screen.getByTestId("count")).toHaveTextContent("0");

    await userEvent.click(screen.getByRole("button", { name: "add" }));
    await userEvent.click(screen.getByRole("button", { name: "zero" }));
    expect(screen.getByTestId("count")).toHaveTextContent("0");

    await userEvent.click(screen.getByRole("button", { name: "add" }));
    await userEvent.click(screen.getByRole("button", { name: "clear" }));
    expect(screen.getByTestId("count")).toHaveTextContent("0");
  });

  it("ignores guest localStorage when logged out", async () => {
    authState.user = null;
    localStorage.setItem(
      "mnm_cart_guest",
      JSON.stringify([{ product, qty: 2, size: "0-3m", color: "Cream" }]),
    );
    localStorage.setItem("mnm_wish_guest", JSON.stringify(["p1"]));

    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    expect(screen.getByTestId("wishlist")).toHaveTextContent("");
    expect(localStorage.getItem("mnm_cart_guest")).toBe("[]");
    expect(localStorage.getItem("mnm_wish_guest")).toBe("[]");
  });

  it("blocks add and wish actions when logged out", async () => {
    const { toast } = await import("sonner");
    authState.user = null;

    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    await userEvent.click(screen.getByRole("button", { name: "add" }));
    expect(screen.getByTestId("count")).toHaveTextContent("0");
    expect(toast.error).toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: "wish" }));
    expect(screen.getByTestId("wishlist")).toHaveTextContent("");
  });

  it("loads persisted cart for signed-in user", async () => {
    localStorage.setItem(
      "mnm_cart_u_1",
      JSON.stringify([{ product, qty: 2, size: "0-3m", color: "Cream" }]),
    );
    localStorage.setItem("mnm_wish_u_1", JSON.stringify(["p1"]));

    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("2"));
    expect(screen.getByTestId("wishlist")).toHaveTextContent("p1");
  });

  it("ignores corrupt localStorage JSON and prunes stale wishlist ids", async () => {
    localStorage.setItem("mnm_cart_u_1", "{not-json");
    localStorage.setItem("mnm_wish_u_1", JSON.stringify(["p1", "stale"]));

    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    expect(screen.getByTestId("wishlist")).toHaveTextContent("p1,stale");

    await userEvent.click(screen.getByRole("button", { name: "prune" }));
    expect(screen.getByTestId("wishlist")).toHaveTextContent("p1");
  });

  it("exposes cart drawer and quick view helpers", async () => {
    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("open")).toHaveTextContent("false"));

    await userEvent.click(screen.getByRole("button", { name: "add" }));
    expect(screen.getByTestId("open")).toHaveTextContent("true");

    await userEvent.click(screen.getByRole("button", { name: "close cart" }));
    expect(screen.getByTestId("open")).toHaveTextContent("false");

    await userEvent.click(screen.getByRole("button", { name: "quick" }));
    await userEvent.click(screen.getByRole("button", { name: "clear quick" }));
  });

  it("keeps separate carts per user id in localStorage", () => {
    localStorage.setItem(
      "mnm_cart_u_1",
      JSON.stringify([{ product, qty: 2, size: "0-3m", color: "Cream" }]),
    );
    localStorage.setItem("mnm_cart_u_2", JSON.stringify([]));

    expect(localStorage.getItem("mnm_cart_u_1")).toContain('"qty":2');
    expect(localStorage.getItem("mnm_cart_u_2")).toBe("[]");
  });

  it("tracks separate line items by size and color", async () => {
    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));

    await userEvent.click(screen.getByRole("button", { name: "add" }));
    await userEvent.click(screen.getByRole("button", { name: "add-variant" }));
    expect(screen.getByTestId("count")).toHaveTextContent("3");

    await userEvent.click(screen.getByRole("button", { name: "qty-variant" }));
    expect(screen.getByTestId("count")).toHaveTextContent("5");

    await userEvent.click(screen.getByRole("button", { name: "remove-variant" }));
    expect(screen.getByTestId("count")).toHaveTextContent("1");
  });

  it("returns empty storage reads and clears legacy guest keys", async () => {
    localStorage.setItem("mnm_cart", "[]");
    localStorage.setItem("mnm_wish", "[]");
    authState.user = null;

    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    expect(localStorage.getItem("mnm_cart")).toBeNull();
    expect(localStorage.getItem("mnm_wish")).toBeNull();
  });

  it("skips wishlist updates when prune keeps the same ids", async () => {
    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));

    await userEvent.click(screen.getByRole("button", { name: "wish" }));
    expect(screen.getByTestId("wishlist")).toHaveTextContent("p1");

    await userEvent.click(screen.getByRole("button", { name: "prune-noop" }));
    expect(screen.getByTestId("wishlist")).toHaveTextContent("p1");
  });

  it("throws when useShop is called outside ShopProvider", () => {
    expect(() => renderHook(() => useShop())).toThrow("ShopProvider missing");
  });
});
