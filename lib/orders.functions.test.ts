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
  acceptOrderReturnAfterInspection,
  allocateReplacementForOrderReturn,
  approveOrderReturn,
  bootstrapProfileFromSignup,
  cancelOrder,
  cancelOrderReturn,
  checkExchangeReplacementAvailability,
  checkReturnEligibility,
  completeOrderReturnExchange,
  convertOrderReturnToRefund,
  createOrder,
  getMyProfile,
  getAdminOrder,
  getOrder,
  getReturnsConfig,
  listAdminOrderReturns,
  listAdminOrders,
  listCodPendingOrders,
  listMyNotifications,
  listMyOrderReturns,
  listMyOrders,
  listMyPayments,
  markCodCollected,
  markOrderReturnDelivered,
  markOrderReturnInspected,
  markOrderReturnPickedUp,
  markOrderReturnPickupRequested,
  markOrderReturnReadyToShip,
  markOrderReturnReceived,
  markOrderReturnRepacking,
  markOrderReturnShipped,
  refundOrderReturn,
  rejectOrderReturn,
  requestOrderExchange,
  requestOrderReturn,
  retryOrderPayment,
  syncPaymentStatus,
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
    await expect(
      createOrder({ data: { items: [], shipping_address: validAddress } } as any),
    ).rejects.toThrow();
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

  it("lists admin orders for an explicit date range", async () => {
    getMock.mockResolvedValueOnce({ orders: [{ order_number: "MNM-10" }] });

    await expect(
      listAdminOrders("2026-08-01T00:00:00.000Z", "2026-08-08T00:00:00.000Z"),
    ).resolves.toEqual({
      orders: [{ order_number: "MNM-10" }],
      page: 1,
      pageSize: 10,
      totalItems: 1,
      totalPages: 1,
    });
    expect(getMock).toHaveBeenCalledWith(
      "/api/v1/admin/orders?from=2026-08-01T00%3A00%3A00.000Z&to=2026-08-08T00%3A00%3A00.000Z&page=1&size=10",
      { auth: true },
    );
  });

  it("lists all admin orders with an explicit page", async () => {
    getMock.mockResolvedValueOnce({
      orders: [{ order_number: "MNM-20" }],
      page: 2,
      pageSize: 10,
      totalItems: 12,
      totalPages: 2,
    });

    await expect(listAdminOrders(undefined, undefined, { page: 2, size: 10 })).resolves.toMatchObject({
      page: 2,
      totalItems: 12,
      totalPages: 2,
    });
    expect(getMock).toHaveBeenCalledWith("/api/v1/admin/orders?page=2&size=10", { auth: true });

    getMock.mockResolvedValueOnce({ page: 1, pageSize: 10, totalItems: 0, totalPages: 1 });
    await expect(listAdminOrders()).resolves.toEqual({
      orders: [],
      page: 1,
      pageSize: 10,
      totalItems: 0,
      totalPages: 1,
    });
  });

  it("lists admin orders with search and status filters", async () => {
    getMock.mockResolvedValueOnce({
      orders: [{ order_number: "MNM-20" }],
      page: 1,
      pageSize: 10,
      totalItems: 1,
      totalPages: 1,
    });

    await listAdminOrders(undefined, undefined, {
      q: "Jane",
      status: "confirmed",
      paymentStatus: "paid",
      paymentMethod: "cod",
      zohoSyncStatus: "attention",
      ledgerlySyncStatus: "synced",
    });
    expect(getMock).toHaveBeenCalledWith(
      "/api/v1/admin/orders?q=Jane&status=confirmed&paymentStatus=paid&paymentMethod=cod&zohoSyncStatus=attention&ledgerlySyncStatus=synced&page=1&size=10",
      { auth: true },
    );
  });

  it("encodes search text and omits blank filters", async () => {
    getMock.mockResolvedValueOnce({ orders: [], page: 1, pageSize: 10, totalItems: 0, totalPages: 1 });

    await listAdminOrders(undefined, undefined, { q: "MNM 1%", status: "", page: 1 });
    expect(getMock).toHaveBeenCalledWith(
      "/api/v1/admin/orders?q=MNM+1%25&page=1&size=10",
      { auth: true },
    );
  });

  it("gets an order by order number", async () => {
    getMock.mockResolvedValueOnce({ order: { id: "o1", order_number: "MNM-1" } });

    const res = await getOrder({ data: { orderNumber: "MNM-1" } });
    expect(res.order.order_number).toBe("MNM-1");
    expect(getMock).toHaveBeenCalledWith("/api/v1/orders/MNM-1", { auth: true });
  });

  it("gets an admin order by order number", async () => {
    getMock.mockResolvedValueOnce({ order: { id: "o1", order_number: "MNM-1" } });

    const res = await getAdminOrder("MNM-1");
    expect(res.order.order_number).toBe("MNM-1");
    expect(getMock).toHaveBeenCalledWith("/api/v1/admin/orders/MNM-1", { auth: true });
  });

  it("cancels an owned order", async () => {
    postMock.mockResolvedValueOnce({ order: { order_number: "MNM-1", status: "cancelled" } });

    await expect(cancelOrder("MNM-1", "Ordered by mistake")).resolves.toEqual({
      order: { order_number: "MNM-1", status: "cancelled" },
    });
    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/orders/MNM-1/cancel",
      { reason: "Ordered by mistake" },
      { auth: true },
    );
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

    getMock.mockResolvedValueOnce(null);
    await expect(listAdminOrderReturns("closed")).resolves.toEqual({ returns: [] });

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

  it("syncs payment status and retries failed payments", async () => {
    postMock.mockResolvedValueOnce({ payment_status: "paid" });
    await expect(syncPaymentStatus({ data: { orderId: 7 } })).resolves.toEqual({
      payment_status: "paid",
    });
    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/orders/sync-payment",
      { orderId: 7 },
      { auth: true },
    );

    postMock.mockResolvedValueOnce({
      orderId: 7,
      orderNumber: "MNM-7",
      razorpayOrderId: "order_7",
      keyId: "rzp_test",
      amount: 49900,
      currency: "INR",
      demoMode: false,
      gatewayMode: "test",
    });
    await expect(retryOrderPayment({ data: { orderId: 7 } })).resolves.toMatchObject({
      orderNumber: "MNM-7",
      razorpayOrderId: "order_7",
    });
    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/orders/retry-payment",
      { orderId: 7 },
      { auth: true },
    );
  });

  it("rejects invalid payment sync and retry payloads", async () => {
    await expect(syncPaymentStatus({ data: { orderId: 0 } })).rejects.toThrow();
    await expect(retryOrderPayment({ data: { orderId: -1 } })).rejects.toThrow();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("lists notifications and defaults missing events to an empty array", async () => {
    getMock.mockResolvedValueOnce({
      events: [{ id: 1, event_type: "order_shipped", title: "Shipped", body: "On the way", order_number: "MNM-1", created_at: "2026-08-01" }],
    });
    await expect(listMyNotifications()).resolves.toEqual({
      events: [
        expect.objectContaining({ event_type: "order_shipped", order_number: "MNM-1" }),
      ],
    });
    expect(getMock).toHaveBeenCalledWith("/api/v1/account/notifications", { auth: true });

    getMock.mockResolvedValueOnce(null);
    await expect(listMyNotifications()).resolves.toEqual({ events: [] });
  });

  it("bootstraps profile from signup data", async () => {
    postMock.mockResolvedValueOnce({ ok: true });

    await expect(
      bootstrapProfileFromSignup({
        data: { full_name: "New Parent", email: "Parent@Example.com" },
      }),
    ).resolves.toEqual({ ok: true });
    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/account/profile/bootstrap",
      { full_name: "New Parent", email: "parent@example.com" },
      { auth: true },
    );
  });

  it("rejects invalid profile bootstrap payloads", async () => {
    await expect(
      bootstrapProfileFromSignup({ data: { full_name: "A", email: "not-an-email" } }),
    ).rejects.toThrow();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("loads returns config and exchange availability", async () => {
    getMock.mockResolvedValueOnce({ enabled: true, window_days: 14 });
    await expect(getReturnsConfig()).resolves.toEqual({ enabled: true, window_days: 14 });
    expect(getMock).toHaveBeenCalledWith("/api/v1/order-returns/config", { auth: true });

    getMock.mockResolvedValueOnce({ sellable: true });
    await expect(checkExchangeReplacementAvailability(12, "3-6m", "Navy Blue")).resolves.toEqual({
      sellable: true,
    });
    expect(getMock).toHaveBeenCalledWith(
      "/api/v1/order-returns/exchange-availability/12?size=3-6m&color=Navy%20Blue",
      { auth: true },
    );
  });

  it("requests exchanges with validated payloads", async () => {
    postMock.mockResolvedValueOnce({ return_number: "EXC-1", status: "requested", request_type: "exchange" });

    await expect(
      requestOrderExchange({
        data: {
          order_number: "MNM-9",
          order_item_id: 3,
          reason: "Need a larger size for my baby",
          replacement_size: "6-9m",
          replacement_color: "Cream",
        },
      }),
    ).resolves.toMatchObject({ return_number: "EXC-1", request_type: "exchange" });
    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/order-returns/exchange",
      expect.objectContaining({
        order_number: "MNM-9",
        order_item_id: 3,
        replacement_size: "6-9m",
      }),
      { auth: true },
    );
  });

  it("rejects invalid exchange payloads before calling the API", async () => {
    await expect(
      requestOrderExchange({
        data: {
          order_number: "MNM-9",
          order_item_id: 0,
          reason: "short",
          replacement_size: "",
          replacement_color: "Cream",
        },
      }),
    ).rejects.toThrow();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("runs admin return workflow transitions", async () => {
    const cases = [
      { fn: markOrderReturnPickupRequested, action: "pickup-requested", status: "pickup_requested" },
      { fn: markOrderReturnPickedUp, action: "picked-up", status: "picked_up" },
      { fn: markOrderReturnInspected, action: "inspect", status: "inspected" },
      { fn: acceptOrderReturnAfterInspection, action: "accept", status: "accepted" },
      { fn: allocateReplacementForOrderReturn, action: "allocate-replacement", status: "allocated" },
      { fn: convertOrderReturnToRefund, action: "convert-to-refund", status: "refund_pending" },
      { fn: markOrderReturnRepacking, action: "repacking", status: "repacking" },
      { fn: markOrderReturnReadyToShip, action: "ready-to-ship", status: "ready_to_ship" },
      { fn: markOrderReturnShipped, action: "shipped", status: "shipped" },
      { fn: markOrderReturnDelivered, action: "delivered", status: "delivered" },
      { fn: completeOrderReturnExchange, action: "complete", status: "completed" },
    ] as const;

    for (const { fn, action, status } of cases) {
      postMock.mockResolvedValueOnce({ return_number: "RET-10", status });
      await expect(fn("RET-10", "handled")).resolves.toEqual({ return_number: "RET-10", status });
      expect(postMock).toHaveBeenLastCalledWith(
        `/api/v1/admin/order-returns/RET-10/${action}`,
        { admin_notes: "handled" },
        { auth: true },
      );
    }

    postMock.mockResolvedValueOnce({ return_number: "RET-11", status: "pickup_requested" });
    await expect(markOrderReturnPickupRequested("RET-11")).resolves.toEqual({
      return_number: "RET-11",
      status: "pickup_requested",
    });
    expect(postMock).toHaveBeenLastCalledWith(
      "/api/v1/admin/order-returns/RET-11/pickup-requested",
      { admin_notes: "" },
      { auth: true },
    );
  });

  it("lists COD pending orders and marks COD collected", async () => {
    getMock.mockResolvedValueOnce({
      orders: [{ id: 1, order_number: "MNM-COD-1", payment_method: "cod", payment_status: "pending" }],
    });
    await expect(listCodPendingOrders()).resolves.toEqual({
      orders: [expect.objectContaining({ order_number: "MNM-COD-1" })],
    });
    expect(getMock).toHaveBeenCalledWith("/api/v1/admin/orders/cod-pending", { auth: true });

    getMock.mockResolvedValueOnce({});
    await expect(listCodPendingOrders()).resolves.toEqual({ orders: [] });

    postMock.mockResolvedValueOnce({ order_number: "MNM-COD-1", payment_status: "paid" });
    await expect(markCodCollected("MNM-COD-1")).resolves.toEqual({
      order_number: "MNM-COD-1",
      payment_status: "paid",
    });
    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/admin/orders/MNM-COD-1/mark-cod-collected",
      undefined,
      { auth: true },
    );
  });
});
