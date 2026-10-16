import { useMemo, useRef, useState } from "react";

/** An editable admin list row: saved rows have `id`; unsaved drafts carry a client-only `draftKey`. */
export type AdminDraftRow = { id?: string | null; draftKey?: string };

/**
 * A row the server just saved or deleted. `savedId` is the id the save returned (for a new draft, or a
 * policy upserted onto an existing slug). `version` is the row's edit count when Save was clicked;
 * omit it for deletes.
 */
export type SettledAdminRow = { key: string; savedId?: string | null; version?: number };

let draftSeq = 0;

function newDraftKey(): string {
  draftSeq += 1;
  return `draft-${draftSeq}`;
}

/** Stable panel/React key for a row; it doesn't shift when rows are added, removed or reordered. */
export function adminRowKey(row: AdminDraftRow): string {
  const key = row.id || row.draftKey;
  if (!key) throw new Error("Admin row has neither id nor draftKey");
  return key;
}

function withoutDraftKey<T extends AdminDraftRow>(row: T, id: string): T {
  const copy = { ...row, id };
  delete copy.draftKey;
  return copy;
}

/**
 * Rows after a reload:
 * - unsaved drafts stay on top;
 * - saved rows in `edited` keep the local copy;
 * - rows in `serverWins` (just saved/deleted with no later edits) take the server copy;
 * - `carriedDrafts` are drafts saved while the admin kept typing: they become the saved row (by id)
 *   with the local content, so nothing typed after clicking Save is lost.
 */
export function mergeReloadedRows<T extends AdminDraftRow>(
  local: readonly T[],
  fresh: readonly T[],
  {
    edited,
    serverWins = new Set(),
    settledDraftKeys = new Set(),
    carriedDrafts = new Map(),
  }: {
    edited: ReadonlySet<string>;
    serverWins?: ReadonlySet<string>;
    settledDraftKeys?: ReadonlySet<string>;
    carriedDrafts?: ReadonlyMap<string, string>;
  },
): T[] {
  const drafts = local.filter((r) => !r.id && r.draftKey && !settledDraftKeys.has(r.draftKey));
  const byKey = new Map(local.map((r) => [adminRowKey(r), r]));
  return [
    ...drafts,
    ...fresh.map((row) => {
      if (!row.id) return row;
      const draftKey = carriedDrafts.get(row.id);
      const draft = draftKey ? byKey.get(draftKey) : undefined;
      if (draft) return withoutDraftKey(draft, row.id);
      if (serverWins.has(row.id) || !edited.has(row.id)) return row;
      return byKey.get(row.id) ?? row;
    }),
  ];
}

/**
 * Local editable copy of an admin list. `reload` keeps unsaved drafts and any saved row the admin has
 * edited since it was last saved, applies only the newest response, and settles rows just saved/deleted.
 */
export function useEditableAdminRows<T extends AdminDraftRow>() {
  const [rows, setRows] = useState<T[]>([]);
  const edited = useRef(new Set<string>());
  const versions = useRef(new Map<string, number>());
  const pendingSettled = useRef<SettledAdminRow[]>([]);
  const reloadSeq = useRef(0);

  const actions = useMemo(() => {
    const apply = (fresh: T[], settled: SettledAdminRow[]) => {
      const serverWins = new Set<string>();
      const settledDraftKeys = new Set<string>();
      const carriedDrafts = new Map<string, string>();
      for (const { key, savedId, version } of settled) {
        const id = savedId || undefined;
        if (key.startsWith("draft-")) settledDraftKeys.add(key);
        const editedSinceSave =
          version !== undefined && (versions.current.get(key) ?? 0) !== version;
        if (!editedSinceSave) {
          edited.current.delete(key);
          if (id) {
            edited.current.delete(id);
            serverWins.add(id);
          }
          serverWins.add(key);
          continue;
        }
        const target = id ?? key;
        if (target !== key) {
          edited.current.delete(key);
          versions.current.set(target, versions.current.get(key) ?? 0);
          carriedDrafts.set(target, key);
        }
        edited.current.add(target);
      }
      const editedNow = new Set(edited.current);
      setRows((local) =>
        mergeReloadedRows(local, fresh, {
          edited: editedNow,
          serverWins,
          settledDraftKeys,
          carriedDrafts,
        }),
      );
    };

    return {
      /** Fetches the list; stale responses are ignored and their settled rows applied with the newest one. */
      reload: async (fetch: () => Promise<T[]>, settled: SettledAdminRow[] = []) => {
        pendingSettled.current.push(...settled);
        const seq = ++reloadSeq.current;
        const fresh = await fetch();
        if (seq !== reloadSeq.current) return;
        apply(fresh, pendingSettled.current.splice(0));
      },
      /** Call when Save is clicked; pass the saved id to the result once the save succeeds. */
      beginSave: (key: string) => {
        const version = versions.current.get(key) ?? 0;
        return (savedId?: string | null): SettledAdminRow => ({ key, savedId, version });
      },
      addDraft: (row: T) => {
        const draftKey = newDraftKey();
        setRows((r) => [{ ...row, draftKey }, ...r]);
        return draftKey;
      },
      updateRow: (key: string, patch: Partial<T>) => {
        edited.current.add(key);
        versions.current.set(key, (versions.current.get(key) ?? 0) + 1);
        setRows((r) => r.map((x) => (adminRowKey(x) === key ? { ...x, ...patch } : x)));
      },
      /** Discards an unsaved draft. Saved rows are never removed locally; delete them via the API and reload. */
      removeDraft: (key: string) => {
        edited.current.delete(key);
        setRows((r) => r.filter((x) => x.id || adminRowKey(x) !== key));
      },
    };
  }, []);

  return { rows, ...actions };
}

/** Saved rows only, e.g. for matching imports against what already exists on the server. */
export function savedAdminRows<T extends AdminDraftRow>(rows: readonly T[]): T[] {
  return rows.filter((r) => r.id);
}
