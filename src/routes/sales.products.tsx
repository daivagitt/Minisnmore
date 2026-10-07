import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { ListPagination } from "@/components/ListPagination";
import { useListPagination } from "@/hooks/useListPagination";
import { listProducts, type ProductRow } from "@/lib/backend-data";
import { ADMIN_PAGE_SIZE } from "@/lib/pagination";
import { filterAdminProductsByQuery } from "@/lib/product-search";
import { salesCatalogTotals, salesProductInventory } from "@/lib/sales-catalog";
import { showApiError } from "@/lib/ui-errors";

export const Route = createFileRoute("/sales/products")({
  head: () => ({ meta: [{ title: "Products · Sales · Mini's n More" }] }),
  component: SalesProductsPage,
});

function formatCount(value: number): string {
  return value.toLocaleString("en-IN");
}

export function SalesProductsPage() {
  const [products, setProducts] = useState<ProductRow[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const filtered = useMemo(() => filterAdminProductsByQuery(products, query), [products, query]);
  const paging = useListPagination(filtered, { pageSize: ADMIN_PAGE_SIZE, resetKey: query });
  const totals = useMemo(() => salesCatalogTotals(filtered), [filtered]);

  useEffect(() => {
    void (async () => {
      try {
        setProducts(await listProducts(true));
      } catch (error) {
        showApiError(error, "Could not load products");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  return (
    <AdminPage
      eyebrow="Sales"
      title="Products & quantity"
      description="View-only catalog with each size and colour, plus stock on every variant."
    >
      <input
        type="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search products, SKUs, sizes, colours…"
        aria-label="Search products"
        className="mb-4 w-full max-w-md rounded-2xl glass px-4 py-2.5 text-sm"
      />
      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : filtered.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          {query.trim() ? "No products match this search." : "No products yet."}
        </p>
      ) : (
        <>
          <p className="text-sm text-muted-foreground mb-4">
            {formatCount(totals.productCount)} products · {formatCount(totals.variantCount)} variants ·{" "}
            {formatCount(totals.unitCount)} units in stock
          </p>
          <div className="space-y-3" id="sales-products">
            {paging.pageItems.map((product) => {
              const inventory = salesProductInventory(product);
              return (
                <article
                  key={product.id ?? product.slug}
                  className="rounded-2xl border border-border overflow-hidden"
                >
                  <div className="flex flex-wrap items-baseline justify-between gap-2 px-3 py-2.5 bg-muted/40">
                    <div>
                      <h2 className="font-medium">{product.name}</h2>
                      <p className="text-xs text-muted-foreground mt-0.5">
                        {product.sku || "No parent SKU"} · {product.active ? "Active" : "Inactive"}
                      </p>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {inventory.variantCount
                        ? `${formatCount(inventory.variantCount)} variants · ${formatCount(inventory.totalStock)} units`
                        : `${formatCount(inventory.totalStock)} units`}
                    </p>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm">
                      <thead className="text-xs uppercase tracking-wide text-muted-foreground">
                        <tr>
                          <th className="px-3 py-2">Variant</th>
                          <th className="px-3 py-2">SKU</th>
                          <th className="px-3 py-2">Qty</th>
                        </tr>
                      </thead>
                      <tbody>
                        {inventory.lines.map((line) => (
                          <tr key={`${product.slug}-${line.sku}-${line.label}`} className="border-t border-border/70">
                            <td className="px-3 py-2">
                              {line.label}
                              {!line.active ? (
                                <span className="ml-2 text-xs text-muted-foreground">Inactive</span>
                              ) : null}
                            </td>
                            <td className="px-3 py-2">{line.sku}</td>
                            <td className="px-3 py-2 font-medium">{formatCount(line.stock)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </article>
              );
            })}
          </div>
          <ListPagination
            page={paging.page}
            totalPages={paging.totalPages}
            totalItems={filtered.length}
            pageSize={ADMIN_PAGE_SIZE}
            scrollToId="sales-products"
            onPageChange={paging.setPage}
          />
        </>
      )}
    </AdminPage>
  );
}
