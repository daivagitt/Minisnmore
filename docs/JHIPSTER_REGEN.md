# JHipster regeneration (catalog microservice)

Running `jhipster` or `jhipster jdl` in `backend/` can recreate **monolith-style** artifacts.

**Always run after regen:**

```bash
bash scripts/post-jhipster-cleanup.sh
cd backend && ./mvnw compile -DskipTests
```

After any regen:

1. **Delete** `src/main/java/com/minisandmore/` and `src/test/java/com/minisandmore/` if they reappear.
2. **Delete** JHipster `/api/{entity}` controllers under `com.miniandmore.app.web.rest/` (keep only `v1/` and `errors/`).
3. **Do not** re-enable `FeignConfiguration` — catalog uses JWT `userLogin`, not Feign to gateway.
4. Keep `.yo-rc.json`: `"feignClient": false`, `"skipUserManagement": true`.
5. Re-run `./mvnw compile` and `docs/MODULES_TESTING.md` smoke checks.

Prefer editing `miniandmore-store.jdl` then `jhipster jdl miniandmore-store.jdl --force` instead of full regen.
