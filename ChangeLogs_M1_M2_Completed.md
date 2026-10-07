# Change Log M1 & M2 Completed Points (September 2026)

## Summary

| NO | Module | Type | Description |
|----|--------|------|-------------|
| 1  | Customer Profile / Newsletter | New Feature | User Subscribe/Unsubscribe opt-in checkbox (default checked) and profile preference management |
| 2  | Blogs | Enhancement | Storefront stories and admin blog CMS enabled with cover photos, tags, and reading times |
| 3  | Watch & Shop | New Feature | Shoppable vertical video reels on homepage linked to product SKUs with tap-to-shop |
| 4  | Promotions & Discounts | New Feature | Buy One Get One (BOGO) promotion rules with Buy X Get Y logic and cart/checkout discount calculations |
| 5  | Cultural Gift Concierge | New Feature | Guided concierge gifting page with collapsible accordions, occasion/budget/palette selection, and packaging notes |
| 6  | Tracking & Notifications | Enhancement | Automatic "Out for Delivery" and "Delivered" customer email updates synchronized with ExpressBees scans |
| 7  | Post-Order Workflows (Emails) | New Feature | Complete transactional HTML email suite for Cancellations, Returns, Exchanges, and Refunds |
| 8  | Checkout & Logistics | Bug / Validation | Real-time pincode serviceability check blocking checkout progression if address is unserviceable |
| 9  | Products Catalog (Storefront) | Bug / UX | Mobile responsive filter chips with collapsible drawer and clean chip wrapping on small screens |
| 10 | Admin Invoices & Printing | Bug / Print Layout | Optimized single-page A4 print layout for GST Tax Invoice without two-page overflow |
| 11 | Admin Orders & Accounting Sync | Enhancement | Zoho Books payment status, sync badges, error diagnostics, and retry actions on Sales Orders list |
| 12 | Cart | Bug / UX Copy | Updated cart item removal dialog confirmation CTA button from "Remove only" to "Remove" |
| 13 | Storefront Contact Page | New Feature | Dedicated Contact Us page with address, phone, WhatsApp direct link, map embed, and logged-in autofill |
| 14 | Admin Security & Auditing | New Feature | Automated email alerts to info@stoltglobal.com with field-level diffs on admin configuration changes |
| 15 | Admin Products Catalog | Enhancement / UX | Collapsible accordion sections on product editor with expand/collapse all to prevent excessive scrolling |
| 16 | Order Cancellation (Customer UX) | Bug / Copy | Removed internal "Inventory back to stock" warehouse wording from order cancel confirmation modal |
| 17 | Customer Management (Admin) | Enhancement | Customer database list with purchase history indicator (hasOrdered boolean) and join-date segment filters |
| 18 | Admin Products Catalog | Enhancement / Form | Visual red asterisk (*) indicators for all mandatory product creation fields (Name, Category, SKU, Fabric) |

---

## 1. Customer Profile / Newsletter — Subscribe & unsubscribe preferences

**Module Name:** Customer Profile / Newsletter  

**Type:** New Feature  

**What Was Wrong / Needed:**  
Customers had no way to choose whether they want promotional emails, new feature announcements, or subscription perks, nor could they pause, skip, or unsubscribe from marketing emails in their profile. Additionally, the registration form lacked a default-checked opt-in consent for email subscriptions.

**Short Description:**  
Added an email subscription preference (`subscribed` boolean flag on customer profiles and database). During customer registration/signup (`src/routes/login.tsx`), the subscription checkbox is presented and ticked by default ("Keep me updated on new arrivals, gentle stories and nursery notes"). On **Account → Profile** (`src/routes/account.profile.tsx`), customers can view their subscription status and toggle it to subscribe, pause, or unsubscribe at any time.

**Manual Testing Steps:**

1. Open the storefront in an incognito window and navigate to **Sign Up / Register** (`/login?mode=register`).
2. Confirm the **"Keep me updated on new arrivals, gentle stories and nursery notes"** checkbox is displayed and **checked by default**.
3. Register a new account with the checkbox left checked; verify in database/account that `subscribed` is set to `true`.
4. Register another account after unchecking the box; verify `subscribed` is set to `false`.
5. Log in to an account and go to **Account → Profile** (`/account/profile`).
6. Look for the **Email Updates / Newsletter** toggle card.
7. Toggle the preference to unsubscribe/pause and click save; confirm the notification and verify the profile persists `subscribed = false`.
8. Toggle the preference back to active subscription and save; verify `subscribed = true`.

---

## 2. Blogs — Storefront stories and admin blog CMS

**Module Name:** Blogs  

**Type:** Enhancement  

**What Was Wrong / Needed:**  
The Blogs section was disabled or had placeholder content without dynamic CMS capabilities. Staff had no way to draft, publish, edit, or delete baby care and organic parenting articles from the admin panel, and customers could not read published stories.

**Short Description:**  
Enabled the **Blogs** feature end-to-end on both storefront (`/blog`, `/blog/$slug`) and admin (`/admin/blog`). Customers can browse articles on organic cotton, gentle baby care, fabric care, and conscious parenting with dedicated cover imagery, tags, reading time, and breadcrumbs. In **Admin → Blogs**, administrators can create new blog posts, upload cover photos, write content with rich text/markdown, manage publish status, and edit or delete posts. Homepage also features a curated blog teaser strip (`HomeBlogStripEditor`).

**Manual Testing Steps:**

1. Navigate to `/blog` on the storefront.
2. Confirm the blog index loads with published articles (e.g., articles on organic cotton, parenting, baby care), showing cover images, titles, reading times, and snippets.
3. Click any blog post card to open `/blog/$slug`. Confirm the full post displays with header, hero cover image, publication date, author, formatted body paragraphs, and "Back to blogs" navigation.
4. Log in as admin and navigate to **Admin → Blogs** (`/admin/blog`).
5. Click **New blog post**, enter Title, Slug, Excerpt, Content, upload a cover photo, and toggle Published to true.
6. Save and open the blog storefront in an incognito window; confirm the new story appears immediately.
7. Edit the blog post in admin to modify text; verify updates reflect on the live post.
8. Delete the test blog post; verify it is removed from the blog index.

---

## 3. Watch & Shop — Shoppable video reels linked to product SKUs

**Module Name:** Watch & Shop  

**Type:** New Feature  

**What Was Wrong / Needed:**  
The Watch & Shop shoppable video reel section was disabled. There was no way for merchandisers to upload video links and link each reel to a specific product SKU so customers could watch short video reels and directly shop the featured item.

**Short Description:**  
Re-enabled the **Watch & Shop** feature on the homepage (`src/routes/index.tsx`) and admin dashboard (`src/routes/admin.home.tsx`, `HomeWatchShopEditor`). In admin, merchandisers can configure up to 5 video reels by providing the video URL (MP4 / streaming link) and linking each reel directly to a product SKU or slug. On the homepage, customers can watch looping vertical video reels, view product details overlay (title, price, image), and tap "Shop Now" to go straight to the product page.

**Manual Testing Steps:**

1. Log in to the admin panel and navigate to **Admin → Home** (`/admin/home`).
2. Locate the **Watch & Shop** section card.
3. Add a new reel row: enter a valid video link (e.g., `/assets/...mp4` or CDN video URL), enter a valid product SKU (e.g., `MNM-SW-001`), and set the sort order.
4. Save the home page configuration and verify success toast.
5. Go to the storefront homepage (`/`).
6. Scroll down to the **Watch & Shop** section ("Tap a moment to shop the look").
7. Confirm the video reels render with smooth playback and video controls/looping.
8. Verify each reel displays the linked product name, price, and thumbnail.
9. Click the product overlay card or "Shop the Look" button on a reel; confirm it navigates directly to the corresponding product details page (`/products/$slug`).
10. Return to admin and test leaving the SKU blank or adding more than 5 reels; confirm validation warnings work as expected.

---

## 4. Promotions & Discounts — Buy One Get One (BOGO) promotion engine

**Module Name:** Promotions & Discounts  

**Type:** New Feature  

**What Was Wrong / Needed:**  
The coupon and discount system only supported flat rupee or percentage discounts. There was no way to create Buy-X-Get-Y (BOGO) promotions, such as "Buy 1 Get 1 Free" or "Buy 2 Get 1 50% Off", with SKU-level targeting.

**Short Description:**  
Implemented Buy-X-Get-Y (BOGO) coupon promotions end-to-end (`BOGOCouponCalculator.java`, `CouponDiscountType.BOGO`, `src/routes/admin.coupons.tsx`, cart & checkout calculations). In **Admin → Coupons**, admins can create coupons with discount type **BOGO**, specifying Buy Quantity (X), Get Quantity (Y), discount percentage on the free/discounted items (e.g., 100% for free), and optional target SKU restrictions. At cart and checkout, the system validates qualifying items in the cart, automatically calculates the BOGO discount, and applies the discount cleanly to order totals.

**Manual Testing Steps:**

1. Log in to the admin dashboard and navigate to **Admin → Coupons** (`/admin/coupons`).
2. Click **Create Coupon**, enter code (e.g., `BOGOFREE`), select type **BOGO**, enter Buy Quantity = 1, Get Quantity = 1, Discount % = 100%, and save.
3. Open the storefront as a customer and add 2 qualifying items to the cart.
4. Open the cart page (`/cart`) and apply coupon code `BOGOFREE`.
5. **Expected:** Coupon applies successfully; line item or order summary displays the BOGO discount equivalent to 1 item's price.
6. Test with only 1 item in cart and apply `BOGOFREE`; **Expected:** Alert informs that minimum quantity requirement is not met.
7. Add a 3rd item (total 3 items); **Expected:** Discount applies to 1 item (Buy 1 Get 1); add a 4th item; **Expected:** Discount scales to 2 items (Buy 2 Get 2).
8. Proceed to checkout and confirm the BOGO discount reflects accurately on the final payable amount and invoice summary.

---

## 5. Cultural Gift Concierge — Guided concierge gifting flow with collapse options

**Module Name:** Cultural Gift Concierge  

**Type:** New Feature  

**What Was Wrong / Needed:**  
The Cultural Gift Concierge page was disabled or incomplete. Customers needed a guided gifting flow for traditional and milestone occasions (e.g., Baby Shower, Naming Ceremony, First Birthday) with budget, palette, and age selection, and collapsible summary panels to review gift selections without visual clutter.

**Short Description:**  
Enabled the **Cultural Gift Concierge** page (`/gift`, `src/routes/gift.tsx`) with CMS integration (`src/routes/admin.gift.tsx`, `gift-page.ts`). Customers can walk through an interactive concierge step-by-step: select gifting occasion, budget tier (e.g., ₹2,000–₹5,000), color palette, and baby age (0-3M up to 2-3Y). The page features collapsible accordions/summary sections allowing users to review their curated selections, include a personalized greeting note, select gift-wrapping options, and add the bundle directly to the cart.

**Manual Testing Steps:**

1. On the storefront, click the **Gift Concierge** link in the navigation menu or browse to `/gift`.
2. Step 1 (Occasion): Select an occasion (e.g., "Naming Ceremony" or "Baby Shower") and click Next.
3. Step 2 (Budget & Age): Pick a budget tier (e.g., ₹2k - ₹5k) and age range (e.g., 3-6M).
4. Step 3 (Color Palette): Select a palette (e.g., "Earthy Neutrals", "Pastel Hues").
5. Expand and collapse the accordion sections to verify smooth accordion toggling without content jumping.
6. Review the curated gift bundle preview, enter a gift message for the recipient, and click **Add Curated Gift to Cart**.
7. Verify the bundle is added to the cart drawer with the specified options and gift packaging indicator.
8. In **Admin → Gift Concierge** (`/admin/gift`), edit occasion titles or banner text; verify updates reflect on `/gift`.

---

## 6. Tracking & Notifications — ExpressBees sync for "Out for Delivery" & "Delivered" emails

**Module Name:** Tracking & Notifications  

**Type:** Enhancement  

**What Was Wrong / Needed:**  
When a parcel progressed through shipping, customers only received order confirmation and dispatch emails. Key transit milestones like "Out for Delivery" and "Delivered" were not triggering customer emails, and courier webhooks from ExpressBees did not notify customers when their parcel was with the delivery agent or dropped off.

**Short Description:**  
Integrated automated email notifications for `OUT_FOR_DELIVERY` and `DELIVERED` shipment statuses synchronized with ExpressBees carrier webhooks (`OrderMailService.java`, `orderOutForDeliveryEmail.html`, `orderDeliveredEmail.html`, `OrderEventService.java`). When ExpressBees posts an out-for-delivery or delivery scan webhook (or admin updates tracking), the system updates order status in real time and automatically dispatches a branded email with tracking URL, delivery address, and parcel summary.

**Manual Testing Steps:**

1. Create a test order and advance it to `SHIPPED` with an ExpressBees AWB number.
2. Simulate an ExpressBees webhook callback with status `OUT_FOR_DELIVERY` (or trigger in admin order fulfillment).
3. **Expected:** Order status updates to `OUT_FOR_DELIVERY` on customer tracking and admin order list.
4. **Expected:** An `orderOutForDeliveryEmail` is dispatched to the customer's registered email address with the courier name, tracking ID, and expected delivery notice.
5. Simulate an ExpressBees webhook callback with status `DELIVERED`.
6. **Expected:** Order status changes to `DELIVERED`.
7. **Expected:** An `orderDeliveredEmail` is dispatched confirming successful delivery and providing return/exchange window details.
8. Verify the email formatting in both desktop and mobile email clients (responsive layout, images, and links).

---

## 7. Post-Order Workflows (Emails) — Transactional emails for Cancel, Exchange, and Returns

**Module Name:** Post-Order Workflows (Emails)  

**Type:** New Feature  

**What Was Wrong / Needed:**  
Customers had no automated email communications during post-order requests. Actions such as requesting a cancellation, admin approval/rejection of cancels, return pickups, return approvals, and exchange dispatches lacked customer email notifications.

**Short Description:**  
Implemented comprehensive HTML transactional email templates and service triggers for all post-order workflows (`OrderMailService.java`):
- **Cancellations:** `cancellationRequestedEmail`, `cancellationAcceptedEmail`, `cancellationRejectedEmail`, `orderCancelledEmail`.
- **Returns:** `returnRequestedEmail`, `returnApprovedEmail`, `pickupScheduledEmail`, `outForPickupEmail`, `returnReceivedEmail`, `returnAcceptedEmail`, `returnRejectedEmail`, `returnQcFailedEmail`.
- **Exchanges:** `exchangeRequestedEmail`, `exchangeApprovedEmail`, `exchangeAcceptedEmail`, `exchangeRejectedEmail`, `exchangeCancelledEmail`, `exchangeOutForDeliveryEmail`, `exchangeDeliveredEmail`.
- **Refunds:** `refundInitiatedEmail`, `refundCompletedEmail`.

**Manual Testing Steps:**

1. Open an existing order on the storefront and request a cancellation.
2. **Expected:** Customer receives `cancellationRequestedEmail` acknowledging receipt of request.
3. In Admin, approve the cancellation request.
4. **Expected:** Customer receives `cancellationAcceptedEmail` (or `orderCancelledEmail`) with refund timeline.
5. For a delivered order, request a Return.
6. **Expected:** Customer receives `returnRequestedEmail`.
7. Admin approves return and schedules ExpressBees reverse pickup.
8. **Expected:** Customer receives `returnApprovedEmail` and `pickupScheduledEmail` with pickup AWB and instructions.
9. For an exchange request, approve and ship the replacement product.
10. **Expected:** Customer receives `exchangeApprovedEmail`, and upon dispatch receives `exchangeOutForDeliveryEmail` followed by `exchangeDeliveredEmail`.

---

## 8. Checkout & Logistics — Serviceability check blocking unserviceable pincodes

**Module Name:** Checkout & Logistics  

**Type:** Bug / Validation  

**What Was Wrong / Needed:**  
On the checkout screen, if a customer entered a pincode that was not serviceable by the delivery partner (ExpressBees), the customer was still able to proceed and click "Place Order". This resulted in unfulfillable orders, failed courier bookings, and manual order cancellations.

**Short Description:**  
Integrated real-time pincode serviceability validation in the checkout flow (`src/routes/checkout.tsx`, `PincodeLookupService.java`, `XpressBeesClient.checkServiceability`). When a 6-digit Indian pincode is entered or selected from saved addresses, the system queries ExpressBees serviceability. If the pincode is non-serviceable, an inline error is displayed ("Delivery isn't available for this pincode yet"), and the "Continue to Payment" / "Place Order" button is disabled to prevent unfulfillable orders.

**Manual Testing Steps:**

1. Add an item to cart and proceed to Checkout (`/checkout`).
2. In the shipping address form, enter a known non-serviceable pincode (e.g., remote or unserviced PIN `999999` or test unserviceable PIN).
3. **Expected:** Pincode lookup returns unserviceable status; an error message displays: *"Delivery isn't available for this pincode yet."*
4. Scroll to the "Continue to payment" CTA button.
5. **Expected:** The button is disabled (or clicking it blocks submission and focuses on the pincode field error).
6. Change the pincode to a valid, serviceable pincode (e.g., `560001` or `400067`).
7. **Expected:** Pincode lookup succeeds, city/state auto-populate, estimated delivery date appears, the error disappears, and the "Continue to payment" CTA becomes clickable.
8. Verify that selecting an unserviceable saved address from the address book immediately marks the address as unserviceable and blocks submission.

---

## 9. Products Catalog (Storefront) — Mobile filter bar and alignment

**Module Name:** Products Catalog (Storefront)  

**Type:** Bug / UX  

**What Was Wrong / Needed:**  
On mobile devices and small screens, the filter chips (Category, Age, Fabric) and sort dropdown on the `/shop` products page overflowed horizontally, broke layout alignment, or overlapped with product cards, making filtering awkward on smartphones.

**Short Description:**  
Redesigned the mobile filter bar on `/shop` (`src/routes/shop.tsx`). Added a dedicated mobile toggle button (`Filters` with icon) that smoothly expands/collapses the filter drawer/pill area. Filter chips for categories, age groups, and fabrics wrap cleanly with proper spacing and touch-friendly padding, and active filter count and "Clear filters" action remain accessible on mobile viewports.

**Manual Testing Steps:**

1. Open Chrome DevTools and toggle device mode to mobile viewport (e.g., iPhone 12/14, 375px or 390px width).
2. Navigate to `/shop`.
3. Confirm the product grid displays 2 items per row with aligned typography and pricing.
4. Check the filter bar below search: confirm the **"Filters"** toggle button and Sort dropdown are neatly aligned side-by-side.
5. Tap the **"Filters"** button to expand filter options.
6. Confirm Category, Age (0-3M, 3-6M, etc.), and Fabric chips wrap neatly within the container without horizontal page scrollbars or clipped text.
7. Select multiple filters (e.g., Category: "Rompers", Fabric: "Organic Cotton"); verify matching count updates (e.g., "Showing 4 pieces matching your filters").
8. Tap "Clear filters"; confirm chips reset smoothly and product listing restores.

---

## 10. Admin Invoices & Printing — Single-page A4 GST Tax Invoice layout

**Module Name:** Admin Invoices & Printing  

**Type:** Bug / Print Layout  

**What Was Wrong / Needed:**  
When generating or printing the GST Tax Invoice from the admin order screen, excessive margins, unconstrained table rows, and tall header elements caused the invoice to spill over onto a second page. Packing staff needed a compact, compliant single-page A4 document to insert inside parcel boxes.

**Short Description:**  
Optimized `PortraitInvoiceDocument.tsx` with dedicated print CSS media queries (`print:w-[210mm] print:h-[297mm] print:max-h-[297mm] print:overflow-hidden print:p-[6mm]`, compact line heights, scaled QR code, and streamlined item table). The entire GST tax invoice—including brand logo, seller details, GSTIN, shipping address, QR code, itemized HSN/GST breakdown, CGST/SGST amounts, and footer sign-off—fits cleanly onto a single A4 sheet without spilling over.

**Manual Testing Steps:**

1. Log in to the admin panel and open an order with multiple line items (`/admin/orders`).
2. Click **Print Tax Invoice** or open `/admin/orders/{orderNumber}/invoice`.
3. In the browser, trigger Print (Ctrl+P / Cmd+P) or preview print mode.
4. Ensure paper size is set to **A4** and margins are set to **Default** or **None**.
5. **Expected:** Print preview displays **Total: 1 page** (Page 1 of 1).
6. Confirm all required GST elements are visible on that single page:
   - Seller Legal Entity & GSTIN
   - Customer Billing & Shipping Address
   - Invoice Number & Invoice Date
   - Order QR Code
   - Item descriptions, HSN codes, Taxable value, CGST, SGST, and Grand Total
   - Authorized Signatory & declaration footer
7. Test with an order containing 4–5 items; confirm row padding adjusts cleanly to remain on 1 page.

---

## 11. Admin Orders & Accounting Sync — Zoho payment status on Sales Orders list

**Module Name:** Admin Orders & Accounting Sync  

**Type:** Enhancement  

**What Was Wrong / Needed:**  
On the Sales Orders list page (`/admin/orders`), staff could not see whether an order's payment had successfully synced and reconciled in Zoho Books. Staff had to open each order individually or check Zoho Books directly to know if an order was paid or had sync errors.

**Short Description:**  
Integrated Zoho payment and synchronization indicators into the Orders Workspace (`src/components/admin/OrdersWorkspace.tsx`). The orders table displays a dedicated **Zoho** sync status badge (`synced`, `failed`, `pending`, `eligible`), timestamp of last sync, and error tooltips. Administrators can filter orders by Zoho status ("All Zoho", "Synced", "Needs attention"), view page summary counts (e.g., "Zoho 8/10 on this page"), and click **"Re-sync to Zoho"** or **"Sync pending to Zoho"** directly from the list view.

**Manual Testing Steps:**

1. Navigate to **Admin → Orders** (`/admin/orders`).
2. Locate the filter bar and look for the **Zoho** dropdown filter.
3. Select filter option **"Needs attention"**; verify the list filters to show only orders whose Zoho sync failed or is pending.
4. Inspect an order card/row:
   - Confirm the **Zoho status badge** is visible alongside the payment status badge.
   - For synced orders: confirm badge shows green `synced` and shows `Last synced [Date/Time]`.
   - For failed syncs: confirm red badge and readable error explanation text.
5. Click **"Sync to Zoho"** on an un-synced order; verify loading indicator and subsequent toast notification (`{orderNumber} synced to Zoho`).
6. Click **"Sync pending to Zoho"** at the top; verify bulk background sync runs and updates all eligible rows on the page.

---

## 12. Cart — Dialog confirmation CTA text updated to "Remove"

**Module Name:** Cart  

**Type:** Bug / UX Copy  

**What Was Wrong / Needed:**  
When a user clicked to remove an item from their cart, the confirmation dialog presented a button labeled `"Remove only"` alongside `"Save to Wishlist"`. The phrase "Remove only" was clunky and confusing to shoppers.

**Short Description:**  
Updated the CTA button copy in `src/components/RemoveFromCartDialog.tsx` from `"Remove only"` to `"Remove"`. The dialog cleanly presents two distinct actions: secondary button labeled **"Remove"** to delete the item from cart, and primary button **"Save to wishlist"** to move the item to wishlist.

**Manual Testing Steps:**

1. Add any product to the shopping cart.
2. Open the cart drawer or navigate to the cart page (`/cart`).
3. Click the trash/delete icon on a cart item.
4. **Expected:** Confirmation dialog modal appears with title *"Remove from cart?"*.
5. **Expected:** The dialog buttons are:
   - Cancel / Close
   - **Remove** (previously "Remove only")
   - **Save to wishlist**
6. Click **Remove**; verify the item is removed from the cart immediately.
7. Repeat test by clicking **Save to wishlist**; verify item is moved to wishlist.

---

## 13. Storefront Contact Page — Dedicated Contact Us page with logged-in autofill

**Module Name:** Storefront Contact Page  

**Type:** New Feature  

**What Was Wrong / Needed:**  
There was no dedicated Contact Us page displaying company physical address, official phone number, email address, WhatsApp link, and embedded map. Additionally, when logged-in customers navigated to contact the team, they had to manually type their name, email, and phone every time.

**Short Description:**  
Created a dedicated **Contact Us** page (`/contact-us`, `src/routes/contact-us.tsx`, `ContactForm.tsx`). The page provides full contact information: customer support email, direct phone number, one-tap WhatsApp chat link (`wa.me`), physical studio address, and Google Maps embed. For signed-in users, the contact form automatically pre-fills their registered full name, email address, and saved telephone number while still allowing edits before submitting.

**Manual Testing Steps:**

1. Open the website header or footer and click **Contact Us** (or go to `/contact-us`).
2. Confirm the page displays:
   - Customer care email with `mailto:` link.
   - Support telephone number with `tel:` link.
   - Direct WhatsApp action button opening WhatsApp chat with prefilled greeting.
   - Studio address with Google Maps embed and directions link.
3. While logged out, confirm the contact form inputs (Name, Email, Phone, Message) are empty and accept input.
4. Log in as an existing customer who has an address/profile saved.
5. Re-open `/contact-us`.
6. **Expected:** Full Name, Email, and Phone number are **automatically pre-filled** from the user's account session.
7. Edit the message field, submit, and confirm success notification.

---

## 14. Admin Security & Auditing — Configuration change email notifications to info@stoltglobal.com

**Module Name:** Admin Security & Auditing  

**Type:** New Feature  

**What Was Wrong / Needed:**  
Critical business configurations—such as coupon rules, storewide discounts, and Zoho Books integration keys—could be altered by staff without any audit notification, making it impossible to detect unintended modifications or policy changes in real time.

**Short Description:**  
Implemented automated administrative change alerts (`AdminConfigChangeEmailService.java`, `adminConfigChangeEmail.html`). Whenever an administrator saves changes to site configurations (including coupons, storewide discount rules, or Zoho integration credentials), the backend captures a before-and-after snapshot and immediately dispatches a detailed diff email to `info@stoltglobal.com`. The email highlights the user who made the change, timestamp, and field-level diffs without blocking the save operation if email sending is delayed.

**Manual Testing Steps:**

1. Log in to the admin panel with administrative privileges.
2. Navigate to **Admin → Settings** (`/admin/settings`) or **Admin → Discounts** (`/admin/discount`).
3. Change a configuration parameter (e.g., alter the active storewide discount percentage or update a Zoho sync setting).
4. Save the configuration and verify the green success toast.
5. Check the inbox for `info@stoltglobal.com` (or examine backend mail logs in test environment).
6. **Expected:** An administrative alert email is received titled *"Admin Configuration Updated"*.
7. Confirm the email body displays:
   - Admin username/email who performed the change
   - Timestamp (IST)
   - Modified section (e.g., Discounts, Coupons, Zoho)
   - Exact field name, previous value, and new value.

---

## 15. Admin Products Catalog — Collapsible sections to reduce page scrolling

**Module Name:** Admin Products Catalog  

**Type:** Enhancement / UX  

**What Was Wrong / Needed:**  
The Admin Product editor (`/admin/products`) had dozens of fields stacked in a single continuous page (basic details, pricing, copy, size chart, swatches, gallery, SEO, Zoho link). Admins had to scroll excessively up and down to make small edits or upload images.

**Short Description:**  
Restructured the product edit view into clean, collapsible accordion panels (`src/components/admin/AdminPage.tsx`, `AdminPanelSection`, `AdminSectionToggles`, `src/routes/admin.products.tsx`). Core identity fields (Name, SKU, Price, Save button) remain persistently accessible at the top, while Merchandising, Story & Copy, Size Extras, Swatches, and Image Gallery can be collapsed or expanded individually. Includes **"Expand all"** and **"Collapse all"** toggle buttons.

**Manual Testing Steps:**

1. Navigate to **Admin → Products** (`/admin/products`).
2. Click **Edit** on any existing product.
3. **Expected:** The product editor presents organized collapsible sections:
     - Basic Details & Pricing (visible/sticky)
     - Merchandising & Badges (Collapsible)
     - Story & Copy (Collapsible)
     - Size Charts & Options (Collapsible)
     - Image Gallery & Swatches (Collapsible)
  4. Click a section header to collapse it; confirm the section smoothly closes and reduces vertical page height.
  5. Click **"Collapse all"** at the top; confirm all accordion sections close.
  6. Click **"Expand all"**; confirm all sections expand.
  7. Start an image upload inside the Image Gallery, collapse the section, and re-expand; verify ongoing uploads/states remain intact (`forceMount`).

---

## 16. Order Cancellation (Customer UX) — Removed restock text from cancel modal

**Module Name:** Order Cancellation (Customer UX)  

**Type:** Bug / Copy  

**What Was Wrong / Needed:**  
When a customer clicked "Cancel Order" on their account orders page, the confirmation modal dialog included internal warehouse jargon: *"Inventory will be returned to stock"*. This wording was unnecessary for customers and caused confusion regarding warehouse processing.

**Short Description:**  
Cleaned up the cancel confirmation modal in `src/routes/account.orders.$orderNumber.tsx` (commit `b53b6ff`). Removed the line *"Inventory back to stock"* entirely. The confirmation dialog now clearly and concisely focuses on what the customer needs to know: confirming their cancellation request, providing a reason, and warning that the cancellation action cannot be undone.

**Manual Testing Steps:**

1. Log in as a customer with an active, unfulfilled order.
2. Open **Account → Orders** and click on the order.
3. Click the **"Cancel Order"** button.
4. Inspect the confirmation dialog modal text carefully.
5. **Expected:** Dialog prompts for cancellation reason and warns that cancellation cannot be undone.
6. **Expected:** The phrase *"Inventory will be returned to stock"* (or *"Inventory back to stock"*) does **not** appear anywhere in the dialog.
7. Submit the cancellation request; verify normal cancellation flow proceeds.

---

## 17. Customer Management (Admin) — Customer purchase history flag and join segments

**Module Name:** Customer Management (Admin)  

**Type:** Enhancement  

**What Was Wrong / Needed:**  
In the Admin Users/Customers screen, staff could not distinguish customers who had actually placed orders from users who merely registered an account. Staff needed join-date segmentation and a clear boolean indicator (`hasOrdered`) to identify purchasing customers.

**Short Description:**  
Enhanced the customer database page (`src/routes/admin.customers.tsx`, `src/routes/admin.users.tsx`, `user-filters.ts`, `user-join-stats.ts`). Added a boolean `hasOrdered` badge indicating whether a user has placed at least one completed order. Added quick join-date segment filters ("Joined today", "This week", "This month", "Older"), status filters ("Active", "Pending"), and newsletter subscription filters ("Subscribed", "Not subscribed") to segment and analyze the customer base.

**Manual Testing Steps:**

1. Navigate to **Admin → Customers** (`/admin/customers` or `/admin/users`).
2. Inspect the customer listing table.
3. **Expected:** Customers display an **Order History / Purchased** indicator:
   - Users with orders display a badge indicating order activity (`hasOrdered: true`).
   - Newly registered users without purchases display `No orders yet`.
4. Click the join segment pills at the top ("Joined today", "This week", "This month", "Older").
5. **Expected:** Customer list filters to match users registered within the selected time window, updating the segment count badge.
6. Use the **Newsletter** filter dropdown and choose **"Subscribed"**; verify list filters to subscribed users only.
7. Combine search query (name/email/login) with the `hasOrdered` and segment filters; verify responsive pagination.

---

## 18. Admin Products Catalog — Mandatory indicator (*) on product form fields

**Module Name:** Admin Products Catalog  

**Type:** Enhancement / Form Usability  

**What Was Wrong / Needed:**  
On the Admin Product create/edit screen, administrators could not tell which fields were mandatory before attempting to save. Submitting the form with missing fields failed without clear visual indicators highlighting required inputs.

**Short Description:**  
Updated the form field components (`Field` in `src/components/admin/AdminPage.tsx`, `src/routes/admin.products.tsx`). Added a distinct red asterisk (`<span className="text-destructive">*</span>`) next to all required product fields, including **Product Name\***, **Category\***, **SKU\***, and **Fabric type\***. Optional fields (e.g., Details kicker, Sort order, Secondary badge) remain without asterisks.

**Manual Testing Steps:**

1. Navigate to **Admin → Products** (`/admin/products`).
2. Click **New product** (or edit an existing product).
3. Inspect the field labels across the form.
4. **Expected:** The following mandatory fields clearly show a red asterisk:
   - **Name \***
   - **Category \***
   - **SKU \***
   - **Fabric type \***
5. Confirm optional fields (e.g., Slug, Sort order, Units (UOM), Badge, Colors) do not display the red asterisk.
6. Attempt to submit with a mandatory field left blank; verify validation catches the field and guides the user.

---
