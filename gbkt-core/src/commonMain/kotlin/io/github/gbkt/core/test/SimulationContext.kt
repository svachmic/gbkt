/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

import io.github.gbkt.core.Game
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.IRAnimationPause
import io.github.gbkt.core.ir.IRAnimationPlay
import io.github.gbkt.core.ir.IRAnimationQueue
import io.github.gbkt.core.ir.IRAnimationResume
import io.github.gbkt.core.ir.IRAnimationSetFrame
import io.github.gbkt.core.ir.IRAnimationSetSpeed
import io.github.gbkt.core.ir.IRAnimationStop
import io.github.gbkt.core.ir.IRArrayAccess
import io.github.gbkt.core.ir.IRAssign
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRCall
import io.github.gbkt.core.ir.IRCallExpr
import io.github.gbkt.core.ir.IRCameraFollow
import io.github.gbkt.core.ir.IRCameraSetBounds
import io.github.gbkt.core.ir.IRCameraSetPosition
import io.github.gbkt.core.ir.IRCameraShake
import io.github.gbkt.core.ir.IRCameraShakeStop
import io.github.gbkt.core.ir.IRCameraSnapTo
import io.github.gbkt.core.ir.IRCameraStopFollow
import io.github.gbkt.core.ir.IRCameraUpdate
import io.github.gbkt.core.ir.IRCameraX
import io.github.gbkt.core.ir.IRCameraY
import io.github.gbkt.core.ir.IRClearScreen
import io.github.gbkt.core.ir.IRComposedTransition
import io.github.gbkt.core.ir.IRDialogChoice
import io.github.gbkt.core.ir.IRDialogHide
import io.github.gbkt.core.ir.IRDialogIsActive
import io.github.gbkt.core.ir.IRDialogIsComplete
import io.github.gbkt.core.ir.IRDialogSay
import io.github.gbkt.core.ir.IRDialogShow
import io.github.gbkt.core.ir.IRDialogTick
import io.github.gbkt.core.ir.IREntityUpdate
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRFor
import io.github.gbkt.core.ir.IRIf
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRMenuCancel
import io.github.gbkt.core.ir.IRMenuCursorX
import io.github.gbkt.core.ir.IRMenuCursorY
import io.github.gbkt.core.ir.IRMenuHide
import io.github.gbkt.core.ir.IRMenuIsActive
import io.github.gbkt.core.ir.IRMenuIsVisible
import io.github.gbkt.core.ir.IRMenuMoveTo
import io.github.gbkt.core.ir.IRMenuOpen
import io.github.gbkt.core.ir.IRMenuSelect
import io.github.gbkt.core.ir.IRMenuSelectedIndex
import io.github.gbkt.core.ir.IRMenuShow
import io.github.gbkt.core.ir.IRMenuTick
import io.github.gbkt.core.ir.IRMenuToggle
import io.github.gbkt.core.ir.IRPaletteApply
import io.github.gbkt.core.ir.IRPaletteFade
import io.github.gbkt.core.ir.IRPaletteFlash
import io.github.gbkt.core.ir.IRPaletteSetColor
import io.github.gbkt.core.ir.IRPoolActiveCount
import io.github.gbkt.core.ir.IRPoolDespawn
import io.github.gbkt.core.ir.IRPoolDespawnAll
import io.github.gbkt.core.ir.IRPoolDespawnWhere
import io.github.gbkt.core.ir.IRPoolEntityVar
import io.github.gbkt.core.ir.IRPoolForEach
import io.github.gbkt.core.ir.IRPoolHasSpace
import io.github.gbkt.core.ir.IRPoolIsFull
import io.github.gbkt.core.ir.IRPoolSpawn
import io.github.gbkt.core.ir.IRPoolSpawnAt
import io.github.gbkt.core.ir.IRPoolTrySpawn
import io.github.gbkt.core.ir.IRPoolUpdate
import io.github.gbkt.core.ir.IRPrintAt
import io.github.gbkt.core.ir.IRRaw
import io.github.gbkt.core.ir.IRSaveArrayAccess
import io.github.gbkt.core.ir.IRSaveArrayWrite
import io.github.gbkt.core.ir.IRSaveCopy
import io.github.gbkt.core.ir.IRSaveErase
import io.github.gbkt.core.ir.IRSaveExists
import io.github.gbkt.core.ir.IRSaveFieldRead
import io.github.gbkt.core.ir.IRSaveFieldWrite
import io.github.gbkt.core.ir.IRSaveLoad
import io.github.gbkt.core.ir.IRSaveSave
import io.github.gbkt.core.ir.IRSceneChange
import io.github.gbkt.core.ir.IRShowBackground
import io.github.gbkt.core.ir.IRShowSprites
import io.github.gbkt.core.ir.IRSpriteSetPalette
import io.github.gbkt.core.ir.IRStateMachineGoto
import io.github.gbkt.core.ir.IRStateMachineStart
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRTernary
import io.github.gbkt.core.ir.IRTransitionActive
import io.github.gbkt.core.ir.IRTransitionCancel
import io.github.gbkt.core.ir.IRTransitionFadeIn
import io.github.gbkt.core.ir.IRTransitionFadeOut
import io.github.gbkt.core.ir.IRTransitionFlash
import io.github.gbkt.core.ir.IRTransitionIris
import io.github.gbkt.core.ir.IRTransitionWipe
import io.github.gbkt.core.ir.IRUnary
import io.github.gbkt.core.ir.IRVar
import io.github.gbkt.core.ir.IRWhen
import io.github.gbkt.core.ir.IRWhile
import io.github.gbkt.core.ir.UnaryOp
import io.github.gbkt.core.ir.i8
import io.github.gbkt.core.ir.u16
import io.github.gbkt.core.ir.u8

/**
 * The heart of the testing framework - executes IR nodes in-memory without ROM/emulator.
 *
 * Maintains game state (variables, scene, sprites, entities, pools) and provides methods to advance
 * time frame-by-frame while executing game logic.
 */
class SimulationContext(val game: Game) {

    // Variable state - all game variables stored by name
    private val variables = mutableMapOf<String, SimValue>()

    // Scene tracking
    private var _currentScene: String = game.startScene
    val currentScene: String
        get() = _currentScene

    private var sceneJustChanged = false

    // Frame tracking
    private var _frameCount = 0
    val frameCount: Int
        get() = _frameCount

    // Input state - set by MockInputProvider before each frame
    var joypad: Int = 0
    var joypadPrev: Int = 0

    // Sprite simulation state
    private val sprites = mutableMapOf<String, SimSprite>()

    // Pool simulation state
    private val pools = mutableMapOf<String, SimPool>()

    // Camera state
    private var cameraX: SimValue = SimValue.ZERO
    private var cameraY: SimValue = SimValue.ZERO

    // Transition state
    private var transitionActive = false

    // Scene change callback for testing
    var onSceneChange: ((from: String, to: String) -> Unit)? = null

    init {
        initializeVariables()
        initializeSprites()
        initializePools()
        executeSceneEnter()
    }

    private fun initializeVariables() {
        // Initialize all game variables with their default values
        for (variable in game.variables) {
            val initialValue =
                when (val v = variable.value) {
                    is u8 -> SimValue.of(v.raw)
                    is u16 -> SimValue.of(v.raw)
                    is i8 -> SimValue.of(v.raw)
                    is Int -> SimValue.of(v)
                    is Byte -> SimValue(v.toLong())
                    is Short -> SimValue(v.toLong())
                    is UByte -> SimValue(v.toLong())
                    is UShort -> SimValue(v.toLong())
                    else -> SimValue.ZERO
                }
            variables[variable.name] = initialValue
        }

        // Initialize special variables
        variables["_frame_count"] = SimValue.ZERO
        variables["_joypad"] = SimValue.ZERO
        variables["_joypad_prev"] = SimValue.ZERO
    }

    private fun initializeSprites() {
        for (sprite in game.sprites) {
            val position = sprite.position
            if (position != null) {
                sprites[sprite.name] =
                    SimSprite(
                        name = sprite.name,
                        x = position.initialX,
                        y = position.initialY,
                        visible = true,
                        currentAnimation = null,
                    )

                // Register sprite position variables
                variables[position.xVarName] = SimValue.of(position.initialX)
                variables[position.yVarName] = SimValue.of(position.initialY)
            } else {
                sprites[sprite.name] =
                    SimSprite(
                        name = sprite.name,
                        x = 0,
                        y = 0,
                        visible = true,
                        currentAnimation = null,
                    )
            }
        }
    }

    private fun initializePools() {
        for (pool in game.pools) {
            pools[pool.name] =
                SimPool(
                    name = pool.name,
                    size = pool.size,
                    hasPosition = pool.hasPosition,
                    hasVelocity = pool.hasVelocity,
                    stateFields = pool.stateFields,
                    onFrameStatements = pool.onFrameStatements,
                    despawnConditions = pool.despawnConditions,
                )
        }
    }

    private fun executeSceneEnter() {
        val scene = game.scenes[_currentScene] ?: return
        scene.onEnter.forEach { executeStatement(it) }
    }

    private fun executeSceneExit() {
        val scene = game.scenes[_currentScene] ?: return
        scene.onExit.forEach { executeStatement(it) }
    }

    /**
     * Execute one frame of game logic. Updates input state, runs scene's onFrame statements, and
     * advances frame count.
     */
    fun executeFrame() {
        // Update input variables
        variables["_joypad_prev"] = SimValue.of(joypadPrev)
        variables["_joypad"] = SimValue.of(joypad)

        // Handle scene change from previous frame
        if (sceneJustChanged) {
            sceneJustChanged = false
            executeSceneEnter()
        }

        // Get current scene
        val scene = game.scenes[_currentScene] ?: error("Unknown scene: $_currentScene")

        // Execute frame statements
        scene.onFrame.forEach { executeStatement(it) }

        // Update frame count
        _frameCount++
        variables["_frame_count"] = SimValue.of(_frameCount)
    }

    /** Execute an IR statement in the simulation. */
    fun executeStatement(stmt: IRStatement) {
        when (stmt) {
            is IRAssign -> executeAssign(stmt)
            is IRIf -> executeIf(stmt)
            is IRWhen -> executeWhen(stmt)
            is IRWhile -> executeWhile(stmt)
            is IRFor -> executeFor(stmt)
            is IRCall -> executeCall(stmt)
            is IRSceneChange -> executeSceneChange(stmt)

            // Animation
            is IRAnimationPlay -> executeAnimationPlay(stmt)
            is IRAnimationStop -> executeAnimationStop(stmt)
            is IRAnimationSetFrame -> {
                /* Frame tracking not simulated in detail */
            }
            is IRAnimationPause -> {
                sprites[stmt.spriteName]?.animationPaused = true
            }
            is IRAnimationResume -> {
                sprites[stmt.spriteName]?.animationPaused = false
            }
            is IRAnimationSetSpeed -> {
                /* Speed changes not simulated in detail */
            }
            is IRAnimationQueue -> {
                /* Queue not simulated in detail */
            }

            // Pool operations
            is IRPoolUpdate -> executePoolUpdate(stmt)
            is IRPoolSpawn -> executePoolSpawn(stmt)
            is IRPoolSpawnAt -> executePoolSpawnAt(stmt)
            is IRPoolTrySpawn -> executePoolTrySpawn(stmt)
            is IRPoolDespawn -> executePoolDespawn(stmt)
            is IRPoolDespawnAll -> executePoolDespawnAll(stmt)
            is IRPoolForEach -> executePoolForEach(stmt)
            is IRPoolDespawnWhere -> executePoolDespawnWhere(stmt)

            // Camera
            is IRCameraSetPosition -> {
                cameraX = evaluateExpr(stmt.x)
                cameraY = evaluateExpr(stmt.y)
            }
            is IRCameraUpdate -> {
                /* Camera updates not simulated in detail */
            }
            is IRCameraFollow -> {
                /* Following not simulated in detail */
            }
            is IRCameraStopFollow -> {
                /* Not simulated */
            }
            is IRCameraSnapTo -> {
                cameraX = evaluateExpr(stmt.x)
                cameraY = evaluateExpr(stmt.y)
            }
            is IRCameraSetBounds -> {
                /* Bounds not simulated */
            }
            is IRCameraShake -> {
                /* Shake not simulated visually */
            }
            is IRCameraShakeStop -> {
                /* Not simulated */
            }

            // Transitions
            is IRTransitionFadeOut -> executeTransition(stmt.durationFrames, stmt.onComplete)
            is IRTransitionFadeIn -> executeTransition(stmt.durationFrames, stmt.onComplete)
            is IRTransitionFlash -> {
                /* Flash not simulated visually */
            }
            is IRTransitionWipe -> executeTransition(stmt.durationFrames, stmt.onComplete)
            is IRTransitionIris -> executeTransition(stmt.durationFrames, stmt.onComplete)
            is IRComposedTransition -> {
                transitionActive = true
            }
            is IRTransitionCancel -> {
                transitionActive = false
            }

            // State machine
            is IRStateMachineStart -> {
                /* State machine tracking would go here */
            }
            is IRStateMachineGoto -> {
                /* State machine tracking would go here */
            }

            // Entity
            is IREntityUpdate -> {
                /* Entity updates - velocities etc */
            }

            // Display
            is IRClearScreen -> {
                /* Visual only */
            }
            is IRShowSprites -> {
                /* Visual only */
            }
            is IRShowBackground -> {
                /* Visual only */
            }
            is IRPrintAt -> {
                /* Visual only */
            }

            // Palette
            is IRPaletteApply -> {
                /* Visual only */
            }
            is IRPaletteSetColor -> {
                /* Visual only */
            }
            is IRPaletteFade -> {
                /* Visual only */
            }
            is IRSpriteSetPalette -> {
                /* Visual only */
            }
            is IRPaletteFlash -> {
                /* Visual only */
            }

            // Save system
            is IRSaveLoad -> {
                /* Save system not simulated */
            }
            is IRSaveSave -> {
                /* Save system not simulated */
            }
            is IRSaveErase -> {
                /* Save system not simulated */
            }
            is IRSaveCopy -> {
                /* Save system not simulated */
            }
            is IRSaveFieldWrite -> {
                /* Save system not simulated */
            }
            is IRSaveArrayWrite -> {
                /* Save system not simulated */
            }

            // Raw code - cannot simulate
            is IRRaw -> {
                /* Raw C code cannot be simulated */
            }

            // Dialog system - not simulated
            is IRDialogShow -> {
                /* Dialog not simulated */
            }
            is IRDialogHide -> {
                /* Dialog not simulated */
            }
            is IRDialogSay -> {
                /* Dialog not simulated */
            }
            is IRDialogChoice -> {
                /* Dialog not simulated */
            }
            is IRDialogTick -> {
                /* Dialog not simulated */
            }

            // Menu system - not simulated
            is IRMenuOpen -> {
                /* Menu not simulated */
            }
            is IRMenuShow -> {
                /* Menu not simulated */
            }
            is IRMenuHide -> {
                /* Menu not simulated */
            }
            is IRMenuToggle -> {
                /* Menu not simulated */
            }
            is IRMenuCancel -> {
                /* Menu not simulated */
            }
            is IRMenuSelect -> {
                /* Menu not simulated */
            }
            is IRMenuMoveTo -> {
                /* Menu not simulated */
            }
            is IRMenuTick -> {
                /* Menu not simulated */
            }

            // Catch-all for any other IR nodes added in the future
            else -> {
                /* Unhandled IR statement type */
            }
        }
    }

    private fun executeAssign(stmt: IRAssign) {
        val current = variables[stmt.target] ?: SimValue.ZERO
        val newValue = evaluateExpr(stmt.value)

        val result =
            when (stmt.op) {
                AssignOp.SET -> newValue
                AssignOp.ADD -> current + newValue
                AssignOp.SUB -> current - newValue
                AssignOp.MUL -> current * newValue
                AssignOp.AND -> current and newValue
                AssignOp.OR -> current or newValue
            }

        variables[stmt.target] = result

        // Update sprite positions if this is a sprite position variable
        for (sprite in sprites.values) {
            if (stmt.target.endsWith("_x") && stmt.target.startsWith(sprite.name)) {
                sprite.x = result.toInt()
            } else if (stmt.target.endsWith("_y") && stmt.target.startsWith(sprite.name)) {
                sprite.y = result.toInt()
            }
        }
    }

    private fun executeIf(stmt: IRIf) {
        val conditionResult = evaluateExpr(stmt.condition)
        if (conditionResult.isTrue) {
            stmt.then.forEach { executeStatement(it) }
        } else if (stmt.otherwise != null) {
            stmt.otherwise.forEach { executeStatement(it) }
        }
    }

    private fun executeWhen(stmt: IRWhen) {
        for (branch in stmt.branches) {
            if (evaluateExpr(branch.condition).isTrue) {
                branch.body.forEach { executeStatement(it) }
                return
            }
        }
        stmt.otherwise?.forEach { executeStatement(it) }
    }

    private fun executeWhile(stmt: IRWhile) {
        var iterations = 0
        val maxIterations = 10000 // Prevent infinite loops in tests
        while (evaluateExpr(stmt.condition).isTrue && iterations < maxIterations) {
            stmt.body.forEach { executeStatement(it) }
            iterations++
        }
        if (iterations >= maxIterations) {
            error("While loop exceeded $maxIterations iterations - possible infinite loop")
        }
    }

    private fun executeFor(stmt: IRFor) {
        for (i in stmt.range) {
            variables[stmt.counter] = SimValue.of(i)
            stmt.body.forEach { executeStatement(it) }
        }
    }

    private fun executeCall(stmt: IRCall) {
        // Function calls are generally side-effect-free in Game Boy context
        // They're typically just for rendering which we don't simulate
    }

    private fun executeSceneChange(stmt: IRSceneChange) {
        val oldScene = _currentScene
        executeSceneExit()
        _currentScene = stmt.sceneName
        sceneJustChanged = true
        onSceneChange?.invoke(oldScene, stmt.sceneName)
    }

    private fun executeAnimationPlay(stmt: IRAnimationPlay) {
        sprites[stmt.spriteName]?.let { sprite ->
            sprite.currentAnimation = stmt.animationName
            sprite.animationPaused = false
        }
    }

    private fun executeAnimationStop(stmt: IRAnimationStop) {
        sprites[stmt.spriteName]?.let { sprite ->
            sprite.currentAnimation = null
            sprite.animationPaused = false
        }
    }

    private fun executeTransition(durationFrames: Int, onComplete: List<IRStatement>) {
        transitionActive = true
        // In simulation, transitions complete immediately (we don't wait)
        // Execute onComplete immediately for testing
        onComplete.forEach { executeStatement(it) }
        transitionActive = false
    }

    // Pool operations
    private fun executePoolUpdate(stmt: IRPoolUpdate) {
        pools[stmt.poolName]?.update(this)
    }

    private fun executePoolSpawn(stmt: IRPoolSpawn) {
        pools[stmt.poolName]?.spawn(this, stmt.initStatements)
    }

    private fun executePoolSpawnAt(stmt: IRPoolSpawnAt) {
        val x = evaluateExpr(stmt.x).toInt()
        val y = evaluateExpr(stmt.y).toInt()
        pools[stmt.poolName]?.spawnAt(this, x, y, stmt.initStatements)
    }

    private fun executePoolTrySpawn(stmt: IRPoolTrySpawn) {
        val pool = pools[stmt.poolName]
        if (pool != null && pool.hasSpace) {
            pool.spawn(this, stmt.initStatements)
        } else {
            stmt.elseStatements.forEach { executeStatement(it) }
        }
    }

    private fun executePoolDespawn(stmt: IRPoolDespawn) {
        val index = evaluateExpr(stmt.indexExpr).toInt()
        pools[stmt.poolName]?.despawn(index)
    }

    private fun executePoolDespawnAll(stmt: IRPoolDespawnAll) {
        pools[stmt.poolName]?.despawnAll()
    }

    private fun executePoolForEach(stmt: IRPoolForEach) {
        val pool = pools[stmt.poolName] ?: return
        pool.forEach(this, stmt.indexVar, stmt.bodyStatements)
    }

    private fun executePoolDespawnWhere(stmt: IRPoolDespawnWhere) {
        val pool = pools[stmt.poolName] ?: return
        pool.despawnWhere(this, stmt.indexVar, stmt.condition)
    }

    /** Evaluate an IR expression and return its value. */
    fun evaluateExpr(expr: IRExpression): SimValue =
        when (expr) {
            is IRLiteral -> SimValue.from(expr.value)
            is IRVar -> variables[expr.name] ?: SimValue.ZERO
            is IRBinary -> evaluateBinary(expr)
            is IRUnary -> evaluateUnary(expr)
            is IRTernary -> evaluateTernary(expr)
            is IRCallExpr -> evaluateCallExpr(expr)
            is IRArrayAccess -> evaluateArrayAccess(expr)

            // Pool expressions
            is IRPoolActiveCount -> SimValue.of(pools[expr.poolName]?.activeCount ?: 0)
            is IRPoolHasSpace -> SimValue.of(pools[expr.poolName]?.hasSpace ?: false)
            is IRPoolIsFull -> SimValue.of(pools[expr.poolName]?.isFull ?: true)
            is IRPoolEntityVar -> evaluatePoolEntityVar(expr)

            // Save expressions (not simulated)
            is IRSaveFieldRead -> SimValue.ZERO
            is IRSaveExists -> SimValue.FALSE
            is IRSaveArrayAccess -> SimValue.ZERO

            // Camera expressions
            is IRCameraX -> cameraX
            is IRCameraY -> cameraY
            is IRTransitionActive -> SimValue.of(transitionActive)

            // Dialog expressions - not simulated
            is IRDialogIsActive -> SimValue.FALSE
            is IRDialogIsComplete -> SimValue.TRUE

            // Menu expressions - not simulated
            is IRMenuIsActive -> SimValue.FALSE
            is IRMenuIsVisible -> SimValue.FALSE
            is IRMenuSelectedIndex -> SimValue.ZERO
            is IRMenuCursorX -> SimValue.ZERO
            is IRMenuCursorY -> SimValue.ZERO

            // Catch-all for any other expression types
            else -> SimValue.ZERO
        }

    private fun evaluateBinary(expr: IRBinary): SimValue {
        val left = evaluateExpr(expr.left)
        val right = evaluateExpr(expr.right)

        return when (expr.op) {
            BinaryOp.ADD -> left + right
            BinaryOp.SUB -> left - right
            BinaryOp.MUL -> left * right
            BinaryOp.DIV -> left / right
            BinaryOp.MOD -> left % right
            BinaryOp.AND -> left and right
            BinaryOp.OR -> left or right
            BinaryOp.XOR -> left xor right
            BinaryOp.SHL -> left shl right
            BinaryOp.SHR -> left shr right
            BinaryOp.EQ -> left eq right
            BinaryOp.NEQ -> left neq right
            BinaryOp.LT -> left lt right
            BinaryOp.LTE -> left lte right
            BinaryOp.GT -> left gt right
            BinaryOp.GTE -> left gte right
            BinaryOp.LAND -> left land right
            BinaryOp.LOR -> left lor right
        }
    }

    private fun evaluateUnary(expr: IRUnary): SimValue {
        val operand = evaluateExpr(expr.operand)
        return when (expr.op) {
            UnaryOp.NEG -> -operand
            UnaryOp.NOT -> operand.lnot()
            UnaryOp.BNOT -> operand.inv()
        }
    }

    private fun evaluateTernary(expr: IRTernary): SimValue {
        return if (evaluateExpr(expr.cond).isTrue) {
            evaluateExpr(expr.then)
        } else {
            evaluateExpr(expr.otherwise)
        }
    }

    private fun evaluateCallExpr(expr: IRCallExpr): SimValue {
        return when (expr.function) {
            "joypad" -> SimValue.of(joypad)
            else -> SimValue.ZERO // Unknown functions return 0
        }
    }

    private fun evaluateArrayAccess(expr: IRArrayAccess): SimValue {
        val index = evaluateExpr(expr.index).toInt()
        val varName = "${expr.array}_$index"
        return variables[varName] ?: SimValue.ZERO
    }

    private fun evaluatePoolEntityVar(expr: IRPoolEntityVar): SimValue {
        val pool = pools[expr.poolName] ?: return SimValue.ZERO
        val index = variables[expr.indexVar]?.toInt() ?: return SimValue.ZERO
        return pool.getEntityVar(index, expr.fieldName)
    }

    // Public accessors for testing

    fun getVariable(name: String): SimValue = variables[name] ?: SimValue.ZERO

    fun setVariable(name: String, value: Int) {
        variables[name] = SimValue.of(value)
    }

    fun setVariable(name: String, value: SimValue) {
        variables[name] = value
    }

    fun getSprite(name: String): SimSprite? = sprites[name]

    fun getPool(name: String): SimPool? = pools[name]

    /** Enter a scene directly (for test setup). */
    fun enterScene(sceneName: String) {
        if (_currentScene != sceneName) {
            executeSceneExit()
            val oldScene = _currentScene
            _currentScene = sceneName
            executeSceneEnter()
            onSceneChange?.invoke(oldScene, sceneName)
        }
    }
}
