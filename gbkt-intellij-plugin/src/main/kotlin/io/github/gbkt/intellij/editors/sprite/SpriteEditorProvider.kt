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
package io.github.gbkt.intellij.editors.sprite

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import io.github.gbkt.intellij.GbktBundle
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * File editor provider for sprite sheet files (PNG).
 *
 * Adds "Sprite Editor" tab to PNG files, allowing users to view sprite sheets with 8x8 grid overlay
 * and GB preview.
 */
class SpriteEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        // Accept PNG files in gbkt projects
        if (file.extension?.lowercase() != "png") return false

        // Check if this is in a gbkt project (has .gbkt.kts files or build.gradle.kts with gbkt)
        return isInGbktProject(project, file)
    }

    private fun isInGbktProject(project: Project, file: VirtualFile): Boolean {
        // Check for common gbkt indicators
        val projectDir = project.basePath ?: return false

        // First check path-based heuristics (fast, no I/O)
        val path = file.path.lowercase()
        if (
            path.contains("/assets/") ||
                path.contains("/sprites/") ||
                path.contains("/gfx/") ||
                path.contains("/graphics/")
        ) {
            return true
        }

        // Look for gbkt script files (file existence check only, no content read)
        val basePath = file.parent
        var current = basePath
        while (current != null && current.path.startsWith(projectDir)) {
            // Check for .gbkt.kts files
            if (current.children.any { it.name.endsWith(".gbkt.kts") }) {
                return true
            }
            // Check for build.gradle.kts file existence (assume gbkt project if found)
            // Avoiding content read on EDT - use project marker files instead
            if (
                current.findChild("build.gradle.kts") != null &&
                    current.findChild("gbkt-core") != null
            ) {
                return true
            }
            current = current.parent
        }

        return false
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return SpriteFileEditor(file)
    }

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID = "gbkt-sprite-editor"
    }
}

/** File editor wrapper for sprite editor component. */
class SpriteFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

    private val component = SpriteEditorComponent(file)

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent = component

    override fun getName(): String = GbktBundle.message("editor.sprite.name")

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getFile(): VirtualFile = file

    override fun dispose() {}
}
