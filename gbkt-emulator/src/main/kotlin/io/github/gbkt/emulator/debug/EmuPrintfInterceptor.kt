/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.debug

import eu.rekawek.coffeegb.core.AddressSpace
import eu.rekawek.coffeegb.core.cpu.Registers

/**
 * Intercepts GBDK `EMU_printf` trap sequences in the emulated CPU instruction stream.
 *
 * GBDK's `EMU_printf()` macro emits a well-known 5-byte opcode signature at the call site. When the
 * emulated CPU executes this sequence, [check] detects it and extracts the format string from the
 * HL register, routing it to [onMessage].
 *
 * ## Trap Signature
 *
 * The 5-byte sequence at PC+0 through PC+4:
 * ```
 * PC+0: 0x52        — ld d,d (the trap signal; normally a no-op)
 * PC+1: 0x18        — JR (jump-relative opcode)
 * PC+2: <offset>    — JR operand (relative displacement, variable)
 * PC+3: 0x64        — marker byte 1
 * PC+4: 0x64        — marker byte 2
 * ```
 *
 * The HL register holds the address of the null-terminated format string in ROM.
 *
 * ## False-Positive Prevention
 *
 * A standalone `ld d,d` (0x52) is a normal no-op instruction. The interceptor verifies the full
 * 4-byte signature (0x18 at PC+1, 0x64/0x64 at PC+3/PC+4) before treating it as an EMU_printf trap.
 *
 * ## Deduplication
 *
 * Coffee-GB uses a micro-op model — a single logical instruction (like `ld d,d`) may fire multiple
 * CPU ticks. [lastInterceptedPc] records the last PC that triggered the interceptor. Subsequent
 * ticks at the same PC are ignored, ensuring [onMessage] fires exactly once per `EMU_printf` call
 * within a single frame. Call [resetDedup] between frames to allow the same call site to fire again
 * on the next frame.
 *
 * @param onMessage Callback invoked with the extracted format string and the PC address when an
 *   EMU_printf trap is detected. Called on the emulator thread — callers should be thread-safe.
 */
class EmuPrintfInterceptor(private val onMessage: (String, Int) -> Unit) {

    /**
     * The PC value of the last successfully intercepted EMU_printf trap. Initialized to -1 (no
     * instruction yet intercepted). Used to prevent duplicate firing when the micro-op model ticks
     * the same instruction multiple times.
     */
    @Volatile private var lastInterceptedPc: Int = -1

    /**
     * Maximum length of a C string read from emulator memory. Caps [readCString] to prevent runaway
     * reads into unknown memory.
     */
    private val maxStringLength = 256

    /**
     * Resets the deduplication state so that previously seen PC addresses can fire again. Call this
     * at frame boundaries to allow the same EMU_printf call site to fire once per frame rather than
     * once ever.
     */
    fun resetDedup() {
        lastInterceptedPc = -1
    }

    /**
     * Called after each CPU tick. Checks if the current instruction is an EMU_printf trap.
     *
     * This method is designed to be called ~4 million times per second (at 1x Game Boy speed). The
     * opcode check on the first line is an O(1) fast-reject that returns immediately for any opcode
     * other than 0x52, keeping the hot path minimal.
     *
     * @param registers The CPU register file. Used to read PC (current instruction address) and HL
     *   (format string address).
     * @param addressSpace The emulator's memory bus. Used to read the signature bytes and the
     *   null-terminated format string.
     */
    fun check(registers: Registers, addressSpace: AddressSpace) {
        val pc = registers.pc

        // Fast reject: only PC that changed since last intercept needs full check
        if (pc == lastInterceptedPc) return

        // Fast reject: opcode 0x52 (ld d,d) is the only interesting first byte
        val opcode = addressSpace.getByte(pc) and 0xFF
        if (opcode != 0x52) return

        // Verify full 4-byte signature to prevent false positives from normal ld d,d
        val byte1 = addressSpace.getByte(pc + 1) and 0xFF
        if (byte1 != 0x18) return // Must be JR

        val byte3 = addressSpace.getByte(pc + 3) and 0xFF
        val byte4 = addressSpace.getByte(pc + 4) and 0xFF
        if (byte3 != 0x64 || byte4 != 0x64) return // Must have 0x6464 marker

        // Valid EMU_printf trap detected — record PC to prevent duplicate firing
        lastInterceptedPc = pc

        // HL register holds the address of the null-terminated format string
        val hlAddress = registers.hl
        val message = readCString(addressSpace, hlAddress)

        onMessage(message, pc)
    }

    /**
     * Reads a null-terminated C string from the emulator's address space.
     *
     * Terminates at the first null byte (0x00) or after [maxStringLength] characters, whichever
     * comes first. The safety cap prevents unbounded reads if the string pointer is corrupt or the
     * game code has a bug.
     *
     * @param addressSpace Memory source for byte reads.
     * @param startAddress Address of the first character of the string.
     * @return The string contents, without the null terminator.
     */
    internal fun readCString(addressSpace: AddressSpace, startAddress: Int): String {
        val sb = StringBuilder()
        var addr = startAddress
        while (sb.length < maxStringLength) {
            val byte = addressSpace.getByte(addr) and 0xFF
            if (byte == 0) break
            sb.append(byte.toChar())
            addr++
        }
        return sb.toString()
    }
}
