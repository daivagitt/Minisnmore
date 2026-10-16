import type { ReactNode } from "react";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

export type ShipmentCancelMode = "xpressbees" | "manual";

type AdminReasonDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: ReactNode;
  reason: string;
  onReasonChange: (value: string) => void;
  reasonPlaceholder?: string;
  submitLabel: string;
  onSubmit: () => void;
  submitDisabled?: boolean;
  showShipmentMode?: boolean;
  shipmentMode?: ShipmentCancelMode;
  onShipmentModeChange?: (mode: ShipmentCancelMode) => void;
};

export function AdminReasonDialog({
  open,
  onOpenChange,
  title,
  description,
  reason,
  onReasonChange,
  reasonPlaceholder,
  submitLabel,
  onSubmit,
  submitDisabled = false,
  showShipmentMode = false,
  shipmentMode = "xpressbees",
  onShipmentModeChange,
}: AdminReasonDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        {typeof description === "string" ? (
          <p className="text-sm text-muted-foreground">{description}</p>
        ) : (
          description
        )}
        {showShipmentMode ? (
          <label className="block text-sm font-medium">
            Mode
            <select
              value={shipmentMode}
              onChange={(event) => onShipmentModeChange?.(event.target.value as ShipmentCancelMode)}
              className="mt-1 h-10 w-full rounded-xl border border-border bg-background px-3"
            >
              <option value="xpressbees">ExpressBees API</option>
              <option value="manual">Manual carrier confirmation</option>
            </select>
          </label>
        ) : null}
        <label className="block text-sm font-medium">
          Reason
          <textarea
            value={reason}
            onChange={(event) => onReasonChange(event.target.value)}
            placeholder={reasonPlaceholder}
            className="mt-1 min-h-20 w-full rounded-xl border border-border bg-background px-3 py-2"
          />
        </label>
        <DialogFooter>
          <button
            type="button"
            onClick={() => void onSubmit()}
            disabled={submitDisabled || reason.trim().length < 5}
            className="rounded-full bg-destructive px-4 py-2 text-sm text-destructive-foreground disabled:opacity-50"
          >
            {submitLabel}
          </button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
