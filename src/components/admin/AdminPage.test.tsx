import { useState } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AdminCollapsibleSection } from "./AdminPage";

function Harness() {
  const [open, setOpen] = useState(false);
  return (
    <AdminCollapsibleSection title="Size chart" hint="Measurement table" open={open} onOpenChange={setOpen}>
      <p>Chart body</p>
    </AdminCollapsibleSection>
  );
}

describe("AdminCollapsibleSection", () => {
  it("hides content until expanded", async () => {
    render(<Harness />);
    expect(screen.getByRole("button", { name: /size chart/i })).toBeTruthy();
    expect(screen.queryByText("Chart body")).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: /size chart/i }));
    expect(screen.getByText("Chart body")).toBeTruthy();
  });
});
