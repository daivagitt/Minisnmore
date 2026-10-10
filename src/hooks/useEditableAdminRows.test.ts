import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  adminRowKey,
  mergeReloadedRows,
  savedAdminRows,
  useEditableAdminRows,
  type SettledAdminRow,
} from "./useEditableAdminRows";

type Row = { id?: string; draftKey?: string; title: string; excerpt?: string | null };

function setup() {
  const hook = renderHook(() => useEditableAdminRows<Row>());
  const reload = (rows: Row[], settled?: SettledAdminRow[]) =>
    act(() => hook.result.current.reload(() => Promise.resolve(rows), settled));
  return { result: hook.result, reload };
}

describe("adminRowKey", () => {
  it("prefers the saved id and refuses rows without any key", () => {
    expect(adminRowKey({ draftKey: "draft-1" })).toBe("draft-1");
    expect(adminRowKey({ id: "7", draftKey: "draft-1" })).toBe("7");
    expect(() => adminRowKey({})).toThrow();
  });
});

describe("mergeReloadedRows", () => {
  const server: Row[] = [
    { id: "1", title: "one" },
    { id: "2", title: "two" },
  ];

  it("keeps unsettled drafts on top and edited rows' local copies", () => {
    const keep: Row = { draftKey: "draft-a", title: "keep" };
    const saved: Row = { draftKey: "draft-b", title: "saved" };
    const local: Row[] = [keep, saved, { id: "1", title: "one (unsaved)" }, server[1]];
    const fresh: Row[] = [...server, { id: "3", title: "saved" }];
    expect(
      mergeReloadedRows(local, fresh, {
        edited: new Set(["1"]),
        settledDraftKeys: new Set(["draft-b"]),
      }),
    ).toEqual([keep, { id: "1", title: "one (unsaved)" }, server[1], fresh[2]]);
  });

  it("server copy wins for settled rows even if they were edited", () => {
    const local: Row[] = [{ id: "1", title: "local" }];
    expect(
      mergeReloadedRows(local, [server[0]], { edited: new Set(["1"]), serverWins: new Set(["1"]) }),
    ).toEqual([server[0]]);
  });

  it("drops rows that no longer exist on the server", () => {
    expect(mergeReloadedRows(server, [server[1]], { edited: new Set(["1"]) })).toEqual([server[1]]);
  });
});

describe("useEditableAdminRows", () => {
  it("keeps an edited row across reloads until it is saved", async () => {
    const { result, reload } = setup();
    await reload([{ id: "1", title: "one" }]);
    act(() => result.current.updateRow("1", { title: "edited" }));
    await reload([{ id: "1", title: "one" }]);
    expect(result.current.rows).toEqual([{ id: "1", title: "edited" }]);

    const settle = result.current.beginSave("1");
    await reload([{ id: "1", title: "edited" }], [settle("1")]);
    await reload([{ id: "1", title: "changed elsewhere" }]);
    expect(result.current.rows).toEqual([{ id: "1", title: "changed elsewhere" }]);
  });

  it("a field cleared from null to empty is clean again after save", async () => {
    const { result, reload } = setup();
    await reload([{ id: "1", title: "one", excerpt: null }]);
    act(() => result.current.updateRow("1", { excerpt: "" }));
    const settle = result.current.beginSave("1");
    await reload([{ id: "1", title: "one", excerpt: null }], [settle("1")]);
    await reload([{ id: "1", title: "server update", excerpt: null }]);
    expect(result.current.rows).toEqual([{ id: "1", title: "server update", excerpt: null }]);
  });

  it("keeps edits typed after clicking Save", async () => {
    const { result, reload } = setup();
    await reload([{ id: "1", title: "one" }]);
    act(() => result.current.updateRow("1", { title: "sent" }));
    const settle = result.current.beginSave("1");
    act(() => result.current.updateRow("1", { title: "typed during save" }));
    await reload([{ id: "1", title: "sent" }], [settle("1")]);
    expect(result.current.rows).toEqual([{ id: "1", title: "typed during save" }]);
  });

  it("turns a draft edited during its save into the saved row with the local content", async () => {
    const { result, reload } = setup();
    await reload([]);
    let draft = "";
    act(() => {
      draft = result.current.addDraft({ title: "" });
    });
    act(() => result.current.updateRow(draft, { title: "sent" }));
    const settle = result.current.beginSave(draft);
    act(() => result.current.updateRow(draft, { title: "sent + more" }));
    await reload([{ id: "9", title: "sent" }], [settle("9")]);
    expect(result.current.rows).toEqual([{ id: "9", title: "sent + more" }]);
  });

  it("a draft saved onto an existing row (upsert) replaces that row's unsaved local copy", async () => {
    const { result, reload } = setup();
    await reload([{ id: "1", title: "terms" }]);
    act(() => result.current.updateRow("1", { title: "old local edit" }));
    let draft = "";
    act(() => {
      draft = result.current.addDraft({ title: "" });
    });
    act(() => result.current.updateRow(draft, { title: "terms v2" }));
    const settle = result.current.beginSave(draft);
    await reload([{ id: "1", title: "terms v2" }], [settle("1")]);
    expect(result.current.rows).toEqual([{ id: "1", title: "terms v2" }]);
  });

  it("ignores a stale reload but still settles its rows with the newest one", async () => {
    const { result, reload } = setup();
    await reload([{ id: "1", title: "one" }]);
    act(() => result.current.updateRow("1", { title: "saved A" }));
    const settleA = result.current.beginSave("1");

    let resolveStale: (rows: Row[]) => void = () => {};
    let stale: Promise<void> = Promise.resolve();
    act(() => {
      stale = result.current.reload(
        () => new Promise<Row[]>((resolve) => (resolveStale = resolve)),
        [settleA("1")],
      );
    });
    await reload([{ id: "1", title: "saved A" }]);
    await act(async () => {
      resolveStale([{ id: "1", title: "one" }]);
      await stale;
    });
    expect(result.current.rows).toEqual([{ id: "1", title: "saved A" }]);

    await reload([{ id: "1", title: "changed elsewhere" }]);
    expect(result.current.rows).toEqual([{ id: "1", title: "changed elsewhere" }]);
  });

  it("removeDraft discards only that draft and never a saved row", async () => {
    const { result, reload } = setup();
    await reload([{ id: "1", title: "one" }]);
    let first = "";
    let second = "";
    act(() => {
      first = result.current.addDraft({ title: "first" });
      second = result.current.addDraft({ title: "second" });
    });
    act(() => {
      result.current.removeDraft(second);
      result.current.removeDraft("1");
    });
    expect(result.current.rows).toEqual([
      { title: "first", draftKey: first },
      { id: "1", title: "one" },
    ]);
  });
});

describe("savedAdminRows", () => {
  it("excludes drafts", () => {
    expect(
      savedAdminRows<Row>([
        { draftKey: "d", title: "x" },
        { id: "1", title: "y" },
      ]),
    ).toEqual([{ id: "1", title: "y" }]);
  });
});
