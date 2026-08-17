import { RefreshCw } from "lucide-react";
import { PageHeader } from "@/components/ui";
import { getProducts } from "@/lib/api";
import { CatalogClient } from "./catalog-client";

export const dynamic = "force-dynamic";

export default async function CatalogPage() {
  const products = await getProducts();
  return (
    <div className="page">
      <PageHeader
        eyebrow="Rappi Turbo · Catálogo normalizado"
        title="Productos disponibles"
        description="Curá los SKU que Disney podrá usar en sus experiences. Precio y stock finales siempre se confirman en Rappi según la ubicación."
        action={
          <button className="button">
            <RefreshCw size={15} /> Sincronizar catálogo
          </button>
        }
      />
      <CatalogClient products={products} />
    </div>
  );
}
