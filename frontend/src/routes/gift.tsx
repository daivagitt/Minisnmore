import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { useEffect, useMemo, useState } from "react";
import {
  Gift as GiftIcon,
  Check,
  ArrowLeft,
  ArrowRight,
  Baby,
  Sparkles,
  Flower2,
  ShoppingBag,
  type LucideIcon,
} from "lucide-react";
import heroBaby from "@/assets/hero-smiling-baby.jpg";
import { type Product, type AgeRange } from "@/data/products";
import { useProducts, useProductsLoaded } from "@/hooks/useProducts";
import { useShop } from "@/store/shop";
import { toast } from "sonner";
import { getGiftPage } from "@/lib/backend-data";
import { validateGiftStep } from "@/lib/gift-flow";
import {
  resolveGiftPage,
  type GiftFeatureIcon,
  type GiftPageContent,
} from "@/lib/gift-page";

export const Route = createFileRoute("/gift")({
  head: () => ({ meta: [{ title: "Gift · Mini's n More" }] }),
  loader: async () => {
    try {
      return await getGiftPage();
    } catch {
      return {};
    }
  },
  component: Gift,
});

const FEATURE_ICONS: Record<GiftFeatureIcon, LucideIcon> = {
  sparkles: Sparkles,
  flower: Flower2,
  gift: GiftIcon,
};

function Gift() {
  const raw = Route.useLoaderData();
  const content: GiftPageContent = resolveGiftPage(raw.gift_page_json);
  const occasions = content.occasions;
  const budgets = content.budgets;
  const palettes = content.palettes;
  const ageOptions = content.ages;
  const steps = content.steps;
  const heroImage = content.hero_image_url.trim() || heroBaby;

  const defaultBudgetId = budgets[0]?.id ?? "2-5k";
  const preferredBudgetId = budgets.find((b) => b.id === "2-5k")?.id ?? defaultBudgetId;
  const defaultAge: AgeRange = ageOptions.find((a) => a.value === "3-6m")?.value ?? ageOptions[0]?.value ?? "3-6m";

  const [step, setStep] = useState(0);
  const [occ, setOcc] = useState(occasions[0] ?? "");
  const [budId, setBudId] = useState(preferredBudgetId);
  const [age, setAge] = useState<AgeRange>(defaultAge);
  const [palette, setPalette] = useState(palettes[0] ?? "");
  const [recipient, setRecipient] = useState("");
  const [recipientError, setRecipientError] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const navigate = useNavigate();
  const { addToCart, setCartOpen } = useShop();
  const catalog = useProducts();
  const productsLoaded = useProductsLoaded();

  // Keep wizard selections valid if CMS options change after navigation.
  useEffect(() => {
    if (occasions.length && !occasions.includes(occ)) setOcc(occasions[0]);
  }, [occasions, occ]);
  useEffect(() => {
    if (budgets.length && !budgets.some((b) => b.id === budId)) {
      setBudId(budgets.find((b) => b.id === "2-5k")?.id ?? budgets[0].id);
    }
  }, [budgets, budId]);
  useEffect(() => {
    if (palettes.length && !palettes.includes(palette)) setPalette(palettes[0]);
  }, [palettes, palette]);
  useEffect(() => {
    if (ageOptions.length && !ageOptions.some((a) => a.value === age)) {
      setAge(ageOptions.find((a) => a.value === "3-6m")?.value ?? ageOptions[0].value);
    }
  }, [ageOptions, age]);
  useEffect(() => {
    if (step >= steps.length) setStep(Math.max(0, steps.length - 1));
  }, [steps.length, step]);

  const bud = budgets.find((b) => b.id === budId) ?? budgets[0];

  const curated = useMemo<Product[]>(() => {
    if (!bud) return [];
    return catalog
      .filter((p) => p.ages.includes(age))
      .filter((p) => p.price <= bud.max)
      .slice(0, 8);
  }, [age, bud, catalog]);

  const fallbackCurated = curated.length > 0 ? curated : catalog.slice(0, 6);
  const selectedTotal = fallbackCurated
    .filter((p) => selectedIds.includes(p.id))
    .reduce((s, p) => s + p.price, 0);

  const last = step === steps.length - 1;

  const toggleSelect = (id: string) => {
    setSelectedIds((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  };

  const addToCartAndCheckout = () => {
    const picks = fallbackCurated.filter((p) => selectedIds.includes(p.id));
    if (picks.length === 0) {
      toast.error("Pick at least one gift to continue");
      return;
    }
    picks.forEach((p) => addToCart(p, { qty: 1 }));
    toast.success(`Added ${picks.length} gift${picks.length > 1 ? "s" : ""} to cart`);
    setCartOpen(false);
    navigate({ to: "/checkout" });
  };

  const goNext = () => {
    const error = validateGiftStep(step, { recipient, selectedIds });
    if (error) {
      if (step === 1) setRecipientError(error);
      else toast.error(error);
      return;
    }
    setStep((s) => s + 1);
  };

  return (
    <div className="px-4 md:px-6 space-y-10 md:space-y-12">
      <div className="grid lg:grid-cols-[1.15fr_0.85fr] gap-5 md:gap-6 items-stretch">
        <div className="relative overflow-hidden rounded-[1.75rem] md:rounded-[2rem] min-h-[260px] sm:min-h-[320px]">
          <img
            src={heroImage}
            alt={content.hero_image_alt}
            className="absolute inset-0 h-full w-full object-cover object-center"
          />
          <div className="absolute inset-0 bg-gradient-to-r from-background/90 via-background/55 to-background/25" />
          <div className="relative z-10 p-6 md:p-10 max-w-xl">
            <div className="inline-flex items-center gap-2 glass rounded-full px-3 sm:px-4 py-1.5 sm:py-2 text-[10px] sm:text-xs uppercase tracking-[0.22em] text-primary">
              <Baby className="h-3 w-3 sm:h-3.5 sm:w-3.5" /> {content.hero_eyebrow}
            </div>
            <h2 className="font-display text-2xl sm:text-3xl md:text-4xl mt-3 sm:mt-4">{content.hero_title}</h2>
            <p className="mt-2 sm:mt-3 text-sm md:text-base text-foreground/75">{content.hero_subtitle}</p>
          </div>
        </div>
        <div className="glass-strong rounded-[1.75rem] md:rounded-[2rem] p-5 sm:p-6 md:p-8 grid gap-3 content-start">
          {content.features.map((item, i) => {
            const Icon = FEATURE_ICONS[item.icon] ?? GiftIcon;
            return (
              <div key={`feature-${i}`} className="glass rounded-2xl p-4">
                <Icon className="h-5 w-5 text-primary" />
                <div className="font-display text-lg sm:text-xl mt-2 sm:mt-3">{item.title}</div>
                <p className="text-sm text-muted-foreground mt-1.5">{item.body}</p>
              </div>
            );
          })}
        </div>
      </div>

      <motion.form
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        className="glass-strong rounded-[1.75rem] md:rounded-[2rem] p-5 sm:p-7 md:p-8 space-y-6"
        onSubmit={(e) => e.preventDefault()}
      >
        <div className="flex items-center gap-2 overflow-x-auto pb-1">
          {steps.map((s, i) => (
            <div key={`${s}-${i}`} className="flex items-center gap-2 flex-1 min-w-fit">
              <div
                className={`h-7 w-7 sm:h-8 sm:w-8 grid place-items-center rounded-full text-[11px] sm:text-xs font-medium shrink-0 ${
                  i < step
                    ? "bg-primary text-primary-foreground"
                    : i === step
                      ? "bg-primary text-primary-foreground ring-4 ring-primary/20"
                      : "glass text-muted-foreground"
                }`}
              >
                {i < step ? <Check className="h-3.5 w-3.5" /> : i + 1}
              </div>
              {i < steps.length - 1 && (
                <div className={`h-px flex-1 min-w-[16px] ${i < step ? "bg-primary" : "bg-foreground/15"}`} />
              )}
            </div>
          ))}
        </div>
        <div className="text-[10px] sm:text-xs uppercase tracking-[0.28em] text-primary">
          Step {step + 1} of {steps.length} — {steps[step]}
        </div>

        <motion.div
          key={step}
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.35 }}
          className="min-h-[220px] space-y-5"
        >
          {step === 0 && (
            <>
              <Group label="Occasion" options={occasions} value={occ} onChange={setOcc} />
              <Group
                label="Budget"
                options={budgets.map((b) => b.label)}
                value={bud?.label ?? ""}
                onChange={(label) => {
                  const m = budgets.find((b) => b.label === label);
                  if (m) setBudId(m.id);
                }}
              />
            </>
          )}
          {step === 1 && (
            <>
              <Group
                label="Baby's age"
                options={ageOptions.map((a) => a.label)}
                value={ageOptions.find((a) => a.value === age)?.label ?? ""}
                onChange={(label) => {
                  const m = ageOptions.find((a) => a.label === label);
                  if (m) setAge(m.value);
                }}
              />
              <Field label="Recipient's name">
                <input
                  value={recipient}
                  onChange={(e) => {
                    setRecipient(e.target.value);
                    if (recipientError) setRecipientError(null);
                  }}
                  onBlur={() => {
                    if (!recipient.trim()) setRecipientError("Recipient name is required");
                  }}
                  aria-invalid={!!recipientError}
                  aria-describedby={recipientError ? "gift-recipient-error" : undefined}
                  className={`glass w-full rounded-full px-4 py-3 text-sm bg-transparent outline-none focus:ring-2 ${
                    recipientError
                      ? "ring-2 ring-destructive/50 focus:ring-destructive/50"
                      : "focus:ring-primary/40"
                  }`}
                  placeholder="e.g. Aanya's mum"
                />
                {recipientError && (
                  <p id="gift-recipient-error" className="mt-1 text-xs text-destructive">
                    {recipientError}
                  </p>
                )}
              </Field>
            </>
          )}
          {step === 2 && <Group label="Palette" options={palettes} value={palette} onChange={setPalette} />}
          {step === 3 && (
            <div className="space-y-4">
              <div className="flex flex-wrap items-end justify-between gap-2">
                <div>
                  <div className="text-xs uppercase tracking-wider text-muted-foreground">
                    Curated for {ageOptions.find((a) => a.value === age)?.label} · {bud?.label}
                  </div>
                  <div className="font-display text-xl sm:text-2xl mt-1">Choose your gifts</div>
                </div>
                {selectedIds.length > 0 && (
                  <div className="text-sm">
                    <span className="text-muted-foreground">Total </span>
                    <span className="font-display text-lg">₹{selectedTotal.toLocaleString("en-IN")}</span>
                  </div>
                )}
              </div>
              {!productsLoaded && (
                <div className="glass rounded-2xl p-6 text-sm text-muted-foreground">Loading gifts…</div>
              )}
              {productsLoaded && catalog.length === 0 && (
                <div className="glass rounded-2xl p-6 text-sm text-muted-foreground">
                  No products are available to gift right now.
                </div>
              )}
              {productsLoaded && catalog.length > 0 && curated.length === 0 && (
                <div className="glass rounded-2xl p-3 text-xs text-muted-foreground">
                  No exact matches in that budget — showing other pieces from the shop instead.
                </div>
              )}
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 sm:gap-4">
                {fallbackCurated.map((p) => {
                  const sel = selectedIds.includes(p.id);
                  return (
                    <button
                      type="button"
                      key={p.id}
                      onClick={() => toggleSelect(p.id)}
                      className={`text-left glass rounded-2xl overflow-hidden hover-lift relative transition ${
                        sel ? "ring-2 ring-primary" : ""
                      }`}
                    >
                      <div className="aspect-square overflow-hidden">
                        <img src={p.image} alt={p.name} className="h-full w-full object-cover" loading="lazy" />
                      </div>
                      <div className="p-3">
                        <div className="text-xs sm:text-sm font-medium line-clamp-1">{p.name}</div>
                        <div className="text-xs text-muted-foreground mt-0.5">
                          ₹{p.price.toLocaleString("en-IN")}
                        </div>
                      </div>
                      {sel && (
                        <div className="absolute top-2 right-2 h-6 w-6 grid place-items-center rounded-full bg-primary text-primary-foreground">
                          <Check className="h-3.5 w-3.5" />
                        </div>
                      )}
                    </button>
                  );
                })}
              </div>
            </div>
          )}
          {step === 4 && (
            <Field label="Your handwritten note">
              <textarea
                value={note}
                onChange={(e) => setNote(e.target.value)}
                className="glass w-full rounded-3xl px-4 py-3 text-sm bg-transparent outline-none min-h-[140px] focus:ring-2 focus:ring-primary/40"
                placeholder="We'll handwrite this on cream card stock."
              />
            </Field>
          )}
        </motion.div>

        <div className="flex items-center justify-between pt-2 gap-2 flex-wrap">
          <button
            type="button"
            disabled={step === 0}
            onClick={() => setStep((s) => s - 1)}
            className="rounded-full glass px-4 py-2.5 text-sm inline-flex items-center gap-2 disabled:opacity-40"
          >
            <ArrowLeft className="h-4 w-4" /> Back
          </button>
          {!last ? (
            <button
              type="button"
              onClick={goNext}
              className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm inline-flex items-center gap-2 hover:shadow-glow transition"
            >
              Next <ArrowRight className="h-4 w-4" />
            </button>
          ) : (
            <button
              type="button"
              onClick={addToCartAndCheckout}
              className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm inline-flex items-center gap-2 hover:shadow-glow transition"
            >
              <ShoppingBag className="h-4 w-4" />
              Add {selectedIds.length || ""} gift{selectedIds.length === 1 ? "" : "s"} & checkout
            </button>
          )}
        </div>
      </motion.form>

      <div className="glass-strong rounded-[1.75rem] md:rounded-[2rem] p-6 sm:p-8 grid sm:grid-cols-[1fr_1fr] gap-6">
        <div>
          <GiftIcon className="h-7 w-7 sm:h-8 sm:w-8 text-primary" />
          <h2 className="font-display text-2xl sm:text-3xl mt-2">{content.inside_title}</h2>
          <ul className="mt-3 space-y-2 text-sm text-muted-foreground">
            {content.inside_bullets.map((bullet, i) => (
              <li key={`inside-${i}`}>· {bullet}</li>
            ))}
          </ul>
        </div>
        <div className="space-y-3">
          <div className="glass rounded-2xl p-4 text-sm">{content.highlight_line}</div>
          <div className="glass rounded-2xl p-4 text-xs space-y-1.5">
            <div className="uppercase tracking-wider text-muted-foreground">Your brief so far</div>
            <div>
              <b>{occ}</b> · {bud?.label}
            </div>
            <div>
              Baby {ageOptions.find((a) => a.value === age)?.label} · {palette}
            </div>
            {recipient && <div>For {recipient}</div>}
            {selectedIds.length > 0 && (
              <div>
                {selectedIds.length} piece(s) · ₹{selectedTotal.toLocaleString("en-IN")}
              </div>
            )}
          </div>
          <Link to="/shop" className="block text-xs text-primary story-link">
            {content.shop_link_label}
          </Link>
        </div>
      </div>
    </div>
  );
}

function Group({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: readonly string[];
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div>
      <div className="text-xs uppercase tracking-wider text-muted-foreground mb-2">{label}</div>
      <div className="flex flex-wrap gap-2">
        {options.map((o, i) => (
          <button
            type="button"
            key={`${label}-${i}-${o}`}
            onClick={() => onChange(o)}
            className={`rounded-full px-3.5 py-2 text-xs transition ${
              value === o ? "bg-primary text-primary-foreground" : "glass hover:bg-white/60"
            }`}
          >
            {o}
          </button>
        ))}
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-xs uppercase tracking-wider text-muted-foreground mb-2">{label}</div>
      {children}
    </div>
  );
}
