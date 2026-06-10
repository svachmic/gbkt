/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.constraints

/**
 * Platform capability descriptor for a target console.
 *
 * This interface describes the hardware constraints and capabilities of a target platform, enabling
 * the DSL and codegen to validate and generate appropriate code.
 *
 * Implementations should be immutable and provide all hardware specifications needed for validation
 * and code generation.
 */
interface TargetProfile {
    /** Human-readable platform name (e.g., "Nintendo Game Boy Color"). */
    val name: String

    /** Short identifier for CLI/config (e.g., "gbc", "gb", "gba"). */
    val id: String

    /** Screen/display specifications. */
    val screen: ScreenSpec

    /** Sprite hardware specifications. */
    val sprites: SpriteSpec

    /** Memory layout specifications. */
    val memory: MemorySpec

    /** Audio hardware specifications. */
    val audio: AudioSpec

    /** Whether the platform supports ROM banking. */
    val supportsBanking: Boolean

    /** Maximum ROM size in bytes. */
    val maxRomSize: Int

    /** Default number of ROM banks. */
    val defaultRomBanks: Int

    /** Maximum RAM banks (for battery-backed save). */
    val maxRamBanks: Int
}
