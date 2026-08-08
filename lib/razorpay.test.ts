import { describe, expect, it } from "vitest";
import { razorpayDisplayConfig } from "./razorpay";

describe("razorpayDisplayConfig", () => {
  it("hides every online method except the selected one", () => {
    expect(razorpayDisplayConfig("card")).toEqual({
      display: {
        hide: [
          { method: "upi" },
          { method: "netbanking" },
          { method: "wallet" },
          { method: "emi" },
          { method: "paylater" },
        ],
        preferences: { show_default_blocks: true },
      },
    });
  });

  it("keeps only UPI visible when UPI is selected", () => {
    const hidden = razorpayDisplayConfig("upi").display.hide.map((h) => h.method);
    expect(hidden).toEqual(["card", "netbanking", "wallet", "emi", "paylater"]);
  });
});
