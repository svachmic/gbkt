# Game Boy 2-bpp Planar Tile Encoding — Decode Notes (D-V2 diagnostic)

## Hardware encoding (reference)

A single Game Boy background or sprite tile is **8 pixels wide × 8 pixels tall**, encoded as **16 bytes** = 8 rows × 2 bytes per row. The two bytes for each row encode the SAME pixel position twice across two bitplanes:

- **Byte at offset `2*r`** = plane 0 (low bit of each pixel's color index) for row `r`
- **Byte at offset `2*r+1`** = plane 1 (high bit of each pixel's color index) for row `r`

For each pixel column `c ∈ {0..7}`, the pixel's 2-bit color index is computed by interleaving bits from the two planes:

```
bit_plane0 = (byte[2*r]     >> (7 - c)) & 1
bit_plane1 = (byte[2*r + 1] >> (7 - c)) & 1
color      = (bit_plane1 << 1) | bit_plane0       // 0..3
```

Color 0 = transparent / lightest, color 3 = darkest (on default DMG palette). Bit 7 is the LEFTMOST pixel of the row; bit 0 is the RIGHTMOST.

## Decoding the literal byte `0xF0`

`0xF0` = `11110000` binary = bits 7,6,5,4 set, bits 3,2,1,0 clear → leftmost 4 pixels lit on whatever plane this byte belongs to, rightmost 4 pixels clear.

## Decoding the literal byte `0x0F`

`0x0F` = `00001111` binary = bits 7,6,5,4 clear, bits 3,2,1,0 set → leftmost 4 pixels clear, rightmost 4 pixels lit.

## Pixel-by-pixel decode of the CURRENT Plan 10.1-02 literal

The current `bgFillCheckerboard()` emits (from `MetaspriteBuilder.kt` lines 362-364):

```
0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F,
0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F
```

= 16 bytes total = 1 complete tile. Pairing them as `(plane0, plane1)` for each of the 8 rows:

| Row | Offset | Byte[2r] (P0) | Byte[2r+1] (P1) | Decode (P0=P1 → color 0 or 3) |
|-----|--------|---------------|-----------------|-------------------------------|
| 0   | 0,1    | `0xF0`        | `0xF0`          | cols 0-3 = color 3 (██), cols 4-7 = color 0 (..) |
| 1   | 2,3    | `0xF0`        | `0xF0`          | cols 0-3 = color 3 (██), cols 4-7 = color 0 (..) |
| 2   | 4,5    | `0x0F`        | `0x0F`          | cols 0-3 = color 0 (..), cols 4-7 = color 3 (██) |
| 3   | 6,7    | `0x0F`        | `0x0F`          | cols 0-3 = color 0 (..), cols 4-7 = color 3 (██) |
| 4   | 8,9    | `0xF0`        | `0xF0`          | cols 0-3 = color 3 (██), cols 4-7 = color 0 (..) |
| 5   | 10,11  | `0xF0`        | `0xF0`          | cols 0-3 = color 3 (██), cols 4-7 = color 0 (..) |
| 6   | 12,13  | `0x0F`        | `0x0F`          | cols 0-3 = color 0 (..), cols 4-7 = color 3 (██) |
| 7   | 14,15  | `0x0F`        | `0x0F`          | cols 0-3 = color 0 (..), cols 4-7 = color 3 (██) |

### Per-tile visual rendering (ASCII)

```
Col:   01234567
Row 0: ████....
Row 1: ████....
Row 2: ....████
Row 3: ....████
Row 4: ████....
Row 5: ████....
Row 6: ....████
Row 7: ....████
```

**Per-tile pattern: 4-pixel-wide × 2-pixel-tall alternating cells. That's a 2:1 aspect-ratio rectangle, NOT a square.**

### Tiled across the BG grid (160×144 = 20×18 tiles), `fill_bkg_rect(0, 0, 20, 18, 0)` replicates this tile uniformly. Composited at 2×2-tile granularity:

```
Cols:  0..7     8..15
Row 0: ████.... ████....
Row 1: ████.... ████....
Row 2: ....████ ....████
Row 3: ....████ ....████
Row 4: ████.... ████....
Row 5: ████.... ████....
Row 6: ....████ ....████
Row 7: ....████ ....████
```

The tile boundaries are seamless because every tile is identical. The visible cell unit is **4w × 2h** — exactly "rectangles, slightly wider than taller" as the user reported.

## What the user is seeing (corroboration via screenshot inspection)

`.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior1-animation-advance.png` shows the BG: cells are visibly **wider than tall by a factor of ~2** (clearly NOT square). This matches the 4w×2h prediction above.

## What a TRUE 4×4 checker requires

For a true 4×4 checker — i.e., each visible cell is 4 wide × 4 tall — every group of 4 consecutive rows must share the same horizontal pattern, then flip. The correct literal is:

```
// rows 0-3 (offsets 0-7): all (0xF0, 0xF0) → left 4 cols lit
0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,
// rows 4-7 (offsets 8-15): all (0x0F, 0x0F) → right 4 cols lit
0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F
```

That produces per-tile:

```
Row 0: ████....
Row 1: ████....
Row 2: ████....
Row 3: ████....
Row 4: ....████
Row 5: ....████
Row 6: ....████
Row 7: ....████
```

Composited 2×2 across the BG:

```
Cols:  0..7     8..15
Row 0: ████.... ████....
Row 1: ████.... ████....
Row 2: ████.... ████....
Row 3: ████.... ████....
Row 4: ....████ ....████
Row 5: ....████ ....████
Row 6: ....████ ....████
Row 7: ....████ ....████
```

Visible cell unit = **4w × 4h** = true square 4×4 checker.

## Root cause statement

Plan 10.1-02's literal alternates `0xF0`-row vs `0x0F`-row every **2 rows** (because each "row" in the human-readable comma group is 2 bytes = 1 actual tile row, so the visible alternation period is 2 rows). The intended alternation period is 4 rows. The fix is to repeat each `0xF0,0xF0,0xF0,0xF0` group AND each `0x0F,0x0F,0x0F,0x0F` group consecutively — i.e., 8 consecutive `0xF0` bytes followed by 8 consecutive `0x0F` bytes — instead of interleaving the two halves twice.

## How Plan 10.1-02's test (`Seed005CheckerboardBytePatternTest`) locked the wrong shape

The test asserts `"0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F"` appears exactly twice in the emitted code. That string IS present in the current (wrong) literal, twice — so the test passes — but it also forbids the correct literal (where that exact substring appears ZERO times; the correct literal is `0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0x0F,…`). The test was authored against the wrong mental model: the orchestrator and Plan 02 author both assumed that "row" in the textual comma-grouping equals a pixel row, which is half-true (each `0xF0,0xF0` pair is a pixel row, so the textual half-rows pair up to make pixel rows — but the alternation period in pixel rows is then 2, not 4).
