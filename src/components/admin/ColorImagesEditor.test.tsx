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

  it("exposes a print photo for the display-image drop target even when there is only one image", () => {
    render(
      <ColorImagesEditor
        colors={[{ name: "Navy", imageUrls: ["https://cdn.example.com/navy-1.jpg"] }]}
        onPatchColor={vi.fn()}
      />,
    );

    expect(screen.getByLabelText("Drag Navy image 1 as display image")).toBeInTheDocument();
  });

  it("hides extra gallery photos until Show all is clicked", async () => {
    render(
      <ColorImagesEditor
        colors={[
          {
            name: "Navy",
            imageUrls: [
              "https://cdn.example.com/navy-1.jpg",
              "https://cdn.example.com/navy-2.jpg",
              "https://cdn.example.com/navy-3.jpg",
            ],
          },
        ]}
        onPatchColor={vi.fn()}
      />,
    );

    expect(screen.getByLabelText("Reorder Navy image 1")).toBeInTheDocument();
    expect(screen.getByLabelText("Reorder Navy image 2")).toBeInTheDocument();
    expect(screen.queryByLabelText("Reorder Navy image 3")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Show all 3" }));

    expect(screen.getByLabelText("Reorder Navy image 3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Show fewer" })).toBeInTheDocument();
  });
});
