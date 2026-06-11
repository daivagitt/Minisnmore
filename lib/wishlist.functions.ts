import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { z } from "zod";

const ProductIdInput = z.object({ product_id: z.string().min(1).max(120) });
const SyncInput = z.object({
  product_ids: z.array(z.string().min(1).max(120)).max(500),
});

export const listWishlist = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }) => {
    const { supabase, userId } = context;
    const { data, error } = await supabase
      .from("wishlist_items")
      .select("product_id")
      .eq("user_id", userId);
    if (error) throw new Error(error.message);
    return { product_ids: (data ?? []).map((r) => r.product_id) };
  });

export const addToWishlist = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => ProductIdInput.parse(d))
  .handler(async ({ data, context }) => {
    const { supabase, userId } = context;
    const { error } = await supabase
      .from("wishlist_items")
      .upsert(
        { user_id: userId, product_id: data.product_id },
        { onConflict: "user_id,product_id" }
      );
    if (error) throw new Error(error.message);
    return { ok: true };
  });

export const removeFromWishlist = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => ProductIdInput.parse(d))
  .handler(async ({ data, context }) => {
    const { supabase, userId } = context;
    const { error } = await supabase
      .from("wishlist_items")
      .delete()
      .eq("user_id", userId)
      .eq("product_id", data.product_id);
    if (error) throw new Error(error.message);
    return { ok: true };
  });

// Merge local guest wishlist into server-side wishlist on login.
export const syncWishlist = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => SyncInput.parse(d))
  .handler(async ({ data, context }) => {
    const { supabase, userId } = context;
    if (data.product_ids.length) {
      const rows = data.product_ids.map((pid) => ({ user_id: userId, product_id: pid }));
      const { error } = await supabase
        .from("wishlist_items")
        .upsert(rows, { onConflict: "user_id,product_id" });
      if (error) throw new Error(error.message);
    }
    const { data: all, error: lErr } = await supabase
      .from("wishlist_items")
      .select("product_id")
      .eq("user_id", userId);
    if (lErr) throw new Error(lErr.message);
    return { product_ids: (all ?? []).map((r) => r.product_id) };
  });