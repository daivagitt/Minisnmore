import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CouponOffers } from "./CouponOffers";

const listActiveCoupons = vi.hoisted(() => vi.fn());

vi.mock("@/lib/coupons.functions", async () => {
  const actual = await vi.importActual<typeof import("@/lib/coupons.functions")>("@/lib/coupons.functions");
  return {
    ...actual,
    listActiveCoupons,
  };
});

describe("CouponOffers", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("disables apply for BOGO offers until enough items are in the cart", async () => {
    listActiveCoupons.mockResolvedValue({
      coupons: [
        {
          code: "BOGO1",
          discount_type: "bogo",
          discount_value: 100,
          min_order: 0,
          max_discount: null,
          bogo_buy_qty: 1,
          bogo_get_qty: 1,
        },
      ],
    });

    const user = userEvent.setup();
    render(<CouponOffers subtotal={1000} itemCount={1} onApply={vi.fn()} />);

    await waitFor(() => expect(screen.getByRole("button", { name: /view 1 offer/i })).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /view 1 offer/i }));

    expect(screen.getByText(/add 1 more item to unlock/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Apply" })).toBeDisabled();
  });

  it("applies an eligible offer and closes the sheet", async () => {
    listActiveCoupons.mockResolvedValue({
      coupons: [
        {
          code: "SAVE10",
          discount_type: "percent",
          discount_value: 10,
          min_order: 0,
          max_discount: null,
        },
      ],
    });

    const onApply = vi.fn();
    const user = userEvent.setup();
    render(<CouponOffers subtotal={1500} itemCount={2} onApply={onApply} />);

    await waitFor(() => expect(screen.getByRole("button", { name: /view 1 offer/i })).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /view 1 offer/i }));
    await user.click(screen.getByRole("button", { name: "Apply" }));

    expect(onApply).toHaveBeenCalledWith("SAVE10");
    expect(screen.queryByRole("button", { name: "Apply" })).not.toBeInTheDocument();
  });
});
