export function rowBtn(add: boolean) {
  return `rounded-full glass px-3 py-1.5 text-xs flex items-center gap-1 ${add ? "" : "text-destructive"}`;
}
