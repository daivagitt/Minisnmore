import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./admin.coupons";

const listCoupons = vi.hoisted(() => vi.fn());
const saveCoupon = vi.hoisted(() => vi.fn());

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
}));

vi.mock("@/lib/backend-data", async () => {
  const actual = await vi.importActual<typeof import("@/lib/backend-data")>("@/lib/backend-data");
  return {
    ...actual,
    listCoupons,
    saveCoupon,
    deleteCoupon: vi.fn(),
    bulkSave: vi.fn(),
  };
});

vi.mock("@/components/admin/BulkImport", () => ({
  BulkImport: () => <div>Import</div>,
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

function renderCoupons() {
  const Component = (Route as { component: () => JSX.Element }).component;
  return render(<Component />);
}

describe("admin coupons page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listCoupons.mockResolvedValue([]);
    saveCoupon.mockResolvedValue({});
  });

  it("lets admin create a BOGO offer", async () => {
    const user = userEvent.setup();
    renderCoupons();

    await waitFor(() => expect(screen.getByRole("button", { name: /new coupon/i })).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /new coupon/i }));

    const typeSelect = screen.getAllByRole("combobox")[0];
    expect(screen.getByRole("option", { name: /bogo \(buy x get y\)/i })).toBeInTheDocument();

    await user.selectOptions(typeSelect, "bogo");
    expect(screen.getByText(/buy quantity/i)).toBeInTheDocument();
    expect(screen.getByText(/get quantity/i)).toBeInTheDocument();
    expect(screen.getByText(/% off get item/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue("100")).toBeInTheDocument();
  });
});
