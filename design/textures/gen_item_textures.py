#!/usr/bin/env python3
"""Gera as texturas 32x32 dos itens (pixel art) direto nos assets do mod.

Uso: python3 design/textures/gen_item_textures.py   (precisa do Pillow)

Cada item é desenhado com o kit de pixelkit.py (formas sombreadas com luz de cima à esquerda e
contorno colorido) mais detalhes colocados à mão. O Painel de Comando, que só existe no
Bedrock, vai para o pacote de recursos em bedrock/ quando a pasta existir.
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pixelkit import (  # noqa: E402
    BRASS, BRICK, CLOTH, COPPER, DARK_IRON, FIRE, GREEN, IRON, MORTAR, PAPER, RED, SOOT, WOOD, Canvas, rivet,
)

ROOT = Path(__file__).resolve().parent.parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "vapor_trilhos" / "textures" / "item"
BEDROCK_OUT = ROOT / "bedrock" / "packs" / "RP" / "textures" / "items" / "vapor_trilhos"


def steam_boiler():
    cv = Canvas()
    # chaminé (atrás) com aro de latão
    cv.vcyl(20, 3, 24, 11, SOOT)
    cv.hcyl(19, 1, 25, 3, BRASS)
    # apito de latão
    cv.vcyl(11, 5, 12, 9, BRASS)
    cv.rect(10, 4, 13, 4, BRASS[2]); cv.set(10, 4, BRASS[1])
    # tanque
    cv.hcyl(4, 10, 27, 25, GREEN)
    for (x, y) in ((4, 10), (27, 10), (4, 25), (27, 25)):
        cv.set(x, y, None)
    for y in range(11, 25):
        cv.set(27, y, GREEN[4] if y > 17 else GREEN[3])
    # tampa da frente (ferro) arredondada
    for y in range(11, 25):
        cv.set(3, y, IRON[3])
    cv.vcyl(1, 12, 3, 23, IRON)
    cv.set(1, 12, None); cv.set(1, 23, None)
    for y in (13, 17, 21):
        rivet(cv, 2, y)
    # cintas de cobre que acompanham a curva do tanque
    for bx in (8, 19):
        cv.hcyl(bx, 10, bx + 1, 25, COPPER)
        for y in (12, 16, 20, 24):
            cv.set(bx, y, COPPER[0])
    # manômetro
    cv.disc(14.5, 14.5, 3.3, BRASS)
    cv.disc(14.5, 14.5, 2.2, PAPER, ring=False)
    cv.set(14, 14, RED[3]); cv.set(15, 13, RED[2]); cv.set(16, 12, RED[1])
    # porta da fornalha com brilho
    cv.box(11, 19, 17, 24, DARK_IRON)
    cv.rect(12, 21, 16, 23, FIRE[3])
    cv.rect(13, 22, 15, 22, FIRE[1]); cv.set(14, 21, FIRE[2])
    for x in (12, 14, 16):
        cv.set(x, 20, DARK_IRON[4])
    # pés
    cv.box(6, 26, 9, 28, DARK_IRON)
    cv.box(22, 26, 25, 28, DARK_IRON)
    cv.outline()
    return cv.image()


def track_loop(cv, cxl, cxr, cy, radius, belt=2.6, wheels=True):
    """Esteira vista de lado, em forma de pista (pontas redondas): sapatas de madeira com
    garras de ferro que saltam para fora, roda dentada nas pontas e rodas de apoio no meio."""
    def shape(x, y):
        px, py = x + 0.5, y + 0.5
        if px < cxl:
            dx, dy = px - cxl, py - cy
            d = math.hypot(dx, dy)
            s = cxl - radius * (math.atan2(dy, -dx) + math.pi / 2)
        elif px > cxr:
            dx, dy = px - cxr, py - cy
            d = math.hypot(dx, dy)
            s = cxr + radius * (math.pi / 2 - math.atan2(dy, dx))
        else:
            d = abs(py - cy)
            s = px if py < cy else 2 * cxr - px + 40
        return d, s

    for y in range(32):
        for x in range(32):
            d, s = shape(x, y)
            lug = int(s // 2) % 3 == 0
            outer = int(s // 2) % 6 == 0
            top = (y + 0.5) < cy
            shade = 0 if (y + 0.5) < cy - radius * 0.5 else 1 if (y + 0.5) < cy else 2 if (y + 0.5) < cy + radius * 0.6 else 3
            if radius - belt <= d <= radius:
                if lug:
                    cv.set(x, y, IRON[min(4, shade + 1)])
                else:
                    cv.set(x, y, WOOD[min(4, shade + (1 if d > radius - 1.2 else 2))])
            elif radius < d <= radius + 1 and outer:
                cv.set(x, y, IRON[min(4, shade + 2)] if not top else IRON[shade + 1])
            elif d < radius - belt:
                cv.set(x, y, SOOT[3] if d < radius - belt - 1 else SOOT[4])
    if wheels:
        r = radius - belt - 1.2
        for cx in (cxl, cxr):
            cv.disc(cx - 0.5, cy - 0.5, r * 0.82, IRON)
            # dentes da roda dentada
            for a in range(0, 360, 45):
                tx = cx - 0.5 + math.cos(math.radians(a)) * (r * 0.82 + 0.7)
                ty = cy - 0.5 + math.sin(math.radians(a)) * (r * 0.82 + 0.7)
                cv.set(round(tx), round(ty), IRON[3])
            cv.disc(cx - 0.5, cy - 0.5, r * 0.38, DARK_IRON, ring=False)
        for i in (1, 2):
            mx = cxl + (cxr - cxl) * i / 3
            cv.disc(mx - 0.5, cy + 1.2, r * 0.55, IRON)
            cv.set(round(mx - 0.5), round(cy + 1.2), DARK_IRON[3])


def reinforced_track():
    cv = Canvas()
    track_loop(cv, 8.5, 23.5, 15.5, 7.6)
    cv.outline()
    return cv.image()


def boilermaker_wrench():
    cv = Canvas()
    # cabo em diagonal, 3 px, com punho enrolado em cobre
    for i in range(0, 17):
        x, y = 5 + i, 27 - i
        cv.set(x - 1, y, IRON[1]); cv.set(x, y, IRON[2]); cv.set(x + 1, y, IRON[3])
        cv.set(x, y - 1, IRON[1]); cv.set(x + 1, y - 1, IRON[2])
    for i in range(0, 9):
        x, y = 5 + i, 27 - i
        stripe = i % 3 == 0
        cv.set(x - 1, y, COPPER[1] if not stripe else COPPER[3])
        cv.set(x, y, COPPER[2] if not stripe else COPPER[4])
        cv.set(x + 1, y, COPPER[3])
        cv.set(x, y - 1, COPPER[0] if not stripe else COPPER[2])
        cv.set(x + 1, y - 1, COPPER[1] if not stripe else COPPER[3])
    # ponta do cabo
    cv.disc(4.5, 27.5, 1.6, IRON)
    # cabeça com boca aberta virada para cima/direita (fenda em U ao longo da diagonal)
    hx, hy = 23.5, 8.5
    cv.disc(hx, hy, 6.4, IRON)
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - hx - 0.5, y + 0.5 - hy - 0.5
            u = (dx - dy) / math.sqrt(2)
            v = (dx + dy) / math.sqrt(2)
            if (u > -0.3 and abs(v) < 2.2) or math.hypot(dx, dy) < 2.2 or u > 4.6:
                cv.px[y][x] = None
    # parafuso de ajuste em latão
    cv.set(19, 13, BRASS[1]); cv.set(20, 13, BRASS[2]); cv.set(19, 14, BRASS[2]); cv.set(20, 12, BRASS[0])
    cv.outline()
    return cv.image()


def repair_kit():
    cv = Canvas()
    # alça de ferro
    cv.box(10, 4, 21, 6, IRON)
    cv.box(10, 6, 12, 11, IRON)
    cv.box(19, 6, 21, 11, IRON)
    # tampa
    cv.box(3, 11, 28, 16, RED)
    cv.rect(4, 12, 27, 12, RED[1])
    for x in range(3, 29):
        cv.set(x, 16, RED[4])
    # corpo
    cv.box(3, 17, 28, 27, RED)
    cv.rect(4, 17, 27, 17, RED[3])
    # cantoneiras de ferro
    for (x0, y0) in ((3, 11), (25, 11), (3, 24), (25, 24)):
        cv.box(x0, y0, x0 + 3, y0 + 3, IRON)
        rivet(cv, x0 + 1, y0 + 1)
    # fecho de latão
    cv.box(14, 14, 17, 20, BRASS)
    cv.set(15, 18, BRASS[4]); cv.set(16, 18, BRASS[4])
    # faixa amarela e preta
    for x in range(8, 24):
        c = BRASS[1] if (x // 2) % 2 == 0 else SOOT[2]
        cv.set(x, 23, c); cv.set(x, 24, c if c != BRASS[1] else BRASS[2])
    cv.outline()
    return cv.image()


def landship():
    cv = Canvas()
    # esteira: faixa com sapatas de madeira, garras de ferro e rodas
    cv.rect(2, 23, 29, 28, SOOT[3])
    for x in range(2, 30):
        k = x % 3
        cv.set(x, 22, IRON[1] if k == 0 else WOOD[1])
        cv.set(x, 29, IRON[3] if k == 0 else WOOD[3])
    for y in range(23, 29):
        cv.set(1, y, WOOD[2] if y % 3 else IRON[2])
        cv.set(30, y, WOOD[3] if y % 3 else IRON[3])
    for cx in (4.5, 10.5, 16.5, 22.5, 27.5):
        cv.disc(cx, 25.5, 2.2, IRON)
        cv.set(round(cx), 26, DARK_IRON[3])
    # casco de ferro com rebites e convés de madeira
    cv.box(2, 17, 29, 21, IRON)
    for x in range(4, 28, 4):
        rivet(cv, x, 19)
    cv.box(1, 15, 30, 16, WOOD)
    for x in range(5, 30, 5):
        cv.set(x, 16, WOOD[3])
    # cabine na frente (esquerda): interior escuro, colunas, teto de cobre, leme
    cv.rect(4, 7, 13, 14, SOOT[3])
    cv.rect(4, 7, 13, 7, SOOT[4])
    cv.vcyl(2, 6, 3, 14, WOOD)
    cv.vcyl(14, 6, 15, 14, WOOD)
    cv.hcyl(1, 3, 16, 6, COPPER)
    cv.set(1, 3, None); cv.set(16, 3, None)
    cv.draw(5, 8, [
        "..y.y..",
        ".yYZzy.",
        "yZ.b.zy",
        ".z.B.z.",
        "yz...zy",
        ".yzZzy.",
        "..z.z..",
    ], {"y": BRASS[1], "Y": BRASS[0], "z": BRASS[2], "Z": BRASS[3], "b": WOOD[1], "B": WOOD[3]})
    cv.box(11, 11, 12, 14, WOOD)
    # lanterna pendurada na coluna da frente
    cv.set(0, 8, BRASS[3]); cv.set(0, 9, FIRE[1]); cv.set(0, 10, FIRE[2]); cv.set(0, 11, BRASS[3])
    cv.set(1, 7, BRASS[2])
    # farol
    cv.set(1, 18, FIRE[0]); cv.set(1, 19, FIRE[2])
    # caldeira atrás (direita) com chaminé
    cv.hcyl(17, 8, 29, 14, GREEN)
    cv.hcyl(22, 8, 23, 14, COPPER)
    cv.set(17, 8, None); cv.set(29, 8, None); cv.set(29, 14, GREEN[4])
    cv.vcyl(24, 2, 27, 7, SOOT)
    cv.hcyl(23, 0, 28, 1, BRASS)
    cv.disc(19.5, 11, 1.5, BRASS)
    cv.outline()
    return cv.image()


def skid(cv):
    """Base de ferro comum a todos os módulos (o encaixe no convés)."""
    cv.box(2, 25, 29, 28, IRON)
    cv.rect(3, 28, 28, 28, IRON[4])
    for x in (4, 10, 21, 27):
        rivet(cv, x, 26)


def bed_module():
    cv = Canvas()
    skid(cv)
    # cabeceira e pés da cama
    cv.box(3, 9, 6, 24, WOOD)
    cv.box(25, 15, 28, 24, WOOD)
    cv.set(4, 10, WOOD[0]); cv.set(26, 16, WOOD[0])
    # colchão e cobertor vermelho
    cv.box(7, 16, 24, 21, CLOTH)
    cv.hcyl(13, 15, 24, 21, RED)
    for x in range(13, 25):
        cv.set(x, 15, RED[1])
    cv.rect(13, 22, 24, 22, RED[4])
    # dobra do cobertor
    cv.rect(13, 15, 14, 21, RED[0]); cv.set(14, 21, RED[2])
    # travesseiro
    cv.box(7, 13, 12, 16, CLOTH)
    cv.set(8, 14, CLOTH[0])
    # estrado
    cv.box(7, 22, 24, 24, WOOD)
    cv.outline()
    return cv.image()


def cargo_module():
    cv = Canvas()
    skid(cv)
    # baú de madeira com cintas de ferro e fecho de latão
    cv.box(4, 8, 27, 13, WOOD)
    cv.hcyl(4, 8, 27, 12, WOOD, highlight=0.35)
    cv.box(4, 14, 27, 24, WOOD)
    for y in (17, 21):
        for x in range(5, 27):
            cv.set(x, y, WOOD[3])
    cv.rect(4, 13, 27, 13, WOOD[4])
    for x0 in (7, 23):
        cv.box(x0, 8, x0 + 1, 24, IRON)
    cv.box(14, 11, 17, 16, BRASS)
    cv.set(15, 14, BRASS[4]); cv.set(16, 14, BRASS[4])
    cv.outline()
    return cv.image()


def furnace_module():
    cv = Canvas()
    skid(cv)
    # corpo de tijolos
    cv.rect(4, 7, 27, 24, MORTAR[2])
    for row, y in enumerate(range(7, 24, 3)):
        off = 0 if row % 2 == 0 else 3
        for x0 in range(4 - off, 28, 6):
            a, b = max(4, x0), min(27, x0 + 4)
            if a <= b:
                cv.box(a, y, b, y + 1, BRICK)
    # topo de ferro e chaminezinha
    cv.box(3, 5, 28, 7, IRON)
    cv.vcyl(20, 1, 23, 5, SOOT)
    cv.hcyl(19, 1, 24, 1, BRASS)
    # boca da fornalha com fogo
    cv.box(9, 13, 22, 23, DARK_IRON)
    cv.rect(10, 15, 21, 22, SOOT[4])
    cv.draw(10, 16, [
        "....f.......",
        "...fF..f....",
        "..fFFf.Ff.f.",
        ".fFffFfFFfF.",
        "eeffeeffeffe",
        "EeEEeEEeEEeE",
        "EEEEEEEEEEEE",
    ], {"F": FIRE[0], "f": FIRE[2], "e": FIRE[3], "E": FIRE[4]})
    cv.rect(9, 13, 22, 13, IRON[1])
    cv.outline()
    return cv.image()


def compactor_module():
    cv = Canvas()
    skid(cv)
    # braços de ferro que seguram o rolo
    cv.box(3, 8, 6, 24, DARK_IRON)
    cv.box(25, 8, 28, 24, DARK_IRON)
    # travessa e pistão a vapor
    cv.box(6, 6, 25, 9, IRON)
    for x in (9, 15, 21):
        rivet(cv, x, 7)
    cv.vcyl(13, 2, 18, 5, BRASS)
    cv.rect(12, 1, 19, 1, BRASS[3])
    # rolo de aço com cravos em fileiras alternadas (como um rolo compactador "pé de carneiro")
    cv.hcyl(7, 11, 24, 24, IRON, highlight=0.3)
    for row, y in enumerate(range(12, 24, 3)):
        for x in range(8 + (row % 2) * 2, 24, 4):
            t = (y - 11) / 13
            cv.set(x, y, IRON[0] if t < 0.45 else IRON[1])
            cv.set(x + 1, y, IRON[3])
            cv.set(x, y + 1, IRON[3] if t < 0.45 else IRON[4])
    for y in range(11, 25):
        cv.set(7, y, IRON[3]); cv.set(24, y, IRON[4])
    # cubos laterais
    cv.disc(4.5, 17, 2.4, BRASS)
    cv.disc(26.5, 17, 2.4, BRASS)
    cv.outline()
    return cv.image()


def command_panel():
    cv = Canvas()
    # placa de madeira com moldura de latão
    cv.box(2, 3, 29, 28, BRASS)
    cv.box(4, 5, 27, 26, WOOD)
    for (x, y) in ((3, 4), (28, 4), (3, 27), (28, 27)):
        cv.set(x, y, BRASS[0])
    # manômetro grande: mostrador claro, marcas na borda, faixa vermelha e ponteiro
    cv.disc(11.5, 13.5, 6.4, BRASS)
    cv.disc(11.5, 13.5, 5.0, PAPER, ring=False)
    for a in range(135, 406, 30):
        rad = math.radians(a)
        cv.set(round(11.5 + math.cos(rad) * 4.4 - 0.5), round(13.5 + math.sin(rad) * 4.4 - 0.5), SOOT[2])
    for a in (375, 390, 405):
        rad = math.radians(a)
        cv.set(round(11.5 + math.cos(rad) * 4.4 - 0.5), round(13.5 + math.sin(rad) * 4.4 - 0.5), RED[2])
    cv.line(11, 13, 14, 10, RED[3])
    cv.set(14, 10, RED[1])
    cv.set(11, 13, SOOT[4]); cv.set(10, 13, SOOT[2])
    # alavancas (abafador e válvula)
    for x, top in ((21, 7), (25, 10)):
        cv.vcyl(x, top + 2, x + 1, 20, IRON)
        cv.disc(x + 0.5, top + 1, 1.6, RED)
        cv.box(x - 1, 20, x + 2, 22, DARK_IRON)
    # lâmpadas
    cv.disc(8, 23, 1.3, GREEN, ring=False)
    cv.disc(13, 23, 1.3, FIRE[1:] + FIRE[-1:], ring=False)
    cv.set(7, 22, GREEN[0]); cv.set(12, 22, FIRE[0])
    cv.outline()
    return cv.image()


JAVA_ITEMS = {
    "landship": landship,
    "steam_boiler": steam_boiler,
    "reinforced_track": reinforced_track,
    "boilermaker_wrench": boilermaker_wrench,
    "repair_kit": repair_kit,
    "bed_module": bed_module,
    "cargo_module": cargo_module,
    "furnace_module": furnace_module,
    "compactor_module": compactor_module,
}
BEDROCK_ITEMS = {"command_panel": command_panel}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in JAVA_ITEMS.items():
        fn().save(OUT / f"{name}.png")
        print(OUT / f"{name}.png")
    if BEDROCK_OUT.parent.parent.exists():
        BEDROCK_OUT.mkdir(parents=True, exist_ok=True)
        for name, fn in BEDROCK_ITEMS.items():
            fn().save(BEDROCK_OUT / f"{name}.png")
            print(BEDROCK_OUT / f"{name}.png")


if __name__ == "__main__":
    main()
