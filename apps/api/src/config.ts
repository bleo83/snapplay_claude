import { config as loadEnv } from "dotenv";
import { z } from "zod";

loadEnv({ path: "../../.env.local" });
loadEnv({ path: ".env.local" });
loadEnv();

const envSchema = z.object({
  NODE_ENV: z
    .enum(["development", "test", "production"])
    .default("development"),
  SNAPPLAY_API_PORT: z.coerce.number().int().positive().default(4000),
  SNAPPLAY_PUBLIC_BASE_URL: z.string().url().default("http://localhost:4000"),
  SNAPPLAY_BACKOFFICE_URL: z.string().url().default("http://localhost:3000"),
  SNAPPLAY_ALLOWED_ORIGINS: z.string().default("http://localhost:3000"),
  SNAPPLAY_DEMO_MODE: z
    .string()
    .default("true")
    .transform((value) => value === "true"),
  SUPABASE_URL: z.string().url().optional(),
  SUPABASE_PUBLISHABLE_KEY: z.string().optional(),
  SUPABASE_SECRET_KEY: z.string().optional(),
  SUPABASE_SERVICE_ROLE_KEY: z.string().optional(),
  RAPPI_WEBHOOK_SECRET: z.string().min(32).optional(),
  LOG_LEVEL: z
    .enum(["fatal", "error", "warn", "info", "debug", "trace", "silent"])
    .default("info"),
});

export const env = envSchema.parse(process.env);

if (!env.SNAPPLAY_DEMO_MODE) {
  const hasServerKey = Boolean(
    env.SUPABASE_SECRET_KEY ?? env.SUPABASE_SERVICE_ROLE_KEY,
  );
  if (!env.SUPABASE_URL || !hasServerKey || !env.RAPPI_WEBHOOK_SECRET) {
    throw new Error(
      "SUPABASE_URL, SUPABASE_SECRET_KEY (or legacy SUPABASE_SERVICE_ROLE_KEY), and RAPPI_WEBHOOK_SECRET are required outside demo mode",
    );
  }
}
