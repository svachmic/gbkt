/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.dsl.asset
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.genre.sport.dsl.racing
import io.github.gbkt.genre.sport.dsl.vehicle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Plan 07.4-22 — JVM-tier audit + lock for cross-bank zone tilemap access in race_enter.
 *
 * **Hypothesis (UAT secondary issue 2):** `_zone_track1_tiles` is allocated to a non-HOME ROM bank
 * (`#pragma bank 2` in `zone_bank2.c`) but `set_bkg_tiles(..., _zone_track1_tiles)` is called from
 * `race_enter` in `bank1.c` without an explicit `SWITCH_ROM(2)` first. GBDK's `set_bkg_tiles` reads
 * the tile data via `__memcpy` against the raw 16-bit pointer; if the MBC5 ROM bank register is not
 * pointing at bank 2 when the read happens, the memcpy reads from whatever bank IS mapped at
 * 0x4000–0x7FFF — typically bank 1 — and gets garbage tile data. The result is a blank or corrupted
 * background tilemap.
 *
 * **Audit-first principle (feedback_quality_over_shortcuts.md):** this test does NOT presuppose the
 * bug exists. It inspects the ground-truth shape of the generated C and resolves to one of two
 * locked verdicts:
 *
 * - **PASSED at HEAD** → the zone tilemap data lives in the SAME bank as race_enter (e.g., HOME or
 *   bank 1), so no SWITCH_ROM is needed. Test ALSO asserts no SWITCH_ROM is emitted (locks against
 *   an over-eager fix that adds a stale switch).
 *
 * - **FAILED at HEAD** → the zone tilemap data lives in a different bank from race_enter and no
 *   SWITCH_ROM precedes the set_bkg_tiles call. Plan 07.4-22 Task 2 must add the guard. After Task
 *   2, this test goes GREEN.
 *
 * **Companion lock:** `set_bkg_data_for_HOME_resident_tileset_does_not_emit_SWITCH_ROM` — the
 * racing tileset (`_racing_track1_tileset`) is a 48-byte const that lives in HOME (per Plan 07.4-11
 * SportVisitor.buildBuiltinTrackTilesetVarDecl). It must NOT be guarded by a SWITCH_ROM — that
 * would be dead code. This locks against the over-eager-fix scenario where a future plan adds
 * SWITCH_ROM to every set_bkg_* call indiscriminately.
 *
 * **Rebase coordination with Plan 07.4-20:** the cross-bank guard, IF added, is inserted INSIDE
 * Plan 20's per-scene `try { ... } finally { setSceneContext(null, false) }` block in
 * `buildSceneFile` (option P-1 from the plan's <rebase_note>). The guard is a pure data
 * transformation on the genreEnterOps list — no scene-context side effects, no nested try/finally,
 * no risk of leaking scene context. This test does NOT verify Plan 20's try/finally invariant
 * directly; that is locked by `ScreenClearSceneAwareTest` and `PrintOpSceneAwareTest`. This test
 * only verifies the cross-bank emission shape.
 */
class CrossBankZoneTilemapAccessTest {

    /**
     * Audit + lock — given the racer-pattern IR, determine whether the zone tilemap data is
     * cross-bank from race_enter, and verify the cross-bank guard emission contract holds.
     *
     * **Plan 07.4-22 original contract:** zone allocated to bank 2, race_enter in bank 1 →
     * `SWITCH_ROM(2)` prepended before `set_bkg_tiles` call.
     *
     * **Plan 07.4-30 revision:** Inline SWITCH_ROM inside a BANKED function (bank1.c) is unsafe.
     * After `SWITCH_ROM(N)` executes at 0x4000+, the MBC remaps 0x4000-0x7FFF to bank N. Subsequent
     * instruction fetches (still at 0x4000+) come from bank N tilemap data, not bank 1 code →
     * garbage execution → `EmulatorFrameHangException`.
     *
     * The fix (Plan 07.4-30 D-N-SWITCHROM-RESTORE): replace the inline `SWITCH_ROM(N);
     * set_bkg_tiles(...)` pair with a HOME-bank helper call `_bkg_tiles_load_banked(N, ...)`. The
     * helper lives at 0x0000-0x3FFF (never remapped by MBC) and safely executes SWITCH_ROM +
     * set_bkg_tiles + SWITCH_ROM(1) restore.
     *
     * Verdict at HEAD (post Plan 07.4-30): zone allocated to bank 2, race_enter in bank 1 →
     * `_bkg_tiles_load_banked(2u, ...)` in race_enter body; no inline SWITCH_ROM in race_enter.
     */
    @Test
    fun `race_enter set_bkg_tiles for banked zone is preceded by SWITCH_ROM`() {
        val output = GBDKPipeline().generate(buildRacerLikeIR())
        val bank1 =
            output.files["bank1.c"]
                ?: fail("Plan 07.4-22: bank1.c not generated. Files: ${output.files.keys}")

        // Resolve which bank the zone tile array lives in by scanning all generated files for
        // the canonical `_zone_track1_tiles` declaration and reading the `#pragma bank N` line.
        val zoneBank = resolveZoneBank(output.files, zoneId = "track1")

        // race_enter lives in bank1.c (per buildSceneFile fileBank=1 default for racer-like IR).
        val raceEnterBody = extractFunctionBody(bank1, "race_enter")

        if (zoneBank > 1) {
            // CROSS-BANK CASE (Plan 07.4-30) — zone is in bank N > 1, race_enter is in bank 1.
            // The guard must replace set_bkg_tiles with a HOME-bank helper call:
            //   _bkg_tiles_load_banked(N, x, y, w, h, _zone_track1_tiles)
            // No inline SWITCH_ROM should appear in race_enter (unsafe in BANKED code).
            val helperPattern = Regex("_bkg_tiles_load_banked\\s*\\(\\s*${zoneBank}[uU]?\\s*,")
            assertTrue(
                helperPattern.containsMatchIn(raceEnterBody),
                "Plan 07.4-30: zone _zone_track1_tiles is in bank $zoneBank but race_enter " +
                    "did NOT emit `_bkg_tiles_load_banked($zoneBank, ...)`. The cross-bank guard " +
                    "must route the set_bkg_tiles call through the HOME-bank helper to avoid " +
                    "SWITCH_ROM corruption of the banked instruction stream. " +
                    "See Plan 07.4-30 D-N-SWITCHROM-RESTORE.\n\nrace_enter body:\n$raceEnterBody",
            )
            // No inline SWITCH_ROM should remain in race_enter body after the guard replaces it.
            assertFalse(
                Regex("SWITCH_ROM\\s*\\(").containsMatchIn(raceEnterBody),
                "Plan 07.4-30: race_enter must NOT contain inline SWITCH_ROM after the HOME-bank " +
                    "helper fix. Inline SWITCH_ROM inside BANKED code is unsafe — the CPU would " +
                    "fetch subsequent instructions from the wrong bank.\n\n" +
                    "race_enter body:\n$raceEnterBody",
            )
        } else {
            // SAME-BANK CASE — zone is in bank 1 (or HOME). No SWITCH_ROM needed.
            // The original set_bkg_tiles call should remain, no helper substitution.
            val setBkgTilesIdx = raceEnterBody.indexOf("set_bkg_tiles(")
            if (setBkgTilesIdx < 0) {
                fail(
                    "Plan 07.4-22: set_bkg_tiles not found in race_enter for same-bank zone — " +
                        "the SportVisitor genre enterOps splice did not fire.\n\n" +
                        "race_enter body:\n$raceEnterBody"
                )
            }
            assertFalse(
                Regex("SWITCH_ROM\\s*\\(")
                    .containsMatchIn(raceEnterBody.substring(0, setBkgTilesIdx)),
                "Plan 07.4-22: zone tile array is in the same bank as race_enter (bank " +
                    "$zoneBank); SWITCH_ROM should NOT be emitted before set_bkg_tiles.\n\n" +
                    "race_enter body:\n$raceEnterBody",
            )
        }
    }

    /**
     * Back-compat lock — the racing tileset (`_racing_track1_tileset`) is a 48-byte const emitted
     * by `SportVisitor.buildBuiltinTrackTilesetVarDecl` and lives in HOME (auto-routed by the
     * pipeline because it has no `#pragma bank` directive). `set_bkg_data` reading from a
     * HOME-resident pointer needs NO SWITCH_ROM. This test locks against an over-eager fix that
     * wraps every `set_bkg_*` call in a SWITCH_ROM regardless of the pointer's bank.
     */
    @Test
    fun `set_bkg_data for HOME resident tileset does not emit SWITCH_ROM`() {
        val output = GBDKPipeline().generate(buildRacerLikeIR())
        val bank1 =
            output.files["bank1.c"]
                ?: fail("Plan 07.4-22: bank1.c not generated. Files: ${output.files.keys}")

        val raceEnterBody = extractFunctionBody(bank1, "race_enter")
        val setBkgDataIdx = raceEnterBody.indexOf("set_bkg_data(")
        if (setBkgDataIdx < 0) {
            fail(
                "Plan 07.4-22: set_bkg_data not found in race_enter — the SportVisitor genre " +
                    "enterOps splice did not fire. Investigate.\n\n" +
                    "race_enter body:\n$raceEnterBody"
            )
        }
        val precedingText = raceEnterBody.substring(0, setBkgDataIdx)
        // Tileset is HOME-resident — emitting SWITCH_ROM(N) before this read would either be
        // a no-op (N=1, which is a waste) or actively wrong (any other N). Either way, dead.
        assertFalse(
            Regex("SWITCH_ROM\\s*\\(").containsMatchIn(precedingText),
            "Plan 07.4-22 back-compat: _racing_track1_tileset is HOME-resident (per " +
                "SportVisitor.buildBuiltinTrackTilesetVarDecl). set_bkg_data must NOT be " +
                "preceded by SWITCH_ROM. A guard here is dead code; an over-eager fix that " +
                "adds SWITCH_ROM to every set_bkg_* call would trip this lock.\n\n" +
                "race_enter body:\n$raceEnterBody",
        )
    }

    /**
     * Build the racer-pattern GameIR exactly as `ScreenClearSceneAwareTest` does — same actors,
     * same vehicles, same `racing { }` block, same race scene. Reusing the fixture shape keeps the
     * JVM-tier evidence aligned with what Plan 19/20 already lock.
     */
    private fun buildRacerLikeIR() =
        game("CrossBankZoneFixture") {
                config {
                    cartridge = Cartridge.ROM_ONLY
                    romBanks = 2
                }
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
                val raceScene = scene("race") {
                    enter { print("LAP:", position = PositionDef(1, 1)) }
                    frame { /* empty */ }
                }
                start = raceScene
            }
            .build()

    /**
     * Resolve which ROM bank the `_zone_<id>_tiles` const array is **declared** in.
     *
     * The token `_zone_<id>_tiles` appears in MULTIPLE generated files: the declaration site
     * (`zone_bankN.c`) and every reference site (`bank1.c` for `set_bkg_tiles`, AI/collision
     * helpers in `main.c`, the extern in `game.h`). We must match the DECLARATION specifically —
     * the canonical form `const UINT8 _zone_<id>_tiles[<size>] = { ... }` — to determine which bank
     * the data lives in.
     *
     * Returns 0 if declared in a file with no `#pragma bank` directive (HOME bank) or N if declared
     * in a file with `#pragma bank N`.
     *
     * Throws if the declaration is not found anywhere — that means the SportVisitor varDecl
     * pipeline regressed and is a bug independent of cross-bank access.
     */
    private fun resolveZoneBank(files: Map<String, String>, zoneId: String): Int {
        // Match the canonical declaration shape only — the `const UINT8 _zone_<id>_tiles[N] = {`
        // pattern with brace initializer. The `= {` suffix excludes the extern declaration in
        // `game.h` (`extern const UINT8 _zone_<id>_tiles[N];`) and the call-site references in
        // `bank1.c` / `main.c`.
        val declarationPattern =
            Regex(
                "(?<!extern\\s)const\\s+UINT8\\s+_zone_${Regex.escape(zoneId)}" +
                    "_tiles\\s*\\[\\s*\\d+\\s*\\]\\s*=\\s*\\{"
            )
        for ((filename, content) in files) {
            if (!declarationPattern.containsMatchIn(content)) continue
            // Parse `#pragma bank N` from the file header (typically line 2). If absent, the
            // file is HOME (bank 0).
            val pragmaMatch = Regex("#pragma\\s+bank\\s+(\\d+)").find(content)
            val bank = pragmaMatch?.groupValues?.get(1)?.toInt() ?: 0
            // Sanity check: the filename pattern `zone_bankN.c` (when present) must agree with
            // the `#pragma bank` line. Discrepancy here would indicate a codegen bug.
            val filenameMatch = Regex("zone_bank(\\d+)\\.c").matchEntire(filename)
            if (filenameMatch != null) {
                val filenameBank = filenameMatch.groupValues[1].toInt()
                require(filenameBank == bank) {
                    "Plan 07.4-22: filename '$filename' implies bank $filenameBank but " +
                        "#pragma bank says $bank — codegen inconsistency."
                }
            }
            return bank
        }
        fail(
            "Plan 07.4-22: declaration of _zone_${zoneId}_tiles not found in any generated " +
                "file. Files searched: ${files.keys}. SportVisitor varDecl pipeline may have " +
                "regressed."
        )
    }

    /**
     * Walks the C source from `void <funcName>(void)` to the matching closing brace, tracking
     * nested braces. Same shape as `ScreenClearSceneAwareTest.extractFunctionBody`.
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
