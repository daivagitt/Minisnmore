import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { NumberField } from "@/components/admin/NumberField";
import { BulkImport } from "@/components/admin/BulkImport";
import { toast } from "sonner";
import { getUserErrorMessage, showApiError, showValidationErrors } from "@/lib/ui-errors";
import { Plus, Trash2, Save } from "lucide-react";
import {
  bulkSave,
  deleteCoupon,
  getCouponValidationErrors,
  listCoupons,
  saveCoupon,
  type CouponRow,
} from "@/lib/backend-data";
import { fromDatetimeLocalValue, toDatetimeLocalValue } from "@/lib/dates";

export const Route = createFileRoute("/admin/coupons")({ component: CouponsPage });

type Coupon = CouponRow;
const empty: Coupon = {
  code: "",
  discount_type: "percent",
  discount_value: null,
  min_order: null,
  max_discount: null,
  bogo_buy_qty: 1,
  bogo_get_qty: 1,
  expires_at: null,
  active: true,
};

function CouponsPage() {
  const [rows, setRows] = useState<Coupon[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      setRows(await listCoupons());
    } catch (error) {
      showApiError(error, "Could not load coupons");
    }
    setLoading(false);
  };
  useEffect(() => {
    load();
  }, []);

  const save = async (c: Coupon) => {
    const payload = {
      ...c,
      code: c.code.toUpperCase(),
    };
    const errors = getCouponValidationErrors(payload);
    if (errors.length) {
      showValidationErrors(errors);
      return;
    }
    try {
      await saveCoupon(payload);
      toast.success("Saved");
      load();
    } catch (error) {
      showApiError(error, "Could not save coupon");
    }
  };
  const remove = async (id?: string) => {
    if (!id) return setRows((r) => r.filter((x) => x.id));
    if (!confirm("Delete this coupon?")) return;
    try {
      await deleteCoupon(id);
      toast.success("Deleted");
      load();
    } catch (error) {
      showApiError(error, "Could not delete coupon");
    }
  };

  return (
    <AdminPage
      eyebrow="Marketing"
      title="Coupons"
      description="Discount codes and BOGO offers customers can apply at checkout."
      action={
        <div className="flex items-center gap-2 flex-wrap">
          <BulkImport
            entityLabel="coupons"
            templateName="coupons"
            fields={[
              {
                key: "code",
                required: true,
                example: "WELCOME10",
                note: "Letters/numbers only. Unique.",
              },
              { key: "discount_type", required: true, example: "percent", note: "percent, flat, or bogo" },
              { key: "discount_value", required: true, example: 10, note: "Percent/flat value, or % off the get item for BOGO (100 = free)" },
              { key: "min_order", example: 999 },
              { key: "max_discount", example: 500 },
              { key: "bogo_buy_qty", example: 1, note: "BOGO only. Items the customer must buy." },
              { key: "bogo_get_qty", example: 1, note: "BOGO only. Free/discounted items." },
              { key: "expires_at", example: "2026-12-31", note: "ISO date or blank" },
              { key: "active", example: "TRUE" },
            ]}
            transform={(r) => {
              const type = String(r.discount_type ?? "percent").toLowerCase();
              const row: Coupon = {
                code: String(r.code ?? "").trim().toUpperCase(),
                discount_type: type,
                discount_value:
                  r.discount_value === "" || r.discount_value == null ? null : Number(r.discount_value),
                min_order: r.min_order === "" || r.min_order == null ? null : Number(r.min_order),
                max_discount:
                  r.max_discount === "" || r.max_discount == null ? null : Number(r.max_discount),
                bogo_buy_qty: type === "bogo" ? Number(r.bogo_buy_qty ?? 1) : 1,
                bogo_get_qty: type === "bogo" ? Number(r.bogo_get_qty ?? 1) : 1,
                expires_at: r.expires_at ? new Date(r.expires_at).toISOString() : null,
                active: String(r.active ?? "true").toLowerCase() !== "false",
              };
              const errors = getCouponValidationErrors(row);
              if (errors.length) throw new Error(errors.join("\n"));
              return {
                ...row,
                min_order: Number(row.min_order) || 0,
              };
            }}
            persist={async (payloads) => {
              try {
                return await bulkSave(payloads as Coupon[], rows, "code", saveCoupon);
              } catch (error) {
                return {
                  inserted: 0,
                  errors: [
                    getUserErrorMessage(
                      error,
                      "Import failed. Please verify your sheet data and try again.",
                    ),
                  ],
                };
              }
            }}
            onDone={load}
          />
          <button
            onClick={() => setRows((r) => [{ ...empty }, ...r])}
            className="rounded-full bg-primary text-primary-foreground px-4 py-2.5 text-sm flex items-center gap-2"
          >
            <Plus className="h-4 w-4" /> New coupon
          </button>
        </div>
      }
    >
      {loading ? (
        <div className="text-sm text-muted-foreground">Loading…</div>
      ) : (
        <div className="space-y-4">
          {rows.length === 0 && (
            <div className="glass rounded-2xl p-6 text-sm text-muted-foreground text-center">
              No coupons yet.
            </div>
          )}
          {rows.map((c, i) => (
            <div
              key={c.id ?? `new-${i}`}
              className="glass-strong rounded-3xl p-5 grid sm:grid-cols-2 lg:grid-cols-3 gap-3"
            >
              <Field label="Code">
                <input
                  className={inputCls()}
                  value={c.code}
                  onChange={(e) =>
                    setRows((r) => r.map((x, j) => (j === i ? { ...x, code: e.target.value } : x)))
                  }
                />
              </Field>
              <Field label="Type">
                <select
                  className={inputCls()}
                  value={c.discount_type}
                  onChange={(e) =>
                    setRows((r) =>
                      r.map((x, j) => {
                        if (j !== i) return x;
                        const discount_type = e.target.value;
                        return {
                          ...x,
                          discount_type,
                          ...(discount_type === "bogo"
                            ? {
                                discount_value:
                                  x.discount_value && x.discount_value <= 100 ? x.discount_value : 100,
                                bogo_buy_qty: x.bogo_buy_qty ?? 1,
                                bogo_get_qty: x.bogo_get_qty ?? 1,
                              }
                            : {}),
                        };
                      }),
                    )
                  }
                >
                  <option value="percent">Percent (%)</option>
                  <option value="flat">Flat (₹)</option>
                  <option value="bogo">BOGO (buy X get Y)</option>
                </select>
              </Field>
              {c.discount_type === "bogo" ? (
                <>
                  <NumberField
                    label="Buy quantity"
                    value={c.bogo_buy_qty}
                    onChange={(bogo_buy_qty) =>
                      setRows((r) => r.map((x, j) => (j === i ? { ...x, bogo_buy_qty } : x)))
                    }
                    min={1}
                    max={10}
                    step={1}
                    required
                  />
                  <NumberField
                    label="Get quantity"
                    value={c.bogo_get_qty}
                    onChange={(bogo_get_qty) =>
                      setRows((r) => r.map((x, j) => (j === i ? { ...x, bogo_get_qty } : x)))
                    }
                    min={1}
                    max={10}
                    step={1}
                    required
                  />
                  <NumberField
                    label="% off get item"
                    hint="100 = free"
                    value={c.discount_value}
                    onChange={(discount_value) =>
                      setRows((r) => r.map((x, j) => (j === i ? { ...x, discount_value } : x)))
                    }
                    min={1}
                    max={100}
                    required
                  />
                </>
              ) : (
                <NumberField
                  label="Value"
                  value={c.discount_value}
                  onChange={(discount_value) =>
                    setRows((r) => r.map((x, j) => (j === i ? { ...x, discount_value } : x)))
                  }
                  required
                />
              )}
              <NumberField
                label="Min order (₹)"
                value={c.min_order}
                onChange={(min_order) =>
                  setRows((r) => r.map((x, j) => (j === i ? { ...x, min_order } : x)))
                }
                min={0}
                placeholder="Optional"
              />
              <NumberField
                label="Max discount (₹)"
                value={c.max_discount}
                onChange={(max_discount) =>
                  setRows((r) =>
                    r.map((x, j) => (j === i ? { ...x, max_discount } : x)),
                  )
                }
                min={0}
                placeholder="Optional"
              />
              <Field label="Expires at">
                <input
                  type="datetime-local"
                  className={inputCls()}
                  value={toDatetimeLocalValue(c.expires_at)}
                  onChange={(e) =>
                    setRows((r) =>
                      r.map((x, j) =>
                        j === i
                          ? {
                              ...x,
                              expires_at: fromDatetimeLocalValue(e.target.value),
                            }
                          : x,
                      ),
                    )
                  }
                />
              </Field>
              <label className="flex items-center gap-2 text-sm sm:col-span-2 lg:col-span-3">
                <input
                  type="checkbox"
                  checked={c.active}
                  onChange={(e) =>
                    setRows((r) =>
                      r.map((x, j) => (j === i ? { ...x, active: e.target.checked } : x)),
                    )
                  }
                />{" "}
                Active
              </label>
              <div className="sm:col-span-2 lg:col-span-3 flex gap-2">
                <button
                  onClick={() => save(c)}
                  className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm flex items-center gap-2"
                >
                  <Save className="h-4 w-4" /> Save
                </button>
                <button
                  onClick={() => remove(c.id)}
                  className="rounded-full glass px-4 py-2 text-sm flex items-center gap-2 text-destructive"
                >
                  <Trash2 className="h-4 w-4" /> Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </AdminPage>
  );
}
