# Java services

- `gateway/`: authentication, security, and API routing.
- `store/`: store API and core catalog proxy.
- `commerce/`: commerce API and order/return proxies.

Each service is an independent Maven project with its own wrapper, `pom.xml`, Java packages, resources, configuration, and tests. Run `./mvnw test` or `./mvnw package` from the service directory.

Core business logic and integrations live in [`backend/`](../backend/). Shared local infrastructure lives in [`docker/`](../docker/), and repository-level launch and deployment scripts live in [`scripts/`](../scripts/) and [`deploy/`](../deploy/).
