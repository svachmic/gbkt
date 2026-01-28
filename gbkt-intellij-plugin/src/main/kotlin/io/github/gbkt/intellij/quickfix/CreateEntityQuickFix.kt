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
package io.github.gbkt.intellij.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick fix that creates a new entity definition.
 *
 * When an undefined entity reference is detected, this quick fix offers to create
 * a new entity definition at the appropriate location in the file.
 */
class CreateEntityQuickFix(private val entityName: String) : LocalQuickFix {

    override fun getName(): String = "Create entity '$entityName'"

    override fun getFamilyName(): String = "gbkt DSL quick fixes"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement.containingFile as? KtFile ?: return
        val psiFactory = KtPsiFactory(project)

        // Template for new entity with TODO placeholders for user customization
        // Uses minimal template - user adds components as needed
        // Default position is centered on screen (80, 72)
        val defaults = ClampValueQuickFix.Companion.Defaults
        val template = """

val $entityName by entity {
    // TODO: Set initial position
    position(${defaults.ENTITY_X}, ${defaults.ENTITY_Y})
    // TODO: Configure sprite (uncomment and adjust)
    // sprite(SpriteAsset("$entityName.png")) {
    //     size = ${defaults.SPRITE_WIDTH} x ${defaults.SPRITE_HEIGHT}
    // }
}
"""

        WriteCommandAction.runWriteCommandAction(project) {
            // Find a good insertion point - after other entity definitions or at top level
            val insertionPoint = findInsertionPoint(file)
            val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return@runWriteCommandAction

            val insertOffset: Int
            if (insertionPoint != null) {
                insertOffset = insertionPoint.textRange.endOffset
                document.insertString(insertOffset, template)
            } else {
                // Append to end of file
                insertOffset = document.textLength
                document.insertString(insertOffset, template)
            }

            PsiDocumentManager.getInstance(project).commitDocument(document)

            // Position cursor inside the new block (after "position(")
            positionCursorInNewEntity(project, file, insertOffset + template.indexOf("position(") + "position(".length)
        }
    }

    /**
     * Positions the cursor at the specified offset and scrolls the editor.
     */
    private fun positionCursorInNewEntity(project: Project, file: KtFile, offset: Int) {
        val virtualFile = file.virtualFile ?: return
        val fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile)
        val textEditor = fileEditor as? TextEditor ?: return
        val editor = textEditor.editor

        editor.caretModel.moveToOffset(offset)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    private fun findInsertionPoint(file: KtFile): org.jetbrains.kotlin.psi.KtDeclaration? {
        // Find the last entity definition to insert after it
        val declarations = file.declarations

        // First, look for the last entity definition
        val lastEntity = declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtProperty>()
            .lastOrNull { property ->
                val delegate = property.delegateExpression
                delegate is org.jetbrains.kotlin.psi.KtCallExpression &&
                    delegate.calleeExpression?.text == "entity"
            }

        if (lastEntity != null) return lastEntity

        // Otherwise, insert after the last top-level declaration
        return declarations.lastOrNull()
    }
}
