#!/usr/bin/env python3
"""Gera as texturas 32x32 dos blocos da montagem (Fase 3) direto nos assets do mod.

Uso: python3 design/textures/gen_block_textures.py   (precisa do Pillow)
Mesmo kit e mesmas cores dos itens (pixelkit.py): luz de cima à esquerda, 5 tons por material.
Ao contrário dos itens, as faces de bloco são cheias (sem transparência) e repetem lado a lado.
"""
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pixelkit import (  # noqa: E402
    BRASS, BRICK, CLOTH, COPPER, DARK_IRON, FIRE, GREEN, IRON, MORTAR, PAPER, RED, SIZE, SOOT, WOOD, Canvas, rivet,
)

ROOT = Path(__file__).resolve().parent.parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "vapor_trilhos" / "textures" / "block"

DARK_WOOD = [tuple(max(0, int(c * 0.78)) for c in col[:3]) + (255,) for col in WOOD]


def grain(cv, x0, y0, x1, y1, ramp, seed, vertical=False):
    """Tábuas com veio: tom médio, riscos claros/escuros e um nó de vez em quando."""
    rnd = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            cv.set(x, y, ramp[2])
    along = range(y0, y1 + 1) if vertical else range(x0, x1 + 1)
    across = range(x0, x1 + 1) if vertical else range(y0, y1 + 1)
    for a in across:
        if rnd.random() < 0.45:
            tone = ramp[1] if rnd.random() < 0.5 else ramp[3]
            start = rnd.choice(list(along))
            length = rnd.randint(3, 9)
            for b in range(start, min(start + length, along[-1] + 1)):
                cv.set(*((a, b) if vertical else (b, a)), tone)
    if rnd.random() < 0.6:
        kx, ky = rnd.randint(x0 + 2, x1 - 2), rnd.randint(y0 + 1, y1 - 1)
        cv.set(kx, ky, ramp[4]); cv.set(kx + 1, ky, ramp[3])


def planks(cv, ramp, seed, vertical=False, count=4, nails=True):
    """Face de tábuas (4 tábuas de 8 px), com juntas escuras, borda clara e pregos."""
    size = SIZE // count
    for i in range(count):
        a, b = i * size, (i + 1) * size - 1
        if vertical:
            grain(cv, a, 0, b, SIZE - 1, ramp, seed + i, vertical=True)
            for y in range(SIZE):
                cv.set(a, y, ramp[1]); cv.set(b, y, ramp[4])
            off = (i * 11) % 24
            for y in range(SIZE):
                if (y + off) % 32 == 0:
                    for x in range(a, b + 1):
                        cv.set(x, y, ramp[4])
        else:
            grain(cv, 0, a, SIZE - 1, b, ramp, seed + i)
            for x in range(SIZE):
                cv.set(x, a, ramp[1]); cv.set(x, b, ramp[4])
            off = (i * 13) % 24 + 4
            cv.set(off, a + 1, ramp[4]); cv.set(off, b - 1, ramp[4])
            for y in range(a, b + 1):
                cv.set(off, y, ramp[4])
            cv.set(off + 1, a + 1, ramp[1])
            if nails:
                for nx in (2, SIZE - 3):
                    cv.set(nx, a + 3, IRON[3]); cv.set(nx, a + 4, IRON[4])


def plate(cv, ramp, seed, x0=0, y0=0, x1=SIZE - 1, y1=SIZE - 1, rivets=True):
    """Chapa de metal com leve granulado, borda chanfrada e rebites nos cantos."""
    rnd = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            r = rnd.random()
            cv.set(x, y, ramp[1] if r < 0.05 else ramp[3] if r > 0.96 else ramp[2])
    for x in range(x0, x1 + 1):
        cv.set(x, y0, ramp[0]); cv.set(x, y0 + 1, ramp[1]); cv.set(x, y1, ramp[4]); cv.set(x, y1 - 1, ramp[3])
    for y in range(y0, y1 + 1):
        cv.set(x0, y, ramp[1]); cv.set(x1, y, ramp[4]); cv.set(x1 - 1, y, ramp[3])
    if rivets:
        for (x, y) in ((x0 + 3, y0 + 3), (x1 - 4, y0 + 3), (x0 + 3, y1 - 4), (x1 - 4, y1 - 4)):
            rivet(cv, x, y, ramp)


def frame(cv, ramp, width=3):
    """Moldura de ferro em volta da face, chanfrada."""
    for i in range(width):
        t = i / max(1, width - 1)
        top = ramp[0] if i == 0 else ramp[1]
        bottom = ramp[4] if i == 0 else ramp[3]
        for x in range(i, SIZE - i):
            cv.set(x, i, top); cv.set(x, SIZE - 1 - i, bottom)
        for y in range(i, SIZE - i):
            cv.set(i, y, top if t < 1 else ramp[2]); cv.set(SIZE - 1 - i, y, bottom)
    for x, y in ((1, 1), (SIZE - 3, 1), (1, SIZE - 3), (SIZE - 3, SIZE - 3)):
        rivet(cv, x, y, ramp)


def thick_line(cv, x0, y0, x1, y1, ramp):
    """Barra de 3 px com brilho de um lado e sombra do outro."""
    steps = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(steps + 1):
        x = round(x0 + (x1 - x0) * i / steps)
        y = round(y0 + (y1 - y0) * i / steps)
        cv.set(x, y - 1, ramp[1]); cv.set(x, y, ramp[2]); cv.set(x, y + 1, ramp[3])


# =============================================================================================
# Blocos
# =============================================================================================

def landship_chassis():
    cv = Canvas()
    planks(cv, WOOD, 11, vertical=True, nails=False)
    thick_line(cv, 3, 3, 28, 28, IRON)
    thick_line(cv, 28, 3, 3, 28, IRON)
    cv.box(13, 13, 18, 18, IRON)
    rivet(cv, 15, 15)
    frame(cv, IRON)
    return cv.image()


def landship_helm_front():
    cv = Canvas()
    planks(cv, DARK_WOOD, 21)
    c = 15.5
    # pegadores de madeira que saem do aro
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        for r in (12.6, 13.6, 14.4):
            x, y = c + math.cos(a) * r, c + math.sin(a) * r
            cv.set(int(x), int(y), WOOD[1] if r < 14 else WOOD[3])
    # raios
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        for r10 in range(30, 115):
            r = r10 / 10
            cv.set(int(c + math.cos(a) * r), int(c + math.sin(a) * r), BRASS[2] if math.sin(a) + math.cos(a) > 0 else BRASS[1])
    # aro de latão (2 px)
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x + 0.5 - c - 0.5, y + 0.5 - c - 0.5)
            if 9.6 <= d <= 11.8:
                v = -((x - c) + (y - c)) / 16
                cv.set(x, y, BRASS[0] if v > 0.45 else BRASS[1] if v > 0 else BRASS[2] if v > -0.45 else BRASS[3])
    cv.disc(15.5, 15.5, 3.4, BRASS)
    cv.disc(15.5, 15.5, 1.3, COPPER, ring=False)
    return cv.image()


def landship_helm_side():
    cv = Canvas()
    planks(cv, DARK_WOOD, 31)
    for x in range(SIZE):
        cv.set(x, 0, BRASS[1]); cv.set(x, 1, BRASS[2]); cv.set(x, 2, BRASS[4])
    for (x0, y0) in ((0, 3), (26, 3), (0, 26), (26, 26)):
        plate(cv, IRON, x0 + y0, x0, y0, x0 + 5, y0 + 5, rivets=False)
        rivet(cv, x0 + 2, y0 + 2)
    return cv.image()


def landship_helm_top():
    cv = Canvas()
    planks(cv, WOOD, 41)
    # rosa dos ventos de latão; a seta vermelha aponta para a frente do landship
    # (o modelo gira 180°: o volante fica virado para o piloto)
    cv.disc(15.5, 14.5, 9.4, BRASS)
    cv.disc(15.5, 14.5, 7.8, PAPER, ring=False)
    for a in range(0, 360, 45):
        r = math.radians(a)
        cv.set(round(15.5 + math.cos(r) * 6.4 - 0.5), round(14.5 + math.sin(r) * 6.4 - 0.5), SOOT[2])
    cv.draw(13, 9, [
        "..P..",
        ".P.P.",
        "..P..",
        "..r..",
        ".rRr.",
        ".rRr.",
        "rRRRr",
        "rRRRr",
        ".sRs.",
        "..s..",
    ], {"P": SOOT[2], "r": RED[2], "R": RED[1], "s": RED[3]})
    for x in range(12, 20):
        cv.set(x, 27, RED[2]); cv.set(x, 28, RED[3])
    return cv.image()


def reinforced_track_side():
    cv = Canvas()
    cv.rect(0, 0, SIZE - 1, SIZE - 1, SOOT[3])
    # sapatas em cima e embaixo
    for x in range(SIZE):
        k = x % 8
        for y in range(0, 6):
            cv.set(x, y, IRON[2 if y < 3 else 3] if k in (0, 1) else WOOD[1 if y < 2 else 2 if y < 4 else 3])
        for y in range(26, 32):
            cv.set(x, y, IRON[3 if y < 29 else 4] if k in (0, 1) else WOOD[2 if y < 28 else 3 if y < 30 else 4])
        if k == 0:
            cv.set(x, 2, IRON[0]); cv.set(x, 28, IRON[1])
    # trilho de ferro e rodas
    for x in range(SIZE):
        cv.set(x, 6, DARK_IRON[1]); cv.set(x, 25, DARK_IRON[3])
    for cx in (7.5, 23.5):
        cv.disc(cx, 15.5, 6.6, IRON)
        cv.disc(cx, 15.5, 2.4, DARK_IRON)
        cv.set(int(cx), 15, DARK_IRON[4])
    return cv.image()


def reinforced_track_top():
    cv = Canvas()
    for i in range(4):
        y0 = i * 8
        grain(cv, 4, y0, 27, y0 + 5, WOOD, 50 + i)
        for x in range(4, 28):
            cv.set(x, y0, WOOD[0]); cv.set(x, y0 + 5, WOOD[3])
        for y in (y0 + 6, y0 + 7):
            for x in range(4, 28):
                cv.set(x, y, IRON[1] if y == y0 + 6 else IRON[3])
        cv.set(9, y0 + 6, IRON[0]); cv.set(22, y0 + 6, IRON[0])
    for y in range(SIZE):
        for x in (0, 1, 2, 3):
            cv.set(x, y, IRON[[1, 2, 2, 3][x]])
        for x in (28, 29, 30, 31):
            cv.set(x, y, IRON[[2, 2, 3, 4][x - 28]])
        if y % 8 == 3:
            rivet(cv, 1, y); rivet(cv, 29, y)
    return cv.image()


def steam_boiler_side():
    cv = Canvas()
    cv.vcyl(0, 0, SIZE - 1, SIZE - 1, [GREEN[1]] + GREEN[1:], highlight=0.3)
    # cintas de cobre (anéis) com rebites
    for y0 in (3, 26):
        for x in range(SIZE):
            cv.set(x, y0, COPPER[1]); cv.set(x, y0 + 1, COPPER[2]); cv.set(x, y0 + 2, COPPER[3])
            if x % 6 == 2:
                cv.set(x, y0 + 1, COPPER[0])
    # emendas das chapas
    for y in range(6, 26):
        cv.set(15, y, GREEN[3])
        if y % 4 == 0:
            cv.set(14, y, GREEN[0]); cv.set(17, y, GREEN[0])
    # manômetro e porta da fornalha
    cv.disc(8.5, 11.5, 3.6, BRASS)
    cv.disc(8.5, 11.5, 2.4, PAPER, ring=False)
    cv.line(8, 12, 10, 10, RED[3])
    cv.box(19, 15, 27, 23, DARK_IRON)
    cv.rect(20, 18, 26, 22, FIRE[4])
    cv.rect(21, 19, 25, 21, FIRE[3]); cv.rect(22, 20, 24, 20, FIRE[2])
    for x in (20, 23, 26):
        cv.set(x, 17, DARK_IRON[4])
    return cv.image()


def steam_boiler_top():
    cv = Canvas()
    plate(cv, IRON, 61)
    for a in range(0, 360, 30):
        r = math.radians(a)
        rivet(cv, round(15.5 + math.cos(r) * 11 - 0.5), round(15.5 + math.sin(r) * 11 - 0.5))
    cv.disc(15.5, 15.5, 7.5, BRASS)
    cv.disc(15.5, 15.5, 5.2, SOOT, ring=False)
    cv.disc(15.5, 15.5, 3.2, [SOOT[4]] * 5, ring=False)
    return cv.image()


def bed_module_top():
    cv = Canvas()
    planks(cv, WOOD, 71)
    # colchão com cobertor vermelho e travesseiro
    cv.rect(3, 2, 28, 29, CLOTH[2])
    cv.box(5, 3, 26, 9, CLOTH)
    cv.set(7, 4, CLOTH[0]); cv.set(8, 4, CLOTH[0])
    cv.hcyl(3, 11, 28, 29, RED, highlight=0.15)
    for x in range(3, 29):
        cv.set(x, 11, RED[0]); cv.set(x, 12, RED[1]); cv.set(x, 14, RED[3])
    for y in range(15, 30, 5):
        for x in range(4, 28):
            if (x + y) % 3 == 0:
                cv.set(x, y, RED[3])
    return cv.image()


def bed_module_side():
    cv = Canvas()
    planks(cv, WOOD, 81)
    cv.rect(0, 0, SIZE - 1, 6, CLOTH[1])
    for x in range(SIZE):
        cv.set(x, 0, CLOTH[0]); cv.set(x, 6, CLOTH[3])
    cv.rect(0, 7, SIZE - 1, 13, RED[2])
    for x in range(SIZE):
        cv.set(x, 7, RED[1]); cv.set(x, 12, RED[3]); cv.set(x, 13, RED[4])
        if x % 4 == 0:
            cv.set(x, 10, RED[3])
    return cv.image()


def cargo_module_side():
    cv = Canvas()
    planks(cv, WOOD, 91)
    for x0 in (0, 27):
        for y in range(SIZE):
            for i in range(5):
                cv.set(x0 + i, y, IRON[[1, 2, 2, 3, 4][i]])
            if y % 8 == 4:
                rivet(cv, x0 + 1, y)
    for x in range(5, 27):
        cv.set(x, 10, WOOD[4]); cv.set(x, 11, WOOD[1])
    cv.box(13, 8, 18, 15, BRASS)
    cv.set(15, 12, BRASS[4]); cv.set(16, 12, BRASS[4]); cv.set(15, 13, BRASS[4])
    return cv.image()


def cargo_module_top():
    cv = Canvas()
    planks(cv, WOOD, 101, vertical=True, nails=False)
    for y0 in (6, 22):
        for x in range(SIZE):
            for i in range(4):
                cv.set(x, y0 + i, IRON[[1, 2, 3, 4][i]])
            if x % 8 == 3:
                rivet(cv, x, y0 + 1)
    frame(cv, DARK_IRON, width=2)
    return cv.image()


def furnace_module_side():
    cv = Canvas()
    cv.rect(0, 0, SIZE - 1, SIZE - 1, MORTAR[2])
    rnd = random.Random(111)
    for row in range(8):
        y = row * 4
        off = 0 if row % 2 == 0 else 4
        for x0 in range(-off, SIZE, 8):
            a, b = max(0, x0), min(SIZE - 1, x0 + 6)
            if a > b:
                continue
            tone = rnd.choice((0, 0, 1))
            for x in range(a, b + 1):
                for yy in range(y, y + 3):
                    c = BRICK[1 + tone] if yy == y else BRICK[2 + tone] if yy == y + 1 else BRICK[3]
                    cv.set(x, yy, c)
            cv.set(a, y, BRICK[0])
    # boca da fornalha com fogo
    cv.box(8, 14, 23, 31, DARK_IRON)
    cv.rect(10, 17, 21, 31, SOOT[4])
    cv.draw(10, 20, [
        "...f....F...",
        "..fF...fFf..",
        ".fFFf.fFFf.f",
        "fFffFfFffFfF",
        "effeeffeeffe",
        "eeEeeEeeEeeE",
        "EEEEEEEEEEEE",
    ], {"F": FIRE[0], "f": FIRE[2], "e": FIRE[3], "E": FIRE[4]})
    for x in range(10, 22):
        cv.set(x, 27, SOOT[2] if x % 2 else SOOT[3])
    return cv.image()


def furnace_module_top():
    cv = Canvas()
    plate(cv, IRON, 121)
    # grelha de ventilação e chaminezinha
    for y in range(6, 14, 2):
        for x in range(5, 27):
            cv.set(x, y, DARK_IRON[4]); cv.set(x, y + 1, IRON[1])
    cv.disc(15.5, 21.5, 5.2, BRASS)
    cv.disc(15.5, 21.5, 3.2, [SOOT[4]] * 5, ring=False)
    cv.set(14, 20, FIRE[3]); cv.set(16, 22, FIRE[4])
    return cv.image()


def compactor_module_side():
    cv = Canvas()
    cv.hcyl(0, 0, SIZE - 1, SIZE - 1, IRON, highlight=0.3)
    for row, y in enumerate(range(2, 30, 4)):
        for x in range((row % 2) * 4, SIZE, 8):
            t = y / 31
            cv.set(x, y, IRON[0] if t < 0.45 else IRON[1])
            cv.set(x + 1, y, IRON[1] if t < 0.45 else IRON[2])
            cv.set(x + 2, y, IRON[3])
            cv.set(x, y + 1, IRON[3] if t < 0.45 else IRON[4])
            cv.set(x + 1, y + 1, IRON[4])
    for x in range(SIZE):
        cv.set(x, 0, DARK_IRON[1]); cv.set(x, SIZE - 1, DARK_IRON[4])
    return cv.image()


def compactor_module_top():
    cv = Canvas()
    plate(cv, DARK_IRON, 131)
    # faixa de aviso e tampa do pistão
    for y in range(3, 9):
        for x in range(3, 29):
            cv.set(x, y, BRASS[1] if (x + y) // 3 % 2 == 0 else SOOT[3])
    cv.disc(15.5, 19.5, 6.8, IRON)
    cv.disc(15.5, 19.5, 4.4, BRASS)
    cv.disc(15.5, 19.5, 1.6, BRASS, ring=False)
    return cv.image()


BLOCKS = {
    "landship_chassis": landship_chassis,
    "landship_helm_front": landship_helm_front,
    "landship_helm_side": landship_helm_side,
    "landship_helm_top": landship_helm_top,
    "reinforced_track_side": reinforced_track_side,
    "reinforced_track_top": reinforced_track_top,
    "steam_boiler_side": steam_boiler_side,
    "steam_boiler_top": steam_boiler_top,
    "bed_module_top": bed_module_top,
    "bed_module_side": bed_module_side,
    "cargo_module_side": cargo_module_side,
    "cargo_module_top": cargo_module_top,
    "furnace_module_side": furnace_module_side,
    "furnace_module_top": furnace_module_top,
    "compactor_module_side": compactor_module_side,
    "compactor_module_top": compactor_module_top,
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in BLOCKS.items():
        fn().save(OUT / f"{name}.png")
        print(OUT / f"{name}.png")


if __name__ == "__main__":
    main()
