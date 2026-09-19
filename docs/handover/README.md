# Mini's n More — production handover

These notes describe the **live production** site at [https://minisnmore.com](https://minisnmore.com) as of 19 September 2026.

Repo on the VPS: `/var/www/uat1/mini_ms_test`  
Git remote: `https://github.com/developersqobox-org/mini_and_more.git` (`prod` branch)

| Document | What it covers |
| --- | --- |
| [Admin training and documents](./admin-training-and-documents.md) | Admin screens, product required fields (`*`), CSV import, related docs |
| [Architecture diagram](./architecture-diagram.md) | Services, ports, request flow |
| [Figma documents](./figma-documents.md) | Design source mapping (no Figma file is in the repo) |
| [Deployment guide](./deployment-guide.md) | How this host is built, restarted, and cut over |
| [Management guide](./management-guide.md) | Day-to-day operations, PM2, Nginx, who owns what |
| [Environment variables and runbook](./environment-variables-and-runbook.md) | `/etc/mini-and-more/prod.env`, restart order, health checks |
| [Zoho webhooks and details](./zoho-webhooks-and-details.md) | URLs, secrets, payloads, Nginx routes |
| [Server logs and database](./server-logs-and-database.md) | Where logs live, which Postgres DBs matter, what to check |
| [Post-deployment support](./post-deployment-support.md) | Support window, severity, how to escalate, what “done” looks like |

Deeper technical docs remain under [`docs/`](../).

Do **not** commit secrets. Copy values from `/etc/mini-and-more/prod.env` on the server (mode `600`).
