import { PageHeader } from "@/components/ui";
import { AnalyticsClient } from "./analytics-client";

export const dynamic = "force-dynamic";

export default function AnalyticsPage() {
  return (
    <div className="page">
      <PageHeader
        eyebrow="Piloto Disney x Rappi"
        title="Analytics del piloto"
        description="Funnel de atribución con filtros interactivos. Redirect no equivale a checkout ni conversión."
      />
      <AnalyticsClient />
    </div>
  );
}
