"""Minimal PNG reader/writer (8-bit RGBA / RGB / greyscale, non-interlaced), Python standard library only."""
import struct
import zlib


def read(data: bytes):
    """Returns (width, height, pixels) with pixels as a list of rows of [r, g, b, a]."""
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('not a PNG')
    pos, idat, palette, trns = 8, b'', None, None
    width = height = depth = colour = interlace = 0
    while pos < len(data):
        length, kind = struct.unpack('>I4s', data[pos:pos + 8])
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b'IHDR':
            width, height, depth, colour, _, _, interlace = struct.unpack('>IIBBBBB', chunk)
        elif kind == b'PLTE':
            palette = [tuple(chunk[i:i + 3]) for i in range(0, len(chunk), 3)]
        elif kind == b'tRNS':
            trns = chunk
        elif kind == b'IDAT':
            idat += chunk
        elif kind == b'IEND':
            break
    if depth != 8 or interlace:
        raise ValueError('only 8-bit non-interlaced PNGs are supported')
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    raw = zlib.decompress(idat)
    stride = width * channels
    rows, prev, i = [], bytearray(stride), 0
    for _ in range(height):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - channels] if x >= channels else 0
            b = prev[x]
            c = prev[x - channels] if x >= channels else 0
            if f == 1:
                line[x] = (line[x] + a) & 0xFF
            elif f == 2:
                line[x] = (line[x] + b) & 0xFF
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 0xFF
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 0xFF
        rows.append(line)
        prev = line
    pixels = []
    for line in rows:
        row = []
        for x in range(width):
            px = line[x * channels:(x + 1) * channels]
            if colour == 6:
                row.append(list(px))
            elif colour == 2:
                row.append(list(px) + [255])
            elif colour == 0:
                row.append([px[0]] * 3 + [255])
            elif colour == 4:
                row.append([px[0]] * 3 + [px[1]])
            else:
                r, g, b = palette[px[0]]
                alpha = trns[px[0]] if trns is not None and px[0] < len(trns) else 255
                row.append([r, g, b, alpha])
        pixels.append(row)
    return width, height, pixels


def write(path: str, pixels):
    """Writes rows of [r, g, b, a] as an 8-bit RGBA PNG."""
    height, width = len(pixels), len(pixels[0])
    raw = b''.join(b'\x00' + bytes(v for px in row for v in px) for row in pixels)

    def chunk(kind, body):
        return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body) & 0xFFFFFFFF)

    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
           + chunk(b'IDAT', zlib.compress(raw, 9))
           + chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(png)
