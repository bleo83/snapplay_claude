import crypto from "node:crypto";
import {
  demoDashboard,
  type CatalogProduct,
  type Dashboard,
  type Experience,
  type OrganizationProfile,
  type Order,
  type SmartLink,
} from "@snapplay/contracts";
import { env } from "./config.js";
import { addDemoExperience, demoStore } from "./demo-store.js";
import { getSupabaseServiceClient } from "./supabase.js";

export interface ProductFilters {
  q?: string | undefined;
  category?: string | undefined;
  status?: "ACTIVE" | "INACTIVE" | undefined;
}

export interface CreateExperienceInput {
  name: string;
  contextTitle: string;
  channel: string;
  handoffMode: Experience["handoffMode"];
  productCount: number;
  startsAt: string;
  endsAt: string | null;
}

export interface Repository {
  organization(organizationId: string): Promise<OrganizationProfile>;
  updateOrganization(
    organizationId: string,
    input: Pick<
      OrganizationProfile,
      "legalName" | "displayName" | "country" | "defaultCurrency" | "timezone"
    >,
  ): Promise<OrganizationProfile>;
  dashboard(organizationId: string): Promise<Dashboard>;
  products(
    organizationId: string,
    filters: ProductFilters,
  ): Promise<CatalogProduct[]>;
  experiences(organizationId: string): Promise<Experience[]>;
  createExperience(
    organizationId: string,
    actorUserId: string,
    input: CreateExperienceInput,
  ): Promise<Experience>;
  smartLinks(organizationId: string): Promise<SmartLink[]>;
  orders(organizationId: string, status?: Order["status"]): Promise<Order[]>;
  resolveSmartLink(
    shortCode: string,
  ): Promise<{ experienceName: string; trackingToken: string } | null>;
}

function filterProducts(
  products: CatalogProduct[],
  filters: ProductFilters,
): CatalogProduct[] {
  const query = filters.q?.trim().toLocaleLowerCase("es");
  return products.filter((product) => {
    const matchesText =
      !query ||
      product.name.toLocaleLowerCase("es").includes(query) ||
      product.description?.toLocaleLowerCase("es").includes(query);
    return (
      matchesText &&
      (!filters.category || product.categories.includes(filters.category)) &&
      (!filters.status || product.status === filters.status)
    );
  });
}

const demoRepository: Repository = {
  async organization() {
    return demoStore.organization;
  },
  async updateOrganization(_organizationId, input) {
    Object.assign(demoStore.organization, input);
    return demoStore.organization;
  },
  async dashboard() {
    return demoStore.dashboard;
  },
  async products(_organizationId, filters) {
    return filterProducts(demoStore.products, filters);
  },
  async experiences() {
    return demoStore.experiences;
  },
  async createExperience(_organizationId, _actorUserId, input) {
    return addDemoExperience({
      id: crypto.randomUUID(),
      ...input,
      version: 1,
      status: "DRAFT",
    });
  },
  async smartLinks() {
    return demoStore.smartLinks;
  },
  async orders(_organizationId, status) {
    return status
      ? demoStore.orders.filter((order) => order.status === status)
      : demoStore.orders;
  },
  async resolveSmartLink(shortCode) {
    const link = demoStore.smartLinks.find(
      (item) => item.shortCode === shortCode && item.status === "ACTIVE",
    );
    return link
      ? {
          experienceName: link.experienceName,
          trackingToken: crypto.randomBytes(24).toString("base64url"),
        }
      : null;
  },
};

type Row = Record<string, any>;

function relation(row: Row | Row[] | null | undefined): Row | undefined {
  return Array.isArray(row) ? row[0] : (row ?? undefined);
}

function required<T>(value: T | null | undefined, message: string): T {
  if (value === null || value === undefined) throw new Error(message);
  return value;
}

async function connectionIds(organizationId: string): Promise<string[]> {
  const supabase = getSupabaseServiceClient();
  const { data, error } = await supabase
    .from("connections")
    .select("id")
    .or(
      `content_organization_id.eq.${organizationId},commerce_organization_id.eq.${organizationId}`,
    );
  if (error) throw error;
  return (data ?? []).map((row) => String(row.id));
}

async function supabaseExperiences(
  organizationId: string,
): Promise<Experience[]> {
  const supabase = getSupabaseServiceClient();
  const { data, error } = await supabase
    .from("experiences")
    .select(
      `
      id, name, status, current_version,
      content_contexts!inner(title, channels!inner(display_name)),
      experience_versions(version, handoff_mode, product_ids, effective_from, effective_to)
    `,
    )
    .eq("organization_id", organizationId)
    .order("created_at", { ascending: false });
  if (error) throw error;

  return ((data as Row[] | null) ?? []).map((row) => {
    const context = relation(row.content_contexts);
    const channel = relation(context?.channels);
    const versions = (row.experience_versions as Row[] | undefined) ?? [];
    const version =
      versions.find(
        (item) => Number(item.version) === Number(row.current_version),
      ) ?? versions.sort((a, b) => Number(b.version) - Number(a.version))[0];
    return {
      id: String(row.id),
      name: String(row.name),
      contextTitle: String(context?.title ?? "Sin contexto"),
      channel: String(channel?.display_name ?? "Sin canal"),
      version: Number(row.current_version),
      status: row.status as Experience["status"],
      handoffMode: (version?.handoff_mode ??
        "STORE_DEEPLINK") as Experience["handoffMode"],
      productCount: Array.isArray(version?.product_ids)
        ? version.product_ids.length
        : 0,
      startsAt: String(version?.effective_from ?? new Date().toISOString()),
      endsAt: version?.effective_to ? String(version.effective_to) : null,
    };
  });
}

async function supabaseProducts(
  organizationId: string,
  filters: ProductFilters,
): Promise<CatalogProduct[]> {
  const ids = await connectionIds(organizationId);
  if (ids.length === 0) return [];
  const supabase = getSupabaseServiceClient();
  let query = supabase
    .from("catalog_products")
    .select("*")
    .in("connection_id", ids);
  if (filters.status) query = query.eq("status", filters.status);
  if (filters.category)
    query = query.contains("categories", [filters.category]);
  if (filters.q)
    query = query.or(
      `name.ilike.%${filters.q}%,description.ilike.%${filters.q}%`,
    );
  const { data, error } = await query.order("name");
  if (error) throw error;
  return (data ?? []).map((row) => ({
    id: String(row.id),
    providerProductId: String(row.provider_product_id),
    name: String(row.name),
    description: row.description === null ? null : String(row.description),
    imageUrl: String(
      row.image_url ?? "https://placehold.co/640x480?text=Producto",
    ),
    brand: row.brand === null ? null : String(row.brand),
    categories: (row.categories ?? []).map(String),
    ageRestricted: Boolean(row.age_restricted),
    referencePriceMinor:
      row.reference_price_minor === null
        ? null
        : Number(row.reference_price_minor),
    currency: row.currency === null ? null : String(row.currency),
    status: row.status as CatalogProduct["status"],
  }));
}

async function supabaseOrders(
  organizationId: string,
  status?: Order["status"],
): Promise<Order[]> {
  const ids = await connectionIds(organizationId);
  if (ids.length === 0) return [];
  const supabase = getSupabaseServiceClient();
  let query = supabase
    .from("provider_orders")
    .select(
      `
      id, provider_order_ref, status, order_total_minor, eligible_item_value_minor,
      currency, publisher_user_ref, placed_at,
      provider_order_items(count),
      handoff_sessions(experience_versions(experiences(name)))
    `,
    )
    .in("connection_id", ids);
  if (status) query = query.eq("status", status);
  const { data, error } = await query
    .order("placed_at", { ascending: false })
    .limit(100);
  if (error) throw error;
  return ((data as Row[] | null) ?? []).map((row) => {
    const handoff = relation(row.handoff_sessions);
    const version = relation(handoff?.experience_versions);
    const experience = relation(version?.experiences);
    const itemAggregate = relation(row.provider_order_items);
    return {
      id: String(row.id),
      providerOrderRef: String(row.provider_order_ref),
      experienceName: String(experience?.name ?? "Sin atribución"),
      status: row.status as Order["status"],
      orderTotalMinor: Number(row.order_total_minor),
      eligibleItemValueMinor:
        row.eligible_item_value_minor === null
          ? null
          : Number(row.eligible_item_value_minor),
      currency: String(row.currency),
      itemCount: Number(itemAggregate?.count ?? 0),
      placedAt: String(row.placed_at),
      publisherUserRef:
        row.publisher_user_ref === null ? null : String(row.publisher_user_ref),
    };
  });
}

const supabaseRepository: Repository = {
  async organization(organizationId) {
    const supabase = getSupabaseServiceClient();
    const { data, error } = await supabase
      .from("organizations")
      .select("*")
      .eq("id", organizationId)
      .single();
    if (error) throw error;
    return {
      id: String(data.id),
      legalName: String(data.legal_name),
      displayName: String(data.display_name),
      organizationType:
        data.organization_type as OrganizationProfile["organizationType"],
      country: String(data.country),
      defaultCurrency: String(data.default_currency),
      timezone: String(data.timezone),
      status: data.status as OrganizationProfile["status"],
    };
  },
  async updateOrganization(organizationId, input) {
    const supabase = getSupabaseServiceClient();
    const { error } = await supabase
      .from("organizations")
      .update({
        legal_name: input.legalName,
        display_name: input.displayName,
        country: input.country,
        default_currency: input.defaultCurrency,
        timezone: input.timezone,
        updated_at: new Date().toISOString(),
      })
      .eq("id", organizationId);
    if (error) throw error;
    return this.organization(organizationId);
  },
  async dashboard(organizationId) {
    const [experiences, orders] = await Promise.all([
      supabaseExperiences(organizationId),
      supabaseOrders(organizationId),
    ]);
    const delivered = orders.filter((order) => order.status === "DELIVERED");
    const ids = await connectionIds(organizationId);
    const supabase = getSupabaseServiceClient();
    const { count: scans, error } =
      ids.length === 0
        ? { count: 0, error: null }
        : await supabase
            .from("handoff_sessions")
            .select("id", { count: "exact", head: true })
            .in("connection_id", ids);
    if (error) throw error;
    const scanCount = scans ?? 0;
    return {
      rangeLabel: "Datos disponibles",
      currency: delivered[0]?.currency ?? orders[0]?.currency ?? "ARS",
      metrics: {
        scans: scanCount,
        handoffs: scanCount,
        deliveredOrders: delivered.length,
        gmvMinor: delivered.reduce(
          (total, order) => total + order.orderTotalMinor,
          0,
        ),
        conversionRate:
          scanCount === 0
            ? 0
            : Number(((delivered.length / scanCount) * 100).toFixed(2)),
        revenueShareMinor: 0,
      },
      experiences,
      recentOrders: orders.slice(0, 10),
    };
  },
  products: supabaseProducts,
  experiences: supabaseExperiences,
  async createExperience(organizationId, actorUserId, input) {
    const supabase = getSupabaseServiceClient();
    const [
      { data: context, error: contextError },
      { data: connection, error: connectionError },
      { data: contract, error: contractError },
    ] = await Promise.all([
      supabase
        .from("content_contexts")
        .select("id, channels!inner(display_name)")
        .eq("organization_id", organizationId)
        .eq("title", input.contextTitle)
        .limit(1)
        .maybeSingle(),
      supabase
        .from("connections")
        .select("id")
        .eq("content_organization_id", organizationId)
        .eq("status", "ACTIVE")
        .limit(1)
        .maybeSingle(),
      supabase
        .from("commercial_contracts")
        .select("id")
        .eq("publisher_organization_id", organizationId)
        .eq("status", "ACTIVE")
        .limit(1)
        .maybeSingle(),
    ]);
    if (contextError) throw contextError;
    if (connectionError) throw connectionError;
    if (contractError) throw contractError;
    const contextId = required(
      context?.id,
      `Unknown content context: ${input.contextTitle}`,
    );
    const connectionId = required(
      connection?.id,
      "No active commerce connection exists for this organization",
    );
    const contractId = required(
      contract?.id,
      "No active commercial contract exists for this organization",
    );

    const { data: products, error: productsError } = await supabase
      .from("catalog_products")
      .select("id")
      .eq("connection_id", connectionId)
      .eq("status", "ACTIVE")
      .limit(input.productCount);
    if (productsError) throw productsError;

    const { data: created, error: createError } = await supabase
      .from("experiences")
      .insert({
        organization_id: organizationId,
        name: input.name,
        content_context_id: contextId,
        connection_id: connectionId,
        contract_id: contractId,
        status: "DRAFT",
        current_version: 1,
      })
      .select("id")
      .single();
    if (createError) throw createError;

    const { error: versionError } = await supabase
      .from("experience_versions")
      .insert({
        experience_id: created.id,
        version: 1,
        status: "DRAFT",
        handoff_mode: input.handoffMode,
        product_ids: (products ?? []).map((product) => product.id),
        eligibility: { countries: ["AR"] },
        presentation: { locale: "es-AR" },
        effective_from: input.startsAt,
        effective_to: input.endsAt,
      });
    if (versionError) {
      await supabase.from("experiences").delete().eq("id", created.id);
      throw versionError;
    }

    await supabase.from("audit_log").insert({
      organization_id: organizationId,
      actor_user_id: actorUserId,
      action: "experience.created",
      resource_type: "experience",
      resource_id: created.id,
      request_id: crypto.randomUUID(),
      after_redacted: { name: input.name, version: 1, status: "DRAFT" },
    });

    return {
      id: String(created.id),
      ...input,
      version: 1,
      status: "DRAFT",
      productCount: products?.length ?? 0,
    };
  },
  async smartLinks(organizationId) {
    const supabase = getSupabaseServiceClient();
    const { data, error } = await supabase
      .from("smart_links")
      .select(
        "id, short_code, placement_key, status, experiences!inner(name), handoff_sessions(status)",
      )
      .eq("organization_id", organizationId)
      .order("created_at", { ascending: false });
    if (error) throw error;
    return ((data as Row[] | null) ?? []).map((row) => {
      const experience = relation(row.experiences);
      const handoffs = (row.handoff_sessions as Row[] | undefined) ?? [];
      return {
        id: String(row.id),
        shortCode: String(row.short_code),
        url: `${env.SNAPPLAY_PUBLIC_BASE_URL}/r/${String(row.short_code)}`,
        experienceName: String(experience?.name ?? "Sin experience"),
        placementKey: String(row.placement_key),
        status: row.status as SmartLink["status"],
        scans: handoffs.length,
        conversions: handoffs.filter(
          (handoff) => handoff.status === "CONVERTED",
        ).length,
      };
    });
  },
  orders: supabaseOrders,
  async resolveSmartLink(shortCode) {
    const supabase = getSupabaseServiceClient();
    const { data: link, error: linkError } = await supabase
      .from("smart_links")
      .select(
        "id, status, expires_at, experiences!inner(id, name, current_version, connection_id, contract_id)",
      )
      .eq("short_code", shortCode)
      .eq("status", "ACTIVE")
      .maybeSingle();
    if (linkError) throw linkError;
    if (!link || (link.expires_at && new Date(link.expires_at) <= new Date()))
      return null;
    const experience = relation((link as Row).experiences);
    if (!experience) return null;

    const [
      { data: version, error: versionError },
      { data: contractVersion, error: contractError },
    ] = await Promise.all([
      supabase
        .from("experience_versions")
        .select("id")
        .eq("experience_id", experience.id)
        .eq("version", experience.current_version)
        .eq("status", "PUBLISHED")
        .maybeSingle(),
      supabase
        .from("contract_versions")
        .select("id, data_sharing_policies!inner(mode)")
        .eq("contract_id", experience.contract_id)
        .eq("status", "APPROVED")
        .lte("effective_from", new Date().toISOString())
        .order("version", { ascending: false })
        .limit(1)
        .maybeSingle(),
    ]);
    if (versionError) throw versionError;
    if (contractError) throw contractError;
    if (!version || !contractVersion) return null;

    const trackingToken = crypto.randomBytes(24).toString("base64url");
    const trackingHash = crypto
      .createHash("sha256")
      .update(trackingToken)
      .digest("hex");
    const policy = relation((contractVersion as Row).data_sharing_policies);
    const { error: handoffError } = await supabase
      .from("handoff_sessions")
      .insert({
        smart_link_id: link.id,
        experience_version_id: version.id,
        connection_id: experience.connection_id,
        contract_version_id: contractVersion.id,
        tracking_token_hash: trackingHash,
        status: "REDIRECTED",
        data_sharing_mode: policy?.mode ?? "BILLING_ONLY",
        expires_at: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString(),
      });
    if (handoffError) throw handoffError;
    return { experienceName: String(experience.name), trackingToken };
  },
};

export const repository: Repository = env.SNAPPLAY_DEMO_MODE
  ? demoRepository
  : supabaseRepository;
