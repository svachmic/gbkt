/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * REQ-14: resolveZoneSize pure function policy tests.
 *
 * D-01: gbkt-lang stays filesystem-free (no ImageIO/File) — enforced by the acceptance criteria grep.
 * D-02: pure resolveZoneSize(explicit?, derivedPngTileDims?) returns:
 *         explicit when given, else derived, else 20×18.
 * D-03: no input combination returns 32×32.
 */
class ZoneSizeResolutionTest {

    @Test
    fun `explicit wins over derived and fallback`() {
        val result = resolveZoneSize(explicit = 10 to 12, derivedPngTileDims = 60 to 32)
        assertEquals(10 to 12, result)
    }

    @Test
    fun `png-derived used when no explicit size`() {
        val result = resolveZoneSize(explicit = null, derivedPngTileDims = 60 to 32)
        assertEquals(60 to 32, result)
    }

    @Test
    fun `tileset-only fallback is 20x18 when no explicit and no derived`() {
        val result = resolveZoneSize(explicit = null, derivedPngTileDims = null)
        assertEquals(20 to 18, result)
    }

    @Test
    fun `no input combination returns 32x32`() {
        val combinations = listOf(
            resolveZoneSize(null, null),
            resolveZoneSize(null, 60 to 32),
            resolveZoneSize(10 to 12, null),
            resolveZoneSize(10 to 12, 60 to 32),
            resolveZoneSize(20 to 18, null),
            resolveZoneSize(null, 20 to 18),
        )
        combinations.forEach { result ->
            assertNotEquals(32 to 32, result)
        }
    }
}
