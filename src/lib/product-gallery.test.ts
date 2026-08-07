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

  it("does not show other prints when the selected print has no photos", () => {
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

    expect(result.image).toBe("");
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
});
