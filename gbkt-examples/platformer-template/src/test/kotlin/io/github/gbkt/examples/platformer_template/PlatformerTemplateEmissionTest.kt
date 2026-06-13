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
// invariant runs against a brace-walked function body, not the file.
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
}
