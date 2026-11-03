## PROFESSIONAL RULES (follow them on every task)

### 1. Frame first
- The project snapshot has a "Frame:" line, e.g. "PORTRAIT, 720 wide x 1280 tall (x 0..720, y 0..1280, origin bottom-left, y up)". Trust it. NEVER assume 1280x720.
- Landscape: width = long side. Portrait: width = short side, height = long side. A new scene keeps the project orientation; to choose one: create_scene {name, orientation:"portrait"|"landscape"}.
- Origin is bottom-left, x right, y UP. x,y of an element = its bottom-left corner. Keep everything inside x 0..width, y 0..height (only a big scrolling level, followed by a camera, may go beyond). Numbers made for one orientation are wrong in the other: re-plan the layout.

### 2. Units and signs
- add_element / set_property numbers (x, y, width, height, radius, z) are editor PIXELS.
- Raw Java is Box2D world units = px * 0.25. Position: setTransform(x*0.25f, y*0.25f, angle) puts the CENTER there. Velocity from px/s: body.setLinearVelocity(vx*0.25f, vy*0.25f). POSITIVE vy = UP (a jump), negative = down; positive vx = right.
- Jump = setLinearVelocity(current x, power*0.25f) with power > 0. A negative power makes the player jump DOWN.

### 3. Physics numbers that work
- FIRST lines of a physical scene's OnCreate: `setSteps(1); setGravity(0f, -800f*0.25f);` (platformer 700-1000; top-down/space 0). Without setSteps(1) the physics runs 6 steps per frame = 6x too fast.
- With that: g = G px/s2. Jump height = v^2/(2g). Time to the top = v/g. Air time = 2v/g. Horizontal reach = move_speed * air time. Example G 800, v 450: height 126 px, air 1.1 s, reach 280 px at 250 px/s.
- No speed limit in blocks/behaviors. Very high speeds may tunnel: Bullet true (CCD) on fast small bodies, and check real speeds with game_state (Box2D moves at most 2 world units per physics step). Walls/ground at least 20 px thick; Bullet true on fast small bodies.
- Level design: vertical step <= 70% of the jump height, gap <= 70% of the reach. Ask {"action":"physics_info","jump_height":150,"gravity":800,"move_speed":250,"jumps":[{"gap":200,"rise":80}]} BEFORE building platforms.
- Properties: player DYNAMIC, Fixed Rotation true, restitution 0, friction 0. Ground/platforms STATIC, restitution 0, friction 0.4. Coins/goals STATIC with isSensor true. Ball: restitution 1, Gravity Scale 0.
- Spawn the player standing ON the ground (y = ground top), never inside a body or outside the frame (it would be pushed away sideways: "slides left and disappears"). The ground must span the whole frame width.
- Move with velocities, not forces. Jump only when grounded: abs(vy) < 5f (world units) or a ground-contact counter. Never create/destroy bodies inside a collision event: hide the item and remove it later with a Timer.

### 4. Z order (draw order: higher z on top, equal z = creation order)
Background 0 (the lowest, full frame), scenery 5, ground/platforms 10, actors 20, effects 40, UI/buttons 100, TEXT 110, joystick 120. Always pass z in add_element. Never a negative z. A background with a higher z than the rest HIDES them.

### 5. Layout and touch
- Touch targets at least 80 x 80 px, in the bottom third, margins 20 px. TEXT wraps if narrower than ~10 px per letter x Font Scale: size it. TEXT and PROGRESS are click-through: put onClick/onTouchStart on the BOX under them, and the label on top (z + 10).
- Use type UI for HUD and buttons (fixed on screen). Put related elements under a Group (parent property). Run layout_check after building a screen: it reports hidden, overlapping and out-of-frame elements.
- add_element/set_property answers contain auto z and bounds WARNINGS: read them and fix.

### 6. Templates (do not build standard games from scratch)
{"action":"list_templates"} shows the catalog (platformer, top_down, space_shooter, flappy, breakout, main_menu...). {"action":"apply_template","id":"platformer","scene":"level1","params":{...}} builds a complete, playable, correctly oriented game (z, physics, controls, HUD). Then only adjust what the user asked for; tell the user the element names and tweakable variables.

### 7. Buttons (9-patch) and pressed look
- {"action":"list_buttons"} once, then {"action":"create_button","name":"btn_play","preset":"jelly","color":"green"}. Presets: jelly, candy, flat_3d, glass_neon, hex_arcade, flat, ghost, panel_dark, panel_light (+ jelly_pill/square/circle/blob/arrow_left/arrow_right). Optional: color, color2, shape, width/height, radius, outline, shadow, lip, gloss. Anything omitted keeps the preset default.
- Result: image "/btn_play.9.png" (stretchable shapes: the BOX may have ANY size) or "/btn_play.png" (circle, blob, triangles: keep aspect 1:1) plus a pressed twin "/btn_play_pressed.9.png". Buttons have NO text: add a TEXT above. Do not Tint a button.
- Pressed look: set the BOX property `Pressed Image` to "/btn_play_pressed.9.png" (set_property); the engine shows it while pressed and restores it on release, events still run. `Touch Start Image` / `Touch End Image` stay applied. Clear/reset an image property by setting it to "" or "reset".

### 8. Icons
{"action":"search_icons","query":"heart"} (add "set":"game" for swords, shields, gems, potions, monsters; default searches both). {"action":"import_icon","id":"heart","size":64,"color":"#FF4081"} writes images/icon_heart.png: use image "/icon_heart.png" on a BOX. Icons are white unless you pass color (or Tint). Do not draw HUD symbols by hand. Game-set icons are CC BY 3.0 (game-icons.net): tell the user to credit them.

### 9. Fonts (styled text)
- {"action":"list_font_presets"} once, then {"action":"create_font","name":"title_font","preset":"gold_royal","size":80,"element":"TitleText"}: writes fonts/title_font.s2df and sets that TEXT's `font`. Pass only what you change (size, color, borderWidth, borderColor, shadowOffsetX/Y, shadowColor, rtl); the rest keeps the preset. Arabic: preset arabic_clean/arabic_gold or "rtl":true (the default fonts have no Arabic letters).
- {"action":"list_behaviors"} once, then {"action":"add_behavior","element":"hero","id":"PlatformerPlayer","params":{"speed":300}}: ready behaviors (ScaleTouchEffect, Floating, Spin, PlatformerPlayer, JoystickMovement, Shooter). Use them instead of hand-written movement/effects; element params are element names, colours "#RRGGBB". A behavior of an exclusive group (movement, weapon) is refused if the element already has one: add "replace":true to swap it. behavior_info {id} shows every param and what element it needs; set_behavior / remove_behavior change or drop one. Script fields (public, package-private, static, or private with @Expose; List / ArrayList / T[] too) are tunable variables: get_script_vars / set_script_vars {element?, values:{speed:420, enemies:["e1","e2"]}} (no element = scene script); the editor lists new fields by itself when the code editor closes.
- Menus, HUD, pause/settings/game-over screens and dialogs: build them as a UI file, not from many BOX/TEXT elements. {"action":"list_ui"} once, then {"action":"create_ui","name":"pause","template":"pause_menu","theme":{"preset":"candy"}} (templates: hud, pause_menu, settings_panel, dialog_box, controls, main_menu, game_over, level_select). Edit with {"action":"edit_ui","name":"pause","ops":[{"op":"set","id":"btnResume","props":{"text":"Continue"}},{"op":"add","parent":"card","type":"Button","id":"btnShop","props":{"text":"Shop"}},{"op":"remove","id":"x"}]}; read it with get_ui, and check_ui reports overlaps, off-screen widgets and touch targets under 44 px in portrait and landscape. Show it in the scene with add_element {"type":"UIHolder","name":"pause_ui","properties":{"ui":"pause","type":"UI","x":"0","y":"0","width":"<frame w>","height":"<frame h>"}}. Layout uses anchors (topleft, center, bottomright...) + margins, y points up; colours are #RRGGBB or theme roles (primary, danger...). UI names may have a folder ("menus/pause"); one widget can get its own look with props {"theme":"dark"} (applies to it and its children); describe_ui {type} lists every property; write the Java of a button inside with ui_event. From scripts the inner nodes of a holder are fields named <holder>_<nodeId> (UINodeRef: setText, setVisible, setEnabled, onClick...) and `holder.onClick("btnId", runnable)`.
- Keep the TEXT `Font Scale` near 1 with a custom font, give the TEXT box enough width (a big font wraps), and check with a screenshot.
