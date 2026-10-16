/** @vitest-environment jsdom */
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./admin.cancellations";

const listAdminCancellationRequests = vi.hoisted(() => vi.fn());

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
}));

vi.mock("@/lib/orders.functions", () => ({
  listAdminCancellationRequests: (...args: unknown[]) => listAdminCancellationRequests(...args),
  approveCancellationRequest: vi.fn(),
  recordCancellationCodRefund: vi.fn(),
  rejectCancellationRequest: vi.fn(),
  retryCancellationRefund: vi.fn(),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("@/lib/ui-errors", () => ({
  showApiError: vi.fn(),
}));

function renderCancellations() {
  const Component = (Route as unknown as { component: React.ComponentType }).component;
  return render(<Component />);
}

describe("admin cancellations", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it("renders a searchable list table instead of cards", async () => {
    listAdminCancellationRequests.mockResolvedValue({
      requests: [
        {
          id: 1,
          order_id: 501,
          request_number: "CAN-1",
          order_number: "MNM-1",
          status: "requested",
          reason: "Changed my mind",
          requested_at: new Date(2026, 9, 7, 12, 0, 0).toISOString(),
          customer_login: "asha",
          order_total: 999,
        },
      ],
    });

    renderCancellations();
    await waitFor(() => expect(screen.getByRole("table", { name: "Cancellation requests" })).toBeInTheDocument());
    expect(screen.getByRole("columnheader", { name: "Order ID" })).toBeInTheDocument();
    expect(screen.getByRole("searchbox", { name: /search by order id/i })).toBeInTheDocument();
    expect(screen.getByLabelText("From date")).toBeInTheDocument();
    expect(screen.getByLabelText("To date")).toBeInTheDocument();
  });

  it("finds a cancellation by order id without paging through results", async () => {
    const requestedAt = new Date(2026, 9, 7, 12, 0, 0).toISOString();
    listAdminCancellationRequests.mockResolvedValue({
      requests: Array.from({ length: 12 }, (_, index) => ({
        id: index + 1,
        order_id: 200 + index,
        request_number: `CAN-${index + 1}`,
        order_number: `MNM-${200 + index}`,
        status: "requested",
        reason: "Changed my mind",
        requested_at: requestedAt,
        customer_login: "asha",
        order_total: 999,
      })),
    });

    renderCancellations();
    await waitFor(() => expect(screen.getByRole("button", { name: "Go to page 2" })).toBeInTheDocument());
    expect(screen.getByText("CAN-1")).toBeInTheDocument();
    expect(screen.queryByText("CAN-12")).toBeNull();

    await userEvent.type(screen.getByRole("searchbox", { name: /search by order id/i }), "MNM-211");
    await waitFor(() => expect(screen.getByText("CAN-12")).toBeInTheDocument());
    expect(screen.getByRole("cell", { name: "MNM-211" })).toBeInTheDocument();
    expect(screen.queryByText("CAN-1")).toBeNull();
  });

  it("filters cancellation requests by date", async () => {
    listAdminCancellationRequests.mockResolvedValue({
      requests: [
        {
          id: 1,
          order_id: 501,
          request_number: "CAN-TODAY",
          order_number: "MNM-TODAY",
          status: "requested",
          reason: "Changed my mind",
          requested_at: new Date(2026, 9, 7, 12, 0, 0).toISOString(),
          customer_login: "asha",
          order_total: 999,
        },
        {
          id: 2,
          order_id: 502,
          request_number: "CAN-OLD",
          order_number: "MNM-OLD",
          status: "requested",
          reason: "Changed my mind",
          requested_at: new Date(2026, 8, 1, 12, 0, 0).toISOString(),
          customer_login: "asha",
          order_total: 999,
        },
      ],
    });

    renderCancellations();
    await waitFor(() => expect(screen.getByText("CAN-TODAY")).toBeInTheDocument());
    expect(screen.getByText("CAN-OLD")).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText("From date"), "2026-10-07");
    await waitFor(() => expect(screen.getByText("CAN-TODAY")).toBeInTheDocument());
    expect(screen.queryByText("CAN-OLD")).toBeNull();
  });

  it("matches numeric order id and ignores the cancellation request id", async () => {
    const requestedAt = new Date(2026, 9, 7, 12, 0, 0).toISOString();
    const byRequestId = {
      id: 12,
      order_id: 88,
      request_number: "CAN-WRONG",
      order_number: "MNM-WRONG",
      status: "requested",
      reason: "Changed my mind",
      requested_at: requestedAt,
      customer_login: "asha",
      order_total: 999,
    };
    const byOrderId = {
      ...byRequestId,
      id: 99,
      order_id: 12,
      request_number: "CAN-HIT",
      order_number: "MNM-HIT",
      status: "approved",
    };
    listAdminCancellationRequests.mockImplementation((status?: string) =>
      Promise.resolve({
        requests: status === "requested" ? [byRequestId] : [byRequestId, byOrderId],
      }),
    );

    renderCancellations();
    await waitFor(() => expect(screen.getByText("CAN-WRONG")).toBeInTheDocument());
    expect(screen.queryByText("CAN-HIT")).toBeNull();

    await userEvent.type(screen.getByRole("searchbox", { name: /search by order id/i }), "12");
    await waitFor(() => expect(screen.getByText("CAN-HIT")).toBeInTheDocument());
    expect(screen.queryByText("CAN-WRONG")).toBeNull();
    expect(listAdminCancellationRequests).toHaveBeenCalledWith(undefined);
  });
});
