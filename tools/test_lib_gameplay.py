#!/usr/bin/env python3
"""Tests of the gameplay / combat / camera behaviors in behaviors/lib/ (Health, DamageOnTouch, Destructible, Projectile,
Spawner, Collectible, PowerUp, Checkpoint, Respawn, Lifetime, Timer, ScoreCounter, Door, CameraFollow, CameraShake, Turret, Goal).

  * annotation completeness through the editor's own readers (reflection AND source scanner): icon in assets/icons/catalog.json,
    every public field has @Param (description, min/max for numbers, default inside the range), events parse and are really fired,
    @Block methods have English + Arabic text and supported parameter types;
  * headless smoke tests of the logic that needs no physics world: Health, Timer, Lifetime, number/time formatting, name matching,
    contact tracking, scene variables (by reflection and the fallback store), checkpoints, the shared camera rig.

Needs the template module compiled once:  bash gradlew :template:compileDebugJavaWithJavac   (and the gradle cache for gdx / gson).
"""
import glob, os, subprocess, sys, tempfile, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def find(pattern):
    r = glob.glob(os.path.expanduser(pattern), recursive=True)
    return sorted(r)[-1] if r else None


IDS = ["Health", "DamageOnTouch", "Destructible", "Projectile", "Turret", "Spawner", "Collectible", "PowerUp", "Checkpoint", "Respawn",
       "Lifetime", "Timer", "ScoreCounter", "Door", "Goal", "CameraFollow", "CameraShake"]

INTRO = """# Gameplay, combat and camera behaviors

Seventeen ready-made behaviors for the things almost every game needs: hit points, damage, pickups, spawning, checkpoints,
timers, scores, doors, a win condition and a camera that follows and shakes. Add them from the behaviors dialog of an element
(category Combat, Gameplay, Logic or Camera); their settings appear in the inspector, their events in the Events panel and their
blocks in the block editor as soon as the scene contains an element that uses them.

This page is generated from the annotations in `app/src/main/java/com/star4droid/template/behaviors/lib/` by
`python3 tools/test_lib_gameplay.py --docs`, so the tables always match the code. The notes under each behavior are written by hand.

## Summary

@@SUMMARY@@

## How they work together

**Damage chain.** `DamageOnTouch`, `Projectile` and `Turret` do not know what a target is. They call the first *Damageable* behavior of
the element they hit: `Health` (hit points) or `Destructible` (a number of hits). An element without either is not hurt, unless the
damager has *Auto health* above 0: then a `Health` with that many hit points is added on the first hit (a plain enemy with "3 hit
points" and no configuration). Your own behaviors can hurt things with `GameplaySupport.hit(target, amount, source, pushX, pushY, stunSeconds, autoHealth)`,
and a new kind of life only has to implement `Damageable`.

**Who counts as a target.** Everywhere a behavior asks "who" there are two fields that add up: a *list* of elements, and a *name prefix*
text (`enemy, boss` matches every element whose name starts with `enemy` or `boss`, ignoring capitals; `*` matches all). Clones made by
a `Spawner` are named `<template>_c<number>`, so the template's name works as the prefix for all its copies. Contacts come from the
physics engine, which gives no contact between two non-dynamic bodies (a kinematic bullet against a static wall): elements in a *list*
are therefore also found by overlap of their boxes, name prefixes rely on real contacts, so one of the two bodies must be Dynamic
(a player, a ball) - or put the element in the list. `Turret` finds prefix targets by looking through the scene four times a second.

**Scene variables.** `Collectible`, `ScoreCounter`, `Timer`, `Door`, `Goal` and `Respawn` read and write a scene variable by *name*
(create an int or float in the Variables of the scene, e.g. `score`). If no variable with that name exists the behaviors still work with a
hidden per-scene number, so two behaviors using the same name always see the same value.

**Hiding without freezing.** The engine does not update invisible elements, so a hidden collectible could never come back and a
hidden spawner template could never spawn. These behaviors hide with alpha 0, no body and no touches instead. The element is still
"visible" for the engine; if you check visibility in your own code use the *alpha* or `isCollected`-style blocks.

**Templates.** The element a `Spawner` (or a `Destructible` drop) copies is hidden when the game starts and its behaviors are switched
off, so a template bullet does not fly away and a template with a `Lifetime` does not expire; every copy gets the behaviors back in
the on/off state you set in the editor, with the same settings and the same events.

**Never inside a contact.** The physics world is locked while it reports contacts. All of these behaviors only *note* the touch and act
in the next frame; do the same in your own event blocks (use the `destroyLater` helper or a behavior block, not `destroy` inside a collision event).

**Camera.** `CameraFollow` places the camera, `CameraShake` adds an offset on top of it, `Health` (*Shake on hit*) can trigger the shake.
They share one camera rig, so they run in any order, with or without each other; anything else that moves the camera
(`setCameraXY`, the stage follow) simply wins for that frame. Units are editor pixels, y up. The camera is updated while the elements
are drawn, so it can lag the player by one frame; the smoothing hides it.

## Typical set-ups

- **Platformer**: player = `PlatformerPlayer` + `Health` (die action RESPAWN) + `Respawn` (fall below -300) + `CameraFollow` (bounds = level size);
  spikes = `DamageOnTouch` (knockback 250, up 300, stun 0.25); flags = `Checkpoint`; coins = `Collectible` (group `coins`) as sensors; exit = `Goal` (collect group `coins`, next scene);
  HUD: a Text with `ScoreCounter`, a ProgressItem set as the *bar* of `Health`, a Text with `Timer`.
- **Top-down / space shooter**: `Spawner` with waves on the edge of the screen (template = enemy with `Chase`/`Patrol`, `Health`, `DamageOnTouch`);
  `Turret`s with *Auto health*; `PowerUp` pickups (Shooter `fireRate` x2 for 8 s); `CameraShake` + Health *Shake on hit*; `Goal` with *must be gone* = the boss.
- **Breakout**: bricks = `Destructible` (hits 2, damaged pictures, hitter prefix `ball`, optional drop = power-up) ; ball ; `Goal` with *need touch* off and *must be gone* = the bricks.
"""

NOTES = {
    "Health": "Put a ProgressItem in *bar* and the bar follows the health (its Max and Progress are driven for you). *Stun behaviors* are the movement behaviors that are switched off during a hit that has a stun time, so knockback is visible (movement behaviors set the velocity every frame). With *Blink while invulnerable* the element flickers at 12 Hz. `die action` RESTART_SCENE reopens the running scene by its class name (all lower case); if your scene name has capitals, type it in *scene*. OPEN_SCENE with an empty scene does nothing.",
    "DamageOnTouch": "With *Repeat every* 0 a target is hurt once per touch, and again as soon as its invulnerability ends if it is still touching (standing on spikes). With *Repeat every* > 0 it is hurt at that rhythm (lava, fire). Knockback pushes away from the center of this element. Needs a Dynamic body somewhere in the contact, or the target in the *targets* list.",
    "Destructible": "The pictures are spread evenly over the damage: picture k appears when k/(n+1) of the durability is gone (3 hits and 2 pictures: the first hit shows picture 1, the second picture 2, the third breaks it). *Reset* restores the original picture. The drop is a copy of an element that you place anywhere in the scene: it is hidden at the start and its behaviors (a Collectible, a PowerUp) are active in the copy.",
    "Projectile": "Put it on the bullet element. Pair with a `Spawner` whose template is the bullet, placed on the gun (spawn at spawner), and *use element rotation* to fire where the gun points - or call the `launch` block with an angle. A Dynamic bullet should have gravity scale 0 unless *gravity* is on (the behavior sets it).",
    "Turret": "The bullets are created once and reused (at most 64), so a fast turret does not create garbage. Change *bullet size* and the image before the game starts. The turret rotates its own body: set *image angle* to the direction the picture looks (0 = right). Use *fire now* with *auto fire off* (`turret on` false) for turrets that fire on a button or timer.",
    "Spawner": "Place the spawner anywhere; the *template* is any element of the scene (it is hidden). Copies keep the template's properties, behaviors and events and are named `<template>_c<n>`. If the spawner is on the template itself, the copies do not get a spawner. *Max alive* counts the copies that exist; they leave the count when destroyed. In wave mode wave n has `wave size + wave growth * (n-1)` members; *wait until cleared* starts the next wave only when none of this spawner's copies is left. Spawning happens in the next frame after a block asks for it (safe inside collision events).",
    "Collectible": "Make the element a Sensor, so the player walks through it. The reward is added to the scene variable by name, the *group* is what a `Goal` counts. With HIDE + *respawn after* the coin comes back and counts again. `collect` lets blocks collect it (a magnet, a key press).",
    "PowerUp": "The *behavior* is the class name on the collector and the *setting* is a field of it, exactly as written in the behavior (see the parameters tables of the behaviors: `PlatformerPlayer` -> `jumpSpeed`, `speed`; `Shooter` -> `fireRate`, `damage`). The old value is remembered and put back when the time is over; taking the power-up again while it is active only extends the time. The pickup stays around (invisible) until the effects ended, then it is destroyed. Permanent effects (*duration* 0) are never put back.",
    "Checkpoint": "The respawn point is stored per *channel* in memory (it survives restarting the scene) and optionally on disk. Call `forget checkpoints` from a New Game button. A `Goal` clears the default channel when the level is won.",
    "Respawn": "Set the die action of `Health` to RESPAWN. With *lives* > 0 every respawn takes one life; on the last one `onOutOfLives` fires and the *game over scene* opens. *Lives variable* keeps a scene variable in sync, show it with a ScoreCounter. At the start of the scene the element moves to the saved checkpoint if there is one.",
    "Lifetime": "Works on any element, also on UI. The *fade* happens during the last seconds. With HIDE the element is only hidden (alpha 0, no body). `restart lifetime` brings back the full time and the look.",
    "Timer": "The text shows `prefix + time + suffix`. Countdowns round up (the last second shows 0:01 until the time is over), stopwatches round down. The color of the text changes at the warning time and returns on reset. *Variable* receives the seconds (so a Goal or Door can check them). `onTick(seconds)` fires once per second.",
    "ScoreCounter": "Put it on the Text itself or point *display* at one. The number runs to the new value over at most *animate time*. The high score is stored on the device under *high score key* (Preferences, at most once a second); give each level its own key. `erase high score` resets it.",
    "Door": "Set the element as a Sensor. TELEPORT with another Door at the destination gives a two-way portal: a teleported element is ignored by doors for one second. *Need variable* / *consume* implement keys: a Collectible adds 1 to `keys`, the Door needs 1 and takes it. WHEN_PRESSED works with a button element or the Up / W key.",
    "Goal": "*Collect group* is satisfied when every Collectible of that group has been collected (collectibles destroyed for other reasons are not counted). *Must be gone* lists elements that must be destroyed (boss, enemies). With *need touch* off the level is won the moment the conditions are met. The *win panel* is hidden at the start and shown on the win. `onBlocked(missing)` tells how many conditions are open when the player touches the goal too early.",
    "CameraFollow": "Put it on the player (target empty) or on any element and pick the target. *Follow speed* is a catch-up rate (higher = tighter, independent of the frame rate). The dead zone keeps the camera still while the target moves inside the box. *Bounds* are a rectangle in editor pixels (your level); when the level is smaller than the screen the camera is centered. Zoom 2 = twice as close; if the scene has a Camera element, set the zoom in only one of them.",
    "CameraShake": "Strength 0..1 is added to a *trauma* that fades by itself; the displacement grows with trauma^*power* so a gentle bump and an explosion feel different. `shake camera for` keeps a minimum strength for some seconds (earthquake, engine). One CameraShake per scene is enough; Health's *Shake on hit* needs it.",
}


def docs():
    classes, jars, out, cp = build()
    if classes is None:
        return 2
    r = subprocess.run(["java", "-cp", os.pathsep.join([out, cp]), "DocGen"] + IDS, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return 1
    text = r.stdout
    summary = text.split("@@SUMMARY@@\n", 1)[1].split("@@DETAILS@@", 1)[0].strip("\n")
    details = text.split("@@DETAILS@@", 1)[1]
    for i in IDS:
        details = details.replace("@@NOTES:" + i + "@@", "**Notes.** " + NOTES.get(i, ""))
    doc = INTRO.replace("@@SUMMARY@@", summary) + "\n## Reference\n" + details.rstrip() + "\n"
    path = os.path.join(ROOT, "docs/behaviors/gameplay.md")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8").write(doc)
    print("wrote", path, len(doc), "chars")
    return 0


def build():
    cache = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
    jars = [find(cache + "/com.badlogicgames.gdx/gdx/*/*/gdx-1*.jar"), find(cache + "/com.badlogicgames.gdx/gdx-box2d/*/*/gdx-box2d-1*.jar"), find(cache + "/com.badlogicgames.gdx/gdx-bullet/*/*/gdx-bullet-1*.jar"), find(cache + "/com.github.mgsx-dev.gdx-gltf/gltf/*/*/gltf-*.jar"),
            find(cache + "/com.google.code.gson/gson/*/*/gson-2*.jar"), find(cache + "/com.badlogicgames.box2dlights/box2dlights/*/*/box2dlights-*.jar")]
    jars = [j for j in jars if j and "sources" not in j]
    classes = os.path.join(ROOT, "template/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
    if not os.path.isdir(classes):
        print("template classes missing: run  bash gradlew :template:compileDebugJavaWithJavac")
        return None, None, None, None
    tmp = tempfile.mkdtemp(prefix="gameplay_test_")
    out = os.path.join(tmp, "out")
    os.makedirs(out)
    cp = os.pathsep.join([classes] + jars)
    srcs = glob.glob(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/behaviors/*.java")) + glob.glob(os.path.join(ROOT, "tools/lib_gameplay_test/*.java"))
    r = subprocess.run(["javac", "-nowarn", "-Xlint:-options", "-d", out, "-cp", cp] + srcs, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return None, None, None, None
    return classes, jars, out, cp


def main():
    if "--docs" in sys.argv:
        return docs()
    classes, jars, out, cp = build()
    if classes is None:
        return 1
    r = subprocess.run(["java", "-cp", os.pathsep.join([out, cp]), "GameplayLibTest", ROOT], capture_output=True, text=True)
    print(r.stdout + r.stderr)
    return r.returncode


if __name__ == "__main__":
    sys.exit(main())
