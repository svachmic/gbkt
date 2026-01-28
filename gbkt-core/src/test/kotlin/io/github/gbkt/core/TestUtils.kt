/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator

/**
 * Test utility extension function to generate C code without validation.
 *
 * Uses GBDKCodeGenerator directly to produce a single C file output, which is convenient for test
 * assertions that check for specific generated code patterns. This function skips validation,
 * allowing tests to generate code even for games that would fail validation constraints.
 *
 * @return Generated C code as a string
 */
fun Game.compileForTest(): String = GBDKCodeGenerator(this).generate()

/**
 * Compile the game with validation.
 *
 * Validates the game first and throws [ValidationException] if validation fails (unless
 * warnOnValidationErrors is true).
 *
 * @param warnOnValidationErrors If true, validation errors are logged as warnings instead of
 *   throwing an exception
 * @return Generated C code as a string
 * @throws ValidationException if validation fails and warnOnValidationErrors is false
 */
fun Game.compile(warnOnValidationErrors: Boolean = false): String {
    val result = validate()
    if (!result.isValid && !warnOnValidationErrors) {
        throw ValidationException(result)
    }
    return GBDKCodeGenerator(this).generate()
}

/**
 * Compile the game and return both the generated code and validation result.
 *
 * This function always generates code regardless of validation status, but returns the validation
 * result so callers can inspect warnings/errors.
 *
 * @return A pair of (generated C code, validation result)
 */
fun Game.compileWithValidation(): Pair<String, ValidationResult> {
    val result = validate()
    val code = GBDKCodeGenerator(this).generate()
    return code to result
}
