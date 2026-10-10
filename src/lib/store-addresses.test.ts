import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import {
  fromStoreAddressDto,
  getStoreAddressValidationErrors,
  listStoreAddresses,
  saveStoreAddress,
  toStoreAddressDto,
} from "./store-addresses";

vi.mock("@/lib/api", () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

const mockedApi = api as unknown as {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
  put: ReturnType<typeof vi.fn>;
};

describe("store addresses", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("maps usages between UI and API", () => {
    expect(
      fromStoreAddressDto({
        id: 3,
        fullName: "Warehouse",
        phone: "9876543210",
        line1: "12 Flower Lane",
        city: "Bengaluru",
        state: "Karnataka",
        pincode: "560001",
        usages: ["SHIPMENT", "INVOICE"],
        isDefault: true,
      }).usages,
    ).toEqual(["shipment", "invoice"]);
    expect(
      fromStoreAddressDto({
        id: 4,
        fullName: "Warehouse",
        phone: "9876543210",
        line1: "12 Flower Lane",
        city: "Bengaluru",
        state: "Karnataka",
        pincode: "560001",
        usage: "NORMAL",
        is_default: true,
      }).usages,
    ).toEqual(["normal"]);
    expect(
      toStoreAddressDto({
        label: "HQ",
        full_name: "Mini's n More",
        phone: "9876543210",
        line1: "12 Flower Lane",
        line2: "",
        city: "Bengaluru",
        state: "Karnataka",
        pincode: "560001",
        country: "India",
        usages: ["shipment", "normal", "invoice"],
        is_default: true,
      }).usages,
    ).toEqual(["SHIPMENT", "NORMAL", "INVOICE"]);
  });

  it("requires a complete address and at least one usage", () => {
    expect(
      getStoreAddressValidationErrors({
        label: "",
        full_name: "",
        phone: "",
        line1: "",
        line2: "",
        city: "",
        state: "",
        pincode: "",
        country: "India",
        usages: [],
        is_default: false,
      }),
    ).not.toHaveLength(0);
  });

  it("lists and creates admin store addresses", async () => {
    mockedApi.get.mockResolvedValueOnce([
      {
        id: 1,
        fullName: "Warehouse",
        phone: "9876543210",
        line1: "12 Flower Lane",
        city: "Bengaluru",
        state: "Karnataka",
        pincode: "560001",
        usages: ["SHIPMENT"],
        isDefault: true,
      },
    ]);
    const rows = await listStoreAddresses();
    expect(mockedApi.get).toHaveBeenCalledWith("/api/v1/admin/store-addresses", { auth: true });
    expect(rows[0]?.usages).toEqual(["shipment"]);

    mockedApi.post.mockResolvedValueOnce({
      id: 2,
      fullName: "Stolt Global",
      phone: "9876543210",
      line1: "12 Flower Lane extra",
      city: "Bengaluru",
      state: "Karnataka",
      pincode: "560001",
      usages: ["INVOICE"],
      isDefault: false,
    });
    await saveStoreAddress({
      label: "Sold by",
      full_name: "Stolt Global",
      phone: "9876543210",
      line1: "12 Flower Lane extra",
      line2: "",
      city: "Bengaluru",
      state: "Karnataka",
      pincode: "560001",
      country: "India",
      usages: ["invoice"],
      is_default: false,
    });
    expect(mockedApi.post).toHaveBeenCalledWith(
      "/api/v1/admin/store-addresses",
      expect.objectContaining({ usages: ["INVOICE"], fullName: "Stolt Global" }),
      { auth: true },
    );
  });
});
