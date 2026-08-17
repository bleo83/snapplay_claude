"use server";

import { redirect } from "next/navigation";
import { z } from "zod";
import { createClient } from "@/lib/supabase/server";

export async function login(formData: FormData) {
  const parsed = z
    .object({
      email: z.string().email(),
      password: z.string().min(8),
    })
    .safeParse({
      email: formData.get("email"),
      password: formData.get("password"),
    });

  if (!parsed.success)
    redirect("/login?error=Ingresá+un+email+y+contraseña+válidos");
  const { error } = await (
    await createClient()
  ).auth.signInWithPassword(parsed.data);
  if (error)
    redirect(
      `/login?error=${encodeURIComponent("Credenciales incorrectas o usuario inactivo")}`,
    );
  redirect("/");
}
