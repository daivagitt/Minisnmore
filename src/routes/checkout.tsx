import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { useShop } from "@/store/shop";
import { useAuth } from "@/hooks/useAuth";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { Check, ChevronLeft, Heart, Loader2, Smartphone, CreditCard, Building2, Wallet, Banknote, Shield, MapPin, Plus, Zap, Minus, X, ShoppingBag } from "lucide-react";
import { toast } from "sonner";
import { createOrder, verifyPayment, getMyProfile, updateMyProfile, getFirstOrderEligibility } from "@/lib/orders.functions";
import { validateCoupon } from "@/lib/coupons.functions";
import { commerceFromSiteSettings } from "@/lib/commerce";
import { orderTotals } from "@/lib/money";

export const Route = createFileRoute("/checkout")({
  head: () => ({ meta: [{ title: "Checkout · Mini's n More" }] }),
  component: Checkout,
});

const steps = ["Details", "Shipping", "Payment", "Review"];

declare global { interface Window { Razorpay: any } }

type SavedAddress = {
  id: string;
  label?: string;
  full_name: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  pincode: string;
  is_default?: boolean;
};

function loadRazorpay(): Promise<boolean> {
  return new Promise((resolve) => {
    if (typeof window === "undefined") return resolve(false);
    if (window.Razorpay) return resolve(true);
    const s = document.createElement("script");
    s.src = "https://checkout.razorpay.com/v1/checkout.js";
    s.onload = () => resolve(true);
    s.onerror = () => resolve(false);
    document.body.appendChild(s);
  });
}

function Checkout() {
  const { cart, cartTotal, clearCart, updateQty, removeFromCart } = useShop();
  const { user, loading: authLoading } = useAuth();
  const settings = useSiteSettings();
  const nav = useNavigate();
  const [step, setStep] = useState(0);
  const [done, setDone] = useState<{ orderNumber: string } | null>(null);
  const [busy, setBusy] = useState(false);

  // form state
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [babyName, setBabyName] = useState("");
  const [line1, setLine1] = useState("");
  const [line2, setLine2] = useState("");
  const [pin, setPin] = useState("");
  const [city, setCity] = useState("");
  const [state, setStateName] = useState("");
  const [pinLoading, setPinLoading] = useState(false);
  const [donation, setDonation] = useState<number>(0);
  const [coupon, setCoupon] = useState("");
  const [couponOff, setCouponOff] = useState(0);
  const [shipMode, setShipMode] = useState<"std" | "exp">("std");
  const [payMethod, setPayMethod] = useState<"upi" | "card" | "netbanking" | "wallet">("upi");
  const [upiId, setUpiId] = useState("");
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([]);
  const [selectedAddrId, setSelectedAddrId] = useState<string>("");
  const [saveAddress, setSaveAddress] = useState(true);
  const [profileLoaded, setProfileLoaded] = useState(false);
  const [firstOrder, setFirstOrder] = useState<{ eligible: boolean; pct: number } | null>(null);

  useEffect(() => {
    if (!authLoading && !user) {
      nav({ to: "/login", search: { redirect: "/checkout" } });
    }
  }, [user, authLoading, nav]);

  useEffect(() => {
    if (user?.email && !email) setEmail(user.email);
  }, [user, email]);

  // Auto-populate profile (name, phone) from server
  useEffect(() => {
    if (!user || profileLoaded) return;
    getMyProfile()
      .then((r) => {
        if (r.profile?.full_name && !fullName) setFullName(r.profile.full_name);
        if (r.profile?.phone && !phone) setPhone(r.profile.phone);
      })
      .catch(() => {})
      .finally(() => setProfileLoaded(true));
  }, [user, profileLoaded, fullName, phone]);

  useEffect(() => {
    if (!user) return;
    getFirstOrderEligibility()
      .then((r) => setFirstOrder({ eligible: r.eligible, pct: r.discount_pct }))
      .catch(() => {});
  }, [user]);

  // Load saved addresses + last preferences from localStorage
  useEffect(() => {
    if (!user) return;
    try {
      const raw = localStorage.getItem(`mnm_addresses_${user.id}`);
      const list: SavedAddress[] = raw ? JSON.parse(raw) : [];
      setSavedAddresses(list);
      const def = list.find((a) => a.is_default) ?? list[0];
      if (def) {
        setSelectedAddrId(def.id);
        applyAddress(def);
        setSaveAddress(false);
      }
      const lastPay = localStorage.getItem("mnm_last_pay") as any;
      if (lastPay && ["upi", "card", "netbanking", "wallet"].includes(lastPay)) setPayMethod(lastPay);
      const lastShip = localStorage.getItem("mnm_last_ship") as any;
      if (lastShip === "std" || lastShip === "exp") setShipMode(lastShip);
    } catch {}
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  const applyAddress = (a: SavedAddress) => {
    if (a.full_name) setFullName(a.full_name);
    if (a.phone) setPhone(a.phone);
    setLine1(a.line1 || "");
    setLine2(a.line2 || "");
    setPin(a.pincode || "");
    setCity(a.city || "");
    setStateName(a.state || "");
  };

  const onPickSaved = (id: string) => {
    setSelectedAddrId(id);
    if (id === "__new") {
      setLine1(""); setLine2(""); setPin(""); setCity(""); setStateName("");
      setSaveAddress(true);
      return;
    }
    const a = savedAddresses.find((x) => x.id === id);
    if (a) { applyAddress(a); setSaveAddress(false); }
  };

  const persistAddressIfNeeded = () => {
    if (!user || !saveAddress) return;
    try {
      const key = `mnm_addresses_${user.id}`;
      const raw = localStorage.getItem(key);
      const list: SavedAddress[] = raw ? JSON.parse(raw) : [];
      const dup = list.find((a) => a.line1 === line1 && a.pincode === pin && a.city === city);
      if (dup) return;
      const next: SavedAddress = {
        id: crypto.randomUUID(),
        label: "Home",
        full_name: fullName, phone, line1, line2, city, state, pincode: pin,
        is_default: list.length === 0,
      };
      localStorage.setItem(key, JSON.stringify([...list, next]));
      setSavedAddresses([...list, next]);
    } catch {}
  };

  const applyCoupon = async () => {
    const raw = coupon.trim().toUpperCase();
    if (!raw) return;
    try {
      const res = await validateCoupon({ data: { code: raw, subtotal: cartTotal } });
      if (!res.ok) { setCouponOff(0); toast.error(res.error); return; }
      setCoupon(res.code);
      setCouponOff(res.discount);
      toast.success("Coupon applied");
    } catch {
      setCouponOff(0);
      toast.error("Could not validate coupon");
    }
  };

  const commerce = commerceFromSiteSettings(settings);
  const firstOrderOff = firstOrder?.eligible
    ? Math.round((cartTotal * (firstOrder.pct || 10)) / 100)
    : 0;
  // Server uses the larger of coupon vs first-order (when a coupon is present).
  const effectiveDiscount = couponOff > 0 ? Math.max(couponOff, firstOrderOff) : firstOrderOff;
  const totals = orderTotals({
    subtotal: cartTotal,
    discount: effectiveDiscount,
    donation,
    shipMode,
    commerce,
  });
  const shipping = totals.shipping;
  const gst = totals.gst;
  const grand = totals.grand;

  const onPinChange = async (v: string) => {
    const clean = v.replace(/\D/g, "").slice(0, 6);
    setPin(clean);
    if (clean.length === 6) {
      setPinLoading(true);
      try {
        const r = await fetch(`https://api.postalpincode.in/pincode/${clean}`);
        const d = await r.json();
        const po = d?.[0]?.PostOffice?.[0];
        if (po) { setCity(po.District ?? ""); setStateName(po.State ?? ""); }
        else toast.error("Pincode not found");
      } catch { toast.error("Couldn't fetch pincode"); }
      finally { setPinLoading(false); }
    }
  };

  const next = () => {
    if (step === 0) {
      if (!fullName.trim()) return toast.error("Please enter your full name");
      if (!/^\S+@\S+\.\S+$/.test(email)) return toast.error("Please enter a valid email");
      const digits = phone.replace(/\D/g, "");
      if (digits.length < 10) return toast.error("Please enter a valid phone number");
      // Persist phone/name onto profile (best-effort, non-blocking)
      updateMyProfile({ data: { full_name: fullName.trim(), phone: phone.trim() } }).catch(() => {});
    }
    if (step === 1) {
      if (!line1.trim() || pin.length !== 6 || !city.trim() || !state.trim())
        return toast.error("Please complete the address");
      persistAddressIfNeeded();
      try { localStorage.setItem("mnm_last_ship", shipMode); } catch {}
    }
    if (step === 2) {
      try { localStorage.setItem("mnm_last_pay", payMethod); } catch {}
    }
    setStep((s) => Math.min(s + 1, steps.length - 1));
  };
  const back = () => setStep((s) => Math.max(s - 1, 0));

  const place = async () => {
    if (cart.length === 0) return;
    setBusy(true);
    try {
      const init = await createOrder({
        data: {
          items: cart.map((i) => ({
            product_id: i.product.id,
            product_slug: i.product.slug,
            product_name: i.product.name,
            product_image: i.product.image,
            size: i.size,
            color: i.color,
            qty: i.qty,
            price: i.product.price,
          })),
          shipping_address: {
            full_name: fullName, email, phone, line1, line2, city, state, pincode: pin, baby_name: babyName,
          },
          shipping_mode: shipMode,
          coupon_code: coupon ? coupon.toUpperCase() : "",
          donation,
        },
      });

      // Demo / Test mode: simulate Razorpay success without a real charge so a
      // dummy transaction can be recorded and the order placed end-to-end.
      const gw = (init as any).gatewayMode as "demo" | "test" | "live" | "unknown" | undefined;
      const simulate = (init as any).demoMode;
      if (simulate) {
        const label = gw === "test" ? "TEST MODE" : "DEMO MODE";
        const confirmed = window.confirm(
          `🧪 ${label}\n\nAmount: ₹${(init.amount / 100).toLocaleString("en-IN")}\nOrder: ${init.orderNumber}\n\nClick OK to simulate a successful payment, Cancel to simulate cancellation.\n\nNote: Razorpay is using ${gw === "test" ? "test" : "demo"} keys — no real money will be charged.`
        );
        if (!confirmed) {
          toast("Payment cancelled — your cart is saved, you can retry anytime.", { icon: "🛒" });
          return;
        }
        const v = await verifyPayment({
          data: {
            orderId: init.orderId,
            razorpay_order_id: init.razorpayOrderId,
            razorpay_payment_id: `pay_demo_${Date.now()}`,
            razorpay_signature: "demo_signature",
          },
        });
        clearCart();
        setDone({ orderNumber: v.orderNumber });
        toast.success(`Payment successful ✨ (${gw === "test" ? "test" : "demo"})`);
        setTimeout(() => {
          nav({ to: "/account/orders/$orderNumber", params: { orderNumber: v.orderNumber } });
        }, 2500);
        return;
      }

      if ((init as any).gatewayMode === "unknown") {
        throw new Error("Razorpay key format is invalid. Please check the configured Key ID and Secret.");
      }

      const ok = await loadRazorpay();
      if (!ok) throw new Error("Could not load Razorpay");

      const rzp = new window.Razorpay({
        key: init.keyId,
        amount: init.amount,
        currency: init.currency,
        name: "Mini's n More",
        description: `Order ${init.orderNumber}`,
        order_id: init.razorpayOrderId,
        prefill: { name: fullName, email, contact: phone },
        theme: { color: "#7c5cff" },
        method: {
          upi: payMethod === "upi",
          card: payMethod === "card",
          netbanking: payMethod === "netbanking",
          wallet: payMethod === "wallet",
          emi: false,
          paylater: false,
        },
        config: {
          display: {
            preferences: { show_default_blocks: true },
          },
        },
        handler: async (resp: any) => {
          try {
            const v = await verifyPayment({
              data: {
                orderId: init.orderId,
                razorpay_order_id: resp.razorpay_order_id,
                razorpay_payment_id: resp.razorpay_payment_id,
                razorpay_signature: resp.razorpay_signature,
              },
            });
            clearCart();
            setDone({ orderNumber: v.orderNumber });
            toast.success("Payment successful ✨");
            // auto-redirect to the order details page after a short pause
            setTimeout(() => {
              nav({ to: "/account/orders/$orderNumber", params: { orderNumber: v.orderNumber } });
            }, 2500);
          } catch (e: any) {
            toast.error(e?.message || "Payment verification failed. Please contact support if amount was debited.");
          }
        },
        modal: {
          ondismiss: () => {
            toast("Payment cancelled — your cart is saved, you can retry anytime.", { icon: "🛒" });
          },
        },
      });
      rzp.on("payment.failed", (r: any) => {
        const reason = r?.error?.description || r?.error?.reason || "Payment failed";
        toast.error(`${reason}. Please try a different payment method.`);
      });
      rzp.open();
    } catch (e: any) {
      toast.error(e?.message || "Could not start payment");
    } finally {
      setBusy(false);
    }
  };

  if (authLoading || !user) {
    return <div className="px-4 grid place-items-center py-24"><Loader2 className="h-6 w-6 animate-spin text-primary" /></div>;
  }

  if (done) {
    return (
      <div className="px-4 md:px-6 max-w-xl mx-auto">
        <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
          className="glass-strong rounded-[2rem] p-8 md:p-10 text-center relative overflow-hidden">
          <div className="mx-auto h-20 w-20 rounded-full bg-primary text-primary-foreground grid place-items-center shadow-glow">
            <Check className="h-10 w-10" />
          </div>
          <div className="text-[10px] uppercase tracking-[0.32em] text-primary mt-5">Thank you</div>
          <h1 className="font-display text-3xl md:text-4xl mt-2">Your order is on its way ✨</h1>
          <p className="text-muted-foreground mt-3 text-sm md:text-base">
            Order <span className="font-medium text-foreground">{done.orderNumber}</span> has been placed successfully. We'll hand-wrap it with care and send a tracking update on WhatsApp & email shortly.
          </p>
          <div className="grid grid-cols-3 gap-2 mt-6 text-[11px]">
            <div className="glass rounded-2xl p-3"><div className="text-primary font-medium">Confirmed</div><div className="text-muted-foreground mt-0.5">Right now</div></div>
            <div className="glass rounded-2xl p-3"><div className="text-primary font-medium">Wrapped</div><div className="text-muted-foreground mt-0.5">Within 24h</div></div>
            <div className="glass rounded-2xl p-3"><div className="text-primary font-medium">Delivered</div><div className="text-muted-foreground mt-0.5">3–5 days</div></div>
          </div>
          <div className="flex gap-3 justify-center mt-7 flex-wrap">
            <Link to="/account/orders/$orderNumber" params={{ orderNumber: done.orderNumber }}
              className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm">View order</Link>
            <Link to="/shop" className="rounded-full glass px-5 py-2.5 text-sm">Keep shopping</Link>
          </div>
        </motion.div>
      </div>
    );
  }

  if (cart.length === 0) {
    return (
      <div className="px-4 md:px-6 max-w-xl mx-auto">
        <div className="glass-strong rounded-[2rem] p-10 text-center">
          <div className="mx-auto h-14 w-14 rounded-full glass grid place-items-center"><ShoppingBag className="h-6 w-6 text-muted-foreground" /></div>
          <h1 className="font-display text-3xl mt-4">Your basket is empty</h1>
          <p className="text-sm text-muted-foreground mt-2">Add a few pieces before heading to checkout.</p>
          <Link to="/shop" className="mt-5 inline-block rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm">Browse shop</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="px-4 md:px-6 grid lg:grid-cols-[1fr_360px] gap-6 max-w-6xl mx-auto">
      <div className="glass-strong rounded-[2rem] p-6 md:p-10">
        {firstOrder?.eligible && (
          <div className="mb-5 rounded-2xl border border-primary/30 bg-primary/10 text-primary px-4 py-3 text-sm flex items-center gap-2">
            <Zap className="h-4 w-4" />
            <span>
              First-order bonus — <strong>{firstOrder.pct}% off</strong> applied automatically at payment.
            </span>
          </div>
        )}
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-6">
          {step > 0 && <button onClick={back} className="flex items-center gap-1 hover:text-foreground"><ChevronLeft className="h-4 w-4" /> Back</button>}
        </div>
        <div className="flex items-center gap-3 mb-8">
          {steps.map((s, i) => (
            <div key={s} className="flex items-center gap-3 flex-1">
              <div className={`h-8 w-8 rounded-full grid place-items-center text-xs font-medium transition ${i <= step ? "bg-primary text-primary-foreground" : "glass text-muted-foreground"}`}>
                {i < step ? <Check className="h-3.5 w-3.5" /> : i + 1}
              </div>
              <div className={`text-xs hidden md:block ${i <= step ? "text-foreground" : "text-muted-foreground"}`}>{s}</div>
              {i < steps.length - 1 && <div className={`flex-1 h-px ${i < step ? "bg-primary" : "bg-border"}`} />}
            </div>
          ))}
        </div>

        <AnimatePresence mode="wait">
          <motion.div key={step} initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }}
            transition={{ duration: 0.3 }} className="space-y-4">
            {step === 0 && (
              <div className="grid md:grid-cols-2 gap-4">
                {savedAddresses.length > 0 && (
                  <div className="md:col-span-2 glass rounded-2xl p-4 flex items-center justify-between gap-3 flex-wrap">
                    <div className="flex items-center gap-2 text-sm">
                      <Zap className="h-4 w-4 text-primary" />
                      <span>Welcome back — use your saved details for a 1-tap checkout.</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        const def = savedAddresses.find((a) => a.is_default) ?? savedAddresses[0];
                        if (def) { onPickSaved(def.id); }
                        setStep(3);
                      }}
                      className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-xs font-medium"
                    >
                      Quick checkout
                    </button>
                  </div>
                )}
                <Field label="Full name" value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder="Ananya Sharma" />
                <Field label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="ananya@email.com" />
                <Field label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+91 99999 99999" />
                <Field label="Baby's name (optional)" value={babyName} onChange={(e) => setBabyName(e.target.value)} placeholder="Aarav" />
              </div>
            )}
            {step === 1 && (
              <div className="grid md:grid-cols-2 gap-4">
                {savedAddresses.length > 0 && (
                  <div className="md:col-span-2">
                    <div className="text-xs uppercase tracking-wider text-muted-foreground mb-2 flex items-center gap-1.5">
                      <MapPin className="h-3.5 w-3.5 text-primary" /> Choose a saved address
                    </div>
                    <div className="grid sm:grid-cols-2 gap-2.5">
                      {savedAddresses.map((a) => {
                        const active = selectedAddrId === a.id;
                        return (
                          <button
                            type="button"
                            key={a.id}
                            onClick={() => onPickSaved(a.id)}
                            className={`text-left rounded-2xl p-3.5 border transition ${active ? "bg-primary/10 border-primary ring-2 ring-primary/30" : "glass border-transparent hover:bg-white/60"}`}
                          >
                            <div className="flex items-center justify-between">
                              <div className="text-[11px] uppercase tracking-wider text-primary">{a.label || "Home"}</div>
                              {a.is_default && <span className="text-[10px] rounded-full bg-primary/15 text-primary px-2 py-0.5 uppercase tracking-wider">Default</span>}
                            </div>
                            <div className="text-sm font-medium mt-1">{a.full_name}</div>
                            <div className="text-xs text-muted-foreground line-clamp-2">{a.line1}{a.line2 ? `, ${a.line2}` : ""}, {a.city}, {a.state} {a.pincode}</div>
                          </button>
                        );
                      })}
                      <button
                        type="button"
                        onClick={() => onPickSaved("__new")}
                        className={`text-left rounded-2xl p-3.5 border transition inline-flex items-center gap-2 ${selectedAddrId === "__new" ? "bg-primary/10 border-primary ring-2 ring-primary/30" : "glass border-dashed border-border hover:bg-white/60"}`}
                      >
                        <Plus className="h-4 w-4 text-primary" />
                        <span className="text-sm">Use a new address</span>
                      </button>
                    </div>
                  </div>
                )}
                <Field label="Address line 1" value={line1} onChange={(e) => setLine1(e.target.value)} placeholder="Flat 12, Tulip Apartments" />
                <Field label="Address line 2" value={line2} onChange={(e) => setLine2(e.target.value)} placeholder="MG Road" />
                <label className="block">
                  <div className="text-xs uppercase tracking-wider text-muted-foreground mb-1.5">Pincode {pinLoading && <span className="text-primary normal-case">· looking up…</span>}</div>
                  <input value={pin} onChange={(e) => onPinChange(e.target.value)} inputMode="numeric" placeholder="400001"
                    className="glass w-full rounded-full px-4 py-3 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/40" />
                </label>
                <label className="block">
                  <div className="text-xs uppercase tracking-wider text-muted-foreground mb-1.5">City</div>
                  <input value={city} onChange={(e) => setCity(e.target.value)} placeholder="Mumbai"
                    className="glass w-full rounded-full px-4 py-3 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/40" />
                </label>
                <label className="block md:col-span-2">
                  <div className="text-xs uppercase tracking-wider text-muted-foreground mb-1.5">State</div>
                  <input value={state} onChange={(e) => setStateName(e.target.value)} placeholder="Maharashtra"
                    className="glass w-full rounded-full px-4 py-3 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/40" />
                </label>
                <div className="md:col-span-2">
                  <div className="text-xs uppercase tracking-wider text-muted-foreground mb-2">Delivery</div>
                  <div className="grid md:grid-cols-2 gap-3">
                    <label className="glass rounded-2xl p-4 flex items-center gap-3 cursor-pointer">
                      <input type="radio" name="ship" checked={shipMode === "std"} onChange={() => setShipMode("std")} className="accent-primary" />
                      <span className="text-sm">Standard · 4–6 days · {cartTotal >= commerce.freeShippingMinInr ? "Free" : `₹${commerce.shippingStandardInr.toLocaleString("en-IN")}`}{cartTotal < commerce.freeShippingMinInr && <span className="text-[11px] text-muted-foreground"> (Free above ₹{commerce.freeShippingMinInr.toLocaleString("en-IN")}/-)</span>}</span>
                    </label>
                    <label className="glass rounded-2xl p-4 flex items-center gap-3 cursor-pointer">
                      <input type="radio" name="ship" checked={shipMode === "exp"} onChange={() => setShipMode("exp")} className="accent-primary" />
                      <span className="text-sm">Express · 2–3 days · ₹{commerce.shippingExpressInr.toLocaleString("en-IN")}</span>
                    </label>
                  </div>
                </div>
                <label className="md:col-span-2 inline-flex items-center gap-2 text-xs text-muted-foreground cursor-pointer">
                  <input type="checkbox" checked={saveAddress} onChange={(e) => setSaveAddress(e.target.checked)} className="accent-primary" />
                  Save this address for next time
                </label>
              </div>
            )}
            {step === 2 && (
              <div className="space-y-4">
                <div>
                  <div className="text-xs uppercase tracking-wider text-muted-foreground mb-2">Choose payment method</div>
                  <div className="grid sm:grid-cols-2 gap-2.5">
                    <PayTile id="upi" active={payMethod === "upi"} onClick={() => setPayMethod("upi")} icon={Smartphone}
                      title="UPI" sub="GPay · PhonePe · Paytm · BHIM" />
                    <PayTile id="card" active={payMethod === "card"} onClick={() => setPayMethod("card")} icon={CreditCard}
                      title="Credit / Debit Card" sub="Visa · Mastercard · RuPay · Amex" />
                    <PayTile id="netbanking" active={payMethod === "netbanking"} onClick={() => setPayMethod("netbanking")} icon={Building2}
                      title="Net Banking" sub="HDFC · ICICI · SBI · Axis & 50+ more" />
                    <PayTile id="wallet" active={payMethod === "wallet"} onClick={() => setPayMethod("wallet")} icon={Wallet}
                      title="Wallets" sub="Paytm · Mobikwik · Freecharge" />
                  </div>
                </div>

                {payMethod === "upi" && (
                  <div className="glass rounded-2xl p-4 space-y-2">
                    <label className="block">
                      <div className="text-xs uppercase tracking-wider text-muted-foreground mb-1.5">UPI ID (optional)</div>
                      <input value={upiId} onChange={(e) => setUpiId(e.target.value)} placeholder="yourname@okhdfcbank"
                        className="w-full rounded-full bg-transparent border border-border/60 px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-primary/40" />
                      <div className="text-[11px] text-muted-foreground mt-1.5">You can also pick your UPI app on the next screen.</div>
                    </label>
                  </div>
                )}
                {payMethod === "card" && (
                  <div className="glass rounded-2xl p-4 grid sm:grid-cols-2 gap-3">
                    <label className="block sm:col-span-2">
                      <div className="text-xs uppercase tracking-wider text-muted-foreground mb-1.5">Card number</div>
                      <input inputMode="numeric" placeholder="1234 5678 9012 3456" disabled
                        className="w-full rounded-full bg-muted/40 border border-border/60 px-4 py-2.5 text-sm outline-none" />
                    </label>
                    <label className="block">
                      <div className="text-xs uppercase tracking-wider text-muted-foreground mb-1.5">Expiry</div>
                      <input placeholder="MM / YY" disabled
                        className="w-full rounded-full bg-muted/40 border border-border/60 px-4 py-2.5 text-sm outline-none" />
                    </label>
                    <label className="block">
                      <div className="text-xs uppercase tracking-wider text-muted-foreground mb-1.5">CVV</div>
                      <input placeholder="•••" disabled
                        className="w-full rounded-full bg-muted/40 border border-border/60 px-4 py-2.5 text-sm outline-none" />
                    </label>
                    <div className="sm:col-span-2 text-[11px] text-muted-foreground inline-flex items-center gap-1.5">
                      <Shield className="h-3.5 w-3.5 text-primary" /> Card details are entered securely on the next screen — never stored on our servers.
                    </div>
                  </div>
                )}
                {payMethod === "netbanking" && (
                  <div className="glass rounded-2xl p-4 text-sm text-muted-foreground inline-flex items-center gap-2">
                    <Banknote className="h-4 w-4 text-primary" /> You'll choose your bank on the next screen.
                  </div>
                )}
                {payMethod === "wallet" && (
                  <div className="glass rounded-2xl p-4 text-sm text-muted-foreground inline-flex items-center gap-2">
                    <Wallet className="h-4 w-4 text-primary" /> Pick your wallet on the next screen.
                  </div>
                )}

                <div>
                  <div className="text-xs uppercase tracking-wider text-muted-foreground mb-1.5">Promo code</div>
                  <div className="flex gap-2">
                    <input value={coupon} onChange={(e) => setCoupon(e.target.value.toUpperCase())} placeholder="LITTLEONE"
                      className="glass flex-1 rounded-full px-4 py-3 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/40" />
                    <button onClick={applyCoupon} className="rounded-full bg-foreground text-background px-5 text-sm">Apply</button>
                  </div>
                  {couponOff > 0 && <div className="text-xs text-primary mt-1.5">✓ {coupon} applied · −₹{couponOff.toLocaleString("en-IN")}</div>}
                </div>

                <div className="glass rounded-2xl p-4">
                  <div className="flex items-center gap-2 text-sm font-medium">
                    <Heart className="h-4 w-4 text-primary" /> Add a little kindness
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">100% of your contribution funds organic clothing for newborns at partner orphanages.</p>
                  <div className="flex flex-wrap gap-2 mt-3">
                    {[0, 25, 50, 100, 250].map((v) => (
                      <button key={v} onClick={() => setDonation(v)}
                        className={`rounded-full px-3.5 py-1.5 text-xs transition ${donation === v ? "bg-primary text-primary-foreground" : "glass hover:bg-white/60"}`}>
                        {v === 0 ? "No, thanks" : `₹${v}`}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="text-[11px] text-muted-foreground inline-flex items-center gap-1.5">
                  <Shield className="h-3.5 w-3.5 text-primary" /> 256-bit SSL encrypted · Powered by Razorpay
                </div>
              </div>
            )}
            {step === 3 && (
              <div className="space-y-3">
                <p className="text-sm text-muted-foreground">Almost there. Please review your order, then pay securely.</p>
                {cart.map((i) => (
                  <div key={i.product.id + i.size + i.color} className="glass rounded-2xl p-3 flex gap-3 items-center">
                    <img src={i.product.image} alt="" className="h-14 w-14 rounded-xl object-cover" />
                    <div className="flex-1 text-sm min-w-0">
                      <div className="font-medium truncate">{i.product.name}</div>
                      <div className="text-xs text-muted-foreground">{i.size} · {i.color}</div>
                      <div className="mt-1.5 inline-flex items-center gap-1 glass rounded-full px-1 py-0.5">
                        <button onClick={() => updateQty(i.product.id, i.qty - 1, i.size, i.color)} className="h-6 w-6 grid place-items-center rounded-full hover:bg-white/60"><Minus className="h-3 w-3" /></button>
                        <span className="text-xs w-5 text-center">{i.qty}</span>
                        <button onClick={() => updateQty(i.product.id, i.qty + 1, i.size, i.color)} className="h-6 w-6 grid place-items-center rounded-full hover:bg-white/60"><Plus className="h-3 w-3" /></button>
                      </div>
                    </div>
                    <div className="text-right">
                      <div className="text-sm font-medium">₹{(i.qty * i.product.price).toLocaleString("en-IN")}</div>
                      <button onClick={() => removeFromCart(i.product.id, i.size, i.color)} className="text-[11px] text-muted-foreground hover:text-destructive inline-flex items-center gap-1 mt-1">
                        <X className="h-3 w-3" /> Remove
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </motion.div>
        </AnimatePresence>

        <div className="mt-8 flex justify-end">
          {step < steps.length - 1 ? (
            <button onClick={next} className="rounded-full bg-primary text-primary-foreground px-6 py-3 font-medium hover:shadow-glow transition">Continue</button>
          ) : (
            <button onClick={place} disabled={cart.length === 0 || busy} className="rounded-full bg-primary text-primary-foreground px-6 py-3 font-medium hover:shadow-glow transition disabled:opacity-50 inline-flex items-center gap-2">
              {busy && <Loader2 className="h-4 w-4 animate-spin" />}
              {busy ? "Starting payment…" : `Pay ₹${grand.toLocaleString("en-IN")}`}
            </button>
          )}
        </div>
      </div>

      <aside className="glass-strong rounded-[2rem] p-6 h-fit lg:sticky lg:top-28">
        <div className="font-display text-xl mb-4">Order summary</div>
        <div className="space-y-2.5 max-h-72 overflow-y-auto pr-1">
          {cart.map((i) => (
            <div key={i.product.id + i.size + i.color} className="flex gap-3 text-sm">
              <img src={i.product.image} className="h-12 w-12 rounded-lg object-cover" alt="" />
              <div className="flex-1 min-w-0">
                <div className="font-medium leading-tight truncate">{i.product.name}</div>
                <div className="text-[11px] text-muted-foreground">{i.size} · {i.color}</div>
                <div className="mt-1 inline-flex items-center gap-1">
                  <button onClick={() => updateQty(i.product.id, i.qty - 1, i.size, i.color)} className="h-5 w-5 grid place-items-center rounded-full glass hover:bg-white/60"><Minus className="h-2.5 w-2.5" /></button>
                  <span className="text-[11px] w-5 text-center">{i.qty}</span>
                  <button onClick={() => updateQty(i.product.id, i.qty + 1, i.size, i.color)} className="h-5 w-5 grid place-items-center rounded-full glass hover:bg-white/60"><Plus className="h-2.5 w-2.5" /></button>
                  <button onClick={() => removeFromCart(i.product.id, i.size, i.color)} aria-label="Remove" className="ml-1 h-5 w-5 grid place-items-center rounded-full text-muted-foreground hover:text-destructive"><X className="h-2.5 w-2.5" /></button>
                </div>
              </div>
              <div className="text-right">₹{(i.qty * i.product.price).toLocaleString("en-IN")}</div>
            </div>
          ))}
          {cart.length === 0 && <div className="text-sm text-muted-foreground">Basket is empty.</div>}
        </div>
        <div className="border-t border-border/60 mt-4 pt-4 space-y-1.5 text-sm">
          <div className="flex justify-between"><span>Subtotal</span><span>₹{cartTotal.toLocaleString("en-IN")}</span></div>
          {couponOff > 0 && <div className="flex justify-between text-primary"><span>Discount ({coupon})</span><span>− ₹{couponOff.toLocaleString("en-IN")}</span></div>}
          {firstOrder?.eligible && firstOrderOff > 0 && couponOff === 0 && (
            <div className="flex justify-between text-primary"><span>First-order ({firstOrder.pct}%)</span><span>− ₹{firstOrderOff.toLocaleString("en-IN")}</span></div>
          )}
          <div className="flex justify-between text-muted-foreground"><span>Shipping</span><span>{shipping === 0 ? "Free" : `₹${shipping}`}</span></div>
          <div className="flex justify-between text-muted-foreground"><span>GST ({commerce.gstRate}%)</span><span>₹{gst.toLocaleString("en-IN")}</span></div>
          {donation > 0 && <div className="flex justify-between text-primary"><span>Donation ♥</span><span>₹{donation.toLocaleString("en-IN")}</span></div>}
          <div className="flex justify-between font-display text-lg pt-2 border-t border-border/60 mt-2"><span>Total</span><span>₹{grand.toLocaleString("en-IN")}</span></div>
        </div>
      </aside>
    </div>
  );
}

function Field({ label, ...rest }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="block">
      <div className="text-xs uppercase tracking-wider text-muted-foreground mb-1.5">{label}</div>
      <input {...rest} className="glass w-full rounded-full px-4 py-3 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/40" />
    </label>
  );
}

function PayTile({ active, onClick, icon: Icon, title, sub }: {
  id: string; active: boolean; onClick: () => void; icon: any; title: string; sub: string;
}) {
  return (
    <button type="button" onClick={onClick}
      className={`text-left rounded-2xl p-4 flex items-center gap-3 transition border ${active ? "bg-primary/10 border-primary ring-2 ring-primary/30" : "glass border-transparent hover:bg-white/60"}`}>
      <div className={`h-10 w-10 rounded-xl grid place-items-center shrink-0 ${active ? "bg-primary text-primary-foreground" : "bg-primary/10 text-primary"}`}>
        <Icon className="h-5 w-5" />
      </div>
      <div className="min-w-0">
        <div className="text-sm font-medium">{title}</div>
        <div className="text-[11px] text-muted-foreground truncate">{sub}</div>
      </div>
      <div className={`ml-auto h-4 w-4 rounded-full border ${active ? "bg-primary border-primary" : "border-border"}`}>
        {active && <Check className="h-3 w-3 text-primary-foreground m-0.5" />}
      </div>
    </button>
  );
}
