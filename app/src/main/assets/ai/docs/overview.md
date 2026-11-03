# Overview: project structure and how a game is built

## The pieces

A Star2D **project** is a folder on the phone. It holds **scenes** (levels/screens). A scene has **elements** (sprites, text, joysticks...), **events** (logic, as visual blocks or Java), optional **joints** and **variables**. When you build, the app generates one Java class per scene, compiles all of it on the phone and runs it with LibGDX + Box2D. A scene can also be **3D** (`scenes create {type:'3D'}`): 3D elements OBJ3D / LIGHT3D / CAMERA3D in meters with Bullet physics and glTF models, drawn under the normal 2D layer (HUD, UI holders). See `scenes`, section "3D scenes".

## Folders on disk (relative to the project root; the `files` tool uses these paths)

| Path | Content |
|---|---|
| `scenes/<scene>` | JSON array, one object per element (all its properties). No file extension. |
| `configs/<scene>.json` | Scene settings: orientation, logic size, background color, ambient light. See `scenes`. |
| `Events/<scene>/public/<Event>.visual` / `.java` | Scene events (OnCreate, OnStep, ...). `.visual` = the blocks, `.java` = the generated Java. |
| `Events/<scene>/private/<script>/<event>.visual` / `.java` | Element events (onClick, ...). `<script>` is the element's `Script` property (default = element name). |
| `Events/<scene>/public/variables.json` | The scene's variables. See `variables`. |
| `joints/<scene>/<name>-<Type>` | One file per joint. See `joints`. |
| `scripts/<scene>/*.java` | Custom Java members pasted into the scene class (see `java-api`). |
| `java/` | Your own Java classes (compiled with the game). See `java-api`. |
| `images/` | PNG/JPG sprites, `.tmx` maps. Referred to as `/name.png` (relative to `images/`). |
| `sounds/` | Sound effects and music (wav/mp3/ogg). Referred to by file name. |
| `anims/<animation>` | Sprite animations (JSON, no extension). See `assets`. |
| `files/` | Particle `.p` files and other data. |
| `models/` | 3D models `.glb` / `.gltf` (3D scenes). Referred to as `/name.glb` in an OBJ3D's `Model`. |
| `shaders/` | Shader snippets `NAME.glsl` of 3D objects (`Shader` = `shaders/NAME`). |

Do NOT edit by hand (generated): `java/com/star4droid/Game/<scene>.java`, `dex/`, `bin/`, `logs/`, `Events/JavaChanges.json`, `Events/*/UndoRedo.json`. Prefer the dedicated tools (`elements`, `events`, `scenes`, `variables`, `joints`) over `files` for anything they cover; they keep the editor in sync. After you change files with `files`, the app refreshes.

A new project has one scene, `scene1` (the main scene, the game starts there), and the empty folders `sounds`, `anims`, `images`, `files`, `icon`.

## How logic becomes code

1. Blocks you write with `events` are saved as `.visual` files. The app turns every block into Java (each block has a Java template, see `blocks-catalog`).
2. For each scene the generator writes a class `com.star4droid.Game.<scene in lowercase>` that extends the engine's `StageImp`. Inside it:
   - every element becomes a field `PlayerItem <name>` (so you can write `player.getBody()` in blocks and Java),
   - every scene variable becomes a field,
   - scene events become `OnCreate`, `OnStep` (generated as `onDraw`), collision callbacks, ...,
   - element events become anonymous event objects attached to the element.
3. `OnCreate` runs once after ALL elements and joints exist.
4. The Java is compiled at **source level 7**: no lambdas, no `var`, no streams. A local variable used inside a timer/anonymous class must be `final`. See `pitfalls`.

## What `build` does

`build` regenerates the Java of all scenes and compiles it. The result is `ok` or a list of errors with file and line. Common errors: a typo in a raw Java field, a non-`final` variable used in a timer block, an element name that is not a valid Java identifier or collides with an engine member (`world`, `steps`, `playing`, `project`, `finished`, `preferences`, `GameStage`, `UiStage`; also avoid Java keywords and names ending in `_def`). `run_game` builds and starts the game on the phone; `run_test` can do both and then play a scenario.

## Typical working order

1. `scenes` configure (orientation, logic size).
2. Create sprites with `create_image` and sounds with `create_sound` (`assets`).
3. `elements` add everything in one call (`elements`).
4. `variables` for score/lives/flags (`variables`).
5. `events` for OnCreate (gravity!), OnStep, collisions, clicks (`events`, `blocks`).
6. `run_test`, look at screenshots, fix, repeat.

## Scene runtime facts

- Game cameras show the scene's logic screen: x from 0 to logic width, y from 0 to logic height (pixels), origin bottom-left.
- Elements with `type` UI live on a separate screen-fixed layer (HUD, joystick, background); all other elements live in the physics world.
- 3D scenes: the 3D world (Bullet physics, meters, y up) is drawn first, then the 2D world and UI layers on top in pixels as above.
- `openScene("name")` (block `OpenScene`) switches scene; variables of the old scene are gone, use `saveData` / `setValue` to keep data (see `variables`).
