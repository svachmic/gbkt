/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.core.ir.ArrayAccessExpr
import io.github.gbkt.core.ir.ArrayAssign
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.CallOp
import io.github.gbkt.core.ir.CameraOp
import io.github.gbkt.core.ir.CastExpr
import io.github.gbkt.core.ir.ChestObjectIR
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.DialogChoice
import io.github.gbkt.core.ir.DialogExprSegment
import io.github.gbkt.core.ir.DialogSay
import io.github.gbkt.core.ir.DialogTextSegment
import io.github.gbkt.core.ir.DoorObjectIR
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.FadeOp
import io.github.gbkt.core.ir.ForOp
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GotoXYOp
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.LeverObjectIR
import io.github.gbkt.core.ir.MathOp
import io.github.gbkt.core.ir.MoveBy
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.NpcObjectIR
import io.github.gbkt.core.ir.PoolDestroyActor
import io.github.gbkt.core.ir.PoolForEachActive
import io.github.gbkt.core.ir.PoolSpawnActor
import io.github.gbkt.core.ir.PressurePlateObjectIR
import io.github.gbkt.core.ir.PrintOp
import io.github.gbkt.core.ir.PuzzleEventHandler
import io.github.gbkt.core.ir.PuzzleObjectIR
import io.github.gbkt.core.ir.ReturnOp
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SconceObjectIR
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.SetPosition
import io.github.gbkt.core.ir.SignObjectIR
import io.github.gbkt.core.ir.SwitchObjectIR
import io.github.gbkt.core.ir.SystemIR
import io.github.gbkt.core.ir.TernaryExpr
import io.github.gbkt.core.ir.TimedBlockObjectIR
import io.github.gbkt.core.ir.TriggerObjectIR
import io.github.gbkt.core.ir.TriggerSystem
import io.github.gbkt.core.ir.UnaryExpr
import io.github.gbkt.core.ir.WhileOp
import io.github.gbkt.core.ir.ZoneIR
import io.github.gbkt.core.ir.ZoneObjectIR

// ---------------------------------------------------------------------------
// Shared expression child mapping
// ---------------------------------------------------------------------------

/**
 * Recursively applies [transform] to every child [Expr] inside compound expression types.
 *
 * For [BinaryExpr], only the children (left/right) are transformed — the caller handles the
 * [BinaryExpr] node itself. For all other compound types (UnaryExpr, TernaryExpr, ArrayAccessExpr,
 * CallExpr, CastExpr), the node is rebuilt with transformed children. Leaf nodes (Literal, VarRef,
 * StringLiteral, PropertyAccessExpr, etc.) are returned as-is.
 *
 * This is the single source of truth for structural recursion into expression children, used by
 * [BitwiseOptimizationPass] and [ConstantFoldingPass].
 */
internal fun mapExprChildren(expr: Expr, transform: (Expr) -> Expr): Expr =
    when (expr) {
        is BinaryExpr -> expr.copy(left = transform(expr.left), right = transform(expr.right))
        is UnaryExpr -> expr.copy(operand = transform(expr.operand))
        is TernaryExpr ->
            expr.copy(
                condition = transform(expr.condition),
                thenExpr = transform(expr.thenExpr),
                elseExpr = transform(expr.elseExpr),
            )
        is ArrayAccessExpr -> expr.copy(index = transform(expr.index))
        is CallExpr -> expr.copy(args = expr.args.map { transform(it) })
        is CastExpr -> expr.copy(inner = transform(expr.inner))
        else -> expr // Literal, VarRef, StringLiteral, PropertyAccessExpr, PoolGetActiveCount
    }

// ---------------------------------------------------------------------------
// Shared nested-op traversal
// ---------------------------------------------------------------------------

/**
 * Invokes [action] on every nested [ScriptOp] list inside [op].
 *
 * This is the single source of truth for which [ScriptOp] subtypes contain nested op lists. Used by
 * [collectNavigations], [collectAllOps], and any other function that needs to walk the op tree
 * structure.
 */
internal inline fun forEachNestedOpList(op: ScriptOp, action: (List<ScriptOp>) -> Unit) {
    when (op) {
        is IfOp -> {
            action(op.then)
            action(op.otherwise)
        }
        is WhileOp -> action(op.body)
        is ForOp -> action(op.body)
        is FadeOp -> action(op.after)
        is DialogChoice -> op.options.forEach { action(it.body) }
        is PoolForEachActive -> action(op.body)
        is PoolDestroyActor -> action(op.deathCallbackOps)
        else -> Unit
    }
}

// ---------------------------------------------------------------------------
// Scene transition graph utilities
// ---------------------------------------------------------------------------

/**
 * Builds a scene transition graph by collecting all [NavigateTo] targets from every scene's op
 * lists, collision rules, zone callbacks, exploration callbacks, menu item bodies, combat hooks,
 * and puzzle event handlers. Nested ops inside [IfOp], [WhileOp], [ForOp], [FadeOp], and
 * [DialogChoice] are walked recursively.
 *
 * Transitions from non-scene sources (collision rules, zones, etc.) are collected into a synthetic
 * `__global__` node. Every scene has an implicit edge to `__global__`, ensuring these targets are
 * reachable from any scene in BFS.
 *
 * @return Map from source scene ID to the set of scene IDs it transitions to.
 */
@Suppress("LongMethod") // Collects navigations from all game IR sources — by design
internal fun buildTransitionGraph(game: GameIR): Map<String, Set<String>> {
    val graph = mutableMapOf<String, MutableSet<String>>()

    // Scene-local transitions
    for (scene in game.scenes) {
        val edges = graph.getOrPut(scene.id) { mutableSetOf() }
        collectNavigations(scene.enterOps, edges)
        collectNavigations(scene.frameOps, edges)
        collectNavigations(scene.exitOps, edges)
    }

    // Non-scene transitions: collision rules, zones, menus, combat hooks, puzzle handlers
    val globalEdges = mutableSetOf<String>()
    for (rule in game.collisionRules) {
        collectNavigations(rule.onCollide, globalEdges)
    }
    for (zone in game.zones) {
        collectNavigations(zone.onEnter, globalEdges)
        collectNavigations(zone.onExit, globalEdges)
        for (obj in zone.objects) {
            collectNavigations(obj.onInteract, globalEdges)
        }
    }
    for (menu in game.menus) {
        for (item in menu.items) {
            collectNavigations(item.body, globalEdges)
        }
    }
    for (pool in game.actorPools) {
        collectNavigations(pool.deathCallback, globalEdges)
    }
    for (system in game.systems) {
        collectSystemNavigations(system, globalEdges)
    }
    for (puzzleObj in game.puzzleObjects) {
        for (handler in puzzleObj.handlers) {
            collectNavigations(handler.actions, globalEdges)
        }
    }

    // Make global targets reachable from every scene
    if (globalEdges.isNotEmpty()) {
        for (scene in game.scenes) {
            graph.getOrPut(scene.id) { mutableSetOf() }.addAll(globalEdges)
        }
    }

    return graph
}

/** Recursively collects all [NavigateTo] target IDs from a list of [ScriptOp]s. */
internal fun collectNavigations(ops: List<ScriptOp>, out: MutableSet<String>) {
    for (op in ops) {
        if (op is NavigateTo) out += op.sceneId
        forEachNestedOpList(op) { nested -> collectNavigations(nested, out) }
    }
}

/** Collects [NavigateTo] targets from all script op lists within a [SystemIR]. */
internal fun collectSystemNavigations(system: SystemIR, out: MutableSet<String>) {
    when (system) {
        is ExplorationSystem -> {
            collectNavigations(system.stepStatements, out)
            collectNavigations(system.blockedStatements, out)
            collectNavigations(system.interactStatements, out)
            for (gauge in system.gauges) {
                collectNavigations(gauge.onLowStatements, out)
                collectNavigations(gauge.onDepletedStatements, out)
            }
        }
        is CombatEngineSystem -> {
            collectNavigations(system.onVictoryCondition, out)
            collectNavigations(system.onDefeatCondition, out)
            collectNavigations(system.onVictoryOps, out)
            collectNavigations(system.onDefeatOps, out)
            for ((_, ops) in system.combatHooks) {
                collectNavigations(ops, out)
            }
        }
        else -> Unit
    }
}

/**
 * Computes the set of all scene IDs reachable from [start] via the [graph] edges using BFS.
 *
 * @return Set of reachable scene IDs (including [start] itself).
 */
internal fun bfsReachable(start: String, graph: Map<String, Set<String>>): Set<String> {
    val visited = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    queue += start
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (visited.add(current)) {
            val neighbors = graph[current] ?: emptySet()
            queue.addAll(neighbors.filterNot { it in visited })
        }
    }
    return visited
}

// ---------------------------------------------------------------------------
// ScriptOp expression transformation
// ---------------------------------------------------------------------------

/**
 * Applies [transformExpr] to every [Expr] field in [op], recursing into nested op lists.
 *
 * This is the structural traversal that both [BitwiseOptimizationPass] and [ConstantFoldingPass]
 * share: they only differ in the expression transformation logic.
 */
@Suppress("LongMethod") // Pattern match over all ScriptOp subtypes — by design
internal fun transformExprsInOp(
    op: ScriptOp,
    transformExpr: (Expr) -> Expr,
    transformOps: (List<ScriptOp>) -> List<ScriptOp>,
): ScriptOp =
    when (op) {
        is Assign -> op.copy(value = transformExpr(op.value))
        is ArrayAssign -> op.copy(index = transformExpr(op.index), value = transformExpr(op.value))
        is IfOp ->
            op.copy(
                condition = transformExpr(op.condition),
                then = transformOps(op.then),
                otherwise = transformOps(op.otherwise),
            )
        is WhileOp -> op.copy(condition = transformExpr(op.condition), body = transformOps(op.body))
        is ForOp ->
            op.copy(
                from = transformExpr(op.from),
                to = transformExpr(op.to),
                body = transformOps(op.body),
            )
        is SetPosition -> op.copy(x = transformExpr(op.x), y = transformExpr(op.y))
        is MoveBy -> op.copy(dx = transformExpr(op.dx), dy = transformExpr(op.dy))
        is TriggerSystem -> op.copy(args = op.args.mapValues { (_, v) -> transformExpr(v) })
        is PrintOp -> op.copy(values = op.values.map { transformExpr(it) })
        is FadeOp -> op.copy(after = transformOps(op.after))
        is DialogChoice ->
            op.copy(options = op.options.map { opt -> opt.copy(body = transformOps(opt.body)) })
        is CallOp -> op.copy(args = op.args.map { transformExpr(it) })
        is ReturnOp -> op.copy(value = op.value?.let { transformExpr(it) })
        is MathOp -> op.copy(args = op.args.map { transformExpr(it) })
        is CameraOp -> op.copy(args = op.args.mapValues { (_, v) -> transformExpr(v) })
        is PoolForEachActive -> op.copy(body = transformOps(op.body))
        is PoolSpawnActor -> op.copy(x = transformExpr(op.x), y = transformExpr(op.y))
        is PoolDestroyActor ->
            op.copy(
                slotExpr = transformExpr(op.slotExpr),
                deathCallbackOps = transformOps(op.deathCallbackOps),
            )
        is GotoXYOp -> op.copy(x = transformExpr(op.x), y = transformExpr(op.y))
        is DialogSay ->
            op.copy(
                segments =
                    op.segments.map { seg ->
                        when (seg) {
                            is DialogExprSegment -> seg.copy(expr = transformExpr(seg.expr))
                            is DialogTextSegment -> seg
                        }
                    }
            )
        else -> op // NavigateTo, PlaySound, SpawnActor, DestroyActor, AnimateOp,
    // SetVisible, WaitFrames, RawOp — no expression fields
    }

/** Applies [transformExpr] to every [Expr] field in all ops, recursing into nested op lists. */
internal fun transformExprsInOps(
    ops: List<ScriptOp>,
    transformExpr: (Expr) -> Expr,
): List<ScriptOp> = ops.map {
    transformExprsInOp(it, transformExpr) { nested -> transformExprsInOps(nested, transformExpr) }
}

/**
 * Applies [transformExpr] to all expression fields in scenes, systems, zones, collision rules,
 * puzzle objects, actor pools, dialogs, menus, and combat engine hooks of [game].
 */
internal fun transformExprsInGame(game: GameIR, transformExpr: (Expr) -> Expr): GameIR =
    game.copy(
        scenes = game.scenes.map { transformExprsInScene(it, transformExpr) },
        systems = game.systems.map { transformExprsInSystem(it, transformExpr) },
        zones = game.zones.map { transformExprsInZone(it, transformExpr) },
        collisionRules =
            game.collisionRules.map {
                it.copy(onCollide = transformExprsInOps(it.onCollide, transformExpr))
            },
        actorPools =
            game.actorPools.map {
                it.copy(deathCallback = transformExprsInOps(it.deathCallback, transformExpr))
            },
        menus =
            game.menus.map { menu ->
                menu.copy(
                    items =
                        menu.items.map { item ->
                            item.copy(body = transformExprsInOps(item.body, transformExpr))
                        }
                )
            },
        puzzleObjects = game.puzzleObjects.map { transformExprsInPuzzleObject(it, transformExpr) },
    )

/** Applies [transformExpr] to all expression fields in a single [scene]. */
internal fun transformExprsInScene(scene: SceneIR, transformExpr: (Expr) -> Expr): SceneIR =
    scene.copy(
        enterOps = transformExprsInOps(scene.enterOps, transformExpr),
        frameOps = transformExprsInOps(scene.frameOps, transformExpr),
        exitOps = transformExprsInOps(scene.exitOps, transformExpr),
    )

/**
 * Applies [transformExpr] to all expression fields in a single [system]. ExplorationSystem and
 * CombatEngineSystem have ScriptOp lists; other systems pass through unchanged.
 */
internal fun transformExprsInSystem(system: SystemIR, transformExpr: (Expr) -> Expr): SystemIR =
    when (system) {
        is ExplorationSystem ->
            system.copy(
                stepStatements = transformExprsInOps(system.stepStatements, transformExpr),
                blockedStatements = transformExprsInOps(system.blockedStatements, transformExpr),
                interactStatements = transformExprsInOps(system.interactStatements, transformExpr),
                gauges =
                    system.gauges.map { gauge ->
                        gauge.copy(
                            onLowStatements =
                                transformExprsInOps(gauge.onLowStatements, transformExpr),
                            onDepletedStatements =
                                transformExprsInOps(gauge.onDepletedStatements, transformExpr),
                        )
                    },
            )
        is CombatEngineSystem ->
            system.copy(
                onVictoryCondition = transformExprsInOps(system.onVictoryCondition, transformExpr),
                onDefeatCondition = transformExprsInOps(system.onDefeatCondition, transformExpr),
                onVictoryOps = transformExprsInOps(system.onVictoryOps, transformExpr),
                onDefeatOps = transformExprsInOps(system.onDefeatOps, transformExpr),
                combatHooks =
                    system.combatHooks.mapValues { (_, ops) ->
                        transformExprsInOps(ops, transformExpr)
                    },
            )
        else -> system
    }

/** Applies [transformExpr] to all expression fields in a single [zone]. */
internal fun transformExprsInZone(zone: ZoneIR, transformExpr: (Expr) -> Expr): ZoneIR =
    zone.copy(
        onEnter = transformExprsInOps(zone.onEnter, transformExpr),
        onExit = transformExprsInOps(zone.onExit, transformExpr),
        objects = zone.objects.map { transformExprsInZoneObject(it, transformExpr) },
    )

/** Applies [transformExpr] to all expression fields in a single [zoneObj]. */
@Suppress("LongMethod") // Pattern match over all ZoneObjectIR subtypes — by design
internal fun transformExprsInZoneObject(
    zoneObj: ZoneObjectIR,
    transformExpr: (Expr) -> Expr,
): ZoneObjectIR =
    when (zoneObj) {
        is ChestObjectIR ->
            zoneObj.copy(onInteract = transformExprsInOps(zoneObj.onInteract, transformExpr))
        is SignObjectIR ->
            zoneObj.copy(onInteract = transformExprsInOps(zoneObj.onInteract, transformExpr))
        is SconceObjectIR ->
            zoneObj.copy(
                onInteract = transformExprsInOps(zoneObj.onInteract, transformExpr),
                onLit = transformExprsInOps(zoneObj.onLit, transformExpr),
                onExtinguished = transformExprsInOps(zoneObj.onExtinguished, transformExpr),
            )
        is NpcObjectIR ->
            zoneObj.copy(onInteract = transformExprsInOps(zoneObj.onInteract, transformExpr))
        is LeverObjectIR ->
            zoneObj.copy(
                onInteract = transformExprsInOps(zoneObj.onInteract, transformExpr),
                onActivate = transformExprsInOps(zoneObj.onActivate, transformExpr),
                onDeactivate = transformExprsInOps(zoneObj.onDeactivate, transformExpr),
            )
    }

/** Applies [transformExpr] to all expression fields in a single [puzzleObj]. */
@Suppress("LongMethod") // Pattern match over all PuzzleObjectIR subtypes — by design
internal fun transformExprsInPuzzleObject(
    puzzleObj: PuzzleObjectIR,
    transformExpr: (Expr) -> Expr,
): PuzzleObjectIR {
    fun transformHandlers(handlers: List<PuzzleEventHandler>): List<PuzzleEventHandler> =
        handlers.map {
            it.copy(actions = transformExprsInOps(it.actions, transformExpr))
        }

    return when (puzzleObj) {
        is SwitchObjectIR ->
            puzzleObj.copy(
                onActivate = transformExprsInOps(puzzleObj.onActivate, transformExpr),
                onDeactivate = transformExprsInOps(puzzleObj.onDeactivate, transformExpr),
                handlers = transformHandlers(puzzleObj.handlers),
            )
        is DoorObjectIR ->
            puzzleObj.copy(
                onOpen = transformExprsInOps(puzzleObj.onOpen, transformExpr),
                onClose = transformExprsInOps(puzzleObj.onClose, transformExpr),
                handlers = transformHandlers(puzzleObj.handlers),
            )
        is PressurePlateObjectIR ->
            puzzleObj.copy(
                onStepOn = transformExprsInOps(puzzleObj.onStepOn, transformExpr),
                onStepOff = transformExprsInOps(puzzleObj.onStepOff, transformExpr),
                handlers = transformHandlers(puzzleObj.handlers),
            )
        is TimedBlockObjectIR -> puzzleObj.copy(handlers = transformHandlers(puzzleObj.handlers))
        is TriggerObjectIR -> puzzleObj.copy(handlers = transformHandlers(puzzleObj.handlers))
    }
}

// ---------------------------------------------------------------------------
// ScriptOp flattening (collecting all ops recursively)
// ---------------------------------------------------------------------------

/** Recursively collects all [ScriptOp] instances (including nested ones) from a list. */
internal fun collectAllOps(ops: List<ScriptOp>): List<ScriptOp> {
    val result = mutableListOf<ScriptOp>()
    for (op in ops) {
        result += op
        forEachNestedOpList(op) { nested -> result += collectAllOps(nested) }
    }
    return result
}
