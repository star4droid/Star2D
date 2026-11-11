# Path finding: NAVGRID, PathHolder, Smart Chase, Navigation blocks

Smart walkers find the shortest way around walls: enemies that chase the player through a maze, a guard on a patrol path, a unit that walks where the player taps. Works in 2D and 3D scenes.

## 1. The NAVGRID element
Add it with `elements` `{op:'add', type:'NAVGRID', name:'grid'}` (one per area; usually one per scene). It is an editor overlay: invisible in the game unless `Show In Game` is true.

| Property | Meaning |
|---|---|
| x, y, width, height | The area covered (2D: px, bottom-left corner like any element). 3D: x / z of the corner and the size in METERS, laid on the ground at y. |
| Cell Shape | `SQUARE` (4 or 8 directions) or `HEX` (6 directions, rounder paths). |
| Cell Width, Cell Height | Cell size. Smaller = more precise, more work. Keep cells about the size of a walker (32 px is a good start). HEX: a regular hexagon has height = width x 0.866. |
| Diagonal, Cut Corners | SQUARE only: 8 directions; whether diagonal steps may pass the corner of a wall. |
| Agent Size | Width of the walkers: paths keep this far from walls (0 = paths may touch walls). |
| Obstacles | Elements (comma separated) that block the cells they cover. Followed when they move; their cells open again when they are destroyed (doors, crates). |
| Use Static Bodies | At game start every STATIC physical element blocks its cells (fastest way to make a maze of BOX walls). |
| Cells | Painted walls and slow cells (see below). |

`Cells` text: `v1:<cols>,<rows>:` then runs `<count><letter>` row by row from the bottom-left cell: `o` open, `x` wall, `b`..`i` slow ground cost 2..9. Example `v1:4,2:3o1x4b`. Empty = all open. The user paints it in the editor (Properties > Paint cells: wall / erase / slow brushes, rectangles, "Walls from the static elements", "Walls around the edge"). Prefer `Use Static Bodies` or `Obstacles` over writing Cells by hand.

## 2. Walkers
- Behavior `SmartChase` (2D) / `SmartChase3D` (3D) on the enemy: `target` (the player), `navGrid` (empty = the scene's grid), `speed`, `sightRange`, `giveUpRange`, `flee`, `stopDistance`, `repathTime`, facing options. Events of the behavior: `onSeeTarget`, `onLoseTarget`, `onReachTarget`, `onNoPath`, `onPathBlocked`. Use `behaviors {action:'add'}`.
- Navigation blocks (category "Navigation"): `navGoTo` (walk to x,y), `navGoToElement`, `navChase`, `navFlee`, `navFollow` (path variable, mode ONCE / LOOP / PING_PONG), `navPause`, `navResume`, `navStop`, `navSetSpeed`, `navPlace`.
- Grid changes at run time: `navAddObstacle`, `navRemoveObstacle` (a door that opens), `navCloseCell`, `navOpenCell`, `navCellCost`, value `walkable at x y`, C-block `ifWalkable`.
- Physical walkers (DYNAMIC bodies) move by velocity and still collide; set `Fixed Rotation` true and gravity 0 in top-down games. Non-physical ones are moved directly.

## 3. Paths: the PathHolder variable type
A variable of type `PathHolder` (scene variable or element variable) holds a list of points.
- Draw it in the editor: Variables panel > PathHolder > Draw on the canvas (tap to add, drag to move, reverse, loop preview). Value text `x1,y1,x2,y2,...`.
- Fill it at run time: `navFindPath` (element to element) or `navFindPathXY` (point to point); the search may finish a few frames later: check `path ___ was found` / `no way in path ___`.
- Edit: `navPathAdd`, `navPathClear`, `navPathReverse`. Read: `length of path`, `points in path`, `x of point ___ of path ___`, `nearest point of path ___ to ___`, `distance left for ___`.
- Patrol: `navFollow` with mode `LOOP` (back to the first point) or `PING_PONG`.

## 4. Events
Scene events: `onPathEnd(current)` (ONCE path finished), `onNoPath(current)` (target unreachable), `onPathBlocked(current)` (the way got blocked; it re-plans by itself). `current` = the walker.

## 5. Recipe: tap to walk + chasing ghost (2D top-down)
1. Walls: BOX elements with type STATIC; NAVGRID covering the level with `Use Static Bodies` true, Cell Width/Height 32.
2. Player: DYNAMIC BOX, Fixed Rotation true. Scene `OnCreate`: `setWorldGravity 0 0`.
3. Scene event for the touch (a full-screen transparent UI BOX with `onTouchStart`): block `navGoTo` with element `player`, grid `""`, x `touch x`, y `touch y`, speed `150`.
4. Ghost: DYNAMIC BOX + behavior `SmartChase` with target `player`, speed 110. Its `onReachTarget` = game over.
5. A key that opens a door: door listed in `Obstacles`; on pickup `destroyAfter` the door (or `navRemoveObstacle`): walkers find the new way.
See the example project "Maze Chase" (square maze + hexagon scene).

## Pitfalls
- No NAVGRID in the scene = walkers do nothing (`onNoPath`). The grid must cover both the walker and the target.
- Cells bigger than the corridors close them; Agent Size larger than half a corridor closes it too.
- Do not destroy bodies inside collision events; use `destroyAfter` / `destroyLater`.
- 3D: the grid is flat on the ground (x/z); walkers keep their own height (dynamic bodies fall with gravity).
