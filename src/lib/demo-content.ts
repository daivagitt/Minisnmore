/** Template copy shipped with the storefront scaffold — never show on a live site. */
const DEMO_COPY = [
  "minis today, memories forever",
  "softness made for first cuddles",
  "wrapped with wonder",
  "stitched with a mother's promise",
  "whisper-soft organic cotton, plant-dyed hues",
  "hand-finished organic clothing for your little one's softest beginnings",
  "tiny luxuries, softly made",
  "heirloom · hand-finished",
  "made for tiny ones",
  "shop the new edit",
  "shop infant edit",
  "discover gifting",
  "read our story",
  "made for every little chapter",
  "gifts that feel like a hug",
  "trusted by 12,000+ families",
  "my monthly little box of magic",
  "anika, bengaluru",
  "welcome offer · 10% off",
  "free shipping over ₹1499",
  "whisper-soft on the very",
  "slowly stitched",
  "a note from the founders",
  "200+",
  "artisan moms",
] as const;

function normalize(text: string) {
  return text.trim().toLowerCase().replace(/\s+/g, " ");
}

export function isDemoStorefrontCopy(value: unknown): boolean {
  if (typeof value !== "string") return false;
  const text = normalize(value);
  if (!text) return false;
  return DEMO_COPY.some((snippet) => text.includes(snippet));
}
