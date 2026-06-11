# Zoho Books Integration — Test Scenario Report

**Product:** Mini's n More storefront (Admin + Spring Boot + Zoho Books v3)  
**Audience:** QA, engineering team lead, release sign-off  
**Last updated:** 2026-06-02  
**Environment under test:** UAT (`https://uat.minisnmore.com`) or local (`http://localhost:5173` + API **gateway** `:8080`)

---

## 1. Executive summary

The store integrates with **Zoho Books (India data center)** for catalogue sync, inventory, paid-order fulfilment (sales order + invoice), shipment tracking, and **credit notes on order returns** (refund flow). Integration is driven from **Admin → Zoho settings** (`/admin/zoho`), **Admin → Order returns** (`/admin/returns`), and background schedulers on the backend.

This document lists **production-level test scenarios**, expected results, and evidence to capture for team-lead sign-off.

| Area | Automated tests | Manual UAT required |
|------|-----------------|---------------------|
| OAuth / connection | Partial (unit) | Yes |
| Push products → Zoho | Partial | Yes |
| Pull products ← Zoho | Partial | Yes |
| Product images (pull) | Partial | Yes |
| Inventory pull | Partial | Yes |
| Order sync (post-payment) | Partial | Yes |
| Tracking pull | Partial | Yes |
| Credit notes (returns) | Partial | Yes |
| Failure / recovery | Partial | Yes |

---

## 2. Prerequisites

### 2.1 Zoho Books organisation

- [ ] Zoho Books org with **Items**, **Sales orders**, **Invoices**, and (recommended) **Locations** enabled  
- [ ] **Item Image** enabled: Settings → Items → Field Customization  
- [ ] **Inventory tracking** enabled for SKUs that need stock sync  
- [ ] At least one **warehouse/location** (e.g. “Head Office”)  
- [ ] Test items in Zoho with: name, SKU, rate, stock, category (custom field or `Category:` in description), **uploaded image**, optional sizes/colors in custom fields or description lines  

### 2.2 Zoho API Console (Server-based application)

- [ ] App type: **Server-based** (not client-only)  
- [ ] Data center: **India** (`in`) unless org is on `.com` / EU  
- [ ] Redirect URI includes: `{origin}/admin/zoho`  
- [ ] JavaScript domain includes storefront origin  
- [ ] OAuth scope includes: `ZohoBooks.fullaccess.all` (or minimum: settings + sales + documents read/write as used by sync)

### 2.3 Server environment variables

| Variable | Purpose |
|----------|---------|
| `ZOHO_CLIENT_ID` | OAuth client ID |
| `ZOHO_CLIENT_SECRET` | OAuth secret (vault only) |
| `ZOHO_ORG_ID` | Books organisation ID (6–15 digits) |
| `ZOHO_DATA_CENTER` | `in` (default) |
| `ZOHO_OAUTH_*` | Homepage, redirect, JS domain URLs |
| `ZOHO_SYNC_PRODUCT_IMAGES` | `true` to push/pull images |
| `ZOHO_SYNC_PRODUCT_STOCK` | `true` for inventory item payloads |
| `ZOHO_INVENTORY_ACCOUNT_ID` | Required if stock sync uses inventory items |
| `ZOHO_DEFAULT_LOCATION_ID` | Opening stock / location on items & SO lines |
| `ZOHO_PULL_PRODUCTS_AUTO_CREATE_CATEGORY` | `true` to create Admin categories on pull |
| `STORAGE_*` / `S3_PUBLIC_BASE_URL` | R2/S3 for **imported** Zoho images on pull |

### 2.4 Store prerequisites

- [ ] Admin user with `ROLE_ADMIN`  
- [ ] Active products in Admin with **SKU** + **category** (for push)  
- [ ] R2/S3 uploads working (product images in Admin)  
- [ ] Razorpay configured for a real **paid** test order (order sync path)  
- [ ] **Order return** functionality enabled (customer `/account/returns`, admin `/admin/returns`)  
- [ ] **Razorpay refund** credentials configured (not demo mode) for admin **Issue refund**  
- [ ] **Test orders eligible for returns**: paid, delivered (shipment date or tracking), within 7-day return window  

---

## 3. Test entry points

| Entry | URL / API |
|-------|-----------|
| Admin UI | `/admin/zoho` |
| Integration status | `GET /api/v1/admin/zoho/integration-status` |
| Test connection | `GET /api/v1/admin/zoho/test` |
| Connect (OAuth) | `GET /api/v1/admin/zoho/connect-url` → browser → `POST /api/v1/admin/zoho/oauth/complete` |
| Sync products → Zoho | `POST /api/v1/admin/zoho/sync-products` |
| Pull products ← Zoho | `POST /api/v1/admin/zoho/pull-products` |
| Pull inventory | `POST /api/v1/admin/zoho/pull-inventory` |
| Retry orders | `POST /api/v1/admin/zoho/retry-orders` |
| Sync one order | `POST /api/v1/admin/zoho/sync-orders/by-number/{orderNumber}` |
| Pull tracking (bulk) | `POST /api/v1/admin/zoho/pull-order-tracking` |
| Public track order | `GET /api/v1/orders/track/{orderNumber}` (no auth) |
| User order returns | `GET/POST /api/v1/order-returns` (authenticated customer) |
| Admin order returns | `GET /api/v1/admin/order-returns`; `POST …/approve`, `…/mark-received`, `…/refund` |
| Credit note on refund | `ZohoBooksSyncService.createCreditNoteForReturnedOrder` (invoked from admin **Issue refund**) |

---

## 4. Test scenarios

### 4.1 OAuth and connection

| ID | Scenario | Steps | Expected result | Pass? |
|----|----------|-------|-----------------|-------|
| Z-01 | Server OAuth configured | Open `/admin/zoho` with env vars set | “Connect to Zoho Books” shown; no manual secret form | ☐ |
| Z-02 | OAuth connect | Click Connect → sign in to Zoho → redirect to `/admin/zoho` | Toast “Zoho Books connected”; status connected | ☐ |
| Z-03 | Test connection | Click **Test connection** | Success message; org name from Zoho | ☐ |
| Z-04 | Manual fallback mode | Unset `ZOHO_CLIENT_ID` on server; reload page | Manual credential form; save + test still works | ☐ |
| Z-05 | Token refresh | Run bulk sync twice within 1 hour | Second run does not fail with 401 (token cache) | ☐ |
| Z-06 | OAuth access denied | Revoke app / wrong secret | Clear error in UI; logs mention access denied | ☐ |

**Evidence:** Screenshot of connected status; API response from `/test`.

---

### 4.2 Push products (Admin → Zoho)

| ID | Scenario | Steps | Expected result | Pass? |
|----|----------|-------|-----------------|-------|
| Z-10 | Push single product | Admin → product → sync to Zoho (if exposed) or bulk | `zoho_item_id` set; `zoho_sync_status` = SYNCED | ☐ |
| Z-11 | Push all active | **Sync products to Zoho** | Summary: success count; failures list SKU/category issues | ☐ |
| Z-12 | Missing SKU | Product without SKU in bulk sync | Failure row: SKU required | ☐ |
| Z-13 | Missing category | Product without category | Failure row: category required | ☐ |
| Z-14 | Push with image | Product with `images_json` public URL; image sync on | Image visible on item in Zoho Books | ☐ |
| Z-15 | Inventory item type | `ZOHO_INVENTORY_ACCOUNT_ID` set + stock sync on | Item type inventory in Zoho; stock/location when configured | ☐ |
| Z-16 | No inventory account | Account ID empty + stock sync on | Item still creates as goods; notice/warning in summary (no invalid inventory payload) | ☐ |

**Evidence:** Zoho item screen; Admin product `zoho_item_id`; sync summary toast.

---

### 4.3 Pull products (Zoho → Admin)

| ID | Scenario | Steps | Expected result | Pass? |
|----|----------|-------|-----------------|-------|
| Z-20 | Pull new item | Create item in Zoho with SKU, rate, stock, image | **Pull products from Zoho** → new Admin product; image in list | ☐ |
| Z-21 | Pull update by SKU | Change Zoho rate/stock; pull again | Admin price/stock updated | ☐ |
| Z-22 | Category from custom field | Zoho custom field label/api contains “category” | Admin category assigned or auto-created (if enabled) | ☐ |
| Z-23 | Category from description | Description line `Category: Dresses` | Category matched | ☐ |
| Z-24 | Sizes / colors | Custom fields or `Sizes:` / `Colors:` lines | `sizes_json` / `colors_json` populated | ☐ |
| Z-25 | Image via document API | Item image only in Zoho (no public URL) | Image imported to R2; `images_json` has CDN URL | ☐ |
| Z-26 | Image failure notice | R2 misconfigured or no Zoho image | Pull succeeds; notice: image not imported | ☐ |
| Z-27 | Auto-create category off | Unmatched category; flag false | Product saved; notice about category mismatch | ☐ |

**Evidence:** Admin product row with thumbnail; DB/API `images_json`, `sizes_json`, `colors_json`.

---

### 4.4 Inventory pull

| ID | Scenario | Steps | Expected result | Pass? |
|----|----------|-------|-----------------|-------|
| Z-30 | Pull stock | Change `stock_on_hand` in Zoho; **Pull inventory** | Admin `stock` matches Zoho | ☐ |
| Z-31 | Unlinked product | Admin product without `zoho_item_id` | Skipped or failed with clear message | ☐ |
| Z-32 | Inventory disabled in Zoho | Item without inventory tracking | Failure explains enable tracking in Zoho | ☐ |

---

### 4.5 Orders (checkout → Zoho)

| ID | Scenario | Steps | Expected result | Pass? |
|----|----------|-------|-----------------|-------|
| Z-40 | Auto sync on payment | Complete checkout + Razorpay success | Order `zoho_sync_status` SYNCED; SO + invoice IDs set | ☐ |
| Z-41 | Retry failed orders | **Sync orders to Zoho** after fixing config | Pending paid orders get SO/invoice | ☐ |
| Z-42 | Sync by order number | Enter `MNM-…` in single-order field | Same as Z-40 for that order | ☐ |
| Z-43 | GST / India fields | Order to Indian address | SO/invoice created without 400 GST errors | ☐ |
| Z-44 | Line items | Multi-item order | All lines on Zoho SO with correct qty/rate | ☐ |

**Evidence:** Zoho sales order + invoice; Admin order detail Zoho IDs.

---

### 4.6 Order tracking

| ID | Scenario | Steps | Expected result | Pass? |
|----|----------|-------|-----------------|-------|
| Z-50 | Pull tracking bulk | Add tracking in Zoho SO; **Pull order tracking** | `tracking_number`, carrier, shipment date on order | ☐ |
| Z-51 | Public track page | `/track` + order number | Timeline shows status (no PII leak) | ☐ |
| Z-52 | Delivered status | Zoho status closed/delivered | Store order may move to FULFILLED | ☐ |

---

### 4.7 Credit notes (returns → Zoho)

| ID | Scenario | Steps | Expected result | Evidence |
|----|----------|-------|-----------------|----------|
| Z-55 | Credit note on refund | Paid order synced to Zoho (invoice id set); complete return → admin **Issue refund** | `zoho_credit_note_id` on return; credit note in Zoho Books | Admin return row; Zoho credit notes list |
| Z-56 | Credit note amount | Refund on multi-line order | Credit note lines match order qty/rate | Zoho credit note detail |
| Z-57 | Credit note failure | Order without `zoho_invoice_id` or Zoho API error | Razorpay refund still succeeds; admin note mentions Zoho failure; return status REFUNDED | Admin notes + payment REFUNDED |
| Z-58 | Credit note in Zoho UI | After Z-55 | Credit note linked to customer/invoice | Zoho Books UI screenshot |

---

### 4.8 Schedulers and resilience

| ID | Scenario | Steps | Expected result | Pass? |
|----|----------|-------|-----------------|-------|
| Z-60 | Order retry job | Wait for scheduled retry (or trigger via logs) | Failed paid orders retried | ☐ |
| Z-61 | Tracking job | Scheduled tracking pull | Open orders updated | ☐ |
| Z-62 | OAuth abort mid-bulk | Invalid token mid product sync | Stops with error; does not corrupt DB | ☐ |
| Z-63 | Deleted Zoho item | Delete item in Zoho; sync store product | Relink or recreate per SKU logic | ☐ |

---

### 4.9 Security and roles

| ID | Scenario | Steps | Expected result | Pass? |
|----|----------|-------|-----------------|-------|
| Z-70 | Non-admin blocked | Customer JWT on `/api/v1/admin/zoho/*` | 403 Forbidden | ☐ |
| Z-71 | Secrets not in API | `GET` zoho settings | Client secret / refresh token not returned to browser | ☐ |

---

## 5. Regression checklist (release gate)

Before production deploy, all **P0** scenarios must pass on UAT:

- [ ] Z-02, Z-03 (connected)  
- [ ] Z-11 or Z-10 (product push)  
- [ ] Z-20, Z-25 (pull + image)  
- [ ] Z-30 (inventory)  
- [ ] Z-40, Z-42 (order sync)  
- [ ] Z-50, Z-51 (tracking)  
- [ ] Z-55 (credit note on refund)  

---

## 6. Known limitations (document for stakeholders)

1. **Category:** Zoho Books v3 has no item-category API; category is stored in item **description** (`Category: …`) on push and parsed on pull.  
2. **Full Zoho JSON** is not stored in Admin; only mapped product fields.  
3. **Images on pull** require R2/S3 + `ZOHO_SYNC_PRODUCT_IMAGES=true`; Zoho-hosted URLs are not used directly in the storefront.  
4. **Bulk sync** may return **notices** (warnings) alongside successes — review sync summary, not only pass/fail counts.  
5. **Order sync** requires paid orders and valid customer/address/GST data for India.  
6. **Credit notes** are created only when a Zoho invoice id exists; refund can succeed without Zoho.  

---

## 7. Defect log template

| Date | Tester | Scenario ID | Environment | Steps | Actual | Severity | Jira/GitHub |
|------|--------|-------------|-------------|-------|--------|----------|-------------|
| | | | | | | | |

---

## 8. Sign-off

| Role | Name | Date | Result |
|------|------|------|--------|
| QA | | | Pass / Fail with exceptions |
| Dev lead | | | |
| Product / Ops | | | |

**Exceptions (if any):**

---

## 9. References

- In-repo setup: `PROD_SETUP.md` § Zoho Books OAuth  
- Admin UI: `src/routes/admin.zoho.tsx`  
- Backend: `ZohoBooksSyncService`, `ZohoItemImageSync`, `ZohoSyncResource`, `OrderReturnService`  
- Unit tests: `ZohoBooksSyncServiceTest`, `ZohoItemImageSyncTest`, `ZohoSyncResourceTest`, `OrderReturnServiceTest`
