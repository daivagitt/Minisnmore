# Figma documents

There is **no Figma file committed** in this repository (no Figma URLs in source as of 19 September 2026). Design is implemented directly in the React storefront.

Use this page as the handover checklist: paste the real Figma links when the design team shares them, and use the source map below to find the matching UI.

## Links to fill in

| Surface | Figma file / frame | Owner | Status |
| --- | --- | --- | --- |
| Storefront (home, shop, PDP, cart, checkout) | _add URL_ | | Missing in repo |
| Account (orders, returns, profile) | _add URL_ | | Missing in repo |
| Admin CMS | _add URL_ | | Missing in repo |
| Brand (logo, colours, type) | _add URL_ | | Missing in repo |
| Email templates (order confirmation, shipping) | _add URL_ | | Implemented in `backend/src/main/resources/templates/mail/` |

Until Figma is attached, treat **production** [https://minisnmore.com](https://minisnmore.com) as the visual source of truth.

## Code map (implemented UI)

| Experience | Primary files |
| --- | --- |
| Home | `frontend/src/routes/index.tsx` |
| Shop listing | `frontend/src/routes/shop.tsx` |
| Product detail | `frontend/src/routes/product.$slug.tsx` (and related `frontend/src/components/Product*.tsx`) |
| Cart / checkout | `frontend/src/routes/checkout.tsx`, cart store under `frontend/src/store/` |
| Account orders | `frontend/src/routes/account.orders.$orderNumber.tsx` |
| Admin shell + nav | `frontend/src/routes/admin.tsx`, `frontend/src/lib/admin-nav.ts` |
| Admin products | `frontend/src/routes/admin.products.tsx` |
| Tokens / layout | Tailwind + `frontend/src/components/` glass / display fonts |

Assets (favicon, OG image, robots): `public/`. Nginx serves those files from disk, not via the SPA.

## Handover actions

1. Export the current Figma “Mini’s n More” file as view-only for the receiving team.
2. Transfer Figma file ownership or add the client org as editor (Figma → Share → transfer).
3. Put the canonical URL in the table above and in the project wiki.
4. If design and production diverge, file tickets against the **live site**, not against an old prototype.
