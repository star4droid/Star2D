# Recipes (2 of 2): breakout, endless runner, flappy, menu and levels

Same conventions as `recipes`: 1280 x 720 landscape, `scenes configure` first, a white `images/white.png` (see `recipes`) for flat colors, `define_blocks` for `destroyLater` (see `blocks`). Verify each with `run_test`.

## 4. Breakout

1. `variables`: `{"ops":[{"op":"add","name":"score","type":"int","value":"0"},{"op":"add","name":"bricksLeft","type":"int","value":"16"},{"op":"add","name":"launched","type":"boolean","value":"false"}]}`
2. `elements` (2 rows x 8 bricks shown as a pattern: row r (0..1), column c (0..7): `x = 120 + c*130`, `y = 560 - r*45`; write all 16 bricks `brick_0_0` ... `brick_1_7`):
```json
{"ops":[
 {"op":"add","type":"BOX","name":"wallTop","props":{"x":0,"y":700,"width":1280,"height":20,"type":"STATIC","image":"/white.png","Tint":"#455A64","restitution":1,"friction":0}},
 {"op":"add","type":"BOX","name":"wallLeft","props":{"x":-20,"y":0,"width":20,"height":720,"type":"STATIC","image":"/white.png","restitution":1,"friction":0}},
 {"op":"add","type":"BOX","name":"wallRight","props":{"x":1280,"y":0,"width":20,"height":720,"type":"STATIC","image":"/white.png","restitution":1,"friction":0}},
 {"op":"add","type":"BOX","name":"paddle","props":{"x":560,"y":40,"width":160,"height":24,"type":"KINAMATIC","image":"/white.png","Tint":"#29B6F6","restitution":1,"friction":0,"z":5}},
 {"op":"add","type":"CIRCLE","name":"ball","props":{"x":628,"y":80,"radius":12,"type":"DYNAMIC","image":"/white.png","Tint":"#FFEB3B","restitution":1,"friction":0,"Gravity Scale":0,"Bullet":true,"z":6}},
 {"op":"add","type":"BOX","name":"brick_0_0","props":{"x":120,"y":560,"width":120,"height":35,"type":"STATIC","image":"/white.png","Tint":"#EF5350","restitution":1,"friction":0}},
 {"op":"add","type":"BOX","name":"brick_0_1","props":{"x":250,"y":560,"width":120,"height":35,"type":"STATIC","image":"/white.png","Tint":"#EF5350","restitution":1,"friction":0}},
 {"op":"add","type":"TEXT","name":"scoreText","props":{"x":30,"y":640,"width":500,"height":50,"Text":"Score: 0","Font Scale":3,"z":100}},
 {"op":"add","type":"TEXT","name":"msg","props":{"x":340,"y":300,"width":600,"height":120,"Text":"TAP TO LAUNCH","Font Scale":5,"z":100}}
]}
```
(bricks of other rows/columns: same props, different `x`, `y`, `Tint`.)
3. `events`:
```json
{"ops":[
 {"op":"set","scope":"scene","event":"OnCreate","blocks":[{"block":"setWorldGravity","fields":{"strength":0}}]},
 {"op":"set","scope":"scene","event":"OnStep","blocks":[
   {"block":"ifThen","fields":{"boolean":{"r":"screen is touched","args":[]}},"do":[
     {"block":"goTo","fields":{"body":"paddle","x":{"r":"max ___ , ___","args":[80,{"r":"min ___ , ___","args":[1200,{"r":"touch x","args":[]}]}]},"y":40}}]},
   {"block":"if","fields":{"boolean":{"r":"___ < ___","args":[{"r":"y of ___","args":["ball"]},0]}},"do":[{"block":"OpenScene","fields":{"scene":"scene1"}}]}]},
 {"op":"set","scope":"scene","event":"onCollisionStart","blocks":[
   {"block":"ifThen","fields":{"boolean":"(body1.getName().equals(\"ball\") && body2.getName().startsWith(\"brick\")) || (body2.getName().equals(\"ball\") && body1.getName().startsWith(\"brick\"))"},"do":[
     {"block":"codeLine","fields":{"code":"PlayerItem brk = body1.getName().startsWith(\"brick\") ? body1 : body2;"}},
     {"block":"hide","fields":{"body":"brk"}},{"block":"destroyLater","fields":{"item":"brk","seconds":0.05}},
     {"block":"changeVariable","fields":{"variable":"score","by":10}},{"block":"changeVariable","fields":{"variable":"bricksLeft","by":-1}},
     {"block":"setText","fields":{"ui":"scoreText","text":"\"Score: \" + score"}},
     {"block":"if","fields":{"boolean":{"r":"___ <= ___","args":["bricksLeft",0]}},"do":[{"block":"showText","fields":{"ui":"msg","text":"\"YOU WIN\""}}]}]}]}
]}
```
Launching is a separate event (step 4). The paddle is moved by teleporting a KINAMATIC body (fine for kinematic).
4. Launch on first tap: add a full-screen transparent UI box `{"op":"add","type":"BOX","name":"tapArea","props":{"type":"UI","x":0,"y":0,"width":1280,"height":720,"image":"/white.png","Tint":"#FFFFFF00","z":90}}` and its event:
```json
{"op":"set","scope":"element","element":"tapArea","event":"onTouchStart","blocks":[
 {"block":"ifThen","fields":{"boolean":{"r":"not ___","args":["launched"]}},"do":[
   {"block":"setVariable","fields":{"variable":"launched","value":"true"}},
   {"block":"setVelocityX","fields":{"body":"ball","speed":250}},{"block":"setVelocityY","fields":{"body":"ball","speed":350}},
   {"block":"showText","fields":{"ui":"msg","text":"\"\""}}]}]}
```
Ball speed is about 430 px/s (250 and 350 per axis); faster works too (keep `Bullet` true). A ball that never loses speed needs `restitution` 1 and `friction` 0 on everything it touches (set above). `Bullet` true avoids tunneling.

## 5. Endless runner "Dash": tap to jump, obstacles come from the right

1. `variables`: `score` int 0, `groundContacts` int 0, `spawned` int 0, `alive` boolean true. `define_blocks` `destroyLater`.
2. `elements`:
```json
{"ops":[
 {"op":"add","type":"BOX","name":"ground","props":{"x":0,"y":0,"width":1280,"height":60,"type":"STATIC","image":"/white.png","Tint":"#4E342E","friction":0,"restitution":0}},
 {"op":"add","type":"BOX","name":"player","props":{"x":200,"y":60,"width":50,"height":70,"type":"DYNAMIC","image":"/white.png","Tint":"#FFB300","Fixed Rotation":true,"restitution":0,"friction":0,"z":5}},
 {"op":"add","type":"BOX","name":"obsT","props":{"x":-400,"y":60,"width":40,"height":70,"type":"DYNAMIC","image":"/white.png","Tint":"#B71C1C","Gravity Scale":0,"Fixed Rotation":true,"Collision":"(ground)","friction":0,"restitution":0,"z":4}},
 {"op":"add","type":"TEXT","name":"scoreText","props":{"x":30,"y":640,"width":500,"height":60,"Text":"Score: 0","Font Scale":4,"z":100}},
 {"op":"add","type":"BOX","name":"tapArea","props":{"type":"UI","x":0,"y":0,"width":1280,"height":720,"image":"/white.png","Tint":"#FFFFFF00","z":90}}
]}
```
3. `events`:
```json
{"ops":[
 {"op":"set","scope":"scene","event":"OnCreate","blocks":[
   {"block":"setWorldGravity","fields":{"strength":1000}},
   {"block":"everySeconds","fields":{"seconds":1.4},"do":[
     {"block":"ifThen","fields":{"boolean":"alive"},"do":[
       {"block":"changeVariable","fields":{"variable":"spawned","by":1}},
       {"block":"spawnCopy","fields":{"body":"obsT","var":"o","newName":{"r":"join ___ ___","args":[{"r":"\"___\"","args":["obs"]},"spawned"]},"x":1350,"y":95}},
       {"block":"setVelocityX","fields":{"body":"o","speed":-420}},
       {"block":"destroyLater","fields":{"item":"o","seconds":6}}]}]},
   {"block":"everySeconds","fields":{"seconds":0.5},"do":[
     {"block":"ifThen","fields":{"boolean":"alive"},"do":[
       {"block":"changeVariable","fields":{"variable":"score","by":1}},
       {"block":"setText","fields":{"ui":"scoreText","text":"\"Score: \" + score"}}]}]}]},
 {"op":"set","scope":"element","element":"tapArea","event":"onTouchStart","blocks":[
   {"block":"ifThen","fields":{"boolean":{"r":"___ and ___","args":["alive",{"r":"___ > ___","args":["groundContacts",0]}]}},"do":[
     {"block":"jump","fields":{"body":"player","power":450}}]}]},
 {"op":"set","scope":"scene","event":"onCollisionStart","blocks":[
   {"block":"ifThen","fields":{"boolean":"(body1.getName().equals(\"player\") && body2.getName().equals(\"ground\")) || (body2.getName().equals(\"player\") && body1.getName().equals(\"ground\"))"},"do":[{"block":"changeVariable","fields":{"variable":"groundContacts","by":1}}]},
   {"block":"ifThen","fields":{"boolean":"alive && ((body1.getName().equals(\"player\") && body2.getName().startsWith(\"obs\")) || (body2.getName().equals(\"player\") && body1.getName().startsWith(\"obs\")))"},"do":[
     {"block":"setVariable","fields":{"variable":"alive","value":"false"}},
     {"block":"saveData","fields":{"key":"last","value":"score"}},
     {"block":"waitSeconds","fields":{"seconds":0.5},"do":[{"block":"OpenScene","fields":{"scene":"gameover"}}]}]}]},
 {"op":"set","scope":"scene","event":"onCollisionEnd","blocks":[
   {"block":"ifThen","fields":{"boolean":"(body1.getName().equals(\"player\") && body2.getName().equals(\"ground\")) || (body2.getName().equals(\"player\") && body1.getName().equals(\"ground\"))"},"do":[{"block":"changeVariable","fields":{"variable":"groundContacts","by":-1}}]}]}
]}
```
The obstacles (`obsT` clones) skip the ground via `Collision` `(ground)`, so only the player triggers collision events with them. Jump height here: 450^2 / (2*1000) = 101 px, air time 0.9 s (obstacles are 70 px high). Create the `gameover` scene as in `recipes` (step 8 of recipe 1).

## 6. Flappy-style: tap to flap through gaps

1. `variables`: `score` int 0, `gy` int 400, `spawned` int 0, `alive` boolean true. `create_image` white rect for everything.
2. `elements`: `player` BOX 50 x 50 at x 250, y 360, DYNAMIC, `Fixed Rotation` true, `restitution` 0; two KINAMATIC templates off-screen: `pipeBotT` and `pipeTopT`, each `width` 90, `height` 600, `x` -500 (`y` 0 and 600); TEXT `scoreText`; transparent UI BOX `tapArea` (as in recipe 5).
3. `events`:
```json
{"ops":[
 {"op":"set","scope":"scene","event":"OnCreate","blocks":[
   {"block":"setWorldGravity","fields":{"strength":900}},
   {"block":"everySeconds","fields":{"seconds":1.6},"do":[
     {"block":"ifThen","fields":{"boolean":"alive"},"do":[
       {"block":"changeVariable","fields":{"variable":"spawned","by":1}},
       {"block":"setVariable","fields":{"variable":"gy","value":{"r":"random ___ to ___","args":[220,500]}}},
       {"block":"spawnCopy","fields":{"body":"pipeBotT","var":"pb","newName":{"r":"join ___ ___","args":[{"r":"\"___\"","args":["pipeB"]},"spawned"]},"x":1350,"y":{"r":"___ - ___","args":["gy",400]}}},
       {"block":"setVelocityX","fields":{"body":"pb","speed":-260}},
       {"block":"destroyLater","fields":{"item":"pb","seconds":7}},
       {"block":"spawnCopy","fields":{"body":"pipeTopT","var":"pt","newName":{"r":"join ___ ___","args":[{"r":"\"___\"","args":["pipeT"]},"spawned"]},"x":1350,"y":{"r":"___ + ___","args":["gy",400]}}},
       {"block":"setVelocityX","fields":{"body":"pt","speed":-260}},
       {"block":"destroyLater","fields":{"item":"pt","seconds":7}},
       {"block":"changeVariable","fields":{"variable":"score","by":1}},
       {"block":"setText","fields":{"ui":"scoreText","text":"\"Score: \" + score"}}]}]}]},
 {"op":"set","scope":"element","element":"tapArea","event":"onTouchStart","blocks":[
   {"block":"ifThen","fields":{"boolean":"alive"},"do":[{"block":"setVelocityY","fields":{"body":"player","speed":400}}]}]},
 {"op":"set","scope":"scene","event":"OnStep","blocks":[
   {"block":"ifThen","fields":{"boolean":{"r":"___ and ___","args":["alive",{"r":"___ or ___","args":[{"r":"___ < ___","args":[{"r":"y of ___","args":["player"]},0]},{"r":"___ > ___","args":[{"r":"y of ___","args":["player"]},760]}]}]}},"do":[
     {"block":"setVariable","fields":{"variable":"alive","value":"false"}},{"block":"OpenScene","fields":{"scene":"scene1"}}]}]},
 {"op":"set","scope":"scene","event":"onCollisionStart","blocks":[
   {"block":"ifThen","fields":{"boolean":"alive && (body1.getName().startsWith(\"pipe\") || body2.getName().startsWith(\"pipe\"))"},"do":[
     {"block":"setVariable","fields":{"variable":"alive","value":"false"}},
     {"block":"waitSeconds","fields":{"seconds":0.6},"do":[{"block":"OpenScene","fields":{"scene":"scene1"}}]}]}]}
]}
```
`spawnCopy` places the CENTER of the clone: the 600 px tall pipes centered at `gy-400` and `gy+400` leave a 200 px gap at `gy`. Pipes are KINAMATIC and pushed left with `setVelocityX`; a KINAMATIC body is not affected by gravity. The player bounces nothing (`restitution` 0).

## 7. Menu, levels, win and game over (scene flow)

1. Scenes: `scenes` `{"action":"create","name":"level2","config":{"or":"landscape","logicWidth":1280,"logicHeight":720,"sceneColor":"#87CEEB"}}`, same for `gameover` and `win`. Build `scene1` as the MENU: TEXT `title` ("MY GAME", Font Scale 8, x 340, y 460, w 600, h 100) and TEXT `playBtn` ("PLAY", Font Scale 6, x 440, y 280, w 400, h 120) with `onClick` -> `OpenScene` `level1`. Put level 1 in a scene named `level1` (copy a finished level with `scenes` `{"action":"copy","name":"level1","new_name":"level2"}` and edit it).
2. Goal element: BOX `flag` STATIC `isSensor` true. In the level's `onCollisionStart`:
```json
{"block":"ifThen","fields":{"boolean":"(body1.getName().equals(\"player\") && body2.getName().equals(\"flag\")) || (body2.getName().equals(\"player\") && body1.getName().equals(\"flag\"))"},"do":[
  {"block":"saveData","fields":{"key":"last","value":"score"}},
  {"block":"OpenScene","fields":{"scene":"level2"}}]}
```
In the last level open `win` instead. `gameover` / `win` have TEXT + a `retry`/`menu` TEXT with `onClick` -> `OpenScene` `scene1` (menu) or `level1`.
3. Show the saved score in `win`/`gameover` `OnCreate`: `{"block":"setText","fields":{"ui":"resultText","text":{"r":"join ___ ___","args":[{"r":"\"___\"","args":["Score: "]},{"r":"saved number ___","args":["last"]}]}}}`.
4. Set the run entry: `run_game` starts the OPEN scene (or pass `scene`); the real game always starts at `scene1`. Test each scene with `run_test` `{"scene":"level2","steps":[...]}`.
