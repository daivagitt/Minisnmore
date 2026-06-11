import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { getMyProfile, listMyOrders, listMyPayments } from "@/lib/orders.functions";

type Profile = { full_name?: string | null; phone?: string | null };
type AccountData = {
  profile: Profile | null;
  orders: any[] | null;
  payments: any[] | null;
  loadingProfile: boolean;
  loadingOrders: boolean;
  loadingPayments: boolean;
  refreshProfile: () => Promise<void>;
  refreshOrders: () => Promise<void>;
  refreshPayments: () => Promise<void>;
  setProfile: (profile: Profile | null) => void;
};

const AccountDataContext = createContext<AccountData | null>(null);

export function AccountDataProvider({ children, enabled = true }: { children: ReactNode; enabled?: boolean }) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [orders, setOrders] = useState<any[] | null>(null);
  const [payments, setPayments] = useState<any[] | null>(null);
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [loadingOrders, setLoadingOrders] = useState(false);
  const [loadingPayments, setLoadingPayments] = useState(false);

  const refreshProfile = async () => {
    setLoadingProfile(true);
    try {
      const result = await getMyProfile();
      setProfile(result.profile ?? { full_name: "", phone: "" });
    } catch {
      setProfile({ full_name: "", phone: "" });
    } finally {
      setLoadingProfile(false);
    }
  };

  const refreshOrders = async () => {
    setLoadingOrders(true);
    try {
      const result = await listMyOrders();
      setOrders(result.orders ?? []);
    } catch {
      setOrders([]);
    } finally {
      setLoadingOrders(false);
    }
  };

  const refreshPayments = async () => {
    setLoadingPayments(true);
    try {
      const result = await listMyPayments();
      setPayments(result.payments ?? []);
    } catch {
      setPayments([]);
    } finally {
      setLoadingPayments(false);
    }
  };

  useEffect(() => {
    if (!enabled) return;
    void refreshProfile();
  }, [enabled]);

  const value = useMemo(
    () => ({
      profile,
      orders,
      payments,
      loadingProfile,
      loadingOrders,
      loadingPayments,
      refreshProfile,
      refreshOrders,
      refreshPayments,
      setProfile,
    }),
    [profile, orders, payments, loadingProfile, loadingOrders, loadingPayments]
  );

  return <AccountDataContext.Provider value={value}>{children}</AccountDataContext.Provider>;
}

export function useAccountData() {
  const ctx = useContext(AccountDataContext);
  if (!ctx) throw new Error("useAccountData must be used inside AccountDataProvider");
  return ctx;
}