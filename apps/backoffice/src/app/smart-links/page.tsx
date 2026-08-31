import { PageHeader } from "@/components/ui";
import { getExperiences, getSmartLinks } from "@/lib/api";
import { SmartLinksClient } from "./smart-links-client";

export const dynamic = "force-dynamic";

export default async function SmartLinksPage() {
  const [links, experiences] = await Promise.all([
    getSmartLinks(),
    getExperiences(),
  ]);
  const publishedExperiences = experiences.filter(
    (e) => e.status === "PUBLISHED",
  );
  return (
    <div className="page">
      <PageHeader
        eyebrow="QR estable · destino dinámico"
        title="Smart links"
        description="Cada QR apunta primero a Snap Play. Podés cambiar la experience o el destino sin volver a emitir la pieza."
      />
      <SmartLinksClient
        initialLinks={links}
        publishedExperiences={publishedExperiences}
      />
    </div>
  );
}
