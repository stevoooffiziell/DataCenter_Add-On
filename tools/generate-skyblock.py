"""
Builds the starter island of the "Data Center SkyBlock" world type: a small island with a Data
Center style server room, written as a structure into the add-on's resources
(data/data_center_addon/structure/skyblock_island.nbt). The add-on places it on the first start of a
world of that type (SkyblockIsland).

    python tools/generate-skyblock.py
"""
import gzip
import io
import json
import os
import struct
from collections import deque

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_VERSION = 3955  # Minecraft 1.21.1
DC = 'datacenter:'


# ------------------------------------------------------------------ NBT


class Byte(int): pass
class Short(int): pass
class Long(int): pass


def _name(out, s):
    b = s.encode('utf-8')
    out.write(struct.pack('>H', len(b)))
    out.write(b)


def _type(v):
    if isinstance(v, Byte): return 1
    if isinstance(v, Short): return 2
    if isinstance(v, Long): return 4
    if isinstance(v, bool): return 1
    if isinstance(v, int): return 3
    if isinstance(v, float): return 6
    if isinstance(v, str): return 8
    if isinstance(v, list): return 9
    if isinstance(v, dict): return 10
    raise TypeError(type(v))


def _payload(out, v):
    t = _type(v)
    if t == 1: out.write(struct.pack('>b', int(v)))
    elif t == 2: out.write(struct.pack('>h', v))
    elif t == 3: out.write(struct.pack('>i', v))
    elif t == 4: out.write(struct.pack('>q', v))
    elif t == 6: out.write(struct.pack('>d', v))
    elif t == 8: _name(out, v)
    elif t == 9:
        et = _type(v[0]) if v else 0
        out.write(struct.pack('>bi', et, len(v)))
        for e in v:
            _payload(out, e)
    elif t == 10:
        for k, e in v.items():
            out.write(struct.pack('>b', _type(e)))
            _name(out, k)
            _payload(out, e)
        out.write(b'\x00')


def nbt_file(root):
    out = io.BytesIO()
    out.write(b'\x0a')
    _name(out, '')
    _payload(out, root)
    return gzip.compress(out.getvalue())


# ------------------------------------------------------------------ the island

blocks = {}          # (x, y, z) -> (name, props, nbt)


def put(x, y, z, name, nbt=None, **props):
    if ':' not in name:
        name = 'minecraft:' + name
    blocks[(x, y, z)] = (name, {k: str(v).lower() for k, v in props.items()}, nbt)


W, D = 15, 11        # x 0..14, z 0..10


def inside(x, z, shrink=0):
    """A rounded island outline."""
    cx, cz = (W - 1) / 2, (D - 1) / 2
    rx, rz = cx - shrink + 0.6, cz - shrink + 0.6
    return ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2 <= 1.0


# body: grass on top, dirt below, a stone core that tapers off
for x in range(W):
    for z in range(D):
        if inside(x, z):
            put(x, 4, z, 'grass_block', snowy='false')
            put(x, 3, z, 'dirt')
        if inside(x, z, 1.5):
            put(x, 2, z, 'dirt')
        if inside(x, z, 3):
            put(x, 1, z, 'stone')
        if inside(x, z, 4.6):
            put(x, 0, z, 'stone')
put(4, 1, 5, 'iron_ore')
put(5, 0, 5, 'coal_ore')
put(3, 2, 4, 'clay')

# the server room: x 7..13, z 2..8, floor at y 4, walls y 5..7, roof y 8
X0, X1, Z0, Z1, FLOOR, ROOF = 7, 13, 2, 8, 4, 8
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        edge_x, edge_z = x in (X0, X1), z in (Z0, Z1)
        put(x, FLOOR - 1, z, 'stone')
        put(x, FLOOR, z, DC + 'dark_raised_floor', facing='north')
        if edge_x or edge_z:
            if edge_x and edge_z:
                for y in range(FLOOR + 1, ROOF):
                    put(x, y, z, DC + 'dark_pillar', facing='north')
            else:
                face = ('west' if x == X0 else 'east') if edge_x else ('north' if z == Z0 else 'south')
                put(x, FLOOR + 1, z, DC + 'dark_wall_panel_base', facing=face)
                put(x, FLOOR + 2, z, DC + 'dark_wall_panel', facing=face)
                put(x, FLOOR + 3, z, DC + 'dark_wall_panel_seam', facing=face)
        put(x, ROOF, z, DC + 'dark_concrete', facing='north')

# a blue band and windows on the long walls
for x in range(X0 + 1, X1):
    if x in (9, 11):
        put(x, FLOOR + 2, Z0, DC + 'dark_window', facing='north')
        put(x, FLOOR + 2, Z1, DC + 'dark_window', facing='south')
    else:
        put(x, FLOOR + 2, Z0, DC + 'dark_wall_panel_blue', facing='north')
        put(x, FLOOR + 2, Z1, DC + 'dark_wall_panel_blue', facing='south')
put(X1, FLOOR + 2, 5, DC + 'dark_window', facing='east')

# lights under the roof, a cold aisle down the middle
for x, z in ((9, 4), (11, 4), (9, 6), (11, 6)):
    put(x, ROOF - 1, z, DC + 'ceiling_light', facing='north')
for x in range(X0 + 1, X1):
    put(x, FLOOR, 5, DC + 'dark_floor_grating', facing='east')

# the door in the west wall, hazard marking in front of it
put(X0, FLOOR + 1, 5, DC + 'server_door', facing='east', half='lower', hinge='left', open='false')
put(X0, FLOOR + 2, 5, DC + 'server_door', facing='east', half='upper', hinge='left', open='false')
put(X0, FLOOR + 3, 5, DC + 'dark_wall_panel_seam', facing='west')
for z in (4, 5, 6):
    put(X0 - 1, FLOOR, z, DC + 'dark_floor_hazard', facing='east')


# ------------------------------------------------------------------ the chest


def item(slot, id, count=1, components=None):
    e = {'Slot': Byte(slot), 'id': id if ':' in id else 'minecraft:' + id, 'count': count}
    if components:
        e['components'] = components
    return e


GUIDE_DE = [
    'Willkommen auf deiner Insel!\n\nZiel: Baue aus diesem kleinen Raum ein Rechenzentrum.\n\nData Center gibt dir beim ersten Betreten ein Starterkit: Shop, Rack, Werkbank, Mining-Rig, den Hosting Manager und mehr.',
    'Erste Schritte\n\n1. Baum fällen, Setzlinge pflanzen.\n2. Lava und Eis: Bruchstein-Generator bauen.\n3. Holz am Log Buyer verkaufen, das bringt das erste Geld.\n4. Teile im Shop kaufen.',
    'Der Serverraum\n\nDer Mittelgang ist der Kaltgang: Stell deine Racks links und rechts davon auf.\n\nMit dem Hosting Manager des Addons siehst du Kunden, Aufträge und deinen Ruf.',
    'Wachsen\n\nDie Insel ist klein. Erweitere sie mit Bruchstein und Erde, und bau den Raum später zu einem Tier-I-Raum aus, sonst kommst du in der Reputation nicht über Start-ups hinaus.',
]


def book(pages, title, author):
    return {'minecraft:written_book_content': {
        'title': title, 'author': author,
        'pages': [json.dumps({'text': p}, ensure_ascii=False) for p in pages],
    }}


chest_items = [
    item(0, 'lava_bucket'),
    item(1, 'ice', 2),
    item(2, 'oak_sapling', 2),
    item(3, 'bone_meal', 8),
    item(4, 'bread', 8),
    item(5, 'torch', 8),
    item(9, 'wheat_seeds', 4),
    item(10, 'melon_seeds'),
    item(11, 'pumpkin_seeds'),
    item(12, 'sugar_cane', 2),
    item(13, 'sand', 4),
    item(14, 'cactus'),
    item(15, 'red_mushroom'),
    item(16, 'brown_mushroom'),
    item(18, 'dirt', 8),
    item(19, 'cobblestone', 16),
    item(26, 'written_book', 1, book(GUIDE_DE, 'Inselhandbuch', 'Data Center')),
]

put(12, FLOOR + 1, 3, 'chest', {'id': 'minecraft:chest', 'Items': chest_items}, facing='west', type='single',
    waterlogged='false')
put(12, FLOOR + 1, 7, 'crafting_table')
put(11, FLOOR + 1, 7, 'furnace', facing='north', lit='false')

# ------------------------------------------------------------------ the tree (oak, west side)

TX, TZ = 3, 5
for y in range(5, 10):
    put(TX, y, TZ, 'oak_log', axis='y')
leaves = []
for y, r in ((7, 2), (8, 2), (9, 1), (10, 1)):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            if r == 2 and abs(dx) == 2 and abs(dz) == 2:
                continue
            if y == 10 and abs(dx) == 1 and abs(dz) == 1:
                continue
            p = (TX + dx, y, TZ + dz)
            if p not in blocks:
                leaves.append(p)
# leaf distance to the nearest log, as the game computes it
dist = {}
queue = deque()
for p, (name, _, _) in blocks.items():
    if name == 'minecraft:oak_log':
        queue.append((p, 0))
leaf_set = set(leaves)
while queue:
    (x, y, z), d = queue.popleft()
    for n in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
        if n in leaf_set and n not in dist:
            dist[n] = d + 1
            queue.append((n, d + 1))
for p in leaves:
    put(*p, 'oak_leaves', distance=min(7, dist.get(p, 7)), persistent='false', waterlogged='false')

# ------------------------------------------------------------------ write the structure

palette, index, out_blocks = [], {}, []
for (x, y, z), (name, props, nbt) in sorted(blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
    key = (name, tuple(sorted(props.items())))
    if key not in index:
        index[key] = len(palette)
        entry = {'Name': name}
        if props:
            entry['Properties'] = dict(sorted(props.items()))
        palette.append(entry)
    b = {'state': index[key], 'pos': [x, y, z]}
    if nbt:
        b['nbt'] = nbt
    out_blocks.append(b)

size = [max(p[i] for p in blocks) + 1 for i in range(3)]
structure = {'DataVersion': DATA_VERSION, 'size': size, 'palette': palette, 'blocks': out_blocks, 'entities': []}

# the add-on places it at -7 60 -5 (its middle at 0, 0) and sets the spawn on the grass at +5 +5 +8
# (SkyblockIsland); a change of the size or the spawn has to go there too
out = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'data_center_addon', 'structure', 'skyblock_island.nbt')
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, 'wb') as f:
    f.write(nbt_file(structure))
print(f'{len(blocks)} blocks, size {size}, {len(palette)} states -> {out}')
