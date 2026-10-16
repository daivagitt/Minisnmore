import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { FileDown, Loader2, Trash2 } from "lucide-react";
import { toast } from "sonner";
import {
  deleteAdminContactMessage,
  listAdminContactMessages,
  type ContactMessageRow,
} from "@/lib/contact-messages";
import { downloadTableAsXlsx, exportFilename, rowsForExport } from "@/lib/export-table";
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

export function AdminContactMessagesPage({ canDelete = true }: { canDelete?: boolean } = {}) {
  const [rows, setRows] = useState<ContactMessageRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);

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

  const exportMessages = async () => {
    if (!rows.length) return;
    setExporting(true);
    try {
      await downloadTableAsXlsx(
        rowsForExport(rows, [
          { header: "Name", value: (row) => row.name },
          { header: "Email", value: (row) => row.email },
          { header: "Phone", value: (row) => row.phone },
          { header: "Address", value: (row) => row.address },
          { header: "Comments", value: (row) => row.comment },
          { header: "Received", value: (row) => formatWhen(row.created_date) },
        ]),
        exportFilename("contact-messages"),
        "Contact messages",
      );
    } catch (error) {
      showApiError(error, "Could not export messages");
    } finally {
      setExporting(false);
    }
  };

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
      action={
        <button
          type="button"
          onClick={() => void exportMessages()}
          disabled={loading || exporting || rows.length === 0}
          className="inline-flex items-center gap-2 rounded-full bg-primary px-4 py-2.5 text-sm text-primary-foreground disabled:opacity-60"
        >
          <FileDown className="h-4 w-4" />
          {exporting ? "Exporting…" : "Export"}
        </button>
      }
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
                {canDelete ? (
                  <button
                    type="button"
                    onClick={() => remove(row.id)}
                    disabled={busy === row.id}
                    className="inline-flex items-center gap-1.5 rounded-full border border-border px-3 py-1.5 text-xs text-muted-foreground hover:text-destructive disabled:opacity-50"
                  >
                    <Trash2 className="h-3.5 w-3.5" /> Delete
                  </button>
                ) : null}
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
