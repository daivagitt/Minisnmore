# Mini's n More — requested technical handover pack

This index contains only the documents requested from handover items **2, 3, 8, and 9**. It excludes admin training, Figma, account details, Git merge/access, ownership transfer, external-library inventory, and post-deployment hypercare.

The content describes the production layout represented by this repository and was reviewed against the code and deployment configuration on **14 October 2026**. Commands that inspect the live server must still be run by an authorised operator before sign-off.

Shareable combined pack: [DOCX](./MINISNMore_Technical_Handover_Pack.docx) · [PDF](./MINISNMore_Technical_Handover_Pack.pdf)

Standalone database schema: [DOCX](./MINISNMore_Database_Schema.docx) · [PDF](./MINISNMore_Database_Schema.pdf)

Repo on the VPS: `/var/www/uat1/mini_ms_test`  
Git remote: `https://github.com/developersqobox-org/mini_and_more.git` (`prod` branch)

| Document | What it covers |
| --- | --- |
| [SSL certificate creation and usage](./ssl-certificate-creation-and-usage.md) | Let’s Encrypt issue, Nginx use, renewal, verification, and incident recovery |
| [Architecture diagram](./architecture-diagram.md) | Services, ports, request flow |
| [Deployment guide](./deployment-guide.md) | How this host is built, restarted, and cut over |
| [Management guide](./management-guide.md) | Day-to-day operations, PM2, Nginx, backups, and change windows |
| [Environment variables and runbook](./environment-variables-and-runbook.md) | `/etc/mini-and-more/prod.env`, restart order, health checks |
| [Zoho, XpressBees, and Mini’s n More admin integrations](./zoho-webhooks-and-details.md) | Callback URLs, secrets, payload rules, admin operations, verification, and audit tables |
| [Server logs and database](./server-logs-and-database.md) | Where logs live, which Postgres DBs matter, what to check |
| [Database schema](./database-schema.md) | Current ER diagrams, table catalogue, keys, relationships, enums, and verification SQL |

Deeper technical docs remain under [`docs/`](../).

Do **not** commit secrets. Copy values from `/etc/mini-and-more/prod.env` on the server (mode `600`).
