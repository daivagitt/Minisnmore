import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { invalidatePageVisibility } from "@/hooks/usePageVisibility";
import { toast } from "sonner";
import { Eye, EyeOff } from "lucide-react";
import { listPageVisibility, savePageVisibility, type PageVisibilityRow } from "@/lib/backend-data";
import { showApiError } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/pages")({ component: PagesPage });

const LABELS: Record<string, { name: string; desc: string }> = {
  shop: { name: "Shop", desc: "Browse all products" },
  gift: { name: "Gift", desc: "Curated gift sets and gift cards" },
  subscribe: { name: "Subscribe", desc: "Monthly subscription plans" },
  track: { name: "Track", desc: "Order tracking page" },
  blog: { name: "Blogs", desc: "Stories & articles" },
};

type Row = PageVisibilityRow;

function PagesPage() {
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      setRows(await listPageVisibility(true));
    } catch (error) {
      showApiError(error, "Could not load page visibility");
    }
    setLoading(false);
  };
  useEffect(() => {
    load();
  }, []);

  const toggle = async (r: Row) => {
    const next = !r.visible;
    setRows((rs) => rs.map((x) => (x.id === r.id ? { ...x, visible: next } : x)));
    try {
      await savePageVisibility({ ...r, visible: next });
      toast.success(`${LABELS[r.page_key]?.name ?? r.page_key} ${next ? "shown" : "hidden"}`);
      invalidatePageVisibility();
    } catch (error) {
      showApiError(error, "Could not update page");
      load();
    }
  };

  return (
    <AdminPage
      eyebrow="Site"
      title="Page visibility"
      description="Show or hide pages from the customer storefront navigation and footer only. This does not hide admin screens or change what you see here in the dashboard."
    >
      {loading ? (
        <div className="text-sm text-muted-foreground">Loading…</div>
      ) : (
        <div className="grid sm:grid-cols-2 gap-3">
          {rows.filter((r) => r.page_key in LABELS).map((r) => {
            const meta = LABELS[r.page_key] ?? { name: r.page_key, desc: "" };
            return (
              <button
                key={r.id}
                onClick={() => toggle(r)}
                className={`glass-strong rounded-3xl p-5 text-left flex items-center gap-4 transition ${r.visible ? "" : "opacity-60"}`}
              >
                <div
                  className={`h-11 w-11 rounded-full grid place-items-center ${r.visible ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"}`}
                >
                  {r.visible ? <Eye className="h-5 w-5" /> : <EyeOff className="h-5 w-5" />}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="font-medium">{meta.name}</div>
                  <div className="text-xs text-muted-foreground">{meta.desc}</div>
                </div>
                <div className="text-xs uppercase tracking-wider">
                  {r.visible ? "Visible" : "Hidden"}
                </div>
              </button>
            );
          })}
        </div>
      )}
    </AdminPage>
  );
}
