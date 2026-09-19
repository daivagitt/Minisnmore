# Management guide

How to operate Mini's n More production without changing code.

## Owners (fill names on handover)

| Area | Primary | Backup |
| --- | --- | --- |
| VPS / Nginx / PM2 / Postgres | | |
| GitHub `developersqobox-org/mini_and_more` | | |
| Cloudflare (DNS + R2) | | |
| Zoho Books org + webhooks | | |
| Razorpay live keys | | |
| Google / Apple / Facebook OAuth apps | | |
| Gmail / Zoho SMTP | | |
| Domain `minisnmore.com` registrar | | |
| Let’s Encrypt renewal | | |

Transfer access as part of go-live: GitHub org, Cloudflare account, Zoho org, Razorpay dashboard, OAuth consoles, DNS, server SSH keys. Do not share the contents of `/etc/mini-and-more/prod.env` over chat; copy on the server or via a password manager.

## Daily checks

```bash
pm2 list
curl -fsS -o /dev/null -w '%{http_code}\n' https://minisnmore.com/
curl -fsS -o /dev/null -w '%{http_code}\n' https://minisnmore.com/api/v1/categories
df -h /
free -h
```

Expect `200` on home and categories. Shop may `307` then `200` on `/shop?sort=new`. Unauthenticated `/api/v1/order-returns` should be `401`, not `502`/`503`.

## PM2

```bash
pm2 list
pm2 logs mini-prod-ms-gateway --lines 80
pm2 logs mini-prod-ms-catalog-core --lines 80
pm2 restart mini-prod-ms-store --update-env   # only after sourcing prod.env
```

A plain `pm2 restart` **does not** pick up new env file values. Use `deploy/scripts/prod-reload-env-and-restart.sh`.

Do not `pm2 delete` the frontend during business hours unless you immediately start `mini-prod-frontend` again on `:3000`.

## Nginx / TLS

```bash
nginx -t
systemctl status nginx
certbot certificates
```

Site file: `/etc/nginx/sites-available/mini-prod.conf`. It includes the isolated locations conf from the repo. After editing the snippet:

```bash
nginx -t && systemctl reload nginx
```

## Admin operations

- Catalog edits: [https://minisnmore.com/admin/products](https://minisnmore.com/admin/products)
- Orders / returns: `/admin/orders`, `/admin/returns`
- Zoho: `/admin/zoho` — long timeouts; wait for the UI result

Storewide discount lives on **Discount**. Changing it recalculates selling price from MRP on simple products.

## Payments and mail

- Razorpay: live key id is in `prod.env` (`RAZORPAY_KEY_ID`). Demo flags `RAZORPAY_PAYMENT_DEMO` / `RAZORPAY_REFUND_DEMO` must stay **false** in production.
- Mail: `SPRING_MAIL_PROVIDER` is `gmail` or `zoho`. From address must match the authenticated mailbox.

## What this VPS also hosts

`ledgerly-*` Postgres databases and a stopped `ledgerly-frontend` PM2 app belong to a **different** product. Do not drop those databases or reuse their users for Mini’s n More.

## Backup expectations

| What | Where | Operator action |
| --- | --- | --- |
| App code | GitHub `prod` branch | Push before every server-only hotfix |
| Env / secrets | `/etc/mini-and-more/prod.env` | Off-server copy in a vault |
| Postgres `miniandmore` | local 5432 | Schedule `pg_dump` (see [server-logs-and-database.md](./server-logs-and-database.md)) |
| Images | R2 bucket `miniandmore` prefix `prod` | Cloudflare versioning / second bucket |
| Nginx | `/etc/nginx` + repo `deploy/nginx` | Keep repo copy in sync |

## Change window

Prefer catalog/gateway bounces off peak. Each JVM can take 30s–several minutes. Nginx keeps serving the old frontend until `mini-prod-frontend` is restarted.
