import { Sparkles } from "lucide-react";
import { login } from "./actions";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { error } = await searchParams;
  return (
    <main className="login-page">
      <section className="login-card">
        <div className="login-brand">
          <span className="brand-mark">
            <Sparkles size={18} />
          </span>
          <strong>Snap Play</strong>
        </div>
        <div>
          <p className="eyebrow">BACKOFFICE SEGURO</p>
          <h1>Ingresá a tu workspace</h1>
          <p className="muted">
            Administrá experiences, catálogo, atribución y conciliación desde un
            solo lugar.
          </p>
        </div>
        <form action={login} className="login-form">
          <label>
            Email
            <input
              className="text-input"
              name="email"
              type="email"
              autoComplete="email"
              required
            />
          </label>
          <label>
            Contraseña
            <input
              className="text-input"
              name="password"
              type="password"
              autoComplete="current-password"
              minLength={8}
              required
            />
          </label>
          {error ? <div className="inline-alert">{error}</div> : null}
          <button className="button primary" type="submit">
            Ingresar
          </button>
        </form>
        <p className="login-note">
          El acceso se valida con Supabase Auth y la membresía de la
          organización.
        </p>
      </section>
    </main>
  );
}
