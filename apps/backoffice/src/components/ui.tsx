import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

export function PageHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        {eyebrow ? <span className="eyebrow">{eyebrow}</span> : null}
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action ? <div className="page-actions">{action}</div> : null}
    </header>
  );
}

export function MetricCard({
  label,
  value,
  detail,
  icon: Icon,
  accent = "blue",
}: {
  label: string;
  value: string;
  detail: string;
  icon: LucideIcon;
  accent?: "blue" | "green" | "orange" | "violet";
}) {
  return (
    <article className="metric-card">
      <div className={`metric-icon ${accent}`}>
        <Icon size={19} />
      </div>
      <span className="metric-label">{label}</span>
      <strong className="metric-value">{value}</strong>
      <small>{detail}</small>
    </article>
  );
}

const statusLabels: Record<string, string> = {
  PUBLISHED: "Publicada",
  IN_REVIEW: "En revisión",
  DRAFT: "Borrador",
  ACTIVE: "Activo",
  PAUSED: "Pausado",
  DELIVERED: "Entregada",
  NEAR_DESTINATION: "Está llegando",
  PICKED_UP: "En camino",
  CONFIRMED: "Confirmada",
  CANCELLED: "Cancelada",
};

export function StatusPill({ status }: { status: string }) {
  return (
    <span className={`status-pill status-${status.toLowerCase()}`}>
      {statusLabels[status] ?? status}
    </span>
  );
}
