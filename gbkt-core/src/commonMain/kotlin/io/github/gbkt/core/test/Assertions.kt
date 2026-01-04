/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

/**
 * Fluent assertion API for gbkt tests.
 *
 * Provides beautiful, readable assertions that make tests a joy to write and read.
 */

// =============================================================================
// INTEGER EXPECTATIONS
// =============================================================================

/** Expectation builder for integer values. */
class IntExpectation(private val actual: Int, private val name: String) {
    /** Assert exact equality. */
    fun toEqual(expected: Int) {
        if (actual != expected) {
            throw AssertionError("Expected $name to equal $expected, but was $actual")
        }
    }

    /** Assert value is greater than threshold. */
    fun toBeGreaterThan(threshold: Int) {
        if (actual <= threshold) {
            throw AssertionError("Expected $name to be greater than $threshold, but was $actual")
        }
    }

    /** Assert value is greater than or equal to threshold. */
    fun toBeAtLeast(threshold: Int) {
        if (actual < threshold) {
            throw AssertionError("Expected $name to be at least $threshold, but was $actual")
        }
    }

    /** Assert value is less than threshold. */
    fun toBeLessThan(threshold: Int) {
        if (actual >= threshold) {
            throw AssertionError("Expected $name to be less than $threshold, but was $actual")
        }
    }

    /** Assert value is less than or equal to threshold. */
    fun toBeAtMost(threshold: Int) {
        if (actual > threshold) {
            throw AssertionError("Expected $name to be at most $threshold, but was $actual")
        }
    }

    /** Assert value is within a range (inclusive). */
    fun toBeBetween(range: IntRange) {
        if (actual !in range) {
            throw AssertionError("Expected $name to be in $range, but was $actual")
        }
    }

    /** Assert value is zero. */
    fun toBeZero() = toEqual(0)

    /** Assert value is positive (> 0). */
    fun toBePositive() {
        if (actual <= 0) {
            throw AssertionError("Expected $name to be positive, but was $actual")
        }
    }

    /** Assert value is negative (< 0). */
    fun toBeNegative() {
        if (actual >= 0) {
            throw AssertionError("Expected $name to be negative, but was $actual")
        }
    }

    /** Assert value satisfies a custom predicate. */
    fun toSatisfy(description: String = "custom condition", predicate: (Int) -> Boolean) {
        if (!predicate(actual)) {
            throw AssertionError("Expected $name to satisfy $description, but was $actual")
        }
    }

    /** Get the actual value for chaining. */
    val value: Int
        get() = actual
}

// =============================================================================
// SPRITE EXPECTATIONS
// =============================================================================

/** Expectation builder for sprite state. */
class SpriteExpectation(private val sprite: SimSprite?) {
    private val name: String
        get() = sprite?.name ?: "sprite"

    init {
        if (sprite == null) {
            throw AssertionError("Expected sprite to exist, but it was null")
        }
    }

    /** Assert sprite is at exact position. */
    fun toBeAt(x: Int, y: Int) {
        if (sprite!!.x != x || sprite.y != y) {
            throw AssertionError(
                "Expected $name to be at ($x, $y), but was at (${sprite.x}, ${sprite.y})"
            )
        }
    }

    /** Assert sprite X position. */
    fun toHaveX(x: Int) {
        if (sprite!!.x != x) {
            throw AssertionError("Expected $name X to be $x, but was ${sprite.x}")
        }
    }

    /** Assert sprite Y position. */
    fun toHaveY(y: Int) {
        if (sprite!!.y != y) {
            throw AssertionError("Expected $name Y to be $y, but was ${sprite.y}")
        }
    }

    /** Assert sprite is visible. */
    fun toBeVisible() {
        if (!sprite!!.visible) {
            throw AssertionError("Expected $name to be visible, but it was hidden")
        }
    }

    /** Assert sprite is hidden. */
    fun toBeHidden() {
        if (sprite!!.visible) {
            throw AssertionError("Expected $name to be hidden, but it was visible")
        }
    }

    /** Assert sprite is playing a specific animation. */
    fun toBePlayingAnimation(animationName: String) {
        if (sprite!!.currentAnimation != animationName) {
            val current = sprite.currentAnimation ?: "none"
            throw AssertionError(
                "Expected $name to be playing '$animationName', but was playing '$current'"
            )
        }
    }

    /** Assert sprite is not playing any animation. */
    fun toNotBeAnimating() {
        if (sprite!!.currentAnimation != null) {
            throw AssertionError(
                "Expected $name to not be animating, but was playing '${sprite.currentAnimation}'"
            )
        }
    }

    /** Assert sprite animation is paused. */
    fun toHaveAnimationPaused() {
        if (!sprite!!.animationPaused) {
            throw AssertionError("Expected $name animation to be paused, but it was playing")
        }
    }

    /** Assert sprite collides with another sprite. */
    fun toCollideWith(other: SimSprite, width: Int = 8, height: Int = 8) {
        if (!sprite!!.collidesWith(other, width, height)) {
            throw AssertionError(
                "Expected $name to collide with ${other.name}, but they don't overlap"
            )
        }
    }

    /** Assert sprite does not collide with another sprite. */
    fun toNotCollideWith(other: SimSprite, width: Int = 8, height: Int = 8) {
        if (sprite!!.collidesWith(other, width, height)) {
            throw AssertionError(
                "Expected $name to not collide with ${other.name}, but they overlap"
            )
        }
    }
}

// =============================================================================
// POOL EXPECTATIONS
// =============================================================================

/** Expectation builder for pool state. */
class PoolExpectation(private val pool: SimPool?) {
    private val name: String
        get() = pool?.name ?: "pool"

    init {
        if (pool == null) {
            throw AssertionError("Expected pool to exist, but it was null")
        }
    }

    /** Assert pool has exact active count. */
    fun toHaveActiveCount(count: Int) {
        if (pool!!.activeCount != count) {
            throw AssertionError(
                "Expected $name to have $count active entities, but had ${pool.activeCount}"
            )
        }
    }

    /** Assert pool is empty (no active entities). */
    fun toBeEmpty() = toHaveActiveCount(0)

    /** Assert pool has at least one active entity. */
    fun toNotBeEmpty() {
        if (pool!!.activeCount == 0) {
            throw AssertionError("Expected $name to have active entities, but it was empty")
        }
    }

    /** Assert pool has space for spawning. */
    fun toHaveSpace() {
        if (!pool!!.hasSpace) {
            throw AssertionError("Expected $name to have space, but it was full")
        }
    }

    /** Assert pool has space for at least N more entities. */
    fun toHaveSpaceFor(count: Int) {
        val available = pool!!.size - pool.activeCount
        if (available < count) {
            throw AssertionError(
                "Expected $name to have space for $count, but only has room for $available"
            )
        }
    }

    /** Assert pool is full. */
    fun toBeFull() {
        if (!pool!!.isFull) {
            throw AssertionError(
                "Expected $name to be full, but has ${pool.size - pool.activeCount} slots available"
            )
        }
    }

    /** Assert all active entities satisfy a condition. */
    fun allMatch(description: String = "condition", predicate: (index: Int) -> Boolean) {
        for (index in pool!!.activeIndices()) {
            if (!predicate(index)) {
                throw AssertionError(
                    "Expected all entities in $name to match $description, but entity $index did not"
                )
            }
        }
    }

    /** Assert any active entity satisfies a condition. */
    fun anyMatch(description: String = "condition", predicate: (index: Int) -> Boolean) {
        val match = pool!!.activeIndices().any { predicate(it) }
        if (!match) {
            throw AssertionError("Expected at least one entity in $name to match $description")
        }
    }
}

// =============================================================================
// GAME/SCENE EXPECTATIONS
// =============================================================================

/** Expectation builder for overall game state. */
class GameExpectation(private val simulation: SimulationContext) {

    /** Assert game is in a specific scene. */
    fun toBeInScene(sceneName: String) {
        if (simulation.currentScene != sceneName) {
            throw AssertionError(
                "Expected game to be in scene '$sceneName', but was in '${simulation.currentScene}'"
            )
        }
    }

    /** Assert game has reached a certain frame count. */
    fun toHaveFrameCount(count: Int) {
        if (simulation.frameCount != count) {
            throw AssertionError(
                "Expected frame count to be $count, but was ${simulation.frameCount}"
            )
        }
    }

    /** Assert game has run for at least N frames. */
    fun toHaveRunForAtLeast(frames: Int) {
        if (simulation.frameCount < frames) {
            throw AssertionError(
                "Expected to have run for at least $frames frames, but only ran ${simulation.frameCount}"
            )
        }
    }
}

// =============================================================================
// BOOLEAN EXPECTATIONS
// =============================================================================

/** Expectation builder for boolean values. */
class BoolExpectation(private val actual: Boolean, private val name: String) {
    fun toBeTrue() {
        if (!actual) {
            throw AssertionError("Expected $name to be true, but was false")
        }
    }

    fun toBeFalse() {
        if (actual) {
            throw AssertionError("Expected $name to be false, but was true")
        }
    }
}
