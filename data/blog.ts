export type BlogPost = {
  slug: string;
  title: string;
  excerpt: string;
  cover: string;
  readMins: number;
  date: string;
  tag: string;
  body: string[];
};

import cover1 from "@/assets/p-swaddle-cream.jpg";
import cover2 from "@/assets/p-romper-peach.jpg";
import cover3 from "@/assets/p-sleepsuit-sage.jpg";
import cover4 from "@/assets/p-cardigan-cream.jpg";
import cover5 from "@/assets/p-dress-pink.jpg";
import cover6 from "@/assets/p-overalls-beige.jpg";
import cover7 from "@/assets/gift-box.jpg";
import cover8 from "@/assets/lifestyle-mother.jpg";

export const posts: BlogPost[] = [
  {
    slug: "the-art-of-gifting-for-new-parents",
    title: "The art of gifting for new parents",
    excerpt: "Skip the generic hampers. Here's how to give a gift that's actually useful, beautiful, and remembered long after the first wash.",
    cover: cover7,
    readMins: 5,
    date: "Jun 12, 2026",
    tag: "Gifting",
    body: [
      "Most baby gifts are opened, smiled at, and then tucked into a cupboard. The ones that stay in rotation share three traits: they solve a real problem, they age gracefully, and they don't add to the clutter of outgrown clothes.",
      "Organic cotton swaddles and sleepsuits in 0-3 months are safe bets — every parent uses them, and quality ones survive dozens of washes. A gift card bundled with a single beautiful piece says 'I thought about this' more than a basket of things no one asked for.",
      "Size up, not down. A 6-9 month romper gifted to a newborn parent feels like a promise: you're in this for the long haul, and I see that. Include a short note explaining why you chose the piece — it transforms a garment into a memory.",
      "Our gifting collection is packaged in seed-paper boxes that can be planted after unwrapping. The box becomes wildflowers; the clothes become heirlooms. That's the kind of gift people talk about years later.",
    ],
  },
  {
    slug: "newborn-sleep-and-what-they-wear",
    title: "Newborn sleep: how the right clothing helps them rest longer",
    excerpt: "Temperature, fabric, and fit all play a role in how well a baby sleeps. Here's what the first-year wardrobe should get right.",
    cover: cover8,
    readMins: 6,
    date: "May 25, 2026",
    tag: "Sleep",
    body: [
      "Newborns spend 14 to 17 hours a day sleeping, but rarely in long stretches. One of the most overlooked reasons for frequent wake-ups is discomfort from clothing that's either too warm, too tight, or made from synthetic blends that trap heat and moisture.",
      "The ideal sleep outfit is a single layer of organic cotton or muslin, loose enough to allow natural movement but fitted enough that fabric doesn't bunch around the face. Avoid drawstrings, decorative buttons on the back, and any piece that requires frequent adjustment.",
      "Room temperature matters more than blanket thickness. In Indian homes, 24 to 26 degrees Celsius is the sweet spot. At this range, a short-sleeve onesie with a light muslin swaddle is usually enough. If the back of your baby's neck feels damp, remove a layer; if it feels cool, add one.",
      "Watch for signs of fabric-related wakefulness: squirming against seams, redness at elastic bands, or sweating at the nape. Switching to seam-free organic pieces and flat-waist bottoms often adds 30 to 45 minutes to the first sleep stretch — small for you, significant for a newborn.",
    ],
  },
  {
    slug: "why-organic-cotton-matters-for-newborns",
    title: "Why organic cotton matters for newborn skin",
    excerpt: "Newborn skin is up to five times thinner than ours. Here's why 100% Certified Organic Cotton is the gentlest first layer.",
    cover: cover1,
    readMins: 5,
    date: "May 2, 2026",
    tag: "Fabric 101",
    body: [
      "A baby's skin is a miracle in progress — thinner, more porous, and far quicker to react than our own. Anything they wear in the first year sits against that delicate barrier for 20+ hours a day, which is why fabric choice is one of the quietest, most important decisions a parent makes.",
      "Conventional cotton is grown with heavy pesticide use, then bleached and softened with chemical finishes that can linger in the weave. 100% Certified Organic Cotton skips all of that — it's grown without synthetic pesticides, processed without harsh chlorines, and tested for residues at every stage.",
      "The result is a fabric that feels softer with every wash instead of stiffer, breathes naturally, and is far less likely to trigger the redness, dryness, and tiny prickly heat bumps that make babies cranky. For us, organic cotton isn't a marketing word — it's the bare minimum.",
      "When you shop with us, every newborn-stage piece is 100% certified organic, dyed with low-impact dyes, and finished with coconut shell or wood buttons — never plastic snaps near the skin.",
    ],
  },
  {
    slug: "say-goodbye-to-rash-rash-friendly-clothing",
    title: "Say goodbye to baby rash: a guide to rash-friendly clothing",
    excerpt: "Most baby rashes start with the wrong fabric, the wrong wash, or the wrong fit. Here's the simple checklist we follow.",
    cover: cover2,
    readMins: 6,
    date: "Apr 22, 2026",
    tag: "Care guide",
    body: [
      "Rashes are rarely random. The three biggest culprits — in order — are synthetic blends that trap heat, harsh detergents that leave residue, and tight elastic that rubs the same spot all day. Fix those three and most pediatric \"mystery rashes\" simply disappear.",
      "Look for: 100% natural fibers (organic cotton, muslin, bamboo), flat seams or seam-free construction, soft-tape elastic instead of woven elastic, and labels printed onto the garment instead of stitched-in tags.",
      "Wash in fragrance-free, dye-free detergent and skip fabric softener entirely — softener leaves a film that blocks the fabric's natural breathability. A second rinse cycle for newborn loads is a small habit that pays off all year.",
      "Finally, size up before you size down. A slightly roomy romper lets air move; a snug one traps sweat in the folds of the neck, thighs and underarms — exactly where rashes love to bloom.",
    ],
  },
  {
    slug: "muslin-magic-the-fabric-that-grows-softer",
    title: "Muslin magic: the fabric that gets softer every wash",
    excerpt: "Why double-gauze muslin is the most-used piece in every parent's drawer — from swaddles to summer dresses.",
    cover: cover3,
    readMins: 4,
    date: "Apr 10, 2026",
    tag: "Fabric 101",
    body: [
      "Muslin is woven loosely on purpose. Those tiny visible gaps are what let body heat escape, which is why a muslin-swaddled baby sleeps cooler in summer and surprisingly warm in winter (the fabric traps still air against the skin).",
      "The first wash shrinks the weave just slightly, locking the threads. From the second wash onwards, every cycle softens the cotton fibers — a good muslin at six months feels nothing like the day you brought it home.",
      "Use the same square as a swaddle, a nursing cover, a stroller shade, a burp cloth, a play mat, and eventually a comfort blanket. Few baby items earn their keep like a stack of well-loved muslins.",
    ],
  },
  {
    slug: "decoding-baby-clothing-labels",
    title: "Decoding baby clothing labels: what really matters",
    excerpt: "100% Certified Organic, BCI, hypoallergenic — what each label actually promises, and which ones you can safely ignore.",
    cover: cover4,
    readMins: 5,
    date: "Mar 28, 2026",
    tag: "Buying guide",
    body: [
      "100% Certified Organic (Global Organic Textile Standard) is the strictest — it covers the whole chain from farm to finished garment, including dye chemistry, factory wages, and water use. If you only learn one label, learn this one.",
      "Independent fabric safety tests check the finished cloth for harmful substances — they don't certify how the cotton was grown, only that what reaches your baby's skin is below safety thresholds.",
      "BCI (Better Cotton Initiative) is a sustainability program, not a guarantee — BCI cotton is often blended with conventional cotton in the supply chain. \"Hypoallergenic\" and \"dermatologist-tested\" have no legal definition. Treat them as marketing, not proof.",
      "Our rule: 100% Certified Organic for everything that touches skin, independently tested trims and accessories, and full transparency about where every piece is made.",
    ],
  },
  {
    slug: "dressing-baby-by-season",
    title: "Dressing baby by season: a simple Indian-weather playbook",
    excerpt: "From Mumbai monsoons to Delhi winters — a layer-by-layer guide that actually works in Indian homes.",
    cover: cover5,
    readMins: 6,
    date: "Mar 14, 2026",
    tag: "Care guide",
    body: [
      "The thumb rule: babies need one more light layer than you do indoors, and the same number outdoors. \"Bundle them up\" is well-meaning advice that has caused more heat rashes than cold ones.",
      "Summer (Apr–Jun): single-layer muslin or organic cotton, sleeveless or short-sleeve, with a thin swaddle for AC sleep. Skip socks during the day.",
      "Monsoon (Jul–Sep): organic cotton with quick-dry properties, a light cardigan for damp evenings, and breathable cotton booties — never plastic.",
      "Winter (Nov–Feb): start with a bamboo or cotton base layer, add a knit cardigan, and only reach for fleece on the coldest nights. Cap, mittens and socks for stroller walks; remove them indoors so heat can escape from the head and feet.",
    ],
  },
  {
    slug: "circular-baby-fashion-a-quieter-way",
    title: "A quieter way to dress your baby",
    excerpt: "Babies outgrow clothes in weeks, not seasons. Here's how thoughtful choices keep wardrobes lighter and kinder.",
    cover: cover6,
    readMins: 4,
    date: "Feb 28, 2026",
    tag: "Sustainability",
    body: [
      "An average baby outgrows 7 sizes in the first 24 months. That's roughly 280 garments per child if you buy a modest 40 pieces per size — most of which still have years of life left.",
      "Passing pieces to siblings, cousins and parent circles extends garment life beautifully. Gently-worn pieces can continue their journey with a little care and mindful storage.",
      "It's a small habit with a big footprint: one resold romper saves around 2,700 litres of water versus producing a new one. Tiny clothes, smaller impact.",
    ],
  },
];