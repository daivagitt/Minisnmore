import type { ComponentType } from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Route } from "./admin.policies";

const listPolicies = vi.hoisted(() => vi.fn());
const savePolicy = vi.hoisted(() => vi.fn());

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: unknown) => config,
}));

vi.mock("@/lib/backend-data", async () => {
  const actual = await vi.importActual<typeof import("@/lib/backend-data")>("@/lib/backend-data");
  return { ...actual, listPolicies, savePolicy, deletePolicy: vi.fn() };
});

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

const SAVED = {
  id: "1",
  slug: "privacy",
  title: "Privacy policy",
  body: "Privacy policy body text for customers",
};

function renderPolicies() {
  const Component = (Route as unknown as { component: ComponentType }).component;
  return render(<Component />);
}

const addButton = () =>
  screen
    .getAllByRole("button", { name: /new policy/i })
    .find((el) => !el.hasAttribute("aria-expanded")) as HTMLElement;
const cardOf = (trigger: HTMLElement) => trigger.parentElement!.parentElement as HTMLElement;
const titleInputs = () =>
  screen.queryAllByRole("textbox").filter((el) => el.getAttribute("maxlength"));
const visibleTitleInputs = () =>
  titleInputs().filter((el) => !el.closest("[hidden]")) as HTMLInputElement[];

describe("admin policies page collapsible rows", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listPolicies.mockResolvedValue([SAVED]);
  });

  it("starts saved rows collapsed", async () => {
    renderPolicies();
    await waitFor(() =>
      expect(screen.getByRole("button", { name: /privacy policy/i })).toBeInTheDocument(),
    );
    expect(visibleTitleInputs()).toHaveLength(0);
  });

  it("opens every new draft, even after Collapse all", async () => {
    const user = userEvent.setup();
    renderPolicies();
    await screen.findByRole("button", { name: /privacy policy/i });

    await user.click(screen.getByRole("button", { name: /collapse all/i }));
    await user.click(addButton());
    await user.click(addButton());
    expect(visibleTitleInputs()).toHaveLength(2);
  });

  it("deleting one draft keeps the other draft and its typed content", async () => {
    const user = userEvent.setup();
    renderPolicies();
    await screen.findByRole("button", { name: /privacy policy/i });

    await user.click(addButton());
    await user.type(visibleTitleInputs()[0], "Returns");
    await user.click(addButton());

    const newest = screen.getByRole("button", { name: /^new policy$/i, expanded: true });
    const newestCard = cardOf(newest);
    await user.click(within(newestCard).getByRole("button", { name: /delete/i }));

    expect(visibleTitleInputs().map((el) => el.value)).toEqual(["Returns"]);
  });

  it("saving one row keeps unsaved edits in another saved row", async () => {
    const user = userEvent.setup();
    const other = { id: "2", slug: "terms", title: "Terms of use", body: "Some terms text here" };
    listPolicies.mockResolvedValue([SAVED, other]);
    renderPolicies();
    await screen.findByRole("button", { name: /privacy policy/i });
    await user.click(screen.getByRole("button", { name: /expand all/i }));

    const termsCard = cardOf(screen.getByRole("button", { name: /terms of use/i }));
    const termsTitle = within(termsCard).getAllByRole("textbox")[0];
    await user.clear(termsTitle);
    await user.type(termsTitle, "Terms unsaved");

    savePolicy.mockResolvedValue(SAVED);
    const privacyCard = cardOf(screen.getByRole("button", { name: /privacy policy/i }));
    await user.click(within(privacyCard).getByRole("button", { name: /save/i }));

    await waitFor(() => expect(savePolicy).toHaveBeenCalled());
    await waitFor(() => expect(listPolicies).toHaveBeenCalledTimes(2));
    expect(screen.getByRole("button", { name: /terms unsaved/i })).toBeInTheDocument();
    expect(visibleTitleInputs().map((el) => el.value)).toContain("Terms unsaved");
  });

  it("keeps a draft open after its first save and keeps other drafts", async () => {
    const user = userEvent.setup();
    renderPolicies();
    await screen.findByRole("button", { name: /privacy policy/i });

    await user.click(addButton());
    await user.type(visibleTitleInputs()[0], "Other draft");
    await user.click(addButton());

    const created = { id: "2", slug: "terms", title: "Terms of use", body: "Some terms text here" };
    savePolicy.mockResolvedValue(created);
    listPolicies.mockResolvedValue([created, SAVED]);

    const draftCard = cardOf(screen.getByRole("button", { name: /^new policy$/i, expanded: true }));
    const [title, body] = within(draftCard).getAllByRole("textbox");
    await user.type(within(draftCard).getByRole("combobox"), "terms");
    await user.type(title, "Terms of use");
    await user.type(body, "Some terms text here");
    await user.click(within(draftCard).getByRole("button", { name: /save/i }));

    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: /terms of use/i, expanded: true }),
      ).toBeInTheDocument(),
    );
    expect(
      screen.getByRole("button", { name: /other draft/i, expanded: true }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /privacy policy/i, expanded: false }),
    ).toBeInTheDocument();
  });
});
