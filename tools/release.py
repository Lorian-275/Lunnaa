"""Compila la app, sube el APK a Supabase Storage y lo publica como actualización.

Uso: python tools/release.py "Qué cambió en esta versión"
Antes, sube versionCode (y versionName) en android/app/build.gradle.kts.
Las apps instaladas ven la versión nueva la próxima vez que se abren.
"""
import os
import re
import subprocess
import sys
from pathlib import Path

import truststore

# Usa los certificados de Windows: antivirus como Avast reemplazan el certificado HTTPS con uno propio.
truststore.inject_into_ssl()

from dotenv import load_dotenv
from supabase import create_client

ROOT = Path(__file__).resolve().parent.parent
ANDROID = ROOT / "android"
GRADLE_FILE = ANDROID / "app" / "build.gradle.kts"
APK = ANDROID / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
JBR = Path(r"C:\Program Files\Android\Android Studio\jbr")
# Creado para Gradle con los certificados de Java + el de Avast (ver README).
TRUSTSTORE = Path.home() / ".gradle" / "lunna-truststore.jks"
BUCKET = "apk"


def read_version(gradle: str) -> tuple[int, str]:
    code = re.search(r"versionCode\s*=\s*(\d+)", gradle)
    name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    if not code or not name:
        raise ValueError("No encontré versionCode/versionName en build.gradle.kts")
    return int(code.group(1)), name.group(1)


def build() -> None:
    env = {**os.environ}
    env.setdefault("JAVA_HOME", str(JBR))
    args = [str(ANDROID / "gradlew.bat"), "assembleRelease", "--console=plain"]
    if TRUSTSTORE.exists():
        args.append(f"-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 "
                    f"-Djavax.net.ssl.trustStore={TRUSTSTORE} -Djavax.net.ssl.trustStorePassword=changeit")
    subprocess.run(args, cwd=ANDROID, env=env, check=True)


def main(notes: str) -> None:
    load_dotenv(ROOT / ".env")
    if not (ANDROID / "keystore.properties").exists():
        sys.exit("Falta android/keystore.properties: sin la llave de firma, los teléfonos rechazan la actualización.")
    code, name = read_version(GRADLE_FILE.read_text(encoding="utf-8"))
    sb = create_client(os.environ["SUPABASE_URL"], os.environ["SUPABASE_SERVICE_ROLE_KEY"])

    latest = sb.table("app_releases").select("version_code").order("version_code", desc=True).limit(1).execute().data
    if latest and latest[0]["version_code"] >= code:
        sys.exit(f"versionCode {code} no es mayor que el publicado ({latest[0]['version_code']}). "
                 "Súbelo en android/app/build.gradle.kts.")

    print(f"Compilando Lunna {name} (versionCode {code})...")
    build()

    path = f"lunna-{code}.apk"
    storage = sb.storage.from_(BUCKET)
    storage.upload(path, APK.read_bytes(),
                   {"content-type": "application/vnd.android.package-archive", "upsert": "true"})
    sb.table("app_releases").insert({"version_code": code, "version_name": name, "apk_path": path,
                                     "notes": notes or None}).execute()

    # Solo se guarda el APK más reciente, para no gastar el 1 GB gratis de Storage.
    old = [f["name"] for f in storage.list() if f["name"] != path]
    if old:
        storage.remove(old)
    sb.table("app_releases").delete().lt("version_code", code).execute()

    link = storage.create_signed_url(path, 7 * 24 * 3600)
    print(f"\nPublicada Lunna {name} ({APK.stat().st_size / 1024**2:.1f} MB).")
    print("Las apps instaladas la ofrecerán al abrirse.")
    print(f"Link para instalarla por primera vez (vale 7 días):\n{link.get('signedURL') or link.get('signedUrl')}")


if __name__ == "__main__":
    if len(sys.argv) > 2:
        sys.exit('Uso: python tools/release.py "Qué cambió"')
    main(sys.argv[1] if len(sys.argv) == 2 else "")
