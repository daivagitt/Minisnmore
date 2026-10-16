/** @vitest-environment jsdom */
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./account.profile";

const updateMyProfile = vi.hoisted(() => vi.fn());
const refreshProfile = vi.hoisted(() => vi.fn());
const setCachedProfile = vi.hoisted(() => vi.fn());
const accountData = vi.hoisted(() => ({
  profile: { full_name: "Jane Doe", phone: "9876543210", subscribed: false } as {
    full_name: string;
    phone: string;
    subscribed: boolean;
  } | null,
  loadingProfile: false,
  refreshProfile,
  setProfile: setCachedProfile,
}));

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
}));

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => ({ user: { email: "parent@example.com" } }),
}));

vi.mock("@/lib/account", () => ({
  useAccountData: () => accountData,
}));

vi.mock("@/lib/orders.functions", async () => {
  const actual = await vi.importActual<typeof import("@/lib/orders.functions")>("@/lib/orders.functions");
  return {
    ...actual,
    updateMyProfile,
  };
});

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

const ProfilePage = (Route as unknown as { component: React.ComponentType }).component;

describe("account profile subscribe", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    accountData.profile = { full_name: "Jane Doe", phone: "9876543210", subscribed: false };
    accountData.loadingProfile = false;
    refreshProfile.mockResolvedValue(undefined);
    updateMyProfile.mockResolvedValue({ ok: true });
  });

  it("saves a subscribe preference from profile settings", async () => {
    render(<ProfilePage />);

    const checkbox = screen.getByRole("checkbox", { name: /subscribe to emails and offers/i }) as HTMLInputElement;
    expect(checkbox.checked).toBe(false);

    await userEvent.click(checkbox);
    expect(checkbox.checked).toBe(true);
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() =>
      expect(updateMyProfile).toHaveBeenCalledWith({
        data: { full_name: "Jane Doe", phone: "9876543210", subscribed: true },
      }),
    );
  });

  it("can unsubscribe from emails in profile settings", async () => {
    accountData.profile = { full_name: "Jane Doe", phone: "9876543210", subscribed: true };
    render(<ProfilePage />);

    const checkbox = screen.getByRole("checkbox", { name: /subscribe to emails and offers/i }) as HTMLInputElement;
    expect(checkbox.checked).toBe(true);
    expect(screen.getByText(/uncheck to unsubscribe/i)).toBeTruthy();

    await userEvent.click(checkbox);
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() =>
      expect(updateMyProfile).toHaveBeenCalledWith({
        data: { full_name: "Jane Doe", phone: "9876543210", subscribed: false },
      }),
    );
  });
});
