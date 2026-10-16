import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ReturnConditionNotice } from "./ReturnConditionNotice";

describe("ReturnConditionNotice", () => {
  it("warns that used items will be rejected", () => {
    render(<ReturnConditionNotice />);
    expect(screen.getByRole("note")).toHaveTextContent(/unused/i);
    expect(screen.getByRole("note")).toHaveTextContent(/will be rejected/i);
  });
});
