import { createFileRoute } from "@tanstack/react-router";
import { LegalPage } from "@/components/LegalPage";
import { PolicyContent } from "@/components/PolicyContent";
import { useSiteSettings } from "@/hooks/useSiteSettings";

export const Route = createFileRoute("/shipping")({
  head: () => ({ meta: [
    { title: "Shipping · Mini's n More" },
    { name: "description", content: "Dispatch SLAs, delivery windows, free shipping, and order tracking." },
  ]}),
  component: ShippingPage,
});

function ShippingPage() {
  const { cod_fee_inr: codFee } = useSiteSettings();
  const fallback = (
    <LegalPage eyebrow="Care" title="Shipping" lastUpdated="13 May 2026">
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
        <li>Orders below ₹999: a flat shipping fee of <strong>₹99</strong> applies.</li>
        <li><strong>Cash on Delivery</strong> (where available): {codFee > 0 ? <>an additional GST-inclusive handling fee of <strong>₹{codFee.toLocaleString("en-IN")}</strong> applies.</> : <>no additional handling fee applies.</>}</li>
      </ul>

      <h2>4. Order tracking</h2>
      <p>Track your order in real time at <a href="/track">/track</a> or from your <a href="/account/orders">account</a>.</p>

      <h2>5. Damaged or incorrect items</h2>
      <p>If your order arrives damaged, defective, or contains an incorrect item, please notify us within <strong>48 hours</strong> of delivery with a photograph.</p>

      <h2>6. Care guidance</h2>
      <p>To help pieces last longer, we include wash-care guidance with every order. Use mild detergent, avoid harsh bleach and dry in shade to preserve softness and colour.</p>

      <h2>7. International shipping</h2>
      <p>Currently we ship within India only. Watch this space — international shipping is on the roadmap.</p>

      <h2>8. Contact</h2>
      <p>Need help? Write to us at <a href="mailto:care@minisnmore.in">care@minisnmore.in</a> or WhatsApp us — we typically respond within 24 hours on working days.</p>
    </LegalPage>
  );
  return <PolicyContent slug="shipping" eyebrow="Care" defaultTitle="Shipping" lastUpdated="13 May 2026" fallback={fallback} />;
}
