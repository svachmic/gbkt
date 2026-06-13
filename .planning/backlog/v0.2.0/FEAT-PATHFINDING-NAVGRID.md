---
id: FEAT-PATHFINDING-NAVGRID
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 DSL implementation milestone
scope: medium
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md#section-10"
triage_date: 2026-06-12
---

# FEAT-PATHFINDING-NAVGRID: navGrid() builder, findPathTo infix, weighted tiles, Heuristic enum, dynamic obstacles

## Source

Removed from context/DSL_REFERENCE.md lines 1824–1983 (commit removal-commit-TBD).

**Implemented today:** `PathfindingBuilder` in `gbkt-lang/.../dsl/SystemBuilders.kt:315` with `gridSize(px)`, `mapSize(widthTiles, heightTiles)`, `maxOpenNodes(count)`, `maxPathLength(length)`. Script-level ops: `pathfindStep(npc: ActorRef, target: ActorRef)` at ScriptBuilder.kt:611 and `waypointStep(npc: ActorRef)` at ScriptBuilder.kt:633. What is NOT implemented: `navGrid("arena") { size = 16 x 16; blocked(...) }` builder, `navGrid(from = dungeonMap) { blockedTiles(...) }` tilemap form, `weight(x, y, cost = n)` tile weighting, `player findPathTo treasure using navGrid` infix, `player.findPathTo(treasure).using(navGrid) { diagonal = true; heuristic = Heuristic.MANHATTAN }`, path result API (`path.found`, `path.hasNext`, `path.directionX()`, etc.), `enemy.followPath(path) { speed = 2; onArrive { } }`, `navGrid.addObstacle(enemy)` / `removeObstacle()`, `Heuristic.MANHATTAN` / `CHEBYSHEV` / `EUCLIDEAN` enum.

## Why This Matters

A declarative `navGrid` builder with infix `findPathTo` queries and dynamic obstacle updates would make enemy AI and NPC routing accessible without touching C. The current `pathfindStep(npc, target)` is low-level and requires the caller to manage grid setup externally.

## When to Surface

**Trigger:** v0.2.0 DSL implementation milestone — after the doc reconciliation is done and the framework is stable.

## Verbatim removed content

### Navigation Grid Setup

Define which tiles are walkable for pathfinding:

```kotlin
// Manual definition
val navGrid = navGrid("arena") {
    size = 16 x 16
    default = true        // All tiles walkable by default
    blocked(0..15, 0)     // Top wall
    blocked(0..15, 15)    // Bottom wall
    blocked(0, 0..15)     // Left wall
    blocked(15, 0..15)    // Right wall
    blocked(8, 8)         // Obstacle in center
}

// From tilemap (auto-extract from Tiled map)
val navGrid = navGrid(from = dungeonMap) {
    blockedTiles(0, 1, 2)  // Wall tile indices are blocked
}

// With collision layer from Tiled
val navGrid = navGrid(from = dungeonMap) {
    collisionLayer = "Collision"  // Use Tiled layer name
}
```

### Weighted Tiles

Give tiles different movement costs for more realistic pathfinding:

```kotlin
val navGrid = navGrid("dungeon") {
    size = 16 x 16
    default = true

    // Swamp area is slow
    weight(4..8, 4..8, cost = 3)  // 3x slower than normal

    // Road is fast
    weight(0..15, 8, cost = 1)  // Normal speed

    // Impassable walls (cost = 0 means blocked)
    blocked(0..15, 0)
}
```

### Pathfinding Queries

Find paths between entities or tiles:

```kotlin
gameplayScene = scene("gameplay") {
    frame {
        // Fluent infix syntax
        val path = player findPathTo treasure using navGrid

        // Or with options
        val path2 = player.findPathTo(treasure).using(navGrid) {
            diagonal = true   // Allow 8-way movement
            maxDepth = 64     // Search limit
            heuristic = Heuristic.MANHATTAN  // or CHEBYSHEV, EUCLIDEAN
        }

        // From/to tile coordinates
        val path3 = findPath(fromTileX = 0, fromTileY = 0, toTileX = 15, toTileY = 15)
            .using(navGrid)
    }
}
```

### Following Paths

Move entities along computed paths:

```kotlin
gameplayScene = scene("gameplay") {
    frame {
        val path = enemy findPathTo player using navGrid

        whenever(path.found and path.hasNext) {
            // Move toward next waypoint
            enemy.x += path.directionX(enemy.x)  // Returns -1, 0, or 1
            enemy.y += path.directionY(enemy.y)

            // Advance when waypoint reached
            whenever(path.atWaypoint(enemy, threshold = 4)) {
                path.advance()
            }
        }
    }
}

// Or use automatic path following
frame {
    val path = enemy findPathTo player using navGrid

    enemy.followPath(path) {
        speed = 2
        onArrive { /* reached destination */ }
        onBlocked { /* path blocked */ }
    }
}
```

### Path State Queries

Check path state with conditions:

```kotlin
whenever(path.found) { /* valid path exists */ }
whenever(path.notFound) { /* no valid path */ }
whenever(path.hasNext) { /* more waypoints remain */ }

// Path properties (as Expr)
val len = path.length       // Total waypoints
val idx = path.currentIndex // Current waypoint index
val nextX = path.nextX      // Next waypoint X (tiles)
val nextY = path.nextY      // Next waypoint Y (tiles)
```

### Dynamic Obstacles

Modify navigation at runtime:

```kotlin
frame {
    // Block tile where enemy stands (pixels → tiles automatic)
    navGrid.addObstacle(enemy)

    // Later, clear it
    navGrid.removeObstacle(enemy)

    // Or by tile coordinates
    navGrid.setBlocked(8, 8)
    navGrid.setWalkable(8, 8)

    // Change movement cost
    navGrid.setWeight(x = 5, y = 5, cost = 3)

    // Check if tile is walkable
    whenever(navGrid.isWalkable(tileX, tileY)) {
        // Tile is passable
    }
}
```

### Heuristics

Choose the distance calculation method:

- `Heuristic.MANHATTAN` - |dx| + |dy| - Best for 4-way movement (default)
- `Heuristic.CHEBYSHEV` - max(|dx|, |dy|) - Best for 8-way movement
- `Heuristic.EUCLIDEAN` - sqrt(dx² + dy²) - Most accurate but slower
