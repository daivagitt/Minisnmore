import { useEffect, useState } from "react";
import { listPackageBoxes, type PackageBoxRow } from "@/lib/package-boxes";

export function usePackageBoxes(enabled: boolean) {
  const [boxes, setBoxes] = useState<PackageBoxRow[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    if (!enabled) {
      setLoaded(false);
      setError(null);
      return;
    }
    let cancelled = false;
    setLoaded(false);
    setError(null);
    void listPackageBoxes()
      .then((rows) => {
        if (cancelled) return;
        setBoxes(rows);
        setLoaded(true);
      })
      .catch((err) => {
        if (cancelled) return;
        setBoxes([]);
        setLoaded(true);
        setError(err);
      });
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  return { boxes, loaded, error };
}
