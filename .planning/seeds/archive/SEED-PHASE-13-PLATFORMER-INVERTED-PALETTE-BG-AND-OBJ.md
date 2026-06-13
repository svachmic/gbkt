# SEED: Phase 13 — platformer-template colors inverted (BG + OBJ palette polarity)

> **Triage:** VERIFIED-ALREADY-FIXED — [TRIAGE.md#SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ](.planning/phases/16-seed-triage/TRIAGE.md#SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ) · 2026-06-12

**Created:** 2026-06-04 (during Phase 13.4 `--gaps-only` execution — user UAT heads-up)
**Origin phase:** 13.4 (framework-primitives-zone-scene-ergonomics-inserted), surfaced while closing D-04
**Source:** User — "the world and the character in the platformer has inverted colors" … "could be linked to the same issue from Phase 13.6."
**Status:** OPEN — routed to a NEW diagnose-first sibling phase (user decision 2026-06-04, AskUserQuestion: "New diagnose-first phase").
**Evidence:** `.planning/seeds/evidence/platformer-gbc-inverted-colors-2026-06-04.png` (GBC-mode gameplay screenshot).
**Blast radius:** WIDE — spans BOTH the background palette pipeline (`gbkt-gradle-plugin` `ConvertZoneTilesetsTask` → `set_bkg_palette` codegen) AND the sprite palette pipeline (`ConvertSpritesTask` → `set_sprite_palette`). A fix must be verified against the platformer-template (BG world1/world2 tilesets + player metasprite) without reverting the 13.3-22/24 ascending-ramp polarity fix or the Phase 12.9 D2a `-keep_palette_order` player-sprite fix. Per `feedback_route_to_proper_phase_when_blast_radius_is_wide`: diagnose-first, no inline fix.

## Symptom

In GBC color mode the platformer-template gameplay renders with light↔dark **inverted** palettes:
- **World (BG):** sky and ground are a near-uniform dark-green/olive wash; the sky (tile 0x11) should read LIGHT, not dark. Foreground "blocks" are lighter green than the sky — the value relationship looks inverted.
- **Character (OBJ):** the player metasprite is a dark body with a light halo/outline — looks inverted (body should be light).

## Regression window (important)

The platformer-template colors were **APPROVED as recently as Phase 12.9** (12.9-08h G3: user "all pictures are perfect"). So this is a **Phase 13.x regression**, NOT a long-standing defect. Prime suspects:
- **Phase 13.3 "Color refactor"** (`gbc()`/`GbcColor`/`gbcHex()` → `Color` namespace migration, plans 13.3-04/07/08/11) — the same refactor the user blamed for the 13.3 elephant inversion ("Probably Color refactor broke it"). 13.3-21/22/23 fixed the *metasprites-example* gray sprite-palette polarity (ascending ramp), but may not have covered the platformer-template's BG tileset palettes or its player metasprite palette.
- Possible interaction with the per-zone `set_bkg_palette` wiring added in Phase 12.9 (W5) and the `-keep_palette_order` asset-pipeline pin.

## Root-cause hypotheses to diagnose (codegen-read + live GBC MCP probe)

1. **(BG) palette-VALUE polarity:** the generated `_zone_world1Area1Zone_tileset_palettes[16]` / `world2` BG palette arrays carry RGB555 entries in inverted luminance order vs the source PNG PLTE (light↔dark swapped), so `set_bkg_palette` uploads an inverted ramp — mirroring the 13.3 elephant gray-ramp polarity bug but on the BG path.
2. **(OBJ) sprite-palette polarity:** the player metasprite palette array is similarly inverted (or the 13.3 ascending-ramp fix didn't reach the platformer player sprite's palette authoring site).
3. **Color-namespace migration scaling/order:** the `Color`/`gbc()` migration emits wrong channel order or 8-bit↔5-bit scaling for one or both pipelines (FALSIFY against the byte-neutral finding from 13.3-21: `gbc(r,g,b)` and `Color.rgb555(r,g,b)` were proven the SAME bit layout — so re-confirm this holds for the platformer assets).

## Relationship to Phase 13.6

Phase 13.6 (sprite-pipeline transparency — non-zero tRNS → OBJ index 0) is the closest existing sibling but is **sprite-only**; it does NOT cover the **background** inversion. This seed therefore needs its OWN phase (BG + OBJ palette polarity), kept compatible with — and not reverting — both the 13.3-22/24 ascending-ramp fix and the 13.6 tRNS work.

## Suggested phase shape (diagnose-first; mirrors 13.3-21..23)

1. **DIAGNOSE** — codegen-read the platformer-template generated BG + OBJ palette arrays vs the source PNG PLTE luminance order; live GBC MCP probe (read BCPD/OCPD palette RAM) vs the rendered frame; lock ONE polarity root cause per pipeline; capture before/after probes. Compare against the Phase 12.9-approved reference if recoverable.
2. **FIX** — correct the polarity at the asset-pipeline/codegen source (NOT by re-authoring each game's palettes); RED→GREEN emission test(s) locking the corrected BG + OBJ palette byte order; clean platformer-template `:buildRom` EXIT 0; do NOT revert 13.3-22/24 or 12.9 D2a.
3. **RE-BIND** — live GBC MCP reshoot of gameplay (world + player) + title; binding user APPROVED verdict per the Visual Evidence Rule.

## Verification anchors

- The 7-target byte-identity / ROM-smoke sweep must stay green for non-platformer targets.
- Metasprites example elephant (13.3) must remain correctly non-inverted (regression guard).
