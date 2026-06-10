/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.dsl.AssignableVar
import io.github.gbkt.core.dsl.SceneRef
import io.github.gbkt.core.dsl.asset
import io.github.gbkt.core.dsl.buttons
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.dsl.zone
import io.github.gbkt.genre.platformer.dsl.levelCardScene
import io.github.gbkt.genre.platformer.dsl.platformerPhysics
import io.github.gbkt.genre.platformer.dsl.tilemapCollision
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// Phase 12.6-06 — levelCardScene emission contract (D-05 re-pointed + D-06)
//
// This test file is the load-bearing JVM-tier regression guard for the entire
// DEFECT-1 + DEFECT-2 closure. It locks four invariants of the post-Phase-12.6
// codegen contract:
//
//   1. Start-press ordering (D-05 re-pointed):
//      The levelCardScene's frame handler (lowered through the levelCardScene
//      delegate-pattern helper added in Plan 12.6-04) emits a `cEmit(
//      "setup_current_level();")` STRICTLY BEFORE a `navigate_to_scene(SCENE_
//      GAMEPLAY)` call. Locking the textual ORDERING is the D-02 reference-
//      accuracy contract — if the visitor ever inverted the ops, the runtime
//      would navigate before the new level's tilemap was wired, mirroring the
//      same-frame stomp DEFECT-1 produced from the OLD main-loop guard.
//
//   2. Spawn-table HOME-bank emission (D-06):
//      `buildLevelSpawnTablesIfNeeded` (Plan 12.6-05) emits
//      `const UINT8 _level_spawn_x[]` and `const UINT8 _level_spawn_y[]` arrays
//      in HOME bank. With both fixture gameplay zones declaring `spawn(40u,
//      120u)`, the joined element list contains the substring `40u, 40u`
//      somewhere in `_level_spawn_x[]`. Locks the const-array shape that the
//      consumer (per-case body in `setup_current_level`) reads.
//
//   3. Per-case body spawn writes (D-06 + Pitfalls 2/3):
//      The per-zone case body inside `setup_current_level()` writes
//      `_playerX = ((INT16)_level_spawn_x[0u]) << 4;`,
//      `_playerY = ((INT16)_level_spawn_y[0u]) << 4;`,
//      `_playerVx = 0;`, and `_playerVy = 0;`. The <<4 shift is the subpixel
//      conversion (Pitfall 2); the velocity reset closes Pitfall 3 (without
//      it the player would carry level-N momentum across the level-switch and
//      same-frame re-fire the level-end trigger). The exact symbol names
//      `_playerX/_playerY/_playerVx/_playerVy` come from the fixture's
//      `tilemapCollision { position(...); velocity(...) }` config; the
//      hoisted symbol-resolution block in GBDKPipeline (mirroring
//      PlatformerVisitor.kt:549-558) substitutes these binders into the
//      per-case template.
//
//   4. Main-loop guard regression (Phase 12.6 D-04 trim, belt-and-braces):
//      `main()`'s body does NOT contain `setup_current_level()`. The call was
//      moved out of the main-loop guard (Plan 12.6-02) and into the
//      levelCardScene Start-press path (Plans 12.6-04 + 12.6-05). The same
//      intent is asserted in LevelSwitchEmissionTest's inverted positive (the
//      post-Plan-12.6-02 `assertFalse`); this co-located complement lives
//      next to the levelCardScene assertions for proximity-to-fix readability.
//
// Convention notes:
//   - Brace-walk extraction helper is COPIED VERBATIM from
//     LevelSwitchEmissionTest.kt:92-112 (PATTERNS.md § Scope-level grep gates
//     corollary — file-level grep on main.c cannot distinguish race_enter
//     from title_enter; the per-test EVIDENCE_DIR + helper convention is
//     intentional).
//   - EVIDENCE_DIR points to the phase-LOCAL `evidence/tier1-shape/` directory
//     (NOT Phase 12's evidence dir) — per the per-test EVIDENCE_DIR
//     convention.
// =============================================================================

class LevelCardSceneEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe).
         *
         * Same pattern as LevelSwitchEmissionTest (sibling pipeline test). `user.dir` resolves to
         * the `:gbkt-backend-gbdk:test` task's working directory; we ascend one level to the repo
         * (or worktree) root, then descend into the phase-local evidence directory. Hard-coding an
         * absolute path would silently route evidence files outside the active worktree and miss
         * the commit (#3099 worktree path safety).
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../.planning/phases/12.6-main-loop-level-switch-codegen-fix-phase-12-6/" +
                        "evidence/tier1-shape"
                )
                .normalize()
    }

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers — brace-walk extraction (awk-equivalent)
    //
    // Copy verbatim from LevelSwitchEmissionTest (PATTERNS.md § Scope-level grep
    // gates corollary explicit instruction); duplication is intentional per the
    // per-test EVIDENCE_DIR + helper pattern established by BanksEmissionTest +
    // TilemapCollisionEmissionTest + LevelSwitchEmissionTest.
    // -------------------------------------------------------------------------

    private fun extractFunctionBody(cSource: String, functionSignaturePrefix: String): String {
        val lines = cSource.lines()
        val startIdx = lines.indexOfFirst { it.startsWith(functionSignaturePrefix) }
        if (startIdx == -1) return ""
        val body = StringBuilder()
        var depth = 0
        var started = false
        for (i in startIdx until lines.size) {
            val line = lines[i]
            body.appendLine(line)
            for (ch in line) {
                if (ch == '{') {
                    depth++
                    started = true
                }
                if (ch == '}') depth--
            }
            if (started && depth == 0) break
        }
        return body.toString()
    }

    /**
     * Build a minimal levelCardScene fixture wiring all four contracts together:
     *
     * - `platformerPhysics { solidThreshold(17) }` activates `gameUsesTilemapCollision` Path A —
     *   without this gate, neither the main-loop guard, the spawn tables, NOR the per-case body
     *   extension fire (all 4 emission sites short-circuit on the same predicate).
     * - `tilemapCollision { position(playerX, playerY); velocity(playerVx, playerVy) }` binds the
     *   user-DSL symbol names so the codegen-resolved per-case body emits `_playerX`/`_playerY`/
     *   `_playerVx`/`_playerVy` (matching the platformer-template production path). WITHOUT this
     *   block, the codegen falls back to `_player_x` etc. — Test 3's exact substring assertions
     *   would fail.
     * - Two gameplay zones (`gameplayZone1`, `gameplayZone2`) BOTH declare `spawn(40u, 120u)` — the
     *   X=40 value appearing twice produces the joined substring `40u, 40u` in `_level_spawn_x[]`
     *   (Test 2's contract). Two zones also ensures the `switch (_current_level % N)` body has ≥2
     *   case branches.
     * - `gameplayScene = scene("gameplay") { ... }` is declared BEFORE the `levelCardScene { }`
     *   block per RESEARCH §Pitfall 5 (the Kotlin reference must resolve at DSL-recording time).
     * - `val nextLevelScene by levelCardScene { onStartPress(gameplayScene) }` is the helper from
     *   Plan 12.6-04 — the property name `nextLevelScene` becomes the scene id, and the lowered
     *   scene-frame function in `bank1.c` is named `nextLevelScene_frame` (per SceneVisitor's
     *   `${scene.id}_frame` naming convention at SceneVisitor.kt:291).
     */
    private fun buildLevelCardSceneGameDsl() =
        game("LevelCardSceneEmissionTest") {
                // Path A activates gameUsesTilemapCollision (gate-on prerequisite for
                // setup_current_level, _level_spawn_x[], _level_spawn_y[], and the
                // main-loop level-switch guard).
                platformerPhysics { solidThreshold(17) }

                // Bind the symbol names the per-case body's spawn writes will reference.
                // The AssignableVar names ("playerX" / "playerY" / "playerVx" / "playerVy")
                // flow through GenericSystem.config → GBDKPipeline's hoisted symbol
                // resolution → emitted as `_playerX`/`_playerY`/etc. The fixture mirrors
                // the platformer-template production path (PlatformerTemplate.kt:172-178).
                val playerX = AssignableVar("playerX")
                val playerY = AssignableVar("playerY")
                val playerVx = AssignableVar("playerVx")
                val playerVy = AssignableVar("playerVy")
                tilemapCollision {
                    position(playerX, playerY)
                    velocity(playerVx, playerVy)
                    hitbox(0, 0, 8, 24)
                    solidThreshold(17)
                }

                // Two gameplay zones — both declare spawn(40u, 120u). Identical X values
                // produce the joined substring `40u, 40u` in _level_spawn_x[] (Test 2).
                val gameplayZone1 by zone {
                    tileset(asset("res/graphics/level1.png"))
                    spawn(40u, 120u)
                }
                val gameplayZone2 by zone {
                    tileset(asset("res/graphics/level2.png"))
                    spawn(40u, 120u)
                }

                // Title scene — required for `start =` assignment and to serve as a
                // pre-gameplay entry point. Does NOT carry the tilemap-collision payload.
                val titleScene =
                    scene("title") {
                        enter { cEmit("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);") }
                        frame { whenever(buttons.start.pressed) { navigate(SceneRef("gameplay")) } }
                    }

                // Gameplay scene — MUST be declared BEFORE `levelCardScene { }` per
                // RESEARCH §Pitfall 5 (the Kotlin reference passed to onStartPress
                // resolves at DSL-recording time).
                val gameplayScene =
                    scene("gameplay") {
                        zone(gameplayZone1)
                        frame {
                            whenever(buttons.start.pressed) { navigate(SceneRef("nextLevelScene")) }
                        }
                    }

                // Hidden scene binding the 2nd gameplay zone so it surfaces in gameIR.zones
                // (the setup_current_level switch enumerates gameIR.zones with the title/
                // nextlevel filter — gameplayZone2 must be reachable for the case count
                // to hit ≥2). Modeled after LevelSwitchEmissionTest's `gameplay2` scene.
                scene("gameplay2") {
                    zone(gameplayZone2)
                    frame { whenever(buttons.start.pressed) { navigate(SceneRef("gameplay")) } }
                }

                // The Plan 12.6-04 DSL surface under test — delegate-pattern helper
                // captures the property name "nextLevelScene" as the scene id.
                // Lowers to a regular scene whose frame handler emits
                // `cEmit("setup_current_level();")` STRICTLY BEFORE `navigate(gameplayScene)`.
                // The lowered scene id matches the widened next-level-scene matcher
                // landed by Plan 12.6-02 (`lower.contains("nextlevel")`), so the
                // main-loop substrate continues to emit SCENE_NEXTLEVELSCENE references.
                @Suppress("UNUSED_VARIABLE")
                val nextLevelScene by levelCardScene { onStartPress(gameplayScene) }

                start = titleScene
            }
            .build()

    // -------------------------------------------------------------------------
    // Test 1 — Start-press path emits setup_current_level THEN navigate_to_scene
    //
    // Locks the D-05 re-pointed emission contract: `setup_current_level()` is
    // emitted by the levelCardScene's Start-press frame handler (NOT the trimmed
    // main-loop guard), and STRICTLY BEFORE the `navigate_to_scene(SCENE_
    // GAMEPLAY)` call. The textual-ordering assertion is the D-02 reference-
    // accuracy contract — inverted ops would re-introduce the DEFECT-1
    // same-frame VRAM stomp at a new emission site.
    //
    // Scope-level grep gate: the assertion fires against the brace-walked
    // `nextLevelScene_frame` body, NOT against `bank1.c` at file scope. The
    // scene-frame function naming convention `${scene.id}_frame` comes from
    // SceneVisitor.kt:291; property name `nextLevelScene` (preserved verbatim
    // by LevelCardSceneDelegate.provideDelegate → property.name) yields
    // `nextLevelScene_frame`.
    // -------------------------------------------------------------------------

    @Test
    fun `levelCardScene start-press path emits setup_current_level then navigate_to_scene(gameplay)`() {
        val gameIR = buildLevelCardSceneGameDsl()
        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Brace-walk extraction — locks all subsequent substring checks inside the
        // nextLevelScene_frame's scope. The signature anchor is `void nextLevelScene_frame`
        // (CEmitter emits banked functions as `void name(void) BANKED` so we anchor on
        // the prefix without the BANKED qualifier or the opening paren — matching either
        // `void nextLevelScene_frame(void) BANKED {` or `void nextLevelScene_frame(void) {`).
        val frameBody = extractFunctionBody(bank1C, "void nextLevelScene_frame")
        File(EVIDENCE_DIR, "nextLevelScene_frame.c").writeText(frameBody)

        assertTrue(
            frameBody.isNotEmpty(),
            "nextLevelScene_frame body must be extractable via brace-walk from bank1.c " +
                "(SceneVisitor.kt:291 emits scene-frame functions as `${'$'}{scene.id}_frame`; " +
                "the LevelCardSceneDelegate captures property name `nextLevelScene` verbatim " +
                "as the scene id, so the function name is `nextLevelScene_frame`). " +
                "bank1.c head:\n${bank1C.take(4000)}",
        )

        // D-05 re-pointed positive — setup_current_level() must appear in the frame body.
        // This is the load-bearing positive assertion that, together with Test 4's
        // co-located inverted complement, proves the call MOVED from main() to the
        // levelCardScene Start-press path (and did not get accidentally deleted in
        // the move).
        assertTrue(
            frameBody.contains("setup_current_level()"),
            "nextLevelScene_frame body must contain `setup_current_level()` " +
                "(D-05 re-pointed — the call moved from main()'s level-switch guard to " +
                "the levelCardScene Start-press path; see Plans 12.6-02 + 12.6-04 + 12.6-05). " +
                "frame body:\n${frameBody.take(4000)}",
        )

        // Navigate target — the levelCardScene helper's `onStartPress(gameplayScene)` binder
        // lowers to `navigate(gameplayScene)`, which the codegen renders as
        // `navigate_to_scene(SCENE_GAMEPLAY)` (SceneVisitor.kt:324 emits the enum constant
        // as `SCENE_${id.uppercase()}`).
        assertTrue(
            frameBody.contains("navigate_to_scene(SCENE_GAMEPLAY)"),
            "nextLevelScene_frame body must contain `navigate_to_scene(SCENE_GAMEPLAY)` " +
                "(the Start-press handler's navigate target — `onStartPress(gameplayScene)` " +
                "from Plan 12.6-04 lowers to `navigate(gameplayScene)`, which SceneVisitor " +
                "renders as `navigate_to_scene(SCENE_${'$'}{id.uppercase()})`). " +
                "frame body:\n${frameBody.take(4000)}",
        )

        // D-02 reference-accuracy contract — strict ordering. The Plan 12.6-04 helper's
        // materialize() block calls `cEmit("setup_current_level();")` BEFORE
        // `navigate(gameplay)` (PlatformerExtensions.kt:800-801); both lower into the
        // same `whenever(buttons.start.pressed) { ... }` then-branch, preserving
        // declaration order. A regression that flipped these (e.g., reordered the
        // RawOp/NavigateTo emission) would re-introduce the same-frame stomp at the
        // new emission site, defeating the whole DEFECT-1 fix.
        val setupIdx = frameBody.indexOf("setup_current_level()")
        val navIdx = frameBody.indexOf("navigate_to_scene(SCENE_GAMEPLAY)")
        assertTrue(
            setupIdx >= 0 && navIdx > setupIdx,
            "setup_current_level() must come BEFORE navigate_to_scene(SCENE_GAMEPLAY) in " +
                "the nextLevelScene_frame body — see Phase 12.6 D-02 reference-accuracy gate. " +
                "Inverted ops would re-introduce the DEFECT-1 same-frame VRAM stomp at the " +
                "new emission site. setupIdx=$setupIdx navIdx=$navIdx. " +
                "frame body:\n${frameBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // Test 2 — _level_spawn_x[] / _level_spawn_y[] HOME-bank const arrays emit
    //
    // Locks the D-06 spawn-table emission contract. `buildLevelSpawnTablesIfNeeded`
    // (Plan 12.6-05 — GBDKPipeline.kt:2549) emits two `const UINT8` arrays in
    // HOME bank, one element per gameplay zone. The fixture's 2 zones both declare
    // `spawn(40u, 120u)` so the X array's joined element list contains the
    // substring `40u, 40u` somewhere in `_level_spawn_x[]`.
    //
    // The arrays are forward-declarations the consumer (per-case body in
    // `setup_current_level`) reads via `_level_spawn_x[idx]` indexing; without
    // them, the per-case body would reference unresolved symbols at SDCC link.
    // Locking both the array-declaration prefix AND the joined-element substring
    // catches a regression that emitted only one of the two arrays or scrambled
    // the per-zone ordering.
    // -------------------------------------------------------------------------

    @Test
    fun `buildLevelSpawnTablesIfNeeded emits const UINT8 arrays for gameplay zones (D-06)`() {
        val gameIR = buildLevelCardSceneGameDsl()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()
        File(EVIDENCE_DIR, "main_spawn_tables.c").writeText(mainC.take(8000))

        // X array declaration — locks the const-UINT8 array shape (Plan 12.6-05
        // GBDKPipeline.kt:2579). The `[]` (empty-bracket) form is the literal C
        // shape SDCC accepts for inline-initialized const arrays.
        assertTrue(
            mainC.contains("const UINT8 _level_spawn_x[]"),
            "main.c must contain the spawn-X table declaration `const UINT8 _level_spawn_x[]` " +
                "(per Plan 12.6-05 D-06 substrate-tier closure). " +
                "main.c head:\n${mainC.take(4000)}",
        )

        // Y array declaration — companion gate. Both arrays emit in lockstep (or
        // neither emits, when `gameUsesTilemapCollision` is false). Locking both
        // halves prevents a regression that emitted only X (which the per-case body
        // would silently fall back to a Y of 0).
        assertTrue(
            mainC.contains("const UINT8 _level_spawn_y[]"),
            "main.c must contain the spawn-Y table declaration `const UINT8 _level_spawn_y[]` " +
                "(companion gate to _level_spawn_x[] — both arrays emit in lockstep). " +
                "main.c head:\n${mainC.take(4000)}",
        )

        // Joined-element shape — the fixture's 2 zones declare `spawn(40u, 120u)` each,
        // so `_level_spawn_x[]` joins to `40u, 40u`. The substring is distinctive
        // enough to anchor the per-zone ordering AND the literal-value-pass-through
        // (the codegen path is `zone.spawnX.toInt() → "${it}u" → joinToString(", ")`,
        // so a regression that dropped the `u` suffix, scrambled the order, or
        // substituted a default would fail this check).
        assertTrue(
            mainC.contains("40u, 40u"),
            "_level_spawn_x[] array must contain the joined element `40u, 40u` (fixture " +
                "declares spawn(40u, 120u) on BOTH gameplay zones — same X value twice " +
                "produces the substring `40u, 40u` in the joined array literal). " +
                "main.c head:\n${mainC.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // Test 3 — setup_current_level per-case body writes spawn position + zeros velocity
    //
    // Locks the D-06 per-case body extension (Plan 12.6-05 — GBDKPipeline.kt:2493-
    // 2496). Each gameplay-zone case in `setup_current_level()`'s switch writes:
    //   _playerX = ((INT16)_level_spawn_x[N]) << 4;
    //   _playerY = ((INT16)_level_spawn_y[N]) << 4;
    //   _playerVx = 0;
    //   _playerVy = 0;
    //
    // The <<4 shift converts pixel coords (DSL) to subpixel form (the runtime
    // representation — see Pitfall 2). The velocity reset is part of the DEFECT-2
    // fix contract (Pitfall 3): without it, level-N velocity would carry into
    // level-N+1, same-frame re-firing the level-end trigger.
    //
    // Symbol names: `_playerX`/`_playerY`/`_playerVx`/`_playerVy` come from the
    // fixture's `tilemapCollision { position(...); velocity(...) }` config. The
    // hoisted symbol-resolution block in GBDKPipeline (mirroring
    // PlatformerVisitor.kt:549-558) substitutes these binders into the per-case
    // template, so the assertion below tests the EXACT shape platformer-template
    // produces on the production path.
    //
    // Scope-level grep gate: assertions fire against the brace-walked
    // `setup_current_level` body, NOT main.c at file scope. A file-level grep
    // on `_playerX` would false-positive on game.h's extern declaration or on
    // any actor-update code that mutates the global; locking the substring at
    // function scope keeps the test surgically targeted to the per-case body.
    // -------------------------------------------------------------------------

    @Test
    fun `setup_current_level per-case body writes spawn from table (D-06)`() {
        val gameIR = buildLevelCardSceneGameDsl()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Brace-walk extraction — locks assertions inside the setup_current_level body.
        // The signature anchor `void setup_current_level(void) NONBANKED` mirrors the
        // sibling LevelSwitchEmissionTest's helper-emission assertion.
        val setupBody = extractFunctionBody(mainC, "void setup_current_level(void) NONBANKED")
        File(EVIDENCE_DIR, "setup_current_level.c").writeText(setupBody)

        assertTrue(
            setupBody.isNotEmpty(),
            "setup_current_level body must be extractable via brace-walk from main.c " +
                "(Plan 12-17 Task 2 emits the function as a HOME-bank NONBANKED definition; " +
                "Plan 12.6-05 extended its per-case body with the four lines under test). " +
                "main.c head:\n${mainC.take(4000)}",
        )

        // Spawn-X write — the <<4 shift is the subpixel conversion (Pitfall 2 in
        // 12.6-RESEARCH.md). The `[0u]` index is the first gameplay zone in the
        // fixture's switch (idx=0 in the mapIndexed loop). The (INT16) cast widens
        // the UINT8 array element for the shift.
        assertTrue(
            setupBody.contains("_playerX = ((INT16)_level_spawn_x[0u]) << 4;"),
            "setup_current_level body must contain the spawn-X write " +
                "`_playerX = ((INT16)_level_spawn_x[0u]) << 4;` for the first gameplay zone " +
                "(per Plan 12.6-05 D-06 — subpixel <<4 shift per Pitfall 2; _playerX symbol " +
                "from the fixture's tilemapCollision { position(...) } binder). " +
                "setup body:\n${setupBody.take(4000)}",
        )

        // Spawn-Y write — same subpixel-shift contract for the Y axis. The Y symbol
        // also comes from the fixture's tilemapCollision binder.
        assertTrue(
            setupBody.contains("_playerY = ((INT16)_level_spawn_y[0u]) << 4;"),
            "setup_current_level body must contain the spawn-Y write " +
                "`_playerY = ((INT16)_level_spawn_y[0u]) << 4;` for the first gameplay zone " +
                "(per Plan 12.6-05 D-06 — subpixel <<4 shift; _playerY symbol from the " +
                "fixture's tilemapCollision { position(...) } binder). " +
                "setup body:\n${setupBody.take(4000)}",
        )

        // Velocity-X reset — Pitfall 3 contract. Without this line, horizontal velocity
        // from level N would carry into level N+1, same-frame re-firing the level-end
        // trigger if the player happened to be moving right at the moment of switch.
        // The fixture binds _playerVx via the velocity(...) setter; the codegen
        // substitutes the bound symbol into the per-case template.
        assertTrue(
            setupBody.contains("_playerVx = 0;"),
            "setup_current_level body must contain the velocity-X reset `_playerVx = 0;` " +
                "(per Plan 12.6-05 D-06 + Pitfall 3 — without this, level-N horizontal " +
                "momentum carries into level-N+1, same-frame re-firing the level-end trigger; " +
                "_playerVx symbol from the fixture's tilemapCollision { velocity(...) } binder). " +
                "setup body:\n${setupBody.take(4000)}",
        )

        // Velocity-Y reset — companion to _playerVx. Same Pitfall 3 contract for the
        // Y-axis. Without this, vertical velocity (e.g., mid-jump impulse) would
        // carry across the level-switch.
        assertTrue(
            setupBody.contains("_playerVy = 0;"),
            "setup_current_level body must contain the velocity-Y reset `_playerVy = 0;` " +
                "(per Plan 12.6-05 D-06 + Pitfall 3 — companion to _playerVx; vertical " +
                "momentum would otherwise carry across the level-switch; _playerVy symbol " +
                "from the fixture's tilemapCollision { velocity(...) } binder). " +
                "setup body:\n${setupBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // Test 4 — main-loop guard regression (Phase 12.6 D-04 trim, belt-and-braces)
    //
    // Locks the inverted-positive contract: `main()`'s body does NOT contain
    // `setup_current_level()`. The call moved out of the main-loop guard
    // (Plan 12.6-02 trimmed the `setup_current_level()` call from
    // `buildMainLoopLevelSwitchGuardIfNeeded`) and into the levelCardScene
    // Start-press path (locked positively by Test 1 above).
    //
    // The same intent is also asserted in LevelSwitchEmissionTest's
    // post-Plan-12.6-02 inversion (the `mainBody.contains("setup_current_level()")`
    // positive was converted to `assertFalse(...)`). This co-located complement
    // lives next to the levelCardScene tests for proximity-to-fix readability —
    // a future plan author reading just this file gets the full inversion
    // contract without cross-referencing the sibling test.
    //
    // Belt-and-braces rationale: if either the LevelSwitchEmissionTest inversion
    // OR this complement passes alone, the contract is locked. Both passing
    // double-locks it — the cost is one extra assertion, the benefit is that a
    // future executor who deletes one of the two tests (e.g., during a refactor
    // that splits LevelSwitchEmissionTest) does not silently drop the contract.
    // -------------------------------------------------------------------------

    @Test
    fun `main loop guard does NOT contain setup_current_level (Phase 12_6 D-04 trim regression guard)`() {
        val gameIR = buildLevelCardSceneGameDsl()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Brace-walk extraction — locks the assertion inside main()'s scope. A
        // file-level grep on main.c would false-positive on `setup_current_level`'s
        // own definition (the HOME-bank `void setup_current_level(void) NONBANKED`
        // function body — which itself contains the IDENTIFIER `setup_current_level`
        // in its signature). The brace-walk on `void main(void)` filters to the
        // main-loop scope, where the call MUST be absent post-Plan-12.6-02 trim.
        val mainBody = extractFunctionBody(mainC, "void main(void)")
        File(EVIDENCE_DIR, "main_no_setup.c").writeText(mainBody)

        assertTrue(
            mainBody.isNotEmpty(),
            "main() body must be extractable via brace-walk from main.c (main() is the " +
                "program entry point — its absence would be a more severe regression than " +
                "the D-04 trim). main.c head:\n${mainC.take(4000)}",
        )

        // D-04 trim inversion — `setup_current_level()` call must be absent from main().
        // This is the belt-and-braces complement to LevelSwitchEmissionTest's
        // post-Plan-12.6-02 inversion. The positive emission site (the levelCardScene
        // Start-press path) is locked by Test 1 above.
        assertFalse(
            mainBody.contains("setup_current_level()"),
            "Phase 12.6 D-04 trim: main() body must NOT contain `setup_current_level()` " +
                "(call moved to levelCardScene Start-press path per Plans 12.6-02 + 12.6-04 + " +
                "12.6-05). This is the belt-and-braces co-located complement to " +
                "LevelSwitchEmissionTest's D-05 inversion — both assertions express the same " +
                "invariant for proximity-to-fix readability. " +
                "main() body:\n${mainBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // Test 5 — Phase 12.9 D-11: nextLevelScene_enter BG-clear uses 32x32 (NOT 20x18)
    //
    // Locks the Phase 12.9 D-11 / D-10 reframe fix: LevelCardSceneBuilder.materialize()
    // must emit `fill_bkg_rect(0u, 0u, 32u, 32u, 0u)` in `nextLevelScene_enter`.
    //
    // Root cause (D-10): `fill_bkg_rect(20x18)` only clears the visible viewport.
    // The GBC BG hardware-addressable plane is 32x32 tiles (LCDC.3 = 256x256 px).
    // After camera scroll in gameplay, BG columns 20-31 hold world1Area1 tile indices
    // (0x0E/0x0F grass/ground) that survive transition and appear as "0F" text artifact
    // in anchor-5/01-02 captures. Replacing with 32x32 clears the full BG plane.
    //
    // D-11 closes R-03 (nextLevel transition distinctness) + R-04 (0F artifact) via a
    // single 1-line literal change in PlatformerExtensions.kt:867.
    //
    // Scope-level grep gate: assertions fire against the brace-walked
    // `nextLevelScene_enter` body, NOT bank1.c at file scope (per CLAUDE.md
    // § Scope-level grep gates corollary — file-level grep cannot distinguish
    // nextLevelScene_enter from title_enter if title also calls fill_bkg_rect).
    //
    // Fixture: this test uses `buildLevelCardSceneWithZoneDsl()` which binds a
    // screen(asset(...)) on the levelCardScene. Phase 13.5 Plan 06 migrated from the
    // old zone()-based approach to screen() — the screenMode superset in SceneVisitor
    // emits the fill_bkg_rect + centered _bkg_tiles_load_banked + DISPLAY_ON ceremony.
    // The screen()-bound path is the production path that PlatformerTemplate.kt uses.
    // -------------------------------------------------------------------------

    private fun buildLevelCardSceneWithZoneDsl() =
        game("LevelCardSceneD11Test") {
                // Path A gate — required for setup_current_level and the card-scene enter path.
                platformerPhysics { solidThreshold(17) }

                // Bind symbol names for the per-case body (same as main fixture).
                val playerX = AssignableVar("playerX")
                val playerY = AssignableVar("playerY")
                val playerVx = AssignableVar("playerVx")
                val playerVy = AssignableVar("playerVy")
                tilemapCollision {
                    position(playerX, playerY)
                    velocity(playerVx, playerVy)
                    hitbox(0, 0, 8, 24)
                    solidThreshold(17)
                }

                // Gameplay zone — defines the tileset asset path so the zone-load prelude
                // in SceneVisitor fires (set_bkg_data + _bkg_tiles_load_banked + DISPLAY_ON).
                val gameplayZone1 by zone {
                    tileset(asset("res/graphics/level1.png"))
                    spawn(40u, 120u)
                }

                val titleScene =
                    scene("title") {
                        enter { cEmit("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);") }
                        frame { whenever(buttons.start.pressed) { navigate(SceneRef("gameplay")) } }
                    }

                val gameplayScene =
                    scene("gameplay") {
                        zone(gameplayZone1)
                        frame {
                            whenever(buttons.start.pressed) { navigate(SceneRef("nextLevelScene")) }
                        }
                    }

                // Phase 13.5 Req #18: screen() primitive replaces the old zone()-based approach.
                // screen(asset(...)) synthesises a _screen_nextLevelScene ZoneIR with
                // screenMode=true;
                // SceneVisitor's screenMode superset branch emits the full centered-draw ceremony.
                @Suppress("UNUSED_VARIABLE")
                val nextLevelScene by levelCardScene {
                    screen(asset("res/graphics/nextlevel.png"))
                    onStartPress(gameplayScene)
                }

                start = titleScene
            }
            .build()

    // -------------------------------------------------------------------------
    // Test 6 — Phase 12.9 D1: fill_bkg_rect precedes the first DISPLAY_ON
    //
    // Root cause (12.9-08e Diagnose): SceneVisitor.kt (~line 299) appends an
    // inline `CRawCode("DISPLAY_ON;")` as the LAST zone-load item, BEFORE the
    // user-authored `LevelCardSceneBuilder` clear runs. So bank1.c has:
    //   line N:   DISPLAY_ON;           ← zone-load inline
    //   line N+3: fill_bkg_rect(...)    ← card-scene clear (too late!)
    //   line N+4: DISPLAY_ON;           ← card-scene final
    // The LCD turns on before the BG is cleared → GBC PPU composites a flip
    // frame with un-cleared world1 cols 20–31 = the `0F` tile.
    //
    // Fix: gate the inline DISPLAY_ON so it is omitted when the scene's
    // enterOps already terminate with a DISPLAY_ON (trailing-DISPLAY_ON
    // heuristic). The clear then runs before the single final DISPLAY_ON.
    //
    // Scope-level grep gate (CLAUDE.md § Scope-level grep gates): extracts the
    // nextLevelScene_enter body via brace-walk before asserting — a file-level
    // grep on bank1.c would match DISPLAY_ON from other scene enter functions.
    // -------------------------------------------------------------------------

    @Test
    fun `nextLevelScene_enter fill_bkg_rect precedes first DISPLAY_ON (Phase 12_9 D1 flip-frame fix)`() {
        val gameIR = buildLevelCardSceneWithZoneDsl()
        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Brace-walk extraction — locks assertions inside nextLevelScene_enter scope.
        val enterBody = extractFunctionBody(bank1C, "void nextLevelScene_enter")
        File(EVIDENCE_DIR, "nextLevelScene_enter_d1_displayon.c").writeText(enterBody)

        assertTrue(
            enterBody.isNotEmpty(),
            "nextLevelScene_enter body must be extractable via brace-walk from bank1.c. " +
                "bank1.c head:\n${bank1C.take(4000)}",
        )

        // D1 positive: fill_bkg_rect(0u, 0u, 32u, 32u, 0u) must be present in the enter body.
        assertTrue(
            enterBody.contains("fill_bkg_rect(0u, 0u, 32u, 32u, 0u)"),
            "nextLevelScene_enter body must contain fill_bkg_rect(0u, 0u, 32u, 32u, 0u). " +
                "enter body:\n${enterBody.take(4000)}",
        )

        // D1 ordering constraint: fill_bkg_rect must appear BEFORE the first DISPLAY_ON.
        // SceneVisitor currently inserts a zone-load inline DISPLAY_ON BEFORE the card-scene
        // builder's clear block — this assertion is RED against unfixed codegen.
        val fillIdx = enterBody.indexOf("fill_bkg_rect(0u, 0u, 32u, 32u, 0u)")
        val firstDisplayOnIdx = enterBody.indexOf("DISPLAY_ON;")
        assertTrue(
            fillIdx >= 0 && firstDisplayOnIdx >= 0 && fillIdx < firstDisplayOnIdx,
            "fill_bkg_rect(0u,0u,32u,32u,0u) must appear BEFORE the first DISPLAY_ON; in " +
                "nextLevelScene_enter (D1 flip-frame fix — LCD must be off during BG clear). " +
                "fillIdx=$fillIdx firstDisplayOnIdx=$firstDisplayOnIdx. " +
                "enter body:\n${enterBody.take(4000)}",
        )
    }

    @Test
    fun `nextLevelScene_enter emits fill_bkg_rect 32x32 covering full GBC BG plane (Phase 12_9 D-11)`() {
        val gameIR = buildLevelCardSceneWithZoneDsl()
        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Brace-walk extraction — locks assertions inside nextLevelScene_enter scope.
        // The function signature prefix `void nextLevelScene_enter` matches both
        // `void nextLevelScene_enter(void) BANKED {` and `void nextLevelScene_enter(void) {`.
        val enterBody = extractFunctionBody(bank1C, "void nextLevelScene_enter")
        File(EVIDENCE_DIR, "nextLevelScene_enter_d11.c").writeText(enterBody)

        assertTrue(
            enterBody.isNotEmpty(),
            "nextLevelScene_enter body must be extractable via brace-walk from bank1.c " +
                "(SceneVisitor.kt emits scene-enter functions as `\${scene.id}_enter`; " +
                "LevelCardSceneDelegate captures property name `nextLevelScene` verbatim, " +
                "so the function is `nextLevelScene_enter`). " +
                "bank1.c head:\n${bank1C.take(4000)}",
        )

        // D-11 positive: 32x32 fill covers the full GBC BG hardware-addressable tile area.
        // LCDC.3 selects 256x256 pixel BG map = 32x32 tiles. Camera scroll in gameplay
        // writes tile data to columns 20-31; after `move_bkg(0u, 0u)` in the transition,
        // those columns are visible — only a full 32x32 clear removes the residual indices.
        assertTrue(
            enterBody.contains("fill_bkg_rect(0u, 0u, 32u, 32u, 0u)"),
            "nextLevelScene_enter body must contain `fill_bkg_rect(0u, 0u, 32u, 32u, 0u)` " +
                "(Phase 12.9 D-11: 32x32 covers the full GBC BG hardware plane — see " +
                "PlatformerExtensions.kt:867 and DIAGNOSTIC.md D-10 reframe). " +
                "enter body:\n${enterBody.take(4000)}",
        )

        // D-11 negative: the old 20x18 shape (DEVICE_SCREEN_WIDTH / DEVICE_SCREEN_HEIGHT)
        // must be fully purged from the enter body. A stale generation or a revert of
        // PlatformerExtensions.kt would cause this assertion to fail — preventing the
        // R-03 + R-04 regression from silently re-entering.
        assertFalse(
            enterBody.contains("fill_bkg_rect(0u, 0u, DEVICE_SCREEN_WIDTH"),
            "nextLevelScene_enter body must NOT contain the old " +
                "`fill_bkg_rect(0u, 0u, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0u)` shape " +
                "(Phase 12.9 D-11 replacement: DEVICE_SCREEN_WIDTH=20 only clears visible " +
                "viewport, leaving columns 20-31 with residual 0x0E/0x0F tile indices). " +
                "enter body:\n${enterBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // Test 8 — Phase 12.9 Polish+WR-01: card-scene enter does NOT emit the (0,0)
    //          tilemap-place `_bkg_tiles_load_banked(<bank>, 0, 0, ...)` before
    //          the centered redraw.
    //
    // Root cause: the zone-load prelude in SceneVisitor (~line 254-288) always
    // emits `_bkg_tiles_load_banked(bank, 0, 0, w, h, tilemap)` to position the
    // tilemap at BG origin before the user's enterOps run. For the card scene,
    // LevelCardSceneBuilder.materialize() subsequently calls
    // `fill_bkg_rect(0u, 0u, 32u, 32u, 0u)` to wipe the FULL BG plane and then
    // redraws the card CENTERED. The zone-load tilemap-place at (0,0) is therefore
    // immediately overwritten — a brief top-left flash before centering.
    //
    // Fix (polish): detect the card overdraw via an exact-match signal —
    //   `scene.enterOps.any { it is RawOp && it.code.contains("fill_bkg_rect(0u, 0u, 32u, 32u,
    // 0u)") }`
    // When true, SKIP the `_bkg_tiles_load_banked(bank, 0, 0, ...)` CExprStatement from
    // the zone-load prelude (keep pixelLoad=set_bkg_data and keep set_bkg_palette).
    //
    // WR-01: replace the fragile substring check `code.contains("DISPLAY_ON")` with
    //   an exact-match `code.trim() == "DISPLAY_ON;"` so a comment mentioning
    //   "DISPLAY_ON" or a macro named "DISPLAY_ONCE" cannot accidentally suppress
    //   the inline DISPLAY_ON.
    //
    // Assertions:
    //   (1) card-scene enter does NOT contain `_bkg_tiles_load_banked(<bank>, 0u, 0u,`
    //       (the (0,0) origin placement — only the centered draw remains).
    //   (2) card-scene enter still contains `fill_bkg_rect(0u, 0u, 32u, 32u, 0u)`.
    //   (3) card-scene enter still contains at least one `DISPLAY_ON;`.
    //
    // RED: assertion (1) fails until the tilemap-place skip is added to SceneVisitor.
    // GREEN: after the skip is in place, all three assertions pass.
    //
    // Fixture: buildLevelCardSceneWithZoneDsl() — the zone-bound card scene.
    // -------------------------------------------------------------------------

    @Test
    fun `nextLevelScene_enter does NOT emit 0-0 tilemap-place before centered redraw (Phase 12_9 Polish+WR-01)`() {
        val gameIR = buildLevelCardSceneWithZoneDsl()
        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()

        val enterBody = extractFunctionBody(bank1C, "void nextLevelScene_enter")
        File(EVIDENCE_DIR, "nextLevelScene_enter_no_00_place.c").writeText(enterBody)

        assertTrue(
            enterBody.isNotEmpty(),
            "nextLevelScene_enter body must be extractable via brace-walk. " +
                "bank1.c head:\n${bank1C.take(4000)}",
        )

        // Polish (1) negative: the (0,0) tilemap-place must be ABSENT from the card-scene enter.
        // SceneVisitor skips `_bkg_tiles_load_banked(<bank>, 0, 0, ...)` when the card-overdraw
        // signal is detected (fill_bkg_rect(0u, 0u, 32u, 32u, 0u) present in enterOps).
        // RED: current codegen always emits the (0,0) place → assertion fails.
        assertFalse(
            enterBody.contains("_bkg_tiles_load_banked(") && enterBody.contains(", 0u, 0u,"),
            "Phase 12.9 Polish+WR-01: nextLevelScene_enter must NOT emit the (0,0) " +
                "`_bkg_tiles_load_banked(<bank>, 0u, 0u, ...)` tilemap-place when the card " +
                "overdraw signal is present (fill_bkg_rect(0u,0u,32u,32u,0u) wipes it " +
                "immediately — skip eliminates the top-left flash). " +
                "enter body:\n${enterBody.take(4000)}",
        )

        // Polish (2) positive: fill_bkg_rect still present (card clear still runs).
        assertTrue(
            enterBody.contains("fill_bkg_rect(0u, 0u, 32u, 32u, 0u)"),
            "nextLevelScene_enter must still contain fill_bkg_rect(0u, 0u, 32u, 32u, 0u) " +
                "(the card BG clear must not be affected by the tilemap-place skip). " +
                "enter body:\n${enterBody.take(4000)}",
        )

        // Polish (3) positive: DISPLAY_ON still present.
        assertTrue(
            enterBody.contains("DISPLAY_ON;"),
            "nextLevelScene_enter must still contain DISPLAY_ON; " +
                "(LCD must be re-enabled after the card clear). " +
                "enter body:\n${enterBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // Test 9 — Phase 12.9 WR-01 back-compat: normal zone scene STILL emits the
    //          (0,0) `_bkg_tiles_load_banked` tilemap-place.
    //
    // Confirms that scenes WITHOUT the card overdraw signal (no
    // fill_bkg_rect(0u, 0u, 32u, 32u, 0u) in enterOps) keep the (0,0) tilemap-
    // place UNCHANGED — byte-identical to pre-Phase-12.9-12 codegen.
    //
    // The gameplay scene in buildLevelCardSceneWithZoneDsl() has a zone bound
    // (gameplayZone1 / tilesetPath set) but does NOT emit fill_bkg_rect in its
    // enterOps → the (0,0) tilemap-place must be present in gameplay_enter.
    //
    // RED: would fail if the card-overdraw skip accidentally applied to all scenes.
    // GREEN: normal scene path unaffected — (0,0) place still emitted.
    // -------------------------------------------------------------------------

    @Test
    fun `gameplay_enter still emits 0-0 tilemap-place for normal zone scene (WR-01 back-compat)`() {
        val gameIR = buildLevelCardSceneWithZoneDsl()
        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()

        val enterBody = extractFunctionBody(bank1C, "void gameplay_enter")
        File(EVIDENCE_DIR, "gameplay_enter_normal_00_place.c").writeText(enterBody)

        assertTrue(
            enterBody.isNotEmpty(),
            "gameplay_enter body must be extractable from bank1.c. " +
                "bank1.c head:\n${bank1C.take(4000)}",
        )

        // Back-compat positive: normal zone scene always emits the (0,0) tilemap-place.
        assertTrue(
            enterBody.contains("_bkg_tiles_load_banked("),
            "WR-01 back-compat: gameplay_enter must contain `_bkg_tiles_load_banked(` " +
                "(normal zone scene is unaffected by the card-overdraw skip). " +
                "enter body:\n${enterBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // Test 10 — Phase 13.5 Req #18: nextLevelScene_enter emits full screenMode
    //           superset via screen() primitive (hide_sprites, move_bkg, fill_bkg_rect,
    //           centered _bkg_tiles_load_banked)
    //
    // Locks the Phase 13.5 Plan 06 migration: LevelCardSceneBuilder.screen(asset(...))
    // forwards to SceneBuilder.screen() via sceneBuilderBlocks, which synthesises a
    // _screen_nextLevelScene ZoneIR with screenMode=true. SceneVisitor's screenMode
    // superset branch emits the full centered-draw sequence in nextLevelScene_enter.
    //
    // This test is structurally similar to Tests 5-8 (same fixture, same brace-walk
    // helper) but asserts the PRESENCE of the four screenMode superset elements rather
    // than just fill_bkg_rect shape/order — it anchors the Req #18 contract at the
    // codegen boundary.
    //
    // Scope-level grep gate: assertions fire against the brace-walked
    // `nextLevelScene_enter` body (not bank1.c at file scope).
    // -------------------------------------------------------------------------

    @Test
    fun `nextLevelScene_enter emits full screenMode superset via screen() primitive (Phase 13_5 Req18)`() {
        val gameIR = buildLevelCardSceneWithZoneDsl()
        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()

        val enterBody = extractFunctionBody(bank1C, "void nextLevelScene_enter")
        File(EVIDENCE_DIR, "nextLevelScene_enter_screen_superset.c").writeText(enterBody)

        assertTrue(
            enterBody.isNotEmpty(),
            "nextLevelScene_enter body must be extractable via brace-walk from bank1.c " +
                "(Phase 13.5 Req #18: screen() on LevelCardSceneBuilder forwards to " +
                "SceneBuilder.screen() which synthesises _screen_nextLevelScene ZoneIR " +
                "with screenMode=true). bank1.c head:\n${bank1C.take(4000)}",
        )

        // Req #18 positive (1): hide_sprites_range — LCD blanked before BG redraw.
        assertTrue(
            enterBody.contains("hide_sprites_range(0u, MAX_HARDWARE_SPRITES)"),
            "nextLevelScene_enter body must contain `hide_sprites_range(0u, MAX_HARDWARE_SPRITES)` " +
                "(SceneVisitor screenMode superset step 1: hide all OAM sprites so they do " +
                "not flash over the card art during BG redraw). " +
                "enter body:\n${enterBody.take(4000)}",
        )

        // Req #18 positive (2): move_bkg(0u, 0u) — scroll reset before centering.
        assertTrue(
            enterBody.contains("move_bkg(0u, 0u)"),
            "nextLevelScene_enter body must contain `move_bkg(0u, 0u)` " +
                "(SceneVisitor screenMode superset step 2: reset BG scroll to origin so " +
                "the centred placement math starts from a known base). " +
                "enter body:\n${enterBody.take(4000)}",
        )

        // Req #18 positive (3): fill_bkg_rect(0u, 0u, 32u, 32u, 0u) — full BG plane clear.
        assertTrue(
            enterBody.contains("fill_bkg_rect(0u, 0u, 32u, 32u, 0u)"),
            "nextLevelScene_enter body must contain `fill_bkg_rect(0u, 0u, 32u, 32u, 0u)` " +
                "(SceneVisitor screenMode superset step 3: clear all 32x32 tiles of the GBC " +
                "BG plane — not just the 20x18 viewport — so camera-scroll residuals in " +
                "cols 20-31 are erased before the centred draw; Phase 12.9 D-11). " +
                "enter body:\n${enterBody.take(4000)}",
        )

        // Req #18 positive (4): centering expression references DEVICE_SCREEN_WIDTH.
        // The SceneVisitor superset emits:
        //   _bkg_tiles_load_banked(<bank>, (DEVICE_SCREEN_WIDTH - <w>) / 2u, <y>, <w>, <h>, <data>)
        // Asserting the DEVICE_SCREEN_WIDTH substring confirms the centred placement was
        // emitted, without hard-coding the exact tile dimensions (which vary per PNG).
        assertTrue(
            enterBody.contains("DEVICE_SCREEN_WIDTH"),
            "nextLevelScene_enter body must contain `DEVICE_SCREEN_WIDTH` " +
                "(SceneVisitor screenMode superset step 4: centred _bkg_tiles_load_banked " +
                "uses `(DEVICE_SCREEN_WIDTH - <w>) / 2u` as the X offset — the constant " +
                "appears in the tile-load call even though its value is known at compile " +
                "time, allowing SDCC to fold it while keeping the codegen readable). " +
                "enter body:\n${enterBody.take(4000)}",
        )
    }
}
