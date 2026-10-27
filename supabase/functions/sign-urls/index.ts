// POST { ids: string[] } -> { [songId]: urlFirmada }. Solo para usuarios con sesión.
import { createClient } from "npm:@supabase/supabase-js@2.45.4";
import { AwsClient } from "npm:aws4fetch@1.0.20";

const EXPIRES_SECONDS = 6 * 3600;
// Topes mensuales por debajo del plan gratis (Supabase: 500k invocaciones; R2: 10M lecturas Class B).
// Cada URL firmada se estima en <= 10 lecturas de R2 (ExoPlayer pide el audio por partes al adelantar).
const MAX_INVOCATIONS = 400_000;
const MAX_SIGNED_URLS = 500_000;
const env = (k: string) => Deno.env.get(k)!;
const admin = createClient(env("SUPABASE_URL"), env("SUPABASE_SERVICE_ROLE_KEY"));

const r2 = new AwsClient({
  accessKeyId: env("R2_ACCESS_KEY_ID"),
  secretAccessKey: env("R2_SECRET_ACCESS_KEY"),
  service: "s3",
  region: "auto",
});
const bucketUrl = `https://${env("R2_ACCOUNT_ID")}.r2.cloudflarestorage.com/${env("R2_BUCKET")}`;

Deno.serve(async (req) => {
  const auth = req.headers.get("Authorization");
  if (!auth) return new Response("unauthorized", { status: 401 });

  // Cliente con el token del usuario: RLS decide qué canciones puede ver.
  const sb = createClient(env("SUPABASE_URL"), env("SUPABASE_ANON_KEY"), {
    global: { headers: { Authorization: auth } },
  });
  const { data: { user } } = await sb.auth.getUser();
  if (!user) return new Response("unauthorized", { status: 401 });

  const body = await req.json().catch(() => null);
  const ids = body?.ids;
  if (!Array.isArray(ids) || ids.length === 0 || ids.length > 500) {
    return new Response("ids debe ser una lista de 1 a 500 elementos", { status: 400 });
  }

  const { data, error } = await sb.from("songs").select("id, r2_key").in("id", ids);
  if (error) return new Response(error.message, { status: 500 });

  const { data: usage, error: usageError } = await admin.rpc("bump_usage", { n: data.length });
  // Si no se puede contar, no se firma: es preferible cortar la música que pasarse del plan gratis.
  if (usageError) return new Response(usageError.message, { status: 500 });
  if (usage.invocations > MAX_INVOCATIONS || usage.signed_urls > MAX_SIGNED_URLS) {
    return new Response("Se alcanzó el límite mensual gratis. Vuelve el próximo mes.", { status: 429 });
  }

  const urls: Record<string, string> = {};
  for (const s of data) {
    // r2_key es hash + extensión (ver tools/upload.py), no necesita escaparse.
    const url = new URL(`${bucketUrl}/${s.r2_key}`);
    url.searchParams.set("X-Amz-Expires", String(EXPIRES_SECONDS));
    const signed = await r2.sign(new Request(url), { aws: { signQuery: true } });
    urls[s.id] = signed.url;
  }
  return Response.json(urls);
});
