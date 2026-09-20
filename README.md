# README — versión C completa (sin cortes)

```markdown
<h1 align="center">Obinot</h1>

<p align="center">
  <strong>A modern, actively-maintained community fork of Binot — rebuilt for the Material 3 Expressive + AI era.</strong>
</p>

<p align="center">
  <a href="https://github.com/DENSLnetion/Binot"><img src="https://img.shields.io/badge/upstream-DENSLnetion%2FBinot-2ea44f?style=for-the-badge&logo=github&logoColor=white" alt="Upstream" /></a>
  <img src="https://img.shields.io/badge/fork-LexicoON%2FObinot-blue?style=for-the-badge&logo=github&logoColor=white" alt="Fork" />
  <img src="https://img.shields.io/github/v/release/LexicoON/Obinot?style=for-the-badge&color=blue" alt="Release" />
  <img src="https://img.shields.io/github/license/LexicoON/Obinot?style=for-the-badge" alt="License" />
</p>

<p align="center">
  <em>👉 Please support the original creator of Binot: <a href="https://github.com/DENSLnetion/Binot">github.com/DENSLnetion/Binot</a></em>
</p>

## Screenshot

<img width="1920" height="3234" alt="Screenshot" src="https://github.com/user-attachments/assets/f6ea2734-ae8b-4cf1-a493-3ede4b031b11" />

## What is Obinot?

Obinot is a native Android app that turns spoken words into clean, structured Markdown notes. Speak naturally and let AI tidy, summarize, analyze, or translate the result — all stored locally, all under your control.

It is a community-maintained fork of [Binot](https://github.com/DENSLnetion/Binot), originally created by **[@DENSLnetion](https://github.com/DENSLnetion)** and released under the MIT License. The original stopped working when AI providers retired the hardcoded model names. This fork migrates to current models, adopts Material 3 Expressive, and adds quality-of-life features.

Obinot 2.0 is distributed as a **completely independent app** (`com.obinot.app`) so both Binot and Obinot can coexist on the same device. Data portability is preserved: `.binot` notes and `.binotbak` backups are fully cross-compatible.

## Features

- 🎙️ **Voice capture** — Fast mode (on-device) or Accurate mode (full audio + AI transcription). Background recording, live waveform, optional live transcript.
- 🤖 **Multi-provider AI** — Bring your own Gemini or Groq API key, or use Dynamic mode to route tasks automatically and stretch free quotas.
- 🧠 **Tidy · Summary · Analyze** — Clean up rough transcripts, extract key points, or identify sentiments and action items. 23 output languages.
- 📖 **Custom Markdown reader** — Headers, lists, tables, code blocks, blockquotes, links, and offline KaTeX math + Mermaid diagrams.
- ✅ **Interactive checkboxes** — Tappable Markdown checklists that persist.
- ✏️ **Highlights with notes** — Select any text to highlight it and attach a personal note.
- 💡 **AI Explain** — Select any text and ask AI to explain it in context.
- 🔍 **Full-text search** — Room FTS4 with prefix matching, instant across the entire database.
- 🏷️ **Colored labels** — Assign a color to each label for easy scanning.
- 🗂️ **Local-first organization** — Custom labels, multi-select, pin, duplicate, trash with recovery.
- 📦 **Export & backup** — `.binot` notes, `.obinotbak` full backups, legacy Binot 1.x backups, Markdown export.
- ✨ **Material 3 Expressive** — Spring physics, 7 color palettes, dark + AMOLED themes, adaptive layouts for phone and tablet.
- 🌐 **English + Spanish** — In-app language picker, plus system-level per-app locale on Android 13+.

## Migrating from Binot 1.x

Obinot 2.0 installs as a **separate app** from Binot 1.x. To bring your data over:

1. Open Binot 1.x → **Settings → Data & System → Backup**.
2. Save the `.binotbak` file.
3. Open Obinot → **Settings → Data & System → Import**.
4. Select the backup.

Notes, audio, labels, and preferences are restored. API keys are not included (re-enter once in Obinot).

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

Obinot is licensed under the **MIT License**.

This project is a fork of [Binot](https://github.com/DENSLnetion/Binot), originally created by [DENSLnetion](https://github.com/DENSLnetion). The original Binot was released under MIT, which requires preserving its copyright notice and license text. The [LICENSE](LICENSE) file retains DENSLnetion's copyright alongside ours.

We are deeply grateful to DENSLnetion for creating Binot and releasing it under an open license.

**If you enjoy Obinot, please also consider starring and supporting the original project:**
👉 [github.com/DENSLnetion/Binot](https://github.com/DENSLnetion/Binot)
```
