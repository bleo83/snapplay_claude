import crypto from "node:crypto";
import { orderStatusSchema, type Order } from "@snapplay/contracts";
import { z } from "zod";
import { env } from "./config.js";
import { demoStore } from "./demo-store.js";
import { getSupabaseServiceClient } from "./supabase.js";

export const partnerOrderEventSchema = z.object({
  specversion: z.literal("1.0"),
  id: z.string().min(8).max(128),
  source: z.string().min(1),
  type: z.enum([
    "com.rappi.order.placed.v1",
    "com.rappi.order.status-changed.v1",
  ]),
  subject: z.string().min(1),
  time: z.string().datetime(),
  datacontenttype: z.literal("application/json"),
  data: z.object({
    connection_id: z.string().uuid(),
    provider_order_ref: z.string().min(1).max(160),
    tracking_token: z.string().min(16).max(512),
    status: orderStatusSchema.exclude(["PARTIALLY_REFUNDED", "REFUNDED"]),
    currency: z
      .string()
      .regex(/^[A-Z]{3}$/)
      .nullable()
      .optional(),
    order_total_minor: z.number().int().nonnegative().nullable().optional(),
    eligible_item_value_minor: z
      .number()
      .int()
      .nonnegative()
      .nullable()
      .optional(),
    occurred_at: z.string().datetime(),
  }),
});

export type PartnerOrderEvent = z.infer<typeof partnerOrderEventSchema>;
const demoEventIds = new Set<string>();

const allowedTransitions: Partial<Record<Order["status"], Order["status"][]>> =
  {
    PLACED: ["CONFIRMED", "PREPARING", "REJECTED", "CANCELLED", "FAILED"],
    CONFIRMED: ["PREPARING", "COURIER_ASSIGNED", "CANCELLED", "FAILED"],
    PREPARING: ["COURIER_ASSIGNED", "PICKED_UP", "CANCELLED", "FAILED"],
    COURIER_ASSIGNED: ["PICKED_UP", "CANCELLED", "FAILED"],
    PICKED_UP: [
      "NEAR_DESTINATION",
      "ARRIVED_AT_DESTINATION",
      "DELIVERED",
      "FAILED",
    ],
    NEAR_DESTINATION: ["ARRIVED_AT_DESTINATION", "DELIVERED", "FAILED"],
    ARRIVED_AT_DESTINATION: ["DELIVERED", "FAILED"],
  };

function unprocessable(message: string): Error & { statusCode: number } {
  return Object.assign(new Error(message), { statusCode: 422 });
}

function validateTransition(from: Order["status"], to: Order["status"]): void {
  if (from === to) return;
  if (!(allowedTransitions[from] ?? []).includes(to)) {
    throw unprocessable(`Invalid order transition: ${from} -> ${to}`);
  }
}

export async function ingestPartnerOrderEvent(
  event: PartnerOrderEvent,
): Promise<{ duplicate: boolean }> {
  if (env.SNAPPLAY_DEMO_MODE) {
    if (demoEventIds.has(event.id)) return { duplicate: true };
    demoEventIds.add(event.id);
    const existing = demoStore.orders.find(
      (order) => order.providerOrderRef === event.data.provider_order_ref,
    );
    if (existing) {
      validateTransition(existing.status, event.data.status);
      existing.status = event.data.status;
    } else {
      if (!event.data.currency || event.data.order_total_minor == null) {
        throw unprocessable(
          "The first event for an order must include currency and order_total_minor",
        );
      }
      demoStore.orders.unshift({
        id: crypto.randomUUID(),
        providerOrderRef: event.data.provider_order_ref,
        experienceName: "Webhook Rappi (demo)",
        status: event.data.status,
        orderTotalMinor: event.data.order_total_minor,
        eligibleItemValueMinor: event.data.eligible_item_value_minor ?? null,
        currency: event.data.currency,
        itemCount: 0,
        placedAt: event.data.occurred_at,
        publisherUserRef: null,
      });
    }
    return { duplicate: false };
  }

  const supabase = getSupabaseServiceClient();
  const { data: storedEvent, error: eventError } = await supabase
    .from("partner_events")
    .insert({
      event_id: event.id,
      event_type: event.type,
      connection_id: event.data.connection_id,
      provider_order_ref: event.data.provider_order_ref,
      payload: event,
      signature_timestamp: event.time,
    })
    .select("id")
    .single();
  if (eventError?.code === "23505") return { duplicate: true };
  if (eventError) throw eventError;

  try {
    const trackingHash = crypto
      .createHash("sha256")
      .update(event.data.tracking_token)
      .digest("hex");
    const { data: handoff, error: handoffError } = await supabase
      .from("handoff_sessions")
      .select("id, publisher_user_ref")
      .eq("connection_id", event.data.connection_id)
      .eq("tracking_token_hash", trackingHash)
      .maybeSingle();
    if (handoffError) throw handoffError;
    if (!handoff)
      throw unprocessable(
        "The tracking token does not match an active handoff",
      );

    const { data: current, error: currentError } = await supabase
      .from("provider_orders")
      .select("id, status")
      .eq("connection_id", event.data.connection_id)
      .eq("provider_order_ref", event.data.provider_order_ref)
      .maybeSingle();
    if (currentError) throw currentError;

    let orderId: string;
    const fromStatus = current?.status as Order["status"] | undefined;
    if (current) {
      validateTransition(fromStatus as Order["status"], event.data.status);
      const updates: Record<string, unknown> = {
        status: event.data.status,
        updated_at: new Date().toISOString(),
      };
      if (event.data.order_total_minor != null)
        updates.order_total_minor = event.data.order_total_minor;
      if (event.data.eligible_item_value_minor != null)
        updates.eligible_item_value_minor =
          event.data.eligible_item_value_minor;
      if (event.data.status === "DELIVERED")
        updates.delivered_at = event.data.occurred_at;
      const { error } = await supabase
        .from("provider_orders")
        .update(updates)
        .eq("id", current.id);
      if (error) throw error;
      orderId = String(current.id);
    } else {
      if (!event.data.currency || event.data.order_total_minor == null) {
        throw unprocessable(
          "The first event for an order must include currency and order_total_minor",
        );
      }
      const { data: order, error } = await supabase
        .from("provider_orders")
        .insert({
          connection_id: event.data.connection_id,
          handoff_id: handoff.id,
          provider_order_ref: event.data.provider_order_ref,
          status: event.data.status,
          currency: event.data.currency,
          order_total_minor: event.data.order_total_minor,
          eligible_item_value_minor:
            event.data.eligible_item_value_minor ?? null,
          publisher_user_ref: handoff.publisher_user_ref,
          placed_at: event.data.occurred_at,
          delivered_at:
            event.data.status === "DELIVERED" ? event.data.occurred_at : null,
        })
        .select("id")
        .single();
      if (error) throw error;
      orderId = String(order.id);
    }

    const { error: historyError } = await supabase
      .from("order_status_history")
      .insert({
        provider_order_id: orderId,
        partner_event_id: storedEvent.id,
        from_status: fromStatus ?? null,
        to_status: event.data.status,
        occurred_at: event.data.occurred_at,
      });
    if (historyError?.code !== "23505" && historyError) throw historyError;

    await Promise.all([
      supabase
        .from("handoff_sessions")
        .update({ status: "CONVERTED", updated_at: new Date().toISOString() })
        .eq("id", handoff.id),
      supabase
        .from("partner_events")
        .update({ status: "PROCESSED", processed_at: new Date().toISOString() })
        .eq("id", storedEvent.id),
      supabase.from("outbox_events").insert({
        aggregate_type: "provider_order",
        aggregate_id: orderId,
        event_type:
          event.data.status === "NEAR_DESTINATION"
            ? "io.snapplay.order.milestone.v1"
            : "io.snapplay.order.updated.v1",
        schema_version: "1.0",
        payload: {
          provider_order_id: orderId,
          handoff_id: handoff.id,
          status: event.data.status,
          occurred_at: event.data.occurred_at,
        },
        occurred_at: event.data.occurred_at,
      }),
    ]);
    return { duplicate: false };
  } catch (cause) {
    await supabase
      .from("partner_events")
      .update({
        status: "REJECTED",
        error_detail:
          cause instanceof Error
            ? cause.message.slice(0, 500)
            : "Unknown processing error",
        processed_at: new Date().toISOString(),
      })
      .eq("id", storedEvent.id);
    throw cause;
  }
}
