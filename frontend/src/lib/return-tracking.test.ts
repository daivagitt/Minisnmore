import { describe, expect, it } from "vitest";
import {
  EXCHANGE_STAGES,
  RETURN_STAGES,
  closedReturnMessage,
  hasReturnShipmentDetails,
  isClosedReturn,
  isOpenReturnForOrderUi,
  resolvePickupTrackingUrl,
  resolveReturnStage,
  returnStageDetail,
  returnStageLabel,
  returnStatusLabel,
} from "@/lib/return-tracking";

describe("return-tracking helpers", () => {
  it("renders the complete return timeline in order", () => {
    expect(RETURN_STAGES.map((stage) => stage.key)).toEqual([
      "request",
      "pickup",
      "transit",
      "inspection",
      "refunded",
    ]);
  });

  it("renders the complete exchange timeline in order", () => {
    expect(EXCHANGE_STAGES.map((stage) => stage.key)).toEqual([
      "request",
      "pickup",
      "inspection",
      "replacement",
      "out_for_delivery",
      "delivered",
    ]);
  });

  it("maps return statuses onto customer-facing stages", () => {
    expect(resolveReturnStage({ status: "requested" })).toBe(0);
    expect(resolveReturnStage({ status: "approved" })).toBe(0);
    expect(resolveReturnStage({ status: "pickup_scheduled" })).toBe(1);
    expect(resolveReturnStage({ status: "picked_up" })).toBe(2);
    expect(resolveReturnStage({ status: "received" })).toBe(3);
    expect(resolveReturnStage({ status: "accepted" })).toBe(3);
    expect(resolveReturnStage({ status: "refunded" })).toBe(4);
    expect(returnStatusLabel({ status: "pickup_scheduled" })).toBe("Pickup scheduled");
    expect(returnStatusLabel({ status: "approved" })).toBe("Approved");
    expect(returnStatusLabel({ status: "accepted" })).toBe("Quality check complete");
    expect(returnStatusLabel({ status: "refunded" })).toBe("Refund initiated");
    expect(
      returnStatusLabel({ status: "refunded", order_payment_status: "refunded" }),
    ).toBe("Refund processed");
    expect(returnStatusLabel({ status: "failed" })).toBe("Refund failed");
    expect(closedReturnMessage({ status: "failed" })).toContain("retry");
  });

  it("maps ExpressBees scans onto the same steps with clearer labels", () => {
    expect(
      returnStatusLabel({ status: "pickup_scheduled", reverse_carrier_status: "out_for_pickup" }),
    ).toBe("Out for pickup");
    expect(returnStatusLabel({ status: "picked_up", reverse_carrier_status: "in_transit" })).toBe("In transit");
    expect(
      returnStatusLabel({
        request_type: "exchange",
        status: "picked_up",
        reverse_carrier_status: "in_transit",
      }),
    ).toBe("Picked up");
    expect(resolveReturnStage({ request_type: "exchange", status: "picked_up" })).toBe(1);
    expect(
      resolveReturnStage({
        request_type: "exchange",
        status: "shipped",
        outbound_carrier_status: "delivery_failed",
      }),
    ).toBe(4);
    expect(
      resolveReturnStage({
        request_type: "exchange",
        status: "shipped",
        outbound_carrier_status: "out_for_delivery",
      }),
    ).toBe(4);
    expect(
      returnStatusLabel({
        request_type: "exchange",
        status: "shipped",
        outbound_carrier_status: "out_for_delivery",
      }),
    ).toBe("Out for delivery");
    expect(
      returnStatusLabel({
        request_type: "exchange",
        status: "ready_to_ship",
        outbound_carrier_status: "awaiting_pickup",
        outbound_awb: "XB1",
      }),
    ).toBe("Waiting for pickup");
  });

  it("maps exchange statuses onto replacement stages after inspection", () => {
    expect(resolveReturnStage({ request_type: "exchange", status: "accepted" })).toBe(3);
    expect(resolveReturnStage({ request_type: "exchange", status: "ready_to_ship" })).toBe(3);
    expect(resolveReturnStage({ request_type: "exchange", status: "shipped" })).toBe(3);
    expect(resolveReturnStage({ request_type: "exchange", status: "completed" })).toBe(5);
    expect(returnStatusLabel({ request_type: "exchange", status: "shipped" })).toBe("Replacement on the way");
  });

  it("knows which returns block duplicate actions on order detail", () => {
    expect(isOpenReturnForOrderUi({ status: "accepted" })).toBe(true);
    expect(isOpenReturnForOrderUi({ status: "failed" })).toBe(true);
    expect(isOpenReturnForOrderUi({ status: "rejected" })).toBe(false);
    expect(isOpenReturnForOrderUi({ status: "refunded" })).toBe(false);
    expect(isOpenReturnForOrderUi({ status: "completed" })).toBe(false);
  });

  it("treats declined and cancelled requests as closed", () => {
    expect(isClosedReturn({ status: "rejected" })).toBe(true);
    expect(isClosedReturn({ status: "cancelled" })).toBe(true);
    expect(resolveReturnStage({ status: "rejected" })).toBe(-1);
    expect(closedReturnMessage({ status: "rejected", rejection_reason: "Outside the window" })).toBe(
      "This return request was declined. Reason: Outside the window",
    );
    expect(closedReturnMessage({ request_type: "exchange", status: "rejected", rejection_reason: "Size unavailable" })).toBe(
      "This exchange request was declined. Reason: Size unavailable",
    );
  });

  it("explains a failed pickup on the pickup stage", () => {
    expect(
      returnStageDetail({ status: "pickup_failed", pickup_failure_reason: "Customer unavailable" }, "pickup", "current"),
    ).toBe("Customer unavailable");
    expect(returnStatusLabel({ status: "pickup_failed" })).toBe("Pickup unsuccessful");
  });

  it("detects shipment details for AWB and tracking links", () => {
    expect(hasReturnShipmentDetails({ status: "approved" })).toBe(false);
    expect(hasReturnShipmentDetails({ reverse_awb: "REV-1", tracking_url: "https://track/rev" })).toBe(true);
    expect(hasReturnShipmentDetails({ outbound_awb: "OUT-1", outbound_tracking_url: "https://track/out" })).toBe(true);
  });

  it("builds a pickup tracking URL from reverse AWB when none is stored", () => {
    expect(resolvePickupTrackingUrl({ tracking_url: "https://track/rev" })).toBe("https://track/rev");
    expect(resolvePickupTrackingUrl({ reverse_awb: "XB123", carrier_name: "XpressBees" })).toBe(
      "https://shipmentv2.xpressbees.com/orders/tracking/XB123",
    );
    expect(resolvePickupTrackingUrl({ reverse_awb: "XB 1", carrier_name: "XpressBees" })).toBe(
      "https://shipmentv2.xpressbees.com/orders/tracking/XB%201",
    );
    expect(resolvePickupTrackingUrl({ reverse_awb: "DLV-1", carrier_name: "Delhivery" })).toBeNull();
    expect(resolvePickupTrackingUrl({})).toBeNull();
  });

  it("treats refunded exchanges as closed while keeping return refund timelines", () => {
    expect(isClosedReturn({ request_type: "exchange", status: "refunded" })).toBe(true);
    expect(isClosedReturn({ request_type: "return", status: "refunded" })).toBe(false);
    expect(resolveReturnStage({ request_type: "exchange", status: "refunded" })).toBe(5);
    expect(returnStageLabel({ status: "pickup_failed" }, "pickup")).toBe("Pickup unsuccessful");
    expect(returnStatusLabel({ request_type: "exchange", status: "refunded" })).toBe("Refund initiated");
    expect(
      returnStatusLabel({
        request_type: "exchange",
        status: "refunded",
        order_payment_status: "refunded",
      }),
    ).toBe("Refund processed");
  });

  it("keeps accepted returns on quality check until the refund is actually started", () => {
    expect(resolveReturnStage({ status: "accepted" })).toBe(3);
    expect(returnStageLabel({ status: "accepted" }, "inspection")).toBe("Quality check complete");
    expect(returnStageLabel({ status: "accepted" }, "refunded")).toBe("Refund");
    expect(returnStageDetail({ status: "accepted" }, "inspection", "current")).toBe(
      "The item passed the quality check. Your refund will start next.",
    );
    expect(returnStageDetail({ status: "accepted" }, "refunded", "upcoming")).toBe(
      "Refunds start after the item passes the quality check.",
    );
  });

  it("shows the bank timeline only after the gateway has processed the refund", () => {
    expect(returnStageDetail({ status: "refunded" }, "refunded", "current")).toBe(
      "A refund has been initiated to your original payment method.",
    );
    expect(
      returnStageDetail({ status: "refunded", order_payment_status: "refunded" }, "refunded", "current"),
    ).toBe("It will take 2–5 business days to get refunded into your original payment source.");
  });

  it("shows accurate copy when an exchange pickup is already collected", () => {
    expect(
      returnStageDetail({ request_type: "exchange", status: "picked_up" }, "pickup", "current"),
    ).toBe("The courier has collected your parcel.");
    expect(
      returnStageDetail(
        { request_type: "exchange", status: "picked_up", reverse_carrier_status: "in_transit" },
        "pickup",
        "current",
      ),
    ).toBe("Your parcel is on the way back to us.");
  });

  it("does not map unknown statuses to the first timeline stage", () => {
    expect(resolveReturnStage({ status: "unexpected_status" })).toBe(-1);
    expect(returnStatusLabel({ status: "unexpected_status" })).toBe("Status update");
  });
});
