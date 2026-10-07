import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./sales.products";
import type { ProductRow } from "@/lib/backend-data";

const listProductsMock = vi.hoisted(() => vi.fn());

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
}));

vi.mock("@/lib/backend-data", () => ({
  listProducts: (...args: unknown[]) => listProductsMock(...args),
}));

vi.mock("@/lib/ui-errors", () => ({
  showApiError: vi.fn(),
}));

function product(overrides: Partial<ProductRow> = {}): ProductRow {
  return {
    slug: "jabla",
    name: "Cotton Jabla",
    sku: "JB",
    fabric_type: null,
    description: null,
    story: null,
    category_id: null,
    price: 499,
    compare_at_price: null,
    currency: "INR",
    age_min_months: null,
    age_max_months: null,
    sizes: ["0-3m"],
    colors: [],
    item_structure: "VARIANTS",
    variants_json: JSON.stringify([
      { sku: "JB-03-CR", size: "0-3m", color: "Cream", stock: 8, active: true },
      { sku: "JB-03-SG", size: "0-3m", color: "Sage", stock: 3, active: true },
    ]),
    images: [],
    badge: null,
    hsn: null,
    tax_pct: null,
    units: null,
    stock: 0,
    sort_order: 1,
    featured: false,
    trending: false,
    new_arrival: false,
    best_seller: false,
    active: true,
    ...overrides,
  };
}

function renderPage() {
  const Component = (Route as { component: React.ComponentType }).component;
  return render(<Component />);
}

describe("sales products route", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Element.prototype.scrollIntoView = vi.fn();
    listProductsMock.mockResolvedValue([
      product(),
      product({
        slug: "wrap",
        name: "Muslin Wrap",
        sku: "WRAP",
        item_structure: "SIMPLE",
        variants_json: null,
        stock: 5,
      }),
    ]);
  });

  it("shows each product with its variants and quantities", async () => {
    renderPage();

    await waitFor(() => expect(screen.getByText("Cotton Jabla")).toBeInTheDocument());
    expect(screen.getByText("2 products · 2 variants · 16 units in stock")).toBeInTheDocument();
    expect(screen.getByText("0-3m · Cream")).toBeInTheDocument();
    expect(screen.getByText("JB-03-CR")).toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();
    expect(screen.getByText("0-3m · Sage")).toBeInTheDocument();
    expect(screen.getByText("JB-03-SG")).toBeInTheDocument();
    expect(screen.getByText("Muslin Wrap")).toBeInTheDocument();
    expect(screen.getByText("No variants")).toBeInTheDocument();
    expect(screen.getByText("5 units")).toBeInTheDocument();
  });
});
