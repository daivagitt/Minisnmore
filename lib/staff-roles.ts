export const STAFF_ROLES = [
  "ROLE_SUPER_ADMIN",
  "ROLE_ADMIN",
  "ROLE_SALES",
  "ROLE_OPERATIONS",
  "ROLE_CUSTOMER_SUPPORT",
] as const;

export type StaffRole = (typeof STAFF_ROLES)[number];

export const STAFF_ROLE_LABELS: Record<StaffRole, string> = {
  ROLE_SUPER_ADMIN: "Super Admin",
  ROLE_ADMIN: "Admin",
  ROLE_SALES: "Sales",
  ROLE_OPERATIONS: "Operations",
  ROLE_CUSTOMER_SUPPORT: "Customer Support",
};

export function isStaffRole(value: string): value is StaffRole {
  return (STAFF_ROLES as readonly string[]).includes(value);
}

export function primaryStaffRole(authorities: string[] | null | undefined): StaffRole | null {
  if (!authorities?.length) return null;
  if (authorities.includes("ROLE_SUPER_ADMIN")) return "ROLE_SUPER_ADMIN";
  if (authorities.includes("ROLE_ADMIN")) return "ROLE_ADMIN";
  if (authorities.includes("ROLE_SALES")) return "ROLE_SALES";
  if (authorities.includes("ROLE_OPERATIONS")) return "ROLE_OPERATIONS";
  if (authorities.includes("ROLE_CUSTOMER_SUPPORT")) return "ROLE_CUSTOMER_SUPPORT";
  return null;
}

export function isCmsAdminRole(role: StaffRole | null): boolean {
  return role === "ROLE_SUPER_ADMIN" || role === "ROLE_ADMIN";
}

export function isStaffAuthorities(authorities: string[] | null | undefined): boolean {
  return primaryStaffRole(authorities) != null;
}

export function staffHomePath(role: StaffRole | null): string {
  if (role === "ROLE_OPERATIONS") return "/operations/dashboard";
  if (role === "ROLE_SALES") return "/sales/products";
  if (role === "ROLE_CUSTOMER_SUPPORT") return "/customersupport/orders";
  if (isCmsAdminRole(role)) return "/admin";
  return "/";
}

export function assignableRoles(actorRole: StaffRole | null): StaffRole[] {
  if (actorRole === "ROLE_SUPER_ADMIN") return [...STAFF_ROLES];
  if (actorRole === "ROLE_ADMIN") {
    return ["ROLE_SALES", "ROLE_OPERATIONS", "ROLE_CUSTOMER_SUPPORT"];
  }
  return [];
}

export function isStaffInvoicePath(pathname: string): boolean {
  return /^\/(?:admin|operations|customersupport)\/orders\/[^/]+\/invoice$/.test(pathname);
}

export function canManageStaff(actorRole: StaffRole | null, targetRole: StaffRole | null): boolean {
  if (!actorRole || !targetRole) return false;
  if (actorRole === "ROLE_SUPER_ADMIN") return true;
  if (actorRole === "ROLE_ADMIN") {
    return targetRole === "ROLE_SALES" || targetRole === "ROLE_OPERATIONS" || targetRole === "ROLE_CUSTOMER_SUPPORT";
  }
  return false;
}
