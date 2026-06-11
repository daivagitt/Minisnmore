import { useEffect, useState } from "react";
import { listPageVisibility } from "@/lib/backend-data";

let cache: Record<string, boolean> | null = null;
let inflight: Promise<Record<string, boolean>> | null = null;
const subs = new Set<(m: Record<string, boolean>) => void>();

async function load(): Promise<Record<string, boolean>> {
  if (cache) return cache;
  if (inflight) return inflight;
  inflight = (async () => {
    try {
      const rows = await listPageVisibility();
      const map: Record<string, boolean> = {};
      rows.forEach((r) => {
        map[r.page_key] = r.visible;
      });
      cache = map;
      subs.forEach((fn) => fn(map));
      return map;
    } catch (err) {
      if (import.meta.env.DEV) {
        console.error("usePageVisibility load failed", err);
      }
      inflight = null;
      throw err;
    } finally {
      inflight = null;
    }
  })();
  return inflight;
}

export function invalidatePageVisibility() {
  cache = null;
  void load();
}

export function usePageVisibility() {
  const [map, setMap] = useState<Record<string, boolean>>(cache ?? {});
  useEffect(() => {
    let active = true;
    if (!cache) {
      load()
        .then((m) => active && setMap(m))
        .catch(() => {});
    }
    const sub = (m: Record<string, boolean>) => active && setMap(m);
    subs.add(sub);
    return () => {
      active = false;
      subs.delete(sub);
    };
  }, []);
  const isVisible = (key: string) => map[key] !== false;
  return { map, isVisible };
}
