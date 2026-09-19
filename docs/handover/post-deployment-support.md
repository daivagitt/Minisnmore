# Post-deployment support

This is the support model **after** Mini’s n More is live on [https://minisnmore.com](https://minisnmore.com). It is not a new feature list. The goal is: the shop stays up, money and orders stay consistent, and the receiving team can run the site without the original implementers on call 24/7.

Fill in names, hours, and ticket URLs at handover.

## What “support” covers

| Included | Not included (new project / change request) |
| --- | --- |
| Site down, 5xx, TLS expiry | New pages, new payment methods, new courier |
| Login / OAuth / admin cannot save | Redesign, new admin modules |
| Checkout, Razorpay capture/refund mismatch | Price-list overhaul, marketplace feeds |
| Zoho webhook 401/400, tracking not updating | New Zoho org or Books→Inventory migration |
| Catalog not showing after a known deploy | Full catalog rebuild / bulk data cleanup |
| Restart, env typo, Nginx reload, PM2 crash loop | Moving host, Kubernetes, new domain |
| Log/DB diagnosis of a production incident | Training for a second brand |

If work is a **defect in what was already delivered**, it is support. If it is **new behaviour**, it is a change request.

## Support window (agree in writing)

| Item | Default to confirm |
| --- | --- |
| Warranty / hypercare | First **14 calendar days** after go-live: same-day response for Sev-1/2 during business hours |
| Business hours | e.g. Mon–Sat 10:00–19:00 IST |
| After hours | Sev-1 only (site down / payments failing), next-hour acknowledgement if on-call is contracted |
| After hypercare | Ticket-based, next business day unless a retainer exists |
| Channel | Named email / Slack / WhatsApp group — **one** place, not DMs |

## Severity

| Severity | Examples | Target |
| --- | --- | --- |
| **Sev-1** | Homepage or `/api` 502/504; cannot pay; TLS broken; all checkouts fail | Acknowledge 30 min (business) / 1 h (if on-call). Workaround or restore same day. |
| **Sev-2** | Admin cannot save products; Zoho webhooks 401 for all events; images 403 from R2; orders created but email/SMS fail | Acknowledge same business day. Fix or workaround in 1–2 business days. |
| **Sev-3** | One SKU wrong; one order tracking stuck; cosmetic admin `*` missing on a field | Next business day ack. Scheduled fix. |
| **Sev-4** | Docs typo, “how do I…” | Best effort during hypercare. |

A single test order failing is Sev-2 until proven systemic (then Sev-1).

## Who does what

| Role | Does | Does not |
| --- | --- | --- |
| **Implementer (hypercare)** | Diagnose with logs/DB, restart using prod scripts, patch if the defect is in delivered code | Change Zoho org, Cloudflare plan, or Razorpay KYC without owner |
| **Client ops / merchandising** | Admin catalog, banners, coupons, order/return handling in UI | SSH, `prod.env`, Nginx |
| **Client IT owner** | SSH keys, DNS, Cloudflare, Zoho console, Razorpay, GitHub | Guess env values from chat |
| **Host / VPS provider** | Disk, RAM, DDoS, hardware | Application bugs |

## How to raise an incident

Send **one** message with:

1. Time (IST) and URL (exact path).
2. What the customer or admin clicked.
3. Order number / SKU / Zoho invoice id if any.
4. Screenshot or HTTP status.
5. Whether a deploy happened in the last 24 h.

Then the support engineer:

1. `pm2 list` + public curls (see [environment-variables-and-runbook.md](./environment-variables-and-runbook.md)).
2. Nginx access/error for that timestamp.
3. Matching PM2 log (`gateway` vs `catalog-core`).
4. Postgres only if orders/webhooks/catalog rows are in doubt ([server-logs-and-database.md](./server-logs-and-database.md)).
5. Fix with the **smallest** bounce (one PM2 process), not a full isolated recycle, unless several services are dead.

## Restore vs rollback

| Situation | Action |
| --- | --- |
| Process `errored` / OOM | `pm2` restart that name; if loop, check last deploy and disk |
| Bad frontend build | Keep API up; restore previous `dist/` or `git checkout` last known `prod` SHA and `prod-frontend-pm2.sh` |
| Bad catalog JAR | Relink previous `backend/target/*.jar`, `prod-restart-isolated-catalog.sh`. Schema: only roll back DB if that JAR required a Liquibase changeset you applied — restore `pg_dump` to a **copy** first |
| Bad env edit | Restore `prod.env` from vault, `prod-reload-env-and-restart.sh` |
| Nginx mistake | `nginx -t` must pass; if live is broken, restore `mini-prod.conf` from git and `systemctl reload nginx` |

Do not `git reset --hard` on the VPS if there are uncommitted live hotfixes — stash or commit first.

## Exit criteria for hypercare (hand-off complete)

All of the following should be true:

- [ ] Client can log in to `/admin`, create/edit a product (sees `*` required fields), and see it on `/shop`.
- [ ] A real or agreed test checkout completes; Razorpay dashboard shows the payment; `/admin/orders` shows the order.
- [ ] Zoho invoice and shipment webhooks return `200` in Nginx; a shipped package updates tracking on the account order page.
- [ ] Mail (order confirmation) arrives for that order.
- [ ] Client IT has: SSH (or a jump host), GitHub `prod`, Cloudflare, Zoho, Razorpay, `/etc/mini-and-more/prod.env` in a vault.
- [ ] Client has this `docs/handover/` folder and knows not to use UAT script names.
- [ ] `pm2 save` + reboot policy understood (`pm2 startup`).
- [ ] Postgres dump job scheduled or explicitly accepted as a risk.

When those boxes are ticked, ongoing work is **operations + change requests**, not “the site is not finished”.

## Contacts (fill at sign-off)

| Function | Name | Phone / email |
| --- | --- | --- |
| Implementer hypercare | | |
| Client product owner | | |
| Client technical owner | | |
| Hosting | | |
| Zoho admin | | |
| Razorpay admin | | |

Related: [deployment-guide.md](./deployment-guide.md), [management-guide.md](./management-guide.md), [environment-variables-and-runbook.md](./environment-variables-and-runbook.md).
