import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import {
  bookAdminOrderShipment,
  completeAdminOrderPackage,
  previewAdminOrderPackageRates,
} from "@/lib/orders.functions";
import { BookShipmentDialog } from "./BookShipmentDialog";
import { CreatePackageDialog } from "./CreatePackageDialog";
import type { AdminOrderRow } from "@/lib/orders.functions";

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

vi.mock("@/lib/orders.functions", async () => {
  const actual = await vi.importActual<typeof import("@/lib/orders.functions")>("@/lib/orders.functions");
  return {
    ...actual,
    previewAdminOrderPackageRates: vi.fn().mockResolvedValue({
      actual_weight_grams: 500,
      volumetric_weight_grams: 400,
      chargeable_weight_grams: 500,
      couriers: [{ id: "xb-1", name: "Xpressbees 2 K.G", rate: 219, preferred: true }],
    }),
    completeAdminOrderPackage: vi.fn().mockResolvedValue({}),
    bookAdminOrderShipment: vi.fn().mockResolvedValue({ order: {} }),
  };
});

function order(overrides: Partial<AdminOrderRow> = {}): AdminOrderRow {
  return {
    id: 18,
    order_number: "MNM-18",
    total: 100,
    cod_fee: 0,
    status: "confirmed",
    placed_at: "2026-09-30T00:00:00Z",
    payment_status: "paid",
    payment_method: "cod",
    zoho_sync_status: "",
    zoho_sync_error: "",
    zoho_last_synced_at: "",
    zoho_sales_order_id: "",
    zoho_invoice_id: "",
    zoho_sync_eligible: false,
    ledgerly_sync_status: "",
    ledgerly_sync_error: "",
    ledgerly_last_synced_at: "",
    ledgerly_sales_order_id: "",
    ledgerly_invoice_id: "",
    ledgerly_sync_eligible: false,
    default_package_weight_grams: 500,
    package_length_cm: 10,
    package_breadth_cm: 8,
    package_height_cm: 4,
    dispatch_address: { name: "Head Office", city: "Bengaluru", state: "Karnataka", pincode: "560074" },
    shipping_address: { full_name: "Sai", city: "Bangalore", state: "Karnataka", pincode: "560100" },
    order_items: [{ id: 1, product_name: "Body Suit", size: "6-12 M", color: "Snail", sku: "MN-12-612M-P01-173", qty: 1 }],
    can_book_expressbees_shipment: true,
    can_complete_package: true,
    ...overrides,
  };
}

describe("Zoho-style fulfillment dialogs", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(previewAdminOrderPackageRates).mockResolvedValue({
      actual_weight_grams: 500,
      volumetric_weight_grams: 400,
      chargeable_weight_grams: 500,
      preferred_courier_id: "xb-1",
      preferred_courier_name: "Xpressbees 2 K.G",
      couriers: [{ id: "xb-1", name: "Xpressbees 2 K.G", rate: 219, preferred: true }],
    });
    vi.mocked(completeAdminOrderPackage).mockResolvedValue({});
    vi.mocked(bookAdminOrderShipment).mockResolvedValue({ order: {} });
  });

  it("shows New Package fields from the sales order", () => {
    render(<CreatePackageDialog order={order()} open onOpenChange={() => {}} onSaved={() => {}} />);
    expect(screen.getByText("New Package")).toBeTruthy();
    expect(screen.getByDisplayValue("PKG-00018")).toBeTruthy();
    expect(screen.getByText(/Package Slip#/)).toBeTruthy();
    expect(screen.getByLabelText("Package type")).toHaveValue("custom");
    expect(screen.getByText("Specify custom dimensions")).toBeTruthy();
    expect(screen.getByText("Box")).toBeTruthy();
    expect(screen.getByText("Envelope")).toBeTruthy();
    expect(screen.getByText("All order line items are included in this package. Choose a Zoho parcel type, then save weight and dimensions. Package date is display only.")).toBeTruthy();
    expect(screen.getByText("Body Suit — 6-12 M — Snail")).toBeTruthy();
    expect(screen.queryByText("Stock on Hand")).toBeNull();
    expect(screen.queryByText("Head Office")).toBeNull();
  });

  it("saves weight and dimensions through complete-package", async () => {
    const user = userEvent.setup();
    render(<CreatePackageDialog order={order()} open onOpenChange={() => {}} onSaved={() => {}} />);
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(completeAdminOrderPackage).toHaveBeenCalledWith(
      "MNM-18",
      expect.objectContaining({
        length_cm: 10,
        breadth_cm: 8,
        height_cm: 4,
        package_type: "custom",
      }),
    );
  });

  it("prefills envelope dimensions when reopening a package without saved cm fields", () => {
    render(
      <CreatePackageDialog
        order={order({
          package_type: "envelope",
          package_length_cm: 0,
          package_breadth_cm: 0,
          package_height_cm: 0,
        })}
        open
        onOpenChange={() => {}}
        onSaved={() => {}}
      />,
    );
    expect(screen.getByLabelText("Length")).toHaveValue(32);
    expect(screen.getByLabelText("Width")).toHaveValue(24);
    expect(screen.getByLabelText("Height")).toHaveValue(2);
  });

  it("fills standard envelope dimensions when Envelope is selected", async () => {
    const user = userEvent.setup();
    render(<CreatePackageDialog order={order()} open onOpenChange={() => {}} onSaved={() => {}} />);
    await user.selectOptions(screen.getByLabelText("Package type"), "envelope");
    expect(screen.getByLabelText("Length")).toHaveValue(32);
    expect(screen.getByLabelText("Width")).toHaveValue(24);
    expect(screen.getByLabelText("Height")).toHaveValue(2);
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(completeAdminOrderPackage).toHaveBeenCalledWith(
      "MNM-18",
      expect.objectContaining({
        length_cm: 32,
        breadth_cm: 24,
        height_cm: 2,
        package_type: "envelope",
      }),
    );
  });

  it("walks Validate Address then blocks Create Shipment until a package exists", async () => {
    const user = userEvent.setup();
    render(
      <BookShipmentDialog
        order={order({ packed_at: "" })}
        open
        requiresOverride={false}
        onOpenChange={() => {}}
        onSaved={() => {}}
      />,
    );
    expect(screen.getByText("New Shipment")).toBeTruthy();
    expect(screen.getByText("Validate Address")).toBeTruthy();
    expect(screen.getByText(/Dispatch Address/)).toBeTruthy();
    expect(screen.getByText(/Destination Address/)).toBeTruthy();
    expect(screen.queryByText("Change Address")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Save & Continue" }));
    expect(toast.error).toHaveBeenCalledWith("Create a package before booking a shipment");
    expect(screen.queryByText("Shipment Order#*")).toBeNull();
  });

  it("opens Create Shipment with package slip, delivery status and shipping methods step", async () => {
    const user = userEvent.setup();
    render(
      <BookShipmentDialog
        order={order({ packed_at: "2026-09-30T00:00:00Z" })}
        open
        requiresOverride={false}
        onOpenChange={() => {}}
        onSaved={() => {}}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Save & Continue" }));
    expect(screen.getAllByText("Create Shipment").length).toBeGreaterThan(0);
    expect(screen.getByDisplayValue("PKG-00018")).toBeTruthy();
    expect(screen.getByDisplayValue("SHP-00018")).toBeTruthy();
    expect(screen.getByText("MiniMS Delivery Status")).toBeTruthy();
    expect(screen.getByText("Auto-fill from Package")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Create Shipment" })).toBeTruthy();
  });

  it("does not complete the package again from the shipment wizard", async () => {
    const user = userEvent.setup();
    render(
      <BookShipmentDialog
        order={order({ packed_at: "2026-09-30T00:00:00Z" })}
        open
        requiresOverride={false}
        onOpenChange={() => {}}
        onSaved={() => {}}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Save & Continue" }));
    await screen.findByRole("button", { name: "Create Shipment" });
    await user.click(screen.getByRole("button", { name: "Create Shipment" }));
    expect(completeAdminOrderPackage).not.toHaveBeenCalled();
    expect(bookAdminOrderShipment).toHaveBeenCalledWith(
      "MNM-18",
      expect.objectContaining({ package_type: "custom" }),
    );
  });

  it("fills envelope dimensions on the shipment step and persists parcel type on book", async () => {
    const user = userEvent.setup();
    render(
      <BookShipmentDialog
        order={order({ packed_at: "2026-09-30T00:00:00Z", package_type: "custom" })}
        open
        requiresOverride={false}
        onOpenChange={() => {}}
        onSaved={() => {}}
      />,
    );
    await user.selectOptions(screen.getByRole("combobox"), "manual");
    await user.click(screen.getByRole("button", { name: "Save & Continue" }));
    await user.selectOptions(screen.getByLabelText("Parcel type"), "envelope");
    expect(screen.getByLabelText("Length")).toHaveValue(32);
    expect(screen.getByLabelText("Width")).toHaveValue(24);
    expect(screen.getByLabelText("Height")).toHaveValue(2);
    await user.type(screen.getByLabelText(/AWB/i), "AWB-12345");
    await user.click(screen.getByRole("button", { name: "Create Shipment" }));
    expect(bookAdminOrderShipment).toHaveBeenCalledWith(
      "MNM-18",
      expect.objectContaining({ package_type: "envelope" }),
    );
  });

  it("auto-fills saved package weight and dimensions in kg/cm", async () => {
    const user = userEvent.setup();
    render(
      <BookShipmentDialog
        order={order({ packed_at: "2026-09-30T00:00:00Z", package_weight_grams: 500 })}
        open
        requiresOverride={false}
        onOpenChange={() => {}}
        onSaved={() => {}}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Save & Continue" }));
    expect(screen.getByLabelText("Weight")).toHaveValue(0.5);
    expect(screen.getByLabelText("Length")).toHaveValue(10);
    expect(screen.getByLabelText("Width")).toHaveValue(8);
    expect(screen.getByLabelText("Height")).toHaveValue(4);
    expect(screen.getByDisplayValue("kg")).toBeTruthy();
    expect(screen.getByDisplayValue("cm")).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Auto-fill from Package" }));
    expect(screen.getByLabelText("Weight")).toHaveValue(0.5);
    expect(screen.getByLabelText("Length")).toHaveValue(10);
  });

  it("uses the saved Zoho package type on the shipment form", async () => {
    const user = userEvent.setup();
    render(
      <BookShipmentDialog
        order={order({ packed_at: "2026-09-30T00:00:00Z", package_type: "envelope" })}
        open
        requiresOverride={false}
        onOpenChange={() => {}}
        onSaved={() => {}}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Save & Continue" }));
    expect(screen.getByLabelText("Parcel type")).toHaveValue("envelope");
  });

  it("shows courier prices without ETA days", async () => {
    vi.mocked(previewAdminOrderPackageRates).mockResolvedValue({
      actual_weight_grams: 500,
      volumetric_weight_grams: 200,
      chargeable_weight_grams: 500,
      preferred_courier_id: "xb-1",
      preferred_courier_name: "Surface Xpressbees 0.5 K.G",
      couriers: [
        { id: "xb-1", name: "Surface Xpressbees 0.5 K.G", rate: 0, edd_days: 3, eta: "3 days", preferred: true },
        { id: "xb-2", name: "Xpressbees 1 K.G", rate: 48, edd_days: 2, eta: "2 days" },
      ],
    });
    const user = userEvent.setup();
    render(
      <BookShipmentDialog
        order={order({ packed_at: "2026-09-30T00:00:00Z", package_weight_grams: 500 })}
        open
        requiresOverride={false}
        onOpenChange={() => {}}
        onSaved={() => {}}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Save & Continue" }));
    expect(await screen.findByText("Surface Xpressbees 0.5 K.G")).toBeTruthy();
    expect(screen.getByText("Rate on booking")).toBeTruthy();
    expect(screen.getByText("₹48")).toBeTruthy();
    expect(screen.queryByText("₹0")).toBeNull();
    expect(screen.queryByText(/days/)).toBeNull();
  });
});
