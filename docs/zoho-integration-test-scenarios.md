# Zoho Books Integration — Test Scenario Report

> **Deprecated:** Zoho Books integration was replaced by **Ledgerly**. Use [ledgerly-integration-test-scenarios.md](./ledgerly-integration-test-scenarios.md) for current QA scenarios (L-01…L-61). The Z-01…Z-71 scenarios below are retained for historical reference only.

---

**Product:** Mini's n More storefront (Admin + Spring Boot + Zoho Books v3)  
**Audience:** QA, engineering team lead, release sign-off  
**Last updated:** 2026-06-02  
**Environment under test:** UAT (`https://minisnmore.com`) or local (`http://localhost:5173` + API **gateway** `:8080`)

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

*(Remaining Z-01…Z-71 scenario tables unchanged — see git history or Ledgerly doc for current testing.)*

**Current integration:** [ledgerly-integration-test-scenarios.md](./ledgerly-integration-test-scenarios.md)
