import { describe, expect, it } from "vitest";
import { galleryForSelectedColor } from "@/lib/product-gallery";

describe("galleryForSelectedColor", () => {
  it("uses per-colour gallery when the selected colour has images", () => {
    const result = galleryForSelectedColor(
      {
        image: "https://cdn.example.com/default.jpg",
        gallery: ["https://cdn.example.com/lifestyle.jpg"],
        colors: [
          {
            name: "Print 1",
            hex: "#d9bfa6",
            imageUrls: [
              "https://cdn.example.com/print1-front.jpg",
              "https://cdn.example.com/print1-back.jpg",
            ],
          },
          { name: "Print 2", hex: "#d9bfa6", imageUrl: "https://cdn.example.com/print2.jpg" },
        ],
      },
      "Print 1",
    );

    expect(result.image).toBe("https://cdn.example.com/print1-front.jpg");
    expect(result.gallery).toEqual(["https://cdn.example.com/print1-back.jpg"]);
  });

  it("keeps rear photos when a print-specific front image is mixed in a shared gallery", () => {
    const shared = [
      "https://cdn.example.com/navy-front.jpg",
      "https://cdn.example.com/rear.jpg",
    ];
    const result = galleryForSelectedColor(
      {
        image: shared[0],
        gallery: [],
        colors: [
          { name: "Navy", hex: "#2c3e58", imageUrls: shared },
          { name: "Ivory", hex: "#f2eee8", imageUrls: shared },
        ],
      },
      "Navy",
    );

    expect(result.image).toBe("https://cdn.example.com/navy-front.jpg");
    expect(result.gallery).toEqual(["https://cdn.example.com/rear.jpg"]);
  });

  it("does not attach another print's front or rear from a shared gallery", () => {
    const shared = [
      "https://cdn.example.com/navy-front.jpg",
      "https://cdn.example.com/navy-rear.jpg",
      "https://cdn.example.com/ivory-front.jpg",
      "https://cdn.example.com/ivory-rear.jpg",
    ];
    const navy = galleryForSelectedColor(
      {
        image: shared[0],
        gallery: [],
        colors: [
          { name: "Navy", hex: "#2c3e58", imageUrls: shared },
          { name: "Ivory", hex: "#f2eee8", imageUrls: shared },
        ],
      },
      "Navy",
    );

    expect(navy.image).toBe("https://cdn.example.com/navy-front.jpg");
    expect(navy.gallery).toEqual(["https://cdn.example.com/navy-rear.jpg"]);
    expect(navy.gallery.join(" ")).not.toContain("ivory");
  });

  it("does not use print swatches in the main gallery when no lifestyle photos exist", () => {
    const result = galleryForSelectedColor(
      {
        image: "https://cdn.example.com/brown-swatch.jpg",
        gallery: [
          "https://cdn.example.com/bird-swatch.jpg",
          "https://cdn.example.com/brown-swatch.jpg",
        ],
        colors: [
          {
            name: "BIRD",
            hex: "#d9bfa6",
            swatchUrl: "https://cdn.example.com/bird-swatch.jpg",
          },
          {
            name: "BROWN SWADDLE",
            hex: "#d9bfa6",
            swatchUrl: "https://cdn.example.com/brown-swatch.jpg",
          },
        ],
      },
      "BROWN SWADDLE",
    );

    expect(result.image).toBe("");
    expect(result.gallery).toEqual([]);
  });

  it("uses per-print lifestyle gallery when uploaded", () => {
    const result = galleryForSelectedColor(
      {
        image: "https://cdn.example.com/swatch.jpg",
        gallery: [],
        colors: [
          {
            name: "SNAIL",
            hex: "#d9bfa6",
            swatchUrl: "https://cdn.example.com/swatch.jpg",
            imageUrls: [
              "https://cdn.example.com/snail-1.jpg",
              "https://cdn.example.com/snail-2.jpg",
            ],
          },
        ],
      },
      "SNAIL",
    );

    expect(result.image).toBe("https://cdn.example.com/snail-1.jpg");
    expect(result.gallery).toEqual(["https://cdn.example.com/snail-2.jpg"]);
  });

  it("falls back to the product gallery when the colour has no images", () => {
    const result = galleryForSelectedColor(
      {
        image: "https://cdn.example.com/default.jpg",
        gallery: ["https://cdn.example.com/lifestyle.jpg"],
        colors: [{ name: "Cream", hex: "#f7e3d5" }],
      },
      "Cream",
    );

    expect(result.image).toBe("https://cdn.example.com/default.jpg");
    expect(result.gallery).toEqual(["https://cdn.example.com/lifestyle.jpg"]);
  });

  it("falls back to the product hero when the selected print has no photos", () => {
    const result = galleryForSelectedColor(
      {
        image: "https://cdn.example.com/default.jpg",
        gallery: ["https://cdn.example.com/other-print.jpg"],
        colors: [
          { name: "Print 1", hex: "#d9bfa6", imageUrls: ["https://cdn.example.com/p1.jpg"] },
          { name: "Print 2", hex: "#d9bfa6" },
        ],
      },
      "Print 2",
    );

    expect(result.image).toBe("https://cdn.example.com/default.jpg");
    expect(result.gallery).toEqual([]);
  });

  it("filters shared galleries by print name in the filename", () => {
    const shared = [
      "https://cdn.example.com/side-button-pink-gallery-1.jpg",
      "https://cdn.example.com/side-button-snail-gallery-1.jpg",
      "https://cdn.example.com/side-button-blue-gallery-1.jpg",
    ];
    const pink = galleryForSelectedColor(
      {
        image: "",
        gallery: [],
        colors: [
          { name: "PINK", hex: "#d9bfa6", imageUrls: shared },
          { name: "SNAIL", hex: "#d9bfa6", imageUrls: shared },
          { name: "BLUE", hex: "#d9bfa6", imageUrls: shared },
        ],
      },
      "PINK",
    );
    expect(pink.image).toBe("https://cdn.example.com/side-button-pink-gallery-1.jpg");
    expect(pink.gallery).toEqual([]);

    const snail = galleryForSelectedColor(
      {
        image: "",
        gallery: [],
        colors: [
          { name: "PINK", hex: "#d9bfa6", imageUrls: shared },
          { name: "SNAIL", hex: "#d9bfa6", imageUrls: shared },
        ],
      },
      "SNAIL",
    );
    expect(snail.image).toBe("https://cdn.example.com/side-button-snail-gallery-1.jpg");
  });

  it("shows only the selected print when galleries are distinct (bloomers-style)", () => {
    const happyBeeUrls = ["https://cdn.example.com/happy-bee-1.jpg", "https://cdn.example.com/happy-bee-2.jpg"];
    const pinkUrls = ["https://cdn.example.com/pink-1.jpg", "https://cdn.example.com/pink-2.jpg"];
    const snailUrls = ["https://cdn.example.com/snail-1.jpg", "https://cdn.example.com/snail-2.jpg"];
    const product = {
      image: "https://cdn.example.com/card.jpg",
      gallery: ["https://cdn.example.com/card.jpg"],
      colors: [
        { name: "HAPPY BEE", hex: "#d9bfa6", imageUrls: happyBeeUrls },
        { name: "PINK BUDS", hex: "#d9bfa6", imageUrls: pinkUrls },
        { name: "SNAIL", hex: "#d9bfa6", imageUrls: snailUrls },
      ],
    };
    const happy = galleryForSelectedColor(product, "HAPPY BEE");
    expect(happy.image).toBe(happyBeeUrls[0]);
    expect(happy.gallery).toEqual([happyBeeUrls[1]]);
    expect(happy.image).not.toBe(snailUrls[0]);
    const pink = galleryForSelectedColor(product, "PINK BUDS");
    expect(pink.image).toBe(pinkUrls[0]);
  });

  it("uses the selected size SKU photo instead of mixing same-colour galleries", () => {
    const product = {
      image: "https://cdn.example.com/card.jpg",
      gallery: ["https://cdn.example.com/card.jpg"],
      colors: [
        {
          name: "Pink",
          hex: "#f4c",
          imageUrls: ["https://cdn.example.com/pink-03.jpg", "https://cdn.example.com/pink-36.jpg"],
        },
      ],
      variants: [
        {
          sku: "KEDAR-03-PINK",
          size: "0-3M",
          color: "Pink",
          price: 100,
          stock: 2,
          active: true,
          imageUrl: "https://cdn.example.com/pink-03.jpg",
        },
        {
          sku: "KEDAR-36-PINK",
          size: "3-6M",
          color: "Pink",
          price: 100,
          stock: 2,
          active: true,
          imageUrl: "https://cdn.example.com/pink-36.jpg",
        },
      ],
    };
    expect(galleryForSelectedColor(product, "Pink", "0-3M")).toEqual({
      image: "https://cdn.example.com/pink-03.jpg",
      gallery: [],
    });
    expect(galleryForSelectedColor(product, "Pink", "3-6M")).toEqual({
      image: "https://cdn.example.com/pink-36.jpg",
      gallery: [],
    });
  });

  it("keeps rear extras with the selected size SKU photo", () => {
    const product = {
      image: "https://cdn.example.com/card.jpg",
      gallery: ["https://cdn.example.com/card.jpg", "https://cdn.example.com/pink-rear.jpg"],
      colors: [
        {
          name: "Pink",
          hex: "#f4c",
          imageUrls: [
            "https://cdn.example.com/pink-03.jpg",
            "https://cdn.example.com/pink-rear.jpg",
            "https://cdn.example.com/pink-36.jpg",
          ],
        },
      ],
      variants: [
        {
          sku: "KEDAR-03-PINK",
          size: "0-3M",
          color: "Pink",
          price: 100,
          stock: 2,
          active: true,
          imageUrl: "https://cdn.example.com/pink-03.jpg",
        },
        {
          sku: "KEDAR-36-PINK",
          size: "3-6M",
          color: "Pink",
          price: 100,
          stock: 2,
          active: true,
          imageUrl: "https://cdn.example.com/pink-36.jpg",
        },
      ],
    };
    expect(galleryForSelectedColor(product, "Pink", "0-3M")).toEqual({
      image: "https://cdn.example.com/pink-03.jpg",
      gallery: [],
    });
    expect(galleryForSelectedColor(product, "Pink", "3-6M")).toEqual({
      image: "https://cdn.example.com/pink-36.jpg",
      gallery: [],
    });
  });

  it("shows rear photos stored on the variant row", () => {
    const product = {
      image: "https://cdn.example.com/card.jpg",
      gallery: [],
      colors: [{ name: "Pink", hex: "#f4c", imageUrls: [] }],
      variants: [
        {
          sku: "KEDAR-03-PINK",
          size: "0-3M",
          color: "Pink",
          price: 100,
          stock: 2,
          active: true,
          imageUrl: "https://cdn.example.com/pink-03.jpg",
          imageUrls: ["https://cdn.example.com/pink-03-rear.jpg"],
        },
      ],
    };
    expect(galleryForSelectedColor(product, "Pink", "0-3M")).toEqual({
      image: "https://cdn.example.com/pink-03.jpg",
      gallery: ["https://cdn.example.com/pink-03-rear.jpg"],
    });
  });

  it("shares one gallery when same-colour sizes use the same photos", () => {
    const product = {
      image: "https://cdn.example.com/card.jpg",
      gallery: ["https://cdn.example.com/card.jpg"],
      colors: [
        {
          name: "Pink",
          hex: "#f4c",
          imageUrls: ["https://cdn.example.com/pink.jpg", "https://cdn.example.com/pink-rear.jpg"],
        },
      ],
      variants: [
        {
          sku: "KEDAR-03-PINK",
          size: "0-3M",
          color: "Pink",
          price: 100,
          stock: 2,
          active: true,
          imageUrl: "https://cdn.example.com/pink.jpg",
          imageUrls: ["https://cdn.example.com/pink-rear.jpg"],
        },
        {
          sku: "KEDAR-36-PINK",
          size: "3-6M",
          color: "Pink",
          price: 100,
          stock: 2,
          active: true,
          imageUrl: "https://cdn.example.com/pink.jpg",
        },
      ],
    };
    expect(galleryForSelectedColor(product, "Pink", "0-3M")).toEqual({
      image: "https://cdn.example.com/pink.jpg",
      gallery: ["https://cdn.example.com/pink-rear.jpg"],
    });
    expect(galleryForSelectedColor(product, "Pink", "3-6M")).toEqual({
      image: "https://cdn.example.com/pink.jpg",
      gallery: [],
    });
  });

  it("falls back to another size of the same colour when this size has no photo", () => {
    const product = {
      image: "https://cdn.example.com/card.jpg",
      gallery: ["https://cdn.example.com/card.jpg"],
      colors: [{ name: "red", hex: "#c00", imageUrls: [] }],
      variants: [
        {
          sku: "KED-RED-0-3",
          size: "0-3",
          color: "red",
          price: 380,
          stock: 0,
          active: true,
        },
        {
          sku: "KED-RED-3-6",
          size: "3-6",
          color: "red",
          price: 380,
          stock: 0,
          active: true,
          imageUrl: "https://cdn.example.com/red-36.jpg",
        },
        {
          sku: "KED-RED-6-12",
          size: "6-12",
          color: "red",
          price: 380,
          stock: 0,
          active: true,
        },
      ],
    };
    expect(galleryForSelectedColor(product, "red", "3-6")).toEqual({
      image: "https://cdn.example.com/red-36.jpg",
      gallery: [],
    });
    expect(galleryForSelectedColor(product, "red", "0-3")).toEqual({
      image: "https://cdn.example.com/red-36.jpg",
      gallery: [],
    });
    expect(galleryForSelectedColor(product, "red", "6-12")).toEqual({
      image: "https://cdn.example.com/red-36.jpg",
      gallery: [],
    });
  });

  it("does not mix other size photos into a SKU that already has its own gallery", () => {
    const product = {
      image: "https://cdn.example.com/card.jpg",
      gallery: ["https://cdn.example.com/card.jpg", "https://cdn.example.com/tablet.jpg"],
      colors: [
        {
          name: "Blue",
          hex: "#00f",
          imageUrls: [
            "https://cdn.example.com/blue-03.jpg",
            "https://cdn.example.com/blue-03-rear.jpg",
            "https://cdn.example.com/blue-36.jpg",
            "https://cdn.example.com/blue-612.jpg",
          ],
        },
      ],
      variants: [
        {
          sku: "KED-BLU-0-3",
          size: "0-3",
          color: "Blue",
          price: 380,
          stock: 2,
          active: true,
          imageUrl: "https://cdn.example.com/blue-03.jpg",
          imageUrls: ["https://cdn.example.com/blue-03-rear.jpg"],
        },
        {
          sku: "KED-BLU-3-6",
          size: "3-6",
          color: "Blue",
          price: 380,
          stock: 2,
          active: true,
          imageUrl: "https://cdn.example.com/blue-36.jpg",
          imageUrls: ["https://cdn.example.com/tablet.jpg"],
        },
        {
          sku: "KED-BLU-6-12",
          size: "6-12",
          color: "Blue",
          price: 380,
          stock: 2,
          active: true,
          imageUrl: "https://cdn.example.com/blue-612.jpg",
        },
      ],
    };
    expect(galleryForSelectedColor(product, "Blue", "0-3")).toEqual({
      image: "https://cdn.example.com/blue-03.jpg",
      gallery: ["https://cdn.example.com/blue-03-rear.jpg"],
    });
  });

  it("falls back to the product hero when a selected size has no matching photos", () => {
    const product = {
      image: "https://cdn.example.com/card.jpg",
      gallery: ["https://cdn.example.com/card.jpg"],
      colors: [
        {
          name: "Blue",
          hex: "#00f",
          imageUrls: ["https://cdn.example.com/blue-03.jpg", "https://cdn.example.com/blue-36.jpg"],
        },
      ],
      variants: [
        {
          sku: "KED-BLU-0-3",
          size: "0-3",
          color: "Blue",
          price: 380,
          stock: 2,
          active: true,
          imageUrl: "https://cdn.example.com/blue-03.jpg",
        },
        {
          sku: "KED-BLU-3-6",
          size: "3-6",
          color: "Blue",
          price: 380,
          stock: 2,
          active: true,
          imageUrl: "https://cdn.example.com/blue-36.jpg",
        },
        {
          sku: "KED-BLU-6-12",
          size: "6-12",
          color: "Blue",
          price: 380,
          stock: 2,
          active: true,
        },
      ],
    };
    expect(galleryForSelectedColor(product, "Blue", "6-12")).toEqual({
      image: "https://cdn.example.com/card.jpg",
      gallery: [],
    });
  });
});
