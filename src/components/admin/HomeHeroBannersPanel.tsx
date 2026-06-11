import { Link } from "@tanstack/react-router";
import { ExternalLink } from "lucide-react";

export function HomeHeroBannersPanel() {
  return (
    <div className="glass rounded-2xl p-4 space-y-3">
      <div className="text-sm font-medium">Hero carousel slides</div>
      <p className="text-xs text-muted-foreground leading-relaxed">
        The top homepage carousel is built from <strong>Homepage banners</strong>, not from JSON
        here. Each slide uses a banner image, headline, subtitle, button label and link.
      </p>
      <ul className="text-xs text-muted-foreground space-y-1.5 list-disc pl-4">
        <li>
          <span className="text-foreground/90">Image</span> — wide hero photo (upload under
          Banners)
        </li>
        <li>
          <span className="text-foreground/90">Title & subtitle</span> — main text on the slide
        </li>
        <li>
          <span className="text-foreground/90">Button label & link</span> — e.g. “Shop now” →
          /shop
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
