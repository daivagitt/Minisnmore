import { Link } from "@tanstack/react-router";
import { Fragment, useMemo, type ReactNode } from "react";
import {
  Facebook,
  Heart,
  Instagram,
  Leaf,
  Linkedin,
  Mail,
  Phone,
  Youtube,
} from "lucide-react";
import { useCategories } from "@/hooks/useCategories";
import { usePageVisibility } from "@/hooks/usePageVisibility";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { resolveContactEmail, toDirectDownloadUrl } from "@/lib/utils";
import footerLogo from "@/assets/logo-footer.png";

const defaultFooterTagline =
  "Minis today, memories forever. Premium organic clothing for the first 24 months — softly made in India, gently loved everywhere.";

const COLLECTION_LINKS = [
  { to: "/best-sellers" as const, label: "Best sellers" },
  { to: "/new-arrivals" as const, label: "New arrivals" },
  { to: "/trending" as const, label: "Trending" },
];

type FooterShopLink =
  | { key: string; label: string; to: "/shop"; category: string }
  | { key: string; label: string; to: "/gift" };

function formatShopLabel(label: string) {
  const text = label.trim();
  if (!text) return text;
  return text.charAt(0).toUpperCase() + text.slice(1);
}

const FOOTER_LINK_ROW = "h-5 flex items-center text-sm leading-none";
const FOOTER_LINK_STACK = "flex flex-col gap-2";
const FOOTER_TOP_GRID =
  "grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-y-6 gap-x-4 sm:gap-x-5 lg:gap-x-6 xl:gap-x-8 items-start";

function splitShopColumns(links: FooterShopLink[]) {
  const mid = Math.ceil(links.length / 2);
  return [links.slice(0, mid), links.slice(mid)] as const;
}

function FooterNavList({ children }: { children: ReactNode }) {
  return <ul className={FOOTER_LINK_STACK}>{children}</ul>;
}

function FooterNavItem({ children }: { children: ReactNode }) {
  return <li className={FOOTER_LINK_ROW}>{children}</li>;
}

function FooterColumn({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return <div className={`min-w-0 ${className}`.trim()}>{children}</div>;
}

function FooterHeading({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={`font-display text-base text-foreground mb-2 inline-block border-b-2 border-primary/40 pb-0.5 ${className}`.trim()}
    >
      {children}
    </div>
  );
}

function FooterSubheading({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={`text-[11px] uppercase tracking-[0.22em] text-muted-foreground shrink-0 ${className}`.trim()}
    >
      {children}
    </span>
  );
}

function ShopFooterLink({ item }: { item: FooterShopLink }) {
  if (item.to === "/shop") {
    return (
      <Link
        to="/shop"
        search={{ category: item.category }}
        className="story-link text-muted-foreground hover:text-foreground"
      >
        {formatShopLabel(item.label)}
      </Link>
    );
  }
  return (
    <Link to={item.to} className="story-link text-muted-foreground hover:text-foreground">
      {item.label}
    </Link>
  );
}

function socialHref(url: string | null | undefined) {
  const trimmed = url?.trim();
  return trimmed ? trimmed : null;
}

const FOOTER_CONTACT_VALUE_LINK =
  "hover:text-foreground transition-colors cursor-pointer underline-offset-2 hover:underline";

function FooterContactValueLink({
  href,
  children,
  ariaLabel,
}: {
  href: string;
  children: ReactNode;
  ariaLabel?: string;
}) {
  return (
    <a href={href} className={FOOTER_CONTACT_VALUE_LINK} aria-label={ariaLabel}>
      {children}
    </a>
  );
}

function SocialIconLink({
  href,
  label,
  children,
}: {
  href: string | null;
  label: string;
  children: ReactNode;
}) {
  const className =
    "h-9 w-9 grid place-items-center rounded-full bg-primary text-primary-foreground shadow-sm hover:scale-110 hover:shadow-md transition";
  if (href) {
    return (
      <a href={href} target="_blank" rel="noreferrer" aria-label={label} className={className}>
        {children}
      </a>
    );
  }
  return (
    <span aria-label={label} className={`${className} opacity-90`} title={`${label} link coming soon`}>
      {children}
    </span>
  );
}

export function Footer() {
  const { isVisible } = usePageVisibility();
  const categories = useCategories();
  const settings = useSiteSettings();
  const shopLinks = useMemo(() => {
    const links: FooterShopLink[] = [];
    if (isVisible("shop")) {
      for (const category of categories) {
        links.push({
          key: category.id,
          label: category.name,
          to: "/shop",
          category: category.slug,
        });
      }
    }
    if (isVisible("gift")) {
      links.push({ key: "gift", label: "Gifting", to: "/gift" });
    }
    return links;
  }, [categories, isVisible]);
  const [shopColLeft, shopColRight] = splitShopColumns(shopLinks);
  const shopRowCount = Math.max(shopColLeft.length, shopColRight.length);
  const logo = settings.logo_url || footerLogo;
  const tagline = settings.footer_tagline || defaultFooterTagline;
  const contactEmail = settings.contact_email ? resolveContactEmail(settings.contact_email) : null;
  const mailtoHref = contactEmail ? `mailto:${contactEmail}` : null;

  return (
    <footer className="relative mt-10">
      <div className="relative mx-4 md:mx-6 mb-3 glass-strong rounded-[2rem] px-5 py-4 sm:px-6 sm:py-5 md:px-8 overflow-hidden">
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

        <div className={`relative ${FOOTER_TOP_GRID}`}>
            <FooterColumn className="sm:col-span-2 lg:col-span-1">
            <div className="mx-auto w-full max-w-[13rem] sm:max-w-[14rem] text-left lg:mx-0">
              <img
                src={logo}
                alt={settings.brand_name}
                className="mb-2 block h-20 sm:h-24 md:h-28 w-full object-contain object-left object-[left_top]"
                style={{ background: "transparent" }}
                loading="lazy"
                decoding="async"
              />
              <div className="pl-[calc(15%-0.5rem)]">
            {tagline.trim() ? (
              <p className="text-sm text-muted-foreground leading-snug">{tagline}</p>
            ) : null}
            <div className="mt-2 text-xs text-muted-foreground space-y-0.5">
              {settings.legal_entity_name ? (
                <div className="text-foreground/80 font-medium">{settings.legal_entity_name}</div>
              ) : null}
              {settings.contact_phone ? (
                <div>
                  {settings.care_label || "Care"} Number:{" "}
                  <FooterContactValueLink
                    href={`tel:${settings.contact_phone.replace(/\s+/g, "")}`}
                    ariaLabel={`Call ${settings.contact_phone}`}
                  >
                    {settings.contact_phone}
                  </FooterContactValueLink>
                </div>
              ) : null}
              {contactEmail && mailtoHref ? (
                <div>
                  Email:{" "}
                  <FooterContactValueLink href={mailtoHref} ariaLabel={`Email ${contactEmail}`}>
                    {contactEmail}
                  </FooterContactValueLink>
                </div>
              ) : null}
            </div>
              </div>
            </div>
          </FooterColumn>

          <FooterColumn className="shrink-0">
                <FooterHeading>Shop</FooterHeading>
                <div
                  className="grid w-fit max-w-full grid-cols-[auto_auto] gap-x-2 sm:gap-x-3 gap-y-2"
                  aria-label="Shop categories"
                >
                  {Array.from({ length: shopRowCount }, (_, row) => (
                    <Fragment key={`shop-row-${row}`}>
                      <div className={FOOTER_LINK_ROW}>
                        {shopColLeft[row] ? <ShopFooterLink item={shopColLeft[row]} /> : null}
                      </div>
                      <div className={FOOTER_LINK_ROW}>
                        {shopColRight[row] ? <ShopFooterLink item={shopColRight[row]} /> : null}
                      </div>
                    </Fragment>
                  ))}
                </div>
              </FooterColumn>

          <FooterColumn className="shrink-0">
                <FooterHeading>Collections</FooterHeading>
                <FooterNavList>
                  {COLLECTION_LINKS.map((item) => (
                    <FooterNavItem key={item.to}>
                      <Link to={item.to} className="story-link text-muted-foreground hover:text-foreground">
                        {item.label}
                      </Link>
                    </FooterNavItem>
                  ))}
                </FooterNavList>
              </FooterColumn>

          <FooterColumn className="shrink-0">
                <FooterHeading>Care</FooterHeading>
                <FooterNavList>
                  {isVisible("track") ? (
                    <FooterNavItem>
                      <Link to="/track" className="story-link text-muted-foreground hover:text-foreground">
                        Order tracking
                      </Link>
                    </FooterNavItem>
                  ) : null}
                  {isVisible("subscribe") ? (
                    <FooterNavItem>
                      <Link to="/subscribe" className="story-link text-muted-foreground hover:text-foreground">
                        Subscriptions
                      </Link>
                    </FooterNavItem>
                  ) : null}
                  <FooterNavItem>
                    <Link to="/account" className="story-link text-muted-foreground hover:text-foreground">
                      My account
                    </Link>
                  </FooterNavItem>
                  <FooterNavItem>
                    <Link to="/about" className="story-link text-muted-foreground hover:text-foreground">
                      About us
                    </Link>
                  </FooterNavItem>
                  <FooterNavItem>
                    <a
                      href={toDirectDownloadUrl(settings.ecatalogue_url) || "/e-catalogue.pdf"}
                      download
                      className="story-link text-muted-foreground hover:text-foreground"
                    >
                      Download e-catalogue
                    </a>
                  </FooterNavItem>
                </FooterNavList>
              </FooterColumn>

          <div className="sm:col-span-2 lg:col-span-3 lg:col-start-2 lg:row-start-2 flex flex-wrap items-center w-full max-w-full gap-x-2 gap-y-2">
              <FooterSubheading>Follow us</FooterSubheading>
              <div className="flex items-center gap-1.5">
                <SocialIconLink href={socialHref(settings.instagram_url)} label="Instagram">
                  <Instagram className="h-4 w-4" />
                </SocialIconLink>
                <SocialIconLink href={socialHref(settings.facebook_url)} label="Facebook">
                  <Facebook className="h-4 w-4" />
                </SocialIconLink>
                <SocialIconLink href={socialHref(settings.linkedin_url)} label="LinkedIn">
                  <Linkedin className="h-4 w-4" />
                </SocialIconLink>
                <SocialIconLink href={socialHref(settings.youtube_url)} label="YouTube">
                  <Youtube className="h-4 w-4" />
                </SocialIconLink>
              </div>
              <div className="flex flex-wrap items-center gap-2 sm:ml-2 sm:pl-2 sm:border-l border-border/40">
                <FooterSubheading>Contact us</FooterSubheading>
                <div className="flex items-center gap-1.5">
                  {mailtoHref ? (
                    <a
                      href={mailtoHref}
                      aria-label={`Email ${contactEmail}`}
                      title={contactEmail ?? undefined}
                      className="h-9 w-9 shrink-0 grid place-items-center rounded-full bg-primary text-primary-foreground shadow-sm hover:scale-110 hover:shadow-md transition"
                    >
                      <Mail className="h-4 w-4" />
                    </a>
                  ) : null}
                  {settings.contact_phone ? (
                    <a
                      href={`tel:${settings.contact_phone.replace(/\s+/g, "")}`}
                      aria-label="Call us"
                      className="h-9 w-9 shrink-0 grid place-items-center rounded-full bg-primary text-primary-foreground shadow-sm hover:scale-110 hover:shadow-md transition"
                    >
                      <Phone className="h-4 w-4" />
                    </a>
                  ) : null}
                </div>
              </div>
          </div>
        </div>

        <div className="relative mt-3 pt-2.5 border-t border-border/60 space-y-2 text-xs text-muted-foreground">
          <div className="flex items-center justify-center gap-1.5 text-center">
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
        </div>
      </div>
    </footer>
  );
}
