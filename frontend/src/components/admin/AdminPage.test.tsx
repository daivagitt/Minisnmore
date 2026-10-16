import { useState } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AdminCollapsibleSection, AdminPanelSection, AdminSectionToggles } from "./AdminPage";
import { useAdminSections } from "@/hooks/useAdminSections";

function Harness() {
  const [open, setOpen] = useState(false);
  return (
    <AdminCollapsibleSection
      title="Size chart"
      hint="Measurement table"
      open={open}
      onOpenChange={setOpen}
    >
      <p>Chart body</p>
    </AdminCollapsibleSection>
  );
}

function PanelsHarness() {
  const sections = useAdminSections<"hero" | "footer">();
  const [enabled, setEnabled] = useState(false);
  return (
    <>
      <AdminSectionToggles sections={sections} />
      <AdminPanelSection title="Hero" {...sections.panel("hero", true)}>
        <p>Hero body</p>
      </AdminPanelSection>
      <AdminPanelSection
        title="Footer"
        {...sections.panel("footer")}
        actions={
          <label>
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
            />
            Show footer
          </label>
        }
      >
        <p>Footer body</p>
      </AdminPanelSection>
    </>
  );
}

describe("AdminCollapsibleSection", () => {
  it("hides content until expanded", async () => {
    render(<Harness />);
    expect(screen.getByRole("button", { name: /size chart/i })).toBeTruthy();
    expect(screen.getByText("Chart body")).not.toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: /size chart/i }));
    expect(screen.getByText("Chart body")).toBeVisible();
  });
});

describe("AdminPanelSection with useAdminSections", () => {
  it("starts from each panel's default", () => {
    render(<PanelsHarness />);
    expect(screen.getByText("Hero body")).toBeVisible();
    expect(screen.getByText("Footer body")).not.toBeVisible();
  });

  it("expand all and collapse all override defaults and earlier toggles", async () => {
    render(<PanelsHarness />);
    await userEvent.click(screen.getByRole("button", { name: /footer/i }));
    await userEvent.click(screen.getByRole("button", { name: /collapse all/i }));
    expect(screen.getByText("Hero body")).not.toBeVisible();
    expect(screen.getByText("Footer body")).not.toBeVisible();

    await userEvent.click(screen.getByRole("button", { name: /expand all/i }));
    expect(screen.getByText("Hero body")).toBeVisible();
    expect(screen.getByText("Footer body")).toBeVisible();
  });

  it("a single toggle after expand all only affects that panel", async () => {
    render(<PanelsHarness />);
    await userEvent.click(screen.getByRole("button", { name: /expand all/i }));
    await userEvent.click(screen.getByRole("button", { name: /hero/i }));
    expect(screen.getByText("Hero body")).not.toBeVisible();
    expect(screen.getByText("Footer body")).toBeVisible();
  });

  it("keeps child state when a panel is collapsed and reopened", async () => {
    function Counter() {
      const [n, setN] = useState(0);
      return <button onClick={() => setN(n + 1)}>Clicked {n}</button>;
    }
    function CounterHarness() {
      const sections = useAdminSections();
      return (
        <AdminPanelSection title="Uploads" {...sections.panel("uploads", true)}>
          <Counter />
        </AdminPanelSection>
      );
    }
    render(<CounterHarness />);
    await userEvent.click(screen.getByRole("button", { name: /clicked 0/i }));
    await userEvent.click(screen.getByRole("button", { name: /uploads/i }));
    await userEvent.click(screen.getByRole("button", { name: /uploads/i }));
    expect(screen.getByRole("button", { name: /clicked 1/i })).toBeVisible();
  });

  it("header actions work without toggling the panel", async () => {
    render(<PanelsHarness />);
    const checkbox = screen.getByRole("checkbox", { name: /show footer/i }) as HTMLInputElement;
    await userEvent.click(checkbox);
    expect(checkbox.checked).toBe(true);
    expect(screen.getByText("Footer body")).not.toBeVisible();
  });
});
