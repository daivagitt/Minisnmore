import { createFileRoute, redirect } from "@tanstack/react-router";

/** Legacy path — keep bookmarks and old links working. */
export const Route = createFileRoute("/contact")({
  beforeLoad: () => {
    throw redirect({ to: "/contact-us" });
  },
});
