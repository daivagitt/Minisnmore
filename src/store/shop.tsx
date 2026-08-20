import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import type { Product } from "@/data/products";
import { useAuth } from "@/hooks/useAuth";
import { FIT_LIMITS } from "@/lib/product-fit";
import { roundInr } from "@/lib/money";
import {
  defaultSellableSelection,
  findVariant,
  isSimpleProductSellable,
  isVariantSellable,
  isVariantsProduct,
  lineUnitPrice,
} from "@/lib/product-variants";
import { RemoveFromCartDialog } from "@/components/RemoveFromCartDialog";
import { cartsEqual, sanitizeCartWithCatalog, type CartSyncResult } from "@/lib/cart-catalog";
import { normalizeWishlistIds, wishlistAlreadyExistsMessage } from "@/lib/wishlist";
import {
  listMyCart,
  listMyWishlist,
  replaceMyCart,
  replaceMyWishlist,
} from "@/lib/account.functions";
import { toast } from "sonner";

type PendingRemove = {
  id: string;
  size?: string;
  color?: string;
  variantSku?: string;
  productName: string;
};

export type CartItem = {
  product: Product;
  qty: number;
  size: string;
  color: string;
  variantSku?: string;
  unitPrice: number;
};

export type AppliedCoupon = { code: string; discount: number };

type ShopState = {
  cart: CartItem[];
  wishlist: string[];
  appliedCoupon: AppliedCoupon | null;
  setAppliedCoupon: (coupon: AppliedCoupon | null) => void;
  addToCart: (p: Product, opts?: { size?: string; color?: string; qty?: number; variantSku?: string }) => void;
  removeFromCart: (id: string, size?: string, color?: string, variantSku?: string) => void;
  promptRemoveFromCart: (id: string, size?: string, color?: string, variantSku?: string) => void;
  updateQty: (id: string, qty: number, size?: string, color?: string, variantSku?: string) => void;
  toggleWish: (id: string, productName?: string) => void;
  pruneWishlist: (validIds: Iterable<string>) => void;
  syncCartWithCatalog: (products: Product[]) => CartSyncResult;
  clearCart: () => void;
  cartCount: number;
  cartTotal: number;
  cartOpen: boolean;
  setCartOpen: (o: boolean) => void;
  quickView: Product | null;
  setQuickView: (p: Product | null) => void;
};

const Ctx = createContext<ShopState | null>(null);

function cartStorageKey(userId: number | null) {
  return userId != null ? `mnm_cart_u_${userId}` : "mnm_cart_guest";
}

function wishStorageKey(userId: number | null) {
  return userId != null ? `mnm_wish_u_${userId}` : "mnm_wish_guest";
}

function couponStorageKey(userId: number | null) {
  return userId != null ? `mnm_coupon_u_${userId}` : "mnm_coupon_guest";
}

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function isCartItem(raw: unknown): raw is CartItem {
  if (!raw || typeof raw !== "object") return false;
  const row = raw as Partial<CartItem>;
  return !!row.product && typeof row.product === "object" && typeof row.product.id === "string" && typeof row.qty === "number";
}

function coerceCart(raw: unknown): CartItem[] {
  if (!Array.isArray(raw)) return [];
  return raw.filter(isCartItem).map(normalizeCartItem);
}

function coerceCoupon(raw: unknown): AppliedCoupon | null {
  if (!raw || typeof raw !== "object") return null;
  const row = raw as Partial<AppliedCoupon>;
  if (typeof row.code !== "string" || !row.code.trim()) return null;
  const discount = typeof row.discount === "number" && Number.isFinite(row.discount) ? row.discount : 0;
  return { code: row.code, discount };
}

function normalizeCartItem(raw: CartItem): CartItem {
  const unitPrice =
    typeof raw.unitPrice === "number" && Number.isFinite(raw.unitPrice)
      ? raw.unitPrice
      : lineUnitPrice(raw.product, { size: raw.size, color: raw.color, sku: raw.variantSku });
  return { ...raw, unitPrice };
}

function requireSignedIn() {
  toast.error("Please sign in to use your basket and wishlist.");
  return false;
}

export function ShopProvider({ children }: { children: ReactNode }) {
  const { user, loading: authLoading } = useAuth();
  const userId = user?.id ?? null;
  const cKey = cartStorageKey(userId);
  const wKey = wishStorageKey(userId);
  const couponKey = couponStorageKey(userId);
  const scopeKey = `${cKey}|${wKey}|${couponKey}`;

  const [cart, setCart] = useState<CartItem[]>([]);
  const [wishlist, setWishlist] = useState<string[]>([]);
  const [appliedCoupon, setAppliedCoupon] = useState<AppliedCoupon | null>(null);
  const [cartOpen, setCartOpen] = useState(false);
  const [quickView, setQuickView] = useState<Product | null>(null);
  const [pendingRemove, setPendingRemove] = useState<PendingRemove | null>(null);
  const hydratedScope = useRef<string | null>(null);
  const prevUserIdRef = useRef<number | null>(null);
  /** Skip one persist cycle after auth scope reload (setState is async; avoid leaking cart across keys). */
  const skipPersistRef = useRef(false);
  const wishlistSyncRef = useRef(0);
  const lastSyncedWishlistRef = useRef<string[]>([]);
  const wishlistPersistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const wishlistPersistSeqRef = useRef(0);

  useLayoutEffect(() => {
    if (authLoading) return;
    setCart([]);
    setWishlist([]);
    setAppliedCoupon(null);
    setCartOpen(false);
  }, [scopeKey, authLoading]);

  useEffect(() => {
    if (authLoading) return;

    const prevUserId = prevUserIdRef.current;
    if (prevUserId != null && userId == null) {
      localStorage.setItem(cartStorageKey(null), "[]");
      localStorage.setItem(wishStorageKey(null), "[]");
    }
    prevUserIdRef.current = userId;

    skipPersistRef.current = true;
    hydratedScope.current = null;

    let nextCart: CartItem[] = [];
    let nextWish: string[] = [];
    let nextCoupon: AppliedCoupon | null = null;
    if (userId != null) {
      nextCart = coerceCart(readJson<unknown>(cKey, []));
      nextWish = normalizeWishlistIds(readJson<string[]>(wKey, []));
      nextCoupon = coerceCoupon(readJson<PersistedCoupon>(couponKey, null));

      const wishSyncId = ++wishlistSyncRef.current;
      void (async () => {
        const localWish = nextWish;
        const localCart = nextCart;
        const localCoupon = nextCoupon;
        try {
          const [wishRes, cartRes] = await Promise.all([listMyWishlist(), listMyCart()]);
          if (wishlistSyncRef.current !== wishSyncId) return;

          let serverWish = normalizeWishlistIds(wishRes.product_ids);
          if (serverWish.length === 0 && localWish.length > 0) {
            serverWish = normalizeWishlistIds((await replaceMyWishlist(localWish)).product_ids);
          }
          if (wishlistSyncRef.current !== wishSyncId) return;
          setWishlist((current) => {
            const next = current.join("\0") === localWish.join("\0") ? serverWish : current;
            localStorage.setItem(wKey, JSON.stringify(next));
            lastSyncedWishlistRef.current = next;
            return next;
          });

          const serverCart = coerceCart(cartRes.items);
          const serverCoupon = coerceCoupon(cartRes.coupon);
          const adoptServer = !(serverCart.length === 0 && localCart.length > 0);
          if (!adoptServer) {
            await replaceMyCart(localCart, localCoupon);
          }
          if (wishlistSyncRef.current !== wishSyncId) return;
          setCart((current) => {
            const next = cartsEqual(current, localCart) ? (adoptServer ? serverCart : localCart) : current;
            localStorage.setItem(cKey, JSON.stringify(next));
            return next;
          });
          setAppliedCoupon((current) => {
            const next = current === localCoupon ? (adoptServer ? serverCoupon : localCoupon) : current;
            if (next) localStorage.setItem(couponKey, JSON.stringify(next));
            else localStorage.removeItem(couponKey);
            return next;
          });
        } catch {
          // Keep the local cache when the account API is unavailable.
        }
      })();
    } else {
      localStorage.setItem(cartStorageKey(null), "[]");
      localStorage.setItem(wishStorageKey(null), "[]");
      localStorage.removeItem(couponStorageKey(null));
      localStorage.removeItem("mnm_cart");
      localStorage.removeItem("mnm_wish");
    }

    setCart(nextCart);
    setWishlist(nextWish);
    setAppliedCoupon(nextCoupon);
  }, [scopeKey, cKey, wKey, couponKey, userId, authLoading]);

  useEffect(() => {
    if (authLoading) return;
    if (skipPersistRef.current) {
      skipPersistRef.current = false;
      hydratedScope.current = scopeKey;
      return;
    }
    if (hydratedScope.current !== scopeKey) return;
    if (userId == null) return;
    localStorage.setItem(cKey, JSON.stringify(cart));
    localStorage.setItem(wKey, JSON.stringify(wishlist));
    if (appliedCoupon) localStorage.setItem(couponKey, JSON.stringify(appliedCoupon));
    else localStorage.removeItem(couponKey);
    void replaceMyCart(cart, appliedCoupon).catch(() => {});

    if (wishlistPersistTimerRef.current) clearTimeout(wishlistPersistTimerRef.current);
    const wishlistToSync = wishlist;
    const persistSeq = ++wishlistPersistSeqRef.current;
    wishlistPersistTimerRef.current = setTimeout(() => {
      void replaceMyWishlist(wishlistToSync)
        .then(() => {
          if (wishlistPersistSeqRef.current !== persistSeq) return;
          lastSyncedWishlistRef.current = wishlistToSync;
        })
        .catch(() => {
          if (wishlistPersistSeqRef.current !== persistSeq) return;
          const rollback = lastSyncedWishlistRef.current;
          setWishlist(rollback);
          localStorage.setItem(wKey, JSON.stringify(rollback));
          toast.error("Could not update your wishlist. Please try again.");
        });
    }, 300);

    return () => {
      if (wishlistPersistTimerRef.current) clearTimeout(wishlistPersistTimerRef.current);
    };
  }, [cart, wishlist, appliedCoupon, cKey, wKey, couponKey, scopeKey, authLoading, userId]);

  const addToCart: ShopState["addToCart"] = (p, opts = {}) => {
    if (userId == null) {
      requireSignedIn();
      return;
    }
    const defaults = defaultSellableSelection(p);
    const size = opts.size ?? defaults.size;
    const color = opts.color ?? defaults.color;
    const variant = findVariant(p, { size, color, sku: opts.variantSku });
    if (isVariantsProduct(p) && !isVariantSellable(variant)) {
      toast.error("That size/color is out of stock");
      return;
    }
    if (!isVariantsProduct(p) && !isSimpleProductSellable(p)) {
      toast.error("This item is out of stock");
      return;
    }
    const variantSku = variant?.sku ?? opts.variantSku;
    const unitPrice = lineUnitPrice(p, { size, color, sku: variantSku });
    const qty = opts.qty ?? 1;
    setCart((c) => {
      const i = c.findIndex(
        (x) =>
          x.product.id === p.id &&
          (variantSku ? x.variantSku === variantSku : x.size === size && x.color === color),
      );
      if (i >= 0) {
        const next = [...c];
        next[i] = { ...next[i], qty: Math.min(FIT_LIMITS.qtyMax, next[i].qty + qty), unitPrice, variantSku };
        return next;
      }
      return [...c, { product: p, qty: Math.min(FIT_LIMITS.qtyMax, qty), size, color, variantSku, unitPrice }];
    });
    setCartOpen(true);
  };
  const matches = (x: CartItem, id: string, size?: string, color?: string, variantSku?: string) =>
    x.product.id === id &&
    (variantSku !== undefined
      ? x.variantSku === variantSku
      : (size === undefined || x.size === size) && (color === undefined || x.color === color));
  const removeFromCart = (id: string, size?: string, color?: string, variantSku?: string) =>
    setCart((c) => c.filter((x) => !matches(x, id, size, color, variantSku)));
  const promptRemoveFromCart = (id: string, size?: string, color?: string, variantSku?: string) => {
    const item = cart.find((x) => matches(x, id, size, color, variantSku));
    if (!item) return;
    setPendingRemove({
      id,
      size,
      color,
      variantSku,
      productName: item.product.name,
    });
  };
  const updateQty = (id: string, qty: number, size?: string, color?: string, variantSku?: string) => {
    const item = cart.find((x) => matches(x, id, size, color, variantSku));
    if (!item) return;
    const n = Math.max(0, Math.min(FIT_LIMITS.qtyMax, qty));
    if (n === 0) {
      promptRemoveFromCart(id, size, color, variantSku);
      return;
    }
    setCart((c) =>
      c.map((x) => (matches(x, id, size, color, variantSku) ? { ...x, qty: n } : x)),
    );
  };
  const warnWishlistDuplicate = (productName?: string) => {
    toast.warning(wishlistAlreadyExistsMessage(productName));
  };
  const addToWishlist = (id: string, productName?: string) => {
    if (userId == null) {
      requireSignedIn();
      return;
    }
    const key = String(id);
    if (wishlist.includes(key)) {
      warnWishlistDuplicate(productName);
      return;
    }
    setWishlist((w) => {
      if (w.includes(key)) {
        warnWishlistDuplicate(productName);
        return w;
      }
      return [...w, key];
    });
  };
  const toggleWish = (id: string, productName?: string) => {
    if (userId == null) {
      requireSignedIn();
      return;
    }
    const key = String(id);
    if (wishlist.includes(key)) {
      setWishlist((w) => w.filter((x) => x !== key));
      return;
    }
    setWishlist((w) => {
      if (w.includes(key)) {
        warnWishlistDuplicate(productName);
        return w;
      }
      return [...w, key];
    });
  };
  const confirmRemoveFromCart = (moveToWishlist: boolean) => {
    if (!pendingRemove) return;
    const { id, size, color, variantSku, productName } = pendingRemove;
    setPendingRemove(null);
    if (moveToWishlist) addToWishlist(id, productName);
    removeFromCart(id, size, color, variantSku);
  };
  const pruneWishlist = useCallback((validIds: Iterable<string>) => {
    const valid = new Set(Array.from(validIds, String));
    setWishlist((w) => {
      const next = normalizeWishlistIds(w).filter((id) => valid.has(id));
      return next.length === w.length ? w : next;
    });
  }, []);
  const syncCartWithCatalog = useCallback((products: Product[]): CartSyncResult => {
    let result: CartSyncResult = { cart: [], removed: [], adjusted: [] };
    setCart((current) => {
      result = sanitizeCartWithCatalog(current, products);
      return cartsEqual(current, result.cart) ? current : result.cart;
    });
    return result;
  }, []);
  const clearCart = () => {
    setCart([]);
    setAppliedCoupon(null);
  };

  const cartCount = cart.reduce((s, i) => s + i.qty, 0);
  const cartTotal = cart.reduce((s, i) => s + roundInr(i.qty * i.unitPrice), 0);

  return (
    <Ctx.Provider
      value={{
        cart,
        wishlist,
        appliedCoupon,
        setAppliedCoupon,
        addToCart,
        removeFromCart,
        promptRemoveFromCart,
        updateQty,
        toggleWish,
        pruneWishlist,
        syncCartWithCatalog,
        clearCart,
        cartCount,
        cartTotal,
        cartOpen,
        setCartOpen,
        quickView,
        setQuickView,
      }}
    >
      {children}
      <RemoveFromCartDialog
        open={pendingRemove != null}
        productName={pendingRemove?.productName ?? ""}
        alreadyInWishlist={pendingRemove != null && wishlist.includes(String(pendingRemove.id))}
        onOpenChange={(open) => {
          if (!open) setPendingRemove(null);
        }}
        onMoveToWishlist={() => confirmRemoveFromCart(true)}
        onRemoveOnly={() => confirmRemoveFromCart(false)}
      />
    </Ctx.Provider>
  );
}

export function useShop() {
  const c = useContext(Ctx);
  if (!c) throw new Error("ShopProvider missing");
  return c;
}
