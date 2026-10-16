/** @vitest-environment jsdom */
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./admin.contact-messages";

const listAdminContactMessages = vi.hoisted(() => vi.fn());
const downloadTableAsXlsx = vi.hoisted(() => vi.fn());

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
}));

vi.mock("@/lib/contact-messages", () => ({
  listAdminContactMessages: (...args: unknown[]) => listAdminContactMessages(...args),
  deleteAdminContactMessage: vi.fn(),
}));

vi.mock("@/lib/export-table", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/export-table")>();
  return {
    ...actual,
    downloadTableAsXlsx: (...args: unknown[]) => downloadTableAsXlsx(...args),
  };
});

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("@/lib/ui-errors", () => ({
  showApiError: vi.fn(),
}));

function renderMessages() {
  const Component = (Route as unknown as { component: React.ComponentType }).component;
  return render(<Component />);
}

describe("admin contact messages", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    downloadTableAsXlsx.mockResolvedValue(undefined);
    listAdminContactMessages.mockResolvedValue([
      {
        id: "1",
        name: "Asha",
        email: "asha@example.com",
        phone: "9876543210",
        address: "12 Flower Lane",
        comment: "Need gift wrap",
        created_date: "2026-10-07T06:30:00.000Z",
      },
    ]);
  });

  it("exports contact messages", async () => {
    renderMessages();
    await waitFor(() => expect(screen.getByText("asha@example.com")).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: "Export" }));
    await waitFor(() => expect(downloadTableAsXlsx).toHaveBeenCalled());
    const [rows, filename, sheet] = downloadTableAsXlsx.mock.calls[0];
    expect(filename).toMatch(/^contact-messages-\d{8}\.xlsx$/);
    expect(sheet).toBe("Contact messages");
    expect(rows).toEqual([
      expect.objectContaining({
        Name: "Asha",
        Email: "asha@example.com",
        Phone: "9876543210",
        Address: "12 Flower Lane",
        Comments: "Need gift wrap",
      }),
    ]);
  });
});
