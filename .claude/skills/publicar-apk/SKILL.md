---
name: publicar-apk
description: Regla de Lunna para publicar actualizaciones del APK. Úsala SIEMPRE antes de correr tools/release.py, subir un APK al bucket "apk" de Supabase, insertar o borrar filas en app_releases, o cambiar versionCode/versionName en android/app/build.gradle.kts. Publicar solo cuando la persona lo pida de forma explícita.
---

# Publicar una actualización del APK

Publicar llega a los 5 dispositivos: la app muestra el aviso "Nueva versión" a todos y `release.py` borra el APK anterior. Por eso **no se publica nada hasta que la persona diga explícitamente que lo haga** (por ejemplo "publica la actualización" o "sube la versión"). Terminar una funcionalidad, que compile o que funcione en la tablet no cuenta como permiso.

## Sin permiso explícito, NO hacer
- Correr `tools/release.py`.
- Subir, reemplazar o borrar archivos del bucket `apk`.
- Insertar, cambiar o borrar filas de `app_releases` (ni por SQL ni por MCP).
- Subir `versionCode` / `versionName` en `android/app/build.gradle.kts`.

## Sí se puede, para probar
- Compilar (`assembleDebug` o `assembleRelease`) e instalar en la tablet por adb con `adb install -r`. Eso no avisa a nadie más.
- Hacer commits y push del código.

## Cuando la persona lo pida
1. Subir `versionCode` en 1 y poner un `versionName` nuevo.
2. `$env:PYTHONIOENCODING="utf-8"; .\.venv\Scripts\python.exe tools\release.py "notas de la versión"`
3. Confirmar que se lee la fila nueva en `app_releases` y entregar el link que imprime el script.
