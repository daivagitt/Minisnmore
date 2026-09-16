import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ReviewStars } from "./ReviewStars";

describe("ReviewStars", () => {
  it("announces half-star ratings", () => {
    render(<ReviewStars rating={4.5} />);
    expect(screen.getByLabelText("4.5 out of 5 stars")).toBeInTheDocument();
  });

  it("announces whole-star ratings without a decimal", () => {
    render(<ReviewStars rating={3} />);
    expect(screen.getByLabelText("3 out of 5 stars")).toBeInTheDocument();
  });
});
