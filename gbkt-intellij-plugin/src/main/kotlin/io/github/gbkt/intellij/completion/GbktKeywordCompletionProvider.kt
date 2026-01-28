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
        if (PsiTreeUtil.getParentOfType(position, KtStringTemplateExpression::class.java) != null) return
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

    private fun getTailText(keyword: String): String {
        return when (keyword) {
            "gbGame" -> " { ... }"
            "scene" -> "(name) { ... }"
            "entity" -> " { ... }"
            "dialog" -> "(name) { ... }"
            "camera" -> "(name) { ... }"
            "stats" -> " { ... }"
            "flags" -> " { ... }"
            "u8Var",
            "u16Var",
            "i8Var",
            "i16Var" -> "(initial)"
            "u8Array",
            "u16Array",
            "i8Array",
            "i16Array" -> "(size, initial)"
            "monster" -> " { ... }"
            "ability" -> " { ... }"
            "item" -> " { ... }"
            "floor" -> " { ... }"
            "encounterTable" -> "(id) { ... }"
            "whenever" -> "(condition) { ... }"
            "branch" -> " { ... }"
            "repeat" -> "(times) { ... }"
            else -> ""
        }
    }

    private fun insertKeywordTemplate(
        ctx: com.intellij.codeInsight.completion.InsertionContext,
        keyword: String,
    ) {
        val editor = ctx.editor
        val document = editor.document

        val template =
            when (keyword) {
                "gbGame" -> " {\n    \n}"
                "scene" -> "(\"name\") {\n    \n}"
                "entity" -> " {\n    \n}"
                "dialog" -> "(\"name\") {\n    \n}"
                "camera" -> "(\"main\") {\n    \n}"
                "stats" -> " {\n    \n}"
                "flags" -> " {\n    \n}"
                "u8Var",
                "u16Var",
                "i8Var",
                "i16Var" -> "(0)"
                "u8Array",
                "u16Array",
                "i8Array",
                "i16Array" -> "(10, 0)"
                "monster" -> " {\n    \n}"
                "ability" -> " {\n    \n}"
                "item" -> " {\n    \n}"
                "floor" -> " {\n    \n}"
                "encounterTable" -> "(\"id\") {\n    \n}"
                "whenever" -> "() {\n    \n}"
                "branch" -> " {\n    \n}"
                "repeat" -> "(1) {\n    \n}"
                else -> return
            }

        document.insertString(ctx.tailOffset, template)

        // Position caret inside the block or at the parameter
        val caretOffset =
            when (keyword) {
                "scene",
                "dialog",
                "encounterTable" -> ctx.tailOffset + 2 // Inside quotes
                "whenever" -> ctx.tailOffset + 1 // Inside parentheses
                else -> ctx.tailOffset + template.indexOf('\n') + 5 // Inside block
            }
        editor.caretModel.moveToOffset(caretOffset)
    }
}
