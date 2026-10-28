# Lunnaa
Es un reproductor personal para aproximadamente 5 personas donde cargas los archivos de tu musica y lo reproduce.

## Subir canciones
```powershell
$env:PYTHONIOENCODING="utf-8"; .\.venv\Scripts\python.exe tools\upload.py "C:\ruta\a\la\carpeta"
```
Sube los `.mp3` y `.m4a` a R2 sin pasar de 9 GB. Se puede correr varias veces: lo ya subido se salta.

## Publicar una actualización de la app
1. En `android/app/build.gradle.kts` sube `versionCode` en 1 (y cambia `versionName`, por ejemplo `"1.1"`).
2. Corre:
   ```powershell
   $env:PYTHONIOENCODING="utf-8"; .\.venv\Scripts\python.exe tools\release.py "Qué cambió"
   ```
   Compila el APK firmado, lo sube al bucket `apk` de Supabase y lo registra en `app_releases`.
   Las apps instaladas lo ofrecen al abrirse. También imprime un link de 7 días para instalarla por primera vez.

**La llave de firma** (`android/lunna-release.keystore` + `android/keystore.properties`) no está en git.
Si se pierde, los teléfonos no aceptan actualizaciones y hay que desinstalar y reinstalar en todos. Guarda una copia aparte.

**Avast:** intercepta HTTPS. Python usa `truststore`; Gradle usa `~/.gradle/lunna-truststore.jks`
(los certificados de Java + la raíz de Avast, creado con `keytool -importcert`).
