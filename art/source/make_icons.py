# Generates the 4 Death Breakdown icon options as 32x32 pixel art scaled to 512x512.
import random, math, sys
from PIL import Image
OUT_DIR = sys.argv[1] if len(sys.argv) > 1 else "."
N = 32
def hx(h, a=255):
    h = h.lstrip('#'); return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)
OUTL = hx('#141210')
BONE, BONEL, BONED = hx('#e9e3cf'), hx('#fbf7ea'), hx('#b9b19a')
RED, REDL, REDD = hx('#d8343a'), hx('#ff6b6b'), hx('#8e1b22')
GOLD, GOLDL = hx('#f2b632'), hx('#ffe08a')
BLUE, BLUEL = hx('#4f8fe0'), hx('#9cc7ff')
IRON, IRONL, IROND = hx('#c9ced6'), hx('#f1f3f6'), hx('#7d848f')
PURP = hx('#a46ee8')

def background(seed, base):
    random.seed(seed)
    img = Image.new('RGBA', (N, N)); px = img.load()
    for y in range(N):
        for x in range(N):
            v = random.choice([-10, -5, 0, 0, 5, 8])
            px[x, y] = tuple(max(0, min(255, c + v)) for c in base[:3]) + (255,)
    for i in range(N):  # bevelled frame
        for (x, y, f) in ((i, 0, 1.6), (0, i, 1.6), (i, N - 1, 0.55), (N - 1, i, 0.55)):
            px[x, y] = tuple(max(0, min(255, int(c * f))) for c in base[:3]) + (255,)
    return img

class Sprite:
    def __init__(self): self.p = {}
    def put(self, x, y, c): self.p[(x, y)] = c
    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1): self.put(x, y, c)
    def art(self, ox, oy, rows, pal):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in pal: self.put(ox + i, oy + j, pal[ch])
    def draw(self, img, outline=True):
        px = img.load()
        if outline:
            for (x, y) in list(self.p):
                for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                    q = (x + dx, y + dy)
                    if q not in self.p and 0 <= q[0] < N and 0 <= q[1] < N: px[q] = OUTL
        for (x, y), c in self.p.items():
            if 0 <= x < N and 0 <= y < N: px[x, y] = c

SKULL = [
    "...BBBBBB...",
    ".BBLLLLLLBB.",
    "BBLLLBBBBBBB",
    "BLLBBBBBBBBB",
    "BLBBBBBBBBBD",
    "BBKKKBBKKKBD",
    "BBKKKBBKKKBD",
    "BBKKRBBKKRBD",
    "BBBBBKKBBBBD",
    ".BBBBKKBBBD.",
    "..BDBDBDBD..",
    "..BKBKBKBD..",
    "..BBBBBBBD..",
]
SKPAL = {'B': BONE, 'L': BONEL, 'D': BONED, 'K': OUTL, 'R': REDD}

def heart(s, ox, oy, size, cracked=False):
    pts = set()
    for j in range(size):
        for i in range(size):
            x = (i + 0.5) / size * 2.6 - 1.3
            y = 1.25 - (j + 0.5) / size * 2.6
            if (x*x + y*y - 1) ** 3 - x*x * y**3 <= 0: pts.add((i, j))
    for (i, j) in pts:
        c = RED
        if (i - 1, j - 1) not in pts or (i, j - 1) not in pts: c = REDL
        if (i + 1, j) not in pts or (i, j + 1) not in pts: c = REDD
        s.put(ox + i, oy + j, c)
    s.put(ox + size // 4, oy + size // 4, hx('#ffffff'))
    if cracked:
        cx = size // 2; path = [(0, 0), (1, 1), (0, 2), (-1, 3), (0, 4), (1, 5), (0, 6), (0, 7), (-1, 8), (0, 9), (0, 10)]
        for dx, dy in path:
            q = (ox + cx + dx, oy + 2 + dy)
            if (q[0] - ox, q[1] - oy) in pts: s.put(q[0], q[1], OUTL)
    return pts

def bars(s, ox, oy, lens, cols, gap=3, h=2):
    for k, (L, c) in enumerate(zip(lens, cols)):
        y = oy + k * gap
        s.rect(ox, y, ox + L - 1, y + h - 1, c)
        s.rect(ox, y, ox + L - 1, y, tuple(min(255, int(v * 1.25)) for v in c[:3]) + (255,))

def shield(s, ox, oy):
    rows = ["IIIIIIIII", "ILLLLLLLI", "ILIIIIIDI", "ILIIIIIDI", "ILIIIIIDI", "ILIIIIIDI", ".ILIIIDI.", ".IDIIIDI.", "..IDDDI..", "...III..."]
    s.art(ox, oy, rows, {'I': IRON, 'L': IRONL, 'D': IROND})
    s.rect(ox + 4, oy + 2, ox + 4, oy + 7, BLUE)   # enchant glint stripe
    s.put(ox + 3, oy + 4, BLUEL); s.put(ox + 5, oy + 4, BLUEL)

def option_a():  # Skull + hit list
    img = background(1, hx('#2b2f3a')); s = Sprite()
    s.art(3, 9, SKULL, SKPAL)
    bars(s, 18, 9, [10, 8, 6, 11], [RED, RED, BLUE, GOLD], gap=4)
    s.draw(img); return img

def option_b():  # Cracked heart + shield
    img = background(2, hx('#2a2230')); s = Sprite()
    heart(s, 3, 4, 20, cracked=True)
    t = Sprite(); shield(t, 19, 17)
    s.draw(img); t.draw(img); return img

def option_c():  # Tombstone with recap lines
    img = background(3, hx('#24302a')); s = Sprite()
    G, GL, GD = hx('#9aa0a6'), hx('#c4c9ce'), hx('#6c7177')
    for y in range(5, 29):
        for x in range(7, 25):
            r = 9
            if y < 5 + r and (x - 15.5) ** 2 + (y - (5 + r)) ** 2 > r * r: continue
            c = G
            if x <= 8 or y <= 6: c = GL
            if x >= 23: c = GD
            s.put(x, y, c)
    bars(s, 10, 12, [12, 9, 7, 11], [RED, RED, BLUE, GOLD], gap=3)
    s.rect(4, 28, 27, 29, hx('#5d8a3a')); s.rect(4, 28, 27, 28, hx('#7fb04c'))  # grass
    s.draw(img); return img

def option_d():  # Magnifier over skull
    img = background(4, hx('#30261f')); s = Sprite()
    s.art(6, 6, SKULL, SKPAL)
    r = Sprite()
    cx, cy, R = 13, 13, 8
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if R - 1.5 <= d <= R + 0.5: r.put(x, y, GOLD if x + y < cx + cy else hx('#b9851c'))
    for k in range(7):
        for w in range(2): r.put(20 + k + w, 20 + k, hx('#6b3a1e'))
    s.draw(img); r.draw(img)
    px = img.load()  # lens tint
    for y in range(N):
        for x in range(N):
            if math.hypot(x - cx, y - cy) < R - 1.5:
                c = px[x, y]; px[x, y] = (min(255, c[0] + 18), min(255, c[1] + 22), min(255, c[2] + 34), 255)
    px[cx - 4, cy - 4] = hx('#ffffff')
    return img

opts = {'a-skull-hitlist': option_a, 'b-cracked-heart-shield': option_b, 'c-tombstone-recap': option_c, 'd-magnifier-skull': option_d}
sheet = Image.new('RGBA', (4 * 256 + 5 * 16, 256 + 32), hx('#1b1b1b'))
for k, (name, fn) in enumerate(opts.items()):
    im = fn().resize((512, 512), Image.NEAREST)
    im.save(f"{OUT_DIR}/icon-{name}.png")
    sheet.paste(im.resize((256, 256), Image.NEAREST), (16 + k * 272, 16))
sheet.save(f"{OUT_DIR}/icon-options.png")
print("ok")
