# Phase 10 Close Audit

**Date:** 2026-05-18  
**Plan:** 10-20 (Phase close: seeds + conditional Phase 10.1 + Phase 13 edits)  
**Status:** SHIPPED

---

## Phase 10 Close Summary

Phase 10 (Port metasprites GBDK example to gbkt) is complete. All 20 plans executed.

The second reference port delivers:
- `metasprite { frame { tile(relX, relY, baseId) } }` DSL primitive with MetaspriteIR
- `moveMetasprite(ref)` ScriptOp + hardware flip + sub-palette cycling via OAM
- `spritePalette { }` DSL + GBC palette slot auto-assignment fix (Plan 16)
- `bgFillCheckerboard()` background helper
- GBC_COMPATIBLE target ROM that eliminates the 70-line tile-flip infrastructure from the reference
- CoffeeGbEmulator GBC frame-ready event fix (Plan 18 Rule 1 auto-fix)

The metasprite primitive (D-04) is validated as a substrate; the three-signal contract is met
at the mechanism layer. Visual parity of the sprite rendering is deferred to Phase 10.1.

---

## Three-Signal Verdict

| Signal | Result | Detail |
|--------|--------|--------|
| ROM size (D-11.1) | **PASS** | 3879 bytes vs reference 3496 bytes; ratio 1.110×; cap 6992 bytes (2×). Framework overhead same pattern as Phase 9; amortizes for non-trivial games. |
| Generated C (D-11.2) | **PASS + notable win** | gbkt shorter on active game-logic surface: 70-line tile-flip infrastructure eliminated by GBC_COMPATIBLE target. Palette slot bug found and fixed in Plan 16. |
| UAT 3/3 behaviors (D-11.3) | **PASS (mechanism) / PARTIAL (visual)** | Behavior 1 (B-press animation): PASS. Behavior 2 (A-press flip): PASS. Behavior 3 (GBC sub-palette): PASS (cyan sprite visible in GBC-mode screenshot). Visual defects D-V1/D-V2 remain in DMG screenshots (garbled tiles, diagonal BG). |

Overall: **SHIPPED — mechanism layer complete; visual parity of asset rendering deferred to Phase 10.1**

---

## Surplus Seeds

4 surplus seeds captured and committed to `.planning/seeds/`:

| Seed | ID | Description | Priority | Scope |
|------|----|-------------|----------|-------|
| D-V1 | SEED-004 | Elephant sprite tiles render corrupted — png2asset tile-data ordering vs MetaspriteVisitor coordinate mismatch or 8x8/8x16 mode mismatch | HIGH | medium |
| D-V2 | SEED-005 | bgFillCheckerboard() emits diagonal stripe pattern, not checkerboard — byte literal encodes diagonal line, not checker | HIGH | small (1-line fix) |
| D-V3 | SEED-006 | `_elephant_subPalette` global variable never assigned in `play_frame()` — MetaspriteVisitor codegen gap | MEDIUM | small |
| D-extra | SEED-007 | GameBuilder.kt:713 same `if (pal.slot >= 0) pal.slot else 0` bug as the fixed SceneBuilder.palette() path | LOW | small |

Seed IDs do not collide with SEED-001..003 (planted during Phases 07.9, 09, 09.1).

---

## Phase 10.1 Status

**Created** — Phase 10.1 placeholder inserted in ROADMAP.md.

Seeds addressed:
- SEED-004 (D-V1: corrupted tile rendering)
- SEED-005 (D-V2: diagonal bg stripes)
- SEED-006 (D-V3: stale _elephant_subPalette global)
- SEED-007 (D-extra: GameBuilder actor-palette slot default)

Goal: resolve D-V1/D-V2/D-V3/D-extra so the metasprites ROM achieves full visual parity
with the GBDK reference, and `_elephant_subPalette` sym-file assertion passes in UAT.

Next step: `/gsd:discuss-phase 10.1` → `/gsd:plan-phase 10.1`.

---

## Phase 13 Routing

### Confirmed existing requirements (Phase 10 evidence)

The following Phase 13 requirements (items 1/2/3, from Phase 09.3 audit) were **confirmed**
by Phase 10's PHASE-13 markers:

1. **Typed Cartridge enum** — confirmed at `Metasprites.kt:13, 63` (`// PHASE-13: replace CARTRIDGE_ROM_ONLY with typed Cartridge.ROM_ONLY`)
2. **Single-frame if/unless primitive** — confirmed at `Metasprites.kt:338, 365, 375` (3 independent `whenever`-as-if patterns in the frame handler)
3. **Fixed-point sub-pixel abstraction** — confirmed at `Metasprites.kt:73, 388` (posX/posY as i16Var with manual `shr 4` conversion)

No edits needed for items 1/2/3 — they are already in ROADMAP.md Phase 13.

### NEW requirements added (Phase 10 → Phase 13)

Two new requirements added to Phase 13 ROADMAP.md:

**Requirement 4: `MetaspriteBuilder.sprite()` method**
- Surfaced at `Metasprites.kt:130` and `Metasprites CLAUDE.md §"PHASE-13 TODOs" item 1`
- The `sprite(asset("sprites/elephant.png"))` call inside `metasprite { }` cannot compile
- Asset loaded implicitly by pipeline convention; explicit binding deferred to Phase 13
- Impact: missing IDE navigation, opaque asset wiring, inconsistency with `actor { sprite(...) }` pattern

**Requirement 5: Explicit-slot `palette()` DSL for multi-palette scenes**
- Surfaced at `Metasprites.kt:92, 317` and `Metasprites CLAUDE.md §"PHASE-13 TODOs" item 2`
- Plan 16 fixed the `else 0` bug (auto-increment slot); but there is no API for authors to
  specify explicit GBC OBP slot numbers (e.g., `palette(enemyPal).intoSlot(3)`)
- The auto-increment fix is correct for sequential declarations; explicit-slot is a separate
  ergonomics gap
- Impact: games with non-sequential palette slot assignments cannot express intent in DSL

---

## Lessons for Future Ports

1. **Visual UAT must capture screenshots before mechanism close.** Plans 17/18 captured
   mechanism assertions (variable checks) correctly but deferred human visual review. D-V1
   and D-V2 were caught by human review of the screenshots — the JVM-tier and MCP-tier
   assertions were not sufficient to surface them. For Phase 11/12 ports, schedule a visual
   inspection checkpoint _before_ the three-signal close plan.

2. **png2asset byte ordering is a new source of defects.** The Phase 10 corpus introduced
   actual sprite-asset data transcription (from `png2asset` output to `MetaspriteIR`).
   This is a class of defects the simple_physics port (Phase 9) did not exercise (no sprite
   data in phys.c). Future ports with sprite/tile data should include a hex-compare of
   `elephant_tiles[]` vs reference as a first-class verification step.

3. **GBC mode introduces a new test tier.** Behavior 3 required `gbcMode=true` in
   `AgentSessionConfig`. The GBC frame-ready event bug in CoffeeGbEmulator (D-V4, Rule 1
   auto-fix in Plan 18) was a direct consequence of not having a GBC-mode test in any prior
   phase. Future phases targeting GBC features should add a GBC-mode smoke test early.

4. **Emitter global vs local variable sync.** D-V3 (stale `_elephant_subPalette` global)
   is a pattern risk: visitor methods that emit both a local variable (correct, used by OAM
   call) and a debug global (incorrect, never synced) will silently pass mechanism tests
   but fail sym-file assertions. Future visitors that declare debug globals should also
   emit the sync assignment in the same code block.

5. **The single-named-bug doctrine works.** Phase 10's hard scope cap prevented expanding
   into D-V1/D-V2/D-V3 mid-port. All four surplus defects are cleanly bounded and fit into
   a well-scoped Phase 10.1. The surplus-to-seeds pipeline operationally validated the
   Phase 9 precedent (Phase 09.1).
