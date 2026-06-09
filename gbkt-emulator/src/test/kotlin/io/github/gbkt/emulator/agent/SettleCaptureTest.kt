/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.debug.DebugLogEntry
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Stub-driven RED→GREEN regression for the [StepAgent.settle] capture-timing primitive (Phase
 * 12.10, R-02).
 *
 * Models the real post-scene-transition failure: immediately after a transition the PPU has not
 * latched the new scene, so a frame-buffer read returns a stale/mid-render frame. A few frames
 * later the PPU stabilizes on the rendered frame.
 *
 * The stub's [GbEmulator.getFrameBuffer] returns DISTINCT "rendering-in-progress" buffers for the
 * first few `stepFrame` calls (D-11), then a content-equal "rendered" buffer (distinct instances,
 * per RESEARCH §Pitfall 3) for all later calls. The test proves that an immediate capture grabs the
 * stale frame while [StepAgent.settle] returns the rendered frame — the exact bug the primitive
 * fixes. This runs with NO ROM dependency (CI-safe).
 *
 * RED→GREEN note: this test does not compile against pre-Task-1 code (no `settle()` symbol); it goes
 * GREEN once the primitive exists.
 */
class SettleCaptureTest {

    @TempDir lateinit var tempDir: File

    private val pixelCount = 160 * 144

    private fun mockMemory(): MemoryAccess {
        val mem = IntArray(0x10000) { 0 }
        return object : MemoryAccess {
            override fun readByte(address: Int): Int = mem[address]

            override fun writeByte(address: Int, value: Int) {
                mem[address] = value
            }
        }
    }

    /**
     * Builds a stub emulator whose frame buffer follows a custom [frameFor] progression keyed off
     * the number of [GbEmulator.stepFrame] calls. Mirrors the canonical stub in [StepAgentTest].
     */
    private fun progressionStub(frameFor: (stepCount: Int) -> IntArray): GbEmulator =
        object : GbEmulator {
            private var _paused = true
            private var _running = false
            private var stepCount = 0

            override fun start() {
                _running = true
            }

            override fun stop() {
                _running = false
            }

            override fun pause() {
                _paused = true
            }

            override fun resume() {
                _paused = false
            }

            override fun stepFrame() {
                stepCount++
            }

            override fun setSpeed(multiplier: Float) = Unit

            override fun getFrameBuffer(): IntArray = frameFor(stepCount)

            override fun getMemory(): MemoryAccess = mockMemory()

            override fun getDebugLog(): List<DebugLogEntry> = emptyList()

            override fun isRunning(): Boolean = _running

            override fun isPaused(): Boolean = _paused

            override val isHeadless: Boolean = true
        }

    private fun makeAgent(stub: GbEmulator): StepAgent {
        val rom = File(tempDir, "test.gb").also { it.writeBytes(ByteArray(64)) }
        val sym =
            File(tempDir, "test.sym")
                .also { it.writeText("DEF _current_scene 00:C102\n") }
        val config =
            AgentSessionConfig(
                romFile = rom,
                symFile = sym,
                screenshotDir = File(tempDir, "screenshots"),
            )
        return StepAgent(config = config, stubEmulatorFactory = { stub }).also { it.start() }
    }

    @Test
    fun settleLatchesRenderedFrameNotStaleFrame() {
        // D-11 progression: 3 DISTINCT stale (mid-render) buffers, then a REPEATED rendered buffer.
        // Stale buffers are distinct instances/contents so 2-consecutive-identical does not falsely
        // latch early. The rendered buffer is content-equal across late calls but a FRESH instance
        // each call (Pitfall 3) — proves settle compares CONTENT, not identity.
        val staleBuffers =
            listOf(
                IntArray(pixelCount) { 0x000000 },
                IntArray(pixelCount) { 0x111111 },
                IntArray(pixelCount) { 0x222222 },
            )
        val renderedFill = 0xABCDEF
        val agent =
            makeAgent(
                progressionStub { stepCount ->
                    if (stepCount < staleBuffers.size) staleBuffers[stepCount]
                    else IntArray(pixelCount) { renderedFill } // fresh instance, same content
                }
            )

        // Immediate (pre-settle) capture grabs the first stale, mid-render frame.
        val immediate = agent.captureFrameBuffer()
        assertTrue(
            immediate.contentEquals(staleBuffers[0]),
            "immediate capture should be the first stale frame",
        )

        // settle() steps until 2 consecutive frame buffers are pixel-identical → rendered frame.
        val settled = agent.settle()

        // RED→GREEN proof: immediate (stale) differs from settled (rendered)...
        assertFalse(
            immediate.contentEquals(settled),
            "settled frame must differ from the stale immediate frame",
        )
        // ...and settle latched the RENDERED frame, not a mid-render one.
        assertTrue(
            settled.contentEquals(IntArray(pixelCount) { renderedFill }),
            "settled frame must equal the rendered fill",
        )

        agent.close()
    }

    @Test
    fun settleReturnsLastFrameWhenNeverStable() {
        // Best-effort cap=30 contract (D-02): a stub that NEVER stabilizes (fresh distinct buffer on
        // every call) → settle() returns the last frame and does NOT throw.
        val agent =
            makeAgent(
                progressionStub { stepCount ->
                    IntArray(pixelCount) { stepCount } // every frame differs from the previous
                }
            )

        val settled = agent.settle() // must not throw despite never stabilizing
        // After SETTLE_FRAME_CAP advances, the last frame reflects the final stepCount.
        assertEquals(
            StepAgent.SETTLE_FRAME_CAP,
            settled[0],
            "best-effort capture returns the last (cap-th) frame",
        )

        agent.close()
    }

    @Test
    fun settleComparesContentNotIdentity() {
        // The rendered buffer is a fresh instance on every call; settle must still latch it via
        // contentEquals. This guards against an identity-based stability check.
        val renderedFill = 0x445566
        var firstRendered: IntArray? = null
        val agent =
            makeAgent(
                progressionStub { stepCount ->
                    when (stepCount) {
                        0 -> IntArray(pixelCount) { 0x010101 } // initial stale frame
                        else -> // every rendered call is a fresh, content-equal instance
                        IntArray(pixelCount) { renderedFill }
                            .also { if (firstRendered == null) firstRendered = it }
                    }
                }
            )

        val settled = agent.settle()
        assertTrue(
            settled.contentEquals(IntArray(pixelCount) { renderedFill }),
            "settle latched the rendered frame by content",
        )
        // settle latched a LATER rendered instance (a different object from the first rendered
        // frame) yet still matched it — proving content-comparison, not identity-comparison.
        assertNotSame(
            firstRendered,
            settled,
            "settle latched a later fresh instance, not the first rendered one",
        )

        agent.close()
    }
}
