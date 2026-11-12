# Star2D docs: map

Star2D is a 2D game-engine editor on an Android phone (LibGDX + Box2D). Do NOT read everything. The rules below are all you need to start; read only the part you are about to use: `read_docs {topic:'blocks'}`, `{topic:'elements', section:'JOYSTICK'}`, `{search:'sensor pickup'}`. A long topic answers with its table of contents first.

## Rules
1. The engine runs ONLY on the phone. Never compile/run/test anywhere else. The editor must stay open on screen.
2. Blocks first: logic = `events` with `blocks` (user-editable). Java only when no block fits.
3. Batch: many ops per call; every call costs shared quota. Check `status.scene_rev` before re-reading a scene. Change code with `files` search / read `from_line,to_line` / edit (and `events` edit), never by rewriting a file.
4. Units: editor px, origin BOTTOM-LEFT, y UP, x/y = element's bottom-left corner. Raw Box2D/Java: world = px * 0.25. Names: letters, digits, `_`. FIRST read `status.frame` (orientation + width x height) and keep every element inside it; a new scene copies the project orientation, pass `orientation:'portrait'` to `scenes create` for vertical games.
4b. Draw order: higher z = on top, UI is not automatically above the world. Background 0, ground 10, actors 20, effects 40, UI box 100, TEXT 110, JOYSTICK 120, never negative.
5. Event names. Scene: `OnCreate` `OnStep` `onCollisionStart` `onCollisionEnd` `OnPause` `OnResume`. Systems (scene events too): path finding `onPathEnd` `onNoPath` `onPathBlocked`, animation `onTimelineEnd` `onTimelineMarker` `onTweenEnd`, network `onPlayerJoined` `onPlayerLeft` `onGameFound` `onConnected` `onDisconnected` `onHostLeft` `onNetMessage` `onGameStarted` `onJoinFailed`. Element: `onClick` `onTouchStart` `onTouchEnd` `OnBodyCreated` `OnBodyUpdate` `onBodyCollided` `onBodyCollideEnd`.
6. Physics: first block of a physical scene's `OnCreate` is `setWorldGravity` (about 800 platformer, 0 top-down); positive velocity Y moves UP; no speed limit in blocks or behaviors (very fast bodies may tunnel: set Bullet true (CCD) and keep walls thick); call `physics_info` before choosing jump power, gravity, speeds or platform gaps; use scene collision events, `isSensor` pickups, `hide` + `destroyLater` to remove things.
7. Images: `create_image` `hero.png` -> element `image` `/hero.png`.
8. TEXT wraps if narrower than its content (about 10 px per letter x Font Scale): resize with `set`. A TEXT/PROGRESS without touch events is click-through: put the event on the BOX button only.
9. UI: `layout_check`, then `screenshot` `{target:'scene', region:{...}}`, fix with `elements set`.
10. Test: `run_game` returns when loaded (`ready:true`); `down` stays pressed until `up`/`release`; real time, 45 s per call. Verify with `screenshot`, `game_state`, `game_logs` before saying it works.

## Topics (read when)
- `overview`: folders on disk, how scenes/events become a game, what `build` does. First project contact.
- `scenes`: scene files, orientation, logic size, colors, several scenes; section `3D scenes` (OBJ3D/LIGHT3D/CAMERA3D in meters, settings, Bullet physics, glTF models, terrain, water, shaders, quality). Before creating scenes, and before ANY 3D work.
- `elements`: EVERY element type with every property, default and allowed values. Use `section:'<TYPE>'`.
- `physics`: body types, friction, sensors, filtering, gravity, speeds, units, signs, jump formulas, platformer checklist and "when it goes wrong". Before anything that moves.
- `events`: every event, exact names, when it fires. Before writing logic.
- `blocks`: block JSON format, `define_blocks`. Before the first `events` call.
- `blocks-examples`, `blocks-examples-2`: 15 verified block sequences (movement, jump, coins, shooting, timers, game over, scenes, saving, camera). Copy from here.
- `blocks-catalog`: every block (also `block_catalog` tool, with `search`).
- `variables`: scene variables, types, saving data.
- `ui-input`: TEXT sizing, buttons, designing UI (layout_check + screenshot loop), touch, keyboard, joystick, camera follow.
- `assets`: images, animations, sounds, particles, maps, fonts, paths, `create_image` / `create_sound`.
- `icons`: the `icons` tool: ~1800 ready icons in two sets (ui: heart, star, sound, arrows...; game: swords, potions, gems, monsters...) searchable by many English words, imported as PNG with size/colour. Before drawing any small symbol or item picture.
- `fonts`: the `fonts` tool: game fonts (.s2df) from 23 presets (outline, shadow, neon, gold, pixel, Arabic...), measuring text so it does not wrap. Before any title, score or menu text that should look styled, and for Arabic.
- `behaviors`: the `behaviors` tool: ready behaviors (scale touch effect, floating, spin, platformer player, joystick movement, shooter...), exclusive groups, public variables of scripts, behavior events. Before writing any movement or effect logic.
- `ui`: the `ui` tool: menus, HUDs, pause/settings/game-over screens and dialogs as `.ui` files (UI Maker): templates, widgets, themes, anchors, events, layout warnings, UIHOLDER. Before building any menu or HUD from BOX/TEXT elements.
- `features`: newer features in one page: Pivot, Y Sort, animation property, Auto Size, sprite-sheet frames (`region`), translations (`@key`), explain blocks, tileset merge. Before using any of them.
- `joints`: the `joints` tool, which types are safe.
- `groups`: the GROUP element and `parent`: children follow the parent (world/UI rules), hide/destroy cascade. Before building anything with moving parts (a car with wheels, a HUD panel).
- `prefabs`: reusable groups of elements saved as files: the `prefabs` tool (create, add, apply, revert, unpack, link), overrides, scripts. Before repeating the same enemy/UI piece.
- `navigation`: path finding: NAVGRID element (cells, obstacles, static walls), Smart Chase behaviors, Navigation blocks, PathHolder paths and patrols. Before any enemy that must find its way around walls, tap-to-walk, or patrols.
- `timeline`: keyframe animations (timelines/*.s2tl, Timeline panel, record mode) and tween blocks. Before animating doors, coins, menus, cut-scenes.
- `network`: local Wi-Fi multiplayer: NETWORK element, lobby, synced elements (net property), Network blocks and events. Before any multiplayer game.
- `java-api`: Java API, file locations, package rules (only for raw Java).
- `najma`: Najma (نجمة), the Arabic scripting language: `.njm` files next to the Java classes (syntax, events, sentences, functions, Arabic libGDX names). Only when the user wants Arabic code or the project has `.njm` files.
- `recipes`, `recipes-2`: complete small games as ordered tool calls (platformer, shooter, clicker, breakout, runner, ...). Read ONE recipe section.
- `testing`: `run_test` / `game_input` examples and limits.
- `pitfalls`: the mistakes that cost the most time (skim once).

## Minimal path for a new game
`status` -> `set_plan` -> `scenes` configure -> `create_image` -> `elements` (batch) -> `variables` -> `events` (batch) -> `run_test` -> fix -> `finish_work`. Read `blocks` + one `blocks-examples` entry before the first `events`, `elements` sections for the types you use, `physics` if bodies move.
