# Variables: score, lives, flags, saved data

## Scene variables

A scene variable is a field of the scene class: every event of that scene can read and write it by name, in raw-Java fields and in reporters. Manage them with the `variables` tool (batch):

```json
{"ops":[
 {"op":"add","name":"score","type":"int","value":"0"},
 {"op":"add","name":"lives","type":"int","value":"3"},
 {"op":"add","name":"speed","type":"float","value":"300f"},
 {"op":"add","name":"playerName","type":"String","value":"\"Hero\""},
 {"op":"add","name":"gameOver","type":"boolean","value":"false"},
 {"op":"set","name":"lives","value":"5"},
 {"op":"delete","name":"speed"},
 {"op":"list"}
]}
```

- `type`: `int`, `float`, `double`, `long`, `boolean`, `String` (the editor's panel offers these). Any Java type name is pasted as is.
- `value` is **raw Java**, written after `=`: numbers as is, **float needs the `f` suffix** (`0.5f`), **String needs quotes** (`"Hero"`), booleans `true`/`false`. An empty value gives the Java default (0, false, null). The tool takes `value` as a string.
- Names: letters, digits, `_`, not starting with a digit; must not equal an element name or an engine member (`world`, `steps`, `playing`, `project`, `finished`).
- Pass `"scene":"level2"` (in the op or at the top level) to edit another scene's variables. The variables of one scene are not visible from another.
- The file is `Events/<scene>/public/variables.json`; `list` shows it.

## Using them in blocks

- `setVariable(variable, value)`: `score = value;`. Example: `{"block":"setVariable","fields":{"variable":"score","value":0}}`.
- `changeVariable(variable, by)`: `score += by;` (use a negative number to subtract).
- In any plain field type the variable name: `{"block":"setVelocityX","fields":{"body":"player","speed":"speed"}}`, or in a reporter arg: `["score", 10]`.
- Show it: `setText` with `{"r":"join ___ ___","args":[{"r":"\"___\"","args":["Score: "]},"score"]}` (see `blocks-examples`).
- Types must match: an `int` variable cannot receive a float. Use `to int ___` / `round ___`, or declare the variable `float`.
- `newVariable(type, name, value)` makes a LOCAL variable that exists only in this event (and only later in the same block sequence).

## Variables reset with the scene

When a scene starts (also when you `OpenScene` the same scene again) its variables get their initial values from the `variables` tool. They are not kept between scenes.

## Persistent data (between scenes and between runs)

Stored on the phone as text in the game's own save file (`game_<project folder>`, shared by all scenes of the game, separate from the editor's settings and from other games).

- Save: block `saveData(key, value)`: `{"block":"saveData","fields":{"key":"best","value":"score"}}`. The key is plain text (no quotes); the value is a Java expression.
- Read a number: reporter `saved number ___` (int; missing or non-numeric = 0): `{"r":"saved number ___","args":["best"]}`.
- Read text: reporter `saved text ___` (missing = empty string).
- Java: `setValue("key","text")`, `getValue("key")`, `getNumber("key")` (int).
- Clear everything the game saved: block `clearSaves` (no fields): `{"block":"clearSaves"}`. Java: `clearSaves()`.
- Do not use the blocks `saveValue` / `setsplits` (they do not compile).
- Saved data persists between game runs on the phone, so a test that depends on a clean start should write the value first (`saveData` with 0 in `OnCreate`) or accept the old value.

## Checking variable values while testing

`game_state` returns the current values of the scene's `int/float/boolean/String` variables, and `run_test` can assert them: `{"do":"assert","prop":"var:score","op":">=","value":3}`. See `testing`.
