/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.test

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Acceptance test that locks the Phase 22 (FIX-07) invariants as an automated regression guard.
 *
 * Checks five gates so that the broken `.planning/phases/PHASE/evidence` pattern cannot silently
 * return in a future phase:
 *
 * **R1 — EVIDENCE_DIR no longer points to `.planning/phases`:** Every test file that still defines
 * an `EVIDENCE_DIR` companion must resolve it to a path under `build/` (gitignored scratch), never
 * to `.planning/phases/…/evidence`. Detected by scanning for lines that assign `EVIDENCE_DIR` to a
 * `.planning` path (path-construction check).
 *
 * **R1 — No new test file constructs a `File(…)` path into `.planning/phases`:** A line that
 * combines a `File(` / `.resolve(` call with a `.planning` path string is the canonical broken
 * pattern. Comments and assertion-message strings that merely mention `.planning/phases` for
 * traceability are permitted (they do not write to the filesystem).
 *
 * **R5 — No `.copy(gbcMode = true)` in non-comment code:** GBC mode is now auto-detected from the
 * ROM CGB header byte by `AgentSessionConfig.discoverFiles` (plan 22-02). Manual `.copy(gbcMode =
 * true)` calls in production test code cause inverted-palette captures on DMG-built ROMs. Only
 * comment lines are allowed to mention the pattern (e.g. KDoc explaining it is no longer needed).
 *
 * **R6 — Zero committed evidence files under `.planning/phases/PHASE/evidence/`:** All previously
 * committed evidence (PNGs, TXT, JSON) was `git rm`'d by plan 22-12. `git ls-files` must return
 * zero entries for that glob.
 *
 * **R6 — Exactly 22 golden PNGs under `gbkt-examples` test resources:** The 22 binding baseline
 * PNGs (6 metasprites + 16 platformer-template) were migrated to `src/test/resources/goldens/` in
 * plans 22-04 and 22-05. The count must stay at 22 to detect accidental additions or deletions.
 *
 * This file is EXCLUDED from its own scans (it necessarily references the forbidden patterns in
 * KDoc and string literals to document what to look for).
 */
class CleanTreeEvidenceAcceptanceTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Repo-root resolution
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Walks up from `System.getProperty("user.dir")` until it finds a directory containing
     * `settings.gradle.kts` (the Gradle multi-project root). Returns the repo root so that this
     * test works regardless of which module Gradle sets as the working directory.
     */
    private fun findRepoRoot(): File {
        var dir = File(System.getProperty("user.dir")).canonicalFile
        repeat(10) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        return dir
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Collects every Kotlin test source file under [root], excluding this test file itself. */
    private fun allTestKtFiles(root: File): List<File> {
        val selfName = "CleanTreeEvidenceAcceptanceTest.kt"
        return root
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != selfName }
            .filter { it.invariantSeparatorsPath.contains("/src/test/") }
            .toList()
    }

    /**
     * Returns true when [line] looks like a path-construction call that includes `.planning`.
     *
     * Matches patterns like `File(…".planning…`, `.resolve("…planning…"`, `Path("…planning…"`. Does
     * NOT match pure comment lines (starting with `//` after trimming) or lines where the
     * `.planning` string appears only as part of a non-path string (e.g. assertion messages that
     * merely cite a phase path for traceability).
     *
     * Heuristic: the line must contain both a file-construction keyword and the `.planning` token,
     * and the `.planning` string must appear inside quotes on the same line or in a continuation
     * string that is the first argument of `File(` / `.resolve(`.
     */
    private fun isPathConstructionWithPlanning(line: String): Boolean {
        val trimmed = line.trim()
        // Skip pure comment lines — they do not write to the filesystem.
        if (trimmed.startsWith("//") || trimmed.startsWith("*")) return false
        // The line must reference `.planning` (the path segment, not a field name).
        if (!trimmed.contains(".planning")) return false
        // Must also have a file-construction keyword on the same line to be a path expression.
        val hasFileConstruction =
            trimmed.contains("File(") ||
                trimmed.contains(".resolve(") ||
                trimmed.contains("Paths.get(") ||
                trimmed.contains("Path(")
        return hasFileConstruction
    }

    /**
     * Returns true when [line] contains `.copy(gbcMode` **in non-comment code**.
     *
     * KDoc and inline comments that say "no `.copy(gbcMode = true)` needed" are acceptable; only
     * live Kotlin code that calls `.copy(gbcMode` is the forbidden pattern.
     */
    private fun isCodeLevelGbcModeCopy(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.contains("copy(gbcMode")) return false
        // Pure comment lines: starts with // or * (inside block/KDoc comment)
        if (trimmed.startsWith("//") || trimmed.startsWith("*")) return false
        return true
    }

    /**
     * Returns true when [line] assigns `EVIDENCE_DIR` to a path that contains `.planning`.
     *
     * Catches the original broken pattern: `val EVIDENCE_DIR =
     * File(…).resolve("…planning/phases/…/evidence")` after the migration, EVIDENCE_DIR definitions
     * all resolve to `build/` so this should return false for every current file.
     */
    private fun isEvidenceDirPointingToPlanning(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.startsWith("//") || trimmed.startsWith("*")) return false
        if (!trimmed.contains("EVIDENCE_DIR")) return false
        if (!trimmed.contains(".planning")) return false
        // Must be an assignment (val/var EVIDENCE_DIR = … or EVIDENCE_DIR = …) not a comment
        return trimmed.contains("=") && trimmed.contains(".planning")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gate 1 — R1: EVIDENCE_DIR never assigned to .planning path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `R1 no EVIDENCE_DIR companion points to planning-phases path`() {
        val root = findRepoRoot()
        val offenders = mutableListOf<String>()
        for (file in allTestKtFiles(root)) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                if (isEvidenceDirPointingToPlanning(line)) {
                    offenders.add("${file.relativeTo(root)}:${index + 1}: $line")
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "R1 FAILED — EVIDENCE_DIR companion(s) pointing to .planning path found " +
                "(migrate to build/gbkt/test-evidence). Offending lines:\n${offenders.joinToString("\n")}",
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gate 2 — R1: No File(…) construction paths into .planning in test code
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `R1 no File path construction into planning-phases in test source`() {
        val root = findRepoRoot()
        val offenders = mutableListOf<String>()
        for (file in allTestKtFiles(root)) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                if (isPathConstructionWithPlanning(line)) {
                    offenders.add("${file.relativeTo(root)}:${index + 1}: $line")
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "R1 FAILED — File/Path construction(s) pointing into .planning found in test source " +
                "(these write to archived phase dirs; redirect to build/). Offending lines:\n${offenders.joinToString("\n")}",
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gate 3 — R5: No .copy(gbcMode = true) in non-comment test code
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `R5 no copy-gbcMode-true in non-comment test code`() {
        val root = findRepoRoot()
        val offenders = mutableListOf<String>()
        for (file in allTestKtFiles(root)) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                if (isCodeLevelGbcModeCopy(line)) {
                    offenders.add("${file.relativeTo(root)}:${index + 1}: $line")
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "R5 FAILED — .copy(gbcMode = true) found in live test code " +
                "(GBC mode is auto-detected by AgentSessionConfig.discoverFiles; remove manual override). " +
                "Offending lines:\n${offenders.joinToString("\n")}",
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gate 4 — R6: Zero committed evidence files under .planning/phases/**/evidence/**
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `R6 zero tracked evidence files under planning-phases evidence dirs`() {
        val root = findRepoRoot()

        // Prefer git ls-files for accuracy (only committed files, not untracked).
        val gitOutput =
            runCatching {
                    val process =
                        ProcessBuilder("git", "ls-files", ".planning/phases/**/evidence/**")
                            .directory(root)
                            .redirectErrorStream(true)
                            .start()
                    process.inputStream.bufferedReader().readText().trim().also {
                        process.waitFor()
                    }
                }
                .getOrNull()

        if (gitOutput != null) {
            assertEquals(
                "",
                gitOutput,
                "R6 FAILED — git ls-files reports committed evidence files under " +
                    ".planning/phases/**/evidence/ (run 'git rm' to remove them). Files:\n$gitOutput",
            )
        } else {
            // Fallback: filesystem check (git unavailable in test env)
            val evidenceFiles =
                root
                    .walkTopDown()
                    .filter { it.isFile }
                    .filter {
                        val p = it.invariantSeparatorsPath
                        p.contains("/.planning/phases/") && p.contains("/evidence/")
                    }
                    .toList()
            assertTrue(
                evidenceFiles.isEmpty(),
                "R6 FAILED — evidence files found under .planning/phases/**/evidence/ " +
                    "(git unavailable; filesystem fallback). Files:\n${evidenceFiles.map { it.relativeTo(root) }.joinToString("\n")}",
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gate 5 — R6: Exactly 22 golden PNGs under gbkt-examples test resources
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `R6 exactly 22 golden PNGs under gbkt-examples test resources`() {
        val root = findRepoRoot()
        val goldens =
            root
                .resolve("gbkt-examples")
                .walkTopDown()
                .filter { it.isFile && it.extension == "png" }
                .filter { it.invariantSeparatorsPath.contains("/src/test/resources/goldens/") }
                .toList()

        val goldenPaths = goldens.map { it.relativeTo(root).path }.sorted()
        assertEquals(
            22,
            goldens.size,
            "R6 FAILED — expected exactly 22 golden PNGs under gbkt-examples/**/src/test/resources/goldens/ " +
                "(6 metasprites + 16 platformer-template). Got ${goldens.size}:\n${goldenPaths.joinToString("\n")}",
        )
    }
}
