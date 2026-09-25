/** @vitest-environment jsdom */
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./contact-us";

const submitContactMessage = vi.hoisted(() => vi.fn());

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
}));

vi.mock("@/hooks/useSiteSettings", () => ({
  useSiteSettings: () => ({
    contact_email: "care@minisnmore.com",
    contact_phone: "+91 76765 25139",
    whatsapp_number: "7676525139",
    invoice_registered_office_address:
      "Second Floor, 3298, 80 Feet Road, Hosakerehalli Cross, Bengaluru, Karnataka, 560085",
  }),
}));

vi.mock("@/lib/contact-messages", () => ({
  submitContactMessage: (...args: unknown[]) => submitContactMessage(...args),
}));

vi.mock("@/assets/lifestyle-mother.webp", () => ({
  default: "lifestyle-mother.webp",
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

const ContactUsPage = (Route as { component: React.ComponentType }).component;

describe("contact us page", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    submitContactMessage.mockResolvedValue({ id: "1" });
  });

  it("renders the Contact Us hero, form, address and map", () => {
    render(<ContactUsPage />);
    expect(screen.getByRole("heading", { level: 1, name: /contact\s+us/i })).toBeInTheDocument();
    expect(screen.getByAltText(/mother holding a smiling baby/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /care@minisnmore.com/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/^name$/i)).toBeInTheDocument();
    expect(screen.getByText(/hosakerehalli/i)).toBeInTheDocument();
    expect(screen.getByTitle(/studio location on google maps/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /open in google maps/i })).toHaveAttribute(
      "href",
      expect.stringContaining("google.com/maps"),
    );
  });

  it("submits the write-to-us form", async () => {
    render(<ContactUsPage />);
    await userEvent.type(screen.getByLabelText(/^name$/i), "Asha");
    await userEvent.type(screen.getByLabelText(/^email$/i), "asha@example.com");
    await userEvent.type(screen.getByLabelText(/contact number/i), "9876543210");
    await userEvent.type(screen.getByLabelText(/^address$/i), "12 Flower Lane");
    await userEvent.type(screen.getByLabelText(/^comments$/i), "Need a gift wrap for a newborn.");
    await userEvent.click(screen.getByRole("button", { name: "Send message" }));

    await waitFor(() =>
      expect(submitContactMessage).toHaveBeenCalledWith({
        name: "Asha",
        email: "asha@example.com",
        phone: "9876543210",
        address: "12 Flower Lane",
        comment: "Need a gift wrap for a newborn.",
      }),
    );
  });
});
