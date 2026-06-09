#!/usr/bin/env python3
"""Plan 07.4-29 Task 3 — pixel-signature probe (no external deps).

Decodes PNGs via stdlib (zlib + struct). Counts unique pixel triplets and
non-BG pixels per PNG. Computes pixel-deltas between PNG pairs.
"""

import struct
import sys
import zlib
from pathlib import Path


def parse_png(path):
    """Returns (width, height, rgba_pixels) — rgba_pixels is a list of (r,g,b,a)."""
    data = Path(path).read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"not a PNG: {path}"
    pos = 8
    ihdr = None
    idat_chunks = []
    palette = None
    trns = None
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        ctype = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        if ctype == b"IHDR":
            ihdr = struct.unpack(">IIBBBBB", chunk[:13])
        elif ctype == b"PLTE":
            palette = [chunk[i:i + 3] for i in range(0, len(chunk), 3)]
        elif ctype == b"tRNS":
            trns = chunk
        elif ctype == b"IDAT":
            idat_chunks.append(chunk)
        elif ctype == b"IEND":
            break
        pos += 8 + length + 4
    width, height, bit_depth, color_type, _comp, _filter, _interlace = ihdr
    raw = zlib.decompress(b"".join(idat_chunks))
    bpp_map = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}
    bpp = bpp_map[color_type] * bit_depth // 8 if bit_depth >= 8 else 1
    stride = (width * (bpp_map[color_type] * bit_depth) + 7) // 8 + 1  # +1 for filter byte per row
    pixels = []
    prev_row = bytes(stride - 1)
    pos = 0
    for _y in range(height):
        filt = raw[pos]
        row = bytearray(raw[pos + 1:pos + stride])
        # Apply filter — only supports filter=0 (None) and filter=1 (Sub) and 2 (Up) for simplicity
        if filt == 0:
            pass
        elif filt == 1:
            for i in range(bpp, len(row)):
                row[i] = (row[i] + row[i - bpp]) & 0xFF
        elif filt == 2:
            for i in range(len(row)):
                row[i] = (row[i] + prev_row[i]) & 0xFF
        elif filt == 3:
            for i in range(len(row)):
                left = row[i - bpp] if i >= bpp else 0
                up = prev_row[i]
                row[i] = (row[i] + (left + up) // 2) & 0xFF
        elif filt == 4:
            for i in range(len(row)):
                a = row[i - bpp] if i >= bpp else 0
                b = prev_row[i]
                c = prev_row[i - bpp] if i >= bpp else 0
                p = a + b - c
                pa = abs(p - a); pb = abs(p - b); pc = abs(p - c)
                pred = a if pa <= pb and pa <= pc else b if pb <= pc else c
                row[i] = (row[i] + pred) & 0xFF
        else:
            raise ValueError(f"unsupported PNG filter: {filt}")
        prev_row = bytes(row)
        pos += stride
        # Convert row pixels
        if color_type == 2:  # RGB
            for x in range(width):
                r, g, b = row[x * 3], row[x * 3 + 1], row[x * 3 + 2]
                pixels.append((r, g, b, 255))
        elif color_type == 6:  # RGBA
            for x in range(width):
                r, g, b, a = row[x * 4], row[x * 4 + 1], row[x * 4 + 2], row[x * 4 + 3]
                pixels.append((r, g, b, a))
        elif color_type == 3:  # Indexed
            for x in range(width):
                idx = row[x]
                r, g, b = palette[idx]
                a = trns[idx] if trns and idx < len(trns) else 255
                pixels.append((r, g, b, a))
        elif color_type == 0:  # Grayscale
            for x in range(width):
                v = row[x]
                pixels.append((v, v, v, 255))
        elif color_type == 4:  # Grayscale + Alpha
            for x in range(width):
                v, a = row[x * 2], row[x * 2 + 1]
                pixels.append((v, v, v, a))
    return width, height, pixels


def signature(path):
    w, h, px = parse_png(path)
    counts = {}
    for p in px:
        counts[p] = counts.get(p, 0) + 1
    bg = max(counts, key=counts.get)
    unique = len(counts)
    non_bg = sum(v for k, v in counts.items() if k != bg)
    return w, h, unique, non_bg, bg, px


def delta(path_a, path_b):
    wa, ha, pxa = parse_png(path_a)
    wb, hb, pxb = parse_png(path_b)
    if (wa, ha) != (wb, hb):
        return -1, f"size mismatch {wa}x{ha} vs {wb}x{hb}"
    diff = sum(1 for a, b in zip(pxa, pxb) if a != b)
    return diff, ""


def main():
    evdir = Path("/Users/michalsvacha/GitHub/personal/gbkt/.planning/phases/"
                 "07.4-sport-genre-codegen-fix-inserted/evidence/round-7-lcd-disable")
    prefix_anchor = Path("/Users/michalsvacha/GitHub/personal/gbkt/.planning/phases/"
                        "07.4-sport-genre-codegen-fix-inserted/evidence/round-6-wram-corruption/"
                        "01-baseline-race-entry-postH1.png")

    pngs = [
        "23-postfix-race-entry-mcp.png",
        "24-sc-1-visual-player-and-rival.png",
        "25-sc-3-visual-camera-scroll.png",
        "26-sc-4-visual-track-corridor.png",
    ]

    lines = ["=== Pixel signatures ==="]
    for name in pngs:
        p = evdir / name
        if not p.exists():
            lines.append(f"{name} MISSING")
            continue
        w, h, unique, non_bg, bg, _ = signature(p)
        lines.append(f"{name} unique_pixels={unique} non_bg_pixels={non_bg} bg={bg} size={w}x{h}")

    lines.append("=== Deltas ===")
    if prefix_anchor.exists():
        d, err = delta(prefix_anchor, evdir / "23-postfix-race-entry-mcp.png")
        anchor_short = f".../{prefix_anchor.parent.name}/{prefix_anchor.name}"
        lines.append(f"delta({anchor_short}, 23-postfix-race-entry-mcp.png) = {d} {err}")
    else:
        lines.append(f"# prefix_anchor missing at {prefix_anchor}; skipping delta(prefix, 23)")
    d, err = delta(evdir / "23-postfix-race-entry-mcp.png", evdir / "25-sc-3-visual-camera-scroll.png")
    lines.append(f"delta(23-postfix-race-entry-mcp.png, 25-sc-3-visual-camera-scroll.png) = {d} {err}")

    lines.append("=== Supporting variable reads ===")
    sidecar = evdir / ".supporting-variables.txt"
    if sidecar.exists():
        for sline in sidecar.read_text().splitlines():
            if sline.strip():
                lines.append(sline.strip())

    out = "\n".join(lines) + "\n"
    (evdir / "29-pixel-signatures.txt").write_text(out)
    print(out)


if __name__ == "__main__":
    main()
