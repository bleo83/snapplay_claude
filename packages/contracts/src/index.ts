import { z } from "zod";

export const organizationTypeSchema = z.enum([
  "CONTENT_PROVIDER",
  "COMMERCE_PROVIDER",
  "ORCHESTRATOR",
  "BRAND",
  "MERCHANT",
]);

export const organizationProfileSchema = z.object({
  id: z.string().uuid(),
  legalName: z.string().min(2),
  displayName: z.string().min(2),
  organizationType: organizationTypeSchema,
  country: z.string().regex(/^[A-Z]{2}$/),
  defaultCurrency: z.string().regex(/^[A-Z]{3}$/),
  timezone: z.string().min(1),
  status: z.enum(["PENDING", "ACTIVE", "SUSPENDED", "CLOSED"]),
});

export const handoffModeSchema = z.enum([
  "STORE_DEEPLINK",
  "DYNAMIC_STOREFRONT",
  "CART_HANDOFF",
  "ORDER_API",
]);

export const experienceStatusSchema = z.enum([
  "DRAFT",
  "IN_REVIEW",
  "PUBLISHED",
  "PAUSED",
  "RETIRED",
]);

export const orderStatusSchema = z.enum([
  "PLACED",
  "CONFIRMED",
  "PREPARING",
  "COURIER_ASSIGNED",
  "PICKED_UP",
  "NEAR_DESTINATION",
  "ARRIVED_AT_DESTINATION",
  "DELIVERED",
  "REJECTED",
  "CANCELLED",
  "PARTIALLY_REFUNDED",
  "REFUNDED",
  "FAILED",
]);

export const moneySchema = z.object({
  amountMinor: z.number().int(),
  currency: z.string().regex(/^[A-Z]{3}$/),
});

export const catalogProductSchema = z.object({
  id: z.string().uuid(),
  providerProductId: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  imageUrl: z.string().url(),
  brand: z.string().nullable(),
  categories: z.array(z.string()),
  ageRestricted: z.boolean(),
  referencePriceMinor: z.number().int().nonnegative().nullable(),
  currency: z
    .string()
    .regex(/^[A-Z]{3}$/)
    .nullable(),
  status: z.enum(["ACTIVE", "INACTIVE"]),
});

export const commerceDestinationSchema = z.object({
  providerStoreId: z.string(),
  providerCategoryId: z.string(),
});

export const experienceSchema = z.object({
  id: z.string().uuid(),
  name: z.string(),
  contextTitle: z.string(),
  channel: z.string(),
  connectionId: z.string().uuid().optional(),
  territory: z.string().optional(),
  destination: commerceDestinationSchema.optional(),
  version: z.number().int().positive(),
  status: experienceStatusSchema,
  handoffMode: handoffModeSchema,
  productCount: z.number().int().nonnegative(),
  startsAt: z.string().datetime(),
  endsAt: z.string().datetime().nullable(),
});

export const smartLinkSchema = z.object({
  id: z.string().uuid(),
  shortCode: z.string(),
  url: z.string().url(),
  experienceName: z.string(),
  placementKey: z.string(),
  status: z.enum(["ACTIVE", "PAUSED", "EXPIRED", "RETIRED"]),
  scans: z.number().int().nonnegative(),
  conversions: z.number().int().nonnegative(),
});

export const orderSchema = z.object({
  id: z.string().uuid(),
  providerOrderRef: z.string(),
  experienceName: z.string(),
  status: orderStatusSchema,
  orderTotalMinor: z.number().int().nonnegative(),
  eligibleItemValueMinor: z.number().int().nonnegative().nullable(),
  currency: z.string().regex(/^[A-Z]{3}$/),
  itemCount: z.number().int().nonnegative(),
  placedAt: z.string().datetime(),
  publisherUserRef: z.string().nullable(),
});

export const dashboardSchema = z.object({
  rangeLabel: z.string(),
  currency: z.string(),
  metrics: z.object({
    scans: z.number().int().nonnegative(),
    handoffs: z.number().int().nonnegative(),
    deliveredOrders: z.number().int().nonnegative(),
    gmvMinor: z.number().int().nonnegative(),
    conversionRate: z.number().nonnegative(),
    revenueShareMinor: z.number().int(),
  }),
  experiences: z.array(experienceSchema),
  recentOrders: z.array(orderSchema),
});

export type CommerceDestination = z.infer<typeof commerceDestinationSchema>;
export type CatalogProduct = z.infer<typeof catalogProductSchema>;
export type OrganizationProfile = z.infer<typeof organizationProfileSchema>;
export type Experience = z.infer<typeof experienceSchema>;
export type SmartLink = z.infer<typeof smartLinkSchema>;
export type Order = z.infer<typeof orderSchema>;
export type Dashboard = z.infer<typeof dashboardSchema>;

export const demoOrganization: OrganizationProfile = {
  id: "50d2d7eb-c8fd-42df-bbb0-0ea0e2271090",
  legalName: "The Walt Disney Company Argentina S.A.",
  displayName: "Disney",
  organizationType: "CONTENT_PROVIDER",
  country: "AR",
  defaultCurrency: "ARS",
  timezone: "America/Argentina/Buenos_Aires",
  status: "ACTIVE",
};

export const demoProducts: CatalogProduct[] = [
  {
    id: "71f4fceb-a8be-45c9-bd66-61c9e60fd26f",
    providerProductId: "sku_toy_story_cup",
    name: "Vaso coleccionable Toy Story",
    description: "Vaso temático de 500 ml, edición Movie Night.",
    imageUrl: "https://placehold.co/640x480/1f6fff/ffffff?text=Toy+Story+Cup",
    brand: "Disney",
    categories: ["merchandising", "movie-night"],
    ageRestricted: false,
    referencePriceMinor: 1499000,
    currency: "ARS",
    status: "ACTIVE",
  },
  {
    id: "6d32d586-e5ce-41f4-b9c4-7c891ad26f06",
    providerProductId: "sku_popcorn_01",
    name: "Pochoclos clásicos",
    description: "Pochoclos listos para una noche de película.",
    imageUrl: "https://placehold.co/640x480/f5c451/1d2433?text=Pochoclos",
    brand: "Turbo",
    categories: ["snacks", "movie-night"],
    ageRestricted: false,
    referencePriceMinor: 659000,
    currency: "ARS",
    status: "ACTIVE",
  },
  {
    id: "275f24dd-31c9-41d5-8580-ad83bfa89d61",
    providerProductId: "sku_moana_tumbler",
    name: "Vaso Moana",
    description: "Vaso reutilizable inspirado en Moana.",
    imageUrl: "https://placehold.co/640x480/10b7b4/ffffff?text=Moana+Tumbler",
    brand: "Disney",
    categories: ["merchandising", "family"],
    ageRestricted: false,
    referencePriceMinor: 1599000,
    currency: "ARS",
    status: "ACTIVE",
  },
  {
    id: "13591f43-f74b-4bf8-9e7d-766db1bb4b0d",
    providerProductId: "sku_cola_15l",
    name: "Gaseosa cola 1,5 L",
    description: "Bebida para compartir.",
    imageUrl: "https://placehold.co/640x480/e94235/ffffff?text=Gaseosa",
    brand: "Partner brand",
    categories: ["bebidas"],
    ageRestricted: false,
    referencePriceMinor: 399000,
    currency: "ARS",
    status: "ACTIVE",
  },
  {
    id: "ba60645a-7e51-49e0-b0a7-39c956570e8b",
    providerProductId: "sku_beer_473",
    name: "Cerveza 473 ml",
    description: "Venta sujeta a edad, horario y jurisdicción.",
    imageUrl: "https://placehold.co/640x480/d88b22/ffffff?text=Cerveza",
    brand: "Partner brand",
    categories: ["bebidas", "adultos"],
    ageRestricted: true,
    referencePriceMinor: 279000,
    currency: "ARS",
    status: "ACTIVE",
  },
  {
    id: "e91c3430-720f-4310-ad83-0bda12fd741c",
    providerProductId: "sku_fries_large",
    name: "Papas grandes",
    description: "Papas crocantes para acompañar el partido.",
    imageUrl: "https://placehold.co/640x480/f4b83f/1d2433?text=Papas",
    brand: "Turbo",
    categories: ["comida", "sports-night"],
    ageRestricted: false,
    referencePriceMinor: 499000,
    currency: "ARS",
    status: "ACTIVE",
  },
];

const DEMO_CONNECTION_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
const DEMO_DESTINATION: CommerceDestination = {
  providerStoreId: "rappi-store-ar-001",
  providerCategoryId: "snacks-drinks",
};

export const demoExperiences: Experience[] = [
  {
    id: "184a63fe-4420-46de-9894-b16110364263",
    name: "Toy Story Movie Night",
    contextTitle: "Toy Story",
    channel: "Disney+",
    connectionId: DEMO_CONNECTION_ID,
    territory: "AR",
    destination: DEMO_DESTINATION,
    version: 3,
    status: "PUBLISHED",
    handoffMode: "STORE_DEEPLINK",
    productCount: 4,
    startsAt: "2026-08-01T00:00:00.000Z",
    endsAt: "2026-12-01T00:00:00.000Z",
  },
  {
    id: "7351e94e-a88b-46bd-8f3c-a6df37c13483",
    name: "Moana Family Night",
    contextTitle: "Moana",
    channel: "Disney+",
    connectionId: DEMO_CONNECTION_ID,
    territory: "AR",
    destination: DEMO_DESTINATION,
    version: 2,
    status: "PUBLISHED",
    handoffMode: "STORE_DEEPLINK",
    productCount: 3,
    startsAt: "2026-08-10T00:00:00.000Z",
    endsAt: null,
  },
  {
    id: "1d79abf3-33e1-427c-aeba-c27f607f6f71",
    name: "ESPN Match Night",
    contextTitle: "ESPN Live",
    channel: "ESPN",
    connectionId: DEMO_CONNECTION_ID,
    territory: "AR",
    destination: DEMO_DESTINATION,
    version: 1,
    status: "IN_REVIEW",
    handoffMode: "STORE_DEEPLINK",
    productCount: 5,
    startsAt: "2026-09-01T00:00:00.000Z",
    endsAt: null,
  },
  {
    id: "53d116c9-e676-4c49-9a2b-371d0b4d5c1a",
    name: "Hulu Classics",
    contextTitle: "Hulu",
    channel: "Hulu",
    connectionId: DEMO_CONNECTION_ID,
    territory: "AR",
    destination: DEMO_DESTINATION,
    version: 1,
    status: "DRAFT",
    handoffMode: "STORE_DEEPLINK",
    productCount: 2,
    startsAt: "2026-10-01T00:00:00.000Z",
    endsAt: null,
  },
];

export const demoOrders: Order[] = [
  {
    id: "492f7e7f-13c0-413a-88aa-bc974605ace9",
    providerOrderRef: "RAPPI-884031",
    experienceName: "Toy Story Movie Night",
    status: "DELIVERED",
    orderTotalMinor: 2899000,
    eligibleItemValueMinor: 1499000,
    currency: "ARS",
    itemCount: 4,
    placedAt: "2026-08-16T18:42:12.000Z",
    publisherUserRef: "dsy_usr_D8K2M1",
  },
  {
    id: "9706d58d-b550-4cb5-8aa0-d0be94cab621",
    providerOrderRef: "RAPPI-884029",
    experienceName: "Moana Family Night",
    status: "NEAR_DESTINATION",
    orderTotalMinor: 2458000,
    eligibleItemValueMinor: 1599000,
    currency: "ARS",
    itemCount: 3,
    placedAt: "2026-08-16T18:31:03.000Z",
    publisherUserRef: "dsy_usr_Q4L9T7",
  },
  {
    id: "46b54e09-f690-4cf0-b8cc-af69bd841bd7",
    providerOrderRef: "RAPPI-884012",
    experienceName: "Toy Story Movie Night",
    status: "PICKED_UP",
    orderTotalMinor: 1898000,
    eligibleItemValueMinor: 1499000,
    currency: "ARS",
    itemCount: 2,
    placedAt: "2026-08-16T18:05:44.000Z",
    publisherUserRef: "dsy_usr_B2F8H3",
  },
  {
    id: "588691c1-d37f-477e-bcad-5f5ac5bc7c91",
    providerOrderRef: "RAPPI-883998",
    experienceName: "Toy Story Movie Night",
    status: "CANCELLED",
    orderTotalMinor: 1058000,
    eligibleItemValueMinor: 0,
    currency: "ARS",
    itemCount: 2,
    placedAt: "2026-08-16T17:44:09.000Z",
    publisherUserRef: "dsy_usr_R1E5C8",
  },
];

export const demoSmartLinks: SmartLink[] = [
  {
    id: "9ab6732c-093d-4fee-8efa-b5d2b6ec4d1f",
    shortCode: "7E1vM2kP9xQ4",
    url: "http://localhost:4000/r/7E1vM2kP9xQ4",
    experienceName: "Toy Story Movie Night",
    placementKey: "disney-plus.toy-story.endcard",
    status: "ACTIVE",
    scans: 18742,
    conversions: 1248,
  },
  {
    id: "f9aeff9c-1016-4d60-8dbe-6464cc1266cf",
    shortCode: "4G8zN7bK2mL6",
    url: "http://localhost:4000/r/4G8zN7bK2mL6",
    experienceName: "Moana Family Night",
    placementKey: "disney-plus.moana.pause",
    status: "ACTIVE",
    scans: 9836,
    conversions: 771,
  },
];

export const demoDashboard: Dashboard = {
  rangeLabel: "Últimos 30 días",
  currency: "ARS",
  metrics: {
    scans: 42581,
    handoffs: 28416,
    deliveredOrders: 2314,
    gmvMinor: 6849500000,
    conversionRate: 5.43,
    revenueShareMinor: 217840000,
  },
  experiences: demoExperiences,
  recentOrders: demoOrders,
};
