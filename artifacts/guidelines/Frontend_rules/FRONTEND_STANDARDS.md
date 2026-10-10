# 🌐 FRONTEND_STANDARDS.md

## 1. Purpose

This document defines **mandatory standards** for building web frontends in this project (React / Next.js / TypeScript).

⚠️ Any frontend code that violates this document is considered **non-production-ready**.

All AI agents and developers **must read and follow this file before implementation**.

> For mobile (Flutter), use `flutter_rules/`. For design/interaction rules, use `ui_ux_rules/UI_UX_STANDARDS.md`.

---

## 2. Core Principles (Non-Negotiable)

- **Type safety by default**
- **Component-driven architecture**
- **Predictable, unidirectional data flow**
- **Accessibility is mandatory, not optional**
- **Performance budgets are enforced**
- **No business logic inside UI components**
- **Fail safely, never silently**

---

## 3. Tech Stack Standard

- **Language:** TypeScript (`strict` mode ON)
- **Framework:** React 18+ (Next.js for SSR/SSG apps)
- **Styling:** Tailwind CSS or CSS Modules (pick one per project)
- **Data fetching:** TanStack Query (server state) — never store server data in global UI state
- **Forms:** React Hook Form + schema validation (Zod)

❌ No JavaScript (`.js`/`.jsx`) for app code — TypeScript only
❌ No mixing multiple styling systems in one project
❌ No jQuery / direct DOM manipulation

---

## 4. Project Architecture (MANDATORY)

**Feature-First + Layered**

```
src/
│
├── app/                # routing / pages (Next.js app router)
├── components/         # shared, reusable, presentational UI
│   └── ui/             # design-system primitives (Button, Input)
├── features/
│   ├── auth/
│   │   ├── api/        # data access (queries/mutations)
│   │   ├── components/ # feature-scoped UI
│   │   ├── hooks/      # feature logic
│   │   └── types.ts
│   └── profile/
├── lib/                # framework-agnostic utilities, clients
├── hooks/              # shared hooks
├── stores/             # global UI state (Zustand/Redux)
└── styles/
```

❌ **No dumping everything into `components/`**
❌ **No deep relative imports** (`../../../../`) — use path aliases (`@/features/...`)

---

## 5. Component Standards

- **Functional components only** (no class components)
- One component per file; file name matches component name
- Presentational vs. container separation: UI components stay dumb
- Props **must be typed** with explicit interfaces — no `any`
- Prefer composition over prop-drilling; use context for cross-cutting concerns

```tsx
// ❌ BAD — untyped, logic + fetch inside UI
function User(props: any) {
  const [u, setU] = useState<any>();
  useEffect(() => { fetch('/api/user').then(r => r.json()).then(setU); }, []);
  return <div>{u?.name}</div>;
}

// ✅ GOOD — typed, data via query hook, UI is presentational
interface UserCardProps { user: User }
function UserCard({ user }: UserCardProps) {
  return <div className="card">{user.name}</div>;
}
```

❌ **God components** (> ~200 lines → split)
❌ **Business logic / API calls inside presentational components**

---

## 6. State Management (MANDATORY)

| State type | Where it lives |
|------------|----------------|
| Server data | TanStack Query (cache, refetch, invalidate) |
| Global UI state | Zustand / Redux Toolkit |
| Local UI state | `useState` / `useReducer` |
| URL state | Router query params |

❌ **Never copy server data into global stores**
❌ **No prop-drilling more than 2 levels** — lift to context/store

---

## 7. Data Fetching & API Layer

- All network calls go through a **typed API client** in `lib/` or feature `api/`
- Use query hooks (`useQuery` / `useMutation`) — never `fetch` inside components
- Always handle **loading, error, and empty** states explicitly
- Validate responses at runtime with **Zod** before trusting them

```tsx
const { data, isLoading, isError } = useUser(userId);
if (isLoading) return <Spinner />;
if (isError) return <ErrorState />;
if (!data) return <EmptyState />;
```

❌ **No unhandled promise rejections**
❌ **No silent catch blocks**

---

## 8. Styling Rules

- Use the design-system tokens (colors, spacing, typography) — **no magic values**
- Mobile-first responsive design
- No inline styles except for truly dynamic values
- Dark mode support where the design system requires it

❌ **Hardcoded hex colors / arbitrary pixel values scattered in components**

---

## 9. Accessibility (A11y) — MANDATORY

- Semantic HTML first (`<button>`, `<nav>`, `<main>`, headings in order)
- All interactive elements **keyboard-accessible** and focus-visible
- Images require `alt`; icons-only buttons require `aria-label`
- Color contrast **≥ WCAG AA (4.5:1)**
- Forms: labels tied to inputs, errors announced via `aria-live`

❌ **`<div onClick>` for clickable actions**
❌ **Removing focus outlines without a visible replacement**

---

## 10. Performance Standards

- **Core Web Vitals targets:** LCP < 2.5s, CLS < 0.1, INP < 200ms
- Code-split by route; lazy-load heavy/below-the-fold components
- Memoize expensive renders (`React.memo`, `useMemo`, `useCallback`) **only when measured**
- Optimize images (next/image or equivalent, correct sizes, lazy)
- Keep initial JS bundle lean; audit with bundle analyzer

❌ **Importing entire libraries for one function** (e.g. full lodash)
❌ **Unkeyed lists / unstable keys**

---

## 11. Forms & Validation

- React Hook Form + Zod schema for every non-trivial form
- Validate on the client **and** never trust client validation server-side
- Disable submit while pending; show inline field errors

---

## 12. Security Standards (CRITICAL)

- **Never render unsanitized HTML** — avoid `dangerouslySetInnerHTML`; sanitize if unavoidable
- Store auth tokens in **HTTP-only cookies**, not `localStorage`
- No secrets in frontend code or env vars exposed to the browser unless intended public
- Escape/encode all user-generated content (XSS protection)
- Validate and constrain redirects (no open redirects)

❌ **Secrets/API keys committed to the client bundle**
❌ **Trusting any client-side check for authorization**

---

## 13. Error Handling

- App-level **Error Boundary** for render errors
- User-friendly error states — never expose stack traces
- Log client errors to a monitoring service (Sentry or equivalent)

---

## 14. Testing Standards

**Mandatory:**
- Unit tests for hooks/utilities (Vitest/Jest)
- Component tests for critical UI (React Testing Library — test behavior, not implementation)
- E2E tests for critical user flows (Playwright/Cypress)

❌ **Snapshot-only testing as the sole strategy**
❌ **No tests at all**

---

## 15. Code Quality Rules

- ESLint + Prettier enforced in CI
- `strict: true` in `tsconfig` — no `any`, no `@ts-ignore` without justification
- No unused code, no commented-out blocks
- Clear naming; components `PascalCase`, hooks `useCamelCase`

---

## 16. AI Agent Frontend Rules

**AI MUST:**
- Use TypeScript with explicit types
- Follow feature-first structure and path aliases
- Separate UI from data/logic (query hooks, stores)
- Handle loading/error/empty states
- Meet accessibility and performance requirements

**If unsure → STOP and ask.**

---

## 17. Pre-Merge Checklist

- ✅ TypeScript strict, no `any`
- ✅ Architecture & folder structure respected
- ✅ Server data via query layer (not global store)
- ✅ Loading / error / empty states handled
- ✅ Accessibility verified (keyboard + contrast)
- ✅ Performance budget respected
- ✅ No secrets in client bundle
- ✅ Tests added for new logic/flows

---

## 18. Forbidden Practices

- Plain JS for app code
- API calls inside presentational components
- Server data duplicated in global state
- `dangerouslySetInnerHTML` without sanitization
- Tokens in `localStorage`
- Ignoring accessibility

---

## 19. Final Statement

This frontend standard ensures:
- Type-safe, maintainable UI code
- Accessible, performant user experiences
- Secure handling of data and auth
- Production-ready, enterprise-grade frontends
