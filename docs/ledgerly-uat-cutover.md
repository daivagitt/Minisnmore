# Ledgerly UAT cutover — issues encountered

**Date:** 2026-06-30  
**Environment:** UAT (`uat.minisnmore.com`, isolated microservices `:9180`–`:9184`)

Full shared runbook (includes Ledgerly platform fixes): see also `spec-driven-harmony/docs/ledgerly-uat-cutover.md` in the Ledgerly repo.

## What’s done

- Manual DB migration (`ledgerly_*` columns/tables) on UAT catalog DB
- Catalog `application.ledgerly.*` property binding + scheduler defaults
- Store `CatalogCoreClient` Ledgerly admin proxy fix
- Store `SecurityConfiguration` restored — public `/api/v1/categories` and `/products` return **200**
- Ledgerly platform deployed — opaque `lt_live_…` tokens work (no JWT decode error)

## What you must do next (blocking)

1. **Rotate** developer token in [Ledgerly → Settings → API & Webhooks](https://lender.qualityoutsidethebox.org/app/settings/integrations) (old token was exposed in chat).
2. Update `/etc/mini-and-more/uat.env` and **Admin → Integrations → Ledgerly**:

   ```bash
   LEDGERLY_API_BASE_URL=https://lender.qualityoutsidethebox.org/api
   LEDGERLY_API_TOKEN=<new lt_live_…>
   LEDGERLY_ORG_ID=<from status response — see below>
   ```

3. Get `organizationId`:

   ```bash
   curl -sS -H "Authorization: Bearer $LEDGERLY_API_TOKEN" \
     "$LEDGERLY_API_BASE_URL/v1/platform/integration/status"
   ```

4. Reload catalog env:

   ```bash
   set -a && . /etc/mini-and-more/uat.env && set +a
   pm2 restart mini-uat-ms-catalog-core --update-env
   ```

5. In **Ledgerly** Settings → API & Webhooks, add webhook:
   - URL: `https://uat.minisnmore.com/internal/v1/ledgerly/webhooks`
   - Secret: same as `LEDGERLY_WEBHOOK_SECRET` in `uat.env`

6. Run smoke tests **L-02, L-10, L-12** then full **L-01–L-61** in [ledgerly-integration-test-scenarios.md](./ledgerly-integration-test-scenarios.md).

## Open follow-ups (non-blocking)

| # | Item |
|---|------|
| 15 | Stop `mini-uat-ms-integration` (legacy Zoho) after smoke tests pass |
| 11 | Stop or upgrade legacy monolith on `:8081` (Zoho column mismatch) |
| 1 | Prod cutover: fixed Liquibase or reviewed SQL (don’t re-run rename on UAT) |
| 2–3 | Clean Maven/CI (modernizer, gateway compile from source) |

## R2 / images

- Store `S3_*` → product images in Admin (Mini's n More R2).
- With `LEDGERLY_SYNC_PRODUCT_IMAGES=true`, pull/webhook downloads Ledgerly `imageUrl` and imports to R2 (same pattern as legacy `ZOHO_SYNC_PRODUCT_IMAGES`).
- Push to Ledgerly sends the first store `images_json` URL as `imageUrl` (no binary upload to Ledgerly).
- Ledgerly does **not** need store R2 credentials; R2 is used only on the store side when importing pulled images.

## Product field sync (parity)

| Direction | Fields |
|-----------|--------|
| Push (store → Ledgerly) | sku, name, price/rate, stock, cost (from compare-at), unit (`ea`), taxPct (18 default), encoded description, imageUrl |
| Pull (Ledgerly → store) | price, stock, cost, unit, hsn, taxPct, description (decoded), imageUrl → R2 when enabled |
| Webhook `item.updated` | Same as pull row |

Structured `description` lines carry category, fabric, sizes, colors, age, story, badge, compare-at. Slug and merchandising flags remain store-only.

## Security

- Never commit `uat.env` or tokens to git.
- Rotate any token that appeared in chat or logs.
