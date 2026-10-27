"""Uso: python tools/test_upload.py"""
import tempfile
from pathlib import Path

from upload import make_key

with tempfile.TemporaryDirectory() as d:
    a, b, c = Path(d, "a.MP3"), Path(d, "b.mp3"), Path(d, "c.mp3")
    a.write_bytes(b"misma cancion")
    b.write_bytes(b"misma cancion")
    c.write_bytes(b"otra cancion")
    assert make_key(a) == make_key(b), "mismo contenido debe dar la misma clave"
    assert make_key(a) != make_key(c)
    assert make_key(a).endswith(".mp3") and len(make_key(a)) == 36
print("ok")
