import {
  bookOrderReturnOutbound,
  type AdminOrderReturnRow,
  type AdminOrderRow,
} from "@/lib/orders.functions";

const EMPTY_SYNC = {
  zoho_sync_status: "",
  zoho_sync_error: "",
  zoho_last_synced_at: "",
  zoho_sales_order_id: "",
  zoho_invoice_id: "",
  zoho_sync_eligible: false,
  ledgerly_sync_status: "",
  ledgerly_sync_error: "",
  ledgerly_last_synced_at: "",
  ledgerly_sales_order_id: "",
  ledgerly_invoice_id: "",
  ledgerly_sync_eligible: false,
};

/** Maps an exchange return onto the forward-delivery package / shipment dialogs. */
export function exchangeToFulfillmentOrder(row: AdminOrderReturnRow): AdminOrderRow {
  const qty = Math.max(
    1,
    Number(row.items?.[0]?.accepted_qty ?? row.items?.[0]?.requested_qty ?? 1) || 1,
  );
  return {
    id: row.id,
    order_number: row.return_number,
    total: Number(row.replacement_item_amount ?? row.order_total ?? 0),
    cod_fee: 0,
    status: row.status,
    placed_at: row.requested_at,
    payment_status: "paid",
    payment_method: "prepaid",
    ...EMPTY_SYNC,
    can_complete_package: Boolean(row.can_complete_package),
    can_book_shipment: Boolean(row.can_book_outbound),
    can_book_expressbees_shipment: Boolean(row.can_book_outbound),
    default_package_weight_grams: row.default_package_weight_grams,
    package_weight_grams: row.package_weight_grams,
    package_length_cm: row.package_length_cm,
    package_breadth_cm: row.package_breadth_cm,
    package_height_cm: row.package_height_cm,
    package_chargeable_weight_grams: row.package_chargeable_weight_grams,
    package_courier_id: row.package_courier_id || "",
    package_courier_name: row.package_courier_name || "",
    package_type: row.package_type || "custom",
    package_box_id: row.package_box_id ?? null,
    packed_at: row.packed_at || "",
    package_slip_number: row.package_slip_number || undefined,
    shipment_order_number: row.shipment_order_number || undefined,
    shipping_address: row.shipping_address,
    dispatch_address: row.dispatch_address,
    tracking_number: row.outbound_awb || "",
    shipment_label_url: row.outbound_label_url || "",
    order_items: [
      {
        id: row.order_item_id,
        product_name: row.product_name,
        product_image: row.product_image || undefined,
        size: row.replacement_size || row.original_size || undefined,
        color: row.replacement_color || row.original_color || undefined,
        sku: row.sku || undefined,
        qty,
      },
    ],
  };
}

export async function bookExchangeFulfillmentShipment(
  returnNumber: string,
  payload: {
    mode: "manual" | "xpressbees";
    carrier_name?: string;
    awb?: string;
    tracking_url?: string;
    label_url?: string;
    courier_id?: string;
    courier_name?: string;
    package_type?: "custom" | "box" | "envelope";
    package_box_id?: number | null;
    override_fulfillment_hold?: boolean;
    override_reason?: string;
  },
) {
  const saved = (await bookOrderReturnOutbound(returnNumber, {
    mode: payload.mode,
    carrier_name: payload.carrier_name,
    outbound_awb: payload.awb,
    tracking_url: payload.tracking_url,
    label_url: payload.label_url,
    courier_id: payload.courier_id,
    courier_name: payload.courier_name,
    package_type: payload.package_type,
    package_box_id: payload.package_box_id,
  })) as AdminOrderReturnRow;
  return {
    order: {
      tracking_number: saved?.outbound_awb || payload.awb || "",
      shipment_label_url: saved?.outbound_label_url || payload.label_url || "",
    },
  };
}
