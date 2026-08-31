import { createFileRoute } from "@tanstack/react-router";
import { LegalPage } from "@/components/LegalPage";
import { PolicyContent } from "@/components/PolicyContent";

export const Route = createFileRoute("/privacy")({
  head: () => ({ meta: [
    { title: "Privacy Policy · Mini's n More" },
    { name: "description", content: "How Mini's n More collects, uses and protects your personal data under India's DPDP Act 2023." },
  ]}),
  component: PrivacyPage,
});

function PrivacyPage() {
  const fallback = (
    <LegalPage eyebrow="Legal" title="Privacy Policy" lastUpdated="13 May 2026">
      <p>This Privacy Policy explains how <strong>Mini's n More</strong> ("we", "our", "us") collects, uses, shares and protects information about you when you use our website, mobile experience and related services (the "Services"). We are committed to handling your data responsibly and in compliance with India's <strong>Digital Personal Data Protection Act, 2023 (DPDP Act)</strong> and applicable rules made thereunder.</p>

      <h2>1. Who we are</h2>
      <p>Mini's n More is a Direct-to-Consumer baby apparel brand operating in India. For the purposes of the DPDP Act, we are the <em>Data Fiduciary</em> in respect of personal data we collect from you.</p>

      <h2>2. Information we collect</h2>
      <ul>
        <li><strong>Identity &amp; contact data:</strong> name, email, phone number, billing and shipping address.</li>
        <li><strong>Order data:</strong> products purchased, sizes, baby age range you select, gift notes, order history.</li>
        <li><strong>Payment data:</strong> processed by our PCI-DSS compliant payment partner (Razorpay). We do not store full card numbers, CVV or UPI PINs on our servers.</li>
        <li><strong>Account data:</strong> login credentials, profile details, wishlist, preferences.</li>
        <li><strong>Device &amp; usage data:</strong> IP address, browser type, pages viewed, referring URL, cookie identifiers.</li>
        <li><strong>Communication data:</strong> messages you send us via email, WhatsApp, support forms.</li>
      </ul>

      <h2>3. How we use your data</h2>
      <ul>
        <li>To process and fulfil your orders, including shipping and customer support.</li>
        <li>To provide customer support and respond to your queries.</li>
        <li>To personalise your shopping experience and recommend products suited to your baby's age.</li>
        <li>To send transactional communications (order confirmations, shipping updates).</li>
        <li>With your consent, to send marketing emails and newsletters — you can opt out at any time.</li>
        <li>To detect, prevent and address fraud, security and technical issues.</li>
        <li>To comply with legal and regulatory obligations.</li>
      </ul>

      <h2>4. Lawful basis</h2>
      <p>We process your personal data on the basis of your <strong>consent</strong> (collected at signup and at relevant touchpoints), to perform a contract with you (order fulfilment), and to comply with our legal obligations.</p>

      <h2>5. Sharing of personal data</h2>
      <p>We share information only with trusted partners who help us run our business, under contractual confidentiality obligations:</p>
      <ul>
        <li><strong>Payments:</strong> Razorpay Software Pvt. Ltd.</li>
        <li><strong>Logistics:</strong> shipping carriers we use to deliver your order.</li>
        <li><strong>Order management &amp; inventory:</strong> Zoho Inventory.</li>
        <li><strong>Hosting &amp; backend infrastructure:</strong> our managed cloud provider, hosted within India / EU regions.</li>
        <li><strong>Analytics &amp; communications:</strong> service providers that help us understand site usage and send transactional messages.</li>
      </ul>
      <p>We do <strong>not</strong> sell your personal data to third parties.</p>

      <h2>6. Cookies</h2>
      <p>We use first-party and limited third-party cookies for cart persistence, login sessions, analytics and to remember your preferences. You can control cookies through your browser settings.</p>

      <h2>7. Data retention</h2>
      <p>We retain personal data only for as long as necessary to fulfil the purposes for which it was collected, including legal, tax and accounting requirements (typically up to 8 years for invoices under Indian tax law). Account data is retained for the life of your account; you may request deletion at any time.</p>

      <h2>8. Your rights</h2>
      <p>Under the DPDP Act, you have the right to:</p>
      <ul>
        <li>Access and obtain a copy of your personal data.</li>
        <li>Correct or update inaccurate personal data.</li>
        <li>Erase your personal data (subject to legal retention).</li>
        <li>Withdraw consent at any time.</li>
        <li>Nominate another individual to exercise your rights in case of incapacity or death.</li>
        <li>Lodge a grievance with our Grievance Officer (below) and, if unresolved, with the Data Protection Board of India.</li>
      </ul>

      <h2>9. Children's data</h2>
      <p>Our Services are intended for adults shopping for infants and children. We do not knowingly collect personal data of children for direct use; "baby age" data you provide is used solely to recommend the right size.</p>

      <h2>10. Security</h2>
      <p>We implement reasonable technical and organisational measures including encryption in transit (TLS), access controls, role-based permissions and regular reviews. No system is perfectly secure; please use a strong unique password.</p>

      <h2>11. Grievance Officer</h2>
      <p>For any privacy-related grievance, please contact:</p>
      <ul>
        <li><strong>Email:</strong> grievance@minisnmore.in</li>
        <li><strong>Response timeline:</strong> within 7 working days.</li>
      </ul>

      <h2>12. Changes to this policy</h2>
      <p>We may update this Privacy Policy from time to time. The "Last updated" date above reflects the latest revision. Material changes will be notified via email or a prominent site notice.</p>

      <h2>13. Contact</h2>
      <p>Questions? Write to us at <a href="mailto:hello@minisnmore.in">hello@minisnmore.in</a>.</p>
    </LegalPage>
  );
  return <PolicyContent slug="privacy" eyebrow="Legal" defaultTitle="Privacy Policy" lastUpdated="13 May 2026" fallback={fallback} />;
}
