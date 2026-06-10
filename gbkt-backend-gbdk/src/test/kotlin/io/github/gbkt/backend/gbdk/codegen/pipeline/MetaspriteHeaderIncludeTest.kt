/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/*
 * DEF-10.1-09-A regression guard — `game.h` must include `<gbdk/metasprites.h>` whenever
 * the game declares ≥1 metasprite, because `buildHeaderFile` emits
 * `extern const metasprite_t* const sprite_<id>_frames[];` rawSections (WR-02 / Plan 07)
 * that reference the `metasprite_t` typedef. Without the include, SDCC reports
 * `game.h: error 1: Syntax error, declaration ignored at 'metasprite_t'`.
 *
 * Surfaced by Plan 10.1-09's ROM-build smoke test; closed by wave-4 inline fixup that
 * extends the `cgbHeaderInclude` pattern. Mirrors the bank1.c per-bank include pattern
 * from Plan 06 (CR-02 / SEED-009) and is paired with the WR02MetaspriteExternTest
 * extern-emission regression guard.
 */
class MetaspriteHeaderIncludeTest {

    private fun metasprite(id: String): MetaspriteIR =
        MetaspriteIR(
            id = id,
            frames =
                listOf(
                    MetaspriteFrame(tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 0)))
                ),
        )

    private fun buildGame(metasprites: List<MetaspriteIR>): GameIR =
        GameIR(
            name = "MetaspriteHeaderIncludeTestGame",
            scenes = listOf(SceneIR(id = "title")),
            metasprites = metasprites,
            startScene = "title",
        )

    @Test
    fun game_h_includes_gbdk_metasprites_h_when_game_has_metasprites() {
        val game = buildGame(metasprites = listOf(metasprite("elephant")))

        val output = GBDKPipeline().generate(game)
        val gameH = output.files["game.h"]

        assertNotNull(
            gameH,
            "game.h was not generated. Inspect output.files keys: ${output.files.keys}",
        )

        assertTrue(
            gameH.contains("#include <gbdk/metasprites.h>"),
            "game.h must include <gbdk/metasprites.h> when metasprites are present — " +
                "SDCC fails to parse `extern const metasprite_t* const sprite_<id>_frames[];` " +
                "without the type definition (DEF-10.1-09-A). game.h head:\n" +
                gameH.take(800),
        )
    }

    @Test
    fun game_h_omits_gbdk_metasprites_h_when_no_metasprites_declared() {
        val game = buildGame(metasprites = emptyList())

        val output = GBDKPipeline().generate(game)
        val gameH = output.files["game.h"]

        assertNotNull(
            gameH,
            "game.h was not generated. Inspect output.files keys: ${output.files.keys}",
        )

        assertFalse(
            gameH.contains("#include <gbdk/metasprites.h>"),
            "game.h must NOT include <gbdk/metasprites.h> when no metasprites are declared " +
                "(non-metasprite games should not pay the include cost). game.h head:\n" +
                gameH.take(800),
        )
    }
}
