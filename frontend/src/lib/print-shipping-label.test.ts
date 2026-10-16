/** @vitest-environment jsdom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { openPrintableShippingLabel, shippingLabelKind } from "./print-shipping-label";

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

type Listener = (event: Event) => void;

function fakePopup() {
  const listeners = new Map<string, Set<Listener>>();
  const doc = document.implementation.createHTMLDocument("label");
  const popup = {
    closed: false,
    location: { href: "" },
    document: doc,
    focus: vi.fn(),
    print: vi.fn(),
    close: vi.fn(),
    addEventListener: (type: string, fn: Listener) => {
      const set = listeners.get(type) ?? new Set<Listener>();
      set.add(fn);
      listeners.set(type, set);
    },
    removeEventListener: (type: string, fn: Listener) => {
      listeners.get(type)?.delete(fn);
    },
    dispatch: (type: string) => {
      listeners.get(type)?.forEach((fn) => fn(new Event(type)));
    },
  };
  return popup;
}

function blobResponse(blob: Blob, ok = true) {
  return { ok, blob: async () => blob } as Response;
}

describe("shippingLabelKind", () => {
  it("treats html and svg as external even when the address looks like a pdf", () => {
    expect(shippingLabelKind(new Blob(["<html>"], { type: "text/html" }), "https://label.example/a.pdf")).toBe(
      "external",
    );
    expect(shippingLabelKind(new Blob(["<svg>"], { type: "image/svg+xml" }), "https://label.example/a.png")).toBe(
      "external",
    );
    expect(shippingLabelKind(new Blob(["%PDF"], { type: "application/pdf" }), "https://label.example/out")).toBe("pdf");
    expect(shippingLabelKind(new Blob(["png"], { type: "image/png" }), "https://label.example/out")).toBe("image");
  });
});

describe("openPrintableShippingLabel", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:label");
    vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);
  });

  it("prints a fetched PDF once after the tab loads and revokes the file when the tab closes", async () => {
    const popup = fakePopup();
    vi.spyOn(window, "open").mockReturnValue(popup as unknown as Window);
    vi.spyOn(globalThis, "fetch").mockResolvedValue(blobResponse(new Blob(["%PDF"], { type: "application/pdf" })));

    await openPrintableShippingLabel("https://label.example/OUT-456");

    expect(popup.location.href).toBe("blob:label");
    expect(popup.print).not.toHaveBeenCalled();
    popup.dispatch("load");
    popup.dispatch("load");
    expect(popup.print).toHaveBeenCalledTimes(1);
    popup.dispatch("pagehide");
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:label");
  });

  it("prints a fetched image from an image element and does not embed the address in html", async () => {
    const popup = fakePopup();
    vi.spyOn(window, "open").mockReturnValue(popup as unknown as Window);
    vi.spyOn(globalThis, "fetch").mockResolvedValue(blobResponse(new Blob(["png"], { type: "image/png" })));

    await openPrintableShippingLabel("https://label.example/out.png?q=</script>");

    const image = popup.document.querySelector("img");
    expect(image).not.toBeNull();
    expect(image?.getAttribute("src")).toBe("blob:label");
    expect(popup.document.body.innerHTML).not.toContain("</script>");
    expect(popup.document.querySelector("iframe")).toBeNull();
    image?.dispatchEvent(new Event("load"));
    image?.dispatchEvent(new Event("load"));
    expect(popup.print).toHaveBeenCalledTimes(1);
  });

  it("opens the carrier page when the label cannot be read", async () => {
    const popup = fakePopup();
    vi.spyOn(window, "open").mockReturnValue(popup as unknown as Window);
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new Error("CORS"));
    const htmlLabel = "https://label.example/out?q=</script><script>alert(1)</script>";

    await openPrintableShippingLabel(htmlLabel);

    expect(popup.location.href).toBe(htmlLabel);
    expect(popup.document.body.innerHTML).not.toContain("<script>");
    expect(popup.print).not.toHaveBeenCalled();
  });

  it("does not navigate to a non-web label address", async () => {
    const popup = fakePopup();
    vi.spyOn(window, "open").mockReturnValue(popup as unknown as Window);

    await openPrintableShippingLabel("javascript:alert(1)");

    expect(popup.close).toHaveBeenCalled();
    expect(popup.location.href).toBe("");
    expect(toast.error).toHaveBeenCalled();
  });

  it("does nothing for an empty label", async () => {
    const open = vi.spyOn(window, "open");
    await openPrintableShippingLabel("  ");
    expect(open).not.toHaveBeenCalled();
  });

  it("tells the user when the print tab is blocked", async () => {
    vi.spyOn(window, "open").mockReturnValue(null);
    await openPrintableShippingLabel("https://label.example/out");
    expect(toast.error).toHaveBeenCalledWith("Allow pop-ups to print the shipping label");
  });

  it("leaves a closed tab alone", async () => {
    const popup = fakePopup();
    popup.closed = true;
    vi.spyOn(window, "open").mockReturnValue(popup as unknown as Window);
    vi.spyOn(globalThis, "fetch").mockResolvedValue(blobResponse(new Blob(["%PDF"], { type: "application/pdf" })));

    await openPrintableShippingLabel("https://label.example/out");

    expect(popup.location.href).toBe("");
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });
});
