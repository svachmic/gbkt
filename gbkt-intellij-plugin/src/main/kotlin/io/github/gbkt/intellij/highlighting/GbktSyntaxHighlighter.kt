/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gbkt.intellij.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import io.github.gbkt.intellij.lang.GbktLexer

/**
 * Syntax highlighter for gbkt DSL files.
 *
 * Since gbkt files are Kotlin scripts, the Kotlin plugin handles base tokenization and
 * highlighting. DSL-specific highlighting (for keywords like `scene`, `entity`, `runIf`, etc.) is
 * provided by [GbktDslAnnotator] which examines Kotlin PSI elements and applies text attributes.
 *
 * This class:
 * - Provides the lexer (which delegates to KotlinLexer)
 * - Defines text attribute keys for DSL elements
 * - Supports the [GbktColorSettingsPage] for user customization
 */
class GbktSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = GbktLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        // Base highlighting is handled by Kotlin's highlighter.
        // DSL-specific highlighting is provided by GbktDslAnnotator.
        return EMPTY_KEYS
    }

    companion object {
        // Text attribute keys for gbkt DSL elements

        /** Top-level DSL functions like gbGame, scene, entity, dialog. Bold blue color. */
        @JvmField
        val DSL_FUNCTION =
            createTextAttributesKey(
                "GBKT_DSL_FUNCTION",
                DefaultLanguageHighlighterColors.FUNCTION_DECLARATION,
            )

        /** Control flow DSL keywords like runIf, branch, repeat. Purple color. */
        @JvmField
        val DSL_CONTROL_FLOW =
            createTextAttributesKey(
                "GBKT_DSL_CONTROL_FLOW",
                DefaultLanguageHighlighterColors.KEYWORD,
            )

        /** Builder methods like position, velocity, sprite, hitbox. Green color. */
        @JvmField
        val DSL_BUILDER_METHOD =
            createTextAttributesKey(
                "GBKT_DSL_BUILDER_METHOD",
                DefaultLanguageHighlighterColors.INSTANCE_METHOD,
            )

        /** Input-related keywords like dpad, buttons, pressed, held. Orange color. */
        @JvmField
        val DSL_INPUT =
            createTextAttributesKey("GBKT_DSL_INPUT", DefaultLanguageHighlighterColors.CONSTANT)

        /** Condition operators like isEqualTo, collidesWith, overlaps. Teal color. */
        @JvmField
        val DSL_CONDITION =
            createTextAttributesKey(
                "GBKT_DSL_CONDITION",
                DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL,
            )

        /** Lifecycle callbacks like enter, exit, tick, frame. Italic style. */
        @JvmField
        val DSL_LIFECYCLE =
            createTextAttributesKey("GBKT_DSL_LIFECYCLE", DefaultLanguageHighlighterColors.METADATA)

        /** Type references like SceneRef, Entity, Sprite. */
        @JvmField
        val DSL_TYPE =
            createTextAttributesKey(
                "GBKT_DSL_TYPE",
                DefaultLanguageHighlighterColors.CLASS_REFERENCE,
            )

        private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }
}

/** DSL keywords organized by category. Used for highlighting and completion. */
object GbktKeywords {
    /** Top-level DSL entry points. */
    val TOP_LEVEL_FUNCTIONS =
        setOf(
            // Core
            "gbGame",
            "scene",
            "entity",
            "dialog",
            "camera",
            "stats",
            "flags",
            // Variables
            "u8Var",
            "u16Var",
            "i8Var",
            "i16Var",
            "u8Array",
            "u16Array",
            "i8Array",
            "i16Array",
            // RPG
            "character",
            "monster",
            "ability",
            "item",
            "floor",
            "encounterTable",
            "battle",
            "inventory",
            "statusEffect",
            "exploration",
            "gameFlow",
            // Systems
            "save",
            "config",
            "assets",
            "tag",
            "navGrid",
            "cutscene",
            "audioMixer",
            "physics",
            "inputBuffer",
            "transition",
            "tween",
        )

    /** Control flow keywords. */
    val CONTROL_FLOW =
        setOf(
            "runIf",
            "branch",
            "repeat",
            "repeatWhile",
            "repeatIndexed",
            "repeatRange",
            "then",
            "otherwise",
        )

    /** Entity/scene builder methods. */
    val BUILDER_METHODS =
        setOf(
            "position",
            "velocity",
            "sprite",
            "hitbox",
            "combat",
            "states",
            "tag",
            "size",
            "palette",
            "paletteIndex",
            "regions",
            "animations",
            "maxHp",
            "attackPower",
            "defense",
            "team",
            "invincibilityFrames",
            "knockbackForce",
            "state",
            "plays",
            "at",
            "every",
            "frames",
            "box",
            "portrait",
            "textSpeed",
            "textSound",
            "speaker",
            "smoothing",
            "offset",
            "deadzone",
            "bounds",
            "follow",
            "hp",
            "sp",
            "atk",
            "def",
            "matk",
            "mdef",
            "agl",
            "acc",
            "eva",
            "safeSteps",
            "initialChance",
            "incrementPerStep",
            "maxChance",
            "weight",
            "page",
            "flag",
            "locked",
            "contains",
            "onOpen",
            "startsOpen",
            "startsOn",
            "oneShot",
            "onPull",
            "text",
            "requiresFacing",
            "name",
            "facing",
            "movement",
            "healsParty",
            "startsLit",
            "lightColor",
            "lightRadius",
        )

    /** Input-related keywords. */
    val INPUT =
        setOf(
            "dpad",
            "buttons",
            "pressed",
            "released",
            "held",
            "left",
            "right",
            "up",
            "down",
            "a",
            "b",
            "start",
            "select",
            "any",
            "none",
            "x",
            "y",
        )

    /** Condition operators. */
    val CONDITIONS =
        setOf(
            "isEqualTo",
            "isNotEqualTo",
            "isGreaterThan",
            "isLessThan",
            "isGreaterThanOrEqualTo",
            "isLessThanOrEqualTo",
            "isAbove",
            "isBelow",
            "isAtLeast",
            "isAtMost",
            "collidesWith",
            "overlaps",
            "onScreen",
            "and",
            "or",
            "not",
        )

    /** Lifecycle callbacks. */
    val LIFECYCLE =
        setOf(
            // Scene lifecycle
            "enter",
            "exit",
            "tick",
            "frame",
            "second",
            "halfSecond",
            "quarterSecond",
            "on",
            "goto",
            // Battle lifecycle
            "onVictory",
            "onDefeat",
            "onState",
            "onTurnStart",
            "onTurnEnd",
            "onDamage",
            "onHeal",
            "onDeath",
            // Item/ability lifecycle
            "onUse",
            "onEquip",
            "onUnequip",
            // World/exploration lifecycle
            "onInteract",
            "onStep",
            "onBlocked",
            "onEncounter",
            // Character lifecycle
            "onLevelUp",
            "onStatusApply",
            "onStatusRemove",
        )

    /** Important types. */
    val TYPES =
        setOf(
            "SceneRef",
            "Entity",
            "Sprite",
            "Dialog",
            "DialogHandle",
            "Camera",
            "StateMachine",
            "State",
            "StateRef",
            "AnimationRef",
            "TagRef",
            "Expr",
            "Condition",
            "AssignableExpr",
            "BattleState",
            "BattleActionType",
            "CombatTeam",
            "Easing",
            "BorderStyle",
            "PortraitPosition",
            "ItemCategory",
            "MonsterSize",
            "MonsterTier",
            "Aspect",
            "Direction",
            "MovementPattern",
            "MapObjectType",
        )

    /** All keywords combined. */
    val ALL =
        TOP_LEVEL_FUNCTIONS +
            CONTROL_FLOW +
            BUILDER_METHODS +
            INPUT +
            CONDITIONS +
            LIFECYCLE +
            TYPES
}
