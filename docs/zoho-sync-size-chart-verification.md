# Product Zoho sync/pull and independent local size charts

Updated 2026-09-08. The confirmed audit gaps have been implemented, with additional fixes from a second review. This is not an assertion that every possible distributed-system edge case has been proven.

Size charts are local product data. Sending or pulling charts through Zoho is **not a requirement** and no chart transport was added. This corrects the scope mistake in the initial audit.

## Implemented fixes

| Area | Behaviour now |
| --- | --- |
| Malformed product/inventory lists | Missing/null/wrong-type lists, invalid rows, error envelopes, missing/invalid pagination, wrong page numbers, and empty pages claiming more results fail safely. Failed product pulls do not perform stale cleanup or advance the cursor. |
| Pagination completion | Product and inventory pulls honour the final-page marker, including non-empty final pages. |
| Pagination cycles | All previously seen page identities are tracked, independent of row ordering and timestamps. Non-adjacent cycles stop both product and inventory pulls. |
| SKU lookup before push | Lookup includes inactive items and requires a complete result. It selects an exact case-insensitive match, rejects ambiguity/mismatches/missing IDs, and refuses incomplete lookup results instead of creating or overwriting an uncertain item. |
| Item response validation | Item create/update/detail calls reject HTTP-200 business-error envelopes, missing item objects/IDs, and conflicting returned identities. Both string and integer IDs are accepted. |
| Field discovery | A failed discovery is propagated rather than cached as a successful empty mapping. Successful definitions are scoped by organization/data center and expire after five minutes. |
| Image import failures | Storage/download failures are surfaced, catalog fields can still be saved, product status becomes FAILED, and the pull cursor remains unchanged. Existing galleries are not replaced before downloads/storage succeed. |
| Image retry selection | Persisted image failures are retried even when their remote timestamp predates the saved cursor (manual/full-pull recovery). Successful sibling variants do not hide an earlier parent image failure. |
| Transaction entry points | Default, async, and inventory pull entry points no longer reintroduce the class-level transaction around a paginated pull. Unit checks verify annotations, not live database transaction isolation. |
| Local chart validation | Frontend parsing, Excel import, save serialization, and backend product writes reject negative/non-finite measurements, boolean/object/array coercion, and duplicate size bands/measurement rows. Zero remains allowed for garment construction measurements; blanks/N/A are not zero. |
| Local chart labels and units | Both sides of size filtering use the same month/dash/common-size normalization. Explicit centimetre/inch values are accepted; the fit parser converts inches to centimetres. |
| Local chart display | The compact guide remains. An expandable table exposes every saved section and measurement in the product size guide and admin preview. |
| Chart ownership | Regression tests verify that simple and variant pulls preserve their existing local chart JSON. The mirrored `lib/` and `src/lib/` chart implementations remain aligned. |
| Variant size/color conflicts | Pulling a different SKU/item ID into an occupied size/color combination now reports a conflict before changing the matrix. This covers appends, existing-variant size/color changes, inactive rows, and item-ID/SKU matches pointing to different local rows. Combination matching is case-insensitive with surrounding whitespace trimmed. Existing duplicate records are not automatically merged or deleted. |

The API response checks follow the published [Zoho Books pagination contract](https://www.zoho.com/books/api/v3/pagination/) and [item response examples](https://www.zoho.com/books/api/v3/items/). Organization-specific live behaviour still needs UAT verification.

## Regression verification

- Full frontend suite: **588 tests across 73 files passed**.
- Selected backend suite: **433 tests across 22 suites passed**, with zero failures, errors, or skips, including the duplicate size/color regression.
- `git diff --check`: passed.
- Whole-project `npx tsc --noEmit`: not clean; existing errors include routing/search props, admin field props, auth test mocks, and product/color types. An in-memory check against HEAD versions of the modified files reproduced 111 baseline diagnostics, including the existing product-page state-setter error. No remaining errors were reported for the chart parser, Excel code, or new full-chart component/test. Unrelated errors were not changed.

Backend selection:

```sh
cd backend
./mvnw -o -Dtest='Zoho*Test,ProductVariantSupportTest,ProductDescriptionCodecTest,ProductServiceTest,ProductSizeChartValidationTest' test -q
```

Frontend:

```sh
npm test
```

Mockito requires JVM attachment, so the sandbox-blocked backend run was repeated with approval outside the sandbox. Tests use local HTTP mocks/repository doubles. Deployment is recorded below; no manual live Zoho sync/pull, database migrations, or credential changes were performed.

## UAT deployment — 2026-09-08

- Deployed the verified backend and frontend, including the variant conflict guard, to this workspace's UAT services only. Production was not restarted or modified.
- Release marker: `https://uat.minisnmore.com/RELEASE.txt`, first line `variant-conflict-20260908`.
- Restarted `mini-uat-ms-catalog-core` (:9184) and `mini-uat-frontend` (:3000), retaining their existing PM2 configuration/environment.
- Catalog readiness and liveness reported `UP`; the public catalog API and storefront returned HTTP 200 after restart.
- Backend SHA-256: `e6676e2a855f6a4d916dee94b72dca429e3bb1adeffac000b4d2a4f772e8daef`.
- Rollback artifacts: `deploy/backups/variant-conflict.0LWGUc/miniandmore-0.0.1-SNAPSHOT.jar` and `deploy/backups/variant-conflict.0LWGUc/dist-previous/` (also a separate `dist/` copy).
- Restore those artifacts to the existing backend JAR and `dist/` paths, then restart the same two UAT PM2 services if rollback is required. Old frontend assets were also retained in the new release for already-open browser sessions.
- Existing duplicated variants were left untouched. The new guard prevents further conflicting pulls; resolving already-created duplicates requires an explicit mapping/data-repair decision.

## Second review: what still needs controlled integration verification

1. **Concurrent writers and multi-instance operation:** simultaneous admin push/pull, scheduled pulls, webhook updates, and an organization switch during an operation. The changes do not introduce a distributed lock or organization-scoped product-link migration.
2. **A changing remote catalog between pages:** validated pagination is not a snapshot guarantee. Remote insertions/deletions/reordering during a full pull can still affect which rows are seen; destructive stale reconciliation needs a controlled moving-catalog scenario before a blanket sign-off.
3. **Real database failure boundaries:** commit failures, deadlocks, optimistic/pessimistic concurrency, process termination after an external write but before local persistence, and recovery across service restarts.
4. **Live organization capabilities:** custom-field permissions, field-definition changes, image/document endpoints, revoked OAuth permissions, rate limits, and large-catalog timings. Cache scope and expiry are unit-tested; this does not establish safe automatic switching of an already-linked catalog to another organization.
5. **Retry operations:** permanently invalid images or unavailable required metadata now remain visible failures and can hold the cursor back. They need repair or an explicit operational decision, not silent success. No independent durable image job queue was added.

These are remaining verification/design boundaries, not claims that all have been reproduced as defects in this workspace. The next safe step is a controlled UAT matrix using test products and deliberate failure injection, with explicit approval for any live Zoho mutations.

Existing unrelated worktree edits and documents were preserved.
