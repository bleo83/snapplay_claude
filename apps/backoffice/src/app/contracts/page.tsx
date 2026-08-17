import { FileCheck2, Plus, ShieldCheck } from "lucide-react";
import { PageHeader, StatusPill } from "@/components/ui";
import { formatMoney } from "@/lib/format";

export default function ContractsPage() {
  return (
    <div className="page">
      <PageHeader
        eyebrow="Reglas económicas versionadas"
        title="Contratos"
        description="Definí qué evento es cobrable, fees, revenue share, vigencia, productos elegibles y política de datos."
        action={
          <button className="button primary">
            <Plus size={16} /> Nuevo contrato
          </button>
        }
      />
      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Versión 3</span>
            <h2>Disney × Rappi Argentina</h2>
          </div>
          <StatusPill status="ACTIVE" />
        </div>
        <div className="feature-grid">
          <div className="feature-card">
            <FileCheck2 size={20} color="#316ff6" />
            <h3>Fee de orquestación</h3>
            <p>
              {formatMoney(25000, "ARS")} por orden entregada y atribuida.
              Ajuste si existe refund total.
            </p>
          </div>
          <div className="feature-card">
            <ShieldCheck size={20} color="#7558d9" />
            <h3>Revenue share Disney</h3>
            <p>
              8% sobre el valor neto de merchandising elegible; política
              PSEUDONYMOUS.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}
