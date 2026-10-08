#!/usr/bin/env python3
"""Gera o modelo 3D do landship (geometria Bedrock / GeckoLib), as animações e a textura.

Saídas (no mesmo diretório deste script):
  landship.geo.json         geometria (abre no Blockbench: File > Open Model)
  landship.animation.json   animações (GeckoLib / Bedrock)
  landship.png              textura 256x256
  landship_glowmask.png     partes que brilham (fornalha, lanterna) — usado pelo GeckoLib

Unidades: 16 px = 1 bloco. Origem no chão, centro do veículo. Frente = -Z (norte).
Pegada: 46 x 46 px (≈ 2,9 blocos). Casco até y=18; cabine e chaminé acima disso são visuais.

Uso: python3 gen_landship.py
"""
import json
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent
TEX = 256
REG = 64  # cada material ocupa uma região 64x64 do atlas

MATERIALS = [
    "planks", "iron_plate", "dark_iron", "copper",
    "brass", "tread_h", "tread_v", "brick",
    "red_fabric", "white_wool", "crate", "firebox",
    "gauge", "leather", "boiler_green", "soot",
]


def region(mat):
    i = MATERIALS.index(mat)
    return (i % 4) * REG, (i // 4) * REG


# ----------------------------------------------------------------------------
# Geometria
# ----------------------------------------------------------------------------
bones = []
_bone_index = {}
_cube_counter = [0]


def bone(name, parent=None, pivot=(0, 0, 0), rotation=None, locators=None):
    b = {"name": name, "pivot": [float(v) for v in pivot]}
    if parent:
        b["parent"] = parent
    if rotation:
        b["rotation"] = [float(v) for v in rotation]
    if locators:
        b["locators"] = {k: [float(v) for v in p] for k, p in locators.items()}
    b["cubes"] = []
    bones.append(b)
    _bone_index[name] = b
    return name


def _face_dims(face, size):
    w, h, d = size
    return {
        "north": (w, h), "south": (w, h),
        "east": (d, h), "west": (d, h),
        "up": (w, d), "down": (w, d),
    }[face]


def cube(bone_name, origin, size, mat, pivot=None, rotation=None, inflate=None):
    """mat: nome de material, ou dict {face: material, '*': padrão}."""
    _cube_counter[0] += 1
    rnd = random.Random(_cube_counter[0] * 7919)
    uv = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        m = mat if isinstance(mat, str) else mat.get(face, mat.get("*"))
        fw, fh = _face_dims(face, size)
        rx, ry = region(m)
        if m == "gauge":
            ox, oy = 0, 0  # mostrador desenhado no canto da região
        else:
            ox = rnd.randint(0, max(0, int(REG - fw - 1)))
            oy = rnd.randint(0, max(0, int(REG - fh - 1)))
        u, v = rx + ox, ry + oy
        uw, vh = fw, fh
        if face == "down":  # convenção Bedrock: face de baixo com V invertido
            v, vh = v + fh, -fh
        uv[face] = {"uv": [u, v], "uv_size": [uw, vh]}
    c = {"origin": [float(x) for x in origin], "size": [float(x) for x in size], "uv": uv}
    if rotation:
        c["pivot"] = [float(x) for x in (pivot or (0, 0, 0))]
        c["rotation"] = [float(x) for x in rotation]
    if inflate:
        c["inflate"] = inflate
    _bone_index[bone_name]["cubes"].append(c)


def mirror_x(origin, size):
    """Espelha um cubo no eixo X (lado esquerdo ↔ direito)."""
    return (-(origin[0] + size[0]), origin[1], origin[2]), size


bone("root")
bone("body", "root", pivot=(0, 8, 0))

# --- Casco e deque ----------------------------------------------------------
bone("hull", "body", pivot=(0, 8, 0))
cube("hull", (-16, 3, -21), (32, 13, 42), "iron_plate")
cube("hull", (-16, 4, -23), (32, 12, 2), {"*": "iron_plate", "north": "iron_plate"})   # placa frontal
cube("hull", (-16, 4, 21), (32, 12, 2), "iron_plate")                                  # placa traseira
cube("hull", (-14, 9, -24), (28, 2, 1), "copper")                                      # friso frontal
bone("deck", "body", pivot=(0, 17, 0))
cube("deck", (-23, 16, -23), (46, 2, 46), {"*": "iron_plate", "up": "planks"})
for z in (-23.5, 22.5):                                                                # cantoneiras
    cube("deck", (-23.5, 15.5, z), (47, 1, 1), "dark_iron")
for x in (-23.5, 22.5):
    cube("deck", (x, 15.5, -23), (1, 1, 46), "dark_iron")

# --- Esteiras -----------------------------------------------------------------
for side, sx in (("left", -1), ("right", 1)):
    tb = bone(f"track_{side}", "body", pivot=(sx * 19.5, 7, 0))

    def tc(origin, size, mat, b=tb, **kw):
        if sx < 0:
            origin, size = mirror_x(origin, size)
        cube(b, origin, size, mat, **kw)

    # laço da esteira (corridas de cima/baixo e as pontas)
    tc((16, 11, -20), (7, 3, 40), {"*": "tread_v", "up": "tread_h", "down": "tread_h"})
    tc((16, 1, -20), (7, 2, 40), {"*": "tread_v", "up": "tread_h", "down": "tread_h"})
    tc((16, 3, -23), (7, 8, 3), {"*": "tread_v", "north": "tread_h"})
    tc((16, 3, 20), (7, 8, 3), {"*": "tread_v", "south": "tread_h"})
    tc((16, 1.5, -22), (7, 2, 2), "tread_v", pivot=(sx * 19.5, 2.5, -21), rotation=(45, 0, 0))
    tc((16, 11.5, -22), (7, 2, 2), "tread_v", pivot=(sx * 19.5, 12.5, -21), rotation=(45, 0, 0))
    tc((16, 1.5, 20), (7, 2, 2), "tread_v", pivot=(sx * 19.5, 2.5, 21), rotation=(45, 0, 0))
    tc((16, 11.5, 20), (7, 2, 2), "tread_v", pivot=(sx * 19.5, 12.5, 21), rotation=(45, 0, 0))
    # placa interna (esconde o vão entre esteira e casco)
    tc((15.5, 3, -20), (0.5, 8, 40), "dark_iron")

    # garras (animadas: as de cima andam para a frente, as de baixo para trás)
    top = bone(f"tread_{side}_top", tb, pivot=(sx * 19.5, 14, 0))
    bot = bone(f"tread_{side}_bottom", tb, pivot=(sx * 19.5, 0, 0))
    for z in range(-18, 18, 4):
        tc((16, 14, z), (7, 1, 1.5), "dark_iron", b=top)
        tc((16, 0, z), (7, 1, 1.5), "dark_iron", b=bot)

    # rodas de apoio (octógono = 2 caixas, uma girada 45°)
    for i, zc in enumerate((-15, -5, 5, 15)):
        wb = bone(f"wheel_{side}_{i}", tb, pivot=(sx * 19.5, 7, zc))
        tc((17, 3, zc - 4), (5, 8, 8), {"*": "dark_iron", "east": "brass", "west": "brass"}, b=wb)
        tc((17, 3, zc - 4), (5, 8, 8), "dark_iron", b=wb, pivot=(sx * 19.5, 7, zc), rotation=(45, 0, 0))
        tc((22, 5.5, zc - 1.5), (0.8, 3, 3), "brass", b=wb)  # cubo da roda

# --- Cabine -------------------------------------------------------------------
bone("cabin", "body", pivot=(0, 18, -8))
cube("cabin", (-8, 18, -23), (16, 8, 2), {"*": "iron_plate", "south": "planks"})       # painel frontal
for x in (-8, 6):
    for z in (-23, 6):
        cube("cabin", (x, 18, z), (2, 22, 2), "dark_iron")                             # colunas
cube("cabin", (-9.5, 40, -24.5), (19, 2, 34), {"*": "copper", "up": "planks"})         # teto
cube("cabin", (-7.5, 42, -22), (15, 1, 29), "copper")                                  # cumeeira
for x in (-8.5, 7.5):
    cube("cabin", (x, 27, -21), (1, 1, 27), "brass")                                   # corrimãos
cube("cabin", (-4, 18, -14), (8, 3, 6), "leather")                                     # banco do piloto
cube("cabin", (-4, 21, -9), (8, 6, 1), "leather")
cube("cabin", (-7, 18, -4), (14, 3, 6), "leather")                                     # banco dos passageiros
cube("cabin", (-7, 21, 1.5), (14, 6, 1), "leather")
cube("cabin", (-0.5, 18, -20.5), (1, 9, 1), "dark_iron")                               # coluna de direção
bone("lantern", "cabin", pivot=(0, 38, -25))
cube("lantern", (-1, 36.5, -26), (2, 3, 2), {"*": "firebox", "up": "brass", "down": "brass"})
cube("lantern", (-1.5, 39.5, -26.5), (3, 0.5, 3), "brass")

bone("helm", "cabin", pivot=(0, 27, -19.5), rotation=(-55, 0, 0))                      # leme
cube("helm", (-4, 26.5, -20), (8, 1, 1), "planks")
cube("helm", (-0.5, 23, -20), (1, 8, 1), "planks")
cube("helm", (-4, 30.5, -20), (8, 1, 1), "brass")
cube("helm", (-4, 22.5, -20), (8, 1, 1), "brass")
cube("helm", (-4.5, 22.5, -20), (1, 9, 1), "brass")
cube("helm", (3.5, 22.5, -20), (1, 9, 1), "brass")
cube("helm", (-1, 26, -20.5), (2, 2, 2), "copper")

# --- Caldeira -----------------------------------------------------------------
bone("boiler", "body", pivot=(0, 24, 15.5),
     locators={"steam_vent": (4, 35, 13), "firebox_front": (0, 23, 25)})
cube("boiler", (-7, 20, 8), (14, 9, 15), "boiler_green")
cube("boiler", (-5, 18, 8), (10, 13, 15), "boiler_green")
for z in (9, 14.5, 20):                                                                # cintas de cobre
    cube("boiler", (-7.5, 19.5, z), (15, 10, 1), "copper")
    cube("boiler", (-5.5, 18, z), (11, 13.5, 1), "copper")
cube("boiler", (-4, 20, 7), (8, 6, 1), "dark_iron")                                    # porta da caixa de fumaça
cube("boiler", (-2.5, 26.5, 7), (5, 5, 1), {"*": "brass", "north": "gauge"})           # manômetro
cube("boiler", (2.5, 31, 11.5), (2, 3, 2), "brass")                                    # válvula de segurança
cube("boiler", (2, 34, 11), (3, 1, 3), "brass")
bone("gauge_needle", "boiler", pivot=(0, 29, 6.8))
cube("gauge_needle", (-0.25, 29, 6.6), (0.5, 2, 0.3), "red_fabric")
bone("firebox_door", "boiler", pivot=(-5, 23, 24))
cube("firebox_door", (-5, 18.5, 23), (10, 9, 1.5), {"*": "dark_iron", "south": "firebox"})
cube("firebox_door", (3, 22, 24.5), (1, 2, 1), "brass")                                # puxador
bone("whistle", "boiler", pivot=(0, 31, 10.5))
cube("whistle", (-0.5, 31, 10), (1, 4, 1), "brass")
cube("whistle", (-1, 35, 9.5), (2, 2.5, 2), "brass")
bone("chimney", "boiler", pivot=(0, 31, 19), locators={"smoke": (0, 54, 19)})
cube("chimney", (-3, 31, 16), (6, 2, 6), "dark_iron")
cube("chimney", (-2, 33, 17), (4, 18, 4), "soot")
cube("chimney", (-3, 51, 16), (6, 2, 6), "dark_iron")
cube("chimney", (-2.5, 46, 16.5), (5, 1, 5), "copper")

# --- Motor: cilindros verticais com pistões animados --------------------------
for side, sx in (("left", -1), ("right", 1)):
    eb = bone(f"engine_{side}", "body", pivot=(sx * 5.5, 18, 5.5))

    def ec(origin, size, mat, b=eb):
        if sx < 0:
            origin, size = mirror_x(origin, size)
        cube(b, origin, size, mat)

    ec((4, 18, 4), (3, 9, 3), "copper")
    ec((3.5, 26, 3.5), (4, 1, 4), "brass")
    pb = bone(f"piston_{side}", eb, pivot=(sx * 5.5, 27, 5.5))
    ec((5, 27, 5), (1, 5, 1), "brass", b=pb)
    ec((4.5, 32, 4.5), (2, 1, 2), "dark_iron", b=pb)

# --- Encaixes de módulo -------------------------------------------------------
# células 3x3 do deque: colunas L/R (x = ∓15,5), linhas F/M/R (z = -15,5 / 0 / 15,5)
SLOTS = {
    "slot_fl": (-15.5, -15.5), "slot_fr": (15.5, -15.5),
    "slot_ml": (-15.5, 0.0), "slot_mr": (15.5, 0.0),
    "slot_rl": (-15.5, 15.5), "slot_rr": (15.5, 15.5),
}
for name, (cx, cz) in SLOTS.items():
    out = -1 if cx < 0 else 1  # lado de fora do veículo
    bone(name, "body", pivot=(cx, 18, cz))

    b = bone(f"{name}_bed", name, pivot=(cx, 18, cz))
    cube(b, (cx - 6.5, 18, cz - 6.5), (13, 3, 13), "planks")
    cube(b, (cx - 6, 21, cz - 6), (12, 2, 12), {"*": "red_fabric"})
    cube(b, (cx - 5.5, 23, cz - 5.5), (11, 1.5, 4), "white_wool")
    cube(b, (cx - 6.5, 21, cz - 6.5), (13, 5, 1), "planks")
    cube(b, (cx - 6.5, 21, cz + 5.5), (13, 3, 1), "planks")

    b = bone(f"{name}_cargo", name, pivot=(cx, 18, cz))
    cube(b, (cx - 6, 18, cz - 6), (12, 11, 12), "crate")
    cube(b, (cx - 6.5, 29, cz - 6.5), (13, 2, 13), {"*": "dark_iron", "up": "planks"})
    cube(b, (cx - 6.5, 21.5, cz - 6.5), (13, 1, 13), "dark_iron")
    cube(b, (cx + 6 if out > 0 else cx - 7, 25, cz - 1), (1, 2, 2), "brass")                # trinco

    b = bone(f"{name}_furnace", name, pivot=(cx, 18, cz))
    cube(b, (cx - 6, 18, cz - 6), (12, 12, 12), "brick")
    cube(b, (cx - 6.5, 30, cz - 6.5), (13, 1, 13), "iron_plate")
    door_x = cx + 6 if out > 0 else cx - 6.5
    cube(b, (door_x, 19.5, cz - 3), (0.5, 6, 6), {"*": "dark_iron", "east": "firebox", "west": "firebox"})
    cube(b, (cx - 1.5, 31, cz - 1.5), (3, 7, 3), "soot")
    cube(b, (cx - 2, 38, cz - 2), (4, 1, 4), "dark_iron")

# compactador frontal (encaixe da frente, fica fora da pegada)
bone("slot_front", "body", pivot=(0, 6, -23))
bone("slot_front_compactor", "slot_front", pivot=(0, 6, -23))
for x in (-20, 18):
    cube("slot_front_compactor", (x, 4, -31), (2, 3, 8), "iron_plate")
cube("slot_front_compactor", (-20, 7, -26), (40, 2, 2), "dark_iron")
cube("slot_front_compactor", (-20, 9.5, -33), (40, 1, 4), "iron_plate")             # para-lama do rolo
bone("roller", "slot_front_compactor", pivot=(0, 4.5, -31))
cube("roller", (-21, 0, -35.5), (42, 9, 9), {"*": "tread_v", "east": "brass", "west": "brass"})
cube("roller", (-20.5, 0, -35.5), (41, 9, 9), "tread_v", pivot=(0, 4.5, -31), rotation=(45, 0, 0))

# locais dos assentos (usados como referência pelo código)
bone("seats", "body", pivot=(0, 21, 0),
     locators={"seat_pilot": (0, 21, -11), "seat_passenger_0": (-3.5, 21, -1),
               "seat_passenger_1": (3.5, 21, -1)})
for b in bones:
    if not b["cubes"]:
        del b["cubes"]

geo = {
    "format_version": "1.16.0",
    "minecraft:geometry": [{
        "description": {
            "identifier": "geometry.vapor_trilhos.landship",
            "texture_width": TEX, "texture_height": TEX,
            "visible_bounds_width": 6, "visible_bounds_height": 5,
            "visible_bounds_offset": [0, 1.5, 0],
        },
        "bones": bones,
    }],
}

# ----------------------------------------------------------------------------
# Animações (todas em laço sem emenda: octógono repete a cada 45°, garras a cada 4 px)
# ----------------------------------------------------------------------------
def track_anim(side, direction):
    rot = 90 * direction        # frente = -Z: topo da roda vai para a frente
    shift = -8 * direction
    bones_ = {f"wheel_{side}_{i}": {"rotation": {"0.0": [0, 0, 0], "0.5": [rot, 0, 0]}} for i in range(4)}
    bones_[f"tread_{side}_top"] = {"position": {"0.0": [0, 0, 0], "0.5": [0, 0, shift]}}
    bones_[f"tread_{side}_bottom"] = {"position": {"0.0": [0, 0, 0], "0.5": [0, 0, -shift]}}
    return {"loop": True, "animation_length": 0.5, "bones": bones_}


def piston_anim(length, amp, body_shake):
    half = length / 2
    a = {
        "loop": True, "animation_length": length,
        "bones": {
            "piston_left": {"position": {"0.0": [0, 0, 0], f"{half}": [0, amp, 0], f"{length}": [0, 0, 0]}},
            "piston_right": {"position": {"0.0": [0, amp, 0], f"{half}": [0, 0, 0], f"{length}": [0, amp, 0]}},
        },
    }
    if body_shake:
        q = length / 4
        a["bones"]["body"] = {"position": {"0.0": [0, 0, 0], f"{q}": [0, body_shake, 0],
                                           f"{half}": [0, 0, 0], f"{3 * q}": [0, body_shake, 0],
                                           f"{length}": [0, 0, 0]}}
    return a


animations = {
    "format_version": "1.8.0",
    "animations": {
        "animation.landship.track_left.forward": track_anim("left", 1),
        "animation.landship.track_left.reverse": track_anim("left", -1),
        "animation.landship.track_right.forward": track_anim("right", 1),
        "animation.landship.track_right.reverse": track_anim("right", -1),
        "animation.landship.engine.idle": piston_anim(1.0, 1.0, 0.05),
        "animation.landship.engine.working": piston_anim(0.4, 2.0, 0.12),
        "animation.landship.compactor.roll": {
            "loop": True, "animation_length": 0.5,
            "bones": {"roller": {"rotation": {"0.0": [0, 0, 0], "0.5": [90, 0, 0]}}},
        },
        "animation.landship.boiler.vent": {
            "animation_length": 1.0,
            "bones": {
                "boiler": {"position": {"0.0": [0, 0, 0], "0.05": [0.3, 0, 0], "0.1": [-0.3, 0, 0],
                                        "0.15": [0.3, 0, 0], "0.2": [-0.3, 0, 0], "0.25": [0, 0, 0]}},
                "whistle": {"scale": {"0.0": [1, 1, 1], "0.1": [1.3, 1.3, 1.3], "0.6": [1.3, 1.3, 1.3],
                                      "1.0": [1, 1, 1]}},
            },
        },
        "animation.landship.firebox.open": {
            "animation_length": 0.4, "loop": "hold_on_last_frame",
            "bones": {"firebox_door": {"rotation": {"0.0": [0, 0, 0], "0.4": [0, -100, 0]}}},
        },
    },
}


# ----------------------------------------------------------------------------
# Textura procedural
# ----------------------------------------------------------------------------
def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def shade(c, f):
    return clamp(tuple(v * f for v in c))


img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
glow = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
px, gpx = img.load(), glow.load()
R = random.Random(42)


def fill(mat, fn):
    rx, ry = region(mat)
    for y in range(REG):
        for x in range(REG):
            px[rx + x, ry + y] = clamp(fn(x, y)) + (255,)


def noise(base, amt):
    n = R.uniform(-amt, amt)
    return tuple(v + n for v in base)


def planks_fn(base, vertical=False):
    tints = [R.uniform(0.85, 1.12) for _ in range(16)]

    def f(x, y):
        a, b = (x, y) if not vertical else (y, x)
        row = b // 8
        c = shade(base, tints[row % 16])
        if b % 8 == 7:
            return shade(c, 0.55)
        if (a + row * 13) % 32 == 0:
            return shade(c, 0.7)
        if b % 8 in (2, 5) and (a * 7 + row * 3) % 11 == 0:
            c = shade(c, 0.88)
        if (a + row * 13) % 32 in (2, 29) and b % 8 == 3:
            return (70, 70, 74)  # pregos
        return noise(c, 6)
    return f


fill("planks", planks_fn((126, 88, 54)))


def riveted(base, panel=16):
    def f(x, y):
        bx, by = x % panel, y % panel
        if bx == 0 or by == 0:
            return shade(base, 0.68)
        if bx == panel - 1 or by == panel - 1:
            return shade(base, 1.12)
        if (bx in (2, panel - 3)) and (by % 4 == 2):
            return shade(base, 1.35)
        if (bx in (2, panel - 3)) and (by % 4 == 3):
            return shade(base, 0.6)
        return noise(base, 7)
    return f


fill("iron_plate", riveted((118, 122, 128)))
fill("boiler_green", riveted((44, 84, 58), panel=8))
fill("dark_iron", lambda x, y: noise((54, 55, 60), 6) if (x * 3 + y * 5) % 29 else (72, 72, 78))


def copper_fn(x, y):
    c = noise((184, 106, 62), 10)
    if (x * 13 + y * 7) % 37 < 3 and R.random() < 0.6:
        return noise((84, 158, 136), 8)  # pátina
    if y % 16 == 0:
        return shade(c, 0.75)
    return c


fill("copper", copper_fn)
fill("brass", lambda x, y: noise(shade((200, 164, 74), 1.0 + 0.12 * ((y % 6) - 3) / 3), 6))


def tread(horizontal):
    def f(x, y):
        t = (y if horizontal else x) % 4
        c = (88, 84, 78) if t < 2 else (42, 40, 38)
        if t == 0:
            c = (110, 104, 96)
        o = x if horizontal else y
        if t == 1 and o % 7 == 3:
            return (140, 132, 118)  # pinos
        return noise(c, 5)
    return f


fill("tread_h", tread(True))
fill("tread_v", tread(False))


def brick_fn(x, y):
    row = y // 5
    off = 4 if row % 2 else 0
    if y % 5 == 4 or (x + off) % 8 == 7:
        return noise((176, 168, 156), 6)
    tint = ((x + off) // 8 * 31 + row * 17) % 5
    return noise(shade((150, 62, 46), 0.85 + tint * 0.06), 8)


fill("brick", brick_fn)
fill("red_fabric", lambda x, y: shade((168, 40, 40), 1.25) if y % 12 in (0, 1)
     else noise(shade((150, 34, 36), 1.0 if (x + y) % 2 else 0.92), 5))
fill("white_wool", lambda x, y: noise((232, 228, 218), 8))


def crate_fn(x, y):
    if x % 16 in (0, 15) or y % 16 in (0, 15):
        return noise((66, 66, 70), 5)
    return planks_fn((172, 128, 78), vertical=True)(x, y)


fill("crate", crate_fn)
fill("leather", lambda x, y: (196, 170, 120) if (x % 6 == 0 and y % 2 == 0) else noise((112, 66, 40), 7))
fill("soot", lambda x, y: noise((40 + (y % 9) * 1.2, 38, 36), 7))

# fornalha: grade com brasas (também vai para o glowmask)
rx, ry = region("firebox")
for y in range(REG):
    for x in range(REG):
        if x % 4 == 0 or y % 6 == 0:
            c = (48, 46, 46, 255)
        else:
            heat = R.random()
            c = clamp((255, 120 + heat * 110, 30 + heat * 50)) + (255,)
            gpx[rx + x, ry + y] = c
        px[rx + x, ry + y] = c

# manômetro 5x5 no canto da região (o resto da região é latão)
fill("gauge", lambda x, y: noise((200, 164, 74), 5))
gx, gy = region("gauge")
dial = [
    "BBBBB",
    "BWWRB",
    "BWNWB",
    "BWWWB",
    "BBBBB",
]
pal = {"B": (150, 118, 50), "W": (236, 230, 210), "R": (200, 40, 30), "N": (30, 30, 30)}
for y, row in enumerate(dial):
    for x, ch in enumerate(row):
        px[gx + x, gy + y] = pal[ch] + (255,)

# ----------------------------------------------------------------------------
(OUT / "landship.geo.json").write_text(json.dumps(geo, indent=1) + "\n")
(OUT / "landship.animation.json").write_text(json.dumps(animations, indent=1) + "\n")
img.save(OUT / "landship.png")
glow.save(OUT / "landship_glowmask.png")
n_cubes = sum(len(b.get("cubes", [])) for b in bones)
print(f"{len(bones)} bones, {n_cubes} cubes -> {OUT}")
