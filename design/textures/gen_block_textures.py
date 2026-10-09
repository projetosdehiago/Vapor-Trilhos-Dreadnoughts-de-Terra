#!/usr/bin/env python3
"""Gera as texturas 16x16 dos blocos da montagem (Fase 3) direto nos assets do mod.

Uso: python3 design/textures/gen_block_textures.py
As cores são as mesmas dos itens (gen_item_textures.py) e do modelo do landship.
"""
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "vapor_trilhos" / "textures" / "block"

IRON = (184, 188, 194, 255)
IRON_MID = (138, 143, 150, 255)
IRON_DARK = (74, 76, 82, 255)
WOOD = (160, 122, 74, 255)
WOOD_DARK = (110, 80, 48, 255)
WOOD_LIGHT = (184, 146, 96, 255)
COPPER = (200, 122, 69, 255)
COPPER_DARK = (142, 79, 42, 255)
GREEN = (63, 122, 82, 255)
GREEN_DARK = (42, 82, 54, 255)
GREEN_LIGHT = (90, 150, 106, 255)
RED = (168, 50, 50, 255)
RED_DARK = (110, 32, 32, 255)
WOOL = (232, 228, 220, 255)
BRASS = (216, 176, 74, 255)
SOOT = (58, 54, 52, 255)
FIRE = (255, 160, 48, 255)
FIRE_DARK = (210, 90, 30, 255)
BRICK = (150, 74, 58, 255)
BRICK_DARK = (104, 50, 40, 255)
MORTAR = (170, 160, 150, 255)


def new(fill):
    img = Image.new("RGBA", (16, 16), fill)
    return img, ImageDraw.Draw(img)


def noise(img, amount=8, seed=0):
    """Variação leve de tom por pixel, para não ficar chapado."""
    rng = random.Random(seed)
    px = img.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            d = rng.randint(-amount, amount)
            px[x, y] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)
    return img


def planks(base=WOOD, dark=WOOD_DARK, seed=1):
    img, d = new(base)
    for y in (3, 7, 11, 15):
        d.line((0, y, 15, y), fill=dark)
    for i, y in enumerate((0, 4, 8, 12)):
        x = (i * 5 + 3) % 16
        d.line((x, y, x, y + 2), fill=dark)
    return noise(img, 6, seed)


def riveted(base, dark, light, seed=2):
    img, d = new(base)
    d.rectangle((0, 0, 15, 15), outline=dark)
    d.line((1, 1, 14, 1), fill=light)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        d.point((x, y), fill=light)
        d.point((x + 1, y + 1), fill=dark)
    return noise(img, 5, seed)


def track_top():
    img, d = new(IRON_DARK)
    for y in range(0, 16, 4):
        d.rectangle((1, y, 14, y + 2), fill=WOOD)
        d.line((1, y + 2, 14, y + 2), fill=WOOD_DARK)
        d.point((0, y + 1), fill=IRON)
        d.point((15, y + 1), fill=IRON)
    return noise(img, 5, 3)


def track_side():
    img, d = new(IRON_MID)
    d.rectangle((0, 0, 15, 3), fill=IRON_DARK)
    d.rectangle((0, 12, 15, 15), fill=IRON_DARK)
    for x in (2, 7, 12):
        d.ellipse((x - 2, 5, x + 2, 9), fill=IRON_DARK)
        d.point((x, 7), fill=BRASS)
    for x in range(0, 16, 3):
        d.point((x, 1), fill=IRON)
        d.point((x, 14), fill=IRON)
    return noise(img, 5, 4)


def boiler_side():
    img, d = new(GREEN)
    d.line((0, 1, 15, 1), fill=GREEN_LIGHT)
    d.line((0, 14, 15, 14), fill=GREEN_DARK)
    for x in (3, 12):
        d.rectangle((x, 0, x + 1, 15), fill=COPPER)
        d.line((x + 1, 0, x + 1, 15), fill=COPPER_DARK)
    d.rectangle((6, 9, 9, 12), fill=SOOT)
    d.rectangle((7, 10, 8, 11), fill=FIRE)
    d.ellipse((6, 3, 9, 6), fill=WOOL)
    d.point((8, 4), fill=RED)
    return noise(img, 4, 5)


def boiler_top():
    img = riveted(IRON_DARK, SOOT, IRON_MID, 6)
    d = ImageDraw.Draw(img)
    d.ellipse((4, 4, 11, 11), fill=SOOT)
    d.ellipse((6, 6, 9, 9), fill=(30, 28, 27, 255))
    return img


def chassis(seed):
    img = planks(WOOD, WOOD_DARK, seed)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 15, 15), outline=IRON_MID)
    d.rectangle((1, 1, 14, 14), outline=IRON_DARK)
    d.line((1, 1, 14, 14), fill=IRON_MID)
    d.line((14, 1, 1, 14), fill=IRON_MID)
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15), (7, 7), (8, 8)):
        d.point((x, y), fill=IRON)
    return img


def helm_front():
    img = planks(WOOD_DARK, (80, 58, 36, 255), 7)
    d = ImageDraw.Draw(img)
    d.ellipse((2, 2, 13, 13), outline=WOOD_LIGHT)
    d.ellipse((3, 3, 12, 12), outline=WOOD)
    for (x0, y0, x1, y1) in ((7, 0, 8, 15), (0, 7, 15, 8)):
        d.rectangle((x0, y0, x1, y1), fill=WOOD_LIGHT)
    d.line((2, 2, 13, 13), fill=WOOD_LIGHT)
    d.line((13, 2, 2, 13), fill=WOOD_LIGHT)
    d.rectangle((6, 6, 9, 9), fill=BRASS)
    return img


def helm_top():
    img = planks(WOOD, WOOD_DARK, 8)
    d = ImageDraw.Draw(img)
    d.ellipse((4, 4, 11, 11), outline=BRASS)
    # seta para a frente do landship (o modelo gira 180°: o volante fica virado para o piloto)
    d.polygon([(8, 14), (6, 11), (10, 11)], fill=RED)
    return img


def bed_top():
    img, d = new(RED)
    d.rectangle((0, 0, 15, 4), fill=WOOL)
    d.line((0, 5, 15, 5), fill=RED_DARK)
    d.line((0, 10, 15, 10), fill=RED_DARK)
    d.rectangle((0, 0, 15, 15), outline=WOOD_DARK)
    return noise(img, 5, 9)


def bed_side():
    img = planks(WOOD, WOOD_DARK, 10)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 2, 15, 6), fill=RED)
    d.line((0, 6, 15, 6), fill=RED_DARK)
    d.rectangle((0, 0, 15, 1), fill=WOOL)
    return img


def cargo_side():
    img = planks(WOOD_LIGHT, WOOD_DARK, 11)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 15, 15), outline=IRON_DARK)
    d.line((0, 5, 15, 5), fill=IRON_DARK)
    d.line((0, 10, 15, 10), fill=IRON_DARK)
    d.rectangle((7, 6, 8, 8), fill=BRASS)
    return img


def cargo_top():
    img = planks(WOOD_LIGHT, WOOD_DARK, 12)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 15, 15), outline=IRON_DARK)
    d.line((1, 1, 14, 14), fill=WOOD_DARK)
    d.line((14, 1, 1, 14), fill=WOOD_DARK)
    return img


def furnace_side():
    img, d = new(BRICK)
    for y in (3, 7, 11, 15):
        d.line((0, y, 15, y), fill=MORTAR)
    for i, y in enumerate((0, 4, 8, 12)):
        for x in ((3, 11) if i % 2 == 0 else (7, 15)):
            d.line((x, y, x, y + 2), fill=MORTAR)
    d.rectangle((4, 8, 11, 14), fill=SOOT)
    d.rectangle((5, 10, 10, 14), fill=FIRE_DARK)
    d.rectangle((6, 11, 9, 14), fill=FIRE)
    return noise(img, 6, 13)


def furnace_top():
    img = riveted(IRON_MID, IRON_DARK, IRON, 14)
    d = ImageDraw.Draw(img)
    d.rectangle((6, 6, 9, 9), fill=SOOT)
    return img


def compactor_side():
    img, d = new(IRON_DARK)
    for x in range(0, 16, 4):
        d.rectangle((x, 0, x + 1, 15), fill=IRON_MID)
        d.line((x + 1, 0, x + 1, 15), fill=IRON)
    d.rectangle((0, 0, 15, 1), fill=SOOT)
    d.rectangle((0, 14, 15, 15), fill=SOOT)
    return noise(img, 5, 15)


def compactor_top():
    img = riveted(IRON_MID, IRON_DARK, IRON, 16)
    d = ImageDraw.Draw(img)
    d.rectangle((2, 6, 13, 9), fill=IRON_DARK)
    d.point((7, 7), fill=BRASS)
    d.point((8, 8), fill=BRASS)
    return img


TEXTURES = {
    "reinforced_track_top": track_top,
    "reinforced_track_side": track_side,
    "steam_boiler_side": boiler_side,
    "steam_boiler_top": boiler_top,
    "landship_chassis": lambda: chassis(17),
    "landship_helm_front": helm_front,
    "landship_helm_side": lambda: planks(WOOD_DARK, (80, 58, 36, 255), 18),
    "landship_helm_top": helm_top,
    "bed_module_top": bed_top,
    "bed_module_side": bed_side,
    "cargo_module_side": cargo_side,
    "cargo_module_top": cargo_top,
    "furnace_module_side": furnace_side,
    "furnace_module_top": furnace_top,
    "compactor_module_side": compactor_side,
    "compactor_module_top": compactor_top,
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in TEXTURES.items():
        fn().save(OUT / f"{name}.png")
        print(OUT / f"{name}.png")


if __name__ == "__main__":
    main()
