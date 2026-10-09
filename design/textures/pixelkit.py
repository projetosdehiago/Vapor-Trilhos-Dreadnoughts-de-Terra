"""Kit de pixel art 32x32 usado por gen_item_textures.py: rampas de cor por material, formas
e contorno colorido automático."""
import math
from PIL import Image

SIZE = 32


def hexc(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# rampas: [brilho, claro, médio, escuro, mais escuro]
IRON = [hexc(h) for h in ("f4f7fa", "cdd3db", "a3aab5", "747c87", "4a5059")]
DARK_IRON = [hexc(h) for h in ("8e939a", "6a7079", "4e535b", "373b41", "24272b")]
WOOD = [hexc(h) for h in ("e3bd7f", "c49558", "9d713f", "74512b", "4c341b")]
COPPER = [hexc(h) for h in ("ffd3ad", "f0a06b", "cc7444", "9b4f2b", "64301a")]
BRASS = [hexc(h) for h in ("fff6c4", "f6d465", "d6a93a", "a37a22", "6a4c12")]
GREEN = [hexc(h) for h in ("c7ecc9", "86c995", "56a36b", "3a7650", "234a33")]
RED = [hexc(h) for h in ("ffb0a0", "ee6b58", "c8413a", "902a26", "5a1917")]
SOOT = [hexc(h) for h in ("7d7872", "5c5751", "433f3a", "2e2b28", "1c1a18")]
CLOTH = [hexc(h) for h in ("ffffff", "f1ece2", "d9d0c0", "b3a792", "857a67")]
BRICK = [hexc(h) for h in ("e8957a", "c86a4d", "a24c34", "773322", "4c1f15")]
MORTAR = [hexc(h) for h in ("d8d1c6", "bdb4a6", "9e9587", "7a7266", "564f46")]
STONE = [hexc(h) for h in ("e2e2de", "c4c4bf", "a2a29d", "7b7b77", "53534f")]
FIRE = [hexc(h) for h in ("fffbd0", "fff07a", "ffc63d", "ff7b1d", "c7480f")]
GLASS = [hexc(h) for h in ("f0fdff", "c8eef6", "94cfdc", "5f9fb2", "3b6d7d")]
PAPER = [hexc(h) for h in ("fffdf3", "f4ecd6", "ddd0ae", "b4a47e", "7f714f")]


class Canvas:
    def __init__(self):
        self.px = [[None] * SIZE for _ in range(SIZE)]

    def set(self, x, y, c):
        if 0 <= x < SIZE and 0 <= y < SIZE and c is not None:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < SIZE and 0 <= y < SIZE else None

    # --- formas -------------------------------------------------------------------------
    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def box(self, x0, y0, x1, y1, ramp, bevel=1):
        """Retângulo com borda de cima/esquerda clara e de baixo/direita escura."""
        self.rect(x0, y0, x1, y1, ramp[2])
        for b in range(bevel):
            for x in range(x0 + b, x1 + 1 - b):
                self.set(x, y0 + b, ramp[1] if b else ramp[0])
                self.set(x, y1 - b, ramp[3])
            for y in range(y0 + b, y1 + 1 - b):
                self.set(x0 + b, y, ramp[1])
                self.set(x1 - b, y, ramp[3] if b else ramp[4])
        self.set(x0, y0, ramp[0])

    def hcyl(self, x0, y0, x1, y1, ramp, highlight=0.25):
        """Cilindro deitado: faixas horizontais com brilho perto do topo."""
        h = y1 - y0
        for y in range(y0, y1 + 1):
            t = (y - y0) / max(1, h)
            if t < highlight - 0.12:
                c = ramp[1]
            elif t < highlight + 0.08:
                c = ramp[0]
            elif t < 0.5:
                c = ramp[1]
            elif t < 0.75:
                c = ramp[2]
            elif t < 0.92:
                c = ramp[3]
            else:
                c = ramp[4]
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def vcyl(self, x0, y0, x1, y1, ramp, highlight=0.3):
        """Cilindro em pé: faixas verticais com brilho à esquerda."""
        w = x1 - x0
        for x in range(x0, x1 + 1):
            t = (x - x0) / max(1, w)
            if t < highlight - 0.15:
                c = ramp[1]
            elif t < highlight + 0.1:
                c = ramp[0]
            elif t < 0.55:
                c = ramp[1]
            elif t < 0.8:
                c = ramp[2]
            else:
                c = ramp[3]
            for y in range(y0, y1 + 1):
                self.set(x, y, c)

    def disc(self, cx, cy, r, ramp, ring=True):
        """Disco com luz de cima à esquerda e aro escuro."""
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                dx, dy = x - cx, y - cy
                d = math.hypot(dx, dy)
                if d <= r + 0.15:
                    if ring and d > r - 0.85:
                        c = ramp[3] if dx + dy > -r * 0.4 else ramp[2]
                    else:
                        v = -(dx + dy) / (r * 1.6)
                        c = ramp[0] if v > 0.45 else ramp[1] if v > 0.1 else ramp[2] if v > -0.35 else ramp[3]
                    self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c, width=1):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(steps + 1):
            x = round(x0 + (x1 - x0) * i / steps)
            y = round(y0 + (y1 - y0) * i / steps)
            for w in range(width):
                self.set(x + w, y, c)

    def draw(self, x, y, rows, palette):
        """Desenho à mão: cada letra vira uma cor de 'palette' ('.' = não mexe)."""
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch != "." and ch != " ":
                    self.set(x + i, y + j, palette[ch])

    # --- acabamento -----------------------------------------------------------------------
    def outline(self, strength=0.42):
        """Contorno colorido: cada borda vazia pega a cor vizinha bem escurecida."""
        add = []
        for y in range(SIZE):
            for x in range(SIZE):
                if self.px[y][x] is None:
                    neigh = [self.get(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
                    neigh = [n for n in neigh if n is not None and n[3] == 255]
                    if neigh:
                        r = sum(n[0] for n in neigh) / len(neigh)
                        g = sum(n[1] for n in neigh) / len(neigh)
                        b = sum(n[2] for n in neigh) / len(neigh)
                        add.append((x, y, (int(r * strength), int(g * strength), int(b * strength), 255)))
        for x, y, c in add:
            self.px[y][x] = c

    def image(self):
        im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        for y in range(SIZE):
            for x in range(SIZE):
                if self.px[y][x]:
                    im.putpixel((x, y), self.px[y][x])
        return im


def rivet(cv, x, y, ramp=IRON):
    cv.set(x, y, ramp[0])
    cv.set(x + 1, y + 1, ramp[3])
