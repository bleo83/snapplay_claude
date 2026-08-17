import { ArrowRight, CheckCircle2, ShoppingBag } from "lucide-react";
import Link from "next/link";

export default async function HandoffPreviewPage({
  searchParams,
}: {
  searchParams: Promise<{ experience?: string }>;
}) {
  const params = await searchParams;
  return (
    <div className="page" style={{ maxWidth: 720, paddingTop: 90 }}>
      <section className="panel" style={{ textAlign: "center", padding: 44 }}>
        <div
          className="metric-icon green"
          style={{ margin: "0 auto 18px", width: 50, height: 50 }}
        >
          <CheckCircle2 size={25} />
        </div>
        <span className="eyebrow">Handoff demo resuelto</span>
        <h1 style={{ margin: "9px 0", fontSize: 31 }}>
          {params.experience ?? "Movie Night"}
        </h1>
        <p style={{ color: "#687388", lineHeight: 1.6 }}>
          En producción, este paso redirige a la storefront session firmada por
          Rappi. Para localhost mostramos una preview segura.
        </p>
        <div
          className="revenue-callout"
          style={{ margin: "24px 0", textAlign: "left" }}
        >
          <span>
            <ShoppingBag size={19} /> Tracking token creado
          </span>
          <strong>Atribución activa</strong>
        </div>
        <Link className="button primary" href="/smart-links">
          Volver a smart links <ArrowRight size={15} />
        </Link>
      </section>
    </div>
  );
}
