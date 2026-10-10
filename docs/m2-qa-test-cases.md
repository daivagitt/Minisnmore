# M2 QA test cases (cancel / returns / exchange)

**Verdict:** The first pass (48 cases) did **not** cover every coded edge. This pack is **95 cases** aligned to `OrderCancellationService`, `OrderReturnService`, and XB webhooks.

**Important:** `RETURN_WINDOW_DAYS = 3` in code (not 7 from older docs).

| Area | Positive | Negative | Total |
|------|----------|----------|-------|
| Cancel | 5 | 12 | 17 |
| Returns | 12 | 15 | 27 |
| Exchange | 9 | 11 | 20 |
| ExpressBees | 6 | 7 | 13 |
| Emails | 3 | 2 | 5 |
| Zoho independence | 2 | 1 | 3 |
| RBAC | 2 | 2 | 4 |
| Gaps/Exploratory | 1 | 4 | 5 |
| **Total** | **40** | **54** | **94** |

Excel (filterable + Pass/Fail column): [`m2-qa-test-cases.xlsx`](./m2-qa-test-cases.xlsx)

## Cases

| ID | Type | Area | P | Title | Expected |
|----|------|------|---|-------|----------|
| M2-C-P01 | Positive | Cancel | P0 | Customer submits cancel with valid reason | REQUESTED + cancellationRequested email + admin queue |
| M2-C-P02 | Positive | Cancel | P0 | Admin approves online cancel → Razorpay refund | Order CANCELLED; refund id; stock restored; accepted email |
| M2-C-P03 | Positive | Cancel | P0 | Admin approves COD cancel + record manual refund | Refund recorded once; customer informed; no ExpressBees |
| M2-C-P04 | Positive | Cancel | P0 | Admin rejects cancel | REJECTED; order continues; rejected email |
| M2-C-P05 | Positive | Cancel | P1 | Customer withdraws pending cancel | Pending request cleared; order unchanged |
| M2-C-N01 | Negative | Cancel | P0 | Cancel after FULFILLED blocked | 409 already dispatched |
| M2-C-N02 | Negative | Cancel | P0 | Cancel when tracking dispatched | 409 already dispatched |
| M2-C-N03 | Negative | Cancel | P0 | Duplicate pending cancel | 409 already pending |
| M2-C-N04 | Negative | Cancel | P1 | Reason too short/empty | 400 reason ≥3 |
| M2-C-N05 | Negative | Cancel | P2 | Reason too long | 400 reason too long |
| M2-C-N06 | Negative | Cancel | P0 | Already cancelled order | 409 already cancelled |
| M2-C-N07 | Negative | Cancel | P1 | Reject reason too short | 400 rejection ≥5 |
| M2-C-N08 | Negative | Cancel | P0 | Other user's order | 404/forbidden |
| M2-C-N09 | Negative | Cancel | P1 | Approve when not REQUESTED | 400 not awaiting approval |
| M2-C-N10 | Negative | Cancel | P1 | COD refund on non-collected / not approved | 400 only approved / only collected COD |
| M2-C-N11 | Negative | Cancel | P1 | Duplicate COD refund record | 409 already recorded |
| M2-C-N12 | Negative | Cancel | P2 | COD refund missing method/short ref | 400 method required / ref ≥4 |
| M2-R-P01 | Positive | Returns | P0 | Customer return within window (3 days) | REQUESTED; email; admin list |
| M2-R-P02 | Positive | Returns | P0 | Full path approve→pickup→receive→inspect→accept→refund | REFUNDED; Razorpay; Ledgerly CN; stock if RESTOCKABLE |
| M2-R-P03 | Positive | Returns | P0 | Admin reject at REQUESTED | REJECTED; customer notified |
| M2-R-P04 | Positive | Returns | P0 | Admin reject after INSPECTED (QC fail) | REJECTED; QC/return rejected email |
| M2-R-P05 | Positive | Returns | P0 | Pickup fail → reschedule | New pickup; stays open |
| M2-R-P06 | Positive | Returns | P0 | Pickup fail → close case | CANCELLED/closed; no refund |
| M2-R-P07 | Positive | Returns | P1 | Manual / self-ship logistics | Pickup scheduled without fabricating XB id |
| M2-R-P08 | Positive | Returns | P0 | Partial qty accept with mixed disposition | Refund only accepted units; stock only restockable |
| M2-R-P09 | Positive | Returns | P1 | Customer cancels own pending return | CANCELLED; only when REQUESTED |
| M2-R-P10 | Positive | Returns | P0 | Eligibility API eligible=true | eligible true; window_days=3 |
| M2-R-P11 | Positive | Returns | P1 | Refund retry after FAILED (Razorpay) | Succeeds or stays FAILED cleanly; no double pay if refund id set |
| M2-R-P12 | Positive | Returns | P1 | Ledgerly CN failure still completes local refund | Local REFUNDED; CN failure logged/retryable |
| M2-R-N01 | Negative | Returns | P0 | Not delivered yet | 400 not delivered yet |
| M2-R-N02 | Negative | Returns | P0 | Window expired (>3 days) | 400 window expired |
| M2-R-N03 | Negative | Returns | P0 | Unpaid order | 400 only paid |
| M2-R-N04 | Negative | Returns | P0 | Cancelled order | 400 cannot be returned |
| M2-R-N05 | Negative | Returns | P0 | Feature disabled | eligible false / 503 |
| M2-R-N06 | Negative | Returns | P0 | Open return/exchange already exists | 409 already open |
| M2-R-N07 | Negative | Returns | P0 | Qty exceeds ordered / duplicate line | 400 exceeds qty / line selected more than once |
| M2-R-N08 | Negative | Returns | P0 | Reason <10 chars | 400 at least 10 characters |
| M2-R-N09 | Negative | Returns | P0 | Wrong status transitions | 400 status-specific messages |
| M2-R-N10 | Negative | Returns | P1 | Accept with 0 units / missing disposition | 400 at least one unit / disposition required |
| M2-R-N11 | Negative | Returns | P0 | processRefund on EXCHANGE | 400 use convert-to-refund |
| M2-R-N12 | Negative | Returns | P1 | Double COD refund / wrong COD state | 409/400 |
| M2-R-N13 | Negative | Returns | P1 | Existing Razorpay refund id skips second charge | Skips Razorpay; stays REFUNDED |
| M2-R-N14 | Negative | Returns | P1 | Cancel return after APPROVED | 400 only pending can be cancelled |
| M2-R-N15 | Negative | Returns | P0 | Wrong user cancel/view | 404 not found |
| M2-E-P01 | Positive | Exchange | P0 | Exchange when replacement in stock | EXCHANGE REQUESTED; email |
| M2-E-P02 | Positive | Exchange | P0 | Happy path to COMPLETED | COMPLETED; tracking emails |
| M2-E-P03 | Positive | Exchange | P0 | Reject after inspection | REJECTED + exchangeRejected email |
| M2-E-P04 | Positive | Exchange | P0 | Pickup fail → exchange cancelled | CANCELLED + exchangeCancelled email |
| M2-E-P05 | Positive | Exchange | P0 | Settle price diff COLLECTED/REFUNDED/WAIVED | Settlement cleared; can allocate |
| M2-E-P06 | Positive | Exchange | P1 | NOT_REQUIRED when same price | Settlement NOT_REQUIRED; allocate without pay |
| M2-E-P07 | Positive | Exchange | P0 | Disposition RESTOCKABLE vs DISCARD/VENDOR | Stock increments only RESTOCKABLE |
| M2-E-P08 | Positive | Exchange | P0 | Convert exchange to refund | REFUNDED path instead of outbound |
| M2-E-P09 | Positive | Exchange | P0 | Approve reserves replacement stock | Stock decremented/reserved; APPROVED |
| M2-E-N01 | Negative | Exchange | P0 | Replacement OOS at request | 400 unavailable for exchange |
| M2-E-N02 | Negative | Exchange | P0 | Non-variant product | 400 does not support exchanges |
| M2-E-N03 | Negative | Exchange | P0 | OOS race at approve | 409 replacement no longer available/in stock |
| M2-E-N04 | Negative | Exchange | P0 | Open return blocks exchange | 409 open return exists |
| M2-E-N05 | Negative | Exchange | P0 | Duplicate open exchange on same item | 409 already open for this item |
| M2-E-N06 | Negative | Exchange | P0 | Outbound before READY_TO_SHIP / unsettled | 400/409 settle first / ready to ship |
| M2-E-N07 | Negative | Exchange | P1 | Allocate with released reservation | 409 reservation was released |
| M2-E-N08 | Negative | Exchange | P1 | Settlement amount/method mismatch | 400 amount must equal / method+ref required |
| M2-E-N09 | Negative | Exchange | P0 | Outside eligibility window/unpaid/undelivered | Same eligibility 400s |
| M2-E-N10 | Negative | Exchange | P0 | Invalid reason (<10 chars) | 400 ≥10 characters |
| M2-E-N11 | Negative | Exchange | P1 | Return-only API on exchange | 400 Not an exchange request |
| M2-X-P01 | Positive | ExpressBees | P0 | Book reverse pickup after approve | AWB stored; PICKUP_SCHEDULED/REQUESTED |
| M2-X-P02 | Positive | ExpressBees | P0 | Outbound replacement AWB + tracking | SHIPPED; tracking to customer |
| M2-X-P03 | Positive | ExpressBees | P0 | Forward ship from orders (local SoR) | AWB without Zoho wait |
| M2-X-P04 | Positive | ExpressBees | P0 | Webhook pickup fail reasons | markPickupFailed; admin can act |
| M2-X-P05 | Positive | ExpressBees | P1 | Webhook delivered on exchange outbound | DELIVERED → can complete |
| M2-X-P06 | Positive | ExpressBees | P1 | Idempotent/unmapped webhook status | No corrupt transition; ignore unmapped |
| M2-X-N01 | Negative | ExpressBees | P0 | Pickup before APPROVED | 400 must be approved |
| M2-X-N02 | Negative | ExpressBees | P1 | Missing logistics mode / AWB | 400 mode/AWB required |
| M2-X-N03 | Negative | ExpressBees | P0 | XB not configured / API down | 503/502; status stays APPROVED; retry OK |
| M2-X-N04 | Negative | ExpressBees | P0 | Cancel path never books XB | No XB API calls |
| M2-X-N05 | Negative | ExpressBees | P1 | Webhook missing return_number/AWB/status | 400 missing fields |
| M2-X-N06 | Negative | ExpressBees | P2 | Webhook when feature not configured | 503 not configured |
| M2-X-N07 | Negative | ExpressBees | P1 | markPickupFailed before scheduled | 400 pickup must be scheduled |
| M2-M-P01 | Positive | Emails | P0 | Cancel triad emails | Requested/Accepted/Rejected templates |
| M2-M-P02 | Positive | Emails | P0 | Return lifecycle emails | return* templates |
| M2-M-P03 | Positive | Emails | P0 | Exchange emails reject/cancel/close | exchangeRejected / Cancelled / close templates |
| M2-M-N01 | Negative | Emails | P0 | SMTP fail does not roll back status | Status persists; mail warn logged |
| M2-M-N02 | Negative | Emails | P1 | Wrong template by request type | Correct template each; not swapped |
| M2-Z-P01 | Positive | Zoho independence | P0 | Ship with Ledgerly/Zoho sync FAILED | AWB created; shipping not blocked |
| M2-Z-P02 | Positive | Zoho independence | P1 | Return refund without Zoho CN when disabled | Local refund; Zoho not called |
| M2-Z-N01 | Negative | Zoho independence | P0 | Zoho outage must not block ship UI | No hard block on SoR ship |
| M2-B-P01 | Positive | RBAC | P0 | Privileged admin can cancel/returns/ship | All succeed |
| M2-B-P02 | Positive | RBAC | P1 | Limited role view-only | Mutate controls hidden |
| M2-B-N01 | Negative | RBAC | P0 | Unauthorized admin mutate API | 401/403; no state change |
| M2-B-N02 | Negative | RBAC | P0 | Customer JWT on admin APIs | 403; no cross-tenant leak |
| M2-G-01 | Negative | Gaps/Exploratory | P1 | Concurrent double-approve race | One wins; other 400; no double refund/stock |
| M2-G-02 | Negative | Gaps/Exploratory | P1 | Partial multi-item: return some then exchange other | Allowed per open/terminal + per-item rules |
| M2-G-03 | Negative | Gaps/Exploratory | P1 | BOGO free-line refund ₹0 vs paid line | Refund matches discount_allocation |
| M2-G-04 | Positive | Gaps/Exploratory | P2 | Delivery anchor from Ledgerly/XB vs shipment_date | eligible based on resolved anchor |
| M2-G-05 | Negative | Gaps/Exploratory | P1 | RBAC permission matrix exhaustive | Every deny/allow matches rbac config |

## Residual (not fully closable from code alone)

- **M2-G-01** concurrency / double-approve soak
- **M2-G-02** multi-item return-then-exchange sequencing
- **M2-G-03** BOGO refund allocation (see `bogo-qa-checklist.md`)
- **M2-G-04** delivery-anchor source variants
- **M2-G-05** full RBAC role×action matrix from admin RBAC config
