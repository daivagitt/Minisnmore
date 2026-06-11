import { useEffect, useState } from "react";
import { getCachedPolicy, loadPolicy, subscribePolicy, type PolicyRow } from "@/lib/storefront";

export function usePolicy(slug: string): { policy: PolicyRow | null; loading: boolean } {
  const cached = getCachedPolicy(slug);
  const [policy, setPolicy] = useState<PolicyRow | null>(cached ?? null);
  const [loading, setLoading] = useState(cached === undefined);
  useEffect(() => {
    let alive = true;
    if (getCachedPolicy(slug) === undefined) {
      setLoading(true);
      loadPolicy(slug).then((p) => { if (alive) { setPolicy(p); setLoading(false); } });
    }
    const unsub = subscribePolicy(slug, (p) => { if (alive) setPolicy(p); });
    return () => { alive = false; unsub(); };
  }, [slug]);
  return { policy, loading };
}
