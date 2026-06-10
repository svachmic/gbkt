/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// SYSTEM IR VISITOR INTERFACE
// =============================================================================

/**
 * Visitor interface for [SystemIR] dispatch.
 *
 * Provides one `visit*` method per [SystemIR] subtype (7 total). Implementations convert IR system
 * nodes to a result of type [T].
 *
 * The `I` suffix distinguishes this interface from any backend system visitor implementations.
 *
 * Usage:
 * ```kotlin
 * object MySystemVisitor : SystemIRVisitorI<String> {
 *     override fun visitDialogSystem(system: DialogSystem): String = "dialog:${system.id}"
 *     // ...
 * }
 * val result = someSystem.accept(MySystemVisitor)
 * ```
 */
interface SystemIRVisitorI<T> {

    fun visitDialogSystem(system: DialogSystem): T

    fun visitSoundSystem(system: SoundSystem): T

    fun visitSaveSystem(system: SaveSystem): T

    fun visitExplorationSystem(system: ExplorationSystem): T

    fun visitCameraSystem(system: CameraSystem): T

    fun visitGenericSystem(system: GenericSystem): T

    fun visitPathfindingSystem(system: PathfindingSystem): T

    fun visitCombatEngineSystem(system: CombatEngineSystem): T
}
