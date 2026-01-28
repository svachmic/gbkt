/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk

import io.github.gbkt.backend.api.CodegenBackend
import io.github.gbkt.backend.api.GeneratedFile
import io.github.gbkt.backend.api.GenerationOptions
import io.github.gbkt.backend.api.GenerationResult
import io.github.gbkt.backend.api.ValidationResult
import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.profiles.GameBoyColorProfile
import io.github.gbkt.core.Game
import io.github.gbkt.core.GameValidator
import io.github.gbkt.core.constraints.TargetProfile

/**
 * GBDK-2020 backend for Game Boy / Game Boy Color.
 *
 * This backend generates GBDK-compatible C code and can optionally compile it using the GBDK-2020
 * toolchain.
 */
class GBDKBackend(override val profile: TargetProfile = GameBoyColorProfile) : CodegenBackend {
    override val id = "gbdk"
    override val displayName = "GBDK-2020 for ${profile.name}"
    override val romExtension = if (profile.id == "gbc") "gbc" else "gb"

    override fun validate(game: Game): ValidationResult {
        // GameValidator returns the unified ValidationResult type directly
        // All errors and warnings are preserved
        return GameValidator(game).validate()
    }

    @Suppress("TooGenericExceptionCaught") // GBDKCodeGenerator can throw various runtime exceptions
    override fun generate(game: Game, options: GenerationOptions): GenerationResult {
        return try {
            val generator = GBDKCodeGenerator(game)
            // Use multi-file generation to split code by bank
            // GBDK-2020 doesn't support multiple #pragma bank in a single file
            val files = generator.generateMultiFile()
            val generatedFiles =
                files.mapValues { (path, content) ->
                    GeneratedFile(
                        path,
                        content,
                        "Bank ${path.removeSuffix(".c").removePrefix("bank")}",
                    )
                }
            GenerationResult(success = true, files = generatedFiles)
        } catch (e: Exception) {
            GenerationResult.failed(e.message ?: "Code generation failed")
        }
    }

    companion object {
        /** Create backend for Game Boy (DMG). */
        fun forGameBoy() = GBDKBackend(io.github.gbkt.backend.gbdk.profiles.GameBoyProfile)

        /** Create backend for Game Boy Color. */
        fun forGameBoyColor() = GBDKBackend(GameBoyColorProfile)
    }
}
