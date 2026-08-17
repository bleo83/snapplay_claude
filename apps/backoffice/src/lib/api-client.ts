import { createClient } from "./supabase/client";
import { isAuthEnabled } from "./supabase/config";

export async function apiFetch(
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  const headers = new Headers(init.headers);
  if (isAuthEnabled) {
    const { data } = await createClient().auth.getSession();
    if (data.session?.access_token)
      headers.set("authorization", `Bearer ${data.session.access_token}`);
  }
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:4000";
  return fetch(`${apiUrl}${path}`, { ...init, headers });
}
