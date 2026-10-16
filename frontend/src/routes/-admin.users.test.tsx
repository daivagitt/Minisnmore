import { describe, expect, it, vi } from "vitest";
import { Route } from "./admin.users";

vi.mock("@tanstack/react-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tanstack/react-router")>();
  return {
    ...actual,
    createFileRoute: () => (config: unknown) => config,
  };
});

describe("admin users route", () => {
  it("redirects the leftover Users URL to Customers", () => {
    expect(() => (Route as unknown as { beforeLoad: () => void }).beforeLoad()).toThrow(
      expect.objectContaining({ options: expect.objectContaining({ to: "/admin/customers" }) }),
    );
  });
});
