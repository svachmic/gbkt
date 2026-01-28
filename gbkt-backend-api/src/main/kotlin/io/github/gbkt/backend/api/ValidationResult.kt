/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.api

// Re-export validation types from core for backward compatibility
// This allows code using backend-api to access these types without importing from core
typealias ValidationResult = io.github.gbkt.core.ValidationResult

typealias ValidationMessage = io.github.gbkt.core.ValidationMessage

typealias ValidationSeverity = io.github.gbkt.core.ValidationSeverity

typealias ValidationException = io.github.gbkt.core.ValidationException
