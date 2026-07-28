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
});
