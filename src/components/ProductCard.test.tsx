import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProductCard } from "./ProductCard";
import { ShopProvider, useShop } from "@/store/shop";
import type { Product } from "@/data/products";

const authState = vi.hoisted(() => ({
  user: { id: 1, login: "parent@example.com" } as { id: number; login: string },
  loading: false,
}));

vi.mock("@/hooks/useAuth", () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => children,
  useAuth: () => ({
    user: authState.user,
    loading: authState.loading,
    session: { accessToken: "test-token" },
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

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, to, params, ...props }: any) => (
    <a href={params?.slug ? `/product/${params.slug}` : to} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("framer-motion", () => ({
  motion: {
    div: ({ children, initial, whileInView, viewport, transition, animate, exit, ...props }: any) => <div {...props}>{children}</div>,
  },
}));

const product: Product = {
  id: "p1",
  slug: "petal-romper",
  name: "Petal Romper",
  tagline: "Soft organic cotton",
  price: 1299,
  compareAt: 1499,
  image: "/romper.jpg",
  gallery: ["/romper.jpg"],
  ages: ["0-3m"],
  fabric: "Organic Cotton",
  colors: [
    { name: "Cream", hex: "#fff4df" },
    { name: "Rose", hex: "#ffc0cb" },
  ],
  sizes: ["0-3m"],
  rating: 4.9,
  reviewCount: 12,
  category: "Newborn",
  categorySlug: "newborn",
  categoryName: "Newborn",
  sku: "TEST-001",
  isNew: true,
  isBestseller: true,
  description: "A keepsake romper.",
};

function ShopProbe() {
  const { wishlist, quickView } = useShop();
  return (
    <>
      <span data-testid="wishlist">{wishlist.join(",")}</span>
      <span data-testid="quick-view">{quickView?.slug ?? "none"}</span>
    </>
  );
}

function Providers({ children }: { children: React.ReactNode }) {
  return <ShopProvider>{children}</ShopProvider>;
}

describe("ProductCard", () => {
  beforeEach(() => {
    localStorage.clear();
    authState.user = { id: 1, login: "parent@example.com" };
    authState.loading = false;
  });

  it("renders product details and updates wishlist/quick view state", async () => {
    render(
      <Providers>
        <ProductCard product={product} />
        <ShopProbe />
      </Providers>,
    );

    expect(screen.getByRole("img", { name: "Petal Romper" })).toHaveAttribute("src", "/romper.jpg");
    expect(screen.getByText("New")).toBeInTheDocument();
    expect(screen.getByText("Bestseller")).toBeInTheDocument();
    expect(screen.getByText("₹1,299")).toBeInTheDocument();
    expect(screen.getByText("₹1,499")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Add to wishlist" }));
    await waitFor(() => expect(screen.getByTestId("wishlist")).toHaveTextContent("p1"));

    await userEvent.click(screen.getByRole("button", { name: "Quick view" }));
    expect(screen.getByTestId("quick-view")).toHaveTextContent("petal-romper");
  });
});
