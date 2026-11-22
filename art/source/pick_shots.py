"""Copies the gallery screenshots (from the gallery-shots branch, downloaded to a folder) into art/gallery/shots.
Chat pictures (taken at GUI scale 3) are cropped to the chat corner and scaled up to 1920x1080 so the text fills
the frame. Everything else is used as taken. Usage: python3 pick_shots.py <downloaded folder> [mod|plugin|all]"""
import os, shutil, sys
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'gallery', 'shots')
AS_IS = {
    'mod': ['fight', 'death-screen', 'recap-mobs', 'recap-lava', 'recap-creeper', 'recap-hover', 'settings', 'key-binds', 'reopen'],
    'plugin': ['fight', 'death-chat', 'chat-hover', 'book-mobs', 'book-mobs-page2', 'book-lava', 'book-creeper'],
}
CHAT = {'mod': ['chat-line', 'help', 'history'], 'plugin': ['help', 'history']}
BOX = (0, 504, 1024, 1080)  # the chat corner at GUI scale 3 (16:9)


def main(src, which):
    os.makedirs(OUT, exist_ok=True)
    for side in (['mod', 'plugin'] if which == 'all' else [which]):
        for n in AS_IS[side]:
            f = os.path.join(src, f'{side}-{n}.png')
            if os.path.exists(f):
                shutil.copy(f, os.path.join(OUT, f'{side}-{n}.png'))
        for n in CHAT[side]:
            f = os.path.join(src, f'{side}-{n}.png')
            if os.path.exists(f):
                Image.open(f).convert('RGB').crop(BOX).resize((1920, 1080), Image.LANCZOS).save(os.path.join(OUT, f'{side}-{n}.png'))
    print('picked into', os.path.abspath(OUT))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else 'all')
