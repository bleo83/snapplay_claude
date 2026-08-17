"use client";

import type { OrganizationProfile } from "@snapplay/contracts";
import { useState, type FormEvent } from "react";
import { apiFetch } from "@/lib/api-client";

export function OrganizationForm({
  initialOrganization,
}: {
  initialOrganization: OrganizationProfile;
}) {
  const [organization, setOrganization] = useState(initialOrganization);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    const form = new FormData(event.currentTarget);
    const body = {
      legalName: form.get("legalName"),
      displayName: form.get("displayName"),
      country: form.get("country"),
      defaultCurrency: form.get("defaultCurrency"),
      timezone: form.get("timezone"),
    };
    try {
      const response = await apiFetch("/v1/organization", {
        method: "PATCH",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      });
      if (!response.ok)
        throw new Error("No se pudieron guardar los datos de la empresa.");
      setOrganization((await response.json()) as OrganizationProfile);
      setMessage("Cambios guardados.");
    } catch (cause) {
      setMessage(cause instanceof Error ? cause.message : "Error inesperado");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="panel" style={{ padding: 24 }}>
      <div className="panel-heading">
        <div>
          <h2>Empresa</h2>
          <p>
            {organization.organizationType} · {organization.status}
          </p>
        </div>
      </div>
      <form className="form-grid" onSubmit={submit}>
        <div className="form-field full">
          <label htmlFor="legalName">Razón social</label>
          <input
            className="text-input"
            id="legalName"
            name="legalName"
            defaultValue={organization.legalName}
            required
          />
        </div>
        <div className="form-field">
          <label htmlFor="displayName">Nombre visible</label>
          <input
            className="text-input"
            id="displayName"
            name="displayName"
            defaultValue={organization.displayName}
            required
          />
        </div>
        <div className="form-field">
          <label htmlFor="timezone">Zona horaria</label>
          <input
            className="text-input"
            id="timezone"
            name="timezone"
            defaultValue={organization.timezone}
            required
          />
        </div>
        <div className="form-field">
          <label htmlFor="country">País ISO</label>
          <input
            className="text-input"
            id="country"
            name="country"
            defaultValue={organization.country}
            pattern="[A-Z]{2}"
            maxLength={2}
            required
          />
        </div>
        <div className="form-field">
          <label htmlFor="defaultCurrency">Moneda ISO</label>
          <input
            className="text-input"
            id="defaultCurrency"
            name="defaultCurrency"
            defaultValue={organization.defaultCurrency}
            pattern="[A-Z]{3}"
            maxLength={3}
            required
          />
        </div>
        {message ? <div className="inline-alert">{message}</div> : null}
        <div className="modal-actions">
          <button className="button primary" disabled={saving} type="submit">
            {saving ? "Guardando…" : "Guardar empresa"}
          </button>
        </div>
      </form>
    </section>
  );
}
