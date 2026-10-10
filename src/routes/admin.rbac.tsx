import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { AdminPage, Field, inputCls } from "@/components/admin/AdminPage";
import { ListPagination } from "@/components/ListPagination";
import { useAuth } from "@/hooks/useAuth";
import { useListPagination } from "@/hooks/useListPagination";
import { api } from "@/lib/api";
import { formatOrderDate } from "@/lib/dates";
import { LIST_PAGE_SIZE } from "@/lib/pagination";
import {
  STAFF_ROLE_LABELS,
  assignableRoles,
  canManageStaff,
  isStaffAuthorities,
  primaryStaffRole,
  type StaffRole,
} from "@/lib/staff-roles";
import { showApiError } from "@/lib/ui-errors";
import { toast } from "sonner";

export const Route = createFileRoute("/admin/rbac")({
  head: () => ({ meta: [{ title: "RBAC · Mini's n More" }] }),
  component: AdminRbacPage,
});

type StaffUser = {
  id: number;
  login: string;
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
  activated: boolean;
  pendingInvite?: boolean;
  authorities?: string[];
  createdDate?: string | null;
};

function displayName(user: StaffUser) {
  return [user.firstName, user.lastName].filter(Boolean).join(" ").trim() || user.login;
}

function AdminRbacPage() {
  const { staffRole } = useAuth();
  const roles = assignableRoles(staffRole);
  const [users, setUsers] = useState<StaffUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<StaffRole>(roles[0] ?? "ROLE_SALES");

  const load = async () => {
    setLoading(true);
    try {
      const rows = await api.get<StaffUser[]>("/api/admin/users?page=0&size=1000&sort=createdDate,desc", { auth: true });
      setUsers(Array.isArray(rows) ? rows : []);
    } catch (error) {
      showApiError(error, "Could not load staff");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  useEffect(() => {
    if (roles.length && !roles.includes(role)) setRole(roles[0]);
  }, [roles, role]);

  const staffUsers = useMemo(() => users.filter((user) => isStaffAuthorities(user.authorities)), [users]);
  const staffPaging = useListPagination(staffUsers, { pageSize: LIST_PAGE_SIZE });

  const invite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !roles.length) return;
    setBusy(true);
    try {
      const login = email.trim().toLowerCase();
      await api.post(
        "/api/admin/users",
        {
          login,
          email: login,
          firstName: firstName.trim(),
          lastName: lastName.trim(),
          langKey: "en",
          activated: false,
          authorities: [role],
        },
        { auth: true, unwrap: false },
      );
      toast.success("Invitation sent");
      setFirstName("");
      setLastName("");
      setEmail("");
      await load();
    } catch (error) {
      showApiError(error, "Could not send invitation");
    } finally {
      setBusy(false);
    }
  };

  const saveUser = async (user: StaffUser, patch: Partial<StaffUser> & { authorities?: string[] }) => {
    try {
      await api.put(
        `/api/admin/users/${encodeURIComponent(user.login)}`,
        {
          id: user.id,
          login: user.login,
          email: user.email,
          firstName: user.firstName,
          lastName: user.lastName,
          activated: user.activated,
          langKey: "en",
          authorities: user.authorities,
          createdDate: user.createdDate,
          ...patch,
        },
        { auth: true, unwrap: false },
      );
      if (patch.activated !== undefined) {
        toast.success(patch.activated ? "User activated" : "User deactivated");
      } else {
        toast.success("User updated");
      }
      await load();
    } catch (error) {
      showApiError(error, patch.activated !== undefined ? (patch.activated ? "Could not activate user" : "Could not deactivate user") : "Could not update user");
    }
  };

  const resendInvite = async (user: StaffUser) => {
    try {
      await api.post(`/api/admin/users/${encodeURIComponent(user.login)}/resend-invite`, {}, { auth: true, unwrap: false });
      toast.success(`Invitation resent to ${user.email}`);
    } catch (error) {
      showApiError(error, "Could not resend invitation");
    }
  };

  return (
    <AdminPage
      eyebrow="Admin"
      title="RBAC"
      description="Invite staff and manage roles below your level. Super Admin can manage every role; Admin can manage Sales, Operations and Customer Support."
    >
      <p className="text-sm text-muted-foreground -mt-2">
        Customer join stats live on{" "}
        <Link to="/admin/customers" className="text-primary underline-offset-2 hover:underline">
          Customers
        </Link>
        .
      </p>

      <section className="space-y-3">
        <h2 className="font-display text-xl">Staff</h2>
        <form onSubmit={invite} className="glass-strong rounded-2xl p-5 grid gap-3 md:grid-cols-2 lg:grid-cols-5 items-end">
          <Field label="First name">
            <input className={inputCls()} value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
          </Field>
          <Field label="Last name">
            <input className={inputCls()} value={lastName} onChange={(e) => setLastName(e.target.value)} />
          </Field>
          <Field label="Email">
            <input className={inputCls()} type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </Field>
          <Field label="Role">
            <select className={inputCls()} value={role} onChange={(e) => setRole(e.target.value as StaffRole)}>
              {roles.map((item) => (
                <option key={item} value={item}>
                  {STAFF_ROLE_LABELS[item]}
                </option>
              ))}
            </select>
          </Field>
          <button
            type="submit"
            disabled={busy || !roles.length}
            className="rounded-full bg-primary text-primary-foreground px-5 py-2.5 text-sm disabled:opacity-60"
          >
            {busy ? "Sending…" : "Send invitation"}
          </button>
        </form>

        {loading ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : staffUsers.length === 0 ? (
          <div className="glass-strong rounded-3xl p-6 text-sm text-muted-foreground">No staff accounts yet.</div>
        ) : (
          <>
            <div id="admin-rbac-staff" className="overflow-x-auto rounded-2xl border border-border">
              <table className="w-full text-left text-sm">
                <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2">Name</th>
                    <th className="px-3 py-2">Email</th>
                    <th className="px-3 py-2">Role</th>
                    <th className="px-3 py-2">Joined</th>
                    <th className="px-3 py-2">Status</th>
                    <th className="px-3 py-2">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {staffPaging.pageItems.map((user) => {
                    const targetRole = primaryStaffRole(user.authorities);
                    const manageable = canManageStaff(staffRole, targetRole);
                    return (
                      <tr key={user.id} className="border-t border-border/70">
                        <td className="px-3 py-2 font-medium">{displayName(user)}</td>
                        <td className="px-3 py-2">{user.email}</td>
                        <td className="px-3 py-2">
                          {manageable ? (
                            <select
                              className="rounded-xl border border-border bg-background px-2 py-1"
                              value={targetRole ?? ""}
                              onChange={(e) => void saveUser(user, { authorities: [e.target.value] })}
                            >
                              {assignableRoles(staffRole).map((item) => (
                                <option key={item} value={item}>
                                  {STAFF_ROLE_LABELS[item]}
                                </option>
                              ))}
                            </select>
                          ) : (
                            (targetRole && STAFF_ROLE_LABELS[targetRole]) || "—"
                          )}
                        </td>
                        <td className="px-3 py-2">{formatOrderDate(user.createdDate)}</td>
                        <td className="px-3 py-2">
                          {user.activated ? (
                            <span className="rounded-full bg-emerald-100 text-emerald-800 dark:bg-emerald-950/80 dark:text-emerald-300 px-2.5 py-0.5 text-xs font-semibold">
                              Active
                            </span>
                          ) : user.pendingInvite ? (
                            <span className="rounded-full bg-amber-100 text-amber-800 dark:bg-amber-950/80 dark:text-amber-300 px-2.5 py-0.5 text-xs font-semibold">
                              Pending invite
                            </span>
                          ) : (
                            <span className="rounded-full bg-red-100 text-red-800 dark:bg-red-950/80 dark:text-red-300 px-2.5 py-0.5 text-xs font-semibold">
                              Deactivated
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-2">
                          {manageable ? (
                            user.activated ? (
                              <button
                                type="button"
                                className="rounded-full border border-red-200 dark:border-red-900/60 bg-red-50/60 dark:bg-red-950/30 text-red-700 dark:text-red-300 hover:bg-red-100 px-3 py-1 text-xs font-medium transition"
                                onClick={() => void saveUser(user, { activated: false })}
                              >
                                Deactivate
                              </button>
                            ) : user.pendingInvite ? (
                              <button
                                type="button"
                                className="rounded-full border border-amber-300 dark:border-amber-800 text-amber-800 dark:text-amber-200 hover:bg-amber-50 dark:hover:bg-amber-950/40 px-3 py-1 text-xs font-medium transition"
                                onClick={() => void resendInvite(user)}
                              >
                                Resend invite
                              </button>
                            ) : (
                              <button
                                type="button"
                                className="rounded-full border border-emerald-300 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200 hover:bg-emerald-50 dark:hover:bg-emerald-950/40 px-3 py-1 text-xs font-medium transition"
                                onClick={() => void saveUser(user, { activated: true })}
                              >
                                Activate
                              </button>
                            )
                          ) : (
                            "—"
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <ListPagination
              page={staffPaging.page}
              totalPages={staffPaging.totalPages}
              totalItems={staffPaging.totalItems}
              pageSize={staffPaging.pageSize}
              scrollToId="admin-rbac-staff"
              onPageChange={staffPaging.setPage}
            />
          </>
        )}
      </section>
    </AdminPage>
  );
}
