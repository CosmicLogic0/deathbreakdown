# Death Breakdown gallery graphics (title cards + labeled explainers), 1920x1080.
# Same theme as banners.py: dark red + black, pixel frame, faint "damage log" bars, Press Start 2P titles.
# Every explainer is clearly a drawn graphic: themed background, pixel frame, labels and arrows. The game
# pictures inside them are crops of the real CI screenshots (art/gallery/shots/), never redrawn.
# Run from art/:  python source/gallery.py <press-start-2p .woff>
import math, os, random, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

PIX = sys.argv[1] if len(sys.argv) > 1 else '/tmp/fnt/package/files/press-start-2p-latin-400-normal.woff'
INTER = '/usr/share/fonts/opentype/inter/Inter-Bold.otf'
INTER_SB = '/usr/share/fonts/opentype/inter/Inter-SemiBold.otf'
F = lambda s: ImageFont.truetype(PIX, s)
T = lambda s: ImageFont.truetype(INTER, s)
TS = lambda s: ImageFont.truetype(INTER_SB, s)
W, H, P = 1920, 1080, 8
BG, GRID = (20, 9, 11), (34, 14, 17)
BARS = [(150, 30, 38), (110, 24, 30), (60, 95, 160), (190, 140, 40)]
FRAME1, FRAME2 = ((220, 52, 60), (120, 20, 28)), ((150, 34, 42), (70, 12, 18))
TITLE, SUB, LINE, SMALL, SH = (255, 214, 214), (255, 200, 90), (235, 120, 120), (190, 80, 86), (6, 2, 3)
ARMOR, ENCH, OTHER, HEALTH, DMG = (120, 170, 255), (205, 120, 255), (120, 220, 200), (110, 230, 110), (255, 90, 90)
SHOTS = 'gallery/shots'
OUT = 'gallery'


def base(seed, avoid=()):
    im = Image.new('RGB', (W, H), BG)
    d = ImageDraw.Draw(im)
    random.seed(seed)
    for y in range(0, H, P * 4):
        d.rectangle([0, y, W, y + 1], fill=GRID)
    for y in range(P * 4, H - P * 4, P * 3):
        x = P * 4 + random.randrange(0, 8) * P
        while x < W - P * 8:
            L = random.randrange(3, 16) * P
            if not any(a <= x + L and x <= b and c <= y + P and y <= e for a, c, b, e in avoid):
                col = random.choices(BARS, weights=[5, 5, 2, 1])[0]
                d.rectangle([x, y, x + L - 1, y + P - 1], fill=tuple(int(v * 0.42) for v in col))
            x += L + random.randrange(2, 10) * P
    for t, (c1, c2) in enumerate([FRAME1, FRAME2]):
        o = t * P
        d.rectangle([o, o, W - 1 - o, o + P - 1], fill=c1)
        d.rectangle([o, o, o + P - 1, H - 1 - o], fill=c1)
        d.rectangle([o, H - P - o, W - 1 - o, H - 1 - o], fill=c2)
        d.rectangle([W - P - o, o, W - 1 - o, H - 1 - o], fill=c2)
    return im, d


def stext(d, xy, s, f, col, anchor='la', sh=4):
    d.text((xy[0] + sh, xy[1] + sh), s, font=f, fill=SH, anchor=anchor)
    d.text(xy, s, font=f, fill=col, anchor=anchor)


def heading(d, title, sub=None):
    stext(d, (W // 2, 62), title, F(40), TITLE, 'ma')
    if sub:
        d.text((W // 2, 128), sub, font=T(30), fill=SUB, anchor='ma')


def shot(name):
    return Image.open(os.path.join(SHOTS, name)).convert('RGB')


def panel(im, d, pic, box, border=(220, 52, 60)):
    """Pastes a real screenshot crop into box (x0, y0, x1, y1), scaled to fit, with a pixel border."""
    x0, y0, x1, y1 = box
    bw, bh = x1 - x0, y1 - y0
    s = min(bw / pic.width, bh / pic.height)
    p = pic.resize((max(1, int(pic.width * s)), max(1, int(pic.height * s))), Image.LANCZOS)
    px, py = x0 + (bw - p.width) // 2, y0 + (bh - p.height) // 2
    d.rectangle([px - 14, py - 14, px + p.width + 13, py + p.height + 13], fill=(0, 0, 0))
    d.rectangle([px - 10, py - 10, px + p.width + 9, py + p.height + 9], fill=border)
    d.rectangle([px - 4, py - 4, px + p.width + 3, py + p.height + 3], fill=(0, 0, 0))
    im.paste(p, (px, py))
    return px, py, s


def arrow(d, a, b, col=(255, 200, 90), w=7, head=24):
    d.line([a, b], fill=SH, width=w + 6)
    d.line([a, b], fill=col, width=w)
    ang = math.atan2(b[1] - a[1], b[0] - a[0])
    l = (b[0] - head * math.cos(ang - 0.45), b[1] - head * math.sin(ang - 0.45))
    r = (b[0] - head * math.cos(ang + 0.45), b[1] - head * math.sin(ang + 0.45))
    d.polygon([b, l, r], fill=col, outline=SH)


def tag(d, xy, text, col, f=None, anchor='mm', pad=14):
    f = f or T(30)
    l, t, r, b = d.textbbox(xy, text, font=f, anchor=anchor)
    d.rectangle([l - pad - 3, t - pad + 1, r + pad + 3, b + pad + 7], fill=SH)
    d.rectangle([l - pad, t - pad, r + pad, b + pad], fill=(36, 14, 18), outline=col, width=4)
    d.text(xy, text, font=f, fill=col, anchor=anchor)
    return l - pad, t - pad, r + pad, b + pad


def ring(d, box, col, w=6):
    x0, y0, x1, y1 = box
    d.rectangle([x0 - 2, y0 - 2, x1 + 2, y1 + 2], outline=SH, width=w + 4)
    d.rectangle(box, outline=col, width=w)


def footer(d, text='Death Breakdown · by CosmicLogic'):
    d.text((W // 2, H - 40), text, font=F(14), fill=SMALL, anchor='mm')


def title_card(name, line1, line2, badges, out, seed):
    im, d = base(seed, [(140, 200, 1800, 900)])
    icon = Image.open('icon.png').convert('RGBA').resize((480, 480), Image.NEAREST)
    im.paste(icon, (170, 250), icon)
    size = 58 if len(name) < 16 else 46
    stext(d, (720, 330), name, F(size), TITLE, sh=6)
    d.text((724, 440), line1, font=T(46), fill=SUB)
    d.text((724, 510), line2, font=T(34), fill=LINE)
    x = 724
    for b in badges:
        l, t, r, bb = d.textbbox((0, 0), b, font=T(28))
        w = r - l + 40
        d.rectangle([x + 4, 610 + 4, x + w + 4, 666 + 4], fill=SH)
        d.rectangle([x, 610, x + w, 666], fill=(60, 18, 24), outline=(220, 52, 60), width=4)
        d.text((x + w // 2, 638), b, font=T(28), fill=TITLE, anchor='mm')
        x += w + 18
    d.text((724, 730), 'by CosmicLogic', font=F(18), fill=SMALL)
    im.save(os.path.join(OUT, out))
    print('made', out)


def versions_card(title, sub, cols, rows, notes, out, seed):
    """A version chart: rows of Minecraft versions, a column per loader, tick or 'n/a'."""
    im, d = base(seed, [(120, 40, 1800, 1040)])
    heading(d, title, sub)
    cw, rh = 300, 58
    x0 = (W - (220 + cw * len(cols) + 24)) // 2 + 24
    y0 = 268
    d.rectangle([x0 - 24 + 6, y0 - 70 + 6, x0 + 220 + cw * len(cols) + 6, y0 + rh * len(rows) + 6], fill=SH)
    d.rectangle([x0 - 24, y0 - 70, x0 + 220 + cw * len(cols), y0 + rh * len(rows)], fill=(30, 12, 15), outline=(220, 52, 60), width=5)
    d.text((x0, y0 - 40), 'Minecraft', font=T(30), fill=SUB, anchor='lm')
    for i, c in enumerate(cols):
        d.text((x0 + 220 + cw * i + cw // 2, y0 - 40), c, font=T(30), fill=SUB, anchor='mm')
    for r, (ver, cells) in enumerate(rows):
        y = y0 + rh * r
        if r % 2 == 0:
            d.rectangle([x0 - 20, y + 2, x0 + 216 + cw * len(cols), y + rh - 2], fill=(44, 18, 22))
        d.text((x0, y + rh // 2), ver, font=T(32), fill=TITLE, anchor='lm')
        for i, ok in enumerate(cells):
            cx, cy = x0 + 220 + cw * i + cw // 2, y + rh // 2
            if ok is True:
                d.line([(cx - 16, cy), (cx - 4, cy + 13), (cx + 18, cy - 14)], fill=HEALTH, width=8)
            else:
                d.text((cx, cy), ok or 'n/a', font=TS(24), fill=(150, 110, 112), anchor='mm')
    y = y0 + rh * len(rows) + 52
    for n in notes:
        d.text((W // 2, y), n, font=T(30), fill=LINE, anchor='mm')
        y += 46
    im.save(os.path.join(OUT, out))
    print('made', out)


VERSIONS = ['26.3', '26.2', '26.1', '1.21.11', '1.21.1', '1.20.1', '1.19.2', '1.18.2', '1.16.5', '1.12.2']


def make_static():
    os.makedirs(OUT, exist_ok=True)
    title_card('Death Breakdown', 'See exactly what killed you.',
               'Every hit from your last 10 seconds, and what your armor blocked.',
               ['Forge 1.12.2 - 26.3', 'NeoForge 1.20.1 - 26.3', 'Press K'], 'mod-01-title.png', 41)
    title_card('Death Breakdown Plugin', 'The death recap for Paper servers.',
               'Chat summary, hover details and a book. No client mod needed.',
               ['Paper 1.12.2 - 26.3', 'Vanilla clients', 'Requires LogicLib Plugin'], 'plugin-01-title.png', 42)
    neo = {'26.3', '26.2', '26.1', '1.21.11', '1.21.1', '1.20.1'}
    versions_card('Works where you play', 'One mod, 10 Minecraft versions, Forge and NeoForge',
                  ['Forge', 'NeoForge'], [(v, [True, True if v in neo else 'no NeoForge']) for v in VERSIONS],
                  ['Players without the mod on a modded server still get the chat recap.'], 'mod-06-versions.png', 43)
    versions_card('Works where you play', 'One plugin jar for every Paper version',
                  ['Paper'], [(v, [True]) for v in VERSIONS],
                  ['Players need nothing installed: it all happens in chat and a book.',
                   'Requires LogicLib Plugin 0.2.0 or newer in your plugins folder.'], 'plugin-05-versions.png', 44)


if __name__ == '__main__':
    make_static()


def caption(d, xy, num, text, w=520):
    x, y = xy
    stext(d, (x, y), num, F(34), SUB, 'ma', sh=3)
    lines, cur = [], ''
    for word in text.split():
        t = (cur + ' ' + word).strip()
        if d.textlength(t, font=T(30)) > w and cur:
            lines.append(cur); cur = word
        else:
            cur = t
    lines.append(cur)
    for i, l in enumerate(lines):
        d.text((x, y + 56 + i * 40), l, font=T(30), fill=TITLE, anchor='ma')


def three_steps(title, sub, crops, captions, out, seed):
    """Three real screenshot crops left to right with numbered captions and arrows between them."""
    im, d = base(seed, [(60, 40, 1860, 1040)])
    heading(d, title, sub)
    boxes = [(60, 200, 620, 760), (680, 200, 1240, 760), (1300, 200, 1860, 760)]
    centers = []
    for (pic, box), cap, n in zip(zip(crops, boxes), captions, ['1', '2', '3']):
        px, py, s = panel(im, d, pic, box)
        centers.append(((box[0] + box[2]) // 2, (box[1] + box[3]) // 2))
        caption(d, ((box[0] + box[2]) // 2, 800), n, cap)
    for a, b in zip(boxes, boxes[1:]):
        arrow(d, (a[2] - 2, 480), (b[0] + 2, 480), w=9, head=26)
    footer(d)
    im.save(os.path.join(OUT, out))
    print('made', out)


def grid4(title, sub, items, out, seed, foot='Death Breakdown · by CosmicLogic'):
    """Four real screenshot crops in a 2x2 grid, each with a one-line caption."""
    im, d = base(seed, [(60, 40, 1860, 1040)])
    heading(d, title, sub)
    boxes = [(110, 190, 930, 520), (990, 190, 1810, 520), (110, 600, 930, 930), (990, 600, 1810, 930)]
    for (pic, cap), box in zip(items, boxes):
        panel(im, d, pic, box)
        d.text(((box[0] + box[2]) // 2, box[3] + 38), cap, font=T(30), fill=TITLE, anchor='mm')
    footer(d, foot)
    im.save(os.path.join(OUT, out))
    print('made', out)


def mod_explainers():
    death = shot('mod-death-screen.png')
    recap = shot('mod-recap-lava.png')
    keys = shot('mod-key-binds.png').crop((160, 720, 1760, 1060))
    three_steps('How it works', 'From death to the full story in one click',
                [death, recap, keys],
                ['You die. Click the skull "Death Breakdown" button on the death screen.',
                 'Read every hit from your last 10 seconds, oldest at the top.',
                 'Press K, or type /deathbreakdown, to open it again any time.'],
                'mod-02-how-it-works.png', 51)

    # Read the recap: the real lava recap with its parts labeled
    im, d = base(52, [(60, 40, 1860, 1040)])
    heading(d, 'Read the recap', 'One row per hit. Hover a row for every number.')
    px, py, s = panel(im, d, shot('mod-recap-lava.png'), (380, 240, 1540, 900))
    X = lambda x: int(px + x * s)
    Y = lambda y: int(py + y * s)
    for text, tx, cx in [('Seconds before death', 470, 318), ('Damage type', 800, 630), ('Armor blocked', 1110, 960), ('Health left', 1440, 1520)]:
        col = ARMOR if 'Armor' in text else HEALTH if 'Health' in text else SUB
        l, t, r, b = tag(d, (tx, 200), text, col, T(26))
        arrow(d, ((l + r) // 2, b + 4), (X(cx), Y(296) - 10), col=col, w=5, head=18)
    ring(d, (X(232), Y(664), X(1684), Y(711)), DMG, w=5)
    for text, ty, target, col in [('Your last 5 deaths: < >', 290, (X(63) - 30, Y(55)), SUB),
                                  ('Tip line', 400, (X(670), Y(224)), SUB),
                                  ('Final blow', 660, (X(232) - 8, Y(687)), DMG),
                                  ('Copy as text', 860, (X(500) - 8, Y(1015)), SUB)]:
        l, t, r, b = tag(d, (195, ty), text, col, T(26))
        arrow(d, (r + 4, (t + b) // 2), target, col=col, w=5, head=18)
    l, t, r, b = tag(d, (1180, 975), 'Settings: every option', SUB, T(26))
    arrow(d, ((l + r) // 2 - 120, t - 4), (X(960), Y(1050)), w=5, head=18)
    footer(d)
    im.save(os.path.join(OUT, 'mod-03-read-the-recap.png'))
    print('made mod-03-read-the-recap.png')

    # What your gear blocked: three real recaps, three kinds of protection
    im, d = base(53, [(60, 40, 1860, 1040)])
    heading(d, 'See what your gear blocked', 'Armor and each Protection enchantment, in hearts')
    crops = {'mod-recap-creeper.png': (232, 0, 1690, 640), 'mod-recap-lava.png': (232, 160, 1690, 840),
             'mod-recap-hover.png': (232, 0, 1690, 820)}
    items = [('mod-recap-creeper.png', 'Creeper', 'Blast Protection IV', 'enchants stopped 3.71\u2665', ENCH),
             ('mod-recap-lava.png', 'Lava', 'Fire Protection IV', 'enchants stopped 4.84\u2665', ENCH),
             ('mod-recap-hover.png', 'Zombie with an iron sword', 'Iron armor', 'armor stopped 9.84\u2665', ARMOR)]
    for i, (f, what, gear, num, col) in enumerate(items):
        x0 = 70 + i * 620
        panel(im, d, shot(f).crop(crops[f]), (x0, 230, x0 + 540, 560))
        d.text((x0 + 270, 640), what, font=T(34), fill=TITLE, anchor='mm')
        tag(d, (x0 + 270, 720), gear, col, T(32))
        d.text((x0 + 270, 800), num, font=T(32), fill=col, anchor='mm')
    d.text((W // 2, 905), 'The numbers come from the game\'s own armor and enchantment math, hit by hit.', font=T(30), fill=LINE, anchor='mm')
    footer(d)
    im.save(os.path.join(OUT, 'mod-04-gear-blocked.png'))
    print('made mod-04-gear-blocked.png')

    grid4('New in 1.1.0', 'Your recent deaths, a settings screen, history and help',
          [(shot('mod-recap-creeper.png').crop((0, 0, 1920, 640)), 'Your last 5 deaths: flip with < and >, plus a tip'),
           (shot('mod-settings.png'), 'Settings screen: every option explained'),
           (shot('mod-history.png'), '/db history: click a death to open it'),
           (shot('mod-help.png'), '/db help: every command, explained')],
          'mod-05-new-in-1-1.png', 54)


def plugin_explainers():
    im, d = base(61, [(60, 40, 1860, 1040)])
    heading(d, 'How it works on your server', 'Players see it in chat, no mod needed')
    chat = shot('plugin-death-chat.png').crop((0, 840, 1000, 970))
    px, py, sc = panel(im, d, chat, (170, 190, 1750, 380))
    stext(d, (110, (py + py + chat.height * sc) / 2), '1', F(40), SUB, 'mm', sh=3)
    d.text((W // 2, py + chat.height * sc + 46), 'After each death: a one-line summary in chat', font=T(32), fill=TITLE, anchor='mm')
    hover = shot('plugin-chat-hover.png').crop((380, 420, 1050, 1080))
    book = shot('plugin-book-mobs.png').crop((560, 0, 1360, 760))
    a1 = panel(im, d, hover, (250, 500, 860, 945))
    a2 = panel(im, d, book, (1060, 500, 1670, 945))
    stext(d, (180, 720), '2', F(40), SUB, 'mm', sh=3)
    stext(d, (1740, 720), '3', F(40), SUB, 'mm', sh=3)
    d.text((555, 985), 'Hover [details] to see every hit', font=T(30), fill=TITLE, anchor='mm')
    d.text((1365, 985), 'Click [Open recap] for the book', font=T(30), fill=TITLE, anchor='mm')
    footer(d, 'Death Breakdown Plugin · by CosmicLogic')
    im.save(os.path.join(OUT, 'plugin-02-how-it-works.png'))
    print('made plugin-02-how-it-works.png')

    im, d = base(62, [(60, 40, 1860, 1040)])
    heading(d, 'The recap book', 'A clean summary first, then one page per hit')
    p1 = shot('plugin-book-mobs.png').crop((600, 0, 1320, 760))
    p2 = shot('plugin-book-mobs-page2.png').crop((600, 0, 1320, 760))
    a = panel(im, d, p1, (420, 200, 960, 960))
    b = panel(im, d, p2, (1000, 200, 1540, 960))
    px, py, s = a
    for text, y, ty, col in [('What killed you', 285, 330, SUB), ('Total damage', 357, 490, DMG), ('Armor + enchants', 411, 650, ARMOR), ('A tip (new)', 501, 810, SUB)]:
        l, t, r, bb = tag(d, (230, ty), text, col, T(28), anchor='mm')
        arrow(d, (r + 4, ty), (px + 110 * s, py + y * s), col=col, w=5, head=16)
    px, py, s = b
    for text, y, ty, col in [('One page per hit', 141, 380, SUB), ('Every number', 430, 620, ARMOR)]:
        l, t, r, bb = tag(d, (1720, ty), text, col, T(28), anchor='mm')
        arrow(d, (l - 4, ty), (px + 600 * s, py + y * s), col=col, w=5, head=16)
    d.text((1720, 690), 'for that hit', font=T(28), fill=ARMOR, anchor='mm')
    footer(d, 'Death Breakdown Plugin · by CosmicLogic')
    im.save(os.path.join(OUT, 'plugin-03-recap-book.png'))
    print('made plugin-03-recap-book.png')

    grid4('New in 1.1.0', 'History, a tip line, copy, and help', [
        (shot('plugin-history.png'), '/db history: your last 5 deaths, click to open'),
        (shot('plugin-book-mobs.png').crop((600, 240, 1320, 600)), 'A tip in the hover and the book'),
        (shot('plugin-death-chat.png').crop((0, 840, 1000, 970)), '[copy] puts it on your clipboard (1.15+)'),
        (shot('plugin-help.png'), '/db help: every command, explained')],
        'plugin-04-new-in-1-1.png', 63, 'Death Breakdown Plugin · by CosmicLogic')


if __name__ == '__main__':
    mod_explainers()
    plugin_explainers()
