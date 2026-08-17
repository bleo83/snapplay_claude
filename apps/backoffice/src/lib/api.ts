import "server-only";

import {
  dashboardSchema,
  demoDashboard,
  demoExperiences,
  demoOrders,
  demoOrganization,
  demoProducts,
  demoSmartLinks,
  type CatalogProduct,
  type Dashboard,
  type Experience,
  type Order,
  type OrganizationProfile,
  type SmartLink,
} from "@snapplay/contracts";
import { isAuthEnabled } from "./supabase/config";
import { createClient } from "./supabase/server";

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:4000";

async function apiGet<T>(path: string, fallback: T): Promise<T> {
  try {
    const headers = new Headers();
    if (isAuthEnabled) {
      const { data } = await (await createClient()).auth.getSession();
      if (data.session?.access_token)
        headers.set("authorization", `Bearer ${data.session.access_token}`);
    }
    const response = await fetch(`${apiUrl}${path}`, {
      headers,
      cache: "no-store",
      signal: AbortSignal.timeout(1_500),
    });
    if (!response.ok) {
      if (isAuthEnabled)
        throw new Error(`Snap Play API returned ${response.status}`);
      return fallback;
    }
    return (await response.json()) as T;
  } catch (cause) {
    if (isAuthEnabled) throw cause;
    return fallback;
  }
}

export async function getDashboard(): Promise<Dashboard> {
  const value = await apiGet("/v1/dashboard", demoDashboard);
  return dashboardSchema.parse(value);
}

export async function getOrganization(): Promise<OrganizationProfile> {
  return apiGet("/v1/organization", demoOrganization);
}

export async function getProducts(): Promise<CatalogProduct[]> {
  const response = await apiGet<{ items: CatalogProduct[] }>(
    "/v1/catalog/products",
    { items: demoProducts },
  );
  return response.items;
}

export async function getExperiences(): Promise<Experience[]> {
  const response = await apiGet<{ items: Experience[] }>("/v1/experiences", {
    items: demoExperiences,
  });
  return response.items;
}

export async function getSmartLinks(): Promise<SmartLink[]> {
  const response = await apiGet<{ items: SmartLink[] }>("/v1/smart-links", {
    items: demoSmartLinks,
  });
  return response.items;
}

export async function getOrders(): Promise<Order[]> {
  const response = await apiGet<{ items: Order[] }>("/v1/orders", {
    items: demoOrders,
  });
  return response.items;
}
