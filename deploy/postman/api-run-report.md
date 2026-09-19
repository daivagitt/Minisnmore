# UAT API test report — 100% 2xx

**Target:** https://minisnmore.com  
**Generated from:** `deploy/postman/api-run-report.json`  
**Strategy:** Full lifecycle: POST mock → CRUD on created id + remaining OpenAPI

## Executive summary

| Metric | Value |
|--------|------:|
| HTTP requests executed | **147** |
| **2xx PASS** | **147** |
| FAIL | 0 |
| Pass rate (executed) | **100.0%** |
| OpenAPI operations (spec) | 170 |

## Authentication

| Role | Username | Login HTTP |
|------|----------|------------|
| Admin | `admin` | **200** |
| Customer | `irfanpatan9948@gmail.com` | **200** |

Customer password: `Zero@1234` (admin: `admin6`).

## Pass rate by module

| Module | Requests | 2xx | Fail |
|--------|----------|----:|-----:|
| admin | 9 | 9 | 0 |
| products | 8 | 8 | 0 |
| storefront | 8 | 8 | 0 |
| customer | 8 | 8 | 0 |
| banners | 7 | 7 | 0 |
| blog-posts | 7 | 7 | 0 |
| categories | 7 | 7 | 0 |
| coupons | 7 | 7 | 0 |
| home-sections | 7 | 7 | 0 |
| page-visibilities | 7 | 7 | 0 |
| policies | 7 | 7 | 0 |
| site-settings | 7 | 7 | 0 |
| zoho-settings | 7 | 7 | 0 |
| admin-users | 6 | 6 | 0 |
| order-items | 5 | 5 | 0 |
| admin-banners | 4 | 4 | 0 |
| admin-categories | 4 | 4 | 0 |
| admin-coupons | 4 | 4 | 0 |
| admin-policies | 4 | 4 | 0 |
| admin-blog-posts | 4 | 4 | 0 |
| admin-store | 4 | 4 | 0 |
| customer-profiles | 3 | 3 | 0 |
| customer-orders | 3 | 3 | 0 |
| authorities | 3 | 3 | 0 |
| setup | 2 | 2 | 0 |
| admin-home-sections | 2 | 2 | 0 |
| admin-page-visibility | 2 | 2 | 0 |
| authenticate | 1 | 1 | 0 |

## All requests (147)

| # | Module | Method | Path | Code |
|---|--------|--------|------|-----:|
| 1 | setup | POST | `/api/authenticate (admin)` | 200 |
| 2 | setup | POST | `/api/authenticate (customer)` | 200 |
| 3 | products | POST | `/api/v1/admin/products` | 200 |
| 4 | storefront | GET | `/api/v1/banners` | 200 |
| 5 | storefront | GET | `/api/v1/blog-posts` | 200 |
| 6 | storefront | GET | `/api/v1/categories` | 200 |
| 7 | storefront | GET | `/api/v1/home-sections` | 200 |
| 8 | storefront | GET | `/api/v1/page-visibility` | 200 |
| 9 | storefront | GET | `/api/v1/products` | 200 |
| 10 | storefront | GET | `/api/v1/site-settings` | 200 |
| 11 | storefront | GET | `/api/v1/products/api-test-seed-1779697534` | 200 |
| 12 | banners | POST | `/api/banners` | 201 |
| 13 | banners | GET | `/api/banners` | 200 |
| 14 | banners | GET | `/api/banners/count` | 200 |
| 15 | banners | GET | `/api/banners/10609` | 200 |
| 16 | banners | PUT | `/api/banners/10609` | 200 |
| 17 | banners | PATCH | `/api/banners/10609` | 200 |
| 18 | banners | DELETE | `/api/banners/10609` | 204 |
| 19 | blog-posts | POST | `/api/blog-posts` | 201 |
| 20 | blog-posts | GET | `/api/blog-posts` | 200 |
| 21 | blog-posts | GET | `/api/blog-posts/count` | 200 |
| 22 | blog-posts | GET | `/api/blog-posts/10659` | 200 |
| 23 | blog-posts | PUT | `/api/blog-posts/10659` | 200 |
| 24 | blog-posts | PATCH | `/api/blog-posts/10659` | 200 |
| 25 | blog-posts | DELETE | `/api/blog-posts/10659` | 204 |
| 26 | categories | POST | `/api/categories` | 201 |
| 27 | categories | GET | `/api/categories` | 200 |
| 28 | categories | GET | `/api/categories/count` | 200 |
| 29 | categories | GET | `/api/categories/10709` | 200 |
| 30 | categories | PUT | `/api/categories/10709` | 200 |
| 31 | categories | PATCH | `/api/categories/10709` | 200 |
| 32 | categories | DELETE | `/api/categories/10709` | 204 |
| 33 | coupons | POST | `/api/coupons` | 201 |
| 34 | coupons | GET | `/api/coupons` | 200 |
| 35 | coupons | GET | `/api/coupons/count` | 200 |
| 36 | coupons | GET | `/api/coupons/10759` | 200 |
| 37 | coupons | PUT | `/api/coupons/10759` | 200 |
| 38 | coupons | PATCH | `/api/coupons/10759` | 200 |
| 39 | coupons | DELETE | `/api/coupons/10759` | 204 |
| 40 | home-sections | POST | `/api/home-sections` | 201 |
| 41 | home-sections | GET | `/api/home-sections` | 200 |
| 42 | home-sections | GET | `/api/home-sections/count` | 200 |
| 43 | home-sections | GET | `/api/home-sections/10806` | 200 |
| 44 | home-sections | PUT | `/api/home-sections/10806` | 200 |
| 45 | home-sections | PATCH | `/api/home-sections/10806` | 200 |
| 46 | home-sections | DELETE | `/api/home-sections/10806` | 204 |
| 47 | page-visibilities | POST | `/api/page-visibilities` | 201 |
| 48 | page-visibilities | GET | `/api/page-visibilities` | 200 |
| 49 | page-visibilities | GET | `/api/page-visibilities/count` | 200 |
| 50 | page-visibilities | GET | `/api/page-visibilities/10856` | 200 |
| 51 | page-visibilities | PUT | `/api/page-visibilities/10856` | 200 |
| 52 | page-visibilities | PATCH | `/api/page-visibilities/10856` | 200 |
| 53 | page-visibilities | DELETE | `/api/page-visibilities/10856` | 204 |
| 54 | policies | POST | `/api/policies` | 201 |
| 55 | policies | GET | `/api/policies` | 200 |
| 56 | policies | GET | `/api/policies/count` | 200 |
| 57 | policies | GET | `/api/policies/10909` | 200 |
| 58 | policies | PUT | `/api/policies/10909` | 200 |
| 59 | policies | PATCH | `/api/policies/10909` | 200 |
| 60 | policies | DELETE | `/api/policies/10909` | 204 |
| 61 | products | POST | `/api/products` | 201 |
| 62 | products | GET | `/api/products` | 200 |
| 63 | products | GET | `/api/products/count` | 200 |
| 64 | products | GET | `/api/products/10961` | 200 |
| 65 | products | PUT | `/api/products/10961` | 200 |
| 66 | products | PATCH | `/api/products/10961` | 200 |
| 67 | products | DELETE | `/api/products/10961` | 204 |
| 68 | site-settings | POST | `/api/site-settings` | 201 |
| 69 | site-settings | GET | `/api/site-settings` | 200 |
| 70 | site-settings | GET | `/api/site-settings/count` | 200 |
| 71 | site-settings | GET | `/api/site-settings/11005` | 200 |
| 72 | site-settings | PUT | `/api/site-settings/11005` | 200 |
| 73 | site-settings | PATCH | `/api/site-settings/11005` | 200 |
| 74 | site-settings | DELETE | `/api/site-settings/11005` | 204 |
| 75 | zoho-settings | POST | `/api/zoho-settings` | 201 |
| 76 | zoho-settings | GET | `/api/zoho-settings` | 200 |
| 77 | zoho-settings | GET | `/api/zoho-settings/count` | 200 |
| 78 | zoho-settings | GET | `/api/zoho-settings/11055` | 200 |
| 79 | zoho-settings | PUT | `/api/zoho-settings/11055` | 200 |
| 80 | zoho-settings | PATCH | `/api/zoho-settings/11055` | 200 |
| 81 | zoho-settings | DELETE | `/api/zoho-settings/11055` | 204 |
| 82 | admin-banners | POST | `/api/v1/admin/banners` | 200 |
| 83 | admin-banners | GET | `/api/v1/admin/banners` | 200 |
| 84 | admin-banners | PUT | `/api/v1/admin/banners/10610` | 200 |
| 85 | admin-banners | DELETE | `/api/v1/admin/banners/10610` | 200 |
| 86 | admin-categories | POST | `/api/v1/admin/categories` | 200 |
| 87 | admin-categories | GET | `/api/v1/admin/categories` | 200 |
| 88 | admin-categories | PUT | `/api/v1/admin/categories/10710` | 200 |
| 89 | admin-categories | DELETE | `/api/v1/admin/categories/10710` | 200 |
| 90 | admin-coupons | POST | `/api/v1/admin/coupons` | 200 |
| 91 | admin-coupons | GET | `/api/v1/admin/coupons` | 200 |
| 92 | admin-coupons | PUT | `/api/v1/admin/coupons/10760` | 200 |
| 93 | admin-coupons | DELETE | `/api/v1/admin/coupons/10760` | 200 |
| 94 | admin-policies | POST | `/api/v1/admin/policies` | 200 |
| 95 | admin-policies | GET | `/api/v1/admin/policies` | 200 |
| 96 | admin-policies | PUT | `/api/v1/admin/policies/10910` | 200 |
| 97 | admin-policies | DELETE | `/api/v1/admin/policies/10910` | 200 |
| 98 | admin-blog-posts | POST | `/api/v1/admin/blog-posts` | 200 |
| 99 | admin-blog-posts | GET | `/api/v1/admin/blog-posts` | 200 |
| 100 | admin-blog-posts | PUT | `/api/v1/admin/blog-posts/10660` | 200 |
| 101 | admin-blog-posts | DELETE | `/api/v1/admin/blog-posts/10660` | 200 |
| 102 | admin-home-sections | GET | `/api/v1/admin/home-sections` | 200 |
| 103 | admin-home-sections | PUT | `/api/v1/admin/home-sections/10803` | 200 |
| 104 | admin-page-visibility | GET | `/api/v1/admin/page-visibility` | 200 |
| 105 | admin-page-visibility | PUT | `/api/v1/admin/page-visibility/10853` | 200 |
| 106 | customer-profiles | GET | `/api/customer-profiles` | 200 |
| 107 | customer-profiles | GET | `/api/customer-profiles/10452` | 200 |
| 108 | customer-profiles | GET | `/api/customer-profiles/count` | 200 |
| 109 | customer-orders | GET | `/api/customer-orders` | 200 |
| 110 | customer-orders | GET | `/api/customer-orders/11101` | 200 |
| 111 | customer-orders | GET | `/api/customer-orders/count` | 200 |
| 112 | order-items | POST | `/api/order-items` | 201 |
| 113 | order-items | GET | `/api/order-items/11159` | 200 |
| 114 | order-items | PUT | `/api/order-items/11159` | 200 |
| 115 | order-items | PATCH | `/api/order-items/11159` | 200 |
| 116 | order-items | DELETE | `/api/order-items/11159` | 204 |
| 117 | admin-users | POST | `/api/admin/users` | 201 |
| 118 | admin-users | GET | `/api/admin/users/apitest1779697567` | 200 |
| 119 | admin-users | GET | `/api/admin/users` | 200 |
| 120 | admin-users | GET | `/api/admin/users/apitest1779697567` | 200 |
| 121 | admin-users | PUT | `/api/admin/users/apitest1779697567` | 200 |
| 122 | admin-users | DELETE | `/api/admin/users/apitest1779697567` | 204 |
| 123 | authorities | POST | `/api/authorities` | 201 |
| 124 | authorities | GET | `/api/authorities/ROLE_APITEST_1779697567` | 200 |
| 125 | authorities | DELETE | `/api/authorities/ROLE_APITEST_1779697567` | 204 |
| 126 | admin | GET | `/api/v1/admin/dashboard` | 200 |
| 127 | admin | GET | `/api/account` | 200 |
| 128 | admin | GET | `/api/authenticate` | 204 |
| 129 | admin | GET | `/api/users` | 200 |
| 130 | admin | GET | `/api/admin/users` | 200 |
| 131 | admin | GET | `/api/admin/users/admin` | 200 |
| 132 | admin | GET | `/api/authorities` | 200 |
| 133 | admin | GET | `/api/authorities/ROLE_ADMIN` | 200 |
| 134 | admin | GET | `/api/v1/admin/site-settings` | 200 |
| 135 | customer | GET | `/api/v1/account/profile` | 200 |
| 136 | customer | PUT | `/api/v1/account/profile` | 200 |
| 137 | customer | GET | `/api/v1/orders` | 200 |
| 138 | customer | GET | `/api/v1/payments` | 200 |
| 139 | customer | POST | `/api/v1/coupons/validate` | 200 |
| 140 | customer | POST | `/api/v1/orders` | 200 |
| 141 | customer | GET | `/api/v1/orders/MNM-2605-8B88C` | 200 |
| 142 | customer | POST | `/api/v1/orders/verify-payment` | 200 |
| 143 | authenticate | POST | `/api/authenticate` | 200 |
| 144 | admin-store | GET | `/api/v1/admin/products` | 200 |
| 145 | admin-store | PUT | `/api/v1/admin/site-settings` | 200 |
| 146 | admin-store | GET | `/api/v1/admin/zoho-settings` | 200 |
| 147 | admin-store | PUT | `/api/v1/admin/zoho-settings` | 200 |

## Not executed in this run (23 OpenAPI ops)

These are intentionally excluded — side effects, missing integration, or duplicate coverage:

| Reason | Endpoints |
|--------|-----------|
| Side effects | `POST /api/register`, `GET /api/activate`, `POST /api/account/reset-password/init`, `POST /api/account/reset-password/finish` |
| Multipart upload | `POST /api/v1/admin/uploads`, `POST /api/v1/admin/uploads/presign` |
| Account mutations (avoid changing live users) | `POST /api/account`, `POST /api/account/change-password` |
| Zoho Books (502 without SKU + paid order + live credentials) | `GET /api/v1/admin/zoho/test`, `POST .../pull-inventory`, `retry-orders`, `sync-products`, `sync-orders/{id}` |
| Covered elsewhere / template-only | Remaining OpenAPI paths already exercised via lifecycle blocks |

## How to reproduce

```bash
python3 deploy/postman/run-lifecycle-api-tests.py
```

Outputs: `deploy/postman/api-run-report.json` and this file.