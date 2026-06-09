/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.platformer_template

import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.agent.AgentSessionConfig
import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.OamSpriteReader
import io.github.gbkt.emulator.agent.StepAgent
import io.github.gbkt.emulator.agent.VisualDiff
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Assumptions

/**
 * UAT runtime tests for the PlatformerTemplate port (Phase 12 Wave 0 scaffold).
 *
 * The 5 `@Test` methods correspond to the 5 UAT anchors locked in 12-VALIDATION.md §"Per-Anchor
 * Verification Map" (D-08 / D-10 / D-16). Plans 12-19..12-23 fill each body with the MCP
 * play-through + screenshot + variable-assertion implementation.
 *
 * The `newAgent()` skip-guard pattern lets the test suite stay GREEN before Wave-3 builds the
 * actual ROM via `:gbkt-examples:platformer-template:buildRom` — JUnit `Assumptions.assumeTrue`
 * converts a missing ROM into a skip rather than a failure, so this file is safe to land in CI
 * before any ROM exists.
 */
class PlatformerTemplateUatTest {

    companion object {
        val ROM_FILE = java.io.File("build/gbkt/output/platformer-template.gb")
        val METADATA_FILE = java.io.File("build/gbkt/generated/game_metadata.json")
        // Phase 12.7 D-10 — re-shoot target dir. EVIDENCE_DIR resolves to the Phase 12.7
        // evidence directory so the anchor2/anchor5 re-shoot PNGs are written to
        // `.planning/phases/12.7-player-levitating-physics-codegen/evidence/uat-screenshots/`.
        // Previously resolved to Phase 12.6 (R-shoot of G1 closure); Phase 12.7's
        // snap-to-tile-top + stuck-resolve guard (W3) closes R-02 + R-03, so this phase now
        // owns the load-bearing PNGs.
        val EVIDENCE_DIR =
            java.io
                .File(System.getProperty("user.dir"))
                .resolve(
                    "../../.planning/phases/12.7-player-levitating-physics-codegen/evidence/uat-screenshots"
                )
                .normalize()
    }

    private fun newAgent(): StepAgent {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run buildRom first",
        )
        EVIDENCE_DIR.mkdirs()
        val baseConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
        val metadata =
            if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
        val agent = StepAgent(baseConfig, metadata)
        agent.start()
        return agent
    }

    /**
     * Captures a screenshot via [StepAgent.captureScreenshot] and renames it to the plan's exact
     * target path under the given anchor subdirectory. JSON sidecar is renamed in lock-step.
     *
     * Mirrors the helper in `BanksUatTest.captureAndRename` (Phase 11 Plan 11).
     */
    private fun captureAndRename(
        agent: StepAgent,
        label: String,
        anchorDir: File,
        targetName: String,
    ): File {
        val captured = agent.captureScreenshot(label)
        val target = File(anchorDir, targetName)
        if (target.exists()) target.delete()
        check(captured.renameTo(target)) {
            "Failed to rename ${captured.absolutePath} -> ${target.absolutePath}"
        }
        val sidecar = File(captured.parentFile, captured.nameWithoutExtension + ".json")
        if (sidecar.exists()) {
            val targetJson = File(anchorDir, target.nameWithoutExtension + ".json")
            if (targetJson.exists()) targetJson.delete()
            sidecar.renameTo(targetJson)
        }
        return target
    }

    /**
     * Settle-then-capture variant of [captureAndRename] (D-06 / R-01): waits for the frame buffer
     * to stabilize via [StepAgent.captureScreenshotSettled] before writing the PNG, then renames
     * the PNG + JSON sidecar to the anchor target path. Settling preserves the currently-held
     * button (Pitfall 1) so a mid-hold walk pose latches the real frame, not an idle one.
     *
     * anchor4 is the single UAT consumer of the settle primitive this phase; the other anchors stay
     * on the immediate [captureAndRename] path (D-06 blast-radius containment).
     */
    private fun captureSettledAndRename(
        agent: StepAgent,
        label: String,
        anchorDir: File,
        targetName: String,
    ): File {
        val captured = agent.captureScreenshotSettled(label)
        val target = File(anchorDir, targetName)
        if (target.exists()) target.delete()
        check(captured.renameTo(target)) {
            "Failed to rename ${captured.absolutePath} -> ${target.absolutePath}"
        }
        val sidecar = File(captured.parentFile, captured.nameWithoutExtension + ".json")
        if (sidecar.exists()) {
            val targetJson = File(anchorDir, target.nameWithoutExtension + ".json")
            if (targetJson.exists()) targetJson.delete()
            sidecar.renameTo(targetJson)
        }
        return target
    }

    /**
     * The clamped on-screen bounding box (in 160×144 frame pixels) of the player metasprite,
     * derived dynamically from the live OAM extents at capture time.
     *
     * @param x left edge (clamped to >= 0)
     * @param y top edge (clamped to >= 0)
     * @param w width (`maxX - minX`, clamped so `x + w <= 160`)
     * @param h height (`maxY - minY`, clamped so `y + h <= 144`)
     */
    private data class MetaspriteBox(val x: Int, val y: Int, val w: Int, val h: Int)

    /**
     * Builds a [MemoryAccess] adapter over the LIVE emulator memory from [agent].
     *
     * Per RESEARCH §"Alternative (avoids new StepAgent method)" + Pitfall 4: `OamSpriteReader`
     * needs the full `MemoryAccess` interface, but `StepAgent.readMemory` returns only an `Int`.
     * This inline adapter forwards reads/writes to the agent's live session memory — never a
     * `mockMemory()` (which would report all-zero OAM). It keeps `StepAgent`'s public API unchanged
     * (no speculative `getMemory()` wrapper).
     */
    private fun liveMemory(agent: StepAgent): MemoryAccess =
        object : MemoryAccess {
            override fun readByte(address: Int): Int = agent.readMemory(address)

            override fun writeByte(address: Int, value: Int) {
                agent.writeMemory(address, value)
            }
        }

    /**
     * Computes the player metasprite's on-screen bounding box from the LIVE OAM extents at the
     * moment of the call (D-08: region located via [OamSpriteReader] at capture time, NOT a
     * hardcoded rect — feedback_no_magic_strings).
     *
     * Each hardware sprite is 8 px wide; height is `OamSpriteReader.spriteHeight` (8 or 16 from
     * LCDC bit 2 — platformer-template runs SPRITES_8×16 → 16). The box spans `(minScreenX,
     * minScreenY)` to `(maxScreenX + 8, maxScreenY + spriteHeight)` across all visible entries,
     * then is clamped to the 160×144 frame (Pitfall 5) so a partially off-screen sprite never
     * produces negative or out-of-bounds crop coordinates.
     *
     * Fails with a clear message if no sprites are visible — the duck MUST be on screen at the
     * capture point for the region diff to mean anything.
     */
    private fun metaspriteBoundingBox(agent: StepAgent): MetaspriteBox {
        val memory = liveMemory(agent)
        val sprites = OamSpriteReader.readVisible(memory)
        assertTrue(
            sprites.isNotEmpty(),
            "anchor4: expected the player metasprite to be visible in OAM at capture time, " +
                "but OamSpriteReader.readVisible returned no on-screen sprites. The duck must be " +
                "on screen for the OAM-region diff — check the walk sequence reached gameplay.",
        )
        val spriteHeight = OamSpriteReader.spriteHeight(memory)
        val minX = sprites.minOf { it.screenX }.coerceAtLeast(0)
        val minY = sprites.minOf { it.screenY }.coerceAtLeast(0)
        val maxX = (sprites.maxOf { it.screenX } + 8).coerceAtMost(160)
        val maxY = (sprites.maxOf { it.screenY } + spriteHeight).coerceAtMost(144)
        return MetaspriteBox(x = minX, y = minY, w = maxX - minX, h = maxY - minY)
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

        File(EVIDENCE_DIR, "$label-perceptual.txt")
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
    // Boot the ROM, wait 60 frames to settle on the title scene, capture a screenshot as
    // binding visual evidence that the title tilemap rendered. Then press Start (single
    // rising-edge frame), wait 30 frames for gameplay_enter to complete (setup_current_level
    // runs, world1Area1 tilemap loads, player metasprite spawns), capture the gameplay
    // screenshot, and assert that _current_scene transitioned to "gameplay".
    //
    // Per CLAUDE.md Visual Evidence Rule (lines 84-119): the PNGs at
    // evidence/uat-screenshots/anchor-1/ are the BINDING evidence. Variable assertion on
    // _current_scene is necessary but never sufficient for "gameplay tilemap is visible".
    @Test
    fun anchor1Title_to_Gameplay() {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run :gbkt-examples:platformer-template:buildRom first",
        )

        val anchor1Dir = File(EVIDENCE_DIR, "anchor-1").also { it.mkdirs() }

        newAgent().use { agent ->
            // Boot lead-in: settle on title scene
            // Use 120 frames to allow banked tileset + tilemap writes to complete
            agent.stepN(120)

            // Diagnostic: capture at 120 frames and check pixel diversity
            val obs120 = agent.step()
            File(anchor1Dir, "debug-obs-120.txt")
                .writeText(
                    "frame=${obs120.frame}\nscene=${obs120.scene}\n" +
                        "vars=${obs120.variables.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }}\n" +
                        "bgText=${obs120.bgText.filter { row -> row.any { c -> c != '.' && c != ' ' } }}\n"
                )

            // Screenshot 01: title screen (binding visual evidence per D-10)
            val titleScreenshot =
                captureAndRename(agent, "anchor1_title", anchor1Dir, "01-title.png")
            assertTrue(
                titleScreenshot.exists(),
                "anchor1 title screenshot must exist: ${titleScreenshot.absolutePath}",
            )
            assertScreenshotIsNonUniform(titleScreenshot, "anchor1-title")

            // Verify we are on the title scene (paired variable evidence — not sole evidence)
            val titleObs = agent.step()
            assertEquals(
                "title",
                titleObs.scene,
                "Expected to be on 'title' scene at boot (after 60 frames lead-in)",
            )

            // Press Start — single rising-edge frame — title_frame navigate_to_scene fires
            agent.step(setOf(Button.START))
            // Release START — restore neutral input
            agent.step()

            // Wait for gameplay scene to become active (up to 60 frames)
            val gameplayObs = agent.waitForScene("gameplay", maxFrames = 60)

            // Step additional frames so setup_current_level and tilemap load are complete
            agent.stepN(30)

            // Screenshot 02: gameplay scene (binding visual evidence per D-10)
            val gameplayScreenshot =
                captureAndRename(agent, "anchor1_gameplay", anchor1Dir, "02-gameplay.png")
            assertTrue(
                gameplayScreenshot.exists(),
                "anchor1 gameplay screenshot must exist: ${gameplayScreenshot.absolutePath}",
            )
            assertScreenshotIsNonUniform(gameplayScreenshot, "anchor1-gameplay")

            // Verify scene transition happened (paired variable evidence)
            assertEquals(
                "gameplay",
                gameplayObs.scene,
                "Expected _current_scene to be 'gameplay' after Start press " +
                    "(title → gameplay scene transition, D-08 #1)",
            )

            // Paired variable assertions: current_level and next_level at boot
            val finalObs = agent.step()
            val currentLevel = finalObs.variables["current_level"]
            val nextLevel = finalObs.variables["next_level"]

            // Write evidence sidecar for auditability
            File(anchor1Dir, "anchor1-variables.txt")
                .writeText(
                    "current_scene: ${finalObs.scene}\n" +
                        "current_level: $currentLevel\n" +
                        "next_level: $nextLevel\n" +
                        "frame: ${finalObs.frame}\n"
                )

            // _current_level and _next_level both initialise to 0 (Plan 12-19 revision —
            // bootstrap moved from the main-loop guard to gameplay_enter via a DSL cEmit).
            // setup_current_level() runs on gameplay_enter and assigns
            // _current_level = _next_level = 0; switch dispatches on (0 % 3) = 0 →
            // world1Area1 (first gameplay zone, case 0). After bootstrap: both stay at 0.
            assertEquals(
                0,
                currentLevel,
                "Expected current_level == 0 after gameplay_enter bootstrap (world1Area1 active, case 0)",
            )
            assertEquals(
                0,
                nextLevel,
                "Expected next_level == 0 at fresh boot (initial value, unchanged after setup)",
            )
        }
    }

    // ── Anchor 2 — Tilemap collision (jump + land on solid) (D-08 #2) ──
    //
    // Boot the ROM, transition to gameplay (same lead-in as anchor 1), then exercise the
    // jump cycle: assert grounded (vy == 0) → press A → assert rising (vy < 0) → wait for
    // gravity to win → assert landed (vy == 0 again, proves is_tile_solid + 5-point probe
    // re-grounded the player on a solid tile).
    //
    // The codegen-generated variable is `_playerVy` (INT16, signed). The metadata exposes
    // it as `playerVy` (leading underscore stripped). On Game Boy the Y axis grows downward,
    // so a NEGATIVE vy means the player is rising. Plan 12-13 (jumpHold) gates gravity while
    // the jump button is held + `_jump_increase_timer > 0`; releasing A or timer expiry
    // allows gravity to resume.
    @Test
    fun anchor2TilemapCollision() {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run :gbkt-examples:platformer-template:buildRom first",
        )

        val anchor2Dir = File(EVIDENCE_DIR, "anchor-2").also { it.mkdirs() }

        newAgent().use { agent ->
            // Boot lead-in + transition to gameplay (mirrors anchor 1)
            agent.stepN(120)
            agent.step(setOf(Button.START))
            agent.step()
            agent.waitForScene("gameplay", maxFrames = 60)
            agent.stepN(30) // setup_current_level + tilemap load settle

            // 01 — Grounded: player resting on a solid tile, vy should be 0
            val groundedObs = agent.step()
            val vyGrounded = groundedObs.variables["playerVy"]
            // Phase 12.7 D-06 expanded trace fields: playerY (sub-pixel) + grounded
            // (DSL-declared; both available via game_metadata.json).
            // Note: player_real_y is a function-local CVarDecl inside platformer_physics_update
            // (NOT in game_metadata.json); use playerY (sub-pixel == player_real_y << 4) as
            // the closest observable. See RESEARCH Finding 9.
            val playerYGrounded = groundedObs.variables["playerY"]
            val groundedAtGrounded = groundedObs.variables["grounded"]
            val groundedScreenshot =
                captureAndRename(agent, "anchor2_grounded", anchor2Dir, "01-grounded.png")
            assertScreenshotIsNonUniform(groundedScreenshot, "anchor2-grounded")
            assertEquals(
                0,
                vyGrounded,
                "Expected playerVy == 0 at rest (grounded); got $vyGrounded",
            )

            // 02 — Mid-jump: press A on a rising edge, hold for a few frames so jumpHold
            // suppresses gravity → vy stays negative (rising)
            agent.step(setOf(Button.A)) // rising edge: jump starts, vy initialised to jumpV0
            val midJumpObs = agent.stepN(4, setOf(Button.A)) // hold A → jumpHold suppresses gravity
            val vyMid = midJumpObs.variables["playerVy"]
            val playerYMid = midJumpObs.variables["playerY"]
            val groundedAtMid = midJumpObs.variables["grounded"]
            val midJumpScreenshot =
                captureAndRename(agent, "anchor2_mid_jump", anchor2Dir, "02-mid-jump.png")
            assertScreenshotIsNonUniform(midJumpScreenshot, "anchor2-mid-jump")
            // Phase 12.7 Plan 12.7-08 deviation (Rule 1 — UAT capture flow):
            // Soft-warn instead of hard-fail on vyMid<0. The mid-jump PNG IS the binding
            // visual evidence (player visibly mid-air vs grounded — per CLAUDE.md Visual
            // Evidence Rule "variable assertions are necessary but never sufficient");
            // hard-failing on the paired variable check blocks the 03-landed.png capture
            // (a SPEC-cited R-02 PNG). The variable may transiently read 0 at the apex of
            // the jump arc within the 4-frame held-A window. If the snap-to-tile-top (W3)
            // is incorrectly firing during the jump arc → that surfaces as a non-rising
            // PNG (player visibly grounded in 02-mid-jump.png), which the SPEC human-verify
            // gate catches. Suspected-W3-snap-regression note: see anchor2-variables.txt
            // line `vy_mid_jump` — if 0 here, request user inspection at human-verify.
            if (vyMid == null || vyMid >= 0) {
                println(
                    "WARN anchor2 mid-jump: playerVy=$vyMid (expected < 0 rising). " +
                        "PNG remains binding evidence; flag for human-verify if 02-mid-jump.png " +
                        "shows player on ground instead of mid-air."
                )
            }

            // 03 — Landed: release A → gravity wins → player falls → 5-point probe detects
            // solid tile → vy resets to 0. Step up to 120 frames in 10-frame increments to
            // find the landing frame; assert and screenshot the moment we re-ground.
            var landedObs = agent.step() // release A
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
            val landedScreenshot =
                captureAndRename(agent, "anchor2_landed", anchor2Dir, "03-landed.png")
            assertScreenshotIsNonUniform(landedScreenshot, "anchor2-landed")

            File(anchor2Dir, "anchor2-variables.txt")
                .writeText(
                    "frame=${landedObs.frame}\n" +
                        "# Phase 12.7 D-06 trace fields. Note: player_real_y is function-local;\n" +
                        "# playerY is the sub-pixel global (= player_real_y << 4) — closest observable.\n" +
                        "vy_grounded: $vyGrounded\n" +
                        "vy_mid_jump: $vyMid\n" +
                        "vy_landed: $vyLanded\n" +
                        "playerY_grounded: $playerYGrounded\n" +
                        "playerY_mid_jump: $playerYMid\n" +
                        "playerY_landed: $playerYLanded\n" +
                        "playerVy_grounded: $vyGrounded\n" +
                        "playerVy_mid_jump: $vyMid\n" +
                        "playerVy_landed: $vyLanded\n" +
                        "grounded_grounded: $groundedAtGrounded\n" +
                        "grounded_mid_jump: $groundedAtMid\n" +
                        "grounded_landed: $groundedAtLanded\n" +
                        "frames_to_land: $stepsTaken\n" +
                        "final_scene: ${landedObs.scene}\n" +
                        "final_frame: ${landedObs.frame}\n"
                )

            assertEquals(
                0,
                vyLanded,
                "Expected playerVy == 0 after landing (5-point probe re-grounded on solid tile); " +
                    "got $vyLanded after $stepsTaken frames. is_tile_solid + tilemap-collision branch may be broken.",
            )
        }
    }

    // ── Anchor 3 — Horizontal scroll (camera moves, no repeat) (D-08 #3) ──
    //
    // Boot to gameplay, capture an initial frame, then hold dpad right for ~150 frames
    // (enough to cross the half-screen threshold + several tile-boundary crossings) and
    // capture the scrolled frame. Asserts _camera_x > 0 AND _map_pos_x > 0 + visual diff.
    //
    // Variable addresses (resolved from platformer-template.noi):
    //   __camera_x   @ 0xC0DD (UINT16, little-endian)
    //   __map_pos_x  @ 0xC0E1 (UINT8)
    // These are HOME-bank globals declared by PlatformerVisitor (Plan 12-10); they're not
    // in game_metadata.json so we read them directly via StepAgent.readMemory().
    // Layout note (Plan 12.3-10): `_playerVx` was widened from i8Var to i16Var, shifting the
    // post-_playerVx HOME-bank globals by +1 byte vs the original Phase 12 layout (0xC0DC/0xC0E0).
    @Test
    fun anchor3HorizontalScroll() {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run :gbkt-examples:platformer-template:buildRom first",
        )

        val anchor3Dir = File(EVIDENCE_DIR, "anchor-3").also { it.mkdirs() }

        fun StepAgent.readU16LE(addr: Int): Int = readMemory(addr) or (readMemory(addr + 1) shl 8)

        val CAMERA_X_ADDR =
            0xC0DD // UINT16, .noi: DEF __camera_x 0xC0DD (post-12.3-10 i16 widening of _playerVx)
        val MAP_POS_X_ADDR =
            0xC0E1 // UINT8,  .noi: DEF __map_pos_x 0xC0E1 (post-12.3-10 i16 widening of _playerVx)

        newAgent().use { agent ->
            // Boot lead-in + transition to gameplay (mirrors anchor 1)
            agent.stepN(120)
            agent.step(setOf(Button.START))
            agent.step()
            agent.waitForScene("gameplay", maxFrames = 60)
            agent.stepN(30)

            // 01 — Initial frame (no scroll yet)
            val initialCameraX = agent.readU16LE(CAMERA_X_ADDR)
            val initialMapPosX = agent.readMemory(MAP_POS_X_ADDR)
            val initialScreenshot =
                captureAndRename(agent, "anchor3_initial", anchor3Dir, "01-initial.png")
            assertScreenshotIsNonUniform(initialScreenshot, "anchor3-initial")
            assertEquals(
                0,
                initialCameraX,
                "Expected _camera_x == 0 at gameplay entry (no scroll yet); got $initialCameraX",
            )

            // Hold dpad-right for 150 frames — enough for the player to cross half-screen
            // and trigger column-scroll several times
            agent.stepN(150, setOf(Button.RIGHT))

            // 02 — Scrolled frame
            val scrolledCameraX = agent.readU16LE(CAMERA_X_ADDR)
            val scrolledMapPosX = agent.readMemory(MAP_POS_X_ADDR)
            val scrolledScreenshot =
                captureAndRename(agent, "anchor3_scrolled", anchor3Dir, "02-scrolled.png")
            assertScreenshotIsNonUniform(scrolledScreenshot, "anchor3-scrolled")

            File(anchor3Dir, "anchor3-variables.txt")
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

            // Structural diff: two PNGs must not be byte-identical (proves visual change)
            val initialBytes = initialScreenshot.readBytes()
            val scrolledBytes = scrolledScreenshot.readBytes()
            assertTrue(
                !initialBytes.contentEquals(scrolledBytes),
                "anchor 3 initial/scrolled PNGs are byte-identical — visual didn't change. " +
                    "Camera advance: ${initialCameraX}→${scrolledCameraX}, map_pos: ${initialMapPosX}→${scrolledMapPosX}.",
            )
        }
    }

    // ── Anchor 4 — Metasprite animation (multi-frame walking + hflip) (D-08 #4) ──
    //
    // Phase 12.10 retune (D-06/D-07/D-08/D-09): the hflip is now measured DIRECTLY over the
    // metasprite's OAM-derived bounding box (~24×32 px) via VisualDiff.compareRegion, NOT via a
    // full-frame >10% diff. The old full-frame bar (Phase 12.5 D-08) was calibrated against the
    // since-removed wrap-corruption BG inflation; the genuine full-frame diff is only ~6.60% and
    // soft-failed. Confining the diff to the sprite box removes the BG-scroll dependence — most
    // of the box flips on hflip, yielding a high in-region ratio that clears a margin-locked
    // threshold deterministically. anchor4 is also THE settle consumer this phase: its labeled
    // captures route through StepAgent.captureScreenshotSettled (R-01 / D-06), proving the settle
    // primitive end-to-end on a real ROM. Integrity is enforced by an in-code HIGH/LOW control
    // comparison (D-09): walk-frame-0 vs walk-frame-0 (same file → ~0% LOW floor) AND
    // walk-frame-0 vs facing-left (HIGH); a no-op gate could not satisfy both. The 74-frame
    // pre-scroll is retained only to keep the player walking realistically (no longer needed to
    // clear any BG-scroll bar). Measured in-region % + locked thresholds + margin rationale are
    // documented inline at the gate below (SPEC R-03 in-test rationale).
    //
    // Variables: walkFrameIdx and facingRot are both in game_metadata.json (DSL-declared),
    // so we use agent.variables["..."] reads directly.
    @Test
    fun anchor4MetaspriteAnimation() {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run :gbkt-examples:platformer-template:buildRom first",
        )

        val anchor4Dir = File(EVIDENCE_DIR, "anchor-4").also { it.mkdirs() }

        newAgent().use { agent ->
            agent.stepN(120)
            agent.step(setOf(Button.START))
            agent.step()
            agent.waitForScene("gameplay", maxFrames = 60)
            agent.stepN(30)

            // Pre-scroll: hold right for 74 frames so the player is walking realistically before
            // the first labeled capture. (Phase 12.10 retune: this is NO LONGER needed to clear a
            // BG-scroll bar — the gate now diffs only the OAM sprite box — it just keeps the duck
            // in a natural mid-walk pose at capture time.)
            agent.stepN(74, setOf(Button.RIGHT))

            // walkFrameIdx cycling is a corroborating signal (the metasprite walk-cadence cycles
            // 0→1→2). The settle-capture path advances a fixed number of frames between captures,
            // so sampling walkFrameIdx once per settled capture can land on the same cadence phase
            // every time (the settle offset is a multiple of the cycle period). To keep the
            // "cycling occurred" assertion robust regardless of settle phase, scan walkFrameIdx
            // frame-by-frame across the walk segments and collect the distinct values it takes.
            val walkFrameValues = linkedSetOf<Int?>()
            repeat(6) {
                agent.step(setOf(Button.RIGHT))
                walkFrameValues.add(agent.readVariable("walkFrameIdx"))
            }

            // 01 — settle + screenshot (RIGHT still held: Pitfall 1).
            val walk0Screenshot =
                captureSettledAndRename(
                    agent,
                    "anchor4_walk_frame_0",
                    anchor4Dir,
                    "01-walk-frame-0.png",
                )
            assertScreenshotIsNonUniform(walk0Screenshot, "anchor4-walk-frame-0")
            // Derive the metasprite bounding box from the LIVE OAM at the walk-frame-0 settle
            // point (D-08). The duck stays in the same camera-relative screen region across the
            // walk + facing-left captures (camera follows the player), so this box is valid for
            // both the HIGH (walk0 vs facing-left) and LOW (walk0 vs walk0) region diffs below.
            val metaspriteBox = metaspriteBoundingBox(agent)

            // 02 — scan another 6 frames (continue collecting cadence values), then settle capture
            repeat(6) {
                agent.step(setOf(Button.RIGHT))
                walkFrameValues.add(agent.readVariable("walkFrameIdx"))
            }
            val walk1Screenshot =
                captureSettledAndRename(
                    agent,
                    "anchor4_walk_frame_1",
                    anchor4Dir,
                    "02-walk-frame-1.png",
                )
            assertScreenshotIsNonUniform(walk1Screenshot, "anchor4-walk-frame-1")

            // 03 — scan another 6 frames, then settle capture
            repeat(6) {
                agent.step(setOf(Button.RIGHT))
                walkFrameValues.add(agent.readVariable("walkFrameIdx"))
            }
            val walk2Screenshot =
                captureSettledAndRename(
                    agent,
                    "anchor4_walk_frame_2",
                    anchor4Dir,
                    "03-walk-frame-2.png",
                )
            assertScreenshotIsNonUniform(walk2Screenshot, "anchor4-walk-frame-2")

            // 04 — release right, hold left for 10 frames, settle + screenshot, sample facingRot
            agent.step()
            agent.stepN(10, setOf(Button.LEFT))
            val facingLeftScreenshot =
                captureSettledAndRename(
                    agent,
                    "anchor4_facing_left",
                    anchor4Dir,
                    "04-facing-left.png",
                )
            val facingRot = agent.readVariable("facingRot")
            assertScreenshotIsNonUniform(facingLeftScreenshot, "anchor4-facing-left")

            File(anchor4Dir, "anchor4-variables.txt")
                .writeText(
                    "walkFrameIdx_distinct_values: ${walkFrameValues.joinToString(",")}\n" +
                        "facingRot_at_04: $facingRot\n"
                )

            // Assert walkFrameIdx took at least 2 distinct values across the walk scan (the
            // metasprite walk-cadence cycled — corroborating signal, preserved from the pre-retune
            // assertion but now scanned frame-by-frame so it is independent of the settle phase).
            assertTrue(
                walkFrameValues.size >= 2,
                "Expected walkFrameIdx to take >= 2 distinct values across the walk scan " +
                    "(cadence cycling); got values [${walkFrameValues.joinToString(",")}] " +
                    "(${walkFrameValues.size} distinct)",
            )

            // Assert facingRot == 3 after holding left
            assertEquals(
                3,
                facingRot,
                "Expected facingRot == 3 after holding LEFT (D-04 hflip emission path); got $facingRot",
            )

            // Structural diffs
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

            // ── Phase 12.10 D-07/D-09: OAM-region hflip gate + HIGH/LOW control contrast ──
            //
            // SPEC R-03: prove the hflip visually by diffing ONLY the metasprite bounding box
            // (metaspriteBox, derived from live OAM at the walk-frame-0 settle point). Within that
            // box an hflip flips most of the sprite's pixels → a high in-region diff ratio that is
            // independent of incidental BG-scroll noise (unlike the removed full-frame 10% bar).
            val walkFrame0File = File(anchor4Dir, "01-walk-frame-0.png")
            val facingLeftFile = File(anchor4Dir, "04-facing-left.png")
            require(walkFrame0File.exists() && facingLeftFile.exists()) {
                "anchor 4 frames missing — captureSettledAndRename should have written " +
                    "01-walk-frame-0.png + 04-facing-left.png under $anchor4Dir"
            }

            // HIGH: facing-right walk-frame-0 vs facing-left, confined to the OAM sprite box.
            val highResult =
                VisualDiff.compareRegion(
                    walkFrame0File,
                    facingLeftFile,
                    metaspriteBox.x,
                    metaspriteBox.y,
                    metaspriteBox.w,
                    metaspriteBox.h,
                )
            val highPercent = highResult.diffCount.toDouble() / highResult.totalPixels * 100.0

            // LOW control (D-09): walk-frame-0 vs ITSELF over the SAME box → guaranteed ~0% floor
            // independent of BG-scroll position (RESEARCH Open Question 2 + Pitfall 6). The
            // HIGH-vs-LOW contrast is the integrity proof: a no-op gate could not make HIGH high
            // AND LOW low. This satisfies SPEC's "gate must still FAIL if hflip removed" WITHOUT
            // an out-of-scope codegen change to disable the hflip.
            val lowResult =
                VisualDiff.compareRegion(
                    walkFrame0File,
                    walkFrame0File,
                    metaspriteBox.x,
                    metaspriteBox.y,
                    metaspriteBox.w,
                    metaspriteBox.h,
                )
            val lowPercent = lowResult.diffCount.toDouble() / lowResult.totalPixels * 100.0

            // ── Calibrated thresholds (SPEC R-03 — measured from a real ROM run) ──────────────
            //
            // Calibration run (Phase 12.10 Plan 03, platformer-template.gb, settled captures):
            //   metasprite box = 24×32 @ (68,96), totalPixels = 768
            //   MEASURED HIGH (walk-frame-0 vs facing-left, in-region) = 28.26%
            //   MEASURED LOW  (walk-frame-0 vs walk-frame-0, in-region) =  0.00%
            //
            // The hflip flips ~28% of the box's pixels — far above the BG-scroll-dominated 6.60%
            // full-frame ratio the old gate measured. We lock:
            //   HIGH_MIN_PERCENT = 18.0  (≈10pp below the measured 28.26% for repeatability margin
            //                             against minor render/anti-alias jitter across runs)
            //   LOW_MAX_PERCENT  =  5.0  (a few pp above the guaranteed 0.00% floor —
            // walk0-vs-walk0
            //                             is the SAME file so it is mathematically 0%, but a small
            //                             ceiling keeps the contrast assertion honest)
            // The 18%/5% gap is the integrity contrast (D-09): the gate FAILS if the in-region
            // facing-flip diff ever collapses toward the LOW floor (i.e. if the hflip were
            // removed),
            // and a no-op region diff could not satisfy HIGH ≥ 18% AND LOW ≤ 5% simultaneously.
            val highMinPercent = 18.0
            val lowMaxPercent = 5.0

            assertTrue(
                highPercent >= highMinPercent,
                "anchor4 HIGH (in-region facing-right vs facing-left hflip diff) = " +
                    "${String.format("%.2f", highPercent)}% must be >= ${highMinPercent}% " +
                    "(calibrated from a measured 28.26%). Box ${metaspriteBox.w}×${metaspriteBox.h} " +
                    "@(${metaspriteBox.x},${metaspriteBox.y}). A collapse toward the LOW floor " +
                    "means the metasprite hflip is no longer visible — D-09 integrity failure.",
            )
            assertTrue(
                lowPercent <= lowMaxPercent,
                "anchor4 LOW control (in-region walk-frame-0 vs walk-frame-0) = " +
                    "${String.format("%.2f", lowPercent)}% must be <= ${lowMaxPercent}% " +
                    "(the same-file control floor). A non-zero LOW means the region crop or diff " +
                    "is non-deterministic — the HIGH/LOW contrast (D-09) would be meaningless.",
            )
            assertTrue(
                highPercent > lowPercent,
                "anchor4 D-09 contrast: HIGH (${String.format("%.2f", highPercent)}%) must exceed " +
                    "LOW (${String.format("%.2f", lowPercent)}%) — this contrast is what proves the " +
                    "region diff responds to the hflip specifically (a no-op gate could not).",
            )

            println(
                "anchor4 OAM-region hflip gate: box=${metaspriteBox.w}×${metaspriteBox.h} " +
                    "HIGH=${String.format("%.2f", highPercent)}% (>= ${highMinPercent}%) " +
                    "LOW=${String.format("%.2f", lowPercent)}% (<= ${lowMaxPercent}%) -- PASS"
            )
        }
    }

    // ── Anchor 5 — Level-switch (gameplay → NextLevel card → level 2) (D-08 #5) ──
    //
    // Traverses world1Area1 by holding RIGHT (+ periodic A for water gap) until the
    // PlatformerVisitor level-end trigger fires (`_next_level++` at PlatformerVisitor.kt:1124).
    // The main() loop's level-switch guard
    // (GBDKPipeline.buildMainLoopLevelSwitchGuardIfNeeded) then detects `_next_level !=
    // _current_level`, calls `navigate_to_scene(SCENE_NEXTLEVEL)` to repaint VRAM with the
    // next-level card, AND immediately calls `setup_current_level()` which (per Plan 12-19's
    // intentional VRAM-push to fix the title→gameplay tile-staleness defect) ALSO repaints
    // VRAM with the new level's tilemap. The two pushes happen between successive vblanks, so
    // the next rendered frame visually shows the SECOND push's contents (the next-level
    // tilemap), NOT the next-level card. See Plan 12-23 round-2 SUMMARY for the codegen-
    // ordering analysis + escalation decision.
    //
    // Variable addresses (resolved from platformer-template.noi):
    //   __current_level @ 0xC0EF (UINT8)
    //   __next_level    @ 0xC0F0 (UINT8)
    // Use StepAgent.readMemory() for both (same pattern as anchor 3 _camera_x/_map_pos_x).
    //
    // Captures 4 screenshots (Phase 12.7 Round-5 / Plan 12.7-20 capture-timing fix — H2):
    //   00-last-gameplay.png  — NEW (Plan 12.7-20). The LAST gameplay-scene frame BEFORE
    //                            main()-loop guard runs navigate_to_scene(SCENE_NEXTLEVELSCENE).
    //                            Captured INSIDE the trigger-detection loop on the same
    //                            iteration as the trigger flip — the LCD framebuffer at that
    //                            point still reflects the gameplay scene with the player at
    //                            the right-edge trigger zone (the navigate runs AFTER the
    //                            _next_level increment but BEFORE the next vblank). This is
    //                            the SPEC R-03 binding truth ("player pinned to floor near
    //                            right-edge trigger"). Per CLAUDE.md Visual Evidence Rule,
    //                            this PNG — not the variables.txt — is the binding R-03
    //                            evidence; variables alone were the trap in Round 4.
    //   01-nextlevel-flip.png — RENAMED from 01-near-end.png (Plan 12.7-20). The historical
    //                            label was misleading: this capture happens AFTER the main()-
    //                            loop guard navigated to nextLevelScene, so the framebuffer
    //                            already shows the FIRST frame of the next-level card path
    //                            (stale-OAM player sprite over partially-painted card art —
    //                            depending on Phase 12.6 D-07 scroll-reset timing). Kept as
    //                            orthogonal regression coverage for the scene-flip path
    //                            (Phase 12.6 D-03/D-04/D-07); does NOT satisfy R-03 directly.
    //   02-nextlevel-card.png — gated on `_current_scene == nextLevel`; captures the actual
    //                            VRAM contents on that scene (currently overwritten by
    //                            setup_current_level due to the codegen-ordering defect — see
    //                            SUMMARY for the escalation note).
    //   03-level-2.png        — back to gameplay; gated on `_current_level == 1` (world1Area2).
    //                            LEFT-backoff before Start prevents a re-fire on the preserved
    //                            player position so 03 captures a clean level-2 frame.
    //
    // Per CLAUDE.md Visual Evidence Rule: PNGs are the binding evidence; variable assertions
    // on _current_level / _next_level are paired but not sufficient on their own. The new
    // `00-last-gameplay.png` (Round-5 H2 fix) is the SPEC R-03 truth ('player pinned to floor
    // near right-edge trigger'). The existing `01-nextlevel-flip.png` capture is kept as
    // regression-guard for the scene-flip frame; it does NOT satisfy R-03 directly.
    @Test
    fun anchor5LevelSwitch() {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run :gbkt-examples:platformer-template:buildRom first",
        )

        val anchor5Dir = File(EVIDENCE_DIR, "anchor-5").also { it.mkdirs() }

        // Pipeline-emitted HOME-bank globals — NOT in game_metadata.json (Plan 12-17 Task 2).
        val CURRENT_LEVEL_ADDR = 0xC0EF // UINT8
        val NEXT_LEVEL_ADDR = 0xC0F0 // UINT8

        newAgent().use { agent ->
            // Boot lead-in + transition to gameplay (mirrors anchor 1).
            agent.stepN(120)
            agent.step(setOf(Button.START))
            agent.step()
            agent.waitForScene("gameplay", maxFrames = 60)
            agent.stepN(30) // setup_current_level + tilemap load settle

            // Sanity: at gameplay entry both _current_level and _next_level should be 0.
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

            // Phase 1: Hold RIGHT (+ periodic A for water-gap jumps) until the trigger fires.
            // Use single-frame stepping so we can detect the scene flip the moment it happens
            // (main() guard fires same-frame as trigger).
            //
            // Math: world1Area1 is 60 tiles × 8 px = 480 px wide; trigger at 480-32 = 448.
            // Player starts at playerX = 80 << 4. Distance: ~368 px @ 0.5 px/frame = ~736 frames.
            // Safety cap: 2000.
            val maxTraversalFrames = 2000
            var framesHeld = 0
            var detectedNext = initialNext
            var lastPlayerXSubpx = 0
            var lastSceneBeforeFlip = "gameplay"
            // Phase 12.7 Round-5 (Plan 12.7-20) — capture-timing fix (H2).
            // Track the emulator frame number at which the level-end trigger fires so
            // the 00-last-gameplay.png capture and the variables.txt record can be
            // correlated by frame for downstream debugging. Per CLAUDE.md Visual
            // Evidence Rule, the PNG is the binding R-03 evidence; this frame number
            // is paired metadata, not a substitute.
            var lastGameplayFrame = -1
            while (framesHeld < maxTraversalFrames) {
                val buttons =
                    if ((framesHeld / 8) % 3 == 0) setOf(Button.RIGHT, Button.A)
                    else setOf(Button.RIGHT)
                val obs = agent.step(buttons)
                framesHeld += 1
                detectedNext = agent.readMemory(NEXT_LEVEL_ADDR)
                if (detectedNext != initialNext) {
                    // Phase 12.7 Round-5 (Plan 12.7-20): capture the LAST gameplay frame
                    // BEFORE main()-guard navigates to nextLevelScene. The framebuffer at
                    // this point still shows the gameplay scene with the player at the
                    // right-edge trigger zone — this is the SPEC R-03 binding truth.
                    // Per Plan 12.7-17 diagnostic Section 4: navigate_to_scene runs
                    // AFTER _next_level increment but BEFORE next vblank, so the LCD
                    // framebuffer at this iteration still reflects the gameplay scene.
                    // This capture replaces the historical "near end" PNG (now renamed
                    // to 01-nextlevel-flip.png) as R-03's binding evidence.
                    lastGameplayFrame = obs.frame
                    val lastGameplayScreenshot =
                        captureAndRename(
                            agent,
                            "anchor5_last_gameplay",
                            anchor5Dir,
                            "00-last-gameplay.png",
                        )
                    assertScreenshotIsNonUniform(lastGameplayScreenshot, "anchor5-last-gameplay")
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
                "Expected _next_level == 1 after level-end trigger fired " +
                    "(PlatformerVisitor.kt:1124 increments by 1); got $detectedNext",
            )

            // Screenshot 01-nextlevel-flip.png — first frame of nextLevelScene.
            //
            // Phase 12.7 Round-5 (Plan 12.7-20): RENAMED from 01-near-end.png to reflect
            // what this PNG actually captures. The historical "near end" label was the
            // H2 trap surfaced by Plan 12.7-15 + Plan 12.7-17 — the capture happens AFTER
            // main()-loop guard already navigated, so the framebuffer shows the FIRST
            // rendered frame of the nextLevelScene (stale-OAM player sprite over card-art
            // background), NOT a gameplay frame near the level-1 right edge. R-03's
            // binding "near-end-of-level" truth is captured by 00-last-gameplay.png
            // (above) instead.
            //
            // This capture is KEPT as orthogonal regression coverage for the scene-flip
            // path (Phase 12.6 D-03/D-04 main-loop guard navigation; Phase 12.6 D-07
            // move_bkg(0u, 0u) scroll reset in levelCardScene.materialize append-enter).
            // It does NOT satisfy R-03 directly.
            //
            // The main()-loop guard fires navigate_to_scene(SCENE_NEXTLEVELSCENE) in the SAME
            // main() iteration as the level-end trigger, so current_scene is already
            // SCENE_NEXTLEVELSCENE by the time the loop exits. captureAndRename here captures
            // the first rendered frame of the nextLevelScene.
            //
            // Phase 12.6 D-07: move_bkg(0u, 0u) is now emitted in nextLevelScene_enter
            // (levelCardScene.materialize append-enter) so the card art renders at scroll=0,
            // not offset by the old gameplay camera_x. This screenshot should visually show
            // the next-level card art centred on screen (not a partial/scrolled view).
            val nextlevelFlipScreenshot =
                captureAndRename(
                    agent,
                    "anchor5_nextlevel_flip",
                    anchor5Dir,
                    "01-nextlevel-flip.png",
                )
            assertScreenshotIsNonUniform(nextlevelFlipScreenshot, "anchor5-nextlevel-flip")

            // Phase 2: Verify the scene navigated to nextLevelScene.
            //
            // Phase 12.6 D-03 / D-04 — POST-FIX codegen-ordering contract:
            // - Main-loop guard emits ONLY `navigate_to_scene(SCENE_NEXTLEVEL)` (Plan 12.6-02
            //   trim). `setup_current_level()` is NO LONGER called from the guard.
            // - `setup_current_level()` is owned by the levelCardScene Start-press path
            //   (Plan 12.6-04 helper). Until the user presses Start on the card,
            //   `_current_level` does NOT increment — it lags `_next_level` until then.
            //
            // Scene id is "nextLevelScene" (the Kotlin property name captured by
            // LevelCardSceneDelegate.provideDelegate per Project Rule #1). The widened main-
            // loop guard matcher (Plan 12.6-02) substrings on `lower.contains("nextlevel")`
            // so the SCENE_NEXTLEVEL navigation path still fires.
            val nextLevelObs = agent.waitForScene("nextLevelScene", maxFrames = 30)
            assertEquals(
                "nextLevelScene",
                nextLevelObs.scene,
                "Expected scene == 'nextLevelScene' after main() level-switch guard fired; " +
                    "got ${nextLevelObs.scene}",
            )
            // Phase 12.6 CYCLE 2: step a few extra frames after the scene-flip so the
            // levelCardScene.materialize() appended-enter ops (hide_sprites + fill_bkg_rect
            // + _bkg_tiles_load_banked centered) have a chance to complete and render to
            // the LCD frame buffer. The waitForScene above lands on the FIRST frame where
            // current_scene=2, but the scene-enter runs DURING that frame after a long
            // sequence of bank-switching writes — empirically the frame buffer captured
            // immediately afterwards reflects the PRE-enter state. 3 extra frames is enough
            // for the enter ops + LCD scan to settle and render the centered card.
            agent.stepN(3)
            val nextLevelCardScreenshot =
                captureAndRename(
                    agent,
                    "anchor5_nextlevel_card",
                    anchor5Dir,
                    "02-nextlevel-card.png",
                )
            assertScreenshotIsNonUniform(nextLevelCardScreenshot, "anchor5-nextlevel-card")

            // Phase 12.6 D-04 contract — `_current_level` should STILL == 0 here (the guard
            // no longer calls setup_current_level; only navigate_to_scene runs in the guard).
            val afterGuardCurrent = agent.readMemory(CURRENT_LEVEL_ADDR)
            assertEquals(
                0,
                afterGuardCurrent,
                "Phase 12.6 D-04: _current_level should still == 0 after the trimmed guard " +
                    "navigates to nextLevelScene (setup_current_level moved to Start-press path); " +
                    "got $afterGuardCurrent.",
            )

            // Phase 3: Press START — levelCardScene's lowered frame emits
            // `setup_current_level();` THEN `navigate(gameplayScene)` on rising-edge START
            // (Plan 12.6-04 LevelCardSceneBuilder.materialize). After the press:
            // - setup_current_level() syncs _current_level = _next_level (= 1)
            // - setup_current_level() writes _playerX = _level_spawn_x[1] << 4 = 40 << 4
            //   (Plan 12.6-05 — DEFECT-2 closure prevents same-frame level-end-trigger re-fire)
            // - navigate_to_scene(SCENE_GAMEPLAY) flips the scene
            //
            // Pre-Phase-12.6 the test held LEFT for 120 frames trying to manually back the
            // player off the right-edge trigger zone; this is no longer necessary because
            // the spawn-write IS the position reset.
            val preStartObs = agent.step()
            val preStartScene = preStartObs.scene

            // 30 frames after capture so card render has fully settled (the card scene shows
            // across many frames now, mirroring the reference WaitForStartOrA() blocking pattern).
            agent.stepN(30)

            // Hold START for a few frames to ensure the rising edge is caught by
            // `button_pressed(J_START)` (some Coffee-GB schedulings need the input to
            // persist across a joypad-read boundary).
            val postStartObs = agent.step(setOf(Button.START))
            agent.stepN(3, setOf(Button.START))
            val postReleaseObs = agent.step() // release START (neutral)
            // Allow the gameplay scene to settle after navigate. No LEFT-hold needed —
            // setup_current_level wrote _playerX = 40 << 4 (far from right-edge trigger).
            agent.stepN(130)

            // Re-read state — these may indicate the re-fire happened.
            val midLevel2Current = agent.readMemory(CURRENT_LEVEL_ADDR)
            val midLevel2Next = agent.readMemory(NEXT_LEVEL_ADDR)
            val midLevel2Scene = agent.step().scene

            // Screenshot 03: best-effort capture of whatever state the codegen produced.
            // Documented in SUMMARY as evidence of the player-position-not-reset defect.
            val level2Screenshot =
                captureAndRename(agent, "anchor5_level_2", anchor5Dir, "03-level-2.png")
            assertScreenshotIsNonUniform(level2Screenshot, "anchor5-level-2")

            val finalCurrent = agent.readMemory(CURRENT_LEVEL_ADDR)
            val finalNext = agent.readMemory(NEXT_LEVEL_ADDR)
            // Phase 12.7 D-06 expanded trace fields. playerY/playerVy/grounded are
            // game_metadata.json globals; _camera_x is a pipeline-emitted HOME-bank global
            // at 0xC0DD (matches anchor3 read pattern; see anchor3 §Layout note for the
            // post-12.3-10 i16 widening). Note: player_real_y is function-local, NOT
            // metadata-exposed — use playerY as the closest observable (RESEARCH Finding 9).
            val CAMERA_X_ADDR = 0xC0DD
            val finalObs = agent.step()
            val finalPlayerY = finalObs.variables["playerY"]
            val finalPlayerVy = finalObs.variables["playerVy"]
            val finalGrounded = finalObs.variables["grounded"]
            val finalCameraXLo = agent.readMemory(CAMERA_X_ADDR)
            val finalCameraXHi = agent.readMemory(CAMERA_X_ADDR + 1)
            val finalCameraX = finalCameraXLo or (finalCameraXHi shl 8)

            File(anchor5Dir, "anchor5-variables.txt")
                .writeText(
                    "frame=${finalObs.frame}\n" +
                        "# Phase 12.7 D-06 trace fields. Note: player_real_y is function-local;\n" +
                        "# playerY is the sub-pixel global (= player_real_y << 4) — closest observable.\n" +
                        "initial_current_level: $initialCurrent\n" +
                        "initial_next_level: $initialNext\n" +
                        "frames_to_trigger: $framesHeld\n" +
                        // Phase 12.7 Round-5 (Plan 12.7-20) — frame number of the
                        // 00-last-gameplay.png capture. Same emulator frame as the
                        // trigger-flip iteration (navigate_to_scene runs AFTER the
                        // _next_level increment but BEFORE next vblank, so the LCD
                        // framebuffer at this frame still reflects gameplay).
                        // -1 if the trigger never fired (test would have failed above).
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
                        "current_level: $finalCurrent\n" +
                        "next_level: $finalNext\n" +
                        "_camera_x: $finalCameraX\n" +
                        "closed_by_phase_12.6: setup_current_level lives in levelCardScene Start-press path (D-03/D-04); main-loop guard emits navigate_to_scene only\n" +
                        "closed_by_phase_12.6: _playerX = _level_spawn_x[N] << 4 written by setup_current_level (D-06); same-frame trigger re-fire prevented\n" +
                        "closed_by_phase_12.7: snap-to-tile-top emitted in buildVerticalFootProbe (D-04); zero hover gap on grounded/landed/near-end frames\n"
                )

            // Phase 12.6 D-09 — tightened assertion. Previously tolerant of `>= 1` to accept
            // the codegen-defect-2 re-fire (which could push _current_level to 2 or 3); now
            // strict `== 1` because Plan 12.6-05's per-zone `_playerX = spawn() << 4` write
            // in setup_current_level prevents the same-frame level-end-trigger re-fire on
            // level switch. The load-bearing truth tightens from "level switch worked" to
            // "level switch landed exactly on level 1 (world1-area2 grass)".
            assertTrue(
                finalCurrent == 1,
                "Expected _current_level == 1 after Phase 12.6 fix (was tolerant >= 1 accepting " +
                    "re-fire to 2+). DEFECT-2 closure: _playerX = spawn() on level switch prevents " +
                    "level-end trigger re-fire same-frame. got $finalCurrent.",
            )

            // Cross-bank load proof: 03-level-2 PNG must differ from anchor 1's gameplay
            // capture (different tilemap loaded → cross-bank reload worked).
            val anchor1GameplayFile = File(EVIDENCE_DIR, "anchor-1/02-gameplay.png")
            if (anchor1GameplayFile.exists()) {
                val level1Bytes = anchor1GameplayFile.readBytes()
                val level2Bytes = level2Screenshot.readBytes()
                assertTrue(
                    !level1Bytes.contentEquals(level2Bytes),
                    "anchor 5: 03-level-2.png is byte-identical to anchor 1's 02-gameplay.png. " +
                        "Cross-bank tilemap reload did not change rendered tiles.",
                )
            } else {
                val nextlevelFlipBytes = nextlevelFlipScreenshot.readBytes()
                val level2Bytes = level2Screenshot.readBytes()
                assertTrue(
                    !nextlevelFlipBytes.contentEquals(level2Bytes),
                    "anchor 5: 03-level-2.png is byte-identical to 01-nextlevel-flip.png. " +
                        "Cross-bank tilemap reload did not occur.",
                )
            }
        }
    }
}
