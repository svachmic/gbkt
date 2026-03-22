/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

/**
 * Thrown when the emulator fails to start (corrupt ROM, missing file, Coffee-GB internal error).
 *
 * Wraps the underlying exception with a message that includes the ROM file name (not the full path)
 * to avoid leaking absolute file paths to MCP clients or agent logs.
 */
class EmulatorStartException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Thrown when a screenshot capture or metadata sidecar write fails (I/O error, unwritable dir).
 *
 * Wraps the underlying [java.io.IOException] with a message that includes only the label and
 * frame number, not the full output path.
 */
class ScreenshotCaptureException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Thrown when the emulator fails during frame stepping (CPU hang, illegal opcode, etc.).
 *
 * Includes the frame count at the time of failure for debugging context.
 */
class EmulatorFrameException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Thrown when an input script fails to execute (invalid button state, frame mismatch).
 *
 * Includes the frame count at the time of failure for debugging context.
 */
class EmulatorInputException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Thrown when building an [Observation] fails (memory read error, OAM parsing, etc.).
 *
 * Includes the frame count at the time of failure for debugging context.
 */
class EmulatorObservationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
