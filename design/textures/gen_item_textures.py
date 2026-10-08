#!/usr/bin/env python3
"""Gera as texturas 16x16 dos itens (placeholders em pixel art) direto nos assets do mod.

Uso: python3 design/textures/gen_item_textures.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "vapor_trilhos" / "textures" / "item"

OUTLINE = (43, 38, 32, 255)
IRON = (184, 188, 194, 255)
IRON_MID = (138, 143, 150, 255)
IRON_DARK = (74, 76, 82, 255)
WOOD = (160, 122, 74, 255)
WOOD_DARK = (110, 80, 48, 255)
COPPER = (200, 122, 69, 255)
COPPER_DARK = (142, 79, 42, 255)
GREEN = (63, 122, 82, 255)
GREEN_DARK = (42, 82, 54, 255)
RED = (168, 50, 50, 255)
RED_DARK = (110, 32, 32, 255)
BRASS = (216, 176, 74, 255)
SOOT = (58, 54, 52, 255)
FIRE = (255, 160, 48, 255)


def new():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def outline(img):
    """Contorno escuro de 1 px em volta de tudo que é opaco (estilo dos itens vanilla)."""
    px = img.load()
    edge = []
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and px[nx, ny][3] > 0 and px[nx, ny] != OUTLINE:
                        edge.append((x, y))
                        break
    for x, y in edge:
        px[x, y] = OUTLINE
    return img


def reinforced_track():
    img, d = new()
    d.rectangle((1, 5, 14, 10), fill=IRON_DARK)
    for x in (2, 6, 10):
        d.rectangle((x, 6, x + 2, 9), fill=WOOD)
        d.line((x, 9, x + 2, 9), fill=WOOD_DARK)
    for x in (1, 5, 9, 13):
        d.point((x, 7), fill=IRON)
        d.point((x, 8), fill=IRON_MID)
    d.line((1, 5, 14, 5), fill=IRON_MID)
    return outline(img)


def steam_boiler():
    img, d = new()
    d.rectangle((2, 6, 13, 13), fill=GREEN)
    d.line((2, 13, 13, 13), fill=GREEN_DARK)
    d.line((2, 6, 13, 6), fill=(90, 150, 106, 255))
    for x in (5, 10):
        d.line((x, 6, x, 13), fill=COPPER)
    d.rectangle((10, 2, 11, 5), fill=SOOT)
    d.rectangle((3, 9, 4, 11), fill=FIRE)
    d.point((7, 5), fill=BRASS)
    d.point((7, 4), fill=BRASS)
    return outline(img)


def landship():
    img, d = new()
    # esteira
    d.rectangle((1, 11, 14, 14), fill=IRON_DARK)
    for x in range(2, 14, 2):
        d.point((x, 14), fill=IRON_MID)
        d.point((x, 11), fill=IRON_MID)
    for x in (3, 7, 11):
        d.point((x, 12), fill=BRASS)
    # casco e deque
    d.rectangle((2, 9, 13, 10), fill=IRON_MID)
    d.line((1, 8, 14, 8), fill=WOOD)
    # cabine
    d.line((3, 3, 3, 7), fill=IRON_DARK)
    d.line((7, 3, 7, 7), fill=IRON_DARK)
    d.rectangle((2, 2, 8, 2), fill=COPPER)
    d.point((5, 6), fill=WOOD_DARK)
    # caldeira e chaminé
    d.rectangle((9, 5, 13, 7), fill=GREEN)
    d.line((11, 5, 11, 7), fill=COPPER)
    d.rectangle((11, 1, 12, 4), fill=SOOT)
    return outline(img)


def boilermaker_wrench():
    img, d = new()
    for i in range(8):
        d.point((3 + i, 12 - i), fill=IRON)
        d.point((4 + i, 12 - i), fill=IRON_MID)
    d.rectangle((10, 2, 13, 5), fill=IRON)
    d.rectangle((11, 2, 12, 3), fill=(0, 0, 0, 0))
    d.point((13, 5), fill=IRON_MID)
    d.rectangle((2, 12, 3, 13), fill=COPPER)
    return outline(img)


def repair_kit():
    img, d = new()
    d.rectangle((2, 6, 13, 13), fill=RED)
    d.line((2, 8, 13, 8), fill=RED_DARK)
    d.line((2, 13, 13, 13), fill=RED_DARK)
    d.rectangle((7, 9, 8, 10), fill=BRASS)
    d.line((6, 3, 9, 3), fill=IRON)
    d.line((5, 4, 5, 5), fill=IRON_MID)
    d.line((10, 4, 10, 5), fill=IRON_MID)
    return outline(img)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in (("reinforced_track", reinforced_track), ("steam_boiler", steam_boiler), ("landship", landship),
                     ("boilermaker_wrench", boilermaker_wrench), ("repair_kit", repair_kit)):
        fn().save(OUT / f"{name}.png")
        print(OUT / f"{name}.png")


if __name__ == "__main__":
    main()
