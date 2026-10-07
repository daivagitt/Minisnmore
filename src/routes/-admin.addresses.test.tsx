import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./admin.addresses";

const listStoreAddresses = vi.hoisted(() => vi.fn());
const saveStoreAddress = vi.hoisted(() => vi.fn());

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
}));

vi.mock("@/lib/store-addresses", async () => {
  const actual = await vi.importActual<typeof import("@/lib/store-addresses")>("@/lib/store-addresses");
  return {
    ...actual,
    listStoreAddresses,
    saveStoreAddress,
    deleteStoreAddress: vi.fn(),
  };
});

vi.mock("@/lib/pincode", () => ({
  lookupPincode: vi.fn(),
  normalizePincode: (value: string) => value.replace(/\D/g, "").slice(0, 6),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

function renderAddresses() {
  const Component = (Route as { component: () => JSX.Element }).component;
  return render(<Component />);
}

describe("admin addresses page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listStoreAddresses.mockResolvedValue([]);
    saveStoreAddress.mockResolvedValue({});
  });

  it("lets admin add an invoice address", async () => {
    const user = userEvent.setup();
    renderAddresses();

    await waitFor(() => expect(screen.getByRole("button", { name: /new address/i })).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /new address/i }));

    expect(screen.getByRole("option", { name: /shipment/i })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /normal address/i })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /invoice/i })).toBeInTheDocument();

    await user.selectOptions(screen.getByRole("combobox"), "invoice");
    expect(screen.getByText(/default invoice/i)).toBeInTheDocument();
  });
});
