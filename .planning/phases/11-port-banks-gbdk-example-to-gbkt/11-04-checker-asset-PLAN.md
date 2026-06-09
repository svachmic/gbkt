---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 04
type: execute
wave: 0
depends_on: []
files_modified:
  - gbkt-examples/banks/res/tiles/checker.png
autonomous: true
requirements:
  - BANK-ASSET-TILESET   # Zone needs a real tileset asset to fire set_bkg_tiles (CONTEXT D-claude-3, anchor 2 visual evidence)
user_setup: []
must_haves:
  truths:
    - "`gbkt-examples/banks/res/tiles/checker.png` exists and is a valid PNG"
    - "PNG content is non-trivial (not all-blank) so `set_bkg_tiles` actually fires per CONTEXT D-claude-3"
  artifacts:
    - path: "gbkt-examples/banks/res/tiles/checker.png"
      provides: "Minimal checker tileset for the banked zone in Plan 11-05"
      contains: "valid PNG header"
  key_links:
    - from: "gbkt-examples/banks/res/tiles/checker.png"
      to: "Banks.kt zone(\"play_zone\") { tileset(asset(\"tiles/checker.png\")) }"
      via: "asset() resolution during pipeline build (Plan 11-05)"
      pattern: "asset\\(\"tiles/checker\\.png\""
---

<objective>
Create the minimal tileset PNG asset that Banks.kt's zone references. The PNG must be a valid 8×8 (or 16×16) image with a 2-colour checker pattern — non-trivial pixels so `set_bkg_tiles` is actually called by the generated C, which is the codegen path UAT anchor 2 + JVM invariant INV-2 verify.

Purpose: Decouple asset creation from DSL authoring (Plan 11-05). The asset pipeline runs during `generateC`; without this file, Plan 11-05's `:gbkt-examples:banks:generateC` fails.

Output: `gbkt-examples/banks/res/tiles/checker.png` (binary PNG, ≤ 1 KB).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-RESEARCH.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-PATTERNS.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Generate checker.png</name>
  <read_first>
    - 11-PATTERNS.md §"gbkt-examples/banks/res/tiles/checker.png" (lines 419–432 — directory layout + content guidance)
    - 11-CONTEXT.md D-claude-3 (zone contents — 1 small asset; planner picks shape; must be non-trivial)
    - gbkt-examples/dungeon/res/ (look for existing tileset structure as a reference if unsure of pipeline expectations)
  </read_first>
  <files>gbkt-examples/banks/res/tiles/checker.png</files>
  <action>
    Generate a 16×16-pixel PNG with a 2-tile checkerboard pattern (each tile is 8×8 pixels; total image is 2 tiles wide × 2 tiles tall = 16×16 pixels; alternating black/white 8×8 squares).

    Use one of these methods (pick the one available on the host):

    **Method A — Python (preferred, deterministic):**
    ```
    mkdir -p gbkt-examples/banks/res/tiles
    python3 -c "
    from struct import pack
    import zlib
    # 16x16 PNG, 2-colour checker, 8x8 tile blocks
    w, h = 16, 16
    raw = bytearray()
    for y in range(h):
        raw.append(0)  # filter type none
        for x in range(w):
            # checker: tile = (x // 8) ^ (y // 8); 0 = white, 1 = black
            tile_parity = (x // 8) ^ (y // 8)
            raw.append(0xFF if tile_parity == 0 else 0x00)
            raw.append(0xFF if tile_parity == 0 else 0x00)
            raw.append(0xFF if tile_parity == 0 else 0x00)
    def chunk(t, d):
        return pack('>I', len(d)) + t + d + pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)
    sig = b'\\x89PNG\\r\\n\\x1a\\n'
    ihdr = pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)  # 8-bit RGB
    idat = zlib.compress(bytes(raw), 9)
    with open('gbkt-examples/banks/res/tiles/checker.png', 'wb') as f:
        f.write(sig + chunk(b'IHDR', ihdr) + chunk(b'IDAT', idat) + chunk(b'IEND', b''))
    print('Wrote gbkt-examples/banks/res/tiles/checker.png')
    "
    ```

    **Method B — ImageMagick (fallback if python3 unavailable):**
    ```
    mkdir -p gbkt-examples/banks/res/tiles
    convert -size 16x16 pattern:checkerboard -monochrome gbkt-examples/banks/res/tiles/checker.png
    ```

    Validation: the file must (a) exist, (b) be a valid PNG (first 8 bytes are PNG signature `89 50 4E 47 0D 0A 1A 0A`), (c) be non-empty (≥ 70 bytes for the smallest valid PNG), (d) have a non-blank pixel pattern (file size > 70 bytes implies the IDAT chunk encodes more than uniform colour).

    Do NOT commit a placeholder text file or a 0-byte PNG — the asset pipeline will reject it.
  </action>
  <verify>
    <automated>test -f gbkt-examples/banks/res/tiles/checker.png && head -c 8 gbkt-examples/banks/res/tiles/checker.png | od -An -tx1 | grep -q "89 50 4e 47 0d 0a 1a 0a" && test $(stat -f%z gbkt-examples/banks/res/tiles/checker.png 2>/dev/null || stat -c%s gbkt-examples/banks/res/tiles/checker.png) -ge 70</automated>
  </verify>
  <acceptance_criteria>
    - File `gbkt-examples/banks/res/tiles/checker.png` exists
    - First 8 bytes match PNG signature (`89 50 4E 47 0D 0A 1A 0A`)
    - File size ≥ 70 bytes (rules out the empty/degenerate PNG)
    - File size ≤ 1024 bytes (sanity check — minimal asset, not a large blob accidentally committed)
  </acceptance_criteria>
  <done>Asset present; Plan 11-05 can reference `asset("tiles/checker.png")` in the zone DSL.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| External tool (python3/ImageMagick) → PNG file | Generation runs locally; no network input |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-06 | Tampering | PNG payload | mitigate | PNG generated by deterministic script with explicit pixel data; size bounds enforced in acceptance |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | python3 + ImageMagick are host tools, not package installs |
</threat_model>

<verification>
  - Automated verify command passes.
  - `file gbkt-examples/banks/res/tiles/checker.png` reports `PNG image data, 16 x 16` (or 8x8 if alternative size chosen).
</verification>

<success_criteria>
  - 1 PNG file created at the expected path.
  - Plan 11-05's `:gbkt-examples:banks:generateC` will succeed (verified there).
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-04-SUMMARY.md` listing: 1 file created, byte size, dimensions, generation method used.
</output>
