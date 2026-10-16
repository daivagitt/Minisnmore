import { ReactNode } from "react";
import { Leaf } from "lucide-react";
import {
  storefrontHeaderEyebrowClass,
  storefrontHeaderTitleClass,
  storefrontPageHeaderShellClass,
} from "@/components/StorefrontPageHeader";

export function LegalPage({
  eyebrow, title, lastUpdated, children,
}: { eyebrow: string; title: string; lastUpdated: string; children: ReactNode }) {
  return (
    <div className="px-4 md:px-6 max-w-4xl mx-auto">
      <div className={`relative ${storefrontPageHeaderShellClass} mb-0 overflow-hidden`}>
        <Leaf className="pointer-events-none absolute -top-8 -right-8 h-44 w-44 text-primary/5 rotate-12" aria-hidden />
        <Leaf className="pointer-events-none absolute -bottom-10 -left-8 h-48 w-48 text-primary/5 -rotate-12" aria-hidden />
        <div className="relative">
          <div className={storefrontHeaderEyebrowClass}>{eyebrow}</div>
          <h1 className={storefrontHeaderTitleClass}>{title}</h1>
          <div className="text-xs text-muted-foreground mt-2">Last updated: {lastUpdated}</div>
          <div className="prose prose-sm md:prose-base max-w-none mt-8 text-foreground/90 [&_h2]:font-display [&_h2]:text-xl [&_h2]:md:text-2xl [&_h2]:mt-8 [&_h2]:mb-3 [&_h3]:font-medium [&_h3]:mt-5 [&_h3]:mb-2 [&_p]:my-3 [&_p]:leading-relaxed [&_ul]:list-disc [&_ul]:pl-5 [&_ul]:my-3 [&_li]:my-1 [&_a]:underline [&_a]:underline-offset-4">
            {children}
          </div>
        </div>
      </div>
    </div>
  );
}