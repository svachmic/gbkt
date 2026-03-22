/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

// This file is intentionally thin — CombatHookPoint lives in gbkt-ir (CombatEngineIR.kt)
// so that CombatEngineSystem can reference it without a circular dependency.
//
// This file re-exports the enum via a typealias for convenience when importing from gbkt-rpg.
// DSL builders in this package use io.github.gbkt.core.ir.CombatHookPoint directly.

// (No additional domain types needed for combat hooks — the hook point enum and ops map
// are sufficient. This file documents the design decision.)
