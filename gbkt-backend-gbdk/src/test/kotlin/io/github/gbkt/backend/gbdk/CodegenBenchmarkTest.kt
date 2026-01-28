/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk

import io.github.gbkt.backend.api.BackendRegistry
import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.core.*
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.input.buttons
import io.github.gbkt.core.input.dpad
import io.github.gbkt.core.ir.*
import kotlin.system.measureNanoTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Performance benchmarks for code generation.
 *
 * These tests measure:
 * - DSL recording time
 * - IR validation time
 * - Code generation time
 * - Generated code size
 *
 * Run with: ./gradlew :gbkt-backend-gbdk:test --tests "*.CodegenBenchmarkTest.*"
 *
 * Results are printed to console and can be used to track regressions.
 */
class CodegenBenchmarkTest {

    @BeforeTest
    fun setUp() {
        BackendRegistry.clear()
        BackendRegistry.discover()
    }

    @AfterTest
    fun tearDown() {
        BackendRegistry.clear()
    }

    private val backend
        get() = BackendRegistry.forId("gbdk") ?: error("GBDK backend not found")

    // ============================================================================
    // Benchmark: Minimal Game
    // ============================================================================

    @Test
    fun `benchmark minimal game pipeline`() {
        val warmupIterations = 5
        val measureIterations = 20

        // Warmup - use fully qualified name to avoid DSL repeat shadowing
        for (i in 1..warmupIterations) {
            benchmarkPipeline { createMinimalGame() }
        }

        // Measure
        val results = (1..measureIterations).map { benchmarkPipeline { createMinimalGame() } }

        printBenchmarkResults("Minimal Game", results)
    }

    // ============================================================================
    // Benchmark: Medium Complexity Game
    // ============================================================================

    @Test
    fun `benchmark medium game pipeline`() {
        val warmupIterations = 3
        val measureIterations = 10

        // Warmup - use for loop to avoid DSL repeat shadowing
        for (i in 1..warmupIterations) {
            benchmarkPipeline { createMediumGame() }
        }

        // Measure
        val results = (1..measureIterations).map { benchmarkPipeline { createMediumGame() } }

        printBenchmarkResults("Medium Game", results)
    }

    // ============================================================================
    // Benchmark: Scalability Test
    // ============================================================================

    @Test
    fun `benchmark scalability with increasing entity count`() {
        val entityCounts = listOf(1, 5, 10, 20)

        println("\n=== Entity Count Scalability ===")
        println("Entities | DSL (ms) | Validation (ms) | Codegen (ms) | Total (ms) | Code Size")
        println("-".repeat(80))

        for (count in entityCounts) {
            val result = benchmarkPipeline { createGameWithEntities(count) }
            println(
                "%8d | %8.2f | %15.2f | %12.2f | %10.2f | %d bytes"
                    .format(
                        count,
                        result.dslTimeNs / 1_000_000.0,
                        result.validationTimeNs / 1_000_000.0,
                        result.codegenTimeNs / 1_000_000.0,
                        result.totalTimeNs / 1_000_000.0,
                        result.codeSize,
                    )
            )
        }
    }

    @Test
    fun `benchmark scalability with increasing scene count`() {
        val sceneCounts = listOf(1, 3, 5, 10)

        println("\n=== Scene Count Scalability ===")
        println("Scenes | DSL (ms) | Validation (ms) | Codegen (ms) | Total (ms) | Code Size")
        println("-".repeat(80))

        for (count in sceneCounts) {
            val result = benchmarkPipeline { createGameWithScenes(count) }
            println(
                "%6d | %8.2f | %15.2f | %12.2f | %10.2f | %d bytes"
                    .format(
                        count,
                        result.dslTimeNs / 1_000_000.0,
                        result.validationTimeNs / 1_000_000.0,
                        result.codegenTimeNs / 1_000_000.0,
                        result.totalTimeNs / 1_000_000.0,
                        result.codeSize,
                    )
            )
        }
    }

    // ============================================================================
    // Benchmark Infrastructure
    // ============================================================================

    data class BenchmarkResult(
        val dslTimeNs: Long,
        val validationTimeNs: Long,
        val codegenTimeNs: Long,
        val totalTimeNs: Long,
        val codeSize: Int,
    )

    private fun benchmarkPipeline(gameFactory: () -> Game): BenchmarkResult {
        var game: Game? = null
        var codeSize = 0

        // Measure DSL recording
        val dslTime = measureNanoTime { game = gameFactory() }

        // Measure validation
        val validationTime = measureNanoTime { backend.validate(game!!) }

        // Measure code generation
        val codegenTime = measureNanoTime {
            val code = GBDKCodeGenerator(game!!).generate()
            codeSize = code.length
        }

        return BenchmarkResult(
            dslTimeNs = dslTime,
            validationTimeNs = validationTime,
            codegenTimeNs = codegenTime,
            totalTimeNs = dslTime + validationTime + codegenTime,
            codeSize = codeSize,
        )
    }

    private fun printBenchmarkResults(name: String, results: List<BenchmarkResult>) {
        val avgDsl = results.map { it.dslTimeNs }.average()
        val avgValidation = results.map { it.validationTimeNs }.average()
        val avgCodegen = results.map { it.codegenTimeNs }.average()
        val avgTotal = results.map { it.totalTimeNs }.average()
        val avgCodeSize = results.map { it.codeSize }.average()

        val minTotal = results.minOf { it.totalTimeNs }
        val maxTotal = results.maxOf { it.totalTimeNs }

        println()
        println("=== $name Benchmark Results (${results.size} iterations) ===")
        println("DSL Recording:   %.2f ms (avg)".format(avgDsl / 1_000_000.0))
        println("Validation:      %.2f ms (avg)".format(avgValidation / 1_000_000.0))
        println("Code Generation: %.2f ms (avg)".format(avgCodegen / 1_000_000.0))
        println(
            "Total Pipeline:  %.2f ms (avg), %.2f ms (min), %.2f ms (max)"
                .format(avgTotal / 1_000_000.0, minTotal / 1_000_000.0, maxTotal / 1_000_000.0)
        )
        println("Generated Code:  %.0f bytes (avg)".format(avgCodeSize))
        println()
    }

    // ============================================================================
    // Game Factories
    // ============================================================================

    private fun createMinimalGame(): Game =
        gbGame("MinimalGame") {
            val mainScene = scene("main") { every.frame {} }
            start = mainScene
        }

    private fun createMediumGame(): Game =
        gbGame("MediumGame") {
            var score by u16Var(0)
            var lives by u8Var(3)
            var posX by u8Var(80)
            var posY by u8Var(72)

            val player by entity { position(80, 72) }

            lateinit var gameplayScene: SceneRef
            lateinit var gameoverScene: SceneRef

            gameplayScene =
                scene("gameplay") {
                    every.frame {
                        whenever(dpad.right) { posX += 2 }
                        whenever(dpad.left) { posX -= 2 }
                        whenever(dpad.up) { posY -= 2 }
                        whenever(dpad.down) { posY += 2 }

                        score += 1
                    }
                }

            gameoverScene = scene("gameover") { every.frame {} }

            val titleScene =
                scene("title") {
                    every.frame { whenever(buttons.start.pressed) { scene(gameplayScene) } }
                }

            start = titleScene
        }

    private fun createGameWithEntities(count: Int): Game =
        gbGame("EntityGame$count") {
            repeat(count) { i -> entity { position(i * 10, i * 10) } }

            val mainScene = scene("main") { every.frame {} }
            start = mainScene
        }

    private fun createGameWithScenes(count: Int): Game =
        gbGame("SceneGame$count") {
            val firstScene = scene("scene0") { every.frame {} }

            repeat(count - 1) { i -> scene("scene${i + 1}") { every.frame {} } }

            start = firstScene
        }
}
