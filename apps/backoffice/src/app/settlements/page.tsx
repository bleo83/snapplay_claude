import { Calculator, Download } from "lucide-react";
import { PageHeader, StatusPill } from "@/components/ui";
import { formatMoney } from "@/lib/format";

export default function SettlementsPage() {
  return (
    <div className="page">
      <PageHeader
        eyebrow="Ledger y conciliación"
        title="Conciliación"
        description="Compará órdenes Rappi, fees y revenue share antes de emitir el statement."
        action={
          <button className="button">
            <Calculator size={15} /> Recalcular período
          </button>
        }
      />
      <section className="panel table-panel">
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Período</th>
                <th>Órdenes</th>
                <th>GMV</th>
                <th>Fee Snap Play</th>
                <th>Revenue share</th>
                <th>Diferencias</th>
                <th>Estado</th>
                <th />
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>
                  <strong>Agosto 2026</strong>
                </td>
                <td>2.314</td>
                <td>{formatMoney(6849500000)}</td>
                <td>{formatMoney(57850000)}</td>
                <td>{formatMoney(217840000)}</td>
                <td>3</td>
                <td>
                  <StatusPill status="IN_REVIEW" />
                </td>
                <td>
                  <button className="icon-button">
                    <Download size={14} />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
