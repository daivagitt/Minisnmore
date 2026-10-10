import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { listPackageBoxes } from "@/lib/package-boxes";
import { usePackageBoxes } from "./usePackageBoxes";

vi.mock("@/lib/package-boxes", async () => {
  const actual = await vi.importActual<typeof import("@/lib/package-boxes")>("@/lib/package-boxes");
  return { ...actual, listPackageBoxes: vi.fn() };
});

const mockedList = vi.mocked(listPackageBoxes);

describe("usePackageBoxes", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("does not load when disabled", () => {
    const { result } = renderHook(() => usePackageBoxes(false));
    expect(result.current.loaded).toBe(false);
    expect(result.current.boxes).toEqual([]);
    expect(mockedList).not.toHaveBeenCalled();
  });

  it("loads catalog rows when enabled", async () => {
    mockedList.mockResolvedValueOnce([
      { id: "1", name: "Small", length_cm: 10, breadth_cm: 8, height_cm: 4 },
    ]);
    const { result } = renderHook(() => usePackageBoxes(true));
    await waitFor(() => expect(result.current.loaded).toBe(true));
    expect(result.current.boxes).toHaveLength(1);
    expect(result.current.error).toBeNull();
  });

  it("surfaces fetch failures instead of pretending the catalog is empty", async () => {
    const err = new Error("403");
    mockedList.mockRejectedValueOnce(err);
    const { result } = renderHook(() => usePackageBoxes(true));
    await waitFor(() => expect(result.current.loaded).toBe(true));
    expect(result.current.boxes).toEqual([]);
    expect(result.current.error).toBe(err);
  });
});
