/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.ui

import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.debug.DebugLogEntry
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [EmulatorToolbar].
 *
 * Uses a [FakeEmulator] — a simple object-expression-style stub — to verify toolbar button state
 * logic without a real emulator or ROM file.
 *
 * These tests run headless (no display required) because they only inspect Swing component
 * properties, never call [javax.swing.JFrame.show] or [javax.swing.JFrame.setVisible].
 */
class EmulatorToolbarTest {

    /** Minimal [GbEmulator] stub that tracks calls and exposes state for assertions. */
    private class FakeEmulator : GbEmulator {
        var running = true // Starts in "running" state so pause is the first action

        var pauseCalled = false
        var resumeCalled = false
        var stepFrameCalled = false
        var lastSpeed: Float? = null

        override fun start() = Unit

        override fun stop() {
            running = false
        }

        override fun pause() {
            pauseCalled = true
            running = false
        }

        override fun resume() {
            resumeCalled = true
            running = true
        }

        override fun stepFrame() {
            stepFrameCalled = true
        }

        override fun setSpeed(multiplier: Float) {
            lastSpeed = multiplier
        }

        override fun getFrameBuffer(): IntArray = IntArray(160 * 144)

        override fun getMemory(): MemoryAccess = throw UnsupportedOperationException()

        override fun getDebugLog(): List<DebugLogEntry> = emptyList()

        override fun isRunning(): Boolean = running

        override fun isPaused(): Boolean = !running

        override val isHeadless: Boolean = true
    }

    private lateinit var fakeEmulator: FakeEmulator
    private lateinit var toolbar: EmulatorToolbar

    @BeforeEach
    fun setup() {
        fakeEmulator = FakeEmulator()
        toolbar = EmulatorToolbar(fakeEmulator)
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial pause button text is Pause`() {
        // Find the Pause button by scanning toolbar components
        val pauseButton = findButtonByText(toolbar, "Pause")
        assertEquals("Pause", pauseButton?.text, "Initial text should be 'Pause'")
    }

    @Test
    fun `initial step button is disabled`() {
        val stepButton = findButtonByText(toolbar, "Step")
        assertFalse(stepButton?.isEnabled ?: true, "Step button should be disabled initially")
    }

    @Test
    fun `initial speed selection is 1x`() {
        val speed1x = findToggleButtonByText(toolbar, "1x")
        assertTrue(speed1x?.isSelected ?: false, "1x speed toggle should be selected by default")
    }

    @Test
    fun `initial speed 2x and 4x are not selected`() {
        val speed2x = findToggleButtonByText(toolbar, "2x")
        val speed4x = findToggleButtonByText(toolbar, "4x")
        assertFalse(speed2x?.isSelected ?: true, "2x should not be selected initially")
        assertFalse(speed4x?.isSelected ?: true, "4x should not be selected initially")
    }

    // ── Pause/Resume toggle ───────────────────────────────────────────────────

    @Test
    fun `clicking pause button calls emulator pause and changes text to Resume`() {
        val pauseButton =
            findButtonByText(toolbar, "Pause") ?: error("Pause button not found in toolbar")

        // Emulator starts as running — clicking "Pause" should pause it
        pauseButton.doClick()

        assertTrue(fakeEmulator.pauseCalled, "emulator.pause() should have been called")
        assertEquals(
            "Resume",
            pauseButton.text,
            "Button text should change to 'Resume' after pause",
        )
    }

    @Test
    fun `clicking pause button enables step button`() {
        val pauseButton = findButtonByText(toolbar, "Pause") ?: error("Pause button not found")
        val stepButton = findButtonByText(toolbar, "Step") ?: error("Step button not found")

        pauseButton.doClick() // emulator is running, so this pauses it

        assertTrue(stepButton.isEnabled, "Step button should be enabled after pausing")
    }

    @Test
    fun `clicking resume button calls emulator resume and changes text to Pause`() {
        // First pause the emulator
        val pauseButton = findButtonByText(toolbar, "Pause") ?: error("Pause button not found")
        pauseButton.doClick() // Pause
        assertEquals("Resume", pauseButton.text)

        // Now resume — emulator.isRunning() returns false after pause, so clicking again resumes
        pauseButton.doClick() // Resume

        assertTrue(fakeEmulator.resumeCalled, "emulator.resume() should have been called")
        assertEquals("Pause", pauseButton.text, "Button text should return to 'Pause' after resume")
    }

    @Test
    fun `clicking resume button disables step button`() {
        val pauseButton = findButtonByText(toolbar, "Pause") ?: error("Pause button not found")
        val stepButton = findButtonByText(toolbar, "Step") ?: error("Step button not found")

        pauseButton.doClick() // Pause — step becomes enabled
        assertTrue(stepButton.isEnabled)

        pauseButton.doClick() // Resume — step should be disabled again
        assertFalse(stepButton.isEnabled, "Step button should be disabled after resuming")
    }

    // ── Step frame ────────────────────────────────────────────────────────────

    @Test
    fun `clicking step button calls emulator stepFrame`() {
        val pauseButton = findButtonByText(toolbar, "Pause") ?: error("Pause button not found")
        val stepButton = findButtonByText(toolbar, "Step") ?: error("Step button not found")

        pauseButton.doClick() // Pause to enable step
        stepButton.doClick()

        assertTrue(fakeEmulator.stepFrameCalled, "emulator.stepFrame() should have been called")
    }

    // ── Speed buttons ─────────────────────────────────────────────────────────

    @Test
    fun `clicking 2x sets emulator speed to 2_0f`() {
        val speed2x = findToggleButtonByText(toolbar, "2x") ?: error("2x button not found")
        speed2x.doClick()
        assertEquals(2.0f, fakeEmulator.lastSpeed, "2x button should call emulator.setSpeed(2.0f)")
    }

    @Test
    fun `clicking 4x sets emulator speed to 4_0f`() {
        val speed4x = findToggleButtonByText(toolbar, "4x") ?: error("4x button not found")
        speed4x.doClick()
        assertEquals(4.0f, fakeEmulator.lastSpeed, "4x button should call emulator.setSpeed(4.0f)")
    }

    @Test
    fun `clicking 1x sets emulator speed to 1_0f`() {
        // Select 2x first, then switch back to 1x
        val speed2x = findToggleButtonByText(toolbar, "2x") ?: error("2x not found")
        val speed1x = findToggleButtonByText(toolbar, "1x") ?: error("1x not found")
        speed2x.doClick()
        speed1x.doClick()
        assertEquals(1.0f, fakeEmulator.lastSpeed, "1x button should call emulator.setSpeed(1.0f)")
    }

    // ── Memory inspector callback ─────────────────────────────────────────────

    @Test
    fun `clicking Memory toggle fires onMemoryInspectorToggle callback with true`() {
        var callbackValue: Boolean? = null
        toolbar.onMemoryInspectorToggle = { callbackValue = it }

        val memButton =
            findToggleButtonByText(toolbar, "Memory") ?: error("Memory button not found")
        memButton.doClick()

        assertEquals(
            true,
            callbackValue,
            "onMemoryInspectorToggle should be invoked with true on first click",
        )
    }

    @Test
    fun `clicking Memory toggle twice fires callback with false on second click`() {
        val values = mutableListOf<Boolean>()
        toolbar.onMemoryInspectorToggle = { values += it }

        val memButton =
            findToggleButtonByText(toolbar, "Memory") ?: error("Memory button not found")
        memButton.doClick() // Select → true
        memButton.doClick() // Deselect → false

        assertEquals(listOf(true, false), values, "Memory toggle should fire true then false")
    }

    // ── Log viewer callback ───────────────────────────────────────────────────

    @Test
    fun `clicking Log toggle fires onLogViewerToggle callback with true`() {
        var callbackValue: Boolean? = null
        toolbar.onLogViewerToggle = { callbackValue = it }

        val logButton = findToggleButtonByText(toolbar, "Log") ?: error("Log button not found")
        logButton.doClick()

        assertEquals(
            true,
            callbackValue,
            "onLogViewerToggle should be invoked with true on first click",
        )
    }

    // ── updatePauseState() ────────────────────────────────────────────────────

    @Test
    fun `updatePauseState(true) sets button text to Resume and enables step`() {
        toolbar.updatePauseState(true)

        // updatePauseState uses SwingUtilities.invokeLater — pump EDT manually
        // In headless tests on the test thread, EDT processing may be on the same thread
        // or we can use invokeAndWait equivalent by flushing pending events.
        pumpEdt()

        val pauseButton = findButtonByText(toolbar, "Resume")
        val stepButton = findButtonByText(toolbar, "Step")
        assertEquals(
            "Resume",
            pauseButton?.text,
            "updatePauseState(true) should set text to 'Resume'",
        )
        assertTrue(
            stepButton?.isEnabled ?: false,
            "updatePauseState(true) should enable step button",
        )
    }

    @Test
    fun `updatePauseState(false) sets button text to Pause and disables step`() {
        // First set to paused state
        toolbar.updatePauseState(true)
        pumpEdt()

        // Now set back to running
        toolbar.updatePauseState(false)
        pumpEdt()

        val pauseButton = findButtonByText(toolbar, "Pause")
        val stepButton = findButtonByText(toolbar, "Step")
        assertEquals(
            "Pause",
            pauseButton?.text,
            "updatePauseState(false) should set text to 'Pause'",
        )
        assertFalse(
            stepButton?.isEnabled ?: true,
            "updatePauseState(false) should disable step button",
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Finds the first [javax.swing.JButton] in [toolbar] whose text matches [text]. Returns null if
     * not found.
     */
    private fun findButtonByText(toolbar: EmulatorToolbar, text: String): javax.swing.JButton? {
        for (i in 0 until toolbar.componentCount) {
            val c = toolbar.getComponent(i)
            if (c is javax.swing.JButton && c.text == text) return c
        }
        return null
    }

    /**
     * Finds the first [javax.swing.JToggleButton] in [toolbar] whose text matches [text]. Returns
     * null if not found.
     */
    private fun findToggleButtonByText(
        toolbar: EmulatorToolbar,
        text: String,
    ): javax.swing.JToggleButton? {
        for (i in 0 until toolbar.componentCount) {
            val c = toolbar.getComponent(i)
            if (c is javax.swing.JToggleButton && c.text == text) return c
        }
        return null
    }

    /**
     * Pumps all pending EDT events synchronously.
     *
     * [javax.swing.SwingUtilities.invokeAndWait] ensures all
     * [javax.swing.SwingUtilities.invokeLater] tasks posted before this call have completed by the
     * time it returns. This is safe to call from the test thread even in headless mode.
     */
    private fun pumpEdt() {
        javax.swing.SwingUtilities.invokeAndWait { /* no-op — just flushes the EDT queue */ }
    }
}
