import { createFileRoute, redirect } from "@tanstack/react-router";
import { z } from "zod";

const activationKeySchema = z.preprocess((value) => {
  if (value == null || value === "") return undefined;
  const first = Array.isArray(value) ? value[0] : value;
  if (first == null || first === "") return undefined;
  const asString = String(first).trim();
  return asString.replace(/^"(.*)"$/, "$1");
}, z.string().min(1).optional());

const Search = z.object({ key: activationKeySchema });

/** Legacy JHipster activation emails use /account/activate — forward to /activate. */
export const Route = createFileRoute("/account/activate")({
  validateSearch: (search) => Search.parse(search),
  beforeLoad: ({ search }) => {
    throw redirect({
      to: "/activate",
      search: search.key ? { key: search.key } : {},
      replace: true,
    });
  },
});
