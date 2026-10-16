import * as AccordionPrimitive from "@radix-ui/react-accordion";
import type { ReactNode } from "react";
import type { Product } from "@/data/products";
import { BadgeCheck, Plus, X } from "lucide-react";
import { cn } from "@/lib/utils";

const DEFAULT_WASH_CARE = [
  "Use cold or lukewarm water with a mild detergent.",
  "Don't use harsh chemicals to maintain fabric integrity.",
  "Opt for a gentle cycle or hand wash.",
  "Don't iron directly on prints.",
  "Avoid direct sunlight or high heat.",
] as const;

function productDetailTags(product: Product): string[] {
  const tags = [`100% ${product.fabric}`];
  if (product.fabric === "Organic Cotton" || product.fabric === "Muslin" || product.fabric === "Bamboo") {
    tags.push("Pre-Washed", "Breathable");
  } else if (product.fabric === "Linen") {
    tags.push("Pre-Washed", "Lightweight");
  } else {
    tags.push("Soft Touch", "Durable");
  }
  return tags;
}

function productKeyFeatures(product: Product): string[] {
  const features = [`100% ${product.fabric}`];
  if (product.tagline) features.push(product.tagline);
  if (product.ages.length > 0) {
    features.push(`Suitable for ages ${product.ages.join(", ")}`);
  }
  return features;
}

export function ProductInfoSections({ product }: { product: Product }) {
  const tags = productDetailTags(product);
  const keyFeatures = productKeyFeatures(product);

  return (
    <AccordionPrimitive.Root
      type="single"
      collapsible
      className="glass-strong rounded-2xl sm:rounded-3xl p-3.5 sm:p-4 md:p-5 space-y-3"
    >
      <AccordionItem value="product-details" title="Product Details">
        {product.description ? (
          <p className="text-sm text-foreground/80 leading-relaxed whitespace-pre-wrap">
            {product.description}
          </p>
        ) : null}
        <div className={cn("flex flex-wrap gap-2", product.description ? "mt-3" : "")}>
          {tags.map((tag) => (
            <span
              key={tag}
              className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 text-primary px-3 py-1.5 text-xs font-medium"
            >
              <BadgeCheck className="h-3.5 w-3.5 shrink-0" />
              {tag}
            </span>
          ))}
        </div>
      </AccordionItem>

      <AccordionItem value="key-features" title="Key Features">
        <ul className="space-y-1.5 text-sm text-foreground/80 list-disc pl-5">
          {keyFeatures.map((feature) => (
            <li key={feature}>{feature}</li>
          ))}
        </ul>
      </AccordionItem>

      <AccordionItem value="wash-care" title="Wash Care">
        <ul className="space-y-1.5 text-sm text-foreground/80 list-disc pl-5">
          {DEFAULT_WASH_CARE.map((tip) => (
            <li key={tip}>{tip}</li>
          ))}
        </ul>
      </AccordionItem>
    </AccordionPrimitive.Root>
  );
}

function AccordionItem({
  value,
  title,
  children,
}: {
  value: string;
  title: string;
  children: ReactNode;
}) {
  return (
    <AccordionPrimitive.Item value={value} className="glass overflow-hidden rounded-2xl">
      <AccordionPrimitive.Header>
        <AccordionPrimitive.Trigger className="group flex w-full items-center justify-between gap-3 px-4 py-4 text-left text-sm font-semibold">
          <span>{title}</span>
          <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-white/55 text-foreground/70">
            <Plus className="h-4 w-4 group-data-[state=open]:hidden" aria-hidden />
            <X className="hidden h-4 w-4 group-data-[state=open]:block" aria-hidden />
          </span>
        </AccordionPrimitive.Trigger>
      </AccordionPrimitive.Header>
      <AccordionPrimitive.Content className="overflow-hidden data-[state=closed]:animate-accordion-up data-[state=open]:animate-accordion-down">
        <div className="px-4 pb-4">{children}</div>
      </AccordionPrimitive.Content>
    </AccordionPrimitive.Item>
  );
}
