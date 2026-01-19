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
package io.github.gbkt.intellij.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import io.github.gbkt.intellij.GbktIcons
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression

/**
 * Provides context-aware completion for builder methods inside DSL blocks.
 *
 * Analyzes the current context using Kotlin PSI (which builder we're inside) and suggests relevant
 * methods. For example:
 * - Inside entity {}: position, velocity, sprite, hitbox, combat, states
 * - Inside sprite {}: size, palette, hitbox, regions, animations
 * - Inside scene {}: enter, exit, every.frame
 * - Inside combat {}: maxHp, attackPower, defense, team
 */
class GbktBuilderCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val file = parameters.originalFile
        if (!file.name.endsWith(".gbkt.kts")) return

        val position = parameters.position

        // Find the context using PSI traversal (preferred) or fallback to text-based
        val builderContext =
            findBuilderContextPsi(position) ?: findBuilderContextText(file.text, parameters.offset)

        // Add suggestions based on context
        val suggestions = getContextSuggestions(builderContext)
        for ((keyword, description) in suggestions) {
            result.addElement(
                LookupElementBuilder.create(keyword)
                    .withIcon(GbktIcons.FILE)
                    .withTypeText(description)
                    .withTailText(getTailText(keyword), true)
            )
        }
    }

    /**
     * Finds the builder context using Kotlin PSI traversal. Walks up the PSI tree looking for call
     * expressions with known builder names.
     */
    private fun findBuilderContextPsi(position: PsiElement): String? {
        var current: PsiElement? = position

        while (current != null) {
            // Look for lambda expressions that are arguments to call expressions
            if (current is KtLambdaExpression) {
                val callExpr = PsiTreeUtil.getParentOfType(current, KtCallExpression::class.java)
                if (callExpr != null) {
                    val calleeName = callExpr.calleeExpression?.text
                    if (calleeName != null && calleeName in BUILDER_NAMES) {
                        return calleeName
                    }
                }
            }

            // Also check direct call expressions
            if (current is KtCallExpression) {
                val calleeName = current.calleeExpression?.text
                if (calleeName != null && calleeName in BUILDER_NAMES) {
                    return calleeName
                }
            }

            current = current.parent
        }

        return null
    }

    /**
     * Fallback: Finds the builder context by analyzing surrounding text using regex. Used when PSI
     * is not available or incomplete (e.g., during typing).
     */
    private fun findBuilderContextText(text: String, offset: Int): String {
        val textBefore = text.substring(0, minOf(offset, text.length))

        var lastBuilder = ""
        var lastIndex = -1

        for ((builder, pattern) in BUILDER_PATTERNS) {
            val matches = pattern.findAll(textBefore)
            for (match in matches) {
                if (match.range.first > lastIndex) {
                    // Check if this block is still open (count braces)
                    val afterMatch = textBefore.substring(match.range.last)
                    val openBraces = afterMatch.count { it == '{' }
                    val closeBraces = afterMatch.count { it == '}' }
                    if (openBraces > closeBraces) {
                        lastBuilder = builder
                        lastIndex = match.range.first
                    }
                }
            }
        }

        return lastBuilder
    }

    /** Returns suggestions based on the current builder context. */
    private fun getContextSuggestions(context: String): List<Pair<String, String>> {
        return CONTEXT_SUGGESTIONS[context] ?: DEFAULT_SUGGESTIONS
    }

    companion object {
        /** Known builder names for PSI-based context detection */
        private val BUILDER_NAMES =
            setOf(
                "entity",
                "scene",
                "sprite",
                "combat",
                "states",
                "state",
                "dialog",
                "camera",
                "stats",
                "flags",
                "page",
                "floor",
                "monster",
                "ability",
                "item",
                "encounterTable",
                "box",
                "portrait",
                "physics",
                "animations",
                "regions",
                "character",
                "battle",
                "inventory",
                "statusEffect",
            )

        /** Pre-compiled regex patterns for fallback text-based builder detection */
        private val BUILDER_PATTERNS: Map<String, Regex> =
            BUILDER_NAMES.associateWith { name -> Regex("\\b$name\\s*\\{") }

        private val DEFAULT_SUGGESTIONS =
            listOf(
                "position" to "Set position",
                "sprite" to "Add sprite",
                "whenever" to "Conditional action",
            )

        private val CONTEXT_SUGGESTIONS =
            mapOf(
                "entity" to
                    listOf(
                        "position" to "Set position",
                        "velocity" to "Add velocity component",
                        "sprite" to "Add sprite component",
                        "hitbox" to "Add hitbox component",
                        "combat" to "Add combat component",
                        "states" to "Add state machine",
                        "tag" to "Add tag",
                    ),
                "scene" to
                    listOf(
                        "enter" to "Called on scene entry",
                        "exit" to "Called on scene exit",
                        "every" to "Periodic callback",
                    ),
                "sprite" to
                    listOf(
                        "size" to "Sprite dimensions",
                        "palette" to "Color palette",
                        "paletteIndex" to "Palette slot (0-7)",
                        "hitbox" to "Collision bounds",
                        "regions" to "Sprite regions",
                        "animations" to "Animation definitions",
                    ),
                "combat" to
                    listOf(
                        "maxHp" to "Maximum HP",
                        "attackPower" to "Attack damage",
                        "defense" to "Defense value",
                        "team" to "Combat team",
                        "invincibilityFrames" to "I-frames after hit",
                        "knockbackForce" to "Knockback strength",
                    ),
                "states" to listOf("state" to "Define a state"),
                "state" to
                    listOf(
                        "enter" to "Called on state entry",
                        "exit" to "Called on state exit",
                        "tick" to "Called every frame",
                        "on" to "Condition transition",
                    ),
                "dialog" to
                    listOf(
                        "box" to "Dialog box settings",
                        "portrait" to "Speaker portrait",
                        "textSpeed" to "Text animation speed",
                        "textSound" to "Text sound effect",
                        "speaker" to "Default speaker name",
                    ),
                "camera" to
                    listOf(
                        "smoothing" to "Follow smoothing factor",
                        "offset" to "Camera offset",
                        "deadzone" to "Movement deadzone",
                        "bounds" to "Camera bounds",
                    ),
                "stats" to
                    listOf(
                        "hp" to "Health points",
                        "sp" to "Skill points",
                        "atk" to "Attack",
                        "def" to "Defense",
                        "matk" to "Magic attack",
                        "mdef" to "Magic defense",
                        "agl" to "Agility",
                        "acc" to "Accuracy",
                        "eva" to "Evasion",
                    ),
                "flags" to listOf("page" to "Define flag page"),
                "page" to listOf("flag" to "Define a flag"),
                "floor" to
                    listOf(
                        "map" to "Add map to floor",
                        "defaultPosition" to "Starting position",
                        "palettes" to "Floor palettes",
                        "exits" to "Define exits",
                        "objects" to "Add map objects",
                        "encounters" to "Encounter table",
                    ),
                "monster" to
                    listOf(
                        "name" to "Monster display name",
                        "size" to "Monster size",
                        "tier" to "Power tier",
                        "baseStats" to "Base statistics",
                        "aspects" to "Elemental aspects",
                        "ai" to "AI behavior",
                        "exp" to "Experience reward",
                        "drops" to "Loot drops",
                    ),
                "ability" to
                    listOf(
                        "name" to "Ability name",
                        "targeting" to "Target selection",
                        "cost" to "SP/MP cost",
                        "aspect" to "Elemental aspect",
                        "execute" to "Ability effect",
                    ),
                "item" to
                    listOf(
                        "name" to "Item name",
                        "description" to "Item description",
                        "category" to "Item category",
                        "maxStack" to "Max stack size",
                        "buyPrice" to "Purchase price",
                        "sellPrice" to "Sell price",
                        "onUse" to "Use effect",
                    ),
                "encounterTable" to
                    listOf(
                        "safeSteps" to "Steps before encounters",
                        "initialChance" to "Starting encounter chance",
                        "incrementPerStep" to "Chance increase per step",
                        "maxChance" to "Maximum encounter chance",
                        "entry" to "Add encounter entry",
                    ),
                "box" to
                    listOf(
                        "position" to "Box position",
                        "size" to "Box dimensions",
                        "border" to "Border style",
                        "padding" to "Content padding",
                    ),
                "animations" to listOf("plays" to "Animation frames", "every" to "Frame timing"),
                "regions" to listOf("at" to "Region start index", "size" to "Region tile count"),
                "character" to
                    listOf(
                        "name" to "Character name",
                        "stats" to "Character statistics",
                        "level" to "Starting level",
                        "onLevelUp" to "Level up callback",
                    ),
                "battle" to
                    listOf(
                        "maxPartySize" to "Max party members",
                        "maxEnemies" to "Max enemies",
                        "turnOrder" to "Turn order strategy",
                        "onState" to "State callback",
                    ),
                "inventory" to
                    listOf("maxSlots" to "Maximum slots", "startWith" to "Starting items"),
                "statusEffect" to
                    listOf(
                        "name" to "Effect name",
                        "duration" to "Effect duration",
                        "stackMode" to "Stacking behavior",
                        "onTurnEnd" to "Turn end callback",
                    ),
            )
    }

    private fun getTailText(keyword: String): String {
        return when (keyword) {
            "position" -> "(x, y)"
            "velocity" -> "(vx, vy)"
            "sprite" -> "(asset) { ... }"
            "hitbox" -> "(x, y, w, h)"
            "combat" -> " { ... }"
            "states" -> " { ... }"
            "state" -> "(name) { ... }"
            "tag" -> "(tagRef)"
            "enter",
            "exit",
            "tick" -> " { ... }"
            "on" -> "(condition) { goto(...) }"
            "every" -> ".frame { ... }"
            "size" -> " = w x h"
            "palette",
            "paletteIndex" -> " = ..."
            "regions",
            "animations" -> " { ... }"
            "box",
            "portrait" -> " { ... }"
            "page" -> "(name) { ... }"
            "flag" -> "(name)"
            else -> ""
        }
    }
}
