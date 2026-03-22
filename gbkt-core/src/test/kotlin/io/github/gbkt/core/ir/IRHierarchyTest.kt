/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.*

/**
 * Tests for the IR hierarchy contract.
 *
 * Verifies that GameIR, SceneIR, ActorIR, and SystemIR are properly structured with correct fields
 * and nullable platform annotations. Also proves that exhaustive `when` matching compiles without
 * `else` branches.
 */
class IRHierarchyTest {

    @Test
    fun `GameIR can be constructed with all fields`() {
        val scene = SceneIR(id = "main")
        val actor = ActorIR(id = "player", position = PositionDef(80, 72))
        val system = CameraSystem(id = "camera")
        val variable = VariableDef(name = "score", type = VarType.U16)
        val asset = AssetRef(path = "player.png", type = AssetType.SPRITE)
        val config = CartridgeConfig()

        val game =
            GameIR(
                name = "TestGame",
                config = config,
                scenes = listOf(scene),
                actors = listOf(actor),
                systems = listOf(system),
                variables = listOf(variable),
                assets = listOf(asset),
                startScene = "main",
            )

        assertEquals("TestGame", game.name)
        assertEquals(1, game.scenes.size)
        assertEquals(1, game.actors.size)
        assertEquals(1, game.systems.size)
        assertEquals(1, game.variables.size)
        assertEquals(1, game.assets.size)
        assertEquals("main", game.startScene)
    }

    @Test
    fun `GameIR startScene is nullable and defaults to null`() {
        val game = GameIR(name = "TestGame")
        assertNull(game.startScene)
    }

    @Test
    fun `GameIR has default empty collections`() {
        val game = GameIR(name = "MinimalGame")
        assertEquals(emptyList(), game.scenes)
        assertEquals(emptyList(), game.actors)
        assertEquals(emptyList(), game.systems)
        assertEquals(emptyList(), game.variables)
        assertEquals(emptyList(), game.assets)
    }

    @Test
    fun `SceneIR has correct fields`() {
        val op = Assign(target = "score", value = Literal(0))
        val tileset = AssetRef(path = "dungeon.png", type = AssetType.TILESET)
        val scene =
            SceneIR(
                id = "gameplay",
                enterOps = listOf(op),
                frameOps = listOf(op),
                exitOps = listOf(op),
                actorIds = listOf("player", "enemy"),
                tilesetRef = tileset,
            )

        assertEquals("gameplay", scene.id)
        assertEquals(1, scene.enterOps.size)
        assertEquals(1, scene.frameOps.size)
        assertEquals(1, scene.exitOps.size)
        assertEquals(listOf("player", "enemy"), scene.actorIds)
        assertNotNull(scene.tilesetRef)
        assertEquals("dungeon.png", scene.tilesetRef?.path)
        assertEquals(AssetType.TILESET, scene.tilesetRef?.type)
    }

    @Test
    fun `SceneIR has default empty collections`() {
        val scene = SceneIR(id = "empty")
        assertEquals(emptyList(), scene.enterOps)
        assertEquals(emptyList(), scene.frameOps)
        assertEquals(emptyList(), scene.exitOps)
        assertEquals(emptyList(), scene.actorIds)
        assertNull(scene.tilesetRef)
    }

    @Test
    fun `ActorIR has correct fields including nullable sprite and hitbox`() {
        val sprite =
            SpriteDef(assetRef = AssetRef("player.png", AssetType.SPRITE), size = SizeDef(8, 16))
        val hitbox = HitboxDef(0, 0, 8, 16)

        val actor =
            ActorIR(id = "player", position = PositionDef(80, 72), sprite = sprite, hitbox = hitbox)

        assertEquals("player", actor.id)
        assertEquals(80, actor.position.x)
        assertEquals(72, actor.position.y)
        assertNotNull(actor.sprite)
        assertNotNull(actor.hitbox)
    }

    @Test
    fun `ActorIR platform annotations default to null`() {
        val actor = ActorIR(id = "player", position = PositionDef(0, 0))
        assertNull(actor.bankSlot)
        assertNull(actor.vramRange)
        assertNull(actor.oamSlot)
    }

    @Test
    fun `ActorIR platform annotations can be set via copy`() {
        val actor = ActorIR(id = "player", position = PositionDef(0, 0))
        val annotated =
            actor.copy(
                bankSlot = BankSlot(bank = 3),
                vramRange = VRAMRange(startTile = 0, endTile = 15),
                oamSlot = OAMSlot(slot = 0),
            )

        assertEquals(3, annotated.bankSlot?.bank)
        assertEquals(0, annotated.vramRange?.startTile)
        assertEquals(15, annotated.vramRange?.endTile)
        assertEquals(0, annotated.oamSlot?.slot)
    }

    @Test
    fun `SystemIR sealed interface has required subtypes`() {
        val dialog: SystemIR = DialogSystem(id = "dialog")
        val sound: SystemIR = SoundSystem(id = "sound")
        val save: SystemIR = SaveSystem(id = "save")
        val exploration: SystemIR = ExplorationSystem(id = "exploration")
        val camera: SystemIR = CameraSystem(id = "camera")

        assertEquals("dialog", dialog.id)
        assertEquals("sound", sound.id)
        assertEquals("save", save.id)
        assertEquals("exploration", exploration.id)
        assertEquals("camera", camera.id)
    }

    @Test
    fun `SystemIR platform annotations default to null`() {
        val systems: List<SystemIR> =
            listOf(
                DialogSystem(id = "d"),
                SoundSystem(id = "s"),
                SaveSystem(id = "sv"),
                ExplorationSystem(id = "e"),
                CameraSystem(id = "c"),
            )
        for (sys in systems) {
            assertNull(sys.bankSlot, "bankSlot should be null for ${sys.id}")
            assertNull(sys.vramRange, "vramRange should be null for ${sys.id}")
            assertNull(sys.oamSlot, "oamSlot should be null for ${sys.id}")
        }
    }

    @Test
    fun `visitor dispatch on ScriptOp routes to correct visit method`() {
        // Visitor pattern replaces exhaustive when — accept() dispatches to the correct visit
        // method
        val op: ScriptOp = Assign(target = "x", value = Literal(1))
        val description = describeScriptOp(op)
        assertTrue(description.isNotEmpty())
        assertEquals("Assign(x)", description)
    }

    @Test
    fun `visitor dispatch on Expr routes to correct visit method`() {
        val expr: Expr = Literal(42)
        val description = describeExpr(expr)
        assertTrue(description.isNotEmpty())
        assertEquals("Literal(42)", description)
    }

    @Test
    fun `visitor dispatch on SystemIR routes to correct visit method`() {
        val sys: SystemIR = CameraSystem(id = "cam")
        val description = describeSystem(sys)
        assertTrue(description.isNotEmpty())
        assertEquals("CameraSystem", description)
    }
}

/** Visitor-based dispatch on ScriptOp. Uses accept() to route to the correct visit method. */
fun describeScriptOp(op: ScriptOp): String = op.accept(ScriptOpDescriber)

private object ScriptOpDescriber : ScriptOpVisitorI<String> {
    override fun visitAssign(op: Assign): String = "Assign(${op.target})"

    override fun visitArrayAssign(op: ArrayAssign): String = "ArrayAssign(${op.array})"

    override fun visitIfOp(op: IfOp): String = "IfOp"

    override fun visitWhileOp(op: WhileOp): String = "WhileOp"

    override fun visitForOp(op: ForOp): String = "ForOp"

    override fun visitSetPosition(op: SetPosition): String = "SetPosition(${op.actorId})"

    override fun visitMoveBy(op: MoveBy): String = "MoveBy(${op.actorId})"

    override fun visitNavigateTo(op: NavigateTo): String = "NavigateTo(${op.sceneId})"

    override fun visitTriggerSystem(op: TriggerSystem): String = "TriggerSystem(${op.systemId})"

    override fun visitPlaySound(op: PlaySound): String = "PlaySound(${op.soundId})"

    override fun visitMusicPlay(op: MusicPlay): String = "MusicPlay(${op.songId})"

    override fun visitMusicStop(op: MusicStop): String = "MusicStop"

    override fun visitMusicPause(op: MusicPause): String = "MusicPause"

    override fun visitMusicResume(op: MusicResume): String = "MusicResume"

    override fun visitDialogSay(op: DialogSay): String = "DialogSay(${op.dialogId})"

    override fun visitDialogChoice(op: DialogChoice): String = "DialogChoice(${op.dialogId})"

    override fun visitMenuShow(op: MenuShow): String = "MenuShow(${op.menuId})"

    override fun visitMenuHide(op: MenuHide): String = "MenuHide(${op.menuId})"

    override fun visitHudShow(op: HudShow): String = "HudShow(${op.hudId})"

    override fun visitHudHide(op: HudHide): String = "HudHide(${op.hudId})"

    override fun visitPrintAt(op: PrintAt): String = "PrintAt(${op.x},${op.y})"

    override fun visitPrintCentered(op: PrintCentered): String = "PrintCentered(${op.row})"

    override fun visitPrintAligned(op: PrintAligned): String =
        "PrintAligned(${op.alignment},${op.row})"

    override fun visitClearRegion(op: ClearRegion): String = "ClearRegion"

    override fun visitScreenClear(op: ScreenClear): String = "ScreenClear"

    override fun visitScreenFill(op: ScreenFill): String = "ScreenFill(${op.tile})"

    override fun visitPrintOp(op: PrintOp): String = "PrintOp"

    override fun visitGotoXYOp(op: GotoXYOp): String = "GotoXYOp"

    override fun visitFadeOp(op: FadeOp): String = "FadeOp"

    override fun visitSetVisible(op: SetVisible): String = "SetVisible(${op.actorId})"

    override fun visitSpawnActor(op: SpawnActor): String = "SpawnActor(${op.actorId})"

    override fun visitDestroyActor(op: DestroyActor): String = "DestroyActor(${op.actorId})"

    override fun visitPoolSpawnActor(op: PoolSpawnActor): String =
        "PoolSpawnActor(pool=${op.poolId})"

    override fun visitPoolDestroyActor(op: PoolDestroyActor): String =
        "PoolDestroyActor(pool=${op.poolId})"

    override fun visitPoolForEachActive(op: PoolForEachActive): String =
        "PoolForEachActive(pool=${op.poolId})"

    override fun visitPoolDestroyAll(op: PoolDestroyAll): String =
        "PoolDestroyAll(pool=${op.poolId})"

    override fun visitAnimateOp(op: AnimateOp): String = "AnimateOp(${op.actorId})"

    override fun visitCameraOp(op: CameraOp): String = "CameraOp"

    override fun visitWaitFrames(op: WaitFrames): String = "WaitFrames(${op.frames})"

    override fun visitCallOp(op: CallOp): String = "CallOp(${op.function})"

    override fun visitReturnOp(op: ReturnOp): String = "ReturnOp"

    override fun visitMathOp(op: MathOp): String = "MathOp(${op.result})"

    override fun visitRawOp(op: RawOp): String = "RawOp"

    override fun visitSetAnimationState(op: SetAnimationState): String =
        "SetAnimationState(${op.actorId})"

    override fun visitPhysicsStep(op: PhysicsStep): String = "PhysicsStep(${op.actorId})"

    override fun visitPathfindStep(op: PathfindStep): String =
        "PathfindStep(${op.npcActorId} -> ${op.targetActorId})"

    override fun visitWaypointStep(op: WaypointStep): String = "WaypointStep(${op.npcActorId})"

    override fun visitSetPalette(op: SetPalette): String = "SetPalette(${op.paletteName})"

    override fun visitActivatePuzzleObject(op: ActivatePuzzleObject): String =
        "ActivatePuzzleObject(${op.objectId})"

    override fun visitDeactivatePuzzleObject(op: DeactivatePuzzleObject): String =
        "DeactivatePuzzleObject(${op.objectId})"

    override fun visitRevealPuzzleObject(op: RevealPuzzleObject): String =
        "RevealPuzzleObject(${op.objectId})"

    override fun visitHidePuzzleObject(op: HidePuzzleObject): String =
        "HidePuzzleObject(${op.objectId})"
}

/** Visitor-based dispatch on Expr. Uses accept() to route to the correct visit method. */
fun describeExpr(expr: Expr): String = expr.accept(ExprDescriber)

private object ExprDescriber : ExprVisitorI<String> {
    override fun visitLiteral(expr: Literal): String = "Literal(${expr.value})"

    override fun visitStringLiteral(expr: StringLiteral): String = "StringLiteral(${expr.value})"

    override fun visitVarRef(expr: VarRef): String = "VarRef(${expr.name})"

    override fun visitBinaryExpr(expr: BinaryExpr): String = "BinaryExpr"

    override fun visitUnaryExpr(expr: UnaryExpr): String = "UnaryExpr"

    override fun visitCallExpr(expr: CallExpr): String = "CallExpr(${expr.function})"

    override fun visitTernaryExpr(expr: TernaryExpr): String = "TernaryExpr"

    override fun visitArrayAccessExpr(expr: ArrayAccessExpr): String =
        "ArrayAccessExpr(${expr.array})"

    override fun visitPropertyAccessExpr(expr: PropertyAccessExpr): String =
        "PropertyAccessExpr(${expr.objectId}.${expr.property})"

    override fun visitCast(expr: CastExpr): String = "CastExpr(${expr.targetType})"
}

/** Visitor-based dispatch on SystemIR. Uses accept() to route to the correct visit method. */
fun describeSystem(sys: SystemIR): String = sys.accept(SystemDescriber)

private object SystemDescriber : SystemIRVisitorI<String> {
    override fun visitDialogSystem(system: DialogSystem): String = "DialogSystem"

    override fun visitSoundSystem(system: SoundSystem): String = "SoundSystem"

    override fun visitSaveSystem(system: SaveSystem): String = "SaveSystem"

    override fun visitExplorationSystem(system: ExplorationSystem): String = "ExplorationSystem"

    override fun visitCameraSystem(system: CameraSystem): String = "CameraSystem"

    override fun visitGenericSystem(system: GenericSystem): String = "GenericSystem(${system.id})"

    override fun visitPathfindingSystem(system: PathfindingSystem): String =
        "PathfindingSystem(${system.id})"

    override fun visitCombatEngineSystem(system: CombatEngineSystem): String = "CombatEngineSystem"
}
