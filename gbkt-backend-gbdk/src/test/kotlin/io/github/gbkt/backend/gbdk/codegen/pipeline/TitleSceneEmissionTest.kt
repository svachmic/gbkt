/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.dsl.SceneRef
import io.github.gbkt.core.dsl.asset
import io.github.gbkt.core.dsl.buttons
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.dsl.zone
import io.github.gbkt.genre.platformer.dsl.platformerPhysics
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// D-16 INVARIANT #1 — title scene tile-data load + title→gameplay navigation
//
// Plan 12-09b locks invariant #1 emission at the JVM tier via per-function awk
// brace-walk extraction. Anchor 1 currently has ONLY emulator-runtime UAT
// (Plan 12-19); per CLAUDE.md §"Scope-level grep gates corollary", emulator-
// runtime assertions cannot independently catch a future codegen regression —
// they are downstream of the generated C shape. This test locks the shape one
// level below the visual outcome.
//
// What anchor 1 means (per 12-VALIDATION row 1 + 12-CONTEXT D-08):
//
//   First-half:  title_enter loads the title-card tileset (the substrate the
//                player sees) via the zone-binder scene-enter pattern landed by
//                Plan 11.1 (set_bkg_data + _bkg_tiles_load_banked + SHOW_BKG)
//                plus the explicit `fill_bkg_rect` clear emitted via cEmit()
//                (Plan 12-17 Deviation #2: cEmit is the actual DSL bridge until
//                a proper `bgFill()` primitive lands in Phase 13).
//
//   Second-half: title_frame navigates to gameplay on Start press — the
//                `runIf(buttons.start.pressed) { navigate(gameplayScene) }`
//                lowers to `navigate_to_scene(SCENE_GAMEPLAY)` via
//                ScriptOpVisitor.visitNavigateTo (ScriptOpVisitor.kt:675+).
//
// Plan-conflict resolution (documented Rule 1 deviation — see 12-09b-SUMMARY
// §Deviations): the plan's literal Task 1 Test 2 instructed asserting
// `setup_current_level` inside `gameplay_enter` body. Predecessor Plan 12-17
// SUMMARY §Deviation 5 explicitly states that wiring was NOT done — the helper
// is called from main()'s level-switch guard, not from gameplay_enter. The
// reshape below locks the half of invariant #1 that 12-17 DID ship: title_enter
// emits the tile-data load substrate (set_bkg_data + _bkg_tiles_load_banked +
// SHOW_BKG + fill_bkg_rect). Plan 12-09b Task 2's
// `setupCurrentLevel_function_emitted` already locks the existence of the
// helper at column 0 of main.c, so the original Task 1 Test 2 assertion is
// preserved at the only correct scope (main.c, not gameplay_enter).
//
// Scope-level grep gate (CLAUDE.md §"Scope-level grep gates" corollary): each
// substring assertion fires against a brace-walked function body — a file-
// level `bank1C.contains("navigate_to_scene")` would false-positive on any
// other scene's frame body in the same bank file (e.g. a future pause_frame
// that also navigates). The brace-walk pattern below is the Kotlin counterpart
// of awk's `/^void name/` anchor + `gsub(/{/, ...)` depth counter.
// =============================================================================

class TitleSceneEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe).
         *
         * `user.dir` resolves to the Gradle project's working directory, which for the
         * `:gbkt-backend-gbdk:test` task is `<repo>/gbkt-backend-gbdk`. From there we ascend one
         * level (`..`) to reach the repo (or worktree) root, then descend into the phase evidence
         * directory. Hard-coding an absolute path would silently route evidence files outside the
         * active worktree and miss the commit (#3099 worktree path safety).
         *
         * Same pattern as TilemapCollisionEmissionTest (gbkt-genre-platformer) and
         * BanksEmissionTest (gbkt-examples/banks) — establishes the canonical phase 12 evidence
         * directory.
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape"
                )
                .normalize()
    }

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers — brace-walk extraction (awk-equivalent), per CLAUDE.md
    // §"Scope-level grep gates corollary"
    // -------------------------------------------------------------------------

    /**
     * Extracts a C function body by brace-walking from the first line whose contents START WITH
     * [functionSignaturePrefix] (e.g. `void title_frame`) until the matching closing brace at depth
     * zero.
     *
     * The returned blob includes the signature line and the closing brace, so downstream
     * `.contains()` checks operate ONLY on tokens that live inside the named function — never on
     * tokens from unrelated functions in the same file (per CLAUDE.md §"Scope-level grep gates").
     *
     * Matching is anchored to the START of a line (the prefix must appear at column 0) so
     * occurrences inside string literals, comments, or argument lists of a different function
     * cannot false-match. This is the literal counterpart of awk's `/^prefix/` anchor.
     *
     * Mirrors the helper in TilemapCollisionEmissionTest (gbkt-genre-platformer) and
     * BanksEmissionTest (gbkt-examples/banks) — copy verbatim per Plan 12-09b Task 1 instruction.
     */
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

    // -------------------------------------------------------------------------
    // POSITIVE — title_frame emits navigate_to_scene (D-16 #1 second-half)
    //
    // Production mechanism (ScriptOpVisitor.kt:675-678 — visitNavigateTo):
    // `runIf(buttons.start.pressed) { navigate(gameplayScene) }` lowers via
    // `NavigateTo("gameplay")` → `CCall("navigate_to_scene",
    // [CVar("SCENE_GAMEPLAY")])`. Within the title scene's frame body the call
    // sits inside the button-press conditional, so the brace-walked title_frame
    // body MUST contain `navigate_to_scene` as a substring.
    //
    // Anchor 1 link (12-VALIDATION row 1): this is the SHAPE Plan 12-19 (MCP
    // UAT) consumes — the emulator probe waits for `_current_scene_id`
    // transition from SCENE_TITLE to SCENE_GAMEPLAY on Start press; without
    // this emission the runtime probe would fail with `_current_scene_id`
    // stuck at SCENE_TITLE. JVM tier locks the codegen prerequisite; visual
    // tier confirms it actually fires.
    //
    // Scope-level grep gate: a file-level `bank1C.contains("navigate_to_scene")`
    // would false-positive on any other banked frame body that happens to
    // navigate elsewhere (the pipeline-generated trampoline stubs use the same
    // helper). The brace-walked title_frame scope is the locking pattern.
    // -------------------------------------------------------------------------

    @Test
    fun `titleFrame emits navigate_to_scene call on Start press`() {
        val gameIR =
            game("TitleSceneNavigateTest") {
                    // Minimal two-scene game: title + gameplay. solidThreshold is set so
                    // tilemap-collision globals + the level-switch substrate are gated ON
                    // (anchor 1 + anchor 5 share the same predicate). At least one gameplay
                    // zone is required for `buildSetupCurrentLevelFunctionIfNeeded` to emit
                    // the helper (per its `gameplayZones.isEmpty()` early-return); we add a
                    // single gameplay zone bound to gameplayScene to satisfy both gates.
                    platformerPhysics { solidThreshold(17) }

                    val titleZone by zone { tileset(asset("res/graphics/title-screen.png")) }
                    val gameplayZone by zone { tileset(asset("res/graphics/gameplay.png")) }
                    // NextLevel scene exists so the main()-loop guard (anchor 5) is also
                    // gated ON for the LevelSwitchEmissionTest companion. Title test does
                    // not depend on the guard but the setup mirrors the PlatformerTemplate
                    // composition Plan 12-17 ships, keeping the IR fixture identical across
                    // the two test files.
                    val nextLevelZone by zone { tileset(asset("res/graphics/next-level.png")) }

                    val titleScene =
                        scene("title") {
                            zone(titleZone)
                            enter {
                                // cEmit is the Plan 12-17 bridge until `bgFill()` lands in Phase
                                // 13.
                                // The exact literal text matters — TitleSceneEmissionTest Test 2
                                // (below) locks it as the visual prerequisite for the title screen.
                                cEmit("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);")
                            }
                            frame {
                                runIf(buttons.start.pressed) { navigate(SceneRef("gameplay")) }
                            }
                        }
                    scene("gameplay") {
                        // gameplay scene binds a zone so it shows up in the buildSceneFile
                        // zoneBankAllocation table. A frame body is required so SceneVisitor
                        // emits `gameplay_frame` (otherwise the scene-enter is the only
                        // codegen output for this scene).
                        zone(gameplayZone)
                        frame {
                            // No-op frame body — content is irrelevant to the title-side
                            // invariant under test, but a frame block is required for
                            // SceneVisitor to emit the function.
                            runIf(buttons.start.pressed) { navigate(titleScene) }
                        }
                    }
                    scene("nextLevel") {
                        frame { runIf(buttons.start.pressed) { navigate(SceneRef("gameplay")) } }
                    }
                    start = titleScene
                }
                .build()

        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Brace-walk extraction — Plan 12-09b Task 1 step 3 awk equivalent. The
        // `void title_frame` prefix at column 0 anchors the match (line.startsWith);
        // string-literal or comment occurrences inside other functions cannot match.
        val titleFrameBody = extractFunctionBody(bank1C, "void title_frame")

        // Evidence-before-assert (per gbkt-examples/banks' Plan 11-08 INV-1..4 pattern):
        // write the extracted body BEFORE assertions fire so a RED run still produces a
        // reviewable artifact on disk.
        File(EVIDENCE_DIR, "title_frame.c").writeText(titleFrameBody)

        assertTrue(
            titleFrameBody.isNotEmpty(),
            "title_frame must be emitted in bank1.c (the frame body lowers " +
                "`runIf(buttons.start.pressed) { navigate(\"gameplay\") }` via SceneVisitor). " +
                "bank1.c head:\n${bank1C.take(2000)}",
        )

        // The navigation contract — Plan 12-19 (anchor 1 MCP UAT) consumes the runtime
        // behavior of this call. JVM tier locks the codegen prerequisite.
        assertTrue(
            titleFrameBody.contains("navigate_to_scene"),
            "title_frame body must contain `navigate_to_scene` (the call ScriptOpVisitor " +
                "lowers `navigate(\"gameplay\")` to per ScriptOpVisitor.kt:675-678). " +
                "title_frame body:\n${titleFrameBody.take(4000)}",
        )

        // Scene-enum constant — locks the navigation target to the gameplay scene specifically
        // (not just any navigate_to_scene call). A regression that, e.g., changed the navigate
        // target from "gameplay" to "title" would emit `SCENE_TITLE` and break here even though
        // the file-level `navigate_to_scene` substring count would be unchanged.
        assertTrue(
            titleFrameBody.contains("SCENE_GAMEPLAY"),
            "title_frame body must reference the gameplay scene enum constant " +
                "(navigate(\"gameplay\") → navigate_to_scene(SCENE_GAMEPLAY)). " +
                "title_frame body:\n${titleFrameBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // POSITIVE — title_enter emits the banked tile-data load substrate (D-16 #1
    // first-half, reshape — see header note)
    //
    // Production mechanism (SceneVisitor.kt:151-230 — zoneLoadStatements):
    // when a scene binds a zone whose `tilesetPath != null`, the visitor prepends
    // the canonical three-statement zone-load sequence BEFORE user enter ops:
    //
    //   set_bkg_data(0u, _zone_titleZone_tileset_count, _zone_titleZone_tileset);
    //   _bkg_tiles_load_banked(<bank>, 0u, 0u,
    //       _zone_titleZone_tilemap_WIDTH, _zone_titleZone_tilemap_HEIGHT,
    //       _zone_titleZone_tilemap);
    //   SHOW_BKG;
    //
    // The WIDTH/HEIGHT args are CVar macro references (NOT CLiteral defaults) for
    // NEW-path zones. ConvertZoneTilesetsTask (Phase 12.2-06) emits these macros
    // from the actual PNG IHDR dimensions — they are the single source of truth.
    // Using CLiteral(zone.mapWidth/mapHeight) would emit the ZoneIR defaults (32,32)
    // for a tilemap that is only 20x9 tiles (180 bytes), causing GBDK to read 1024
    // tile entries from the 180-byte buffer and producing the row-doubling visual
    // defect (AC13 FAIL, Phase 12.2 debug fix: title-zone-path-a-render).
    //
    // The user's `cEmit("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);")` lowers to a
    // CRawCode statement via ScriptOpVisitor.visitRawOp (ScriptOpVisitor.kt:1610),
    // which CEmitter renders verbatim into the function body AFTER the zone-load
    // prelude.
    //
    // Anchor 1 link (12-VALIDATION row 1, first-half — title screen visible):
    // without this emission the title screen would never render (the player
    // sees the background tile slot 0 pattern only — typically all-zeros, an
    // empty grey screen). Plan 12-19 (MCP UAT) consumes the runtime visual;
    // this JVM tier locks the codegen prerequisite per CLAUDE.md §"Visual
    // Evidence Rule" (variable evidence is necessary but not sufficient — the
    // JVM tier guarantees the codegen, the visual tier confirms it renders).
    //
    // Scope-level grep gate: a file-level `bank1C.contains("set_bkg_data")` would
    // false-positive on any other scene that binds a zone (e.g. gameplay_enter
    // in this same test fixture, which also has a tilesetPath-carrying zone).
    // The brace-walk anchors at `void title_enter` so the assertions fire only
    // against the title scene's body.
    // -------------------------------------------------------------------------

    @Test
    fun `titleEnter emits tile-data load substrate when title scene binds a tileset zone`() {
        val gameIR =
            game("TitleSceneTileLoadTest") {
                    platformerPhysics { solidThreshold(17) }

                    val titleZone by zone { tileset(asset("res/graphics/title-screen.png")) }
                    val gameplayZone by zone { tileset(asset("res/graphics/gameplay.png")) }

                    val titleScene =
                        scene("title") {
                            zone(titleZone)
                            enter { cEmit("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);") }
                            frame {
                                runIf(buttons.start.pressed) { navigate(SceneRef("gameplay")) }
                            }
                        }
                    scene("gameplay") {
                        zone(gameplayZone)
                        frame { runIf(buttons.start.pressed) { navigate(titleScene) } }
                    }
                    start = titleScene
                }
                .build()

        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()
        val titleEnterBody = extractFunctionBody(bank1C, "void title_enter")
        File(EVIDENCE_DIR, "title_enter.c").writeText(titleEnterBody)

        assertTrue(
            titleEnterBody.isNotEmpty(),
            "title_enter must be emitted in bank1.c (the scene declares both a zone binder " +
                "and an enter { cEmit(...) } block; SceneVisitor.kt:114 emits when EITHER " +
                "enterOps OR zoneRefs is non-empty). bank1.c head:\n${bank1C.take(2000)}",
        )

        // set_bkg_data prelude — Phase 11.2 D-A3 / D-claude-4: pixel bytes to VRAM tile-data
        // area BEFORE the tilemap copy. The first arg is `0u` (VRAM slot 0), then the symbolic
        // count `_zone_titleZone_tileset_count` (resolved by the synthesized
        // _zone_titleZone_tileset.h header at lcc link time per BanksEmissionTest INV-7).
        assertTrue(
            titleEnterBody.contains("set_bkg_data(0u, _zone_titleZone_tileset_count"),
            "title_enter must call set_bkg_data with the symbolic tileset count " +
                "(SceneVisitor.kt:174-185 zoneLoadStatements path A — Phase 11.2 D-A3). " +
                "title_enter body:\n${titleEnterBody.take(4000)}",
        )

        // _bkg_tiles_load_banked tilemap copy — references the Gradle-task-emitted
        // `_zone_titleZone_tilemap` symbol (Plan 11.1-17 Phase D NEW path: zones with
        // tilesetPath consume the tilemap symbol, NOT the legacy `_zone_<id>_tiles` stub).
        assertTrue(
            titleEnterBody.contains("_bkg_tiles_load_banked(") &&
                titleEnterBody.contains("_zone_titleZone_tilemap"),
            "title_enter must call _bkg_tiles_load_banked with _zone_titleZone_tilemap " +
                "(SceneVisitor.kt:189-225 zoneLoadStatements). " +
                "title_enter body:\n${titleEnterBody.take(4000)}",
        )

        // WIDTH/HEIGHT macro args — D-01 Path A debug fix (title-zone-path-a-render):
        // NEW-path zones must emit _zone_<id>_tilemap_WIDTH / _HEIGHT macro references, NOT
        // CLiteral(zone.mapWidth) / CLiteral(zone.mapHeight). The ZoneIR defaults are 32x32;
        // emitting them as literals reads 1024 entries from a 180-byte title tilemap and produces
        // the row-doubling defect. ConvertZoneTilesetsTask emits these macros from the actual PNG
        // IHDR dimensions — they are the single source of truth for tilemap geometry.
        assertTrue(
            titleEnterBody.contains("_zone_titleZone_tilemap_WIDTH"),
            "title_enter must reference _zone_titleZone_tilemap_WIDTH in the _bkg_tiles_load_banked " +
                "w arg (D-01 Path A fix: CLiteral(zone.mapWidth)=32 caused row-doubling on the " +
                "20x9-tile title screen). title_enter body:\n${titleEnterBody.take(4000)}",
        )
        assertTrue(
            titleEnterBody.contains("_zone_titleZone_tilemap_HEIGHT"),
            "title_enter must reference _zone_titleZone_tilemap_HEIGHT in the _bkg_tiles_load_banked " +
                "h arg (D-01 Path A fix: CLiteral(zone.mapHeight)=32 caused row-doubling on the " +
                "20x9-tile title screen). title_enter body:\n${titleEnterBody.take(4000)}",
        )

        // DISPLAY_ON re-enable — set_bkg_data/set_bkg_tiles both call display_off() internally,
        // clearing LCDC.7 (LCDCF_ON = 0b10000000, bit 7, the master LCD enable). Without DISPLAY_ON
        // the master LCD stays OFF and wait_vbl_done() hangs — no VBlank while LCD is disabled.
        // Phase 12.4 D-08 Rule 1 Bug fix: SHOW_BKG only sets LCDCF_BGON (bit 0), which is
        // already set by main()'s bootstrap — it does NOT restore bit 7. Confirmed by GBDK
        // hardware.h: LCDCF_ON = 0b10000000 (bit 7), LCDCF_BGON = 0b00000001 (bit 0). Same
        // class of bug as Phase 07.4 Plan 30 (racer DISPLAY_ON wrap after tilemap loads).
        assertTrue(
            titleEnterBody.contains("DISPLAY_ON"),
            "title_enter must emit DISPLAY_ON after the tilemap load (SceneVisitor.kt — " +
                "set_bkg_data/set_bkg_tiles' implicit display_off() must be reversed by " +
                "DISPLAY_ON not SHOW_BKG; Phase 12.4 D-08 Rule 1 Bug fix). " +
                "title_enter body:\n${titleEnterBody.take(4000)}",
        )

        // Ordering — pixel bytes BEFORE tile-index map BEFORE DISPLAY_ON (D-claude-4: pixel
        // data must reach VRAM before the tile-index map references the slots). A regression
        // that reorders these would leave one frame of zero-initialized VRAM between the
        // writes, causing the SEED-014 single-frame visual gap.
        val setBkgDataIdx = titleEnterBody.indexOf("set_bkg_data(")
        val loadBankedIdx = titleEnterBody.indexOf("_bkg_tiles_load_banked(")
        val displayOnIdx = titleEnterBody.indexOf("DISPLAY_ON")
        assertTrue(
            setBkgDataIdx in 0..<loadBankedIdx && loadBankedIdx < displayOnIdx,
            "title_enter ordering: set_bkg_data($setBkgDataIdx) < _bkg_tiles_load_banked" +
                "($loadBankedIdx) < DISPLAY_ON($displayOnIdx). " +
                "title_enter body:\n${titleEnterBody.take(4000)}",
        )

        // User-authored cEmit text — Plan 12-17 emits `fill_bkg_rect(0u, 0u, 20u, 18u, 0u);`
        // verbatim via CRawCode (ScriptOpVisitor.kt:1610). This lives in title_enter AFTER
        // the zone-load prelude (visitor splices user enterOps after zoneLoadStatements,
        // SceneVisitor.kt:236).
        assertTrue(
            titleEnterBody.contains("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);"),
            "title_enter must contain the cEmit-bridged fill_bkg_rect call verbatim " +
                "(Plan 12-17 Deviation #2: cEmit bridges until bgFill() lands in Phase 13). " +
                "title_enter body:\n${titleEnterBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // NEGATIVE GATE — game WITHOUT tilemap-collision does NOT emit
    // setup_current_level (D-16 #1 gate-off byte-identical invariant)
    //
    // Production mechanism (GBDKPipeline.kt:2239-2294 —
    // buildSetupCurrentLevelFunctionIfNeeded): when `gameUsesTilemapCollision`
    // returns false (no platformer_physics system with solidThreshold AND no
    // zone with platformerPhysicsOverride[solidThreshold]), the helper returns
    // null and the function is omitted from main.c.
    //
    // Anchor link (12-CONTEXT D-08 anchor 5 — level-switch substrate): the
    // setup_current_level + main()-loop guard pair are gated together. A
    // regression that fires either gate unconditionally would emit
    // `setup_current_level` references against UNDECLARED `_zone_<id>_tilemap`
    // symbols (no zones declared → no tilemap symbols), failing SDCC link.
    //
    // Why this lives in TitleSceneEmissionTest (not LevelSwitchEmissionTest):
    // anchor 1 (title scene transition) is correctness-equivalent across games
    // that DO and DO NOT opt into tilemap-collision — the title→gameplay
    // navigate is independent of the platformer substrate. Locking the
    // gate-off shape here proves the title path is byte-identical for non-
    // platformer games (Pong, Breakout, Banks all rely on this invariant per
    // Plan 12-17 SUMMARY §Verification).
    // -------------------------------------------------------------------------

    @Test
    fun `noTilemap_omits_setupCurrentLevel`() {
        val gameIR =
            game("TitleSceneNoTilemapTest") {
                    // NO `platformerPhysics { solidThreshold(...) }` block — the gate stays OFF.
                    // No zones declared — Path B of gameUsesTilemapCollision (per-zone
                    // platformerPhysicsOverride) also returns false.
                    val titleScene =
                        scene("title") {
                            frame {
                                runIf(buttons.start.pressed) { navigate(SceneRef("gameplay")) }
                            }
                        }
                    scene("gameplay") {
                        frame { runIf(buttons.start.pressed) { navigate(titleScene) } }
                    }
                    start = titleScene
                }
                .build()

        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()
        File(EVIDENCE_DIR, "noTilemap_main_head.c").writeText(mainC.take(8000))

        // setup_current_level helper must NOT appear in main.c when the gate is off.
        // The string match is intentionally narrow (no parentheses) so a stale comment
        // that mentions the symbol would also fail — the contract is "no reference, no
        // emission" when the predicate returns false.
        assertFalse(
            mainC.contains("setup_current_level"),
            "setup_current_level must NOT appear in main.c when solidThreshold is unset AND " +
                "no zone declares a platformerPhysicsOverride[solidThreshold] (gate-off " +
                "byte-identical regression invariant). main.c head:\n${mainC.take(2000)}",
        )

        // Companion negative: the main()-loop level-switch guard (Plan 12-17 Task 2) must
        // also be absent. Locking both halves of the gate together documents the lockstep
        // emission contract (anchor 5 substrate cannot exist without anchor 5's helper).
        assertFalse(
            mainC.contains("_next_level != _current_level"),
            "main()-loop level-switch guard must NOT appear in main.c when tilemap-collision " +
                "is off (`_next_level != _current_level` substring is the guard's distinctive " +
                "shape per Plan 12-17 SUMMARY §Self-Check). main.c head:\n${mainC.take(2000)}",
        )

        // Sanity — title_frame still emits navigate_to_scene even with the gate OFF. Anchor 1's
        // title→gameplay transition is orthogonal to the platformer substrate; locking this
        // here proves the gate does not accidentally turn off the navigation path.
        val titleFrameBody = extractFunctionBody(bank1C, "void title_frame")
        assertTrue(
            titleFrameBody.contains("navigate_to_scene"),
            "title_frame must still emit navigate_to_scene when the platformer gate is OFF " +
                "(anchor 1 is platformer-independent). title_frame body:\n" +
                titleFrameBody.take(4000),
        )
    }
}
