import lifestyle from "@/assets/lifestyle-mother.webp";
import heroFounders from "@/assets/hero-founders-atelier.jpg";
import newborn from "@/assets/cat-newborn.webp";

export type Review = {
  title: string;
  body: string;
  rating: number;
  author: string;
  date: string;
  photo: string;
};

/** Shown when no reviews are configured in Admin → Home sections. */
export const defaultReviews: Review[] = [
  {
    title: "Softest muslin we own",
    body: "The swaddle set feels feather-light and washes beautifully. My daughter sleeps so much better wrapped in it.",
    rating: 5,
    author: "Priya S., Bengaluru",
    date: "Mar 2025",
    photo: lifestyle,
  },
  {
    title: "Perfect naming-day gift",
    body: "Ordered the gift service for my niece's ceremony — the wrapping was stunning and the note card felt truly personal.",
    rating: 5,
    author: "Ananya M., Hyderabad",
    date: "Feb 2025",
    photo: heroFounders,
  },
  {
    title: "Gentle on newborn skin",
    body: "Zero irritation on day one. The organic cotton romper is now our go-to for every outing.",
    rating: 5,
    author: "Meera K., Chennai",
    date: "Jan 2025",
    photo: newborn,
  },
  {
    title: "Heirloom quality",
    body: "Stitching and fabric feel premium. We're saving pieces for baby number two — they still look new after many washes.",
    rating: 5,
    author: "Divya R., Pune",
    date: "Dec 2024",
    photo: lifestyle,
  },
  {
    title: "Beautiful colours, true to photos",
    body: "The blush terracotta set matched the website exactly. Fast delivery and lovely packaging too.",
    rating: 5,
    author: "Kavya N., Mumbai",
    date: "Nov 2024",
    photo: heroFounders,
  },
];
