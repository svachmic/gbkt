/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.core.dsl.resolveZoneSize
import io.github.gbkt.gradle.internal.GbdkToolchain
import java.io.File
import javax.imageio.ImageIO
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.json.JSONObject

/**
 * Phase 11.2 (REQ-2, D-A1): NEW path -- Gradle png2asset pipeline for user-supplied zone tileset
 * PNGs.
 *
 * Sibling to [ConvertSpritesTask]. Mirrors its png2asset invocation + GBDK_HOME discovery; diverges
 * on:
 * 1. **D-C2** -- fail-fast on missing PNG instead of stub fallback. Zones are load-bearing (a
 *    missing zone tileset breaks gameplay rendering), not optional like sprites.
 * 2. **D-C4** -- Kotlin-side IHDR validation for dimensions (multiple of 8) BEFORE invoking
 *    png2asset. png2asset returns exit 0 even on decoder errors per the plan 02 spike log, so the
 *    Kotlin guard is the primary enforcement.
 * 3. **D-C3** -- `MAX_ZONE_TILESET_TILES = 192` worst-case cap (visual-tile count, before
 *    png2asset's dedup). Larger tilesets must be split across banks in a future phase.
 *
 * Consumed by SceneVisitor's zone-load block via the synthesized `_zone_<id>_tileset.h` header,
 * which exposes two macros:
 * - `_zone_<id>_tileset` -- alias to the native png2asset stem array (e.g. `checker_tiles`).
 * - `_zone_<id>_tileset_count` -- visual-tile count (used by `set_bkg_data`).
 *
 * See CONVENTIONS.md §"Tile pixel data emission: two paths, when to use which" and SEED-017 for the
 * deferred sport-path unification.
 *
 * ## Locked png2asset flag set (Phase 12.8 D-02 revision — conditional)
 *
 * Base (always emitted): `<png> -o <output>.c -map -spr8x8 -bpp 2 -noflip` Indexed-only (appended
 * when [isIndexedPng] returns true): `-keep_palette_order`
 *
 * The Plan 11.2-02 spike originally REMOVED `-keep_palette_order` after observing
 * `keep_palette_order only works with indexed png images` on an RGB test PNG. Phase 12.8 W3
 * re-scoped that finding: the rejection is RUNTIME-GUARDED via [isIndexedPng] so the flag is
 * emitted iff the PNG's IHDR color-type byte is `3` (indexed/colormap). Indexed BG tilesets
 * (`world1-tileset.png` / `world2-tileset.png` are 8-bit colormap per `file(1)`) get the flag
 * automatically per the Reference Makefile (`platformer_template/Makefile:82`); RGB/RGBA inputs
 * (banks/checker.png, platformer-template title-screen.png + next-level.png) keep the original
 * 8-flag set and continue to compile cleanly.
 *
 * ## Output layout (D-B4 flat path)
 *
 * Pixel-bytes `.c` file lives in HOME bank (no `-b` flag passed -- png2asset's default HOME
 * residency per D-B2). Both `.c` and `.h` land flat under `cSourceDir`:
 * - `cSourceDir/_zone_<sanitized>_tileset.c`
 * - `cSourceDir/_zone_<sanitized>_tileset.h`
 */
@CacheableTask
abstract class ConvertZoneTilesetsTask
@Inject
constructor(private val execOperations: ExecOperations) : DefaultTask() {

    /** Path to GBDK installation directory (must contain `bin/png2asset`). */
    @get:Input abstract val gbdkHome: Property<String>

    /** Directory containing zone tileset PNG assets (e.g. `res/`). */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetDirectory: DirectoryProperty

    /**
     * The `game_metadata.json` file produced by GBDKPipeline.buildMetadataFile (Plan 01). Contains
     * the `zoneTilesets` array driving this task.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadataFile: RegularFileProperty

    /** Directory where the synthesized `.c` + `.h` files are placed (D-B4 flat layout). */
    @get:OutputDirectory abstract val cSourceDir: DirectoryProperty

    init {
        description =
            "Convert zone tileset PNG assets to GBDK C tile data using png2asset (Phase 11.2)"
        group = "gbkt"
    }

    @TaskAction
    fun convertZoneTilesets() {
        val metaFile = metadataFile.orNull?.asFile
        if (metaFile == null || !metaFile.exists()) {
            logger.lifecycle(
                "ConvertZoneTilesetsTask: No game_metadata.json -- skipping zone tileset conversion"
            )
            return
        }

        val json = JSONObject(metaFile.readText())
        val zoneTilesets = json.optJSONArray("zoneTilesets")
        if (zoneTilesets == null || zoneTilesets.length() == 0) {
            logger.lifecycle("ConvertZoneTilesetsTask: No zone tilesets -- skipping")
            return
        }

        val assetDir = assetDirectory.orNull?.asFile
        if (assetDir == null || !assetDir.exists()) {
            logger.warn(
                "ConvertZoneTilesetsTask: zoneTilesets manifest carries entries but no asset directory is configured -- cannot resolve PNG paths"
            )
            return
        }

        val gbdkDir = File(gbdkHome.get())
        val png2assetExe = GbdkToolchain.getPng2asset(gbdkDir)

        val outputDir = cSourceDir.get().asFile
        outputDir.mkdirs()

        logger.lifecycle(
            "ConvertZoneTilesetsTask: Converting ${zoneTilesets.length()} zone tileset(s)"
        )

        for (i in 0 until zoneTilesets.length()) {
            val entry = zoneTilesets.getJSONObject(i)
            val zoneId = entry.getString("id")
            val pngRelPath = entry.getString("path")
            val sanitized = entry.getString("sanitizedSymbol")
            val pngFile = assetDir.resolve(pngRelPath)

            // Plan 13.4-02 (REQ-14 sentinel-aware read): mapWidth/mapHeight may be JSONObject.NULL
            // (auto sentinel emitted when the DSL author omitted size()). When non-null, use as the
            // explicit size. When null, derive from the tilemap PNG below via resolveZoneSize.
            val explicitSize: Pair<Int, Int>? =
                if (!entry.isNull("mapWidth") && !entry.isNull("mapHeight"))
                    entry.getInt("mapWidth") to entry.getInt("mapHeight")
                else null

            // Phase 12.1-01 Task 2 (D-01): consume the per-zone bank number propagated from
            // GBDKPipeline.allocateZoneBanks via buildMetadataFile (Task 1). The synthesized
            // _zone_<id>_tilemap.c file MUST carry `#pragma bank N` so SDCC synthesizes the
            // __bank__zone_<id>_tilemap companion symbol that BANK(...) expands to — without
            // this pragma, SDCC reports error 20 `Undefined identifier '__bank__zone_<id>_tilemap'`
            // at link time (Defect 2 of Phase 12 first-buildrom).
            require(entry.has("bank")) {
                "zoneTilesets entry for zone $zoneId missing 'bank' — pipeline did not " +
                    "propagate allocateZoneBanks output through buildMetadataFile " +
                    "(Phase 12.1 D-01). Re-run :generateC against the current backend."
            }
            val zoneBank = entry.getInt("bank")

            // Phase 12.2 R-04: optional separate tilemap PNG path. When non-null, triggers the
            // D-01 two-invocation png2asset path (-maps_only -source_tileset). When null, the
            // tileset PNG doubles as the tilemap source (D-01 Path A one-invocation form).
            val tilemapRelPath: String? =
                if (entry.isNull("tilemapPath")) {
                    null
                } else {
                    entry.optString("tilemapPath", "").takeIf { it.isNotEmpty() }
                }
            val tilemapPngFile: File? = tilemapRelPath?.let { assetDir.resolve(it) }

            // Defensive path-traversal guard (T-11.2-03-03 in threat register).
            val assetCanonical = assetDir.canonicalPath
            val pngCanonical = pngFile.canonicalPath
            require(
                pngCanonical == assetCanonical ||
                    pngCanonical.startsWith(assetCanonical + File.separator)
            ) {
                "Zone $zoneId tileset path escapes asset directory: $pngRelPath"
            }

            // Phase 12.2 REQ-4: same path-traversal guard for the optional tilemap PNG.
            if (tilemapPngFile != null) {
                val tilemapCanonical = tilemapPngFile.canonicalPath
                require(
                    tilemapCanonical == assetCanonical ||
                        tilemapCanonical.startsWith(assetCanonical + File.separator)
                ) {
                    "Zone $zoneId tilemap path escapes asset directory: $tilemapRelPath"
                }
            }

            // Item 1 (EQR-260605): zone-scoped tilemap-PNG existence guard must run BEFORE
            // ImageIO.read() so the zone-scoped "tilemap PNG not found" message wins over the
            // lower-layer "Can't read input file!" that ImageIO would otherwise throw.
            if (tilemapPngFile != null) {
                require(tilemapPngFile.isFile) {
                    "Zone $zoneId tilemap PNG not found at ${tilemapPngFile.absolutePath} (Phase 12.2 REQ-4)"
                }
            }

            // Plan 13.4-02 (REQ-14 D-03): derive PNG tile dims from the tilemap PNG ONLY.
            // NEVER derive from the tileset PNG — tilemap PNG reflects the real level layout.
            // When no tilemap PNG is present (tileset-only zone), derivedDims is null and
            // resolveZoneSize falls back to 20×18.
            val derivedDims: Pair<Int, Int>? =
                tilemapPngFile?.let { png ->
                    val img =
                        ImageIO.read(png)
                            ?: error(
                                "Zone $zoneId tilemap PNG could not be decoded for size derivation " +
                                    "at ${png.absolutePath}"
                            )
                    (img.width / 8) to (img.height / 8)
                }
            val (finalW, finalH) = resolveZoneSize(explicitSize, derivedDims)

            convertOneTileset(
                zoneId,
                pngFile,
                tilemapPngFile,
                sanitized,
                png2assetExe,
                outputDir,
                finalW,
                finalH,
                zoneBank,
            )
        }
    }

    /**
     * Convert a single zone tileset PNG via png2asset and synthesize the gbkt alias header.
     *
     * Order of operations (fail-fast guards run BEFORE the exec):
     * 1. **D-C2** -- PNG must exist on disk.
     * 2. **D-C4** -- PNG width and height must both be multiples of 8.
     * 3. **D-C3** -- Visual tile count must be in 1..[MAX_ZONE_TILESET_TILES].
     * 4. **D-C1** -- png2asset must exist (the helper [GbdkToolchain.getPng2asset] returns the path
     *    unconditionally; we re-check `exists()` here for the explicit fail-fast).
     * 5. Invoke png2asset with the spike-locked flag set.
     * 6. Verify the `.c` output materialised (png2asset returns exit 0 even on decoder errors per
     *    the spike log; output-file presence is the reliable secondary guard).
     * 7. Synthesize the `_zone_<sanitized>_tileset.h` header with the D-A3 alias + count macro.
     *
     * @param screenMapWidth UNUSED after Phase 12.2 (was the Plan 11.1-17 synthesizer input). Kept
     *   in the signature because the metadata JSON still carries `mapWidth`. A future cleanup phase
     *   (out of scope here per terminal-subphase rule) can remove the field from JSON and the
     *   parameter from this function. WIDTH/HEIGHT macros are now derived from real PNG IHDR inside
     *   [synthesizeZoneTilesetHeader] via `tilemapPng` (D-01).
     * @param screenMapHeight UNUSED after Phase 12.2 (see screenMapWidth).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun convertOneTileset(
        zoneId: String,
        pngFile: File,
        tilemapPngFile: File?,
        sanitized: String,
        png2assetExe: File,
        outputDir: File,
        screenMapWidth: Int,
        screenMapHeight: Int,
        zoneBank: Int,
    ) {
        // D-C2: missing PNG fails fast (zones are load-bearing; no stub fallback).
        require(pngFile.isFile) {
            "Zone $zoneId tileset PNG not found at ${pngFile.absolutePath} (D-C2)"
        }

        // Phase 12.2 REQ-4: when tilemap() is set, the file must exist on disk.
        // This is D-01's "genuine error case" — distinct from the absent-tilemap case which has
        // a real one-invocation semantic.
        if (tilemapPngFile != null) {
            require(tilemapPngFile.isFile) {
                "Zone $zoneId tilemap PNG not found at ${tilemapPngFile.absolutePath} (Phase 12.2 REQ-4)"
            }
        }

        // D-C4: PNG dimensions must be multiples of 8 (Game Boy tile size).
        // ImageIO is used because png2asset would silently exit 0 on a dimension error per the
        // plan 02 spike log -- Kotlin-side validation is the primary enforcement.
        val image =
            ImageIO.read(pngFile)
                ?: error(
                    "Zone $zoneId tileset PNG could not be decoded (ImageIO returned null) at ${pngFile.absolutePath}"
                )
        val w = image.width
        val h = image.height
        require(w % 8 == 0 && h % 8 == 0) {
            "Zone $zoneId tileset PNG width/height must be a multiple of 8 (Game Boy tile size); got ${w}x${h} at ${pngFile.absolutePath} (D-C4)"
        }

        // D-C3: visual tile count cap (worst-case; png2asset's dedup may shrink the actual array).
        val tileCount = (w / 8) * (h / 8)
        require(tileCount in 1..MAX_ZONE_TILESET_TILES) {
            "Zone $zoneId tileset > $MAX_ZONE_TILESET_TILES tiles unsupported in phase 11.2; deferred to a future multi-bank-tile-data phase. Got $tileCount tiles at ${pngFile.absolutePath} (D-C3)"
        }

        // D-C1: png2asset binary must exist (GbdkToolchain.getPng2asset returns the path
        // unconditionally; the file-existence check is the explicit fail-fast).
        require(png2assetExe.isFile) {
            "Zone $zoneId tileset conversion needs png2asset at ${png2assetExe.absolutePath} -- " +
                "install GBDK or set GBDK_HOME (D-C1)"
        }

        val outputC = File(outputDir, "_zone_${sanitized}_tileset.c")
        val outputH = File(outputDir, "_zone_${sanitized}_tileset.h")

        // -keep_palette_order re-activated in Phase 12.9 W4 (D-03); gated by isIndexedPng()
        val baseArgs =
            listOf(
                pngFile.absolutePath,
                "-o",
                outputC.absolutePath,
                "-map",
                "-spr8x8",
                "-bpp",
                "2",
                "-noflip",
            )
        val args = if (isIndexedPng(pngFile)) baseArgs + listOf("-keep_palette_order") else baseArgs

        logger.lifecycle("  Converting zone $zoneId: ${pngFile.name} -> ${outputC.name}")
        val result =
            execOperations.exec {
                executable = png2assetExe.absolutePath
                setArgs(args)
                isIgnoreExitValue = true
            }

        if (result.exitValue != 0) {
            error(
                "png2asset failed for zone $zoneId (PNG ${pngFile.absolutePath}); exit=${result.exitValue}. See log above for png2asset stderr."
            )
        }

        // Secondary guard: png2asset exits 0 even on decoder errors (plan 02 spike). The .c output
        // must exist and be non-empty for the conversion to be considered successful.
        if (!outputC.isFile || outputC.length() == 0L) {
            error(
                "png2asset reported success but did not produce $outputC for zone $zoneId (PNG ${pngFile.absolutePath}). Treat any stderr above as failure."
            )
        }

        // Phase 13.7 Plan 05 (Req 4 / D-06 WARNING): if the emitted BG palette luminance order is
        // the strict reverse of the source PNG PLTE, emit one non-fatal ASCII-only logger.warn
        // naming the offending PNG. Build still exits 0 (warn-not-fail; strict-fail is out of
        // scope). Gated on isIndexedPng(pngFile) — same condition as -keep_palette_order above.
        val paletteSymbolForPolarity = "_zone_${sanitized}_tileset_palettes"
        if (isIndexedPng(pngFile)) {
            val emittedValues = parsePaletteRgb555Values(outputC, paletteSymbolForPolarity)
            if (emittedValues != null && checkPalettePolarity(pngFile, emittedValues)) {
                logger.warn(
                    "${pngFile.name} emitted palette luminance order appears inverted " +
                        "relative to source PNG PLTE; " +
                        "check -keep_palette_order and source palette order"
                )
            }
        }

        // Phase 12.9 D-03(b): parse palette array dimension from the png2asset-emitted .c to derive
        // sub-palette count. Fallback 4 matches the pre-fix shape (1 sub-palette × 4 colors = 4
        // array entries) so non-indexed PNGs (which don't emit a _palettes[] array) get a safe
        // default. coerceAtLeast(1) defends against pathological pure-grayscale outputs.
        val paletteSymbol = "_zone_${sanitized}_tileset_palettes"
        val paletteArrayDim = parsePaletteArrayDim(outputC, paletteSymbol) ?: 4
        val subPaletteCount = (paletteArrayDim / 4).coerceAtLeast(1)

        // Native stem rule (corrected during plan 11.2-04 buildRom smoke): png2asset names the
        // byte array after the OUTPUT-FILE basename (not the input PNG basename). With
        // `-o /tmp/_zone_play_zone_tileset.c`, png2asset emits `_zone_play_zone_tileset_tiles[]`.
        // The plan 02 spike's "checker.png -> checker_tiles" observation reflected a specific
        // spike invocation where the output filename happened to match; the underlying rule is
        // <output-basename>_tiles[]. See plan 11.2-04 SUMMARY "Deviations from Plan" for context.
        synthesizeZoneTilesetHeader(
            sanitized = sanitized,
            nativeStem = outputC.nameWithoutExtension,
            tileCount = tileCount,
            outputH = outputH,
            // Phase 12.2 D-01: tilemap PNG when present (two-invocation path), else tileset
            // PNG (one-invocation path). synthesizeZoneTilesetHeader reads the IHDR and derives
            // _tilemap_WIDTH / _HEIGHT from `image.width / 8` × `image.height / 8`.
            tilemapPng = tilemapPngFile ?: pngFile,
            paletteArrayDim = paletteArrayDim,
            subPaletteCount = subPaletteCount,
        )
        logger.lifecycle("    -> ${outputH.name} (alias: _zone_${sanitized}_tileset)")

        // Phase 12.2 D-01: two-path tilemap extraction (closes Defect 7 — the Plan 11.1-17
        // modulo-tile synthesizer was deleted in Plan 12.2-04). Both paths feed real png2asset
        // bytes (or first-invocation _tileset_map[] bytes) into the shared writer below.
        val tilemapBytes: List<Int> =
            if (tilemapPngFile != null) {
                // D-01 Path A two-invocation: png2asset a second time with -maps_only.
                // Reference Makefile flag pattern (gbdk platformer_template Makefile lines 84-86):
                //   <tilemap.png> -c <tmpRaw>.c -noflip -map -maps_only -source_tileset
                // <tileset.png>
                // NOTE: -keep_palette_order is intentionally ABSENT here — palette ordering is
                // INHERITED via -source_tileset (per Phase 12.8 RESEARCH Pitfall 3 / A2). Adding
                // it would be redundant and risks png2asset version-specific edge cases.
                val tmpRaw = File(outputDir, "_zone_${sanitized}_tilemap_raw.c")
                val tilemapArgs =
                    listOf(
                        tilemapPngFile.absolutePath,
                        "-c",
                        tmpRaw.absolutePath,
                        "-noflip",
                        "-map",
                        "-maps_only",
                        "-source_tileset",
                        pngFile.absolutePath,
                    )
                logger.lifecycle(
                    "  Tilemap extraction zone $zoneId: ${tilemapPngFile.name} -> ${tmpRaw.name}"
                )
                val tilemapResult =
                    execOperations.exec {
                        executable = png2assetExe.absolutePath
                        setArgs(tilemapArgs)
                        isIgnoreExitValue = true
                    }
                if (tilemapResult.exitValue != 0) {
                    error(
                        "png2asset (tilemap) failed for zone $zoneId (PNG ${tilemapPngFile.absolutePath}); " +
                            "exit=${tilemapResult.exitValue}. See log above for png2asset stderr."
                    )
                }
                // 0-on-decoder-error secondary guard (Plan 11.2-02 spike — png2asset exits 0 on
                // failure):
                if (!tmpRaw.isFile || tmpRaw.length() == 0L) {
                    error(
                        "png2asset (tilemap) reported success but did not produce $tmpRaw for zone $zoneId " +
                            "(PNG ${tilemapPngFile.absolutePath}). Treat any stderr above as failure."
                    )
                }
                // D-claude-5 / R-02: stem = OUTPUT-FILE basename. png2asset emits
                // `_zone_<sanitized>_tilemap_raw_map[]` because the output filename is
                // `_zone_<sanitized>_tilemap_raw.c`.
                parseMapArrayBytes(tmpRaw, "_zone_${sanitized}_tilemap_raw_map")
                    ?: error(
                        "Could not parse _zone_${sanitized}_tilemap_raw_map[] from ${tmpRaw.absolutePath} " +
                            "for zone $zoneId. png2asset output shape may have changed."
                    )
            } else {
                // D-01 Path A one-invocation: parse the EXISTING first invocation's _tileset_map[].
                // Straight copy of bytes — NO modulo-tiling. (Plan 11.1-17's modulo-tile
                // synthesizer
                // was deleted in Plan 12.2-04; closes Defect 7.)
                parseMapArrayBytes(outputC, "_zone_${sanitized}_tileset_map")
                    ?: error(
                        "Could not parse _zone_${sanitized}_tileset_map[] from ${outputC.absolutePath} " +
                            "for zone $zoneId. png2asset output shape may have changed."
                    )
            }

        // Write the gbkt-owned _zone_<sanitized>_tilemap.c with the parsed bytes.
        // Banking: #pragma bank N from metadata (Phase 12.1-01 Task 2 contract preserved).
        val outputTilemapC = File(outputDir, "_zone_${sanitized}_tilemap.c")
        val bytesPerRow = 16
        val formattedRows =
            tilemapBytes.chunked(bytesPerRow).joinToString(",\n    ") { row ->
                row.joinToString(", ") { "0x%02X".format(it) }
            }
        outputTilemapC.writeText(
            buildString {
                appendLine(
                    "/* Auto-generated by gbkt ConvertZoneTilesetsTask (Phase 12.2 D-01) -- DO NOT EDIT */"
                )
                appendLine("#pragma bank $zoneBank")
                appendLine()
                appendLine("#include <stdint.h>")
                appendLine("#include \"_zone_${sanitized}_tileset.h\"")
                appendLine()
                appendLine("const uint8_t _zone_${sanitized}_tilemap[${tilemapBytes.size}] = {")
                appendLine("    $formattedRows")
                appendLine("};")
            }
        )
        logger.lifecycle(
            "    -> _zone_${sanitized}_tilemap.c (${tilemapBytes.size} bytes, " +
                "${if (tilemapPngFile != null) "two-invocation D-01 path" else "one-invocation D-01 path"})"
        )
    }

    /**
     * Phase 12.8 W3: read the PNG IHDR color-type byte and return true iff the PNG is
     * indexed/colormap (color-type `3`). Used to conditionally append `-keep_palette_order` to the
     * png2asset invocation at [convertOneTileset]: indexed PNGs accept (and need) the flag to
     * preserve PLTE chunk order; RGB/RGBA PNGs reject it with png2asset exit=1 and
     * `keep_palette_order only works with indexed png images`.
     *
     * PNG layout reference (`https://www.w3.org/TR/PNG/`):
     * - Bytes 0..7 : signature `89 50 4E 47 0D 0A 1A 0A`
     * - Bytes 8..11 : IHDR chunk length (always `00 00 00 0D` = 13 bytes)
     * - Bytes 12..15: IHDR chunk type `49 48 44 52` ("IHDR")
     * - Bytes 16..19: width (big-endian, unused here)
     * - Bytes 20..23: height (big-endian, unused here)
     * - Byte 24 : bit depth
     * - Byte 25 : **color type** (0=gray, 2=RGB, 3=indexed, 4=gray+alpha, 6=RGBA)
     *
     * Reads only the first 26 bytes. Returns false on any IO error, signature mismatch, or
     * truncated header — the caller treats false as "not safe to pass `-keep_palette_order`".
     *
     * Visibility note: `internal` (not `private`) so the sibling test `IsIndexedPngTest` can
     * exercise both branches with synthetic headers without resorting to reflection. Marked here
     * because the @CacheableTask Gradle abstract base prohibits a cleaner top-level
     * companion-object helper.
     */
    internal fun isIndexedPng(file: File): Boolean =
        // Phase 12.9 D2a: delegates to the shared package-level isIndexedPngShared() helper
        // (PngUtils.kt) so ConvertSpritesTask can reuse the same PNG-header parse without
        // duplicating the logic. Both tasks share a single source of truth.
        isIndexedPngShared(file)

    /**
     * Parse a `const unsigned char <symbol>[N] = { 0x.., 0x.., ... };` byte-array initializer from
     * a png2asset-emitted .c file. Returns the byte values as `List<Int>` (each in 0..255), or
     * `null` if the symbol is not found.
     *
     * Phase 12.2 D-02: shared parse step for both invocation paths. The one-invocation path
     * (tilemapPath absent) extracts `_zone_<sanitized>_tileset_map[]` from the first png2asset
     * output; the two-invocation path (tilemapPath set) extracts
     * `_zone_<sanitized>_tilemap_raw_map[]` from the second png2asset invocation's output.
     */
    private fun parseMapArrayBytes(file: File, symbol: String): List<Int>? {
        val text = file.readText()
        val pattern =
            Regex(
                """const\s+unsigned\s+char\s+${Regex.escape(symbol)}\s*\[\s*\d+\s*\]\s*=\s*\{([^}]*)\}""",
                RegexOption.DOT_MATCHES_ALL,
            )
        val match = pattern.find(text) ?: return null
        val payload = match.groupValues[1]
        // png2asset may emit either decimal (e.g. `0, 1, 2`) or hex (e.g. `0x00, 0x01`) tokens
        // depending on flags/version. Accept both forms (this mirrors the pre-12.2 parser that
        // lived inside the deleted Plan 11.1-17 modulo-tile synthesizer).
        return payload
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { token ->
                if (token.startsWith("0x") || token.startsWith("0X")) {
                    token.substring(2).toInt(16)
                } else {
                    token.toInt()
                }
            }
    }

    /**
     * Parse the palette array dimension from a `const palette_color_t <symbol>[N] ...` declaration
     * in a png2asset-emitted .c file. Returns the integer dimension `N`, or `null` if the symbol is
     * not found.
     *
     * Phase 12.9 D-03(b): analogous to [parseMapArrayBytes] but targets `palette_color_t` arrays
     * (emitted by png2asset when `-keep_palette_order` is active). The dimension `N` is then
     * divided by 4 to derive the sub-palette count (`N / 4`) per GBC hardware: 4 colors per
     * sub-palette, so `PALETTE_COUNT = arrayDim / 4`.
     */
    private fun parsePaletteArrayDim(file: File, symbol: String): Int? {
        val text = file.readText()
        val pattern =
            Regex("""const\s+palette_color_t\s+${Regex.escape(symbol)}\s*\[\s*(\d+)\s*\]""")
        return pattern.find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Synthesize the `_zone_<sanitized>_tileset.h` header. Delegates to
     * [synthesizeZoneTilesetHeader] (internal top-level function below the class) so tests can call
     * the logic directly without GBDK / png2asset.
     *
     * WR-03 fix: the emitted header now includes `#include <gb/cgb.h>` alongside `<stdint.h>` and
     * `<gbdk/platform.h>` — required for the `palette_color_t` typedef used by the palette extern.
     */
    private fun synthesizeHeader(
        sanitized: String,
        nativeStem: String,
        tileCount: Int,
        outputH: File,
        tilemapPng: File,
        paletteArrayDim: Int,
        subPaletteCount: Int,
    ) =
        synthesizeZoneTilesetHeader(
            sanitized,
            nativeStem,
            tileCount,
            outputH,
            tilemapPng,
            paletteArrayDim,
            subPaletteCount,
        )

    companion object {
        /**
         * D-C3: maximum visual-tile count accepted by [ConvertZoneTilesetsTask] in phase 11.2.
         *
         * Worst-case enforcement (pre-png2asset-dedup). 192 leaves the upper third of bank 0's
         * 256-tile background tile space available for status-bar / UI tiles. Larger tilesets are
         * deferred to a future multi-bank-tile-data phase per plan 03's scope boundary.
         */
        const val MAX_ZONE_TILESET_TILES = 192

        /** Phase 12.8 W3: PNG signature (`89 50 4E 47 0D 0A 1A 0A`). Used by [isIndexedPng]. */
        internal val PNG_SIGNATURE: ByteArray =
            byteArrayOf(
                0x89.toByte(),
                0x50.toByte(),
                0x4E.toByte(),
                0x47.toByte(),
                0x0D.toByte(),
                0x0A.toByte(),
                0x1A.toByte(),
                0x0A.toByte(),
            )

        /** Phase 12.8 W3: offset of the IHDR color-type byte (byte 25 of the file). */
        internal const val PNG_COLOR_TYPE_OFFSET: Int = 25

        /** Phase 12.8 W3: PNG IHDR color-type value for indexed/colormap PNGs. */
        internal const val PNG_COLOR_TYPE_INDEXED: Byte = 3.toByte()

        /**
         * Phase 12.8 W3: bytes read by [isIndexedPng] — signature + IHDR length/type + width +
         * height + bit-depth + color-type = 26 bytes. (Reading one extra byte over the color-type
         * offset because [PNG_COLOR_TYPE_OFFSET] is an index, not a length.)
         */
        internal const val PNG_HEADER_BYTES: Int = 26
    }
}

/**
 * Synthesize the `_zone_<sanitized>_tileset.h` header per RESEARCH §"Example 2".
 *
 * Internal top-level function (promoted from [ConvertZoneTilesetsTask] private method) so that
 * tests can call the header-generation logic directly without requiring GBDK / png2asset. Mirrors
 * the [generateSpriteHeader] and [buildPng2AssetArgs] top-level visibility patterns.
 *
 * Emits:
 * - `extern const uint8_t <nativeStem>_tiles[<tileCount> * 16];` — tile data extern
 * - `#define _zone_<sanitized>_tileset <nativeStem>_tiles` — D-A3 alias
 * - `#define _zone_<sanitized>_tileset_count <tileCount>` — D-A3 symbolic count
 * - `extern const uint8_t _zone_<sanitized>_tilemap[tilemapW * tilemapH];` — tilemap extern
 * - WIDTH/HEIGHT macros derived from real [tilemapPng] IHDR pixel dimensions (Phase 12.2 REQ-3)
 * - `extern const palette_color_t _zone_<sanitized>_tileset_palettes[...]` — palette extern
 *
 * **WR-03 fix:** `#include <gb/cgb.h>` is now included so the header is self-contained. The
 * `palette_color_t` typedef is defined in `<gb/cgb.h>`; without this include the header relies on
 * `<gbdk/platform.h>` transitively providing the typedef, which may not hold in all GBDK
 * configurations. [generateSpriteHeader] already includes this header for the same reason — this
 * fix makes the zone tileset header consistent.
 */
internal fun synthesizeZoneTilesetHeader(
    sanitized: String,
    nativeStem: String,
    tileCount: Int,
    outputH: File,
    tilemapPng: File,
    paletteArrayDim: Int, // Phase 12.9 D-03(b): parsed from png2asset-emitted .c palette array
    subPaletteCount: Int, // Phase 12.9 D-03(c): paletteArrayDim / 4 (GBC: 4 colors per sub-palette)
) {
    // Phase 12.2 D-01 + REQ-3: derive WIDTH/HEIGHT from the relevant PNG's pixel dimensions
    // divided by 8 (Game Boy tile size). tilemapPng is:
    //   - the separate tilemap PNG (D-01 two-invocation path) when set on the zone, OR
    //   - the tileset PNG (D-01 one-invocation path) when the zone has no tilemap() call.
    val tilemapImg =
        javax.imageio.ImageIO.read(tilemapPng)
            ?: error(
                "Could not decode PNG for tilemap dimensions: ${tilemapPng.absolutePath} " +
                    "(zone sanitized symbol = $sanitized)"
            )
    require(tilemapImg.width % 8 == 0 && tilemapImg.height % 8 == 0) {
        "Tilemap PNG dimensions must be multiples of 8 (Game Boy tile size); got " +
            "${tilemapImg.width}x${tilemapImg.height} at ${tilemapPng.absolutePath} (Phase 12.2 REQ-3)"
    }
    val tilemapW = tilemapImg.width / 8
    val tilemapH = tilemapImg.height / 8

    val guard = "_ZONE_${sanitized.uppercase()}_TILESET_H"
    val byteCount = tileCount * 16 // 2bpp 8x8 tile = 16 bytes.
    val content = buildString {
        appendLine("/* Auto-generated by gbkt ConvertZoneTilesetsTask -- DO NOT EDIT */")
        appendLine(
            "/* Phase 12.2: WIDTH/HEIGHT derived from real PNG IHDR (was metadata-driven pre-12.2). */"
        )
        appendLine("#ifndef $guard")
        appendLine("#define $guard")
        appendLine()
        appendLine("#include <stdint.h>")
        appendLine("#include <gbdk/platform.h>")
        // WR-03 fix: include <gb/cgb.h> so this header is self-contained.
        // The palette_color_t typedef (used by the palette extern below) is provided by
        // <gb/cgb.h>. [generateSpriteHeader] already includes it; zone tileset header mirrors it.
        appendLine("#include <gb/cgb.h>")
        appendLine()
        appendLine("/* Native tile data array from png2asset output */")
        appendLine("extern const uint8_t ${nativeStem}_tiles[$byteCount];")
        appendLine()
        appendLine("/* gbkt zone-pipeline aliases (Phase 11.2 D-A3) */")
        appendLine("#define _zone_${sanitized}_tileset ${nativeStem}_tiles")
        appendLine("#define _zone_${sanitized}_tileset_count $tileCount")
        appendLine()
        appendLine("/* Tilemap byte array (Phase 12.2 D-01 two-path extraction) */")
        appendLine("extern const uint8_t _zone_${sanitized}_tilemap[$tilemapW * $tilemapH];")
        appendLine("/* Tilemap dimensions in screen tiles (Phase 12.2 REQ-3): PNG pixels / 8. */")
        appendLine(
            "/* Resolves GBDKPipeline.buildSetupCurrentLevelFunctionIfNeeded's references to */"
        )
        appendLine("/* _zone_<id>_tilemap_WIDTH / _HEIGHT at SDCC link time. */")
        appendLine("#define _zone_${sanitized}_tilemap_WIDTH $tilemapW")
        appendLine("#define _zone_${sanitized}_tilemap_HEIGHT $tilemapH")
        appendLine()
        appendLine("/* Phase 12.9 D-03(b)+(c): palette extern + sub-palette count macro */")
        appendLine(
            "extern const palette_color_t _zone_${sanitized}_tileset_palettes[$paletteArrayDim];"
        )
        appendLine("#define _zone_${sanitized}_tileset_PALETTE_COUNT $subPaletteCount")
        appendLine()
        appendLine("#endif /* $guard */")
    }
    outputH.writeText(content)
}
