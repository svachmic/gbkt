/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.entity

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.ir.AssignableExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRPoolEntityVar
import kotlin.reflect.KProperty

// =============================================================================
// POOL STATE VARIABLE DELEGATE
// Allows typed state access directly in pool lifecycle hooks
// =============================================================================

/**
 * Delegate for pool state variables that can be captured by closure.
 *
 * This enables the pattern:
 * ```kotlin
 * pool("bullets", 8) {
 *     val timer by u8State(60)  // Declare at pool level
 *
 *     onFrame {
 *         timer -= 1  // Use directly in lifecycle hooks
 *     }
 *     despawnWhen {
 *         timer isEqualTo 0
 *     }
 * }
 * ```
 *
 * The variable becomes an AssignableExpr that emits IRPoolEntityVar nodes when used in recording
 * contexts.
 */
class PoolStateVarDelegate(
    private val poolName: String,
    private val indexVar: String,
    private val type: GBVar.VarType,
    private val defaultValue: Int,
    private val fieldRegistry: MutableList<PoolStateField>,
) {
    private var accessor: AssignableExpr? = null

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): PoolStateVarDelegate {
        // Register the field for code generation
        fieldRegistry.add(PoolStateField(property.name, type, defaultValue))
        // Create the accessor
        accessor =
            AssignableExpr(
                "${poolName}_${property.name}",
                type,
                IRPoolEntityVar(poolName, property.name, indexVar),
            )
        return this
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): AssignableExpr {
        return accessor ?: error("PoolStateVarDelegate must be used with 'by' operator")
    }
}

// =============================================================================
// POOL STATE BUILDER
// =============================================================================

/** Builder for custom per-entity state fields. */
@GbktDsl
class PoolStateBuilder(private val poolName: String) {
    internal val fields = mutableListOf<PoolStateField>()

    fun u8Var(initial: Int = 0) = PoolStateDelegate(GBVar.VarType.U8, initial)

    fun i8Var(initial: Int = 0) = PoolStateDelegate(GBVar.VarType.I8, initial)

    fun u16Var(initial: Int = 0) = PoolStateDelegate(GBVar.VarType.U16, initial)

    inner class PoolStateDelegate(private val type: GBVar.VarType, private val defaultValue: Int) {
        operator fun provideDelegate(
            thisRef: Any?,
            property: KProperty<*>,
        ): PoolStateFieldAccessor {
            fields.add(PoolStateField(property.name, type, defaultValue))
            return PoolStateFieldAccessor(property.name)
        }
    }

    /** Accessor that provides AssignableExpr in the scope */
    inner class PoolStateFieldAccessor(private val fieldName: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): AssignableExpr {
            return AssignableExpr(
                "${poolName}_$fieldName",
                fields.find { it.name == fieldName }?.type ?: GBVar.VarType.U8,
                IRPoolEntityVar(poolName, fieldName, "_${poolName}_i"),
            )
        }
    }
}

/** Builder for despawn conditions. */
@GbktDsl
class DespawnConditionBuilder(private val pool: Pool) {
    internal val conditions = mutableListOf<IRExpression>()

    private val scope = PoolEntityScope(pool, pool.indexVar)

    // Delegate position/velocity access to scope
    val x: AssignableExpr
        get() = scope.x

    val y: AssignableExpr
        get() = scope.y

    val velX: AssignableExpr
        get() = scope.velX

    val velY: AssignableExpr
        get() = scope.velY

    /** Animation complete condition */
    val isAnimationComplete: Condition
        get() = scope.isAnimationComplete

    /** Access custom state */
    operator fun get(fieldName: String) = scope[fieldName]

    /** Add a condition - entity despawns when ANY condition is true */
    operator fun Condition.unaryPlus() {
        conditions.add(this.ir)
    }

    // Convenience: just mentioning a condition adds it
    // This allows: despawnWhen { y isBelow 8 } without the + prefix

    /** Allow bare condition expressions to be added */
    @Suppress("UNUSED_PARAMETER")
    operator fun Condition.invoke(dummy: Unit = Unit) {
        conditions.add(this.ir)
    }

    // Override comparison operators to auto-add conditions
    infix fun Expr.isBelow(value: Int): Condition {
        val cond = Condition(IRBinary(this.ir, BinaryOp.LT, IRLiteral(value)))
        conditions.add(cond.ir)
        return cond
    }

    infix fun Expr.isAbove(value: Int): Condition {
        val cond = Condition(IRBinary(this.ir, BinaryOp.GT, IRLiteral(value)))
        conditions.add(cond.ir)
        return cond
    }

    infix fun Expr.isEqualTo(value: Int): Condition {
        val cond = Condition(IRBinary(this.ir, BinaryOp.EQ, IRLiteral(value)))
        conditions.add(cond.ir)
        return cond
    }

    infix fun Expr.isAtLeast(value: Int): Condition {
        val cond = Condition(IRBinary(this.ir, BinaryOp.GTE, IRLiteral(value)))
        conditions.add(cond.ir)
        return cond
    }

    infix fun Expr.isAtMost(value: Int): Condition {
        val cond = Condition(IRBinary(this.ir, BinaryOp.LTE, IRLiteral(value)))
        conditions.add(cond.ir)
        return cond
    }
}

// =============================================================================
// DSL ENTRY POINTS
// =============================================================================

/**
 * Create an entity with the DSL.
 *
 * Usage: val player by entity { position(80, 100) sprite(SpriteAsset("player.png")) { ... } states
 * { ... } }
 */
fun GameBuilder.entity(init: EntityBuilder.() -> Unit): EntityDelegate {
    return EntityDelegate(this, init)
}

/**
 * Create an entity pool with lifecycle management.
 *
 * Usage: val bullets = pool("bullet", size = 8) { position(0, 0) sprite(SpriteAsset("bullet.png"))
 * { size = 4 x 4 }
 *
 *       state {
 *           val timer by u8Var()
 *       }
 *       onSpawn { timer set 120 }
 *       onFrame { y -= 4; timer -= 1 }
 *       despawnWhen { y isBelow 8; timer isEqualTo 0 }
 *   }
 */
fun GameBuilder.pool(name: String, size: Int, init: PoolBuilder.() -> Unit): Pool {
    val builder = PoolBuilder(name, size, this)
    builder.init()

    // Allocate OAM slots for pool sprites
    val oamStartSlot = nextSpriteSlot()
    repeat(size - 1) { nextSpriteSlot() } // Reserve additional slots

    val pool = builder.build(oamStartSlot)
    registerPool(pool)
    return pool
}
