#!/usr/bin/env python3
"""Gera o modelo 3D do landship (geometria Bedrock / GeckoLib), as animações e a textura.

Saídas (no mesmo diretório deste script):
  landship.geo.json         geometria (abre no Blockbench: File > Open Model)
  landship.animation.json   animações (GeckoLib / Bedrock)
  landship.png              textura 256x256
  landship_glowmask.png     partes que brilham (fornalha, lanterna) — usado pelo GeckoLib

Unidades: 16 px = 1 bloco. Origem no chão, centro do veículo. Frente = -Z (norte).
Pegada: 47 x 47 px com as saias blindadas (≈ 2,9 blocos; a caixa de colisão é 2,9). Casco até y=18; cabine e chaminé acima disso são visuais.

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


# A geometria Bedrock tem o eixo X espelhado em relação ao mundo (o Blockbench e o GeckoLib
# invertem X ao carregar). Com a frente em -Z, a ESQUERDA do piloto fica em X positivo aqui.
LEFT = 1

bone("root")
bone("body", "root", pivot=(0, 8, 0))

# Regra deste arquivo: duas faces nunca podem ficar no mesmo plano, viradas para o mesmo lado e
# sobrepostas (isso cintila no jogo). `check_coplanar_faces()` no fim falha a geração se acontecer.

# --- Casco e deque ----------------------------------------------------------
bone("hull", "body", pivot=(0, 8, 0))
cube("hull", (-16, 3, -21), (32, 13, 42), "iron_plate")
cube("hull", (-16, 4, -23), (32, 12, 2), "iron_plate")                                  # placa frontal
cube("hull", (-16, 4, 21), (32, 12, 2), "iron_plate")                                   # placa traseira
cube("hull", (-8, 13, -23.6), (16, 1.5, 0.6), "copper")                                # friso frontal
cube("hull", (-1.5, 5, 23), (3, 2, 2), "dark_iron")                                     # gancho de reboque
for sx in (-1, 1):                                                                      # faróis
    x0 = 9.5 if sx > 0 else -13.5
    cube("hull", (x0, 9.5, -24), (4, 4, 1), "dark_iron")
    cube("hull", (x0 + 0.5, 10, -24.4), (3, 3, 0.4), {"*": "brass", "north": "firebox"})
bone("deck", "body", pivot=(0, 17, 0))
cube("deck", (-23, 16, -23), (46, 2, 46), {"*": "iron_plate", "up": "planks"})
cube("deck", (-23.5, 15.5, -23.5), (47, 1, 1), "dark_iron")                             # cantoneiras
cube("deck", (-23.5, 15.5, 22.5), (47, 1, 1), "dark_iron")
cube("deck", (-23.5, 15.5, -22.5), (1, 1, 45), "dark_iron")
cube("deck", (22.5, 15.5, -22.5), (1, 1, 45), "dark_iron")

# --- Esteiras -----------------------------------------------------------------
WHEEL_Y = 6.0
WHEEL_Z = (-13.5, -4.5, 4.5, 13.5)
for side, sx in (("left", LEFT), ("right", -LEFT)):
    tb = bone(f"track_{side}", "body", pivot=(sx * 19.5, 7, 0))

    def tc(origin, size, mat, b=tb, pivot=None, rotation=None):
        if sx < 0:
            origin, size = mirror_x(origin, size)
            if pivot:
                pivot = (-pivot[0], pivot[1], pivot[2])
        cube(b, origin, size, mat, pivot=pivot, rotation=rotation)

    # laço da esteira: corridas de cima/baixo, pontas e cantos chanfrados
    tc((16, 11, -19), (7, 3, 38), {"*": "tread_v", "up": "tread_h", "down": "tread_h"})
    tc((16, 1, -19), (7, 2, 38), {"*": "tread_v", "up": "tread_h", "down": "tread_h"})
    tc((16, 3, -22), (7, 8, 3), {"*": "tread_v", "north": "tread_h"})
    tc((16, 3, 19), (7, 8, 3), {"*": "tread_v", "south": "tread_h"})
    for zc in (-20.5, 20.5):
        for yc in (2.5, 11.5):
            tc((16.05, yc - 1.4, zc - 1.4), (6.9, 2.8, 2.8), "tread_v",
               pivot=(19.5, yc, zc), rotation=(45, 0, 0))
    # saia blindada: cobre a metade de cima da esteira pelo lado de fora
    tc((22.6, 8, -20), (1, 6.6, 40), {"*": "iron_plate", "up": "dark_iron"})
    tc((22.4, 13.6, -21), (1.4, 1.2, 42), "dark_iron")

    # garras (animadas: as de cima andam para a frente, as de baixo para trás)
    top = bone(f"tread_{side}_top", tb, pivot=(sx * 19.5, 14, 0))
    bot = bone(f"tread_{side}_bottom", tb, pivot=(sx * 19.5, 0, 0))
    for z in range(-18, 18, 4):
        tc((16.2, 14, z), (6.2, 0.8, 1.5), "dark_iron", b=top)
        tc((16.2, 0.2, z), (6.2, 0.8, 1.5), "dark_iron", b=bot)

    # rodas de apoio: octógono feito de 4 barras (cada uma com largura própria, para não cintilar),
    # raio 3,8 — cabe entre as corridas da esteira; a saia esconde a metade de cima
    for i, zc in enumerate(WHEEL_Z):
        wb = bone(f"wheel_{side}_{i}", tb, pivot=(sx * 19.5, WHEEL_Y, zc))
        tc((17.0, WHEEL_Y - 3.5, zc - 1.5), (5.0, 7, 3), "dark_iron", b=wb)
        tc((17.1, WHEEL_Y - 1.5, zc - 3.5), (4.8, 3, 7), "dark_iron", b=wb)
        tc((17.2, WHEEL_Y - 1.5, zc - 3.5), (4.6, 3, 7), "dark_iron", b=wb,
           pivot=(19.5, WHEEL_Y, zc), rotation=(45, 0, 0))
        tc((17.3, WHEEL_Y - 1.5, zc - 3.5), (4.4, 3, 7), "dark_iron", b=wb,
           pivot=(19.5, WHEEL_Y, zc), rotation=(-45, 0, 0))
        tc((22.0, WHEEL_Y - 1, zc - 1), (0.5, 2, 2), "brass", b=wb)                   # cubo
        tc((22.0, WHEEL_Y - 1, zc - 1), (0.4, 2, 2), "brass", b=wb,
           pivot=(22.2, WHEEL_Y, zc), rotation=(45, 0, 0))

# --- Cabine -------------------------------------------------------------------
ROOF_Y = 43
bone("cabin", "body", pivot=(0, 18, -8))
cube("cabin", (-6, 18, -23), (12, 7, 2), {"*": "iron_plate", "south": "planks"})       # painel frontal
for x in (-8, 6):
    for z in (-23, 6):
        cube("cabin", (x, 18, z), (2, ROOF_Y - 18, 2), "dark_iron")                    # colunas
cube("cabin", (-9.5, ROOF_Y, -24.5), (19, 2, 34), {"*": "copper", "up": "planks"})     # teto
cube("cabin", (-7.5, ROOF_Y + 2, -22), (15, 1, 29), "copper")                          # cumeeira
cube("cabin", (-6, ROOF_Y - 2, -22.8), (12, 1, 1), "brass")                            # verga da frente
for x in (-8.5, 7.5):
    cube("cabin", (x, 27, -21), (1, 1, 27), "brass")                                   # corrimãos
cube("cabin", (-4, 18, -14), (8, 3, 6), "leather")                                     # banco do piloto
cube("cabin", (-4, 21, -9), (8, 7, 1), "leather")
cube("cabin", (-7, 18, -4), (14, 3, 6), "leather")                                     # banco dos passageiros
cube("cabin", (-7, 21, 1.5), (14, 6, 1), "leather")
cube("cabin", (-2, 18, -21), (4, 9, 2.6), {"*": "copper", "up": "brass"})                # pedestal do leme
cube("cabin", (-0.5, 26.5, -18.4), (1, 1, 1.0), "dark_iron")                             # eixo do leme
bone("lantern", "cabin", pivot=(0, ROOF_Y, -25))
cube("lantern", (-0.5, ROOF_Y - 1, -25.6), (1, 1, 1.3), "dark_iron")                   # suporte
cube("lantern", (-1, ROOF_Y - 4, -26.5), (2, 3, 2), {"*": "firebox", "up": "brass", "down": "brass"})
cube("lantern", (-1.4, ROOF_Y - 1.3, -26.9), (2.8, 0.5, 2.8), "brass")

# leme de navio, em pé e virado para o piloto (ele fica atrás, em +Z); gira em Z ao esterçar
HELM_C = (0.0, 27.0, -17.4)
HELM_R = 5.0
bone("helm", "cabin", pivot=HELM_C)
seg = 2 * HELM_R * 0.4142                                                               # lado do octógono
for k in range(8):
    t = 1.0 if k % 2 == 0 else 1.2                                                     # espessuras alternadas
    cube("helm", (-seg / 2, HELM_C[1] + HELM_R - 1, HELM_C[2] - t / 2), (seg, 1, t), "planks",
         pivot=HELM_C, rotation=(0, 0, k * 45))
for k in range(4):
    th = 0.8 + 0.04 * k
    cube("helm", (-(HELM_R + 1.6), HELM_C[1] - th / 2, HELM_C[2] - th / 2), (2 * (HELM_R + 1.6), th, th),
         "planks", pivot=HELM_C, rotation=(0, 0, k * 45 + 22.5))
cube("helm", (-1.2, HELM_C[1] - 1.2, HELM_C[2] - 1.1), (2.4, 2.4, 2.2), "brass")       # cubo central

# --- Caldeira -----------------------------------------------------------------
bone("boiler", "body", pivot=(0, 24, 15.5),
     locators={"steam_vent": (3.5, 35, 12.5), "firebox_front": (0, 23, 25)})
# corpo arredondado = 3 caixas sobrepostas com todas as faces em planos diferentes
cube("boiler", (-7, 20, 8.2), (14, 9, 14.6), "boiler_green")
cube("boiler", (-6, 19, 8.1), (12, 11, 14.8), "boiler_green")
cube("boiler", (-5, 18, 8.0), (10, 13, 15.0), "boiler_green")
for z in (9.5, 14.5, 19.5):                                                            # cintas de cobre
    cube("boiler", (-7.5, 19.5, z), (15, 10, 1), "copper")
    cube("boiler", (-6.5, 18.5, z + 0.15), (13, 12, 0.7), "copper")
    cube("boiler", (-5.5, 18.1, z + 0.3), (11, 13.4, 0.4), "copper")
cube("boiler", (-4, 20.5, 7.4), (8, 7, 0.7), "dark_iron")                              # porta da caixa de fumaça
cube("boiler", (-3, 19.5, 7.5), (6, 9, 0.65), "dark_iron")
cube("boiler", (2.6, 23.5, 7.1), (0.8, 1.5, 0.5), "brass")                             # trinco
cube("boiler", (-2.5, 29, 7.2), (5, 5, 0.9), {"*": "brass", "north": "gauge"})         # manômetro
cube("boiler", (2.5, 31, 11.5), (2, 3, 2), "brass")                                    # válvula de segurança
cube("boiler", (2, 34, 11), (3, 1, 3), "brass")
bone("gauge_needle", "boiler", pivot=(0, 31.5, 7.1))
cube("gauge_needle", (-0.25, 31.5, 7.0), (0.5, 2, 0.2), "red_fabric")
bone("firebox_door", "boiler", pivot=(-5, 23, 23.5))
cube("firebox_door", (-5.2, 18.5, 22.95), (10.4, 9, 1.2), {"*": "dark_iron", "south": "firebox"})
cube("firebox_door", (3, 22, 24.15), (1, 2, 0.8), "brass")                             # puxador
bone("whistle", "boiler", pivot=(0, 31, 10.5))
cube("whistle", (-0.5, 31, 10), (1, 4, 1), "brass")
cube("whistle", (-1, 35, 9.5), (2, 2.5, 2), "brass")
bone("chimney", "boiler", pivot=(0, 31, 19), locators={"smoke": (0, 54, 19)})
cube("chimney", (-3, 31, 16), (6, 2, 6), "dark_iron")
cube("chimney", (-2, 33, 17), (4, 18, 4), "soot")
cube("chimney", (-2.5, 46, 16.5), (5, 1, 5), "copper")
cube("chimney", (-3, 51, 16), (6, 2, 6), "dark_iron")

# --- Motor: cilindros verticais com pistões animados --------------------------
for side, sx in (("left", LEFT), ("right", -LEFT)):
    eb = bone(f"engine_{side}", "body", pivot=(sx * 4, 18, 5))

    def ec(origin, size, mat, b=eb):
        if sx < 0:
            origin, size = mirror_x(origin, size)
        cube(b, origin, size, mat)

    ec((2.5, 18, 3.5), (3, 8, 3), "copper")
    ec((2, 26, 3), (4, 1, 4), "brass")
    pb = bone(f"piston_{side}", eb, pivot=(sx * 4, 27, 5))
    ec((3.5, 27, 4.5), (1, 5, 1), "brass", b=pb)
    ec((3, 32, 4), (2, 1, 2), "dark_iron", b=pb)

# --- Encaixes de módulo -------------------------------------------------------
# células 3x3 do deque: colunas L/R (x = ±15,5), linhas F/M/R (z = -15,5 / 0 / 15,5)
SLOTS = {
    "slot_fl": (LEFT * 15.5, -15.5), "slot_fr": (-LEFT * 15.5, -15.5),
    "slot_ml": (LEFT * 15.5, 0.0), "slot_mr": (-LEFT * 15.5, 0.0),
    "slot_rl": (LEFT * 15.5, 15.5), "slot_rr": (-LEFT * 15.5, 15.5),
}
MODULE_KINDS = ("bed", "cargo", "furnace")
for name, (cx, cz) in SLOTS.items():
    out = -1 if cx < 0 else 1  # lado de fora do veículo
    bone(name, "body", pivot=(cx, 18, cz))

    b = bone(f"{name}_bed", name, pivot=(cx, 18, cz))
    cube(b, (cx - 6.5, 18, cz - 6.5), (13, 3, 13), "planks")
    cube(b, (cx - 6, 20.9, cz - 6), (12, 2.1, 12), "red_fabric")
    cube(b, (cx - 5.5, 23, cz - 5.5), (11, 1.5, 4), "white_wool")
    cube(b, (cx - 6.5, 21, cz - 6.5), (13, 5, 1), "planks")                            # cabeceira
    cube(b, (cx - 6.5, 21, cz + 5.5), (13, 3, 1), "planks")                            # pé
    cube(b, (cx - 6.25, 22.6, cz + 1), (12.5, 0.6, 4.6), "white_wool")                  # dobra do lençol

    b = bone(f"{name}_cargo", name, pivot=(cx, 18, cz))
    cube(b, (cx - 6, 18, cz - 6), (12, 11, 12), "crate")
    cube(b, (cx - 6.5, 29, cz - 6.5), (13, 2, 13), {"*": "dark_iron", "up": "planks"})
    cube(b, (cx - 6.4, 21.5, cz - 6.4), (12.8, 1, 12.8), "dark_iron")
    cube(b, (cx - 6.3, 25.5, cz - 6.3), (12.6, 1, 12.6), "dark_iron")
    cube(b, ((cx + 6.4) if out > 0 else (cx - 7.0), 24, cz - 1), (0.6, 2.5, 2), "brass")  # trinco

    b = bone(f"{name}_furnace", name, pivot=(cx, 18, cz))
    cube(b, (cx - 6, 18, cz - 6), (12, 12, 12), "brick")
    cube(b, (cx - 6.5, 30, cz - 6.5), (13, 1, 13), "iron_plate")
    door_x = (cx + 6) if out > 0 else (cx - 6.6)
    cube(b, (door_x, 19.5, cz - 3), (0.6, 6, 6), {"*": "dark_iron", "east": "firebox", "west": "firebox"})
    cube(b, (cx - 1.5, 31, cz - 1.5), (3, 7, 3), "soot")
    cube(b, (cx - 2, 38, cz - 2), (4, 1, 4), "dark_iron")

# compactador frontal (encaixe da frente, fica fora da pegada): rolo redondo entre dois braços
ROLL_Y, ROLL_Z, ROLL_R = 4.5, -31.0, 4.5
bone("slot_front", "body", pivot=(0, 6, -23))
bone("slot_front_compactor", "slot_front", pivot=(0, 6, -23))
cube("slot_front_compactor", (-22.5, 7, -25), (45, 2, 2), "dark_iron")                 # travessa
for sx in (-1, 1):
    def fc(origin, size, mat):
        if sx < 0:
            origin, size = mirror_x(origin, size)
        cube("slot_front_compactor", origin, size, mat)
    fc((21.4, 3.5, -31.5), (1.6, 3, 8.5), "iron_plate")                                  # braço até o eixo
    fc((21.4, 6.5, -24), (1.6, 0.5, 1), "dark_iron")                                     # junta com a travessa
    fc((21.6, 6.5, -30.5), (1.2, 5, 1), "dark_iron")                                     # suporte do para-lama
cube("slot_front_compactor", (-23, 11.5, -35.5), (46, 1, 9), {"*": "iron_plate", "up": "dark_iron"})  # para-lama
bone("roller", "slot_front_compactor", pivot=(0, ROLL_Y, ROLL_Z))
# tambor octogonal (4 barras, larguras diferentes para não cintilar), raio ≈ 4,7
for i, (half_w, rot) in enumerate(((20.0, 0), (19.9, 0), (19.8, 45), (19.7, -45))):
    tall = i == 0
    h, d = (2 * ROLL_R, 3.8) if tall else (3.8, 2 * ROLL_R)
    cube("roller", (-half_w, ROLL_Y - h / 2, ROLL_Z - d / 2), (2 * half_w, h, d), "tread_v",
         pivot=(0, ROLL_Y, ROLL_Z) if rot else None, rotation=(rot, 0, 0) if rot else None)
for sx in (-1, 1):                                                                      # tampas e eixo
    x0 = 20.0 if sx > 0 else -21.0
    cube("roller", (x0, ROLL_Y - 3.5, ROLL_Z - 1.5), (1.0, 7, 3), "dark_iron")
    cube("roller", (x0 + 0.05, ROLL_Y - 1.5, ROLL_Z - 3.5), (0.9, 3, 7), "dark_iron")
    hx = 21.0 if sx > 0 else -21.4
    cube("roller", (hx, ROLL_Y - 1, ROLL_Z - 1), (0.4, 2, 2), "brass")

# locais dos assentos (usados como referência pelo código)
bone("seats", "body", pivot=(0, 21, 0),
     locators={"seat_pilot": (0, 21, -11), "seat_passenger_0": (3.5, 21, -1),
               "seat_passenger_1": (-3.5, 21, -1)})


def check_coplanar_faces():
    """Falha se duas faces sem rotação ficarem no mesmo plano, mesmo sentido e sobrepostas."""
    rotated = {b["name"] for b in bones if b.get("rotation")}
    parent = {b["name"]: b.get("parent") for b in bones}

    def in_rotated(name):
        while name:
            if name in rotated:
                return True
            name = parent[name]
        return False

    def variant(name):  # módulos alternativos do mesmo encaixe nunca aparecem juntos
        for k in MODULE_KINDS:
            if name.endswith("_" + k):
                return name[: -len(k) - 1], k
        return None

    faces = []
    for b in bones:
        if in_rotated(b["name"]):
            continue
        for c in b.get("cubes", []):
            if "rotation" in c:
                continue
            o, s = c["origin"], c["size"]
            for ax in range(3):
                others = [a for a in range(3) if a != ax]
                rect = [(o[a], o[a] + s[a]) for a in others]
                faces.append((ax, o[ax], -1, rect, b["name"]))
                faces.append((ax, o[ax] + s[ax], 1, rect, b["name"]))
    problems = []
    for i in range(len(faces)):
        ax, pos, sg, r1, n1 = faces[i]
        for j in range(i + 1, len(faces)):
            ax2, pos2, sg2, r2, n2 = faces[j]
            if ax != ax2 or sg != sg2 or abs(pos - pos2) > 1e-6:
                continue
            v1, v2 = variant(n1), variant(n2)
            if v1 and v2 and v1[0] == v2[0] and v1[1] != v2[1]:
                continue
            ov = 1.0
            for (a0, a1), (b0, b1) in zip(r1, r2):
                ov *= max(0.0, min(a1, b1) - max(a0, b0))
            if ov > 1e-6:
                problems.append(f"{n1} x {n2}: eixo {'xyz'[ax]}={pos} área {ov:.2f}")
    if problems:
        raise SystemExit("Faces coplanares (cintilam no jogo):\n  " + "\n  ".join(problems))


check_coplanar_faces()

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
# Animações (todas em laço sem emenda: rodas repetem a cada 45°, garras a cada 4 px)
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
