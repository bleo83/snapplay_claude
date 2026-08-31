"use client";

import type { Experience, SmartLink } from "@snapplay/contracts";
import { Copy, Download, Plus, QrCode, X } from "lucide-react";
import { useState, type FormEvent } from "react";
import { StatusPill } from "@/components/ui";
import { apiFetch } from "@/lib/api-client";

export function SmartLinksClient({
  initialLinks,
  publishedExperiences,
}: {
  initialLinks: SmartLink[];
  publishedExperiences: Experience[];
}) {
  const [links, setLinks] = useState(initialLinks);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copyFeedback, setCopyFeedback] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    const form = new FormData(event.currentTarget);
    const payload = {
      experienceId: form.get("experienceId"),
      placementKey: form.get("placementKey"),
    };

    try {
      const response = await apiFetch("/v1/smart-links", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(
          (body as { message?: string }).message ??
            `Error ${response.status}`,
        );
      }
      const created = (await response.json()) as SmartLink;
      setLinks((current) => [created, ...current]);
      setOpen(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Error inesperado");
    } finally {
      setSaving(false);
    }
  }

  async function downloadQr(shortCode: string) {
    try {
      const response = await apiFetch(`/v1/smart-links/${shortCode}/qr`);
      if (!response.ok) throw new Error("No se pudo descargar el QR");
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${shortCode}.png`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch {
      // silently fail — user will retry
    }
  }

  function copyUrl(url: string) {
    navigator.clipboard.writeText(url).then(() => {
      setCopyFeedback(url);
      setTimeout(() => setCopyFeedback(null), 2000);
    });
  }

  return (
    <>
      <div className="toolbar" style={{ justifyContent: "flex-end" }}>
        <button className="button primary" onClick={() => setOpen(true)}>
          <Plus size={16} /> Nuevo link
        </button>
      </div>
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
                    <div style={{ display: "flex", gap: 4 }}>
                      <button
                        className="icon-button"
                        title={
                          copyFeedback === link.url ? "¡Copiado!" : "Copiar URL"
                        }
                        onClick={() => copyUrl(link.url)}
                      >
                        <Copy size={14} />
                      </button>
                      <button
                        className="icon-button"
                        title="Descargar QR"
                        onClick={() => downloadQr(link.shortCode)}
                      >
                        <Download size={14} />
                      </button>
                    </div>
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

      {open ? (
        <div
          className="modal-backdrop"
          role="dialog"
          aria-modal="true"
          aria-labelledby="new-link-title"
        >
          <div className="modal">
            <div className="modal-header">
              <div>
                <h2 id="new-link-title">Nuevo smart link</h2>
                <p>
                  Generá un código QR estable para una experience publicada.
                </p>
              </div>
              <button
                className="icon-button"
                onClick={() => setOpen(false)}
                aria-label="Cerrar"
              >
                <X size={17} />
              </button>
            </div>
            <form className="form-grid" onSubmit={submit}>
              <div className="form-field full">
                <label htmlFor="experienceId">Experience publicada</label>
                <select
                  className="select-input"
                  id="experienceId"
                  name="experienceId"
                  required
                >
                  {publishedExperiences.length === 0 ? (
                    <option value="">— Sin experiences publicadas —</option>
                  ) : (
                    publishedExperiences.map((exp) => (
                      <option key={exp.id} value={exp.id}>
                        {exp.name}
                      </option>
                    ))
                  )}
                </select>
              </div>
              <div className="form-field full">
                <label htmlFor="placementKey">Placement key</label>
                <input
                  className="text-input"
                  id="placementKey"
                  name="placementKey"
                  placeholder="ej. disney-plus.toy-story.endcard"
                  pattern="^[a-z0-9][a-z0-9.\-]*[a-z0-9]$"
                  title="Minúsculas, números, puntos y guiones. Mínimo 3 caracteres."
                  required
                  minLength={3}
                  maxLength={120}
                />
              </div>
              {error ? <div className="inline-alert">{error}</div> : null}
              <div className="modal-actions">
                <button
                  className="button"
                  type="button"
                  onClick={() => setOpen(false)}
                >
                  Cancelar
                </button>
                <button
                  className="button primary"
                  type="submit"
                  disabled={saving || publishedExperiences.length === 0}
                >
                  {saving ? "Creando…" : "Crear link"}
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}
    </>
  );
}
