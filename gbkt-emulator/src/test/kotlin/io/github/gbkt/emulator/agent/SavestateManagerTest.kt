/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.CoffeeGbEmulator
import io.github.gbkt.emulator.EmulatorConfig
import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.debug.DebugLogEntry
import java.io.File
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SavestateManagerTest {

    @TempDir lateinit var tempDir: File

    // Banks ROM path: test runs with user.dir = gbkt-emulator/, so ../gbkt-examples/banks/...
    // resolves
    // relative to the module directory (matching the established convention in
    // RealEmulatorAgentTest).
    private val BANKS_ROM = File("../gbkt-examples/banks/build/gbkt/output/banks.gb")

    /**
     * Creates a mock GbEmulator backed by a flat byte array.
     *
     * @param isPaused Whether the emulator reports as paused.
     * @param initMemory Initial memory patches to apply (address to value).
     */
    private fun mockEmulator(
        isPaused: Boolean = true,
        initMemory: Map<Int, Int> = emptyMap(),
    ): Pair<GbEmulator, IntArray> {
        val mem = IntArray(0x10000) { 0 }
        for ((addr, value) in initMemory) {
            mem[addr] = value
        }
        val emulator =
            object : GbEmulator {
                override fun start() = Unit

                override fun stop() = Unit

                override fun pause() = Unit

                override fun resume() = Unit

                override fun stepFrame() = Unit

                override fun setSpeed(multiplier: Float) = Unit

                override fun getFrameBuffer(): IntArray = IntArray(160 * 144)

                override fun getMemory(): MemoryAccess =
                    object : MemoryAccess {
                        override fun readByte(address: Int): Int = mem[address]

                        override fun writeByte(address: Int, value: Int) {
                            mem[address] = value
                        }
                    }

                override fun getDebugLog(): List<DebugLogEntry> = emptyList()

                override fun isRunning(): Boolean = !isPaused

                override fun isPaused(): Boolean = isPaused

                override val isHeadless: Boolean = true
            }
        return emulator to mem
    }

    @Test
    fun `saved file has correct total size`() {
        val (emulator, _) = mockEmulator()
        val file = File(tempDir, "state.gbst")
        SavestateManager.save(emulator, file)
        // 4 (magic) + 8192 (SRAM) + 8192 (WRAM) + 160 (OAM) + 127 (HRAM) = 16675 bytes
        assertEquals(16675L, file.length())
    }

    @Test
    fun `saved file starts with GBS2 magic bytes`() {
        val (emulator, _) = mockEmulator()
        val file = File(tempDir, "state.gbst")
        SavestateManager.save(emulator, file)
        val magic = file.readBytes().take(4).toByteArray()
        assertArrayEquals(
            byteArrayOf('G'.code.toByte(), 'B'.code.toByte(), 'S'.code.toByte(), '2'.code.toByte()),
            magic,
        )
    }

    @Test
    fun `round-trip restores WRAM bytes correctly`() {
        val wramPatch = mapOf(0xC000 to 0x42, 0xC100 to 0xFF, 0xDFFF to 0x7E)
        val (saveEmulator, _) = mockEmulator(initMemory = wramPatch)
        val file = File(tempDir, "state.gbst")
        SavestateManager.save(saveEmulator, file)

        // Load into a fresh emulator
        val (loadEmulator, loadMem) = mockEmulator()
        SavestateManager.load(loadEmulator, file)

        assertEquals(0x42, loadMem[0xC000])
        assertEquals(0xFF, loadMem[0xC100])
        assertEquals(0x7E, loadMem[0xDFFF])
    }

    @Test
    fun `round-trip restores OAM bytes correctly`() {
        val oamPatch = mapOf(0xFE00 to 0x10, 0xFE10 to 0x20, 0xFE9F to 0x30)
        val (saveEmulator, _) = mockEmulator(initMemory = oamPatch)
        val file = File(tempDir, "state.gbst")
        SavestateManager.save(saveEmulator, file)

        val (loadEmulator, loadMem) = mockEmulator()
        SavestateManager.load(loadEmulator, file)

        assertEquals(0x10, loadMem[0xFE00])
        assertEquals(0x20, loadMem[0xFE10])
        assertEquals(0x30, loadMem[0xFE9F])
    }

    @Test
    fun `round-trip restores HRAM bytes correctly`() {
        val hramPatch = mapOf(0xFF80 to 0xAA, 0xFFFE to 0xBB)
        val (saveEmulator, _) = mockEmulator(initMemory = hramPatch)
        val file = File(tempDir, "state.gbst")
        SavestateManager.save(saveEmulator, file)

        val (loadEmulator, loadMem) = mockEmulator()
        SavestateManager.load(loadEmulator, file)

        assertEquals(0xAA, loadMem[0xFF80])
        assertEquals(0xBB, loadMem[0xFFFE])
    }

    // -------------------------------------------------------------------------
    // SRAM round-trip — GREEN (SavestateManager captures 0xA000–0xBFFF per
    // SEED-016 NARROW fix implemented in Plan 11.1-03)
    //
    // This test mirrors `round-trip restores WRAM bytes correctly` beat-for-beat
    // using SRAM sentinel bytes. The three sentinel addresses cover:
    //   0xA000 — start of SRAM (SRAM bank 0 base)
    //   0xA800 — middle of SRAM
    //   0xBFFF — end of 8KB SRAM (bank 0 ceiling)
    // -------------------------------------------------------------------------

    @Test
    fun `round-trip restores SRAM bytes correctly`() {
        val sramPatch = mapOf(0xA000 to 0x42, 0xA800 to 0xFF, 0xBFFF to 0x7E)
        val (saveEmulator, _) = mockEmulator(initMemory = sramPatch)
        val file = File(tempDir, "state.gbst")
        SavestateManager.save(saveEmulator, file)

        // Load into a fresh emulator
        val (loadEmulator, loadMem) = mockEmulator()
        SavestateManager.load(loadEmulator, file)

        assertEquals(0x42, loadMem[0xA000])
        assertEquals(0xFF, loadMem[0xA800])
        assertEquals(0x7E, loadMem[0xBFFF])
    }

    @Test
    fun `save throws when emulator is not paused`() {
        val (emulator, _) = mockEmulator(isPaused = false)
        val file = File(tempDir, "state.gbst")
        assertThrows(IllegalArgumentException::class.java) { SavestateManager.save(emulator, file) }
    }

    @Test
    fun `load throws when emulator is not paused`() {
        // First save a valid file with a paused emulator
        val (saveEmulator, _) = mockEmulator(isPaused = true)
        val file = File(tempDir, "state.gbst")
        SavestateManager.save(saveEmulator, file)

        // Now try to load into a running emulator
        val (runningEmulator, _) = mockEmulator(isPaused = false)
        assertThrows(IllegalArgumentException::class.java) {
            SavestateManager.load(runningEmulator, file)
        }
    }

    @Test
    fun `load throws on invalid magic bytes`() {
        val file = File(tempDir, "bad.gbst")
        file.writeBytes(ByteArray(8483) { 0 }) // all zeros, wrong magic
        val (emulator, _) = mockEmulator()
        assertThrows(IllegalArgumentException::class.java) { SavestateManager.load(emulator, file) }
    }

    // -------------------------------------------------------------------------
    // Emulator-backed SRAM round-trip — tests the MBC5 ENABLE_RAM bracket in
    // SavestateManager.load() using a real CoffeeGbEmulator (not the IntArray mock).
    //
    // Existing mock-backed tests bypass MBC5 entirely (the IntArray accepts any
    // writeByte without gating). This test uses a real CoffeeGB emulator so the
    // MBC5 ramWriteEnabled gate is in play. Without Plan 10's ENABLE_RAM bracket
    // in SavestateManager.load(), the assertNotEquals(99, restored) line would
    // fail — the mid-clobber sentinel would survive because SRAM writes are
    // silently dropped by the MBC5 gate.
    //
    // Uses Assumptions.assumeTrue to skip when the banks ROM is absent so CI
    // stays GREEN before the ROM has been built (identical skip-guard pattern to
    // BanksUatTest). Run :gbkt-examples:banks:buildRom to enable this test.
    //
    // References: REVIEW CR-01, Phase 11.1 gap-closure plans 11.1-10 (ENABLE_RAM
    // bracket source fix) and 11.1-12 (this JVM-tier regression guard).
    // See also: BanksUatTest anchor 4 (UAT-tier twin of this test).
    // -------------------------------------------------------------------------

    /**
     * Boots a real [CoffeeGbEmulator] from the banks ROM, pauses it, and returns the emulator ready
     * for memory access and savestate operations.
     *
     * Mirrors the pattern used in [RealEmulatorAgentTest] via [AgentDebugSession]. Runs 10 frames
     * to allow MBC5 to settle past the init phase before any SRAM operations.
     *
     * Caller is responsible for calling [GbEmulator.stop] in a finally block.
     */
    private fun bootBanksEmulatorPaused(): GbEmulator {
        val emulator = CoffeeGbEmulator(EmulatorConfig(romFile = BANKS_ROM, headless = true))
        emulator.start()
        emulator.pause()
        repeat(10) { emulator.stepFrame() }
        check(emulator.isPaused()) { "Emulator did not pause as expected" }
        return emulator
    }

    @Test
    fun `emulator-backed SRAM round-trip restores bytes after mid-mutation`() {
        /**
         * Non-tautological probe of REVIEW CR-01 / Phase 11.1-10 ENABLE_RAM bracket.
         *
         * Existing mock-backed tests bypass MBC5 entirely. This test uses a real CoffeeGB emulator
         * so the MBC5 ramWriteEnabled gate is in play. Without Plan 10's ENABLE_RAM bracket in
         * SavestateManager.load(), the assertNotEquals(99, restoredByte) line would fail — the
         * mid-clobber sentinel would survive because load() drops SRAM writes when the gate is
         * closed (the state Banks.kt's save_game_saves leaves behind).
         *
         * Recipe:
         * 1. Boot real CoffeeGbEmulator from banks.gb (MBC5 cartridge), settle 10 PPU frames.
         * 2. ENABLE_RAM: writeByte(0x0000, 0x0A) — opens MBC5 ramWriteEnabled gate.
         * 3. Write sentinel 0x42 to SRAM at 0xA000, verify the write landed (precondition).
         * 4. DISABLE_RAM: writeByte(0x0000, 0x00) — mirrors state save_game_saves leaves behind.
         * 5. SavestateManager.save() — captures SRAM with sentinel in place.
         * 6. ENABLE_RAM again, clobber SRAM[0xA000] with mid-mutation sentinel 99, DISABLE_RAM.
         * 7. SavestateManager.load() — invokes the Plan-10 bracketed restore.
         * 8. Assert restored byte == 0x42 (pre-save value) AND != 99 (mid-clobber sentinel).
         *
         * The test SKIPS (not FAILS) when the banks ROM is absent — CI-safe per Assumptions.
         *
         * References: REVIEW CR-01, Phase 11.1-10 (ENABLE_RAM fix), Phase 11.1-12 (this test).
         */
        Assumptions.assumeTrue(
            BANKS_ROM.exists(),
            "banks.gb not found — run :gbkt-examples:banks:buildRom first",
        )

        val stateFile = File(tempDir, "round-trip.gbst")
        val emulator = bootBanksEmulatorPaused()
        try {
            val memory = emulator.getMemory()

            // Step 2: ENABLE_RAM — open MBC5 ramWriteEnabled gate
            memory.writeByte(0x0000, 0x0A)

            // Step 3: Write sentinel 0x42 to SRAM[0xA000]
            memory.writeByte(0xA000, 0x42)
            val sentinel = memory.readByte(0xA000)
            assertEquals(
                0x42,
                sentinel,
                "Precondition: writeByte to SRAM[0xA000] after ENABLE_RAM must land (MBC5 gate must be open)",
            )

            // Step 4: DISABLE_RAM — mirrors state Banks.kt save_game_saves leaves behind
            memory.writeByte(0x0000, 0x00)

            // Step 5: Save — captures SRAM with 0x42 at 0xA000
            SavestateManager.save(emulator, stateFile)

            // Step 6: ENABLE_RAM again, then clobber SRAM[0xA000] with mid-mutation sentinel 99
            memory.writeByte(0x0000, 0x0A)
            memory.writeByte(0xA000, 99)
            // DISABLE_RAM after the clobber (mirrors the canonical post-save_game_saves state)
            memory.writeByte(0x0000, 0x00)

            // Step 7: load() — invokes Plan-10 ENABLE_RAM bracket, restores SRAM from snapshot
            SavestateManager.load(emulator, stateFile)

            // Step 8: Read restored byte (RAM gate was closed by load's DISABLE_RAM; re-open to
            // read)
            memory.writeByte(0x0000, 0x0A)
            val restoredByte = memory.readByte(0xA000)
            memory.writeByte(0x0000, 0x00)

            // Primary assertion: restored to pre-save value
            assertEquals(
                0x42,
                restoredByte,
                "SRAM round-trip on real MBC5: SRAM[0xA000] must be restored to pre-save sentinel " +
                    "0x42 after load(). If FAIL: SavestateManager.load() ENABLE_RAM bracket is absent " +
                    "or broken (CR-01 regression).",
            )

            // Non-tautological probe: mid-clobber sentinel must not survive
            assertNotEquals(
                99,
                restoredByte,
                "SRAM round-trip on real MBC5: mid-clobber sentinel 99 must be overwritten by load(). " +
                    "If FAIL: SavestateManager.load() is not restoring SRAM bytes (writes dropped by MBC5 gate). " +
                    "Root cause: missing ENABLE_RAM bracket in load() (Plan 11.1-10 CR-01 fix absent).",
            )
        } finally {
            emulator.stop()
        }
    }
}
