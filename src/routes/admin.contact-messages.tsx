import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { Loader2, Trash2 } from "lucide-react";
import { toast } from "sonner";
import {
  deleteAdminContactMessage,
  listAdminContactMessages,
  type ContactMessageRow,
} from "@/lib/contact-messages";
import { showApiError } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/contact-messages")({
  component: AdminContactMessagesPage,
});

function formatWhen(value?: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" });
}

function AdminContactMessagesPage() {
  const [rows, setRows] = useState<ContactMessageRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await listAdminContactMessages());
    } catch (error) {
      showApiError(error, "Could not load messages");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const remove = async (id?: string) => {
    if (!id) return;
    if (!confirm("Delete this message?")) return;
    setBusy(id);
    try {
      await deleteAdminContactMessage(id);
      toast.success("Deleted");
      await load();
    } catch (error) {
      showApiError(error, "Could not delete message");
    } finally {
      setBusy(null);
    }
  };

  return (
    <AdminPage
      eyebrow="Care"
      title="Contact messages"
      description="Messages submitted from the Contact Us page. Email, phone, address and comments are stored here."
    >
      {loading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </div>
      ) : rows.length === 0 ? (
        <div className="glass-strong rounded-3xl p-8 text-sm text-muted-foreground">No messages yet.</div>
      ) : (
        <div className="space-y-3">
          {rows.map((row) => (
            <article key={row.id ?? `${row.email}-${row.created_date}`} className="glass-strong rounded-3xl p-5 space-y-2">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="font-display text-xl">{row.name}</div>
                  <div className="text-xs text-muted-foreground mt-0.5">{formatWhen(row.created_date)}</div>
                </div>
                <button
                  type="button"
                  onClick={() => remove(row.id)}
                  disabled={busy === row.id}
                  className="inline-flex items-center gap-1.5 rounded-full border border-border px-3 py-1.5 text-xs text-muted-foreground hover:text-destructive disabled:opacity-50"
                >
                  <Trash2 className="h-3.5 w-3.5" /> Delete
                </button>
              </div>
              <div className="grid sm:grid-cols-2 gap-x-6 gap-y-1 text-sm">
                <div>
                  <span className="text-muted-foreground">Email:</span> {row.email}
                </div>
                <div>
                  <span className="text-muted-foreground">Phone:</span> {row.phone}
                </div>
              </div>
              <div className="text-sm">
                <span className="text-muted-foreground">Address:</span> {row.address}
              </div>
              <p className="text-sm leading-relaxed whitespace-pre-wrap">{row.comment}</p>
            </article>
          ))}
        </div>
      )}
    </AdminPage>
  );
}
