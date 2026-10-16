import { beforeEach, describe, expect, it, vi } from "vitest";
import { ZodError, z } from "zod";
import { ApiError } from "@/lib/api";
import { getUserErrorMessage, showApiError, showValidationErrors } from "@/lib/ui-errors";

const toastMock = vi.hoisted(() => ({
  error: vi.fn(),
}));

vi.mock("sonner", () => ({
  toast: toastMock,
}));

describe("ui-errors", () => {
  beforeEach(() => {
    toastMock.error.mockReset();
  });

  it("returns ApiError messages", () => {
    const error = new ApiError("Email is already in use!", 400, { title: "Email is already in use!" });
    expect(getUserErrorMessage(error, "Sign up failed")).toBe("Email is already in use!");
  });

  it("formats Zod validation issues", () => {
    const error = z.object({ code: z.string().min(1, "Coupon code is required") }).safeParse({ code: "" }).error;
    expect(getUserErrorMessage(error as ZodError, "Invalid input")).toBe("Coupon code is required");
  });

  it("shows a single field validation line directly", () => {
    showApiError(
      new ApiError("Body must be at least 20 characters", 400, {
        fieldErrors: [{ field: "body", message: "Body must be at least 20 characters" }],
      }),
      "Could not save policy",
    );

    expect(toastMock.error).toHaveBeenCalledWith("Body must be at least 20 characters");
  });

  it("shows the first validation reason as the toast title", () => {
    showApiError(
      new ApiError("Standard shipping: must be >= 0\nGST rate: must be <= 100", 400, {
        fieldErrors: [
          { field: "shippingStandardInr", message: "must be >= 0" },
          { field: "gstRate", message: "must be <= 100" },
        ],
      }),
      "Could not save settings",
    );

    expect(toastMock.error).toHaveBeenCalledWith("Standard shipping: must be >= 0", {
      description: "GST rate: must be <= 100",
    });
  });

  it("shows specific API messages when they differ from the fallback", () => {
    showApiError(new ApiError("Payment signature mismatch", 400, { detail: "Payment signature mismatch" }), "Could not verify payment");

    expect(toastMock.error).toHaveBeenCalledWith("Payment signature mismatch");
  });

  it("falls back when no specific message is available", () => {
    showApiError(new Error(""), "Could not load policies");
    expect(toastMock.error).toHaveBeenCalledWith("Could not load policies");
  });

  it("shows the first validation reason for client-side admin checks", () => {
    showValidationErrors(["Product name is required.", "SKU is required."]);

    expect(toastMock.error).toHaveBeenCalledWith("Product name is required.", {
      description: "SKU is required.",
    });
  });
});
