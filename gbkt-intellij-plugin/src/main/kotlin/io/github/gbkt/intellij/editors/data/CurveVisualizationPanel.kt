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
package io.github.gbkt.intellij.editors.data

import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import javax.swing.JPanel

/**
 * Panel for visualizing progression curves from balance data.
 *
 * Features:
 * - Line graph rendering for numeric data
 * - Multiple series support with different colors
 * - Auto-scaling to fit data
 * - Grid and axis labels
 * - Hover tooltips
 */
class CurveVisualizationPanel : JPanel() {

    /** Data series to display. */
    data class DataSeries(val name: String, val values: List<Double>, val color: Color)

    private val series = mutableListOf<DataSeries>()

    /** X-axis label. */
    var xAxisLabel: String = "Level"

    /** Y-axis label. */
    var yAxisLabel: String = "Value"

    /** Whether to show the grid. */
    var showGrid: Boolean = true

    /** Whether to show data points. */
    var showPoints: Boolean = true

    /** Whether to show legend. */
    var showLegend: Boolean = true

    init {
        preferredSize = Dimension(400, 300)
        background = JBColor.background()
    }

    /** Clears all data series. */
    fun clearSeries() {
        series.clear()
        repaint()
    }

    /** Adds a data series to display. */
    fun addSeries(
        name: String,
        values: List<Double>,
        color: Color = SERIES_COLORS[series.size % SERIES_COLORS.size],
    ) {
        series.add(DataSeries(name, values, color))
        repaint()
    }

    /** Sets data from a BalanceDataModel. */
    fun setData(model: BalanceDataModel) {
        clearSeries()

        // Skip the key column (usually level)
        for ((index, col) in model.columns.withIndex()) {
            if (col.isKey) continue

            val values =
                model.rows.mapNotNull { row ->
                    when (val value = row.getOrNull(index)) {
                        is Number -> value.toDouble()
                        else -> null
                    }
                }

            if (values.isNotEmpty()) {
                addSeries(col.name, values)
            }
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        if (series.isEmpty()) {
            drawEmptyState(g2)
            return
        }

        val chartBounds = calculateChartBounds()

        if (showGrid) {
            drawGrid(g2, chartBounds)
        }

        drawAxes(g2, chartBounds)
        drawSeries(g2, chartBounds)

        if (showLegend && series.size > 1) {
            drawLegend(g2)
        }
    }

    private data class ChartBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val minX: Double,
        val maxX: Double,
        val minY: Double,
        val maxY: Double,
    ) {
        val width: Int
            get() = right - left

        val height: Int
            get() = bottom - top
    }

    private fun calculateChartBounds(): ChartBounds {
        val allValues = series.flatMap { it.values }
        val maxDataPoints = series.maxOfOrNull { it.values.size } ?: 1

        val minY = 0.0
        val maxY = (allValues.maxOrNull() ?: 100.0) * 1.1 // 10% headroom

        return ChartBounds(
            left = MARGIN_LEFT,
            top = MARGIN_TOP,
            right = width - MARGIN_RIGHT,
            bottom = height - MARGIN_BOTTOM,
            minX = 1.0,
            maxX = maxDataPoints.toDouble(),
            minY = minY,
            maxY = maxY,
        )
    }

    private fun drawEmptyState(g2: Graphics2D) {
        g2.color = JBColor.GRAY
        val message = "No data to display"
        val fm = g2.fontMetrics
        val x = (width - fm.stringWidth(message)) / 2
        val y = height / 2
        g2.drawString(message, x, y)
    }

    private fun drawGrid(g2: Graphics2D, bounds: ChartBounds) {
        g2.color = JBColor(Color(200, 200, 200), Color(60, 60, 60))
        g2.stroke =
            BasicStroke(
                1f,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                1f,
                floatArrayOf(4f, 4f),
                0f,
            )

        // Horizontal grid lines
        val ySteps = 5
        for (i in 0..ySteps) {
            val y = bounds.top + (bounds.height * i / ySteps)
            g2.drawLine(bounds.left, y, bounds.right, y)
        }

        // Vertical grid lines
        val xSteps = minOf(10, (bounds.maxX - bounds.minX).toInt())
        for (i in 0..xSteps) {
            val x = bounds.left + (bounds.width * i / xSteps)
            g2.drawLine(x, bounds.top, x, bounds.bottom)
        }
    }

    private fun drawAxes(g2: Graphics2D, bounds: ChartBounds) {
        g2.color = JBColor.foreground()
        g2.stroke = BasicStroke(2f)

        // Y axis
        g2.drawLine(bounds.left, bounds.top, bounds.left, bounds.bottom)
        // X axis
        g2.drawLine(bounds.left, bounds.bottom, bounds.right, bounds.bottom)

        // Y axis labels
        val ySteps = 5
        g2.font = g2.font.deriveFont(10f)
        for (i in 0..ySteps) {
            val value = bounds.minY + (bounds.maxY - bounds.minY) * (ySteps - i) / ySteps
            val y = bounds.top + (bounds.height * i / ySteps)
            val label = formatValue(value)
            val fm = g2.fontMetrics
            g2.drawString(label, bounds.left - fm.stringWidth(label) - 5, y + fm.ascent / 2)
        }

        // X axis labels
        val xSteps = minOf(10, (bounds.maxX - bounds.minX).toInt())
        for (i in 0..xSteps) {
            val value = bounds.minX + (bounds.maxX - bounds.minX) * i / xSteps
            val x = bounds.left + (bounds.width * i / xSteps)
            val label = value.toInt().toString()
            val fm = g2.fontMetrics
            g2.drawString(label, x - fm.stringWidth(label) / 2, bounds.bottom + fm.height)
        }

        // Axis labels
        g2.font = g2.font.deriveFont(12f)

        // X axis label
        val fm = g2.fontMetrics
        g2.drawString(
            xAxisLabel,
            bounds.left + bounds.width / 2 - fm.stringWidth(xAxisLabel) / 2,
            height - 5,
        )

        // Y axis label (rotated)
        val oldTransform = g2.transform
        g2.rotate(-Math.PI / 2, 15.0, (bounds.top + bounds.height / 2).toDouble())
        g2.drawString(yAxisLabel, 15, bounds.top + bounds.height / 2 + fm.ascent / 2)
        g2.transform = oldTransform
    }

    private fun drawSeries(g2: Graphics2D, bounds: ChartBounds) {
        for (dataSeries in series) {
            if (dataSeries.values.isEmpty()) continue

            g2.color = dataSeries.color
            g2.stroke = BasicStroke(2f)

            val path = Path2D.Double()
            var firstPoint = true

            for ((index, value) in dataSeries.values.withIndex()) {
                val x = mapX(index + 1.0, bounds)
                val y = mapY(value, bounds)

                if (firstPoint) {
                    path.moveTo(x, y)
                    firstPoint = false
                } else {
                    path.lineTo(x, y)
                }
            }

            g2.draw(path)

            // Draw points
            if (showPoints && dataSeries.values.size <= 50) {
                for ((index, value) in dataSeries.values.withIndex()) {
                    val x = mapX(index + 1.0, bounds)
                    val y = mapY(value, bounds)
                    g2.fillOval(x.toInt() - 3, y.toInt() - 3, 6, 6)
                }
            }
        }
    }

    private fun drawLegend(g2: Graphics2D) {
        val x = width - MARGIN_RIGHT - 100
        val y = MARGIN_TOP + 10
        val lineHeight = 15

        g2.font = g2.font.deriveFont(10f)

        for ((index, dataSeries) in series.withIndex()) {
            val ly = y + index * lineHeight

            // Color box
            g2.color = dataSeries.color
            g2.fillRect(x, ly, 12, 12)

            // Label
            g2.color = JBColor.foreground()
            g2.drawString(dataSeries.name, x + 16, ly + 10)
        }
    }

    private fun mapX(value: Double, bounds: ChartBounds): Double {
        val normalized = (value - bounds.minX) / (bounds.maxX - bounds.minX)
        return bounds.left + normalized * bounds.width
    }

    private fun mapY(value: Double, bounds: ChartBounds): Double {
        val normalized = (value - bounds.minY) / (bounds.maxY - bounds.minY)
        return bounds.bottom - normalized * bounds.height
    }

    private fun formatValue(value: Double): String {
        return if (value >= 1000) {
            "${(value / 1000).toInt()}k"
        } else if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", value)
        }
    }

    companion object {
        const val MARGIN_LEFT = 60
        const val MARGIN_RIGHT = 20
        const val MARGIN_TOP = 20
        const val MARGIN_BOTTOM = 40

        val SERIES_COLORS =
            listOf(
                Color(66, 133, 244), // Blue
                Color(234, 67, 53), // Red
                Color(52, 168, 83), // Green
                Color(251, 188, 5), // Yellow
                Color(156, 39, 176), // Purple
                Color(0, 172, 193), // Cyan
                Color(255, 112, 67), // Orange
                Color(102, 187, 106), // Light green
            )
    }
}
