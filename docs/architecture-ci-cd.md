# CI/CD and reports pipeline

How code moves from this repository to **https://minisnmore.com** and the branded **Reports** dashboard (`/reports/`).

## Release and deploy (production)

```mermaid
flowchart TB
  subgraph repo["Repository"]
    SRC["frontend/ · backend/ · services/{gateway,store,commerce}"]
    DEPLOY["deploy/scripts/*"]
  end

  subgraph build["Build on host or CI"]
    MVN["./mvnw -Pprod package per Java module"]
    VITE["pnpm/vite build → frontend/dist"]
    LIQ["run-catalog-liquibase.sh when schema changes"]
  end

  subgraph runtime["Production PM2 — 127.0.0.1 only"]
    FE["mini-prod-frontend :3000"]
    GW["mini-prod-ms-gateway :9180"]
    ST["mini-prod-ms-store :9181"]
    CM["mini-prod-ms-commerce :9182"]
    CC["mini-prod-ms-catalog-core :9184"]
  end

  NGINX["Nginx :443 minisnmore.com"]

  SRC --> MVN
  SRC --> VITE
  MVN --> LIQ
  LIQ --> CC
  MVN --> GW
  MVN --> ST
  MVN --> CM
  MVN --> CC
  VITE --> FE
  DEPLOY --> runtime
  NGINX --> FE
  NGINX --> GW
```

- Isolated stack: Consul **off**, gateway profile `prod,uat-isolated`, fixed HTTP routes to `9181` / `9182` / `9184` (see [handover/architecture-diagram.md](./handover/architecture-diagram.md)).
- Cutover scripts: `deploy/scripts/prod-microservices-isolated-pm2.sh`, `prod-nginx-cutover-isolated.sh`, `health-check-microservices.sh`.

## Jenkins / QA reports bundle

```mermaid
flowchart LR
  subgraph inputs["Test & doc inputs"]
    VITEST["Vitest unit + coverage"]
    SURE["Maven Surefire + JaCoCo"]
    API["API lifecycle / Newman / k6"]
    E2E["E2E scenario runner"]
    UI["Manual UI evaluator"]
    DOCS["docs/*.md architecture sources"]
  end

  subgraph gen["jenkins/scripts"]
    PKG["package-*-reports.sh"]
    ARCH["generate-architecture-report.py"]
    SITE["generate-reports-site.py"]
  end

  OUT["artifacts/reports-site or mini_reports/current"]
  PUB["https://minisnmore.com/reports/"]

  VITEST --> PKG
  SURE --> PKG
  API --> PKG
  E2E --> PKG
  UI --> PKG
  DOCS --> ARCH
  PKG --> SITE
  ARCH --> SITE
  SITE --> OUT
  OUT --> PUB
```

| Script | Output |
| --- | --- |
| `generate-architecture-report.py` | `./architecture/` — interactive diagrams (Mermaid) from `docs/` |
| `generate-guidelines-report.py` | `./guidelines/` — API / DB / frontend / backend standards |
| `generate-reports-site.py` | `./index.html` — hub linking all sections |
| `publish-ledgerly-reports.sh` | Publishes to `/var/www/uat1/mini_reports/current` |

SonarQube, Swagger, and JavaDoc indices are wired from the same hub; see `jenkins/scripts/generate-reports-site.py`.
