import { createFileRoute, redirect } from "@tanstack/react-router";
import { z } from "zod";

const Search = z.object({ key: z.string().min(1).optional() });

/** Password-reset emails link to /account/reset/finish — forward to /reset-password. */
export const Route = createFileRoute("/account/reset/finish")({
  validateSearch: (search) => Search.parse(search),
  beforeLoad: ({ search }) => {
    throw redirect({
      to: "/reset-password",
      search: search.key ? { key: search.key } : {},
      replace: true,
    });
  },
});
