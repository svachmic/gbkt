/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "TooManyFunctions", // Asset conversion has multiple per-format helpers
    "LongMethod", // convertSprite builds a multi-step pipeline per asset
    "TooGenericExceptionCaught", // PNG conversion wraps all tool exceptions
)

package io.github.gbkt.gradle.tasks

import io.github.gbkt.core.ir.SpriteMode
import io.github.gbkt.gradle.internal.GbdkToolchain
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.json.JSONException
import org.json.JSONObject

/**
 * Task that converts sprite PNG assets to GBDK-compatible C tile data files using `png2asset`.
 *
 * This task bridges the v2 codegen pipeline's sprite asset includes with GBDK's native asset
 * format. The v2 pipeline emits `#include "sprites/paddle.h"` directives and `set_sprite_data(...,
 * sprites_paddle_tiles)` calls. This task produces the corresponding `.c` tile data file and a `.h`
 * header that declares the path-based name alias.
 *
 * Pipeline:
 * 1. Reads `sprites[]` from the `game_metadata.json` sidecar (Phase 12.4 D-02) for explicit `(id,
 *    spritePath, mirrorDedup, spriteMode, pivotX, pivotY, frameWidth, frameHeight)` per metasprite
 *    (Phase 12.5 D-06 extends the sidecar reader with the 5 new cutting-flag fields)
 * 2. For each sprite entry, locates the corresponding PNG in [assetDirectory]
 * 3. Runs `png2asset` to produce a C file with tile data (e.g. `paddle_tiles[]`)
 * 4. Generates a companion `.h` header that declares the path-based alias (e.g.
 *    `sprites_paddle_tiles`) matching the `set_sprite_data` call
 * 5. Places output in [cSourceDir] subdirectories for lcc compilation
 *
 * The generated `.c` file is compiled by [CompileRomTask] alongside `main.c` and `bank1.c`. The
 * `.h` header is included by `main.c` via the relative path `"sprites/paddle.h"`.
 *
 * All synthesis-fallback paths fail fast (Phase 12.4 D-04 — mirrors Phase 12.2 Defect 7 full-delete
 * pattern). The `fixZeroSizeArrays` helper is a file-scope internal function accessible to
 * `ConvertSpritesTaskFailFastTest` for direct testing of the zero-size-array path.
 */
@CacheableTask
abstract class ConvertSpritesTask @Inject constructor(private val execOperations: ExecOperations) :
    DefaultTask() {

    /** Path to GBDK installation directory (must contain `bin/png2asset`). */
    @get:Input abstract val gbdkHome: Property<String>

    /**
     * Opt-in strict transparency routing mode (Phase 13.6 REQ-4 / D-01 / D-02).
     *
     * When `true`, any indexed sprite PNG whose tRNS chunk declares a transparent color at a
     * non-zero palette index will fail with a [GradleException] naming the sprite file and index,
     * instead of auto-correcting. When `false` (default), the framework auto-corrects and emits
     * a D-06 WARNING. Threaded from [io.github.gbkt.gradle.SpritesExtension.strictTransparency]
     * via [io.github.gbkt.gradle.GbktPlugin].
     *
     * Default: `false` (convention set in GbktPlugin.apply).
     */
    @get:Input abstract val strictTransparency: Property<Boolean>

    /** Directory containing sprite asset PNGs (e.g. `res/sprites/`). */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetDirectory: DirectoryProperty

    /**
     * The `game_metadata.json` file produced by `GBDKPipeline.buildMetadataFile()` (Phase 12.4
     * D-02). Contains the `sprites[]` array driving this task's per-metasprite png2asset
     * invocations.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadataFile: RegularFileProperty

    /**
     * Directory where converted sprite C files are placed.
     *
     * Should be the same directory as [main.c] so lcc can find sprite headers via relative path.
     * Default: `build/gbkt/generated`.
     */
    @get:OutputDirectory abstract val cSourceDir: DirectoryProperty

    init {
        description = "Convert sprite PNG assets to GBDK C tile data using png2asset"
        group = "gbkt"
    }

    @TaskAction
    fun convertSprites() {
        val metaFile = metadataFile.orNull?.asFile
        if (metaFile == null || !metaFile.exists()) {
            logger.lifecycle(
                "ConvertSpritesTask: No game_metadata.json — skipping sprite conversion"
            )
            return
        }

        val json =
            try {
                JSONObject(metaFile.readText())
            } catch (e: JSONException) {
                throw GradleException(
                    "Sprite sidecar at ${metaFile.absolutePath} is malformed: ${e.message}. " +
                        "Re-run :generateC to regenerate the sidecar.",
                    e,
                )
            }
        val sprites = json.optJSONArray("sprites")
        if (sprites == null || sprites.length() == 0) {
            logger.lifecycle(
                "ConvertSpritesTask: No sprites in metadata — skipping sprite conversion"
            )
            return
        }

        val assetDir = assetDirectory.orNull?.asFile
        if (assetDir == null || !assetDir.exists()) {
            logger.lifecycle("ConvertSpritesTask: No asset directory — skipping sprite conversion")
            return
        }

        val gbdkDir = File(gbdkHome.get())
        val png2assetExe = GbdkToolchain.getPng2asset(gbdkDir)
        if (!png2assetExe.exists()) {
            logger.warn(
                "ConvertSpritesTask: png2asset not found at ${png2assetExe.absolutePath} — skipping sprite conversion"
            )
            return
        }

        logger.lifecycle(
            "ConvertSpritesTask: Converting ${sprites.length()} sprite(s) from sidecar"
        )

        val sourceDir = cSourceDir.get().asFile
        for (i in 0 until sprites.length()) {
            val entry = sprites.getJSONObject(i)
            val id = entry.getString("id")
            val spritePath = entry.getString("spritePath")
            val mirrorDedup = entry.getBoolean("mirrorDedup")
            // Phase 12.5 D-06 — read 5 new cutting-flag fields from sidecar.
            // Use opt* for backward compat: stale sidecars without the new fields return defaults.
            // WR-04: wrap SpriteMode.valueOf in try/catch to produce an actionable GradleException
            // if the sidecar contains an unrecognized spriteMode value (e.g. a typo or a future
            // enum value from a newer gbkt version). Raw IllegalArgumentException is cryptic.
            val spriteModeStr = entry.optString("spriteMode", "SPR8x16")
            val spriteMode =
                try {
                    SpriteMode.valueOf(spriteModeStr)
                } catch (_: IllegalArgumentException) {
                    throw GradleException(
                        "Invalid spriteMode '$spriteModeStr' in game_metadata.json for sprite '$id'. " +
                            "Expected one of: ${SpriteMode.entries.joinToString(", ") { it.name }}. " +
                            "Re-run :generateC to regenerate the sidecar. (Phase 12.5 WR-04)"
                    )
                }
            val pivotX = entry.optInt("pivotX", 0)
            val pivotY = entry.optInt("pivotY", 0)
            val frameWidth = entry.optInt("frameWidth", 8)
            val frameHeight = entry.optInt("frameHeight", 8)
            // Phase 13.3 D-07 — read optional frameCount for build-time frame-count cross-validation.
            // Use opt* with -1 sentinel: JSON optInt returns the default when the key is absent.
            // null in Kotlin means "no declared count → skip validation".
            val declaredFrameCount: Int? = if (entry.has("frameCount")) entry.getInt("frameCount") else null
            // Phase 13.3 D-01 — read isMetasprite flag; backward-compat: old sidecars without
            // the flag return false (actor-sprite behavior).
            val isMetaspriteEntry = entry.optBoolean("isMetasprite", false)
            // includePath: where the .h file must land relative to cSourceDir.
            // Plan 12.4-13 Rule 1 fix: use the sidecar "includePath" when present; fall back to
            // path-derived includePath for backwards compatibility with entries that predate this
            // field.
            // For metasprites: "sprites/<id>.h" (matches the #include in generated main.c).
            // For actor sprites: path-derived (e.g. "sprites/paddle.png" → "sprites/paddle.h").
            val includePath =
                if (entry.has("includePath")) entry.getString("includePath")
                else spritePath.substringBeforeLast('.') + ".h"
            val pngFile = assetDir.resolve(spritePath)

            // Defensive path-traversal guard — mirrors ConvertZoneTilesetsTask.kt:174–192
            val assetCanonical = assetDir.canonicalPath
            val pngCanonical = pngFile.canonicalPath
            require(
                pngCanonical == assetCanonical ||
                    pngCanonical.startsWith(assetCanonical + File.separator)
            ) {
                "Sprite '$id' path escapes asset directory: $spritePath"
            }

            if (!pngFile.exists()) {
                throw GradleException(
                    "Sprite PNG not found: ${pngFile.absolutePath} (for metasprite '$id')\n" +
                        "  Declared at: sprite(asset(\"$spritePath\"))\n" +
                        "  Resolved path: ${pngFile.absolutePath}"
                )
            }

            convertSprite(
                pngFile,
                spritePath,
                includePath,
                sourceDir,
                png2assetExe,
                spriteMode,
                pivotX,
                pivotY,
                frameWidth,
                frameHeight,
                mirrorDedup,
                declaredFrameCount = declaredFrameCount,
                isMetaspriteEntry = isMetaspriteEntry,
            )
        }
    }

    /**
     * Convert a sprite PNG using png2asset and generate the companion header.
     *
     * Steps:
     * 1. Create output subdirectory in cSourceDir (e.g. `build/gbkt/generated/sprites/`)
     * 2. Run png2asset to produce `<name>.c` with native tile data array (e.g. `paddle_tiles`)
     * 3. Generate `<name>.h` declaring the path-based array alias for v2 pipeline compatibility
     * 4. The `.c` file will be compiled by CompileRomTask alongside main.c/bank1.c
     *
     * Array naming:
     * - png2asset generates: `paddle_tiles[]`
     * - v2 pipeline expects: `sprites_paddle_tiles[]`
     * - Generated header bridges with: `#define sprites_paddle_tiles paddle_tiles`
     *
     * Sprite mode (SPR8x8 vs SPR8x16), pivot, and frame-cutting dimensions are all read from the
     * sidecar entry (Phase 12.5 D-06). The `mirrorDedup` boolean controls the `-noflip` flag:
     * mirrorDedup=false → `-noflip` added (default); mirrorDedup=true → omitted (allows png2asset
     * mirror-pair tile dedup for from-scratch authored metasprites).
     */
    private fun convertSprite(
        pngFile: File,
        pngRelPath:
            String, // "sprites/paddle.png" or "graphics/player-character-gbapduck-sprites.png"
        includePath: String, // "sprites/paddle.h" or "sprites/player.h"
        sourceDir: File,
        png2assetExe: File,
        spriteMode: SpriteMode,
        pivotX: Int,
        pivotY: Int,
        frameWidth: Int,
        frameHeight: Int,
        mirrorDedup: Boolean,
        // Phase 13.3 D-07: optional declared frame count for post-png2asset cross-validation.
        declaredFrameCount: Int? = null,
        // Phase 13.3 D-01: whether this entry is a metasprite (drives _metasprites[] extern in .h).
        isMetaspriteEntry: Boolean = false,
    ) {
        // Output directories: build/gbkt/generated/sprites/
        val outputSubDir = File(sourceDir, includePath.substringBeforeLast('/'))
        outputSubDir.mkdirs()

        // Use the include-path stem (not the PNG filename stem) for the output .c/.h files.
        // This ensures the output header filename matches the #include directive in main.c.
        // Plan 12.4-13 Rule 1 fix: a metasprite with id "player" uses includePath
        // "sprites/player.h"
        // and the output must be "sprites/player.c" / "sprites/player.h", not
        // "sprites/player-character-gbapduck-sprites.c/.h".
        // For actor sprites where pngRelPath and includePath share the same stem (e.g.
        // "sprites/paddle.png"
        // → "sprites/paddle.h"), this is identical to the old pngFile.nameWithoutExtension
        // behavior.
        val stemName =
            includePath.substringAfterLast('/').substringBeforeLast('.') // "paddle" or "player"
        val outputC = File(outputSubDir, "$stemName.c") // build/gbkt/generated/sprites/paddle.c
        val outputH = File(outputSubDir, "$stemName.h") // build/gbkt/generated/sprites/paddle.h

        // Derive the native array name from the OUTPUT stem (png2asset uses the output filename
        // as the array name, so outputC name "player.c" → "player_tiles[]").
        val nativeArrayName = "${stemName}_tiles"

        // Derive the v2 pipeline array name (path-based convention from main.c actorSpriteIncludes
        // /
        // metaspriteSpriteIncludes). For actor sprites this is path-based; for metasprites this
        // is id-based. In both cases, the includePath encodes the expected include path, so
        // pathBasedArrayName is derived from includePath (not pngRelPath) to match
        // set_sprite_data() calls.
        val pathBasedArrayName =
            includePath.substringBeforeLast('.').replace('/', '_').replace('-', '_') + "_tiles"

        logger.lifecycle("  Converting: ${pngFile.name} → ${outputC.name}")

        // Phase 13.6 REQ-2/REQ-3 — tRNS auto-route: if the sprite PNG declares its transparent
        // color at a non-zero palette index, pre-permute the palette so transparent lands at
        // index 0 before handing to png2asset. With -keep_palette_order, png2asset bakes
        // 2bpp indices from the source palette order — index 0 must be transparent for GB OBJ
        // hardware to render it correctly (OBJ palette index 0 is the hardware-transparent slot).
        //
        // Algorithm: build compact remap {transparentIdx→0, used-visible→1..N, skip 0-pixel entries}
        // (see PngUtils.prePermuteIndexedPng KDoc + RESEARCH.md Pattern 2 / Pitfall 1).
        //
        // D-06 WARNING: emit exactly once per auto-corrected sprite (REQ-3) naming the file + index.
        // D-05: stay SILENT about declared-but-unused (0-pixel) palette slots (no unused-slot msg).
        //
        // Seam for Plan 04 strictTransparency gate: the strict check will be inserted at the
        // "TODO(13.6-04)" marker below — it fires BEFORE the auto-correct path when strict=true.
        //
        // T-13.6-03a (path traversal): temp file created in a stable build-temp directory
        // (buildTempDir, under sourceDir) — not the system TMPDIR, not adjacent to the source asset.
        // T-13.6-03b (temp leak): tempFile?.delete() in the single try/finally block below that
        // wraps the entire temp-file lifetime (buildPng2AssetArgs + exec) — WR-04 fix.
        // REQ-6 (deterministic name): buildTempDir path is stable across rebuilds so the
        // gbkt_permuted_<base>.png filename in the png2asset "Conversion args" comment is reproducible.
        val transparentIdx = getTransparentIndexShared(pngFile)
        val tempFile: File?
        if (transparentIdx != null && transparentIdx > 0) {
            // Phase 13.6 REQ-4 / Plan 04: strict gate — hard-fail the mismatch when strict=true.
            // Fires BEFORE the auto-correct path (and BEFORE countUsedVisibleColors / exec).
            // Pitfall 6: ONLY fires when transparentIdx > 0 (not for no-tRNS or index-0 sprites).
            if (strictTransparency.get()) {
                throw GradleException(
                    "sprite ${pngFile.name} declares transparent color at palette index " +
                        "$transparentIdx (index != 0, strict mode enabled) — fix the source PNG so " +
                        "transparent is at index 0, or disable strict mode with " +
                        "gbkt { sprites { strictTransparency.set(false) } })"
                )
            }
            // Phase 13.6 REQ-5 / Plan 04: overflow guard — fail fast when the sprite has more
            // than 3 USED visible colors (non-zero-pixel, non-transparent). GB OBJ palette allows
            // 1 transparent + 3 visible = 4 total 2bpp entries. Unused/0-pixel palette entries
            // are NOT counted (D-05 / RESEARCH Pitfall 1). Fires BEFORE prePermute and exec.
            val usedVisibleCount = countUsedVisibleColors(pngFile, transparentIdx)
            if (usedVisibleCount > 3) {
                throw GradleException(
                    "sprite ${pngFile.name}: transparent + $usedVisibleCount used visible colors " +
                        "exceeds GB OBJ palette limit (max 3 visible + 1 transparent = 4 total). " +
                        "Reduce the sprite to 3 or fewer USED visible palette entries. " +
                        "Declared-but-unused palette slots (0-pixel) are not counted."
                )
            }
            // WR-05 (ASCII messages): use ASCII 'index N != 0' instead of non-ASCII U+2260 (!=)
            // so non-UTF-8 consoles (Windows cp1252/cp437, some CI log collectors) render correctly.
            logger.warn(
                "sprite ${pngFile.name} declares transparent color at palette index " +
                    "$transparentIdx (index != 0); framework routed it to GB OBJ index 0 " +
                    "(enable strict mode to fail instead)"
            )
            // REQ-6 / W2 (13.8-03): deterministic temp dir and collision-free temp name.
            // W3 (13.8-03): use getTemporaryDir() so temp files land in build/tmp/convertSprites/
            // rather than inside the @OutputDirectory (cSourceDir). Writing undeclared temp files
            // into @OutputDirectory pollutes Gradle's build-cache fingerprint on @CacheableTask.
            // W2 (13.8-03): pass stemName to prePermuteIndexedPng so the temp file is named
            // gbkt_permuted_<stemName>.png — collision-free for same-basename sprites in different
            // subdirs (e.g. player/idle.png and enemy/idle.png → distinct temp names).
            val buildTempDir = temporaryDir
            buildTempDir.mkdirs()
            tempFile = prePermuteIndexedPng(pngFile, transparentIdx, buildTempDir, stemName)
        } else {
            tempFile = null
        }

        // Phase 12.5 D-06: build args from sidecar-driven fields (spriteMode, pivotX, pivotY,
        // frameWidth, frameHeight, mirrorDedup). Height heuristic deleted (sidecar is authoritative).
        // Phase 13.6 REQ-2: pass pngToConvert (permuted temp PNG or original) to buildPng2AssetArgs.
        // WR-04: the entire temp-file lifetime (buildPng2AssetArgs + exec) is inside one try/finally
        // so no throwing statement between temp creation and cleanup can leak the file.
        var lastArgs: List<String> = emptyList()
        val exitValue: Int =
            try {
                val pngToConvert = tempFile ?: pngFile
                val args =
                    buildPng2AssetArgs(
                        pngFile = pngToConvert,
                        outputC = outputC,
                        spriteMode = spriteMode,
                        pivotX = pivotX,
                        pivotY = pivotY,
                        frameWidth = frameWidth,
                        frameHeight = frameHeight,
                        mirrorDedup = mirrorDedup,
                    )
                lastArgs = args
                // Path 2 + 3: invoke png2asset; throw on exit != 0 or exec exception.
                try {
                    execOperations
                        .exec {
                            executable = png2assetExe.absolutePath
                            setArgs(args)
                            isIgnoreExitValue = true
                        }
                        .exitValue
                } catch (e: Exception) {
                    throw GradleException(
                        "png2asset threw for ${pngFile.absolutePath}: ${e.message}",
                        e,
                    )
                }
            } finally {
                // T-13.6-03b / WR-04: always delete the temp permuted PNG after the whole
                // convert pipeline (buildPng2AssetArgs + exec) completes or throws.
                // File.delete returns false silently when the file is absent — safe to call twice.
                tempFile?.delete()
            }

        if (exitValue != 0) {
            throw GradleException(
                "png2asset failed for ${pngFile.absolutePath}: exit $exitValue\n" +
                    "  Flags: ${lastArgs.joinToString(" ")}\n" +
                    "  Output target: ${outputC.absolutePath}"
            )
        }

        // Post-process: fix zero-size arrays that fail to compile with lcc (C89/C99)
        // An all-transparent PNG produces `uint8_t name[0] = {}` which is not valid C
        fixZeroSizeArrays(outputC, pngFile)

        // Phase 13.3 D-07 — frame-count cross-validation: if the DSL declared a frame count via
        // frames(N), parse the actual count from png2asset's output .c and fail the build loudly
        // if the two disagree. This catches DSL/asset desync at build time.
        if (declaredFrameCount != null && outputC.exists()) {
            try {
                validateFrameCount(
                    declaredCount = declaredFrameCount,
                    png2assetCOutput = outputC.readText(),
                    stemName = stemName,
                )
            } catch (e: IllegalArgumentException) {
                throw GradleException(
                    "Frame-count validation failed for metasprite '${stemName}': ${e.message}",
                    e,
                )
            }
        }

        // Phase 13.3 D-19 (Plan 13.3-20) — COLOR axis of GAP-1: collapse the metasprite
        // descriptor onto OBJ sub-palette slot 0 so the asset-driven elephant renders uniform
        // gray (ZERO pink). png2asset bakes absolute S_PAL slot indices from source-PNG palette
        // membership; this deterministic post-process rewrites every S_PAL(1) -> S_PAL(0) AFTER
        // png2asset succeeds and AFTER frame-count validation. Metasprite-only (actor sprites are
        // untouched). See remapMetaspriteSubPalette KDoc + evidence/13.3-DIAGNOSTIC.md step (b).
        if (isMetaspriteEntry) {
            remapMetaspriteSubPalette(outputC)
            logger.info("    → ${outputC.name}: remapped S_PAL(1)->S_PAL(0) (D-19 uniform-gray)")
        }

        // Phase 13.7 Plan 05 (Req 4 / D-06 WARNING): if the emitted OBJ palette luminance order
        // is the strict reverse of the source PNG PLTE, emit one non-fatal ASCII-only logger.warn
        // naming the offending PNG. Build still exits 0 (warn-not-fail; strict-fail is out of
        // scope). Gated on isIndexedPngShared(pngFile) — same condition as -keep_palette_order.
        // Additive second warning alongside D-06 transparent-index warn above (not a replacement).
        if (isIndexedPngShared(pngFile)) {
            val paletteSymbolForPolarity = "${stemName}_palettes"
            val emittedValues = parsePaletteRgb555Values(outputC, paletteSymbolForPolarity)
            if (emittedValues != null && checkPalettePolarity(pngFile, emittedValues)) {
                logger.warn(
                    "${pngFile.name} emitted palette luminance order appears inverted " +
                        "relative to source PNG PLTE; " +
                        "check -keep_palette_order and source palette order"
                )
            }
        }

        // Phase 12.4 bank-overflow fix: inject `#pragma bank 1` into sprite sidecar .c
        // files when the game uses multiple ROM banks (bank1.c exists in cSourceDir).
        //
        // Why: png2asset-generated sprite data arrays (e.g. player_tiles[1984]) are compiled
        // into bank 0 CODE section by default. For games with large metasprite sheets, this
        // pushes bank 0 CODE past 0x3DBB, causing the HOME section (GBDK library + NONBANKED
        // helpers) to overflow past the bank 0 boundary (0x3FFF). Functions like `___sdcc_bcall`
        // (SDCC's bank-switch trampoline) then land at 0x4000+ addresses — in bank 1 ROM space —
        // making ALL banked function calls execute wrong code (infinite hang on first banked call).
        //
        // Fix: `#pragma bank 1` moves sprite tile data into bank 1 CODE section, reducing bank 0
        // pressure. On startup, the MBC has bank 1 active (power-on default for MBC5/MBC1), so
        // `set_sprite_data(first, count, player_tiles)` called from main() (before any banked
        // function trampolines have executed) reads bank 1 data correctly without a SWITCH_ROM
        // wrapper. The `BANKREF(name)` macro already present in png2asset output creates the
        // `___bank__name` symbol, which `#pragma bank 1` will set to 1 (consistent).
        //
        // Guard: only inject when bank1.c exists — single-bank ROM_ONLY games have no bank 1;
        // their sprite data must stay in bank 0. For multi-bank games, bank 1 always has room
        // since scene code starts at 0x14000 with only a few hundred bytes used.
        val bank1Exists = File(sourceDir, "bank1.c").exists()
        if (bank1Exists) {
            injectBankPragma(outputC, 1)
            logger.info("    → ${outputC.name}: injected #pragma bank 1 (bank-overflow fix)")
        }

        // Phase 13.3 Plan 14 Task 2: parse actual tile count from png2asset output for metasprites.
        // The count macro `sprites_<id>_tiles_count` allows GBDKPipeline to emit
        //   set_sprite_data(start, sprites_<id>_tiles_count, sprites_<id>_tiles)
        // using the ACTUAL deduped tile count from png2asset (not a Kotlin geometric calc).
        // N = <stemName>_tiles[LEN] / 16 (16 bytes per 8×8 2bpp tile).
        // Only emitted for metasprite entries — actor sprites use a geometric count in codegen.
        val metaspriteTileCount: Int? =
            if (isMetaspriteEntry && outputC.exists()) {
                parseTileCount(outputC.readText(), stemName)
            } else {
                null
            }

        // Generate companion header with v2 pipeline alias.
        // CR-01 fix: isIndexed is retained for call-site documentation only; the function now
        // emits <gb/cgb.h> + palette extern UNCONDITIONALLY (png2asset always emits the
        // _palettes array for every sprite — see generateSpriteHeader KDoc above).
        // Phase 13.3 D-01: pass isMetaspriteEntry so generateSpriteHeader can emit the
        // native metasprite pointer-array extern for metasprite entries.
        // Phase 13.3 Plan 14 Task 2: pass metaspriteTileCount so generateSpriteHeader can
        // emit the `#define sprites_<id>_tiles_count N` macro for asset-driven metasprites.
        generateSpriteHeader(
            stemName,
            nativeArrayName,
            pathBasedArrayName,
            outputH,
            isIndexed = isIndexedPngShared(pngFile),
            isMetasprite = isMetaspriteEntry,
            tileCount = metaspriteTileCount,
        )
        logger.lifecycle("    → ${outputH.name} (alias: $pathBasedArrayName)")
    }

    // generateSpriteHeader is extracted as an internal top-level function below the class
    // (mirroring buildPng2AssetArgs visibility pattern) so it is reachable from tests.
}

/**
 * Generate a `.h` header file for a png2asset-produced sprite:
 * 1. Declares the native tile array from png2asset output as `extern`
 * 2. Declares the palette array extern unconditionally (png2asset always emits the
 *    `_palettes[]` array for every sprite — no `-no_palettes` flag is passed; the
 *    `-keep_palette_order` flag only affects ORDER, not whether the array is emitted)
 * 3. Provides a `#define` alias mapping the v2 pipeline name to the native name
 *
 * **CR-01 fix:** The `#include <gb/cgb.h>` and `extern const palette_color_t
 * <stem>_palettes[]` were previously gated on [isIndexed]. This caused a latent SDCC
 * "Undefined identifier '<stem>_palettes'" link error for any GBC game whose metasprite
 * PNG is non-indexed (RGB), because `GBDKPipeline.buildMetaspriteSpritePaletteStatements`
 * emits `set_sprite_palette(..., <stem>_palettes)` for every metasprite regardless of
 * PNG format. The isIndexed gate has been removed: both the include and the extern are now
 * always emitted.
 *
 * The [isIndexed] parameter is retained for call-site documentation purposes; it no longer
 * affects the emitted output.
 *
 * Exposed as `internal` at file scope so [ConvertSpritesHeaderPaletteExternTest] can call
 * it directly (mirrors the [buildPng2AssetArgs] visibility pattern).
 */
internal fun generateSpriteHeader(
    stemName: String,          // "paddle"
    nativeArrayName: String,   // "paddle_tiles"
    pathBasedArrayName: String, // "sprites_paddle_tiles"
    outputH: File,
    @Suppress("UNUSED_PARAMETER")
    isIndexed: Boolean = false, // retained for documentation; no longer gates emission (CR-01)
    // Phase 13.3 D-01: when true, also emit the native metasprite pointer-array extern so the
    // linker can resolve `<stemName>_metasprites[]` used in move_metasprite() calls.
    isMetasprite: Boolean = false,
    // Phase 13.3 Plan 14 Task 2: when non-null, emit `#define sprites_<stemName>_tiles_count N`
    // so GBDKPipeline can reference the ACTUAL png2asset deduped tile count in set_sprite_data().
    // Only provided for metasprite entries (parseTileCount result). Null for actor sprites.
    tileCount: Int? = null,
) {
    val guard = outputH.nameWithoutExtension.uppercase() + "_H"
    val paletteArrayName = "${stemName}_palettes"
    val content = buildString {
        appendLine("/* Auto-generated by gbkt ConvertSpritesTask — DO NOT EDIT */")
        appendLine("#ifndef ${guard}")
        appendLine("#define ${guard}")
        appendLine()
        appendLine("#include <stdint.h>")
        appendLine("#include <gbdk/platform.h>")
        // CR-01 fix: include <gb/cgb.h> UNCONDITIONALLY — provides the palette_color_t typedef
        // required by the palette extern below and by set_sprite_palette() in main.c.
        // png2asset always emits a _palettes array for every sprite PNG (no -no_palettes flag
        // is used in the pipeline; -keep_palette_order only affects palette ORDER).
        appendLine("#include <gb/cgb.h>")
        appendLine()
        appendLine("/* Native tile data array from png2asset output */")
        appendLine("extern const uint8_t ${nativeArrayName}[];")
        // CR-01 fix: declare the palette array extern UNCONDITIONALLY — the symbol always exists
        // in the png2asset .c output. GBDKPipeline.buildMetaspriteSpritePaletteStatements emits
        // `set_sprite_palette(<slot>u, 1u, <stem>_palettes)` for every GBC metasprite, so the
        // extern must always be present to prevent SDCC error 20 "Undefined identifier".
        appendLine("extern const palette_color_t ${paletteArrayName}[];")
        // Phase 13.3 D-01 — native metasprite pointer-array extern (alias bridge for the native
        // metasprite array). Only emitted for metasprite entries (not for actor sprite entries).
        // Resolves the linker symbol `<stemName>_metasprites[]` referenced by move_metasprite()
        // calls generated by GBDKPipeline.
        if (isMetasprite) {
            appendLine("extern const metasprite_t* const ${stemName}_metasprites[];")
        }
        appendLine()
        if (nativeArrayName != pathBasedArrayName) {
            appendLine("/* Path-based alias for GBDKPipeline set_sprite_data() calls */")
            appendLine("#define ${pathBasedArrayName} ${nativeArrayName}")
            appendLine()
        }
        // Phase 13.3 Plan 14 Task 2: tile-count macro for asset-driven metasprites.
        // GBDKPipeline.buildAllSpriteDataLoadStatements uses this macro in:
        //   set_sprite_data(start, sprites_<id>_tiles_count, sprites_<id>_tiles)
        // The count is the ACTUAL png2asset deduped count (array length / 16), not a
        // Kotlin geometric calc — png2asset may dedup tiles, making the geometric count wrong.
        // Only emitted for metasprite entries (tileCount != null).
        if (tileCount != null) {
            val countMacroName = "${pathBasedArrayName.removeSuffix("_tiles")}_tiles_count"
            appendLine("/* Actual deduped tile count from png2asset output (array length / 16) */")
            appendLine("#define ${countMacroName} ${tileCount}u")
            appendLine()
        }
        appendLine("#endif /* ${guard} */")
    }
    outputH.writeText(content)
}

/**
 * Inject a `#pragma bank N` directive into a png2asset-generated `.c` file.
 *
 * Places the pragma BEFORE the first `#include` line, following the same convention used by
 * `zone_bank2.c` (generated by [ConvertZoneTilesetsTask]):
 * ```c
 * // Generated by ...
 * #pragma bank N
 *
 * #include "game.h"
 * ```
 *
 * Idempotent: if the file already contains a `#pragma bank` directive (from a prior run or from
 * png2asset), the function is a no-op to avoid injecting duplicates.
 *
 * WR-01 fix: uses [System.lineSeparator] so the file preserves host line endings (CRLF on Windows,
 * LF on Unix/macOS). Previously used hard-coded `"\n"` which stripped CRLF on Windows.
 *
 * Exposed as `internal` at file scope so [ConvertSpritesTaskPragmaTest] can call it directly.
 */
internal fun injectBankPragma(outputC: File, bankNum: Int) {
    if (!outputC.exists()) return
    val content = outputC.readText()
    val pragmaLine = "#pragma bank $bankNum"
    // Idempotent guard — skip if already present
    if (content.contains(pragmaLine)) return
    val lines = content.lines().toMutableList()
    // Insert before the first #include directive so the pragma is at the file top
    val firstIncludeIdx = lines.indexOfFirst { it.trimStart().startsWith("#include") }
    if (firstIncludeIdx >= 0) {
        // Insert blank line before include block, then the pragma
        lines.add(firstIncludeIdx, "")
        lines.add(firstIncludeIdx, pragmaLine)
    } else {
        // Fallback: prepend after the two autogenerated comment lines
        val insertAt = minOf(2, lines.size)
        lines.add(insertAt, "")
        lines.add(insertAt, pragmaLine)
    }
    outputC.writeText(lines.joinToString(System.lineSeparator()))
}

/**
 * Fix zero-size arrays in png2asset output that fail to compile with lcc (C89/C99).
 *
 * An all-transparent PNG produces `const uint8_t name[0] = {}` which is invalid C before ISO C23.
 * When detected, throws a [GradleException] with a diagnostic message directing the developer to
 * check the source PNG (Phase 12.4 D-04 fail-fast pattern — mirrors Phase 12.2 Defect 7
 * full-delete).
 *
 * Exposed as `internal` at file scope so [ConvertSpritesTaskFailFastTest] can call it directly.
 */
internal fun fixZeroSizeArrays(outputC: File, pngFile: File) {
    if (!outputC.exists()) return
    val content = outputC.readText()
    // Detect zero-size tile array: `name[0] = {` or `name[0]={`
    // The pattern matches any identifier ending in _tiles followed by [0]
    val zeroArrayPattern = Regex("""\w+_tiles\s*\[\s*0\s*\]""")
    if (zeroArrayPattern.containsMatchIn(content)) {
        throw GradleException(
            "png2asset produced empty tile array for ${outputC.absolutePath}\n" +
                "  PNG may be all-transparent or 0×0 — verify ${pngFile.absolutePath}"
        )
    }
}

/**
 * Remap every `S_PAL(1)` sub-palette index to `S_PAL(0)` in a png2asset-generated metasprite
 * descriptor (Phase 13.3 D-19 / Plan 13.3-20 — COLOR axis of GAP-1).
 *
 * Why: png2asset bakes ABSOLUTE OBJ sub-palette slot indices into each `METASPR_ITEM(...,
 * S_PAL(n))` from the source PNG's palette membership. The elephant source carries two sub-palettes
 * → the generated `elephant.c` mixes `S_PAL(0)` (×97) and `S_PAL(1)` (×52). At runtime the
 * `S_PAL(1)` OAM entries select OBJ slot 1 (the scene's `pink_pal`) → a pink strip on an otherwise
 * gray elephant. These descriptor indices are IMMUTABLE upstream of this task (png2asset has no
 * "force single sub-palette" flag for an already-multi-palette source PNG — see
 * evidence/13.3-DIAGNOSTIC.md step (b)), and D-19 rejects re-authoring the source art.
 *
 * The fix (mechanism b, mirroring [injectBankPragma] / [fixZeroSizeArrays]): a deterministic
 * post-png2asset rewrite that collapses every entry onto OBJ slot 0, so `play_enter()`'s `gray_pal`
 * at slot 0 is the sole palette authority and the elephant renders uniform gray with ZERO pink
 * (13.3-17 already dropped the `elephant_palettes` upload — Direction B).
 *
 * Scope guard: a literal `S_PAL(1)` → `S_PAL(0)` replace ONLY (no regex broadening) — it touches
 * neither tile-data bytes, METASPR x/y/tile fields, the `_metasprite` pointer arrays, nor the
 * `_palettes[]` array. Idempotent (a second run finds no `S_PAL(1)` and is a no-op) and gated on
 * `isMetaspriteEntry` at the call site so actor sprites are never rewritten.
 *
 * Exposed as `internal` at file scope so [MetaspriteSubPaletteRemapTest] can call it directly.
 */
internal fun remapMetaspriteSubPalette(outputC: File) {
    if (!outputC.exists()) return
    val content = outputC.readText()
    val result = content.replace("S_PAL(1)", "S_PAL(0)")
    if (result != content) outputC.writeText(result)
}

/**
 * Validates that the author-declared frame count matches the count parsed from png2asset output.
 *
 * Implements D-07 build-time cross-validation: after png2asset succeeds, this helper parses the
 * explicit array size from the `<stemName>_metasprites[N]` pointer-array declaration in the
 * generated `.c` content. If the parsed count disagrees with [declaredCount], it throws an
 * exception naming both counts and the metasprite id so the developer can correct the `frames(N)`
 * DSL call.
 *
 * Parse strategy (RESEARCH G2): regex `<stemName>_metasprites\[(\d+)\]` captures the explicit
 * size — png2asset always emits an explicit `[N]` in the pointer-array declaration. This is O(1)
 * and robust.
 *
 * When [png2assetCOutput] does NOT contain a matching `<stemName>_metasprites[N]` declaration,
 * this helper passes silently (the png2asset output is outside the expected shape; no strict
 * validation is possible without the array declaration).
 *
 * Exposed as `internal` at file scope so [ConvertSpritesFrameCountValidationTest] can call it
 * directly (mirrors the [buildPng2AssetArgs] and [generateSpriteHeader] visibility pattern).
 *
 * @param declaredCount The frame count declared in the DSL via `frames(N)`.
 * @param png2assetCOutput The full text content of the png2asset-generated `.c` file.
 * @param stemName The metasprite id / stem name (e.g. "elephant") used to isolate the correct
 *   pointer-array declaration from the output.
 * @throws IllegalArgumentException when [declaredCount] disagrees with the parsed count.
 */
internal fun validateFrameCount(
    declaredCount: Int,
    png2assetCOutput: String,
    stemName: String,
) {
    // Parse the explicit array size from `<stemName>_metasprites[N]` in the png2asset .c output.
    // The regex matches the pointer-array declaration, e.g.:
    //   const metasprite_t* const elephant_metasprites[5] = {
    val pattern = Regex("""${Regex.escape(stemName)}_metasprites\[(\d+)\]""")
    val match = pattern.find(png2assetCOutput) ?: return  // Not found → skip validation
    val parsedCount = match.groupValues[1].toInt()
    if (parsedCount != declaredCount) {
        throw IllegalArgumentException(
            "D-07 frame-count mismatch for metasprite '$stemName': " +
                "DSL declared $declaredCount frame(s) via frames($declaredCount), " +
                "but png2asset output declares ${stemName}_metasprites[$parsedCount]. " +
                "Update the frames($declaredCount) call in your DSL to frames($parsedCount), " +
                "or verify the source PNG produces the expected number of animation frames."
        )
    }
}

/**
 * Parse the actual tile count from a png2asset-generated `.c` file for a metasprite.
 *
 * Implements Plan 13.3-14 Task 2: reads the explicit array size from
 * `<stemName>_tiles[<LEN>]` in the generated `.c` content, then computes
 * `tileCount = LEN / 16` (16 bytes per 8×8 2bpp tile). This gives the ACTUAL
 * deduped tile count that png2asset stored in the file — which may differ from
 * the geometric frame-size calculation (png2asset deduplicates tiles with `-noflip`).
 *
 * Fails loudly if the array length is not divisible by 16 (malformed output).
 * Returns `null` if no `<stemName>_tiles[N]` declaration is found in the output
 * (unexpected shape — skip count macro emission rather than emit a wrong value).
 *
 * Exposed as `internal` at file scope so tests can call it directly (mirrors
 * [validateFrameCount] and [buildPng2AssetArgs] visibility pattern).
 *
 * @param png2assetCOutput Full text content of the png2asset-generated `.c` file.
 * @param stemName The metasprite id / stem name (e.g. "elephant").
 * @return Tile count (array length / 16) or null if the declaration is not found.
 * @throws GradleException if the array length is not divisible by 16.
 */
internal fun parseTileCount(png2assetCOutput: String, stemName: String): Int? {
    // Match `<stemName>_tiles[<LEN>]` in the png2asset-generated C declaration.
    // png2asset always emits an explicit `[N]` in the tile-array declaration, e.g.:
    //   const uint8_t elephant_tiles[704] = {
    val pattern = Regex("""${Regex.escape(stemName)}_tiles\[(\d+)\]""")
    val match = pattern.find(png2assetCOutput) ?: return null
    val arrayLen = match.groupValues[1].toInt()
    if (arrayLen % 16 != 0) {
        throw GradleException(
            "parseTileCount: ${stemName}_tiles array length $arrayLen is not divisible by 16. " +
                "Each 8×8 2bpp tile is 16 bytes — png2asset output may be malformed or use a " +
                "different tile format. Check the source PNG and png2asset invocation flags."
        )
    }
    return arrayLen / 16
}

/**
 * Build the png2asset command-line argument list from sidecar-driven fields.
 *
 * Phase 12.5 D-06: this function replaces the former height-heuristic approach.
 * All cutting-flag parameters are now sourced from the sidecar entry. The sidecar is
 * authoritative; the height-heuristic is deleted.
 *
 * Argument order:
 * 1. Input PNG path
 * 2. -o <outputC>
 * 3. Optionally -spr8x8 (for SPR8x8 mode; SPR8x16 is the default, no flag needed)
 * 4. -px <pivotX> -py <pivotY>
 * 5. -sw <frameWidth> -sh <frameHeight>
 * 6. Optionally -noflip (when mirrorDedup=false)
 *
 * Exposed as `internal` at file scope so [ConvertSpritesTaskSidecarTest] can call it directly
 * without spawning a subprocess (D-06 test approach (a) from 12.5-05-PLAN.md).
 */
internal fun buildPng2AssetArgs(
    pngFile: File,
    outputC: File,
    spriteMode: SpriteMode,
    pivotX: Int,
    pivotY: Int,
    frameWidth: Int,
    frameHeight: Int,
    mirrorDedup: Boolean,
): List<String> {
    val args = mutableListOf(pngFile.absolutePath, "-o", outputC.absolutePath)
    when (spriteMode) {
        SpriteMode.SPR8x8 -> args.add("-spr8x8")
        SpriteMode.SPR8x16 -> {
            /* default — no flag needed */
        }
    }
    args.addAll(listOf("-px", "$pivotX", "-py", "$pivotY"))
    args.addAll(listOf("-sw", "$frameWidth", "-sh", "$frameHeight"))
    // mirrorDedup=false → add -noflip (default: full unique-tile array, DEF-10.1-13-A).
    // mirrorDedup=true → omit -noflip (allow png2asset mirror-pair tile dedup for
    // from-scratch authored metasprites). The sidecar carries the explicit opt-in;
    // no comment-marker sentinel scan needed (Plan 12.4 D-02).
    if (!mirrorDedup) {
        args.add("-noflip")
    }
    // Phase 12.9 D2a fix: append -keep_palette_order for indexed (color-type 3) PNGs.
    // Mirroring ConvertZoneTilesetsTask's isIndexedPng gate (Phase 12.8 W3). Without this
    // flag, png2asset re-sorts the palette, moving the orange sprite-sheet background from
    // its source index 0 to index 2 — an opaque color. On GBC, OBJ palette index 0 is
    // hardware-transparent, so the box only disappears when the background color is at
    // index 0. With -keep_palette_order, png2asset preserves the source palette order
    // and the orange background stays at index 0 → transparent on GBC.
    // NOTE: `-c` in png2asset is deprecated (= `-o`, the output flag) — do NOT use it.
    // The working reference is the GBDK platformer_template Makefile:81.
    // isIndexedPngShared is the shared PNG-header parse from PngUtils.kt (single source).
    if (isIndexedPngShared(pngFile)) {
        args.add("-keep_palette_order")
    }
    return args
}
