# Building a game on GitHub (Actions)

Every project that is put under Git by the editor gets `.github/workflows/` with:

| File | Result |
|---|---|
| `build-android.yml` | signed `game.apk` (artifact `game-apk`) |
| `build-desktop.yml` | Windows and Linux zips (artifacts `game-windows`, `game-linux`) - experimental, see DESKTOP.md |

Both fetch this engine repository (`star4droid/Star2D`, branch/tag chosen when you run the workflow) and use `tools/ci/make_game.py`,
which does what "Export APK" does on the phone: template + encrypted `project.zip` + manifest + icon, then zipalign + apksigner.

**Before pushing** run the game once in the editor: it writes `dex/scenes.dex` (the compiled scenes), which is committed (the default `.gitignore` allows it).
Signing: without secrets a throw-away key is used. For a stable key add the secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`.
The token used to push needs the `workflow` scope, otherwise GitHub refuses the push of `.github/workflows`.

Not available: web (no browser backend in the engine) and iOS (needs a Mac toolchain and a different runtime).

## Release notes

`RELEASE_NOTES.md` (English + Arabic) is the body of the GitHub release made by **build-aab.yml** (GitHub adds its generated list of changes under it). Google Play "What's new" texts are `whatsNewDirectory/whatsnew-en-US` and `whatsnew-ar` (500 characters max each). Update both before a release.
