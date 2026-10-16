/** @vitest-environment jsdom */
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ReturnsSummaryCards } from "./ReturnsSummaryCards";

const summary = {
  total: 12,
  returns: 8,
  exchanges: 4,
  open: 7,
  closed: 5,
  overdue: 2,
  xpressbeesReverseReady: 4,
  stages: { request: 3, pickup: 2, inspection: 1, exchange: 1, closed: 5 },
};

describe("ReturnsSummaryCards", () => {
  it("shows how many returns and exchanges are open", () => {
    render(<ReturnsSummaryCards summary={summary} />);

    expect(screen.getByLabelText("Returns and exchanges summary")).toBeInTheDocument();
    expect(screen.getByText(/There are 8 returns and 4 exchanges/)).toBeInTheDocument();
    expect(screen.getByText(/7 still open, 2 overdue/)).toBeInTheDocument();
    expect(screen.queryByText(/XpressBees reverse book/)).not.toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();
    expect(screen.getAllByText("4")).toHaveLength(2);
    expect(screen.getByRole("button", { name: "8 returns" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "4 exchanges" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "4 book reverse pickup" })).toBeInTheDocument();
  });

  it("shows all cases when a summary card is clicked", async () => {
    const onSelectStage = vi.fn();
    const user = userEvent.setup();
    render(<ReturnsSummaryCards summary={summary} onSelectStage={onSelectStage} />);

    await user.click(screen.getByRole("button", { name: "7 open" }));
    expect(onSelectStage).toHaveBeenCalledWith("all", undefined, "");
  });

  it("filters to return cases when the returns card is clicked", async () => {
    const onSelectStage = vi.fn();
    const user = userEvent.setup();
    render(<ReturnsSummaryCards summary={summary} onSelectStage={onSelectStage} />);

    await user.click(screen.getByRole("button", { name: "8 returns" }));
    expect(onSelectStage).toHaveBeenCalledWith("all", undefined, "return");
  });

  it("filters to exchange cases when the exchanges card is clicked", async () => {
    const onSelectStage = vi.fn();
    const user = userEvent.setup();
    render(<ReturnsSummaryCards summary={summary} onSelectStage={onSelectStage} />);

    await user.click(screen.getByRole("button", { name: "4 exchanges" }));
    expect(onSelectStage).toHaveBeenCalledWith("all", undefined, "exchange");
  });

  it("filters to approved cases when the book pickup card is clicked", async () => {
    const onSelectStage = vi.fn();
    const user = userEvent.setup();
    render(<ReturnsSummaryCards summary={summary} onSelectStage={onSelectStage} />);

    await user.click(screen.getByRole("button", { name: "4 book reverse pickup" }));
    expect(onSelectStage).toHaveBeenCalledWith("request", "approved", "");
  });

  it("shows an empty headline when there are no cases", () => {
    render(
      <ReturnsSummaryCards
        summary={{
          total: 0,
          returns: 0,
          exchanges: 0,
          open: 0,
          closed: 0,
          overdue: 0,
          xpressbeesReverseReady: 0,
          stages: { request: 0, pickup: 0, inspection: 0, exchange: 0, closed: 0 },
        }}
      />,
    );

    expect(screen.getByText("No return or exchange cases yet.")).toBeInTheDocument();
    expect(screen.queryByText(/XpressBees reverse book/)).not.toBeInTheDocument();
  });
});
