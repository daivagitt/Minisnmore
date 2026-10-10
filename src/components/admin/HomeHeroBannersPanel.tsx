import { Link } from "@tanstack/react-router";
import { ExternalLink } from "lucide-react";

export function HomeHeroBannersPanel() {
  return (
    <div className="glass rounded-2xl p-4 space-y-3">
      <div className="text-sm font-medium">Hero carousel slides</div>
      <p className="text-xs text-muted-foreground leading-relaxed">
        The top homepage carousel is built from <strong>Homepage banners</strong>, not from JSON
        here. Each slide uses a banner image, eyebrow label, headline, subtitle, button label, link,
        and three trust lines under the card.
      </p>
      <ul className="text-xs text-muted-foreground space-y-1.5 list-disc pl-4">
        <li>
          <span className="text-foreground/90">Image</span> — wide hero photo (upload under
          Banners)
        </li>
        <li>
          <span className="text-foreground/90">Eyebrow</span> — small overline above the title
          (e.g. “Featured”, “Just in”)
        </li>
        <li>
          <span className="text-foreground/90">Title & subtitle</span> — main text on the slide
        </li>
        <li>
          <span className="text-foreground/90">Button label & link</span> — e.g. “Shop now” →
          /shop
        </li>
        <li>
          <span className="text-foreground/90">Trust lines</span> — organic cotton, free shipping,
          and dyes text under that slide. Leave blank to keep storefront defaults.
        </li>
        <li>
          <span className="text-foreground/90">Active</span> — only active banners appear in the
          carousel
        </li>
      </ul>
      <Link
        to="/admin/banners"
        className="inline-flex items-center gap-1.5 rounded-full bg-primary text-primary-foreground px-4 py-2 text-xs font-medium"
      >
        Edit homepage banners
        <ExternalLink className="h-3.5 w-3.5" />
      </Link>
    </div>
  );
}
