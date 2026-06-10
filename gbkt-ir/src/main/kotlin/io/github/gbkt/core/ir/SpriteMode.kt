/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

/**
 * Sprite rendering mode for png2asset metasprite cutting.
 *
 * Controls the `-spr8x8` or default (no flag) behavior passed to the `png2asset` tool when
 * converting a sprite PNG to GBDK tile data.
 *
 * - [SPR8x8] passes `-spr8x8` to png2asset — selects 8×8 hardware sprite tiles. Use for small actor
 *   sprites where the entire sprite fits in a single 8×8 tile (e.g. ball, paddle).
 * - [SPR8x16] uses png2asset's default (no flag) — selects 8×16 hardware sprite pairs. Use for
 *   larger sprites (e.g. player character, enemy figures) that span two stacked 8×8 tiles per
 *   column in OAM. This is the correct mode for the GBDK platformer-template duck player (three
 *   columns × two rows = 24×32 sprite, `-spr8x16 -px 12 -py 6 -sw 24 -sh 32`).
 *
 * D-15 (Phase 12.5 CONTEXT.md): moved from `ConvertSpritesTask` inner `internal enum class
 * SpriteMode` to this `gbkt-ir` leaf module as a top-level public enum. This creates the single
 * source of truth for all consumers: `MetaspriteIR.spriteMode`, `MetaspriteBuilder.mode()`, and
 * `ConvertSpritesTask` (which imports `io.github.gbkt.core.ir.SpriteMode`). Placing the enum here
 * avoids layer violations — `gbkt-ir` is the leaf module; all downstream modules (`gbkt-lang`,
 * `gbkt-backend-gbdk`, `gbkt-gradle-plugin`) can reference one canonical type.
 */
enum class SpriteMode {
    SPR8x8,
    SPR8x16,
}
