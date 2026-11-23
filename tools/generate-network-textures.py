"""Generates the block textures of the add-on's network cables, switches and Hosting Manager.

Colour variants of Data Center's own cable and switch textures, read from the Data Center jar that
Gradle downloaded. Output: src/main/resources/assets/data_center_addon/textures/block/network/<name>.png

usage: python tools/generate-network-textures.py [path to the Data Center jar]
"""
import colorsys
import glob
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngio  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/data_center_addon/textures/block/network')

# copper cables: jacket colour (Data Center's grey CAT cable, re-tinted)
COPPER = {
    'cable_cat3': (0xC9, 0xBE, 0x9F),   # beige, like old telephone wiring
    'cable_cat5': (0x9A, 0xA0, 0xA6),   # grey
    'cable_cat5e': (0x2F, 0x6F, 0xD0),  # blue
    'cable_cat6': (0x3F, 0xA8, 0x4A),   # green
    'cable_cat6a': (0xE8, 0x7A, 0x1E),  # orange
    'cable_cat8': (0xC8, 0x30, 0x30),   # red
}
# fibre cables: the glowing core of Data Center's fibre cable, re-hued to the standard jacket colours
FIBRE = {
    'cable_om3': 0.50,  # aqua (TIA-598 colour for OM3)
    'cable_om4': 0.83,  # erika violet (OM4)
    'cable_os2': 0.15,  # yellow (single-mode)
}
# switch fronts (hue for the status lights of the two newest switches) and the Hosting Manager screen
SWITCH_FRONTS = {
    'switch_arista_7060_front': ('switch_backbone_front', 0.58),
    'switch_spectrum_front': ('switch_backbone_front', 0.30),
    # the Hosting Manager: Data Center's mining dashboard with a green screen
    'hosting_manager_screen': ('mining_dashboard_screen', 0.36),
}


def find_jar():
    if len(sys.argv) > 1:
        return sys.argv[1]
    props = dict(line.strip().split('=', 1) for line in open(os.path.join(ROOT, 'gradle.properties'), encoding='utf-8')
                 if '=' in line and not line.startswith('#'))
    pattern = os.path.join(os.path.expanduser('~'), '.gradle/caches/modules-2/files-2.1/curse.maven',
                           f"data-center-{props['data_center_project_id']}", props['data_center_file_id'], '*', '*.jar')
    jars = [j for j in glob.glob(pattern) if 'sources' not in j]
    if not jars:
        sys.exit('Data Center jar not found; run gradlew compileJava once or pass the jar path.')
    return jars[0]


def luminance(px):
    return 0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]


def tint(pixels, colour):
    """Keeps the shading of a grey texture and gives it the colour; mid-grey maps to the colour itself."""
    lums = sorted(luminance(px) for row in pixels for px in row if px[3])
    mid = lums[len(lums) // 2] or 1
    out = []
    for row in pixels:
        line = []
        for px in row:
            f = luminance(px) / mid
            line.append([min(255, round(c * f)) for c in colour] + [px[3]])
        out.append(line)
    return out


def rehue(pixels, hue, min_saturation=0.25):
    """Moves every clearly coloured pixel to the given hue; greys and darks stay as they are."""
    out = []
    for row in pixels:
        line = []
        for r, g, b, a in row:
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if s >= min_saturation and l > 0.08:
                r2, g2, b2 = colorsys.hls_to_rgb(hue, l, s)
                line.append([round(r2 * 255), round(g2 * 255), round(b2 * 255), a])
            else:
                line.append([r, g, b, a])
        out.append(line)
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    with zipfile.ZipFile(find_jar()) as jar:
        def source(name):
            return pngio.read(jar.read(f'assets/datacenter/textures/block/{name}.png'))[2]

        for name, colour in COPPER.items():
            pngio.write(os.path.join(OUT, name + '.png'), tint(source('cable_cat'), colour))
        for name, hue in FIBRE.items():
            pngio.write(os.path.join(OUT, name + '.png'), rehue(source('cable_lwl'), hue))
        for name, (base, hue) in SWITCH_FRONTS.items():
            pngio.write(os.path.join(OUT, name + '.png'), rehue(source(base), hue))
    print(f'Wrote {len(COPPER) + len(FIBRE) + len(SWITCH_FRONTS)} textures to {OUT}')


if __name__ == '__main__':
    main()
