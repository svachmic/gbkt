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
package io.github.gbkt.intellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import io.github.gbkt.intellij.editors.sprite.SpriteEditorProvider

/**
 * Action to open a PNG file in the gbkt sprite editor.
 *
 * The sprite editor provides:
 * - 8x8 tile grid overlay
 * - 2BPP color validation
 * - Animation frame preview
 * - Export to binary format
 */
class OpenSpriteEditorAction : AnAction() {

    private val logger = Logger.getInstance(OpenSpriteEditorAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val project = e.project ?: return

        logger.info("Opening sprite editor for: ${virtualFile.path}")

        // Open the file in the editor
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFile(virtualFile, true)

        // Navigate to the Sprite Editor tab by type ID
        fileEditorManager.setSelectedEditor(virtualFile, SpriteEditorProvider.EDITOR_TYPE_ID)
        logger.info("Switched to Sprite Editor tab")
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        // Only show for PNG files
        e.presentation.isEnabledAndVisible =
            virtualFile != null && virtualFile.extension?.lowercase() == "png"
    }
}
