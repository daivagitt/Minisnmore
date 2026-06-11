import { createFileRoute, redirect } from "@tanstack/react-router";
import { z } from "zod";

const Search = z.object({ key: z.string().optional() });

/** Legacy JHipster activation emails use /account/activate — forward to /activate. */
export const Route = createFileRoute("/account/activate")({
  validateSearch: (search) => Search.parse(search),
  beforeLoad: ({ search }) => {
    throw redirect({
      to: "/activate",
      search: { key: search.key },
      replace: true,
    });
  },
});
