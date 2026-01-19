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
package io.github.gbkt.intellij.editors.animation

import io.github.gbkt.intellij.editors.GbColors
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.Timer

/**
 * Panel for previewing sprite animations.
 *
 * Displays a sequence of frames from a sprite sheet as an animation.
 */
class AnimationPreviewPanel : JPanel() {

    /** Source sprite sheet image. */
    var spriteSheet: BufferedImage? = null
        set(value) {
            field = value
            extractFrames()
            repaint()
        }

    /** List of tile indices to animate. */
    var frameIndices: List<Int> = emptyList()
        set(value) {
            field = value
            extractFrames()
            repaint()
        }

    /** Frame duration in milliseconds. */
    var frameDuration: Int = 100
        set(value) {
            field = value.coerceIn(16, 1000)
            animationTimer.delay = field
        }

    /** Zoom level for display. */
    var zoom: Int = 4
        set(value) {
            field = value.coerceIn(1, 8)
            updateDimensions()
            repaint()
        }

    /** Whether to show in GB preview mode. */
    var gbPreviewMode: Boolean = false
        set(value) {
            field = value
            repaint()
        }

    /** Whether animation is currently playing. */
    var isPlaying: Boolean = false
        private set

    /** Current frame index in the animation. */
    private var currentFrame: Int = 0

    /** Extracted frame images. */
    private var frames: List<BufferedImage> = emptyList()

    /** Animation timer. */
    private val animationTimer = Timer(frameDuration) { advanceFrame() }

    /** Tiles per row in sprite sheet. */
    private val tilesX: Int
        get() = (spriteSheet?.width ?: 0) / TILE_SIZE

    init {
        background = Color(45, 45, 45)
        updateDimensions()
    }

    private fun updateDimensions() {
        val size = TILE_SIZE * zoom + PADDING * 2
        preferredSize = Dimension(size, size)
        minimumSize = preferredSize
        revalidate()
    }

    private fun extractFrames() {
        val sheet = spriteSheet ?: return
        if (frameIndices.isEmpty()) {
            frames = emptyList()
            return
        }

        frames = frameIndices.mapNotNull { tileIndex -> extractTile(sheet, tileIndex) }

        currentFrame = 0
    }

    private fun extractTile(sheet: BufferedImage, tileIndex: Int): BufferedImage? {
        if (tilesX <= 0) return null

        val tileX = tileIndex % tilesX
        val tileY = tileIndex / tilesX

        val x = tileX * TILE_SIZE
        val y = tileY * TILE_SIZE

        if (x + TILE_SIZE > sheet.width || y + TILE_SIZE > sheet.height) {
            return null
        }

        return sheet.getSubimage(x, y, TILE_SIZE, TILE_SIZE)
    }

    fun play() {
        if (frames.isEmpty()) return
        isPlaying = true
        animationTimer.start()
    }

    fun pause() {
        isPlaying = false
        animationTimer.stop()
    }

    fun stop() {
        pause()
        currentFrame = 0
        repaint()
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    private fun advanceFrame() {
        if (frames.isEmpty()) return
        currentFrame = (currentFrame + 1) % frames.size
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
        )

        if (frames.isEmpty()) {
            drawEmptyState(g2)
            return
        }

        val frame = frames.getOrNull(currentFrame) ?: return

        // Draw checkered background
        drawTransparencyBackground(g2)

        // Draw the current frame
        if (gbPreviewMode) {
            drawGbPreview(g2, frame)
        } else {
            g2.drawImage(frame, PADDING, PADDING, TILE_SIZE * zoom, TILE_SIZE * zoom, null)
        }

        // Draw frame counter
        g2.color = Color.WHITE
        g2.drawString("Frame ${currentFrame + 1}/${frames.size}", PADDING, height - 5)
    }

    private fun drawEmptyState(g2: Graphics2D) {
        g2.color = Color.GRAY
        g2.drawString("No frames", width / 2 - 25, height / 2)
    }

    private fun drawTransparencyBackground(g2: Graphics2D) {
        val size = TILE_SIZE * zoom
        val checkSize = 8
        for (y in 0 until size / checkSize + 1) {
            for (x in 0 until size / checkSize + 1) {
                g2.color = if ((x + y) % 2 == 0) Color(60, 60, 60) else Color(40, 40, 40)
                g2.fillRect(PADDING + x * checkSize, PADDING + y * checkSize, checkSize, checkSize)
            }
        }
    }

    private fun drawGbPreview(g2: Graphics2D, frame: BufferedImage) {
        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                val rgb = frame.getRGB(x, y)
                val alpha = (rgb shr 24) and 0xFF

                if (alpha > 0) {
                    val color = Color(rgb, true)
                    val index = GbColors.findClosest2bppIndex(color)
                    val gbColor = GbColors.DMG_PALETTE[index]

                    g2.color = gbColor
                    g2.fillRect(PADDING + x * zoom, PADDING + y * zoom, zoom, zoom)
                }
            }
        }
    }

    companion object {
        const val TILE_SIZE = 8
        const val PADDING = 10
    }
}
