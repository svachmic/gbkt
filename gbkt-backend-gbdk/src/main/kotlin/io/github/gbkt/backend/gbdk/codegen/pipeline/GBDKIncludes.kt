/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

/**
 * Canonical GBDK include directives and the base include sets per generated file.
 *
 * Each constant carries its delimiters verbatim (`<...>` for system headers, `"..."` for project
 * headers) so [io.github.gbkt.backend.gbdk.codegen.ast.CFile.includes] lists stay copy-exact. The
 * `*FileBase()` helpers reproduce the fixed include prefix of each generated file — conditional
 * includes (cgb, metasprites, hUGEDriver, sprites, zone tilesets) are appended at the assembly
 * sites in [GBDKPipeline]. Include ORDER is part of the byte-identity contract of the generated C.
 */
object GBDKIncludes {
    const val GB_H = "<gb/gb.h>"
    const val CGB_H = "<gb/cgb.h>"
    const val STDIO_H = "<stdio.h>"
    const val STDLIB_H = "<stdlib.h>"
    const val CONSOLE_H = "<gbdk/console.h>"
    const val METASPRITES_H = "<gbdk/metasprites.h>"
    const val HUGE_DRIVER_H = "<hUGEDriver.h>"
    const val GAME_H = "\"game.h\""

    /** Fixed include prefix of `main.c` (HOME bank). */
    fun homeFileBase(): List<String> = listOf(GB_H, STDIO_H, STDLIB_H, CONSOLE_H, GAME_H)

    /** Fixed include prefix of `bank1.c` (scene file). */
    fun sceneFileBase(): List<String> = listOf(STDIO_H, CONSOLE_H, GAME_H)

    /** Fixed include prefix of `game.h` (header file). */
    fun headerFileBase(): List<String> = listOf(GB_H, STDIO_H, CONSOLE_H)
}
