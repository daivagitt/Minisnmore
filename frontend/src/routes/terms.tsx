import { createFileRoute } from "@tanstack/react-router";
import { LegalPage } from "@/components/LegalPage";
import { PolicyContent } from "@/components/PolicyContent";

export const Route = createFileRoute("/terms")({
  head: () => ({ meta: [
    { title: "Terms & Conditions · Mini's n More" },
    { name: "description", content: "Terms governing your use of Mini's n More — orders, pricing, IP, liability and Indian governing law." },
  ]}),
  component: TermsPage,
});

function TermsPage() {
  const fallback = (
    <LegalPage eyebrow="Legal" title="Terms &amp; Conditions" lastUpdated="13 May 2026">
      <p>These Terms &amp; Conditions ("Terms") govern your access to and use of the website, mobile experience and related services (the "Services") provided by <strong>Mini's n More</strong> ("we", "our", "us"). By using the Services or placing an order, you agree to these Terms.</p>

      <h2>1. Eligibility</h2>
      <p>You must be at least 18 years of age and capable of entering into a legally binding contract under the Indian Contract Act, 1872 to place an order. By using the Services, you represent that you meet these requirements.</p>

      <h2>2. Account</h2>
      <p>You are responsible for maintaining the confidentiality of your login credentials and for all activities under your account. Notify us immediately of any unauthorised use.</p>

      <h2>3. Products &amp; pricing</h2>
      <ul>
        <li>All prices are in <strong>Indian Rupees (INR / ₹)</strong> and are inclusive of applicable GST unless stated otherwise.</li>
        <li>We strive for accuracy in product descriptions, fabric details, sizing and imagery, but minor variations in colour and weave may occur due to the natural nature of organic cotton and your device's display.</li>
        <li>We reserve the right to correct pricing or descriptive errors at any time prior to order acceptance.</li>
      </ul>

      <h2>4. Order acceptance</h2>
      <p>Your order is an offer to purchase. A binding contract is formed only when we send you an order confirmation email. We reserve the right to refuse or cancel any order at our discretion (e.g. for stock issues, suspected fraud, or pricing errors).</p>

      <h2>5. Payment</h2>
      <p>Payments are processed through our PCI-DSS compliant payment partner (Razorpay). We accept UPI, debit/credit cards, net banking, and select wallets. Cash on Delivery may be available on selected pin codes and order values.</p>

      <h2>6. Cancellation</h2>
      <p>Orders may be cancelled free of charge before they are dispatched. Once dispatched, please contact support for assistance.</p>

      <h2>7. Intellectual property</h2>
      <p>All content on the Services — including text, images, logos, brand names, designs, photographs and product descriptions — is owned by or licensed to Mini's n More and is protected under Indian copyright, trademark and other applicable laws. You may not copy, reproduce, distribute, or create derivative works without our prior written consent.</p>

      <h2>8. User-uploaded content</h2>
      <p>If you upload images (e.g. baby photos for the Virtual Fit tool), you confirm you have the necessary rights and consents. Images are processed locally in your browser unless explicitly saved by you. We do not store such images on our servers without your consent.</p>

      <h2>9. Prohibited use</h2>
      <p>You agree not to: (a) misuse the Services or interfere with their operation; (b) attempt to gain unauthorised access; (c) use automated bots or scrapers without permission; (d) upload unlawful, infringing or harmful content; (e) impersonate any person.</p>

      <h2>10. Disclaimer &amp; limitation of liability</h2>
      <p>The Services are provided on an "as is" and "as available" basis. To the maximum extent permitted by law, our aggregate liability arising out of or related to your use of the Services shall not exceed the amount actually paid by you for the order giving rise to the claim.</p>

      <h2>11. Indemnity</h2>
      <p>You agree to indemnify and hold harmless Mini's n More, its directors, employees and partners from any claim arising out of your breach of these Terms or your violation of any law or third-party rights.</p>

      <h2>12. Force majeure</h2>
      <p>We are not liable for delays or failures caused by events beyond our reasonable control, including natural disasters, strikes, pandemics, war, civil unrest, or government actions.</p>

      <h2>13. Governing law &amp; jurisdiction</h2>
      <p>These Terms are governed by the laws of India. Any disputes shall be subject to the exclusive jurisdiction of the competent courts at <strong>Bengaluru, Karnataka</strong>.</p>

      <h2>14. Changes</h2>
      <p>We may modify these Terms at any time. The revised version becomes effective when posted. Continued use of the Services after changes constitutes acceptance.</p>

      <h2>15. Contact</h2>
      <p>Questions? Write to us at <a href="mailto:hello@minisnmore.in">hello@minisnmore.in</a>.</p>
    </LegalPage>
  );
  return <PolicyContent slug="terms" eyebrow="Legal" defaultTitle="Terms & Conditions" lastUpdated="13 May 2026" fallback={fallback} />;
}
