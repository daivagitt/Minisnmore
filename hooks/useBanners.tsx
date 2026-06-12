import { useEffect, useState } from "react";
import { listBanners } from "@/lib/backend-data";

export type HomeBanner = {
  id: string;
  title: string;
  subtitle: string | null;
  image_url: string | null;
  link_url: string | null;
  cta_label: string | null;
};

let cache: HomeBanner[] | null = null;
let inflight: Promise<HomeBanner[]> | null = null;
const subs = new Set<(b: HomeBanner[]) => void>();

function mapRows(rows: Awaited<ReturnType<typeof listBanners>>): HomeBanner[] {
  return rows
    .filter((b) => b.active)
    .slice(0, 6)
    .map((b) => ({
      id: b.id,
      title: b.title,
      subtitle: b.subtitle,
      image_url: b.image_url,
      link_url: b.link_url,
      cta_label: b.cta_label,
    }));
}

async function load(): Promise<HomeBanner[]> {
  if (cache) return cache;
  if (inflight) return inflight;
  inflight = (async () => {
    try {
      const list = mapRows(await listBanners());
      cache = list;
      subs.forEach((fn) => fn(list));
      return list;
    } catch (err) {
      if (import.meta.env.DEV) {
        console.error("useBanners load failed", err);
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

/** Warm the banner cache as soon as this module loads. */
void load();

export function invalidateBanners() {
  cache = null;
  void load();
}

export function useBanners(): { banners: HomeBanner[]; loaded: boolean } {
  const [banners, setBanners] = useState<HomeBanner[]>(cache ?? []);
  const [loaded, setLoaded] = useState(cache !== null);
  useEffect(() => {
    let alive = true;
    if (cache) {
      setBanners(cache);
      setLoaded(true);
      return;
    }
    load()
      .then((list) => {
        if (alive) {
          setBanners(list);
          setLoaded(true);
        }
      })
      .catch(() => {
        if (alive) setLoaded(true);
      });
    const sub = (list: HomeBanner[]) => {
      if (alive) {
        setBanners(list);
        setLoaded(true);
      }
    };
    subs.add(sub);
    return () => {
      alive = false;
      subs.delete(sub);
    };
  }, []);
  return { banners, loaded };
}
