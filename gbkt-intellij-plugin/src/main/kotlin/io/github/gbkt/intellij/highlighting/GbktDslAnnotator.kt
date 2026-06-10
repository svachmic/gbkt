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

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Annotator that provides syntax highlighting for gbkt DSL elements.
 *
 * Since gbkt files are Kotlin scripts, the Kotlin lexer handles base tokenization. This annotator
 * adds gbkt-specific highlighting by examining Kotlin PSI elements and applying text attributes to
 * DSL function calls, input references, and condition operators.
 */
class GbktDslAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Only process gbkt files
        if (!element.containingFile.name.endsWith(".gbkt.kts")) {
            return
        }

        when (element) {
            is KtCallExpression -> annotateCallExpression(element, holder)
            is KtNameReferenceExpression -> annotateNameReference(element, holder)
        }
    }

    private fun annotateCallExpression(call: KtCallExpression, holder: AnnotationHolder) {
        val calleeElement = call.calleeExpression ?: return
        val callee = calleeElement.text

        val textKey =
            when {
                callee in GbktKeywords.TOP_LEVEL_FUNCTIONS -> GbktSyntaxHighlighter.DSL_FUNCTION
                callee in GbktKeywords.CONTROL_FLOW -> GbktSyntaxHighlighter.DSL_CONTROL_FLOW
                callee in GbktKeywords.BUILDER_METHODS -> GbktSyntaxHighlighter.DSL_BUILDER_METHOD
                callee in GbktKeywords.LIFECYCLE -> GbktSyntaxHighlighter.DSL_LIFECYCLE
                callee in GbktKeywords.CONDITIONS -> GbktSyntaxHighlighter.DSL_CONDITION
                else -> null
            }

        if (textKey != null) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(calleeElement.textRange)
                .textAttributes(textKey)
                .create()
        }
    }

    private fun annotateNameReference(
        reference: KtNameReferenceExpression,
        holder: AnnotationHolder,
    ) {
        val name = reference.getReferencedName()

        // Check if this is part of a dot-qualified expression (e.g., dpad.right, buttons.a)
        val parent = reference.parent

        val textKey =
            when {
                // Input keywords: dpad, buttons
                name in GbktKeywords.INPUT && parent !is KtCallExpression ->
                    GbktSyntaxHighlighter.DSL_INPUT

                // Type references
                name in GbktKeywords.TYPES -> GbktSyntaxHighlighter.DSL_TYPE

                // Condition infix operators used as references
                name in GbktKeywords.CONDITIONS && parent is KtDotQualifiedExpression ->
                    GbktSyntaxHighlighter.DSL_CONDITION

                else -> null
            }

        if (textKey != null) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(reference.textRange)
                .textAttributes(textKey)
                .create()
        }
    }
}
