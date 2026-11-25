"""Generates the textures of the add-on's industry blocks and items.

Blocks are colour variants of Data Center's own machine textures, read from the Data Center jar that
Gradle downloaded (see generate-network-textures.py). Items are drawn here, pixel by pixel.
Output: src/main/resources/assets/data_center_addon/textures/{block,item}/industry/<name>.png

usage: python tools/generate-industry-textures.py [path to the Data Center jar]
"""
import importlib.util
import os
import random
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('network_textures', os.path.join(HERE, 'generate-network-textures.py'))
network = importlib.util.module_from_spec(spec)
spec.loader.exec_module(network)
pngio = network.pngio

ROOT = os.path.dirname(HERE)
BLOCK_OUT = os.path.join(ROOT, 'src/main/resources/assets/data_center_addon/textures/block/industry')
ITEM_OUT = os.path.join(ROOT, 'src/main/resources/assets/data_center_addon/textures/item/industry')

# block textures: (Data Center texture, hue)
BLOCKS = {
    # the Purchase Terminal: Data Center's bank terminal with an amber screen
    'purchase_terminal_screen': ('bank_terminal_screen', 0.09),
}


def blank():
    return [[[0, 0, 0, 0] for _ in range(16)] for _ in range(16)]


def pellets():
    """A small heap of plastic pellets: round, off-white with a few coloured ones."""
    img = blank()
    rnd = random.Random(7)
    colours = [(0xE8, 0xE4, 0xD8)] * 6 + [(0x4A, 0x8F, 0xD8), (0xD8, 0x4A, 0x4A), (0x5A, 0xB8, 0x5A)]
    spots = [(3, 11), (6, 12), (9, 12), (12, 11), (4, 8), (7, 9), (10, 9), (13, 8), (5, 5), (8, 6), (11, 5), (8, 3)]
    for cx, cy in spots:
        base = colours[rnd.randrange(len(colours))]
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                x, y = cx + dx, cy + dy
                if abs(dx) + abs(dy) == 2 or not (0 <= x < 16 and 0 <= y < 16):
                    continue
                shade = 1.15 if (dx, dy) == (-1, -1) or (dx < 0 and dy <= 0) else 0.8 if dx > 0 or dy > 0 else 1.0
                img[y][x] = [min(255, int(c * shade)) for c in base] + [255]
        img[cy - 1][cx - 1] = [255, 255, 255, 255] if 0 <= cy - 1 and 0 <= cx - 1 else img[cy - 1][cx - 1]
    return img


ITEMS = {
    'plastic_pellets': pellets,
}


def main():
    os.makedirs(BLOCK_OUT, exist_ok=True)
    os.makedirs(ITEM_OUT, exist_ok=True)
    with zipfile.ZipFile(network.find_jar()) as jar:
        def source(name):
            return pngio.read(jar.read(f'assets/datacenter/textures/block/{name}.png'))[2]

        for name, (base, hue) in BLOCKS.items():
            pngio.write(os.path.join(BLOCK_OUT, name + '.png'), network.rehue(source(base), hue))
    for name, draw in ITEMS.items():
        pngio.write(os.path.join(ITEM_OUT, name + '.png'), draw())
    print(f'Wrote {len(BLOCKS)} block and {len(ITEMS)} item textures')


if __name__ == '__main__':
    main()
