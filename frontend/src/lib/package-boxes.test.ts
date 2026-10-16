import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import {
  fieldsFromBoxCm,
  fromPackageBoxDto,
  getPackageBoxValidationErrors,
  listPackageBoxes,
  matchingPackageBoxId,
  packageTypeChoiceValue,
  parsePackageTypeChoice,
  reopenZohoPackageType,
  resolvedPackageTypeId,
  savePackageBox,
  toPackageBoxDto,
  weightFromPackageType,
} from "./package-boxes";

vi.mock("@/lib/api", () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

const mockedApi = api as unknown as {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
  put: ReturnType<typeof vi.fn>;
};

describe("package boxes", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("maps camelCase and snake_case DTOs including gross weight", () => {
    expect(
      fromPackageBoxDto({
        id: 4,
        name: " Small carton ",
        lengthCm: 20,
        breadth_cm: 15,
        heightCm: 10,
        weight_grams: 400,
      }),
    ).toEqual({
      id: "4",
      name: "Small carton",
      length_cm: 20,
      breadth_cm: 15,
      height_cm: 10,
      weight_grams: 400,
    });
    expect(
      toPackageBoxDto({
        id: "4",
        name: " Small carton ",
        length_cm: 20.4,
        breadth_cm: 15,
        height_cm: 10,
        weight_grams: 400,
      }),
    ).toEqual({
      id: 4,
      name: "Small carton",
      lengthCm: 20,
      breadthCm: 15,
      heightCm: 10,
      weightGrams: 400,
    });
  });

  it("requires a name and centimetre dimensions", () => {
    expect(
      getPackageBoxValidationErrors({ name: "  ", length_cm: 0, breadth_cm: 8, height_cm: 4, weight_grams: 0 }),
    ).toEqual(["Name is required", "Length must be at least 1 cm"]);
  });

  it("matches a saved package type by centimetre dimensions or saved id", () => {
    const boxes = [
      { id: "1", name: "Small", length_cm: 20, breadth_cm: 15, height_cm: 10, weight_grams: 400 },
      { id: "2", name: "Large", length_cm: 40, breadth_cm: 30, height_cm: 20, weight_grams: 0 },
    ];
    expect(matchingPackageBoxId(boxes, 40, 30, 20)).toBe("2");
    expect(matchingPackageBoxId(boxes, 12, 8, 4)).toBe("");
    expect(resolvedPackageTypeId(boxes, { package_box_id: 1, package_length_cm: 40, package_breadth_cm: 30, package_height_cm: 20 })).toBe(
      "1",
    );
    expect(resolvedPackageTypeId([], { package_box_id: 9, package_type: "box" })).toBe("9");
    expect(reopenZohoPackageType("box", "")).toBe("custom");
    expect(reopenZohoPackageType("box", "2")).toBe("box");
    expect(reopenZohoPackageType("envelope", "")).toBe("envelope");
    expect(resolvedPackageTypeId(boxes, { package_type: "envelope", package_length_cm: 20, package_breadth_cm: 15, package_height_cm: 10 })).toBe(
      "",
    );
    expect(
      resolvedPackageTypeId(boxes, {
        package_type: "custom",
        package_length_cm: 20,
        package_breadth_cm: 15,
        package_height_cm: 10,
      }),
    ).toBe("");
  });

  it("treats catalog ids as package types and maps them to Zoho box", () => {
    const boxes = [{ id: "1", name: "kedar", length_cm: 10, breadth_cm: 10, height_cm: 10, weight_grams: 250 }];
    expect(parsePackageTypeChoice("1", boxes)).toEqual({
      zohoType: "box",
      packageTypeId: "1",
      box: boxes[0],
    });
    expect(parsePackageTypeChoice("envelope", boxes)).toEqual({
      zohoType: "envelope",
      packageTypeId: "",
      box: null,
    });
    expect(packageTypeChoiceValue("box", "1")).toBe("1");
    expect(packageTypeChoiceValue("envelope", "")).toBe("envelope");
    expect(weightFromPackageType(boxes[0], "kg")).toBe("0.25");
  });

  it("converts stored centimetres into the packing form unit", () => {
    expect(
      fieldsFromBoxCm({ name: "Mailer", length_cm: 32, breadth_cm: 24, height_cm: 2, weight_grams: 0 }, "cm"),
    ).toEqual({ length: "32", width: "24", height: "2" });
    expect(
      fieldsFromBoxCm({ name: "Mailer", length_cm: 32, breadth_cm: 24, height_cm: 2, weight_grams: 0 }, "in").length,
    ).toBe("12.6");
  });

  it("lists and creates package types through the admin API", async () => {
    mockedApi.get.mockResolvedValueOnce([
      { id: 1, name: "Small carton", lengthCm: 20, breadthCm: 15, heightCm: 10, weightGrams: 400 },
    ]);
    mockedApi.post.mockResolvedValueOnce({
      id: 2,
      name: "Large carton",
      lengthCm: 40,
      breadthCm: 30,
      heightCm: 20,
      weightGrams: 800,
    });

    expect(await listPackageBoxes()).toEqual([
      { id: "1", name: "Small carton", length_cm: 20, breadth_cm: 15, height_cm: 10, weight_grams: 400 },
    ]);
    expect(mockedApi.get).toHaveBeenCalledWith("/api/v1/admin/package-boxes", { auth: true });

    await savePackageBox({ name: "Large carton", length_cm: 40, breadth_cm: 30, height_cm: 20, weight_grams: 800 });
    expect(mockedApi.post).toHaveBeenCalledWith(
      "/api/v1/admin/package-boxes",
      { id: undefined, name: "Large carton", lengthCm: 40, breadthCm: 30, heightCm: 20, weightGrams: 800 },
      { auth: true },
    );
  });
});
