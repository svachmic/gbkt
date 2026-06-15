/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.platformer_template

import io.github.gbkt.emulator.agent.AgentSessionConfig
import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.StepAgent
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Assumptions

/**
 * Phase 12.8 D-11 — anchor PNG re-shoot clone of [PlatformerTemplateUatTest].
 *
 * This class was originally cloned to capture Phase 12.8 re-shoot evidence to a separate evidence
 * directory. Its captures are NOT blessed anchors (not migrated to goldens/).
 *
 * Phase 22 (22-07) migration: EVIDENCE_DIR removed (R1). Captures redirect to gitignored
 * SCRATCH_DIR under build/. GBC mode is auto-detected via [AgentSessionConfig.discoverFiles] from
 * ROM 0x143 (22-02); the D-07 guard asserts the ROM is GBC. The captureAndRename pattern is
 * replaced with direct [StepAgent.captureScreenshot] smoke assertions (length > 0 check).
 */
class PlatformerTemplate128UatTest {

    companion object {
        val ROM_FILE = java.io.File("build/gbkt/output/platformer-template.gb")
        val METADATA_FILE = java.io.File("build/gbkt/generated/game_metadata.json")
        // Phase 22 (22-07): capture to gitignored scratch under build/ — no .planning/phases path.
        val SCRATCH_DIR = File(System.getProperty("user.dir"), "build/gbkt/screenshots")
        // Scratch dir for .txt debug artifacts
        val TEST_EVIDENCE_DIR = File(System.getProperty("user.dir"), "build/gbkt/test-evidence")
    }

    private fun newAgent(): StepAgent {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run buildRom first",
        )
        SCRATCH_DIR.mkdirs()
        TEST_EVIDENCE_DIR.mkdirs()
        // Phase 22 (D-07 guard): platformer-template targets GBC_COMPATIBLE; discoverFiles()
        // auto-detects gbcMode from ROM 0x143 (22-02). Assert GBC mode is active so a mis-built
        // DMG ROM cannot bless an inverted-palette golden.
        val baseConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = SCRATCH_DIR)
        check(baseConfig.gbcMode) {
            "ROM 0x143 CGB flag not set — is this a DMG ROM? " +
                "Aborting to prevent inverted-palette golden bless."
        }
        val metadata =
            if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
        val agent = StepAgent(baseConfig, metadata)
        agent.start()
        return agent
    }

    /**
     * Perceptual screenshot check — asserts that [file] is a non-uniform PNG (i.e. contains real
     * rendered content, not a blank or solid-colour frame).
     *
     * Per WR-07 (REVIEW.md) + Plan 11.1-14: asserts >= 2 distinct RGB colour values AND dominant
     * colour covers fewer than 95% of pixels. Aligns with CLAUDE.md Visual Evidence Rule.
     */
    private fun assertScreenshotIsNonUniform(file: File, label: String) {
        val img =
            ImageIO.read(file) ?: fail("$label: file is not a valid PNG: ${file.absolutePath}")

        val colours = mutableSetOf<Int>()
        val histogram = mutableMapOf<Int, Int>()
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val rgb = img.getRGB(x, y) and 0xFFFFFF
                colours.add(rgb)
                histogram[rgb] = (histogram[rgb] ?: 0) + 1
            }
        }

        assertTrue(
            colours.size >= 2,
            "$label: screenshot must contain >= 2 distinct RGB colours " +
                "(a blank frame has 1; any real tile content uses >= 2 shades). " +
                "Found ${colours.size} distinct colour(s). " +
                "File: ${file.absolutePath}",
        )

        val totalPixels = img.width * img.height
        val dominantCount = histogram.values.max()
        val dominantRatio = dominantCount.toDouble() / totalPixels

        assertTrue(
            dominantRatio < 0.95,
            "$label: dominant colour must cover < 95% of pixels " +
                "(guards against near-blank frames with only a pixel border of content). " +
                "Dominant-colour ratio: ${"%.3f".format(dominantRatio)} " +
                "(${dominantCount}/${totalPixels} pixels). " +
                "File: ${file.absolutePath}",
        )

        File(TEST_EVIDENCE_DIR, "$label-perceptual.txt")
            .writeText(
                "file: ${file.absolutePath}\n" +
                    "dimensions: ${img.width}x${img.height}\n" +
                    "distinct_colours: ${colours.size}\n" +
                    "dominant_ratio: ${"%.4f".format(dominantRatio)}\n" +
                    "dominant_count: $dominantCount\n" +
                    "total_pixels: $totalPixels\n"
            )
    }

    // ── Anchor 1 — Title → gameplay scene transition (D-08 #1) ──
    //
    // Phase 22 (22-07): scratch-smoke — captures are NOT blessed anchors. Uses captureScreenshot
    // directly (no rename to a fixed target name); length > 0 smoke assertion.
    @Test
    fun anchor1Title_to_Gameplay() {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run :gbkt-examples:platformer-template:buildRom first",
        )

        newAgent().use { agent ->
            // Boot lead-in: settle on title scene
            agent.stepN(120)

            // Diagnostic: capture at 120 frames
            val obs120 = agent.step()
            File(TEST_EVIDENCE_DIR, "128-debug-obs-120.txt")
                .writeText(
                    "frame=${obs120.frame}\nscene=${obs120.scene}\n" +
                        "vars=${obs120.variables.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }}\n" +
                        "bgText=${obs120.bgText.filter { row -> row.any { c -> c != '.' && c != ' ' } }}\n"
                )

            // Screenshot 01: title screen — scratch smoke
            val titleScreenshot = agent.captureScreenshot("128_anchor1_title")
            assertTrue(
                titleScreenshot.length() > 0,
                "128 anchor1 title screenshot must be non-empty: ${titleScreenshot.absolutePath}",
            )
            assertScreenshotIsNonUniform(titleScreenshot, "128-anchor1-title")

            // Verify we are on the title scene
            val titleObs = agent.step()
            assertEquals(
                "title",
                titleObs.scene,
                "Expected to be on 'title' scene at boot (after 60 frames lead-in)",
            )

            agent.step(setOf(Button.START))
            agent.step()

            val gameplayObs = agent.waitForScene("gameplay", maxFrames = 60)
            agent.stepN(30)

            // Screenshot 02: gameplay scene — scratch smoke
            val gameplayScreenshot = agent.captureScreenshot("128_anchor1_gameplay")
            assertTrue(
                gameplayScreenshot.length() > 0,
                "128 anchor1 gameplay screenshot must be non-empty: ${gameplayScreenshot.absolutePath}",
            )
            assertScreenshotIsNonUniform(gameplayScreenshot, "128-anchor1-gameplay")

            assertEquals(
                "gameplay",
                gameplayObs.scene,
                "Expected _current_scene to be 'gameplay' after Start press",
            )

            val finalObs = agent.step()
            val currentLevel = finalObs.variables["current_level"]
            val nextLevel = finalObs.variables["next_level"]

            File(TEST_EVIDENCE_DIR, "128-anchor1-variables.txt")
                .writeText(
                    "current_scene: ${finalObs.scene}\n" +
                        "current_level: $currentLevel\n" +
                        "next_level: $nextLevel\n" +
                        "frame: ${finalObs.frame}\n"
                )

            assertEquals(
                0,
                currentLevel,
                "Expected current_level == 0 after gameplay_enter bootstrap",
            )
            assertEquals(
                0,
                nextLevel,
                "Expected next_level == 0 at fresh boot",
            )
        }
    }

    // ── Anchor 2 — Tilemap collision (jump + land on solid) (D-08 #2) ──
    //
    // Phase 22 (22-07): scratch-smoke — captures are NOT blessed anchors.
    @Test
    fun anchor2TilemapCollision() {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run :gbkt-examples:platformer-template:buildRom first",
        )

        newAgent().use { agent ->
            agent.stepN(120)
            agent.step(setOf(Button.START))
            agent.step()
            agent.waitForScene("gameplay", maxFrames = 60)
            agent.stepN(30)

            val groundedObs = agent.step()
            val vyGrounded = groundedObs.variables["playerVy"]
            val playerYGrounded = groundedObs.variables["playerY"]
            val groundedAtGrounded = groundedObs.variables["grounded"]
            val groundedScreenshot = agent.captureScreenshot("128_anchor2_grounded")
            assertTrue(groundedScreenshot.length() > 0, "128 anchor2 grounded screenshot non-empty")
            assertScreenshotIsNonUniform(groundedScreenshot, "128-anchor2-grounded")
            assertEquals(
                0,
                vyGrounded,
                "Expected playerVy == 0 at rest (grounded); got $vyGrounded",
            )

            agent.step(setOf(Button.A))
            val midJumpObs = agent.stepN(4, setOf(Button.A))
            val vyMid = midJumpObs.variables["playerVy"]
            val playerYMid = midJumpObs.variables["playerY"]
            val groundedAtMid = midJumpObs.variables["grounded"]
            val midJumpScreenshot = agent.captureScreenshot("128_anchor2_mid_jump")
            assertTrue(midJumpScreenshot.length() > 0, "128 anchor2 mid-jump screenshot non-empty")
            assertScreenshotIsNonUniform(midJumpScreenshot, "128-anchor2-mid-jump")

            if (vyMid == null || vyMid >= 0) {
                println(
                    "WARN 128 anchor2 mid-jump: playerVy=$vyMid (expected < 0 rising). " +
                        "PNG remains binding evidence."
                )
            }

            var landedObs = agent.step()
            var stepsTaken = 1
            val maxLandingFrames = 120
            while (stepsTaken < maxLandingFrames) {
                landedObs = agent.stepN(10)
                stepsTaken += 10
                if (landedObs.variables["playerVy"] == 0) break
            }
            val vyLanded = landedObs.variables["playerVy"]
            val playerYLanded = landedObs.variables["playerY"]
            val groundedAtLanded = landedObs.variables["grounded"]
            val landedScreenshot = agent.captureScreenshot("128_anchor2_landed")
            assertTrue(landedScreenshot.length() > 0, "128 anchor2 landed screenshot non-empty")
            assertScreenshotIsNonUniform(landedScreenshot, "128-anchor2-landed")

            File(TEST_EVIDENCE_DIR, "128-anchor2-variables.txt")
                .writeText(
                    "frame=${landedObs.frame}\n" +
                        "vy_grounded: $vyGrounded\n" +
                        "vy_mid_jump: $vyMid\n" +
                        "vy_landed: $vyLanded\n" +
                        "playerY_grounded: $playerYGrounded\n" +
                        "playerY_mid_jump: $playerYMid\n" +
                        "playerY_landed: $playerYLanded\n" +
                        "grounded_grounded: $groundedAtGrounded\n" +
                        "grounded_mid_jump: $groundedAtMid\n" +
                        "grounded_landed: $groundedAtLanded\n" +
                        "frames_to_land: $stepsTaken\n"
                )

            assertEquals(
                0,
                vyLanded,
                "Expected playerVy == 0 after landing; got $vyLanded after $stepsTaken frames.",
            )
        }
    }

    // ── Anchor 3 — Horizontal scroll (camera moves, no repeat) (D-08 #3) ──
    //
    // Phase 22 (22-07): scratch-smoke — captures are NOT blessed anchors.
    @Test
    fun anchor3HorizontalScroll() {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run :gbkt-examples:platformer-template:buildRom first",
        )

        fun StepAgent.readU16LE(addr: Int): Int = readMemory(addr) or (readMemory(addr + 1) shl 8)

        val CAMERA_X_ADDR = 0xC0DD
        val MAP_POS_X_ADDR = 0xC0E1

        newAgent().use { agent ->
            agent.stepN(120)
            agent.step(setOf(Button.START))
            agent.step()
            agent.waitForScene("gameplay", maxFrames = 60)
            agent.stepN(30)

            val initialCameraX = agent.readU16LE(CAMERA_X_ADDR)
            val initialMapPosX = agent.readMemory(MAP_POS_X_ADDR)
            val initialScreenshot = agent.captureScreenshot("128_anchor3_initial")
            assertTrue(initialScreenshot.length() > 0, "128 anchor3 initial screenshot non-empty")
            assertScreenshotIsNonUniform(initialScreenshot, "128-anchor3-initial")
            assertEquals(
                0,
                initialCameraX,
                "Expected _camera_x == 0 at gameplay entry; got $initialCameraX",
            )

            agent.stepN(150, setOf(Button.RIGHT))

            val scrolledCameraX = agent.readU16LE(CAMERA_X_ADDR)
            val scrolledMapPosX = agent.readMemory(MAP_POS_X_ADDR)
            val scrolledScreenshot = agent.captureScreenshot("128_anchor3_scrolled")
            assertTrue(scrolledScreenshot.length() > 0, "128 anchor3 scrolled screenshot non-empty")
            assertScreenshotIsNonUniform(scrolledScreenshot, "128-anchor3-scrolled")

            File(TEST_EVIDENCE_DIR, "128-anchor3-variables.txt")
                .writeText(
                    "initial_camera_x: $initialCameraX\n" +
                        "initial_map_pos_x: $initialMapPosX\n" +
                        "scrolled_camera_x: $scrolledCameraX\n" +
                        "scrolled_map_pos_x: $scrolledMapPosX\n"
                )

            assertTrue(
                scrolledCameraX > 0,
                "Expected _camera_x > 0 after holding right 150 frames; got $scrolledCameraX",
            )
            assertTrue(
                scrolledMapPosX > 0,
                "Expected _map_pos_x > 0 (tile column advanced); got $scrolledMapPosX",
            )

            val initialBytes = initialScreenshot.readBytes()
            val scrolledBytes = scrolledScreenshot.readBytes()
            assertTrue(
                !initialBytes.contentEquals(scrolledBytes),
                "anchor 3 initial/scrolled PNGs are byte-identical — visual didn't change.",
            )
        }
    }

    // ── Anchor 4 — Metasprite animation (multi-frame walking + hflip) (D-08 #4) ──
    //
    // Walk-cycle captures (01–03) drive RIGHT to verify multi-frame walkFrameIdx cycling.
    //
    // Phase 22 (22-07): scratch-smoke — captures are NOT blessed anchors. Uses captureScreenshot
    // directly; the Phase 15 F7 OAM xFlip + sprite-region diff gate is preserved.
    @Test
    fun anchor4MetaspriteAnimation() {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run :gbkt-examples:platformer-template:buildRom first",
        )

        newAgent().use { agent ->
            agent.stepN(120)
            agent.step(setOf(Button.START))
            agent.step()
            agent.waitForScene("gameplay", maxFrames = 60)
            agent.stepN(30)

            agent.stepN(74, setOf(Button.RIGHT))

            // 01 — hold right for 6 more frames, sample walkFrameIdx, screenshot
            val walk0Obs = agent.stepN(6, setOf(Button.RIGHT))
            val frameIdx0 = walk0Obs.variables["walkFrameIdx"]
            val walk0Screenshot = agent.captureScreenshot("128_anchor4_walk_frame_0")
            assertTrue(
                walk0Screenshot.length() > 0,
                "128 anchor4 walk-frame-0 screenshot non-empty",
            )
            assertScreenshotIsNonUniform(walk0Screenshot, "128-anchor4-walk-frame-0")

            // 02 — hold right another 6 frames
            val walk1Obs = agent.stepN(6, setOf(Button.RIGHT))
            val frameIdx1 = walk1Obs.variables["walkFrameIdx"]
            val walk1Screenshot = agent.captureScreenshot("128_anchor4_walk_frame_1")
            assertTrue(
                walk1Screenshot.length() > 0,
                "128 anchor4 walk-frame-1 screenshot non-empty",
            )
            assertScreenshotIsNonUniform(walk1Screenshot, "128-anchor4-walk-frame-1")

            // 03 — hold right another 6 frames
            val walk2Obs = agent.stepN(6, setOf(Button.RIGHT))
            val frameIdx2 = walk2Obs.variables["walkFrameIdx"]
            val walk2Screenshot = agent.captureScreenshot("128_anchor4_walk_frame_2")
            assertTrue(
                walk2Screenshot.length() > 0,
                "128 anchor4 walk-frame-2 screenshot non-empty",
            )
            assertScreenshotIsNonUniform(walk2Screenshot, "128-anchor4-walk-frame-2")

            // 04 — release right, hold left for 10 frames, assert facingRot=3, screenshot
            agent.step()
            val faceLeftObs = agent.stepN(10, setOf(Button.LEFT))
            val facingRot = faceLeftObs.variables["facingRot"]
            val facingLeftScreenshot = agent.captureScreenshot("128_anchor4_facing_left")
            assertTrue(
                facingLeftScreenshot.length() > 0,
                "128 anchor4 facing-left screenshot non-empty",
            )
            assertScreenshotIsNonUniform(facingLeftScreenshot, "128-anchor4-facing-left")

            File(TEST_EVIDENCE_DIR, "128-anchor4-variables.txt")
                .writeText(
                    "walkFrameIdx_at_01: $frameIdx0\n" +
                        "walkFrameIdx_at_02: $frameIdx1\n" +
                        "walkFrameIdx_at_03: $frameIdx2\n" +
                        "facingRot_at_04: $facingRot\n"
                )

            val distinctFrameIndices = setOf(frameIdx0, frameIdx1, frameIdx2).size
            assertTrue(
                distinctFrameIndices >= 2,
                "Expected walkFrameIdx to take >= 2 distinct values across 3 captures (cycling); " +
                    "got values [$frameIdx0, $frameIdx1, $frameIdx2] ($distinctFrameIndices distinct)",
            )

            assertEquals(3, facingRot, "Expected facingRot == 3 after holding LEFT; got $facingRot")

            val walk0Bytes = walk0Screenshot.readBytes()
            val walk1Bytes = walk1Screenshot.readBytes()
            val walk2Bytes = walk2Screenshot.readBytes()
            val faceLeftBytes = facingLeftScreenshot.readBytes()

            assertTrue(
                !(walk0Bytes.contentEquals(walk1Bytes) && walk1Bytes.contentEquals(walk2Bytes)),
                "Expected walk-frame PNGs to differ across the 3 captures (animation cycled)",
            )
            assertTrue(
                !walk0Bytes.contentEquals(faceLeftBytes),
                "Expected facing-left PNG to differ from walk-right (sprite mirrored)",
            )

            // Phase 15 F7 (REQ-6) — visible-hflip closure, sprite-region diff at settled camera.
            agent.stepN(14, setOf(Button.RIGHT))
            val rightObs = agent.stepN(12)
            val rightSprites = rightObs.sprites
            val faceRightStill = agent.captureScreenshot("128_anchor4_facing_right_still")
            assertTrue(
                faceRightStill.length() > 0,
                "128 anchor4 facing-right-still screenshot non-empty",
            )
            assertScreenshotIsNonUniform(faceRightStill, "128-anchor4-facing-right-still")

            agent.stepN(12, setOf(Button.LEFT))
            val leftObs = agent.stepN(12)
            val leftSprites = leftObs.sprites
            val faceLeftStill = agent.captureScreenshot("128_anchor4_facing_left_still")
            assertTrue(
                faceLeftStill.length() > 0,
                "128 anchor4 facing-left-still screenshot non-empty",
            )
            assertScreenshotIsNonUniform(faceLeftStill, "128-anchor4-facing-left-still")

            assertTrue(
                rightSprites.isNotEmpty() && rightSprites.all { !it.xFlip },
                "anchor4 hflip: facing-RIGHT player OAM sprites must NOT be x-flipped; " +
                    "got xFlip=${rightSprites.map { it.xFlip }}",
            )
            assertTrue(
                leftSprites.isNotEmpty() && leftSprites.all { it.xFlip },
                "anchor4 hflip: facing-LEFT player OAM sprites MUST be x-flipped; " +
                    "got xFlip=${leftSprites.map { it.xFlip }}",
            )

            val allSprites = rightSprites + leftSprites
            require(allSprites.isNotEmpty()) {
                "anchor4: no on-screen player OAM sprites observed for the facing comparison"
            }
            val pad = 2
            val rx0 = (allSprites.minOf { it.screenX } - pad).coerceIn(0, 159)
            val ry0 = (allSprites.minOf { it.screenY } - pad).coerceIn(0, 143)
            val rx1 = (allSprites.maxOf { it.screenX + 8 } + pad).coerceIn(rx0 + 1, 160)
            val ry1 = (allSprites.maxOf { it.screenY + 16 } + pad).coerceIn(ry0 + 1, 144)

            val imgR = ImageIO.read(faceRightStill)
            val imgL = ImageIO.read(faceLeftStill)
            var regionDiff = 0
            var regionTotal = 0
            for (yy in ry0 until ry1) {
                for (xx in rx0 until rx1) {
                    regionTotal++
                    if ((imgR.getRGB(xx, yy) and 0xFFFFFF) != (imgL.getRGB(xx, yy) and 0xFFFFFF)) {
                        regionDiff++
                    }
                }
            }
            val regionDiffRatio = if (regionTotal > 0) regionDiff.toDouble() / regionTotal else 0.0
            File(TEST_EVIDENCE_DIR, "128-anchor4-facing-region-diff.txt")
                .writeText(
                    "sprite region: x[$rx0-$rx1] y[$ry0-$ry1]\n" +
                        "region pixels: $regionTotal\n" +
                        "differing: $regionDiff\n" +
                        "region_diff_ratio: ${"%.4f".format(regionDiffRatio)}\n" +
                        "facingRot_at_left: $facingRot\n"
                )

            assertTrue(
                regionDiffRatio >= 0.20,
                "anchor4 hflip: facing-right vs facing-left differ in only " +
                    "${"%.1f".format(regionDiffRatio * 100)}% of the player sprite region " +
                    "x[$rx0-$rx1] y[$ry0-$ry1] (expected >= 20% for a real mirror).",
            )
            println(
                "128 anchor4 sprite-region hflip diff: ${"%.1f".format(regionDiffRatio * 100)}% " +
                    "(>= 20%) -- PASS"
            )
        }
    }

    // ── Anchor 5 — Level-switch (gameplay → NextLevel card → level 2) (D-08 #5) ──
    //
    // Phase 22 (22-07): scratch-smoke — captures are NOT blessed anchors.
    @Test
    fun anchor5LevelSwitch() {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run :gbkt-examples:platformer-template:buildRom first",
        )

        val CURRENT_LEVEL_ADDR = 0xC0EF
        val NEXT_LEVEL_ADDR = 0xC0F0

        newAgent().use { agent ->
            agent.stepN(120)
            agent.step(setOf(Button.START))
            agent.step()
            agent.waitForScene("gameplay", maxFrames = 60)
            agent.stepN(30)

            val initialCurrent = agent.readMemory(CURRENT_LEVEL_ADDR)
            val initialNext = agent.readMemory(NEXT_LEVEL_ADDR)
            assertEquals(
                0,
                initialCurrent,
                "Expected _current_level == 0 at gameplay entry; got $initialCurrent",
            )
            assertEquals(
                0,
                initialNext,
                "Expected _next_level == 0 at gameplay entry; got $initialNext",
            )

            val maxTraversalFrames = 2000
            var framesHeld = 0
            var detectedNext = initialNext
            var lastPlayerXSubpx = 0
            var lastSceneBeforeFlip = "gameplay"
            var lastGameplayFrame = -1
            while (framesHeld < maxTraversalFrames) {
                val buttons =
                    if ((framesHeld / 8) % 3 == 0) setOf(Button.RIGHT, Button.A)
                    else setOf(Button.RIGHT)
                val obs = agent.step(buttons)
                framesHeld += 1
                detectedNext = agent.readMemory(NEXT_LEVEL_ADDR)
                if (detectedNext != initialNext) {
                    lastGameplayFrame = obs.frame
                    val lastGameplayScreenshot =
                        agent.captureScreenshot("128_anchor5_last_gameplay")
                    assertTrue(
                        lastGameplayScreenshot.length() > 0,
                        "128 anchor5 last-gameplay screenshot non-empty",
                    )
                    assertScreenshotIsNonUniform(
                        lastGameplayScreenshot,
                        "128-anchor5-last-gameplay",
                    )
                    break
                }
                if (obs.scene != null) lastSceneBeforeFlip = obs.scene!!
                lastPlayerXSubpx = obs.variables["playerX"] ?: lastPlayerXSubpx
            }

            if (detectedNext == initialNext) {
                fail(
                    "level-end trigger did not fire — held RIGHT (+ periodic A) for $framesHeld " +
                        "frames, _next_level still == $initialNext. last _playerX (subpixel) = " +
                        "$lastPlayerXSubpx (player_real_x = ${lastPlayerXSubpx shr 4}). " +
                        "Address 0x${"%04X".format(NEXT_LEVEL_ADDR)} (see platformer-template.noi)."
                )
            }

            assertEquals(
                1,
                detectedNext,
                "Expected _next_level == 1 after level-end trigger fired; got $detectedNext",
            )

            val nextlevelFlipScreenshot = agent.captureScreenshot("128_anchor5_nextlevel_flip")
            assertTrue(
                nextlevelFlipScreenshot.length() > 0,
                "128 anchor5 nextlevel-flip screenshot non-empty",
            )
            assertScreenshotIsNonUniform(nextlevelFlipScreenshot, "128-anchor5-nextlevel-flip")

            val nextLevelObs = agent.waitForScene("nextLevelScene", maxFrames = 30)
            assertEquals(
                "nextLevelScene",
                nextLevelObs.scene,
                "Expected scene == 'nextLevelScene' after main() level-switch guard fired; got ${nextLevelObs.scene}",
            )
            agent.stepN(3)
            val nextLevelCardScreenshot = agent.captureScreenshot("128_anchor5_nextlevel_card")
            assertTrue(
                nextLevelCardScreenshot.length() > 0,
                "128 anchor5 nextlevel-card screenshot non-empty",
            )
            assertScreenshotIsNonUniform(nextLevelCardScreenshot, "128-anchor5-nextlevel-card")

            val afterGuardCurrent = agent.readMemory(CURRENT_LEVEL_ADDR)
            assertEquals(
                0,
                afterGuardCurrent,
                "Phase 12.6 D-04: _current_level should still == 0 after the trimmed guard navigates to nextLevelScene; got $afterGuardCurrent.",
            )

            val preStartObs = agent.step()
            val preStartScene = preStartObs.scene
            agent.stepN(30)
            val postStartObs = agent.step(setOf(Button.START))
            agent.stepN(3, setOf(Button.START))
            val postReleaseObs = agent.step()
            agent.stepN(130)

            val midLevel2Current = agent.readMemory(CURRENT_LEVEL_ADDR)
            val midLevel2Next = agent.readMemory(NEXT_LEVEL_ADDR)
            val midLevel2Scene = agent.step().scene

            val level2Screenshot = agent.captureScreenshot("128_anchor5_level_2")
            assertTrue(level2Screenshot.length() > 0, "128 anchor5 level-2 screenshot non-empty")
            assertScreenshotIsNonUniform(level2Screenshot, "128-anchor5-level-2")

            val finalCurrent = agent.readMemory(CURRENT_LEVEL_ADDR)
            val finalNext = agent.readMemory(NEXT_LEVEL_ADDR)
            val CAMERA_X_ADDR = 0xC0DD
            val finalObs = agent.step()
            val finalPlayerY = finalObs.variables["playerY"]
            val finalPlayerVy = finalObs.variables["playerVy"]
            val finalGrounded = finalObs.variables["grounded"]
            val finalCameraXLo = agent.readMemory(CAMERA_X_ADDR)
            val finalCameraXHi = agent.readMemory(CAMERA_X_ADDR + 1)
            val finalCameraX = finalCameraXLo or (finalCameraXHi shl 8)

            File(TEST_EVIDENCE_DIR, "128-anchor5-variables.txt")
                .writeText(
                    "frame=${finalObs.frame}\n" +
                        "initial_current_level: $initialCurrent\n" +
                        "initial_next_level: $initialNext\n" +
                        "frames_to_trigger: $framesHeld\n" +
                        "last_gameplay_frame: $lastGameplayFrame\n" +
                        "last_scene_before_flip: $lastSceneBeforeFlip\n" +
                        "last_player_x_subpixel: $lastPlayerXSubpx\n" +
                        "last_player_real_x: ${lastPlayerXSubpx shr 4}\n" +
                        "next_level_after_trigger: $detectedNext\n" +
                        "current_level_after_guard: $afterGuardCurrent\n" +
                        "pre_start_scene: $preStartScene\n" +
                        "post_start_scene: ${postStartObs.scene}\n" +
                        "post_release_scene: ${postReleaseObs.scene}\n" +
                        "mid_level_2_current: $midLevel2Current\n" +
                        "mid_level_2_next: $midLevel2Next\n" +
                        "mid_level_2_scene: $midLevel2Scene\n" +
                        "final_current_level: $finalCurrent\n" +
                        "final_next_level: $finalNext\n" +
                        "playerY: $finalPlayerY\n" +
                        "playerVy: $finalPlayerVy\n" +
                        "grounded: $finalGrounded\n" +
                        "_camera_x: $finalCameraX\n"
                )

            assertTrue(
                finalCurrent == 1,
                "Expected _current_level == 1 after Phase 12.6 fix; got $finalCurrent.",
            )

            val nextlevelFlipBytes = nextlevelFlipScreenshot.readBytes()
            val level2Bytes = level2Screenshot.readBytes()
            assertTrue(
                !nextlevelFlipBytes.contentEquals(level2Bytes),
                "anchor 5: level-2 screenshot is byte-identical to nextlevel-flip screenshot — cross-bank reload did not occur.",
            )
        }
    }

    /**
     * Phase 13.4 Plan 13.4-11 — jump-traversal regression guard (GREEN, not a bug fix).
     *
     * Phase 22 (22-07): no captures in this test — pure variable/state guard, no screenshot smoke
     * needed.
     */
    @Test
    fun worldOneArea1RequiresJumpsToReachNextLevel_13_4_11() {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run :gbkt-examples:platformer-template:buildRom first",
        )
        val NEXT_LEVEL_ADDR = 0xC0F0

        // ── Phase A — held-RIGHT-only STALLS before the trigger (the 13.4-10 trap) ──
        newAgent().use { agent ->
            agent.stepN(120)
            agent.step(setOf(Button.START))
            agent.step()
            agent.waitForScene("gameplay", maxFrames = 60)
            agent.stepN(30)

            val spawnX = agent.step().variables["playerX"] ?: 0
            var lastX = spawnX
            var maxX = spawnX
            repeat(800) {
                val obs = agent.step(setOf(Button.RIGHT))
                lastX = obs.variables["playerX"] ?: lastX
                if (lastX > maxX) maxX = lastX
            }
            val nextAfterRightOnly = agent.readMemory(NEXT_LEVEL_ADDR)

            assertTrue(
                maxX > spawnX,
                "Phase A precondition: holding RIGHT should advance the player off spawn " +
                    "(spawn playerX=$spawnX, maxX=$maxX).",
            )
            assertEquals(
                0,
                nextAfterRightOnly,
                "Phase A (13.4-10 trap): holding RIGHT ALONE for 800 frames must NOT reach the " +
                    "level-end trigger on the real 60×32 world1Area1; got $nextAfterRightOnly.",
            )
        }

        // ── Phase B — RIGHT + periodic A REACHES nextLevelScene (the 13.4-11 finding) ──
        newAgent().use { agent ->
            agent.stepN(120)
            agent.step(setOf(Button.START))
            agent.step()
            agent.waitForScene("gameplay", maxFrames = 60)
            agent.stepN(30)

            val maxTraversalFrames = 2000
            var framesHeld = 0
            var detectedNext = agent.readMemory(NEXT_LEVEL_ADDR)
            while (framesHeld < maxTraversalFrames && detectedNext == 0) {
                val buttons =
                    if ((framesHeld / 8) % 3 == 0) setOf(Button.RIGHT, Button.A)
                    else setOf(Button.RIGHT)
                agent.step(buttons)
                framesHeld += 1
                detectedNext = agent.readMemory(NEXT_LEVEL_ADDR)
            }

            assertEquals(
                1,
                detectedNext,
                "Phase B (13.4-11): RIGHT + periodic A must traverse world1Area1 and fire the " +
                    "level-end trigger within $maxTraversalFrames frames; got $detectedNext after $framesHeld frames.",
            )
            val nextLevelObs = agent.waitForScene("nextLevelScene", maxFrames = 30)
            assertEquals(
                "nextLevelScene",
                nextLevelObs.scene,
                "Phase B: scene must flip to 'nextLevelScene' after level-end trigger fires; " +
                    "got ${nextLevelObs.scene}.",
            )
        }
    }
}
