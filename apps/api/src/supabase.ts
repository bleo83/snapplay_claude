import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import { env } from "./config.js";

let serviceClient: SupabaseClient | undefined;

export function getSupabaseServiceClient(): SupabaseClient {
  if (env.SNAPPLAY_DEMO_MODE) {
    throw new Error("Supabase is unavailable while SNAPPLAY_DEMO_MODE=true");
  }

  serviceClient ??= createClient(
    env.SUPABASE_URL as string,
    (env.SUPABASE_SECRET_KEY ?? env.SUPABASE_SERVICE_ROLE_KEY) as string,
    {
      auth: { autoRefreshToken: false, persistSession: false },
      global: { headers: { "x-application-name": "snapplay-api" } },
    },
  );
  return serviceClient;
}
