/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.ui

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * A [JPanel] that renders the Game Boy LCD framebuffer at a configurable integer scale.
 *
 * Default scale is 4x, producing a 640x576 display (160*4 x 144*4). Rendering uses nearest-neighbor
 * interpolation to preserve pixel-art crispness.
 *
 * Thread safety:
 * - [onFrame] may be called from any thread (the emulator loop thread).
 * - Pixel data is copied under [lock] before scheduling a Swing repaint on the EDT.
 * - [paintComponent] reads pixel data under the same [lock].
 *
 * Usage:
 * ```kotlin
 * val panel = GbDisplayPanel(scale = 4)
 * emulator.onFrameReady = panel::onFrame
 * ```
 *
 * @param scale Integer scale factor applied to the native 160x144 Game Boy resolution. A value of 4
 *   produces a 640x576 window. Must be >= 1.
 */
class GbDisplayPanel(private val scale: Int = 4) : JPanel() {

    // Native Game Boy LCD resolution
    private val gbWidth = 160
    private val gbHeight = 144

    // Backing image for rendering (reused each frame to avoid allocation)
    private val img = BufferedImage(gbWidth, gbHeight, BufferedImage.TYPE_INT_RGB)

    // Double-buffer: emulator thread writes here; paint thread reads from here
    private val pixelBuffer = IntArray(gbWidth * gbHeight)
    private val lock = Any()

    init {
        require(scale >= 1) { "Scale must be at least 1, got $scale" }
        preferredSize = Dimension(gbWidth * scale, gbHeight * scale)
        minimumSize = Dimension(gbWidth, gbHeight)
        background = Color.BLACK
    }

    /**
     * Accepts a new framebuffer snapshot from the emulator.
     *
     * Called by [io.github.gbkt.emulator.CoffeeGbEmulator.onFrameReady] after each completed Game
     * Boy frame. This method is safe to call from any thread.
     *
     * @param frameData RGB pixel array of length 160*144 (= 23040). Each element is a packed
     *   0x00RRGGBB integer. Excess elements beyond 23040 are silently ignored.
     */
    fun onFrame(frameData: IntArray) {
        synchronized(lock) {
            System.arraycopy(frameData, 0, pixelBuffer, 0, minOf(frameData.size, pixelBuffer.size))
        }
        SwingUtilities.invokeLater { repaint() }
    }

    /**
     * Renders the current framebuffer scaled to fill the panel, centered with black letterboxing if
     * the panel's aspect ratio differs from 160:144.
     *
     * Always uses nearest-neighbor interpolation so pixel art stays sharp at any integer or
     * non-integer scale.
     */
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        // Copy pixel data under lock and blit into the backing image
        synchronized(lock) { img.setRGB(0, 0, gbWidth, gbHeight, pixelBuffer, 0, gbWidth) }

        val g2 = g as Graphics2D
        g2.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
        )
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)

        val panelW = width
        val panelH = height

        // Compute uniform scale preserving 160:144 aspect ratio (letterbox/pillarbox)
        val scaleX = panelW.toDouble() / gbWidth.toDouble()
        val scaleY = panelH.toDouble() / gbHeight.toDouble()
        val actualScale = minOf(scaleX, scaleY)

        val drawW = (gbWidth * actualScale).toInt()
        val drawH = (gbHeight * actualScale).toInt()
        val offsetX = (panelW - drawW) / 2
        val offsetY = (panelH - drawH) / 2

        // Fill background with black (letterbox/pillarbox bars)
        g2.color = Color.BLACK
        g2.fillRect(0, 0, panelW, panelH)

        // Draw scaled framebuffer centered in the panel
        g2.drawImage(img, offsetX, offsetY, drawW, drawH, null)
    }
}
