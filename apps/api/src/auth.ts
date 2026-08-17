import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { env } from "./config.js";
import { getSupabaseServiceClient } from "./supabase.js";

export interface RequestPrincipal {
  userId: string;
  organizationId: string;
  roles: string[];
}

const demoPrincipal: RequestPrincipal = {
  userId: "00000000-0000-0000-0000-000000000001",
  organizationId: "50d2d7eb-c8fd-42df-bbb0-0ea0e2271090",
  roles: ["ORGANIZATION_ADMIN"],
};

function unauthorized(
  reply: FastifyReply,
  request: FastifyRequest,
  detail: string,
) {
  return reply.status(401).send({
    type: "https://snapplay.io/problems/unauthorized",
    title: "Unauthorized",
    status: 401,
    detail,
    request_id: request.id,
  });
}

export async function registerAuthentication(
  app: FastifyInstance,
): Promise<void> {
  app.decorateRequest("principal", null);

  app.addHook("preHandler", async (request, reply) => {
    if (
      !request.url.startsWith("/v1/") ||
      request.url.startsWith("/v1/partner/")
    )
      return;

    if (env.SNAPPLAY_DEMO_MODE) {
      request.principal = demoPrincipal;
      return;
    }

    const authorization = request.headers.authorization;
    if (!authorization?.startsWith("Bearer ")) {
      return unauthorized(reply, request, "A valid bearer token is required.");
    }

    const token = authorization.slice("Bearer ".length);
    const supabase = getSupabaseServiceClient();
    const { data: userData, error: userError } =
      await supabase.auth.getUser(token);
    if (userError || !userData.user) {
      return unauthorized(
        reply,
        request,
        "The bearer token is invalid or expired.",
      );
    }

    const { data: memberships, error: membershipError } = await supabase
      .from("organization_members")
      .select("organization_id, role_key")
      .eq("user_id", userData.user.id)
      .eq("status", "ACTIVE")
      .order("created_at", { ascending: true })
      .limit(20);

    if (membershipError || !memberships?.length) {
      return reply.status(403).send({
        type: "https://snapplay.io/problems/forbidden",
        title: "Forbidden",
        status: 403,
        detail: "The user does not belong to an active Snap Play organization.",
        request_id: request.id,
      });
    }

    const organizationId = String(memberships[0]!.organization_id);
    request.principal = {
      userId: userData.user.id,
      organizationId,
      roles: memberships
        .filter(
          (membership) => String(membership.organization_id) === organizationId,
        )
        .map((membership) => String(membership.role_key)),
    };
  });
}

declare module "fastify" {
  interface FastifyRequest {
    principal: RequestPrincipal | null;
  }
}
