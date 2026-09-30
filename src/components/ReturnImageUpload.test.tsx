/** @vitest-environment jsdom */
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ReturnImageUpload } from "./ReturnImageUpload";

const uploadReturnImage = vi.hoisted(() => vi.fn());

vi.mock("@/lib/orders.functions", () => ({
  MAX_RETURN_IMAGES: 5,
  uploadReturnImage: (...args: unknown[]) => uploadReturnImage(...args),
}));

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

vi.mock("@/lib/ui-errors", () => ({
  showApiError: vi.fn(),
}));

describe("ReturnImageUpload", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("uploads a selected photo and shows a preview", async () => {
    uploadReturnImage.mockResolvedValue("https://cdn.example/returns/damage.jpg");
    const onChange = vi.fn();
    render(<ReturnImageUpload urls={[]} onChange={onChange} />);

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(["photo"], "damage.jpg", { type: "image/jpeg" });
    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() =>
      expect(onChange).toHaveBeenCalledWith(["https://cdn.example/returns/damage.jpg"]),
    );
    expect(uploadReturnImage).toHaveBeenCalledWith(file);
  });

  it("lets the customer remove an uploaded photo", () => {
    const onChange = vi.fn();
    render(
      <ReturnImageUpload urls={["https://cdn.example/returns/damage.jpg"]} onChange={onChange} />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Remove photo" }));
    expect(onChange).toHaveBeenCalledWith([]);
  });
});
