# Scenes: files, config, orientation, logic size, switching

## What a scene is

A scene is one screen/level: its own elements, events, joints and variables. Files for scene `scene1`:

- `scenes/scene1`: JSON array, one object per element, each with ALL its properties as strings or numbers (see `elements`).
- `configs/scene1.json`: scene settings (below).
- `Events/scene1/...`, `joints/scene1/...`: logic and joints.

`scene1` is the main scene (the game starts there) and cannot be renamed or deleted. Use lowercase names without spaces for other scenes (`level2`, `gameover`): the generated class is `com.star4droid.Game.<name lowercased>`.

## The `scenes` tool

```json
{"action":"list"}
{"action":"create","name":"level2","orientation":"portrait"}
{"action":"create","name":"level3","config":{"sceneColor":"#263238"}}
{"action":"create","name":"world","type":"3D","config":{"Lighting Preset":"SUNSET"}}
{"action":"open","name":"level2"}
{"action":"configure","name":"scene1","config":{"sceneColor":"#87CEEB","logicWidth":1280,"logicHeight":720,"or":"landscape"}}
{"action":"copy","name":"level1","new_name":"level2"}
{"action":"rename","name":"level2","new_name":"level3"}
{"action":"delete","name":"level3"}
```

`create`, `copy`, `rename`, `open` leave the new scene OPEN; `elements`, `events`, `variables`, `joints` act on the open scene unless you pass `scene`. Unknown config keys are ignored (reported in the answer).

`create` copies the orientation and logic size of the PROJECT (read from its config files, scene1 first) unless you pass `orientation` ("landscape" or "portrait"). The answer of `create`, `open`, `configure`, `status`, `project_info` and `scene_dump` states the frame in plain words, for example `scene 'level2': PORTRAIT, 720 wide x 1280 tall (x 0..720, y 0..1280, origin bottom-left, y up)`. Trust that sentence, not an assumption. An exported game has ONE screen orientation: keep all scenes the same unless you really mean it (the answer warns when they differ).

## Config keys (`configs/<scene>.json`)

| Key | Meaning |
|---|---|
| `sceneColor` | Background color, `#RRGGBB` or `#RRGGBBAA`. |
| `Ambient Light` | Color of the ambient light, default `#FFFFFF` (white = lights have no visible effect). Use a DARK color (e.g. `#202030`) when the scene uses LIGHT elements. |
| `or` | `landscape` (also stored as empty string) or `portrait`. |
| `logicWidth` | The LONG side of the logic screen in pixels. |
| `logicHeight` | The SHORT side of the logic screen in pixels. |
| `sceneType` | `3D` for a 3D scene (set by `create {type:'3D'}`, read-only afterwards); missing = 2D. The 3D settings are in "3D scenes" below. |

Light quality keys (only if you really need them): `Enable Blur`, `Blur Number`, `Enable Culling`, `Gamma Correction`, `Enable Shadows`, `Use Diffuse Light`. They are written by the editor's light settings dialog.

## Orientation and logic size (important)

- The **logic screen** is the coordinate space of your elements, in pixels. The game is scaled to fit the phone screen (letterboxed, aspect ratio kept). Pick ONE size and design everything for it.
- Landscape: game width = `logicWidth`, height = `logicHeight`. Example `1280 x 720`.
- Portrait: width = `logicHeight` (short side), height = `logicWidth` (long side). So a portrait 720 x 1280 game is `{"or":"portrait","logicWidth":1280,"logicHeight":720}`. In other words `logicWidth` is always the long side.
- `configure` always stores the larger number as `logicWidth` (long side). If `status` shows `logic_size_warning` (stored width < height, the frame looks reversed), call `configure` with `{"swap_size":true}`. `logic_size` in `status` is the game screen as the game sees it (already swapped in portrait).
- If the keys are missing the editor uses the phone's screen size. Always set both explicitly.
- Coordinates: origin at the **bottom-left** of the logic screen, x to the right, y UP. Visible area is x in [0, width], y in [0, height] (before any camera movement).
- Check the result with `scene_dump` and a `screenshot` (target `editor`, or `game` while running). The portrait runtime path is less tested than landscape: always verify a portrait game on screen.

## Several scenes

- Each scene is independent: its own physics world, variables, events. Typical games: `scene1` (menu or level), `level2`, `gameover`.
- Switch with the block `OpenScene` (field `scene`, name WITHOUT quotes) or Java `openScene("level2");`. Opening a scene again restarts it from scratch.
- Scene variables are reset when a scene starts. To keep a score or best score across scenes or runs use the block `saveData` and the reporter `saved number ___` (see `variables`).
- `finish` (block) ends the current scene; on the main scene it closes the game.
- The `scenes` tool `open` only changes which scene the EDITOR shows. To start the game from another scene use `run_game` with `scene`.

## A "game over" or menu scene pattern

Create the scene with `scenes create`, then add a TEXT and a BOX button there with `elements` (pass `scene`), and give the button an `onClick` event with the block `OpenScene`. See `recipes`.

## Using UI elements for a background

Elements with `type: "UI"` are drawn on a fixed screen layer (not affected by the camera). A BOX with `type: "UI"`, `x:0, y:0` and the full logic size is a good static background. Use it only when you do not move the camera; otherwise make the background a normal element (`type: "STATIC"`, low `z`) that is big enough.

## 3D scenes

A scene is 2D (default) or 3D. Make a 3D one with `{"action":"create","name":"world","type":"3D"}` (any orientation). It is stored as `"sceneType":"3D"` in `configs/<scene>.json`; the kind cannot be changed later (make a new scene). `status`, `scene_dump`, `project_info`, `layout_check` and `physics_info` report `sceneType`. A new 3D scene starts with a ground `Plane`, a `Cube` and an active `Camera` (rename, change or delete them).

A 3D scene is a normal scene with a 3D world drawn FIRST and the 2D layer on top: same events (OnCreate, OnStep, element onClick / onTouchStart / onTouchEnd by tapping the 3D object, OnBodyCreated, OnBodyUpdate, onBodyCollided / onBodyCollideEnd, scene onCollisionStart / onCollisionEnd), variables, scripts, behaviors, sounds and scene switching. 2D and 3D scenes mix freely in one game (2D menu -> 3D level).

### Units and axes
- 3D elements (`OBJ3D`, `LIGHT3D`, `CAMERA3D`): **meters**. x right, **y UP**, z toward the viewer of the default camera (the default camera sits at z +8 looking toward -z). The ground is y 0; an object stands on it when `y` = half its height (a 1 m cube: y 0.5).
- `Rotation X/Y/Z` in degrees (applied Y, then X, then Z). `Rotation Y` turns left/right (yaw), `Rotation X` tilts (pitch, negative = look down).
- `Scale X/Y/Z` = the size in meters for primitives (CUBE 1 = 1 x 1 x 1 m; PLANE 10 x 10 = a 10 m floor; a person ~ CAPSULE 0.6 x 0.9 x 0.6). For models it multiplies the model's own size.
- `z` of a 3D element is a depth (may be negative), NOT a draw order: the z policy, `order` op, bounds and pixel layout checks do not apply to 3D elements.
- The 2D layer keeps PIXELS (status.frame): HUD, JOYSTICK, TEXT, BOX buttons with type UI, UIHOLDER screens, CAMERAVIEW. Rule 8 (UI = .ui in an UIHOLDER) applies unchanged; the holder is drawn over the 3D picture.

### Elements
| TYPE | What | Key properties |
|---|---|---|
| `OBJ3D` | shape, model, terrain or water | `Mesh` CUBE SPHERE CYLINDER CAPSULE CONE PLANE TORUS RAMP STAIRS PYRAMID HEMISPHERE TUBE WEDGE QUAD DISC EMPTY MODEL TERRAIN WATER; `x y z`, `Rotation X/Y/Z`, `Scale X/Y/Z`; `Color`, `Texture` (/name.png), `Texture Repeat X/Y`, `Opacity`, `Emissive` + `Emissive Intensity`, `Metallic`, `Roughness`, `Shininess`, `Unlit`, `Double Sided`; `Cast Shadows`, `Receive Shadows`, `Outline` (+ Color, Width), `Billboard`, `Static Object` (never moves: merged, faster), `Draw Distance`, `LOD Distance`, `Shader`; physics (below) |
| `LIGHT3D` | extra light | `Light Kind` POINT (bulb: `Range` m) / SPOT (`Cone Angle`, `Cone Softness`, shines along its rotation: `Rotation X` -90 = down) / DIRECTIONAL (a second sun: only the rotation matters); `Color`, `Intensity`, `Cast Shadows` (expensive: one or two), `Flicker` 0..1 + `Flicker Speed`, `Attach To` (follows that element: a flashlight on the player) |
| `CAMERA3D` | a camera | `Active` true = the main camera (one); `FOV` (60), `Near`/`Far` (m), `Orthographic` + `Ortho Size`; `Follow` element + `Follow Mode` NONE THIRD_PERSON FIRST_PERSON ORBIT TOP_DOWN SIDE LOOK_AT, `Follow Distance`, `Follow Height`, `Follow Smooth`, `Look Height`, `Follow Angle`; `Touch Look` (drag to turn) + `Look Sensitivity`; `Shake` |
| `CAMERAVIEW` | 2D element (both scene kinds) showing a camera live: mirror, minimap, rear view | `Camera` (CAMERA3D/CAMERA name, "" = main), `Zoom`, `Resolution` 0.25..1, `Refresh Rate` (0 = every frame), `Touch`, `Border Color/Width`, `Corner Radius`, `Opacity`; px like UI |

Add them with `elements` (`{"op":"add","type":"OBJ3D","name":"crate","props":{"Mesh":"CUBE","x":2,"y":0.5,"z":0,"Color":"#A0522D","Body":"DYNAMIC"}}`). Shape names work as the type: `{"op":"add","type":"SPHERE","name":"ball","props":{"y":3}}`, also `MODEL`, `TERRAIN`, `WATER`, `LIGHT3D` (`Light Kind`), `CAMERA3D`, `CAMERAVIEW`; in a 3D scene `LIGHT` and `CAMERA` mean the 3D ones. All properties with defaults: read_docs `elements`, sections `OBJ3D`, `LIGHT3D`, `CAMERA3D`, `CAMERAVIEW`. The 2D-only types (BOX, CIRCLE...) in a 3D scene are 2D-layer elements.

### Scene settings (`scenes configure`, keys of `configs/<scene>.json`)
| Key | Values |
|---|---|
| `Render Mode` | SIMPLE (fast default) SOFT TOON (+ `Toon Bands` 2..6) PBR (realistic, slower) UNLIT (flat colors) |
| `Lighting Preset` | DAY SUNSET NIGHT OVERCAST STUDIO HORROR INDOOR CARTOON CUSTOM. A preset sets sun, ambient, sky and fog for you. ONLY with CUSTOM are these used: `Ambient Color`, `Ambient Intensity`, `Sun` (true/false), `Sun Color`, `Sun Intensity`, `Sun Pitch` (-55: degrees, negative = from above), `Sun Yaw`, `Sky Top`, `Sky Bottom`, `Fog` (true/false), `Fog Color`, `Fog Near`, `Fog Far` (m) |
| `Sky` | GRADIENT (Sky Top -> Sky Bottom) COLOR IMAGE (`Sky Image` /name.png, a panorama) |
| `Shadows`, `Shadow Distance` | sun shadows on/off, how far they are drawn (m, 40) |
| `Gravity X/Y/Z` | m/s², default 0, -9.81, 0 (moon -1.6, floaty 0) |
| `Quality` | LOW MEDIUM HIGH ULTRA: the default graphics level (render scale, view distance, shadow size, per-pixel light, water detail, post effects). The player's own choice (3D Quality blocks) is saved and wins |
| `Post Effect` | NONE VIGNETTE NIGHT_VISION GRAYSCALE SEPIA HORROR CHROMATIC PIXELATE BLOOM_LITE UNDERWATER DREAM (screen effect on the 3D picture, under the HUD); `Post Params` its values |
| `Exposure` | brightness multiplier (1) |

Example: `{"action":"configure","config":{"Lighting Preset":"SUNSET","Render Mode":"SOFT","Post Effect":"VIGNETTE","Gravity Y":-9.81}}`. Wrong values are reported and ignored. `sceneColor`, `logicWidth/Height`, `or` keep their 2D meaning (the 2D layer).

### Physics (Bullet, meters)
Only elements with `Body` other than NONE take part: STATIC (ground, walls), DYNAMIC (falls, is pushed: `Mass` kg), KINEMATIC (moved by code/tweens, pushes dynamics, e.g. moving platforms, doors), TRIGGER (no push, only collision events: pickups, checkpoints, damage zones). `Collider` AUTO (fits the Mesh; models: MESH when STATIC, BOX when moving) BOX SPHERE CAPSULE CYLINDER CONE MESH (exact triangles, STATIC only) CONVEX (hull, for models that move); `Collider Scale X/Y/Z` and `Collider Offset X/Y/Z` adjust it. `Friction`, `Restitution` (bounce), `Linear Damping`, `Angular Damping`, `Gravity Scale`, `Lock Move X/Y/Z`, `Lock Rotation X/Y/Z` (a character: CAPSULE, DYNAMIC, Lock Rotation X and Z true so it never tips over), `Continuous Collision` (fast bullets), `Sleep`, `Layer` + `Ignore Layers` (comma separated names that do not collide).
The 2D physics rules do NOT apply: no `setWorldGravity` block (gravity = `Gravity Y` setting), no 450 px/s limit, no px. Typical: walk 3-6 m/s, run 8-10, jump 4-7 m/s (1-2.5 m), car 15-40 m/s. `physics_info` in a 3D scene answers in meters. Move bodies with the 3D Physics blocks (velocity, impulse, force, grounded, raycast) or tweens for KINEMATIC; moving a DYNAMIC body by setting its position every frame fights the physics.

### Models (glTF / GLB)
Put the file in `models/` (`import_asset {"path":"models/car.glb","url":"https://..."}`; a `.gltf` needs its `.bin` and textures next to it). Use it with `Mesh` MODEL + `Model` "/car.glb" (path inside models/). `Model Node` shows only one node of the file; `Keep Model Material` false replaces its colors with `Color`/`Texture`. Animations of the file: `Animation` = clip played at start ("" none, "*" the first), `Animation Loop`, `Animation Speed`; at runtime the 3D Animation blocks play / cross-fade / play once / pause / stop and list the clip names (`B3.animationNames(item)` in Java). FBX/OBJ are not supported: convert to GLB. Scale big models down with `Scale X/Y/Z` (many exports are in centimeters: 0.01).

### Terrain and water
- `Mesh` TERRAIN: `Scale X/Z` = size in m (60 x 60), `Terrain Height` (max m), `Terrain Noise` 0..1 (random hills; 0 = flat unless a heightmap), `Terrain Seed`, `Terrain Resolution` (grid, 64), `Heightmap` (gray /image.png: white = high), `Color`/`Texture` + `Texture Repeat`. `Body` STATIC + `Collider` MESH (or AUTO) to walk on it; `B3.groundHeight(terrain, x, z)` gives the ground y at a point.
- `Mesh` WATER: `Scale X/Z` size, `Water Color`, `Water Deep Color`, `Foam Color`, `Foam`, `Wave Height` (m), `Wave Speed`, `Wave Length`, `Wave Direction` (degrees), `Water Clarity` 0..1, `Water Resolution`, `Water Quality` AUTO LOW MEDIUM HIGH. `Buoyancy` > 0: DYNAMIC bodies in it float (boats, crates). Usually `Body` NONE and `Cast Shadows` false.

### Shaders
`Shader` of an OBJ3D: "" (the scene's Render Mode), a ready one `builtin:glow` `builtin:hologram` `builtin:dissolve` (`u_amount` 0..1) `builtin:wave` `builtin:rim` `builtin:toon` `builtin:gradient` (`u_top` color) `builtin:pulse` `builtin:xray` `builtin:flag` `builtin:lava` `builtin:ghost`, or a project file `shaders/NAME` = `shaders/NAME.glsl` (write it with `files` write). The file holds only functions, the engine writes the rest:
```glsl
uniform float u_speed;   // from Shader Params "speed=2"
uniform vec4 u_tint;     // from Shader Params "tint=#FF0000"
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos) {
    return vec4(color.rgb * lambert(normal) * u_tint.rgb, color.a);
}
// optional: moves the vertices
vec3 displace(vec3 pos, vec3 normal) { return pos + normal * sin(u_time * u_speed) * 0.05; }
```
Ready to use: `u_time`, `u_color` (element Color), `u_texture` + `hasTexture()`, `u_lightDir`, `u_lightColor`, `u_ambient`, `u_cameraPos`, `lambert(normal)`. `Shader Params` "a=1;tint=#ff0000;dir=1,0,0" become uniforms `u_a` (float), `u_tint` (vec4), `u_dir` (vec3); blocks can change them at runtime. GLSL ES 2.0 (`float` literals with a dot: `1.0`). A screen effect of your own: `B3.setPostCode(code)` (or its 3D Effects block) with `vec4 post(vec4 color, vec2 uv)` (u_time, u_texture, u_resolution, u_amount); `B3.setPostEffect("HORROR", 0.7f)` switches a ready one at runtime.

### Graphics quality and performance
Phones are slow: prefer primitives and low-poly GLB, `Static Object` true for scenery that never moves, few `Cast Shadows` lights (the sun's shadows are enough), `Draw Distance` / `LOD Distance` for far details, SIMPLE or SOFT render mode, `Quality` MEDIUM. The 3D Quality blocks change the level at runtime (LOW..ULTRA, render scale, view distance, shadow quality, fps limit, water quality) for a settings screen.

### Logic in 3D
Use the 3D block categories (3D Motion, 3D Physics, 3D Looks, 3D Animation, 3D Camera, 3D Lights Sky, 3D Effects, 3D Quality, 3D Terrain Water, 3D Objects, 3D Sound) - `block_catalog {search:'3D'}`. Java: static helpers `com.star4droid.template.d3.B3` (`B3.move(item,x,y,z)`, `B3.setVelocity`, `B3.applyImpulse`, `B3.isGrounded`, `B3.lookAtItem`, `B3.follow(camera,target,"THIRD_PERSON")`, `B3.playAnimation(item,"Run",true,1)`, `B3.raycastScreen(sx,sy)` + `B3.rayHit()`, `B3.spawn(item,"copy",x,y,z)`, `B3.destroy(item)`...). The 2D Box2D blocks (setVelocityX, jump, setWorldGravity...) do nothing to 3D elements. Touch: a JOYSTICK (2D layer) read every frame in OnStep to move the player; the camera's `Touch Look` turns the view.

### Inventory (2D and 3D)
The Inventory blocks keep items the player carries for the whole game (all scenes): add item `id` count, remove, has item, item count, item at n, inventory size, empty inventory (`B3.addItem("key",1)`, `B3.hasItem("key")`). Typical: a TRIGGER coin's onBodyCollided -> add item "coin" 1, destroy it; show the counts in an UIHOLDER HUD (ui build `inventory` / `hud` screens).
