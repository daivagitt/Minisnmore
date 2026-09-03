import { useEffect, useState } from "react";
import { listHomeSections } from "@/lib/backend-data";
import { broadcastStorefrontInvalidation } from "@/lib/storefront-cache-broadcast";

export type HomeSection = {
  id: string;
  key: string;
  title: string | null;
  subtitle: string | null;
  enabled: boolean;
  sort_order: number;
  payload: unknown;
};

let cache: HomeSection[] | null = null;
let inflight: Promise<HomeSection[]> | null = null;
const subs = new Set<(s: HomeSection[]) => void>();

async function load(): Promise<HomeSection[]> {
  if (cache) return cache;
  if (inflight) return inflight;
  inflight = (async () => {
    try {
      const rows = await listHomeSections();
      const list: HomeSection[] = rows.map((row) => ({
        id: row.id,
        key: row.key,
        title: row.title,
        subtitle: row.subtitle,
        enabled: row.enabled,
        sort_order: row.sort_order ?? 0,
        payload: row.payload,
      }));
      cache = list;
      subs.forEach((fn) => fn(list));
      return list;
    } catch (err) {
      if (import.meta.env.DEV) {
        console.error("useHomeSections load failed", err);
      }
      cache = [];
      subs.forEach((fn) => fn([]));
      return [];
    } finally {
      inflight = null;
    }
  })();
  return inflight;
}

/** Warm home-section config before the homepage mounts. */
void load();

export function invalidateHomeSections(options?: { broadcast?: boolean }) {
  cache = null;
  void load();
  if (options?.broadcast !== false) broadcastStorefrontInvalidation("homeSections");
}

export function useHomeSections() {
  const [list, setList] = useState<HomeSection[]>(cache ?? []);
  const [loaded, setLoaded] = useState<boolean>(cache !== null);
  useEffect(() => {
    let alive = true;
    if (!cache) {
      load()
        .then((v) => {
          if (alive) {
            setList(v);
            setLoaded(true);
          }
        })
        .catch(() => {
          if (alive) setLoaded(true);
        });
    }
    const sub = (v: HomeSection[]) => {
      if (alive) {
        setList(v);
        setLoaded(true);
      }
    };
    subs.add(sub);
    return () => {
      alive = false;
      subs.delete(sub);
    };
  }, []);
  const byKey = new Map(list.map((s) => [s.key, s]));
  const isEnabled = (key: string) => (loaded ? (byKey.get(key)?.enabled ?? true) : true);
  const titleFor = (key: string, fallback: string) => byKey.get(key)?.title?.trim() || fallback;
  const subtitleFor = (key: string, fallback?: string) => byKey.get(key)?.subtitle?.trim() || fallback || "";
  return { list, byKey, loaded, isEnabled, titleFor, subtitleFor };
}
