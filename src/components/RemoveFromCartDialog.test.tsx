import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { RemoveFromCartDialog } from "./RemoveFromCartDialog";

describe("RemoveFromCartDialog", () => {
  it("shows save to wishlist and remove when open", () => {
    render(
      <RemoveFromCartDialog
        open
        productName="Petal Romper"
        onOpenChange={vi.fn()}
        onMoveToWishlist={vi.fn()}
        onRemoveOnly={vi.fn()}
      />,
    );

    expect(screen.getByText("Remove from cart?")).toBeTruthy();
    expect(
      screen.getByText("Save “Petal Romper” to your wishlist instead, or remove it from cart."),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Save to wishlist" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Remove" })).toBeTruthy();
  });

  it("calls save and remove handlers", async () => {
    const onMoveToWishlist = vi.fn();
    const onRemoveOnly = vi.fn();

    render(
      <RemoveFromCartDialog
        open
        productName="Petal Romper"
        onOpenChange={vi.fn()}
        onMoveToWishlist={onMoveToWishlist}
        onRemoveOnly={onRemoveOnly}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Save to wishlist" }));
    expect(onMoveToWishlist).toHaveBeenCalledOnce();

    await userEvent.click(screen.getByRole("button", { name: "Remove" }));
    expect(onRemoveOnly).toHaveBeenCalledOnce();
  });
});
