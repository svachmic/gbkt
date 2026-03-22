#!/usr/bin/env python3
"""
Generate Czech diacritic tiles for the gbkt font.

Extends the Original 128-tile ASCII font with 14 Czech diacritic tiles:
  Tile 128 = ě  (e + háček)
  Tile 129 = š  (s + háček)
  Tile 130 = č  (c + háček)
  Tile 131 = ř  (r + háček)
  Tile 132 = ž  (z + háček)
  Tile 133 = ý  (y + čárka)
  Tile 134 = á  (a + čárka)
  Tile 135 = í  (i + čárka)
  Tile 136 = é  (e + čárka)
  Tile 137 = ú  (u + čárka)
  Tile 138 = ů  (u + kroužek)
  Tile 139 = ď  (d + háček)
  Tile 140 = ť  (t + háček)
  Tile 141 = ň  (n + háček)

VRAM Layout:
  FONT_OFFSET = 0x72 (tile 114) → 256 - 114 = 142 tiles available
  OR compile-time locale selection (only one font variant compiled)
  This script documents: tiles 128-141 = Czech diacritics.

Font conventions:
  - 8x8 pixel tiles in 4-color palette (indices 0-3)
  - Palette: 0=black (foreground), 1=white, 2=light-gray (background), 3=dark-gray
  - Letters typically use rows 3-7 (5 rows for glyph body)
  - Ascenders (d, t) use rows 1-7
  - Diacritical mark placed in rows 0-1, gap in row 2
"""

from PIL import Image
import sys
import os

BG = 2  # Background palette index (light gray 170,170,170)
FG = 0  # Foreground palette index (black)

def get_tile_pixels(img, char_code):
    """Get 8x8 pixel data as list of rows of palette indices."""
    x = (char_code % 16) * 8
    y = (char_code // 16) * 8
    rows = []
    for row in range(8):
        row_pixels = [img.getpixel((x + col, y + row)) for col in range(8)]
        rows.append(row_pixels)
    return rows

def set_pixel(tile, row, col, value):
    """Set a pixel in the tile data."""
    if 0 <= row < 8 and 0 <= col < 8:
        tile[row][col] = value

def blank_tile():
    """Create a blank 8x8 tile with background color."""
    return [[BG] * 8 for _ in range(8)]

def copy_rows(src_tile, dst_tile, src_start, src_end, dst_start):
    """Copy rows src_start..src_end from src to dst at dst_start."""
    for i, src_row in enumerate(range(src_start, min(src_end, 8))):
        dst_row = dst_start + i
        if 0 <= dst_row < 8:
            dst_tile[dst_row] = src_tile[src_row][:]

def add_hacek(tile, col_offset=1):
    """
    Add háček (ˇ) mark in rows 0-1.
    Háček looks like an inverted V: .#.#.. or similar.
    Placed in row 0-1 area.
    """
    # Háček shape: two pixels like a V pointing down
    # Row 0: .#.#.. (two tips)
    # Row 1: ..#... (point)
    # Adjusted for 8px width with col_offset for centering
    # Clear rows 0-1 first
    tile[0] = [BG] * 8
    tile[1] = [BG] * 8
    # Place háček centered around col 2-3 (typical letter width)
    # Two pixels at top (spread), one at bottom (converge)
    set_pixel(tile, 0, col_offset + 1, FG)  # left tip
    set_pixel(tile, 0, col_offset + 3, FG)  # right tip
    set_pixel(tile, 1, col_offset + 2, FG)  # center bottom

def add_carka(tile, col_offset=1):
    """
    Add čárka (´) acute accent mark in rows 0-1.
    Čárka looks like a diagonal line going up-right.
    """
    tile[0] = [BG] * 8
    tile[1] = [BG] * 8
    # Acute accent: two pixels going up-right
    # Row 1: bottom left pixel
    # Row 0: top right pixel
    set_pixel(tile, 1, col_offset + 1, FG)  # lower
    set_pixel(tile, 0, col_offset + 2, FG)  # upper right

def add_krouzek(tile, col_offset=1):
    """
    Add kroužek (°) ring mark in rows 0-1.
    Ring/circle shape above the letter.
    """
    tile[0] = [BG] * 8
    tile[1] = [BG] * 8
    # Small ring: 3x2 pixel circle
    # Row 0: .###. (top arc)
    # Row 1: .#.#. (sides) — but only 2 rows so: .###.
    # In 2 rows: approximate with a small arc
    set_pixel(tile, 0, col_offset + 1, FG)  # left
    set_pixel(tile, 0, col_offset + 2, FG)  # center top (omit for ring shape)
    set_pixel(tile, 0, col_offset + 3, FG)  # right
    set_pixel(tile, 1, col_offset + 1, FG)  # bottom left
    set_pixel(tile, 1, col_offset + 3, FG)  # bottom right
    # This creates: .###. / .#.#. which looks like a ring

def make_diacritic_tile(base_tile, mark_fn, col_offset=1):
    """
    Create a diacritic tile from base letter + mark function.
    The base letter uses rows 3-7. Mark goes in rows 0-1. Row 2 is gap.
    """
    tile = blank_tile()
    # Copy letter glyph (rows 3-7 from base)
    copy_rows(base_tile, tile, src_start=3, src_end=8, dst_start=3)
    # Add diacritical mark in rows 0-1
    mark_fn(tile, col_offset)
    return tile

def make_diacritic_tile_tall(base_tile, mark_fn, col_offset=1):
    """
    Create diacritic tile for tall letters with ascenders (d, t).
    Shift letter down by 1 row (rows 2-7 become rows 3-7+1, squeezing row 1 into row 2).
    Mark goes in row 0 only (1px mark for tight fit).
    """
    tile = blank_tile()
    # For tall letters (d, t that use rows 1-7): shift down 1px
    # src rows 1-7 → dst rows 2-7 (shift +1, truncate row 7)
    # Actually: compress rows 1-7 into rows 2-7
    # This loses 1 row of detail, but fits with 1 row for mark
    # Row 0: mark
    # Row 1: gap (background)
    # Rows 2-7: letter body (rows 1-6 of original, truncated)
    for src_row in range(1, 7):
        dst_row = src_row + 1
        tile[dst_row] = base_tile[src_row][:]
    # Add mark in row 0 only (compact version)
    mark_fn_compact = lambda t, off: compact_mark(t, off, mark_fn)
    mark_fn_compact(tile, col_offset)
    return tile

def compact_mark(tile, col_offset, full_mark_fn):
    """Apply mark in row 0 only (compact, 1-row version)."""
    temp = blank_tile()
    full_mark_fn(temp, col_offset)
    # Use row 1 of the temp tile as row 0 of our tile (the convergence point)
    tile[0] = temp[1][:]

def make_diacritic_y(base_tile, mark_fn, col_offset=2):
    """
    Special case for y: it uses rows 2-7.
    Mark goes in row 0 only, with row 1 as gap.
    """
    tile = blank_tile()
    # Copy letter glyph (rows 2-7 from base y)
    copy_rows(base_tile, tile, src_start=2, src_end=8, dst_start=2)
    # Add mark in row 0-1
    mark_fn(tile, col_offset)
    return tile

def tile_to_image(tile, palette):
    """Convert 8x8 tile to PIL Image."""
    img = Image.new('P', (8, 8))
    img.putpalette(palette)
    for row in range(8):
        for col in range(8):
            img.putpixel((col, row), tile[row][col])
    return img

def print_tile(tile, label):
    """Print tile for debug visualization."""
    print(f"{label}:")
    for row in tile:
        print(''.join(['#' if p == 0 else '.' for p in row]))
    print()

def main():
    font_path = '/Users/michalsvacha/GitHub/personal/gbkt/LabyrinthOfTheDragon-port/res/tiles/font.png'
    output_path = font_path

    # Load existing font
    img = Image.open(font_path)
    palette = img.getpalette()

    print(f"Original font: {img.size[0]}x{img.size[1]} = {(img.size[0]//8)*(img.size[1]//8)} tiles")

    # Get base letter tiles
    e_tile = get_tile_pixels(img, 0x65)  # e
    s_tile = get_tile_pixels(img, 0x73)  # s
    c_tile = get_tile_pixels(img, 0x63)  # c
    r_tile = get_tile_pixels(img, 0x72)  # r
    z_tile = get_tile_pixels(img, 0x7a)  # z
    y_tile = get_tile_pixels(img, 0x79)  # y
    a_tile = get_tile_pixels(img, 0x61)  # a
    i_tile = get_tile_pixels(img, 0x69)  # i
    u_tile = get_tile_pixels(img, 0x75)  # u
    d_tile = get_tile_pixels(img, 0x64)  # d
    t_tile = get_tile_pixels(img, 0x74)  # t
    n_tile = get_tile_pixels(img, 0x6e)  # n

    # Generate the 14 Czech diacritic tiles
    # Standard col_offset=1 for most letters (centered in 8px)
    czech_tiles = [
        # Tile 128 = ě (e + háček)
        make_diacritic_tile(e_tile, add_hacek, col_offset=1),
        # Tile 129 = š (s + háček)
        make_diacritic_tile(s_tile, add_hacek, col_offset=1),
        # Tile 130 = č (c + háček)
        make_diacritic_tile(c_tile, add_hacek, col_offset=1),
        # Tile 131 = ř (r + háček)
        make_diacritic_tile(r_tile, add_hacek, col_offset=1),
        # Tile 132 = ž (z + háček)
        make_diacritic_tile(z_tile, add_hacek, col_offset=1),
        # Tile 133 = ý (y + čárka)
        make_diacritic_y(y_tile, add_carka, col_offset=2),
        # Tile 134 = á (a + čárka)
        make_diacritic_tile(a_tile, add_carka, col_offset=1),
        # Tile 135 = í (i + čárka)
        make_diacritic_tile(i_tile, add_carka, col_offset=2),
        # Tile 136 = é (e + čárka)
        make_diacritic_tile(e_tile, add_carka, col_offset=1),
        # Tile 137 = ú (u + čárka)
        make_diacritic_tile(u_tile, add_carka, col_offset=1),
        # Tile 138 = ů (u + kroužek)
        make_diacritic_tile(u_tile, add_krouzek, col_offset=1),
        # Tile 139 = ď (d + háček) — tall letter
        make_diacritic_tile_tall(d_tile, add_hacek, col_offset=3),
        # Tile 140 = ť (t + háček) — tall letter
        make_diacritic_tile_tall(t_tile, add_hacek, col_offset=1),
        # Tile 141 = ň (n + háček)
        make_diacritic_tile(n_tile, add_hacek, col_offset=1),
    ]

    labels = ['ě (128)', 'š (129)', 'č (130)', 'ř (131)', 'ž (132)',
              'ý (133)', 'á (134)', 'í (135)', 'é (136)', 'ú (137)',
              'ů (138)', 'ď (139)', 'ť (140)', 'ň (141)']

    # Print all tiles for visual verification
    print("Generated Czech diacritic tiles:")
    for tile, label in zip(czech_tiles, labels):
        print_tile(tile, label)

    # Create extended font image: original 128x64 + new row of 14 tiles
    # New image: 128 wide x 72 tall (8 rows original + 1 new row with 14 tiles)
    new_img = Image.new('P', (128, 72))
    new_img.putpalette(palette)

    # Copy original 128x64 pixels
    for y in range(64):
        for x in range(128):
            new_img.putpixel((x, y), img.getpixel((x, y)))

    # Add 14 new diacritic tiles in row 8 (y=64..71)
    for tile_idx, tile in enumerate(czech_tiles):
        tile_x = tile_idx * 8
        tile_y = 64
        for row in range(8):
            for col in range(8):
                new_img.putpixel((tile_x + col, tile_y + row), tile[row][col])

    # Fill remaining 2 tiles in the new row with background (tiles 14 and 15 of the new row)
    for tile_idx in range(14, 16):
        tile_x = tile_idx * 8
        for row in range(8):
            for col in range(8):
                new_img.putpixel((tile_x + col, 64 + row), BG)

    # Save extended font
    new_img.save(output_path)
    print(f"Saved extended font: {new_img.size[0]}x{new_img.size[1]} = {(new_img.size[0]//8)*(new_img.size[1]//8)} tiles")

    # Verify
    verify = Image.open(output_path)
    w, h = verify.size
    tiles = (w // 8) * (h // 8)
    print(f"Verification: {w}x{h}, {tiles} tiles")
    assert tiles >= 142, f"Expected >= 142 tiles, got {tiles}"
    print("PASS: font.png extended with 142 tiles (128 base + 14 Czech)")

    # VRAM budget report
    print()
    print("=== VRAM Budget Analysis ===")
    print(f"Total tiles: {tiles}")
    print(f"VRAM tile bank capacity: 256 tiles")
    print()
    print("Option A: Lower FONT_OFFSET approach")
    print(f"  FONT_OFFSET = 0x72 (tile 114)")
    print(f"  Available VRAM tiles for font: 256 - 114 = 142 ✓")
    print(f"  Available VRAM tiles for background/dungeon: 0x00-0x71 = 114 tiles")
    print()
    print("Option B: Compile-time locale selection (CHOSEN)")
    print("  Only one font variant compiled (cs.po OR en.po)")
    print("  English: 128 tiles, Czech: 142 tiles")
    print("  FONT_OFFSET = 0x80 (tile 128), need tiles 128-269 → OVERFLOWS without Option A")
    print()
    print("RECOMMENDATION: Use FONT_OFFSET = 0x72 for Czech locale builds")
    print("  This leaves 114 VRAM tiles for dungeon/game graphics")
    print("  The LotD port's dungeon_tiles.png uses the lower tile range")

if __name__ == '__main__':
    main()
