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
package io.github.gbkt.intellij.debug

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.github.gbkt.intellij.sdk.EmulatorType
import io.github.gbkt.intellij.sdk.GbktSdkService
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Enhanced emulator integration with save state management.
 *
 * Features:
 * - Launch ROM in configured emulator
 * - Save state management (list, load, delete)
 * - Quick launch options (debug mode, etc.)
 * - Recent ROMs list
 */
class EmulatorIntegration(private val project: Project) : JPanel(BorderLayout()) {

    private val logger = Logger.getInstance(EmulatorIntegration::class.java)
    private val sdkService = GbktSdkService.getInstance(project)

    private val saveStateModel = DefaultListModel<SaveStateInfo>()
    private val saveStateList = JBList(saveStateModel)
    private val recentRomsModel = DefaultListModel<RecentRom>()
    private val recentRomsList = JBList(recentRomsModel)

    private val statusLabel = JBLabel("Ready")
    private val emulatorLabel = JBLabel()

    private val launchButton = JButton("Launch ROM")
    private val launchDebugButton = JButton("Launch with Debug")
    private val refreshButton = JButton("Refresh")

    private val loadStateButton = JButton("Load State")
    private val deleteStateButton = JButton("Delete State")

    private var currentRomPath: File? = null
    private var emulatorProcess: Process? = null

    init {
        setupUI()
        setupListeners()
        updateEmulatorStatus()
        scanForSaveStates()
        loadRecentRoms()
    }

    private fun setupUI() {
        // Header
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val titleLabel = JBLabel("Emulator Integration")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        headerPanel.add(titleLabel, BorderLayout.WEST)
        headerPanel.add(emulatorLabel, BorderLayout.EAST)

        add(headerPanel, BorderLayout.NORTH)

        // Main content
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.border = BorderFactory.createEmptyBorder(0, 10, 10, 10)

        // Launch buttons
        val launchPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        launchPanel.border = BorderFactory.createTitledBorder("Launch Options")
        launchPanel.add(launchButton)
        launchPanel.add(launchDebugButton)
        launchPanel.add(Box.createHorizontalStrut(20))
        launchPanel.add(refreshButton)
        contentPanel.add(launchPanel)

        // Recent ROMs
        val recentPanel = JPanel(BorderLayout())
        recentPanel.border = BorderFactory.createTitledBorder("Recent ROMs")
        recentRomsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        recentPanel.add(JBScrollPane(recentRomsList), BorderLayout.CENTER)
        recentPanel.preferredSize = java.awt.Dimension(0, 120)
        contentPanel.add(recentPanel)

        contentPanel.add(Box.createVerticalStrut(10))

        // Save states
        val statePanel = JPanel(BorderLayout())
        statePanel.border = BorderFactory.createTitledBorder("Save States")

        saveStateList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        statePanel.add(JBScrollPane(saveStateList), BorderLayout.CENTER)

        val stateButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        stateButtonPanel.add(loadStateButton)
        stateButtonPanel.add(deleteStateButton)
        statePanel.add(stateButtonPanel, BorderLayout.SOUTH)
        statePanel.preferredSize = java.awt.Dimension(0, 150)

        contentPanel.add(statePanel)

        add(JBScrollPane(contentPanel), BorderLayout.CENTER)

        // Status bar
        val statusBar = JPanel(BorderLayout())
        statusBar.border =
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(
                    1,
                    0,
                    0,
                    0,
                    JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(),
                ),
                BorderFactory.createEmptyBorder(5, 10, 5, 10),
            )
        statusBar.add(statusLabel, BorderLayout.WEST)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun setupListeners() {
        launchButton.addActionListener { launchRom(false) }
        launchDebugButton.addActionListener { launchRom(true) }
        refreshButton.addActionListener {
            scanForSaveStates()
            loadRecentRoms()
            findLatestRom()
        }

        loadStateButton.addActionListener { loadSelectedState() }
        deleteStateButton.addActionListener { deleteSelectedState() }

        recentRomsList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val selected = recentRomsList.selectedValue
                if (selected != null) {
                    currentRomPath = selected.file
                    statusLabel.text = "Selected: ${selected.file.name}"
                }
            }
        }

        saveStateList.addListSelectionListener {
            val hasSelection = saveStateList.selectedValue != null
            loadStateButton.isEnabled = hasSelection
            deleteStateButton.isEnabled = hasSelection
        }
    }

    private fun updateEmulatorStatus() {
        val emulatorPath = sdkService.emulatorPath
        val emulatorType = sdkService.emulatorType

        if (emulatorPath != null) {
            emulatorLabel.text = "${emulatorType.name}: ${emulatorPath.fileName}"
            emulatorLabel.foreground = JBColor.foreground()
            launchButton.isEnabled = true
            launchDebugButton.isEnabled = emulatorType == EmulatorType.BGB
        } else {
            emulatorLabel.text = "No emulator configured"
            emulatorLabel.foreground = JBColor.RED
            launchButton.isEnabled = false
            launchDebugButton.isEnabled = false
        }
    }

    private fun findLatestRom(): File? {
        val basePath = project.basePath ?: return null
        val buildDir = File(basePath, "build")

        if (!buildDir.exists()) return null

        val romFiles =
            buildDir.walkTopDown().filter { it.extension in listOf("gb", "gbc") }.toList()

        val latest = romFiles.maxByOrNull { it.lastModified() }
        if (latest != null) {
            currentRomPath = latest
            statusLabel.text = "Found ROM: ${latest.name}"
        }
        return latest
    }

    private fun launchRom(debugMode: Boolean) {
        val romFile = currentRomPath ?: findLatestRom()
        if (romFile == null) {
            statusLabel.text = "No ROM file found"
            return
        }

        val emulatorPath = sdkService.emulatorPath
        if (emulatorPath == null) {
            statusLabel.text = "No emulator configured"
            return
        }

        try {
            val command =
                buildEmulatorCommand(emulatorPath.toString(), romFile.absolutePath, debugMode)
            logger.info("Launching emulator: ${command.joinToString(" ")}")

            emulatorProcess = ProcessBuilder(command).start()
            addToRecentRoms(romFile)
            statusLabel.text = "Launched: ${romFile.name}"
        } catch (ex: IOException) {
            logger.error("Failed to launch emulator", ex)
            statusLabel.text = "Failed to launch emulator"
        }
    }

    private fun buildEmulatorCommand(
        emulatorPath: String,
        romPath: String,
        debugMode: Boolean,
    ): List<String> {
        val command = mutableListOf(emulatorPath)

        when (sdkService.emulatorType) {
            EmulatorType.BGB -> {
                if (debugMode) {
                    command.add("-br")
                    command.add("-setting")
                    command.add("DebugSrcBrk=1")
                }
                command.add(romPath)
            }
            EmulatorType.MGBA -> {
                if (debugMode) {
                    command.add("-d") // Debug mode
                }
                command.add(romPath)
            }
            else -> {
                command.add(romPath)
            }
        }

        return command
    }

    private fun scanForSaveStates() {
        saveStateModel.clear()

        val basePath = project.basePath ?: return
        val saveDir = File(basePath, "build/saves")

        if (!saveDir.exists()) {
            saveDir.mkdirs()
            return
        }

        // Look for save state files (common extensions)
        val stateExtensions = listOf("sav", "ss0", "ss1", "ss2", "ss3", "state", "sgm")
        val stateFiles =
            saveDir.listFiles { file -> file.extension.lowercase() in stateExtensions } ?: return

        stateFiles
            .sortedByDescending { it.lastModified() }
            .forEach { file ->
                saveStateModel.addElement(
                    SaveStateInfo(
                        name = file.nameWithoutExtension,
                        file = file,
                        date = Date(file.lastModified()),
                        sizeBytes = file.length(),
                    )
                )
            }
    }

    private fun loadSelectedState() {
        val selected = saveStateList.selectedValue ?: return
        val romFile = currentRomPath ?: findLatestRom() ?: return
        val emulatorPath = sdkService.emulatorPath ?: return

        // BGB supports loading save states via command line
        if (sdkService.emulatorType == EmulatorType.BGB) {
            try {
                val command =
                    listOf(
                        emulatorPath.toString(),
                        "-stateload",
                        selected.file.absolutePath,
                        romFile.absolutePath,
                    )
                ProcessBuilder(command).start()
                statusLabel.text = "Loaded state: ${selected.name}"
            } catch (ex: IOException) {
                logger.error("Failed to load save state", ex)
                statusLabel.text = "Failed to load state"
            }
        } else {
            statusLabel.text = "Save state loading not supported for ${sdkService.emulatorType}"
        }
    }

    private fun deleteSelectedState() {
        val selected = saveStateList.selectedValue ?: return

        if (selected.file.delete()) {
            saveStateModel.removeElement(selected)
            statusLabel.text = "Deleted: ${selected.name}"
        } else {
            statusLabel.text = "Failed to delete state"
        }
    }

    private fun loadRecentRoms() {
        recentRomsModel.clear()

        val basePath = project.basePath ?: return

        // Scan build directory for ROMs
        val buildDir = File(basePath, "build")
        if (!buildDir.exists()) return

        val romFiles =
            buildDir.walkTopDown().filter { it.extension in listOf("gb", "gbc") }.toList()

        romFiles
            .sortedByDescending { it.lastModified() }
            .take(MAX_RECENT_ROMS)
            .forEach { file ->
                recentRomsModel.addElement(
                    RecentRom(name = file.name, file = file, date = Date(file.lastModified()))
                )
            }

        // Select first if available
        if (recentRomsModel.size > 0) {
            recentRomsList.selectedIndex = 0
            currentRomPath = recentRomsModel.getElementAt(0).file
        }
    }

    private fun addToRecentRoms(romFile: File) {
        // Check if already in list
        for (i in 0 until recentRomsModel.size) {
            if (recentRomsModel.getElementAt(i).file.absolutePath == romFile.absolutePath) {
                return
            }
        }

        // Add to front
        recentRomsModel.insertElementAt(RecentRom(romFile.name, romFile, Date()), 0)

        // Trim to max
        while (recentRomsModel.size > MAX_RECENT_ROMS) {
            recentRomsModel.removeElementAt(recentRomsModel.size - 1)
        }
    }

    /** Information about a save state. */
    data class SaveStateInfo(
        val name: String,
        val file: File,
        val date: Date,
        val sizeBytes: Long,
    ) {
        override fun toString(): String {
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(date)
            val sizeStr =
                when {
                    sizeBytes < 1024 -> "$sizeBytes B"
                    else -> "${sizeBytes / 1024} KB"
                }
            return "$name ($dateStr, $sizeStr)"
        }
    }

    /** Information about a recent ROM. */
    data class RecentRom(val name: String, val file: File, val date: Date) {
        override fun toString(): String {
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(date)
            return "$name ($dateStr)"
        }
    }

    companion object {
        private const val MAX_RECENT_ROMS = 10
    }
}
