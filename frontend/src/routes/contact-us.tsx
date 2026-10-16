import { createFileRoute } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { ArrowUpRight, ExternalLink, Mail, MapPin, Phone } from "lucide-react";
import { ContactForm } from "@/components/ContactForm";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import { canonicalUrl, SOCIAL_SHARE_IMAGE } from "@/lib/site-seo";
import { resolveContactEmail } from "@/lib/utils";
import motherAndBaby from "@/assets/lifestyle-mother.webp";

export const Route = createFileRoute("/contact-us")({
  head: () => ({
    meta: [
      { title: "Contact Us · Mini's n More" },
      {
        name: "description",
        content:
          "Contact us at Mini's n More — share a question, gifting note or order query. Our care team reads every message.",
      },
      { property: "og:title", content: "Contact Us · Mini's n More" },
      {
        property: "og:description",
        content: "Write to Mini's n More with your email, phone and comments. We're here for your minis.",
      },
      { property: "og:image", content: SOCIAL_SHARE_IMAGE },
      { name: "twitter:image", content: SOCIAL_SHARE_IMAGE },
    ],
    links: [
      { rel: "canonical", href: canonicalUrl("/contact-us") },
      { rel: "preload", as: "image", href: motherAndBaby, fetchPriority: "high" },
    ],
  }),
  component: ContactUsPage,
});

const ease = [0.22, 1, 0.36, 1] as const;

function mapsEmbedSrc(address: string): string {
  return `https://maps.google.com/maps?q=${encodeURIComponent(address)}&z=16&output=embed`;
}

function mapsExternalHref(address: string): string {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`;
}

type Channel = {
  key: string;
  icon: typeof Mail;
  label: string;
  value: string;
  href: string;
  external?: boolean;
};

function ContactUsPage() {
  const settings = useSiteSettings();
  const contactEmail = resolveContactEmail(settings.contact_email);
  const contactPhone = settings.contact_phone?.trim() || null;
  const office = settings.invoice_registered_office_address?.trim() || null;

  const channels: Channel[] = [
    {
      key: "email",
      icon: Mail,
      label: "Email",
      value: contactEmail,
      href: `mailto:${contactEmail}`,
    },
    ...(contactPhone
      ? [
          {
            key: "phone",
            icon: Phone,
            label: "Call",
            value: contactPhone,
            href: `tel:${contactPhone.replace(/\s+/g, "")}`,
          } satisfies Channel,
        ]
      : []),
  ];

  return (
    <div className="px-3 md:px-6 pb-10">
      <section className="relative overflow-hidden rounded-[2rem] min-h-[calc(100dvh-9.5rem)] bg-[hsl(var(--secondary)/0.22)]">
        <motion.img
          src={motherAndBaby}
          alt="Mother holding a smiling baby in Mini's n More organic cotton"
          className="absolute inset-0 h-full w-full object-cover object-[center_22%] md:object-[58%_18%] lg:object-[62%_14%]"
          initial={{ scale: 1 }}
          animate={{ scale: 1.04 }}
          transition={{ duration: 22, ease }}
          style={{ transformOrigin: "62% 20%" }}
        />

        <div className="relative z-10 flex min-h-[calc(100dvh-9.5rem)] flex-col justify-center p-4 sm:p-6 lg:p-10 xl:pl-14 gap-8 md:gap-10 py-10 md:py-12">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.55, delay: 0.08, ease }}
            className="max-w-xl"
          >
            <h1 className="font-display text-4xl sm:text-5xl md:text-6xl leading-[0.95] tracking-tight text-white drop-shadow-[0_8px_24px_rgba(40,20,10,0.45)]">
              Contact{" "}
              <motion.span
                className="font-script text-primary inline-block"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.7, delay: 0.2, ease }}
              >
                Us
              </motion.span>
            </h1>
            <p className="mt-4 md:mt-5 font-display text-xl sm:text-2xl md:text-[1.65rem] text-balance leading-snug text-white drop-shadow-[0_6px_18px_rgba(40,20,10,0.4)]">
              We&apos;d love to hear from you.
            </p>
            <p className="mt-3 text-sm md:text-base text-white/90 leading-relaxed max-w-md drop-shadow-[0_4px_14px_rgba(40,20,10,0.35)]">
              A sizing question, a gift for a new arrival, or a note about your mini&apos;s favourite knit — our care
              team reads every message.
            </p>
          </motion.div>

          <div className="grid lg:grid-cols-[0.85fr_1.15fr] gap-6 lg:gap-8 xl:gap-10 items-start w-full">
            <motion.aside
              initial={{ opacity: 0, y: 24 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.12, ease }}
              className="rounded-[1.75rem] md:rounded-[2rem] p-6 sm:p-7 md:p-8 space-y-6 bg-background border border-border/50 shadow-[0_20px_50px_-28px_rgba(74,55,40,0.35)]"
            >
              <div>
                <p className="text-[11px] uppercase tracking-[0.28em] text-primary">Reach us</p>
                <h2 className="font-display text-2xl md:text-3xl mt-2 text-balance leading-tight">
                  Prefer a direct line?
                </h2>
                <p className="mt-3 text-sm text-muted-foreground leading-relaxed">
                  Email or call — we respond personally, usually within one business day.
                </p>
              </div>

              <ul className="space-y-0 divide-y divide-border/70 border-y border-border/70">
                {channels.map((item, index) => (
                  <motion.li
                    key={item.key}
                    initial={{ opacity: 0, x: -12 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ duration: 0.45, delay: 0.18 + index * 0.08, ease }}
                  >
                    <a
                      href={item.href}
                      target={item.external ? "_blank" : undefined}
                      rel={item.external ? "noreferrer" : undefined}
                      className="group flex items-center gap-4 py-4 outline-none focus-visible:ring-2 focus-visible:ring-primary/40 focus-visible:ring-offset-2 rounded-sm"
                    >
                      <span className="h-11 w-11 rounded-full bg-primary/10 text-primary grid place-items-center shrink-0 transition-colors duration-300 group-hover:bg-primary group-hover:text-primary-foreground">
                        <item.icon className="h-4 w-4" aria-hidden />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block text-[10px] uppercase tracking-[0.22em] text-muted-foreground">
                          {item.label}
                        </span>
                        <span className="block mt-0.5 text-sm md:text-[15px] font-medium truncate group-hover:text-primary transition-colors">
                          {item.value}
                        </span>
                      </span>
                      <ArrowUpRight
                        className="h-4 w-4 text-muted-foreground/50 shrink-0 transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-primary"
                        aria-hidden
                      />
                    </a>
                  </motion.li>
                ))}
              </ul>
            </motion.aside>

            <motion.section
              initial={{ opacity: 0, y: 28 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.65, delay: 0.16, ease }}
              className="rounded-[1.75rem] md:rounded-[2rem] overflow-hidden bg-background border border-border/50 shadow-[0_20px_50px_-28px_rgba(74,55,40,0.35)]"
            >
              <div
                aria-hidden
                className="h-1 bg-gradient-to-r from-primary/20 via-primary to-accent/40"
              />
              <div className="p-6 sm:p-8 md:p-10">
                <div className="mb-7 md:mb-8">
                  <p className="text-[11px] uppercase tracking-[0.28em] text-primary">Write to us</p>
                  <h2 className="font-display text-2xl md:text-3xl mt-2 text-balance leading-tight">
                    Send a note for your <span className="font-script text-primary">minis</span>.
                  </h2>
                  <p className="mt-2.5 text-sm text-muted-foreground leading-relaxed max-w-lg">
                    Tell us what you need — gifting help, an order query, or a thought about our organic knits.
                  </p>
                </div>
                <ContactForm />
              </div>
            </motion.section>
          </div>

          {office ? (
            <motion.section
              initial={{ opacity: 0, y: 28 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.65, delay: 0.22, ease }}
              className="rounded-[1.75rem] md:rounded-[2rem] overflow-hidden w-full bg-background border border-border/50 shadow-[0_20px_50px_-28px_rgba(74,55,40,0.35)]"
              aria-labelledby="visit-us-heading"
            >
              <div className="grid lg:grid-cols-[0.9fr_1.1fr] gap-0 items-stretch">
                <div className="p-6 sm:p-8 md:p-10 flex flex-col justify-center">
                  <p className="text-[11px] uppercase tracking-[0.28em] text-primary">Visit us</p>
                  <h2 id="visit-us-heading" className="font-display text-2xl md:text-3xl mt-2 text-balance leading-tight">
                    Our office address
                  </h2>
                  <p className="mt-4 flex items-start gap-3 text-sm md:text-[15px] text-foreground/80 leading-relaxed">
                    <MapPin className="h-4 w-4 text-primary mt-0.5 shrink-0" aria-hidden />
                    <span>{office}</span>
                  </p>
                  <a
                    href={mapsExternalHref(office)}
                    target="_blank"
                    rel="noreferrer"
                    className="mt-5 inline-flex items-center gap-2 text-sm font-medium text-primary story-link w-fit"
                  >
                    Open in Google Maps
                    <ExternalLink className="h-3.5 w-3.5" aria-hidden />
                  </a>
                </div>
                <div className="relative min-h-[220px] sm:min-h-[260px] lg:min-h-[300px] bg-muted/30">
                  <iframe
                    title="Mini's n More office location on Google Maps"
                    src={mapsEmbedSrc(office)}
                    className="absolute inset-0 h-full w-full border-0"
                    loading="lazy"
                    referrerPolicy="no-referrer-when-downgrade"
                    allowFullScreen
                  />
                </div>
              </div>
            </motion.section>
          ) : null}
        </div>
      </section>
    </div>
  );
}
