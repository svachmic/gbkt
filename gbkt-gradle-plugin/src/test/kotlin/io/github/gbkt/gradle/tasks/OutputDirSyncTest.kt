/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class OutputDirSyncTest {

    @TempDir lateinit var outputDir: File

    @Test
    fun `basic - stale file is deleted, emitted file survives`() {
        File(outputDir, "main.c").writeText("// stub\n")
        File(outputDir, "bank1.c").writeText("// stub\n")

        val deleted = syncOutputDir(outputDir, setOf("main.c"))

        assertTrue(File(outputDir, "main.c").exists(), "emitted file must survive sync")
        assertFalse(File(outputDir, "bank1.c").exists(), "stale file must be deleted")
        assertEquals(listOf("bank1.c"), deleted)
    }

    @Test
    fun `dotfile exemption - gitkeep survives even when not in emittedSet`() {
        File(outputDir, "main.c").writeText("// stub\n")
        File(outputDir, ".gitkeep").writeText("")

        val deleted = syncOutputDir(outputDir, setOf("main.c"))

        assertTrue(File(outputDir, ".gitkeep").exists(), ".gitkeep must survive sync")
        assertTrue(deleted.isEmpty(), "nothing should be deleted")
    }

    @Test
    fun `sidecar enumeration - emittedSet sidecars survive but orphaned gbkt-map is deleted`() {
        File(outputDir, "main.c").writeText("// stub\n")
        File(outputDir, "gbkt-build.properties").writeText("# stub\n")
        File(outputDir, "main.c.gbkt.map").writeText("{}\n")
        File(outputDir, "bank1.c.gbkt.map").writeText("{}\n")
        File(outputDir, "game_metadata.json").writeText("{}\n")

        // Note: main.c.gbkt.map survives via emittedSet (mirrors v1 production path: files.keys +
        // writtenSidecars)
        val deleted = syncOutputDir(outputDir, setOf("main.c", "main.c.gbkt.map"))

        assertTrue(
            File(outputDir, "gbkt-build.properties").exists(),
            "gbkt-build.properties must survive",
        )
        assertTrue(
            File(outputDir, "main.c.gbkt.map").exists(),
            "main.c.gbkt.map must survive — it is in emittedSet",
        )
        assertFalse(
            File(outputDir, "bank1.c.gbkt.map").exists(),
            "bank1.c.gbkt.map MUST be deleted — not in emittedSet",
        )
        assertTrue(
            File(outputDir, "game_metadata.json").exists(),
            "game_metadata.json must survive",
        )
        assertEquals(
            listOf("bank1.c.gbkt.map"),
            deleted,
            "orphaned source-map sidecar must be the only deletion",
        )
    }

    @Test
    fun `idempotency - calling sync twice is a no-op`() {
        File(outputDir, "main.c").writeText("// stub\n")
        File(outputDir, "bank1.c").writeText("// stub\n")

        val firstDeleted = syncOutputDir(outputDir, setOf("main.c"))
        val secondDeleted = syncOutputDir(outputDir, setOf("main.c"))

        assertEquals(listOf("bank1.c"), firstDeleted, "first call must delete bank1.c")
        assertTrue(secondDeleted.isEmpty(), "second call must return empty list")
        assertTrue(
            File(outputDir, "main.c").exists(),
            "main.c must still be present after both calls",
        )
    }

    @Test
    fun `read-only file failure mode`() {
        File(outputDir, "main.c").writeText("// stub\n")
        val lockedFile = File(outputDir, "bank1.c").apply { writeText("// stub\n") }

        lockedFile.setWritable(false)
        outputDir.setWritable(false)

        try {
            val deleted = syncOutputDir(outputDir, setOf("main.c"))

            assertTrue(lockedFile.exists(), "locked file must survive (warn-and-skip, not fail)")
            assertFalse(deleted.contains("bank1.c"), "locked file must NOT be in deleted list")
        } finally {
            outputDir.setWritable(true)
        }
    }

    @Test
    fun `missing outputDir handled safely`() {
        val nonExistent = File(outputDir.parentFile, "does-not-exist-${System.nanoTime()}")

        val deleted = syncOutputDir(nonExistent, setOf("main.c"))

        assertTrue(deleted.isEmpty(), "missing outputDir must return empty list without exception")
    }

    @Test
    fun `stale source-map sidecar is deleted when its companion C file is dropped`() {
        File(outputDir, "main.c").writeText("// stub\n")
        File(outputDir, "main.c.gbkt.map").writeText("{}\n")
        File(outputDir, "bank1.c").writeText("// stub\n")
        File(outputDir, "bank1.c.gbkt.map").writeText("{}\n")

        val deleted = syncOutputDir(outputDir, setOf("main.c", "main.c.gbkt.map"))

        assertFalse(File(outputDir, "bank1.c").exists())
        assertFalse(
            File(outputDir, "bank1.c.gbkt.map").exists(),
            "orphaned sidecar must be deleted — same staleness contract as .c files (CR-01 fix)",
        )
        assertEquals(setOf("bank1.c", "bank1.c.gbkt.map"), deleted.toSet())
    }

    @Test
    fun `sidecar not written this run does not protect prior-run stale copy`() {
        // Simulate: build N wrote main.c + main.c.gbkt.map
        File(outputDir, "main.c").writeText("// new build\n")
        File(outputDir, "main.c.gbkt.map").writeText("{\"stale\": true}\n")

        // Build N+1: main.c was rewritten but source-map generation FAILED this run.
        // emittedSet must NOT include "main.c.gbkt.map".
        val deleted = syncOutputDir(outputDir, setOf("main.c"))

        assertFalse(
            File(outputDir, "main.c.gbkt.map").exists(),
            "Stale sidecar from prior build must be deleted when this run did not write a new one",
        )
        assertEquals(listOf("main.c.gbkt.map"), deleted)
    }
}
