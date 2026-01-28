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
package io.github.gbkt.intellij.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.messages.MessageBusConnection
import io.github.gbkt.intellij.codegen.GbktCodegenService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingConstants

/**
 * Panel for previewing generated C code in the gbkt tool window.
 *
 * Features:
 * - Read-only C code editor with syntax highlighting
 * - Refresh button to regenerate code
 * - Auto-refresh checkbox for automatic regeneration on save
 * - Status label showing generation status and timing
 * - Double-click on C line to navigate to Kotlin source
 *
 * Implements [Disposable] for proper resource cleanup when the tool window is closed.
 */
class CCodePreviewPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val codegenService = GbktCodegenService.getInstance(project)
    private val editorDocument = EditorFactory.getInstance().createDocument("")
    private val editor: EditorEx
    private val statusLabel = JBLabel("Ready")
    private val autoRefreshCheckbox = JCheckBox("Auto-refresh on save", false)
    private val progressBar = JProgressBar().apply {
        isIndeterminate = true
        isVisible = false
        preferredSize = Dimension(100, 16)
    }
    private var messageBusConnection: MessageBusConnection? = null
    private var isDisposed = false

    init {
        // Register this panel for disposal when the project closes
        Disposer.register(project, this)

        // Create editor for C code display
        editor = EditorFactory.getInstance().createEditor(
            editorDocument,
            project,
            FileTypeManager.getInstance().getFileTypeByExtension("c"),
            true // read-only
        ) as EditorEx

        configureEditor()
        setupMouseListener()

        // Build UI
        add(createToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(editor.component), BorderLayout.CENTER)
        add(createStatusBar(), BorderLayout.SOUTH)

        // Load initial content
        loadCachedContent()

        // Setup auto-refresh listener
        setupAutoRefreshListener()
    }

    private fun configureEditor() {
        val settings: EditorSettings = editor.settings
        settings.isLineNumbersShown = true
        settings.isWhitespacesShown = false
        settings.isLineMarkerAreaShown = true
        settings.isFoldingOutlineShown = true
        settings.isIndentGuidesShown = true
        settings.isUseSoftWraps = false

        editor.colorsScheme = EditorColorsManager.getInstance().globalScheme
    }

    private fun setupMouseListener() {
        editor.addEditorMouseListener(object : EditorMouseListener {
            override fun mouseClicked(event: EditorMouseEvent) {
                if (event.mouseEvent.clickCount == 2) {
                    navigateToSource(event)
                }
            }
        })
    }

    private fun navigateToSource(event: EditorMouseEvent) {
        val logicalPosition = editor.xyToLogicalPosition(event.mouseEvent.point)
        val cLine = logicalPosition.line + 1 // 1-based

        val sourceMap = codegenService.getLastSourceMap() ?: return
        val sourceLocation = sourceMap.mappings[cLine] ?: return
        val projectPath = project.basePath ?: return

        // Move file I/O operations to background thread to avoid freezing the UI
        ApplicationManager.getApplication().executeOnPooledThread {
            if (isDisposed) return@executeOnPooledThread

            // Find the source file with path traversal protection
            val projectDir = File(projectPath).canonicalFile
            val sourceFile = File(projectPath, sourceLocation.file).canonicalFile

            // Security check: Ensure the resolved path is within the project directory
            // This prevents path traversal attacks via malicious source maps (e.g., "../../etc/passwd")
            if (!sourceFile.canonicalPath.startsWith(projectDir.canonicalPath + File.separator) &&
                sourceFile.canonicalPath != projectDir.canonicalPath
            ) {
                return@executeOnPooledThread // Path traversal attempt, silently ignore
            }

            if (!sourceFile.exists()) return@executeOnPooledThread

            val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(sourceFile)
                ?: return@executeOnPooledThread

            // Navigate to the source location on EDT
            ApplicationManager.getApplication().invokeLater {
                if (isDisposed) return@invokeLater

                val descriptor = OpenFileDescriptor(
                    project,
                    virtualFile,
                    sourceLocation.line - 1, // 0-based for editor
                    sourceLocation.column
                )
                FileEditorManager.getInstance(project).openEditor(descriptor, true)
            }
        }
    }

    private fun createToolbar(): JPanel {
        val toolbarPanel = JPanel(BorderLayout())

        val actionGroup = DefaultActionGroup().apply {
            add(RefreshAction())
            add(CancelAction())
            addSeparator()
        }

        val toolbar: ActionToolbar = ActionManager.getInstance()
            .createActionToolbar("CCodePreview", actionGroup, true)
        toolbar.targetComponent = this

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        leftPanel.add(toolbar.component)
        leftPanel.add(autoRefreshCheckbox)

        toolbarPanel.add(leftPanel, BorderLayout.WEST)

        return toolbarPanel
    }

    private fun createStatusBar(): JPanel {
        val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2))
        statusLabel.horizontalAlignment = SwingConstants.LEFT
        statusPanel.add(statusLabel)
        statusPanel.add(Box.createHorizontalStrut(10))
        statusPanel.add(progressBar)
        return statusPanel
    }

    private fun loadCachedContent() {
        val cachedCode = codegenService.getLastCCode()
        if (cachedCode != null) {
            updateEditorContent(cachedCode)
            updateStatus("Loaded from cache", false)
        } else {
            updateEditorContent("// No generated C code available.\n// Click 'Refresh' to generate.")
            updateStatus("No cached content", false)
        }
    }

    private fun refreshCode() {
        if (isDisposed) return

        if (codegenService.isGenerating()) {
            updateStatus("Generation already in progress...", true)
            return
        }

        updateStatus("Generating C code...", true)
        progressBar.isVisible = true

        codegenService.generateAsync(forceRegenerate = true).thenAccept { result ->
            ApplicationManager.getApplication().invokeLater {
                if (isDisposed) return@invokeLater

                progressBar.isVisible = false
                if (result.success && result.cCode != null) {
                    updateEditorContent(result.cCode)
                    updateStatus("Generated in ${result.generationTimeMs}ms", false)
                } else {
                    val errorMsg = result.errorMessage ?: "Unknown error"
                    updateEditorContent("// Generation failed:\n// $errorMsg")
                    updateStatus("Generation failed", false)
                }
            }
        }.exceptionally { error ->
            ApplicationManager.getApplication().invokeLater {
                if (isDisposed) return@invokeLater

                progressBar.isVisible = false
                updateEditorContent("// Error: ${error.message}")
                updateStatus("Error: ${error.message}", false)
            }
            null
        }
    }

    private fun updateEditorContent(content: String) {
        ApplicationManager.getApplication().runWriteAction {
            editorDocument.setText(content)
        }
    }

    private fun updateStatus(message: String, isWorking: Boolean) {
        statusLabel.text = message
        statusLabel.foreground = if (isWorking) JBColor.BLUE else JBColor.foreground()
    }

    private fun setupAutoRefreshListener() {
        messageBusConnection = project.messageBus.connect()
        messageBusConnection?.subscribe(
            FileDocumentManagerListener.TOPIC,
            object : FileDocumentManagerListener {
                override fun beforeDocumentSaving(document: com.intellij.openapi.editor.Document) {
                    if (!autoRefreshCheckbox.isSelected) return
                    if (isDisposed) return

                    // Check if the saved file is a gbkt file
                    val file = FileDocumentManager.getInstance().getFile(document)
                    if (file != null && file.name.endsWith(".gbkt.kts")) {
                        // Verify the file belongs to this project to avoid triggering
                        // generation for files in other open projects
                        val projectPath = project.basePath
                        if (projectPath != null && file.path.startsWith(projectPath)) {
                            ApplicationManager.getApplication().invokeLater {
                                if (!isDisposed) {
                                    refreshCode()
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true

        messageBusConnection?.disconnect()
        messageBusConnection = null

        if (!editor.isDisposed) {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    private inner class RefreshAction : AnAction(
        "Refresh",
        "Regenerate C code from DSL (Ctrl+Shift+G)",
        AllIcons.Actions.Refresh
    ) {
        init {
            // Register Ctrl+Shift+G as keyboard shortcut for refresh
            val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
            val shortcut = KeyboardShortcut(keyStroke, null)
            registerCustomShortcutSet({ arrayOf(shortcut) }, this@CCodePreviewPanel)
        }

        override fun actionPerformed(e: AnActionEvent) {
            refreshCode()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = !codegenService.isGenerating()
        }
    }

    private inner class CancelAction : AnAction(
        "Cancel",
        "Cancel current C code generation",
        AllIcons.Actions.Cancel
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            if (codegenService.cancelGeneration()) {
                progressBar.isVisible = false
                updateStatus("Generation cancelled", false)
                updateEditorContent("// Generation was cancelled by user")
            }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = codegenService.isGenerating()
        }
    }
}
