# Script variables (Body Script / Scene Script)

Fields written in the Java code of an element's **Body Script** (`java/com/star4droid/Game/Scripts/<scene>/<name>Script.java`) or of the
**Scene Script** (`java/com/star4droid/Game/SceneScript/<scene>Script.java`) show up in the Properties panel (element) or the Variables
panel (scene) with an **auto** badge. A value set there replaces the initializer of the code when the scene starts. Behaviors keep
their own, stricter rule (public instance fields only, see BEHAVIORS.md).

## Which fields are variables

| field | variable? |
|---|---|
| `public` / package-private (no modifier) | yes |
| `static` (public or package-private) | yes, assigned on the class |
| `private` / `protected` + `@Expose` | yes (Unity's `[SerializeField]`) |
| `private` / `protected` without `@Expose` | no |
| `final`, `static final`, `transient`, `@Hidden`, fields of nested classes, local variables, anything in a method | no |

Types: `int long short byte float double boolean String`, `Color` (libGDX), `public enum` (nested; a non-public enum is skipped because the generated scene code, in another package, could not name its constants), element references (`PlayerItem`, `BoxBody`, `CircleItem`, `CustomBody`, `TextItem`, `ProgressItem`, `Joystick`, `GroupItem`, `ParticleItem`, `CameraItem`, `MapItem`, `UIHolder`), **`UINodeRef`** (an inner element of a UI holder, stored as `"holder/nodeId"`; a node is not a `PlayerItem`, so a `PlayerItem` variable cannot point at one: declare it as `UINodeRef`; see UI.md), `@Image String` / `@Sound String`, and **lists** of all of those: `List<T>`, `ArrayList<T>`, `LinkedList<T>`, `Collection<T>`, `T[]`. Anything else (e.g. `Map`, `Body`, arrays of arrays) is ignored. Annotations `@Param(desc,label,min,max,step,unit)`, `@Header`, `@Hidden` work as in behaviors.

```java
import com.star4droid.template.Utils.Expose;
public class HeroScript extends com.star4droid.Game.Refs.mainItemRefs {
    public float speed = 5;                       // property "Speed"
    float jumpTime = 0.3f;                        // package-private: also exposed (add @Hidden or make it private to hide it)
    @Expose private int lives = 3;                // private but exposed
    static int deaths;                            // exposed, assigned on the class
    public List<PlayerItem> enemies;              // list of elements
    public int[] waves = {10, 20, 30};            // list of numbers, default from the initializer
    UINodeRef scoreLabel;                         // pick "hud/score" (inner element of the UI holder "hud")
}
```
`@Expose` is `com.star4droid.template.Utils.Expose` (new Body / Scene scripts already import it). The editor only reads it from the source text.
A `static` variable is assigned once per element that has an override, last one wins; use it for values shared by all instances.

## Auto-detect

When the code editor is left (back, save, switching app: `CodeEditorActivity.onStop`) `ui/behaviors/ScriptVarWatcher.onEditorClosed(path)` runs on a **background thread**:

1. the path decides what the file is (a Body Script or a Scene Script; any other file is ignored),
2. `SourceScanner.scanScript` reads the fields (comments / strings are masked, method bodies and nested classes skipped). A file whose braces are not balanced or that has no class is **not** used (a half-typed file never wipes stored values),
3. `ScriptVarSync.sync` updates the stored map, then the GL thread writes the element property `scriptVars` (all elements that use the script, in the open scene; the saved scene file of other scenes is patched directly), the scene file `Events/<scene>/public/scriptvars.json` is written for the scene script, and the open Properties / Variables panel is redrawn.

Failures are written to `logs/script variables.txt` and otherwise ignored; leaving the editor never waits for the scan. The Properties panel also reconciles on its own whenever it is built (old projects, a script edited by the AI or the Files panel).

### Stored format

```json
{"speed": 420, "enemies": ["e1", "e2"], "waves": [5, 6], "manual": 1,
 "$auto": {"speed": "float", "enemies": "List<PlayerItem>", "waves": "int[]", "lives": "int"}}
```
* Plain keys are the values the user changed (overrides); a missing key means "use the initializer".
* `$auto` (name -> type as written in the code) records the fields that were found in the code the last time. It is bookkeeping only; the code generator and the panel always re-scan the script, so they never depend on it. `$`-keys are hidden from the AI tool (`behaviors vars`).
* On every sync: a field in `$auto` that is **gone** (deleted or renamed) loses its `$auto` entry and its value; a value that no longer **fits** the type (e.g. a list after the field became a `String`, an enum constant that was removed) is dropped, numbers survive `int` -> `float` etc.; everything else is kept. **Keys that never were in `$auto`** (set by hand or by the AI) are never touched. The sync is idempotent.
* No fields and no values: the property / file is empty again.

## Lists

A list variable shows one row per item with the editor of its element type (number pad, text, switch, colour, enum chooser, image / sound picker, **element picker**), move up / down, remove, and an "Add" button (for element lists "Add" opens the picker). The list is stored as a JSON array and emitted as

| code type | generated |
|---|---|
| `List<Integer>`, `ArrayList<..>`, `Collection<..>` | `new java.util.ArrayList<Integer>(java.util.Arrays.asList(1, 2))` (`new ...ArrayList<T>()` when empty) |
| `LinkedList<T>` | `new java.util.LinkedList<T>(...)` |
| `int[]` ... `T[]` | `new int[]{1, 2}` |
| element lists | `BehaviorRegistry.refs(this, PlayerItem.class, "e1", "e2")` (`.toArray(new T[0])` for arrays); missing / wrong-type elements are skipped |

Lists work for the scene script too (Variables panel > Scene script variables).

## Generated assignments

After all elements exist and before the scene `OnCreate` (see BEHAVIORS.md "Storage"). Public instance fields: `((HeroScript) hero.getScript()).speed = 420f;`. Everything else goes through `com.star4droid.template.Utils.ScriptVars` (plain reflection, errors are logged and skipped, never thrown): `ScriptVars.set(target, "jumpTime", 0.5f)`, `ScriptVars.setStatic(HeroScript.class, "deaths", 3)`; public static fields are assigned directly (`HeroScript.gravity = -1.5f;`). A value is only emitted when the user changed it.

## Files

Editor (not in the game): `behaviors/SourceScanner.scanScript`, `behaviors/ScriptVarSync` (pure Java), `behaviors/VarSpec` (`isStatic`, `access`, `container`, `auto`, `signature()`), `behaviors/BehaviorCodeGen.assignVars`, `ui/behaviors/ScriptVarWatcher`, `ui/behaviors/VarEditors` (badge, list editor). Runtime: `template/Utils/Expose`, `template/Utils/ScriptVars`, `template/ui/UINodeRef`, `UIListeners`.
Tests: `python3 tools/test_scriptvars.py` (scanner, sync, generated code compiled **and run** against a real script class), `tools/test_behaviors.py`.

## Object variables (Variables section of an element)

Every element (2D and 3D, not lights) has an **Object variables** card in the Properties panel: variables that belong to the
object and are usable by name in **every** event of it (all its block events), not only in the event that creates them.

| type (editor) | Java field | initial value |
|---|---|---|
| Whole number / Number / Precise number | `int` / `float` / `double` | number (or an expression) |
| Text | `String` | text |
| Yes / no | `boolean` | `true` / `false` |
| List | `java.util.ArrayList<Object>` | items separated by commas: `1, 2.5, apple` |
| Element | `PlayerItem` | element name (long-press the field to choose), found again once all elements exist |

**Stored** in the element property `objectVars` (`[{"name":"health","type":"int","value":"3"}]`), so it follows save / load,
copy / paste, prefabs and scene copy like any other property. Absent or empty = no variables (old projects are unchanged).

**Blocks.** *Variables & data* has
* **Object variable** (`type name value`): declares a variable of the object from any of its events (marker comment
  `/*@objvar type|name|value*/`, no code of its own). The Properties card lists it with "made by a block"; tapping it copies it
  into the list (the list wins over the block for the type / initial value);
* a **Local variable** block in an element event can be turned into an object variable: long-press it (or the selection bar
  button) > *Make it an object variable*;
* **Set / change variable of object** (`element variable value`) and the values **number / text / yes-no variable ___ of ___**
  read and write the variables of another element.
The variable picker of the blocks editor lists the scene variables, the object variables of the element and the ones made by
blocks in any of its events.

**Generated code.** An element with object variables gets a local event class instead of the anonymous adapter:
```java
class hero_Events extends ElementEventAdapter {
    hero_Events(){ super("hero"); }
    public int health = 3;
    public PlayerItem target = findItem("enemy");
    @Override public ElementEventAdapter fresh(){ return new hero_Events(); }
    @Override public void onClick(PlayerItem current) { health = health - 1; }
}
hero_def.elementEvents = new hero_Events();
```
plus `ObjectVars.set(hero, "target", findItem("enemy"));` after every element exists (before the scene `OnCreate`).
`fresh()` is called by the definitions' `clone()` (`ElementEventAdapter.copyOf`): each clone has its own variables, starting from
the initial values; adapters without variables are still shared.

**From anywhere else** (other elements, Java Body / Scene scripts): `com.star4droid.template.Utils.ObjectVars`
`get / set / add / getFloat / getInt / getDouble / getString / getBool / getItem / getList / has / names`, or on the element
`item.getVar("health")`, `item.setVar("health", 3)`, `getVarNumber`, `getVarText`, `getVarBool` (in a Body Script:
`getItem().setVar(...)`). Numbers are converted to the variable's type; a name the element does not declare is kept in a side
map. Inside the object's own events use the bare name.

Note: event files belong to the element's **script** (`Script` property); elements that share a script share the event code, so
each of them must declare the variables that code uses (block-made variables are declared for all of them automatically).

Files: `behaviors/ObjectVarsGen` (storage, markers, generated code), `ui/behaviors/ObjectVarsSection` (card), `Helpers/CodeGenerator`,
`ui/blocks/BlockCatalog` / `BlockEditorActivity` (blocks, picker, promote), runtime `template/Utils/ObjectVars`, `ElementDefs/ElementEventAdapter`.
Test: `python3 tools/test_objectvars.py` (generated class compiled and run: shared across events, outside access, clones).
