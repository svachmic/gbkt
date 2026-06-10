/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SpriteDef
import io.github.gbkt.core.ir.SpriteMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject

// =============================================================================
// SPRITES[] SIDECAR EMIT TEST — Phase 12.4 D-02
// Verifies that GBDKPipeline.buildMetadataFile() produces a correct
// `sprites[]` JSONArray section for ConvertSpritesTask to consume.
//
// Four behaviors per D-11 (lowerCamelCase keys: id, spritePath, mirrorDedup):
//  1. Metasprite with explicit spritePath is emitted as a sprites[] entry.
//  2. Metasprite with spritePath == null is SKIPPED (migration window D-01b).
//  3. mirrorDedup=true is reflected in the emitted entry.
//  4. Empty metasprites list emits sprites:[] (key present, array empty).
// =============================================================================

private val minimalFrame =
    MetaspriteFrame(tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 0)))

private fun minimalGameIR(metasprites: List<MetaspriteIR>) =
    GameIR(
        name = "SpritesTest",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
        metasprites = metasprites,
    )

class GBDKPipelineMetadataSpritesTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: emits sprites array with explicit spritePath
    // =========================================================================
    @Test
    fun `emits_sprites_array_with_explicit_spritePath`() {
        val fixture =
            minimalGameIR(
                listOf(
                    MetaspriteIR(
                        id = "elephant",
                        frames = listOf(minimalFrame),
                        spritePath = "sprites/elephant.png",
                        mirrorDedup = false,
                    )
                )
            )

        val json = pipeline.buildMetadataFile(fixture)
        val parsed = JSONObject(json)

        val sprites = parsed.getJSONArray("sprites")
        assertEquals(1, sprites.length(), "Expected 1 entry in sprites[]")
        val entry = sprites.getJSONObject(0)
        assertEquals("elephant", entry.getString("id"))
        assertEquals("sprites/elephant.png", entry.getString("spritePath"))
        assertEquals(false, entry.getBoolean("mirrorDedup"))
    }

    // =========================================================================
    // Test 2: skips metasprite with null spritePath (migration window D-01b)
    // =========================================================================
    @Test
    fun `skips_metasprite_with_null_spritePath`() {
        val fixture =
            minimalGameIR(
                listOf(MetaspriteIR(id = "hero", frames = listOf(minimalFrame), spritePath = null))
            )

        val json = pipeline.buildMetadataFile(fixture)
        val parsed = JSONObject(json)

        val sprites = parsed.getJSONArray("sprites")
        assertEquals(0, sprites.length(), "Metasprite with null spritePath must be skipped")
    }

    // =========================================================================
    // Test 3: emits mirrorDedup=true when set
    // =========================================================================
    @Test
    fun `emits_mirror_dedup_true_when_set`() {
        val fixture =
            minimalGameIR(
                listOf(
                    MetaspriteIR(
                        id = "tiger",
                        frames = listOf(minimalFrame),
                        spritePath = "sprites/tiger.png",
                        mirrorDedup = true,
                    )
                )
            )

        val json = pipeline.buildMetadataFile(fixture)
        val parsed = JSONObject(json)

        val sprites = parsed.getJSONArray("sprites")
        assertEquals(1, sprites.length())
        val entry = sprites.getJSONObject(0)
        assertEquals("tiger", entry.getString("id"))
        assertEquals(true, entry.getBoolean("mirrorDedup"))
    }

    // =========================================================================
    // Test 4: emits empty array when no metasprites (key MUST be present)
    // =========================================================================
    @Test
    fun `emits_empty_array_when_no_metasprites`() {
        val fixture = minimalGameIR(emptyList())

        val json = pipeline.buildMetadataFile(fixture)
        val parsed = JSONObject(json)

        assertTrue(
            parsed.has("sprites"),
            "Key 'sprites' MUST be present even when metasprites list is empty",
        )
        val sprites = parsed.getJSONArray("sprites")
        assertEquals(0, sprites.length(), "sprites[] must be an empty array (not missing key)")
    }

    // =========================================================================
    // Test 5: emits 5 new png2asset cutting-flag fields per D-05 (Phase 12.5)
    // =========================================================================
    @Test
    fun `emits_sprite_cutting_flags_in_sprites_array`() {
        val fixture =
            minimalGameIR(
                listOf(
                    MetaspriteIR(
                        id = "player",
                        frames = listOf(minimalFrame),
                        spritePath = "graphics/player-character-gbapduck-sprites.png",
                        mirrorDedup = false,
                        spriteMode = SpriteMode.SPR8x16,
                        pivotX = 12,
                        pivotY = 6,
                        frameWidth = 24,
                        frameHeight = 32,
                    )
                )
            )

        val json = pipeline.buildMetadataFile(fixture)
        val parsed = JSONObject(json)

        val sprites = parsed.getJSONArray("sprites")
        assertEquals(1, sprites.length(), "Expected 1 entry in sprites[]")
        val entry = sprites.getJSONObject(0)
        assertEquals(
            "SPR8x16",
            entry.getString("spriteMode"),
            "spriteMode must be lowerCamelCase key with enum name value",
        )
        assertEquals(12, entry.getInt("pivotX"), "pivotX must be emitted")
        assertEquals(6, entry.getInt("pivotY"), "pivotY must be emitted")
        assertEquals(24, entry.getInt("frameWidth"), "frameWidth must be emitted")
        assertEquals(32, entry.getInt("frameHeight"), "frameHeight must be emitted")
    }

    // =========================================================================
    // Test 6: null fields fall through to defensive defaults per PATTERNS.md Pattern 2
    // =========================================================================
    @Test
    fun `emits_defensive_defaults_when_cutting_flags_are_null`() {
        val fixture =
            minimalGameIR(
                listOf(
                    MetaspriteIR(
                        id = "paddle",
                        frames = listOf(minimalFrame),
                        spritePath = "sprites/paddle.png",
                        mirrorDedup = false,
                        // All 5 new fields left null — should emit safe defaults
                        spriteMode = null,
                        pivotX = null,
                        pivotY = null,
                        frameWidth = null,
                        frameHeight = null,
                    )
                )
            )

        val json = pipeline.buildMetadataFile(fixture)
        val parsed = JSONObject(json)

        val sprites = parsed.getJSONArray("sprites")
        assertEquals(1, sprites.length())
        val entry = sprites.getJSONObject(0)
        assertEquals(
            "SPR8x16",
            entry.getString("spriteMode"),
            "null spriteMode must default to SPR8x16",
        )
        assertEquals(0, entry.getInt("pivotX"), "null pivotX must default to 0")
        assertEquals(0, entry.getInt("pivotY"), "null pivotY must default to 0")
        assertEquals(8, entry.getInt("frameWidth"), "null frameWidth must default to 8")
        assertEquals(8, entry.getInt("frameHeight"), "null frameHeight must default to 8")
    }

    // =========================================================================
    // Test 7: actor-sprite entries appear in sprites[] (WR-03 regression guard)
    // Locks Plan 12.4-13 inline fix: actor-sprite emission loop in
    // GBDKPipeline.buildMetadataFile() lines 381-392.
    // RED-on-revert: comment out the actor-sprite loop → sprites.length() == 0,
    // test fails on the ">= 1" assertion. Restore loop → GREEN.
    // =========================================================================
    @Test
    fun `emits_actor_sprite_entries_in_sprites_array`() {
        // Build a minimal GameIR with one actor that has a sprite asset reference.
        // No metasprites — only actor sprites. This exercises the actor-sprite
        // emission path (GBDKPipeline.kt lines 381-392) independently.
        val fixture =
            GameIR(
                name = "ActorSpriteTest",
                config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
                actors =
                    listOf(
                        ActorIR(
                            id = "paddle",
                            position = PositionDef(x = 80, y = 72),
                            sprite =
                                SpriteDef(
                                    assetRef = AssetRef(path = "sprites/paddle.png"),
                                    size = SizeDef(width = 8, height = 8),
                                ),
                        )
                    ),
            )

        val json = pipeline.buildMetadataFile(fixture)
        val parsed = JSONObject(json)

        val sprites = parsed.getJSONArray("sprites")
        assertTrue(sprites.length() >= 1, "Expected at least 1 actor-sprite entry in sprites[]")

        // Locate the actor-sprite entry for sprites/paddle.png
        val entry =
            (0 until sprites.length())
                .map { sprites.getJSONObject(it) }
                .firstOrNull { it.getString("spritePath") == "sprites/paddle.png" }
                ?: error("No sprites[] entry found for spritePath='sprites/paddle.png'")

        // id is derived via spritePath.substringAfterLast('/').substringBeforeLast('.')
        assertEquals("paddle", entry.getString("id"), "id must be PNG stem (no path, no extension)")
        assertEquals(
            "sprites/paddle.png",
            entry.getString("spritePath"),
            "spritePath must match actor sprite asset ref path",
        )

        // includePath is derived as spritePath with .h extension (same subdirectory)
        assertTrue(entry.has("includePath"), "includePath must be present in actor-sprite entry")
        assertEquals(
            "sprites/paddle.h",
            entry.getString("includePath"),
            "includePath must replace .png with .h",
        )

        // Actor sprites always have mirrorDedup=false (no opt-in at actor level per D-05)
        assertEquals(
            false,
            entry.getBoolean("mirrorDedup"),
            "actor-sprite entries must have mirrorDedup=false",
        )

        // Plan 12.5-08 Rule 1 fix: actor-sprite entries DO carry cutting flags derived
        // from actor.sprite.size — without them, ConvertSpritesTask defaults to
        // SPR8x16+frameHeight=8 which fails png2asset for 8x8 sprites (e.g. simple-physics
        // ball). This evolves the original D-05 invariant: cutting flags are still NOT
        // user-declared at actor level, but they ARE auto-derived from sprite.size at
        // sidecar-emit time. Fixture: paddle.png with size=8x8 → SPR8x8, pivot=0,0, frame=8x8.
        assertEquals(
            "SPR8x8",
            entry.getString("spriteMode"),
            "actor-sprite spriteMode must derive from sprite.size (Plan 12.5-08)",
        )
        assertEquals(
            0,
            entry.getInt("pivotX"),
            "actor-sprite pivotX must default to 0 (no DSL pivot at actor level)",
        )
        assertEquals(0, entry.getInt("pivotY"), "actor-sprite pivotY must default to 0")
        assertEquals(
            8,
            entry.getInt("frameWidth"),
            "actor-sprite frameWidth must equal sprite.size.width",
        )
        assertEquals(
            8,
            entry.getInt("frameHeight"),
            "actor-sprite frameHeight must equal sprite.size.height",
        )
    }
}
