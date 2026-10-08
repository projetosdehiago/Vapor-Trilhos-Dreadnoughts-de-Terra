#!/usr/bin/env python3
"""Monta uma página HTML autocontida para visualizar o landship (three.js).

Uso: python3 build_preview.py <saida.html>
Lê landship.geo.json, landship.animation.json, landship.png e landship_glowmask.png deste diretório.
"""
import base64
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent


def data_uri(name):
    return "data:image/png;base64," + base64.b64encode((HERE / name).read_bytes()).decode()


def compact(name):
    return json.dumps(json.loads((HERE / name).read_text()), separators=(",", ":"))


html = (HERE / "preview_template.html").read_text()
html = (html.replace("__GEO__", compact("landship.geo.json"))
            .replace("__ANIM__", compact("landship.animation.json"))
            .replace("__TEX__", data_uri("landship.png"))
            .replace("__GLOW__", data_uri("landship_glowmask.png")))
out = Path(sys.argv[1])
out.write_text(html)
print(f"{out} ({out.stat().st_size // 1024} KB)")
