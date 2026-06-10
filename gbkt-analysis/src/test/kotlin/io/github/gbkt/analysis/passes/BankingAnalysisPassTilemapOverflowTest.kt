/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.FakeProfile
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.ZoneIR
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Phase 12.2 Plan 08 — REQ-5 coverage for [BankingAnalysisPass]'s tilemap-bank overflow guard.
 *
 * Verifies the cumulative tilemap-bank overflow guard via real PNG fixtures: a. Overflow fires when
 * cumulative tilemap bytes exceed the 14336-byte threshold. b. The Phase 12 five-zone scenario
 * (~6480 bytes) passes the guard. c. When `PassContext.assetRoot` is null, the guard is skipped
 * entirely — pre-12.2 unit-test callers (which never wire an asset root) keep working unchanged.
 *
 * Pattern source: `BankingAnalysisPassTest.kt` + `12.2-PATTERNS.md`
 * "BankingAnalysisPassTilemapOverflowTest.kt" template.
 */
class BankingAnalysisPassTilemapOverflowTest {

    private lateinit var tempDir: File

    private val pass = BankingAnalysisPass()

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("gbkt-tilemap-overflow-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.walkBottomUp().forEach { it.delete() }
    }

    /**
     * Writes a PNG of the given pixel dimensions and returns the relative path inside [tempDir].
     */
    private fun writePng(relPath: String, widthPx: Int, heightPx: Int): String {
        val file = File(tempDir, relPath)
        file.parentFile.mkdirs()
        val img = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(img, "PNG", file)
        return relPath
    }

    private fun makeContext(game: GameIR, assetRoot: File? = null): PassContext =
        PassContext(
            game = game,
            profile = FakeProfile,
            config = AnalysisConfig(maxBanks = 32),
            assetRoot = assetRoot,
        )

    @Test
    fun `overflow guard fires when cumulative tilemap bytes exceed 14336`() {
        // Two distinct PNGs reused across 6 zones (the overflow check sums per zone).
        //   big.png   = 480x512 px -> (60 * 64) = 3840 bytes
        //   mid.png   = 480x256 px -> (60 * 32) = 1920 bytes
        // 2 zones using big.png + 4 zones using mid.png = 2*3840 + 4*1920 = 7680 + 7680 = 15360
        // bytes
        // 15360 > 14336 threshold => overflow fires.
        val bigRel = writePng("graphics/big.png", widthPx = 480, heightPx = 512)
        val midRel = writePng("graphics/mid.png", widthPx = 480, heightPx = 256)

        val zones =
            listOf(
                ZoneIR(id = "bigA", name = "bigA", tilesetPath = bigRel),
                ZoneIR(id = "bigB", name = "bigB", tilesetPath = bigRel),
                ZoneIR(id = "midA", name = "midA", tilesetPath = midRel),
                ZoneIR(id = "midB", name = "midB", tilesetPath = midRel),
                ZoneIR(id = "midC", name = "midC", tilesetPath = midRel),
                ZoneIR(id = "midD", name = "midD", tilesetPath = midRel),
            )
        val game = GameIR(name = "OverflowFixture", zones = zones)

        val result = pass.run(makeContext(game, assetRoot = tempDir))

        assertIs<PassResult.Failed>(result, "Expected overflow guard to fail the analysis pass")
        val message = result.diagnostics.first().message
        assertTrue(
            "14336" in message || "overflow" in message.lowercase(),
            "Expected overflow message to reference threshold or 'overflow'; got: $message",
        )
        assertTrue(
            "bigA" in message || "midA" in message,
            "Expected overflow message to list at least one zone id; got: $message",
        )
        assertTrue(
            "15360" in message,
            "Expected overflow message to include the cumulative total 15360 bytes; got: $message",
        )
    }

    @Test
    fun `phase 12 five-zone scenario (under 14336 bytes) passes overflow guard`() {
        // Three world zones at 480x256 px = 60 * 32 = 1920 bytes each (total 5760)
        // Two screen zones at 160x144 px = 20 * 18 = 360 bytes each (total 720)
        // Cumulative = 6480 bytes — well under 14336 threshold.
        val worldRel = writePng("graphics/world.png", widthPx = 480, heightPx = 256)
        val screenRel = writePng("graphics/screen.png", widthPx = 160, heightPx = 144)

        val zones =
            listOf(
                ZoneIR(id = "world1Area1Zone", name = "w1a1", tilesetPath = worldRel),
                ZoneIR(id = "world1Area2Zone", name = "w1a2", tilesetPath = worldRel),
                ZoneIR(id = "world2Area1Zone", name = "w2a1", tilesetPath = worldRel),
                ZoneIR(id = "titleZone", name = "title", tilesetPath = screenRel),
                ZoneIR(id = "nextLevelZone", name = "nextLevel", tilesetPath = screenRel),
            )
        val game = GameIR(name = "Phase12Fixture", zones = zones)

        val result = pass.run(makeContext(game, assetRoot = tempDir))

        assertIs<PassResult.Success>(
            result,
            "Phase 12 fixture (~6480 bytes) must pass the overflow guard",
        )
    }

    @Test
    fun `null assetRoot skips tilemap overflow guard`() {
        // A single zone pointing at a non-existent PNG. With assetRoot=null the guard is skipped
        // entirely — analysis must still succeed. This is the path used by every pre-12.2 JVM
        // unit test that never wires an asset root.
        val zones = listOf(ZoneIR(id = "z1", name = "z1", tilesetPath = "graphics/nonexistent.png"))
        val game = GameIR(name = "NullAssetRootFixture", zones = zones)

        val result = pass.run(makeContext(game, assetRoot = null))

        assertIs<PassResult.Success>(
            result,
            "Overflow guard must be skipped when assetRoot is null, even for missing zones",
        )
    }
}
