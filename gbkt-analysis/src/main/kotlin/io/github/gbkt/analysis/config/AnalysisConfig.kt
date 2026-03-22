/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.config

import io.github.gbkt.core.ir.CartridgeConfig

/**
 * Configurable thresholds and hardware limits for the analysis pass pipeline.
 *
 * The companion factory [fromCartridgeConfig] derives [maxBanks] from the cartridge type string.
 * All threshold fields have sensible defaults that match Game Boy hardware limits.
 *
 * ### Optimization toggles
 * IR-level passes are always-on by default; set the corresponding flag to `false` to skip a pass.
 * C-output pass toggles ([sharedConstantTablesEnabled], [functionDeduplicationEnabled]) are
 * consumed by the C-output optimizer (Plan 04) and have no effect on IR passes.
 *
 * @property maxBanks Maximum number of ROM banks available for this cartridge type.
 * @property bankFillWarningThreshold Fraction of bank capacity that triggers a WARNING (default
 *   85%).
 * @property bankFillErrorThreshold Fraction of bank capacity that triggers an ERROR (default 100%).
 * @property vramTileWarningThreshold VRAM tile count that triggers a WARNING (default 350 tiles).
 * @property vramTileErrorThreshold VRAM tile count that triggers an ERROR (default >384 =
 *   overflow).
 * @property oamWarningThreshold OAM sprite slot count that triggers a WARNING (default 35).
 * @property oamErrorThreshold OAM sprite slot count that triggers an ERROR (default >40 =
 *   overflow).
 * @property wramWarningThreshold Fraction of WRAM consumed that triggers a WARNING (default 83%).
 * @property hramWarningThreshold Fraction of HRAM consumed that triggers a WARNING (default 80%).
 * @property bytesPerStatement Conservative heuristic for code size estimation (default 6 bytes).
 * @property paletteStrictMode When true, warns on GBCColor values that lose precision when
 *   converting from RGB888 to RGB555 (i.e. the low 3 bits of any channel are non-zero).
 * @property constantFoldingEnabled When true (default), the [ConstantFoldingPass] is included in
 *   the pipeline. Set to false to skip compile-time expression folding.
 * @property deadCodeEliminationEnabled When true (default), the [DeadCodeEliminationPass] is
 *   included in the pipeline. Set to false to skip unreachable-scene analysis.
 * @property bitwiseOptimizationEnabled When true (default), the [BitwiseOptimizationPass] is
 *   included in the pipeline. Set to false to skip power-of-2 arithmetic rewrites.
 * @property sharedConstantTablesEnabled When true (default), the C-output optimizer merges
 *   identical constant arrays into shared tables. Consumed by Plan 04's COutputOptimizer.
 * @property functionDeduplicationEnabled When true (default), the C-output optimizer deduplicates
 *   identical generated functions. Consumed by Plan 04's COutputOptimizer.
 */
data class AnalysisConfig(
    val maxBanks: Int,
    val bankFillWarningThreshold: Double = 0.85,
    val bankFillErrorThreshold: Double = 1.0,
    val vramTileWarningThreshold: Int = 350,
    val vramTileErrorThreshold: Int = 385,
    val oamWarningThreshold: Int = 35,
    val oamErrorThreshold: Int = 41,
    val wramWarningThreshold: Double = 0.83,
    val hramWarningThreshold: Double = 0.80,
    val bytesPerStatement: Int = 6,
    val paletteStrictMode: Boolean = false,
    // IR-level optimization pass toggles (always-on by default)
    val constantFoldingEnabled: Boolean = true,
    val deadCodeEliminationEnabled: Boolean = true,
    val bitwiseOptimizationEnabled: Boolean = true,
    // C-output pass toggles (consumed by Plan 04's COutputOptimizer)
    val sharedConstantTablesEnabled: Boolean = true,
    val functionDeduplicationEnabled: Boolean = true,
) {
    companion object {
        /**
         * Derives an [AnalysisConfig] from a [CartridgeConfig], mapping cartridge type strings to
         * their maximum ROM bank counts per the Game Boy hardware specification:
         *
         * | Cartridge | Max Banks |
         * |-----------|-----------|
         * | ROM_ONLY  | 2         |
         * | MBC1      | 32        |
         * | MBC2      | 16        |
         * | MBC3      | 128       |
         * | MBC5      | 256       |
         * | (unknown) | 2         |
         *
         * The actual bank count used is the lesser of the cartridge type maximum and the
         * [CartridgeConfig.romBanks] value declared by the game author.
         */
        fun fromCartridgeConfig(config: CartridgeConfig): AnalysisConfig {
            val typeMax =
                when {
                    config.cartridge.startsWith("ROM_ONLY") -> 2
                    config.cartridge.startsWith("MBC1") -> 32
                    config.cartridge.startsWith("MBC2") -> 16
                    config.cartridge.startsWith("MBC3") -> 128
                    config.cartridge.startsWith("MBC5") -> 256
                    else -> 2
                }
            val effectiveMax = minOf(typeMax, config.romBanks)
            return AnalysisConfig(maxBanks = effectiveMax)
        }
    }
}
