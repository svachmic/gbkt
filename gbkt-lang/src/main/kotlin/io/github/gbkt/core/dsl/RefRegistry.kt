/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.Suggestions
import io.github.gbkt.core.ir.Ref
import io.github.gbkt.core.ir.RefKind

/**
 * Two-stage ref resolution registry.
 *
 * **Stage 1 — Recording:** During DSL execution, [register] records known entity IDs by kind and
 * [ref] records pending references that need to be validated.
 *
 * **Stage 2 — Validation:** At [GameBuilder.build] time, [resolveAll] checks all pending refs
 * against registered IDs. The first unresolved ref throws [DSLValidationError] with a "Did you
 * mean?" suggestion if a close match exists (via [Suggestions.formatSuggestion]).
 *
 * Registration is case-sensitive. Duplicate IDs within the same kind throw at registration time.
 */
class RefRegistry {
    /** Registered known IDs grouped by kind. */
    private val registered: MutableMap<RefKind, MutableSet<String>> = mutableMapOf()

    /** Pending references to be validated at build() time. */
    private val pendingRefs: MutableList<Ref> = mutableListOf()

    /**
     * Registers an entity ID as known for a given kind.
     *
     * @throws DSLValidationError if the same ID is already registered for this kind.
     */
    fun register(id: String, kind: RefKind) {
        val set = registered.getOrPut(kind) { mutableSetOf() }
        if (!set.add(id)) {
            throw DSLValidationError(
                "error: Duplicate declaration \"$id\" — a ${kind.name.lowercase()} with this ID is already registered."
            )
        }
    }

    /**
     * Records a pending reference to be resolved at [resolveAll] time.
     *
     * Returns the [Ref] immediately (unvalidated) so it can be used in builder output.
     */
    fun ref(id: String, kind: RefKind): Ref {
        val r = Ref(id, kind)
        pendingRefs.add(r)
        return r
    }

    /**
     * Resolves all pending references against registered IDs.
     *
     * Throws [DSLValidationError] for the first unresolved reference, with a "Did you mean?"
     * suggestion when a close match exists.
     */
    fun resolveAll() {
        for (ref in pendingRefs) {
            val known = registered[ref.kind] ?: emptySet()
            if (!known.contains(ref.targetId)) {
                val suggestion = Suggestions.formatSuggestion(ref.targetId, known)
                throw DSLValidationError(
                    "error: Unresolved reference \"${ref.targetId}\".$suggestion"
                )
            }
        }
    }

    /** Returns all IDs registered for a given kind. */
    fun registeredIds(kind: RefKind): Set<String> = registered[kind] ?: emptySet()
}
