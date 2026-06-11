import { Link } from "@tanstack/react-router";
import {
  Facebook,
  Heart,
  Instagram,
  Leaf,
  Mail,
  MessageCircle,
  Phone,
  Youtube,
} from "lucide-react";
import { useCategories } from "@/hooks/useCategories";
import { usePageVisibility } from "@/hooks/usePageVisibility";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { toDirectDownloadUrl } from "@/lib/utils";
import footerLogo from "@/assets/logo-footer.png";

function XLogo({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 1200 1227"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      className={className}
      fill="currentColor"
    >
      <path d="M714.163 519.284 1160.89 0h-105.86L667.137 450.887 357.328 0H0l468.492 681.821L0 1226.37h105.866l409.625-476.152 327.181 476.152H1200L714.137 519.284h.026ZM569.165 687.828l-47.468-67.894L144.011 79.694h162.604l304.797 435.991 47.468 67.894 396.2 566.721H892.476L569.165 687.854v-.026Z" />
    </svg>
  );
}

const defaultFooterTagline =
  "Minis today, memories forever. Premium organic clothing for the first 24 months — softly made in India, gently loved everywhere.";

export function Footer() {
  const { isVisible } = usePageVisibility();
  const categories = useCategories();
  const settings = useSiteSettings();
  const logo = settings.logo_url || footerLogo;
  const tagline = settings.footer_tagline || defaultFooterTagline;
  const waHref = settings.whatsapp_number
    ? `https://wa.me/${settings.whatsapp_number.replace(/[^\d]/g, "")}`
    : null;

  return (
    <footer className="relative mt-10">
      <div className="relative mx-4 md:mx-6 mb-3 glass-strong rounded-[2rem] p-4 md:p-5 overflow-hidden">
        <Leaf
          className="pointer-events-none absolute -top-6 -right-6 h-40 w-40 text-primary/5 rotate-12"
          aria-hidden
        />
        <Leaf
          className="pointer-events-none absolute -bottom-10 -left-8 h-48 w-48 text-primary/5 -rotate-12"
          aria-hidden
        />
        <Leaf
          className="pointer-events-none absolute top-1/2 left-1/3 h-24 w-24 text-primary/[0.04] rotate-45"
          aria-hidden
        />

        <div className="relative grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 lg:gap-5 items-start">
          <div className="sm:col-span-2 lg:col-span-1">
            <div className="mb-2">
              <img
                src={logo}
                alt={settings.brand_name}
                className="h-20 sm:h-24 md:h-28 w-auto max-w-full object-contain object-left bg-transparent shrink-0"
                style={{ background: "transparent" }}
                loading="lazy"
                decoding="async"
              />
            </div>
            <div className="pl-3 sm:pl-4 md:pl-5">
            <p className="text-sm text-muted-foreground leading-snug max-w-sm">{tagline}</p>
            <div className="mt-2 text-xs text-muted-foreground space-y-0.5">
              {settings.legal_entity_name ? (
                <div className="text-foreground/80 font-medium">{settings.legal_entity_name}</div>
              ) : null}
              {settings.contact_phone ? (
                <div>
                  {settings.care_label || "Care"} Number:{" "}
                  <a
                    href={`tel:${settings.contact_phone.replace(/\s+/g, "")}`}
                    className="hover:text-foreground"
                  >
                    {settings.contact_phone}
                  </a>
                </div>
              ) : null}
              {settings.contact_email ? (
                <div>
                  Email:{" "}
                  <a href={`mailto:${settings.contact_email}`} className="hover:text-foreground">
                    {settings.contact_email}
                  </a>
                </div>
              ) : null}
            </div>
            <div className="mt-3 grid grid-cols-2 gap-2 text-[11px]">
              <div className="glass rounded-xl px-3 py-2">
                <div className="text-primary font-medium">Free shipping</div>
                <div className="text-muted-foreground">on orders ₹1,499+</div>
              </div>
              <div className="glass rounded-xl px-3 py-2">
                <div className="text-primary font-medium">WELCOME10</div>
                <div className="text-muted-foreground">10% off first order</div>
              </div>
            </div>
            </div>
          </div>

          <div>
            <div className="font-display text-base text-foreground mb-1.5 inline-block border-b-2 border-primary/40 pb-0.5">
              Shop
            </div>
            <ul className="space-y-1 text-sm">
              {isVisible("shop")
                ? categories.map((category) => (
                    <li key={category.id}>
                      <Link
                        to="/shop"
                        search={{ category: category.slug }}
                        className="story-link text-muted-foreground hover:text-foreground"
                      >
                        {category.name}
                      </Link>
                    </li>
                  ))
                : null}
              {isVisible("gift") ? (
                <li>
                  <Link to="/gift" className="story-link text-muted-foreground hover:text-foreground">
                    Gifting
                  </Link>
                </li>
              ) : null}
              {isVisible("blog") ? (
                <li>
                  <Link to="/blog" className="story-link text-muted-foreground hover:text-foreground">
                    Blogs
                  </Link>
                </li>
              ) : null}
            </ul>
          </div>

          <div>
            <div className="font-display text-base text-foreground mb-1.5 inline-block border-b-2 border-primary/40 pb-0.5">
              Care
            </div>
            <ul className="space-y-1 text-sm">
              {isVisible("track") ? (
                <li>
                  <Link to="/track" className="story-link text-muted-foreground hover:text-foreground">
                    Order tracking
                  </Link>
                </li>
              ) : null}
              {isVisible("subscribe") ? (
                <li>
                  <Link to="/subscribe" className="story-link text-muted-foreground hover:text-foreground">
                    Subscriptions
                  </Link>
                </li>
              ) : null}
              <li>
                <Link to="/account" className="story-link text-muted-foreground hover:text-foreground">
                  My account
                </Link>
              </li>
              <li>
                <Link to="/shipping" className="story-link text-muted-foreground hover:text-foreground">
                  Shipping &amp; returns
                </Link>
              </li>
              <li>
                <Link to="/about" className="story-link text-muted-foreground hover:text-foreground">
                  About us
                </Link>
              </li>
              <li>
                <a
                  href={toDirectDownloadUrl(settings.ecatalogue_url) || "/e-catalogue.pdf"}
                  download
                  className="story-link text-muted-foreground hover:text-foreground"
                >
                  Download e-catalogue
                </a>
              </li>
            </ul>
            <div className="mt-3">
              <div className="text-[11px] uppercase tracking-[0.22em] text-muted-foreground mb-1.5">
                Follow us
              </div>
              <div className="flex flex-wrap gap-2">
                {settings.instagram_url ? (
                  <a
                    href={settings.instagram_url}
                    target="_blank"
                    rel="noreferrer"
                    aria-label="Instagram"
                    className="h-9 w-9 grid place-items-center rounded-full bg-primary text-primary-foreground shadow-sm hover:scale-110 hover:shadow-md transition"
                  >
                    <Instagram className="h-4 w-4" />
                  </a>
                ) : null}
                {settings.facebook_url ? (
                  <a
                    href={settings.facebook_url}
                    target="_blank"
                    rel="noreferrer"
                    aria-label="Facebook"
                    className="h-9 w-9 grid place-items-center rounded-full bg-primary text-primary-foreground shadow-sm hover:scale-110 hover:shadow-md transition"
                  >
                    <Facebook className="h-4 w-4" />
                  </a>
                ) : null}
                {settings.youtube_url ? (
                  <a
                    href={settings.youtube_url}
                    target="_blank"
                    rel="noreferrer"
                    aria-label="YouTube"
                    className="h-9 w-9 grid place-items-center rounded-full bg-primary text-primary-foreground shadow-sm hover:scale-110 hover:shadow-md transition"
                  >
                    <Youtube className="h-4 w-4" />
                  </a>
                ) : null}
                {settings.twitter_url ? (
                  <a
                    href={settings.twitter_url}
                    target="_blank"
                    rel="noreferrer"
                    aria-label="X (Twitter)"
                    className="h-9 w-9 grid place-items-center rounded-full bg-primary text-primary-foreground shadow-sm hover:scale-110 hover:shadow-md transition"
                  >
                    <XLogo className="h-3.5 w-3.5" />
                  </a>
                ) : null}
                {settings.contact_email ? (
                  <a
                    href={`mailto:${settings.contact_email}`}
                    aria-label="Email"
                    className="h-9 w-9 grid place-items-center rounded-full bg-primary text-primary-foreground shadow-sm hover:scale-110 hover:shadow-md transition"
                  >
                    <Mail className="h-4 w-4" />
                  </a>
                ) : null}
                {settings.contact_phone ? (
                  <a
                    href={`tel:${settings.contact_phone.replace(/\s+/g, "")}`}
                    aria-label="Call us"
                    className="h-9 w-9 grid place-items-center rounded-full bg-primary text-primary-foreground shadow-sm hover:scale-110 hover:shadow-md transition"
                  >
                    <Phone className="h-4 w-4" />
                  </a>
                ) : null}
                {waHref ? (
                  <a
                    href={waHref}
                    target="_blank"
                    rel="noreferrer"
                    aria-label="WhatsApp"
                    className="h-9 w-9 grid place-items-center rounded-full bg-[#25D366] text-white shadow-sm hover:scale-110 hover:shadow-md transition"
                  >
                    <MessageCircle className="h-4 w-4" />
                  </a>
                ) : null}
              </div>
            </div>
          </div>

          <div>
            <div className="font-display text-base text-foreground mb-1.5 inline-block border-b-2 border-primary/40 pb-0.5">
              Letters from us
            </div>
            <p className="text-sm text-muted-foreground mb-2">
              Soft mornings, slow Sundays — delivered monthly.
            </p>
            <form className="glass rounded-full p-1.5 flex items-center" onSubmit={(e) => e.preventDefault()}>
              <input
                className="bg-transparent flex-1 px-3 py-2 text-sm outline-none placeholder:text-muted-foreground"
                placeholder="you@email.com"
                type="email"
                autoComplete="email"
                aria-label="Email for newsletter"
              />
              <button
                type="submit"
                className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-sm shrink-0"
              >
                Join
              </button>
            </form>
            <ul className="mt-3 space-y-1 text-xs text-muted-foreground">
              <li>· Early access to new drops</li>
              <li>· Subscriber-only offers</li>
              <li>· Care tips from our team</li>
            </ul>
          </div>
        </div>

        <div className="relative mt-3 pt-2.5 border-t border-border/60 flex flex-col sm:flex-row sm:justify-between items-center gap-2 text-xs text-muted-foreground">
          <div className="flex items-center gap-1.5 text-center sm:text-left">
            {settings.footer_copyright ? (
              <span>{settings.footer_copyright}</span>
            ) : (
              <>
                <span>
                  &copy; {new Date().getFullYear()}{" "}
                  {settings.legal_entity_name || settings.brand_name} &middot; Made with
                </span>
                <Heart className="h-3.5 w-3.5 fill-red-500 text-red-500" aria-label="love" />
                <span>in India</span>
              </>
            )}
          </div>
          <div className="flex items-center gap-4">
            <Link to="/privacy" className="story-link hover:text-foreground">
              Privacy
            </Link>
            <span aria-hidden>&middot;</span>
            <Link to="/terms" className="story-link hover:text-foreground">
              Terms
            </Link>
            <span aria-hidden>&middot;</span>
            <Link to="/shipping" className="story-link hover:text-foreground">
              Shipping &amp; returns
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
