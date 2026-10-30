<div align="center">

<img src="./assets/icon.png" width="96" height="96" alt="Star2D icon" />

# ⭐ Star2D Engine

**A full 2D game studio that lives on your Android phone.**
Visual blocks, a real code editor, an AI assistant and GitHub sync, powered by LibGDX + Box2D.

<br>

<a href="https://github.com/star4droid/Star2D/releases/latest">
  <img src="https://img.shields.io/badge/⬇_Download_the_APK-Releases-2ea44f?style=for-the-badge" alt="Download from Releases" />
</a>

<br><br>

![License](https://img.shields.io/badge/license-Apache--2.0-blue)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Engine](https://img.shields.io/badge/LibGDX-Box2D-red)
![Release](https://img.shields.io/github/v/release/star4droid/Star2D?include_prereleases)
![Stars](https://img.shields.io/github/stars/star4droid/Star2D?style=social)

<br>

<img width="1024" height="500" alt="825378" src="https://github.com/user-attachments/assets/f802b374-eee2-4318-937d-a07a4dfe108f" />

</div>

---

## 📥 Where do I download the app?

> **The APK is in [Releases](https://github.com/star4droid/Star2D/releases/latest).**
> Open the latest release, scroll to **Assets** and download the `.apk` file.
> You do **not** need to build anything to try the engine.
>
> The source code in this page is for developers who want to read, build or contribute.

| I want to... | Go to |
|---|---|
| Install and use the engine | **[Releases → latest](https://github.com/star4droid/Star2D/releases/latest)** |
| Build it myself | [Build from source](#-build-from-source) |
| Fix a bug or add a feature | [Contributing](#-contributing) |

---

## ✨ What is Star2D?

Star2D is an **open-source 2D game engine and editor that runs entirely on a phone**.
You design scenes, wire up gameplay with blocks or Java, run the game, and export an APK, all without a computer.

Under the hood it is **LibGDX** for rendering and **Box2D** for physics, with a native Android editor on top.

---

## 🖼️ Tour

### Create games right on your phone

<img width="1080" height="1920" alt="825379" src="https://github.com/user-attachments/assets/5f674e26-cd60-4fd5-b1a3-073097b6fbb9" />

### Learn by making games *(experimental)*
Interactive lessons guide every tap, step by step.

<img width="1080" height="1920" alt="825380" src="https://github.com/user-attachments/assets/5298861c-49ee-4216-bfe6-553cbfe4f7b8" />

### Blocks or real code
Drag-and-drop blocks (nested, searchable) **or** a code editor with auto-complete, live error help and a *Copy all errors* button.

<img width="1080" height="1920" alt="825381" src="https://github.com/user-attachments/assets/3f3818f8-e99a-44fa-8fcf-28d5b214872f" />

### 56 ready-made behaviors
Chase, patrol, shoot, camera follow, camera shake and more. Add one in a tap, then tune its variables.

<img width="1080" height="1920" alt="825382" src="https://github.com/user-attachments/assets/4e7330d9-69a8-4b1f-9cb7-cd4eca0c90bf" />

### UI Maker and Button Maker
Menus, HUDs, sliders, switches, themes. Button styles: jelly, neon, candy, flat, with a pressed look.

<img width="1080" height="1920" alt="825383" src="https://github.com/user-attachments/assets/cbd3df6a-693b-4267-8574-f0df2fd8a3b4" />

### 1,831 icons, free asset packs and an animation editor
<img width="1080" height="1920" alt="825384" src="https://github.com/user-attachments/assets/f7e3d131-abcb-4a43-a66f-b01e81ee527f" />

### AI assistant and GitHub
Gemini / OpenRouter inside the editor, a Remote connector for external assistants, and Git (clone, commit, push) from the project screen.

<img width="1080" height="1920" alt="825385" src="https://github.com/user-attachments/assets/b2dfc286-eb15-42b8-b474-7ac04a8bc031" />

### Groups, Prefabs and scenes

<img width="1080" height="1920" alt="825386" src="https://github.com/user-attachments/assets/8351c32f-a7e8-4745-864c-e063d923c693" />

---

## 🚀 Feature list

- **Editor:** scenes, elements, Bodies list, files manager, undo/redo, safe-area aware layouts for portrait and landscape.
- **Visual scripting:** event-driven blocks (touch, step, collisions...), nested blocks, search, custom blocks.
- **Code editor:** Java with syntax highlighting, auto-complete, error panel, copy all errors.
- **Physics:** Box2D bodies, joints, sensors, custom collider and road editor, lights (point / cone / directional).
- **Behaviors:** 56 built-in, configurable through script variables.
- **UI tools:** UI Maker, Button Maker, themes, UI holders.
- **Assets:** 1,831-icon browser with search and color filter, free asset packs, animation editor, fonts.
- **Structure:** Groups, Prefabs (edit one, apply to all), multiple scenes, Y-sort.
- **AI:** built-in assistant (Gemini, OpenRouter) and a Remote connector. See [server/README.md](server/README.md).
- **Git:** clone, commit, push, and GitHub Actions workflows added to your game project. See [docs/CI.md](docs/CI.md).
- **Export:** produce an APK from the phone; experimental desktop export ([docs/DESKTOP.md](docs/DESKTOP.md)).
- **Languages:** English, Arabic, French, Portuguese, Russian, Spanish.

---

## 🧩 Project layout

| Path | What it is |
|---|---|
| `app/` | The Android editor (this is the APK you install) |
| `template/` | The game runtime used by exported games (generated template, see [docs/TEMPLATE.md](docs/TEMPLATE.md)) |
| `desktop/` | Experimental desktop runtime |
| `formatter/` | Code formatter used by the code editor |
| `server/` | AI / Remote connector server ([README](server/README.md)) |
| `tools/` | Build, template and i18n helper scripts |
| `docs/` | Behaviors, UI, script variables, CI and template docs |

---

## 🛠 Build from source

Requirements: **JDK 21** and the **Android SDK** (Android Studio works).

```bash
git clone https://github.com/star4droid/Star2D.git
cd Star2D
chmod +x gradlew
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

CI builds are in `.github/workflows/`. *Aab & Apk Build* can upload to Google Play (production, beta, alpha or draft) and optionally publish the APK to Releases.

> If you change the game runtime, regenerate the template (`:template` module + the *Build game template* action) and never edit `template.zip` by hand.

---

## 🤝 Contributing

Contributions of every size are welcome:

- Fix bugs or improve performance
- Add behaviors, UI styles or sample games
- Translate strings (`app/src/main/assets/i18n/`, check with `python3 tools/i18n/dump_keys.py --check`)
- Write docs and tutorials

Open an issue first for big changes. Pull requests should keep Android Java compatibility and run the i18n check.

---

## 💖 Support the project

Star2D is free and open source. If it helps you, you can support development with **USDT on the Optimism network**.

![Network](https://img.shields.io/badge/Network-Optimism_(OP_Mainnet)-FF0420?style=for-the-badge&logo=optimism&logoColor=white)
![Token](https://img.shields.io/badge/Token-USDT-26A17B?style=for-the-badge&logo=tether&logoColor=white)

```
0x85E67D4a52Cc028C3D4d3194cbbd04a9cC886D77
```

*(Use the copy button at the top-right of the box.)*

> ⚠️ Send **USDT on Optimism only**. Sending on another network may lose your funds permanently.

You can also star ⭐ the repo, share it, or support through the in-app Support page.

---

## 🌐 Community

[Facebook](https://facebook.com/groups/995354408402399/) · [WhatsApp](https://chat.whatsapp.com/Bxu3kM0b1oE9UL0iZhti4E) · [Discord](https://discord.gg/xNeKWxKnwM) · [YouTube](https://youtube.com/@star4droid) · [Telegram (developer)](https://t.me/str4droid1)

---

## 🇸🇦 بالعربية

**Star2D** محرك ألعاب ثنائية الأبعاد مفتوح المصدر يعمل بالكامل على هاتف أندرويد: بلوكات، محرر أكواد، 56 سلوكًا جاهزًا، صانع واجهات وأزرار، 1,831 أيقونة، مساعد ذكاء اصطناعي، ومزامنة مع GitHub.

> **تحميل التطبيق: من [Releases](https://github.com/star4droid/Star2D/releases/latest)**، افتح آخر إصدار ونزّل ملف `.apk` من قسم **Assets**.

---

> [!NOTE]
> You are free to use and modify this project under the Apache-2.0 license, but you must keep attribution to the original source. See [LICENSE](LICENSE) and [assets/note.txt](assets/note.txt).

> [!CAUTION]
> Some bugs may still exist. Reports and fixes are highly appreciated. 🚒
