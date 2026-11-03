# Behaviors (author guide)

A behavior is a Java class that is attached to an element (`PlayerItem`) from Properties > **Additional behaviors**, from the AI (`behaviors` tool) or from Java (`item.addBehavior("Floating")`). Its `public` fields are the parameters the user edits; its methods are the logic.

## Writing one

```java
package com.star4droid.Game.Behaviors;          // project behaviors; built-ins use com.star4droid.template.behaviors.lib

import com.star4droid.template.behaviors.*;

@BehaviorInfo(name = "Wobble", icon = "wave", category = "Motion",
              targets = BehaviorInfo.Target.BOTH, desc = "Rotates back and forth.", events = {"onTurn(int count)"})
@Sets({"type=KINEMATIC"})                       // forced at run time only, never written to the saved properties
public class Wobble extends Behavior {
    @Param(desc = "Degrees each way.", unit = "°", min = 0, max = 180) public float angle = 15;
    @Header("Timing") public float speed = 2;
    @Hidden public int internalCounter;         // not shown in the inspector

    @Block(label = "set wobble angle")          // appears in the visual-blocks drawer (fork L)
    public void setAngle(float v) { angle = v; }

    public void onUpdate(float dt) { /* px, y up, dt in seconds */ }
}
```

* Project behaviors live in `<project>/java/com/star4droid/Game/Behaviors/<Name>.java` (package `com.star4droid.Game.Behaviors`). **New behavior** in the Files panel creates the skeleton. They are compiled with the game (Java 1.7 on the phone: no lambdas, no `var`).
* Built-ins live in `app/src/main/java/com/star4droid/template/behaviors/lib/`. After adding one run `python3 tools/gen_behavior_builtins.py` (regenerates `BuiltIns.java`) and `tools/make_template.py` (the on-device template).
* Runtime package `com.star4droid.template.behaviors` has no `android.*` imports.

### Lifecycle (override what you need)
`onStart()`, `onUpdate(float dt)` (only while the stage is playing), `onTouchStart(InputEvent)`, `onTouchEnd(InputEvent)`, `onClick()`, `onCollisionBegin(PlayerItem other)`, `onCollisionEnd(PlayerItem other)`, `onEnable()`, `onDisable()`, `onDestroy()`.
Fields: `item` (the element), `stage` (StageImp), `enabled`. Helpers: `body()`, `actor()`, `isUI()`, `findItem(name)`, `centerX/centerY/widthPx/heightPx`, `setCenter(x,y)`, `setVelocity(vx,vy)` (px/s, y up), `velocity()`, `isPressed(PlayerItem)`, `keyDown(int...)` + `KEY_*`, `overlaps(a,b)`, `time()`.

### Units
Editor pixels, y up; velocities in px/s (the Box2D world is px × 0.25: `Behavior.W`). The physics step count is handled by `setVelocity*`.

### Ordering
Per element: behaviors (list order, the user can reorder) → Body Script (`onX()`) → element events from the Events panel. Click/touch events follow the same order.

### Exclusive groups
`@Exclusive("movement")`: only one enabled behavior of a group per element. The inspector and the AI ask Replace / Keep both (the new one is added OFF). At run time `item.setBehaviorEnabled("Id", true)` turns the others of the group off. `enabled` is persisted as `enabled` in the element's behaviors JSON.

### Requirements and sets
`@Requires(body = true, type = "DYNAMIC", note = "...")`, `@Requires(ui = true)`: the inspector shows a warning icon and an "Apply to element properties" button; never an error. `@Sets({"gravityScale=0","fixedRotation=true","type=KINEMATIC","bullet=true","linearDamping=..","angularDamping=..","sensor=true"})` applies at start while enabled. **Never rewrite the saved element properties from a behavior**: keep offsets (like `Floating`/`Spin`) and restore in `onDisable()`/`onDestroy()`.

### Public variables (parameters)
Every `public`, non-static, non-final field becomes a parameter: int/long/short/byte/float/double/boolean/String, `Color` (`libgdx.graphics.Color` or a `String` annotated nothing: hex `#RRGGBB[AA]`), enums (nested or imported), `@Image String` (project image path), `@Sound String`, **element references** (a field typed `PlayerItem`, `TextItem`, `ProgressItem`, `Joystick`, `BoxBody`... filtered by type in the picker), and `List<T>` / arrays of any of them. Defaults come from the field initializer (simple literals and `new Color(..)`/`Color.RED` are read; anything else falls back to the type's zero). Annotations `@Param(desc,label,min,max,step,unit)`, `@Header("Group")`, `@Hidden`.
The same rule applies to Body Scripts (`Events/.../Script.java`) and the scene script: their public fields appear in Properties (Script variables) and Variables (scene script). Scripts have a wider rule (package-private, `static`, `@Expose private`, auto-detect when the code editor is closed, list editor, UI holder nodes): see **SCRIPTVARS.md**.

### Events a behavior raises
Declare them in `@BehaviorInfo(events = {"onShoot(PlayerItem bullet)"})` and call `fire("onShoot", bullet)`. The user fills them in the Events panel under **Behavior events**; storage: `Events/<scene>/private/<element>/behaviors/<Id>/<event>.java` (+ `.visual`). Generated code: `__b.on("onShoot", new Behavior.Handler(){ public void run(PlayerItem current, Object[] args){ PlayerItem bullet = (PlayerItem) args[0]; ... } });`.

### Runtime API (Java / blocks)
`item.addBehavior(Behavior)`, `item.addBehavior("Id")`, `item.removeBehavior("Id")`, `item.hasBehavior("Id")`, `item.getBehavior("Id")`, `item.setBehaviorParam("Id","field",value)`, `item.getBehaviorParam("Id","field")`, `item.setBehaviorEnabled("Id", boolean)`, `item.isBehaviorEnabled("Id")`. `@Block` methods of the behavior are the block drawer's entries.

## Storage
* Element property `behaviors`: `[{"type":"Floating","params":{"amplitudeY":20},"enabled":true}]`.
* Element property `scriptVars`: `{"speed":420,"hpBar":"HpBar","enemies":["e1","e2"],"tint":"#FF8800"}` (element references are element names).
* Scene script variables: `Events/<scene>/public/scriptvars.json` (same map format). Both maps may carry a bookkeeping key `"$auto"` (fields found in the code, see SCRIPTVARS.md); it is never a value.
* Both element properties are listed in `assets/items/default.json` (needed so they survive load/save) and skipped by the code generator's reflection.
* Generated code (after every element exists, before the scene `OnCreate`): per behavior a block `{ Fqn __b = new Fqn(); __b.field = ...; __b.on(...); __b.attach(item, this); }`, then `((ScriptClass) item.getScript()).field = ...;` for script variables.

## Sharing: `.s2b`
A zip with `<Name>.java`, `behavior.json` (id, name, version, author, description, …) and optional `images/`, `sounds/`. Export from the info dialog of a project behavior; import from the Files panel menu (Import behavior). Importing shows the code warning, copies the assets, fixes the package. No Android intent-filter is registered for `.s2b` (import goes through the Files panel).

## Tests
`python3 tools/test_behaviors.py` compiles the Android-free editor core against the template, cross-checks the source scanner against reflection for every built-in, and compiles generated code. Compile the app with `ANDROID_HOME=/opt/android-sdk bash gradlew :app:compileDebugJavaWithJavac :template:compileDebugJavaWithJavac`.

## Blocks from behaviors (`@Block`) and the behavior-control blocks

### Two kinds of blocks
* **Fixed, always visible** (palette category *Behaviors*, in `BlockCatalog`): `addBehavior`, `removeBehavior`, `enableBehavior`, `disableBehavior`, `setBehaviorEnabled`, `setBehaviorParam`; values `___ has behavior ___`, `___ has behavior ___ turned on`, `number | text | yes/no setting ___ of behavior ___ on ___`, `behavior ___ of ___`. They call the runtime API of `PlayerItem` (`addBehavior("Id")`, `removeBehavior`, `hasBehavior`, `getBehavior`, `setBehaviorEnabled`, `isBehaviorEnabled`, `setBehaviorParam`, `getBehaviorParam`; the number / text / yes-no readers go through `BehaviorOps`). The behavior field is a picker of every known behavior (`BehaviorCatalog.all`).
* **Conditional** (generated from `@Block` methods): shown only while the scene uses the behavior.

### `@Block` (package `com.star4droid.template.behaviors`)
`@Block(label, desc, category, labelAr, descAr, args)` on a PUBLIC non-static method. Only `label` is required. `args` names the parameter slots (otherwise: names from the source for project behaviors, a name derived from the type for built-ins, whose parameter names are not kept at runtime).

| Java type | Slot |
|---|---|
| `int long short byte float double` | number |
| `boolean` | condition (hexagon) |
| `String` | text |
| `PlayerItem`, `Joystick`, `ProgressItem`, `TextItem`, `BoxBody`... | element picker |
| `Color` (libgdx) | hex text `RRGGBBAA` |

`void` -> action block; `boolean` -> condition value; number -> number value; `String` -> text value; an element class -> element value. Other parameter / return types are ignored (the method just has no block). The first slot of every block is the element that owns the behavior (`current` by default). Generated code, e.g. `PlatformerPlayer.jump`:

```java
{
    com.star4droid.template.behaviors.lib.PlatformerPlayer __bhv = (com.star4droid.template.behaviors.lib.PlatformerPlayer) current.getBehavior("PlatformerPlayer");
    if (__bhv != null) __bhv.jump();
}
```
Value blocks: `((X) e.getBehavior("X")) != null ? ((X) e.getBehavior("X")).isGrounded() : false` (default `false` / `0` / `""` / `null`). If the element does not have the behavior nothing happens.

Block identity (saved in `.visual`): the title `<BehaviorId>.<method>` (overloads: `Health.damage(float,float)`). The palette text is `label` (`labelAr` in Arabic); changing the label never breaks saved events, changing the method name does.

### Where they come from
* Built-in behaviors: reflection (`BehaviorBlocks.fromClass`).
* Project behaviors (`<project>/java/com/star4droid/Game/Behaviors/*.java`): a textual scanner (`BlockSourceScanner`; the classes are not compiled while the user edits them). Re-read when the file changes (stamp = modified + length), so editing the source updates the palette on return from the code editor.
* A project behavior with the same id as a built-in replaces its blocks.

### When are they visible?
A behavior's blocks are shown when **any element of the scene of the open event** uses the behavior (property `behaviors` in `scenes/<scene>`; scene read from `.../Events/<scene>/...`; unknown scene = every scene of the project), **or** the open event already contains one of its blocks, **or** it contains an `addBehavior` block that names it (attached at runtime). The AI's `block_catalog` follows the same rule for the open scene (`scene:` to choose, `all:true` for everything, `hidden_behavior_blocks` names the hidden ones). Descriptions: `desc` / `descAr` appear on the (i) button and in `block_catalog {detail:true}`.

### Recommended `@Block` methods for the library behaviors (annotate them when written)
| Behavior | Action blocks | Value blocks |
|---|---|---|
| PlatformerPlayer | `jump`, `setSpeed(float)`, `setJumpSpeed(float)`, `setMaxJumps(int)`, `enableDoubleJump(boolean)`, `setUseKeyboard(boolean)`, `setCoyoteTime(float)` | `isGrounded`, `getSpeed`, `getJumpSpeed` |
| Shooter | `shoot`, `setFireRate`, `setBulletSpeed`, `setBulletSize`, `setDamage`, `setRange`, `setAutoFire(boolean)`, `setAutoAim(boolean)`, `addEnemy(PlayerItem)`, `removeEnemy`, `clearEnemies`, `addPowerUp(PlayerItem)` | `getFireRate`, `getDamage` |
| Health | `damage(float)`, `heal(float)`, `setMaxHealth(float)`, `setHealth(float)`, `setInvincible(boolean)`, `kill()`, `revive()` | `getHealth`, `getMaxHealth`, `isDead`, `getHealthPercent` |
| Spawner | `spawn()`, `start()`, `stop()`, `setInterval(float)`, `setMaxAlive(int)`, `setTemplate(PlayerItem)` | `getSpawnedCount`, `isRunning` |
| Car / vehicle | `accelerate()`, `brake()`, `steerLeft()`, `steerRight()`, `setMaxSpeed(float)`, `setSteerSpeed(float)` | `getSpeed`, `isMoving` |
| Follow / chase | `setTarget(PlayerItem)`, `setFollowSpeed(float)`, `setStopDistance(float)`, `stopFollowing()` | `getDistanceToTarget`, `isFollowing` |
| Patrol | `setSpeed`, `reverse()`, `setPauseTime(float)` | `isWaiting` |
| Collectible | `collect()`, `setValue(int)`, `setRespawn(boolean)` | `isCollected`, `getValue` |
| Timer / cooldown | `start()`, `stop()`, `reset()`, `setDuration(float)` | `getTimeLeft`, `isRunning`, `isFinished` |
| Camera follow | `setTarget(PlayerItem)`, `setSmoothness(float)`, `shake(float)` | `isShaking` |
| Draggable | `setEnabledDrag(boolean)`, `snapBack()` | `isDragging` |
| Score / counter | `add(int)`, `reset()`, `setMax(int)` | `getValue`, `isMax` |
| Spin / Floating / Touch effect | see the annotated exemplars (`setSpinSpeed`, `reverse`, `setClockwise`, `setAmplitude`, `setFrequency`, `setPressedScale`...) | `isClockwise`, `getSpinSpeed`, `getAmplitude` |

Guidelines: label = what the user would say ("set run speed", not "setSpeed"); one verb per block; always `desc` (+ `descAr`, `labelAr` for built-ins); put the *state* in public fields (the inspector) and only *actions / queries* in `@Block`; a getter named like the field is a value block ("run speed").

## Block descriptions (creator / AI)
Every custom block can carry a description in several languages, shown by the (i) button of the block (and in `block_catalog {detail:true}`). Stored next to the block in `Nodes/nodes.json` (action / C-blocks) and `Nodes/blocks.json` (value blocks):
```json
{"title":"Move up","code":"...","fields":[...],"color":-123,"s2":{...},
 "desc":{"en":"Moves the element up.","ar":"يحرك العنصر للأعلى."}}
```
Keys are language codes (lower case, any code); a plain string `"desc":"text"` is read as English; a block without `desc` (older files) simply has none. The popup uses the app language, else `en`, else the first language written. Generated behavior blocks use `desc` (en) and `descAr` (ar) from the annotation. The block creator edits it as rows *(language, text)*: language chooser en / ar / fr / es / tr / pt / ru / de + "other code", add / remove rows; the AI sends `description` (string or map) in `define_blocks`.

## Tests
`python3 tools/test_blocks_behaviors.py` (needs `:template:compileDebugJavaWithJavac` once): reflection == source scanner for every built-in `@Block`, the sample `tools/blocks_test/samples/Health.java` (comments, string concatenation, Arabic, overloads, unsupported types), the scene filter, descriptions (multi-language / old files), and it compiles the generated Java of every block (including the fixed Behaviors blocks read out of `BlockCatalog.java`).

## Translating behavior text (editor UI)
Every place that shows a behavior's text goes through `ui/behaviors/BehaviorText` (name, desc, group, param label / description, event). The English text of the annotation is the default; built-in behaviors look up `Tr.t(key, default)`, project and imported (`.s2b`) behaviors show their own text.

| What | Key |
|---|---|
| display name / description | `bhv_n_<Id>` / `bhv_d_<Id>` |
| parameter label / description | `bhv_p_<Id>_<param>` / `bhv_pd_<Id>_<param>` |
| event name (falls back to the engine's event translation, then to "On death" from `onDeath`) | `bhv_e_<Id>_<event>` |
| category and `@Header` group (non alphanumerics become `_`) | `bhv_g_<Group>` (e.g. `bhv_g_Who_can_pick_it_up`) |
| note of `@Requires` / exclusive group name | `bhv_note_<Id>` / `bhv_xg_<group>` |
| `@Sets` key names | `bhv_set_<key>` (gravityScale, fixedRotation, type, bullet, sensor, linearDamping, angularDamping) |
| UI chrome of the info dialog, the Add dialog and the rows | `bhv_*` (grep `Tr.t("bhv_` in `ui/behaviors`) |

The first block of a behavior event in the block editor ("▶ When hit") and its title bar use the same event name (`BehaviorEvents.displayName`); the event's arguments (`onShoot(PlayerItem bullet)`) are offered as draggable values next to `current`.

## 3D behaviors

3D scenes have their own behavior library (`behaviors/lib/*3D.java`). They are marked
`@BehaviorInfo(targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D", ...})`:

* **Target `THREE_D`** = elements of the 3D world, editor TYPE `OBJ3D`, `LIGHT3D` or `CAMERA3D` (`ElementTypes.THREE_D_TYPES`;
  `CAMERAVIEW` is a 2D / UI element). `types` narrows it (Flicker 3D: `LIGHT3D` only). The source scanner reads both, so project
  behaviors can be 3D too (Files > New behavior > 3D writes a 3D skeleton).
* **Fit rules** (`BehaviorMeta.misfit(bodyType, TYPE)`, `BehaviorText.misfit(m, env)`): a 3D behavior on a 2D / UI element and a
  2D behavior on a 3D element are flagged; the Add dialog of a 3D element opens on the **3D** chip (All shows the 2D ones flagged),
  a 2D / UI element never lists 3D behaviors; the AI's `behaviors add` refuses a 2D/3D mismatch unless `force:true`, and
  `behaviors list {target:'3d'}` lists them. `BehaviorEnv.is3D()` comes from the element's TYPE.
* **Helpers** in `Behavior`: `obj()` (Object3D), `camera3d()`, `light3d()`, `is3D()`, `world3d()`, `mainCamera3d()`, `pos3(item, out)`.
  `Support3D` has the shared parts: input (`readMove`), headings (front = local -Z, yaw 0 looks along -Z), ground height (physics
  raycast + terrains), `Mover` (walk with gravity / steps / walls, DYNAMIC or not, also a CAMERA3D), `Walker` (go to a point),
  `Clips` (crossfaded animation clips), sounds in space and looping sounds, `Prompt` (a 2D button / text shown near an object; several
  objects can share one USE button: the nearest wins).
* Units: meters, seconds, degrees, km/h where it says so. Element references are `PlayerItem` fields; an empty player / camera
  means the main camera (the first-person player).

| Behavior | What |
|---|---|
| FirstPersonController3D | Joystick walk + drag / second joystick look, jump, run, head bob, footsteps; on a capsule (DYNAMIC) or on a CAMERA3D |
| ThirdPersonController3D | Moves relative to the camera, turns to the move, jump / run, idle-walk-run-jump clips; orbit-touch or behind camera |
| CharacterAnimator3D | Picks idle / walk / run / jump / fall clips from the measured speed, crossfades, action clips |
| Car3D | Bullet raycast vehicle: gas / brake / reverse / steer / handbrake drift (buttons, joystick, tilt, keys), km/h, suspension, wheel nodes of a GLB ("wheel-front-left"...) or wheel elements, flip reset, engine sound, follow camera |
| Traffic3D | AI car along waypoint lanes, stops for cars / player, honks, spins wheel nodes, despawns far away |
| EndlessRoad3D | Recycled road tiles toward -Z, side props, lane traffic, pickups, levels (adds Car3D top speed), distance / score, crash events |
| PathFollower3D | Waypoint elements: loop / ping-pong / once, on the ground or flying, waits; camera fly-throughs |
| Patrol3D | Walk between points or wander, stop and look around, clips; pauses while Chase3D chases |
| Chase3D | Sight range + field of view + line of sight, hearing, chase, attack (Health3D damage), search, go home |
| ClickToMove3D | Tap the ground to walk there (ignores taps on 2D controls), marker element |
| PlatformMover3D | Kinematic platform / elevator between stops, waits or waits for a call, carries riders (also a first-person camera) |
| JumpPad3D | Launches what steps on it (bodies, controllers, first-person camera) up / forward |
| Rotator3D, Floater3D, Oscillator3D, Swing3D, Orbit3D | Spin; bob + spin + sway; back and forth; pendulum; circle a center |
| Billboard3D, LookAtTarget3D | Face the camera; turn toward a target in range (objects, cameras, lights), idle sweep |
| Interactable3D | 2D USE button + prompt near the object (look check), tap object / E, onInteract, outline highlight |
| Door3D | Hinge (opens away from the player) / slide / model clips, key item from the Inventory, auto close, sounds, events |
| Pickup3D | Walk-over or button pickup into the Inventory, flies to the player, respawn |
| Seat3D | Sit / stand: camera glides to the seat, movement controller paused, look while seated |
| Trigger3D | Box / radius zone, watched elements / name prefix / camera, enter / exit events, once |
| Health3D | Damageable 3D hit points: invincibility, regen, 2D bar / text, hurt clip / shake / knockback, death clip / hide / destroy |
| Shooter3D, Projectile3D | Hitscan or projectile copies, camera aim, spread, rate, inventory ammo, impact effects; bullets that never tunnel |
| Spawner3D, Lifetime3D | Timed copies of templates (area, limits, throw, lifetime); remove after N s with shrink / fade |
| Respawn3D | Back to start / checkpoint after a fall or death |
| Flicker3D, Flashlight3D | Candle / fire / broken / pulse / strobe light (+ glow object); spot light on the camera with toggle button and battery |
| Footsteps3D, Buoyancy3D, CameraOrbitTouch3D | Step sounds from movement; float on WATER (exact or physical rocking); drag-orbit + pinch-zoom camera |

Runtime API added for them: `Object3D.getYaw3/setYaw3/getBottomY/isDynamic/lockRotation3/applyForceAt/applyImpulseAt/velocityAt/
getAngularVelocityY3/setNodeOffset/resetNode/getNodeCenter/getNodeSize/localToWorld` and `d3/Vehicle3D` (Bullet raycast vehicle
wrapper, no Bullet types in its API).

Test: `python3 tools/test_lib_3d.py` (conventions of every `*3D` behavior, the 2D / 3D fit rules, and a headless smoke run).
