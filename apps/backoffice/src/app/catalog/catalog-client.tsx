"use client";

import type { CatalogProduct } from "@snapplay/contracts";
import { Search, ShieldAlert } from "lucide-react";
import { useMemo, useState } from "react";
import { formatMoney } from "@/lib/format";

export function CatalogClient({ products }: { products: CatalogProduct[] }) {
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("ALL");
  const categories = useMemo(
    () =>
      [...new Set(products.flatMap((product) => product.categories))].sort(),
    [products],
  );
  const filtered = useMemo(
    () =>
      products.filter((product) => {
        const text =
          `${product.name} ${product.description ?? ""}`.toLocaleLowerCase(
            "es",
          );
        return (
          text.includes(query.toLocaleLowerCase("es")) &&
          (category === "ALL" || product.categories.includes(category))
        );
      }),
    [products, query, category],
  );

  return (
    <>
      <div className="toolbar">
        <div style={{ position: "relative" }}>
          <Search
            size={16}
            style={{
              position: "absolute",
              left: 12,
              top: 12,
              color: "#8a94a5",
            }}
          />
          <input
            className="search-input"
            style={{ paddingLeft: 38 }}
            placeholder="Buscar productos o marcas"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
        <select
          className="select-input"
          value={category}
          onChange={(event) => setCategory(event.target.value)}
        >
          <option value="ALL">Todas las categorías</option>
          {categories.map((item) => (
            <option value={item} key={item}>
              {item}
            </option>
          ))}
        </select>
        <span className="date-chip">{filtered.length} SKU visibles</span>
      </div>
      <div className="product-grid">
        {filtered.map((product) => (
          <article className="product-card" key={product.id}>
            <div
              className="product-image"
              style={{ backgroundImage: `url("${product.imageUrl}")` }}
            />
            <div className="product-body">
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  gap: 8,
                }}
              >
                <h3>{product.name}</h3>
                {product.ageRestricted ? (
                  <ShieldAlert
                    size={16}
                    color="#d77d18"
                    aria-label="Producto con restricción de edad"
                  />
                ) : null}
              </div>
              <p>{product.description}</p>
              <div className="product-meta">
                <div className="tag-list">
                  {product.categories.slice(0, 2).map((item) => (
                    <span className="tag" key={item}>
                      {item}
                    </span>
                  ))}
                </div>
                <strong>
                  {product.referencePriceMinor
                    ? formatMoney(
                        product.referencePriceMinor,
                        product.currency ?? "ARS",
                      )
                    : "—"}
                </strong>
              </div>
            </div>
          </article>
        ))}
      </div>
      {!filtered.length ? (
        <div className="empty-state">
          No hay productos que coincidan con esos filtros.
        </div>
      ) : null}
    </>
  );
}
