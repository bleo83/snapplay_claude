"use client";

import { useState, useEffect, useCallback } from "react";
import {
  ScanLine,
  MousePointerClick,
  PackageCheck,
  ChartNoAxesCombined,
  CircleDollarSign,
  Clock,
  RefreshCw,
} from "lucide-react";
import { MetricCard } from "@/components/ui";
import { apiFetch } from "@/lib/api-client";
import { formatCompact, formatMoney } from "@/lib/format";

interface DashboardMetrics {
  scans: number;
  handoffs: number;
  deliveredOrders: number;
  gmvMinor: number;
  conversionRate: number;
  revenueShareMinor: number;
}

interface FunnelStep {
  label: string;
  count: number;
  pct: number;
}

interface DashboardData {
  rangeLabel: string;
  currency: string;
  freshness: string;
  metrics: DashboardMetrics;
  funnelSteps: FunnelStep[];
}

const PERIOD_OPTIONS = [
  { label: "7 dias", days: 7 },
  { label: "14 dias", days: 14 },
  { label: "30 dias", days: 30 },
  { label: "90 dias", days: 90 },
];

export function AnalyticsClient() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [periodDays, setPeriodDays] = useState(30);
  const [excludeBots, setExcludeBots] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const to = new Date().toISOString();
      const from = new Date(
        Date.now() - periodDays * 24 * 60 * 60 * 1000,
      ).toISOString();
      const res = await apiFetch(
        `/v1/dashboard?from=${from}&to=${to}`,
      );
      if (!res.ok) throw new Error(`API returned ${res.status}`);
      const json = await res.json();
      setData(json);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error desconocido");
    } finally {
      setLoading(false);
    }
  }, [periodDays]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  if (loading && !data) {
    return <div className="panel" style={{ padding: "2rem", textAlign: "center" }}>Cargando analytics...</div>;
  }

  if (error && !data) {
    return (
      <div className="panel" style={{ padding: "2rem" }}>
        <p style={{ color: "var(--red)" }}>Error: {error}</p>
        <button className="button" onClick={fetchData}>Reintentar</button>
      </div>
    );
  }

  if (!data) return null;

  const { metrics, funnelSteps } = data;

  return (
    <>
      {/* Filters */}
      <section className="panel" style={{ padding: "1rem 1.5rem", display: "flex", gap: "1rem", alignItems: "center", flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
          <label style={{ fontSize: "0.85rem", fontWeight: 500 }}>Periodo:</label>
          {PERIOD_OPTIONS.map((opt) => (
            <button
              key={opt.days}
              className={`button ${periodDays === opt.days ? "primary" : ""}`}
              style={{ padding: "0.35rem 0.75rem", fontSize: "0.8rem" }}
              onClick={() => setPeriodDays(opt.days)}
            >
              {opt.label}
            </button>
          ))}
        </div>

        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center", marginLeft: "auto" }}>
          <label style={{ fontSize: "0.8rem", display: "flex", alignItems: "center", gap: "0.3rem" }}>
            <input
              type="checkbox"
              checked={excludeBots}
              onChange={(e) => setExcludeBots(e.target.checked)}
            />
            Excluir bots
          </label>
          <button className="button" onClick={fetchData} style={{ padding: "0.35rem 0.75rem" }}>
            <RefreshCw size={14} />
          </button>
        </div>
      </section>

      {/* Freshness indicator */}
      <div style={{ display: "flex", alignItems: "center", gap: "0.4rem", fontSize: "0.8rem", color: "var(--text-muted)", padding: "0.5rem 0" }}>
        <Clock size={13} />
        Actualizado: {new Date(data.freshness).toLocaleString("es-AR")}
        <span style={{ margin: "0 0.3rem" }}>|</span>
        {data.rangeLabel}
        {error && <span style={{ color: "var(--red)", marginLeft: "0.5rem" }}>({error})</span>}
      </div>

      {/* Metric cards */}
      <section className="metric-grid" aria-label="Metricas del piloto">
        <MetricCard
          label="Escaneos"
          value={formatCompact(metrics.scans)}
          detail="QR scans unicos (sin bots)"
          icon={ScanLine}
        />
        <MetricCard
          label="Redirects a Rappi"
          value={formatCompact(metrics.handoffs)}
          detail={metrics.scans > 0 ? `${((metrics.handoffs / metrics.scans) * 100).toFixed(1)}% de escaneos` : "—"}
          icon={MousePointerClick}
          accent="violet"
        />
        <MetricCard
          label="Ordenes entregadas"
          value={formatCompact(metrics.deliveredOrders)}
          detail={`${metrics.conversionRate.toFixed(2)}% conversion`}
          icon={PackageCheck}
          accent="green"
        />
        <MetricCard
          label="GMV atribuido"
          value={formatMoney(metrics.gmvMinor, data.currency)}
          detail="Solo ventas originadas en Snap Play"
          icon={ChartNoAxesCombined}
          accent="orange"
        />
      </section>

      {/* Funnel */}
      <section className="panel performance-panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Embudo de conversion</span>
            <h2>Atribucion del piloto</h2>
          </div>
          <span className="date-chip">{data.rangeLabel}</span>
        </div>

        <div className="funnel">
          {funnelSteps.map((step) => (
            <div className="funnel-row" key={step.label}>
              <span>{step.label}</span>
              <strong>{step.count.toLocaleString("es-AR")}</strong>
              <div>
                <i style={{ width: `${Math.max(step.pct, 2)}%` }} />
              </div>
              <small style={{ minWidth: "3rem", textAlign: "right" }}>{step.pct.toFixed(1)}%</small>
            </div>
          ))}
        </div>

        <div className="revenue-callout">
          <span>
            <CircleDollarSign size={19} /> Revenue share estimado (10%)
          </span>
          <strong>{formatMoney(metrics.revenueShareMinor, data.currency)}</strong>
        </div>
      </section>

      {/* Disclaimer */}
      <p style={{ fontSize: "0.75rem", color: "var(--text-muted)", fontStyle: "italic", padding: "0.5rem 0" }}>
        Redirect no equivale a checkout ni a conversion. Los datos excluyen trafico de prueba y bots.
        Store/category provienen del snapshot de la experience al momento del handoff, no del estado actual del catalogo.
      </p>
    </>
  );
}
