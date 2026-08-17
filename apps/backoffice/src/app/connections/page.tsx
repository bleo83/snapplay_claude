import { CheckCircle2, PlugZap, RefreshCw } from "lucide-react";
import { PageHeader, StatusPill } from "@/components/ui";

export default function ConnectionsPage() {
  return (
    <div className="page">
      <PageHeader
        eyebrow="Partner adapters"
        title="Conexiones"
        description="Capabilities y salud técnica de las conexiones habilitadas para Disney."
        action={
          <button className="button">
            <RefreshCw size={15} /> Probar conexión
          </button>
        }
      />
      <div className="connection-card">
        <div className="connection-logo">R</div>
        <div>
          <h3>Rappi Turbo · Argentina</h3>
          <p>
            Catálogo incremental · Dynamic storefront · Order webhooks ·
            Reconciliation pull
          </p>
        </div>
        <StatusPill status="ACTIVE" />
      </div>
      <div className="feature-grid" style={{ marginTop: 18 }}>
        <div className="feature-card">
          <CheckCircle2 size={20} color="#169b73" />
          <h3>Catálogo sincronizado</h3>
          <p>
            6 SKU demo · último sync hace 3 minutos · store de fallback
            configurado.
          </p>
        </div>
        <div className="feature-card">
          <PlugZap size={20} color="#316ff6" />
          <h3>Tracking end-to-end</h3>
          <p>
            Rappi acepta el tracking token y lo devuelve en eventos y
            conciliación.
          </p>
        </div>
      </div>
    </div>
  );
}
