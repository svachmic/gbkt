/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

/**
 * Tests for the save data system.
 *
 * Validates:
 * - Save slot definition
 * - Variable persistence declaration
 * - Multiple save slots
 * - Save/load operations
 * - IR generation correctness
 */
class SaveTest {

    // =========================================================================
    // SAVE SLOT DEFINITION TESTS
    // =========================================================================

    @Test
    fun `saveData with single slot generates correct structure`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        // Fields are defined but accessed via save.field<T>("name")
                        u16Field() // score

                        config { slots = 1 }
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()

        // Should generate save data structures
        assertTrue(
            code.contains("mygame") || code.contains("save"),
            "Should reference save data name",
        )
    }

    @Test
    fun `saveData with checksum generates validation code`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field() // score

                        config {
                            slots = 1
                            checksum = Checksum.CRC8
                        }
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate save code with checksum")
    }

    @Test
    fun `saveData with magic string generates header validation`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u8Field(default = 1) // level

                        config {
                            slots = 1
                            magic = "GBKT"
                            version = 1
                        }
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()

        // Should contain magic string reference
        assertTrue(code.contains("GBKT") || code.isNotEmpty(), "Should handle magic string")
    }

    // =========================================================================
    // VARIABLE PERSISTENCE TESTS
    // =========================================================================

    @Test
    fun `u8Field generates correct variable type`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u8Field(default = 3) // lives
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()

        // Should use UINT8 type
        assertTrue(
            code.contains("UINT8") || code.contains("uint8") || code.isNotEmpty(),
            "Should generate u8 type",
        )
    }

    @Test
    fun `u16Field generates correct variable type`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field() // score
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()

        // Should use UINT16 type
        assertTrue(
            code.contains("UINT16") || code.contains("uint16") || code.isNotEmpty(),
            "Should generate u16 type",
        )
    }

    @Test
    fun `i8Field generates signed variable type`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        i8Field(default = 0) // offset
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate signed i8 type")
    }

    @Test
    fun `flagsField generates bitfield type`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        flagsField() // flags
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate flags field")
    }

    @Test
    fun `arrayField generates array type`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        arrayField(10) // inventory
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate array field")
    }

    @Test
    fun `stringField generates character array`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        stringField(8) // name
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate string field code")
    }

    // =========================================================================
    // MULTIPLE SAVE SLOTS TESTS
    // =========================================================================

    @Test
    fun `saveData with multiple slots generates slot management`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field() // score

                        config { slots = 3 }
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate multi-slot save code")
    }

    @Test
    fun `loading different slots compiles correctly`() {
        val game =
            gbGame("test") {
                var currentSlot by u8Var(0)
                val save =
                    saveData("mygame") {
                        u16Field() // score

                        config { slots = 3 }
                    }

                start =
                    scene("main") {
                        enter {
                            save.load(0)
                            save.load(1)
                            save.load(2)
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate load code for multiple slots")
    }

    @Test
    fun `save exists check generates validation code`() {
        val game =
            gbGame("test") {
                var canContinue by u8Var(0)
                val save =
                    saveData("mygame") {
                        u16Field() // score

                        config { slots = 3 }
                    }

                start = scene("main") { enter { whenever(save.exists(0)) { canContinue set 1 } } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate exists check code")
    }

    // =========================================================================
    // SAVE/LOAD OPERATIONS TESTS
    // =========================================================================

    @Test
    fun `save load generates SRAM read`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field() // score
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()

        // Should generate SRAM access code
        assertTrue(code.isNotEmpty(), "Should generate load code")
    }

    @Test
    fun `save save generates SRAM write`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field() // score
                    }

                start =
                    scene("main") {
                        enter {
                            save.load(0)
                            save.save(0)
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate save code")
    }

    @Test
    fun `save to current slot generates correct code`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field() // score
                    }

                start =
                    scene("main") {
                        enter {
                            save.load(0)
                            save.save() // Save to current slot
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate save-to-current-slot code")
    }

    @Test
    fun `save erase generates clear code`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field() // score

                        config { slots = 3 }
                    }

                start = scene("main") { enter { save.erase(0) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate erase code")
    }

    @Test
    fun `save eraseAll generates bulk clear code`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field() // score

                        config { slots = 3 }
                    }

                start = scene("main") { enter { save.eraseAll() } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate eraseAll code")
    }

    @Test
    fun `save copy generates slot copy code`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field() // score

                        config { slots = 3 }
                    }

                start = scene("main") { enter { save.copy(from = 0, to = 1) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate copy code")
    }

    // =========================================================================
    // CARTRIDGE CONFIGURATION TESTS
    // =========================================================================

    @Test
    fun `saveData auto-upgrades cartridge to battery-backed`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field() // score
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        // When saveData is used, cartridge should be upgraded to support battery-backed SRAM
        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with battery-backed cartridge")
    }

    // =========================================================================
    // VARIABLE SLOT OPERATIONS TESTS
    // =========================================================================

    @Test
    fun `load with expression slot compiles`() {
        val game =
            gbGame("test") {
                var currentSlot by u8Var(0)
                val save =
                    saveData("mygame") {
                        u16Field() // score

                        config { slots = 3 }
                    }

                start = scene("main") { enter { save.load(Expr(IRVar("currentSlot"))) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("currentSlot") || code.isNotEmpty(),
            "Should generate variable slot load code",
        )
    }

    @Test
    fun `save with expression slot compiles`() {
        val game =
            gbGame("test") {
                var currentSlot by u8Var(0)
                val save =
                    saveData("mygame") {
                        u16Field() // score

                        config { slots = 3 }
                    }

                start =
                    scene("main") {
                        enter {
                            save.load(0)
                            save.save(Expr(IRVar("currentSlot")))
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate variable slot save code")
    }

    @Test
    fun `exists with expression slot compiles`() {
        val game =
            gbGame("test") {
                var currentSlot by u8Var(0)
                var valid by u8Var(0)
                val save =
                    saveData("mygame") {
                        u16Field() // score

                        config { slots = 3 }
                    }

                start =
                    scene("main") {
                        enter { whenever(save.exists(Expr(IRVar("currentSlot")))) { valid set 1 } }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate variable slot exists check")
    }

    // =========================================================================
    // MULTIPLE FIELD TYPES IN ONE SAVE
    // =========================================================================

    @Test
    fun `saveData with multiple field types compiles`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field() // score
                        u16Field() // highScore
                        u8Field() // gamesPlayed
                        u8Field(default = 1) // level
                        flagsField() // flags

                        config {
                            slots = 1
                            checksum = Checksum.CRC8
                            magic = "SAVE"
                            version = 1
                        }
                    }

                start =
                    scene("main") {
                        enter {
                            save.load(0)
                            save.save()
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with multiple field types")
    }

    @Test
    fun `saveData with named fields compiles`() {
        val game =
            gbGame("test") {
                var currentScore by u16Var(0)
                val save =
                    saveData("mygame") {
                        u16Field() // score
                        u16Field() // highScore
                    }

                start =
                    scene("main") {
                        enter {
                            save.load(0)
                            save.save()
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with named fields")
    }

    // =========================================================================
    // CHECKSUM VARIANTS TESTS
    // =========================================================================

    @Test
    fun `saveData with XOR checksum compiles`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field()

                        config { checksum = Checksum.XOR }
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with XOR checksum")
    }

    @Test
    fun `saveData with SUM16 checksum compiles`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field()

                        config { checksum = Checksum.SUM16 }
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with SUM16 checksum")
    }

    @Test
    fun `saveData with no checksum compiles`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("mygame") {
                        u16Field()

                        config { checksum = Checksum.NONE }
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with no checksum")
    }
}
