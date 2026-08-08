import { beforeEach, describe, expect, it, vi } from "vitest";

async function loadStorefront(mocks: {
  products?: any[];
  categories?: any[];
  blogPosts?: any[];
  policy?: any | null;
}) {
  vi.resetModules();
  const listProducts = vi.fn(async () => mocks.products ?? []);
  const listCategories = vi.fn(async () => mocks.categories ?? []);
  const listBlogPosts = vi.fn(async () => mocks.blogPosts ?? []);
  const getPolicy = vi.fn(async (slug: string) => {
    const row = mocks.policy;
    if (!row || row.slug !== slug) return null;
    return row;
  });
  vi.doMock("@/lib/backend-data", () => ({
    listProducts,
    listCategories,
    listBlogPosts,
    getPolicy,
  }));
  return import("./storefront");
}

describe("storefront cache", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.resetModules();
  });

  it("maps live products and notifies subscribers", async () => {
    const listProducts = [
      {
        id: "p1",
        slug: "romper",
        name: "Romper",
        description: "Soft muslin romper",
        story: null,
        price: 999,
        compare_at_price: null,
        images: ["/romper.jpg"],
        sizes: ["0-3m"],
        colors: ["cream"],
        age_min_months: 0,
        age_max_months: 3,
        badge: "New",
        featured: false,
        best_seller: false,
        new_arrival: true,
        trending: false,
        active: true,
      },
    ];
    const storefront = await loadStorefront({ products: listProducts });
    const subscriber = vi.fn();
    storefront.subscribeProducts(subscriber);

    const products = await storefront.loadProducts();

    expect(products[0]).toMatchObject({
      slug: "romper",
      image: "/romper.jpg",
      ages: ["0-3m"],
      fabric: "Muslin",
      isNew: true,
    });
    expect(storefront.getCachedProducts()).toBe(products);
    expect(subscriber).toHaveBeenCalledWith(products);
    await storefront.loadProducts();
  });

  it("keeps print images off the card hero when product gallery is empty", async () => {
    const storefront = await loadStorefront({
      products: [
        {
          id: "p2",
          slug: "tying-cap",
          name: "Tying Cap",
          description: "Cap",
          story: null,
          price: 799,
          compare_at_price: 999,
          images: [],
          sizes: ["0-3 M"],
          colors: [{ name: "Print 1", imageUrls: ["https://cdn.example.com/print1.jpg"] }],
          age_min_months: 0,
          age_max_months: 3,
          badge: null,
          featured: false,
          best_seller: false,
          new_arrival: true,
          trending: false,
          active: true,
        },
      ],
    });

    const products = await storefront.loadProducts();
    expect(products[0]?.image).toBe("https://cdn.example.com/print1.jpg");
    expect(products[0]?.gallery).toEqual(["https://cdn.example.com/print1.jpg"]);
    expect(products[0]?.colors[0]?.imageUrls).toEqual(["https://cdn.example.com/print1.jpg"]);
  });

  it("resolves category slug from categories when product DTO omits slug", async () => {
    const storefront = await loadStorefront({
      categories: [
        {
          id: "cat-tshirt",
          slug: "tshirt",
          name: "T-Shirt",
          description: null,
          image_url: null,
          sort_order: 1,
          active: true,
        },
      ],
      products: [
        {
          id: "p4",
          slug: "tee",
          name: "Baby Tee",
          description: "",
          story: null,
          price: 100,
          compare_at_price: null,
          images: [],
          sizes: [],
          colors: [],
          age_min_months: null,
          age_max_months: null,
          badge: null,
          featured: false,
          best_seller: false,
          new_arrival: false,
          trending: false,
          category_id: "cat-tshirt",
          category_slug: "t-shirt",
          category_name: "T-Shirt",
          active: true,
        },
      ],
    });
    const products = await storefront.loadProducts();
    expect(products[0].categorySlug).toBe("tshirt");
    expect(products[0].categoryName).toBe("T-Shirt");
  });

  it("maps category slug for shop-by-category filtering", async () => {
    const listProducts = [
      {
        id: "p3",
        slug: "vest",
        name: "Vest",
        description: "Warm vest",
        story: null,
        price: 599,
        compare_at_price: null,
        images: [],
        sizes: [],
        colors: [],
        age_min_months: null,
        age_max_months: null,
        badge: null,
        featured: false,
        best_seller: false,
        new_arrival: false,
        trending: false,
        category_slug: "outerwear",
        category_name: "Outerwear",
        active: true,
      },
    ];
    const storefront = await loadStorefront({ products: listProducts });
    const products = await storefront.loadProducts();
    expect(products[0].categorySlug).toBe("outerwear");
    expect(products[0].category).toBe("Newborn");
  });

  it("aligns list price with default sellable variant, not parent min", async () => {
    const storefront = await loadStorefront({
      products: [
        {
          id: "p-shorts",
          slug: "basic-shorts",
          name: "BASIC SHORTS",
          description: "Shorts",
          story: null,
          price: 1.64,
          compare_at_price: 2,
          images: ["/shorts.jpg"],
          sizes: ["0-3m", "3-6m"],
          colors: ["Snail Blue", "Bee Blue"],
          age_min_months: 0,
          age_max_months: 6,
          badge: null,
          featured: false,
          best_seller: false,
          new_arrival: false,
          trending: false,
          active: true,
          item_structure: "VARIANTS",
          variants_json: JSON.stringify([
            { sku: "BASIC-SHORTS-03-SNAIL-BLUE", size: "0-3m", color: "Snail Blue", rate: 320, mrp: 400, stock: 5, active: true },
            { sku: "BASIC-SHORTS-36-BEE-BLUE", size: "3-6m", color: "Bee Blue", rate: 1.64, mrp: 2, stock: 35, active: true },
          ]),
        },
      ],
    });
    const products = await storefront.loadProducts();
    expect(products[0].price).toBe(320);
    expect(products[0].compareAt).toBe(400);
  });

  it("does not treat newborn badge text as new arrival", async () => {
    const listProducts = [
      {
        id: "p2",
        slug: "onesie",
        name: "Onesie",
        description: "Cozy",
        story: null,
        price: 799,
        compare_at_price: null,
        images: [],
        sizes: [],
        colors: [],
        age_min_months: null,
        age_max_months: null,
        badge: "newborn essentials",
        featured: false,
        best_seller: false,
        new_arrival: false,
        trending: false,
        active: true,
      },
    ];
    const storefront = await loadStorefront({ products: listProducts });
    const products = await storefront.loadProducts();
    expect(products[0].isNew).toBe(true);
  });

  it("invalidateProducts clears cache before reloading", async () => {
    const initialProducts = [
      {
        id: "p1",
        slug: "a",
        name: "A",
        description: "",
        story: null,
        price: 100,
        compare_at_price: null,
        images: [],
        sizes: [],
        colors: [],
        age_min_months: null,
        age_max_months: null,
        badge: null,
        featured: false,
        best_seller: false,
        new_arrival: false,
        trending: false,
        active: true,
      },
    ];
    const storefront = await loadStorefront({ products: initialProducts });
    const first = await storefront.loadProducts();
    expect(first[0].slug).toBe("a");
    storefront.invalidateProducts();
    await vi.waitFor(() => expect(storefront.getCachedProducts()?.[0]?.slug).toBe("a"));
  });

  it("falls back to seed products when the backend is empty", async () => {
    const storefront = await loadStorefront({ products: [] });
    const products = await storefront.loadProducts();
    expect(products.length).toBeGreaterThan(0);
  });

  it("maps blog posts and falls back to excerpts for body content", async () => {
    const storefront = await loadStorefront({
      blogPosts: [
        {
          slug: "care",
          title: "Care Guide",
          excerpt: "Wash gently",
          content: "",
          cover_url: "/cover.jpg",
          published_at: "2026-05-23T00:00:00.000Z",
          published: true,
        },
      ],
    });
    const subscriber = vi.fn();
    storefront.subscribeBlogPosts(subscriber);

    const posts = await storefront.loadBlogPosts();

    expect(posts[0]).toMatchObject({
      slug: "care",
      cover: "/cover.jpg",
      tag: "Blogs",
      body: ["Wash gently"],
    });
    expect(posts[0].readMins).toBeGreaterThanOrEqual(2);
    expect(subscriber).toHaveBeenCalledWith(posts);
  });

  it("caches policy lookups and notifies slug subscribers", async () => {
    const policy = {
      slug: "privacy",
      title: "Privacy",
      body: "Body",
    };
    const storefront = await loadStorefront({ policy });
    const subscriber = vi.fn();
    storefront.subscribePolicy("privacy", subscriber);

    await expect(storefront.loadPolicy("privacy")).resolves.toEqual({
      slug: "privacy",
      title: "Privacy",
      body: "Body",
    });
    expect(storefront.getCachedPolicy("privacy")).toMatchObject({ title: "Privacy" });
    expect(subscriber).toHaveBeenCalledWith({ slug: "privacy", title: "Privacy", body: "Body" });

    await storefront.loadPolicy("privacy");
  });

  it("caches null when backend policy lookup misses", async () => {
    const storefront = await loadStorefront({ policy: null });
    vi.spyOn(console, "error").mockImplementation(() => undefined);

    await expect(storefront.loadPolicy("missing")).resolves.toBeNull();
    expect(storefront.getCachedPolicy("missing")).toBeNull();

    await storefront.loadPolicy("missing");
  });
});
