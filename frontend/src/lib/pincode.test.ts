import { describe, expect, it, vi } from "vitest";
import {
  deliveryEtaForDistrict,
  lookupPincode,
  normalizePincode,
  parsePostalPincodeResponse,
  validatePincode,
} from "./pincode";

describe("pincode helpers", () => {
  it("normalizes non-digits and caps at six characters", () => {
    expect(normalizePincode("56a000-1")).toBe("560001");
    expect(normalizePincode("1234567890")).toBe("123456");
  });

  it("validates Indian pincodes", () => {
    expect(validatePincode("560001")).toEqual({ ok: true, pin: "560001" });
    expect(validatePincode("56001")).toEqual({ ok: false, error: "Pincode must be 6 digits" });
    expect(validatePincode("056001")).toEqual({ ok: false, error: "Enter a valid Indian pincode" });
    expect(validatePincode("")).toEqual({ ok: false, error: "Enter a 6-digit pincode" });
  });

  it("returns metro vs non-metro delivery estimates", () => {
    expect(deliveryEtaForDistrict("Bangalore")).toBe("2–3 days");
    expect(deliveryEtaForDistrict("Jaipur")).toBe("4–6 days");
  });

  it("parses postal API payloads", () => {
    const lookup = parsePostalPincodeResponse([
      {
        PostOffice: [{ District: "Bangalore", State: "Karnataka" }],
      },
    ]);
    expect(lookup).toEqual({
      city: "Bangalore",
      state: "Karnataka",
      eta: "2–3 days",
    });
    expect(parsePostalPincodeResponse([])).toBeNull();
    expect(parsePostalPincodeResponse([{ PostOffice: [{}] }])).toBeNull();
    expect(parsePostalPincodeResponse([{ PostOffice: [{ District: "  ", State: "Karnataka" }] }])).toBeNull();
  });

  it("looks up pincode through the storefront API", async () => {
    const fetcher = vi.fn().mockResolvedValue({ city: "Rajbhavan", state: "Karnataka", eta: "2–3 days" });
    await expect(lookupPincode("560001", fetcher)).resolves.toEqual({
      ok: true,
      lookup: { city: "Rajbhavan", state: "Karnataka", eta: "2–3 days" },
    });
    expect(fetcher).toHaveBeenCalledWith("/api/v1/pincode/560001");
    await expect(lookupPincode("56001", fetcher)).resolves.toMatchObject({ ok: false });
  });

  it("uses the default storefront fetcher when none is provided", async () => {
    vi.resetModules();
    const getMock = vi.fn().mockResolvedValue({ city: "Bengaluru", state: "Karnataka", eta: "2–3 days" });
    vi.doMock("@/lib/api", () => ({
      api: { get: getMock },
    }));
    const { lookupPincode: lookup } = await import("./pincode");
    await expect(lookup("560001")).resolves.toEqual({
      ok: true,
      lookup: { city: "Bengaluru", state: "Karnataka", eta: "2–3 days" },
    });
    expect(getMock).toHaveBeenCalledWith("/api/v1/pincode/560001");
    vi.doUnmock("@/lib/api");
  });

  it("rejects incomplete lookups and network failures", async () => {
    const incomplete = vi.fn().mockResolvedValue({ city: " ", state: "Karnataka", eta: "2–3 days" });
    await expect(lookupPincode("560001", incomplete)).resolves.toEqual({
      ok: false,
      error: "Delivery isn't available for this pincode yet. Try another pincode or contact us on WhatsApp.",
    });

    const missing = vi.fn().mockResolvedValue(null);
    await expect(lookupPincode("522235", missing)).resolves.toMatchObject({ ok: false });

    const failing = vi.fn().mockRejectedValue(new Error("offline"));
    await expect(lookupPincode("560001", failing)).resolves.toEqual({
      ok: false,
      error: "Couldn't check delivery just now. Please try again.",
    });

    const { ApiError } = await import("@/lib/api");
    const notFound = vi.fn().mockRejectedValue(new ApiError("missing", 404, null));
    await expect(lookupPincode("560001", notFound)).resolves.toMatchObject({
      ok: false,
      error: "Delivery isn't available for this pincode yet. Try another pincode or contact us on WhatsApp.",
    });

    const badRequest = vi.fn().mockRejectedValue(new ApiError("invalid pin", 400, null));
    await expect(lookupPincode("560001", badRequest)).resolves.toMatchObject({
      ok: false,
      error: "Delivery isn't available for this pincode yet. Try another pincode or contact us on WhatsApp.",
    });
  });
});
