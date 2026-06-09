/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.banks

import io.github.gbkt.emulator.agent.AgentSessionConfig
import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.StepAgent
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Assumptions

/**
 * UAT runtime tests for the Banks port.
 *
 * Plans 11-11 and 11-12 add the four UAT anchor @Test methods (scene nav, zone tilemap load, MBC5
 * cartridge byte, SRAM persistence via GBST round-trip). Plan 11-11 covers anchors 1 + 2.
 *
 * The `newAgent()` skip-guard pattern lets the test suite stay GREEN before Wave-3 builds the
 * actual ROM via `:gbkt-examples:banks:buildRom` — JUnit `Assumptions.assumeTrue` converts a
 * missing ROM into a skip rather than a failure, so this file is safe to land in CI before any ROM
 * exists.
 *
 * Per CLAUDE.md Visual Evidence Rule (and Phase 11 D-10): anchors 1 + 2 are VISUAL truths and MUST
 * capture a runtime screenshot at the climax frame. Variable assertions like `_current_scene ==
 * play` are NECESSARY but never SUFFICIENT — the PNG under
 * `.planning/phases/11-.../evidence/uat-screenshots/` is the binding evidence artifact. Phase 07.4
 * plans 14–18 verified SC-4 via variable evidence and burned 5 plans before user UAT revealed the
 * runtime ROM never rendered the target tilemap; this phase does NOT repeat that mistake for
 * anchors 1 + 2.
 */
class BanksUatTest {

    companion object {
        // Executor runs Gradle from repo root, but JVM tests for a subproject
        // resolve `user.dir` to the subproject dir (`gbkt-examples/banks/`),
        // so `../../.planning/...` climbs back to the repo root.
        private val EVIDENCE_DIR =
            File(
                "../../.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots"
            )
        private val ROM_FILE = File("build/gbkt/output/banks.gb")
        private val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
    }

    private fun newAgent(): StepAgent {
        Assumptions.assumeTrue(ROM_FILE.exists(), "banks.gb not found — run buildRom first")
        EVIDENCE_DIR.mkdirs()
        val baseConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
        val metadata =
            if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
        val agent = StepAgent(baseConfig, metadata)
        agent.start()
        return agent
    }

    /**
     * Captures a screenshot via [StepAgent.captureScreenshot] (which writes
     * `{label}_frame{frameNumber}.png` into [EVIDENCE_DIR]) and renames the file to the plan's
     * exact target path. JSON sidecar is renamed in lock-step to keep the evidence dir tidy.
     * Mirrors the helper in `SimplePhysicsUatTest.captureAndRename` (Phase 09.4 Plan 02).
     */
    private fun captureAndRename(agent: StepAgent, label: String, targetName: String): File {
        val captured = agent.captureScreenshot(label)
        val target = File(EVIDENCE_DIR, targetName)
        if (target.exists()) target.delete()
        check(captured.renameTo(target)) {
            "Failed to rename ${captured.absolutePath} -> ${target.absolutePath}"
        }
        val sidecar = File(captured.parentFile, captured.nameWithoutExtension + ".json")
        if (sidecar.exists()) {
            val targetJson = File(EVIDENCE_DIR, target.nameWithoutExtension + ".json")
            if (targetJson.exists()) targetJson.delete()
            sidecar.renameTo(targetJson)
        }
        return target
    }

    /**
     * Perceptual screenshot check — asserts that [file] is a non-uniform PNG (i.e. contains real
     * rendered content, not a blank or solid-colour frame).
     *
     * Per WR-07 (REVIEW.md) + Plan 11.1-14: the former byte-length threshold
     * `screenshotPath.length() > 100/200` was discredited because a 413-byte solid-white PNG
     * produced by the emulator before `set_bkg_data` emission passed the threshold. This helper
     * replaces that check by decoding the PNG via [ImageIO.read] and asserting:
     * 1. The image contains at least 2 distinct RGB colour values. Rationale: a uniform blank frame
     *    has 1 colour; any real tile content uses at least 2 of the Game Boy palette's 4 shades.
     *    The threshold was originally 4 (per palette size) but was relaxed to 2 after Plan 15
     *    CONTINGENCY (c) — the minimal 2-tile checker tileset legitimately renders only 2 shades,
     *    so requiring 4 would over-constrain content using a subset of the palette. The
     *    dominant-ratio gate (point 2) remains the primary anti-blank guard.
     * 2. The dominant colour covers fewer than 95% of pixels — guarding against near-blank frames
     *    where one colour occupies almost all of the frame (e.g. a 1-pixel border of content). This
     *    is the binding structural anti-blank guard.
     *
     * Aligns with CLAUDE.md Verification Methodology — Visual Evidence Rule (lines 84–119): for
     * truths phrased "X is visible on screen", a variable assertion is necessary but NEVER
     * sufficient; the PNG must contain real rendered content, proven here by perceptual analysis
     * rather than file size.
     *
     * A real-content post-11.2 banks ROM screenshot MUST PASS this check. The pre-11.2 blank
     * 413-byte PNG MUST FAIL (confirmed by executor sanity probe documented in 11.1-14-SUMMARY.md).
     *
     * @param file The PNG file to analyse. Must exist before this call.
     * @param label A short label included in all failure messages to identify which anchor failed
     *   (e.g. "anchor1-play-scene").
     * @param region Optional analysis window (x, y, w, h) in pixels. When null (default) the whole
     *   frame is analysed. When supplied, the SAME gate (>= 2 distinct colours AND dominant
     *   ratio < 0.95 — threshold UNCHANGED) is applied only to that region. Phase 15 F5/F6: the
     *   banks codegen-demo paints a 2x2 (16x16px) banked checker swatch at the top-left (tileset-only
     *   `playZone` -> 2x2 tilemap {0,1,1,0} bank-loaded from bank 2). A 16x16 swatch is <= 1.1% of the
     *   160x144 frame, so a FULL-FRAME dominant<0.95 gate is arithmetically unsatisfiable no matter
     *   how correctly the banked tilemap renders — the wrong premise. Scoping the SAME gate to the
     *   painted swatch (where the live D-03 screenshot measures dominant=0.50) proves the banked
     *   tilemap loaded and rendered, WITHOUT lowering the 0.95 threshold (see
     *   evidence/diagnosis/banks.md + evidence/banks-anchor*.png).
     */
    private fun assertScreenshotIsNonUniform(
        file: File,
        label: String,
        region: IntArray? = null,
    ) {
        val img =
            ImageIO.read(file) ?: fail("$label: file is not a valid PNG: ${file.absolutePath}")

        val x0 = region?.get(0) ?: 0
        val y0 = region?.get(1) ?: 0
        val x1 = region?.let { it[0] + it[2] } ?: img.width
        val y1 = region?.let { it[1] + it[3] } ?: img.height

        val colours = mutableSetOf<Int>()
        val histogram = mutableMapOf<Int, Int>()
        for (y in y0 until y1) {
            for (x in x0 until x1) {
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

        val totalPixels = (x1 - x0) * (y1 - y0)
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

        // Write evidence sidecar so future verifiers can audit metric values
        // without re-running the test.
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

    // ── Anchor 1 — cross-bank scene navigation (HOME→bank-1 BANKED trampoline) ──
    //
    // Press Start on the title scene; the play scene loads via the HOME-bank
    // `navigate_to_scene()` BANKED trampoline. MBC5 bank switch must resolve
    // without a "MBC5 unknown address/value" trap, and the play scene must be
    // visibly rendered on screen.
    //
    // Per CLAUDE.md Visual Evidence Rule (lines 84–119): the PNG at
    // `evidence/uat-screenshots/anchor1-play-scene.png` is the BINDING evidence.
    // The companion variable assertion (`Observation.scene == "play"`) is
    // necessary but never sufficient for the "play scene is visible" truth.
    //
    // Per WR-07 (REVIEW.md) + Plan 11.1-14: the byte-length threshold
    // `screenshotPath.length() > 100` was discredited — a 413-byte
    // solid-white PNG produced by the emulator (before `set_bkg_data` emission
    // shipped in Phase 11.2) passed the threshold, making the assertion
    // non-distinguishing. Replaced by `assertScreenshotIsNonUniform` which
    // decodes the PNG via ImageIO and asserts >= 2 distinct RGB colour values +
    // dominant-colour ratio < 95% (threshold relaxed from 4 to 2 per Plan 15
    // CONTINGENCY (c) — the 2-tile checker legitimately uses 2 palette shades).
    // Aligns with CLAUDE.md Visual Evidence Rule.
    @Test
    fun `anchor 1 cross-bank scene navigation`() {
        newAgent().use { agent ->
            // Boot through title scene — Banks.kt's `start = titleScene` puts the
            // emulator on title after a few PPU frames.
            agent.stepN(10)

            // Press Start on title — HOME→bank-1 BANKED `play_enter()` trampoline
            // fires through the navigate_to_scene dispatch. Edge-triggered
            // (single-frame press) per Banks.kt's `whenever(buttons.start.pressed)`.
            agent.step(setOf(Button.START))

            // Wait for the cross-bank BANKED play_enter() call to land —
            // StepAgent.Observation.scene reads `_current_scene` against the
            // scene-id map from game_metadata.json. 60 frames is generous;
            // the trampoline is single-frame in practice.
            val obs = agent.waitForScene("play", maxFrames = 60)

            // Step a couple PPU flush frames so the first rendered post-enter
            // frame is the one we screenshot.
            agent.stepN(2)

            // Visual evidence — the screenshot is the binding artifact per
            // CLAUDE.md Visual Evidence Rule. Without this PNG, a green
            // assertion below is insufficient (Phase 07.4 SC-4 history).
            val screenshotPath =
                captureAndRename(
                    agent,
                    label = "anchor1_play_scene",
                    targetName = "anchor1-play-scene.png",
                )
            assertTrue(
                screenshotPath.exists(),
                "anchor1 screenshot must exist after capture: ${screenshotPath.absolutePath}",
            )
            // F5/F6: banks paints a 2x2 (16x16px) banked checker swatch at the top-left
            // (tileset-only playZone). Scope the SAME non-uniformity gate (0.95 unchanged) to
            // that painted region — proves the banked tilemap rendered without a full-frame
            // premise the 16x16 swatch can never satisfy. See evidence/diagnosis/banks.md.
            assertScreenshotIsNonUniform(
                screenshotPath,
                "anchor1-play-scene",
                region = intArrayOf(0, 0, 16, 16),
            )

            // Variable evidence (secondary; the PNG above is primary). Proves the
            // cross-bank BANKED trampoline contract at the metadata layer:
            // _current_scene transitioned title→play within 60 frames of Start.
            assertEquals(
                "play",
                obs.scene,
                "After Start on title, current scene must be 'play' " +
                    "(cross-bank BANKED trampoline contract)",
            )
        }
    }

    // ── Anchor 4 — SRAM persistence via GBST round-trip (SavestateManager SRAM extension) ──
    //
    // Saves state after triggering the Banks.kt save system (SELECT writes _saveFlag to SRAM),
    // then mutates SRAM[0xA000] to 99 between save and load, then restores the snapshot and
    // verifies the byte returned to its pre-save value.
    //
    // The mutate-between-save-load step is CRITICAL (per RESEARCH §Pitfall 5): without it
    // the test trivially passes even if SavestateManager never touches SRAM (pre==post==0
    // in a fresh emulator). By writing 99 post-save and asserting post-load matches pre-save,
    // we prove that loadState ACTUALLY restored the SRAM region from the snapshot.
    //
    // Hardened per REVIEW CR-01 + Plan 11.1-10 (SavestateManager ENABLE_RAM bracket) +
    // Plan 11.1-11 (this test hardening):
    //
    // Recipe (non-tautological probe):
    //   ENABLE_RAM → mid-mutation (writeMemory 0xA000=99) → mid-landed probe →
    //   loadState → assert restored matches pre AND assert post != mid-sentinel (99).
    //
    // The explicit writeMemory(0x0000, 0x0A) before the mid-mutation guarantees the
    // mutation actually lands in real MBC5 SRAM (Banks.kt's save_game_saves leaves the
    // MBC in DISABLE_RAM state; without the explicit enable, the mutation write is
    // silently dropped, making both pre and post reads return the same untouched value
    // — a false-green tautology, as documented in CR-01).
    //
    // The assertNotEquals(99.toByte(), postBytes[0]) is the non-tautological sentinel:
    // it proves loadState ACTUALLY overwrote the mid-mutation sentinel, not that both
    // the mid-write and the loadState write were silently dropped.
    //
    // Note: StepAgent.saveState / loadState take a File (not a label String). A temp
    // file under EVIDENCE_DIR is used so the artifact is also reviewable on disk.
    @Test
    fun `anchor 4 SRAM persistence via GBST round-trip`() {
        newAgent().use { agent ->
            agent.stepN(10)
            agent.waitForScene("play", maxFrames = 120)

            // Trigger SaveDataBuilder write path: SELECT emits triggerSystem("saves") in Banks.kt
            // which calls save_game_saves -> ENABLE_RAM ; sram[...] = _saveFlag ; DISABLE_RAM.
            agent.step(setOf(Button.SELECT))
            agent.stepN(2) // let the save write settle in SRAM

            // Capture pre-save SRAM bytes (4 bytes from 0xA000-0xA003).
            // StepAgent.readMemory(addr) returns a single byte — collect 4 individually.
            val preBytes = ByteArray(4) { i -> agent.readMemory(0xA000 + i).toByte() }

            // Snapshot emulator state to a file (StepAgent.saveState takes a File).
            // Use EVIDENCE_DIR so the artifact is reviewable alongside other UAT evidence.
            val stateFile = File(EVIDENCE_DIR, "anchor4-pre.gbst")
            agent.saveState(stateFile)

            // Per CR-01 / Plan 11.1-11: explicit ENABLE_RAM so the mid-mutation writeMemory
            // actually lands in real SRAM (Banks.kt's save_game_saves leaves the MBC in
            // DISABLE_RAM state; without this, the mutation write is silently dropped by
            // the MBC5 ramWriteEnabled gate, making the test a false-green tautology).
            agent.writeMemory(0x0000, 0x0A)

            // Mutate SRAM[0xA000] between save and load — THE crucial step per RESEARCH §Pitfall 5.
            // Without this, the test passes by accident even when SavestateManager doesn't capture
            // SRAM.
            agent.writeMemory(0xA000, 99)

            // Capture mid-mutation bytes to prove the ENABLE_RAM + write actually landed.
            // This is the proof-of-mutation sentinel: midBytes[0] MUST equal 99 for the
            // test to be a genuine probe. If it does not, the MBC5 gate did not honour the
            // ENABLE_RAM write, or the StepAgent readMemory/writeMemory shape changed.
            val midBytes = ByteArray(4) { i -> agent.readMemory(0xA000 + i).toByte() }
            assertEquals(
                99.toByte(),
                midBytes[0],
                "Mid-mutation MUST land in SRAM after explicit ENABLE_RAM — if it does not, " +
                    "the MBC5 RAM gate is not honouring the enable write, or the StepAgent " +
                    "readMemory/writeMemory shape changed (CR-01 / Plan 11.1-11).",
            )

            // Restore snapshot — if SavestateManager captured SRAM, the byte reverts to pre-save
            // value.
            agent.loadState(stateFile)

            // Capture post-load SRAM bytes.
            val postBytes = ByteArray(4) { i -> agent.readMemory(0xA000 + i).toByte() }

            // Evidence-before-assert: write the evidence text BEFORE the assertion fires so
            // a RED test still produces a reviewable artifact on disk.
            File(EVIDENCE_DIR, "anchor4-sram-persistence.txt")
                .writeText(
                    "pre: ${preBytes.toList()}\n" +
                        "mid: ${midBytes.toList()}\n" +
                        "mid_landed: ${midBytes[0] == 99.toByte()}\n" +
                        "loadState_overwrote_mid: ${postBytes[0] != 99.toByte()}\n" +
                        "post: ${postBytes.toList()}\n" +
                        "match: ${preBytes.contentEquals(postBytes)}\n"
                )

            assertContentEquals(
                preBytes,
                postBytes,
                "SRAM round-trip MUST preserve byte values mid-cycle mutation. " +
                    "pre=${preBytes.toList()} post=${postBytes.toList()}. " +
                    "Root cause if FAIL: SavestateManager does NOT capture 0xA000-0xBFFF (D-06 narrow fix).",
            )

            // Non-tautological sentinel (CR-01 / Plan 11.1-11): the post-load value MUST NOT
            // be 99 (the mid-mutation sentinel). If it still equals 99, then
            // SavestateManager.load()
            // did not actually write to SRAM — the mid-mutation survived the load, proving the
            // round-trip is a no-op on a real MBC5 (CR-01 false-green regression).
            assertNotEquals(
                99.toByte(),
                postBytes[0],
                "loadState MUST overwrite the mid-mutation sentinel value 99 with the saved " +
                    "snapshot. If postBytes[0] still equals 99, then SavestateManager.load() " +
                    "is not actually writing to SRAM (CR-01 false-green regression).",
            )
        }
    }

    // ── Anchor 2 — banked zone tilemap visible (SWITCH_ROM-from-HOME wrapper) ──
    //
    // Within the play scene, the banked zone tilemap is loaded via the HOME-bank
    // `_bkg_tiles_load_banked` SWITCH_ROM wrapper (Plan 07.4-30 path). The
    // checker tilemap pattern (`res/tiles/checker.png`, allocated to bank 2 by
    // `allocateZoneBanks`) must be visibly rendered on the background layer.
    //
    // Per CLAUDE.md Visual Evidence Rule (lines 84–119): this is the canonical
    // "tilemap is visible" anchor. A variable assertion like
    // `_current_tileset_id == 1` is INSUFFICIENT — that exact pattern misfired
    // in Phase 07.4 round-2 and the SC stayed GREEN while the runtime ROM never
    // rendered the track. The PNG under
    // `evidence/uat-screenshots/anchor2-tilemap.png` is the BINDING evidence.
    //
    // Per WR-07 (REVIEW.md) + Plan 11.1-14: the byte-length threshold
    // `screenshotPath.length() > 200` was discredited — a 413-byte solid-white
    // PNG produced by the emulator before `set_bkg_data` emission landed in
    // Phase 11.2 passed the threshold regardless of whether any tilemap pixels
    // were actually rendered. Replaced by `assertScreenshotIsNonUniform` which
    // decodes the PNG via ImageIO and asserts >= 4 distinct RGB colour values +
    // dominant-colour ratio < 95%, per CLAUDE.md Visual Evidence Rule.
    @Test
    fun `anchor 2 banked zone tilemap visible`() {
        newAgent().use { agent ->
            agent.stepN(10) // boot to title
            agent.step(setOf(Button.START)) // title -> play
            agent.waitForScene("play", maxFrames = 60)

            // Step additional frames so the SWITCH_ROM-from-HOME wrapper
            // (`_bkg_tiles_load_banked` → SWITCH_ROM(2) → set_bkg_tiles(...) →
            // SWITCH_ROM(1)) has completed and the checker tilemap pixels are
            // in VRAM by the time we capture. 30 frames is comfortably past
            // the zone-load PPU settle.
            agent.stepN(30)

            // Visual evidence — variable-state assertions (e.g. on
            // `_current_tileset_id` or `_current_zone`) are INSUFFICIENT for
            // "tilemap pixels are visible" per the CLAUDE.md Visual Evidence
            // Rule. The PNG IS the evidence.
            val screenshotPath =
                captureAndRename(
                    agent,
                    label = "anchor2_tilemap",
                    targetName = "anchor2-tilemap.png",
                )
            assertTrue(
                screenshotPath.exists(),
                "anchor2 screenshot must exist: ${screenshotPath.absolutePath}",
            )
            // F5/F6: the banked zone checker is a 2x2 (16x16px) swatch at the top-left. Scope the
            // SAME non-uniformity gate (0.95 unchanged) to the painted region — within the swatch
            // the live D-03 capture measures dominant=0.50, proving the SWITCH_ROM-from-HOME banked
            // tilemap loaded and rendered. See evidence/diagnosis/banks.md + evidence/banks-anchor2*.
            assertScreenshotIsNonUniform(
                screenshotPath,
                "anchor2-tilemap",
                region = intArrayOf(0, 0, 16, 16),
            )
        }
    }
}
