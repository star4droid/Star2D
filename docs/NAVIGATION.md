# Path finding (NavGrid)

User / AI guide: `app/src/main/assets/ai/docs/navigation.md` (served by `read_docs {topic:'navigation'}`). This file is the map of the code.

## Runtime (`com.star4droid.template.nav`, shared with the exported games)
| Class | Role |
|---|---|
| `NavGrid` | Square or pointy-top hex cells over an area (2D: editor px, y up; 3D: meters on the ground x/z). Painted walls, costs 1..9, obstacle counters, agent-size inflation, A* (octile / hex heuristic, cost-weighted), path smoothing, `encode()/decode()` of the `Cells` property (`v1:cols,rows:` + run-length `o` / `x` / `b..i`). |
| `NavGridItem` | The NAVGRID element at run time: builds the grid from its def, adds `Obstacles` and (option) every STATIC body via `NavShapes`, draws it when `Show In Game`. |
| `NavWorld` | Per-scene state: grids, obstacles, agents and the search queue (`Nav.searchesPerFrame` per frame; queued paths are PENDING). Updated by `StageImp` before `onDraw` (`Nav.update`). |
| `NavAgent` | Walks one element: follow (ONCE / LOOP / PING_PONG), chase (re-plans when the target changes cell, on `repathTime`), flee. Bodies move by velocity, others by position; 3D keeps the vertical speed. Raises `onPathEnd`, `onNoPath`, `onPathBlocked`. |
| `NavObstacle` | Element that blocks the cells it covers; re-stamped when it moves, released when destroyed. |
| `NavShapes` | Cells covered by Box2D shapes / polygons / circles / segments (editor "walls from static elements" and runtime). |
| `PathHolder` | Path value type (points + state FOUND / PENDING / NO_PATH). `PathHolder.of(x1, y1, ...)` for drawn paths. |
| `Nav` | Static facade for blocks / scripts (`findPath`, `goTo`, `chase`, `follow`, `addObstacle`, `setCell`, `walkable`, path reporters...). |

Behaviors: `behaviors/lib/SmartChase.java` (2D), `SmartChase3D.java` (3D) on top of `NavAgent`.

## Editor
- Element: `assets/items/navgrid.json` (+ `types.json`, `map.json` section `Navigation`, `spinners.json` `Cell Shape`), `editor/items/EditorNavGridItem.java` (2D overlay, badge selection), `editor/three/NavGrid3D.java` (3D ground overlay + picking).
- Painting: `editor/NavPaint.java` (a `CanvasTool`: brushes wall / erase / slow, rectangles, from static, border, invert, clear; undo through `EditorAction`), panel `ui/NavPaintPanel.java`. Two fingers always pan / zoom.
- Paths: variable type `PathHolder` (Variables panel and element variables) drawn with `editor/PathDraw.java` + `ui/PathDrawPanel.java`; generated as `PathHolder.of(...)` by `ObjectVarsGen` / the scene generator.
- Blocks: `ui/blocks/BlocksSystems.java` category Navigation (21 statements / C-blocks + reporters), tested by `tools/test_blocks_systems.py`.
- Scene events `onPathEnd`, `onNoPath`, `onPathBlocked`: `behaviors/SceneEventsExt.java`, overrides added by `Helpers/CodeGenerator`.

## Tests
`python3 tools/test_nav.py` (A*, hex, costs, obstacles, agent size, encode/decode), `tools/test_blocks_systems.py`, `tools/gen_systems_examples.py --check` (example MazeChase compiles against the template and phone class paths).
