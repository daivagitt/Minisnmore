import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./admin.package-types";

const listPackageBoxes = vi.hoisted(() => vi.fn());
const savePackageBox = vi.hoisted(() => vi.fn());

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
}));

vi.mock("@/lib/package-boxes", async () => {
  const actual = await vi.importActual<typeof import("@/lib/package-boxes")>("@/lib/package-boxes");
  return {
    ...actual,
    listPackageBoxes,
    savePackageBox,
    deletePackageBox: vi.fn(),
  };
});

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

function renderPage() {
  const Component = (Route as unknown as { component: () => React.JSX.Element }).component;
  return render(<Component />);
}

describe("admin package types page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listPackageBoxes.mockResolvedValue([]);
    savePackageBox.mockResolvedValue({});
  });

  it("lets admin add a named box type", async () => {
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole("button", { name: /new package type/i })).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /new package type/i }));

    await user.type(screen.getByPlaceholderText("Small carton"), "Small carton");
    const [length, width, height] = screen.getAllByRole("spinbutton");
    await user.type(length, "20");
    await user.type(width, "15");
    await user.type(height, "10");
    await user.type(screen.getAllByRole("spinbutton")[3], "400");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(savePackageBox).toHaveBeenCalledWith(
      expect.objectContaining({
        name: "Small carton",
        length_cm: 20,
        breadth_cm: 15,
        height_cm: 10,
        weight_grams: 400,
      }),
    );
  });
});
