# Phase 14 — Example Audit Table

**Captured:** 2026-06-06
**Branch:** feat/d_and_d_gaps
**Purpose:** Per-example KEEP/RETIRE verdict (Req 1, D-01..D-04). Empirical build + live MCP run-check.

## Build + Run Results (Task 1 build + Task 2 live MCP run-check)

| example | generateC | buildRom | run (live MCP) | KEEP/RETIRE | evidence |
|---------|-----------|----------|----------------|-------------|----------|
| pong | EXIT 0 | EXIT 0 (PASS*) | LIVE — title→game on START; ball + paddle move, AI paddle tracks, "P1:0 P2:0" HUD renders | **KEEP** | pong-boot.png, pong-after.png |
| breakout | EXIT 0 | EXIT 0 | LIVE — title→game on START; paddle moves on RIGHT, ball + brick wall + "SCORE/LIVES" HUD render | **KEEP** | breakout-boot.png, breakout-after.png |
| racer | EXIT 0 | EXIT 0 | BROKEN — car responds to input but racing is non-functional: static center track box does NOT scroll (camera_x/y stay 0, no follow), car drives off the top edge, lap_count + checkpoint_idx stuck at 0 | **RETIRE (D-03)** | racer-boot.png, racer-after.png (after shows car driven off the static track box) |
| simple-physics | EXIT 0 | EXIT 0 | LIVE — bidirectional sub-pixel physics: RIGHT accelerates spdX to +60, LEFT reverses to −50, sprite tracks position | **KEEP** | simple-physics-boot.png, simple-physics-after.png |
| metasprites | EXIT 0 | EXIT 0 | LIVE — 31-sprite elephant metasprite renders on checkerboard, moves cohesively right under input (spdX physics) | **KEEP** | metasprites-boot.png, metasprites-after.png |
| metasprites-stress | EXIT 0 | EXIT 0 | LIVE — title→play on START; both elephant + tiger metasprites compose into OAM (40 sprites, hiwater=31, WR-05 evidence). Throwaway codegen oracle (D-06/D-07): binding evidence is buildRom EXIT 0 + linkable ROM | **KEEP** | metasprites-stress-boot.png, metasprites-stress-after.png |
| banks | EXIT 0 | EXIT 0 | LIVE — cross-bank scene navigation fires on START (title scene_2 → play scene_1), banked zone tilemap tile renders. Codegen-exercise demo per PLAYBOOK (no gameplay by design); success = anchor evidence | **KEEP** | banks-boot.png, banks-after.png |
| platformer-template | EXIT 0 | EXIT 0 | LIVE — title ("GBDK-2020 PLATFORMER TEMPLATE") → level on START; player metasprite spawns grounded, traverses + jumps under RIGHT+A (Vx=128, airborne Vy, walkFrame animates), level tilemap + platforms render (GBC palette) | **KEEP** | platformer-template-boot.png, platformer-template-after.png |

**Notes:**
- pong ROM hash nondeterminism is a known pre-existing sdcc/lcc issue — buildRom EXIT 0 = PASS* (generated C is the real gate per project memory).
- racer buildRom EXIT 0 — the "known-dead" status from D-03 refers to runtime failure (gameplay broken), confirmed during Task 2 MCP run-check. Build passes; run-check decides RETIRE verdict.
- metasprites and metasprites-stress require `gbcMode: true` + `.noi` symFile in MCP emulator_start.
- platformer-template requires `gbcMode: true` + `.noi` symFile in MCP emulator_start.

## ROM Paths

| example | ROM path | symFile (.noi) |
|---------|----------|----------------|
| pong | gbkt-examples/pong/build/gbkt/output/pong.gb | gbkt-examples/pong/build/gbkt/output/pong.noi |
| breakout | gbkt-examples/breakout/build/gbkt/output/breakout.gb | gbkt-examples/breakout/build/gbkt/output/breakout.noi |
| racer | gbkt-examples/racer/build/gbkt/output/racer.gb | gbkt-examples/racer/build/gbkt/output/racer.noi |
| simple-physics | gbkt-examples/simple-physics/build/gbkt/output/simple-physics.gb | gbkt-examples/simple-physics/build/gbkt/output/simple-physics.noi |
| metasprites | gbkt-examples/metasprites/build/gbkt/output/metasprites.gb | gbkt-examples/metasprites/build/gbkt/output/metasprites.noi |
| metasprites-stress | gbkt-examples/metasprites-stress/build/gbkt/output/metasprites-stress.gb | gbkt-examples/metasprites-stress/build/gbkt/output/metasprites-stress.noi |
| banks | gbkt-examples/banks/build/gbkt/output/banks.gb | gbkt-examples/banks/build/gbkt/output/banks.noi |
| platformer-template | gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb | gbkt-examples/platformer-template/build/gbkt/output/platformer-template.noi |

## Run-Check Results (Task 2 — live MCP emulator)

Run-check performed by the orchestrator via the live `gbkt-emulator` MCP server
(`mcp__gbkt-emulator__*`) — the spawned gsd-executor subagent has those tools stripped
(restricted `tools:` frontmatter, upstream bug), so per D-01 (live emulator, no JVM proxy)
the orchestrator drove the run-checks directly. GBC examples (metasprites, metasprites-stress,
platformer-template) started with `gbcMode: true` + `.noi` symFile per Pitfall 4 (avoids the
DMG green-tint false positive). Each example: boot screenshot → one input cycle → after
screenshot, both saved to `evidence/<example>-{boot,after}.png` (16 PNGs total).

### Verdict summary

- **KEEP (7):** pong, breakout, simple-physics, metasprites, metasprites-stress, banks, platformer-template
- **RETIRE (1):** racer (D-03 — builds EXIT 0 but racing gameplay is non-functional at runtime)

### Per-example liveness notes

- **pong / breakout / simple-physics / metasprites / platformer-template** — clear interactive
  gameplay: sprites move under input, physics/animation update, HUD/level render. Boot→after
  screenshots show a visible state change (loop is live, not a frozen first frame).
- **metasprites-stress** — throwaway codegen oracle (Phase 10.1 D-06/D-07): no input beyond
  START by design. Liveness = title→play scene transition composes BOTH metasprites into OAM
  (40 sprites, `__current_metasprite` hiwater = 31 — WR-05 two-call evidence). KEEP as the
  MetaspriteIR / two-metasprite codegen regression bar.
- **banks** — codegen-exercise demo (no win/lose by design per PLAYBOOK). Liveness = cross-bank
  trampoline navigation (title scene_2 → play scene_1 on START) + banked zone tilemap tile
  render. KEEP as the multi-bank / SRAM codegen reference.
- **racer** — RETIRE (D-03). The car DOES respond to input (speed accelerates, sprite moves),
  but the racing game itself is broken: the track is a static center box that never scrolls,
  the camera never follows (`camera_x`/`camera_y` = 0), the car drives clean off the top edge
  of the track box, and `racing_lap_count_track1` / `racing_checkpoint_idx_track1` stay 0 — no
  lap or checkpoint progression. This is the documented runtime failure justifying retirement,
  NOT a repair target.
