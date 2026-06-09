/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.dsl.asset
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.genre.sport.dsl.racing
import io.github.gbkt.genre.sport.dsl.vehicle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RED tests for Plan 07.4-19 — locks the architectural rule that the DSL `clear()` keyword, when
 * invoked inside a scene that has a background tilemap (either via `tileset(...)` on the scene OR
 * via a genre plugin's `enterOps` splice such as the racing track painter), MUST NOT lower to a
 * bare `cls()` in the generated C — that would wipe the BG tilemap painted by the genre splice (or
 * by the scene's own tileset binding).
 *
 * **Why this exists:** Plan 14's round-2 verification accepted variable-state evidence in lieu of
 * visual evidence, so the bug — DSL `clear()` emits `RawOp("cls();")` (ScriptBuilder.kt:432) which
 * lowers verbatim and wipes the BG painted by [SportVisitor]'s race-scene `enterOps` — went
 * undetected for 5 plans. These RED tests turn the bug into a JVM-tier failure that cannot be
 * hidden by methodology drift.
 *
 * **Critical DSL routing finding:** at HEAD, `ScriptBuilder.clear()` emits `RawOp("cls();")`, NOT
 * `ScreenClear` IR. So a test that built the IR with a hand-constructed `ScreenClear` node would
 * NOT exercise the racer's actual code path. These tests drive the IR via the gbkt-lang DSL
 * `clear()` call so they FAIL at HEAD against the ACTUAL bug. Plan 20 must fix BOTH the DSL routing
 * (`ScriptBuilder.clear()` → emit `ScreenClear`) AND make `visitScreenClear` scene-aware.
 *
 * **Forcing function:** Plan 20 lands GREEN once the DSL `clear()` keyword no longer emits a raw
 * `cls();` for scenes with a BG tilemap.
 */
class ScreenClearSceneAwareTest {

    /**
     * Test #1 — locks the DSL `clear()` invariant for scenes that explicitly bind a tileset via
     * `tileset("path/to.png")` on the scene block. The `tilesetRef != null` branch is the most
     * direct signal that the scene paints a BG tilemap.
     *
     * **Expected at HEAD:** FAIL. `clear()` lowers to `RawOp("cls();")` → `cls()` in the generated
     * `zone_enter` body, which would wipe the BG tilemap painted by the scene's tileset binding.
     *
     * **Expected after Plan 20:** PASS. The DSL `clear()` will route through `ScreenClear` IR and
     * the scene-aware visitor will skip the `cls()` emission for BG-tilemap scenes.
     */
    @Test
    fun `dsl clear in scene with tilesetRef does not emit bare cls`() {
        val gameIR =
            game("ScreenClearTilesetFixture") {
                    config {
                        cartridge = Cartridge.ROM_ONLY
                        romBanks = 2
                    }
                    val zoneScene =
                        scene("zone") {
                            tileset("tilesets/dungeon.png")
                            enter { clear() }
                            frame { /* no-op */ }
                        }
                    start = zoneScene
                }
                .build()

        val output = GBDKPipeline().generate(gameIR)
        val bank1 =
            output.files["bank1.c"] ?: error("bank1.c not generated; got: ${output.files.keys}")
        val zoneEnterBody = extractFunctionBody(bank1, "zone_enter")

        assertFalse(
            zoneEnterBody.contains("cls()"),
            "Plan 07.4-19/20 RED: scene 'zone' has a BG tilemap (via tileset(\"...\"))." +
                " The DSL clear() keyword must NOT result in bare cls() — that wipes the BG" +
                " tilemap. See CLAUDE.md 'Window-Layer UI' and Plan 07.4-19/20.\n\n" +
                "zone_enter body:\n$zoneEnterBody",
        )
    }

    /**
     * Test #2 — locks the DSL `clear()` invariant for the EXACT racer pattern: a `race` scene with
     * no `tilesetRef` of its own, where the racing genre's `SportVisitor` splices `enterOps`
     * (set_bkg_data + set_bkg_tiles + camera target bind) into `race_enter`.
     *
     * This test mirrors the racer code path EXACTLY (vehicle delegates, racing block with
     * waypoints) so the SportVisitor's enterOps splice fires through the real ServiceLoader
     * dispatch. If a future change makes `clear()` scene-aware but only honors `tilesetRef !=
     * null`, this test will catch the missing genre-splice branch.
     *
     * **Expected at HEAD:** FAIL. `race_enter` contains `cls()` IMMEDIATELY after the genre's
     * `set_bkg_data(0, 3, _racing_track1_tileset)` and `set_bkg_tiles(0, 0, ...,
     * _zone_track1_tiles)` paint — wiping the track tilemap one statement after it was painted.
     *
     * **Expected after Plan 20:** PASS.
     */
    @Test
    fun `dsl clear in race scene with genre splice does not emit bare cls`() {
        val gameIR =
            game("RacerLikeFixture") {
                    config { cartridge(Cartridge.ROM_ONLY) }
                    val car by actor {
                        position(80, 80)
                        sprite(asset("sprites/car.png")) {
                            size(8, 16)
                            hitbox(0, 0, 8, 16)
                        }
                    }
                    val rival by actor {
                        position(80, 96)
                        sprite(asset("sprites/car.png")) {
                            size(8, 16)
                            hitbox(0, 0, 8, 16)
                        }
                    }
                    val carPlayer by vehicle {
                        actor(car)
                        stats {
                            speed(200)
                            acceleration(160)
                            handling(180)
                        }
                    }
                    val carAi by vehicle {
                        actor(rival)
                        stats {
                            speed(180)
                            acceleration(150)
                            handling(200)
                        }
                    }
                    @Suppress("UNUSED_VARIABLE")
                    val track1 by racing {
                        laps(3)
                        player(carPlayer)
                        aiOpponents(carAi)
                        track {
                            waypoint(x = 5, y = 5, checkpoint = true)
                            waypoint(x = 15, y = 5)
                            waypoint(x = 15, y = 15, checkpoint = true)
                            waypoint(x = 5, y = 15)
                        }
                    }
                    val raceScene =
                        scene("race") {
                            enter {
                                clear()
                                print("LAP:", position = PositionDef(1, 1))
                            }
                            frame { /* empty */ }
                        }
                    start = raceScene
                }
                .build()

        val output = GBDKPipeline().generate(gameIR)
        val bank1 =
            output.files["bank1.c"] ?: error("bank1.c not generated; got: ${output.files.keys}")
        val raceEnterBody = extractFunctionBody(bank1, "race_enter")

        assertFalse(
            raceEnterBody.contains("cls()"),
            "Plan 07.4-19/20 RED: scene 'race' has a BG tilemap (via genre racing splice that" +
                " paints _zone_track1_tiles into the BG layer). The DSL clear() keyword must NOT" +
                " result in bare cls() — that wipes the BG tilemap one statement after it was" +
                " painted. See CLAUDE.md 'Window-Layer UI' and Plan 07.4-19/20.\n\n" +
                "race_enter body:\n$raceEnterBody",
        )
    }

    /**
     * Test #3 — locks the back-compat branch: scenes WITHOUT a BG tilemap (no `tilesetRef`, no
     * genre `enterOps` splice) MUST still emit `cls()` for `clear()`. This is the title-screen /
     * results-screen / gameover-screen case where the user wants the BG cleared and HUD-style text
     * printed.
     *
     * **Expected at HEAD:** PASS. DSL `clear()` emits `cls()` everywhere; the back-compat case
     * happens to be correct.
     *
     * **Expected after Plan 20:** STILL PASS. Plan 20's scene-aware lowering keeps `cls()` for
     * non-BG scenes — back-compat is preserved.
     */
    @Test
    fun `dsl clear in scene without BG tilemap emits cls for back-compat`() {
        val gameIR =
            game("ScreenClearTitleFixture") {
                    config { cartridge(Cartridge.ROM_ONLY) }
                    val titleScene =
                        scene("title") {
                            enter { clear() }
                            frame { /* no-op */ }
                        }
                    start = titleScene
                }
                .build()

        val output = GBDKPipeline().generate(gameIR)
        val bank1 =
            output.files["bank1.c"] ?: error("bank1.c not generated; got: ${output.files.keys}")
        val titleEnterBody = extractFunctionBody(bank1, "title_enter")

        assertTrue(
            titleEnterBody.contains("cls()"),
            "Plan 07.4-19/20 back-compat: scene 'title' has NO BG tilemap (no tilesetRef, no" +
                " genre enterOps splice). The DSL clear() keyword MUST still result in cls()" +
                " for back-compat (title/results/gameover scenes). Plan 20's scene-aware" +
                " lowering MUST preserve this branch.\n\n" +
                "title_enter body:\n$titleEnterBody",
        )
    }

    /**
     * Walks the C source from `void <funcName>(void)` to the matching closing brace, tracking
     * nested braces. Used to scope text-grep assertions to a single function body and avoid false
     * matches in sibling functions (other scenes' valid `cls()` calls would mask the bug if we
     * grepped at file scope).
     */
    private fun extractFunctionBody(cSource: String, funcName: String): String {
        val signatureIdx = cSource.indexOf("void $funcName(void)")
        require(signatureIdx >= 0) {
            "Function 'void $funcName(void)' not found in C source.\n\n" +
                "First 1500 chars of C source:\n${cSource.take(1500)}"
        }
        val openBrace = cSource.indexOf('{', signatureIdx)
        require(openBrace >= 0) { "Open brace for $funcName not found" }
        var depth = 1
        var i = openBrace + 1
        while (i < cSource.length && depth > 0) {
            when (cSource[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return cSource.substring(openBrace + 1, i - 1)
    }
}
