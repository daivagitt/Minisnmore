import { describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import {
  getContactMessageValidationErrors,
  submitContactMessage,
} from "./contact-messages";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
    api: {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

describe("contact messages", () => {
  it("requires name, email, phone, address and comments", () => {
    expect(
      getContactMessageValidationErrors({
        name: "",
        email: "not-an-email",
        phone: "123",
        address: "",
        comment: "",
      }),
    ).toEqual([
      "Name is required.",
      "Enter a valid email address.",
      "Enter a valid 10-digit Indian mobile number.",
      "Address is required.",
      "Please enter your comments.",
    ]);
  });

  it("posts a public contact message", async () => {
    mockedApi.post.mockResolvedValueOnce({
      id: 9,
      name: "Ada",
      email: "ada@example.com",
      phone: "9876543210",
      address: "12 Flower Lane",
      comment: "Please call me",
      createdDate: "2026-09-21T10:00:00Z",
    });

    await expect(
      submitContactMessage({
        name: " Ada ",
        email: "ada@example.com",
        phone: "+91 98765 43210",
        address: "12 Flower Lane",
        comment: "Please call me",
      }),
    ).resolves.toMatchObject({
      id: "9",
      email: "ada@example.com",
      phone: "9876543210",
    });

    expect(mockedApi.post).toHaveBeenCalledWith("/api/v1/contact-messages", {
      name: "Ada",
      email: "ada@example.com",
      phone: "9876543210",
      address: "12 Flower Lane",
      comment: "Please call me",
    });
  });
});
