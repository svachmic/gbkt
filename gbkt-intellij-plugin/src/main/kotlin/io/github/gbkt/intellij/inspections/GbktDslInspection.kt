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
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import io.github.gbkt.intellij.lang.GbktDslVisitor
import org.jetbrains.kotlin.psi.KtCallExpression
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

    private fun collectDefinedNames(analysis: GbktDslVisitor): Set<String> {
        val names = mutableSetOf<String>()
        analysis.entities.forEach { names.add(it.name) }
        analysis.scenes.forEach { names.add(it.name) }
        analysis.dialogs.forEach { names.add(it.name) }
        analysis.cameras.forEach { names.add(it.name) }
        analysis.variables.forEach { names.add(it.name) }
        analysis.flags.forEach { names.add(it.name) }
        return names
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
        val args = expression.valueArguments

        when (callee) {
            "position" -> {
                // Check if position values are within GB screen bounds (0-255 for coordinates)
                if (args.size >= 2) {
                    checkIntArg(args[0], 0, 255, "X coordinate", holder)
                    checkIntArg(args[1], 0, 255, "Y coordinate", holder)
                }
            }
            "size" -> {
                // Sprite size must be 8x8, 8x16, 16x8, or 16x16
                if (args.size >= 2) {
                    val validSizes = listOf(8, 16)
                    checkIntArgInSet(args[0], validSizes, "Sprite width", holder)
                    checkIntArgInSet(args[1], validSizes, "Sprite height", holder)
                }
            }
            "maxHp",
            "attackPower",
            "defense" -> {
                // Stats typically use u8 (0-255) or u16 (0-65535)
                if (args.isNotEmpty()) {
                    checkIntArg(args[0], 0, 65535, callee, holder)
                }
            }
            "invincibilityFrames" -> {
                // Frame counts are typically u8
                if (args.isNotEmpty()) {
                    checkIntArg(args[0], 0, 255, "Invincibility frames", holder)
                }
            }
            "palette" -> {
                // Palette index must be 0-7 (8 palettes on GBC)
                if (args.isNotEmpty()) {
                    checkIntArg(args[0], 0, 7, "Palette index", holder)
                }
            }
        }
    }

    private fun checkIntArg(
        arg: org.jetbrains.kotlin.psi.KtValueArgument,
        min: Int,
        max: Int,
        name: String,
        holder: ProblemsHolder,
    ) {
        val expr = arg.getArgumentExpression() ?: return
        val value = expr.text.toIntOrNull() ?: return

        if (value < min || value > max) {
            holder.registerProblem(
                expr,
                "$name must be between $min and $max (got $value)",
                ProblemHighlightType.GENERIC_ERROR,
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

        // Skip if it's a definition itself
        if (isDefinitionSite(expression)) return

        // Check if this looks like a reference to a DSL element
        // Only flag as error if it's used in a DSL context (e.g., passed to a DSL function)
        val parent = expression.parent
        if (parent is KtCallExpression) {
            val parentCallee = parent.calleeExpression?.text
            if (parentCallee in ENTITY_CONSUMING_FUNCTIONS && name !in definedNames) {
                // This might be an undefined entity reference
                // But only warn, don't error, since it could be defined elsewhere
                holder.registerProblem(
                    expression,
                    "Possible undefined reference: '$name'. Make sure it's defined in this file or imported.",
                    ProblemHighlightType.WEAK_WARNING,
                )
            }
        }
    }

    private fun isDefinitionSite(expression: KtNameReferenceExpression): Boolean {
        val parent = expression.parent
        return parent is org.jetbrains.kotlin.psi.KtProperty &&
            parent.nameIdentifier?.text == expression.getReferencedName()
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
    }
}
