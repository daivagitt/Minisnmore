import { createFileRoute } from "@tanstack/react-router";
import { Loader2, Search, Sparkles, ShieldCheck, Gift } from "lucide-react";
import { useEffect, useState } from "react";
import babyNewborn from "@/assets/cat-newborn.webp";
import babyInfant from "@/assets/cat-infant.webp";
import babyToddler from "@/assets/cat-toddler.webp";
import lifestyle from "@/assets/lifestyle-mother.webp";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { useOrderTrackingPoll } from "@/hooks/useOrderTrackingPoll";
import { toast } from "sonner";
import { OrderTrackingCard } from "@/components/OrderTrackingCard";
import { TrackingRefreshHint } from "@/components/TrackingRefreshHint";
import { StorefrontPageHeader } from "@/components/StorefrontPageHeader";

export const Route = createFileRoute("/track")({
  validateSearch: (search: Record<string, unknown>) => ({
    order: typeof search.order === "string" ? search.order.trim() : "",
  }),
  head: () => ({ meta: [{ title: "Track Order · Mini's n More" }] }),
  component: Track,
});

function Track() {
  const { order: orderFromUrl } = Route.useSearch();
  const [code, setCode] = useState(orderFromUrl);
  const [activeOrderNumber, setActiveOrderNumber] = useState<string | null>(
    orderFromUrl ? orderFromUrl.toUpperCase() : null,
  );
  const settings = useSiteSettings();
  const { order: trackedOrder, loading, error, lastUpdated, polling } = useOrderTrackingPoll(
    activeOrderNumber,
    "public",
  );

  const handleTrack = () => {
    const next = code.trim();
    if (!next) {
      toast.error("Enter an order ID to track");
      return;
    }
    setActiveOrderNumber(next.toUpperCase());
  };

  useEffect(() => {
    if (!orderFromUrl) return;
    setCode(orderFromUrl);
    setActiveOrderNumber(orderFromUrl.toUpperCase());
  }, [orderFromUrl]);

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
          <input
            value={code}
            onChange={(e) => setCode(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleTrack();
            }}
            className="flex-1 bg-transparent px-4 py-2.5 text-sm outline-none"
            placeholder="Order ID"
          />
          <button
            type="button"
            onClick={handleTrack}
            disabled={loading && !trackedOrder}
            className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm flex items-center gap-2 disabled:opacity-60 disabled:pointer-events-none"
          >
            {loading && !trackedOrder ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />} Track
          </button>
        </div>
      </StorefrontPageHeader>

      <div className="glass-strong rounded-[2rem] p-8">
        {!trackedOrder && !error && !loading && (
          <div className="text-sm text-muted-foreground">
            Enter your order number and tap Track to see the latest delivery timeline.
          </div>
        )}
        {loading && !trackedOrder && (
          <div className="grid place-items-center py-8">
            <Loader2 className="h-6 w-6 animate-spin text-primary" />
          </div>
        )}
        {error && <div className="text-sm text-destructive">{error}</div>}
        {trackedOrder && (
          <>
            <OrderTrackingCard order={trackedOrder} />
            <TrackingRefreshHint lastUpdated={lastUpdated} polling={polling} />
          </>
        )}
      </div>

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
