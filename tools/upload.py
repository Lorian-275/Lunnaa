"""Sube canciones a R2 y las registra en Supabase.

Uso: python tools/upload.py <carpeta>
Lee las claves de ../.env. Se puede correr varias veces: las canciones ya subidas se saltan.
"""
import hashlib
import os
import sys
from pathlib import Path

import boto3
import mutagen
from dotenv import load_dotenv
from supabase import create_client

CONTENT_TYPES = {".mp3": "audio/mpeg", ".m4a": "audio/mp4"}


def make_key(path: Path) -> str:
    # El hash del contenido hace que la misma canción siempre tenga la misma clave (sin duplicados).
    h = hashlib.sha256(path.read_bytes()).hexdigest()[:32]
    return h + path.suffix.lower()


def read_tags(path: Path) -> dict:
    f = mutagen.File(path, easy=True)
    if f is None:
        raise ValueError("formato no reconocido")
    tag = lambda k: (f.get(k) or [None])[0]
    return {
        "title": tag("title") or path.stem,
        "artist": tag("artist"),
        "album": tag("album"),
        "duration_ms": int(f.info.length * 1000),
    }


def main(folder: Path) -> None:
    load_dotenv(Path(__file__).resolve().parent.parent / ".env")
    env = os.environ
    s3 = boto3.client(
        "s3",
        endpoint_url=f"https://{env['R2_ACCOUNT_ID']}.r2.cloudflarestorage.com",
        aws_access_key_id=env["R2_ACCESS_KEY_ID"],
        aws_secret_access_key=env["R2_SECRET_ACCESS_KEY"],
        region_name="auto",
    )
    sb = create_client(env["SUPABASE_URL"], env["SUPABASE_SERVICE_ROLE_KEY"])

    files = sorted(p for p in folder.rglob("*") if p.suffix.lower() in CONTENT_TYPES)
    added = skipped = failed = 0
    for path in files:
        try:
            key = make_key(path)
            if sb.table("songs").select("id").eq("r2_key", key).execute().data:
                skipped += 1
                continue
            row = read_tags(path)
            s3.upload_file(str(path), env["R2_BUCKET"], key,
                           ExtraArgs={"ContentType": CONTENT_TYPES[path.suffix.lower()]})
            # Si esto falla, el archivo queda en R2 sin fila; al volver a correr se completa.
            sb.table("songs").insert({**row, "r2_key": key}).execute()
            added += 1
            print(f"+ {row['title']} - {row['artist'] or '?'}")
        except Exception as e:
            failed += 1
            print(f"! {path.name}: {e}", file=sys.stderr)
    print(f"\n{added} subidas, {skipped} ya existían, {failed} con error")


if __name__ == "__main__":
    if len(sys.argv) != 2 or not Path(sys.argv[1]).is_dir():
        sys.exit("Uso: python tools/upload.py <carpeta>")
    main(Path(sys.argv[1]))
