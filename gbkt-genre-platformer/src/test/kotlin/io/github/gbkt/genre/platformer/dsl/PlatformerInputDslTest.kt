/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.dsl

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.dsl.u8Var
import io.github.gbkt.core.dsl.zone
import io.github.gbkt.core.ir.GenericSystem
import kotlin.test.Test
import kotlin.test.assertEquals

// =============================================================================
// PHASE 12.3 PLAN 01 — platformerInput { } DSL acceptance
//
// Locks R1 contract: `platformerInput { walkSpeed(N); friction(F);
// airFriction(F); walkFrameCount(W); cyclePeriod(P); walkFrameIdx(AssignableVar);
// threeFrameCounter(AssignableVar) }` compiles at game-level and zone-level.
//
// Game-level → registers a GenericSystem with config["type"]="platformer_input"
// + numeric defaults + binder-captured names.
//
// Zone-level → writes ONLY explicitly-set numeric fields into
// ZoneIR.platformerInputOverride. Unset fields are ABSENT (not
// present-with-default — RESEARCH §Pattern 2 absent-vs-default contract).
// AssignableVar binders are NEVER per-zone (D-03 / L-2.2).
//
// The string literals below ("platformer_input", "walkSpeed", "friction",
// "airFriction", "walkFrameCount", "cyclePeriod", "walkFrameIdxVar",
// "threeFrameCounterVar") are the lock for Wave 2 plans 02/04/06/08 which
// read these keys back from GenericSystem.config — DO NOT rename without
// updating PlatformerVisitor.
//
// L-9.1: AssignableVar binders must be declared INSIDE the game("test") { }
// block so their property delegates register against the active GameBuilder
// via GameBuilderContext. Top-level Kotlin vals would not register.
// =============================================================================

class PlatformerInputDslTest {

    /**
     * Test 1 — game-level builder produces a GenericSystem(type="platformer_input") with explicit +
     * default numeric fields AND the captured AssignableVar binder names.
     *
     * Locks the PlatformerVisitor (Wave 2) contract: keys must be `walkSpeed` / `friction` /
     * `airFriction` / `walkFrameCount` / `cyclePeriod` for numerics, and `walkFrameIdxVar` /
     * `threeFrameCounterVar` for binders.
     */
    @Test
    fun `platformerInput stores numeric defaults and AssignableVar binders into GenericSystem config`() {
        val gb =
            game("test1") {
                @Suppress("UNUSED_VARIABLE") var walkFrameIdx by u8Var(0)
                @Suppress("UNUSED_VARIABLE") var threeFrameCounter by u8Var(0)
                platformerInput {
                    walkSpeed(64)
                    walkFrameIdx(walkFrameIdx)
                    threeFrameCounter(threeFrameCounter)
                }
            }

        val system =
            gb.currentSystems().filterIsInstance<GenericSystem>().single {
                (it.config["type"] as? String) == "platformer_input"
            }
        assertEquals(
            64,
            system.config["walkSpeed"],
            "walkSpeed must reflect the explicit setter value.",
        )
        assertEquals(8, system.config["friction"], "friction default (D-01a) must be 8.")
        assertEquals(
            0,
            system.config["airFriction"],
            "airFriction default (D-01a / D-04) must be 0.",
        )
        assertEquals(
            3,
            system.config["walkFrameCount"],
            "walkFrameCount default (D-01a) must be 3.",
        )
        assertEquals(6, system.config["cyclePeriod"], "cyclePeriod default (D-01a) must be 6.")
        assertEquals(
            "walkFrameIdx",
            system.config["walkFrameIdxVar"],
            "walkFrameIdxVar must be the captured AssignableVar.name from the property delegate.",
        )
        assertEquals(
            "threeFrameCounter",
            system.config["threeFrameCounterVar"],
            "threeFrameCounterVar must be the captured AssignableVar.name from the property delegate.",
        )
    }

    /**
     * Test 2 — zone-level builder records ONLY explicitly-set numeric fields into the override map
     * (RESEARCH §Pattern 2 absent-vs-default contract — L-9.2).
     */
    @Test
    fun `zone-level platformerInput stores override map only for explicitly set numeric fields`() {
        val gb =
            game("test2") {
                val level1 by zone {
                    platformerInput {
                        walkSpeed(64)
                        // friction/airFriction/walkFrameCount/cyclePeriod intentionally
                        // omitted — must be ABSENT from the override map.
                    }
                }
                @Suppress("UNUSED_VARIABLE") val _unused = level1
            }

        val zone = gb.currentZones().single { it.id == "level1" }
        assertEquals(
            mapOf<String, Any>("walkSpeed" to 64),
            zone.platformerInputOverride,
            "Override map must contain ONLY explicitly-set numeric fields; defaults must NOT appear.",
        )
    }

    /**
     * Test 3 — zone-level builder MUST NOT include AssignableVar binders in the override map (D-03
     * / L-2.2 / L-9.2 — binders are game-level only; OverrideTrackingInputBuilder does not override
     * the binder methods).
     */
    @Test
    fun `zone-level platformerInput does NOT include AssignableVar binders in override map`() {
        val gb =
            game("test3") {
                @Suppress("UNUSED_VARIABLE") var wfi by u8Var(0)
                val level2 by zone {
                    platformerInput {
                        walkSpeed(64)
                        // Binder call inside zone block — MUST be ignored by the
                        // override tracker (OverrideTrackingInputBuilder doesn't
                        // override walkFrameIdx — D-03 / L-2.2).
                        walkFrameIdx(wfi)
                    }
                }
                @Suppress("UNUSED_VARIABLE") val _unused = level2
            }

        val zone = gb.currentZones().single { it.id == "level2" }
        assertEquals(
            mapOf<String, Any>("walkSpeed" to 64),
            zone.platformerInputOverride,
            "Binder calls in zone-level platformerInput MUST NOT land in the override map " +
                "(binders are game-level only — D-03 / L-2.2).",
        )
    }
}
