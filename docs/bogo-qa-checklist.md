# BOGO Promotion QA Checklist

Use this checklist when testing Buy-X-Get-Y coupons. It reflects current engine behavior after the line-allocation and eligibility-scope work.

## Intentional policy choices (not bugs)

| Policy | Behavior |
|--------|----------|
| Free item selection | **Cheapest-in-group** only. There is no “most expensive free” toggle. |
| Coupon stacking | **One coupon code at a time** (applying another replaces the first). No multi-BOGO stacking. |
| Storewide MRP/% pricing | BOGO runs on **selling price after** store discount. Both can apply. |
| Free item UX | **Manual** — customer must add qualifying items. Cart does **not** auto-inject a free SKU. Upsell copy nudges “Add N more…”. |
| Post-purchase OOS | Checkout rejects understock at reserve time. There is **no** post-place BOGO rebalance if fulfillment later fails one line (cancel/refund uses order totals). |

---

## A. Pricing & combinations

- [ ] **Cheapest free:** Cart has ₹1000 + ₹500 under Buy 1 Get 1 Free → discount = ₹500 (cheaper unit).
- [ ] **Mixed SKUs (scope = Entire cart):** Different products/prices pool together; cheapest-in-group still wins.
- [ ] **Same SKU only:** With scope `same_sku`, two different SKUs (1 each) do **not** unlock BOGO; two units of the **same** SKU do.
- [ ] **Category scope:** With scope `category`, only items in the selected category count; out-of-category lines stay full price and do not fill the buy/get set.
- [ ] **Odd quantity:** 3 units under Buy 1 Get 1 → 1 free (cheapest of a pair), 1 leftover at full price; nudge may ask for 1 more item.
- [ ] **Qty scale:** Update line qty from 2 → 20 under Buy 1 Get 1 Free → exactly 10 free units (discount scales).
- [ ] **Buy 2 Get 1 / % off get item:** Configurable buy/get qty and “% off get item” (100 = free) match admin settings.
- [ ] **Max discount cap:** When max discount is set, total BOGO savings never exceed the cap.

---

## B. Cart / checkout UX

- [ ] Apply BOGO on cart; discount, `bogo_details`, and optional nudge render correctly.
- [ ] Changing cart (add/remove/qty) re-validates the coupon; invalid/incomplete BOGO clears or shows eligibility error.
- [ ] Min order (if set) is evaluated on the **qualifying** subtotal (for category scope: only that category’s lines).
- [ ] Checkout places order with the same discount as cart validation (selling prices).

---

## C. Returns & refunds (line allocation)

Prerequisite: order placed **after** this change so `order_item.discount_allocation` is populated.

- [ ] **Return free line only:** Order paid ₹1000 for ₹1000+₹500 BOGO (₹500 free). Returning the free item refunds **₹0** (or near-zero after rounding).
- [ ] **Return paid line only:** Same order, returning the paid item refunds **~₹1000** (full paid amount), not ₹750 gross-ratio.
- [ ] **Partial qty on a multi-qty line:** Returning 1 of 2 units refunds half of that line’s stored allocation share.
- [ ] **Legacy orders** (no line allocations): still use gross-ratio prorate of order-level discount (backward compatible).

---

## D. Stacking & multi-promo

- [ ] Applying a second coupon code replaces the first (cannot run two codes together).
- [ ] BOGO + existing store MRP discount both appear (product discount + coupon discount) without double-counting the same coupon.
- [ ] Two concurrent BOGO campaigns on separate categories: only one code can be applied at a time (document as known limitation).

---

## E. Admin / config

- [ ] Create/edit coupon with type BOGO, buy/get qty, % off get item.
- [ ] Set **Applies to**: Entire cart / Same SKU only / One category (category required for category scope).
- [ ] Bulk import accepts `eligibility_scope` and `eligibility_category_id`.
- [ ] Active coupons API returns `eligibility_scope` and `eligibility_category_id`.

---

## F. Known gaps (do not fail as regressions)

- [ ] Configurable “most expensive free” — **not implemented**.
- [ ] Auto-add free gift line — **not implemented**.
- [ ] Post-fulfillment OOS / cancel BOGO deal repair — **not implemented**.
- [ ] Multiple simultaneous coupon codes — **not implemented**.

---

## Quick smoke script

1. Admin: create `BOGOTEST` Buy 1 Get 1 Free, scope Entire cart, active.
2. Storefront: add ₹X and ₹Y items (X > Y), apply `BOGOTEST` → discount = Y.
3. Place order → inspect DB `order_item.discount_allocation` (higher-priced line ≈ 0, cheaper ≈ Y).
4. Request return of cheaper line → refund ≈ 0; return of dearer line → refund ≈ X.
5. Change coupon scope to Same SKU; cart with two different products (qty 1 each) → BOGO not eligible; qty 2 of one SKU → eligible.
