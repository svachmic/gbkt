# Phase 12 — Three-Signal Oracle Comparison

Per CONTEXT D-17: this artifact is the primary input the verifier reads to
declare Phase 12 complete. Three signals (ROM size, generated-C diff, UAT
5-anchor verdict) are computed against the reference platformer_template
ROM/source tree; the 4th signal (bank-layout) is in `bank-layout-signal.md`.

**Inputs:**
- gbkt build: `gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb`
  + `build/gbkt/generated/{main,bank1,zone_bank2}.c` + `sprites/player.c`
- Reference ROM: `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/build/gb/platformer_template.gb`
  (built via `make gb` per `evidence/reference/BUILD.md`; binaries gitignored)
- Reference C tree: `${REF}/src/{main,player,level,camera,common}.c`
  + `${REF}/gen/gb/src/{World1Tileset,World2Tileset,World1Area1,World1Area2,World2Area1,TitleScreen,NextLevel,PlayerCharacterSprites}.c`
- 5 UAT anchor SUMMARYs (12-19, 12-20, 12-21, 12-22, 12-23)

---

## Signal 1: ROM size

**Measurement (reproducible):**

```bash
GBKT=/Users/michalsvacha/GitHub/personal/gbkt/gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb
REF=/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/build/gb/platformer_template.gb
stat -f '%z' "$GBKT"   # gbkt ROM bytes
stat -f '%z' "$REF"    # reference ROM bytes
```

| ROM       | Bytes  | KB    | MBC      | Note |
| --------- | ------:| -----:| -------- | ---- |
| gbkt      | 65 536 |  64   | MBC1     | 4 banks (HOME + 3 numbered); cartridge byte set via `config { cartridge = "MBC1" }` per D-claude-3 |
| reference | 32 768 |  32   | MBC5+RAM+BATTERY (`0x1B`) | 2 banks (HOME + 1); `-autobank` + `-Wm-yoA` (4× ROM banks) at link time still produced a 32 KB ROM because the actual content fit |
| ratio     | 2.000  | —     | —        | gbkt / reference |

**Verdict: GREEN (boundary)** — ratio is exactly 2.000, which is **at** the
ROADMAP three-signal contract's `≤ 2×` ceiling (per CONTEXT D-17 #1).
Drivers of the 2× gap (informational, not regressions):

- gbkt emits per-zone tileset + tilemap as **separate** C arrays in bank 2
  (5 tilesets + 5 tilemaps each occupying distinct symbol space — see
  `bank-layout-signal.md`), while the reference's png2asset `-source_tileset`
  flag lets World1Area1/World1Area2 share the world1-tileset and
  World2Area1 share the world2-tileset (2 tilesets + 5 tilemaps).
  The gbkt asset pipeline (`ConvertZoneTilesetsTask`) does not yet exploit
  `-source_tileset` deduplication. This is a known optimization opportunity,
  not a parity failure.
- gbkt's MBC1 + 64 KB allocation rounds up to the next power-of-two ROM
  size; the actual `l__CODE` total is 0x32CD = 13 005 bytes (see
  `bank-layout-signal.md`), so the runtime code-size delta vs the
  reference's 0x335C = 13 148 bytes is **−143 bytes** (gbkt is slightly
  smaller in raw code). The ROM-size delta is dominated by ROM-bank
  alignment + the 2-bank vs 4-bank split, not by codegen verbosity.

**Remediation note:** None required for Phase 12 closure (ratio meets
`≤ 2×`). Surface a polish-phase seed if/when the framework wants to push
under 1.5× via `-source_tileset` deduplication or by collapsing
`zone_bank2.c` into bank 1 (currently bank 2 has data but the
`zone_bank2.c` stub itself is 4 LOC — see Signal 2).

---

## Signal 2: Generated-C diff

**Measurement (reproducible):**

```bash
GBKT_GEN=/Users/michalsvacha/GitHub/personal/gbkt/gbkt-examples/platformer-template/build/gbkt/generated
REF_DIR=/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template
find "$GBKT_GEN" -name '*.c' | xargs wc -l
{ find "$REF_DIR/src" -name '*.c'; find "$REF_DIR/gen" -name '*.c'; } | xargs wc -l
```

### gbkt generated (4 files, 940 LOC total)

| File                   | LOC | Role                                                     |
| ---------------------- | ---:| -------------------------------------------------------- |
| `main.c`               | 593 | HOME-bank dispatch + scene table + platformer physics + camera_update + setup_current_level + level-switch guard + sprite render + gameplay_enter + gameplay_frame |
| `bank1.c`              |  78 | `title_enter`, `title_frame`, `nextLevel_enter`, `nextLevel_frame` (BANKED scene callbacks) |
| `zone_bank2.c`         |   4 | `#pragma bank 2` + `#include "game.h"` stub (zone data symbols emitted from `_zone_*_tileset.h` headers, which the linker places into bank 2 per `BankingAnalysisPass`) |
| `sprites/player.c`     | 265 | 6-frame player metasprite tile data + metasprite frame descriptors (post-12.5 png2asset layout: mode=`SPR8x16`, pivot, frameSize via sprite() block flags) |
| **Total**              | **940** | |

### Reference (13 files, 1 450 LOC total)

| File                                  | LOC | Role                                                                  |
| ------------------------------------- | ---:| --------------------------------------------------------------------- |
| `src/main.c`                          |  93 | main loop, scene dispatch, level-switch guard                         |
| `src/player.c`                        | 355 | physics, input, jump, hflip, metasprite render                        |
| `src/level.c`                         | 153 | setup_current_level, level descriptors, tilemap-collision lookups     |
| `src/camera.c`                        |  83 | camera_update + column-scroll math                                    |
| `src/common.c`                        |  66 | shared types + helper macros                                          |
| `gen/gb/src/PlayerCharacterSprites.c` | 203 | png2asset duck sprite tile data + 6-frame metasprite descriptors      |
| `gen/gb/src/World1Tileset.c`          |  55 | png2asset world1-tileset tile data                                    |
| `gen/gb/src/World2Tileset.c`          |  98 | png2asset world2-tileset tile data                                    |
| `gen/gb/src/World1Area1.c`            |  46 | png2asset tilemap for level 1 (references World1Tileset via `-source_tileset`) |
| `gen/gb/src/World1Area2.c`            |  46 | png2asset tilemap for level 2                                         |
| `gen/gb/src/World2Area1.c`            |  46 | png2asset tilemap for level 3                                         |
| `gen/gb/src/TitleScreen.c`            | 148 | png2asset title-screen tile data + tilemap (combined: `-map` without `-maps_only`) |
| `gen/gb/src/NextLevel.c`              |  58 | png2asset NEXT LEVEL card tile data + tilemap                         |
| **Total**                             | **1 450** | |

### Side-by-side function-cluster mapping

| Reference cluster                 | LOC | gbkt counterpart                                                                                  | LOC | Delta |
| --------------------------------- | ---:| ------------------------------------------------------------------------------------------------- | ---:| -----:|
| `src/main.c` (scene dispatch + game-loop)        |  93 | `main.c` scene table + main loop + level-switch guard subset                                                                | ~120 | +27 (gbkt's dispatch is auto-generated; small overhead from the scene-id macros and the framework's main-loop scaffolding) |
| `src/player.c` (physics + input + render)        | 355 | `main.c` platformer_physics_update + gameplay_frame DSL-lowering + sprite-render cEmit-fudge                                  | ~250 | −105 (declarative DSL reduces boilerplate) |
| `src/level.c` (setup_current_level + descriptors)| 153 | `main.c` setup_current_level + per-level config-table primitive (D-12 platformerPhysics override)                             | ~80  | −73 (config-table primitive collapses per-level descriptor switch into a table lookup)         |
| `src/camera.c` (camera_update + column-scroll)   |  83 | `main.c` platformer_camera_update body emitted by PlatformerVisitor                                                           | ~60  | −23 |
| `src/common.c` (helpers/macros)                  |  66 | Folded into `main.c` + framework headers — no gbkt counterpart file                                                            | 0   | −66 |
| Reference png2asset gen files (8 files)          | 700 | gbkt: `sprites/player.c` (265) + zone data via `_zone_*_tileset.h` headers linked into bank 2 (sizes in `bank-layout-signal.md`) | ~265 +headers | gbkt's `ConvertZoneTilesetsTask` emits tile data into `.h` headers + per-zone `.c` stubs; counted only the visible `.c` LOC |
| **Reference total**                              | **1 450** | **gbkt total (visible `.c` only)**                                                                                | **940** | **−510 LOC (gbkt is ~35% shorter in C surface)** |

### Where gbkt is NOT shorter / clearer (RESEARCH §"seed" trigger)

Per CONTEXT D-17 #2: "where gbkt is NOT shorter/clearer → seed".

| Surface                                              | Reference (imperative)                                          | gbkt (declarative)                                                          | Verdict | Seed? |
| ---------------------------------------------------- | --------------------------------------------------------------- | --------------------------------------------------------------------------- | ------- | ----- |
| Scene dispatch                                       | Manual `switch(scene)` in main.c                                | Auto-generated scene table + dispatcher in main.c                           | gbkt shorter (~27 LOC overhead but no per-scene boilerplate) | — |
| Per-level config                                     | Hand-written `level_descriptors[]` in level.c                   | `platformerPhysics { }` override DSL → config-table primitive               | gbkt shorter (−73 LOC) | — |
| Camera-relative metasprite render                    | One-liner in player.c using `(player_x - camera_x)` directly    | Save/restore cEmit-fudge around `move_metasprite_ex` (`PlatformerTemplate.kt:435-453`) | gbkt LONGER + uglier (cEmit-fudge papering over PlatformerVisitor's lack of screen-relative render auto-emission) | YES — already captured in `SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS.md` |
| Input → playerVx wiring                              | Implicit in player.c update_player()                            | `whenever(dpad.right.held) { playerVx set 127 }` clauses in user DSL        | gbkt comparable (declarative wins on clarity; 4 LOC vs 8 LOC imperative)    | — |
| `platformer_camera_update` call site                 | Direct call in main game loop                                   | `cEmit("platformer_camera_update();")` in user DSL (PlatformerVisitor emits body but no call) | gbkt LONGER (user must manually call what the framework defined)            | YES — same SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS |
| Main()-loop level-switch guard                       | Reads `next_level` + calls `setup_current_level` in main.c      | Codegen-emitted guard at GBDKPipelineV2 — RACES card-art VRAM write (DEFECT-1) | gbkt currently INCORRECT (visual symptom on 02-nextlevel-card.png) | YES — escalated to **Phase 12.6** (codegen fix) per Plan 12-23 OPTION A |
| Level-end trigger position check                     | Reference latches one-shot trigger                              | gbkt re-fires SAME-FRAME after level switch because `_playerX` preserved (DEFECT-2) | gbkt currently INCORRECT (visual symptom on 03-level-2.png) | YES — escalated to **Phase 12.6** (codegen fix) per Plan 12-23 OPTION A |
| World1Area1 / World1Area2 tileset deduplication       | png2asset `-source_tileset` lets two tilemaps share one tileset | gbkt emits 3 separate tilesets (world1Area1, world1Area2, world2Area1) — no dedup | gbkt LARGER (ROM-size contributor — see Signal 1)                          | YES — file `SEED-PHASE-12-PLATFORMER-TILESET-DEDUP.md` (NEW seed below)               |

**Verdict: GREEN (informational)** — overall gbkt C surface is ~35%
shorter than the reference, consistent with the framework's declarative
shape. Three localized regressions surfaced as seeds (auto-emission gaps,
codegen DEFECT-1/2 → Phase 12.6, tileset dedup polish opportunity). None
are blockers for Phase 12 closure (the codegen defects' visual fix
lands in Phase 12.6's retro-GREEN re-shoot of anchor 5; gbkt's
declarative shape is correct, just incomplete in 3 narrow surfaces).

**Remediation:**
- DEFECT-1 + DEFECT-2: routed to **Phase 12.6** (already inserted by orchestrator).
- Auto-emission gaps (camera-relative render, camera_update call site):
  routed to `SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS.md` per
  Plan 12-21.
- Tileset dedup: orthogonal polish; revival when `ConvertZoneTilesetsTask`
  is next touched (Phase 13 framework-primitives candidate).

---

## Signal 3: UAT 5-anchor verdict

Each anchor's row cites the closing plan's SUMMARY for evidence + the
human-verify approval. **Anchor 5 is honestly recorded as JVM-tier GREEN
but visual-RED** per Plan 12-23's OPTION A close — the 3 round-2 PNGs
serve as Phase 12.6's RED baseline, and anchor 5 retro-GREEN closure
follows after Phase 12.6 ships (same pattern as Plan 12-22's Phase 12.3 +
12.5 retro-close).

| Anchor | Description                       | JVM Test (PlatformerTemplateUatTest) | Screenshots                                                                                                                  | Visual Verdict      | Status                | Closing plan |
| ------ | --------------------------------- | ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------- | ------------------- | --------------------- | ------------ |
| 1      | title → gameplay scene transition | `anchor1Title_to_Gameplay` PASS      | 2 PNGs in `evidence/uat-screenshots/anchor-1/` (01-title.png, 02-gameplay.png)                                               | **Y** (human-verify APPROVED 2026-05-23T11:53Z, post-12.2 re-shoot) | **GREEN**             | 12-19 |
| 2      | tilemap-collision jump cycle      | `anchor2TilemapCollision` PASS       | 3 PNGs in `evidence/uat-screenshots/anchor-2/` (01-grounded.png, 02-mid-jump.png, 03-landed.png) + variables.txt              | **Y** (human-verify APPROVED 2026-05-23T12:11Z; spawn-position ambiguity captured as `SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY.md`, orthogonal) | **GREEN**             | 12-20 |
| 3      | horizontal scroll                 | `anchor3HorizontalScroll` PASS       | 2 PNGs in `evidence/uat-screenshots/anchor-3/` (01-initial.png, 02-scrolled.png) + variables.txt (camera_x + map_pos_x trace) | **Y** (human-verify APPROVED 2026-05-23T13:05Z, after 3 inline DSL fixes) | **GREEN**             | 12-21 |
| 4      | metasprite walk-cycle + hflip     | `anchor4MetaspriteAnimation` PASS    | 4 PNGs in Phase 12.3 dir at `12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/uat-screenshots/anchor-4/` (01-walk-frame-0/1/2 + 04-facing-left + variables.txt) | **Y** (REQ-3a human-verify APPROVED 2026-05-24 commit `2cd416a4` — duck art renders correctly post-12.5 png2asset fix; REQ-3b mechanical pixel-diff = 8.69% < 10% strict gate, documented as known limitation in 12-22-SUMMARY — variable evidence + REQ-3a is the primary closure path per test assertion message) | **GREEN (retro)**      | 12-22 (retro-close via Phase 12.3 + Phase 12.5) |
| 5      | level switch (gameplay → NextLevel card → level 2) | `anchor5LevelSwitch` PASS (round-2)  | 3 PNGs in `evidence/uat-screenshots/anchor-5/` (01-near-end.png, 02-nextlevel-card.png, 03-level-2.png) + variables.txt (frames_to_trigger=751, next_level_after_trigger=1, current_level_after_guard=1, final_current_level=2 — re-fire is DEFECT-2 symptom) | **N — visual-RED**: 02 shows world1-area2 tilemap (not NEXT LEVEL card) → CODEGEN-DEFECT-1; 03 shows world2-area1 (not world1-area2) → CODEGEN-DEFECT-2. Both confirmed by user 2026-05-25 as genuine codegen defects, not test-calibration misses. | **JVM-tier GREEN + visual-RED → routed to Phase 12.6** (RED baseline locked; anchor 5 retro-GREEN re-shoot lands when 12.6 ships) | 12-23 (CLOSED via user OPTION A on 2026-05-25 — escalation to **Phase 12.6** `main-loop-level-switch-codegen-fix`) |

### Per-anchor evidence cross-references

- **Anchor 1:** `12-19-SUMMARY.md` — `733770d6` (test impl), `e7e1bd48` (inline codegen fix: gameplay_enter zone tileset/tilemap wiring), `29c6fa1a` (Phase 12.2 escalation for synthetic-tilemap defect)
- **Anchor 2:** `12-20-SUMMARY.md` — single commit; `SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY.md` filed
- **Anchor 3:** `12-21-SUMMARY.md` — single commit; 3 inline DSL fixes; `SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS.md` filed
- **Anchor 4:** `12-22-SUMMARY.md` (retro-close) — production work in Phase 12.3 (PlatformerVisitor framework-level `_walkFrameIdx` + `platformerInput { }` binder) + Phase 12.5 (`png2asset` mode/pivot/frameSize fix) + commits `41570828` (RED→GREEN re-shoot) + `2cd416a4` (REQ-3a human-verify approval)
- **Anchor 5:** `12-23-SUMMARY.md` (CLOSED via OPTION A) — `91d028d3` (round-2 GREEN + defects surfaced), `96dcf891` (initial round-2 SUMMARY), Phase 12.6 inserted by orchestrator at base commit `e4ff4fc5`; `SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS.md` filed orthogonally (Issue A)

### Overall UAT verdict

**RED — anchor 5 visual-RED, routed to Phase 12.6.**

Anchors 1, 2, 3 are visually GREEN end-to-end. Anchor 4 is GREEN via retro-close
(production work in Phases 12.3 + 12.5; REQ-3a primary closure signal —
duck art human-verified; REQ-3b 8.69% known limitation, documented closure
path). Anchor 5 has TWO confirmed codegen defects (DEFECT-1: main-loop
guard overwrites NEXT LEVEL card VRAM; DEFECT-2: preserved `_playerX`
re-fires level-end trigger same-frame). The 3 round-2 PNGs are the locked
RED baseline for Phase 12.6.

**Remediation:** Phase 12.6 (`main-loop-level-switch-codegen-fix`) lands the
codegen fixes for both defects. A follow-up plan re-runs
`:gbkt-examples:platformer-template:test --tests "PlatformerTemplateUatTest.anchor5LevelSwitch"`
against the post-12.6 ROM and verifies the round-3 PNGs match the
intended visual contract:
- `02-nextlevel-card.png` visually shows the NEXT LEVEL card art (not a tilemap)
- `03-level-2.png` visually shows world1-area2 (grass tilemap, distinct from level 1)
- `anchor5-variables.txt`: `final_current_level == 1` (not 2) without LEFT-backoff hacks

---

## Three-Signal Overall Verdict

| Signal                  | Verdict | Notes |
| ----------------------- | ------- | ----- |
| 1 — ROM size            | **GREEN (boundary)** | ratio = 2.000 exactly; meets `≤ 2×` ceiling |
| 2 — Generated-C diff    | **GREEN (informational)** | gbkt ~35% shorter overall; 3 narrow regressions seeded |
| 3 — UAT 5-anchor verdict| **RED (anchor 5 visual-RED → Phase 12.6)** | Anchors 1–4 GREEN; anchor 5 JVM-GREEN + visual-RED, baseline locked for Phase 12.6 |

**Overall: NOT-YET-GREEN.** Phase 12 closure is gated on Phase 12.6
landing + anchor 5 retro-GREEN re-shoot. The 3-signal contract holds for
Signals 1 + 2; Signal 3's anchor 5 visual-RED is the **only** outstanding
defect.

**Path to GREEN:**
1. Phase 12.6 ships (codegen fixes for DEFECT-1 + DEFECT-2 — orchestrator-inserted at base commit `e4ff4fc5`).
2. Follow-up plan re-shoots anchor 5 against post-12.6 ROM; updates the row above to **GREEN (retro)**.
3. Phase 12 final verifier then declares the phase complete and records
   both defects in PHASE-VERIFICATION.md under "Known Defects (routed to
   12.6) — RESOLVED post-retro-shoot".

---

*Generated: 2026-05-25 by Plan 12-24*
*Reference ROM build: 2026-05-21 (sha256 `7f4c5095d195446019004a7a07d8fb6ee75af073ef2ab7012c62c5cb9bd7d587` per `evidence/reference/BUILD.md` capture table; today's metric uses the same on-disk binary at `${REF}/build/gb/platformer_template.gb`)*
*gbkt ROM build: 2026-05-25 07:40Z*
