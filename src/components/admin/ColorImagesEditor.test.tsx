import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ColorImagesEditor } from "./ColorImagesEditor";

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
}));

describe("ColorImagesEditor gallery reorder", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("moves a gallery photo left without auto-saving", async () => {
    const onPatchColor = vi.fn();
    const onGalleryUploaded = vi.fn().mockResolvedValue(undefined);

    render(
      <ColorImagesEditor
        colors={[
          {
            name: "Navy",
            imageUrls: ["https://cdn.example.com/navy-1.jpg", "https://cdn.example.com/navy-2.jpg"],
          },
        ]}
        onPatchColor={onPatchColor}
        onGalleryUploaded={onGalleryUploaded}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Move Navy image 2 left" }));

    expect(onPatchColor).toHaveBeenCalledWith(0, {
      imageUrls: ["https://cdn.example.com/navy-2.jpg", "https://cdn.example.com/navy-1.jpg"],
    });
    expect(onGalleryUploaded).not.toHaveBeenCalled();
  });

  it("disables move left on the first photo", () => {
    render(
      <ColorImagesEditor
        colors={[
          {
            name: "Navy",
            imageUrls: ["https://cdn.example.com/navy-1.jpg", "https://cdn.example.com/navy-2.jpg"],
          },
        ]}
        onPatchColor={vi.fn()}
      />,
    );

    expect(screen.getByRole("button", { name: "Move Navy image 1 left" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Move Navy image 2 right" })).toBeDisabled();
  });

  it("reorders gallery photos by drag and drop without auto-saving", async () => {
    const onPatchColor = vi.fn();
    const onGalleryUploaded = vi.fn().mockResolvedValue(undefined);
    const store: Record<string, string> = {};
    const dataTransfer = {
      setData: (key: string, value: string) => {
        store[key] = value;
      },
      getData: (key: string) => store[key] ?? "",
      effectAllowed: "all",
      dropEffect: "move",
    };

    render(
      <ColorImagesEditor
        colors={[
          {
            name: "Navy",
            imageUrls: ["https://cdn.example.com/navy-1.jpg", "https://cdn.example.com/navy-2.jpg"],
          },
        ]}
        onPatchColor={onPatchColor}
        onGalleryUploaded={onGalleryUploaded}
      />,
    );

    const source = screen.getByLabelText("Reorder Navy image 2");
    const target = screen.getByLabelText("Reorder Navy image 1");
    fireEvent.dragStart(source, { dataTransfer });
    fireEvent.dragOver(target, { dataTransfer });
    fireEvent.drop(target, { dataTransfer });

    expect(onPatchColor).toHaveBeenCalledWith(0, {
      imageUrls: ["https://cdn.example.com/navy-2.jpg", "https://cdn.example.com/navy-1.jpg"],
    });
    expect(onGalleryUploaded).not.toHaveBeenCalled();
  });
});
