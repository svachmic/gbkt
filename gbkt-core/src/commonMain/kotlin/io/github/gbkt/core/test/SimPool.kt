/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

import io.github.gbkt.core.entity.PoolStateField
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.IRAssign
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRStatement

/**
 * Simulated entity pool for testing. Tracks active entities, their state, and handles spawn/despawn
 * lifecycle.
 */
class SimPool(
    val name: String,
    val size: Int,
    val hasPosition: Boolean,
    val hasVelocity: Boolean,
    val stateFields: List<PoolStateField>,
    val onFrameStatements: List<IRStatement>,
    val despawnConditions: List<IRExpression>
) {
    // Entity active flags
    private val active = BooleanArray(size) { false }

    // Per-entity state: Map of "fieldName" to array of values
    private val entityState = mutableMapOf<String, Array<SimValue>>()

    init {
        // Initialize position variables if pool has position
        if (hasPosition) {
            entityState["x"] = Array(size) { SimValue.ZERO }
            entityState["y"] = Array(size) { SimValue.ZERO }
        }
        if (hasVelocity) {
            entityState["vel_x"] = Array(size) { SimValue.ZERO }
            entityState["vel_y"] = Array(size) { SimValue.ZERO }
        }
        // Initialize custom state fields
        for (field in stateFields) {
            entityState[field.name] = Array(size) { SimValue.of(field.defaultValue) }
        }
    }

    /** Number of currently active entities. */
    val activeCount: Int
        get() = active.count { it }

    /** Check if pool has space for more entities. */
    val hasSpace: Boolean
        get() = active.any { !it }

    /** Check if pool is full. */
    val isFull: Boolean
        get() = !hasSpace

    /** Spawn a new entity, returns the index or -1 if full. */
    fun spawn(sim: SimulationContext, initStatements: List<IRStatement>): Int {
        val index = active.indexOfFirst { !it }
        if (index == -1) return -1

        active[index] = true

        // Reset state to defaults
        if (hasPosition) {
            entityState["x"]!![index] = SimValue.ZERO
            entityState["y"]!![index] = SimValue.ZERO
        }
        if (hasVelocity) {
            entityState["vel_x"]!![index] = SimValue.ZERO
            entityState["vel_y"]!![index] = SimValue.ZERO
        }
        for (field in stateFields) {
            entityState[field.name]!![index] = SimValue.of(field.defaultValue)
        }

        // Set up index variable for init statements
        sim.setVariable("_pool_idx", index)

        // Execute initialization statements
        for (stmt in initStatements) {
            executePoolStatement(sim, stmt, index)
        }

        return index
    }

    /** Spawn at a specific position. */
    fun spawnAt(sim: SimulationContext, x: Int, y: Int, initStatements: List<IRStatement>): Int {
        val index = spawn(sim, initStatements)
        if (index >= 0 && hasPosition) {
            entityState["x"]!![index] = SimValue.of(x)
            entityState["y"]!![index] = SimValue.of(y)
        }
        return index
    }

    /** Despawn entity at index. */
    fun despawn(index: Int) {
        if (index in 0 until size) {
            active[index] = false
        }
    }

    /** Despawn all entities. */
    fun despawnAll() {
        for (i in 0 until size) {
            active[i] = false
        }
    }

    /** Execute forEach over active entities. */
    fun forEach(sim: SimulationContext, indexVar: String, bodyStatements: List<IRStatement>) {
        for (i in 0 until size) {
            if (active[i]) {
                sim.setVariable(indexVar, i)
                syncEntityToContext(sim, i)
                for (stmt in bodyStatements) {
                    executePoolStatement(sim, stmt, i)
                }
                syncContextToEntity(sim, i)
            }
        }
    }

    /** Despawn entities matching a condition. */
    fun despawnWhere(sim: SimulationContext, indexVar: String, condition: IRExpression) {
        for (i in 0 until size) {
            if (active[i]) {
                sim.setVariable(indexVar, i)
                syncEntityToContext(sim, i)
                if (sim.evaluateExpr(condition).isTrue) {
                    active[i] = false
                }
            }
        }
    }

    /** Update all active entities (run onFrame, check despawn conditions). */
    fun update(sim: SimulationContext) {
        for (i in 0 until size) {
            if (active[i]) {
                sim.setVariable("_pool_idx", i)
                syncEntityToContext(sim, i)

                // Execute onFrame statements
                for (stmt in onFrameStatements) {
                    executePoolStatement(sim, stmt, i)
                }

                // Check despawn conditions
                for (condition in despawnConditions) {
                    if (sim.evaluateExpr(condition).isTrue) {
                        active[i] = false
                        break
                    }
                }

                // Sync back if still active
                if (active[i]) {
                    syncContextToEntity(sim, i)
                }
            }
        }
    }

    /** Get entity variable value. */
    fun getEntityVar(index: Int, fieldName: String): SimValue {
        return entityState[fieldName]?.getOrNull(index) ?: SimValue.ZERO
    }

    /** Set entity variable value. */
    fun setEntityVar(index: Int, fieldName: String, value: SimValue) {
        entityState[fieldName]?.set(index, value)
    }

    /** Check if entity at index is active. */
    fun isActive(index: Int): Boolean = index in 0 until size && active[index]

    /** Get all active entity indices. */
    fun activeIndices(): List<Int> = (0 until size).filter { active[it] }

    // Sync entity state to simulation context variables
    private fun syncEntityToContext(sim: SimulationContext, index: Int) {
        for ((field, values) in entityState) {
            val varName = "${name}_${index}_$field"
            sim.setVariable(varName, values[index])
        }

        // Also set pool-prefixed x, y for current entity iteration
        // Use pool name prefix to avoid collision between multiple pools
        if (hasPosition) {
            sim.setVariable("${name}_x", entityState["x"]!![index])
            sim.setVariable("${name}_y", entityState["y"]!![index])
            // Also set simple x/y for backwards compatibility within single-pool usage
            sim.setVariable("x", entityState["x"]!![index])
            sim.setVariable("y", entityState["y"]!![index])
        }
    }

    // Sync simulation context variables back to entity state
    private fun syncContextToEntity(sim: SimulationContext, index: Int) {
        // Check for updates to pool-prefixed variables (preferred)
        if (hasPosition) {
            entityState["x"]!![index] = sim.getVariable("${name}_x")
            entityState["y"]!![index] = sim.getVariable("${name}_y")
        }

        // Also sync custom state fields back from context
        for ((field, values) in entityState) {
            if (field != "x" && field != "y") {
                val varName = "${name}_${index}_$field"
                values[index] = sim.getVariable(varName)
            }
        }
    }

    // Execute a statement in pool context
    private fun executePoolStatement(sim: SimulationContext, stmt: IRStatement, entityIndex: Int) {
        when (stmt) {
            is IRAssign -> {
                // Handle pool entity field assignments
                val target = stmt.target
                if (entityState.containsKey(target)) {
                    val current = entityState[target]!![entityIndex]
                    val newValue = sim.evaluateExpr(stmt.value)
                    val result =
                        when (stmt.op) {
                            AssignOp.SET -> newValue
                            AssignOp.ADD -> current + newValue
                            AssignOp.SUB -> current - newValue
                            AssignOp.MUL -> current * newValue
                            AssignOp.AND -> current and newValue
                            AssignOp.OR -> current or newValue
                        }
                    entityState[target]!![entityIndex] = result

                    // Also update context for chained operations
                    sim.setVariable(target, result)
                } else {
                    // Regular assignment
                    sim.executeStatement(stmt)
                }
            }
            else -> sim.executeStatement(stmt)
        }
    }

    override fun toString(): String = "SimPool($name: $activeCount/$size active)"
}
