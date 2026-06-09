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
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ZoneIR
import io.github.gbkt.genre.platformer.dsl.levelCardScene
import io.github.gbkt.genre.platformer.dsl.platformerPhysics
import io.github.gbkt.genre.platformer.dsl.tilemapCollision
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// SETUP_CURRENT_LEVEL DISPLAY_OFF/DISPLAY_ON GATE EMISSION TEST
// Phase 12.11 / Wave 2 (Plan 12.11-02) — RED→GREEN guard for Failure B fix.
//
// Root cause (Plan 12.11-01 DIAGNOSTIC.md, Failure B CONFIRMED):
// `setup_current_level()` calls `_bkg_set_level_submap_banked()` (which calls
// `set_bkg_submap`) while `LCDC.7 = 1` (display ON). VRAM BG map writes while
// the LCD is active are dropped or corrupted in Coffee-GB, producing a 98.3%
// near-blank frame. The fix (Plan 12.11-03) wraps `setup_current_level()` with
// `DISPLAY_OFF` before the switch and `DISPLAY_ON` after — mirroring the GBDK
// reference contract exactly.
//
// Fix site: `GBDKPipeline.buildSetupCurrentLevelFunctionIfNeeded`
//
// Required corrected generated-C token contract (from DIAGNOSTIC.md):
//   void setup_current_level(void) NONBANKED {
//       _current_level = _next_level;
//       DISPLAY_OFF;                     // ← ADD before switch (fix token)
//       switch (_current_level % <N>u) {
//       ...
//       }
//       DISPLAY_ON;                      // ← ADD after switch (fix token)
//   }
//
// Ordering constraint (D-04):
//   indexOf("DISPLAY_OFF") < indexOf("switch (_current_level") < lastIndexOf("DISPLAY_ON")
//
// Test 1 (B site, RED pre-fix): Brace-walk setup_current_level body; assert
//   DISPLAY_OFF present, DISPLAY_ON present, and ordering constraint holds.
//   RED against HEAD — pre-fix body has neither token.
//
// Test 2 (A site, GREEN preservation guard): Brace-walk nextLevelScene_frame
//   body; assert setup_current_level() BEFORE navigate_to_scene(SCENE_GAMEPLAY).
//   Mirrors LevelCardSceneEmissionTest Test 1. Must stay GREEN after Plan 03.
//
// Test 3 (scope-grep hygiene, GREEN): Assert the extracted setup_current_level
//   body does NOT contain `void main(` — proves the brace-walk is scope-bounded
//   and cannot false-positive on main()'s own startup DISPLAY_OFF (DIAGNOSTIC.md
//   confirms DISPLAY_OFF at main.c:747 and DISPLAY_ON at main.c:758 are inside
//   main(), NOT setup_current_level(). File-level grep -c would return 2 even
//   pre-fix — this test guards against that Pitfall 2 false positive).
//
// Per CLAUDE.md §"Scope-level grep gates (corollary)": brace-walk helper
// extracts the setup_current_level() body before asserting — file-level grep
// is explicitly forbidden for this assertion.
//
// T-12.11-04 (threat register): Test 3 proves the extraction is scope-bounded.
// T-12.11-05 (threat register): SUMMARY.md records the literal RED test 1
//   output — the guard is meaningless if it passes pre-fix.
// =============================================================================

// ---------------------------------------------------------------------------
// Brace-walk helper (shared per-class convention, copy verbatim from
// LevelCardSceneEmissionTest per the Phase 12.7 inline-per-class convention —
// keeps the test self-contained; duplication is intentional per PATTERNS.md
// § Scope-level grep gates corollary).
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Game IR fixture — a tilemap_collision GenericSystem (gates
// buildSetupCurrentLevelFunctionIfNeeded ON) plus one gameplay zone whose id
// does NOT contain title/nextlevel and whose tilesetPath != null (Pitfall 10).
// A second empty scene escapes the single-scene HOME fast-path.
// Copied verbatim from SetupCurrentLevelPaletteEmissionTest.
// ---------------------------------------------------------------------------

private fun buildTilemapCollisionGame(target: GbcTarget = GbcTarget.GBC_COMPATIBLE) =
    GameIR(
        name = "SetupCurrentLevelDisplayGateTest",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 4, gbcTarget = target),
        scenes = listOf(
            SceneIR(id = "title"),
            SceneIR(id = "gameplay"),
        ),
        zones = listOf(
            ZoneIR(
                id = "world1Area1Zone",
                name = "World 1 Area 1",
                tilesetPath = "tiles/world1.png", // NON-NULL → mirrors real gameplay zone (Pitfall 10)
                mapWidth = 60,
                mapHeight = 18,
            )
        ),
        systems = listOf(
            GenericSystem(id = "tilemapCollision", config = mapOf("type" to "tilemap_collision")),
        ),
        startScene = "title",
    )

// ---------------------------------------------------------------------------
// DSL fixture for A-site (Test 2) — mirrors the LevelCardSceneEmissionTest
// buildLevelCardSceneGameDsl() fixture, supplying a levelCardScene with
// onStartPress(gameplayScene) so nextLevelScene_frame is generated in bank1.c.
// ---------------------------------------------------------------------------

private fun buildLevelCardSceneGameDsl() =
    game("SetupCurrentLevelDisplayGateTestDsl") {
            // Path A activates gameUsesTilemapCollision (gate-on prerequisite for
            // setup_current_level and the main-loop level-switch guard).
            platformerPhysics { solidThreshold(17) }

            // Bind the symbol names the per-case body's spawn writes will reference.
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

            // Two gameplay zones — ensures the switch body has ≥2 case branches.
            val gameplayZone1 by zone {
                tileset(asset("res/graphics/level1.png"))
                spawn(40u, 120u)
            }
            val gameplayZone2 by zone {
                tileset(asset("res/graphics/level2.png"))
                spawn(40u, 120u)
            }

            val titleScene = scene("title") {
                enter { cEmit("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);") }
                frame { whenever(buttons.start.pressed) { navigate(SceneRef("gameplay")) } }
            }

            // Gameplay scene declared BEFORE levelCardScene (RESEARCH Pitfall 5).
            val gameplayScene = scene("gameplay") {
                zone(gameplayZone1)
                frame { whenever(buttons.start.pressed) { navigate(SceneRef("nextLevelScene")) } }
            }

            // Hidden scene binding the 2nd gameplay zone so it surfaces in gameIR.zones.
            scene("gameplay2") {
                zone(gameplayZone2)
                frame { whenever(buttons.start.pressed) { navigate(SceneRef("gameplay")) } }
            }

            // levelCardScene delegate — property name "nextLevelScene" becomes scene id →
            // lowered frame function is "nextLevelScene_frame" in bank1.c.
            @Suppress("UNUSED_VARIABLE")
            val nextLevelScene by levelCardScene { onStartPress(gameplayScene) }

            start = titleScene
        }
        .build()

// =============================================================================
// TEST CLASS
// =============================================================================

class SetupCurrentLevelDisplayGateEmissionTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1 — B site: setup_current_level DISPLAY_OFF/DISPLAY_ON gate (RED)
    //
    // Pre-fix (current) generator: setup_current_level body has NEITHER
    // DISPLAY_OFF nor DISPLAY_ON (DIAGNOSTIC.md confirmed: only main() startup
    // block at lines 747/758 has them; setup_current_level lines 145-406 have
    // none). This test is RED against the pre-fix generator.
    //
    // Post-fix (Plan 03): DISPLAY_OFF is emitted immediately before the switch;
    // DISPLAY_ON is emitted after the closing switch }. This test goes GREEN.
    //
    // Ordering constraint (D-04 / DIAGNOSTIC.md Fix Site 1):
    //   indexOf("DISPLAY_OFF") < indexOf("switch (_current_level") < lastIndexOf("DISPLAY_ON")
    //
    // Scope: assertions fire against the brace-walked setup_current_level body,
    // NOT file-level main.c. DIAGNOSTIC.md confirms the pre-fix state has
    // DISPLAY_OFF and DISPLAY_ON only inside main() (lines 747/758) — file-level
    // grep -c main.c would return 2 and mask the gap.
    // =========================================================================

    @Test
    fun `setup_current_level body contains DISPLAY_OFF before switch and DISPLAY_ON after (B-site fix token, RED pre-fix)`() {
        val gameIR = buildTilemapCollisionGame(GbcTarget.GBC_COMPATIBLE)
        val allFiles = pipeline.generate(gameIR).files
        val mainC = allFiles["main.c"] ?: error("main.c not generated. Files: ${allFiles.keys}")

        // Brace-walk extraction — mandatory per CLAUDE.md § Scope-level grep gates
        // corollary. Extracts setup_current_level(void) NONBANKED scope only.
        // The signature prefix "void setup_current_level(void) NONBANKED" matches
        // the pre-fix generated function declaration at main.c:145-147
        // (DIAGNOSTIC.md evidence: `void setup_current_level(void) NONBANKED {`).
        val body = extractFunctionBody(mainC, "void setup_current_level(void) NONBANKED")
        assertTrue(
            body.isNotEmpty(),
            "setup_current_level body must be extractable via brace-walk from main.c. " +
                "main.c head:\n${mainC.take(4000)}",
        )

        // B-site assertion 1: DISPLAY_OFF present before the switch.
        // RED pre-fix: DIAGNOSTIC.md confirmed body has no DISPLAY_OFF token.
        // GREEN post-fix (Plan 03): buildSetupCurrentLevelFunctionIfNeeded adds it.
        assertTrue(
            body.contains("DISPLAY_OFF"),
            "setup_current_level body must contain DISPLAY_OFF (B-site fix token — LCD must " +
                "be disabled before VRAM BG map writes in _bkg_set_level_submap_banked; see " +
                "DIAGNOSTIC.md Fix Site 1 + GBDK reference contract). " +
                "body:\n${body.take(4000)}",
        )

        // B-site assertion 2: DISPLAY_ON present after the switch.
        // RED pre-fix: DIAGNOSTIC.md confirmed body has no DISPLAY_ON token.
        // GREEN post-fix (Plan 03): buildSetupCurrentLevelFunctionIfNeeded adds it.
        assertTrue(
            body.contains("DISPLAY_ON"),
            "setup_current_level body must contain DISPLAY_ON (B-site fix token — LCD must " +
                "be re-enabled after all VRAM writes complete; see DIAGNOSTIC.md Fix Site 1 + " +
                "GBDK reference contract `DISPLAY_OFF; ... DISPLAY_ON;`). " +
                "body:\n${body.take(4000)}",
        )

        // B-site assertion 3: ordering constraint (D-04 mandatory).
        // DISPLAY_OFF must precede `switch (_current_level`; DISPLAY_ON must follow the
        // last closing } of the switch. This is the exact ordering the GBDK reference
        // contract specifies (DIAGNOSTIC.md Fix Site 1):
        //   DISPLAY_OFF; → switch (...) { ... } → DISPLAY_ON;
        val displayOffIdx = body.indexOf("DISPLAY_OFF")
        val switchIdx = body.indexOf("switch (_current_level")
        val displayOnLastIdx = body.lastIndexOf("DISPLAY_ON")
        assertTrue(
            displayOffIdx >= 0 && switchIdx > displayOffIdx && displayOnLastIdx > switchIdx,
            "Ordering constraint (D-04): indexOf(DISPLAY_OFF) < indexOf(switch (_current_level) " +
                "< lastIndexOf(DISPLAY_ON) must hold in the brace-walked setup_current_level body. " +
                "displayOffIdx=$displayOffIdx switchIdx=$switchIdx displayOnLastIdx=$displayOnLastIdx. " +
                "body:\n${body.take(4000)}",
        )
    }

    // =========================================================================
    // Test 2 — A site: setup_current_level() before navigate_to_scene(SCENE_GAMEPLAY)
    //          in nextLevelScene_frame (preservation guard, stays GREEN)
    //
    // Mirrors LevelCardSceneEmissionTest Test 1 (D-05 re-pointed ordering contract).
    // Asserts that Plan 03's DISPLAY_OFF/ON fix does NOT accidentally reorder
    // the Start-press handler's call sequence. setup_current_level() must still
    // come BEFORE navigate_to_scene(SCENE_GAMEPLAY) in nextLevelScene_frame.
    //
    // This test should be GREEN both before AND after Plan 03.
    // If it turns RED after Plan 03, the fix regressed the A-site ordering.
    // =========================================================================

    @Test
    fun `nextLevelScene_frame emits setup_current_level before navigate_to_scene(SCENE_GAMEPLAY) (A-site preservation guard, GREEN)`() {
        val gameIR = buildLevelCardSceneGameDsl()
        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        // Brace-walk extraction — scope-bounded to nextLevelScene_frame body.
        // Signature prefix "void nextLevelScene_frame" matches both
        // `void nextLevelScene_frame(void) BANKED {` and `void nextLevelScene_frame(void) {`
        // (CEmitter emits banked functions with or without the BANKED qualifier).
        val frameBody = extractFunctionBody(bank1C, "void nextLevelScene_frame")
        assertTrue(
            frameBody.isNotEmpty(),
            "nextLevelScene_frame body must be extractable via brace-walk from bank1.c " +
                "(SceneVisitor emits scene-frame functions as `\${scene.id}_frame`; " +
                "LevelCardSceneDelegate captures property name `nextLevelScene` verbatim). " +
                "bank1.c head:\n${bank1C.take(4000)}",
        )

        // A-site positive 1: setup_current_level() must be present in the frame body.
        assertTrue(
            frameBody.contains("setup_current_level()"),
            "nextLevelScene_frame body must contain `setup_current_level()` " +
                "(A-site ordering preservation — call moved from main() to levelCardScene " +
                "Start-press path per Plans 12.6-02 + 12.6-04 + 12.6-05). " +
                "frame body:\n${frameBody.take(4000)}",
        )

        // A-site positive 2: navigate_to_scene(SCENE_GAMEPLAY) must be present.
        assertTrue(
            frameBody.contains("navigate_to_scene(SCENE_GAMEPLAY)"),
            "nextLevelScene_frame body must contain `navigate_to_scene(SCENE_GAMEPLAY)` " +
                "(the Start-press handler's navigate target from onStartPress(gameplayScene)). " +
                "frame body:\n${frameBody.take(4000)}",
        )

        // A-site ordering constraint (D-02 reference-accuracy contract):
        // setup_current_level() must come STRICTLY BEFORE navigate_to_scene(SCENE_GAMEPLAY).
        // A regression that flipped these would re-introduce DEFECT-1's same-frame VRAM stomp.
        val setupIdx = frameBody.indexOf("setup_current_level()")
        val navIdx = frameBody.indexOf("navigate_to_scene(SCENE_GAMEPLAY)")
        assertTrue(
            setupIdx >= 0 && navIdx > setupIdx,
            "setup_current_level() must appear BEFORE navigate_to_scene(SCENE_GAMEPLAY) in " +
                "nextLevelScene_frame (A-site D-02 reference-accuracy preservation guard). " +
                "setupIdx=$setupIdx navIdx=$navIdx. frame body:\n${frameBody.take(4000)}",
        )
    }

    // =========================================================================
    // Test 3 — Scope-grep hygiene: extracted body is bounded (does NOT contain main)
    //
    // Guards against Pitfall 2 (DIAGNOSTIC.md): file-level grep on main.c for
    // DISPLAY_OFF returns 2 hits — both inside `void main(void)` at lines 747/758.
    // A broken brace-walk that returned the whole file would let Test 1 pass as
    // GREEN pre-fix (false positive). This test proves the extraction is
    // scope-bounded by asserting that the extracted body does NOT contain
    // `void main(` — the main() function signature.
    //
    // T-12.11-04 (threat register mitigate): this assertion is the explicit guard.
    // This test should be GREEN both before AND after Plan 03.
    // =========================================================================

    @Test
    fun `extractFunctionBody scope-bounded — extracted setup_current_level body does NOT contain main() signature (scope-grep hygiene, GREEN)`() {
        val gameIR = buildTilemapCollisionGame(GbcTarget.GBC_COMPATIBLE)
        val allFiles = pipeline.generate(gameIR).files
        val mainC = allFiles["main.c"] ?: error("main.c not generated. Files: ${allFiles.keys}")

        // Precondition: main.c must contain DISPLAY_OFF somewhere (in main() startup)
        // so this test would be meaningful — if the whole file has no DISPLAY_OFF,
        // there is nothing to false-positive on.
        assertTrue(
            mainC.contains("DISPLAY_OFF"),
            "main.c must contain DISPLAY_OFF somewhere (pre-fix: in main() startup block " +
                "at line 747 per DIAGNOSTIC.md) for the scope-hygiene assertion to be meaningful. " +
                "main.c head:\n${mainC.take(4000)}",
        )

        // Brace-walk extraction — must return only the setup_current_level scope.
        val body = extractFunctionBody(mainC, "void setup_current_level(void) NONBANKED")
        assertTrue(
            body.isNotEmpty(),
            "setup_current_level body must be extractable. " +
                "main.c head:\n${mainC.take(4000)}",
        )

        // Scope-hygiene assertion (T-12.11-04):
        // The extracted body must NOT contain `void main(` — if it does, the brace-walk
        // returned more than just setup_current_level's scope and could false-positive
        // on main()'s DISPLAY_OFF/DISPLAY_ON at lines 747/758.
        assertFalse(
            body.contains("void main("),
            "Scope-hygiene: the brace-walked setup_current_level body must NOT contain " +
                "`void main(` — if it does, the extraction returned beyond setup_current_level's " +
                "closing brace and could falsely satisfy Test 1's DISPLAY_OFF assertion using " +
                "main()'s startup DISPLAY_OFF (Pitfall 2 false positive guard, T-12.11-04). " +
                "extracted body:\n${body.take(4000)}",
        )
    }
}
