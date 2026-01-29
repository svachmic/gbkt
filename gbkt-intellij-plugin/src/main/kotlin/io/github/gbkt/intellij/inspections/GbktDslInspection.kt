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
package io.github.gbkt.intellij.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import io.github.gbkt.intellij.lang.GbktDslVisitor
import io.github.gbkt.intellij.quickfix.ClampValueQuickFix
import io.github.gbkt.intellij.quickfix.CreateCharacterQuickFix
import io.github.gbkt.intellij.quickfix.CreateEntityQuickFix
import io.github.gbkt.intellij.quickfix.CreateMonsterQuickFix
import io.github.gbkt.intellij.quickfix.CreateSceneQuickFix
import io.github.gbkt.intellij.quickfix.CreateVariableQuickFix
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Inspection for gbkt DSL validation.
 *
 * Checks for:
 * - Undefined entity/scene references
 * - Invalid DSL method calls (wrong context)
 * - Invalid numeric values for Game Boy constraints
 * - Missing required DSL blocks
 */
class GbktDslInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "gbkt DSL validation"

    override fun getGroupDisplayName(): String = "gbkt"

    override fun isEnabledByDefault(): Boolean = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        if (!GbktDslVisitor.isGbktFile(file)) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        // Pre-analyze the file to get all definitions
        val analysis = GbktDslVisitor.analyze(file)
        val definedNames = collectDefinedNames(analysis)

        return object : KtVisitorVoid() {

            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                val callee = expression.calleeExpression?.text ?: return

                // Check for context requirements
                checkContextRequirements(expression, callee, holder)

                // Check for Game Boy constraints
                checkGameBoyConstraints(expression, callee, holder)
            }

            override fun visitReferenceExpression(
                expression: org.jetbrains.kotlin.psi.KtReferenceExpression
            ) {
                super.visitReferenceExpression(expression)

                // Only process name reference expressions
                if (expression is KtNameReferenceExpression) {
                    // Check for undefined references (only in specific DSL contexts)
                    checkUndefinedReference(expression, definedNames, holder)
                }
            }
        }
    }

    private fun collectDefinedNames(analysis: GbktDslVisitor): Set<String> = buildSet {
        analysis.entities.forEach { add(it.name) }
        analysis.scenes.forEach { add(it.name) }
        analysis.dialogs.forEach { add(it.name) }
        analysis.cameras.forEach { add(it.name) }
        analysis.variables.forEach { add(it.name) }
        analysis.flags.forEach { add(it.name) }
        // RPG definitions
        analysis.characters.forEach { add(it.name) }
        analysis.monsters.forEach { add(it.name) }
        analysis.abilities.forEach { add(it.name) }
        analysis.items.forEach { add(it.name) }
        analysis.floors.forEach { add(it.name) }
        analysis.battles.forEach { add(it.name) }
        analysis.inventories.forEach { add(it.name) }
        analysis.statusEffects.forEach { add(it.name) }
    }

    private fun checkContextRequirements(
        expression: KtCallExpression,
        callee: String,
        holder: ProblemsHolder,
    ) {
        val requiredContexts = GbktDslVisitor.CONTEXT_REQUIREMENTS[callee] ?: return

        // Find parent DSL context
        val parentContext = findParentDslContext(expression)

        if (parentContext != null && parentContext !in requiredContexts) {
            val validContexts = requiredContexts.joinToString(", ")
            val message =
                "'$callee' cannot be used inside '$parentContext'. " +
                    "Valid contexts: $validContexts"
            holder.registerProblem(
                expression.calleeExpression ?: expression,
                message,
                ProblemHighlightType.GENERIC_ERROR,
            )
        }
    }

    private fun findParentDslContext(expression: KtCallExpression): String? {
        var parent = expression.parent
        while (parent != null) {
            if (parent is KtCallExpression) {
                val parentCallee = parent.calleeExpression?.text
                if (parentCallee in GbktDslVisitor.DSL_FUNCTIONS) {
                    return parentCallee
                }
            }
            parent = parent.parent
        }
        return null
    }

    private fun checkGameBoyConstraints(
        expression: KtCallExpression,
        callee: String,
        holder: ProblemsHolder,
    ) {
        val args = expression.valueArguments.toList()
        // Use centralized constraints from ClampValueQuickFix for consistency
        val constraints = ClampValueQuickFix.Companion.Constraints

        when (callee) {
            "position" -> {
                // Check if position values are within GB screen bounds
                if (args.size >= 2) {
                    checkIntArgRange(args[0], constraints.SCREEN_X, "X coordinate", holder)
                    checkIntArgRange(args[1], constraints.SCREEN_Y, "Y coordinate", holder)
                }
            }
            "size" -> {
                // Sprite size must be 8x8, 8x16, 16x8, or 16x16
                if (args.size >= 2) {
                    checkIntArgInSet(args[0], constraints.SPRITE_SIZE, "Sprite width", holder)
                    checkIntArgInSet(args[1], constraints.SPRITE_SIZE, "Sprite height", holder)
                }
            }
            "maxHp",
            "attackPower",
            "defense" -> {
                // Stats typically use u16 (0-65535)
                if (args.isNotEmpty()) {
                    checkIntArgRange(args[0], constraints.U16_RANGE, callee, holder)
                }
            }
            "invincibilityFrames" -> {
                // Frame counts are typically u8
                if (args.isNotEmpty()) {
                    checkIntArgRange(args[0], constraints.U8_RANGE, "Invincibility frames", holder)
                }
            }
            "palette",
            "paletteIndex" -> {
                // Palette index must be 0-7 (8 palettes on GBC)
                if (args.isNotEmpty()) {
                    checkIntArgRange(args[0], constraints.PALETTE_INDEX, "Palette index", holder)
                }
            }
        }
    }

    /**
     * Checks an integer argument against an IntRange constraint. Uses centralized constraints from
     * ClampValueQuickFix.Constraints.
     */
    private fun checkIntArgRange(
        arg: org.jetbrains.kotlin.psi.KtValueArgument,
        range: IntRange,
        name: String,
        holder: ProblemsHolder,
    ) {
        val expr = arg.getArgumentExpression() ?: return
        val value = expr.text.toIntOrNull() ?: return

        if (value !in range) {
            holder.registerProblem(
                expr,
                "$name must be between ${range.first} and ${range.last} (got $value)",
                ProblemHighlightType.GENERIC_ERROR,
                ClampValueQuickFix(value, range.first, range.last, name),
            )
        }
    }

    private fun checkIntArgInSet(
        arg: org.jetbrains.kotlin.psi.KtValueArgument,
        validValues: List<Int>,
        name: String,
        holder: ProblemsHolder,
    ) {
        val expr = arg.getArgumentExpression() ?: return
        val value = expr.text.toIntOrNull() ?: return

        if (value !in validValues) {
            holder.registerProblem(
                expr,
                "$name must be one of: ${validValues.joinToString(", ")} (got $value)",
                ProblemHighlightType.GENERIC_ERROR,
            )
        }
    }

    private fun checkUndefinedReference(
        expression: KtNameReferenceExpression,
        definedNames: Set<String>,
        holder: ProblemsHolder,
    ) {
        val name = expression.getReferencedName()

        // Skip if it's a known DSL function
        if (name in GbktDslVisitor.DSL_FUNCTIONS) return

        // Skip common Kotlin/stdlib names
        if (name in COMMON_KOTLIN_NAMES) return

        // Skip common gbkt-core types and imports
        if (name in GBKT_CORE_TYPES) return

        // Skip if it's a definition itself
        if (isDefinitionSite(expression)) return

        // Skip if it's a lambda parameter (e.g., { target -> target.damage() })
        if (isLambdaParameter(expression, name)) return

        // Check if this looks like a reference to a DSL element
        // Only flag as error if it's used in a DSL context (e.g., passed to a DSL function)
        val parent = expression.parent
        if (parent is KtCallExpression) {
            val parentCallee = parent.calleeExpression?.text
            if (parentCallee != null && name !in definedNames) {
                // Determine appropriate quick fixes based on context
                val quickFixes = getQuickFixesForContext(parentCallee, name)

                if (quickFixes.isNotEmpty()) {
                    holder.registerProblem(
                        expression,
                        "Possible undefined reference: '$name'. Make sure it's defined in this file or imported.",
                        ProblemHighlightType.WEAK_WARNING,
                        *quickFixes.toTypedArray(),
                    )
                } else if (parentCallee in ENTITY_CONSUMING_FUNCTIONS) {
                    // Fallback for entity-consuming functions without specific quick fixes
                    holder.registerProblem(
                        expression,
                        "Possible undefined reference: '$name'. Make sure it's defined in this file or imported.",
                        ProblemHighlightType.WEAK_WARNING,
                        CreateEntityQuickFix(name),
                    )
                }
            }
        }
    }

    /** Returns appropriate quick fixes based on the context function. */
    private fun getQuickFixesForContext(callee: String, name: String): List<LocalQuickFix> {
        return when (callee) {
            // Entity-consuming functions
            "collidesWith",
            "overlaps",
            "follow",
            "damage",
            "heal" -> listOf(CreateEntityQuickFix(name))

            // Scene-consuming functions
            "scene",
            "transition",
            "goto" -> listOf(CreateSceneQuickFix(name))

            // Party/character-consuming functions
            "addToParty",
            "removeFromParty",
            "equipTo" -> listOf(CreateCharacterQuickFix(name), CreateEntityQuickFix(name))

            // Monster-consuming functions (encounters, battle setup)
            "entry",
            "addEnemy",
            "spawnMonster" -> listOf(CreateMonsterQuickFix(name))

            // Could be variable or entity
            "isEqualTo",
            "isGreaterThan",
            "isLessThan",
            "isAtLeast",
            "isAtMost" ->
                listOf(
                    CreateVariableQuickFix(name, CreateVariableQuickFix.VariableType.U8),
                    CreateVariableQuickFix(name, CreateVariableQuickFix.VariableType.U16),
                    CreateEntityQuickFix(name),
                )

            // Arithmetic operations - likely variables
            "set",
            "add",
            "subtract" ->
                listOf(
                    CreateVariableQuickFix(name, CreateVariableQuickFix.VariableType.U8),
                    CreateVariableQuickFix(name, CreateVariableQuickFix.VariableType.U16),
                )

            else -> emptyList()
        }
    }

    private fun isDefinitionSite(expression: KtNameReferenceExpression): Boolean {
        val parent = expression.parent
        return parent is org.jetbrains.kotlin.psi.KtProperty &&
            parent.nameIdentifier?.text == expression.getReferencedName()
    }

    /**
     * Checks if the reference is to a lambda parameter. Example: `execute { target ->
     * target.damage() }` - "target" is a lambda parameter.
     */
    private fun isLambdaParameter(expression: KtNameReferenceExpression, name: String): Boolean {
        // Walk up the tree to find enclosing lambda expressions
        var current: com.intellij.psi.PsiElement? = expression.parent
        while (current != null) {
            // Check if this is a lambda with a parameter matching the given name
            if (
                current is KtFunctionLiteral &&
                    current.valueParameters.toList().any { it.name == name }
            ) {
                return true
            }
            current = current.parent
        }
        return false
    }

    companion object {
        /** DSL functions that consume entity/scene references. */
        private val ENTITY_CONSUMING_FUNCTIONS =
            setOf("collidesWith", "overlaps", "follow", "scene")

        /** Common Kotlin names to ignore. */
        private val COMMON_KOTLIN_NAMES =
            setOf(
                "println",
                "print",
                "listOf",
                "mapOf",
                "setOf",
                "arrayOf",
                "true",
                "false",
                "null",
                "this",
                "it",
                "Int",
                "String",
                "Boolean",
                "Unit",
                "Any",
                "Nothing",
                "Math",
                "kotlin",
                "java",
            )

        /**
         * Common gbkt-core types that are imported automatically or commonly used. These should not
         * trigger "undefined reference" warnings.
         */
        private val GBKT_CORE_TYPES =
            setOf(
                // Input system
                "dpad",
                "buttons",
                "screen",
                // Asset types
                "SpriteAsset",
                "TilesetAsset",
                "PaletteAsset",
                "SoundAsset",
                "MusicAsset",
                // Enums
                "EquipSlot",
                "ItemCategory",
                "TargetingMode",
                "Aspect",
                "MonsterTier",
                "TurnOrderStrategy",
                "StackMode",
                "MovementStyle",
                "Easing",
                "Team",
                "BattleState",
                // Common references
                "context",
                "caster",
                "target",
                "player",
                "enemy",
                // Frame/duration helpers
                "frames",
                "seconds",
                // Common DSL receivers
                "stats",
                "combat",
                "sprite",
                "position",
                "velocity",
            )
    }
}
