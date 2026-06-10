/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File contains multiple test fixtures, not a single class

package io.github.gbkt.analysis

import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.constraints.AudioSpec
import io.github.gbkt.core.constraints.MemorySpec
import io.github.gbkt.core.constraints.ScreenSpec
import io.github.gbkt.core.constraints.SpriteSize
import io.github.gbkt.core.constraints.SpriteSpec
import io.github.gbkt.core.constraints.TargetProfile
import io.github.gbkt.core.ir.GameIR

/** Minimal TargetProfile for tests — no dependency on gbkt-backend-gbdk. */
internal object FakeProfile : TargetProfile {
    override val name = "Fake Test Profile"
    override val id = "fake"
    override val screen =
        ScreenSpec(
            width = 160,
            height = 144,
            bitsPerPixel = 2,
            tileSize = 8,
            backgroundLayers = 1,
            supportsPalettes = false,
            paletteCount = 0,
            colorsPerPalette = 4,
        )
    override val sprites =
        SpriteSpec(
            maxSprites = 40,
            maxPerScanline = 10,
            sizes = listOf(SpriteSize(8, 8)),
            supportsPalettes = false,
            paletteCount = 2,
            supportsFlipping = true,
            supportsPriority = true,
        )
    override val memory =
        MemorySpec(
            workRam = 8192,
            videoRam = 8192,
            oamSize = 160,
            hiRam = 127,
            romBankSize = 16384,
            ramBankSize = 8192,
        )
    override val audio = AudioSpec(channels = emptyList(), supportsPCM = false, sampleRate = 0)
    override val supportsBanking = true
    override val maxRomSize = 8 * 1024 * 1024
    override val defaultRomBanks = 2
    override val maxRamBanks = 4
}

/** Creates a baseline [PassContext] with the fake profile and minimal config. */
internal fun baseContext(): PassContext =
    PassContext(
        game = GameIR(name = "Test Game"),
        profile = FakeProfile,
        config = AnalysisConfig(maxBanks = 2),
    )
