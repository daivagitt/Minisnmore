import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ReturnImageGallery } from "./ReturnImageGallery";

describe("ReturnImageGallery", () => {
  it("renders nothing when urls are empty", () => {
    const { container } = render(<ReturnImageGallery urls={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("renders linked thumbnails", () => {
    render(<ReturnImageGallery urls={["https://cdn.example/returns/a.jpg"]} alt="Customer photo" />);
    const img = screen.getByRole("img", { name: "Customer photo" });
    expect(img).toHaveAttribute("src", "https://cdn.example/returns/a.jpg");
    expect(img).toHaveAttribute("referrerpolicy", "no-referrer");
    expect(img.closest("a")).toHaveAttribute("href", "https://cdn.example/returns/a.jpg");
  });
});
