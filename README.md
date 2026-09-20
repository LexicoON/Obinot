<p align="center">
  <img src="https://raw.githubusercontent.com/LexicoON/Obinot/dev/store-assets/icon.png" width="120" height="120" alt="Obinot" />
</p>

<h1 align="center">Obinot</h1>

<p align="center">
  <strong>A modern, actively-maintained community fork of Binot — rebuilt for the Material 3 Expressive + AI era.</strong>
</p>

<p align="center">
  <a href="https://github.com/DENSLnetion/Binot"><img src="https://img.shields.io/badge/upstream-DENSLnetion%2FBinot-2ea44f?style=for-the-badge&logo=github&logoColor=white" alt="Upstream" /></a>
  <img src="https://img.shields.io/badge/fork-LexicoON%2FObinot-blue?style=for-the-badge&logo=github&logoColor=white" alt="Fork" />
  <img src="https://img.shields.io/github/v/release/LexicoON/Obinot?style=for-the-badge&color=blue" alt="Release" />
  <img src="https://img.shields.io/badge/license-MIT%20%2B%20Apache--2.0-blue?style=for-the-badge" alt="License: MIT + Apache 2.0" />
</p>

<p align="center">
  <em>👉 Please support the original creator of Binot: <a href="https://github.com/DENSLnetion/Binot">github.com/DENSLnetion/Binot</a></em>
</p>

## Screenshots

<p align="center">
  <img src="https://raw.githubusercontent.com/LexicoON/Obinot/dev/store-assets/readme/cover.png" width="780" alt="Obinot — voice notes, structured by AI" />
</p>

<p align="center">
  <img src="https://raw.githubusercontent.com/LexicoON/Obinot/dev/store-assets/readme/features.png" width="780" alt="Dark mode, colored labels, and offline diagrams" />
</p>

## What is Obinot?

Obinot is a native Android app that turns spoken words into clean, structured Markdown notes. Speak naturally and let AI tidy, summarize, analyze, or translate the result — all stored locally, all under your control.

It is a community-maintained fork of [Binot](https://github.com/DENSLnetion/Binot), originally created by **[@DENSLnetion](https://github.com/DENSLnetion)** and released under the MIT License. The original stopped working when AI providers retired the hardcoded model names. This fork migrates to current models, adopts Material 3 Expressive, and adds quality-of-life features.

Obinot is distributed as a **completely independent app** (`com.obinot.app`) so both Binot and Obinot can coexist on the same device. Data portability is preserved: `.binot` notes and `.binotbak` backups are fully cross-compatible.

## Features

- 🎙️ **Voice capture** — Fast mode (on-device) or Accurate mode (full audio + AI transcription). Background recording, live waveform, optional live transcript.
- 🤖 **Multi-provider AI** — Bring your own Gemini or Groq API key, or use Dynamic mode to route tasks automatically and stretch free quotas.
- 🧠 **Tidy · Summary · Analyze** — Clean up rough transcripts, extract key points, or identify sentiments and action items. 23 output languages.
- 💬 **Chat about any note** — Ask follow-up questions directly from the reader. The conversation is saved with the note and travels in backups.
- 📖 **Custom Markdown reader** — Headers, lists, tables, code blocks, blockquotes, links, and offline RaTeX math (block + inline) + Mermaid diagrams. Long-press any formula block to copy its LaTeX source.
- ✏️ **Markdown summary editor** — Edit AI-processed summaries in raw Markdown with a live preview toggle.
- ✅ **Interactive checkboxes** — Tappable Markdown checklists that persist.
- ✏️ **Highlights with notes** — Select any text to highlight it and attach a personal note.
- 🔍 **Full-text search** — Room FTS4 with prefix matching, instant across the entire database.
- 🏷️ **Colored labels** — Assign a color to each label for easy scanning.
- 🗂️ **Local-first organization** — Custom labels, multi-select, pin, duplicate, trash with recovery.
- 📦 **Export & backup** — `.binot` notes, `.obinotbak` full backups, legacy Binot 1.x backups, Markdown export.
- ✨ **Material 3 Expressive** — Spring physics, animated bouncy components, wavy audio progress bar, 7 color palettes, dark + AMOLED themes, adaptive layouts for phone and tablet.
- 🌐 **English + Spanish** — In-app language picker, plus system-level per-app locale on Android 13+.

## Migrating from Binot 1.x

Obinot installs as a **separate app** from Binot 1.x. To bring your data over:

1. Open Binot 1.x → **Settings → Data & System → Backup**.
2. Save the `.binotbak` file.
3. Open Obinot → **Settings → Data & System → Import**.
4. Select the backup.

Notes, audio, labels, preferences, and any AI chat history saved with notes are restored. API keys are not included (re-enter once in Obinot).

> **What's in a backup vs. a `.binot` export?**
> Full backups (`.obinotbak`) include everything: notes, audio, labels, preferences, and the AI chat history attached to each note. Single-note `.binot` exports include only the note's content, audio, and labels — **not** the chat history. This is intentional: `.binot` is a portable note format, not a conversation archive.

## Download

Latest APK: [github.com/LexicoON/Obinot/releases](https://github.com/LexicoON/Obinot/releases) — ships as `Obinot-vX.Y.Z.apk`.

For the original Binot: [DENSLnetion/Binot/releases](https://github.com/DENSLnetion/Binot/releases).

## Building from source

Requires JDK 21 and Android SDK with API 37.

```bash
git clone https://github.com/LexicoON/Obinot.git
cd Obinot
./gradlew assembleDebug
```

Release builds need signing keys via environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`). CI injects them from GitHub Secrets.

## Privacy

Obinot has no servers, no accounts, and no telemetry. All notes, audio, and settings stay on your device. AI features send data directly from your device to your chosen provider using your own API key. Crash logs are saved locally and only shared if you explicitly tap "Share".

Full policy: [privacy_policy.md](privacy_policy.md). Play Store data safety notes: [data_safety.md](data_safety.md).

## License & Attribution

Obinot is distributed under **two licenses**, depending on which part of the code you're looking at:

- **The original Binot codebase** is licensed under the **MIT License**. Copyright © 2026 DENSLnetion. See [LICENSE](LICENSE).
- **The fork-specific code** (new features, modifications, and everything added on top of the original) is licensed under the **Apache License 2.0**. Copyright © 2026 LexicoON. See [LICENSE-OBINOT](LICENSE-OBINOT).

Obinot also bundles third-party libraries under their own permissive licenses:
- **[RaTeX](https://github.com/erweixin/RaTeX)** — MIT License. Native LaTeX rendering engine (Rust core + Android JNI bindings), used for block and inline math.
- **[Mermaid](https://github.com/mermaid-js/mermaid)** — MIT License. Diagram rendering, bundled as a JS asset.
- **Jetpack Compose, Room, Retrofit, Moshi, MaterialKolor, and other AndroidX / Kotlin libraries** — Apache License 2.0 or MIT, as declared by their respective maintainers.

This project is a fork of [Binot](https://github.com/DENSLnetion/Binot), originally created by [DENSLnetion](https://github.com/DENSLnetion). The original was released under MIT, and that license is preserved in full. We are deeply grateful to DENSLnetion for creating Binot and releasing it under an open license, which made this fork possible.

**If you enjoy Obinot, please also consider starring and supporting the original project:**
👉 [github.com/DENSLnetion/Binot](https://github.com/DENSLnetion/Binot)