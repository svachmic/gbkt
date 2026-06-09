---
phase: 12
slug: port-platformer-template-gbdk-example-to-gbkt
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-21
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Sourced from
> `12-RESEARCH.md` §Validation Architecture and CONTEXT.md decisions D-08 / D-10 / D-16 / D-21.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **JVM-tier framework** | JUnit5 + Kotlin + `GbktTestExtension` (gbkt-test) |
| **JVM config file** | none — per-module `build.gradle.kts` (existing convention) |
| **UAT-tier framework** | MCP `gbkt-emulator` server (StepAgent + AgentSessionConfig) — drives Coffee-GB emulator with playbook-style scripted input |
| **JVM quick run command** | `./gradlew :gbkt-genre-platformer:test :gbkt-backend-gbdk:test --tests "*Platformer*" --tests "*TilemapCollision*" --tests "*JumpHold*"` |
| **JVM full suite command** | `./gradlew :gbkt-ir:test :gbkt-lang:test :gbkt-engine:test :gbkt-core:test :gbkt-backend-api:test :gbkt-backend-gbdk:test :gbkt-analysis:test :gbkt-genre-platformer:test` |
| **Codegen smoke command (D-21, MANDATORY in verifier)** | `./gradlew :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:buildRom` |
| **Reference ROM rebuild (D-17a)** | `cd /Users/michalsvacha/gbdk/examples/cross-platform/platformer_template && make` (see `evidence/reference/BUILD.md`) |
| **UAT command (per anchor)** | `./gradlew :gbkt-examples:platformer-template:test --tests "PlatformerTemplateUatTest.anchor*"` |
| **Regression sweep (per ROADMAP §Phase 12 Success Criteria)** | `./gradlew :gbkt-examples:pong:buildRom :gbkt-examples:breakout:buildRom :gbkt-examples:simple-physics:buildRom :gbkt-examples:metasprites:buildRom :gbkt-examples:metasprites-stress:buildRom :gbkt-examples:banks:buildRom :gbkt-examples:racer:buildRom` |
| **Estimated JVM runtime** | quick: ~15s · full: ~90s · buildRom: ~25s · regression sweep: ~3 min |

---

## Sampling Rate

- **After every task commit:** Run the JVM quick command for the surface touched (per-module slice).
- **After every plan wave:** Run the JVM full suite + `:gbkt-examples:platformer-template:buildRom`.
- **Before `/gsd:verify-work`:** JVM full suite green + buildRom EXIT 0 (no SDCC `unknown address/value`, no lcc warnings) + all 5 UAT anchors GREEN with screenshots + regression sweep EXIT 0 on the 7 targets.
- **Max feedback latency:** ~30s for any single-plan task; ~3 min for regression sweep at phase close.

---

## Per-Anchor Verification Map (D-08 / D-10 / D-16)

Each anchor pairs THREE evidence tiers per CLAUDE.md §"Verification Methodology — Visual Evidence Rule":
1. **Screenshot** — required for every anchor (all 5 are visual truths).
2. **Variable assertion** — paired with screenshot, never as sole evidence.
3. **JVM emission invariant** — per-function `awk` brace-walk grep over generated C (per CLAUDE.md §"Scope-level grep gates corollary").

| # | Anchor | Visual Evidence (binding) | Variable Assertion (paired) | JVM Emission Invariant (awk brace-walk + grep) |
|---|--------|---------------------------|-----------------------------|------------------------------------------------|
| 1 | Title → gameplay scene transition | Screenshot: gameplay scene rendered with tilemap + player metasprite visible | `_current_scene == SCENE_GAMEPLAY` post-Start; `_next_level` / `_current_level` transitions tracked | `awk '/^void title_frame/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' main.c \| grep 'navigate_to_scene'` AND for the gameplay enter: `awk '/^void gameplay_enter/{p=1;d=0} p{...} p' bank1.c \| grep 'setup_current_level'` |
| 2 | Tilemap collision (jump + land on solid) | 2 screenshots: grounded + airborne (mid-jump) | `_player_vy` transitions 0 → negative → 0 over the jump cycle | `awk '/^UINT8 is_tile_solid/{p=1;d=0} p{...} p' main.c \| grep -c 'SWITCH_ROM'` expect 2 (entry + exit) AND grep `_current_level_non_solid_tile_count` within scope |
| 3 | Horizontal scroll (camera moves, no repeat) | 2 screenshots: initial frame + scrolled frame (visibly different tilemap content) | `_camera_x > 0`, `_map_pos_x > 0` after rightward traversal past half-screen threshold | `awk '/^void platformer_camera_update/{p=1;d=0} p{...} p' bank1.c \| grep 'set_bkg_submap'` AND grep `_old_map_pos_x` within scope |
| 4 | Metasprite animation (multi-frame walking + hflip) | 3 screenshots ~6 frames apart showing pose differences; 1 left-facing frame (hflip path) | `_walkFrameIdx` cycles 0→1→2→0 while right-held; `_facingRot == 3` when left-held | `awk '/^void gameplay_frame/{p=1;d=0} p{...} p' bank1.c \| grep 'sprite_player_frames\['` AND grep `move_metasprite_flipx` within scope |
| 5 | Level-switch (NextLevel card → level 2) | 2 screenshots: NextLevel card rendered + level 2 gameplay (visibly different tilemap from level 1) | `_current_level == 1` after switch; `_next_level == 1` at switch trigger | `awk '/^void main\(\)/{p=1;d=0} p{...} p' main.c \| grep '_next_level'` AND grep `setup_current_level` within the level-switch guard scope |

> **Awk pattern is binding.** Per CLAUDE.md §"Scope-level grep gates": a file-level `grep -c cls() bank1.c`
> cannot distinguish per-function callers; for per-function invariants the brace-walk pattern above MUST be used.
> Plan 07.4-23 Task 1 step 3 demonstrates the canonical awk shape.

---

## Wave 0 Requirements

> Wave 0 = test infrastructure stubs that must exist before any feature wave runs.

- [ ] `gbkt-examples/platformer-template/src/test/kotlin/PlatformerTemplateUatTest.kt` — UAT test class with 5 anchor test methods (per D-08); Wave 0 emits SKIP stubs that print "PENDING anchor N" until UAT plans 21–25 wire them.
- [ ] `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/TilemapCollisionEmissionTest.kt` — JVM-tier emission test for D-16 invariant 2 (`is_tile_solid` SWITCH_ROM wrapper shape).
- [ ] `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/HorizontalScrollEmissionTest.kt` — JVM-tier emission test for D-16 invariant 3 (`platformer_camera_update` column-update shape).
- [ ] `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/JumpHoldEmissionTest.kt` — JVM-tier emission test for D-14 (gravity-suppression-while-held shape inside `buildPhysicsUpdateFunction`).
- [ ] `gbkt-backend-gbdk/src/test/kotlin/.../MultiTilesetAllocationTest.kt` — JVM-tier emission test for D-15 (3 zones + 2 tilesets bank allocation; gap-or-pass verdict).
- [ ] `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/reference/BUILD.md` — reference ROM build reproducibility doc (per D-17a). Lists `GBDK_HOME`, `make` invocation, expected outputs (`platformer_template.gb`, `.map`, `.noi`).
- [ ] `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/` — directory must exist before UAT plans run.

---

## Manual-Only Verifications

| Behavior | Anchor | Why Manual | Test Instructions |
|----------|--------|------------|-------------------|
| GBC palette load conditional (per D-claude-4) | tangential to anchors 1–5 | DMG vs CGB hardware path; the MCP emulator's default profile covers DMG; CGB confirmation requires either toggling Coffee-GB CGB mode or visual inspection on a CGB target | Boot in CGB mode via `mcp__gbkt-emulator__emulator_start --gbc`; screenshot title screen; confirm CGB-only palette colors load |
| Visual-pixel-parity with reference ROM | none (explicitly out of scope per CONTEXT.md "Out of scope") | UAT verifies INTEGRATION contract, NOT pixel-exact match | n/a — DO NOT add an anchor for this |

---

## Verifier ROM-Build Smoke Test (D-21, MANDATORY)

Per user memory `feedback_rom_build_smoke_test_for_codegen_phases.md` and CLAUDE.md §"Common Errors":
the verifier MUST run a clean buildRom before declaring the phase complete. JVM tests do NOT see
staleness in `build/gbkt/generated/`.

```bash
./gradlew :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:buildRom
# Pass criteria:
#   - EXIT 0
#   - no SDCC "unknown address/value" errors
#   - no lcc warnings
#   - .noi file: every DEF l__CODE_<N> byte size ≤ 16384 (hard ROM-bank capacity per Phase 11 D-15)
#   - ROM boots to title screen cleanly (UAT anchor 1)
```

Regression sweep at phase close (per ROADMAP §Phase 12 Success Criteria):

```bash
./gradlew \
  :gbkt-examples:pong:buildRom \
  :gbkt-examples:breakout:buildRom \
  :gbkt-examples:simple-physics:buildRom \
  :gbkt-examples:metasprites:buildRom \
  :gbkt-examples:metasprites-stress:buildRom \
  :gbkt-examples:banks:buildRom \
  :gbkt-examples:racer:buildRom
# All 7 must EXIT 0 — no example may regress as a side-effect of D-12..D-15 codegen extensions.
```

---

## Validation Sign-Off

- [ ] All 5 UAT anchors have automated `<verify>` (screenshot + variable assertion) + JVM-tier `<emission_invariant>` (awk brace-walk + grep)
- [ ] Sampling continuity: no 3 consecutive tasks without automated verification — D-16's 5 invariants are spread across waves so every wave has at least one JVM-tier sample
- [ ] Wave 0 stubs cover all UAT anchor MISSING test files + reference ROM BUILD.md + evidence directories
- [ ] No watch-mode flags in any test invocation
- [ ] Feedback latency < 30s for per-task quick command; <3 min for phase-close regression sweep
- [ ] Per-function awk brace-walk pattern used for EVERY per-function emission invariant (no file-level `grep -c` for D-16 invariants 1–5)
- [ ] D-21 ROM-build smoke test wired into the verifier pre-completion gate
- [ ] `nyquist_compliant: true` set in frontmatter once all above items checked

**Approval:** pending — flip to `approved YYYY-MM-DD` when planner completes plan-checker pass and the per-task verification map below is filled in with concrete plan/task IDs.

---

## Per-Task Verification Map (filled in by planner during plan-phase)

> Planner: after carving plans, populate this table — one row per task, citing the plan ID, wave,
> the anchor or invariant it serves (if any), and the automated command that proves it.
> Use the canonical anchor IDs (D-08 #1..#5) and emission invariant IDs (D-16 #1..#5) for traceability.

| Task ID | Plan | Wave | Serves Anchor / Invariant | Test Type | Automated Command | Status |
|---------|------|------|---------------------------|-----------|-------------------|--------|
| _TBD_   | _TBD_ | _TBD_ | _D-08 #N or D-16 #N or —_ | unit / emission / uat / smoke | `_TBD_` | ⬜ pending |

*Status legend: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*
