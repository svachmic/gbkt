/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis

/**
 * A structured diagnostic message produced by an analysis pass.
 *
 * @property id Unique diagnostic code (e.g., "ANLZ-01", "ANLZ-03").
 * @property severity Whether this diagnostic is an error, warning, or informational note.
 * @property message Human-readable description of what was found.
 * @property location Optional source location for context (e.g., "scene 'gameplay'").
 * @property suggestion Optional actionable fix suggestion for the developer.
 */
data class Diagnostic(
    val id: String,
    val severity: Severity,
    val message: String,
    val location: String? = null,
    val suggestion: String? = null,
)

/** Severity level of a [Diagnostic]. */
enum class Severity {
    ERROR,
    WARNING,
    INFO,
}
