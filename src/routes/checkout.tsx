import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { useShop } from "@/store/shop";
import { useAuth } from "@/hooks/useAuth";
import { useProducts } from "@/hooks/useProducts";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { Check, ChevronLeft, Loader2, Smartphone, CreditCard, Building2, Wallet, Banknote, Shield, MapPin, Plus, Zap, Minus, X, Tag } from "lucide-react";
import { toast } from "sonner";
import { ApiError } from "@/lib/api";
import { EmptyCartState } from "@/components/EmptyCartState";
import { cartLineColorLabel, cartLineImage, formatCartSyncMessage } from "@/lib/cart-catalog";
import { createOrder, verifyPayment, getMyProfile, updateMyProfile, ProfileUpdate, fullNameFieldError } from "@/lib/orders.functions";
import {
  clearLegacyLocalAddresses,
  createMyAddress,
  listMyAddresses,
  normalizeAddressDraft,
  normalizeAddressPhone,
  readLegacyLocalAddresses,
  syncMyAddresses,
  validateCheckoutAddress,
  type CheckoutAddressFieldErrors,
  type CheckoutAddressFieldName,
  type SavedAddress,
} from "@/lib/account.functions";
import { validateCoupon } from "@/lib/coupons.functions";
import { commerceFromSiteSettings } from "@/lib/commerce";
import { COD_FEE_INR, orderTotals } from "@/lib/money";
import { lookupPincode, normalizePincode } from "@/lib/pincode";
import { LazyImage } from "@/components/LazyImage";
import { CopyButton } from "@/components/CopyButton";
import { CouponOffers } from "@/components/CouponOffers";
import { openRazorpayCheckout } from "@/lib/razorpay";

export const Route = createFileRoute("/checkout")({
  head: () => ({ meta: [{ title: "Checkout · Mini's n More" }] }),
  component: Checkout,
});

const steps = ["Details", "Shipping", "Payment", "Review"];

type PayMethod = "upi" | "card" | "netbanking" | "wallet" | "cod";

function payMethodLabel(method: PayMethod) {
  switch (method) {
    case "upi": return "UPI";
    case "card": return "Credit / Debit Card";
    case "netbanking": return "Net Banking";
    case "wallet": return "Wallets";
    case "cod": return "Cash on Delivery";
  }
}

/** Keep digits only; drop a leading 91 country code when a 12-digit paste is used; cap at 10. */
function normalizePhoneInput(value: string): string {
  return normalizeAddressPhone(value);
}

function phoneFieldError(phone: string): string | undefined {
  const result = ProfileUpdate.safeParse({ full_name: "A A", phone });
  if (result.success) return undefined;
  return result.error.issues.find((issue) => issue.path[0] === "phone")?.message;
}

async function persistCheckoutProfile(fullName: string, phone: string) {
  const name = fullName.trim();
  const normalizedPhone = normalizePhoneInput(phone);
  if (!name || phoneFieldError(normalizedPhone)) return;
  try {
    await updateMyProfile({ data: { full_name: name, phone: normalizedPhone } });
  } catch {
    // createOrder also syncs profile server-side as a fallback.
  }
}

function Checkout() {
  const { cart, cartTotal, clearCart, updateQty, promptRemoveFromCart, appliedCoupon, setAppliedCoupon, syncCartWithCatalog } = useShop();
  const products = useProducts();
  const { user, loading: authLoading } = useAuth();
  const settings = useSiteSettings();
  const nav = useNavigate();
  const [step, setStep] = useState(0);
  const [done, setDone] = useState<{ orderNumber: string } | null>(null);
  const [busy, setBusy] = useState(false);

  // form state
  const [fullName, setFullName] = useState("");
  const accountEmail = user?.email ?? user?.login ?? "";
  const [phone, setPhone] = useState("");
  const [babyName, setBabyName] = useState("");
  const [line1, setLine1] = useState("");
  const [line2, setLine2] = useState("");
  const [pin, setPin] = useState("");
  const [city, setCity] = useState("");
  const [state, setStateName] = useState("");
  const [pinLoading, setPinLoading] = useState(false);
  const [pinDeliverable, setPinDeliverable] = useState<boolean | null>(null);
  const [pinError, setPinError] = useState<string | null>(null);
  const [donation, setDonation] = useState<number>(0);
  const [couponInput, setCouponInput] = useState(appliedCoupon?.code ?? "");
  const [shipMode, setShipMode] = useState<"std" | "exp">("std");
  const [payMethod, setPayMethod] = useState<PayMethod>("upi");
  const [upiId, setUpiId] = useState("");
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([]);
  const [selectedAddrId, setSelectedAddrId] = useState<string>("");
  const [saveAddress, setSaveAddress] = useState(true);
  const [profileLoaded, setProfileLoaded] = useState(false);
  const [fullNameTouched, setFullNameTouched] = useState(false);
  const [phoneTouched, setPhoneTouched] = useState(false);
  const [addressErrors, setAddressErrors] = useState<CheckoutAddressFieldErrors>({});
  const [addressTouched, setAddressTouched] = useState<Partial<Record<CheckoutAddressFieldName, boolean>>>({});

  const checkoutAddress = () => ({ line1, line2, city, state, pincode: pin });

  const addressFieldError = (name: CheckoutAddressFieldName) =>
    addressTouched[name] ? addressErrors[name] : undefined;

  const revalidateAddressFields = (
    draft: ReturnType<typeof checkoutAddress>,
    fields: CheckoutAddressFieldName[],
  ) => {
    const fieldErrors = validateCheckoutAddress(draft);
    setAddressErrors((prev) => {
      const next = { ...prev };
      for (const field of fields) next[field] = fieldErrors[field];
      return next;
    });
  };

  const touchAddressField = (name: CheckoutAddressFieldName) => {
    const draft = checkoutAddress();
    setAddressTouched((prev) => ({ ...prev, [name]: true }));
    revalidateAddressFields(draft, [name]);
  };

  const updateAddressField = <K extends CheckoutAddressFieldName>(name: K, value: string) => {
    const next = { ...checkoutAddress(), [name]: value };
    if (name === "line1") setLine1(value);
    if (name === "line2") setLine2(value);
    if (name === "city") setCity(value);
    if (name === "state") setStateName(value);
    if (name === "pincode") setPin(value);
    if (addressTouched[name]) revalidateAddressFields(next, [name]);
  };

  useEffect(() => {
    setCouponInput(appliedCoupon?.code ?? "");
  }, [appliedCoupon?.code]);

  useEffect(() => {
    if (!appliedCoupon?.code) return;
    let cancelled = false;
    validateCoupon({ data: { code: appliedCoupon.code, subtotal: cartTotal } })
      .then((res) => {
        if (cancelled) return;
        if (res.ok) setAppliedCoupon({ code: res.code!, discount: res.discount! });
        else setAppliedCoupon(null);
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [cartTotal, appliedCoupon?.code, setAppliedCoupon]);

  useEffect(() => {
    if (!authLoading && !user) {
      nav({ to: "/login", search: { redirect: "/checkout" } });
    }
  }, [user, authLoading, nav]);

  // Auto-populate profile (name, phone) from server
  useEffect(() => {
    if (!user || profileLoaded) return;
    getMyProfile()
      .then((r) => {
        if (r.profile?.full_name && !fullName) setFullName(r.profile.full_name);
        if (r.profile?.phone && !phone) setPhone(normalizePhoneInput(r.profile.phone));
      })
      .catch(() => {})
      .finally(() => setProfileLoaded(true));
  }, [user, profileLoaded, fullName, phone]);

  // Load saved addresses from account + last checkout preferences from localStorage
  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    const storageKey = `mnm_addresses_${user.id}`;

    void (async () => {
      try {
        let { addresses } = await listMyAddresses();
        if (addresses.length === 0) {
          const legacy = readLegacyLocalAddresses(storageKey);
          if (legacy.length > 0) {
            const synced = await syncMyAddresses(
              legacy.map((a) => ({
                label: a.label ?? "Home",
                full_name: a.full_name,
                phone: a.phone,
                line1: a.line1,
                line2: a.line2 ?? "",
                city: a.city,
                state: a.state,
                pincode: a.pincode,
                is_default: a.is_default,
              })),
            );
            addresses = synced.addresses;
            clearLegacyLocalAddresses(storageKey);
          }
        }
        if (cancelled) return;
        setSavedAddresses(addresses);
        const def = addresses.find((a) => a.is_default) ?? addresses[0];
        if (def) {
          setSelectedAddrId(String(def.id));
          applyAddress(def);
          setSaveAddress(false);
        }
      } catch {
        if (!cancelled) setSavedAddresses([]);
      }
    })();

    try {
      const lastPay = localStorage.getItem("mnm_last_pay") as PayMethod | null;
      // COD is a per-order choice — never restore it from a previous checkout.
      if (lastPay === "cod") {
        try { localStorage.removeItem("mnm_last_pay"); } catch {}
      } else if (lastPay && ["upi", "card", "netbanking", "wallet"].includes(lastPay)) {
        setPayMethod(lastPay);
      }
      const lastShip = localStorage.getItem("mnm_last_ship") as "std" | "exp" | null;
      if (lastShip === "std" || lastShip === "exp") setShipMode(lastShip);
    } catch {}

    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  const checkPinDelivery = async (pinValue: string, opts?: { toastOnError?: boolean }) => {
    const clean = normalizePincode(pinValue);
    if (clean.length !== 6) {
      setPinDeliverable(null);
      setPinError(null);
      return;
    }
    setPinLoading(true);
    setPinError(null);
    try {
      const result = await lookupPincode(clean);
      if (result.ok) {
        setCity(result.lookup.city);
        setStateName(result.lookup.state);
        setPinDeliverable(true);
        setPinError(null);
        setAddressErrors((prev) => ({ ...prev, city: undefined, state: undefined, pincode: undefined }));
      } else {
        setCity("");
        setStateName("");
        setPinDeliverable(false);
        setPinError(result.error);
        if (opts?.toastOnError !== false) toast.error(result.error);
      }
    } catch {
      setPinDeliverable(false);
      const message = "Couldn't check pincode";
      setPinError(message);
      if (opts?.toastOnError !== false) toast.error(message);
    } finally {
      setPinLoading(false);
    }
  };

  const applyAddress = (a: SavedAddress) => {
    if (a.phone) setPhone(normalizePhoneInput(a.phone));
    setLine1(a.line1 || "");
    setLine2(a.line2 || "");
    setPin(a.pincode || "");
    setCity(a.city || "");
    setStateName(a.state || "");
    if (normalizePincode(a.pincode || "").length === 6) {
      void checkPinDelivery(a.pincode || "", { toastOnError: false });
    } else {
      setPinDeliverable(null);
      setPinError(null);
    }
  };

  const onPickSaved = (id: string) => {
    setSelectedAddrId(id);
    setAddressErrors({});
    setAddressTouched({});
    if (id === "__new") {
      setLine1(""); setLine2(""); setPin(""); setCity(""); setStateName("");
      setPinDeliverable(null);
      setPinError(null);
      setSaveAddress(true);
      return;
    }
    const a = savedAddresses.find((x) => String(x.id) === id);
    if (a) { applyAddress(a); setSaveAddress(false); }
  };

  const persistAddressIfNeeded = async () => {
    if (!user || !saveAddress) return;
    const dup = savedAddresses.find((a) => a.line1 === line1 && a.pincode === pin && a.city === city);
    if (dup) return;
    try {
      const { address } = await createMyAddress(
        normalizeAddressDraft({
          label: "Home",
          full_name: fullName.trim(),
          phone: normalizePhoneInput(phone),
          line1,
          line2,
          city,
          state,
          pincode: pin,
          is_default: savedAddresses.length === 0,
        }),
      );
      setSavedAddresses((list) => [...list, address]);
      setSelectedAddrId(String(address.id));
    } catch {
      // Non-blocking — checkout can still complete with the typed address.
    }
  };

  const applyCoupon = async (codeArg?: string) => {
    const raw = (codeArg ?? couponInput).trim().toUpperCase();
    if (!raw) return;
    setCouponInput(raw);
    try {
      const res = await validateCoupon({ data: { code: raw, subtotal: cartTotal } });
      if (!res.ok) {
        setAppliedCoupon(null);
        toast.error(res.error);
        return;
      }
      setAppliedCoupon({ code: res.code!, discount: res.discount! });
      toast.success("Coupon applied");
    } catch {
      setAppliedCoupon(null);
      toast.error("Could not validate coupon");
    }
  };

  const couponOff = appliedCoupon?.discount ?? 0;
  const coupon = appliedCoupon?.code ?? "";

  const commerce = commerceFromSiteSettings(settings);
  const totals = orderTotals({
    subtotal: cartTotal,
    discount: couponOff,
    donation,
    codFee: step >= 2 && payMethod === "cod" ? COD_FEE_INR : 0,
    shipMode,
    commerce,
  });
  const shipping = totals.shipping;
  const grand = totals.grand;

  const onPinChange = async (v: string) => {
    const clean = normalizePincode(v);
    setPin(clean);
    if (addressTouched.pincode) revalidateAddressFields({ ...checkoutAddress(), pincode: clean }, ["pincode"]);
    if (clean.length === 6) {
      await checkPinDelivery(clean);
    } else {
      setPinDeliverable(null);
      setPinError(null);
    }
  };

  const shippingFieldErrors = validateCheckoutAddress(checkoutAddress());
  const shippingHasErrors = Object.values(shippingFieldErrors).some(Boolean);
  const pinNotDeliverable = pin.length === 6 && pinDeliverable !== true;
  const shippingContinueDisabled = step === 1 && (pinLoading || shippingHasErrors || pinNotDeliverable);

  const validateDetailsStep = () => {
    const nameError = fullNameFieldError(fullName);
    if (nameError) return nameError;
    if (!fullName.trim()) return "Enter your full name";
    if (!/^\S+@\S+\.\S+$/.test(accountEmail)) return "Your account email is missing or invalid";
    return phoneFieldError(phone);
  };

  const validateShippingStep = () => {
    const fieldErrors = validateCheckoutAddress(checkoutAddress());
    const firstFieldError = (["line1", "pincode", "city", "state", "line2"] as CheckoutAddressFieldName[])
      .map((field) => fieldErrors[field])
      .find(Boolean);
    if (firstFieldError) return firstFieldError;
    if (pinNotDeliverable) return pinError ?? "Delivery isn't available for this pincode yet.";
    return undefined;
  };

  const next = () => {
    if (step === 0) {
      setFullNameTouched(true);
      setPhoneTouched(true);
      const detailsError = validateDetailsStep();
      if (detailsError) return toast.error(detailsError);
      void persistCheckoutProfile(fullName, phone);
    }
    if (step === 1) {
      const allTouched = Object.fromEntries(
        (["line1", "line2", "pincode", "city", "state"] as CheckoutAddressFieldName[]).map((field) => [field, true]),
      ) as Record<CheckoutAddressFieldName, boolean>;
      setAddressTouched((prev) => ({ ...prev, ...allTouched }));
      const fieldErrors = validateCheckoutAddress(checkoutAddress());
      setAddressErrors(fieldErrors);
      const shippingError = validateShippingStep();
      if (shippingError) return toast.error(shippingError);
      persistAddressIfNeeded();
      try { localStorage.setItem("mnm_last_ship", shipMode); } catch {}
    }
    if (step === 2 && payMethod !== "cod") {
      try { localStorage.setItem("mnm_last_pay", payMethod); } catch {}
    }
    setStep((s) => Math.min(s + 1, steps.length - 1));
  };
  const back = () => setStep((s) => Math.max(s - 1, 0));

  const place = async () => {
    if (cart.length === 0) return;
    const detailsError = validateDetailsStep();
    if (detailsError) { setStep(0); return toast.error(detailsError); }
    const shippingError = validateShippingStep();
    if (shippingError) { setStep(1); return toast.error(shippingError); }
    setBusy(true);
    try {
      await persistCheckoutProfile(fullName, phone);
      const sync = syncCartWithCatalog(products);
      if (sync.removed.length || sync.adjusted.length) {
        const message = formatCartSyncMessage(sync);
        if (message) toast.info(message);
      }
      if (sync.cart.length === 0) {
        toast.error("Your basket is empty — unavailable items were removed.");
        return;
      }

      const init = await createOrder({
        data: {
          items: sync.cart.map((i) => ({
            product_id: i.product.id,
            product_slug: i.product.slug,
            product_name: i.product.name,
            product_image: cartLineImage(i),
            size: i.size,
            color: cartLineColorLabel(i),
            sku: i.variantSku ?? "",
            qty: i.qty,
            price: i.unitPrice,
          })),
          shipping_address: {
            full_name: fullName, email: accountEmail, phone, line1, line2, city, state, pincode: pin, baby_name: babyName,
          },
          shipping_mode: shipMode,
          payment_method: payMethod === "cod" ? "cod" : "online",
          coupon_code: coupon ? coupon.toUpperCase() : "",
          donation,
        },
      });

      // Cash on Delivery: no gateway involved — the order is confirmed and payment is
      // collected at the doorstep, so there's nothing left to verify online.
      if ((init as any).cod) {
        clearCart();
        setDone({ orderNumber: init.orderNumber });
        toast.success("Order placed — pay on delivery ✨");
        setTimeout(() => {
          nav({ to: "/account/orders/$orderNumber", params: { orderNumber: init.orderNumber } });
        }, 2500);
        return;
      }

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

      await openRazorpayCheckout(
        {
          orderId: init.orderId,
          orderNumber: init.orderNumber,
          razorpayOrderId: init.razorpayOrderId,
          keyId: init.keyId,
          amount: init.amount,
          currency: init.currency,
        },
        {
          payMethod: payMethod === "cod" ? undefined : payMethod,
          prefill: {
            name: fullName,
            email: accountEmail,
            contact: phone,
            ...(upiId.trim() ? { vpa: upiId.trim() } : {}),
          },
          description: `Order ${init.orderNumber}`,
          onSuccess: async (orderNumber) => {
            clearCart();
            setDone({ orderNumber });
            toast.success("Payment successful ✨");
            setTimeout(() => {
              nav({ to: "/account/orders/$orderNumber", params: { orderNumber } });
            }, 2500);
          },
          onCancelled: () => {
            toast("Payment cancelled — your cart is saved, you can retry anytime.", { icon: "🛒" });
          },
          onFailed: (reason) => {
            toast.error(`${reason}. Please try a different payment method.`);
          },
        },
      );
    } catch (e: unknown) {
      if (e instanceof ApiError && e.status === 409) {
        syncCartWithCatalog(products);
        toast.error(e.message || "Some items are no longer available — your basket was updated.");
        return;
      }
      toast.error(e instanceof Error ? e.message : "Could not start payment");
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
            Order{" "}
            <span className="inline-flex items-center gap-1.5 font-medium text-foreground">
              {done.orderNumber}
              <CopyButton
                value={done.orderNumber}
                label="Copy order ID"
                successMessage="Order ID copied"
                className="h-7 w-7"
                iconClassName="h-3 w-3"
              />
            </span>
            {" "}has been placed successfully. We'll hand-wrap it with care and send a tracking update on WhatsApp & email shortly.
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
        <div className="glass-strong rounded-[2rem]">
          <EmptyCartState />
        </div>
      </div>
    );
  }

  return (
    <div className="px-4 md:px-6 grid lg:grid-cols-[1fr_360px] gap-6 max-w-6xl mx-auto">
      <div className="glass-strong rounded-[2rem] p-6 md:p-10">
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
                        if (def) {
                          onPickSaved(String(def.id));
                          void persistCheckoutProfile(fullName, def.phone);
                        }
                        setStep(3);
                      }}
                      className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-xs font-medium"
                    >
                      Quick checkout
                    </button>
                  </div>
                )}
                <Field
                  label="Full name"
                  required
                  maxLength={120}
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  onBlur={() => setFullNameTouched(true)}
                  error={fullNameTouched ? fullNameFieldError(fullName) : undefined}
                  placeholder="Ananya Sharma"
                />
                <Field label="Email" type="email" required readOnly disabled value={accountEmail} placeholder="ananya@email.com" />
                <Field
                  label="Phone"
                  required
                  type="tel"
                  inputMode="numeric"
                  autoComplete="tel"
                  maxLength={10}
                  value={phone}
                  onChange={(e) => setPhone(normalizePhoneInput(e.target.value))}
                  onBlur={() => setPhoneTouched(true)}
                  error={phoneTouched ? phoneFieldError(phone) : undefined}
                  placeholder="9999999999"
                />
                <Field label="Baby's name (optional)" value={babyName} onChange={(e) => setBabyName(e.target.value)} placeholder="Aarav" />
              </div>
            )}
            {step === 1 && (
              <div className="grid md:grid-cols-2 gap-4">
                <p className="md:col-span-2 text-xs text-muted-foreground"><span className="text-destructive font-semibold">*</span> indicates a required field</p>
                {savedAddresses.length > 0 && (
                  <div className="md:col-span-2">
                    <div className="text-xs uppercase tracking-wider text-muted-foreground mb-2 flex items-center gap-1.5">
                      <MapPin className="h-3.5 w-3.5 text-primary" /> Choose a saved address
                    </div>
                    <div className="grid sm:grid-cols-2 gap-2.5">
                      {savedAddresses.map((a) => {
                        const addrId = String(a.id);
                        const active = selectedAddrId === addrId;
                        return (
                          <button
                            type="button"
                            key={addrId}
                            onClick={() => onPickSaved(addrId)}
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
                <Field
                  label="Address line 1"
                  required
                  value={line1}
                  onChange={(e) => updateAddressField("line1", e.target.value)}
                  onBlur={() => touchAddressField("line1")}
                  error={addressFieldError("line1")}
                  maxLength={200}
                  placeholder="Flat 12, Tulip Apartments"
                />
                <Field
                  label="Address line 2"
                  value={line2}
                  onChange={(e) => updateAddressField("line2", e.target.value)}
                  onBlur={() => touchAddressField("line2")}
                  error={addressFieldError("line2")}
                  maxLength={200}
                  placeholder="MG Road"
                />
                <Field
                  label="Pincode"
                  required
                  value={pin}
                  onChange={(e) => void onPinChange(e.target.value)}
                  onBlur={() => touchAddressField("pincode")}
                  error={addressFieldError("pincode") ?? (addressTouched.pincode ? pinError ?? undefined : undefined)}
                  inputMode="numeric"
                  maxLength={6}
                  placeholder="400001"
                  hint={pinLoading ? "looking up…" : undefined}
                />
                <Field
                  label="City"
                  required
                  value={city}
                  onChange={(e) => updateAddressField("city", e.target.value)}
                  onBlur={() => touchAddressField("city")}
                  error={addressFieldError("city")}
                  maxLength={80}
                  placeholder="Mumbai"
                />
                <Field
                  label="State"
                  required
                  value={state}
                  onChange={(e) => updateAddressField("state", e.target.value)}
                  onBlur={() => touchAddressField("state")}
                  error={addressFieldError("state")}
                  maxLength={80}
                  className="md:col-span-2"
                  placeholder="Maharashtra"
                />
                <div className="md:col-span-2">
                  <div className="text-xs uppercase tracking-wider text-muted-foreground mb-2">Delivery</div>
                  <div className="grid md:grid-cols-2 gap-3">
                    <label className="glass rounded-2xl p-4 flex items-center gap-3 cursor-pointer">
                      <input type="radio" name="ship" checked={shipMode === "std"} onChange={() => setShipMode("std")} className="accent-primary" />
                      <span className="text-sm">Standard · 4–6 days · {cartTotal >= commerce.freeShippingMinInr ? "Free" : `₹${commerce.shippingStandardInr.toLocaleString("en-IN")}`}{cartTotal < commerce.freeShippingMinInr && <span className="text-[11px] text-muted-foreground"> (Free above ₹{commerce.freeShippingMinInr.toLocaleString("en-IN")}/-)</span>}</span>
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
                    <PayTile id="cod" active={payMethod === "cod"} onClick={() => setPayMethod("cod")} icon={Banknote}
                      title="Cash on Delivery" sub={`Pay in cash · ₹${COD_FEE_INR} handling fee`} />
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
                  <div className="glass rounded-2xl p-4 text-sm text-muted-foreground">
                    <div className="inline-flex items-center gap-2">
                      <CreditCard className="h-4 w-4 text-primary shrink-0" />
                      <span>You'll enter your card details securely on the Razorpay screen after you review your order.</span>
                    </div>
                    <div className="mt-2 text-[11px] inline-flex items-center gap-1.5">
                      <Shield className="h-3.5 w-3.5 text-primary" /> Card details are never stored on our servers.
                    </div>
                  </div>
                )}
                {payMethod === "netbanking" && (
                  <div className="glass rounded-2xl p-4 text-sm text-muted-foreground inline-flex items-center gap-2">
                    <Building2 className="h-4 w-4 text-primary" /> You'll choose your bank on the Razorpay screen after you review your order.
                  </div>
                )}
                {payMethod === "wallet" && (
                  <div className="glass rounded-2xl p-4 text-sm text-muted-foreground inline-flex items-center gap-2">
                    <Wallet className="h-4 w-4 text-primary" /> Pick your wallet on the Razorpay screen after you review your order.
                  </div>
                )}
                {payMethod === "cod" && (
                  <div className="glass rounded-2xl p-4 text-sm text-muted-foreground inline-flex items-center gap-2">
                    <Banknote className="h-4 w-4 text-primary" /> Pay ₹{grand.toLocaleString("en-IN")} in cash (or UPI) when your order arrives. A ₹{COD_FEE_INR} handling fee applies.
                  </div>
                )}

                <div className="text-[11px] text-muted-foreground inline-flex items-center gap-1.5">
                  <Shield className="h-3.5 w-3.5 text-primary" />
                  {payMethod === "cod"
                    ? "No online payment required — pay when your order arrives."
                    : "256-bit SSL encrypted · Powered by Razorpay"}
                </div>
              </div>
            )}
            {step === 3 && (
              <div className="space-y-3">
                <p className="text-sm text-muted-foreground">
                  {payMethod === "cod"
                    ? "Almost there. Please review your order, then place it."
                    : "Almost there. Please review your order, then pay securely."}
                </p>
                <div className="glass rounded-2xl p-4 flex items-start justify-between gap-3">
                  <div className="flex items-start gap-2.5 min-w-0">
                    <CreditCard className="h-4 w-4 text-primary mt-0.5 shrink-0" />
                    <div className="text-sm min-w-0">
                      <div className="text-xs uppercase tracking-wider text-muted-foreground mb-1">Payment</div>
                      <div className="font-medium">{payMethodLabel(payMethod)}</div>
                      {payMethod === "cod" && (
                        <div className="text-muted-foreground text-xs mt-0.5">Pay ₹{grand.toLocaleString("en-IN")} on delivery (₹{COD_FEE_INR} handling fee included)</div>
                      )}
                    </div>
                  </div>
                  <button type="button" onClick={() => setStep(2)} className="text-xs text-primary story-link shrink-0">Edit</button>
                </div>
                <div className="glass rounded-2xl p-4 flex items-start justify-between gap-3">
                  <div className="flex items-start gap-2.5 min-w-0">
                    <MapPin className="h-4 w-4 text-primary mt-0.5 shrink-0" />
                    <div className="text-sm min-w-0">
                      <div className="text-xs uppercase tracking-wider text-muted-foreground mb-1">Deliver to</div>
                      <div className="font-medium">{fullName}</div>
                      <div className="text-muted-foreground">{line1}{line2 ? `, ${line2}` : ""}, {city}, {state} {pin}</div>
                      <div className="text-muted-foreground mt-0.5">Phone: {phone}</div>
                    </div>
                  </div>
                  <button type="button" onClick={() => setStep(1)} className="text-xs text-primary story-link shrink-0">Edit</button>
                </div>
                {cart.map((i) => (
                  <div key={i.product.id + i.size + i.color} className="glass rounded-2xl p-3 flex gap-3 items-center">
                    <LazyImage src={cartLineImage(i)} alt="" className="h-14 w-14 rounded-xl object-cover" priority="auto" />
                    <div className="flex-1 text-sm min-w-0">
                      <div className="font-medium truncate">{i.product.name}</div>
                      <div className="text-xs text-muted-foreground">{i.size} · {cartLineColorLabel(i)}</div>
                      <div className="mt-1.5 inline-flex items-center gap-1 glass rounded-full px-1 py-0.5">
                        <button onClick={() => updateQty(i.product.id, i.qty - 1, i.size, i.color)} className="h-6 w-6 grid place-items-center rounded-full hover:bg-white/60"><Minus className="h-3 w-3" /></button>
                        <span className="text-xs w-5 text-center">{i.qty}</span>
                        <button onClick={() => updateQty(i.product.id, i.qty + 1, i.size, i.color)} className="h-6 w-6 grid place-items-center rounded-full hover:bg-white/60"><Plus className="h-3 w-3" /></button>
                      </div>
                    </div>
                    <div className="text-right">
                      <div className="text-sm font-medium">₹{(i.qty * i.unitPrice).toLocaleString("en-IN")}</div>
                      <button onClick={() => promptRemoveFromCart(i.product.id, i.size, i.color)} className="text-[11px] text-muted-foreground hover:text-destructive inline-flex items-center gap-1 mt-1">
                        <X className="h-3 w-3" /> Remove
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </motion.div>
        </AnimatePresence>

        <div className="mt-8 flex flex-col-reverse sm:flex-row sm:justify-end gap-3">
          {step < steps.length - 1 ? (
            <button
              onClick={next}
              disabled={shippingContinueDisabled}
              className="w-full sm:w-auto rounded-full bg-primary text-primary-foreground px-6 py-3.5 sm:py-3 font-medium hover:shadow-glow transition disabled:opacity-50 disabled:pointer-events-none"
            >
              Continue
            </button>
          ) : (
            <button onClick={place} disabled={cart.length === 0 || busy} className="w-full sm:w-auto rounded-full bg-primary text-primary-foreground px-6 py-3.5 sm:py-3 font-medium hover:shadow-glow transition disabled:opacity-50 inline-flex items-center justify-center gap-2">
              {busy && <Loader2 className="h-4 w-4 animate-spin" />}
              {busy
                ? (payMethod === "cod" ? "Placing order…" : "Starting payment…")
                : payMethod === "cod"
                  ? "Place order"
                  : `Pay ₹${grand.toLocaleString("en-IN")}`}
            </button>
          )}
        </div>
      </div>

      <aside className="glass-strong rounded-[2rem] p-6 h-fit lg:sticky lg:top-28">
        <div className="font-display text-xl mb-4">Order summary</div>
        <div className="space-y-2.5 max-h-72 overflow-y-auto pr-1">
          {cart.map((i) => (
            <div key={i.product.id + i.size + i.color} className="flex gap-3 text-sm">
              <LazyImage src={cartLineImage(i)} className="h-12 w-12 rounded-lg object-cover" alt="" priority="auto" />
              <div className="flex-1 min-w-0">
                <div className="font-medium leading-tight truncate">{i.product.name}</div>
                <div className="text-[11px] text-muted-foreground">{i.size} · {cartLineColorLabel(i)}</div>
                <div className="mt-1 inline-flex items-center gap-1">
                  <button onClick={() => updateQty(i.product.id, i.qty - 1, i.size, i.color)} className="h-5 w-5 grid place-items-center rounded-full glass hover:bg-white/60"><Minus className="h-2.5 w-2.5" /></button>
                  <span className="text-[11px] w-5 text-center">{i.qty}</span>
                  <button onClick={() => updateQty(i.product.id, i.qty + 1, i.size, i.color)} className="h-5 w-5 grid place-items-center rounded-full glass hover:bg-white/60"><Plus className="h-2.5 w-2.5" /></button>
                  <button onClick={() => promptRemoveFromCart(i.product.id, i.size, i.color)} aria-label="Remove" className="ml-1 h-5 w-5 grid place-items-center rounded-full text-muted-foreground hover:text-destructive"><X className="h-2.5 w-2.5" /></button>
                </div>
              </div>
              <div className="text-right">₹{(i.qty * i.unitPrice).toLocaleString("en-IN")}</div>
            </div>
          ))}
          {cart.length === 0 && <div className="text-sm text-muted-foreground">Basket is empty.</div>}
        </div>
        <div className="border-t border-border/60 mt-4 pt-4 space-y-3">
          <div>
            <label className="text-xs uppercase tracking-wider text-muted-foreground">Have a code?</label>
            <div className="mt-2 flex gap-2">
              <div className="flex-1 glass rounded-full flex items-center px-3">
                <Tag className="h-3.5 w-3.5 text-muted-foreground" />
                <input
                  value={couponInput}
                  onChange={(e) => setCouponInput(e.target.value.toUpperCase())}
                  placeholder="LITTLEONE"
                  className="flex-1 bg-transparent px-2 py-2 text-sm outline-none uppercase"
                />
              </div>
              <button onClick={() => applyCoupon()} className="rounded-full bg-foreground text-background px-4 text-sm">Apply</button>
            </div>
            {appliedCoupon && (
              <div className="mt-2 text-xs text-primary flex items-center justify-between">
                <span>{appliedCoupon.code} · −₹{appliedCoupon.discount.toLocaleString("en-IN")}</span>
                <button
                  onClick={() => { setAppliedCoupon(null); setCouponInput(""); }}
                  className="text-muted-foreground hover:text-destructive"
                >
                  remove
                </button>
              </div>
            )}
            <CouponOffers subtotal={cartTotal} activeCode={appliedCoupon?.code} onApply={(c) => applyCoupon(c)} />
          </div>
        </div>
        <div className="border-t border-border/60 mt-4 pt-4 space-y-1.5 text-sm">
          <div className="flex justify-between"><span>Subtotal</span><span>₹{cartTotal.toLocaleString("en-IN")}</span></div>
          {couponOff > 0 && <div className="flex justify-between text-primary"><span>Discount ({coupon})</span><span>− ₹{couponOff.toLocaleString("en-IN")}</span></div>}
          <div className="flex justify-between text-muted-foreground"><span>Shipping</span><span>{shipping === 0 ? "Free" : `₹${shipping}`}</span></div>
          {totals.codFee > 0 && <div className="flex justify-between text-muted-foreground"><span>COD handling fee</span><span>₹{totals.codFee.toLocaleString("en-IN")}</span></div>}
          {donation > 0 && <div className="flex justify-between text-primary"><span>Donation ♥</span><span>₹{donation.toLocaleString("en-IN")}</span></div>}
          <div className="flex justify-between font-display text-lg pt-2 border-t border-border/60 mt-2"><span>Total</span><span>₹{grand.toLocaleString("en-IN")}</span></div>
        </div>
      </aside>
    </div>
  );
}

function Field({
  label,
  required,
  error,
  hint,
  className,
  ...rest
}: {
  label: string;
  error?: string;
  hint?: string;
  className?: string;
} & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className={`block ${className ?? ""}`}>
      <div className="text-xs uppercase tracking-wider text-muted-foreground mb-1.5">
        {label}
        {required && (
          <>
            {" "}
            <span className="text-destructive font-semibold" aria-hidden="true">*</span>
            <span className="sr-only"> (required)</span>
          </>
        )}
        {hint ? <span className="text-primary normal-case"> · {hint}</span> : null}
      </div>
      <input
        aria-invalid={!!error}
        {...rest}
        className={`glass w-full rounded-full px-4 py-3 text-sm bg-transparent outline-none focus:ring-2 focus:ring-primary/40 disabled:opacity-70 disabled:cursor-not-allowed ${error ? "ring-2 ring-destructive/40 border border-destructive/50" : ""}`}
      />
      {error ? <p className="mt-1.5 text-[11px] text-destructive">{error}</p> : null}
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
