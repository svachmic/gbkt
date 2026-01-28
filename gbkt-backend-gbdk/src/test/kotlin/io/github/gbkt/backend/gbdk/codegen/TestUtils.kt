/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen

import io.github.gbkt.core.Game

/**
 * Test utility extension function to generate C code for testing.
 *
 * Uses GBDKCodeGenerator directly to produce a single C file output, which is convenient for test
 * assertions that check for specific generated code patterns.
 *
 * @return Generated C code as a string
 */
fun Game.compileForTest(): String = GBDKCodeGenerator(this).generate()
