import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useAdminSections } from "./useAdminSections";

describe("useAdminSections", () => {
  it("reset returns every panel to its default", () => {
    const { result } = renderHook(() => useAdminSections());
    act(() => result.current.expandAll());
    act(() => result.current.setOpen("a", false));
    act(() => result.current.reset());
    expect(result.current.panel("a").open).toBe(false);
    expect(result.current.panel("b", true).open).toBe(true);
  });

  it("keeps action identities stable across state changes", () => {
    const { result } = renderHook(() => useAdminSections());
    const { reset, setOpen } = result.current;
    act(() => result.current.setOpen("a", true));
    expect(result.current.reset).toBe(reset);
    expect(result.current.setOpen).toBe(setOpen);
  });
});
