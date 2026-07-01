import { createFileRoute } from "@tanstack/react-router";
import { Loader2, Search, Sparkles, ShieldCheck, Gift } from "lucide-react";
import { useState } from "react";
import babyNewborn from "@/assets/cat-newborn.webp";
import babyInfant from "@/assets/cat-infant.webp";
import babyToddler from "@/assets/cat-toddler.webp";
import lifestyle from "@/assets/lifestyle-mother.webp";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { toast } from "sonner";
import { OrderTrackingCard } from "@/components/OrderTrackingCard";
import { StorefrontPageHeader } from "@/components/StorefrontPageHeader";
import { trackOrderPublic } from "@/lib/orders.functions";

export const Route = createFileRoute("/track")({
  head: () => ({ meta: [{ title: "Track Order · Mini's n More" }] }),
  component: Track,
});

function Track() {
  const [code, setCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [trackedOrder, setTrackedOrder] = useState<any | null>(null);
  const settings = useSiteSettings();
  const handleTrack = async () => {
    if (loading) return;
    const next = code.trim();
    if (!next) {
      toast.error("Enter an order ID to track");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await trackOrderPublic({ data: { orderNumber: next } });
      setTrackedOrder(response.order ?? null);
      setCode(next.toUpperCase());
      if (!response.order) {
        setError("No tracking details found for that order.");
      }
    } catch (e) {
      const message = e instanceof Error ? e.message : "Could not load tracking details.";
      setTrackedOrder(null);
      setError(message);
    } finally {
      setLoading(false);
    }
  };
  return (
    <div className="px-4 md:px-6 max-w-7xl mx-auto grid lg:grid-cols-[1fr_280px] gap-6 lg:gap-10">
      <div className="space-y-10 min-w-0">
      <StorefrontPageHeader
        eyebrow="Your order"
        heading="Track your order"
        subtitle="Hand-wrapped, tissue-tucked, and on its way. Pop in your order number to follow the journey."
        className="mb-0"
        animate={false}
      >
        <div className="mt-4 glass rounded-full p-1.5 flex items-center">
          <input value={code} onChange={(e) => setCode(e.target.value)}
            className="flex-1 bg-transparent px-4 py-2.5 text-sm outline-none" placeholder="Order ID" />
          <button
            type="button"
            onClick={handleTrack}
            disabled={loading}
            className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm flex items-center gap-2 disabled:opacity-60 disabled:pointer-events-none"
          >
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />} Track
          </button>
        </div>
      </StorefrontPageHeader>

      <div className="glass-strong rounded-[2rem] p-8">
        {!trackedOrder && !error && (
          <div className="text-sm text-muted-foreground">
            Enter your order number and tap Track to see the latest delivery timeline.
          </div>
        )}
        {error && <div className="text-sm text-destructive">{error}</div>}
        {trackedOrder && <OrderTrackingCard order={trackedOrder} />}
      </div>

      {/* What to expect — featured cards with imagery */}
      <div>
        <div className="text-xs uppercase tracking-[0.28em] text-primary mb-3">What to expect</div>
        <div className="grid sm:grid-cols-3 gap-4">
          {[
            { icon: Sparkles, title: "Hand-wrapped", desc: "Tissue, twine, a tiny note.", img: babyNewborn },
            { icon: ShieldCheck, title: "Quality-checked", desc: "Inspected before it leaves us.", img: babyInfant },
            { icon: Gift, title: "Gift-ready", desc: "Add a free note at checkout.", img: babyToddler },
          ].map((c) => (
            <div key={c.title} className="glass-strong rounded-3xl overflow-hidden">
              <img src={c.img} alt="" loading="lazy" className="w-full h-32 object-cover" />
              <div className="p-4">
                <div className="flex items-center gap-2"><c.icon className="h-4 w-4 text-primary" /><span className="font-medium text-sm">{c.title}</span></div>
                <p className="text-xs text-muted-foreground mt-1.5">{c.desc}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
      </div>

      {/* Side rail with imagery (desktop only) */}
      <aside className="hidden lg:flex flex-col gap-4">
        <div className="glass-strong rounded-3xl overflow-hidden">
          <img src={lifestyle} alt="" loading="lazy" className="w-full h-72 object-cover" />
          <div className="p-4">
            <div className="text-xs uppercase tracking-wider text-primary">Slow shipping</div>
            <div className="font-display text-lg mt-0.5">From our hands to yours</div>
            <p className="text-xs text-muted-foreground mt-2 leading-relaxed">Each order is folded by a small team in Bengaluru. We slow down so you don't have to.</p>
          </div>
        </div>
        <div className="glass rounded-3xl p-5">
          <div className="text-xs uppercase tracking-wider text-muted-foreground">Need help?</div>
          <div className="text-sm mt-1">{settings.contact_email || "care@minisnmore.com"}</div>
          <div className="text-xs text-muted-foreground mt-1">Mon–Sat · 10am–6pm IST</div>
        </div>
      </aside>
    </div>
  );
}
