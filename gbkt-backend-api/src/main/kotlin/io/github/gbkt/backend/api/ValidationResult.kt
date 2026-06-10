/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.api

/**
 * Result of game validation.
 *
 * Backends return this from their [CodegenBackend.validate] implementation to report any errors or
 * warnings found during game validation.
 */
data class ValidationResult(
    /** Whether the game is valid (no errors). */
    val isValid: Boolean,

    /** Validation errors that prevent compilation. */
    val errors: List<ValidationMessage> = emptyList(),

    /** Validation warnings (non-fatal issues). */
    val warnings: List<ValidationMessage> = emptyList(),
) {
    companion object {
        /** A successful validation result with no issues. */
        val SUCCESS = ValidationResult(isValid = true)

        /** Create a failed result with a single error message. */
        fun failed(message: String, category: String? = null) =
            ValidationResult(
                isValid = false,
                errors = listOf(ValidationMessage(message, category, ValidationSeverity.ERROR)),
            )
    }
}

/** A single validation message (error or warning). */
data class ValidationMessage(
    /** Human-readable description of the issue. */
    val message: String,

    /** Optional category for grouping (e.g., "sprites", "memory", "assets"). */
    val category: String? = null,

    /** Severity of the message. */
    val severity: ValidationSeverity = ValidationSeverity.ERROR,
)

/** Severity level for validation messages. */
enum class ValidationSeverity {
    /** Fatal error that prevents compilation. */
    ERROR,

    /** Non-fatal warning that should be addressed but doesn't prevent compilation. */
    WARNING,

    /** Informational message. */
    INFO,
}

/** Exception thrown when validation fails and strict mode is enabled. */
class ValidationException(
    val result: ValidationResult,
    message: String = "Game validation failed with ${result.errors.size} error(s)",
) : Exception(message)
