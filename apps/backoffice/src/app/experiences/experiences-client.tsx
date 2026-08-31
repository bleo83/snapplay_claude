"use client";

import type { Experience } from "@snapplay/contracts";
import { Copy, Plus, X } from "lucide-react";
import { useState, type FormEvent } from "react";
import { StatusPill } from "@/components/ui";
import { apiFetch } from "@/lib/api-client";
import { formatDate } from "@/lib/format";

const DEMO_CONNECTION_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

export function ExperiencesClient({
  initialExperiences,
}: {
  initialExperiences: Experience[];
}) {
  const [experiences, setExperiences] = useState(initialExperiences);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    const form = new FormData(event.currentTarget);
    const payload = {
      name: form.get("name"),
      contextTitle: form.get("contextTitle"),
      connectionId: form.get("connectionId"),
      territory: form.get("territory"),
      destination: {
        providerStoreId: form.get("providerStoreId"),
        providerCategoryId: form.get("providerCategoryId"),
      },
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
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(
          (body as { message?: string }).message ??
            "No se pudo crear. Verificá que la API esté corriendo.",
        );
      }
      const created = (await response.json()) as Experience;
      setExperiences((current) => [created, ...current]);
      setOpen(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Error inesperado");
    } finally {
      setSaving(false);
    }
  }

  async function runAction(
    id: string,
    method: "PATCH" | "POST",
    path: string,
  ) {
    setActionError(null);
    try {
      const response = await apiFetch(`/v1/experiences/${id}/${path}`, {
        method,
      });
      if (!response.ok) throw new Error(`Error ${response.status}`);
      const updated = (await response.json()) as Experience;
      setExperiences((current) =>
        current.map((e) => (e.id === updated.id ? updated : e)),
      );
      // Clone creates a new experience — append it
      if (path === "clone") {
        setExperiences((current) => [updated, ...current]);
      }
    } catch (cause) {
      setActionError(
        cause instanceof Error ? cause.message : "Acción fallida",
      );
    }
  }

  return (
    <>
      <div className="toolbar" style={{ justifyContent: "flex-end" }}>
        <button className="button primary" onClick={() => setOpen(true)}>
          <Plus size={16} /> Nueva experience
        </button>
      </div>
      {actionError ? (
        <div className="inline-alert" style={{ marginBottom: 12 }}>
          {actionError}
        </div>
      ) : null}
      <section className="panel table-panel">
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Contexto</th>
                <th>Destino</th>
                <th>Handoff</th>
                <th>Versión</th>
                <th>Estado</th>
                <th>Inicio</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {experiences.map((item) => (
                <tr key={item.id}>
                  <td>
                    <strong>{item.name}</strong>
                  </td>
                  <td>{item.contextTitle}</td>
                  <td>
                    {item.destination ? (
                      <span className="tag" style={{ fontSize: 11 }}>
                        {item.destination.providerStoreId} /{" "}
                        {item.destination.providerCategoryId}
                      </span>
                    ) : (
                      <span className="muted">—</span>
                    )}
                  </td>
                  <td>
                    <span className="tag">{item.handoffMode}</span>
                  </td>
                  <td>v{item.version}</td>
                  <td>
                    <StatusPill status={item.status} />
                  </td>
                  <td>{formatDate(item.startsAt)}</td>
                  <td>
                    <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
                      {(item.status === "DRAFT" ||
                        item.status === "IN_REVIEW") && (
                        <button
                          className="button primary"
                          style={{ fontSize: 12, padding: "3px 10px" }}
                          onClick={() =>
                            runAction(item.id, "PATCH", "publish")
                          }
                        >
                          Publicar
                        </button>
                      )}
                      {item.status === "PUBLISHED" && (
                        <>
                          <button
                            className="button"
                            style={{ fontSize: 12, padding: "3px 10px" }}
                            onClick={() =>
                              runAction(item.id, "PATCH", "pause")
                            }
                          >
                            Pausar
                          </button>
                          <button
                            className="button"
                            style={{ fontSize: 12, padding: "3px 10px" }}
                            onClick={() =>
                              runAction(item.id, "POST", "clone")
                            }
                          >
                            Clonar
                          </button>
                        </>
                      )}
                      {(item.status === "PUBLISHED" ||
                        item.status === "PAUSED") && (
                        <button
                          className="button"
                          style={{ fontSize: 12, padding: "3px 10px", color: "var(--red, #c0392b)" }}
                          onClick={() =>
                            runAction(item.id, "PATCH", "retire")
                          }
                        >
                          Retirar
                        </button>
                      )}
                      {item.status === "PAUSED" && (
                        <button
                          className="button"
                          style={{ fontSize: 12, padding: "3px 10px" }}
                          onClick={() =>
                            runAction(item.id, "POST", "clone")
                          }
                        >
                          Clonar
                        </button>
                      )}
                    </div>
                  </td>
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
                  Creá un borrador con store y categoría. Contrato y catálogo se
                  validan al publicar.
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
              {/* Hidden: connectionId for demo — in production fetched from /v1/connections */}
              <input
                type="hidden"
                name="connectionId"
                value={DEMO_CONNECTION_ID}
              />
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
                <label htmlFor="territory">Territorio</label>
                <select
                  className="select-input"
                  id="territory"
                  name="territory"
                >
                  <option value="AR">Argentina (AR)</option>
                  <option value="MX">México (MX)</option>
                  <option value="CO">Colombia (CO)</option>
                  <option value="BR">Brasil (BR)</option>
                </select>
              </div>
              <div className="form-field">
                <label htmlFor="providerStoreId">Store ID (Rappi)</label>
                <input
                  className="text-input"
                  id="providerStoreId"
                  name="providerStoreId"
                  defaultValue="rappi-store-ar-001"
                  required
                />
              </div>
              <div className="form-field">
                <label htmlFor="providerCategoryId">Categoría ID (Rappi)</label>
                <input
                  className="text-input"
                  id="providerCategoryId"
                  name="providerCategoryId"
                  defaultValue="snacks-drinks"
                  required
                />
              </div>
              <div className="form-field">
                <label htmlFor="handoffMode">Modo de handoff</label>
                <select
                  className="select-input"
                  id="handoffMode"
                  name="handoffMode"
                >
                  <option value="STORE_DEEPLINK">Store deep link</option>
                  <option value="DYNAMIC_STOREFRONT">Dynamic storefront</option>
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
