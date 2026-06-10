/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.platformer_template

import java.io.File
import kotlin.test.*

// =============================================================================
// PLATFORMER-TEMPLATE C EMISSION INVARIANTS — Phase 12 Wave-0 scaffold
//
// Wave 0 ships only the brace-walk helper + EVIDENCE_DIR companion object.
// Plans 12-09 / 12-09b / 12-12 / 12-14 / 12-15 add the per-anchor invariant
// tests bound to the 5 UAT anchors (D-16 invariants 1..5).
//
// Scope-level grep gate (per CLAUDE.md §"Scope-level grep gates"): every
// invariant runs against a brace-walked function body, not the file. The
// `extractFunctionBody()` helper below is the locking pattern.
// =============================================================================

class PlatformerTemplateEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe).
         *
         * `user.dir` resolves to the Gradle project's working directory, which inside a Claude Code
         * worktree is the worktree root — not the main repository. Hard-coding the main-repo
         * absolute path would silently route evidence files outside the active checkout and miss
         * the commit (#3099 worktree path safety).
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../../.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape"
                )
                .normalize()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a C function body by brace-walking from the first line containing `void
     * ${functionName}(` until the matching closing brace at depth zero.
     *
     * The returned blob includes the signature line and the closing brace, so downstream
     * `.contains()` checks operate ONLY on tokens that live inside the named function — never on
     * tokens from unrelated functions in the same bank file (per CLAUDE.md §"Scope-level grep
     * gates").
     */
    private fun extractFunctionBody(cSource: String, functionName: String): String {
        val lines = cSource.lines()
        val startIdx = lines.indexOfFirst { it.contains("void $functionName(") }
        if (startIdx == -1) return ""
        val body = StringBuilder()
        var depth = 0
        var started = false
        for (i in startIdx until lines.size) {
            val line = lines[i]
            body.appendLine(line)
            for (ch in line) {
                if (ch == '{') {
                    depth++
                    started = true
                }
                if (ch == '}') depth--
            }
            if (started && depth == 0) break
        }
        return body.toString()
    }
}
