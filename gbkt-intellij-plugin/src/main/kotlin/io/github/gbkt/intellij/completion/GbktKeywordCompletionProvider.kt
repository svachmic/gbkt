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
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import io.github.gbkt.intellij.GbktIcons
import io.github.gbkt.intellij.highlighting.GbktKeywords
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Provides completion for top-level gbkt DSL keywords.
 *
 * Suggests keywords like:
 * - gbGame, scene, entity, dialog, camera
 * - u8Var, u16Var, i8Var, i16Var
 * - monster, ability, item, floor
 * - whenever, branch, repeat
 */
class GbktKeywordCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val file = parameters.originalFile
        if (!file.name.endsWith(".gbkt.kts")) return

        // Skip completion inside strings and comments for performance
        val position = parameters.position
        if (PsiTreeUtil.getParentOfType(position, KtStringTemplateExpression::class.java) != null)
            return
        if (PsiTreeUtil.getParentOfType(position, PsiComment::class.java) != null) return

        // Add top-level DSL functions
        for (keyword in GbktKeywords.TOP_LEVEL_FUNCTIONS) {
            result.addElement(
                LookupElementBuilder.create(keyword)
                    .withIcon(GbktIcons.FILE)
                    .withTypeText("gbkt DSL")
                    .withTailText(getTailText(keyword), true)
                    .withInsertHandler { ctx, _ -> insertKeywordTemplate(ctx, keyword) }
            )
        }

        // Add control flow keywords
        for (keyword in GbktKeywords.CONTROL_FLOW) {
            result.addElement(
                LookupElementBuilder.create(keyword)
                    .withIcon(GbktIcons.FILE)
                    .withTypeText("control flow")
                    .withBoldness(true)
            )
        }

        // Add input keywords
        for (keyword in GbktKeywords.INPUT) {
            result.addElement(LookupElementBuilder.create(keyword).withTypeText("input"))
        }

        // Add condition operators
        for (keyword in GbktKeywords.CONDITIONS) {
            result.addElement(LookupElementBuilder.create(keyword).withTypeText("condition"))
        }
    }

    private fun getTailText(keyword: String): String = KEYWORD_TEMPLATES[keyword]?.tailText ?: ""

    private fun insertKeywordTemplate(
        ctx: com.intellij.codeInsight.completion.InsertionContext,
        keyword: String,
    ) {
        val template = KEYWORD_TEMPLATES[keyword] ?: return
        val editor = ctx.editor

        editor.document.insertString(ctx.tailOffset, template.insertion)

        val caretOffset =
            when (template.caretTarget) {
                CaretTarget.INSIDE_QUOTES -> ctx.tailOffset + 2
                CaretTarget.INSIDE_PARENS -> ctx.tailOffset + 1
                CaretTarget.INSIDE_BLOCK -> ctx.tailOffset + template.insertion.indexOf('\n') + 5
            }
        editor.caretModel.moveToOffset(caretOffset)
    }

    /** Where the caret lands after a keyword template has been inserted. */
    private enum class CaretTarget {
        /** Between the quotes of the template's string argument, e.g. `("name")`. */
        INSIDE_QUOTES,
        /** Just inside the opening parenthesis, e.g. `()`. */
        INSIDE_PARENS,
        /** On the indented blank line of the inserted block (or after a value argument). */
        INSIDE_BLOCK,
    }

    /** Couples the popup tail text with the snippet inserted when the keyword is selected. */
    private data class KeywordTemplate(
        val tailText: String,
        val insertion: String,
        val caretTarget: CaretTarget = CaretTarget.INSIDE_BLOCK,
    )

    companion object {
        private const val BLOCK_TAIL = " { ... }"
        private const val NAMED_BLOCK_TAIL = "(name) { ... }"
        private const val BLOCK_BODY = " {\n    \n}"

        /** Keywords that open a plain `{ ... }` block. */
        private val BLOCK_TEMPLATE = KeywordTemplate(BLOCK_TAIL, BLOCK_BODY)

        /** Keywords that take a quoted name argument before their block. */
        private val NAMED_BLOCK_TEMPLATE =
            KeywordTemplate(NAMED_BLOCK_TAIL, "(\"name\")$BLOCK_BODY", CaretTarget.INSIDE_QUOTES)

        /** Scalar variable declarations, e.g. `u8Var(0)`. */
        private val VAR_TEMPLATE = KeywordTemplate("(initial)", "(0)")

        /** Array variable declarations, e.g. `u8Array(10, 0)`. */
        private val ARRAY_TEMPLATE = KeywordTemplate("(size, initial)", "(10, 0)")

        /** Tail text and insertion snippet for every keyword that supports template insertion. */
        private val KEYWORD_TEMPLATES: Map<String, KeywordTemplate> =
            mapOf(
                "gbGame" to BLOCK_TEMPLATE,
                "scene" to NAMED_BLOCK_TEMPLATE,
                "entity" to BLOCK_TEMPLATE,
                "dialog" to NAMED_BLOCK_TEMPLATE,
                "camera" to KeywordTemplate(NAMED_BLOCK_TAIL, "(\"main\")$BLOCK_BODY"),
                "stats" to BLOCK_TEMPLATE,
                "flags" to BLOCK_TEMPLATE,
                "u8Var" to VAR_TEMPLATE,
                "u16Var" to VAR_TEMPLATE,
                "i8Var" to VAR_TEMPLATE,
                "i16Var" to VAR_TEMPLATE,
                "u8Array" to ARRAY_TEMPLATE,
                "u16Array" to ARRAY_TEMPLATE,
                "i8Array" to ARRAY_TEMPLATE,
                "i16Array" to ARRAY_TEMPLATE,
                "monster" to BLOCK_TEMPLATE,
                "ability" to BLOCK_TEMPLATE,
                "item" to BLOCK_TEMPLATE,
                "floor" to BLOCK_TEMPLATE,
                "encounterTable" to
                    KeywordTemplate(
                        "(id) { ... }",
                        "(\"id\")$BLOCK_BODY",
                        CaretTarget.INSIDE_QUOTES,
                    ),
                "whenever" to
                    KeywordTemplate(
                        "(condition) { ... }",
                        "()$BLOCK_BODY",
                        CaretTarget.INSIDE_PARENS,
                    ),
                "branch" to BLOCK_TEMPLATE,
                "repeat" to KeywordTemplate("(times) { ... }", "(1)$BLOCK_BODY"),
            )
    }
}
