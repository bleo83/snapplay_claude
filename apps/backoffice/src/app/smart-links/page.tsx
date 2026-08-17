import { Copy, Plus, QrCode } from "lucide-react";
import { PageHeader, StatusPill } from "@/components/ui";
import { getSmartLinks } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function SmartLinksPage() {
  const links = await getSmartLinks();
  return (
    <div className="page">
      <PageHeader
        eyebrow="QR estable · destino dinámico"
        title="Smart links"
        description="Cada QR apunta primero a Snap Play. Podés cambiar la experience o el destino sin volver a emitir la pieza."
        action={
          <button className="button primary">
            <Plus size={16} /> Nuevo link
          </button>
        }
      />
      <section className="panel table-panel">
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Experience</th>
                <th>Placement</th>
                <th>URL</th>
                <th>Estado</th>
                <th>Escaneos</th>
                <th>Conversiones</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {links.map((link) => (
                <tr key={link.id}>
                  <td>
                    <strong>{link.experienceName}</strong>
                  </td>
                  <td>{link.placementKey}</td>
                  <td>
                    <code>{link.url}</code>
                  </td>
                  <td>
                    <StatusPill status={link.status} />
                  </td>
                  <td>{link.scans.toLocaleString("es-AR")}</td>
                  <td>{link.conversions.toLocaleString("es-AR")}</td>
                  <td>
                    <button className="icon-button" title="Copiar">
                      <Copy size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      <div
        className="feature-card"
        style={{
          marginTop: 18,
          display: "flex",
          alignItems: "center",
          gap: 14,
        }}
      >
        <QrCode size={30} color="#316ff6" />
        <div>
          <h3>El QR no contiene información sensible</h3>
          <p>
            El código es aleatorio; película, partner, usuario y contrato se
            resuelven del lado del servidor.
          </p>
        </div>
      </div>
    </div>
  );
}
