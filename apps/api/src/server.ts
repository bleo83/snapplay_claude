import crypto from "node:crypto";
import cors from "@fastify/cors";
import helmet from "@fastify/helmet";
import rateLimit from "@fastify/rate-limit";
import {
  experienceSchema,
  organizationProfileSchema,
  orderStatusSchema,
  type Experience,
} from "@snapplay/contracts";
import Fastify, { type FastifyError } from "fastify";
import { z } from "zod";
import { env } from "./config.js";
import { registerAuthentication } from "./auth.js";
import {
  ingestPartnerOrderEvent,
  partnerOrderEventSchema,
} from "./partner-events.js";
import { repository } from "./repository.js";
import { verifyWebhookSignature } from "./webhook-security.js";

const app = Fastify({
  logger: {
    level: env.LOG_LEVEL,
    redact: [
      "req.headers.authorization",
      "req.headers.x-snapplay-signature",
      "body.tracking_token",
      "body.publisher_user_ref",
    ],
  },
  requestIdHeader: "x-request-id",
  genReqId: () => crypto.randomUUID(),
});

app.addContentTypeParser(
  "application/cloudevents+json",
  { parseAs: "buffer" },
  (_request, body, done) => done(null, body),
);

await app.register(helmet, { contentSecurityPolicy: false });
await app.register(cors, {
  origin: env.SNAPPLAY_ALLOWED_ORIGINS.split(",").map((origin) =>
    origin.trim(),
  ),
  credentials: true,
});
await app.register(rateLimit, { max: 200, timeWindow: "1 minute" });
await registerAuthentication(app);

app.setErrorHandler((error: FastifyError, request, reply) => {
  if (error instanceof z.ZodError) {
    return reply.status(422).send({
      type: "https://snapplay.io/problems/validation-error",
      title: "Validation error",
      status: 422,
      detail: "The request did not match the expected schema.",
      request_id: request.id,
      errors: error.issues,
    });
  }

  request.log.error(error);
  const statusCode =
    error.statusCode && error.statusCode >= 400 ? error.statusCode : 500;
  return reply.status(statusCode).send({
    type: "https://snapplay.io/problems/internal-error",
    title: statusCode === 500 ? "Internal error" : "Request error",
    status: statusCode,
    detail:
      statusCode === 500 ? "An unexpected error occurred." : error.message,
    request_id: request.id,
  });
});

app.get("/health", async () => ({
  status: "ok",
  mode: env.SNAPPLAY_DEMO_MODE ? "demo" : "supabase",
  timestamp: new Date().toISOString(),
}));

app.get("/v1/dashboard", async (request) =>
  repository.dashboard(request.principal!.organizationId),
);

app.get("/v1/organization", async (request) =>
  repository.organization(request.principal!.organizationId),
);

app.patch("/v1/organization", async (request, reply) => {
  if (!request.principal!.roles.includes("ORGANIZATION_ADMIN")) {
    return reply.status(403).send({
      type: "https://snapplay.io/problems/forbidden",
      title: "Forbidden",
      status: 403,
      detail: "Only an organization admin can update company details.",
      request_id: request.id,
    });
  }
  const body = organizationProfileSchema
    .pick({
      legalName: true,
      displayName: true,
      country: true,
      defaultCurrency: true,
      timezone: true,
    })
    .parse(request.body);
  return repository.updateOrganization(request.principal!.organizationId, body);
});

app.get("/v1/catalog/products", async (request) => {
  const query = z
    .object({
      q: z.string().optional(),
      category: z.string().optional(),
      status: z.enum(["ACTIVE", "INACTIVE"]).optional(),
    })
    .parse(request.query);

  const items = await repository.products(
    request.principal!.organizationId,
    query,
  );

  return {
    items,
    total: items.length,
    source: env.SNAPPLAY_DEMO_MODE ? "demo" : "supabase",
  };
});

app.get("/v1/experiences", async (request) => ({
  items: await repository.experiences(request.principal!.organizationId),
}));

app.post("/v1/experiences", async (request, reply) => {
  const canCreate = request.principal!.roles.some((role) =>
    ["ORGANIZATION_ADMIN", "CONTENT_MANAGER", "PUBLISHER"].includes(role),
  );
  if (!canCreate) {
    return reply.status(403).send({
      type: "https://snapplay.io/problems/forbidden",
      title: "Forbidden",
      status: 403,
      detail: "The active role cannot create experiences.",
      request_id: request.id,
    });
  }
  const createSchema = z.object({
    name: z.string().min(3).max(160),
    contextTitle: z.string().min(2).max(160),
    channel: z.string().min(2).max(80),
    handoffMode: z.enum([
      "STORE_DEEPLINK",
      "DYNAMIC_STOREFRONT",
      "CART_HANDOFF",
    ]),
    productCount: z.number().int().min(0).max(100),
    startsAt: z.string().datetime(),
    endsAt: z.string().datetime().nullable().default(null),
  });
  const body = createSchema.parse(request.body);
  const experience: Experience = experienceSchema.parse({
    id: crypto.randomUUID(),
    ...body,
    version: 1,
    status: "DRAFT",
  });

  const created = await repository.createExperience(
    request.principal!.organizationId,
    request.principal!.userId,
    experience,
  );
  return reply.status(201).send(created);
});

app.get("/v1/smart-links", async (request) => ({
  items: await repository.smartLinks(request.principal!.organizationId),
}));

app.get("/v1/orders", async (request) => {
  const query = z
    .object({ status: orderStatusSchema.optional() })
    .parse(request.query);
  const items = await repository.orders(
    request.principal!.organizationId,
    query.status,
  );
  return { items, total: items.length };
});

app.post("/v1/partner/events", async (request, reply) => {
  const rawBody = request.body as Buffer;
  const timestamp = request.headers["x-snapplay-timestamp"];
  const signature = request.headers["x-snapplay-signature"];
  const eventId = request.headers["x-snapplay-event-id"];
  if (
    typeof timestamp !== "string" ||
    typeof signature !== "string" ||
    typeof eventId !== "string" ||
    !env.RAPPI_WEBHOOK_SECRET ||
    !verifyWebhookSignature({
      rawBody,
      timestamp,
      signature,
      secret: env.RAPPI_WEBHOOK_SECRET,
    })
  ) {
    return reply.status(401).send({
      type: "https://snapplay.io/problems/invalid-webhook-signature",
      title: "Invalid webhook signature",
      status: 401,
      detail:
        "The signature is invalid or the timestamp is outside the five-minute window.",
      request_id: request.id,
    });
  }

  let parsedBody: unknown;
  try {
    parsedBody = JSON.parse(rawBody.toString("utf8"));
  } catch {
    return reply.status(422).send({
      type: "https://snapplay.io/problems/validation-error",
      title: "Validation error",
      status: 422,
      detail: "The CloudEvent body is not valid JSON.",
      request_id: request.id,
    });
  }
  const event = partnerOrderEventSchema.parse(parsedBody);
  if (event.id !== eventId) {
    return reply.status(422).send({
      type: "https://snapplay.io/problems/event-id-mismatch",
      title: "Event ID mismatch",
      status: 422,
      detail: "The event ID header and CloudEvent body do not match.",
      request_id: request.id,
    });
  }
  const result = await ingestPartnerOrderEvent(event);
  return reply
    .status(202)
    .send({ event_id: event.id, accepted: true, duplicate: result.duplicate });
});

app.get<{ Params: { shortCode: string } }>(
  "/r/:shortCode",
  async (request, reply) => {
    const resolution = await repository.resolveSmartLink(
      request.params.shortCode,
    );
    if (!resolution) {
      return reply.status(404).send({
        type: "https://snapplay.io/problems/link-not-found",
        title: "Smart link not found",
        status: 404,
        detail: "The link is missing, inactive or expired.",
        request_id: request.id,
      });
    }

    const demoDestination = new URL(
      "/handoff-preview",
      env.SNAPPLAY_BACKOFFICE_URL,
    );
    demoDestination.searchParams.set("experience", resolution.experienceName);
    demoDestination.searchParams.set("tracking", resolution.trackingToken);
    return reply
      .header("cache-control", "no-store")
      .redirect(demoDestination.toString(), 302);
  },
);

try {
  await app.listen({ port: env.SNAPPLAY_API_PORT, host: "127.0.0.1" });
} catch (error) {
  app.log.error(error);
  process.exit(1);
}
