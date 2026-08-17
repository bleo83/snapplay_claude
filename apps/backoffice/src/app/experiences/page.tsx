import { PageHeader } from "@/components/ui";
import { getExperiences } from "@/lib/api";
import { ExperiencesClient } from "./experiences-client";

export const dynamic = "force-dynamic";

export default async function ExperiencesPage() {
  const experiences = await getExperiences();
  return (
    <div className="page">
      <PageHeader
        eyebrow="Contenido → oferta → delivery"
        title="Experiences"
        description="Definí qué productos aparecen para cada película, channel o evento y publicá versiones inmutables."
      />
      <ExperiencesClient initialExperiences={experiences} />
    </div>
  );
}
