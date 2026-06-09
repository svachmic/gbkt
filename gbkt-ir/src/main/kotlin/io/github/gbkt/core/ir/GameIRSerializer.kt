/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "LargeClass" // GameIRSerializer handles all IR node types — splitting reduces readability
)

package io.github.gbkt.core.ir

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON serializer and deserializer for [GameIR].
 *
 * Enables external tools (non-JVM frontends, debuggers, visualization tools) to produce and consume
 * GameIR. Output is human-readable with 2-space indentation. Uses the `org.json` library following
 * the established [AssetManifest] pattern.
 *
 * Usage:
 * ```kotlin
 * val json = GameIRSerializer.toJson(game)
 * val back = GameIRSerializer.fromJson(json)
 * assert(game == back)
 * ```
 *
 * Schema versioning: [SCHEMA_VERSION] is written as `schemaVersion` in the root JSON object.
 * Deserializing a JSON with a different version emits a warning to `System.err` but continues.
 *
 * Unknown [ScriptOp] and [Expr] subtypes: serialized with type discriminator and class name;
 * deserialized as [RawOp] / [Literal] placeholders with a warning to `System.err`.
 *
 * **Serialize-only types** (deserialization returns `emptyList()`): [SystemIR], [ZoneIR],
 * [GlobalFlagsIR], [ItemCategoryDef], [ItemDef], [ContainerIR], [DropTableIR], [PuzzleObjectIR],
 * [CollisionGroupIR], [CollisionRuleIR].
 *
 * Full round-trip fidelity is guaranteed for all other types: scenes, actors, variables, arrays,
 * collections, dialogs, menus, HUDs, music, actor pools, assets, palettes, sound effects, structs,
 * cartridge config, and all [ScriptOp]/[Expr] subtypes.
 */
object GameIRSerializer {

    /** Current JSON schema version. Increment when the serialized format changes incompatibly. */
    const val SCHEMA_VERSION = 1

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Serialize a [GameIR] to a JSON string with 2-space indentation.
     *
     * @param game The game IR to serialize.
     * @return Human-readable JSON string.
     */
    fun toJson(game: GameIR): String = serializeGameIR(game).toString(2)

    /**
     * Deserialize a [GameIR] from a JSON string previously produced by [toJson].
     *
     * Warns on schema version mismatch but attempts to parse.
     *
     * @param json JSON string to deserialize.
     * @return Reconstructed [GameIR].
     */
    fun fromJson(json: String): GameIR {
        val root = JSONObject(json)
        val version = root.optInt("schemaVersion", 0)
        if (version != SCHEMA_VERSION) {
            System.err.println(
                "WARNING: IR JSON schema version $version differs from expected $SCHEMA_VERSION; attempting parse"
            )
        }
        return deserializeGameIR(root)
    }

    // =========================================================================
    // GameIR — top-level serialization
    // =========================================================================

    private fun serializeGameIR(game: GameIR): JSONObject {
        val json = JSONObject()
        json.put("schemaVersion", SCHEMA_VERSION)
        json.put("name", game.name)
        json.put("startScene", game.startScene ?: JSONObject.NULL)
        json.put("config", serializeCartridgeConfig(game.config))
        json.put("sourceLocation", serializeSourceLocation(game.sourceLocation))
        json.put("scenes", serializeList(game.scenes, ::serializeScene))
        json.put("actors", serializeList(game.actors, ::serializeActor))
        json.put("metasprites", serializeList(game.metasprites, ::serializeMetaspriteIR))
        json.put("variables", serializeList(game.variables, ::serializeVariableDef))
        json.put("arrays", serializeList(game.arrays, ::serializeArrayDef))
        json.put("assets", serializeList(game.assets, ::serializeAssetRef))
        json.put("palettes", serializeList(game.palettes, ::serializeGBCPalette))
        json.put("soundEffects", serializeList(game.soundEffects, ::serializeSoundEffectDef))
        json.put("structs", serializeList(game.structs, ::serializeStructDef))
        json.put("hashTables", serializeList(game.hashTables, ::serializeHashTable))
        json.put("pools", serializeList(game.pools, ::serializePool))
        json.put("ringBuffers", serializeList(game.ringBuffers, ::serializeRingBuffer))
        json.put("fixedSlots", serializeList(game.fixedSlots, ::serializeFixedSlots))
        json.put("dialogs", serializeList(game.dialogs, ::serializeDialogDef))
        json.put("menus", serializeList(game.menus, ::serializeMenuDef))
        json.put("huds", serializeList(game.huds, ::serializeHudDef))
        json.put("musicDefs", serializeList(game.musicDefs, ::serializeMusicDef))
        json.put("actorPools", serializeList(game.actorPools, ::serializeActorPoolIR))
        // Simplified domain-specific types — structure preserved, nested detail may be incomplete
        json.put("systems", serializeList(game.systems) { serializeSystemIR(it) })
        json.put("zones", serializeList(game.zones) { serializeZoneIR(it) })
        json.put("flags", serializeList(game.flags) { serializeSimple("GlobalFlagsIR", it.id) })
        json.put(
            "itemCategories",
            serializeList(game.itemCategories) { serializeSimple("ItemCategoryDef", it.id) },
        )
        json.put("items", serializeList(game.items) { serializeSimple("ItemDef", it.id) })
        json.put(
            "containers",
            serializeList(game.containers) { serializeSimple("ContainerIR", it.id) },
        )
        json.put(
            "dropTables",
            serializeList(game.dropTables) { serializeSimple("DropTableIR", it.id) },
        )
        json.put(
            "puzzleObjects",
            serializeList(game.puzzleObjects) { serializeSimple("PuzzleObjectIR", it.id) },
        )
        json.put(
            "collisionGroups",
            serializeList(game.collisionGroups) { serializeSimple("CollisionGroupIR", it.id) },
        )
        json.put(
            "collisionRules",
            serializeList(game.collisionRules) { serializeCollisionRule(it) },
        )
        return json
    }

    private fun deserializeGameIR(json: JSONObject): GameIR {
        return GameIR(
            name = json.getString("name"),
            startScene = json.optString("startScene", null).takeIf { it != "null" && it != "" },
            config =
                json.optJSONObject("config")?.let { deserializeCartridgeConfig(it) }
                    ?: CartridgeConfig(),
            sourceLocation =
                json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
            scenes = deserializeList(json.optJSONArray("scenes")) { deserializeScene(it) },
            actors = deserializeList(json.optJSONArray("actors")) { deserializeActor(it) },
            metasprites =
                deserializeList(json.optJSONArray("metasprites")) { deserializeMetaspriteIR(it) },
            variables =
                deserializeList(json.optJSONArray("variables")) { deserializeVariableDef(it) },
            arrays = deserializeList(json.optJSONArray("arrays")) { deserializeArrayDef(it) },
            assets = deserializeList(json.optJSONArray("assets")) { deserializeAssetRef(it) },
            palettes = deserializeList(json.optJSONArray("palettes")) { deserializeGBCPalette(it) },
            soundEffects =
                deserializeList(json.optJSONArray("soundEffects")) {
                    deserializeSoundEffectDef(it)
                },
            structs = deserializeList(json.optJSONArray("structs")) { deserializeStructDef(it) },
            hashTables =
                deserializeList(json.optJSONArray("hashTables")) { deserializeHashTable(it) },
            pools = deserializeList(json.optJSONArray("pools")) { deserializePool(it) },
            ringBuffers =
                deserializeList(json.optJSONArray("ringBuffers")) { deserializeRingBuffer(it) },
            fixedSlots =
                deserializeList(json.optJSONArray("fixedSlots")) { deserializeFixedSlots(it) },
            dialogs = deserializeList(json.optJSONArray("dialogs")) { deserializeDialogDef(it) },
            menus = deserializeList(json.optJSONArray("menus")) { deserializeMenuDef(it) },
            huds = deserializeList(json.optJSONArray("huds")) { deserializeHudDef(it) },
            musicDefs = deserializeList(json.optJSONArray("musicDefs")) { deserializeMusicDef(it) },
            actorPools =
                deserializeList(json.optJSONArray("actorPools")) { deserializeActorPoolIR(it) },
            // Domain-specific: simplified deserialization — not full round-trip
            systems = emptyList(), // TODO: SystemIR deserialization
            zones = emptyList(), // TODO: ZoneIR full deserialization
            flags = emptyList(), // TODO: GlobalFlagsIR full deserialization
            itemCategories = emptyList(), // TODO: ItemCategoryDef full deserialization
            items = emptyList(), // TODO: ItemDef full deserialization
            containers = emptyList(), // TODO: ContainerIR full deserialization
            dropTables = emptyList(), // TODO: DropTableIR full deserialization
            puzzleObjects = emptyList(), // TODO: PuzzleObjectIR full deserialization
            collisionGroups = emptyList(), // TODO: CollisionGroupIR full deserialization
            collisionRules = emptyList(), // TODO: CollisionRuleIR full deserialization
        )
    }

    // =========================================================================
    // ZoneIR serialization (serialize-only; deserialization returns emptyList())
    // =========================================================================

    /**
     * Serialize a [ZoneIR] to a JSON object.
     *
     * Only the minimal fields required for external-tool consumption are emitted. The
     * [ZoneIR.screenMode] flag is serialized here so tools can distinguish synthetic `screen()`
     * zones from user-authored `zone { }` zones. Deserialization of ZoneIR is not supported
     * (emptyList() in [deserializeGameIR]); the `optBoolean("screenMode", false)` read style is
     * documented here for future full-deserialization support — default false preserves
     * backward-compat for serialized IR produced before this field existed (Assumption A3).
     */
    private fun serializeZoneIR(zone: ZoneIR): JSONObject {
        val json = JSONObject()
        json.put("type", "ZoneIR")
        json.put("id", zone.id)
        json.put("tilesetPath", zone.tilesetPath ?: JSONObject.NULL)
        // screenMode: default false → optBoolean("screenMode", false) round-trips correctly
        // for IR produced before this field was added (A3 backward-compat).
        json.put("screenMode", zone.screenMode)
        return json
    }

    // =========================================================================
    // SceneIR serialization
    // =========================================================================

    private fun serializeScene(scene: SceneIR): JSONObject {
        val json = JSONObject()
        json.put("id", scene.id)
        json.put("enterOps", serializeList(scene.enterOps, ::serializeOp))
        json.put("frameOps", serializeList(scene.frameOps, ::serializeOp))
        json.put("exitOps", serializeList(scene.exitOps, ::serializeOp))
        json.put("actorIds", JSONArray(scene.actorIds))
        json.put("tilesetRef", scene.tilesetRef?.let { serializeAssetRef(it) } ?: JSONObject.NULL)
        json.put(
            "collisionData",
            scene.collisionData?.let { JSONArray(it.map { b -> b.toInt() }) } ?: JSONObject.NULL,
        )
        json.put("mapWidth", scene.mapWidth ?: JSONObject.NULL)
        json.put("sourceLocation", serializeSourceLocation(scene.sourceLocation))
        json.put("bankSlot", scene.bankSlot?.let { serializeBankSlot(it) } ?: JSONObject.NULL)
        json.put("vramRange", scene.vramRange?.let { serializeVRAMRange(it) } ?: JSONObject.NULL)
        json.put("oamSlot", scene.oamSlot?.let { serializeOAMSlot(it) } ?: JSONObject.NULL)
        return json
    }

    private fun deserializeScene(json: JSONObject): SceneIR {
        val collisionDataArray = json.optJSONArray("collisionData")
        val collisionData =
            if (collisionDataArray != null) {
                ByteArray(collisionDataArray.length()) { i ->
                    collisionDataArray.getInt(i).toByte()
                }
            } else {
                null
            }
        return SceneIR(
            id = json.getString("id"),
            enterOps = deserializeList(json.optJSONArray("enterOps")) { deserializeOp(it) },
            frameOps = deserializeList(json.optJSONArray("frameOps")) { deserializeOp(it) },
            exitOps = deserializeList(json.optJSONArray("exitOps")) { deserializeOp(it) },
            actorIds = deserializeStringList(json.optJSONArray("actorIds")),
            tilesetRef = json.optJSONObject("tilesetRef")?.let { deserializeAssetRef(it) },
            collisionData = collisionData,
            mapWidth = json.optInt("mapWidth", -1).takeIf { it >= 0 },
            sourceLocation =
                json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
            bankSlot = json.optJSONObject("bankSlot")?.let { deserializeBankSlot(it) },
            vramRange = json.optJSONObject("vramRange")?.let { deserializeVRAMRange(it) },
            oamSlot = json.optJSONObject("oamSlot")?.let { deserializeOAMSlot(it) },
        )
    }

    // =========================================================================
    // ActorIR serialization
    // =========================================================================

    private fun serializeActor(actor: ActorIR): JSONObject {
        val json = JSONObject()
        json.put("id", actor.id)
        json.put("position", serializePosition(actor.position))
        json.put("sprite", actor.sprite?.let { serializeSpriteDef(it) } ?: JSONObject.NULL)
        json.put("hitbox", actor.hitbox?.let { serializeHitbox(it) } ?: JSONObject.NULL)
        json.put("sourceLocation", serializeSourceLocation(actor.sourceLocation))
        json.put("bankSlot", actor.bankSlot?.let { serializeBankSlot(it) } ?: JSONObject.NULL)
        json.put("vramRange", actor.vramRange?.let { serializeVRAMRange(it) } ?: JSONObject.NULL)
        json.put("oamSlot", actor.oamSlot?.let { serializeOAMSlot(it) } ?: JSONObject.NULL)
        json.put(
            "movementConfig",
            actor.movementConfig?.let { serializeMovementConfig(it) } ?: JSONObject.NULL,
        )
        json.put(
            "animationStates",
            serializeList(actor.animationStates, ::serializeAnimationStateDef),
        )
        json.put("frameSpeed", actor.frameSpeed ?: JSONObject.NULL)
        json.put(
            "physicsConfig",
            actor.physicsConfig?.let { serializePhysicsConfig(it) } ?: JSONObject.NULL,
        )
        json.put(
            "waypointRoute",
            actor.waypointRoute?.let { serializeWaypointRoute(it) } ?: JSONObject.NULL,
        )
        json.put("followTargetId", actor.followTargetId ?: JSONObject.NULL)
        json.put("palette", actor.palette?.let { serializeGBCPalette(it) } ?: JSONObject.NULL)
        return json
    }

    private fun deserializeActor(json: JSONObject): ActorIR {
        return ActorIR(
            id = json.getString("id"),
            position = deserializePosition(json.getJSONObject("position")),
            sprite = json.optJSONObject("sprite")?.let { deserializeSpriteDef(it) },
            hitbox = json.optJSONObject("hitbox")?.let { deserializeHitbox(it) },
            sourceLocation =
                json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
            bankSlot = json.optJSONObject("bankSlot")?.let { deserializeBankSlot(it) },
            vramRange = json.optJSONObject("vramRange")?.let { deserializeVRAMRange(it) },
            oamSlot = json.optJSONObject("oamSlot")?.let { deserializeOAMSlot(it) },
            movementConfig =
                json.optJSONObject("movementConfig")?.let { deserializeMovementConfig(it) },
            animationStates =
                deserializeList(json.optJSONArray("animationStates")) {
                    deserializeAnimationStateDef(it)
                },
            frameSpeed = json.optInt("frameSpeed", -1).takeIf { it >= 0 },
            physicsConfig =
                json.optJSONObject("physicsConfig")?.let { deserializePhysicsConfig(it) },
            waypointRoute =
                json.optJSONObject("waypointRoute")?.let { deserializeWaypointRoute(it) },
            followTargetId =
                json.optString("followTargetId", null).takeIf { it != "null" && it != "" },
            palette = json.optJSONObject("palette")?.let { deserializeGBCPalette(it) },
        )
    }

    // =========================================================================
    // MetaspriteIR serialization
    // =========================================================================

    private fun serializeMetaspriteIR(ms: MetaspriteIR): JSONObject {
        val obj =
            JSONObject()
                .put("id", ms.id)
                .put("frames", serializeList(ms.frames, ::serializeMetaspriteFrame))
                .put("sourceLocation", serializeSourceLocation(ms.sourceLocation))
        // Req 5 (12.9 WR-05): compile-time OBJ palette slot — additive, omit when null for backward
        // compat
        ms.initialSubPaletteSlot?.let { obj.put("initialSubPaletteSlot", it) }
        // Req 4 (13.7 WR-05): owning scene ID for scene-scoped suppression — additive, omit when
        // null
        ms.sceneId?.let { obj.put("sceneId", it) }
        return obj
    }

    private fun deserializeMetaspriteIR(json: JSONObject): MetaspriteIR {
        return MetaspriteIR(
            id = json.getString("id"),
            frames =
                deserializeList(json.optJSONArray("frames")) { deserializeMetaspriteFrame(it) },
            sourceLocation =
                json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
            // Req 5: optional compile-time OBJ slot — default null on older serialized JSON
            initialSubPaletteSlot =
                if (json.has("initialSubPaletteSlot")) json.getInt("initialSubPaletteSlot")
                else null,
            // Req 4: optional owning scene ID — default null on older serialized JSON
            sceneId = json.optString("sceneId", null),
        )
    }

    private fun serializeMetaspriteFrame(frame: MetaspriteFrame): JSONObject {
        return JSONObject().put("tiles", serializeList(frame.tiles, ::serializeMetaspriteTile))
    }

    private fun deserializeMetaspriteFrame(json: JSONObject): MetaspriteFrame {
        return MetaspriteFrame(
            tiles = deserializeList(json.optJSONArray("tiles")) { deserializeMetaspriteTile(it) }
        )
    }

    private fun serializeMetaspriteTile(tile: MetaspriteTile): JSONObject {
        return JSONObject().put("relX", tile.relX).put("relY", tile.relY).put("tileId", tile.tileId)
    }

    private fun deserializeMetaspriteTile(json: JSONObject): MetaspriteTile {
        return MetaspriteTile(
            relX = json.getInt("relX"),
            relY = json.getInt("relY"),
            tileId = json.getInt("tileId"),
        )
    }

    // =========================================================================
    // VariableDef and ArrayDef serialization
    // =========================================================================

    private fun serializeVariableDef(v: VariableDef): JSONObject {
        return JSONObject()
            .put("name", v.name)
            .put("type", v.type.name)
            .put("initialValue", v.initialValue)
    }

    private fun deserializeVariableDef(json: JSONObject): VariableDef {
        return VariableDef(
            name = json.getString("name"),
            type = VarType.valueOf(json.getString("type")),
            initialValue = json.optInt("initialValue", 0),
        )
    }

    private fun serializeArrayDef(a: ArrayDef): JSONObject {
        return JSONObject()
            .put("name", a.name)
            .put("elementType", a.elementType.name)
            .put("size", a.size)
    }

    private fun deserializeArrayDef(json: JSONObject): ArrayDef {
        return ArrayDef(
            name = json.getString("name"),
            elementType = VarType.valueOf(json.getString("elementType")),
            size = json.getInt("size"),
        )
    }

    // =========================================================================
    // Collections IR serialization
    // =========================================================================

    private fun serializeCollElementType(t: CollElementType): JSONObject {
        val json = JSONObject()
        return when (t) {
            is CollElementType.Primitive ->
                json.put("kind", "Primitive").put("varType", t.varType.name)
            is CollElementType.Struct ->
                json.put("kind", "Struct").put("structName", t.structDef.name)
        }
    }

    private fun deserializeCollElementType(json: JSONObject): CollElementType {
        return when (val kind = json.getString("kind")) {
            "Primitive" -> CollElementType.Primitive(VarType.valueOf(json.getString("varType")))
            "Struct" -> {
                // Reconstruct a minimal StructDef as placeholder — full struct is in GameIR.structs
                val structName = json.getString("structName")
                val placeholder = StructFieldDef("_placeholder", VarType.U8)
                CollElementType.Struct(StructDef(name = structName, fields = listOf(placeholder)))
            }
            else -> {
                System.err.println(
                    "WARNING: Unknown CollElementType kind '$kind'; defaulting to U8"
                )
                CollElementType.Primitive(VarType.U8)
            }
        }
    }

    private fun serializeHashTable(t: IRCollHashTable): JSONObject {
        return JSONObject()
            .put("name", t.name)
            .put("keyType", serializeCollElementType(t.keyType))
            .put("valueType", serializeCollElementType(t.valueType))
            .put("size", t.size)
            .put("sourceLocation", serializeSourceLocation(t.sourceLocation))
    }

    private fun deserializeHashTable(json: JSONObject): IRCollHashTable {
        return IRCollHashTable(
            name = json.getString("name"),
            keyType = deserializeCollElementType(json.getJSONObject("keyType")),
            valueType = deserializeCollElementType(json.getJSONObject("valueType")),
            size = json.getInt("size"),
            sourceLocation =
                json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
        )
    }

    private fun serializePool(p: IRCollPool): JSONObject {
        return JSONObject()
            .put("name", p.name)
            .put("elementType", serializeCollElementType(p.elementType))
            .put("capacity", p.capacity)
            .put("sourceLocation", serializeSourceLocation(p.sourceLocation))
    }

    private fun deserializePool(json: JSONObject): IRCollPool {
        return IRCollPool(
            name = json.getString("name"),
            elementType = deserializeCollElementType(json.getJSONObject("elementType")),
            capacity = json.getInt("capacity"),
            sourceLocation =
                json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
        )
    }

    private fun serializeRingBuffer(r: IRCollRingBuffer): JSONObject {
        return JSONObject()
            .put("name", r.name)
            .put("elementType", serializeCollElementType(r.elementType))
            .put("capacity", r.capacity)
            .put("sourceLocation", serializeSourceLocation(r.sourceLocation))
    }

    private fun deserializeRingBuffer(json: JSONObject): IRCollRingBuffer {
        return IRCollRingBuffer(
            name = json.getString("name"),
            elementType = deserializeCollElementType(json.getJSONObject("elementType")),
            capacity = json.getInt("capacity"),
            sourceLocation =
                json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
        )
    }

    private fun serializeFixedSlots(f: IRCollFixedSlots): JSONObject {
        val json =
            JSONObject()
                .put("name", f.name)
                .put("elementType", serializeCollElementType(f.elementType))
                .put("count", f.count)
                .put("sourceLocation", serializeSourceLocation(f.sourceLocation))
        val namedSlotsJson = JSONObject()
        for ((k, v) in f.namedSlots) namedSlotsJson.put(k, v)
        json.put("namedSlots", namedSlotsJson)
        return json
    }

    private fun deserializeFixedSlots(json: JSONObject): IRCollFixedSlots {
        val namedSlotsJson = json.optJSONObject("namedSlots")
        val namedSlots = mutableMapOf<String, Int>()
        if (namedSlotsJson != null) {
            for (key in namedSlotsJson.keys()) namedSlots[key] = namedSlotsJson.getInt(key)
        }
        return IRCollFixedSlots(
            name = json.getString("name"),
            elementType = deserializeCollElementType(json.getJSONObject("elementType")),
            count = json.getInt("count"),
            namedSlots = namedSlots,
            sourceLocation =
                json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
        )
    }

    // =========================================================================
    // Foundation types
    // =========================================================================

    private fun serializePosition(p: PositionDef): JSONObject =
        JSONObject().put("x", p.x).put("y", p.y)

    private fun deserializePosition(json: JSONObject): PositionDef =
        PositionDef(json.getInt("x"), json.getInt("y"))

    private fun serializeHitbox(h: HitboxDef): JSONObject =
        JSONObject().put("x", h.x).put("y", h.y).put("width", h.width).put("height", h.height)

    private fun deserializeHitbox(json: JSONObject): HitboxDef =
        HitboxDef(json.getInt("x"), json.getInt("y"), json.getInt("width"), json.getInt("height"))

    private fun serializeSizeDef(s: SizeDef): JSONObject =
        JSONObject().put("width", s.width).put("height", s.height)

    private fun deserializeSizeDef(json: JSONObject): SizeDef =
        SizeDef(json.getInt("width"), json.getInt("height"))

    private fun serializeSpriteDef(s: SpriteDef): JSONObject {
        val json = JSONObject()
        json.put("assetRef", serializeAssetRef(s.assetRef))
        json.put("size", serializeSizeDef(s.size))
        json.put("hitbox", s.hitbox?.let { serializeHitbox(it) } ?: JSONObject.NULL)
        json.put("frameWidth", s.frameWidth ?: JSONObject.NULL)
        json.put("frameHeight", s.frameHeight ?: JSONObject.NULL)
        return json
    }

    private fun deserializeSpriteDef(json: JSONObject): SpriteDef {
        return SpriteDef(
            assetRef = deserializeAssetRef(json.getJSONObject("assetRef")),
            size = deserializeSizeDef(json.getJSONObject("size")),
            hitbox = json.optJSONObject("hitbox")?.let { deserializeHitbox(it) },
            frameWidth = json.optInt("frameWidth", -1).takeIf { it >= 0 },
            frameHeight = json.optInt("frameHeight", -1).takeIf { it >= 0 },
        )
    }

    private fun serializeSourceLocation(loc: SourceLocation?): Any {
        if (loc == null) return JSONObject.NULL
        return JSONObject().put("file", loc.file).put("line", loc.line).put("col", loc.col)
    }

    private fun deserializeSourceLocation(json: JSONObject): SourceLocation =
        SourceLocation(json.getString("file"), json.getInt("line"), json.getInt("col"))

    private fun serializeAssetRef(ref: AssetRef): JSONObject =
        JSONObject().put("path", ref.path).put("type", ref.type.name)

    private fun deserializeAssetRef(json: JSONObject): AssetRef =
        AssetRef(json.getString("path"), AssetType.valueOf(json.getString("type")))

    private fun serializeCartridgeConfig(config: CartridgeConfig): JSONObject {
        return JSONObject()
            .put("cartridge", config.cartridge.name)
            .apply { config.romBanks?.let { put("romBanks", it) } }
            .put("ramBanks", config.ramBanks)
            .put("gbcTarget", config.gbcTarget.name)
    }

    private fun deserializeCartridgeConfig(json: JSONObject): CartridgeConfig {
        val cartridgeName = json.optString("cartridge", "ROM_ONLY")
        val cartridge =
            Cartridge.entries.firstOrNull { it.name == cartridgeName }
                ?: run {
                    System.err.println(
                        "WARNING: Unknown cartridge '$cartridgeName'; defaulting to ROM_ONLY"
                    )
                    Cartridge.ROM_ONLY
                }
        return CartridgeConfig(
            cartridge = cartridge,
            romBanks =
                if (json.has("romBanks") && !json.isNull("romBanks")) json.getInt("romBanks")
                else null,
            ramBanks = json.optInt("ramBanks", 0),
            gbcTarget =
                json.optString("gbcTarget", GbcTarget.DMG.name).let { GbcTarget.valueOf(it) },
        )
    }

    // =========================================================================
    // Platform annotations
    // =========================================================================

    private fun serializeBankSlot(b: BankSlot): JSONObject {
        val json = JSONObject().put("bank", b.bank)
        if (b.offset != null) json.put("offset", b.offset)
        return json
    }

    private fun deserializeBankSlot(json: JSONObject): BankSlot =
        BankSlot(json.getInt("bank"), json.optInt("offset", -1).takeIf { it >= 0 })

    private fun serializeVRAMRange(v: VRAMRange): JSONObject =
        JSONObject().put("startTile", v.startTile).put("endTile", v.endTile)

    private fun deserializeVRAMRange(json: JSONObject): VRAMRange =
        VRAMRange(json.getInt("startTile"), json.getInt("endTile"))

    private fun serializeOAMSlot(o: OAMSlot): JSONObject = JSONObject().put("slot", o.slot)

    private fun deserializeOAMSlot(json: JSONObject): OAMSlot = OAMSlot(json.getInt("slot"))

    // =========================================================================
    // MovementConfig serialization
    // =========================================================================

    private fun serializeMovementConfig(m: MovementConfig): JSONObject {
        val json =
            JSONObject()
                .put("style", m.style.name)
                .put("speed", m.speed)
                .put("tileSize", m.tileSize)
        if (m.smoothConfig != null) {
            json.put(
                "smoothConfig",
                JSONObject()
                    .put("speed", m.smoothConfig.speed)
                    .put("acceleration", m.smoothConfig.acceleration)
                    .put("friction", m.smoothConfig.friction)
                    .put("diagonalMode", m.smoothConfig.diagonalMode.name)
                    .put("fixedPointMode", m.smoothConfig.fixedPointMode.name),
            )
        }
        return json
    }

    private fun deserializeMovementConfig(json: JSONObject): MovementConfig {
        val smoothConfigJson = json.optJSONObject("smoothConfig")
        val smoothConfig =
            if (smoothConfigJson != null) {
                SmoothMovementConfig(
                    speed = smoothConfigJson.getInt("speed"),
                    acceleration = smoothConfigJson.getInt("acceleration"),
                    friction = smoothConfigJson.getInt("friction"),
                    diagonalMode =
                        DiagonalMode.valueOf(
                            smoothConfigJson.optString("diagonalMode", DiagonalMode.RAW.name)
                        ),
                    fixedPointMode =
                        FixedPointMode.valueOf(
                            smoothConfigJson.optString(
                                "fixedPointMode",
                                FixedPointMode.INTEGER.name,
                            )
                        ),
                )
            } else {
                null
            }
        return MovementConfig(
            style = MovementStyle.valueOf(json.getString("style")),
            speed = json.optInt("speed", 4),
            tileSize = json.optInt("tileSize", 8),
            smoothConfig = smoothConfig,
        )
    }

    // =========================================================================
    // AnimationStateDef serialization
    // =========================================================================

    private fun serializeAnimationStateDef(a: AnimationStateDef): JSONObject {
        val json =
            JSONObject()
                .put("name", a.name)
                .put("startFrame", a.startFrame)
                .put("endFrame", a.endFrame)
                .put("speed", a.speed)
                .put("loop", a.loop)
        val transitions = JSONArray()
        for (t in a.transitions) {
            val tj = JSONObject().put("fromState", t.fromState).put("toState", t.toState)
            if (t.condition != null) tj.put("condition", serializeExpr(t.condition))
            transitions.put(tj)
        }
        json.put("transitions", transitions)
        return json
    }

    private fun deserializeAnimationStateDef(json: JSONObject): AnimationStateDef {
        val transitionsArray = json.optJSONArray("transitions")
        val transitions =
            if (transitionsArray != null) {
                (0 until transitionsArray.length()).map { i ->
                    val tj = transitionsArray.getJSONObject(i)
                    AnimTransition(
                        fromState = tj.getString("fromState"),
                        toState = tj.getString("toState"),
                        condition = tj.optJSONObject("condition")?.let { deserializeExpr(it) },
                    )
                }
            } else {
                emptyList()
            }
        return AnimationStateDef(
            name = json.getString("name"),
            startFrame = json.getInt("startFrame"),
            endFrame = json.getInt("endFrame"),
            speed = json.optInt("speed", 8),
            loop = json.optBoolean("loop", true),
            transitions = transitions,
        )
    }

    // =========================================================================
    // PhysicsConfig serialization
    // =========================================================================

    private fun serializePhysicsConfig(p: PhysicsConfig): JSONObject {
        return JSONObject()
            .put("velocityX", p.velocityX)
            .put("velocityY", p.velocityY)
            .put("accelerationX", p.accelerationX)
            .put("accelerationY", p.accelerationY)
            .put("gravity", p.gravity)
            .put("bounce", p.bounce)
            .put("maxFallSpeed", p.maxFallSpeed)
            .put("platformerMode", p.platformerMode)
            .put("variableJump", p.variableJump)
            .put("jumpCutMultiplier", p.jumpCutMultiplier)
            .put("coyoteFrames", p.coyoteFrames)
            .put("wallResponse", p.wallResponse.name)
            .put("wallJump", p.wallJump)
            .put("wallJumpVelocityX", p.wallJumpVelocityX)
            .put("wallJumpVelocityY", p.wallJumpVelocityY)
            .put("fixedPointMode", p.fixedPointMode.name)
    }

    private fun deserializePhysicsConfig(json: JSONObject): PhysicsConfig {
        return PhysicsConfig(
            velocityX = json.optInt("velocityX", 0),
            velocityY = json.optInt("velocityY", 0),
            accelerationX = json.optInt("accelerationX", 0),
            accelerationY = json.optInt("accelerationY", 0),
            gravity = json.optInt("gravity", 0),
            bounce = json.optInt("bounce", 0),
            maxFallSpeed = json.optInt("maxFallSpeed", 8),
            platformerMode = json.optBoolean("platformerMode", false),
            variableJump = json.optBoolean("variableJump", false),
            jumpCutMultiplier = json.optInt("jumpCutMultiplier", 2),
            coyoteFrames = json.optInt("coyoteFrames", 0),
            wallResponse =
                json.optString("wallResponse", WallResponse.STOP.name).let {
                    WallResponse.valueOf(it)
                },
            wallJump = json.optBoolean("wallJump", false),
            wallJumpVelocityX = json.optInt("wallJumpVelocityX", 0),
            wallJumpVelocityY = json.optInt("wallJumpVelocityY", 0),
            fixedPointMode =
                json.optString("fixedPointMode", FixedPointMode.INTEGER.name).let {
                    FixedPointMode.valueOf(it)
                },
        )
    }

    // =========================================================================
    // WaypointRoute serialization
    // =========================================================================

    private fun serializeWaypointRoute(w: WaypointRoute): JSONObject {
        val points = JSONArray()
        for ((x, y) in w.points) points.put(JSONObject().put("x", x).put("y", y))
        return JSONObject().put("points", points).put("loop", w.loop)
    }

    private fun deserializeWaypointRoute(json: JSONObject): WaypointRoute {
        val pointsArray = json.optJSONArray("points") ?: JSONArray()
        val points =
            (0 until pointsArray.length()).map { i ->
                val p = pointsArray.getJSONObject(i)
                Pair(p.getInt("x"), p.getInt("y"))
            }
        return WaypointRoute(points = points, loop = json.optBoolean("loop", true))
    }

    // =========================================================================
    // GBCPalette serialization
    // =========================================================================

    private fun serializeGBCPalette(p: GBCPalette): JSONObject {
        val colors = JSONArray()
        for (c in p.colors) colors.put(c.rgb555)
        return JSONObject()
            .put("name", p.name)
            .put("colors", colors)
            .put("slot", p.slot)
            .put("type", p.type.name)
    }

    private fun deserializeGBCPalette(json: JSONObject): GBCPalette {
        val colorsArray = json.getJSONArray("colors")
        val colors = (0 until colorsArray.length()).map { GBCColor(colorsArray.getInt(it)) }
        return GBCPalette(
            name = json.getString("name"),
            colors = colors,
            slot = json.optInt("slot", -1),
            type = PaletteType.valueOf(json.optString("type", PaletteType.SPRITE.name)),
        )
    }

    // =========================================================================
    // SoundEffectDef serialization
    // =========================================================================

    private fun serializeSoundEffectDef(s: SoundEffectDef): JSONObject {
        return JSONObject()
            .put("id", s.id)
            .put("channel", s.channel.name)
            .put("registers", serializeSoundRegisters(s.registers))
            .put("priority", s.priority.name)
    }

    private fun deserializeSoundEffectDef(json: JSONObject): SoundEffectDef {
        return SoundEffectDef(
            id = json.getString("id"),
            channel = SoundChannel.valueOf(json.getString("channel")),
            registers = deserializeSoundRegisters(json.getJSONObject("registers")),
            priority = SfxPriority.valueOf(json.optString("priority", SfxPriority.MEDIUM.name)),
        )
    }

    private fun serializeSoundRegisters(r: SoundRegisters): JSONObject {
        val json =
            JSONObject()
                .put("frequency", r.frequency)
                .put("length", r.length)
                .put("trigger", r.trigger)
                .put("lengthEnable", r.lengthEnable)
                .put("duty", r.duty.name)
                .put("noiseClockShift", r.noiseClockShift)
                .put("noiseDivisor", r.noiseDivisor)
                .put("noiseWidthMode", r.noiseWidthMode)
                .put("waveOutputLevel", r.waveOutputLevel)
        if (r.envelope != null) {
            json.put(
                "envelope",
                JSONObject()
                    .put("volume", r.envelope.volume)
                    .put("direction", r.envelope.direction.name)
                    .put("pace", r.envelope.pace),
            )
        }
        if (r.sweep != null) {
            json.put(
                "sweep",
                JSONObject()
                    .put("time", r.sweep.time)
                    .put("direction", r.sweep.direction.name)
                    .put("shift", r.sweep.shift),
            )
        }
        if (r.waveform != null) {
            json.put("waveform", JSONArray(r.waveform.map { it.toInt() }))
        }
        return json
    }

    private fun deserializeSoundRegisters(json: JSONObject): SoundRegisters {
        val envelopeJson = json.optJSONObject("envelope")
        val envelope =
            if (envelopeJson != null) {
                EnvelopeConfig(
                    volume = envelopeJson.getInt("volume"),
                    direction = EnvelopeDirection.valueOf(envelopeJson.getString("direction")),
                    pace = envelopeJson.getInt("pace"),
                )
            } else {
                null
            }
        val sweepJson = json.optJSONObject("sweep")
        val sweep =
            if (sweepJson != null) {
                SweepConfig(
                    time = sweepJson.getInt("time"),
                    direction = SweepDirection.valueOf(sweepJson.getString("direction")),
                    shift = sweepJson.getInt("shift"),
                )
            } else {
                null
            }
        val waveformArray = json.optJSONArray("waveform")
        val waveform =
            if (waveformArray != null) {
                ByteArray(waveformArray.length()) { i -> waveformArray.getInt(i).toByte() }
            } else {
                null
            }
        return SoundRegisters(
            frequency = json.optInt("frequency", 0),
            length = json.optInt("length", 0),
            trigger = json.optBoolean("trigger", true),
            lengthEnable = json.optBoolean("lengthEnable", false),
            duty = DutyCycle.valueOf(json.optString("duty", DutyCycle.FIFTY.name)),
            envelope = envelope,
            sweep = sweep,
            noiseClockShift = json.optInt("noiseClockShift", 0),
            noiseDivisor = json.optInt("noiseDivisor", 0),
            noiseWidthMode = json.optBoolean("noiseWidthMode", false),
            waveOutputLevel = json.optInt("waveOutputLevel", 2),
            waveform = waveform,
        )
    }

    // =========================================================================
    // StructDef serialization
    // =========================================================================

    private fun serializeStructDef(s: StructDef): JSONObject {
        val fields = JSONArray()
        for (f in s.fields) {
            fields.put(JSONObject().put("name", f.name).put("type", f.type.name))
        }
        return JSONObject().put("name", s.name).put("fields", fields)
    }

    private fun deserializeStructDef(json: JSONObject): StructDef {
        val fieldsArray = json.optJSONArray("fields") ?: JSONArray()
        val fields =
            (0 until fieldsArray.length()).map { i ->
                val fj = fieldsArray.getJSONObject(i)
                StructFieldDef(
                    name = fj.getString("name"),
                    type = VarType.valueOf(fj.getString("type")),
                )
            }
        return StructDef(name = json.getString("name"), fields = fields)
    }

    // =========================================================================
    // ActorPoolIR serialization
    // =========================================================================

    private fun serializeActorPoolIR(p: ActorPoolIR): JSONObject {
        return JSONObject()
            .put("id", p.id)
            .put("actorTemplateId", p.actorTemplateId)
            .put("config", serializeActorPoolConfig(p.config))
            .put("deathCallback", serializeList(p.deathCallback, ::serializeOp))
    }

    private fun deserializeActorPoolIR(json: JSONObject): ActorPoolIR {
        return ActorPoolIR(
            id = json.getString("id"),
            actorTemplateId = json.getString("actorTemplateId"),
            config =
                json.optJSONObject("config")?.let { deserializeActorPoolConfig(it) }
                    ?: ActorPoolConfig(maxSize = 8),
            deathCallback =
                deserializeList(json.optJSONArray("deathCallback")) { deserializeOp(it) },
        )
    }

    private fun serializeActorPoolConfig(c: ActorPoolConfig): JSONObject {
        return JSONObject()
            .put("maxSize", c.maxSize)
            .put("overflowStrategy", c.overflowStrategy.name)
    }

    private fun deserializeActorPoolConfig(json: JSONObject): ActorPoolConfig {
        return ActorPoolConfig(
            maxSize = json.optInt("maxSize", 8),
            overflowStrategy =
                PoolOverflowStrategy.valueOf(
                    json.optString("overflowStrategy", PoolOverflowStrategy.SILENT_NOOP.name)
                ),
        )
    }

    // =========================================================================
    // MusicDef serialization
    // =========================================================================

    private fun serializeMusicDef(m: MusicDef): JSONObject {
        return JSONObject()
            .put("id", m.id)
            .put("assetRef", serializeAssetRef(m.assetRef))
            .put("sourceLocation", serializeSourceLocation(m.sourceLocation))
    }

    private fun deserializeMusicDef(json: JSONObject): MusicDef {
        return MusicDef(
            id = json.getString("id"),
            assetRef = deserializeAssetRef(json.getJSONObject("assetRef")),
            sourceLocation =
                json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
        )
    }

    // =========================================================================
    // UI types (Dialog, Menu, HUD)
    // =========================================================================

    private fun serializeDialogDef(d: DialogDef): JSONObject {
        val json =
            JSONObject()
                .put("id", d.id)
                .put("textSpeed", d.textSpeed)
                .put("border", d.border.name)
                .put("boxX", d.boxX)
                .put("boxY", d.boxY)
                .put("boxWidth", d.boxWidth)
                .put("boxHeight", d.boxHeight)
                .put("fontMode", d.fontMode.name)
        if (d.speaker != null) json.put("speaker", d.speaker)
        if (d.portrait != null) json.put("portrait", serializeAssetRef(d.portrait))
        if (d.customBorderTiles != null)
            json.put("customBorderTiles", JSONArray(d.customBorderTiles))
        return json
    }

    private fun deserializeDialogDef(json: JSONObject): DialogDef {
        val customBorderArray = json.optJSONArray("customBorderTiles")
        val customBorderTiles =
            if (customBorderArray != null) {
                (0 until customBorderArray.length()).map { customBorderArray.getInt(it) }
            } else {
                null
            }
        return DialogDef(
            id = json.getString("id"),
            textSpeed = json.optInt("textSpeed", 1),
            border = BorderStyle.valueOf(json.optString("border", BorderStyle.NONE.name)),
            speaker = json.optString("speaker", null),
            portrait = json.optJSONObject("portrait")?.let { deserializeAssetRef(it) },
            boxX = json.optInt("boxX", 0),
            boxY = json.optInt("boxY", 14),
            boxWidth = json.optInt("boxWidth", 20),
            boxHeight = json.optInt("boxHeight", 4),
            customBorderTiles = customBorderTiles,
            fontMode = FontMode.valueOf(json.optString("fontMode", FontMode.FIXED_WIDTH.name)),
        )
    }

    private fun serializeMenuDef(m: MenuDef): JSONObject {
        val items = JSONArray()
        for (item in m.items) {
            items.put(
                JSONObject()
                    .put("label", item.label)
                    .put("body", serializeList(item.body, ::serializeOp))
            )
        }
        val json =
            JSONObject()
                .put("id", m.id)
                .put("layout", m.layout.name)
                .put("cursorChar", m.cursorChar)
                .put("renderOnWindow", m.renderOnWindow)
                .put("scrollBehavior", m.scrollBehavior.name)
                .put("x", m.x)
                .put("y", m.y)
                .put("width", m.width)
                .put("height", m.height)
                .put("columns", m.columns)
                .put("items", items)
        if (m.parentId != null) json.put("parentId", m.parentId)
        if (m.cursorSprite != null) json.put("cursorSprite", serializeAssetRef(m.cursorSprite))
        if (m.sfxOnMove != null) json.put("sfxOnMove", m.sfxOnMove)
        if (m.sfxOnSelect != null) json.put("sfxOnSelect", m.sfxOnSelect)
        if (m.sfxOnCancel != null) json.put("sfxOnCancel", m.sfxOnCancel)
        return json
    }

    private fun deserializeMenuDef(json: JSONObject): MenuDef {
        val itemsArray = json.optJSONArray("items") ?: JSONArray()
        val items =
            (0 until itemsArray.length()).map { i ->
                val ij = itemsArray.getJSONObject(i)
                MenuItemDef(
                    label = ij.getString("label"),
                    body = deserializeList(ij.optJSONArray("body")) { deserializeOp(it) },
                )
            }
        return MenuDef(
            id = json.getString("id"),
            layout = MenuLayout.valueOf(json.optString("layout", MenuLayout.VERTICAL.name)),
            cursorChar = json.optString("cursorChar", ">"),
            cursorSprite = json.optJSONObject("cursorSprite")?.let { deserializeAssetRef(it) },
            parentId = json.optString("parentId", null),
            renderOnWindow = json.optBoolean("renderOnWindow", true),
            scrollBehavior =
                ScrollBehavior.valueOf(
                    json.optString("scrollBehavior", ScrollBehavior.AUTO_SCROLL.name)
                ),
            sfxOnMove = json.optString("sfxOnMove", null),
            sfxOnSelect = json.optString("sfxOnSelect", null),
            sfxOnCancel = json.optString("sfxOnCancel", null),
            x = json.optInt("x", 0),
            y = json.optInt("y", 0),
            width = json.optInt("width", 20),
            height = json.optInt("height", 18),
            columns = json.optInt("columns", 1),
            items = items,
        )
    }

    private fun serializeHudDef(h: HudDef): JSONObject {
        val elements = JSONArray()
        for (el in h.elements) elements.put(serializeHudElement(el))
        val json =
            JSONObject()
                .put("id", h.id)
                .put("anchor", h.anchor.name)
                .put("renderOnWindow", h.renderOnWindow)
                .put("elements", elements)
        if (h.tileX != null) json.put("tileX", h.tileX)
        if (h.tileY != null) json.put("tileY", h.tileY)
        return json
    }

    private fun serializeHudElement(el: HudElement): JSONObject {
        return when (el) {
            is HudBar ->
                JSONObject()
                    .put("kind", "HudBar")
                    .put("id", el.id)
                    .put("variable", el.variable)
                    .put("maxVariable", el.maxVariable ?: JSONObject.NULL)
                    .put("maxValue", el.maxValue)
                    .put("width", el.width)
                    .put("fillTile", el.fillTile)
                    .put("emptyTile", el.emptyTile)
                    .put("fillFrames", el.fillFrames)
                    .put("gbcPalette", el.gbcPalette ?: JSONObject.NULL)
            is HudNumber ->
                JSONObject()
                    .put("kind", "HudNumber")
                    .put("id", el.id)
                    .put("variable", el.variable)
                    .put("label", el.label)
                    .put("format", el.format)
            is HudIcons ->
                JSONObject()
                    .put("kind", "HudIcons")
                    .put("id", el.id)
                    .put("variable", el.variable)
                    .put("maxValue", el.maxValue)
                    .put("fullTile", el.fullTile)
                    .put("emptyTile", el.emptyTile)
                    .put("displayMode", el.displayMode.name)
            else -> JSONObject().put("kind", "Unknown")
        }
    }

    private fun deserializeHudDef(json: JSONObject): HudDef {
        val elementsArray = json.optJSONArray("elements") ?: JSONArray()
        val elements =
            (0 until elementsArray.length()).mapNotNull { i ->
                val ej = elementsArray.getJSONObject(i)
                when (ej.optString("kind")) {
                    "HudBar" ->
                        HudBar(
                            id = ej.getString("id"),
                            variable = ej.getString("variable"),
                            maxVariable =
                                ej.optString("maxVariable", null).takeIf {
                                    it != "null" && it != ""
                                },
                            maxValue = ej.optInt("maxValue", 100),
                            width = ej.optInt("width", 8),
                            fillTile = ej.optInt("fillTile", 0x01),
                            emptyTile = ej.optInt("emptyTile", 0x00),
                            fillFrames = ej.optInt("fillFrames", 0),
                            gbcPalette = ej.optInt("gbcPalette", -1).takeIf { it >= 0 },
                        )
                    "HudNumber" ->
                        HudNumber(
                            id = ej.getString("id"),
                            variable = ej.getString("variable"),
                            label = ej.optString("label", ""),
                            format = ej.optString("format", "%d"),
                        )
                    "HudIcons" ->
                        HudIcons(
                            id = ej.getString("id"),
                            variable = ej.getString("variable"),
                            maxValue = ej.getInt("maxValue"),
                            fullTile = ej.optInt("fullTile", 0),
                            emptyTile = ej.optInt("emptyTile", 1),
                            displayMode =
                                IconDisplayMode.valueOf(
                                    ej.optString("displayMode", IconDisplayMode.FULL_AND_EMPTY.name)
                                ),
                        )
                    else -> null
                }
            }
        return HudDef(
            id = json.getString("id"),
            anchor = Anchor.valueOf(json.optString("anchor", Anchor.TOP_LEFT.name)),
            tileX = json.optInt("tileX", -1).takeIf { it >= 0 },
            tileY = json.optInt("tileY", -1).takeIf { it >= 0 },
            renderOnWindow = json.optBoolean("renderOnWindow", true),
            elements = elements,
        )
    }

    // =========================================================================
    // SystemIR — simplified serialization
    // =========================================================================

    private fun serializeSystemIR(s: SystemIR): JSONObject {
        // TODO: full SystemIR round-trip not needed for external tool use cases
        return JSONObject().put("id", s.id).put("type", s::class.simpleName ?: "SystemIR")
    }

    // =========================================================================
    // Simplified domain-specific helpers
    // =========================================================================

    private fun serializeSimple(typeName: String, id: String): JSONObject =
        JSONObject().put("type", typeName).put("id", id)

    private fun serializeCollisionRule(r: CollisionRuleIR): JSONObject {
        return JSONObject()
            .put("groupA", r.groupA)
            .put("groupB", r.groupB)
            .put("response", r.response.name)
    }

    // =========================================================================
    // ScriptOp hierarchy — implemented in Task 2
    // =========================================================================

    internal fun serializeOp(op: ScriptOp): JSONObject {
        val json = JSONObject()
        when (op) {
            is Assign -> {
                json.put("type", "Assign")
                json.put("target", op.target)
                json.put("value", serializeExpr(op.value))
                json.put("op", op.op.name)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is ArrayAssign -> {
                json.put("type", "ArrayAssign")
                json.put("array", op.array)
                json.put("index", serializeExpr(op.index))
                json.put("value", serializeExpr(op.value))
                json.put("op", op.op.name)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is IfOp -> {
                json.put("type", "IfOp")
                json.put("condition", serializeExpr(op.condition))
                json.put("then", serializeList(op.then, ::serializeOp))
                json.put("otherwise", serializeList(op.otherwise, ::serializeOp))
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is WhileOp -> {
                json.put("type", "WhileOp")
                json.put("condition", serializeExpr(op.condition))
                json.put("body", serializeList(op.body, ::serializeOp))
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is ForOp -> {
                json.put("type", "ForOp")
                json.put("variable", op.variable)
                json.put("from", serializeExpr(op.from))
                json.put("to", serializeExpr(op.to))
                json.put("body", serializeList(op.body, ::serializeOp))
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is SetPosition -> {
                json.put("type", "SetPosition")
                json.put("actorId", op.actorId)
                json.put("x", serializeExpr(op.x))
                json.put("y", serializeExpr(op.y))
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is MoveBy -> {
                json.put("type", "MoveBy")
                json.put("actorId", op.actorId)
                json.put("dx", serializeExpr(op.dx))
                json.put("dy", serializeExpr(op.dy))
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is NavigateTo -> {
                json.put("type", "NavigateTo")
                json.put("sceneId", op.sceneId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is TriggerSystem -> {
                json.put("type", "TriggerSystem")
                json.put("systemId", op.systemId)
                val argsJson = JSONObject()
                for ((k, v) in op.args) argsJson.put(k, serializeExpr(v))
                json.put("args", argsJson)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is PlaySound -> {
                json.put("type", "PlaySound")
                json.put("soundId", op.soundId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is MusicPlay -> {
                json.put("type", "MusicPlay")
                json.put("songId", op.songId)
                json.put("fadeInFrames", op.fadeInFrames)
                json.put("resume", op.resume)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is MusicStop -> {
                json.put("type", "MusicStop")
                json.put("fadeOutFrames", op.fadeOutFrames)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is MusicPause -> {
                json.put("type", "MusicPause")
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is MusicResume -> {
                json.put("type", "MusicResume")
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is DialogSay -> {
                json.put("type", "DialogSay")
                json.put("dialogId", op.dialogId)
                val segments = JSONArray()
                for (seg in op.segments) {
                    segments.put(
                        when (seg) {
                            is DialogTextSegment ->
                                JSONObject().put("kind", "text").put("text", seg.text)
                            is DialogExprSegment ->
                                JSONObject()
                                    .put("kind", "expr")
                                    .put("expr", serializeExpr(seg.expr))
                        }
                    )
                }
                json.put("segments", segments)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is DialogChoice -> {
                json.put("type", "DialogChoice")
                json.put("dialogId", op.dialogId)
                val options = JSONArray()
                for (opt in op.options) {
                    options.put(
                        JSONObject()
                            .put("label", opt.label)
                            .put("body", serializeList(opt.body, ::serializeOp))
                    )
                }
                json.put("options", options)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is MenuShow -> {
                json.put("type", "MenuShow")
                json.put("menuId", op.menuId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is MenuHide -> {
                json.put("type", "MenuHide")
                json.put("menuId", op.menuId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is HudShow -> {
                json.put("type", "HudShow")
                json.put("hudId", op.hudId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is HudHide -> {
                json.put("type", "HudHide")
                json.put("hudId", op.hudId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is PrintAt -> {
                json.put("type", "PrintAt")
                json.put("x", op.x)
                json.put("y", op.y)
                json.put("text", op.text)
                json.put("fontMode", op.fontMode.name)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is PrintCentered -> {
                json.put("type", "PrintCentered")
                json.put("text", op.text)
                json.put("row", op.row)
                json.put("fontMode", op.fontMode.name)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is PrintAligned -> {
                json.put("type", "PrintAligned")
                json.put("text", op.text)
                json.put("row", op.row)
                json.put("alignment", op.alignment.name)
                json.put("fontMode", op.fontMode.name)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is ClearRegion -> {
                json.put("type", "ClearRegion")
                json.put("x", op.x)
                json.put("y", op.y)
                json.put("w", op.w)
                json.put("h", op.h)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is ScreenClear -> {
                json.put("type", "ScreenClear")
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is ScreenFill -> {
                json.put("type", "ScreenFill")
                json.put("tile", op.tile)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is SetPalette -> {
                json.put("type", "SetPalette")
                json.put("paletteName", op.paletteName)
                json.put("slot", op.slot)
                json.put("paletteType", op.type.name)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is PrintOp -> {
                json.put("type", "PrintOp")
                json.put("text", op.text)
                json.put("values", serializeList(op.values, ::serializeExpr))
                json.put("position", op.position?.let { serializePosition(it) } ?: JSONObject.NULL)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is FadeOp -> {
                json.put("type", "FadeOp")
                json.put("fadeIn", op.fadeIn)
                json.put("frames", op.frames)
                json.put("after", serializeList(op.after, ::serializeOp))
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is SetVisible -> {
                json.put("type", "SetVisible")
                json.put("actorId", op.actorId)
                json.put("visible", op.visible)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is SpawnActor -> {
                json.put("type", "SpawnActor")
                json.put("actorId", op.actorId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is DestroyActor -> {
                json.put("type", "DestroyActor")
                json.put("actorId", op.actorId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is PoolSpawnActor -> {
                json.put("type", "PoolSpawnActor")
                json.put("poolId", op.poolId)
                json.put("x", serializeExpr(op.x))
                json.put("y", serializeExpr(op.y))
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is PoolDestroyActor -> {
                json.put("type", "PoolDestroyActor")
                json.put("poolId", op.poolId)
                json.put("slotExpr", serializeExpr(op.slotExpr))
                json.put("deathCallbackOps", serializeList(op.deathCallbackOps, ::serializeOp))
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is PoolForEachActive -> {
                json.put("type", "PoolForEachActive")
                json.put("poolId", op.poolId)
                json.put("maxSize", op.maxSize)
                json.put("slotVarName", op.slotVarName)
                json.put("body", serializeList(op.body, ::serializeOp))
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is PoolDestroyAll -> {
                json.put("type", "PoolDestroyAll")
                json.put("poolId", op.poolId)
                json.put("maxSize", op.maxSize)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is AnimateOp -> {
                json.put("type", "AnimateOp")
                json.put("actorId", op.actorId)
                json.put("animation", op.animation)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is CameraOp -> {
                json.put("type", "CameraOp")
                json.put("action", op.action.name)
                val argsJson = JSONObject()
                for ((k, v) in op.args) argsJson.put(k, serializeExpr(v))
                json.put("args", argsJson)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is WaitFrames -> {
                json.put("type", "WaitFrames")
                json.put("frames", op.frames)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is CallOp -> {
                json.put("type", "CallOp")
                json.put("function", op.function)
                json.put("args", serializeList(op.args, ::serializeExpr))
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is ReturnOp -> {
                json.put("type", "ReturnOp")
                if (op.value != null) json.put("value", serializeExpr(op.value))
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is MathOp -> {
                json.put("type", "MathOp")
                json.put("result", op.result)
                json.put("mathOp", op.op.name)
                json.put("args", serializeList(op.args, ::serializeExpr))
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is RawOp -> {
                json.put("type", "RawOp")
                json.put("code", op.code)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is GotoXYOp -> {
                json.put("type", "GotoXYOp")
                json.put("x", serializeExpr(op.x))
                json.put("y", serializeExpr(op.y))
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is PathfindStep -> {
                json.put("type", "PathfindStep")
                json.put("npcActorId", op.npcActorId)
                json.put("targetActorId", op.targetActorId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is WaypointStep -> {
                json.put("type", "WaypointStep")
                json.put("npcActorId", op.npcActorId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is ActivatePuzzleObject -> {
                json.put("type", "ActivatePuzzleObject")
                json.put("objectId", op.objectId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is DeactivatePuzzleObject -> {
                json.put("type", "DeactivatePuzzleObject")
                json.put("objectId", op.objectId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is RevealPuzzleObject -> {
                json.put("type", "RevealPuzzleObject")
                json.put("objectId", op.objectId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is HidePuzzleObject -> {
                json.put("type", "HidePuzzleObject")
                json.put("objectId", op.objectId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is PhysicsStep -> {
                json.put("type", "PhysicsStep")
                json.put("actorId", op.actorId)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is SetAnimationState -> {
                json.put("type", "SetAnimationState")
                json.put("actorId", op.actorId)
                json.put("stateName", op.stateName)
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            is BindCurrentLevel -> {
                json.put("type", "BindCurrentLevel")
                json.put("sourceLocation", serializeSourceLocation(op.sourceLocation))
            }
            else -> {
                // Unknown / genre-specific op — preserve type discriminator and class name
                json.put("type", "Unknown")
                json.put("class", op::class.qualifiedName ?: op::class.simpleName ?: "Unknown")
            }
        }
        return json
    }

    internal fun deserializeOp(json: JSONObject): ScriptOp {
        return when (val type = json.optString("type", "Unknown")) {
            "Assign" ->
                Assign(
                    target = json.getString("target"),
                    value = deserializeExpr(json.getJSONObject("value")),
                    op = AssignOp.valueOf(json.optString("op", AssignOp.SET.name)),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "ArrayAssign" ->
                ArrayAssign(
                    array = json.getString("array"),
                    index = deserializeExpr(json.getJSONObject("index")),
                    value = deserializeExpr(json.getJSONObject("value")),
                    op = AssignOp.valueOf(json.optString("op", AssignOp.SET.name)),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "IfOp" ->
                IfOp(
                    condition = deserializeExpr(json.getJSONObject("condition")),
                    then = deserializeList(json.optJSONArray("then")) { deserializeOp(it) },
                    otherwise =
                        deserializeList(json.optJSONArray("otherwise")) { deserializeOp(it) },
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "WhileOp" ->
                WhileOp(
                    condition = deserializeExpr(json.getJSONObject("condition")),
                    body = deserializeList(json.optJSONArray("body")) { deserializeOp(it) },
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "ForOp" ->
                ForOp(
                    variable = json.getString("variable"),
                    from = deserializeExpr(json.getJSONObject("from")),
                    to = deserializeExpr(json.getJSONObject("to")),
                    body = deserializeList(json.optJSONArray("body")) { deserializeOp(it) },
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "SetPosition" ->
                SetPosition(
                    actorId = json.getString("actorId"),
                    x = deserializeExpr(json.getJSONObject("x")),
                    y = deserializeExpr(json.getJSONObject("y")),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "MoveBy" ->
                MoveBy(
                    actorId = json.getString("actorId"),
                    dx = deserializeExpr(json.getJSONObject("dx")),
                    dy = deserializeExpr(json.getJSONObject("dy")),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "NavigateTo" ->
                NavigateTo(
                    sceneId = json.getString("sceneId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "TriggerSystem" -> {
                val argsJson = json.optJSONObject("args") ?: JSONObject()
                val args = mutableMapOf<String, Expr>()
                for (k in argsJson.keys()) args[k] = deserializeExpr(argsJson.getJSONObject(k))
                TriggerSystem(
                    systemId = json.getString("systemId"),
                    args = args,
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            }
            "PlaySound" ->
                PlaySound(
                    soundId = json.getString("soundId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "MusicPlay" ->
                MusicPlay(
                    songId = json.getString("songId"),
                    fadeInFrames = json.optInt("fadeInFrames", 0),
                    resume = json.optBoolean("resume", false),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "MusicStop" ->
                MusicStop(
                    fadeOutFrames = json.optInt("fadeOutFrames", 0),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "MusicPause" ->
                MusicPause(
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) }
                )
            "MusicResume" ->
                MusicResume(
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) }
                )
            "DialogSay" -> {
                val segmentsArray = json.optJSONArray("segments") ?: JSONArray()
                val segments =
                    (0 until segmentsArray.length()).map { i ->
                        val sj = segmentsArray.getJSONObject(i)
                        when (sj.optString("kind")) {
                            "text" -> DialogTextSegment(sj.getString("text"))
                            else -> DialogExprSegment(deserializeExpr(sj.getJSONObject("expr")))
                        }
                    }
                DialogSay(
                    dialogId = json.getString("dialogId"),
                    segments = segments,
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            }
            "DialogChoice" -> {
                val optionsArray = json.optJSONArray("options") ?: JSONArray()
                val options =
                    (0 until optionsArray.length()).map { i ->
                        val oj = optionsArray.getJSONObject(i)
                        DialogOption(
                            label = oj.getString("label"),
                            body = deserializeList(oj.optJSONArray("body")) { deserializeOp(it) },
                        )
                    }
                DialogChoice(
                    dialogId = json.getString("dialogId"),
                    options = options,
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            }
            "MenuShow" ->
                MenuShow(
                    menuId = json.getString("menuId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "MenuHide" ->
                MenuHide(
                    menuId = json.getString("menuId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "HudShow" ->
                HudShow(
                    hudId = json.getString("hudId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "HudHide" ->
                HudHide(
                    hudId = json.getString("hudId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "PrintAt" ->
                PrintAt(
                    x = json.getInt("x"),
                    y = json.getInt("y"),
                    text = json.getString("text"),
                    fontMode =
                        FontMode.valueOf(json.optString("fontMode", FontMode.FIXED_WIDTH.name)),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "PrintCentered" ->
                PrintCentered(
                    text = json.getString("text"),
                    row = json.getInt("row"),
                    fontMode =
                        FontMode.valueOf(json.optString("fontMode", FontMode.FIXED_WIDTH.name)),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "PrintAligned" ->
                PrintAligned(
                    text = json.getString("text"),
                    row = json.getInt("row"),
                    alignment = TextAlignment.valueOf(json.getString("alignment")),
                    fontMode =
                        FontMode.valueOf(json.optString("fontMode", FontMode.FIXED_WIDTH.name)),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "ClearRegion" ->
                ClearRegion(
                    x = json.getInt("x"),
                    y = json.getInt("y"),
                    w = json.getInt("w"),
                    h = json.getInt("h"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "ScreenClear" ->
                ScreenClear(
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) }
                )
            "ScreenFill" ->
                ScreenFill(
                    tile = json.getInt("tile"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "SetPalette" ->
                SetPalette(
                    paletteName = json.getString("paletteName"),
                    slot = json.getInt("slot"),
                    type = PaletteType.valueOf(json.getString("paletteType")),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "PrintOp" ->
                PrintOp(
                    text = json.getString("text"),
                    values = deserializeList(json.optJSONArray("values")) { deserializeExpr(it) },
                    position = json.optJSONObject("position")?.let { deserializePosition(it) },
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "FadeOp" ->
                FadeOp(
                    fadeIn = json.getBoolean("fadeIn"),
                    frames = json.getInt("frames"),
                    after = deserializeList(json.optJSONArray("after")) { deserializeOp(it) },
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "SetVisible" ->
                SetVisible(
                    actorId = json.getString("actorId"),
                    visible = json.getBoolean("visible"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "SpawnActor" ->
                SpawnActor(
                    actorId = json.getString("actorId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "DestroyActor" ->
                DestroyActor(
                    actorId = json.getString("actorId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "PoolSpawnActor" ->
                PoolSpawnActor(
                    poolId = json.getString("poolId"),
                    x = deserializeExpr(json.getJSONObject("x")),
                    y = deserializeExpr(json.getJSONObject("y")),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "PoolDestroyActor" ->
                PoolDestroyActor(
                    poolId = json.getString("poolId"),
                    slotExpr = deserializeExpr(json.getJSONObject("slotExpr")),
                    deathCallbackOps =
                        deserializeList(json.optJSONArray("deathCallbackOps")) {
                            deserializeOp(it)
                        },
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "PoolForEachActive" ->
                PoolForEachActive(
                    poolId = json.getString("poolId"),
                    maxSize = json.getInt("maxSize"),
                    slotVarName = json.optString("slotVarName", "slot"),
                    body = deserializeList(json.optJSONArray("body")) { deserializeOp(it) },
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "PoolDestroyAll" ->
                PoolDestroyAll(
                    poolId = json.getString("poolId"),
                    maxSize = json.getInt("maxSize"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "AnimateOp" ->
                AnimateOp(
                    actorId = json.getString("actorId"),
                    animation = json.getString("animation"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "CameraOp" -> {
                val argsJson = json.optJSONObject("args") ?: JSONObject()
                val args = mutableMapOf<String, Expr>()
                for (k in argsJson.keys()) args[k] = deserializeExpr(argsJson.getJSONObject(k))
                CameraOp(
                    action = CameraAction.valueOf(json.getString("action")),
                    args = args,
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            }
            "WaitFrames" ->
                WaitFrames(
                    frames = json.getInt("frames"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "CallOp" ->
                CallOp(
                    function = json.getString("function"),
                    args = deserializeList(json.optJSONArray("args")) { deserializeExpr(it) },
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "ReturnOp" ->
                ReturnOp(
                    value = json.optJSONObject("value")?.let { deserializeExpr(it) },
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "MathOp" ->
                MathOp(
                    result = json.getString("result"),
                    op = MathFunction.valueOf(json.getString("mathOp")),
                    args = deserializeList(json.optJSONArray("args")) { deserializeExpr(it) },
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "RawOp" ->
                RawOp(
                    code = json.getString("code"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "GotoXYOp" ->
                GotoXYOp(
                    x = deserializeExpr(json.getJSONObject("x")),
                    y = deserializeExpr(json.getJSONObject("y")),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "PathfindStep" ->
                PathfindStep(
                    npcActorId = json.getString("npcActorId"),
                    targetActorId = json.getString("targetActorId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "WaypointStep" ->
                WaypointStep(
                    npcActorId = json.getString("npcActorId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "ActivatePuzzleObject" ->
                ActivatePuzzleObject(
                    objectId = json.getString("objectId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "DeactivatePuzzleObject" ->
                DeactivatePuzzleObject(
                    objectId = json.getString("objectId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "RevealPuzzleObject" ->
                RevealPuzzleObject(
                    objectId = json.getString("objectId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "HidePuzzleObject" ->
                HidePuzzleObject(
                    objectId = json.getString("objectId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "PhysicsStep" ->
                PhysicsStep(
                    actorId = json.getString("actorId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "SetAnimationState" ->
                SetAnimationState(
                    actorId = json.getString("actorId"),
                    stateName = json.getString("stateName"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "BindCurrentLevel" ->
                BindCurrentLevel(
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) }
                )
            else -> {
                System.err.println(
                    "WARNING: Unknown ScriptOp type '$type'; substituting RawOp placeholder"
                )
                RawOp("// unsupported op: $type")
            }
        }
    }

    // =========================================================================
    // Expr hierarchy serialization
    // =========================================================================

    internal fun serializeExpr(expr: Expr): JSONObject {
        val json = JSONObject()
        when (expr) {
            is Literal -> {
                json.put("type", "Literal")
                json.put("value", expr.value)
                json.put("sourceLocation", serializeSourceLocation(expr.sourceLocation))
            }
            is StringLiteral -> {
                json.put("type", "StringLiteral")
                json.put("value", expr.value)
                json.put("sourceLocation", serializeSourceLocation(expr.sourceLocation))
            }
            is VarRef -> {
                json.put("type", "VarRef")
                json.put("name", expr.name)
                json.put("sourceLocation", serializeSourceLocation(expr.sourceLocation))
            }
            is BinaryExpr -> {
                json.put("type", "BinaryExpr")
                json.put("left", serializeExpr(expr.left))
                json.put("op", expr.op.name)
                json.put("right", serializeExpr(expr.right))
                json.put("sourceLocation", serializeSourceLocation(expr.sourceLocation))
            }
            is UnaryExpr -> {
                json.put("type", "UnaryExpr")
                json.put("op", expr.op.name)
                json.put("operand", serializeExpr(expr.operand))
                json.put("sourceLocation", serializeSourceLocation(expr.sourceLocation))
            }
            is CallExpr -> {
                json.put("type", "CallExpr")
                json.put("function", expr.function)
                json.put("args", serializeList(expr.args, ::serializeExpr))
                json.put("sourceLocation", serializeSourceLocation(expr.sourceLocation))
            }
            is TernaryExpr -> {
                json.put("type", "TernaryExpr")
                json.put("condition", serializeExpr(expr.condition))
                json.put("thenExpr", serializeExpr(expr.thenExpr))
                json.put("elseExpr", serializeExpr(expr.elseExpr))
                json.put("sourceLocation", serializeSourceLocation(expr.sourceLocation))
            }
            is ArrayAccessExpr -> {
                json.put("type", "ArrayAccessExpr")
                json.put("array", expr.array)
                json.put("index", serializeExpr(expr.index))
                json.put("sourceLocation", serializeSourceLocation(expr.sourceLocation))
            }
            is PropertyAccessExpr -> {
                json.put("type", "PropertyAccessExpr")
                json.put("objectId", expr.objectId)
                json.put("property", expr.property)
                json.put("sourceLocation", serializeSourceLocation(expr.sourceLocation))
            }
            is CastExpr -> {
                json.put("type", "CastExpr")
                json.put("targetType", expr.targetType.name)
                json.put("inner", serializeExpr(expr.inner))
                json.put("sourceLocation", serializeSourceLocation(expr.sourceLocation))
            }
            is PoolGetActiveCount -> {
                json.put("type", "PoolGetActiveCount")
                json.put("poolId", expr.poolId)
                json.put("sourceLocation", serializeSourceLocation(expr.sourceLocation))
            }
            else -> {
                // Unknown / genre-specific expr — preserve type discriminator and class name
                json.put("type", "Unknown")
                json.put("class", expr::class.qualifiedName ?: expr::class.simpleName ?: "Unknown")
            }
        }
        return json
    }

    internal fun deserializeExpr(json: JSONObject): Expr {
        return when (val type = json.optString("type", "Unknown")) {
            "Literal" ->
                Literal(
                    value = json.getInt("value"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "StringLiteral" ->
                StringLiteral(
                    value = json.getString("value"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "VarRef" ->
                VarRef(
                    name = json.getString("name"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "BinaryExpr" ->
                BinaryExpr(
                    left = deserializeExpr(json.getJSONObject("left")),
                    op = BinaryOp.valueOf(json.getString("op")),
                    right = deserializeExpr(json.getJSONObject("right")),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "UnaryExpr" ->
                UnaryExpr(
                    op = UnaryOp.valueOf(json.getString("op")),
                    operand = deserializeExpr(json.getJSONObject("operand")),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "CallExpr" ->
                CallExpr(
                    function = json.getString("function"),
                    args = deserializeList(json.optJSONArray("args")) { deserializeExpr(it) },
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "TernaryExpr" ->
                TernaryExpr(
                    condition = deserializeExpr(json.getJSONObject("condition")),
                    thenExpr = deserializeExpr(json.getJSONObject("thenExpr")),
                    elseExpr = deserializeExpr(json.getJSONObject("elseExpr")),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "ArrayAccessExpr" ->
                ArrayAccessExpr(
                    array = json.getString("array"),
                    index = deserializeExpr(json.getJSONObject("index")),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "PropertyAccessExpr" ->
                PropertyAccessExpr(
                    objectId = json.getString("objectId"),
                    property = json.getString("property"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "CastExpr" ->
                CastExpr(
                    targetType = VarType.valueOf(json.getString("targetType")),
                    inner = deserializeExpr(json.getJSONObject("inner")),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            "PoolGetActiveCount" ->
                PoolGetActiveCount(
                    poolId = json.getString("poolId"),
                    sourceLocation =
                        json.optJSONObject("sourceLocation")?.let { deserializeSourceLocation(it) },
                )
            else -> {
                System.err.println(
                    "WARNING: Unknown Expr type '$type'; substituting Literal(0) placeholder"
                )
                Literal(0)
            }
        }
    }

    // =========================================================================
    // Generic utilities
    // =========================================================================

    private fun <T> serializeList(items: List<T>, serialize: (T) -> JSONObject): JSONArray {
        val arr = JSONArray()
        for (item in items) arr.put(serialize(item))
        return arr
    }

    private fun <T> deserializeList(arr: JSONArray?, deserialize: (JSONObject) -> T): List<T> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i -> deserialize(arr.getJSONObject(i)) }
    }

    private fun deserializeStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i -> arr.getString(i) }
    }
}
