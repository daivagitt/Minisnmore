import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { DisplayImageField } from "./DisplayImageField";
import { DISPLAY_IMAGE_DRAG_TYPE } from "@/lib/admin-display-image";

describe("DisplayImageField", () => {
  it("accepts a dragged image URL", () => {
    const onSetDisplayImage = vi.fn();
    const store: Record<string, string> = {
      [DISPLAY_IMAGE_DRAG_TYPE]: "https://cdn.example.com/mitten.jpg",
    };
    const dataTransfer = {
      types: Object.keys(store),
      setData: (key: string, value: string) => {
        store[key] = value;
      },
      getData: (key: string) => store[key] ?? "",
      dropEffect: "copy",
    };

    render(
      <DisplayImageField imageUrl={null} onSetDisplayImage={onSetDisplayImage} />,
    );

    fireEvent.drop(screen.getByRole("group", { name: "Storefront display image" }), {
      dataTransfer,
    });

    expect(onSetDisplayImage).toHaveBeenCalledWith("https://cdn.example.com/mitten.jpg");
  });

  it("renders a same-origin preview URL for catalog photos", () => {
    const { container } = render(
      <DisplayImageField imageUrl="https://cdn.example.com/mitten.jpg" onSetDisplayImage={vi.fn()} />,
    );

    expect(container.querySelector("img")?.getAttribute("src")).toContain("/api/v1/media/preview");
  });
});
