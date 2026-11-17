# Death Breakdown banners: header (mod + plugin) and donation banner. Theme: death/combat, dark red + black,
# bold pixel font, a "damage log" of hit bars in the background. Same layout as every CosmicLogic mod.
# Font: Press Start 2P (OFL), from npm @fontsource/press-start-2p. Run from the art/ folder:
#   python source/banners.py /path/to/press-start-2p-latin-400-normal.woff
import random, sys
from PIL import Image, ImageDraw, ImageFont
FONT = sys.argv[1] if len(sys.argv) > 1 else '/tmp/fnt/package/files/press-start-2p-latin-400-normal.woff'
F = lambda s: ImageFont.truetype(FONT, s)
P = 8
BG, GRID = (20, 9, 11), (34, 14, 17)
BARS = [(150, 30, 38), (110, 24, 30), (60, 95, 160), (190, 140, 40)]   # damage, damage, armor, final blow
FRAME1, FRAME2 = ((220, 52, 60), (120, 20, 28)), ((150, 34, 42), (70, 12, 18))
TITLE, SUB, LINE, SMALL, SH = (255, 214, 214), (255, 200, 90), (235, 120, 120), (190, 80, 86), (6, 2, 3)

def base(w, h, seed, avoid=()):
    im = Image.new("RGB", (w, h), BG); d = ImageDraw.Draw(im); random.seed(seed)
    for y in range(0, h, P * 4):
        d.rectangle([0, y, w, y + 1], fill=GRID)
    # faint "damage log": rows of hit bars, like the recap screen
    for y in range(P * 3, h - P * 3, P * 3):
        x = P * 3 + random.randrange(0, 8) * P
        while x < w - P * 6:
            L = random.randrange(3, 14) * P
            if not any(a <= x + L and x <= b and c <= y + P and y <= e for a, c, b, e in avoid):
                col = random.choices(BARS, weights=[5, 5, 2, 1])[0]
                col = tuple(int(v * 0.55) for v in col)
                d.rectangle([x, y, x + L - 1, y + P - 1], fill=col)
            x += L + random.randrange(2, 9) * P
    for t, (c1, c2) in enumerate([FRAME1, FRAME2]):   # pixel frame
        o = t * P
        d.rectangle([o, o, w - 1 - o, o + P - 1], fill=c1); d.rectangle([o, o, o + P - 1, h - 1 - o], fill=c1)
        d.rectangle([o, h - P - o, w - 1 - o, h - 1 - o], fill=c2); d.rectangle([w - P - o, o, w - 1 - o, h - 1 - o], fill=c2)
    return im, d

def shadow_text(d, xy, s, f, col):
    d.text((xy[0] + 4, xy[1] + 4), s, font=f, fill=SH); d.text(xy, s, font=f, fill=col)

icon = Image.open("icon.png").convert("RGBA")

def header(name, sub, line, out, seed):
    w, h = 1200, 400
    im, d = base(w, h, seed, [(30, 30, 370, 370), (380, 70, 1170, 335)])
    ic = icon.resize((304, 304), Image.NEAREST); im.paste(ic, (48, 48), ic)
    shadow_text(d, (400, 90), name, F(46 if len(name) < 16 else 34), TITLE)
    shadow_text(d, (402, 190), sub, F(19), SUB)
    shadow_text(d, (402, 240), line, F(16), LINE)
    d.text((402, 300), "by CosmicLogic", font=F(14), fill=SMALL)
    im.save(out)

header("Death Breakdown", "See exactly what killed you.", "Every hit. Armor + enchants blocked.", "header.png", 31)
header("Death Breakdown Plugin", "The death recap for Paper servers", "Paper 1.12.2 to 26.3. No mod needed.", "header-plugin.png", 32)

# Donation banner (same layout as every CosmicLogic mod)
w, h = 1200, 340
HS = 20
HCX, HCY = 168, 168
im, d = base(w, h, 33, [(40, 40, 300, 300), (310, 35, 1170, 300)])
heart = ["..XX...XX..", ".XXXX.XXXX.", "XXXXXXXXXXX", "XXXXXXXXXXX", "XXXXXXXXXXX", ".XXXXXXXXX.", "..XXXXXXX..", "...XXXXX...", "....XXX....", ".....X....."]
s = HS; ox, oy = HCX - 11 * s // 2, HCY - 10 * s // 2
for r, row in enumerate(heart):
    for c, ch in enumerate(row):
        if ch == "X":
            col = (216, 52, 58) if not (r < 3 and c in (2, 3)) else (255, 140, 140)
            if c + 1 >= len(row) or row[c + 1] != "X":
                col = (140, 26, 34)
            d.rectangle([ox + c * s, oy + r * s, ox + c * s + s - 1, oy + r * s + s - 1], fill=col)
shadow_text(d, (330, 50), "Help keep this mod alive", F(30), TITLE)
lines = ["Death Breakdown is free. Donations pay for", "new Minecraft versions, new features and", "clearer death recaps."]
for i, l in enumerate(lines):
    d.text((332, 110 + i * 30), l, font=F(15), fill=(240, 196, 196))
bx, by = 332, 222
d.rectangle([bx + 6, by + 6, bx + 366, by + 66], fill=SH); d.rectangle([bx, by, bx + 360, by + 60], fill=(255, 196, 57))
d.rectangle([bx, by, bx + 360, by + 7], fill=(255, 225, 130)); d.rectangle([bx, by + 53, bx + 360, by + 60], fill=(200, 140, 20))
d.text((bx + 30, by + 21), "Donate with PayPal", font=F(17), fill=(20, 40, 110))
d.text((bx + 400, by + 22), "Any amount helps. Thank you!", font=F(15), fill=LINE)
im.save("donate.png")
print("ok")
