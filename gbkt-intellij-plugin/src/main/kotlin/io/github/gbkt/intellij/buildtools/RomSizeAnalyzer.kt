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
package io.github.gbkt.intellij.buildtools

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.io.File
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * ROM size analyzer with bank visualization.
 *
 * Features:
 * - Visual bank usage display (32 banks)
 * - Data breakdown by category
 * - Overflow warnings
 * - Size trends over time
 */
class RomSizeAnalyzer(private val project: Project) : JPanel(BorderLayout()) {

    private val bankPanel = BankVisualizationPanel()
    private val breakdownPanel = DataBreakdownPanel()

    private val statusLabel = JBLabel("No ROM loaded")
    private val totalSizeLabel = JBLabel()
    private val refreshButton = JButton("Refresh")

    private var romData: RomAnalysis? = null

    init {
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // Header
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val titleLabel = JBLabel("ROM Size Analyzer")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        headerPanel.add(titleLabel, BorderLayout.WEST)

        val controlsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0))
        controlsPanel.add(totalSizeLabel)
        controlsPanel.add(refreshButton)
        headerPanel.add(controlsPanel, BorderLayout.EAST)

        add(headerPanel, BorderLayout.NORTH)

        // Main content
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)

        // Bank visualization
        val bankSection = JPanel(BorderLayout())
        bankSection.border = BorderFactory.createTitledBorder("Bank Usage (32 banks × 16KB)")
        bankSection.add(JBScrollPane(bankPanel), BorderLayout.CENTER)
        bankSection.preferredSize = Dimension(0, 200)
        contentPanel.add(bankSection)

        contentPanel.add(Box.createVerticalStrut(10))

        // Data breakdown
        val breakdownSection = JPanel(BorderLayout())
        breakdownSection.border = BorderFactory.createTitledBorder("Data Breakdown")
        breakdownSection.add(breakdownPanel, BorderLayout.CENTER)
        contentPanel.add(breakdownSection)

        add(JBScrollPane(contentPanel), BorderLayout.CENTER)

        // Status bar
        val statusBar = JPanel(BorderLayout())
        statusBar.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
        statusBar.add(statusLabel, BorderLayout.WEST)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun setupListeners() {
        refreshButton.addActionListener { refresh() }
    }

    /** Refreshes the ROM analysis. */
    fun refresh() {
        val basePath = project.basePath ?: return

        // Look for ROM file
        val romFile =
            listOf("build/rom.gb", "build/game.gb", "build/output.gb")
                .map { File(basePath, it) }
                .firstOrNull { it.exists() }

        if (romFile != null) {
            analyzeRom(romFile)
        } else {
            statusLabel.text = "No ROM file found in build/"
        }
    }

    /** Analyzes a ROM file. */
    fun analyzeRom(romFile: File) {
        if (!romFile.exists()) {
            statusLabel.text = "ROM file not found: ${romFile.name}"
            return
        }

        val bytes = romFile.readBytes()
        val analysis = parseRom(bytes, romFile.name)
        this.romData = analysis

        updateDisplay(analysis)
    }

    private fun parseRom(bytes: ByteArray, name: String): RomAnalysis {
        val totalSize = bytes.size
        val bankCount = (totalSize + BANK_SIZE - 1) / BANK_SIZE

        // Calculate bank usage
        val bankUsage = mutableListOf<BankInfo>()
        for (i in 0 until MAX_BANKS) {
            val bankStart = i * BANK_SIZE
            val bankEnd = minOf(bankStart + BANK_SIZE, totalSize)

            if (bankStart < totalSize) {
                val bankBytes = bytes.sliceArray(bankStart until bankEnd)
                val usedBytes = countUsedBytes(bankBytes)
                val category = categorizeBank(i, bankBytes)

                bankUsage.add(
                    BankInfo(
                        index = i,
                        usedBytes = usedBytes,
                        totalBytes = bankEnd - bankStart,
                        category = category,
                    )
                )
            } else {
                bankUsage.add(BankInfo(i, 0, 0, DataCategory.EMPTY))
            }
        }

        // Calculate category breakdown
        val breakdown = mutableMapOf<DataCategory, Int>()
        for (bank in bankUsage) {
            breakdown[bank.category] = (breakdown[bank.category] ?: 0) + bank.usedBytes
        }

        return RomAnalysis(
            name = name,
            totalSize = totalSize,
            bankCount = bankCount,
            banks = bankUsage,
            breakdown = breakdown,
        )
    }

    private fun countUsedBytes(bytes: ByteArray): Int {
        // Count non-padding bytes (not 0x00 or 0xFF)
        return bytes.count { it != 0x00.toByte() && it != 0xFF.toByte() }
    }

    private fun categorizeBank(index: Int, bytes: ByteArray): DataCategory {
        return when {
            index == 0 -> DataCategory.CODE // Bank 0 is always code/vectors
            bytes.isEmpty() -> DataCategory.EMPTY
            looksLikeCode(bytes) -> DataCategory.CODE
            looksLikeGraphics(bytes) -> DataCategory.GRAPHICS
            looksLikeStrings(bytes) -> DataCategory.STRINGS
            looksLikeMapData(bytes) -> DataCategory.MAP_DATA
            else -> DataCategory.DATA
        }
    }

    private fun looksLikeCode(bytes: ByteArray): Boolean {
        // Check for common GB opcodes
        val opcodes = setOf(0x00, 0x01, 0x11, 0x21, 0x31, 0xC3, 0xCD, 0xC9, 0x3E, 0x06, 0x0E)
        val sample = bytes.take(100)
        val opcodeCount = sample.count { it.toInt() and 0xFF in opcodes }
        return opcodeCount > sample.size / 5
    }

    private fun looksLikeGraphics(bytes: ByteArray): Boolean {
        // 2BPP graphics have patterns of bit-paired bytes
        // Simple heuristic: check for repeating patterns
        if (bytes.size < 16) return false

        var patternScore = 0
        for (i in 0 until minOf(bytes.size - 2, 100) step 2) {
            val b1 = bytes[i].toInt() and 0xFF
            val b2 = bytes[i + 1].toInt() and 0xFF
            // In 2BPP, adjacent bytes often have related bit patterns
            if ((b1 xor b2) < 128) patternScore++
        }
        return patternScore > 30
    }

    private fun looksLikeStrings(bytes: ByteArray): Boolean {
        // Check for ASCII-like content
        val printableCount = bytes.count { b ->
            val v = b.toInt() and 0xFF
            v in 0x20..0x7E || v == 0x00 || v == 0x0A
        }
        return printableCount > bytes.size * 3 / 4
    }

    private fun looksLikeMapData(bytes: ByteArray): Boolean {
        // Map data often has repeating small values (tile indices 0-255)
        if (bytes.size < 100) return false

        val histogram = IntArray(256)
        bytes.forEach { histogram[it.toInt() and 0xFF]++ }

        // Map data typically uses fewer unique values
        val uniqueValues = histogram.count { it > 0 }
        return uniqueValues < 64 && histogram.take(32).sum() > bytes.size / 2
    }

    private fun updateDisplay(analysis: RomAnalysis) {
        bankPanel.setAnalysis(analysis)
        breakdownPanel.setAnalysis(analysis)

        totalSizeLabel.text = formatSize(analysis.totalSize)
        statusLabel.text = "${analysis.name} - ${analysis.bankCount} banks used"
    }

    private fun formatSize(bytes: Int): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /** Data categories for ROM content. */
    enum class DataCategory(val displayName: String, val color: Color) {
        CODE("Code", Color(100, 149, 237)),
        GRAPHICS("Graphics", Color(144, 238, 144)),
        STRINGS("Strings", Color(255, 218, 185)),
        MAP_DATA("Map Data", Color(221, 160, 221)),
        DATA("Data", Color(176, 196, 222)),
        EMPTY("Empty", Color(80, 80, 80)),
    }

    /** Information about a single ROM bank. */
    data class BankInfo(
        val index: Int,
        val usedBytes: Int,
        val totalBytes: Int,
        val category: DataCategory,
    ) {
        val usagePercent: Float
            get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f

        val isFull: Boolean
            get() = usagePercent > 0.95f

        val isOverflowing: Boolean
            get() = usedBytes > BANK_SIZE
    }

    /** Complete ROM analysis. */
    data class RomAnalysis(
        val name: String,
        val totalSize: Int,
        val bankCount: Int,
        val banks: List<BankInfo>,
        val breakdown: Map<DataCategory, Int>,
    )

    /** Panel showing bank usage visualization. */
    private inner class BankVisualizationPanel : JPanel() {
        private var analysis: RomAnalysis? = null

        init {
            preferredSize = Dimension(800, 150)
            background = JBColor(Color(45, 45, 45), Color(45, 45, 45))
        }

        fun setAnalysis(analysis: RomAnalysis) {
            this.analysis = analysis
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val data = analysis ?: return

            val bankWidth = 22
            val bankHeight = 100
            val spacing = 2
            val startX = 10
            val startY = 30

            // Draw title
            g2.color = JBColor.foreground()
            g2.font = g2.font.deriveFont(11f)
            g2.drawString("Banks 0-31 (16KB each)", startX, 20)

            // Draw banks
            for (i in 0 until MAX_BANKS) {
                val x = startX + i * (bankWidth + spacing)
                val bank = data.banks.getOrNull(i)

                // Background
                g2.color = JBColor(Color(60, 60, 60), Color(60, 60, 60))
                g2.fillRect(x, startY, bankWidth, bankHeight)

                if (bank != null && bank.totalBytes > 0) {
                    // Fill based on usage
                    val fillHeight = (bankHeight * bank.usagePercent).toInt()
                    g2.color = JBColor(bank.category.color, bank.category.color.darker())
                    g2.fillRect(x, startY + bankHeight - fillHeight, bankWidth, fillHeight)

                    // Warning for nearly full banks
                    if (bank.isFull) {
                        g2.color = JBColor.RED
                        g2.drawRect(x, startY, bankWidth - 1, bankHeight - 1)
                    }
                }

                // Border
                g2.color = JBColor(Color(100, 100, 100), Color(100, 100, 100))
                g2.drawRect(x, startY, bankWidth, bankHeight)

                // Bank number
                g2.color = JBColor.foreground()
                g2.font = g2.font.deriveFont(9f)
                val label = i.toString()
                g2.drawString(
                    label,
                    x + (bankWidth - g2.fontMetrics.stringWidth(label)) / 2,
                    startY + bankHeight + 12,
                )
            }

            // Legend
            var legendX = startX
            val legendY = startY + bankHeight + 30
            g2.font = g2.font.deriveFont(10f)

            for (category in DataCategory.entries) {
                if (category == DataCategory.EMPTY) continue

                g2.color = JBColor(category.color, category.color.darker())
                g2.fillRect(legendX, legendY, 12, 12)

                g2.color = JBColor.foreground()
                g2.drawString(category.displayName, legendX + 16, legendY + 10)

                legendX += g2.fontMetrics.stringWidth(category.displayName) + 30
            }
        }
    }

    /** Panel showing data breakdown by category. */
    private inner class DataBreakdownPanel : JPanel() {
        private var analysis: RomAnalysis? = null

        init {
            preferredSize = Dimension(400, 200)
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }

        fun setAnalysis(analysis: RomAnalysis) {
            this.analysis = analysis
            updateContent()
        }

        private fun updateContent() {
            removeAll()

            val data = analysis
            if (data == null) {
                add(JBLabel("No data"))
                return
            }

            val sortedBreakdown = data.breakdown.entries.sortedByDescending { it.value }

            for ((category, bytes) in sortedBreakdown) {
                if (bytes == 0) continue

                val row = JPanel(BorderLayout())
                row.border = BorderFactory.createEmptyBorder(2, 0, 2, 0)
                row.maximumSize = Dimension(Int.MAX_VALUE, 25)

                // Category label with color
                val labelPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
                val colorBox = JPanel()
                colorBox.background = JBColor(category.color, category.color.darker())
                colorBox.preferredSize = Dimension(12, 12)
                labelPanel.add(colorBox)
                labelPanel.add(JBLabel(category.displayName))
                row.add(labelPanel, BorderLayout.WEST)

                // Size and percentage
                val percent = bytes * 100f / data.totalSize
                val sizeText = String.format(Locale.US, "%s (%.1f%%)", formatSize(bytes), percent)
                row.add(JBLabel(sizeText), BorderLayout.EAST)

                add(row)
            }

            // Total
            add(Box.createVerticalStrut(10))
            val totalRow = JPanel(BorderLayout())
            totalRow.border =
                BorderFactory.createMatteBorder(
                    1,
                    0,
                    0,
                    0,
                    JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(),
                )

            val totalLabel = JBLabel("Total")
            totalLabel.font = totalLabel.font.deriveFont(Font.BOLD)
            totalRow.add(totalLabel, BorderLayout.WEST)

            val totalSize = JBLabel(formatSize(data.totalSize))
            totalSize.font = totalSize.font.deriveFont(Font.BOLD)
            totalRow.add(totalSize, BorderLayout.EAST)
            totalRow.maximumSize = Dimension(Int.MAX_VALUE, 30)

            add(totalRow)

            revalidate()
            repaint()
        }
    }

    companion object {
        const val BANK_SIZE = 16 * 1024 // 16KB per bank
        const val MAX_BANKS = 32 // Standard MBC1 max
    }
}
