/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

/** What kind of IR entity a Ref points to. */
enum class RefKind {
    SCENE,
    ACTOR,
    SYSTEM,
    VARIABLE,
    ASSET,
    ZONE,
}

/** Typed reference to an IR entity by string ID. */
data class Ref(val targetId: String, val kind: RefKind)
