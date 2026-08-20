import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ShopProvider, useShop } from "./shop";
import type { Product } from "@/data/products";

const authState = vi.hoisted(() => ({
  user: { id: 1 } as { id: number } | null,
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

vi.mock("@/lib/account.functions", () => ({
  listMyWishlist: vi.fn(async () => ({ product_ids: [] })),
  syncMyWishlist: vi.fn(async (ids: string[]) => ({ product_ids: ids })),
  replaceMyWishlist: vi.fn(async (ids: string[]) => ({ product_ids: ids })),
  addMyWishlistItem: vi.fn(async () => ({ ok: true })),
  removeMyWishlistItem: vi.fn(async () => ({ ok: true })),
  listMyCart: vi.fn(async () => ({ items: [], coupon: null })),
  replaceMyCart: vi.fn(async (items: unknown[], coupon: unknown) => ({ items, coupon })),
}));

vi.mock("@/components/RemoveFromCartDialog", () => ({
  RemoveFromCartDialog: ({
    open,
    onMoveToWishlist,
    onRemoveOnly,
    onOpenChange,
  }: {
    open: boolean;
    onMoveToWishlist: () => void;
    onRemoveOnly: () => void;
    onOpenChange: (open: boolean) => void;
  }) =>
    open ? (
      <div data-testid="remove-dialog">
        <button type="button" onClick={onMoveToWishlist}>move-wish</button>
        <button type="button" onClick={onRemoveOnly}>remove-only</button>
        <button type="button" onClick={() => onOpenChange(false)}>close-dialog</button>
      </div>
    ) : null,
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
  active: true,
  stock: 5,
};

function Harness() {
  const shop = useShop();
  return (
    <div>
      <span data-testid="count">{shop.cartCount}</span>
      <span data-testid="wishlist">{shop.wishlist.join(",")}</span>
      <button type="button" onClick={() => shop.addToCart(product)}>add</button>
      <button type="button" onClick={() => shop.toggleWish("p1")}>wish</button>
      <button type="button" onClick={() => shop.promptRemoveFromCart("p1")}>prompt-remove</button>
      <button type="button" onClick={() => shop.updateQty("p1", 0)}>decrement-remove</button>
      <button type="button" onClick={() => shop.syncCartWithCatalog([product])}>sync-catalog</button>
    </div>
  );
}

describe("ShopProvider remove dialog", () => {
  beforeEach(() => {
    localStorage.clear();
    authState.user = { id: 1 };
    authState.loading = false;
  });

  it("promptRemoveFromCart opens dialog and supports wishlist move", async () => {
    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    await userEvent.click(screen.getByRole("button", { name: "add" }));
    await userEvent.click(screen.getByRole("button", { name: "prompt-remove" }));

    expect(screen.getByTestId("remove-dialog")).toBeTruthy();
    await userEvent.click(screen.getByRole("button", { name: "move-wish" }));

    expect(screen.getByTestId("count")).toHaveTextContent("0");
    expect(screen.getByTestId("wishlist")).toHaveTextContent("p1");
  });

  it("remove dialog close keeps item in cart", async () => {
    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    await userEvent.click(screen.getByRole("button", { name: "add" }));
    await userEvent.click(screen.getByRole("button", { name: "prompt-remove" }));
    await userEvent.click(screen.getByRole("button", { name: "close-dialog" }));

    expect(screen.getByTestId("count")).toHaveTextContent("1");
    expect(screen.getByTestId("wishlist")).toHaveTextContent("");
  });

  it("remove dialog remove-only removes item without wishlist", async () => {
    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    await userEvent.click(screen.getByRole("button", { name: "add" }));
    await userEvent.click(screen.getByRole("button", { name: "prompt-remove" }));
    await userEvent.click(screen.getByRole("button", { name: "remove-only" }));

    expect(screen.getByTestId("count")).toHaveTextContent("0");
    expect(screen.getByTestId("wishlist")).toHaveTextContent("");
  });

  it("move to wishlist keeps item when already wishlisted", async () => {
    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    await userEvent.click(screen.getByRole("button", { name: "wish" }));
    expect(screen.getByTestId("wishlist")).toHaveTextContent("p1");

    await userEvent.click(screen.getByRole("button", { name: "add" }));
    await userEvent.click(screen.getByRole("button", { name: "decrement-remove" }));
    await userEvent.click(screen.getByRole("button", { name: "move-wish" }));

    expect(screen.getByTestId("count")).toHaveTextContent("0");
    expect(screen.getByTestId("wishlist")).toHaveTextContent("p1");
  });

  it("updateQty to zero opens remove dialog instead of removing immediately", async () => {
    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    await userEvent.click(screen.getByRole("button", { name: "add" }));
    await userEvent.click(screen.getByRole("button", { name: "decrement-remove" }));

    expect(screen.getByTestId("remove-dialog")).toBeTruthy();
    expect(screen.getByTestId("count")).toHaveTextContent("1");
  });

  it("syncCartWithCatalog keeps matching products", async () => {
    render(
      <ShopProvider>
        <Harness />
      </ShopProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("count")).toHaveTextContent("0"));
    await userEvent.click(screen.getByRole("button", { name: "add" }));
    await userEvent.click(screen.getByRole("button", { name: "sync-catalog" }));
    expect(screen.getByTestId("count")).toHaveTextContent("1");
  });
});
