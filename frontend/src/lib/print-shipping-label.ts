import { toast } from "sonner";

const HTTP_URL = /^https?:\/\//i;

const PRINT_CSS = `
  html, body { margin: 0; background: #fff; color: #111; }
  body { font-family: system-ui, sans-serif; }
  .toolbar {
    display: flex;
    justify-content: flex-end;
    padding: 12px 16px;
    background: #f4f4f5;
    border-bottom: 1px solid #e4e4e7;
  }
  button {
    border: 0;
    border-radius: 999px;
    background: #111;
    color: #fff;
    font: inherit;
    font-size: 14px;
    padding: 8px 16px;
    cursor: pointer;
  }
  img { display: block; width: 100%; height: auto; }
  @media print {
    @page { margin: 4mm; }
    .toolbar { display: none !important; }
  }
`;

type LabelKind = "pdf" | "image" | "external";

function mediaType(blob: Blob) {
  return blob.type.toLowerCase().split(";")[0]?.trim() ?? "";
}

/** Classify from the response type. The URL is only a hint when the type is missing. */
export function shippingLabelKind(blob: Blob, labelUrl: string): LabelKind {
  const type = mediaType(blob);
  if (type === "image/svg+xml" || type === "text/html" || type === "application/xhtml+xml") return "external";
  if (type === "application/pdf" || type === "application/x-pdf") return "pdf";
  if (type.startsWith("image/")) return "image";
  if (type) return "external";
  if (/\.pdf(\?|$)/i.test(labelUrl)) return "pdf";
  if (/\.(png|jpe?g|gif|webp|bmp)(\?|$)/i.test(labelUrl)) return "image";
  return "external";
}

function revokeWhenClosed(popup: Window, objectUrl: string) {
  const revoke = () => URL.revokeObjectURL(objectUrl);
  popup.addEventListener("pagehide", revoke);
}

function printOnceOnLoad(popup: Window) {
  const printOnce = () => {
    popup.removeEventListener("load", printOnce);
    try {
      popup.focus();
      popup.print();
    } catch {
      /* The label tab was closed before print. */
    }
  };
  popup.addEventListener("load", printOnce);
}

function showPdf(popup: Window, blob: Blob) {
  const file = mediaType(blob).includes("pdf") ? blob : new Blob([blob], { type: "application/pdf" });
  const objectUrl = URL.createObjectURL(file);
  revokeWhenClosed(popup, objectUrl);
  printOnceOnLoad(popup);
  popup.location.href = objectUrl;
}

function showImage(popup: Window, blob: Blob) {
  const objectUrl = URL.createObjectURL(blob);
  revokeWhenClosed(popup, objectUrl);
  const doc = popup.document;
  if (!doc.body) {
    doc.open();
    doc.write("<!DOCTYPE html><html><head></head><body></body></html>");
    doc.close();
  }
  doc.title = "Shipping label";
  doc.body.replaceChildren();
  const style = doc.createElement("style");
  style.textContent = PRINT_CSS;
  doc.head.append(style);
  const toolbar = doc.createElement("div");
  toolbar.className = "toolbar";
  const button = doc.createElement("button");
  button.type = "button";
  button.textContent = "Print";
  button.addEventListener("click", () => popup.print());
  toolbar.append(button);
  const image = doc.createElement("img");
  image.alt = "Shipping label";
  let printed = false;
  image.addEventListener("load", () => {
    if (printed) return;
    printed = true;
    try {
      popup.focus();
      popup.print();
    } catch {
      /* The label tab was closed before print. */
    }
  });
  image.src = objectUrl;
  doc.body.append(toolbar, image);
}

function openCarrierPage(popup: Window, labelUrl: string) {
  if (popup.closed) return;
  popup.location.href = labelUrl;
}

/**
 * Opens the carrier label in a new tab and prints it once.
 * PDFs and raster images that the browser can read are shown from a local file.
 * Everything else, including HTML and SVG, stays on the carrier's own page.
 */
export async function openPrintableShippingLabel(labelUrl: string): Promise<void> {
  const url = labelUrl.trim();
  if (!url) return;

  const popup = window.open("", "_blank");
  if (!popup) {
    toast.error("Allow pop-ups to print the shipping label");
    return;
  }
  if (!HTTP_URL.test(url)) {
    popup.close();
    toast.error("This shipping label cannot be opened for printing");
    return;
  }

  try {
    const response = await fetch(url);
    if (popup.closed) return;
    if (!response.ok) {
      openCarrierPage(popup, url);
      return;
    }
    const blob = await response.blob();
    if (popup.closed) return;
    const kind = shippingLabelKind(blob, url);
    if (kind === "pdf") {
      showPdf(popup, blob);
      return;
    }
    if (kind === "image") {
      showImage(popup, blob);
      return;
    }
    openCarrierPage(popup, url);
  } catch {
    openCarrierPage(popup, url);
  }
}
