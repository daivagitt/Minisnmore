import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { listProducts, type ProductRow } from "@/lib/backend-data";
import { parseVariantsJson } from "@/lib/product-variants";
import { filterAdminProductsByQuery } from "@/lib/product-search";
import { showApiError } from "@/lib/ui-errors";
import { ListPagination } from "@/components/ListPagination";
import { useListPagination } from "@/hooks/useListPagination";
import { ADMIN_PAGE_SIZE } from "@/lib/pagination";

export const Route = createFileRoute("/sales/products")({
  component: SalesProductsPage,
});

function variantSummary(product: ProductRow) {
  const variants = parseVariantsJson(product.variants_json);
  const stock = variants.length
    ? variants.reduce((sum, row) => sum + (Number(row.stock) || 0), 0)
    : Number(product.stock ?? 0);
  return { count: variants.length, stock };
}

function SalesProductsPage() {
  const [products, setProducts] = useState<ProductRow[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const filtered = useMemo(() => filterAdminProductsByQuery(products, query), [products, query]);
  const paging = useListPagination(filtered, { pageSize: ADMIN_PAGE_SIZE, resetKey: query });
  const pageRows = paging.pageItems;

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
      description="View-only catalog: product, SKU, status, stock and variants."
    >
      <input
        type="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search products…"
        className="mb-4 w-full max-w-md rounded-2xl glass px-4 py-2.5 text-sm"
      />
      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-border">
          <table className="w-full text-left text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2">Product</th>
                <th className="px-3 py-2">SKU</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Stock</th>
                <th className="px-3 py-2">Variants</th>
              </tr>
            </thead>
            <tbody>
              {pageRows.map((product) => {
                const summary = variantSummary(product);
                return (
                  <tr key={product.id ?? product.slug} className="border-t border-border/70">
                    <td className="px-3 py-2 font-medium">{product.name}</td>
                    <td className="px-3 py-2">{product.sku || "—"}</td>
                    <td className="px-3 py-2">{product.active ? "Active" : "Inactive"}</td>
                    <td className="px-3 py-2">{summary.stock}</td>
                    <td className="px-3 py-2">{summary.count}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {!loading && filtered.length > 0 && (
        <ListPagination
          page={paging.page}
          totalPages={paging.totalPages}
          totalItems={filtered.length}
          pageSize={ADMIN_PAGE_SIZE}
          onPageChange={paging.setPage}
        />
      )}
    </AdminPage>
  );
}
