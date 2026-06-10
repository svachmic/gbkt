/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.postprocess

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// C OUTPUT OPTIMIZER TESTS
// Tests for SharedConstantTablePass, FunctionDeduplicationPass, and
// COutputOptimizer orchestrator.
//
// Tests cover:
// 1. SharedConstantTablePass: identical arrays deduped, no-dup unchanged
// 2. FunctionDeduplicationPass: identical bodies deduped, different bodies unchanged
// 3. FunctionDeduplicationPass: different signatures NOT deduped
// 4. COutputOptimizer: both passes run in sequence with combined summary
// 5. COutputOptimizer: toggles work (disable individual passes)
// 6. Edge cases: empty input, single file, no optimizable patterns
// =============================================================================

class COutputOptimizerTest {

    // =========================================================================
    // SharedConstantTablePass Tests
    // =========================================================================

    @Test
    fun `SharedConstantTablePass deduplicates identical constant arrays`() {
        val cText =
            """
            #include "game.h"

            const UINT8 tileset_data[] = {0x00, 0x01, 0x02, 0x03};

            void init_scene() {
                load_tiles(tileset_data);
            }

            const UINT8 duplicate_tileset[] = {0x00, 0x01, 0x02, 0x03};

            void init_scene2() {
                load_tiles(duplicate_tileset);
            }
            """
                .trimIndent()

        val result = SharedConstantTablePass.optimize(cText)

        assertEquals(1, result.arraysDeduped, "Should have deduped 1 duplicate array")
        assertContains(result.optimizedContent, "#define duplicate_tileset tileset_data")
        assertFalse(
            result.optimizedContent.contains("const UINT8 duplicate_tileset[]"),
            "Duplicate array declaration should be removed",
        )
        assertEquals(1, result.details.size)
        assertContains(result.details[0], "duplicate_tileset")
        assertContains(result.details[0], "tileset_data")
    }

    @Test
    fun `SharedConstantTablePass leaves non-duplicate arrays unchanged`() {
        val cText =
            """
            const UINT8 array_a[] = {0x00, 0x01};
            const UINT8 array_b[] = {0x02, 0x03};
            """
                .trimIndent()

        val result = SharedConstantTablePass.optimize(cText)

        assertEquals(0, result.arraysDeduped, "No arrays should be deduped")
        assertEquals(cText, result.optimizedContent, "Content should be unchanged")
        assertTrue(result.details.isEmpty())
    }

    @Test
    fun `SharedConstantTablePass handles empty input`() {
        val result = SharedConstantTablePass.optimize("")

        assertEquals(0, result.arraysDeduped)
        assertEquals("", result.optimizedContent)
        assertTrue(result.details.isEmpty())
    }

    @Test
    fun `SharedConstantTablePass handles single-line arrays correctly`() {
        val cText =
            """
            const UINT8 palette_a[] = {0xFF, 0x00, 0x55, 0xAA};
            void use_a() { set_palette(palette_a); }
            const UINT8 palette_b[] = {0xFF, 0x00, 0x55, 0xAA};
            void use_b() { set_palette(palette_b); }
            """
                .trimIndent()

        val result = SharedConstantTablePass.optimize(cText)

        assertEquals(1, result.arraysDeduped)
        assertContains(result.optimizedContent, "#define palette_b palette_a")
    }

    @Test
    fun `SharedConstantTablePass handles multiline arrays`() {
        val cText =
            """
            const UINT8 map_data_a[] = {
                0x00, 0x01, 0x02,
                0x03, 0x04, 0x05
            };
            const UINT8 map_data_b[] = {
                0x00, 0x01, 0x02,
                0x03, 0x04, 0x05
            };
            """
                .trimIndent()

        val result = SharedConstantTablePass.optimize(cText)

        assertEquals(1, result.arraysDeduped)
        assertContains(result.optimizedContent, "#define map_data_b map_data_a")
    }

    @Test
    fun `SharedConstantTablePass handles three or more duplicates`() {
        val cText =
            """
            const UINT8 arr_1[] = {0x01, 0x02};
            const UINT8 arr_2[] = {0x01, 0x02};
            const UINT8 arr_3[] = {0x01, 0x02};
            """
                .trimIndent()

        val result = SharedConstantTablePass.optimize(cText)

        assertEquals(2, result.arraysDeduped, "Should dedup both arr_2 and arr_3")
        assertContains(result.optimizedContent, "#define arr_2 arr_1")
        assertContains(result.optimizedContent, "#define arr_3 arr_1")
    }

    @Test
    fun `SharedConstantTablePass ignores whitespace differences in initializers`() {
        // Same values, different whitespace — should still be treated as duplicates
        val cText =
            """
            const UINT8 compact[] = {0x00,0x01,0x02};
            const UINT8 spaced[] = {  0x00,  0x01,  0x02  };
            """
                .trimIndent()

        val result = SharedConstantTablePass.optimize(cText)

        assertEquals(1, result.arraysDeduped, "Whitespace-differing arrays should be deduped")
    }

    @Test
    fun `SharedConstantTablePass handles input with no arrays`() {
        val cText =
            """
            #include "game.h"
            void empty_func() {}
            """
                .trimIndent()

        val result = SharedConstantTablePass.optimize(cText)

        assertEquals(0, result.arraysDeduped)
        assertEquals(cText, result.optimizedContent)
    }

    // =========================================================================
    // FunctionDeduplicationPass Tests
    // =========================================================================

    @Test
    fun `FunctionDeduplicationPass deduplicates functions with identical bodies`() {
        val cText =
            """
            void init_scene_01() {
                set_bkg_data(0, 16, tileset);
                load_background();
            }

            void init_scene_02() {
                set_bkg_data(0, 16, tileset);
                load_background();
            }
            """
                .trimIndent()

        val result = FunctionDeduplicationPass.optimize(cText)

        assertEquals(1, result.functionsDeduped, "Should have deduped 1 duplicate function")
        assertContains(result.optimizedContent, "/* Deduplicated: see init_scene_01 */")
        assertFalse(
            result.optimizedContent.contains("void init_scene_02()"),
            "Duplicate function definition should be replaced",
        )
        assertEquals(1, result.details.size)
        assertContains(result.details[0], "init_scene_02")
        assertContains(result.details[0], "init_scene_01")
    }

    @Test
    fun `FunctionDeduplicationPass rewrites call sites to canonical function`() {
        val cText =
            """
            void shared_logic() {
                do_something();
                do_more();
            }

            void duplicate_logic() {
                do_something();
                do_more();
            }

            void main_loop() {
                duplicate_logic();
                other_stuff();
                duplicate_logic();
            }
            """
                .trimIndent()

        val result = FunctionDeduplicationPass.optimize(cText)

        assertEquals(1, result.functionsDeduped)
        // Call sites should be rewritten to canonical
        assertFalse(
            result.optimizedContent.contains("duplicate_logic()"),
            "Call sites should be rewritten to canonical name",
        )
        assertTrue(
            result.optimizedContent.count { it == 's' } > 0,
            "Canonical function calls should exist",
        )
        assertContains(result.optimizedContent, "shared_logic()")
    }

    @Test
    fun `FunctionDeduplicationPass leaves functions with different bodies unchanged`() {
        val cText =
            """
            void scene_enter_a() {
                set_bkg_data(0, 16, tileset_a);
            }

            void scene_enter_b() {
                set_bkg_data(0, 16, tileset_b);
            }
            """
                .trimIndent()

        val result = FunctionDeduplicationPass.optimize(cText)

        assertEquals(
            0,
            result.functionsDeduped,
            "Functions with different bodies should NOT be deduped",
        )
        assertEquals(cText, result.optimizedContent)
    }

    @Test
    fun `FunctionDeduplicationPass does NOT dedup functions with different signatures`() {
        // Same body logic but different parameter types/counts
        val cText =
            """
            void process_one(UINT8 x) {
                result = x + 1;
            }

            void process_two(UINT8 x, UINT8 y) {
                result = x + 1;
            }
            """
                .trimIndent()

        val result = FunctionDeduplicationPass.optimize(cText)

        assertEquals(
            0,
            result.functionsDeduped,
            "Functions with different signatures should NOT be deduped",
        )
        assertEquals(cText, result.optimizedContent)
    }

    @Test
    fun `FunctionDeduplicationPass handles empty input`() {
        val result = FunctionDeduplicationPass.optimize("")

        assertEquals(0, result.functionsDeduped)
        assertEquals("", result.optimizedContent)
        assertTrue(result.details.isEmpty())
    }

    @Test
    fun `FunctionDeduplicationPass handles input with no functions`() {
        val cText =
            """
            #include "game.h"
            const UINT8 data[] = {0x00, 0x01};
            """
                .trimIndent()

        val result = FunctionDeduplicationPass.optimize(cText)

        assertEquals(0, result.functionsDeduped)
        assertEquals(cText, result.optimizedContent)
    }

    @Test
    fun `FunctionDeduplicationPass handles functions with BANKED keyword`() {
        val cText =
            """
            void banked_scene_01() BANKED {
                load_scene_assets();
                activate_sprite(0);
            }

            void banked_scene_02() BANKED {
                load_scene_assets();
                activate_sprite(0);
            }
            """
                .trimIndent()

        val result = FunctionDeduplicationPass.optimize(cText)

        assertEquals(1, result.functionsDeduped)
        assertContains(result.optimizedContent, "/* Deduplicated: see banked_scene_01 */")
    }

    // =========================================================================
    // COutputOptimizer Tests
    // =========================================================================

    @Test
    fun `COutputOptimizer runs both passes in sequence and combines summary`() {
        val cText =
            """
            const UINT8 tiles_a[] = {0x10, 0x20, 0x30};
            const UINT8 tiles_b[] = {0x10, 0x20, 0x30};

            void render_a() {
                draw_sprite(0, 80, 72);
            }

            void render_b() {
                draw_sprite(0, 80, 72);
            }
            """
                .trimIndent()

        val optimizer =
            COutputOptimizer(
                sharedConstantTablesEnabled = true,
                functionDeduplicationEnabled = true,
            )

        val (optimizedFiles, summary) = optimizer.optimize(mapOf("main.c" to cText))

        assertEquals(1, summary.constantArraysDeduped, "Should have deduped 1 constant array")
        assertEquals(1, summary.functionsDeduped, "Should have deduped 1 function")
        assertEquals(2, summary.details.size, "Should have 2 detail entries")

        val optimizedContent = optimizedFiles["main.c"]!!
        assertContains(optimizedContent, "#define tiles_b tiles_a")
        assertContains(optimizedContent, "/* Deduplicated: see render_a */")
    }

    @Test
    fun `COutputOptimizer with constant pass disabled only runs function dedup`() {
        val cText =
            """
            const UINT8 arr_x[] = {0xAA, 0xBB};
            const UINT8 arr_y[] = {0xAA, 0xBB};

            void func_one() { do_work(); }
            void func_two() { do_work(); }
            """
                .trimIndent()

        val optimizer =
            COutputOptimizer(
                sharedConstantTablesEnabled = false,
                functionDeduplicationEnabled = true,
            )

        val (optimizedFiles, summary) = optimizer.optimize(mapOf("bank1.c" to cText))

        assertEquals(0, summary.constantArraysDeduped, "Constant pass is disabled, should be 0")
        assertEquals(1, summary.functionsDeduped, "Function dedup should run")

        val content = optimizedFiles["bank1.c"]!!
        // Arrays should NOT be deduped
        assertContains(content, "const UINT8 arr_x[]")
        assertContains(content, "const UINT8 arr_y[]")
        assertFalse(content.contains("#define arr_y arr_x"))
        // Functions should be deduped
        assertContains(content, "/* Deduplicated: see func_one */")
    }

    @Test
    fun `COutputOptimizer with function pass disabled only runs constant dedup`() {
        val cText =
            """
            const UINT8 pal_a[] = {0x01, 0x02, 0x03};
            const UINT8 pal_b[] = {0x01, 0x02, 0x03};

            void func_alpha() { render_sprite(); update_position(); }
            void func_beta() { render_sprite(); update_position(); }
            """
                .trimIndent()

        val optimizer =
            COutputOptimizer(
                sharedConstantTablesEnabled = true,
                functionDeduplicationEnabled = false,
            )

        val (optimizedFiles, summary) = optimizer.optimize(mapOf("main.c" to cText))

        assertEquals(1, summary.constantArraysDeduped, "Constant dedup should run")
        assertEquals(0, summary.functionsDeduped, "Function pass is disabled, should be 0")

        val content = optimizedFiles["main.c"]!!
        assertContains(content, "#define pal_b pal_a")
        // Functions should NOT be deduped
        assertContains(content, "void func_alpha()")
        assertContains(content, "void func_beta()")
    }

    @Test
    fun `COutputOptimizer with both passes disabled returns original content`() {
        val cText =
            """
            const UINT8 data[] = {0x00};
            void func() { noop(); }
            """
                .trimIndent()

        val optimizer =
            COutputOptimizer(
                sharedConstantTablesEnabled = false,
                functionDeduplicationEnabled = false,
            )

        val (optimizedFiles, summary) = optimizer.optimize(mapOf("main.c" to cText))

        assertEquals(0, summary.constantArraysDeduped)
        assertEquals(0, summary.functionsDeduped)
        assertEquals(cText, optimizedFiles["main.c"])
    }

    @Test
    fun `COutputOptimizer handles empty file map`() {
        val optimizer = COutputOptimizer()

        val (optimizedFiles, summary) = optimizer.optimize(emptyMap())

        assertTrue(optimizedFiles.isEmpty(), "Empty input should produce empty output")
        assertEquals(0, summary.constantArraysDeduped)
        assertEquals(0, summary.functionsDeduped)
        assertTrue(summary.details.isEmpty())
    }

    @Test
    fun `COutputOptimizer handles single file with no optimizable patterns`() {
        val cText =
            """
            #include "game.h"

            void main() {
                DISPLAY_ON;
                while (1) {
                    game_update();
                }
            }
            """
                .trimIndent()

        val optimizer = COutputOptimizer()

        val (optimizedFiles, summary) = optimizer.optimize(mapOf("main.c" to cText))

        assertEquals(0, summary.constantArraysDeduped)
        assertEquals(0, summary.functionsDeduped)
        assertEquals(cText, optimizedFiles["main.c"])
    }

    @Test
    fun `COutputOptimizer applies passes to each file independently`() {
        val file1 =
            """
            const UINT8 sprite_a[] = {0x10, 0x11};
            const UINT8 sprite_b[] = {0x10, 0x11};
            """
                .trimIndent()

        val file2 =
            """
            const UINT8 map_x[] = {0xAA, 0xBB};
            const UINT8 map_y[] = {0xAA, 0xBB};
            """
                .trimIndent()

        val optimizer = COutputOptimizer()

        val (optimizedFiles, summary) =
            optimizer.optimize(mapOf("main.c" to file1, "bank1.c" to file2))

        // Each file should be optimized independently
        assertEquals(2, summary.constantArraysDeduped, "Should dedup 1 array in each of 2 files")
        assertContains(optimizedFiles["main.c"]!!, "#define sprite_b sprite_a")
        assertContains(optimizedFiles["bank1.c"]!!, "#define map_y map_x")
    }

    // =========================================================================
    // Cross-file rewriter regex over-match guards (CR-02 / Plan 11.1-13)
    // These sentinels lock the COutputOptimizer cross-file rewriter regex shape
    // `\b<name>\s*\(` against future drift. The shape is aligned with
    // FunctionDeduplicationPass.kt:130 (intra-file rewriter). Each test uses
    // a two-file setup: main.c carries two identical function bodies (to trigger
    // dedup and produce a cross-file redirect), bank1.c carries the probe
    // pattern under test.
    // =========================================================================

    /**
     * CR-02 / Plan 11.1-13 — sentinel 1.
     *
     * An actual call site `foo(` in bank1.c IS rewritten to `bar(` by the cross-file pass. This is
     * the primary contract: the regex shape `\b<name>\s*\(` must match and rewrite genuine C call
     * sites.
     */
    @Test
    fun `cross-file rewriter rewrites actual call site`() {
        // Two identical functions in main.c trigger FunctionDeduplicationPass to dedup foo into bar
        // and produce a cross-file redirect {foo -> bar}.
        val mainC =
            """
            void bar() {
                do_the_work();
                finish_up();
            }

            void foo() {
                do_the_work();
                finish_up();
            }
            """
                .trimIndent()
        // bank1.c has a genuine call site: foo();
        val bank1C = "foo();\n"
        val optimizer =
            COutputOptimizer(
                sharedConstantTablesEnabled = false,
                functionDeduplicationEnabled = true,
            )
        val (result, _) = optimizer.optimize(mapOf("main.c" to mainC, "bank1.c" to bank1C))
        // The call site must be rewritten to the canonical name
        assertContains(result["bank1.c"]!!, "bar();")
        assertFalse(
            result["bank1.c"]!!.contains("foo()"),
            "Call site foo() must be rewritten to bar()",
        )
    }

    /**
     * CR-02 / Plan 11.1-13 — sentinel 2.
     *
     * Observed-behaviour lock: string literal containing `foo(x)` — does the cross-file rewriter
     * change it?
     *
     * The regex shape `\b<name>\s*\(` matches `foo(` inside a C string literal because the `(`
     * after `foo` in `"foo(x) called"` satisfies the `\s*\(` trailer. The line-prefix comment-skip
     * filter does NOT protect string literals (it only skips lines starting with `//`,
     * block-comment-start, or `*`). Therefore the cross-file rewriter WILL rewrite `foo(` inside
     * the string literal, producing `"bar(x) called"`. This is documented observed behaviour at
     * parity with FunctionDeduplicationPass intra-file rewriter, NOT a guarantee of string-literal
     * safety. Full string-literal hardening is tracked as WR-01/02/03 (out of scope for CR-02).
     */
    @Test
    fun `cross-file rewriter rewrites identifier inside string literal observed behaviour lock`() {
        val mainC =
            """
            void bar() {
                do_the_work();
                finish_up();
            }

            void foo() {
                do_the_work();
                finish_up();
            }
            """
                .trimIndent()
        // bank1.c contains a printf whose format string includes "foo(x)"
        val bank1C = "printf(\"foo(x) called\");\n"
        val optimizer =
            COutputOptimizer(
                sharedConstantTablesEnabled = false,
                functionDeduplicationEnabled = true,
            )
        val (result, _) = optimizer.optimize(mapOf("main.c" to mainC, "bank1.c" to bank1C))
        val bank1Out = result["bank1.c"]!!
        // OBSERVED BEHAVIOUR: the regex \bfoo\s*\( matches foo( inside the string literal.
        // The string literal IS rewritten. Assert the current (post-CR-02) behaviour.
        // If string-literal safety is added in a future plan, this assertion will change.
        assertTrue(
            bank1Out.contains("bar(x) called"),
            "Observed behaviour: string literal foo(x) is rewritten to bar(x) by the cross-file pass",
        )
    }

    /**
     * CR-02 / Plan 11.1-13 — sentinel 3.
     *
     * Observed-behaviour lock: inline block comment containing foo(y) — does the cross-file
     * rewriter change it?
     *
     * The line `int x = 5; [block-comment] foo(y) note [end-block-comment] stuff;` does NOT start
     * with `//`, block-comment-start, or `*`, so the line-prefix comment-skip filter does not
     * protect it. The regex `\bfoo\s*\(` matches `foo(` inside the inline block comment. The
     * cross-file rewriter WILL rewrite the comment content. This is documented observed behaviour
     * at parity with FunctionDeduplicationPass, NOT a guarantee of inline-comment safety. Full
     * comment-token hardening is tracked as WR-01/02/03 (out of scope for CR-02).
     */
    @Test
    fun `cross-file rewriter rewrites identifier inside inline block comment observed behaviour lock`() {
        val mainC =
            """
            void bar() {
                do_the_work();
                finish_up();
            }

            void foo() {
                do_the_work();
                finish_up();
            }
            """
                .trimIndent()
        // bank1.c contains an inline block comment with foo(y) inside it
        val bank1C = "int x = 5; /* foo(y) note */ stuff;\n"
        val optimizer =
            COutputOptimizer(
                sharedConstantTablesEnabled = false,
                functionDeduplicationEnabled = true,
            )
        val (result, _) = optimizer.optimize(mapOf("main.c" to mainC, "bank1.c" to bank1C))
        val bank1Out = result["bank1.c"]!!
        // OBSERVED BEHAVIOUR: the regex \bfoo\s*\( matches foo( inside the inline block comment.
        // The comment content IS rewritten. Assert the current (post-CR-02) behaviour.
        // If inline-comment safety is added in a future plan, this assertion will change.
        assertTrue(
            bank1Out.contains("bar(y) note"),
            "Observed behaviour: inline block comment foo(y) is rewritten to bar(y) by the cross-file pass",
        )
    }

    /**
     * CR-02 / Plan 11.1-13 — sentinel 4.
     *
     * Identifier SUBSTRING overlap is NOT rewritten as a call. `foo_helper()` contains `foo` as a
     * prefix, but the `\b` word-boundary at the start of the regex requires `foo` to be at a word
     * boundary on BOTH sides. Since `_` is a word character, `foo` in `foo_helper` is NOT followed
     * by a word boundary — the pattern `\bfoo\s*\(` does not match `foo_helper(`. The call site
     * `foo_helper();` is preserved verbatim, and only bare `foo()` call sites are rewritten.
     */
    @Test
    fun `cross-file rewriter identifier substring overlap is NOT rewritten as a call`() {
        val mainC =
            """
            void bar() {
                do_the_work();
                finish_up();
            }

            void foo() {
                do_the_work();
                finish_up();
            }
            """
                .trimIndent()
        // bank1.c has foo_helper() — substring of redirect name — plus a real foo() call
        val bank1C = "foo_helper();\nfoo();\n"
        val optimizer =
            COutputOptimizer(
                sharedConstantTablesEnabled = false,
                functionDeduplicationEnabled = true,
            )
        val (result, _) = optimizer.optimize(mapOf("main.c" to mainC, "bank1.c" to bank1C))
        val bank1Out = result["bank1.c"]!!
        // foo_helper must NOT be rewritten — it is not a call site for foo
        assertTrue(
            bank1Out.contains("foo_helper();"),
            "foo_helper() must be preserved verbatim — word-boundary guard",
        )
        // The real foo() call site must be rewritten
        assertTrue(bank1Out.contains("bar();"), "foo() call site must be rewritten to bar()")
    }

    /**
     * CR-02 / Plan 11.1-13 — sentinel 5.
     *
     * Leading-comment-prefix line skip (SEED-015 defense-in-depth preserved after CR-02 regex
     * change). A line that starts with `//` is skipped by the line-prefix filter at
     * COutputOptimizer lines 102-108. This filter was introduced as the SEED-015 fix to prevent
     * comment text like `// Trampoline: foo (bank 1)` from being rewritten. After the CR-02 regex
     * change (from `\b<name>\b` to `\b<name>\s*\(`), the line-prefix filter remains in place and
     * continues to protect `//`-prefixed lines. This sentinel asserts that the SEED-015 fix path is
     * NOT broken by the CR-02 regex change.
     */
    @Test
    fun `cross-file rewriter leading-comment-prefix line is skipped SEED-015 defense-in-depth`() {
        val mainC =
            """
            void bar() {
                do_the_work();
                finish_up();
            }

            void foo() {
                do_the_work();
                finish_up();
            }
            """
                .trimIndent()
        // bank1.c has a // comment line that mentions foo — must NOT be rewritten
        val bank1C = "// Trampoline: foo (bank 1)\nbar();\n"
        val optimizer =
            COutputOptimizer(
                sharedConstantTablesEnabled = false,
                functionDeduplicationEnabled = true,
            )
        val (result, _) = optimizer.optimize(mapOf("main.c" to mainC, "bank1.c" to bank1C))
        val bank1Out = result["bank1.c"]!!
        // The comment line must be preserved verbatim — line-prefix filter skips it
        assertTrue(
            bank1Out.contains("// Trampoline: foo (bank 1)"),
            "Comment line must be preserved verbatim (SEED-015 guard)",
        )
    }
}
