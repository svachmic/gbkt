/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.platformer_template

import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * IR validation tests for the PlatformerTemplate DSL definition (Phase 12 Wave 0 scaffold).
 *
 * The `private val ir = platformerTemplate.build()` field locks the test's dependency on the
 * `platformerTemplate` symbol so the compile contract is enforced. Subsequent plans (12-09 / 12-12
 * / 12-14 / 12-15 / 12-19..23) add `@Test` methods that lock IR shape against the substrate
 * authored by 12-04..12-18.
 */
class PlatformerTemplateIRTest {
    private val ir = platformerTemplate.build()
}
