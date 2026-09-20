# Data Safety Form — Obinot (checklist para Google Play Console)

Esta es la información que hay que ingresar en el **Data Safety** form de
Play Console. Está en inglés porque Play Console está en inglés, pero la
intención es que sea una guía rápida.

## Data collection and security

**Does your app collect or share any of the required user data types?**
→ **No**.

Justificación:
- La app no tiene backend propio.
- No hay analytics, telemetría, crash reporting automático, ni publicidad.
- No hay cuentas de usuario.
- Los datos de las notas viven exclusivamente en el dispositivo.

Cuando el usuario **decide usar una feature de IA**, los datos (audio y/o
texto) se envían directamente desde su dispositivo a Google Gemini o Groq,
usando la API key personal del usuario. Obinot no ve ni almacena esos datos.
Como el envío es iniciado por el usuario y va directo a un tercero con quien
él tiene su propia relación (la API key es suya), **no cuenta como
"collection" del desarrollador**.

## Data types (si el form pregunta específicamente)

Para cada categoría, la respuesta es **"Not collected"**:

- Personal info (name, email, etc.): Not collected
- Financial info: Not collected
- Health and fitness: Not collected
- Messages: Not collected
- Photos and videos: Not collected
- Audio files: Not collected
- Files and docs: Not collected
- Calendar: Not collected
- Contacts: Not collected
- App activity: Not collected
- Web browsing: Not collected
- App info and performance: Not collected
- Device or other IDs: Not collected

## Data safety section

**Is all of the user data collected by your app encrypted in transit?**
→ Yes (todas las llamadas a Gemini y Groq usan HTTPS).

**Do you provide a way for users to request that their data is deleted?**
→ Yes. El usuario puede borrar todas sus notas desde Settings, o desinstalar
la app.

## Notes for reviewers (si Play Console pide aclaración)

Obinot is an open-source voice-notes app. It has no servers and no telemetry.
When AI features are used, the user's own audio and text are sent directly to
the AI provider (Google Gemini or Groq) that the user configured with their
own API key. Obinot does not intermediate this communication, does not see
it, and does not store it.

The app asks for RECORD_AUDIO to record voice notes, and for POST_NOTIFICATIONS
+ FOREGROUND_SERVICE_MICROPHONE to keep recording alive with the screen off.