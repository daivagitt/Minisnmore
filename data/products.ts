import romper from "@/assets/p-romper-peach.jpg";
import swaddle from "@/assets/p-swaddle-cream.jpg";
import dress from "@/assets/p-dress-pink.jpg";
import overalls from "@/assets/p-overalls-beige.jpg";
import cardigan from "@/assets/p-cardigan-cream.jpg";
import sleepsuit from "@/assets/p-sleepsuit-sage.jpg";
import type { ProductVariant } from "@/lib/product-variants";

export type AgeRange = "0-3m" | "3-6m" | "6-12m" | "12-18m" | "18-24m";
export type Fabric = "Organic Cotton" | "Muslin" | "Linen" | "Knit" | "Bamboo";

export type Product = {
  id: string; slug: string; name: string; tagline: string;
  price: number; compareAt?: number; image: string; gallery: string[];
  ages: AgeRange[]; fabric: Fabric;
  colors: { name: string; hex: string; imageUrl?: string; imageUrls?: string[] }[]; sizes: string[];
  rating: number; reviewCount: number;
  category: "Newborn" | "Infant" | "Toddler";
  categorySlug?: string;
  categoryName?: string;
  sku?: string;
  itemStructure?: string;
  variants?: ProductVariant[];
  isNew?: boolean;
  isBestseller?: boolean;
  isTrending?: boolean;
  description: string;
};

export const products: Product[] = [
  { id: "p1", slug: "terracotta-petal-romper", name: "Terracotta Petal Romper",
    tagline: "Featherlight organic cotton", price: 1490, compareAt: 1690,
    image: romper, gallery: [romper, swaddle, dress],
    ages: ["3-6m", "6-12m"], fabric: "Organic Cotton",
    colors: [{ name: "Brick", hex: "#b0693c" }, { name: "Cream", hex: "#f7e3d5" }],
    sizes: ["0-3m", "3-6m", "6-12m", "12-18m"],
    rating: 4.9, reviewCount: 128, category: "Infant", isBestseller: true,
    description: "A buttery-soft romper hand-finished in 100% certified organic cotton. Designed for endless cuddles and quiet naps." },
  { id: "p2", slug: "cloud-muslin-swaddle", name: "Cloud Muslin Swaddle",
    tagline: "Breathable double-gauze", price: 990,
    image: swaddle, gallery: [swaddle, romper, sleepsuit],
    ages: ["0-3m", "3-6m"], fabric: "Muslin",
    colors: [{ name: "Cream", hex: "#f7e3d5" }, { name: "Sand", hex: "#d9bfa6" }],
    sizes: ["One Size"], rating: 4.8, reviewCount: 96, category: "Newborn", isNew: true,
    description: "An airy muslin swaddle that grows softer with every wash. Perfect for sleep, feeds and tiny travels." },
  { id: "p3", slug: "rose-blossom-dress", name: "Rose Blossom Dress",
    tagline: "Hand-stitched ruffles", price: 1890, compareAt: 2290,
    image: dress, gallery: [dress, romper, cardigan],
    ages: ["6-12m", "12-18m", "18-24m"], fabric: "Organic Cotton",
    colors: [{ name: "Tan", hex: "#c18459" }],
    sizes: ["6-12m", "12-18m", "18-24m"],
    rating: 4.9, reviewCount: 211, category: "Infant", isBestseller: true,
    description: "A twirl-worthy dress with delicate ruffled sleeves — made for first birthdays and family afternoons." },
  { id: "p4", slug: "wander-linen-overalls", name: "Wander Linen Overalls",
    tagline: "Breathable summer linen", price: 1690,
    image: overalls, gallery: [overalls, cardigan, sleepsuit],
    ages: ["12-18m", "18-24m"], fabric: "Linen",
    colors: [{ name: "Sand", hex: "#d9bfa6" }, { name: "Taupe", hex: "#c7a991" }],
    sizes: ["12-18m", "18-24m"],
    rating: 4.7, reviewCount: 64, category: "Toddler",
    description: "Easy-snap linen overalls with deep pockets for tiny treasures. Ages beautifully with every adventure." },
  { id: "p5", slug: "pearl-knit-cardigan", name: "Pearl Knit Cardigan",
    tagline: "Hand-loomed in India", price: 2190,
    image: cardigan, gallery: [cardigan, sleepsuit, romper],
    ages: ["3-6m", "6-12m", "12-18m"], fabric: "Knit",
    colors: [{ name: "Cream", hex: "#f7e3d5" }],
    sizes: ["3-6m", "6-12m", "12-18m"],
    rating: 4.9, reviewCount: 87, category: "Infant", isNew: true,
    description: "A heritage piece — hand-loomed by artisan mothers, finished with coconut shell buttons." },
  { id: "p6", slug: "meadow-sleepsuit", name: "Meadow Sleepsuit",
    tagline: "Footed bamboo blend", price: 1490,
    image: sleepsuit, gallery: [sleepsuit, swaddle, romper],
    ages: ["0-3m", "3-6m", "6-12m"], fabric: "Bamboo",
    colors: [{ name: "Taupe", hex: "#c7a991" }],
    sizes: ["0-3m", "3-6m", "6-12m"],
    rating: 4.8, reviewCount: 142, category: "Newborn", isBestseller: true,
    description: "Whisper-soft bamboo with built-in footies. Thermo-regulating for restful nights, all year long." },
  { id: "p7", slug: "buttercup-day-set", name: "Buttercup Day Set",
    tagline: "Two-piece organic cotton co-ord", price: 1590, compareAt: 1890,
    image: romper, gallery: [romper, cardigan, swaddle],
    ages: ["3-6m", "6-12m", "12-18m"], fabric: "Organic Cotton",
    colors: [{ name: "Butter", hex: "#e7c76b" }, { name: "Cream", hex: "#f7e3d5" }],
    sizes: ["3-6m", "6-12m", "12-18m"],
    rating: 4.8, reviewCount: 73, category: "Infant", isNew: true,
    description: "A sun-soft day set with easy-on shoulder snaps and relaxed bloomers for everyday play." },
  { id: "p8", slug: "cocoon-crib-bundle", name: "Cocoon Crib Bundle",
    tagline: "Swaddle + cap + mittens trio", price: 1790,
    image: swaddle, gallery: [swaddle, sleepsuit, cardigan],
    ages: ["0-3m", "3-6m"], fabric: "Muslin",
    colors: [{ name: "Cloud", hex: "#f2eee8" }, { name: "Sand", hex: "#d9bfa6" }],
    sizes: ["One Size"],
    rating: 4.9, reviewCount: 118, category: "Newborn", isNew: true, isBestseller: true,
    description: "Our most-gifted newborn trio with an airy swaddle, tiny cap and matching mittens for first days." },
  { id: "p9", slug: "garden-party-frock", name: "Garden Party Frock",
    tagline: "Soft volume with hand-finished smocking", price: 2090, compareAt: 2490,
    image: dress, gallery: [dress, cardigan, overalls],
    ages: ["6-12m", "12-18m", "18-24m"], fabric: "Organic Cotton",
    colors: [{ name: "Rose", hex: "#d49a92" }, { name: "Petal", hex: "#e7c3c0" }],
    sizes: ["6-12m", "12-18m", "18-24m"],
    rating: 4.9, reviewCount: 156, category: "Toddler", isBestseller: true,
    description: "A celebration frock with delicate smocking, roomy gathers and a feather-soft lining for comfort." },
  { id: "p10", slug: "snuggle-knit-starter", name: "Snuggle Knit Starter",
    tagline: "Layering cardigan for breezy evenings", price: 1990,
    image: cardigan, gallery: [cardigan, romper, dress],
    ages: ["0-3m", "3-6m", "6-12m"], fabric: "Knit",
    colors: [{ name: "Pearl", hex: "#efe5d3" }, { name: "Taupe", hex: "#c7a991" }],
    sizes: ["0-3m", "3-6m", "6-12m"],
    rating: 4.7, reviewCount: 65, category: "Newborn", isNew: true,
    description: "A cozy, breathable knit that slips over sleepwear or playwear without ever feeling bulky." },
];

export const ages: AgeRange[] = ["0-3m", "3-6m", "6-12m", "12-18m", "18-24m"];
// Only Organic Cotton and Muslin are surfaced in filters / nav. Other fabric
// values may still exist on legacy products in the database but are hidden
// from the UI.
export const fabrics: Fabric[] = ["Organic Cotton", "Muslin"];

export const reviews = [
  { id: "r1", author: "Ananya S.", rating: 5, date: "2 weeks ago", title: "Softest fabric ever",
    body: "The terracotta romper feels like a cloud. My daughter wore it for her naming ceremony and we got so many compliments.",
    photo: dress },
  { id: "r2", author: "Priya K.", rating: 5, date: "1 month ago", title: "Beautiful packaging",
    body: "Arrived in the most thoughtful gift box with a handwritten note. The quality is unmatched.", photo: romper },
  { id: "r3", author: "Meera R.", rating: 4, date: "3 weeks ago", title: "Worth every rupee",
    body: "We've washed it 10+ times and it's still buttery soft. Worth every rupee.", photo: swaddle },
];
