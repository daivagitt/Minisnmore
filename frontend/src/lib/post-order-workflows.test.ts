import { beforeEach, describe, expect, it, vi } from "vitest";
import * as orders from "./orders.functions";

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api", () => ({ api }));

beforeEach(() => vi.resetAllMocks());

describe("customer cancellation and admin resolution", () => {
  it("withdraws an owned cancellation request without changing the order number", async () => {
    api.post.mockResolvedValue({ order: { order_number: "MNM-42" } });
    await expect(orders.withdrawCancelRequest("MNM-42")).resolves.toMatchObject({ order: { order_number: "MNM-42" } });
    expect(api.post).toHaveBeenCalledWith("/api/v1/orders/MNM-42/cancel-request/withdraw", {}, { auth: true });
  });

  it("filters admin requests and treats an empty response as an empty queue", async () => {
    api.get.mockResolvedValueOnce([{ request_number: "CAN-42", status: "pending" }]).mockResolvedValueOnce(null);
    await expect(orders.listAdminCancellationRequests("pending review")).resolves.toEqual({ requests: [{ request_number: "CAN-42", status: "pending" }] });
    expect(api.get).toHaveBeenLastCalledWith("/api/v1/admin/cancellation-requests?status=pending%20review", { auth: true });
    await expect(orders.listAdminCancellationRequests()).resolves.toEqual({ requests: [] });
    expect(api.get).toHaveBeenLastCalledWith("/api/v1/admin/cancellation-requests", { auth: true });
  });

  it.each([undefined, "Customer contacted support"])("sends the admin resolution and refund audit data (%s)", async (note) => {
    await orders.approveCancellationRequest("CAN/42", note);
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/admin/cancellation-requests/CAN%2F42/approve", { admin_notes: note ?? "" }, { auth: true });
    await orders.rejectCancellationRequest("CAN/42", "Already dispatched", note);
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/admin/cancellation-requests/CAN%2F42/reject", { rejection_reason: "Already dispatched", admin_notes: note ?? "" }, { auth: true });
    await orders.retryCancellationRefund("CAN/42");
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/admin/cancellation-requests/CAN%2F42/retry-refund", {}, { auth: true });
    const refund = { refund_method: "upi" as const, refund_reference: "UTR-42", admin_notes: note };
    await orders.recordCancellationCodRefund("CAN/42", refund);
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/admin/cancellation-requests/CAN%2F42/record-cod-refund", refund, { auth: true });
  });

  it("preserves API failures so a failed refund cannot be reported as successful", async () => {
    api.post.mockRejectedValue(new Error("Refund provider unavailable"));
    await expect(orders.retryCancellationRefund("CAN-42")).rejects.toThrow("Refund provider unavailable");
  });
});

describe("return pickup, outbound shipment, and cash refund", () => {
  it("passes carrier references and manual refund evidence to authenticated endpoints", async () => {
    const pickup = { mode: "manual" as const, reverse_awb: "REV-42", pickup_window: "tomorrow" };
    await orders.bookOrderReturnPickup("RET/42", pickup);
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/admin/order-returns/RET%2F42/book-pickup", pickup, { auth: true });
    const outbound = { mode: "xpressbees" as const, courier_id: "courier-1", outbound_awb: "OUT-42" };
    await orders.bookOrderReturnOutbound("RET/42", outbound);
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/admin/order-returns/RET%2F42/book-outbound", outbound, { auth: true });
    const refund = { refund_method: "bank_transfer" as const, refund_reference: "UTR-42" };
    await orders.recordOrderReturnCodRefund("RET/42", refund);
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/admin/order-returns/RET%2F42/record-cod-refund", refund, { auth: true });
  });

  it.each([undefined, "Customer requested another pickup date"])("records failed and rescheduled pickups (%s)", async (note) => {
    await orders.markOrderReturnPickupFailed("RET-42", "Customer unavailable", note);
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/admin/order-returns/RET-42/pickup-failed", { failure_reason: "Customer unavailable", admin_notes: note ?? "" }, { auth: true });
    await orders.rescheduleOrderReturnPickup("RET-42", note);
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/admin/order-returns/RET-42/reschedule-pickup", { admin_notes: note ?? "" }, { auth: true });
  });

  it.each(["image/png", "image/webp", "image/jpeg"])("assigns a filename when a browser photo has no name (%s)", async (type) => {
    api.post.mockResolvedValue({ publicUrl: "https://cdn.example.org/returns/photo.jpg" });
    const file = new File(["image"], "", { type });
    await expect(orders.uploadReturnImage(file)).resolves.toBe("https://cdn.example.org/returns/photo.jpg");
    const form = api.post.mock.calls[0][1] as FormData;
    const uploaded = form.get("file") as File;
    expect(uploaded.name).toBe(type === "image/png" ? "return.png" : type === "image/webp" ? "return.webp" : "return.jpg");
  });
});

describe("inspection defaults", () => {
  it("accepts a whole return without requiring item-level disposition overrides", async () => {
    await orders.acceptOrderReturnAfterInspection("RET-42", "restock");
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/admin/order-returns/RET-42/accept", { disposition: "restock", admin_notes: "", items: [] }, { auth: true });
  });

  it("uploads GIF evidence with the correct filename when unnamed", async () => {
    api.post.mockResolvedValue({ publicUrl: "https://cdn.example.org/returns/photo.gif" });
    await orders.uploadReturnImage(new File(["gif"], "", { type: "image/gif" }));
    expect((api.post.mock.calls[0][1] as FormData).get("file")).toMatchObject({ name: "return.gif" });
  });
});
