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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
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
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.messages.MessageBusConnection
import io.github.gbkt.intellij.codegen.GbktCodegenService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.KeyStroke
import javax.swing.SwingConstants

/**
 * Panel for previewing generated C code in the gbkt tool window.
 *
 * Features:
 * - Read-only C code editor with syntax highlighting
 * - Combined multi-file view (main.c, bank1.c, etc.) with file separator headers
 * - Refresh button to regenerate code
 * - Auto-refresh checkbox for automatic regeneration on save
 * - Status label showing generation status and timing
 * - Double-click on C line to navigate to Kotlin source (C->DSL direction)
 * - Caret movement in .kt files highlights matching C line (DSL->C direction)
 * - Auto-refresh when .gbkt.map files change on disk
 *
 * Implements [Disposable] for proper resource cleanup when the tool window is closed.
 */
class CCodePreviewPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val codegenService = GbktCodegenService.getInstance(project)
    private val editorDocument = EditorFactory.getInstance().createDocument("")
    private val editor: EditorEx
    private val statusLabel = JBLabel("Ready")
    private val autoRefreshCheckbox = JCheckBox("Auto-refresh on save", false)
    private val progressBar =
        JProgressBar().apply {
            isIndeterminate = true
            isVisible = false
            preferredSize = Dimension(100, 16)
        }
    private var messageBusConnection: MessageBusConnection? = null
    private var isDisposed = false

    /**
     * Tracks the starting line offset (0-based, in the combined document) for each C file. Key: C
     * filename (e.g. "main.c"), Value: 0-based line offset in combined document. Updated every time
     * loadCombinedContent() builds a new combined view.
     */
    private val fileLineOffsets = mutableMapOf<String, Int>()

    /**
     * Ordered list of C filenames for combined view — needed to resolve which file a
     * combined-document line belongs to (binary search boundary).
     */
    private val fileOrder = mutableListOf<String>()

    init {
        // Register this panel for disposal when the project closes
        Disposer.register(project, this)

        // Create editor for C code display
        editor =
            EditorFactory.getInstance()
                .createEditor(
                    editorDocument,
                    project,
                    FileTypeManager.getInstance().getFileTypeByExtension("c"),
                    true, // read-only
                ) as EditorEx

        configureEditor()
        setupMouseListener()

        // Build UI
        add(createToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(editor.component), BorderLayout.CENTER)
        add(createStatusBar(), BorderLayout.SOUTH)

        // Load initial content
        loadCombinedContent()

        // Setup auto-refresh listeners
        setupAutoRefreshListener()
        setupSourceMapFileWatcher()
        setupDslCaretListener()
        setupCCaretListener()
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
        editor.addEditorMouseListener(
            object : EditorMouseListener {
                override fun mouseClicked(event: EditorMouseEvent) {
                    if (event.mouseEvent.clickCount == 2) {
                        navigateToSource(event)
                    }
                }
            }
        )
    }

    /**
     * C->DSL direction: double-clicking a line in the combined view navigates to the corresponding
     * Kotlin DSL source location.
     */
    private fun navigateToSource(event: EditorMouseEvent) {
        val logicalPosition = editor.xyToLogicalPosition(event.mouseEvent.point)
        val combinedLine = logicalPosition.line + 1 // 1-based

        // Determine which C file and line this combined-document line corresponds to
        val (cFile, cLine) = resolveFileAndLine(combinedLine) ?: return

        // Look up source map for this file
        val multiMap = codegenService.getLastMultiFileSourceMap() ?: return
        val fileMappings = multiMap.filesMappings[cFile] ?: return
        val sourceLocation =
            fileMappings[cLine]
                ?: fileMappings.entries
                    .filter { (line, _) -> line < cLine }
                    .maxByOrNull { (line, _) -> line }
                    ?.value
                ?: return

        val projectPath = project.basePath ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            if (isDisposed) return@executeOnPooledThread

            val virtualFile = resolveSourceFile(projectPath, sourceLocation.file)
            if (virtualFile == null) return@executeOnPooledThread

            openSourceLocation(virtualFile, sourceLocation)
        }
    }

    /**
     * Resolve a combined-document 1-based line number to (cFile, cLine) within that file. Returns
     * null if the line is in a separator header region.
     */
    private fun resolveFileAndLine(combinedLine: Int): Pair<String, Int>? {
        // Find the file whose offset is the largest value <= combinedLine
        var bestFile: String? = null
        var bestOffset = -1

        for (fileName in fileOrder) {
            val offset = fileLineOffsets[fileName] ?: continue
            if (offset < combinedLine && offset > bestOffset) {
                bestOffset = offset
                bestFile = fileName
            }
        }

        val file = bestFile ?: return null
        val fileStartLine = bestOffset // 0-based offset in combined doc
        val cLine = combinedLine - fileStartLine // 1-based within the C file
        return file to cLine
    }

    /**
     * Resolve and validate a source file path, ensuring it's within the project directory. Returns
     * null if the file is invalid or outside the project (path traversal protection).
     */
    private fun resolveSourceFile(
        projectPath: String,
        relativePath: String,
    ): com.intellij.openapi.vfs.VirtualFile? {
        val projectDir = File(projectPath).canonicalFile
        val sourceFile =
            File(relativePath).let {
                if (it.isAbsolute) it.canonicalFile
                else File(projectPath, relativePath).canonicalFile
            }

        val isWithinProject =
            sourceFile.canonicalPath.startsWith(projectDir.canonicalPath + File.separator) ||
                sourceFile.canonicalPath == projectDir.canonicalPath

        if (!isWithinProject || !sourceFile.exists()) return null

        return LocalFileSystem.getInstance().findFileByIoFile(sourceFile)
    }

    /** Open the editor at the specified source location on the EDT. */
    private fun openSourceLocation(
        virtualFile: com.intellij.openapi.vfs.VirtualFile,
        sourceLocation: GbktCodegenService.SourceMap.SourceLocation,
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (isDisposed) return@invokeLater

            val descriptor =
                OpenFileDescriptor(
                    project,
                    virtualFile,
                    sourceLocation.line - 1, // 0-based for editor
                    sourceLocation.column,
                )
            FileEditorManager.getInstance(project).openEditor(descriptor, true)
        }
    }

    private fun createToolbar(): JPanel {
        val toolbarPanel = JPanel(BorderLayout())

        val actionGroup =
            DefaultActionGroup().apply {
                add(RefreshAction())
                add(CancelAction())
                addSeparator()
            }

        val toolbar: ActionToolbar =
            ActionManager.getInstance().createActionToolbar("CCodePreview", actionGroup, true)
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

    /**
     * Load all generated C files and merge them into a single scrollable combined view. File
     * separator headers are added between files. Line offsets are tracked per file so source map
     * lookups remain accurate for C->DSL navigation.
     */
    internal fun loadCombinedContent() {
        val generatedFiles = codegenService.getGeneratedFiles()
        if (generatedFiles.isNullOrEmpty()) {
            // Fall back to single main.c or placeholder
            val cachedCode = codegenService.getLastCCode()
            if (cachedCode != null) {
                fileLineOffsets.clear()
                fileOrder.clear()
                fileLineOffsets["main.c"] = 1 // 1-based start
                fileOrder.add("main.c")
                updateEditorContent(cachedCode)
                updateStatus("Loaded from cache (single file)", false)
            } else {
                updateEditorContent(
                    "// No generated C code available.\n// Click 'Refresh' to generate."
                )
                updateStatus("No cached content", false)
            }
            return
        }

        // Build combined content with file separator headers
        val combined = StringBuilder()
        fileLineOffsets.clear()
        fileOrder.clear()
        var currentLine = 1 // 1-based line counter in combined document

        for ((fileName, content) in generatedFiles) {
            val separator = buildString {
                appendLine()
                appendLine("// ${"═".repeat(55)}")
                appendLine("// FILE: $fileName")
                appendLine("// ${"═".repeat(55)}")
                appendLine()
            }

            // Separator takes up lines — count them
            val separatorLines = separator.count { it == '\n' }

            // Every file gets a separator header, including the first one at the top
            combined.append(separator)
            currentLine += separatorLines

            // Record starting line offset for this file (1-based)
            fileLineOffsets[fileName] = currentLine
            fileOrder.add(fileName)

            combined.append(content)
            // Count lines in content
            val contentLines = content.count { it == '\n' } + if (content.endsWith('\n')) 0 else 1
            currentLine += contentLines
        }

        updateEditorContent(combined.toString())
        updateStatus("Loaded ${generatedFiles.size} file(s)", false)
    }

    private fun refreshCode() {
        if (isDisposed) return

        if (codegenService.isGenerating()) {
            updateStatus("Generation already in progress...", true)
            return
        }

        updateStatus("Generating C code...", true)
        progressBar.isVisible = true

        codegenService
            .generateAsync(forceRegenerate = true)
            .thenAccept { result ->
                ApplicationManager.getApplication().invokeLater {
                    if (isDisposed) return@invokeLater

                    progressBar.isVisible = false
                    if (result.success) {
                        loadCombinedContent()
                        updateStatus("Generated in ${result.generationTimeMs}ms", false)
                    } else {
                        val errorMsg = result.errorMessage ?: "Unknown error"
                        updateEditorContent("// Generation failed:\n// $errorMsg")
                        updateStatus("Generation failed", false)
                    }
                }
            }
            .exceptionally { error ->
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
        ApplicationManager.getApplication().runWriteAction { editorDocument.setText(content) }
    }

    private fun updateStatus(message: String, isWorking: Boolean) {
        statusLabel.text = message
        statusLabel.foreground = if (isWorking) JBColor.BLUE else JBColor.foreground()
    }

    /** Setup auto-refresh when .gbkt.kts DSL files are saved. */
    private fun setupAutoRefreshListener() {
        messageBusConnection = project.messageBus.connect()
        messageBusConnection?.subscribe(
            FileDocumentManagerListener.TOPIC,
            object : FileDocumentManagerListener {
                override fun beforeDocumentSaving(document: com.intellij.openapi.editor.Document) {
                    onDslFileSaved(document)
                }
            },
        )
    }

    /**
     * Called when any document is about to be saved. Guards on auto-refresh enabled and disposed
     * state, then delegates to project-scoped refresh trigger if the file is a .gbkt.kts DSL file.
     */
    private fun onDslFileSaved(document: com.intellij.openapi.editor.Document) {
        if (!autoRefreshCheckbox.isSelected) return
        if (isDisposed) return

        val file = FileDocumentManager.getInstance().getFile(document)
        if (file != null && file.name.endsWith(".gbkt.kts")) {
            triggerRefreshIfInProject(file)
        }
    }

    /**
     * Triggers an async code refresh if [file] belongs to this project. Verifies the file path
     * starts with the project base path to avoid cross-project interference.
     */
    private fun triggerRefreshIfInProject(file: com.intellij.openapi.vfs.VirtualFile) {
        val projectPath = project.basePath
        if (projectPath != null && file.path.startsWith(projectPath)) {
            ApplicationManager.getApplication().invokeLater {
                if (!isDisposed) {
                    refreshCode()
                }
            }
        }
    }

    /**
     * Setup file watcher that monitors *.gbkt.map files in the generated directory. When a map file
     * changes (e.g., after generateC runs from the command line), the combined preview is
     * automatically reloaded.
     */
    private fun setupSourceMapFileWatcher() {
        project.messageBus
            .connect(this)
            .subscribe(
                VirtualFileManager.VFS_CHANGES,
                object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) {
                        if (!autoRefreshCheckbox.isSelected) return
                        if (isDisposed) return

                        // Verify a source map belonging to this project changed
                        val projectPath = project.basePath ?: return
                        val mapChanged = events.any { event ->
                            event is VFileContentChangeEvent &&
                                event.path.endsWith(".gbkt.map") &&
                                event.path.startsWith(projectPath)
                        }
                        if (!mapChanged) return

                        ApplicationManager.getApplication().invokeLater {
                            if (!isDisposed) {
                                loadCombinedContent()
                                updateStatus("Auto-refreshed from source map change", false)
                            }
                        }
                    }
                },
            )
    }

    /**
     * Setup DSL->C caret listener. When the user moves the caret in a .kt file belonging to this
     * project, look up matching C lines via GbktCodegenService and highlight them in the combined
     * view.
     */
    private fun setupDslCaretListener() {
        EditorFactory.getInstance()
            .eventMulticaster
            .addCaretListener(
                object : CaretListener {
                    override fun caretPositionChanged(event: CaretEvent) {
                        if (!autoRefreshCheckbox.isSelected) return
                        if (isDisposed) return

                        val editorForEvent = event.editor
                        val vFile =
                            FileDocumentManager.getInstance().getFile(editorForEvent.document)
                                ?: return

                        // Only react to .kt files in this project
                        val projectPath = project.basePath ?: return
                        if (!vFile.path.startsWith(projectPath)) return
                        if (vFile.extension != "kt") return

                        val kotlinLine = event.newPosition.line + 1 // convert 0-based to 1-based
                        val matches =
                            codegenService.findCLinesForKotlinLocation(vFile.path, kotlinLine)

                        if (matches.isNotEmpty()) {
                            val (cFile, cLine) = matches.first()
                            ApplicationManager.getApplication().invokeLater {
                                if (!isDisposed) scrollToMatchingCLine(cFile, cLine)
                            }
                        }
                    }
                },
                /* disposable = */ this,
            )
    }

    /**
     * C->DSL direction: listen for caret movements in the C preview editor (right panel). When the
     * user places their caret on a line, look up the matching Kotlin source location via
     * [GbktCodegenService.findKotlinLocationForCLine] and navigate the main editor to it.
     *
     * Only fires when auto-refresh is enabled to avoid interference while simply reading code.
     */
    private fun setupCCaretListener() {
        editor.caretModel.addCaretListener(
            object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    if (!autoRefreshCheckbox.isSelected) return
                    if (isDisposed) return

                    val combinedLine = event.newPosition.line + 1 // 0-based to 1-based
                    val (cFile, cLine) = resolveFileAndLine(combinedLine) ?: return

                    val sourceLocation =
                        codegenService.findKotlinLocationForCLine(cFile, cLine) ?: return

                    val projectPath = project.basePath ?: return

                    ApplicationManager.getApplication().executeOnPooledThread {
                        if (isDisposed) return@executeOnPooledThread

                        val virtualFile =
                            resolveSourceFile(projectPath, sourceLocation.file)
                                ?: return@executeOnPooledThread

                        openSourceLocation(virtualFile, sourceLocation)
                    }
                }
            },
            /* disposable = */ this,
        )
    }

    /**
     * DSL->C direction: scroll the combined C view to highlight the line corresponding to the given
     * (cFile, cLine) pair. Translates per-file offsets to combined-document line.
     *
     * @param cFile The C filename (e.g., "main.c", "bank1.c")
     * @param cLine 1-based line number within the C file
     */
    fun scrollToMatchingCLine(cFile: String, cLine: Int) {
        val fileOffset = fileLineOffsets[cFile] ?: return
        // fileOffset is 1-based first content line; cLine is 1-based within file
        val combinedLine = fileOffset + cLine - 1 - 1 // convert to 0-based for editor API
        if (combinedLine < 0) return

        val totalLines = editorDocument.lineCount
        if (combinedLine >= totalLines) return

        val logicalPosition = LogicalPosition(combinedLine, 0)
        editor.scrollingModel.scrollTo(logicalPosition, ScrollType.CENTER)
        editor.caretModel.moveToLogicalPosition(logicalPosition)
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

    private inner class RefreshAction :
        AnAction("Refresh", "Regenerate C code from DSL (Ctrl+Shift+G)", AllIcons.Actions.Refresh) {
        init {
            // Register Ctrl+Shift+G as keyboard shortcut for refresh
            val keyStroke =
                KeyStroke.getKeyStroke(
                    KeyEvent.VK_G,
                    InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK,
                )
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

    private inner class CancelAction :
        AnAction("Cancel", "Cancel current C code generation", AllIcons.Actions.Cancel) {
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
