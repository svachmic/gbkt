# Palette Dump Protocol — BCPS/OCPS Index Pre-Write + BCPD/OCPD Read

**Version:** 1.0  
**Established:** 2026-05-19 (Plan 10.2-03, Task 1)  
**Reused by:** Plans 10.2-04, 10.2-05, 10.2-06 (and any 06a/06b/06c sub-narrow splits)

---

## 1. Protocol Selection — Direct BCPS/OCPS Pre-Write + BCPD/OCPD Read

Per the 2026-05-19 user decision, SEED-012 is extended with `emulator_write_memory`
(added in Plan 10.2-01), enabling the index-write + data-read protocol. The write
tool allows deterministic per-slot addressing without relying on Coffee-GB's
auto-increment behavior (which Pitfall 4 and Assumption A8 flag as unverified for
the read path).

### GBC Hardware Addresses

| Register | Address | Purpose                       |
|----------|---------|-------------------------------|
| BCPS     | 0xFF68  | BG Color Palette Specification (index register) |
| BCPD     | 0xFF69  | BG Color Palette Data (data register)           |
| OCPS     | 0xFF6A  | OBJ Color Palette Specification (index register)|
| OCPD     | 0xFF6B  | OBJ Color Palette Data (data register)          |

### Per-byte Dump Loop

Executed once for BCPD (BG palette RAM) and once for OCPD (OBJ/sprite palette RAM):

```
for index in 0..63:
    emulator_write_memory(BCPS_or_OCPS_addr, index)   // bit 7 (auto-increment) OFF; explicit index per byte
    byte = emulator_read_memory(BCPD_or_OCPD_addr, 1)
    dump[index] = byte
```

**For BG palette (BCPD) dump:**
```
for index in 0..63:
    emulator_write_memory("0xFF68", index)   // Write to BCPS — set palette RAM index
    byte = emulator_read_memory("0xFF69", 1) // Read from BCPD — data at that index
    bcpd_dump[index] = byte
```

**For OBJ palette (OCPD) dump:**
```
for index in 0..63:
    emulator_write_memory("0xFF6A", index)   // Write to OCPS — set palette RAM index
    byte = emulator_read_memory("0xFF6B", 1) // Read from OCPD — data at that index
    ocpd_dump[index] = byte
```

### Why This Protocol (Rationale)

- **64 indexes** cover 8 palettes × 4 colors × 2 LE bytes per palette = full palette RAM per port.
- **Bit 7 (auto-increment) is intentionally LEFT CLEAR** for each write — we want
  deterministic per-iteration addressing, NOT relying on Coffee-GB's increment
  behavior under read (which Pitfall 4 / Assumption A8 flag as unverified).
- If a Wave 1 spike shows auto-increment is deterministic, future probes MAY switch to
  a single bit-7-set BCPS write + 64 sequential reads as a small optimization. Not
  required for closure.
- This protocol uses the `emulator_write_memory` MCP tool added in Plan 10.2-01,
  which writes a single byte at the given address. The tool is invoked 64 times per
  dump for the index port, and `emulator_read_memory` is invoked 64 times for the
  data port.

---

## 2. JSON Shape — Per-Dump File Contract (Used by Plans 04/05/06)

Each dump JSON file (`bcpd-frame60.json` for BG, `ocpd-frame60.json` for OBJ/sprite)
MUST conform to this shape:

```json
{
  "address_port": "0xFF69",
  "index_port": "0xFF68",
  "auto_increment_used": false,
  "byte_count": 64,
  "bytes_hex": ["0xNN", "0xNN", ..., "0xNN"],
  "palettes": [
    {"slot": 0, "colors_15bit": ["0xNNNN", "0xNNNN", "0xNNNN", "0xNNNN"], "interpretation": "<e.g., 'all zero — BCPD slot 0 not written'>"},
    {"slot": 1, "colors_15bit": ["0xNNNN", ...], "interpretation": "..."},
    {"slot": 2, "colors_15bit": ["0xNNNN", ...], "interpretation": "..."},
    {"slot": 3, "colors_15bit": ["0xNNNN", ...], "interpretation": "..."},
    {"slot": 4, "colors_15bit": ["0xNNNN", ...], "interpretation": "..."},
    {"slot": 5, "colors_15bit": ["0xNNNN", ...], "interpretation": "..."},
    {"slot": 6, "colors_15bit": ["0xNNNN", ...], "interpretation": "..."},
    {"slot": 7, "colors_15bit": ["0xNNNN", ...], "interpretation": "..."}
  ],
  "summary": {
    "any_slot_non_zero": true,
    "slot_0_non_zero": false,
    "slot_2_non_zero": true,
    "notes": "<one-line interpretation>"
  }
}
```

### BCPD JSON Shape (address_port: 0xFF69, index_port: 0xFF68)

Same structure as above. For the BG palette dump:
- `address_port` = `"0xFF69"` (BCPD)
- `index_port` = `"0xFF68"` (BCPS)

### OCPD JSON Shape (address_port: 0xFF6B, index_port: 0xFF6A)

Same structure. For the OBJ/sprite palette dump:
- `address_port` = `"0xFF6B"` (OCPD)
- `index_port` = `"0xFF6A"` (OCPS)

### Color Reconstruction Formula

`palettes[i].colors_15bit[j]` = the 15-bit GBC color reconstructed from the byte pair:

```
low_byte  = bytes_hex[i*8 + j*2]      (little-endian — least significant byte)
high_byte = bytes_hex[i*8 + j*2 + 1]  (most significant byte)
color_15bit = (high_byte << 8) | low_byte   // ignore bit 15 — unused per GBC spec
```

Each 15-bit GBC color: bits 14–10 = B, bits 9–5 = G, bits 4–0 = R (5 bits each, 0–31).

To convert to 24-bit RGB for display/comparison:
```
R8 = (R5 << 3) | (R5 >> 2)
G8 = (G5 << 3) | (G5 >> 2)
B8 = (B5 << 3) | (B5 >> 2)
```

### Expected cyan_pal Values (CONTEXT.md D-14)

The expected OCPD slot 2 colors (the cyan elephant palette):

| Index | 15-bit GBC | Approximate RGB  |
|-------|-----------|------------------|
| 0     | 0x7FFF    | white (255,255,255) |
| 1     | 0x7FEA    | light-cyan        |
| 2     | 0x56A0    | mid-cyan          |
| 3     | 0x2940    | dark-cyan         |

If OCPD slot 2 `colors_15bit[0]` == `"0x7FFF"`, the cyan palette IS present in
sprite palette RAM. If it is `"0x0000"`, the write did not reach palette RAM.

---

## 3. Slot Semantics for the Bisect

For sprite-palette-cycle behavior3 (the sub-palette cycle):

| Port | Slot | Expected Write Source | Non-zero = ? |
|------|------|-----------------------|--------------|
| BCPD | 0    | `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` (Plan 22 emission) | BG write reached palette RAM |
| OCPD | 2    | `set_sprite_palette(2u, 1u, cyan_pal)` (Plan 20 emission) | Sprite write reached palette RAM; cyan_pal first color should be 0x7FFF |

These two slots are the **bisect's binding signals**:
- **BCPD slot 0 first-color (`palettes[0].colors_15bit[0]`)**: Proves whether the
  explicit BG palette write from Plan 22 actually landed in Coffee-GB's BCPD RAM.
  If zero at baseline (cbe81d29), the BG path was never working — expected.
- **OCPD slot 2 first-color (`palettes[2].colors_15bit[0]`)**: Proves whether the
  sprite palette write for the cyan elephant is present. At baseline (cbe81d29),
  this MUST be non-zero (0x7FFF expected) — if not, the SEED-013 "cyan once worked"
  premise is broken.

The full 64-byte dump captures all 8 slots per port for additional context — e.g.,
whether the metasprite's S_PAL selector touches a different slot than expected.

---

## 4. Fall-Back Protocol — 16-Byte Minimum

If the Wave 1 spike (Plan 02 MCP test) or this baseline probe shows that the
write-then-read round-trip fails on Coffee-GB (e.g., ALL bytes come back as `0x00`
or `0xFF` regardless of what the emulator writes to BCPS/OCPS), fall back to a
16-byte partial dump covering only the first color of each palette slot:

**Fall-back loop (16 bytes per dump, slots 0..7 first-color only):**
```
for slot in 0..7:
    index = slot * 8     // first byte of each palette slot (byte index within palette RAM)
    emulator_write_memory(BCPS_addr, index)
    low  = emulator_read_memory(BCPD_addr, 1)
    emulator_write_memory(BCPS_addr, index + 1)
    high = emulator_read_memory(BCPD_addr, 1)
    color_15bit[slot] = (high << 8) | low
```

This matches the D-07 minimum "16 bytes, slots 0..7" phrasing (8 slots × 2 bytes
= first color of each palette). Document the degradation cause in SUMMARY.md.

The fall-back still provides the binding bisect signals (BCPD slot 0 non-zero, OCPD
slot 2 non-zero) but loses the per-color detail within each slot. Prefer the full
64-byte dump when the round-trip is confirmed to work.

---

## 5. Reuse Contract for Plans 04/05/06

Plans 04, 05, and 06 (and any 06a/06b/06c sub-narrow probes) MUST reuse this
protocol verbatim for their BCPD and OCPD dumps. This ensures:
1. All probe dumps are structurally comparable (same JSON shape, same byte ordering).
2. Any deviation from the expected slot-value pattern is a signal, not an artifact
   of differing dump protocols between probes.
3. The baseline (this plan's `bcpd-frame60.json` + `ocpd-frame60.json`) is the
   canonical reference against which each probe's dump is compared.

The dump-loop helper script is described inline in each probe plan's MCP-capture
step; the actual invocations are sequential MCP tool calls (no shell loop script
is required — the executor iterates the 64 calls directly).

A convenience reference script for the dump loop is at:
`.planning/phases/10.2-*/evidence/bisect-probe-0-baseline/dump-script.md`
(the markdown describes the shell/python invocation for ad-hoc use outside the
MCP executor context).
