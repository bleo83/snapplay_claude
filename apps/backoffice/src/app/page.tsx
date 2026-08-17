import {
  ArrowRight,
  ChartNoAxesCombined,
  CircleDollarSign,
  MousePointerClick,
  PackageCheck,
  QrCode,
  ScanLine,
} from "lucide-react";
import Link from "next/link";
import { MetricCard, PageHeader, StatusPill } from "@/components/ui";
import { getDashboard } from "@/lib/api";
import { formatCompact, formatDate, formatMoney } from "@/lib/format";

export const dynamic = "force-dynamic";

export default async function DashboardPage() {
  const dashboard = await getDashboard();
  const { metrics } = dashboard;

  return (
    <div className="page">
      <PageHeader
        eyebrow="Disney × Rappi · Argentina"
        title="Commerce que acompaña la historia"
        description="Seguí el recorrido completo: desde el QR en pantalla hasta la entrega y el revenue share."
        action={
          <Link className="button primary" href="/experiences">
            Nueva experience <ArrowRight size={16} />
          </Link>
        }
      />

      <section className="metric-grid" aria-label="Métricas principales">
        <MetricCard
          label="Escaneos"
          value={formatCompact(metrics.scans)}
          detail="+12,4% vs. período anterior"
          icon={ScanLine}
        />
        <MetricCard
          label="Handoffs a Rappi"
          value={formatCompact(metrics.handoffs)}
          detail={`${((metrics.handoffs / metrics.scans) * 100).toFixed(1)}% de los escaneos`}
          icon={MousePointerClick}
          accent="violet"
        />
        <MetricCard
          label="Órdenes entregadas"
          value={formatCompact(metrics.deliveredOrders)}
          detail={`${metrics.conversionRate.toFixed(2)}% de conversión`}
          icon={PackageCheck}
          accent="green"
        />
        <MetricCard
          label="GMV atribuido"
          value={formatMoney(metrics.gmvMinor, dashboard.currency)}
          detail="Sólo ventas originadas en Snap Play"
          icon={ChartNoAxesCombined}
          accent="orange"
        />
      </section>

      <section className="dashboard-grid">
        <article className="panel performance-panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">Embudo</span>
              <h2>Conversión de la experiencia</h2>
            </div>
            <span className="date-chip">{dashboard.rangeLabel}</span>
          </div>
          <div className="funnel">
            <div className="funnel-row">
              <span>
                <QrCode size={16} /> Escaneos
              </span>
              <strong>{metrics.scans.toLocaleString("es-AR")}</strong>
              <div>
                <i style={{ width: "100%" }} />
              </div>
            </div>
            <div className="funnel-row">
              <span>
                <MousePointerClick size={16} /> Handoffs
              </span>
              <strong>{metrics.handoffs.toLocaleString("es-AR")}</strong>
              <div>
                <i style={{ width: "66.7%" }} />
              </div>
            </div>
            <div className="funnel-row">
              <span>
                <PackageCheck size={16} /> Entregadas
              </span>
              <strong>{metrics.deliveredOrders.toLocaleString("es-AR")}</strong>
              <div>
                <i style={{ width: "24%" }} />
              </div>
            </div>
          </div>
          <div className="revenue-callout">
            <span>
              <CircleDollarSign size={19} /> Revenue share estimado
            </span>
            <strong>
              {formatMoney(metrics.revenueShareMinor, dashboard.currency)}
            </strong>
          </div>
        </article>

        <article className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">En vivo</span>
              <h2>Experiences</h2>
            </div>
            <Link href="/experiences">Ver todas</Link>
          </div>
          <div className="experience-list">
            {dashboard.experiences.map((experience) => (
              <div className="experience-row" key={experience.id}>
                <span
                  className={`channel-badge channel-${experience.channel.toLowerCase().replace("+", "plus")}`}
                >
                  {experience.channel.slice(0, 2)}
                </span>
                <div>
                  <strong>{experience.name}</strong>
                  <small>
                    {experience.contextTitle} · v{experience.version}
                  </small>
                </div>
                <StatusPill status={experience.status} />
              </div>
            ))}
          </div>
        </article>
      </section>

      <section className="panel table-panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Última actividad</span>
            <h2>Órdenes recientes</h2>
          </div>
          <Link href="/orders">Ver órdenes</Link>
        </div>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Orden</th>
                <th>Experience</th>
                <th>Estado</th>
                <th>Productos</th>
                <th>Total</th>
                <th>Hora</th>
              </tr>
            </thead>
            <tbody>
              {dashboard.recentOrders.map((order) => (
                <tr key={order.id}>
                  <td>
                    <strong>{order.providerOrderRef}</strong>
                    <small>{order.publisherUserRef ?? "Datos agregados"}</small>
                  </td>
                  <td>{order.experienceName}</td>
                  <td>
                    <StatusPill status={order.status} />
                  </td>
                  <td>{order.itemCount}</td>
                  <td>
                    <strong>
                      {formatMoney(order.orderTotalMinor, order.currency)}
                    </strong>
                  </td>
                  <td>{formatDate(order.placedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
