import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SizeFeatureNote } from "./SizeFeatureNote";

describe("SizeFeatureNote", () => {
  it("hides when the selected size has no callout", () => {
    const { container } = render(<SizeFeatureNote note="  " />);
    expect(container).toBeEmptyDOMElement();
  });

  it("renders the size-specific callout on the photo and in the purchase card", () => {
    const { rerender } = render(<SizeFeatureNote note="This size has grippers" variant="overlay" />);
    expect(screen.getByText("This size has grippers")).toBeInTheDocument();
    rerender(<SizeFeatureNote note="Without grippers" variant="inline" />);
    expect(screen.getByRole("status")).toHaveTextContent("Without grippers");
  });
});
