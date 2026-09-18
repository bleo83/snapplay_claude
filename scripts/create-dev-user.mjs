import { createClient } from "@supabase/supabase-js";

const url = process.env.SUPABASE_URL;
const serverKey =
  process.env.SUPABASE_SECRET_KEY ?? process.env.SUPABASE_SERVICE_ROLE_KEY;
const local = Boolean(
  url && /^http:\/\/(127\.0\.0\.1|localhost)(:|\/)/.test(url),
);
const email =
  process.env.SNAPPLAY_DEMO_USER_EMAIL ??
  (local ? "admin@snapplay.local" : undefined);
const password =
  process.env.SNAPPLAY_DEMO_USER_PASSWORD ??
  (local ? "SnapPlayLocal!2026" : undefined);

if (!url || !serverKey || !email || !password) {
  throw new Error(
    "Set SUPABASE_URL, SUPABASE_SECRET_KEY (or legacy SUPABASE_SERVICE_ROLE_KEY), SNAPPLAY_DEMO_USER_EMAIL and SNAPPLAY_DEMO_USER_PASSWORD in .env.local",
  );
}
if (password.length < 12)
  throw new Error(
    "SNAPPLAY_DEMO_USER_PASSWORD must contain at least 12 characters",
  );

const supabase = createClient(url, serverKey, {
  auth: { persistSession: false, autoRefreshToken: false },
});
const { data: listed, error: listError } = await supabase.auth.admin.listUsers({
  page: 1,
  perPage: 1000,
});
if (listError) throw listError;

let user = listed.users.find(
  (candidate) => candidate.email?.toLowerCase() === email.toLowerCase(),
);
if (!user) {
  const { data, error } = await supabase.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { display_name: "Snap Play Admin" },
  });
  if (error) throw error;
  user = data.user;
}

const { error: membershipError } = await supabase
  .from("organization_members")
  .upsert(
    {
      organization_id: "50d2d7eb-c8fd-42df-bbb0-0ea0e2271090",
      user_id: user.id,
      role_key: "ORGANIZATION_ADMIN",
      status: "ACTIVE",
    },
    { onConflict: "organization_id,user_id,role_key" },
  );
if (membershipError) throw membershipError;

console.log(`Development user ready: ${email}`);
