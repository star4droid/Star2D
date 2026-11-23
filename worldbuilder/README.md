# World Builder

A standalone 3D world editor (libGDX 1.14.2): library of elements, smart one-axis scaling, terrain sculpting and painting,
scatter brush, animated water and waterfalls, effects, element shaders (Star2D shader format), worlds saved on the device.

- `core/` the editor (engine/, editor/, ui/), `android/` the standalone app (package com.star4droid.worldbuilder), `desktop/` a desktop build for testing, `assets/wb/` the files that ship with it (in the `wb/` folder so they never mix with the files of Star2D, which also includes World Builder).
- `tools/gen_textures.py`, `tools/gen_grass.py` make `assets/wb/tex`; `tools/gen_models.py` (run with Blender 4.2, `blender -b -P gen_models.py -- ../assets/wb/models/models.bin`) makes the models.
- Build from the root of the Star2D repository: `./gradlew :worldbuilder:android:assembleDebug` (APK in `worldbuilder/android/build/outputs/apk/debug`), `./gradlew :worldbuilder:desktop:dist` (desktop jar). Inside Star2D it opens from the World Builder button on the home screen.
- Desktop testing without a phone: `java -jar desktop/build/libs/WorldBuilder-desktop.jar --size=620x1340 --density=1.55 --shots=DIR --script="fresh;wait 10;shot a"` (see `TestScript`).
- Texts go through `ui/L.java` (English only for now; set `L.rtl` and add a map for a right-to-left language).
