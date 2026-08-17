"use client";

import type { Experience } from "@snapplay/contracts";
import { Plus, X } from "lucide-react";
import { useState, type FormEvent } from "react";
import { StatusPill } from "@/components/ui";
import { apiFetch } from "@/lib/api-client";
import { formatDate } from "@/lib/format";

export function ExperiencesClient({
  initialExperiences,
}: {
  initialExperiences: Experience[];
}) {
  const [experiences, setExperiences] = useState(initialExperiences);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    const form = new FormData(event.currentTarget);
    const payload = {
      name: form.get("name"),
      contextTitle: form.get("contextTitle"),
      channel: form.get("channel"),
      handoffMode: form.get("handoffMode"),
      productCount: Number(form.get("productCount")),
      startsAt: new Date(String(form.get("startsAt"))).toISOString(),
      endsAt: null,
    };

    try {
      const response = await apiFetch("/v1/experiences", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!response.ok)
        throw new Error(
          "No se pudo crear. Verificá que la API esté corriendo en localhost:4000.",
        );
      const created = (await response.json()) as Experience;
      setExperiences((current) => [created, ...current]);
      setOpen(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Error inesperado");
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <div className="toolbar" style={{ justifyContent: "flex-end" }}>
        <button className="button primary" onClick={() => setOpen(true)}>
          <Plus size={16} /> Nueva experience
        </button>
      </div>
      <section className="panel table-panel">
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Contexto</th>
                <th>Canal</th>
                <th>Handoff</th>
                <th>SKU</th>
                <th>Versión</th>
                <th>Estado</th>
                <th>Inicio</th>
              </tr>
            </thead>
            <tbody>
              {experiences.map((item) => (
                <tr key={item.id}>
                  <td>
                    <strong>{item.name}</strong>
                  </td>
                  <td>{item.contextTitle}</td>
                  <td>{item.channel}</td>
                  <td>
                    <span className="tag">{item.handoffMode}</span>
                  </td>
                  <td>{item.productCount}</td>
                  <td>v{item.version}</td>
                  <td>
                    <StatusPill status={item.status} />
                  </td>
                  <td>{formatDate(item.startsAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {open ? (
        <div
          className="modal-backdrop"
          role="dialog"
          aria-modal="true"
          aria-labelledby="new-experience-title"
        >
          <div className="modal">
            <div className="modal-header">
              <div>
                <h2 id="new-experience-title">Nueva experience</h2>
                <p>
                  Creá un borrador. Catálogo, contrato y vigencia se validan al
                  publicar.
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
                <label htmlFor="name">Nombre</label>
                <input
                  className="text-input"
                  id="name"
                  name="name"
                  defaultValue="Nueva Movie Night"
                  required
                  minLength={3}
                />
              </div>
              <div className="form-field">
                <label htmlFor="contextTitle">Contenido o contexto</label>
                <input
                  className="text-input"
                  id="contextTitle"
                  name="contextTitle"
                  placeholder="Ej. Toy Story"
                  required
                />
              </div>
              <div className="form-field">
                <label htmlFor="channel">Canal</label>
                <select className="select-input" id="channel" name="channel">
                  <option>Disney+</option>
                  <option>ESPN</option>
                  <option>Hulu</option>
                </select>
              </div>
              <div className="form-field">
                <label htmlFor="handoffMode">Modo de handoff</label>
                <select
                  className="select-input"
                  id="handoffMode"
                  name="handoffMode"
                >
                  <option value="DYNAMIC_STOREFRONT">Dynamic storefront</option>
                  <option value="STORE_DEEPLINK">Store deep link</option>
                  <option value="CART_HANDOFF">Cart handoff</option>
                </select>
              </div>
              <div className="form-field">
                <label htmlFor="productCount">Cantidad inicial de SKU</label>
                <input
                  className="text-input"
                  id="productCount"
                  name="productCount"
                  type="number"
                  defaultValue="2"
                  min="0"
                  max="100"
                />
              </div>
              <div className="form-field full">
                <label htmlFor="startsAt">Inicio</label>
                <input
                  className="text-input"
                  id="startsAt"
                  name="startsAt"
                  type="datetime-local"
                  defaultValue="2026-09-01T20:00"
                  required
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
                  disabled={saving}
                >
                  {saving ? "Creando…" : "Crear borrador"}
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}
    </>
  );
}
