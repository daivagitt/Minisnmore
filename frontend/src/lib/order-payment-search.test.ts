import { describe, expect, it } from "vitest";
import { parseOrderPaymentSearch } from "./order-payment-search";

describe("order payment search", () => {
  it.each([true, 1, "1", "true"])("accepts a serialized payment flag: %s", (pay) => {
    expect(parseOrderPaymentSearch({ pay })).toEqual({ pay: true });
  });
  it.each([undefined, null, false, 0, "0", "false", "yes"])("does not start payment for a false or unknown flag: %s", (pay) => {
    expect(parseOrderPaymentSearch({ pay })).toEqual({ pay: false });
  });
});
