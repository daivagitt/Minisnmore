import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { CalendarDays, CalendarRange, FileDown, Users } from "lucide-react";
import { AdminPage, inputCls } from "@/components/admin/AdminPage";
import { downloadTableAsXlsx, exportFilename, rowsForExport } from "@/lib/export-table";
import { ListPagination } from "@/components/ListPagination";
import { useListPagination } from "@/hooks/useListPagination";
import { api } from "@/lib/api";
import { formatOrderDate } from "@/lib/dates";
import { listOrderedCustomerLogins } from "@/lib/orders.functions";
import { LIST_PAGE_SIZE } from "@/lib/pagination";
import {
  adminUserListFiltersActive,
  adminUserListFiltersKey,
  DEFAULT_USER_LIST_FILTERS,
  matchesAdminUserListFilters,
  type AdminUserListFilters,
  type UserStatusFilter,
  type UserSubscribedFilter,
} from "@/lib/user-filters";
import {
  buildUserJoinStats,
  isCustomerUser,
  joinFilterActive,
  joinFilterKey,
  joinFilterLabel,
  sameJoinFilter,
  toggleJoinFilter,
  userJoinedInFilter,
  type JoinBucket,
  type JoinFilter,
} from "@/lib/user-join-stats";
import { showApiError } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/customers")({
  head: () => ({ meta: [{ title: "Customers · Mini's n More" }] }),
  component: AdminCustomersPage,
});

type AdminCustomer = {
  id: number;
  login: string;
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
  activated: boolean;
  subscribed?: boolean | null;
  authorities?: string[];
  createdDate?: string | null;
  hasOrdered?: boolean;
};

const CUSTOMERS_PAGE_SIZE = 1000;
const CUSTOMERS_MAX_PAGES = 50;

const STATUS_FILTERS: { value: UserStatusFilter; label: string }[] = [
  { value: "all", label: "All status" },
  { value: "active", label: "Active" },
  { value: "pending", label: "Pending" },
];

const SUBSCRIBED_FILTERS: { value: UserSubscribedFilter; label: string }[] = [
  { value: "all", label: "All newsletter" },
  { value: "yes", label: "Subscribed" },
  { value: "no", label: "Not subscribed" },
];

function displayName(user: AdminCustomer) {
  return [user.firstName, user.lastName].filter(Boolean).join(" ").trim() || user.login;
}

function withOrderedFlag(users: AdminCustomer[], orderedLogins: Iterable<string>): AdminCustomer[] {
  const ordered = new Set(
    [...orderedLogins].map((login) => login.trim().toLowerCase()).filter(Boolean),
  );
  return users.map((user) => {
    const login = user.login?.trim().toLowerCase() ?? "";
    return { ...user, hasOrdered: Boolean(login && ordered.has(login)) };
  });
}

function listFilterSummary(filters: AdminUserListFilters): string[] {
  const parts: string[] = [];
  if (filters.query.trim()) parts.push(`matching “${filters.query.trim()}”`);
  if (filters.status === "active") parts.push("active");
  if (filters.status === "pending") parts.push("pending");
  if (filters.subscribed === "yes") parts.push("subscribed");
  if (filters.subscribed === "no") parts.push("not subscribed");
  return parts;
}

function customersHeading(joinFilter: JoinFilter, buckets: JoinBucket[], filters: AdminUserListFilters): string {
  const base = joinFilterLabel(joinFilter, buckets);
  const extras = listFilterSummary(filters);
  if (!extras.length) return base;
  return `${base} · ${extras.join(" · ")}`;
}

function filterPillClass(selected: boolean): string {
  return `text-xs rounded-full px-3 py-1.5 border ${
    selected ? "bg-primary text-primary-foreground border-primary" : "border-border text-muted-foreground"
  }`;
}

async function fetchAllCustomers(): Promise<AdminCustomer[]> {
  const all: AdminCustomer[] = [];
  for (let page = 0; page < CUSTOMERS_MAX_PAGES; page += 1) {
    const rows = await api.get<AdminCustomer[]>(
      `/api/admin/users?page=${page}&size=${CUSTOMERS_PAGE_SIZE}&sort=createdDate,desc`,
      { auth: true },
    );
    const batch = Array.isArray(rows) ? rows : [];
    all.push(...batch);
    if (batch.length < CUSTOMERS_PAGE_SIZE) break;
  }
  return all;
}

export function AdminCustomersPage({
  showRbacHint = true,
  eyebrow = "Admin",
}: {
  showRbacHint?: boolean;
  eyebrow?: string;
} = {}) {
  const [users, setUsers] = useState<AdminCustomer[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [timeline, setTimeline] = useState<"days" | "weeks">("weeks");
  const [joinFilter, setJoinFilter] = useState<JoinFilter>({ kind: "all" });
  const [listFilters, setListFilters] = useState<AdminUserListFilters>(DEFAULT_USER_LIST_FILTERS);
  const [exporting, setExporting] = useState(false);

  const load = async () => {
    setLoading(true);
    setLoadError(false);
    try {
      // Keep the customer list independent of ordered-logins so a missing/failed order
      // endpoint cannot blank the entire Customers page.
      const [usersResult, orderedResult] = await Promise.allSettled([
        fetchAllCustomers(),
        listOrderedCustomerLogins(),
      ]);
      if (usersResult.status === "fulfilled") {
        const orderedLogins = orderedResult.status === "fulfilled" ? orderedResult.value : [];
        setUsers(withOrderedFlag(usersResult.value, orderedLogins));
        if (orderedResult.status === "rejected") {
          showApiError(orderedResult.reason, "Could not load who has ordered");
        }
      } else {
        setUsers([]);
        setLoadError(true);
        showApiError(usersResult.reason, "Could not load customers");
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const stats = useMemo(() => buildUserJoinStats(users), [users]);
  const buckets = timeline === "days" ? stats.last7Days : stats.last8Weeks;
  const maxCount = Math.max(1, ...buckets.map((bucket) => bucket.count));
  const customers = useMemo(
    () =>
      users.filter(
        (user) =>
          isCustomerUser(user.authorities) &&
          userJoinedInFilter(user.createdDate, joinFilter) &&
          matchesAdminUserListFilters(user, listFilters),
      ),
    [users, joinFilter, listFilters],
  );
  const paginationResetKey = `${joinFilterKey(joinFilter)}|${adminUserListFiltersKey(listFilters)}`;
  const customerPaging = useListPagination(customers, { pageSize: LIST_PAGE_SIZE, resetKey: paginationResetKey });
  const listFiltersOn = adminUserListFiltersActive(listFilters);
  const hasAnyFilter = joinFilterActive(joinFilter) || listFiltersOn;
  const customFrom = joinFilter.kind === "custom" ? joinFilter.from : "";
  const customTo = joinFilter.kind === "custom" ? joinFilter.to : "";
  const allBuckets = useMemo(() => [...stats.last7Days, ...stats.last8Weeks], [stats.last7Days, stats.last8Weeks]);

  const clearAllFilters = () => {
    setJoinFilter({ kind: "all" });
    setListFilters({ ...DEFAULT_USER_LIST_FILTERS });
  };

  const applyJoinFilter = (next: JoinFilter) => {
    setJoinFilter((current) => toggleJoinFilter(current, next));
  };

  const exportCustomers = async () => {
    if (!customers.length) return;
    setExporting(true);
    try {
      await downloadTableAsXlsx(
        rowsForExport(customers, [
          { header: "Name", value: displayName },
          { header: "Email", value: (user) => user.email ?? "" },
          { header: "Login", value: (user) => user.login },
          { header: "Joined", value: (user) => formatOrderDate(user.createdDate) },
          { header: "Status", value: (user) => (user.activated ? "Active" : "Pending") },
          { header: "Ordered", value: (user) => Boolean(user.hasOrdered) },
          { header: "Newsletter", value: (user) => user.subscribed === true },
        ]),
        exportFilename("customers"),
        "Customers",
      );
    } catch (error) {
      showApiError(error, "Could not export customers");
    } finally {
      setExporting(false);
    }
  };

  const setJoinedDate = (field: "from" | "to", value: string) => {
    setJoinFilter((current) => {
      const from = field === "from" ? value : current.kind === "custom" ? current.from : "";
      const to = field === "to" ? value : current.kind === "custom" ? current.to : "";
      if (!from.trim() && !to.trim()) return { kind: "all" };
      return { kind: "custom", from, to };
    });
  };

  const changeTimeline = (next: "days" | "weeks") => {
    setTimeline(next);
    setJoinFilter((current) => {
      if (current.kind !== "range") return current;
      const nextBuckets = next === "days" ? stats.last7Days : stats.last8Weeks;
      return nextBuckets.some((bucket) => bucket.key === current.key) ? current : { kind: "all" };
    });
  };

  const statCards: {
    key: string;
    label: string;
    n: number;
    sub?: string;
    icon: typeof Users;
    tint: string;
    filter: JoinFilter;
  }[] = [
    {
      key: "all",
      label: "Customers",
      n: stats.total,
      sub: `${stats.active} active · ${stats.pending} pending`,
      icon: Users,
      tint: "from-rose-400/20 to-rose-200/10",
      filter: { kind: "all" },
    },
    {
      key: "today",
      label: "Joined today",
      n: stats.joinedToday,
      icon: CalendarDays,
      tint: "from-amber-400/20 to-amber-200/10",
      filter: { kind: "preset", preset: "today" },
    },
    {
      key: "last7",
      label: "Last 7 days",
      n: stats.joinedLast7Days,
      icon: CalendarRange,
      tint: "from-emerald-400/20 to-emerald-200/10",
      filter: { kind: "preset", preset: "last7" },
    },
    {
      key: "last30",
      label: "Last 30 days",
      n: stats.joinedLast30Days,
      icon: CalendarRange,
      tint: "from-sky-400/20 to-sky-200/10",
      filter: { kind: "preset", preset: "last30" },
    },
    {
      key: "month",
      label: "This month",
      n: stats.joinedThisMonth,
      icon: CalendarDays,
      tint: "from-violet-400/20 to-violet-200/10",
      filter: { kind: "preset", preset: "thisMonth" },
    },
  ];

  return (
    <AdminPage
      eyebrow={eyebrow}
      title="Customers"
      description={
        showRbacHint
          ? "See how many customers joined recently, then browse the customer list. Staff invites and role changes live on RBAC."
          : "See how many customers joined recently, then browse the customer list."
      }
      action={
        <button
          type="button"
          onClick={() => void exportCustomers()}
          disabled={loading || exporting || customers.length === 0}
          className="inline-flex items-center gap-2 rounded-full bg-primary px-4 py-2.5 text-sm text-primary-foreground disabled:opacity-60"
        >
          <FileDown className="h-4 w-4" />
          {exporting ? "Exporting…" : "Export"}
        </button>
      }
    >
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
        {statCards.map((card) => {
          const selected = sameJoinFilter(joinFilter, card.filter);
          return (
            <button
              key={card.key}
              type="button"
              aria-pressed={selected}
              aria-label={`${card.label}: ${loading ? "loading" : card.n}`}
              onClick={() => applyJoinFilter(card.filter)}
              className={`relative overflow-hidden rounded-3xl p-4 text-left glass-strong bg-gradient-to-br ${card.tint} ring-2 ${
                selected ? "ring-primary" : "ring-transparent"
              }`}
            >
              <card.icon className="h-5 w-5 text-primary" />
              <div className="text-[11px] uppercase tracking-[0.22em] text-muted-foreground mt-3">{card.label}</div>
              <div className="font-display text-2xl md:text-3xl mt-1">{loading ? "—" : card.n}</div>
              {card.sub ? <div className="text-xs text-muted-foreground mt-1">{card.sub}</div> : null}
            </button>
          );
        })}
      </div>

      <section className="glass-strong rounded-3xl p-5 space-y-4">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <div className="text-xs uppercase tracking-[0.28em] text-muted-foreground">Customer joins</div>
            <h2 className="font-display text-2xl mt-1">Join timeline</h2>
          </div>
          <div className="flex rounded-full border border-border p-1 text-xs">
            <button
              type="button"
              className={`rounded-full px-3 py-1.5 ${timeline === "days" ? "bg-primary text-primary-foreground" : "text-muted-foreground"}`}
              onClick={() => changeTimeline("days")}
            >
              Last 7 days
            </button>
            <button
              type="button"
              className={`rounded-full px-3 py-1.5 ${timeline === "weeks" ? "bg-primary text-primary-foreground" : "text-muted-foreground"}`}
              onClick={() => changeTimeline("weeks")}
            >
              Last 8 weeks
            </button>
          </div>
        </div>
        <div className="flex items-end gap-2 h-40" role="list" aria-label={timeline === "days" ? "Joins by day" : "Joins by week"}>
          {buckets.map((bucket) => {
            const selected = joinFilter.kind === "range" && joinFilter.key === bucket.key;
            const height = bucket.count === 0 ? 6 : Math.max(12, Math.round((bucket.count / maxCount) * 100));
            return (
              <button
                key={bucket.key}
                type="button"
                role="listitem"
                onClick={() =>
                  applyJoinFilter({ kind: "range", fromMs: bucket.fromMs, toMs: bucket.toMs, key: bucket.key })
                }
                className="flex-1 min-w-0 h-full flex flex-col items-center justify-end gap-1"
                aria-pressed={selected}
                title={`${bucket.label}: ${bucket.count}`}
              >
                <span className="text-[11px] text-muted-foreground">{bucket.count}</span>
                <span
                  className={`w-full max-w-8 rounded-full ${selected ? "bg-primary" : "bg-primary/45"}`}
                  style={{ height: `${height}%` }}
                />
                <span className="text-[10px] uppercase tracking-wide text-muted-foreground truncate w-full text-center">
                  {bucket.label}
                </span>
              </button>
            );
          })}
        </div>
      </section>

      <section className="space-y-3" id="admin-customers">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-display text-xl">{customersHeading(joinFilter, allBuckets, listFilters)}</h2>
          {hasAnyFilter ? (
            <button type="button" className="text-xs text-muted-foreground underline" onClick={clearAllFilters}>
              Clear filters
            </button>
          ) : null}
        </div>

        <div className="glass-strong rounded-3xl p-4 space-y-3">
          <label className="block space-y-1.5">
            <span className="text-xs uppercase tracking-[0.22em] text-muted-foreground">Search</span>
            <input
              className={inputCls()}
              value={listFilters.query}
              onChange={(e) => setListFilters((current) => ({ ...current, query: e.target.value }))}
              placeholder="Search by name, email, or login…"
              aria-label="Search customers by name, email, or login"
              autoComplete="off"
            />
          </label>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block space-y-1.5">
              <span className="text-xs uppercase tracking-[0.22em] text-muted-foreground">Joined from</span>
              <input
                type="date"
                className={inputCls()}
                value={customFrom}
                onChange={(e) => setJoinedDate("from", e.target.value)}
                aria-label="Joined from date"
              />
            </label>
            <label className="block space-y-1.5">
              <span className="text-xs uppercase tracking-[0.22em] text-muted-foreground">Joined to</span>
              <input
                type="date"
                className={inputCls()}
                value={customTo}
                onChange={(e) => setJoinedDate("to", e.target.value)}
                aria-label="Joined to date"
              />
            </label>
          </div>
          <div className="flex flex-wrap gap-4">
            <div className="space-y-1.5" role="group" aria-label="Status filter">
              <div className="text-xs uppercase tracking-[0.22em] text-muted-foreground">Status</div>
              <div className="flex flex-wrap gap-2">
                {STATUS_FILTERS.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    aria-pressed={listFilters.status === option.value}
                    onClick={() => setListFilters((current) => ({ ...current, status: option.value }))}
                    className={filterPillClass(listFilters.status === option.value)}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
            <div className="space-y-1.5" role="group" aria-label="Newsletter filter">
              <div className="text-xs uppercase tracking-[0.22em] text-muted-foreground">Newsletter</div>
              <div className="flex flex-wrap gap-2">
                {SUBSCRIBED_FILTERS.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    aria-pressed={listFilters.subscribed === option.value}
                    onClick={() => setListFilters((current) => ({ ...current, subscribed: option.value }))}
                    className={filterPillClass(listFilters.subscribed === option.value)}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>

        {loading ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : loadError ? (
          <div className="glass-strong rounded-3xl p-6 text-sm space-y-3">
            <p>Could not load customers. Please try again.</p>
            <button
              type="button"
              className="text-xs rounded-full px-3 py-1.5 border border-border text-muted-foreground"
              onClick={() => void load()}
            >
              Retry
            </button>
          </div>
        ) : customers.length === 0 ? (
          <div className="glass-strong rounded-3xl p-6 text-sm text-muted-foreground">
            {hasAnyFilter ? "No customers match these filters." : "No customers yet."}
          </div>
        ) : (
          <>
            <div className="overflow-x-auto rounded-2xl border border-border">
              <table className="w-full text-left text-sm">
                <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2">Name</th>
                    <th className="px-3 py-2">Email</th>
                    <th className="px-3 py-2">Joined</th>
                    <th className="px-3 py-2">Status</th>
                    <th className="px-3 py-2">Ordered</th>
                    <th className="px-3 py-2">Newsletter</th>
                  </tr>
                </thead>
                <tbody>
                  {customerPaging.pageItems.map((user) => (
                    <tr key={user.id} className="border-t border-border/70">
                      <td className="px-3 py-2 font-medium">{displayName(user)}</td>
                      <td className="px-3 py-2">{user.email}</td>
                      <td className="px-3 py-2">{formatOrderDate(user.createdDate)}</td>
                      <td className="px-3 py-2">{user.activated ? "Active" : "Pending"}</td>
                      <td className="px-3 py-2">{user.hasOrdered ? "Yes" : "No"}</td>
                      <td className="px-3 py-2">{user.subscribed === true ? "Subscribed" : "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <ListPagination
              page={customerPaging.page}
              totalPages={customerPaging.totalPages}
              totalItems={customerPaging.totalItems}
              pageSize={customerPaging.pageSize}
              scrollToId="admin-customers"
              onPageChange={customerPaging.setPage}
            />
          </>
        )}
      </section>

      {showRbacHint ? (
        <p className="text-sm text-muted-foreground">
          Invite staff and manage roles on{" "}
          <Link to="/admin/rbac" className="text-primary underline-offset-2 hover:underline">
            RBAC
          </Link>
          .
        </p>
      ) : null}
    </AdminPage>
  );
}
