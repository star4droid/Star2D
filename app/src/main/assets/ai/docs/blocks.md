# Blocks: the JSON format for `events`, and `define_blocks`

Logic is a list of visual blocks. You send them as JSON in `events` (`blocks`). The app turns them into Java; the user can open and edit them in the block editor. **Fifteen worked, verified examples are in `blocks-examples` (1 to 8: setup, movement, jump, pickups, patrol, chase) and `blocks-examples-2` (9 to 15: shooting, timers, lives/game over, saving, camera, animation). Copy their shape.** Titles and fields come from `blocks-catalog` (or the `block_catalog` tool, which also lists the user's custom blocks).

## The format

`blocks` is an array of **statement blocks**; **array order = execution order**.

```json
{"block":"<title from the catalog>",
 "fields":{"<field label>": <value>, ...},
 "do":   [ ...blocks... ],     // only for [container] blocks
 "else": [ ...blocks... ]      // only for containers that list ", else" (if, ifCollision, ifTouching, ifJoystickMoved, try=catch part)
}
```

- `<field label>` is the field name WITHOUT its type suffix: the catalog line `setImage(body:Body, image:image)` means `"fields":{"body":"player","image":"hero_jump.png"}`. Labels may also be given as an array in field order: `"fields":["player","hero_jump.png"]`.
- Omitted fields keep the catalog defaults (e.g. `moveBy` defaults: body `current`, dx 10, dy 0).
- `<value>` is either a **string/number/boolean** (exactly what a user would type into the block's field) or a **value block** (reporter): `{"r":"<exact display text>","args":[<value>, ...]}`. `args` fill the `___` slots in order and can nest: any arg can again be a string or a reporter. The number of args must equal the number of `___` in the display text.
- Raw Java fallback: `{"block":"codeLine","fields":{"code":"score += 10;"}}`. Shortcuts accepted by the tool: `{"java":"..."}` or just a string in the array. Use Java only when no block fits.
- `"current"` in an element field means the element that owns the event (element events). In scene events there is no `current`: write the element's name.

## What you type in a field (this decides if it compiles)

Plain fields (no type shown) are **raw Java expressions**, pasted as they are:

| You want | Type |
|---|---|
| a number | `10`, `0.5`, `-300` (floats are cast by the block; no `f` needed unless the catalog says "raw Java") |
| a variable | `score` |
| text | `"Score: "` WITH the quotes, or `"Score: " + score` |
| a condition | `score > 10 && lives > 0` |
| a reporter | `{"r":"x of ___","args":["player"]}` |

Typed fields (shown as `name:Type` in the catalog):

- `body:Body` (also `item`, `ui`, `joystick`, `progress`, `target`): an **element name** (`player`) or `current`. No quotes. Can also be a Java expression returning a PlayerItem (`findItem("coin1")`).
- `x:Boolean`: a Java boolean expression (`true`, `false`, `lives > 0`) or a boolean reporter.
- `image:image`, `sound:sound`, `animation:animation`, `scene:scene`: the **plain file/scene name WITHOUT quotes** (`hero.png`, `jump.wav`, `walk`, `level2`); the block adds the quotes. Images: with or without a leading `/`. Sounds: the file name inside `sounds/`.
- Reporter args that are element slots (`x of ___`) take the plain element name (no quotes). Reporter slots of the text reporter `"___"`, `saved number ___`, `___ is named ___` (second slot) take plain text and add quotes themselves.

Value blocks produce Java types: arithmetic and `x of ___` give numbers; comparisons, `and/or/not`, `___ touches ___`, `___ is named ___` give booleans; `join`, `to string`, `"___"` give Strings. Put a boolean reporter in a `Boolean` field. Number reporters inside a text field need `to string ___` or `join ___ ___` (join converts automatically).

## Containers

`if` (field `boolean`; `do` / `else`), `while`, `repeatTimes` (fields `times`, `var`: the loop counter name), `ifThen` (just `do`), `waitSeconds` (`seconds`, `do` runs after the delay), `everySeconds` (repeats forever), `ifCollision`, `ifTouching`, `ifJoystickMoved`, `_DoAfter`/`_repeatEvery` (named timers). Inside `do`/`else` write blocks exactly like at top level.

```json
[{"block":"if","fields":{"boolean":{"r":"___ > ___","args":[{"r":"x of ___","args":["player"]},"1000"]}},
  "do":[{"block":"OpenScene","fields":{"scene":"win"}}],
  "else":[]}]
```

`stopEvent` leaves the event. `breakLoop` leaves a loop. `comment` adds a note. `newVariable` declares a LOCAL Java variable (`type`, `name`, `value`), visible to the blocks after it in the same sequence (not inside timers unless final). For scene variables use the `variables` tool and `setVariable` / `changeVariable`.

## Rules that avoid compile errors

1. Element names and variable names must exist (scene elements are fields of the scene class).
2. A reporter's arg count must match its `___` count.
3. Inside `waitSeconds` / `everySeconds` / `_DoAfter` / `_repeatEvery` / `destroyAfter` do not use `current`, `body1`, `body2`, `event` or a local made by `spawnCopy` (none is final in Java 7): copy first with `{"block":"codeLine","fields":{"code":"final PlayerItem me = current;"}}`, then use `me`. The helper block `destroyLater` (below) does this for you.
4. Body blocks (`moveBy`, `goTo`, `setVelocityX`, `x of ___`...) crash on UI-type elements (no body). For TEXT/PROGRESS/UI use `setText`, `setProgress`, `setVisibility`, or Java on `getActor()`.
5. Do not use the blocks marked BROKEN / NO-OP in the catalog (`saveValue`, `setsplits`, `setAlpha`, `moveTowardBody`).
6. Java types are checked: assigning a float (`x of ___`, `speed y of ___`, `frame time`, `random` is int) to an `int` variable fails to compile: wrap it in `to int ___`, or declare the variable `float`. Variables are `int`/`float`/`String`/`boolean`; `saved number ___` is an int. Float variables need values like `0.5f` in the `variables` tool.
7. If `events set` says "unknown block", the error lists similar titles; call `block_catalog` with `search`.

## Reading back

`events get` returns the same JSON you would send (`blocks`), so you can edit and send it again. After `set`, `build` shows Java compile errors with the event file; the visual editor shows the same blocks.

## `define_blocks`: your own blocks

Use it when the same Java is needed in many places or no block exists (for example `shakeCamera`, `playTone`). The blocks are saved in the user's block library under the category `★ <category>` and are then available in `events` and in the editor palette.

```json
{"category":"My Game","color":"#8E24AA","blocks":[
 {"title":"killEnemy","kind":"statement","fields":["enemy(Body)","points"],"defaults":["current","10"],
  "code":"%1$s.destroy();\nscore += (int)(%2$s);\n%3$s"},
 {"title":"repeatWhileAlive","kind":"container","fields":["times"],"defaults":["3"],
  "code":"for(int i = 0; i < (int)(%1$s); i++){\n%2$s\n}\n%3$s"},
 {"title":"half","kind":"value","fields":["a"],"display":"half of ___","code":"((%1$s) / 2f)"}
]}
```

Rules:

- `kind: "statement"`: `fields` are the inputs in order; the template uses `%1$s` ... `%N$s` for them and MUST end with `%{N+1}$s` = the next block (forgetting it silently drops everything after your block).
- `kind: "container"`: inner blocks are `%{N+1}$s`, the next block is `%{N+2}$s` (the tool maps this to the engine's internal layout for you). It gets `do` in `events`.
- `kind: "value"`: an expression, no next slot. `display` is the exact text with one `___` per field (this exact text is what you write in `"r"`); the number of `___` must equal the number of fields.
- A field named `name(Body)` is an element picker (use the element name or `current` in `events`); `(Boolean)`, `(image)`, `(sound)`, `(animation)`, `(scene)` work as in the catalog. Field names are letters/digits/`_`; avoid `inside` and `else`.
- A literal percent sign in the Java must be written `%%`.
- `defaults` are in field order; the `title` must be unique (redefining a title replaces it).
- Code runs inside the scene class: elements and variables are directly visible; Java 7 only (no lambdas).
- Optional `dims`: `"2D"`, `"3D"` or `"BOTH"` (default) at the top level (whole category) or per block: the palette shows the block only in scenes of that type (see "3D blocks" below).
- Optional `description` (a string = English, or a map by language `{"en":"Kills the enemy","ar":"يقتل العدو"}`): shown when the user taps the block's (i) button. Always write one for blocks the user will see; `block_catalog {detail:true}` returns descriptions of the blocks (off by default to keep the list short).
- Using it: `{"block":"killEnemy","fields":{"enemy":"body2","points":20}}`, `{"block":"repeatWhileAlive","fields":{"times":5},"do":[ ... ]}`, and the value block as `{"r":"half of ___","args":["score"]}`.

## Standard helper: `destroyLater` (define it once, use it everywhere)

Destroying a body twice (for example a bullet that hits an enemy AND has a 2 second timer) can crash the game, and the built-in `destroyAfter` / `destroy` blocks do not protect against it. This helper destroys an element after a delay only if it still exists, and copies its argument into a `final` local so it also works with `body1`/`body2`/`current`/spawned clones. Call `define_blocks` with it at the start of any project that spawns or removes things (redefining it is harmless):

```json
{"category":"Helpers","color":"#6D4C41","blocks":[
 {"title":"destroyLater","kind":"statement","fields":["item(Body)","seconds"],"defaults":["current","0.05"],
  "code":"{ final PlayerItem __d = %1$s; Timer.schedule(new Timer.Task(){ public void run(){ if(__d.getActor().getStage() != null) __d.destroy(); } }, (float)(%2$s)); }\n%3$s"}
]}
```

Use: `{"block":"destroyLater","fields":{"item":"body2","seconds":0.05}}` (usually after `hide` of the same element so it stops being visible and interacting at once). Never destroy inside a collision callback directly; the delay makes it safe.

## Blocks of behaviors

Behaviors (`read_docs behaviors`) bring their own blocks, titled `<Behavior>.<method>` (for example `PlatformerPlayer.jump`, value block `is on the ground ___`). The first slot is the element that owns the behavior (`current`); if that element does not have the behavior nothing happens (value blocks give false / 0 / ""). They are listed by `block_catalog` only for behaviors the scene uses (`hidden_behavior_blocks` names the rest; `all:true` lists them anyway). Always available, category `Behaviors`: `addBehavior`, `removeBehavior`, `enableBehavior`, `disableBehavior`, `setBehaviorEnabled`, `setBehaviorParam` and the values `___ has behavior ___`, `___ has behavior ___ turned on`, `number|text|yes/no setting ___ of behavior ___ on ___`, `behavior ___ of ___` (behavior names are the class names: `PlatformerPlayer`, `Shooter`).

## 3D blocks

The palette (and `block_catalog`) shows the blocks made for the scene type: a scene is 3D when `configs/<scene>.json` has `"sceneType":"3D"` (otherwise 2D). In a **3D scene** the 2D-only blocks are hidden (Box2D `Motion`, `Physics+`, `Forces`, `Body options`, `Joints`, 2D `Camera`, `Element` sprite/body blocks, tint/flip/sprite animations, `setGravity`, `setsplits`, `ifCollision`, and the 2D value blocks `x of ___`, `speed x of ___`, `___ touches ___`, `touch x`, `camera x`...). In a **2D scene** every 3D category is hidden. Shared (both): control, loops, variables, math, text, logic, timers, scenes, sounds, debug, language, UI holder blocks, input / joystick, data, behaviors, `show` / `hide` / `destroy` and **Inventory**. `block_catalog` returns `scene_type`, lists only the matching blocks (`all:true` lists both), gives `choices` for list fields and `slots` (e element, n number, t text, b condition) for value blocks.

Units: meters, Y up, degrees. Element slots take an element name or `current` (3D elements, lights and cameras). List fields (`mode(followMode)`, `preset(lightPreset)`...) take one bare word from `choices`; color fields take `#RRGGBB` (no quotes); text slots like `clip`, `part`, `id` take a Java string (`"Run"`) or a variable. A `code(glsl)` field holds GLSL as the inside of a Java string: one line, or `\n` for new lines, no unescaped `"`.

| Category | Blocks (titles) | Value blocks |
|---|---|---|
| 3D Motion (22) | setPosition3D, moveBy3D, moveForward3D, moveRight3D, moveUp3D, setX3D/setY3D/setZ3D, moveTo3D (glide in seconds), moveToward3D, setRotation3D, rotate3D, turn3D (own axes), rotateTo3D, lookAt3D, lookAtElement3D, face3D, turnToward3D, setScale3D, scaleTo3D, attach3D, detach3D | `3D x of ___` (y, z), `3D rotation x of ___` (y, z), `3D scale x of ___` (y, z), `3D distance ___ to ___`, `3D distance ___ to point ___ ___ ___`, `3D flat distance ___ to ___`, `3D angle ___ to ___`, `3D forward x of ___` (y, z), `___ is gliding (3D)` |
| 3D Physics (21) | applyForce3D, applyImpulse3D, applyLocalForce3D, applyTorque3D, setVelocity3D, setVelocityKeepY3D, setAngularVelocity3D, walkForward3D, pushForward3D, jump3D, stop3D, setBodyType3D (NONE STATIC DYNAMIC KINEMATIC TRIGGER), setMass3D, setGravityScale3D, setFriction3D, setBounce3D, setPhysics3D, setWorldGravity3D; containers ifGrounded3D, ifTouching3D, ifRayForward3D | `3D speed x/y/z of ___`, `3D speed of ___`, `___ is on ground (3D)`, `___ touches ___ (3D)`, `3D ray from ___ ___ ___ toward ___ ___ ___ within ___ hits`, `3D ray forward from ___ within ___ hits`, `3D ray down from ___ within ___ hits`, `___ can see ___ within ___ (3D)`, `3D screen ray at ___ ___ hits`, `3D hit element`, `3D hit x/y/z`, `3D hit distance`, `3D element at touch`, `3D element at screen ___ ___`, `3D gravity x/y/z` |
| 3D Looks (12) | setColor3D, setTexture3D, setOpacity3D, setEmissive3D, setVisible3D, toggleVisible3D, setOutline3D, setShape3D, setModel3D, setPartVisible3D, rotatePart3D, setPartRotation3D (model nodes) | `3D opacity of ___`, `___ is shown (3D)`, `___ has model part ___` |
| 3D Animation (7) | playAnimation3D, crossFade3D, playOnce3D, stopAnimation3D, pauseAnimation3D, resumeAnimation3D, animationSpeed3D | `___ is animating (3D)`, `3D clip of ___`, `3D clip names of ___`, `3D clip count of ___` |
| 3D Camera (11) | setMainCamera3D, cameraFollow3D (THIRD_PERSON FIRST_PERSON ORBIT TOP_DOWN SIDE LOOK_AT), cameraStopFollow3D, cameraDistance3D, cameraFov3D, cameraZoomTo3D, cameraShake3D, cameraTouchLook3D, cameraMoveTo3D, cameraLookAt3D, cameraLookAtElement3D (all on the main camera) | `3D screen x/y of ___`, `___ is on screen (3D)`, `3D camera x/y/z`, `3D camera view angle`, `3D camera follow mode`, `3D main camera` |
| 3D Lights & Sky (19) | lightOn3D, toggleLight3D, lightIntensity3D, lightRange3D, lightColor3D, lightCone3D, lightFlicker3D, ambient3D, sun3D, sunAngle3D, lightingPreset3D (DAY SUNSET NIGHT OVERCAST STUDIO HORROR INDOOR CARTOON CUSTOM), fog3D, fogOff3D, sky3D, skyImage3D, renderMode3D (SIMPLE SOFT TOON PBR UNLIT), shadows3D, shadowDistance3D, exposure3D | `___ light is on (3D)`, `3D light intensity of ___`, `3D lighting preset`, `3D render style` |
| 3D Effects & Shaders (9) | postEffect3D (NONE VIGNETTE NIGHT_VISION GRAYSCALE SEPIA HORROR CHROMATIC PIXELATE BLOOM_LITE UNDERWATER DREAM + amount), postAmount3D, postEffectOff3D, postCode3D (`vec4 post(vec4 c, vec2 uv)`), setShader3D (`builtin:glow`... or `shaders/<name>`, params `speed=2;tint=#ff0000`), shaderCode3D (`vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos)`), shaderFloat3D, shaderColor3D, clearShader3D | `3D screen effect`, `3D shader error of ___`, `3D time` |
| 3D Graphics quality (10) | setQuality3D (LOW MEDIUM HIGH ULTRA, saved), nextQuality3D, renderScale3D, viewDistance3D, shadowQuality3D, fpsLimit3D, waterQuality3D, postEffectsAllowed3D, showGraphicsMenu3D (ready settings panel, `arabic` true/false), hideGraphicsMenu3D | `3D quality`, `3D fps`, `3D render scale`, `3D view distance`, `3D shadow quality`, `3D frame limit`, `3D water quality` |
| 3D Water & Terrain (3) | waves3D, waterColors3D, snapToGround3D | `3D ground height of ___ at ___ ___`, `3D water level of ___ at ___ ___` |
| 3D Objects (4) | spawn3D (declares a variable), spawnAt3D, destroy3D, destroyAfter3D | `3D size x/y/z of ___`, `___ is destroyed (3D)`, `3D copy of ___ named ___ at ___ ___ ___` |
| 3D Sound (2) | playSoundAt3D (fades with distance, panned), playSound3D | |
| Inventory (6, 2D and 3D) | addItem, removeItem, setItemCount, selectItem, clearInventory, ifHasItem | `has item ___`, `count of item ___`, `item at ___`, `inventory size`, `selected item` |

Example (3D OnStep): `{"block":"ifGrounded3D","fields":{"body":"player"},"do":[{"block":"jump3D","fields":{"body":"player","speed":5,"onlyOnGround":true}}]}`, `{"block":"cameraFollow3D","fields":{"target":"player","mode":"THIRD_PERSON"}}`.

Block code calls the static facades `com.star4droid.template.d3.B3` / `B3x` (fully qualified, Java 7); your own `define_blocks` code for 3D should do the same (never Bullet or gdx-gltf classes). `define_blocks` takes `"dims":"2D"|"3D"|"BOTH"` for the category and per block (default BOTH): give `"dims":"3D"` to blocks that only make sense in 3D scenes.
