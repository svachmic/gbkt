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
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import io.github.gbkt.intellij.GbktFileType
import io.github.gbkt.intellij.GbktIcons
import io.github.gbkt.intellij.lang.GbktDslVisitor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

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
        if (isInsideStringOrComment(position)) return

        val callContext = findCallContext(position)
        if (handleReferenceCompletion(callContext, file, result)) return

        val builderContext =
            findBuilderContextPsi(position) ?: findBuilderContextText(file.text, parameters.offset)

        addBuilderSuggestions(builderContext, result)
    }

    /** Check if position is inside a string template or comment. */
    private fun isInsideStringOrComment(position: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(position, KtStringTemplateExpression::class.java) !=
            null || PsiTreeUtil.getParentOfType(position, PsiComment::class.java) != null
    }

    /** Handle entity/scene reference completion. Returns true if handled. */
    private fun handleReferenceCompletion(
        callContext: String?,
        file: com.intellij.psi.PsiFile,
        result: CompletionResultSet,
    ): Boolean {
        if (callContext == null) return false

        return when (callContext) {
            in ENTITY_REFERENCE_FUNCTIONS -> {
                addEntityReferenceSuggestions(file, result)
                true
            }
            in SCENE_REFERENCE_FUNCTIONS -> {
                addSceneReferenceSuggestions(file, result)
                true
            }
            else -> false
        }
    }

    /** Add builder-context-specific suggestions to the result set. */
    private fun addBuilderSuggestions(builderContext: String, result: CompletionResultSet) {
        val suggestions = getContextSuggestions(builderContext)
        val hasContext = builderContext.isNotEmpty()

        for ((keyword, description) in suggestions) {
            val element =
                LookupElementBuilder.create(keyword)
                    .withIcon(GbktIcons.FILE)
                    .withTypeText(description)
                    .withTailText(getTailText(keyword), true)

            val prioritized =
                if (hasContext) PrioritizedLookupElement.withPriority(element, 100.0) else element
            result.addElement(prioritized)
        }
    }

    /**
     * Finds the immediate call context (e.g., "collidesWith" when completing inside its argument).
     */
    private fun findCallContext(position: PsiElement): String? {
        var current: PsiElement? = position.parent

        while (current != null) {
            when (current) {
                is KtCallExpression -> {
                    val calleeName = current.calleeExpression?.text
                    if (calleeName != null) return calleeName
                }
                is KtLambdaExpression -> return null // Stop at lambda boundaries
            }
            current = current.parent
        }

        return null
    }

    /**
     * Adds entity reference suggestions from all defined entities across the project. Searches all
     * .gbkt.kts files, not just the current file.
     */
    private fun addEntityReferenceSuggestions(
        file: com.intellij.psi.PsiFile,
        result: CompletionResultSet,
    ) {
        val project = file.project
        val addedNames = hashSetOf<String>()
        val gbktFiles =
            FileTypeIndex.getFiles(GbktFileType, GlobalSearchScope.projectScope(project))

        for (virtualFile in gbktFiles) {
            val psiFile =
                PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            val analysis = GbktDslVisitor.analyze(psiFile)
            val sourceFileSuffix =
                if (virtualFile != file.virtualFile) " - ${virtualFile.name}" else ""

            addDefinitionsToResult(
                analysis.entities,
                "Entity",
                sourceFileSuffix,
                addedNames,
                result,
            )
            addDefinitionsToResult(
                analysis.characters,
                "Character",
                sourceFileSuffix,
                addedNames,
                result,
            )
            addDefinitionsToResult(
                analysis.monsters,
                "Monster",
                sourceFileSuffix,
                addedNames,
                result,
            )
        }
    }

    /** Helper to add named definitions to completion results with deduplication. */
    private fun addDefinitionsToResult(
        definitions: List<GbktDslVisitor.DslDefinition>,
        typeName: String,
        sourceFileSuffix: String,
        addedNames: MutableSet<String>,
        result: CompletionResultSet,
    ) {
        for (definition in definitions) {
            if (addedNames.add(definition.name)) {
                result.addElement(
                    LookupElementBuilder.create(definition.name)
                        .withIcon(AllIcons.Nodes.Class)
                        .withTypeText(typeName)
                        .withTailText(sourceFileSuffix, true)
                )
            }
        }
    }

    /**
     * Adds scene reference suggestions from all defined scenes across the project. Searches all
     * .gbkt.kts files, not just the current file.
     */
    private fun addSceneReferenceSuggestions(
        file: com.intellij.psi.PsiFile,
        result: CompletionResultSet,
    ) {
        val project = file.project
        val addedNames = hashSetOf<String>()

        // Search all gbkt files in the project
        val gbktFiles =
            FileTypeIndex.getFiles(GbktFileType, GlobalSearchScope.projectScope(project))

        for (virtualFile in gbktFiles) {
            val psiFile =
                PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            val analysis = GbktDslVisitor.analyze(psiFile)

            for (scene in analysis.scenes) {
                if (addedNames.add(scene.name)) {
                    val sourceFile =
                        if (virtualFile != file.virtualFile) {
                            " - ${virtualFile.name}"
                        } else {
                            ""
                        }
                    result.addElement(
                        LookupElementBuilder.create(scene.name)
                            .withIcon(AllIcons.Nodes.Module)
                            .withTypeText("Scene")
                            .withTailText("$sourceFile", true)
                    )
                }
            }
        }
    }

    /**
     * Finds the builder context using Kotlin PSI traversal. Walks up the PSI tree looking for call
     * expressions with known builder names.
     */
    private fun findBuilderContextPsi(position: PsiElement): String? {
        var current: PsiElement? = position

        while (current != null) {
            val builderName = extractBuilderName(current)
            if (builderName != null) return builderName
            current = current.parent
        }

        return null
    }

    /** Extract a builder name from a PSI element if it's a relevant call expression. */
    private fun extractBuilderName(element: PsiElement): String? {
        return when (element) {
            is KtLambdaExpression -> {
                val callExpr = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java)
                callExpr?.calleeExpression?.text?.takeIf { it in BUILDER_NAMES }
            }
            is KtCallExpression -> {
                element.calleeExpression?.text?.takeIf { it in BUILDER_NAMES }
            }
            else -> null
        }
    }

    /**
     * Fallback: Finds the builder context by analyzing surrounding text using regex. Used when PSI
     * is not available or incomplete (e.g., during typing).
     */
    private fun findBuilderContextText(text: String, offset: Int): String {
        val textBefore = text.substring(0, minOf(offset, text.length))

        return BUILDER_PATTERNS.flatMap { (builder, pattern) ->
                pattern.findAll(textBefore).map { match -> builder to match }
            }
            .filter { (_, match) -> isBlockStillOpen(textBefore, match.range.last) }
            .maxByOrNull { (_, match) -> match.range.first }
            ?.first ?: ""
    }

    /** Check if a block starting at afterIndex is still open (more '{' than '}'). */
    private fun isBlockStillOpen(text: String, afterIndex: Int): Boolean {
        val afterMatch = text.substring(afterIndex)
        val openBraces = afterMatch.count { it == '{' }
        val closeBraces = afterMatch.count { it == '}' }
        return openBraces > closeBraces
    }

    /** Returns suggestions based on the current builder context. */
    private fun getContextSuggestions(context: String): List<Pair<String, String>> {
        return CONTEXT_SUGGESTIONS[context] ?: DEFAULT_SUGGESTIONS
    }

    companion object {
        /** Functions that take entity references as arguments. */
        private val ENTITY_REFERENCE_FUNCTIONS =
            setOf(
                "collidesWith",
                "overlaps",
                "follow",
                "damage",
                "heal",
                "moveTo",
                "lookAt",
                "distanceTo",
            )

        /** Functions that take scene references as arguments. */
        private val SCENE_REFERENCE_FUNCTIONS = setOf("scene", "transition", "goto")

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
        private val BUILDER_PATTERNS: Map<String, Regex> = BUILDER_NAMES.associateWith { name ->
            Regex("\\b$name\\s*\\{")
        }

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
            "combat",
            "states",
            "enter",
            "exit",
            "tick",
            "regions",
            "animations",
            "box",
            "portrait" -> " { ... }"
            "state",
            "page" -> "(name) { ... }"
            "tag" -> "(tagRef)"
            "on" -> "(condition) { goto(...) }"
            "every" -> ".frame { ... }"
            "size" -> " = w x h"
            "palette",
            "paletteIndex" -> " = ..."
            "flag" -> "(name)"
            else -> ""
        }
    }
}
