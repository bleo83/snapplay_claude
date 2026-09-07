"use client";

import {
  Boxes,
  ChartNoAxesCombined,
  ChevronDown,
  CircleDollarSign,
  Clapperboard,
  FileText,
  Link2,
  PackageCheck,
  PlugZap,
  Settings,
  Sparkles,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";

const navigation = [
  { href: "/", label: "Resumen", icon: ChartNoAxesCombined },
  { href: "/analytics", label: "Analytics", icon: Sparkles },
  { href: "/catalog", label: "Catálogo", icon: Boxes },
  { href: "/experiences", label: "Experiences", icon: Clapperboard },
  { href: "/smart-links", label: "Smart links", icon: Link2 },
  { href: "/orders", label: "Órdenes", icon: PackageCheck },
  { href: "/contracts", label: "Contratos", icon: FileText },
  { href: "/settlements", label: "Conciliación", icon: CircleDollarSign },
  { href: "/connections", label: "Conexiones", icon: PlugZap },
];

const isDemoMode = process.env.NEXT_PUBLIC_SNAPPLAY_DEMO_MODE === "true";

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const [userEmail, setUserEmail] = useState<string | null>(null);

  useEffect(() => {
    if (isDemoMode) return;
    // Dynamically import to avoid bundling Supabase on every page
    import("@/lib/supabase/client").then(({ createClient }) => {
      createClient()
        .auth.getUser()
        .then(({ data }) => {
          setUserEmail(data.user?.email ?? null);
        });
    });
  }, []);

  if (pathname.startsWith("/login")) return children;

  const displayName = isDemoMode
    ? "Demo User"
    : (userEmail?.split("@")[0] ?? "…");
  const displayEmail = isDemoMode ? "Datos demo activos" : (userEmail ?? "…");

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">
            <Sparkles size={18} />
          </span>
          <div>
            <strong>Snap Play</strong>
            <small>Commerce orchestration</small>
          </div>
        </div>

        <div className="workspace-switcher">
          <span className="workspace-avatar">D</span>
          <div>
            <strong>Disney</strong>
            <small>Piloto Argentina</small>
          </div>
          <ChevronDown size={16} />
        </div>

        <nav className="navigation" aria-label="Principal">
          {navigation.map(({ href, label, icon: Icon }) => {
            const active =
              href === "/" ? pathname === "/" : pathname.startsWith(href);
            return (
              <Link
                className={active ? "nav-link active" : "nav-link"}
                href={href}
                key={href}
              >
                <Icon size={18} strokeWidth={1.8} />
                <span>{label}</span>
              </Link>
            );
          })}
        </nav>

        <div className="sidebar-footer">
          <Link className="nav-link" href="/settings">
            <Settings size={18} strokeWidth={1.8} />
            <span>Configuración</span>
          </Link>
          <div className={`demo-badge ${isDemoMode ? "demo" : "live"}`}>
            <span />
            {isDemoMode ? "DEMO — datos de prueba" : "PRODUCCIÓN"}
          </div>
          <div className="profile">
            <span className="profile-avatar">
              {displayName.slice(0, 2).toUpperCase()}
            </span>
            <div>
              <strong>{displayName}</strong>
              <small>{displayEmail}</small>
            </div>
          </div>
        </div>
      </aside>
      <main className="main-content">{children}</main>
    </div>
  );
}
