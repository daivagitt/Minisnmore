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

function attachLiveFileList(input: HTMLInputElement, file: File) {
  let length = 1;
  const liveList = {
    0: file,
    get length() {
      return length;
    },
    item: (index: number) => (index === 0 && length > 0 ? file : null),
    *[Symbol.iterator]() {
      if (length > 0) yield file;
    },
  };
  Object.defineProperty(input, "files", { configurable: true, get: () => liveList });
  Object.defineProperty(input, "value", {
    configurable: true,
    get: () => "",
    set: (next: string) => {
      if (next === "") length = 0;
    },
  });
}

describe("ReturnImageUpload", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("uploads selected photos after the input is reset", async () => {
    uploadReturnImage.mockResolvedValue("https://cdn.example/returns/damage.jpg");
    const onChange = vi.fn();
    render(<ReturnImageUpload urls={[]} onChange={onChange} />);

    const input = screen.getByLabelText<HTMLInputElement>("Add photos");
    const file = new File(["photo"], "damage.jpg", { type: "image/jpeg" });
    attachLiveFileList(input, file);
    fireEvent.change(input);

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
