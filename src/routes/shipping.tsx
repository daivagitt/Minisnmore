import { createFileRoute } from "@tanstack/react-router";
import { LegalPage } from "@/components/LegalPage";
import { PolicyContent } from "@/components/PolicyContent";

export const Route = createFileRoute("/shipping")({
  head: () => ({ meta: [
    { title: "Shipping & Returns · Mini's n More" },
    { name: "description", content: "Dispatch SLAs, delivery windows, free shipping, returns and refunds." },
  ]}),
  component: ShippingPage,
});

function ShippingPage() {
  const fallback = (
    <LegalPage eyebrow="Care" title="Shipping &amp; Returns" lastUpdated="13 May 2026">
      <p>We hand-wrap every order and ship pan-India. Here's what to expect.</p>

      <h2>1. Dispatch timeline</h2>
      <ul>
        <li>Orders placed before <strong>2:00 PM IST</strong> on a working day are processed the same day.</li>
        <li>Standard dispatch: <strong>1–3 business days</strong> from order confirmation.</li>
        <li>Subscription boxes ship on a fixed monthly schedule — see your subscription page for the next ship date.</li>
      </ul>

      <h2>2. Delivery windows</h2>
      <ul>
        <li><strong>Metro cities</strong> (Bengaluru, Mumbai, Delhi NCR, Hyderabad, Chennai, Kolkata, Pune): 2–4 business days after dispatch.</li>
        <li><strong>Tier-2 cities</strong>: 3–6 business days.</li>
        <li><strong>Other pin codes &amp; remote locations</strong>: 5–8 business days.</li>
      </ul>
      <p>You will receive a tracking link via email and SMS once your order is dispatched.</p>

      <h2>3. Shipping charges</h2>
      <ul>
        <li><strong>Free shipping</strong> on all prepaid orders above <strong>₹999</strong>.</li>
        <li>Orders below ₹999: a flat shipping fee of <strong>₹69</strong> applies.</li>
        <li><strong>Cash on Delivery</strong> (where available): an additional handling fee of <strong>₹49</strong> applies.</li>
      </ul>

      <h2>4. Order tracking</h2>
      <p>Track your order in real time at <a href="/track">/track</a> or from your <a href="/account/orders">account</a>.</p>

      <h2>5. Returns</h2>
      <ul>
        <li>We accept returns within <strong>7 days</strong> of delivery for unused, unwashed items in original packaging with tags intact.</li>
        <li>For hygiene reasons, the following are <strong>non-returnable</strong>: innerwear, bibs, swaddles once unsealed, gift cards, and items marked "final sale".</li>
        <li>To initiate a return, visit <a href="/account/returns">My account → Returns</a> or write to <a href="mailto:care@minisnmore.in">care@minisnmore.in</a> within the return window.</li>
        <li>A reverse pickup is arranged free of cost on serviceable pin codes; for non-serviceable areas, you may self-ship and we will refund shipping up to ₹100.</li>
      </ul>

      <h2>6. Damaged or incorrect items</h2>
      <p>If your order arrives damaged, defective, or contains an incorrect item, please notify us within <strong>48 hours</strong> of delivery with a photograph. We will arrange a free replacement or full refund.</p>

      <h2>7. Refunds</h2>
      <ul>
        <li>Refunds are processed within <strong>2 business days</strong> of receiving the returned item at our warehouse.</li>
        <li>Credit reflects in your source account within <strong>5–7 business days</strong> depending on your bank.</li>
        <li>For COD orders, refunds are issued via UPI/bank transfer to a verified account.</li>
      </ul>

      <h2>8. Exchanges</h2>
      <p>We offer free size exchanges within the 7-day window, subject to stock availability. If the desired size is unavailable, we will issue a refund or store credit.</p>

      <h2>9. Care guidance</h2>
      <p>To help pieces last longer, we include wash-care guidance with every order. Use mild detergent, avoid harsh bleach and dry in shade to preserve softness and colour.</p>

      <h2>10. International shipping</h2>
      <p>Currently we ship within India only. Watch this space — international shipping is on the roadmap.</p>

      <h2>11. Contact</h2>
      <p>Need help? Write to us at <a href="mailto:care@minisnmore.in">care@minisnmore.in</a> or WhatsApp us — we typically respond within 24 hours on working days.</p>
    </LegalPage>
  );
  return <PolicyContent slug="shipping" eyebrow="Care" defaultTitle="Shipping & Returns" lastUpdated="13 May 2026" fallback={fallback} />;
}