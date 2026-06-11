# Testing Coverage

## Frontend

The frontend coverage gate focuses on the highest-risk custom integration surface:

- `src/lib/api.ts`
- `src/lib/coupons.functions.ts`
- `src/lib/orders.functions.ts`
- `src/hooks/useAuth.tsx`
- `src/store/shop.tsx`
- `src/routes/login.tsx`
- `src/routes/admin.login.tsx`

The current frontend gate is calibrated to keep quality checks strict but stable
for CI:

- statements: 85%
- lines: 90%
- functions: 95%
- branches: 70%

Branch and some statement paths are lower because auth/session fallbacks, gateway
error branches, and browser-only redirects are intentionally defensive and hard
to deterministically execute in unit tests. Generated TanStack route trees, seed
data, assets, and low-value UI wrapper primitives are excluded from the enforced
frontend gate.

Run:

```bash
npm run test:coverage
```

## Backend

The backend already includes a generated JHipster test suite and JaCoCo reports.
Coverage gates should focus on custom business/security code and exclude generated
metamodels, DTO scaffolding, and repetitive generated CRUD classes. Full repository
reports are still useful for finding gaps, but the enforceable gate should avoid
rewarding brittle tests over generated code.

Run:

```bash
cd backend
./mvnw test
./mvnw verify
```
