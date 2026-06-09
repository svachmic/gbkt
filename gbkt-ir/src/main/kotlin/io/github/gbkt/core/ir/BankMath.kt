/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

/**
 * ROM bank count math utilities shared by the analysis pass pipeline and the GBDK backend.
 *
 * Placed in `gbkt-ir` (the leaf module with zero gbkt dependencies) so both
 * `gbkt-analysis` and `gbkt-backend-gbdk` can depend on it without introducing
 * a circular dependency.
 *
 * ### Caller contract
 * ROM bank counts on Game Boy hardware are always a power of two and are never less than 2.
 * Therefore callers that derive a bank count from [nextPowerOfTwo] MUST apply:
 *
 * ```kotlin
 * val effectiveBanks = maxOf(2, nextPowerOfTwo(maxAssignedBank + 1))
 * ```
 *
 * [nextPowerOfTwo] itself may return 1 for inputs of 0 or 1 — that is mathematically
 * correct but is NOT a valid Game Boy ROM bank count. The `maxOf(2, …)` guard at each
 * call site enforces the hardware minimum.
 */

/**
 * Returns the smallest power of two that is >= [n].
 *
 * ### WARNING — do not use the return value for bank counts without clamping
 * `nextPowerOfTwo(0)` returns 1 and `nextPowerOfTwo(1)` returns 1. **Neither value is a valid
 * Game Boy ROM bank count** — the hardware minimum is 2. Any caller that derives a ROM bank
 * count from this function MUST apply `maxOf(2, nextPowerOfTwo(…))` before using the result.
 * See the file-level KDoc for the full caller contract.
 *
 * Other examples: `nextPowerOfTwo(3) = 4`, `nextPowerOfTwo(5) = 8`, `nextPowerOfTwo(8) = 8`.
 *
 * This function is a shared utility; do NOT add a private copy in BankingAnalysisPass or
 * GBDKBackend — use this single definition.
 */
fun nextPowerOfTwo(n: Int): Int {
    if (n <= 0) return 1
    var v = n - 1
    v = v or (v shr 1)
    v = v or (v shr 2)
    v = v or (v shr 4)
    v = v or (v shr 8)
    v = v or (v shr 16)
    return v + 1
}
