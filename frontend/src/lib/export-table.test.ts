import { beforeEach, describe, expect, it, vi } from "vitest";
import { downloadTableAsXlsx, exportFilename, rowsForExport } from "./export-table";

const writeFile = vi.fn();
const jsonToSheet = vi.fn((rows: unknown) => rows);
const bookNew = vi.fn(() => ({ sheets: [] }));
const bookAppendSheet = vi.fn();

vi.mock("xlsx", () => ({
  utils: {
    book_new: () => bookNew(),
    json_to_sheet: (rows: unknown) => jsonToSheet(rows),
    book_append_sheet: (...args: unknown[]) => bookAppendSheet(...args),
  },
  writeFile: (...args: unknown[]) => writeFile(...args),
}));

describe("export table", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("maps columns and boolean values", () => {
    expect(
      rowsForExport(
        [
          { name: "Asha", active: true, count: 2 },
          { name: "Bina", active: false, count: null },
        ],
        [
          { header: "Name", value: (row) => row.name },
          { header: "Active", value: (row) => row.active },
          { header: "Orders", value: (row) => row.count },
        ],
      ),
    ).toEqual([
      { Name: "Asha", Active: "Yes", Orders: 2 },
      { Name: "Bina", Active: "No", Orders: "" },
    ]);
  });

  it("prefixes formula-like strings so Excel will not execute them", () => {
    expect(
      rowsForExport([{ comment: "=cmd|'/c calc'!A0", name: "+1-555", ok: "Hello" }], [
        { header: "Comments", value: (row) => row.comment },
        { header: "Name", value: (row) => row.name },
        { header: "Note", value: (row) => row.ok },
      ]),
    ).toEqual([{ Comments: "'=cmd|'/c calc'!A0", Name: "'+1-555", Note: "Hello" }]);
  });

  it("builds a dated filename", () => {
    expect(exportFilename("customers", new Date(2026, 9, 7))).toBe("customers-20261007.xlsx");
  });

  it("writes an xlsx workbook", async () => {
    const data = [{ Name: "Asha" }];
    await downloadTableAsXlsx(data, "customers.xlsx", "Customers");
    expect(jsonToSheet).toHaveBeenCalledWith(data);
    expect(bookAppendSheet).toHaveBeenCalled();
    expect(writeFile).toHaveBeenCalledWith(expect.anything(), "customers.xlsx");
  });

  it("rejects an empty export", async () => {
    await expect(downloadTableAsXlsx([], "empty.xlsx")).rejects.toThrow("Nothing to export");
    expect(writeFile).not.toHaveBeenCalled();
  });
});
