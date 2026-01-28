/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.constraints

/**
 * Memory layout specifications.
 *
 * @property workRam Total work RAM in bytes.
 * @property videoRam Total video RAM in bytes.
 * @property oamSize Object Attribute Memory size in bytes.
 * @property hiRam High RAM (zero-page equivalent) size in bytes.
 * @property romBankSize Size of each ROM bank in bytes.
 * @property ramBankSize Size of each external RAM bank in bytes.
 * @property stackSize Typical/recommended stack size in bytes.
 */
data class MemorySpec(
    val workRam: Int,
    val videoRam: Int,
    val oamSize: Int,
    val hiRam: Int,
    val romBankSize: Int,
    val ramBankSize: Int,
    val stackSize: Int = 256,
) {
    /** Total work RAM in KB. */
    val workRamKB: Int
        get() = workRam / 1024

    /** Total video RAM in KB. */
    val videoRamKB: Int
        get() = videoRam / 1024
}
