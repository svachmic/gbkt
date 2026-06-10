/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.gradle.internal.GbdkToolchain
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// PHASE 12.4 PLAN 04 — CONVERT SPRITES TASK FAIL-FAST CONTRACT
//
// Locks the 4 fail-fast GradleException paths in ConvertSpritesTask (D-04):
//
//   Test 1: Missing PNG → GradleException with "Sprite PNG not found",
//           metasprite id, and sprite(asset(...)) DSL reference.
//   Test 2: png2asset exit nonzero → GradleException with "png2asset failed",
//           "exit ", and "Flags:" (skips gracefully when GBDK absent).
//   Test 3: png2asset throws exception → GradleException with "png2asset threw"
//           (skips gracefully when GBDK absent).
//   Test 4: Zero-size tile array → GradleException with "empty tile array"
//           (calls fixZeroSizeArrays() via internal visibility).
//
// Tests 1 and 4 fire in the Kotlin-side guard BEFORE or AFTER png2asset is
// invoked, so they pass regardless of GBDK availability.
// Tests 2 and 3 require GBDK and skip gracefully when absent.
// =============================================================================

class ConvertSpritesTaskFailFastTest {

    @TempDir lateinit var tmpDir: File

    // -------------------------------------------------------------------------
    // Test 1 -- Missing PNG throws GradleException with diagnostic message
    //
    // Sidecar entry: {id:"foo", spritePath:"sprites/missing.png", mirrorDedup:false}
    // No PNG at {tmpDir}/res/sprites/missing.png.
    // Invokes convertSprites(). Expects GradleException with:
    //   - "Sprite PNG not found"
    //   - "missing.png"
    //   - "sprite(asset(\"sprites/missing.png\"))"
    //   - "for metasprite 'foo'"
    // -------------------------------------------------------------------------
    @Test
    fun `missing png throws gradle exception with diagnostic message`() {
        val assetDir = File(tmpDir, "res").apply { mkdirs() }
        // Intentionally do NOT create sprites/missing.png

        val metadataFile = File(tmpDir, "game_metadata.json")
        metadataFile.writeText(
            """
            {
              "sprites": [
                {
                  "id": "foo",
                  "spritePath": "sprites/missing.png",
                  "mirrorDedup": false
                }
              ]
            }
            """
                .trimIndent()
        )

        val outputDir = File(tmpDir, "out").apply { mkdirs() }
        // Need a real GBDK dir to pass the png2asset-exists check, or a fake one
        // that has a dummy png2asset file so the task reaches the missing-PNG guard.
        // The missing-PNG guard fires BEFORE png2asset invocation, so we just need
        // the png2asset file to "exist" (content doesn't matter — it won't be run).
        val fakePng2assetDir = File(tmpDir, "fake-gbdk/bin").apply { mkdirs() }
        val fakePng2asset = File(fakePng2assetDir, "png2asset").apply { writeText("#!/bin/sh\n") }
        fakePng2asset.setExecutable(true)

        val project = ProjectBuilder.builder().withProjectDir(tmpDir).build()
        val task =
            project.tasks
                .register("convertSpritesTest", ConvertSpritesTask::class.java) {
                    gbdkHome.set(File(tmpDir, "fake-gbdk").absolutePath)
                    assetDirectory.set(assetDir)
                    this.metadataFile.set(metadataFile)
                    cSourceDir.set(outputDir)
                }
                .get()

        val ex = assertFailsWith<GradleException> { task.convertSprites() }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Sprite PNG not found"), "Expected 'Sprite PNG not found' in: $msg")
        assertTrue(msg.contains("missing.png"), "Expected 'missing.png' in: $msg")
        assertTrue(
            msg.contains("""sprite(asset("sprites/missing.png"))"""),
            "Expected DSL declaration in: $msg",
        )
        assertTrue(msg.contains("for metasprite 'foo'"), "Expected metasprite id in: $msg")
    }

    // -------------------------------------------------------------------------
    // Test 2 -- png2asset exit nonzero throws GradleException
    //
    // Supplies a 1-byte file with .png extension that png2asset rejects.
    // Skips gracefully when GBDK is absent.
    // Asserts message contains "png2asset failed", "exit ", "Flags:".
    // -------------------------------------------------------------------------
    @Test
    fun `png2asset exit nonzero throws gradle exception`() {
        // Skip when png2asset is not discoverable (e.g., CI without GBDK).
        val gbdkDir =
            try {
                GbdkToolchain.find(null)
            } catch (_: Exception) {
                return
            }
        val png2asset = GbdkToolchain.getPng2asset(gbdkDir)
        if (!png2asset.exists()) return

        val assetDir = File(tmpDir, "res").apply { mkdirs() }
        val spritesDir = File(assetDir, "sprites").apply { mkdirs() }
        // Write a 1-byte file as "bad.png" — png2asset will reject it with nonzero exit
        val badPng = File(spritesDir, "bad.png").apply { writeBytes(byteArrayOf(0x00)) }
        assertTrue(badPng.exists(), "Bad PNG fixture must exist")

        val metadataFile = File(tmpDir, "game_metadata.json")
        metadataFile.writeText(
            """
            {
              "sprites": [
                {
                  "id": "bad",
                  "spritePath": "sprites/bad.png",
                  "mirrorDedup": false
                }
              ]
            }
            """
                .trimIndent()
        )

        val outputDir = File(tmpDir, "out").apply { mkdirs() }

        val project = ProjectBuilder.builder().withProjectDir(tmpDir).build()
        val task =
            project.tasks
                .register("convertSpritesTest", ConvertSpritesTask::class.java) {
                    gbdkHome.set(gbdkDir.absolutePath)
                    assetDirectory.set(assetDir)
                    this.metadataFile.set(metadataFile)
                    cSourceDir.set(outputDir)
                }
                .get()

        val ex = assertFailsWith<GradleException> { task.convertSprites() }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("png2asset failed"), "Expected 'png2asset failed' in: $msg")
        assertTrue(msg.contains("exit "), "Expected 'exit ' in: $msg")
        assertTrue(msg.contains("Flags:"), "Expected 'Flags:' in: $msg")
    }

    // -------------------------------------------------------------------------
    // Test 3 -- png2asset throws exception → GradleException with "png2asset threw"
    //
    // Injects a non-executable placeholder as png2asset so exec throws.
    // The png2asset binary check passes (file exists), but exec fails at runtime.
    // Asserts message contains "png2asset threw".
    //
    // Note: On some platforms, executing a non-binary may result in exit code ≠ 0
    // rather than an exception. In that case, the exit-nonzero branch (Test 2) fires.
    // The test therefore accepts either "png2asset threw" or "png2asset failed" to
    // cover both branches (both surface the failure rather than silently generating a stub).
    // -------------------------------------------------------------------------
    @Test
    fun `png2asset exception throws gradle exception`() {
        val assetDir = File(tmpDir, "res").apply { mkdirs() }
        val spritesDir = File(assetDir, "sprites").apply { mkdirs() }
        // Write a minimal valid PNG so the missing-PNG guard doesn't fire
        val pngFile = File(spritesDir, "test.png")
        writeSpritePng(pngFile, width = 8, height = 8)

        val metadataFile = File(tmpDir, "game_metadata.json")
        metadataFile.writeText(
            """
            {
              "sprites": [
                {
                  "id": "test",
                  "spritePath": "sprites/test.png",
                  "mirrorDedup": false
                }
              ]
            }
            """
                .trimIndent()
        )

        // Inject a non-executable placeholder as png2asset binary:
        // It exists (passes the png2assetExe.exists() check) but cannot be exec'd.
        val fakeBinDir = File(tmpDir, "fake-gbdk/bin").apply { mkdirs() }
        val fakePng2asset = File(fakeBinDir, "png2asset")
        fakePng2asset.writeText("NOT A BINARY")
        fakePng2asset.setExecutable(false)
        // We leave it non-executable so exec will throw an exception.
        // If the OS still "executes" it (returns a nonzero exit code instead), the
        // exit-nonzero branch fires, which also throws GradleException — still GREEN.

        val outputDir = File(tmpDir, "out").apply { mkdirs() }

        val project = ProjectBuilder.builder().withProjectDir(tmpDir).build()
        val task =
            project.tasks
                .register("convertSpritesTest", ConvertSpritesTask::class.java) {
                    gbdkHome.set(File(tmpDir, "fake-gbdk").absolutePath)
                    assetDirectory.set(assetDir)
                    this.metadataFile.set(metadataFile)
                    cSourceDir.set(outputDir)
                }
                .get()

        val ex = assertFailsWith<GradleException> { task.convertSprites() }
        val msg = ex.message ?: ""
        // Either "png2asset threw" (exception path) or "png2asset failed" (exit nonzero)
        // Both are fail-fast GradleExceptions — no silent stub generation.
        assertTrue(
            msg.contains("png2asset threw") || msg.contains("png2asset failed"),
            "Expected 'png2asset threw' or 'png2asset failed' in: $msg",
        )
    }

    // -------------------------------------------------------------------------
    // Test 4 -- Zero-size array throws GradleException with "empty tile array"
    //
    // Writes a synthetic .c file matching the _tiles[0] = {} pattern that
    // fixZeroSizeArrays() detects, then calls fixZeroSizeArrays(outputC, pngFile)
    // directly (exposed as internal for testing — see ConvertSpritesTask KDoc).
    // Asserts message contains "empty tile array".
    // -------------------------------------------------------------------------
    @Test
    fun `zero size array throws gradle exception`() {
        val outputDir = File(tmpDir, "out").apply { mkdirs() }
        val outputC = File(outputDir, "test.c")
        // Write a synthetic .c file with the zero-size array pattern
        outputC.writeText(
            """
            /* Auto-generated by png2asset */
            #include <stdint.h>
            #include <gbdk/platform.h>
            const uint8_t test_tiles[0] = {};
            const uint8_t test_map[] = {};
            """
                .trimIndent()
        )
        val pngFile = File(tmpDir, "sprites/test.png")
        File(tmpDir, "sprites").mkdirs()
        writeSpritePng(pngFile, width = 8, height = 8)

        val ex = assertFailsWith<GradleException> { fixZeroSizeArrays(outputC, pngFile) }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("empty tile array"), "Expected 'empty tile array' in: $msg")
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Write a minimal valid PNG with given dimensions to the target file. */
    private fun writeSpritePng(target: File, width: Int, height: Int) {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        ImageIO.write(img, "PNG", target)
        require(target.isFile && target.length() > 0) {
            "test fixture PNG was not written: ${target.absolutePath}"
        }
    }
}
