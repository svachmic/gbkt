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
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Provides type-aware completion for gbkt DSL enum values.
 *
 * Detects assignment and function argument contexts to suggest appropriate enum values:
 * - slot = → EquipSlot values
 * - category = → ItemCategory values
 * - targeting = → TargetingMode values
 * - aspect = → Aspect values
 * - tier = → MonsterTier values
 * - turnOrder = → TurnOrderStrategy values
 * - stackMode = → StackMode values
 * - movementStyle = → MovementStyle values
 */
class GbktTypeAwareCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val file = parameters.originalFile
        if (!file.name.endsWith(".gbkt.kts")) return

        val position = parameters.position

        // Skip completion inside strings and comments for performance
        if (PsiTreeUtil.getParentOfType(position, KtStringTemplateExpression::class.java) != null) return
        if (PsiTreeUtil.getParentOfType(position, PsiComment::class.java) != null) return

        // Detect assignment or named argument context
        val parameterName = detectParameterContext(position) ?: return

        // Get enum values for this parameter
        val enumValues = ENUM_MAPPINGS[parameterName] ?: return

        // Add enum value suggestions
        for ((enumType, values) in enumValues) {
            // Get the fully qualified name for this enum type
            val fqn = ENUM_FQN_MAPPINGS[enumType]

            for (value in values) {
                val builder = LookupElementBuilder.create("$enumType.$value")
                    .withIcon(AllIcons.Nodes.Enum)
                    .withTypeText(enumType)
                    .withPresentableText(value)
                    .withTailText(" ($enumType)", true)

                // Add import handler if we have a known FQN
                val element = if (fqn != null) {
                    builder.withInsertHandler(EnumImportInsertHandler(fqn))
                } else {
                    builder
                }

                result.addElement(element)
            }
        }
    }

    /**
     * Insert handler that adds the import for enum types when selected.
     */
    private class EnumImportInsertHandler(private val fqn: String) : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val file = context.file as? KtFile ?: return
            val project = context.project

            // Check if import already exists
            val importDirectives = file.importDirectives
            val alreadyImported = importDirectives.any { directive ->
                val importPath = directive.importPath?.pathStr
                importPath == fqn || importPath == "$fqn.*" ||
                    importPath?.startsWith("${fqn.substringBeforeLast('.')}.*") == true
            }

            if (!alreadyImported) {
                // Add the import statement
                val psiFactory = KtPsiFactory(project)
                val importPath = org.jetbrains.kotlin.resolve.ImportPath(
                    org.jetbrains.kotlin.name.FqName(fqn),
                    false // isAllUnder
                )
                val importDirective = psiFactory.createImportDirective(importPath)

                // Find the import list or create one
                val importList = file.importList
                if (importList != null) {
                    importList.add(importDirective)
                } else {
                    // Add after package statement
                    val packageDirective = file.packageDirective
                    if (packageDirective != null) {
                        file.addAfter(importDirective, packageDirective)
                    } else {
                        file.addBefore(importDirective, file.firstChild)
                    }
                }
            }
        }
    }

    /**
     * Detects the parameter name context from assignment or function argument.
     * Returns the parameter name (e.g., "slot", "category", "targeting").
     */
    private fun detectParameterContext(position: PsiElement): String? {
        // Check for binary expression (assignment): slot = <cursor>
        val binaryExpr = PsiTreeUtil.getParentOfType(position, KtBinaryExpression::class.java)
        if (binaryExpr != null) {
            val left = binaryExpr.left
            if (left is KtNameReferenceExpression) {
                return left.getReferencedName()
            }
        }

        // Check for function argument: targeting(<cursor>) or targeting = <cursor>
        val valueArg = PsiTreeUtil.getParentOfType(position, KtValueArgument::class.java)
        if (valueArg != null) {
            // Get the argument name if named argument
            val argName = valueArg.getArgumentName()?.asName?.asString()
            if (argName != null) return argName

            // Try to get from parent call expression by position
            val callExpr = PsiTreeUtil.getParentOfType(valueArg, KtCallExpression::class.java)
            if (callExpr != null) {
                val callee = callExpr.calleeExpression?.text
                // Some functions take the enum directly as first argument
                if (callee in FIRST_ARG_ENUM_FUNCTIONS) {
                    return callee
                }
            }
        }

        // Check for call expression where the function name indicates the enum
        val callExpr = PsiTreeUtil.getParentOfType(position, KtCallExpression::class.java)
        if (callExpr != null) {
            val callee = callExpr.calleeExpression?.text
            if (callee in FIRST_ARG_ENUM_FUNCTIONS) {
                return callee
            }
        }

        return null
    }

    companion object {
        /** Maps enum type names to their fully qualified names in gbkt-core. */
        private val ENUM_FQN_MAPPINGS = mapOf(
            "EquipSlot" to "io.github.gbkt.core.rpg.EquipSlot",
            "ItemCategory" to "io.github.gbkt.core.rpg.ItemCategory",
            "TargetingMode" to "io.github.gbkt.core.rpg.TargetingMode",
            "Aspect" to "io.github.gbkt.core.rpg.Aspect",
            "MonsterTier" to "io.github.gbkt.core.rpg.MonsterTier",
            "TurnOrderStrategy" to "io.github.gbkt.core.rpg.TurnOrderStrategy",
            "StackMode" to "io.github.gbkt.core.rpg.StackMode",
            "MovementStyle" to "io.github.gbkt.core.exploration.MovementStyle",
            "Team" to "io.github.gbkt.core.combat.Team",
            "BattleState" to "io.github.gbkt.core.rpg.BattleState",
            "Easing" to "io.github.gbkt.core.graphics.Easing",
        )

        /** Functions where the first argument is an enum type. */
        private val FIRST_ARG_ENUM_FUNCTIONS = setOf(
            "targeting",
            "aspect",
            "tier",
            "turnOrder",
            "stackMode",
            "movementStyle",
            "category",
            "slot",
        )

        /** Mapping from parameter names to their possible enum types and values. */
        private val ENUM_MAPPINGS: Map<String, Map<String, List<String>>> = mapOf(
            // Equipment slot
            "slot" to mapOf(
                "EquipSlot" to listOf(
                    "WEAPON",
                    "SHIELD",
                    "HEAD",
                    "BODY",
                    "ACCESSORY",
                    "ACCESSORY_1",
                    "ACCESSORY_2",
                )
            ),

            // Item category
            "category" to mapOf(
                "ItemCategory" to listOf(
                    "CONSUMABLE",
                    "WEAPON",
                    "ARMOR",
                    "ACCESSORY",
                    "KEY_ITEM",
                    "MATERIAL",
                )
            ),

            // Ability targeting
            "targeting" to mapOf(
                "TargetingMode" to listOf(
                    "SELF",
                    "SINGLE_ALLY",
                    "SINGLE_ENEMY",
                    "ALL_ALLIES",
                    "ALL_ENEMIES",
                    "ALL",
                    "RANDOM_ENEMY",
                    "RANDOM_ALLY",
                )
            ),

            // Elemental aspect
            "aspect" to mapOf(
                "Aspect" to listOf(
                    "FIRE",
                    "ICE",
                    "LIGHTNING",
                    "EARTH",
                    "WIND",
                    "WATER",
                    "LIGHT",
                    "DARK",
                    "PHYSICAL",
                    "NONE",
                )
            ),

            // Monster tier
            "tier" to mapOf(
                "MonsterTier" to listOf(
                    "COMMON",
                    "UNCOMMON",
                    "RARE",
                    "ELITE",
                    "BOSS",
                    "LEGENDARY",
                )
            ),

            // Turn order strategy
            "turnOrder" to mapOf(
                "TurnOrderStrategy" to listOf(
                    "SPEED_BASED",
                    "ROUND_ROBIN",
                    "PLAYER_FIRST",
                    "ENEMY_FIRST",
                    "RANDOM",
                )
            ),

            // Status effect stacking
            "stackMode" to mapOf(
                "StackMode" to listOf(
                    "REPLACE",
                    "REFRESH_DURATION",
                    "STACK_INTENSITY",
                    "STACK_DURATION",
                    "IGNORE",
                )
            ),

            // Movement style for exploration
            "movementStyle" to mapOf(
                "MovementStyle" to listOf(
                    "GRID",
                    "SMOOTH",
                    "FREE",
                )
            ),

            // Combat team
            "team" to mapOf(
                "Team" to listOf(
                    "PLAYER",
                    "ENEMY",
                    "NEUTRAL",
                )
            ),

            // Battle state
            "onState" to mapOf(
                "BattleState" to listOf(
                    "INIT",
                    "PLAYER_TURN",
                    "ENEMY_TURN",
                    "ACTION",
                    "VICTORY",
                    "DEFEAT",
                    "FLEE",
                )
            ),

            // Easing functions
            "easing" to mapOf(
                "Easing" to listOf(
                    "LINEAR",
                    "EASE_IN",
                    "EASE_OUT",
                    "EASE_IN_OUT",
                    "BOUNCE",
                    "ELASTIC",
                )
            ),

            // Sprite size (special case - uses infix notation)
            "size" to mapOf(
                "" to listOf("8 x 8", "8 x 16", "16 x 8", "16 x 16")
            ),
        )
    }
}
