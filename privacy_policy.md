# Privacy Policy — Obinot

**Last updated: 2026**

Obinot is a community fork of Binot, an open-source voice notes app. This
document explains what data Obinot handles and how.

## TL;DR

- Obinot does **not** collect any data itself. It has no servers, no accounts,
  and no telemetry.
- All your notes, audio, labels and settings live **on your device only**.
- When you use AI features, the content you send (audio or text) is
  transmitted directly from your device to the AI provider you configured
  (Google Gemini or Groq). Obinot does not see, store, or relay that data.

## What data Obinot stores on your device

| Data | Where | Purpose |
|---|---|---|
| Notes (title, raw text, summary, labels, highlights) | Room database (`binot_db`) | Core functionality |
| Audio recordings | Internal storage (`files/audio_records/`) | Audio playback and AI transcription |
| App settings (theme, language, provider, API keys, name) | DataStore (`settings.preferences_pb`) | Persistence of user preferences |

You can delete all of it at any time via **Settings → Data & System**, or by
uninstalling the app.

## What data leaves your device

Only when you explicitly use an AI-powered feature (transcription,
summarization, title generation, AI explain). In those cases:

- **Google Gemini** — audio files and/or text are sent to Google's API using
  your personal API key. See [Google's Privacy Policy](https://policies.google.com/privacy).
- **Groq** — audio files and/or text are sent to Groq's API using your
  personal API key. See [Groq's Privacy Policy](https://groq.com/privacy-policy/).
- **Dynamic mode** — routes requests to whichever of the two providers is
  better suited for the task, using the same rules as above.

Obinot never sees this traffic. It goes directly from your device to the
provider. You can stop this at any time by not using AI features, or by
removing your API keys from Settings.

## Permissions Obinot requests

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | To record voice notes. |
| `POST_NOTIFICATIONS` | To show the ongoing recording notification (Android 13+). |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | To keep recording alive with the screen off. |
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` | To import audio files (only when the native picker is enabled). |
| `INTERNET` | For AI features. |
| `WRITE_SETTINGS` | To mute system sounds while recording. |
| `REQUEST_INSTALL_PACKAGES` | To install in-app updates. |

## Children's privacy

Obinot is not directed at children. It does not knowingly collect any data
from anyone.

## Open source

Obinot is licensed under the MIT License. The source code is available at
[github.com/LexicoON/Obinot](https://github.com/LexicoON/Obinot).

## Contact

If you have questions about this policy, open an issue at
[github.com/LexicoON/Obinot/issues](https://github.com/LexicoON/Obinot/issues).