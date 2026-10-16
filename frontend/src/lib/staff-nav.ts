export type StaffNavItem = { to: string; label: string; exact?: boolean };

export type DeskStaffRole = "ROLE_OPERATIONS" | "ROLE_SALES" | "ROLE_CUSTOMER_SUPPORT";

export const STAFF_ROLE_NAV: Record<DeskStaffRole, { title: string; items: StaffNavItem[] }> = {
  ROLE_OPERATIONS: {
    title: "Operations",
    items: [
      { to: "/operations/dashboard", label: "Dashboard", exact: true },
      { to: "/operations/orders", label: "Orders" },
      { to: "/operations/returns", label: "Returns" },
      { to: "/operations/cancellations", label: "Cancellations" },
      { to: "/operations/invoices", label: "Invoices" },
      { to: "/operations/customers", label: "Customers" },
      { to: "/operations/products", label: "Products" },
      { to: "/operations/contact-messages", label: "Contact messages" },
    ],
  },
  ROLE_SALES: {
    title: "Sales",
    items: [{ to: "/sales/products", label: "Products", exact: true }],
  },
  ROLE_CUSTOMER_SUPPORT: {
    title: "Customer Support",
    items: [
      { to: "/customersupport/orders", label: "Orders" },
      { to: "/customersupport/customers", label: "Customers" },
      { to: "/customersupport/returns", label: "Returns" },
      { to: "/customersupport/cancellations", label: "Cancellations" },
      { to: "/customersupport/contact-messages", label: "Contact messages" },
    ],
  },
};
