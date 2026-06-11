import { useEffect, useState } from "react";
import { listCategories } from "@/lib/backend-data";

export type Category = { id: string; slug: string; name: string; image_url: string | null; description: string | null };

let cache: Category[] | null = null;
let inflight: Promise<Category[]> | null = null;
const subs = new Set<(c: Category[]) => void>();

async function load(): Promise<Category[]> {
  if (cache) return cache;
  if (inflight) return inflight;
  inflight = (async () => {
    try {
      const rows = await listCategories();
      const list: Category[] = rows
        .filter((row) => row.active)
        .map((row) => ({
          id: row.id ?? row.slug,
          slug: row.slug,
          name: row.name,
          image_url: row.image_url,
          description: row.description,
        }));
      cache = list;
      subs.forEach((fn) => fn(list));
      return list;
    } catch (err) {
      if (import.meta.env.DEV) {
        console.error("useCategories load failed", err);
      }
      inflight = null;
      throw err;
    } finally {
      inflight = null;
    }
  })();
  return inflight;
}

export function invalidateCategories() {
  cache = null;
  inflight = null;
  void load();
}

export function useCategories() {
  const [categories, setCategories] = useState<Category[]>(cache ?? []);
  useEffect(() => {
    let active = true;
    if (!cache) {
      load()
        .then((c) => active && setCategories(c))
        .catch(() => {});
    }
    const sub = (c: Category[]) => active && setCategories(c);
    subs.add(sub);
    return () => {
      active = false;
      subs.delete(sub);
    };
  }, []);
  return categories;
}
