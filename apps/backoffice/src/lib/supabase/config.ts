const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;

export const hasSupabaseConfig = Boolean(
  url &&
  key &&
  !url.includes("your-project") &&
  !key.includes("your-publishable-key"),
);

export const isAuthEnabled =
  hasSupabaseConfig && process.env.NEXT_PUBLIC_SNAPPLAY_DEMO_MODE !== "true";

export function publicSupabaseConfig(): { url: string; key: string } {
  if (!hasSupabaseConfig)
    throw new Error("Supabase public configuration is missing");
  return { url: url as string, key: key as string };
}
