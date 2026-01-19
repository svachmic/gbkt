/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

/**
 * Simulated sprite state for testing. Tracks position, visibility, and animation without actual
 * rendering.
 */
data class SimSprite(
    val name: String,
    var x: Int,
    var y: Int,
    var visible: Boolean = true,
    var currentAnimation: String? = null,
    var animationPaused: Boolean = false,
    var animationFrame: Int = 0,
) {
    /** Check if sprite is playing a specific animation. */
    fun isPlayingAnimation(animationName: String): Boolean =
        currentAnimation == animationName && !animationPaused

    /** Check if sprite is at a specific position. */
    fun isAt(expectedX: Int, expectedY: Int): Boolean = x == expectedX && y == expectedY

    /**
     * Check AABB collision with another sprite. Uses simple bounding box based on position and
     * assumed 8x8 size.
     */
    fun collidesWith(other: SimSprite, width: Int = 8, height: Int = 8): Boolean {
        return x < other.x + width &&
            x + width > other.x &&
            y < other.y + height &&
            y + height > other.y
    }

    /** Check AABB collision with hitbox parameters. */
    fun collidesWithHitbox(
        other: SimSprite,
        thisWidth: Int,
        thisHeight: Int,
        otherWidth: Int,
        otherHeight: Int,
    ): Boolean {
        return x < other.x + otherWidth &&
            x + thisWidth > other.x &&
            y < other.y + otherHeight &&
            y + thisHeight > other.y
    }

    override fun toString(): String =
        "SimSprite($name @ $x,$y${if (currentAnimation != null) " playing '$currentAnimation'" else ""}${if (!visible) " [hidden]" else ""})"
}
