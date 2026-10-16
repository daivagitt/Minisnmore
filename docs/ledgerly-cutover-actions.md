# Ledgerly Cutover — Action Items for Mini's n More

**Date:** 2026-06-30  
**Environment:** UAT (`minisnmore.com`, isolated microservices :9180–:9184)  
**Status:** Ledgerly-side platform fixes are **deployed**. Items below require Mini's n More team action.

---

## 🔴 Blocking — Do These First

### 1. Rotate the Ledgerly API token (SECURITY)

A live `lt_live_…` token was shared in chat during setup and is now **compromised**.

**Steps:**
1. Log into Ledgerly → **Settings → API & Webhooks**
2. Delete the current token and generate a new one
3. Copy the new `lt_live_…` value

### 2. Set the new token + org ID in UAT environment

After rotating the token, fetch the `organizationId` from Ledgerly:

```bash
curl -H "Authorization: Bearer <new-lt_live-token>" \
  https://ledgerly.yourdomain.com/v1/platform/integration/status
```

Response will include `"organizationId": "..."`.

Then update UAT environment:

```bash
sudo nano /etc/mini-and-more/prod.env
```

Set:
```
LEDGERLY_API_TOKEN=<new lt_live_… token>
LEDGERLY_ORG_ID=<organizationId from status endpoint>
```

Also set in Admin UI → Integrations → Ledgerly settings panel.

### 3. Reload catalog with the updated env

```bash
set -a && . /etc/mini-and-more/prod.env && set +a
pm2 restart mini-prod-ms-catalog-core --update-env
```

> **Note:** `pm2 restart` alone does **not** reload env variables. You must source the file first and pass `--update-env`.

### 4. Register the webhook in Ledgerly

In Ledgerly → **Settings → API & Webhooks**, register:

- **URL:** `https://minisnmore.com/internal/v1/ledgerly/webhooks`
- **Secret:** value of `LEDGERLY_WEBHOOK_SECRET` from `/etc/mini-and-more/prod.env`

---

## 🟡 Build Fixes (Required for Clean Rebuilds)

### 5. Fix Maven build — JaCoCo + Modernizer

The backend (catalog/commerce) build currently requires skip flags:

```bash
./mvnw -Pprod -DskipTests -Djacoco.skip=true -Dmodernizer.skip=true package
```

This is **not** acceptable for Jenkins/production pipelines. Fix required:

- **JaCoCo:** Add coverage for new `LedgerlySyncService`, `LedgerlyWebhookService`, etc. until thresholds are met, or adjust thresholds in `pom.xml`.
- **Modernizer:** Run `./mvnw modernizer:modernizer` to list violations. Common fixes:
  - Replace `new ArrayList<>()` with `List.of()` or `List.copyOf()` where immutability applies
  - Replace deprecated Guava/Apache Commons APIs with Java 11+ equivalents
  - Check `LedgerlySyncService` specifically (mentioned in the violation report)

### 6. Fix gateway source build (compile from scratch)

The gateway module previously failed to compile from source due to missing `User`, `UserRepository`, and `UserService` classes. **These have been restored to source control in this PR.**

Verify the build now compiles:

```bash
cd services/gateway
./mvnw -DskipTests compile
```

Also restored: `sonar-project.properties` (was missing, blocked the Maven properties plugin).

### 7. Fix store `SecurityConfigurationChainTest`

`SecurityConfiguration.java` was previously missing from source. **It has been restored in this PR.**

Verify tests pass:

```bash
cd services/store
./mvnw test -Djacoco.skip=true
```

---

## 🟢 Follow-up / Decommission

### 8. Stop the legacy Zoho integration service

`mini-prod-ms-integration` (:9183) is still running.

```bash
pm2 stop mini-prod-ms-integration
pm2 save
```

Remove `APPLICATION_ZOHO_INTEGRATION_BASE_URL` from prod.env after confirming catalog no longer calls it.

> Safe to do after Ledgerly smoke tests L-01 to L-61 pass.

### 9. Handle the legacy monolith on :8081

`mini-uat` on port 8081 fails with:
```
ERROR: column c1_0.zoho_category_id does not exist
```

The DB was migrated (zoho_* → ledgerly_*) but the monolith JAR is old.

Options:
- **Stop it** if nothing routes to :8081 (nginx routes /api/ to gateway :9180)
- **Or** redeploy with a current catalog JAR

Check nginx config:
```bash
grep -n ':8081' /etc/nginx/sites-enabled/mini-frontend-uat
```

If nothing proxies to :8081, stop it:
```bash
pm2 stop mini-uat
```

### 10. Document PM2 env reload in deploy runbooks

Every time you add or change env vars in `/etc/mini-and-more/prod.env`, you must:

```bash
set -a && . /etc/mini-and-more/prod.env && set +a
pm2 restart <service> --update-env
```

Update `deploy/prod-microservices-isolated-pm2.sh` or add a restart helper script to bake this in.

---

## ✅ Run Smoke Tests

After completing items 1–4 above, re-run the integration test suite:

```bash
# From docs/ledgerly-integration-test-scenarios.md
L-02  Product pull from Ledgerly
L-10  Category sync
L-12  Order push to Ledgerly
L-13  Webhook received on order update
L-40  Admin UI shows connected: true
L-50  Inventory levels updated from Ledgerly
L-55  Webhook retry on transient failure
```

> Current status: `serverConfigured: true, connected: false`  
> After token rotation + reload, `connected` should become `true`.

---

## 📋 Reference

| Item | Location |
|------|----------|
| UAT secrets | `/etc/mini-and-more/prod.env` |
| Gateway UAT routes | `services/gateway/src/main/resources/config/application-uat-isolated.yml` |
| Ledgerly properties binding | `backend/src/main/java/com/miniandmore/app/config/ApplicationProperties.java` |
| Nginx UAT config | `/etc/nginx/sites-enabled/mini-frontend-uat` |
| Test scenarios | `docs/ledgerly-integration-test-scenarios.md` |
| Migration script | `deploy/scripts/prod-migrate-ledgerly-liquibase.sh` |

### Current UAT service health (last verified 2026-06-30)

| Service | Port | Health |
|---------|------|--------|
| Gateway | 9180 | UP |
| Catalog core | 9184 | UP |
| Commerce | 9182 | UP |
| Store | 9181 | UP |
| Integration (legacy Zoho) | 9183 | Still running — decommission pending |

---

## 🔒 Security Notes

- Rotate the compromised `lt_live_…` token **immediately** (see item 1)
- `LEDGERLY_WEBHOOK_SECRET` must match exactly between `uat.env` and Ledgerly webhook config
- Do **not** commit `/etc/mini-and-more/prod.env` or any token values to git
