/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

/**
 * Thrown when `game_metadata.json` cannot be parsed into a valid [GameMetadata] instance.
 *
 * Wraps the underlying JSON parse error or missing-field error with a descriptive message.
 */
class MetadataParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
