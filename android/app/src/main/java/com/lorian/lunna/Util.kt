package com.lorian.lunna

/** Las cuentas se crean como `<usuario>@lunnaa.local`; el usuario solo escribe su nombre. */
fun emailFor(user: String): String? {
    val u = user.trim().lowercase()
    return if (Regex("[a-z0-9._-]+").matches(u)) "$u@lunnaa.local" else null
}

/** Quita los caracteres que rompen el filtro `or=(...)` de PostgREST o actúan como comodines. */
fun cleanQuery(q: String) = q.replace(Regex("""[,()%_\\*]"""), " ").trim()

fun fmtTime(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)
