import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { SizeNotesEditor } from "./SizeNotesEditor";

describe("SizeNotesEditor", () => {
  it("lets merchandisers set a callout for each size", () => {
    const onChange = vi.fn();
    render(
      <SizeNotesEditor
        sizes={["0-3m", "6-12m"]}
        value={{ "0-3m": "Without grippers" }}
        onChange={onChange}
      />,
    );

    fireEvent.change(screen.getByDisplayValue("Without grippers"), {
      target: { value: "No grippers" },
    });
    expect(onChange).toHaveBeenCalledWith({ "0-3m": "No grippers" });

    fireEvent.change(screen.getByLabelText("Callout for size 6-12m"), {
      target: { value: "This size has grippers" },
    });
    expect(onChange).toHaveBeenCalledWith({
      "0-3m": "Without grippers",
      "6-12m": "This size has grippers",
    });
  });

  it("fills the field from a note saved under a spaced size label", () => {
    render(
      <SizeNotesEditor
        sizes={["0-3m"]}
        value={{ "0-3 M": "Without grippers" }}
        onChange={vi.fn()}
      />,
    );
    expect(screen.getByLabelText("Callout for size 0-3m")).toHaveValue("Without grippers");
  });
});
