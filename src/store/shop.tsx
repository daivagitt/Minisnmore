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
import { roundInr } from "@/lib/money";
import {
  defaultSellableSelection,
  findVariant,
  isVariantSellable,
  isVariantsProduct,
  lineUnitPrice,
} from "@/lib/product-variants";
import { toast } from "sonner";

export type CartItem = {
  product: Product;
  qty: number;
  size: string;
  color: string;
  variantSku?: string;
  unitPrice: number;
};

type ShopState = {
  cart: CartItem[];
  wishlist: string[];
  addToCart: (p: Product, opts?: { size?: string; color?: string; qty?: number; variantSku?: string }) => void;
  removeFromCart: (id: string, size?: string, color?: string, variantSku?: string) => void;
  promptRemoveFromCart: (id: string, size?: string, color?: string, variantSku?: string) => void;
  updateQty: (id: string, qty: number, size?: string, color?: string, variantSku?: string) => void;
  toggleWish: (id: string) => void;
  pruneWishlist: (validIds: Iterable<string>) => void;
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

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
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
  const scopeKey = `${cKey}|${wKey}`;

  const [cart, setCart] = useState<CartItem[]>([]);
  const [wishlist, setWishlist] = useState<string[]>([]);
  const [cartOpen, setCartOpen] = useState(false);
  const [quickView, setQuickView] = useState<Product | null>(null);
  const hydratedScope = useRef<string | null>(null);
  const prevUserIdRef = useRef<number | null>(null);
  /** Skip one persist cycle after auth scope reload (setState is async; avoid leaking cart across keys). */
  const skipPersistRef = useRef(false);

  useLayoutEffect(() => {
    if (authLoading) return;
    setCart([]);
    setWishlist([]);
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
    if (userId != null) {
      nextCart = readJson<CartItem[]>(cKey, []).map(normalizeCartItem);
      nextWish = readJson<string[]>(wKey, []).map(String);
    } else {
      localStorage.setItem(cartStorageKey(null), "[]");
      localStorage.setItem(wishStorageKey(null), "[]");
      localStorage.removeItem("mnm_cart");
      localStorage.removeItem("mnm_wish");
    }

    setCart(nextCart);
    setWishlist(nextWish);
  }, [scopeKey, cKey, wKey, userId, authLoading]);

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
  }, [cart, wishlist, cKey, wKey, scopeKey, authLoading, userId]);

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
        next[i] = { ...next[i], qty: next[i].qty + qty, unitPrice, variantSku };
        return next;
      }
      return [...c, { product: p, qty, size, color, variantSku, unitPrice }];
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
    if (item && window.confirm(`Move "${item.product.name}" to wishlist instead?`)) {
      toggleWish(item.product.id);
    }
    removeFromCart(id, size, color, variantSku);
  };
  const updateQty = (id: string, qty: number, size?: string, color?: string, variantSku?: string) =>
    setCart((c) =>
      c.flatMap((x) => {
        if (!matches(x, id, size, color, variantSku)) return [x];
        const n = Math.max(0, qty);
        return n === 0 ? [] : [{ ...x, qty: n }];
      }),
    );
  const toggleWish = (id: string) => {
    if (userId == null) {
      requireSignedIn();
      return;
    }
    const key = String(id);
    setWishlist((w) => (w.includes(key) ? w.filter((x) => x !== key) : [...w, key]));
  };
  const pruneWishlist = useCallback((validIds: Iterable<string>) => {
    const valid = new Set(Array.from(validIds, String));
    setWishlist((w) => {
      const next = w.filter((id) => valid.has(id));
      return next.length === w.length ? w : next;
    });
  }, []);
  const clearCart = () => setCart([]);

  const cartCount = cart.reduce((s, i) => s + i.qty, 0);
  const cartTotal = cart.reduce((s, i) => s + roundInr(i.qty * i.unitPrice), 0);

  return (
    <Ctx.Provider
      value={{
        cart,
        wishlist,
        addToCart,
        removeFromCart,
        promptRemoveFromCart,
        updateQty,
        toggleWish,
        pruneWishlist,
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
    </Ctx.Provider>
  );
}

export function useShop() {
  const c = useContext(Ctx);
  if (!c) throw new Error("ShopProvider missing");
  return c;
}
