/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * WR-03 — `MetaspriteFrameBuilder.tile(x, y, baseId)` MUST reject x or y values that fall outside
 * the int8_t range (-128..127) at DSL build time. Silent overflow into int8_t in downstream codegen
 * produces broken visual metasprite layouts that only surface at runtime.
 *
 * Mirrors the shape of the existing `MetaspriteBuilderTest` (per Plan 10.1-02 PATTERNS line 26).
 * Boundary values -128 and 127 MUST be accepted (the int8_t representable range is closed).
 */
class MetaspriteBuilderTileRangeTest {

    @Test
    fun tile_rejects_x_outside_int8_range() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                game("Test") {
                    @Suppress("UNUSED_VARIABLE")
                    val foo by metasprite { frame { tile(x = 128, y = 0, baseId = 0) } }
                    val sScene = scene("s") {}
                    start = sScene
                }
            }
        val msg = ex.message.orEmpty()
        // Message must point at the offending axis (x) and the int8_t range so the user can act on
        // it.
        assert(msg.contains("x")) { "Expected message to mention 'x', got: $msg" }
        assert(msg.contains("-128..127") || msg.contains("int8_t")) {
            "Expected message to mention '-128..127' or 'int8_t', got: $msg"
        }
    }

    @Test
    fun tile_rejects_y_outside_int8_range() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                game("Test") {
                    @Suppress("UNUSED_VARIABLE")
                    val foo by metasprite { frame { tile(x = 0, y = -129, baseId = 0) } }
                    val sScene = scene("s") {}
                    start = sScene
                }
            }
        val msg = ex.message.orEmpty()
        assert(msg.contains("y")) { "Expected message to mention 'y', got: $msg" }
        assert(msg.contains("-128..127") || msg.contains("int8_t")) {
            "Expected message to mention '-128..127' or 'int8_t', got: $msg"
        }
    }

    @Test
    fun tile_accepts_int8_boundaries() {
        // -128 and 127 are both representable as int8_t — boundary tiles must NOT throw.
        // No build() is invoked here because that's a separate invariant; just exercise the
        // require() guards on tile() entry.
        game("Test") {
            @Suppress("UNUSED_VARIABLE")
            val foo by metasprite {
                frame {
                    tile(x = -128, y = 127, baseId = 0)
                    tile(x = 127, y = -128, baseId = 0)
                }
            }
            val sScene = scene("s") {}
            start = sScene
        }
    }
}
