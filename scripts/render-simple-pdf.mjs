import fs from "node:fs";

const [inputPath, outputPath] = process.argv.slice(2);
if (!inputPath || !outputPath) {
  throw new Error("Usage: node render-simple-pdf.mjs <input.ms> <output.pdf>");
}

const source = fs.readFileSync(inputPath, "utf8");

function parseMs(text) {
  const lines = text.split(/\r?\n/);
  const blocks = [];
  let mode = "body";
  let paragraph = [];
  let display = [];

  const flushParagraph = () => {
    if (paragraph.length) {
      blocks.push({ type: "paragraph", text: paragraph.join(" ").trim() });
      paragraph = [];
    }
  };
  const flushDisplay = () => {
    if (display.length) {
      blocks.push({ type: "display", lines: display });
      display = [];
    }
  };

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    if (line === ".TL" || line === ".AU" || line === ".DA") {
      flushParagraph();
      const type = line === ".TL" ? "title" : line === ".AU" ? "subtitle" : "date";
      blocks.push({ type, text: lines[++i]?.trim() ?? "" });
      continue;
    }
    if (line.startsWith(".NH ")) {
      flushParagraph();
      const level = Number(line.slice(4).trim()) || 1;
      blocks.push({ type: "heading", level, text: lines[++i]?.trim() ?? "" });
      continue;
    }
    if (line === ".PP") {
      flushParagraph();
      continue;
    }
    if (line.startsWith(".IP ")) {
      flushParagraph();
      const match = line.match(/^\.IP\s+"([^"]*)"/);
      const label = match?.[1] ?? "-";
      const itemText = lines[++i]?.trim() ?? "";
      blocks.push({ type: "item", label, text: itemText });
      continue;
    }
    if (line === ".DS L") {
      flushParagraph();
      mode = "display";
      continue;
    }
    if (line === ".DE") {
      flushDisplay();
      mode = "body";
      continue;
    }
    if (line === "." || line.startsWith(".\"")) continue;
    if (mode === "display") display.push(line);
    else if (line.trim()) paragraph.push(line.trim());
  }
  flushParagraph();
  flushDisplay();
  return blocks;
}

const PAGE_W = 595.28;
const PAGE_H = 841.89;
const LEFT = 58;
const RIGHT = 55;
const TOP = 64;
const BOTTOM = 55;
const BODY_W = PAGE_W - LEFT - RIGHT;

function charFactor(ch) {
  if (" ilI.,:;'!|".includes(ch)) return 0.26;
  if ("mwMW@%&".includes(ch)) return 0.82;
  if (/[A-Z0-9]/.test(ch)) return 0.61;
  return 0.5;
}

function textWidth(text, size) {
  return [...text].reduce((sum, ch) => sum + charFactor(ch) * size, 0);
}

function wrap(text, size, width, firstPrefix = "", nextPrefix = "") {
  const words = text.split(/\s+/).filter(Boolean);
  const output = [];
  let prefix = firstPrefix;
  let line = prefix;
  for (const word of words) {
    const candidate = line === prefix ? `${prefix}${word}` : `${line} ${word}`;
    if (textWidth(candidate, size) <= width || line === prefix) {
      line = candidate;
    } else {
      output.push(line);
      prefix = nextPrefix;
      line = `${prefix}${word}`;
    }
  }
  if (line.trim()) output.push(line);
  return output;
}

function pdfEscape(text) {
  return text.replace(/\\/g, "\\\\").replace(/\(/g, "\\(").replace(/\)/g, "\\)");
}

const pages = [];
let commands = [];
let y = PAGE_H - TOP;
let sectionNumber = [];

function newPage() {
  if (commands.length) pages.push(commands.join("\n"));
  commands = [];
  y = PAGE_H - TOP;
}

function ensure(height) {
  if (y - height < BOTTOM) newPage();
}

function rule(yPos, color = "0.82 0.82 0.82") {
  commands.push(`${color} RG 0.6 w ${LEFT} ${yPos.toFixed(2)} m ${(PAGE_W - RIGHT).toFixed(2)} ${yPos.toFixed(2)} l S`);
}

function drawLine(text, x, yPos, size, font = "F1", color = "0.12 0.12 0.14") {
  commands.push(
    `${color} rg BT /${font} ${size} Tf 1 0 0 1 ${x.toFixed(2)} ${yPos.toFixed(2)} Tm (${pdfEscape(text)}) Tj ET`,
  );
}

function drawWrapped(lines, { x = LEFT, size = 10.2, leading = 14.2, font = "F1", color } = {}) {
  ensure(lines.length * leading + 3);
  for (const line of lines) {
    drawLine(line, x, y, size, font, color);
    y -= leading;
  }
}

for (const block of parseMs(source)) {
  if (block.type === "title") {
    ensure(110);
    y -= 18;
    drawWrapped(wrap(block.text, 22, BODY_W), { size: 22, leading: 27, font: "F2", color: "0.18 0.16 0.26" });
    y -= 10;
    rule(y, "0.73 0.38 0.55");
    y -= 20;
  } else if (block.type === "subtitle") {
    drawWrapped(wrap(block.text, 12, BODY_W), { size: 12, leading: 16, font: "F2", color: "0.33 0.28 0.38" });
    y -= 4;
  } else if (block.type === "date") {
    drawWrapped([block.text], { size: 9.5, leading: 14, color: "0.42 0.42 0.46" });
    y -= 18;
  } else if (block.type === "heading") {
    if (block.level === 1) {
      sectionNumber[0] = (sectionNumber[0] ?? 0) + 1;
      sectionNumber = [sectionNumber[0]];
    } else {
      sectionNumber[1] = (sectionNumber[1] ?? 0) + 1;
    }
    const number = block.level === 1 ? `${sectionNumber[0]}.` : `${sectionNumber[0]}.${sectionNumber[1]}`;
    const size = block.level === 1 ? 15 : 11.8;
    const leading = block.level === 1 ? 20 : 16;
    const gap = block.level === 1 ? 20 : 12;
    const lines = wrap(`${number} ${block.text}`, size, BODY_W);
    ensure(gap + lines.length * leading + 6);
    y -= gap;
    drawWrapped(lines, { size, leading, font: "F2", color: block.level === 1 ? "0.24 0.18 0.29" : "0.34 0.23 0.35" });
    y -= block.level === 1 ? 4 : 2;
  } else if (block.type === "paragraph") {
    const lines = wrap(block.text, 10.2, BODY_W);
    drawWrapped(lines);
    y -= 7;
  } else if (block.type === "item") {
    const labelWidth = Math.max(24, Math.min(145, textWidth(block.label, 9.8) + 20));
    const lines = wrap(block.text, 9.8, BODY_W - labelWidth, "", "");
    ensure(lines.length * 13.5 + 4);
    drawLine(block.label, LEFT + 5, y, 9.8, "F2", "0.30 0.24 0.33");
    for (let i = 0; i < lines.length; i += 1) {
      drawLine(lines[i], LEFT + labelWidth, y, 9.8);
      y -= 13.5;
    }
    y -= 4;
  } else if (block.type === "display") {
    const longest = Math.max(...block.lines.map((line) => textWidth(line, 8.8)), 1);
    const displayLines = block.lines.flatMap((line) =>
      longest <= BODY_W - 24 ? [line] : wrap(line, 8.8, BODY_W - 24),
    );
    const height = displayLines.length * 12.5 + 20;
    ensure(height + 4);
    commands.push(`0.965 0.955 0.972 rg ${LEFT} ${(y - height + 7).toFixed(2)} ${BODY_W} ${height.toFixed(2)} re f`);
    y -= 8;
    for (const line of displayLines) {
      drawLine(line, LEFT + 12, y, 8.8, "F1", "0.18 0.16 0.20");
      y -= 12.5;
    }
    y -= 10;
  }
}
if (commands.length) pages.push(commands.join("\n"));

const objects = [null];
const addObject = (value) => {
  objects.push(value);
  return objects.length - 1;
};

const catalogId = addObject("");
const pagesId = addObject("");
const regularFontId = addObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
const boldFontId = addObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");
const infoId = addObject(
  "<< /Title (Order Cancellation, Returns, Exchanges, and Tracking Status) /Author (Mini's n More) /Creator (Codex local PDF renderer) >>",
);

const pageIds = [];
for (let index = 0; index < pages.length; index += 1) {
  const header = index === 0
    ? ""
    : `0.46 0.42 0.48 rg BT /F1 8 Tf 1 0 0 1 ${LEFT} ${(PAGE_H - 33).toFixed(2)} Tm (Mini's n More - Order Lifecycle Guide) Tj ET\n`;
  const footer = `0.46 0.42 0.48 rg BT /F1 8 Tf 1 0 0 1 ${(PAGE_W / 2 - 8).toFixed(2)} 29 Tm (${index + 1}) Tj ET`;
  const stream = `${header}${pages[index]}\n${footer}`;
  const contentId = addObject(`<< /Length ${Buffer.byteLength(stream, "latin1")} >>\nstream\n${stream}\nendstream`);
  const pageId = addObject(
    `<< /Type /Page /Parent ${pagesId} 0 R /MediaBox [0 0 ${PAGE_W} ${PAGE_H}] /Resources << /Font << /F1 ${regularFontId} 0 R /F2 ${boldFontId} 0 R >> >> /Contents ${contentId} 0 R >>`,
  );
  pageIds.push(pageId);
}

objects[catalogId] = `<< /Type /Catalog /Pages ${pagesId} 0 R >>`;
objects[pagesId] = `<< /Type /Pages /Kids [${pageIds.map((id) => `${id} 0 R`).join(" ")}] /Count ${pageIds.length} >>`;

let pdf = "%PDF-1.4\n%\xE2\xE3\xCF\xD3\n";
const offsets = [0];
for (let id = 1; id < objects.length; id += 1) {
  offsets[id] = Buffer.byteLength(pdf, "latin1");
  pdf += `${id} 0 obj\n${objects[id]}\nendobj\n`;
}
const xrefOffset = Buffer.byteLength(pdf, "latin1");
pdf += `xref\n0 ${objects.length}\n0000000000 65535 f \n`;
for (let id = 1; id < objects.length; id += 1) {
  pdf += `${String(offsets[id]).padStart(10, "0")} 00000 n \n`;
}
pdf += `trailer\n<< /Size ${objects.length} /Root ${catalogId} 0 R /Info ${infoId} 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;

fs.writeFileSync(outputPath, Buffer.from(pdf, "latin1"));
console.log(`Created ${outputPath} (${pages.length} pages)`);
