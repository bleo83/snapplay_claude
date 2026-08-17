import { Database, KeyRound, Shield } from "lucide-react";
import { PageHeader } from "@/components/ui";
import { getOrganization } from "@/lib/api";
import { OrganizationForm } from "./organization-form";

export default async function SettingsPage() {
  const organization = await getOrganization();
  return (
    <div className="page">
      <PageHeader
        eyebrow="Workspace"
        title="Configuración"
        description="Datos legales y operativos de la empresa, acceso a Supabase y política de intercambio del piloto."
      />
      <OrganizationForm initialOrganization={organization} />
      <div className="feature-grid" style={{ marginTop: 18 }}>
        <div className="feature-card">
          <Database size={20} color="#169b73" />
          <h3>Supabase</h3>
          <p>
            El modo demo funciona sin credenciales; al desactivarlo, la API
            utiliza el proyecto de desarrollo.
          </p>
        </div>
        <div className="feature-card">
          <KeyRound size={20} color="#d77d18" />
          <h3>Credenciales</h3>
          <p>
            La service role vive únicamente en la API. El browser sólo recibe la
            publishable key.
          </p>
        </div>
        <div className="feature-card">
          <Shield size={20} color="#7558d9" />
          <h3>Política de datos</h3>
          <p>
            PSEUDONYMOUS para el piloto; BILLING_ONLY está disponible para
            relaciones sin detalle compartido.
          </p>
        </div>
      </div>
      <form action="/auth/signout" method="post" style={{ marginTop: 24 }}>
        <button className="button" type="submit">
          Cerrar sesión
        </button>
      </form>
    </div>
  );
}
