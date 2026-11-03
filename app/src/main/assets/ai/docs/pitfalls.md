# Pitfalls: the mistakes that cost the most time

Read once before the first build. Each item says what happens and what to do.

## Units and physics

1. **Forgot `setWorldGravity`.** The engine runs the physics 6 times per frame by default, so px/s values feel 6x too fast. First block of every physical scene's `OnCreate`: `{"block":"setWorldGravity","fields":{"strength":800}}` (0 for top-down).
2. **World units vs pixels.** `elements`, `moveBy`, `goTo`, `setVelocityX/Y`, `jump`, `spawnCopy` and the reporters `x of`, `y of`, `distance`, `touch x` use pixels. `setTransform`, `setX`, `setY`, `setAngle`, `setLinearVelocity`, `apply*`, all `setCamera*` blocks and `game_state` use world units (px * 0.25). Mixing them puts things at a quarter or four times the intended place.
3. **Default `restitution` is 0.5 and `friction` 0.** Characters bounce and ground is icy. Set `restitution: 0` (and `friction` 0.2 to 0.5 for ground) explicitly.
4. **`Fixed Rotation`** false lets a character tip over on collisions. Set it true for characters.
5. **Sensors fall.** A DYNAMIC `isSensor` body still has gravity. Use `STATIC` for coins/triggers or `Gravity Scale: 0`.
6. **Tunneling at high speed.** Blocks and behaviors have no speed limit, but very fast small bodies can pass through thin walls: set `Bullet: true` (CCD) on them, make walls at least 20 px thick, and check the real speed with `game_state` 
7. **`type: UI` boxes have no physics body.** They never collide and body blocks (`goTo`, `setVelocityX`, `x of ___`) on them throw. Use UI only for HUD, buttons, backgrounds.

## Collisions and destroying things

8. **Do not use the element events `onBodyCollided` / `onBodyCollideEnd`.** The engine runs the first body's handler twice and never the second's. Use the scene events `onCollisionStart` / `onCollisionEnd` with `body1` / `body2` and test both orders.
9. **Never destroy/create/deactivate bodies inside a collision event**, and never destroy the same element twice (a second destroy can crash the game). Use `hide` plus the helper block `destroyLater` (see `blocks`).
10. **`Collision` property** = names of elements that must NOT collide with this one (no push, no events). It is not a "collide only with" list. Clones use their TEMPLATE's name in that filter.
11. **Reporters/blocks that compare names** (`___ is named ___`, `___ touches ___`) work with the element name: for spawned clones use unique names with a shared prefix and `startsWith` in a raw condition.
12. **Clone from the original**, not from clones; give every clone a unique name (`join "bullet" shots`).

## Blocks and Java

13. **Java 7**: no lambdas/streams/`var`. Locals used in timers (`waitSeconds`, `everySeconds`, `_DoAfter`, `_repeatEvery`, `destroyAfter`) must be `final`. `current`, `body1`, `body2`, `event` and locals from `spawnCopy` are NOT final: copy them (`codeLine` `final PlayerItem me = current;`) or use `destroyLater`.
14. **Raw fields are Java.** `setText` / `showText` / `Toast` / `debug` / `setVariable` values need quotes for text: `"\"Score: \" + score"` in JSON. Typed fields (`scene`, `image`, `sound`, `animation`, element pickers) must NOT have quotes.
15. **Java types.** An `int` variable cannot take a float (`x of ___`, `speed of ___`): use `to int ___`. Float variables need `f` (`0.5f`) in the `variables` tool. `setGravityScale` and `setGravity` take raw Java (`0.5f`, `-9.8f`).
16. **Unusable blocks**: `saveValue` and `setsplits` do not compile; `setAlpha` and `moveTowardBody` do nothing. Use `saveData`, `setWorldGravity`, `setOpacity`, `moveToward`. `void` is only for custom script files.
17. **`events set` replaces the whole event.** `get` first when you extend an existing one. Do not edit `Events/**/*.java` with `files` when a `.visual` twin exists: use `events`.
18. **Event variable names**: `current` only exists in element events; scene events use element names, `body1`/`body2` exist only in collision events.
19. **`OpenScene` from `OnStep`** is called every frame: guard it with a variable. Timers (`everySeconds`) of the old scene keep running after `OpenScene`: guard their body with a flag variable (`alive`).
20. **`OnBodyCreated`** may not run for elements defined in the scene: put initialisation in the scene's `OnCreate`.
21. **Keyboard** has no block: `Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.LEFT)` in an `ifThen`. Always add touch controls too.
22. **Not imported** in event code: `Color`, `MathUtils`, `Align`, `Input.Keys` (write full names).

## Elements and assets

23. **Wrong image path shows the Star2D logo.** Use `/hero.png` (relative to `images/`), not `images/hero.png` or `/images/hero.png`. Use the `use_as` value returned by `create_image`.
24. **Size mirrors**: setting `width`/`height`/`radius` also sets the collider size. Check `get_elements`. A CIRCLE uses `radius` (the square `2*radius`), not `width/height`.
25. **`x`,`y` are the bottom-left corner**, y up. `x of ___`/`y of ___` and `goTo`/`spawnCopy` use the CENTER. An element at `y: 0` is on the bottom edge.
26. **Property spelling is exact**: `Fixed Rotation`, `Gravity Scale`, `isSensor`, `Collider Width`, `Text Color`, `Font Scale`. Body type `KINAMATIC` is spelled that way.
27. **Names**: letters/digits/`_`, not starting with a digit; no Java keywords; do not reuse an element name as a variable name; avoid `world`, `steps`, `playing`, `project`, `finished`.
28. **Portrait**: `logicWidth` is the LONG side (see `scenes`). Verify with a screenshot.
29. **Text**: default font is small and ASCII only (`Font Scale` 3 to 6 on 1280 wide); text is left-aligned in its box; HUD text needs a high `z`.
30. **Joystick** default size is 50: set 300. Do not use `angle (deg) ___` (inverted). `CAMERA` element does nothing: use camera blocks (`cameraFollow` sets once: call it every frame in `OnStep`, or `cameraFollowX/Y(player)` once in Java).
31. **Animations**: `setAnimation` restarts at frame 0 on every call (call it only on state change); frame names have no leading slash.
32. **Joints**: `RopeJoint`, `MouseJoint`, `GearJoint` fail to compile. Distance joint `length` is computed from the anchors.
33. **`z` ordering**: higher z is drawn on top; UI and world elements are sorted TOGETHER, so UI is not automatically above the game; equal z is drawn in creation order (the later one covers the earlier one, so a background created last hides everything). Convention: background 0, scenery 5, ground/platforms 10, actors 20, effects 40, UI box 100, TEXT/PROGRESS 110, JOYSTICK 120; never negative (it crashes the editor). When you give no z, the `elements` tool sets the role z for you (and raises elements still at z 0 when a background is added). `layout_check` reports `background_covers`, `background_ties`, `hidden_behind`, `ui_under_world`. A MAP is always at z 0.
34. **Persistent data** (`saveData`) survives runs: tests that depend on a clean state must write the value first.

## Text and touch

- A TEXT too narrow for its content wraps onto several lines (see the `ui-input` section "Text size"). Resize with `set`; the `elements` reply warns about it.
- A TEXT/PROGRESS label WITHOUT touch events is click-through: put `onClick`/`onTouchStart` on the BOX button only. A TEXT that has its own touch event captures the touch.
- The test finger: `down` stays pressed until `up`/`release`; a game still on its loading screen is waited for by the tools, you do not need `wait`.

## Editing code: patch, don't rewrite

Never send a whole script again to change a few lines (slow, costs shared quota, risks losing code). `files` `search` finds the lines (`{"op":"search","path":"java","pattern":"onHit","context":2}`), `read` with `from_line`/`to_line` shows just that part, `edit` replaces exact text (`{"op":"edit","path":"java/Game.java","edits":[{"old":"speed = 200","new":"speed = 260"}]}`; `old` must match once or set `all:true`; all edits apply or none). For raw-Java events use `events` `{"op":"edit",...,"edits":[...]}` the same way. `write` is for new files.

## Process

35. Batch: one `elements` call for a whole scene, one `events` call for all events, one `run_test` for a whole scenario.
36. Never claim success without `build` ok + `run_test`/`screenshot` + `game_logs` without `runtime_errors`.
37. The game runs only on the phone; never run or compile anything yourself. Keep the app visible. Stop the running game (`stop_game`) before editing again.
38. Call `finish_work` when done (it unlocks the editor for the user).

## 3D scenes

39. **2D rules on 3D elements.** OBJ3D/LIGHT3D/CAMERA3D are in METERS (y up, z depth): a cube at `x:400` is 400 m away. Typical sizes 0.5-10, a level 20-200 m. Pixels stay for the 2D layer only (HUD, JOYSTICK, UIHOLDER, CAMERAVIEW).
40. **No setWorldGravity / Box2D blocks in 3D.** Gravity is the scene setting `Gravity Y` (-9.81); move 3D bodies with the 3D Physics / 3D Motion blocks (m/s). `jump`, `setVelocityX`, `goTo` act on 2D bodies only.
41. **Nothing moves or collides.** `Body` is NONE by default: set STATIC for floors/walls (the ground PLANE too), DYNAMIC for things that fall, TRIGGER for pickups. A DYNAMIC object placed inside the ground (`y` < half its height) jumps out or falls through.
42. **Black or invisible scene.** No CAMERA3D with `Active` true, the camera inside an object, or the object behind the camera (default camera at z +8 looks toward -z). `layout_check` reports a missing active camera; check with `screenshot`.
43. **Fog / ambient / sky colors ignored.** They only apply with `"Lighting Preset":"CUSTOM"`; the other presets set them.
44. **Characters tip over.** DYNAMIC CAPSULE + `Lock Rotation X` and `Lock Rotation Z` true; turn it with Rotation Y (blocks), not torque.
45. **Slow game.** Many shadow-casting lights, PBR, huge models, MESH colliders on moving bodies. Prefer SIMPLE/SOFT, one sun, `Static Object` for scenery, low-poly GLB, Quality MEDIUM.
46. **Model not found.** The file must be in `models/` and `Model` written "/name.glb" (Mesh MODEL); FBX/OBJ are not supported.

## build vs run_game
`run_game` compiles AND starts the game in one call. Do not call `build` and then `run_game`: that compiles twice. Use `build` alone only to check compile errors when you do not want the game to open.
