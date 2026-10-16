/** @vitest-environment jsdom */
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./contact-us";

const submitContactMessage = vi.hoisted(() => vi.fn());
const getMyProfile = vi.hoisted(() => vi.fn());
const authMock = vi.hoisted(() => ({
  user: null as null | {
    id: number;
    login: string;
    email: string | null;
    firstName?: string | null;
    lastName?: string | null;
  },
  loading: false,
}));

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
}));

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => authMock,
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

vi.mock("@/lib/orders.functions", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/orders.functions")>();
  return {
    ...actual,
    getMyProfile: (...args: unknown[]) => getMyProfile(...args),
  };
});

vi.mock("@/assets/lifestyle-mother.webp", () => ({
  default: "lifestyle-mother.webp",
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

const ContactUsPage = (Route as unknown as { component: React.ComponentType }).component;

describe("contact us page", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    authMock.user = null;
    authMock.loading = false;
    submitContactMessage.mockResolvedValue({ id: "1" });
    getMyProfile.mockResolvedValue({ profile: { full_name: "", phone: "", subscribed: false } });
  });

  it("renders the Contact Us hero, form, address and map", () => {
    render(<ContactUsPage />);
    expect(screen.getByRole("heading", { level: 1, name: /contact\s+us/i })).toBeInTheDocument();
    expect(screen.getByAltText(/mother holding a smiling baby/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /care@minisnmore.com/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/^name$/i)).toBeInTheDocument();
    expect(screen.getByText(/hosakerehalli/i)).toBeInTheDocument();
    expect(screen.getByTitle(/office location on google maps/i)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /our office address/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /whatsapp/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/whatsapp/i)).not.toBeInTheDocument();
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

  it("autofills email, phone and name for a logged-in user", async () => {
    authMock.user = {
      id: 7,
      login: "asha@example.com",
      email: "asha@example.com",
      firstName: "Asha",
      lastName: "Rao",
    };
    getMyProfile.mockResolvedValue({
      profile: { full_name: "Asha Rao", phone: "9876543210", subscribed: true },
    });

    const { rerender } = render(<ContactUsPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/^email$/i)).toHaveValue("asha@example.com");
      expect(screen.getByLabelText(/contact number/i)).toHaveValue("9876543210");
      expect(screen.getByLabelText(/^name$/i)).toHaveValue("Asha Rao");
    });
    expect(getMyProfile).toHaveBeenCalled();

    authMock.user = null;
    rerender(<ContactUsPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/^email$/i)).toHaveValue("");
      expect(screen.getByLabelText(/contact number/i)).toHaveValue("");
      expect(screen.getByLabelText(/^name$/i)).toHaveValue("");
    });
  });

  it("replaces identity fields when a different account signs in", async () => {
    authMock.user = {
      id: 7,
      login: "asha@example.com",
      email: "asha@example.com",
      firstName: "Asha",
      lastName: "Rao",
    };
    getMyProfile.mockResolvedValue({
      profile: { full_name: "Asha Rao", phone: "9876543210", subscribed: true },
    });

    const { rerender } = render(<ContactUsPage />);
    await waitFor(() => expect(screen.getByLabelText(/^email$/i)).toHaveValue("asha@example.com"));

    authMock.user = {
      id: 8,
      login: "bina@example.com",
      email: "bina@example.com",
      firstName: "Bina",
      lastName: "Shah",
    };
    getMyProfile.mockResolvedValue({
      profile: { full_name: "Bina Shah", phone: "9123456789", subscribed: false },
    });
    rerender(<ContactUsPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/^email$/i)).toHaveValue("bina@example.com");
      expect(screen.getByLabelText(/contact number/i)).toHaveValue("9123456789");
      expect(screen.getByLabelText(/^name$/i)).toHaveValue("Bina Shah");
    });
  });

  it("does not fetch profile for guests", () => {
    render(<ContactUsPage />);
    expect(getMyProfile).not.toHaveBeenCalled();
    expect(screen.getByLabelText(/^email$/i)).toHaveValue("");
  });

  it("keeps auth name when profile has no full_name or rejects", async () => {
    authMock.user = {
      id: 9,
      login: "dev@example.com",
      email: "dev@example.com",
      firstName: "Dev",
      lastName: "Parent",
    };
    getMyProfile.mockRejectedValue(new Error("offline"));

    render(<ContactUsPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/^email$/i)).toHaveValue("dev@example.com");
      expect(screen.getByLabelText(/^name$/i)).toHaveValue("Dev Parent");
    });
    expect(screen.getByLabelText(/contact number/i)).toHaveValue("");
  });

  it("after submit restores login defaults and clears address/comments", async () => {
    authMock.user = {
      id: 7,
      login: "asha@example.com",
      email: "asha@example.com",
      firstName: "Asha",
      lastName: "Rao",
    };
    getMyProfile.mockResolvedValue({
      profile: { full_name: "Asha Rao", phone: "9876543210", subscribed: true },
    });

    render(<ContactUsPage />);
    await waitFor(() => expect(screen.getByLabelText(/contact number/i)).toHaveValue("9876543210"));

    await userEvent.type(screen.getByLabelText(/^address$/i), "12 Flower Lane");
    await userEvent.type(screen.getByLabelText(/^comments$/i), "Need a gift wrap for a newborn.");
    await userEvent.click(screen.getByRole("button", { name: "Send message" }));

    await waitFor(() => expect(submitContactMessage).toHaveBeenCalled());
    await waitFor(() => {
      expect(screen.getByLabelText(/^email$/i)).toHaveValue("asha@example.com");
      expect(screen.getByLabelText(/contact number/i)).toHaveValue("9876543210");
      expect(screen.getByLabelText(/^name$/i)).toHaveValue("Asha Rao");
      expect(screen.getByLabelText(/^address$/i)).toHaveValue("");
      expect(screen.getByLabelText(/^comments$/i)).toHaveValue("");
    });
  });

  it("waits for auth loading before autofilling so typed fields are not clobbered", async () => {
    authMock.loading = true;
    authMock.user = null;
    getMyProfile.mockResolvedValue({
      profile: { full_name: "Asha Rao", phone: "9876543210", subscribed: true },
    });

    const { rerender } = render(<ContactUsPage />);
    await userEvent.type(screen.getByLabelText(/^name$/i), "Guest Name");
    await userEvent.type(screen.getByLabelText(/^email$/i), "guest@example.com");
    await userEvent.type(screen.getByLabelText(/contact number/i), "9000011111");
    expect(getMyProfile).not.toHaveBeenCalled();

    authMock.loading = false;
    authMock.user = {
      id: 7,
      login: "asha@example.com",
      email: "asha@example.com",
      firstName: "Asha",
      lastName: "Rao",
    };
    rerender(<ContactUsPage />);

    // Typed values from the loading window are kept; profile only fills blanks (none here).
    await waitFor(() => expect(getMyProfile).toHaveBeenCalled());
    expect(screen.getByLabelText(/^email$/i)).toHaveValue("guest@example.com");
    expect(screen.getByLabelText(/contact number/i)).toHaveValue("9000011111");
    expect(screen.getByLabelText(/^name$/i)).toHaveValue("Guest Name");
  });
});
