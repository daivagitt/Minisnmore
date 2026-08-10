import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tanstack/react-start", () => ({
  createServerFn: () => ({
    middleware() {
      return this;
    },
    handler(fn: ({ data }: { data: any }) => Promise<any>) {
      return async ({ data }: { data?: unknown }) => fn({ data });
    },
    inputValidator(validator: (d: unknown) => any) {
      return {
        handler(fn: ({ data }: { data: any }) => Promise<any>) {
          return async ({ data }: { data: unknown }) => fn({ data: validator(data) });
        },
      };
    },
  }),
}));

const postMock = vi.hoisted(() => vi.fn());
const getMock = vi.hoisted(() => vi.fn());
const putMock = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", () => ({
  api: {
    post: postMock,
    get: getMock,
    put: putMock,
  },
}));

vi.mock("./coupons.functions", () => ({
  validateCoupon: vi.fn(async () => ({ ok: true, code: "WELCOME10", discount: 100 })),
}));

import {
  approveOrderReturn,
  cancelOrderReturn,
  checkReturnEligibility,
  createOrder,
  getMyProfile,
  getOrder,
  listAdminOrderReturns,
  listMyOrderReturns,
  listMyOrders,
  listMyPayments,
  markOrderReturnReceived,
  refundOrderReturn,
  rejectOrderReturn,
  requestOrderReturn,
  trackOrderPublic,
  updateMyProfile,
  verifyPayment,
} from "./orders.functions";

const validAddress = {
  full_name: "Parent Name",
  email: "parent@example.com",
  phone: "9876543210",
  line1: "Street 1",
  city: "Bengaluru",
  state: "KA",
  pincode: "560001",
};

const validItem = {
  product_id: "p1",
  product_name: "Romper",
  qty: 2,
  price: 499,
};

describe("orders functions", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("creates an order via catalog API", async () => {
    postMock.mockResolvedValueOnce({ orderId: 42, orderNumber: "MNM-42" });

    await expect(
      createOrder({
        data: {
          items: [validItem],
          shipping_address: validAddress,
          shipping_mode: "exp",
          coupon_code: "welcome10",
        },
      }),
    ).resolves.toMatchObject({ orderId: 42 });

    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/orders",
      expect.objectContaining({ shipping_mode: "exp" }),
      { auth: true },
    );
  });

  it("rejects invalid order payloads before calling the API", async () => {
    await expect(createOrder({ data: { items: [], shipping_address: validAddress } } as any)).rejects.toThrow();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("verifies payment through catalog API", async () => {
    postMock.mockResolvedValueOnce({ order_number: "MNM-1", payment_status: "paid" });

    await verifyPayment({
      data: {
        orderId: 1,
        razorpay_order_id: "order_1",
        razorpay_payment_id: "pay_1",
        razorpay_signature: "sig",
      },
    });

    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/orders/verify-payment",
      expect.objectContaining({ orderId: 1 }),
      { auth: true },
    );
  });

  it("rejects invalid payment verification ids", async () => {
    await expect(
      verifyPayment({
        data: {
          orderId: "abc" as any,
          razorpay_order_id: "order",
          razorpay_payment_id: "payment",
          razorpay_signature: "sig",
        },
      }),
    ).rejects.toThrow();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("propagates API errors from verify-payment", async () => {
    postMock.mockRejectedValueOnce(new Error("Payment signature mismatch"));

    await expect(
      verifyPayment({
        data: {
          orderId: 1,
          razorpay_order_id: "order_1",
          razorpay_payment_id: "pay_1",
          razorpay_signature: "bad_sig",
        },
      }),
    ).rejects.toThrow("Payment signature mismatch");
  });

  it("lists orders from catalog API", async () => {
    getMock.mockResolvedValueOnce({ orders: [{ id: "o1" }] });

    const res = await listMyOrders();
    expect(res.orders).toEqual([{ id: "o1" }]);
    expect(getMock).toHaveBeenCalledWith("/api/v1/orders", { auth: true });
  });

  it("gets an order by order number", async () => {
    getMock.mockResolvedValueOnce({ order: { id: "o1", order_number: "MNM-1" } });

    const res = await getOrder({ data: { orderNumber: "MNM-1" } });
    expect(res.order.order_number).toBe("MNM-1");
    expect(getMock).toHaveBeenCalledWith("/api/v1/orders/MNM-1", { auth: true });
  });

  it("tracks orders publicly and manages return workflows", async () => {
    getMock.mockResolvedValueOnce({ order: { order_number: "MNM-9" } });
    await expect(trackOrderPublic({ data: { orderNumber: "MNM-9" } })).resolves.toEqual({
      order: { order_number: "MNM-9" },
    });

    getMock.mockResolvedValueOnce([{ return_number: "RET-1" }]);
    await expect(listMyOrderReturns()).resolves.toEqual({ returns: [{ return_number: "RET-1" }] });

    getMock.mockResolvedValueOnce({ eligible: true, order_number: "MNM-9" });
    await expect(checkReturnEligibility("MNM-9")).resolves.toEqual({
      eligible: true,
      order_number: "MNM-9",
    });

    postMock.mockResolvedValueOnce({ return_number: "RET-2" });
    await expect(
      requestOrderReturn({ data: { order_number: "MNM-9", reason: "Wrong size received" } }),
    ).resolves.toEqual({ return_number: "RET-2" });

    postMock.mockResolvedValueOnce({ return_number: "RET-2", status: "cancelled" });
    await expect(cancelOrderReturn("RET-2")).resolves.toEqual({
      return_number: "RET-2",
      status: "cancelled",
    });

    getMock.mockResolvedValueOnce(null);
    await expect(listMyOrderReturns()).resolves.toEqual({ returns: [] });

    getMock.mockResolvedValueOnce([{ return_number: "RET-3", status: "requested" }]);
    await expect(listAdminOrderReturns("requested")).resolves.toEqual({
      returns: [{ return_number: "RET-3", status: "requested" }],
    });

    getMock.mockResolvedValueOnce([{ return_number: "RET-4", status: "open" }]);
    await expect(listAdminOrderReturns()).resolves.toEqual({
      returns: [{ return_number: "RET-4", status: "open" }],
    });

    postMock.mockResolvedValueOnce({ return_number: "RET-3", status: "approved" });
    await expect(approveOrderReturn("RET-3", "ok")).resolves.toEqual({
      return_number: "RET-3",
      status: "approved",
    });

    postMock.mockResolvedValueOnce({ return_number: "RET-5", status: "approved" });
    await expect(approveOrderReturn("RET-5")).resolves.toEqual({
      return_number: "RET-5",
      status: "approved",
    });
    expect(postMock).toHaveBeenLastCalledWith(
      "/api/v1/admin/order-returns/RET-5/approve",
      { admin_notes: "" },
      { auth: true },
    );

    postMock.mockResolvedValueOnce({ return_number: "RET-3", status: "rejected" });
    await expect(rejectOrderReturn("RET-3", "damaged", "note")).resolves.toEqual({
      return_number: "RET-3",
      status: "rejected",
    });

    postMock.mockResolvedValueOnce({ return_number: "RET-6", status: "rejected" });
    await expect(rejectOrderReturn("RET-6", "damaged")).resolves.toEqual({
      return_number: "RET-6",
      status: "rejected",
    });
    expect(postMock).toHaveBeenLastCalledWith(
      "/api/v1/admin/order-returns/RET-6/reject",
      { rejection_reason: "damaged", admin_notes: "" },
      { auth: true },
    );

    postMock.mockResolvedValueOnce({ return_number: "RET-3", status: "received" });
    await expect(markOrderReturnReceived("RET-3")).resolves.toEqual({
      return_number: "RET-3",
      status: "received",
    });

    postMock.mockResolvedValueOnce({ return_number: "RET-7", status: "received" });
    await expect(markOrderReturnReceived("RET-7")).resolves.toEqual({
      return_number: "RET-7",
      status: "received",
    });

    postMock.mockResolvedValueOnce({ return_number: "RET-3", status: "refunded" });
    await expect(refundOrderReturn("RET-3")).resolves.toEqual({
      return_number: "RET-3",
      status: "refunded",
    });

    postMock.mockResolvedValueOnce({ return_number: "RET-8", status: "refunded" });
    await expect(refundOrderReturn("RET-8")).resolves.toEqual({
      return_number: "RET-8",
      status: "refunded",
    });
  });

  it("lists payments", async () => {
    getMock.mockResolvedValueOnce({ payments: [{ id: "p1" }] });

    const res = await listMyPayments();
    expect(res.payments).toEqual([{ id: "p1" }]);
    expect(getMock).toHaveBeenCalledWith("/api/v1/payments", { auth: true });
  });

  it("loads profile from account API", async () => {
    getMock.mockResolvedValueOnce({ full_name: "Seed User", phone: "90000" });

    const res = await getMyProfile();
    expect(res.profile).toEqual({ full_name: "Seed User", phone: "90000" });
    expect(getMock).toHaveBeenCalledWith("/api/v1/account/profile", { auth: true });
  });

  it("defaults missing profile fields to empty strings", async () => {
    getMock.mockResolvedValueOnce({});

    const res = await getMyProfile();
    expect(res.profile).toEqual({ full_name: "", phone: "" });
  });

  it("updates profile", async () => {
    putMock.mockResolvedValueOnce({ ok: true });

    const res = await updateMyProfile({
      data: { full_name: "  New Name  ", phone: "  9876543210  " },
    });
    expect(res.ok).toBe(true);
    expect(putMock).toHaveBeenCalledWith(
      "/api/v1/account/profile",
      { full_name: "New Name", phone: "9876543210" },
      { auth: true },
    );
  });

  it("rejects an invalid phone number when updating profile", async () => {
    await expect(
      updateMyProfile({ data: { full_name: "New Name", phone: "12345" } }),
    ).rejects.toThrow();
  });

  it("rejects a full name containing digits", async () => {
    await expect(
      updateMyProfile({ data: { full_name: "New Name 2", phone: "9876543210" } }),
    ).rejects.toThrow();
  });

  it("validates full name for registration-style checks", async () => {
    const { fullNameFieldError, FullNameSchema } = await import("./orders.functions");
    expect(fullNameFieldError("")).toMatch(/at least 2 characters/i);
    expect(fullNameFieldError("A")).toMatch(/at least 2 characters/i);
    expect(fullNameFieldError("Ananya Sharma")).toBeUndefined();
    expect(fullNameFieldError("Name123")).toMatch(/letters/i);
    expect(FullNameSchema.parse("O'Brien")).toBe("O'Brien");
  });
});
