# Nabra: personal voice assistant for Android

A private Android app (Kotlin + Jetpack Compose) that controls the whole phone by
voice in **Egyptian Arabic**. Speech recognition runs **offline** on the phone (Vosk).
You build your own commands: say one word, and the app runs a chain of actions.

> Example: say **"يا مساعد … واحد"**. Nabra sends your last 2 photos to Claude,
> waits, types **"حلهم بالترتيب بالإيطالي"**, and presses **Send**.

Personal use only. Not for Google Play (it uses `QUERY_ALL_PACKAGES` and an
AccessibilityService that controls other apps).

---

## Get the APK (no computer needed)

Every push builds a debug APK with GitHub Actions.

**Fastest:** direct link to the latest APK:
https://github.com/tiktok7daniel-stack/Recognize-my-voice-/releases/download/latest/Nabra.apk
(also listed on the repo's **Releases** page as "نبرة — آخر نسخة").

Or from the build artifacts:

1. Open the repo on GitHub, then **Actions** → **Build APK** → the latest green run.
2. Under **Artifacts**, download **Nabra-debug-apk** (a zip file), then unzip it.
3. Open the `.apk` on your phone and allow "Install unknown apps".

To build manually, use **Actions → Build APK → Run workflow**.

Local build (optional): `./gradlew assembleDebug` (JDK 17 and Android SDK 35).

## First launch

1. **Intro**, then a **step-by-step tutorial** (12 short lessons: how listening works, the
   Accessibility "hands", the voice model, ready-made commands, opening apps, building your
   first command, troubleshooting, AI). You can reopen it any time from *Home → "إزاي أستخدم نبرة؟"*
   or *Settings*. Then **Permissions**. Each permission has a status chip, an explanation,
   and a button that opens the right settings page:
   * Microphone, and the Accessibility service ("the hands"). These two are essential.
   * Photos, Notifications, Ignore battery optimizations, Display over other apps.
   * **Android 13+ sideloaded apps**: if the Accessibility switch is greyed out, open
     *App info → ⋮ → Allow restricted settings*, then enable it.
   * There is an OEM card (Xiaomi / Samsung / Huawei / Oppo / Vivo) with links to their
     autostart and battery screens, plus dontkillmyapp.com.
2. **Voice model download** (once, with a progress bar and resume support). If the
   download fails, you can retry or import a zip you downloaded in the browser.
3. **Home**: tap the glowing orb to start listening.

## Vosk Arabic model

| Model | Size | Notes |
|---|---|---|
| `vosk-model-ar-mgb2-0.4` (**default**) | ~318 MB | Trained on MGB-2 broadcast Arabic (MSA + dialects). Best accuracy/size trade-off for a phone. |
| `vosk-model-small-ar-tn-0.1-linto` | ~160 MB | Small and fast, supports runtime grammar, but Tunisian dialect. |
| `vosk-model-ar-0.22-linto-1.1.0` | ~1.3 GB | Very accurate, too heavy for most phones. |

There is no official Egyptian Vosk model. Nabra makes up for it in two ways:

* **Grammar mode.** When the model supports runtime graphs (`graph/HCLr.fst` + `Gr.fst`),
  the recognizer is limited to a **dynamic grammar**: the wake word, the system
  phrases, "افتح" + every app name and alias, and all your triggers. This is far more
  accurate and avoids false triggers. The grammar is rebuilt automatically when apps
  are installed or removed, or when commands, aliases or the wake word change.
* **Free-form fallback.** With a static-graph model, Nabra recognizes free text and
  matches it with normalized fuzzy matching. Normalization strips diacritics and
  tatweel, maps أ/إ/آ → ا, ة → ه, ى → ي, and converts Arabic digits.

You can pick a model or paste a custom zip URL in *Settings → Voice model*.

## Recognition engines

* **Wake word**: always Vosk, offline and on the phone.
* **The command itself** (choose in *Settings → Command engine*):
  * **Google** (default): Android's speech recognizer in `ar-EG`. It returns up to 5
    alternatives, and Nabra picks the first one that matches a real command. On Android 13+
    it is biased toward your own triggers and app names. If it fails (no network, busy,
    blocked), Nabra falls back to Vosk automatically.
  * **Vosk**: 100% offline.
* **Direct triggers**: your own triggers (for example "واحد") also work without the wake word.
  You can turn this off in Settings.
* **Faster ways to give a command**: the "قول أمر دلوقتي" button on Home, a draggable
  **floating mic bubble** over all apps, a **Quick Settings tile**, and a long-press
  **app-icon shortcut**.

## Talk normally (natural requests)

* An **offline Egyptian intent parser** runs instantly and needs no AI. It understands:
  * messages: "كلم محمد على الماسنجر وقوله…", "ابعت لأحمد على الواتس…", "قول لماما إن…"
  * calls, alarms and timers: "اتصل بـ…", "صحيني الساعة ٧ ونص", "تايمر ٥ دقايق"
  * search and navigation: "دور على … في يوتيوب", "وديني …"
  * volume, brightness and real Wi-Fi/Bluetooth toggles: "خلي الصوت ٥٠", "شغل الواي فاي"
* **Skills**: WhatsApp messages open a deep link to the contact's chat (resolved from your contacts)
  and then press send. Messenger, Telegram and Instagram are driven through the UI: search, open
  the chat, type, send. SMS, calls (contacts lookup), AlarmClock alarms/timers, search, maps
  navigation, volume and brightness are also covered. Wi-Fi/Bluetooth are switched for real
  through the system panel and accessibility.
* **AI understanding**: any other sentence goes to the AI, which runs **on the phone, offline**
  (MediaPipe LLM, `.task` model imported or downloaded by link), or to an online model. The AI
  returns one intent: a skill, a spoken chat answer, or a multi-step screen task.
* **Skill memory**: after the AI agent completes a task, Nabra offers to save the steps as a
  normal command, so next time it runs instantly and offline.
* **Recovery**: *Click on text* scrolls and retries when the target isn't visible.
* **Telegram remote**: use your own bot with a one-time pairing code. Messages run as commands,
  and `/stop` cancels.
* **Free online model presets**: OpenRouter, NVIDIA and Groq.

## Send to an existing chat (not a new one)

Sharing photos to an app always starts a **new** chat. To keep using the same chat:

* The *Attach last N photos* step has an optional **chat link** field (for example
  `https://claude.ai/chat/…`). When it is set, Nabra opens that exact chat inside the app, taps
  **+ → Photos**, selects the newest photos, confirms, and the next steps type and send in the
  same chat.
* The new step types *Open link / specific chat* and *Attach photos inside the open chat* can be
  used separately in any command. Links for Claude, ChatGPT, Gemini, WhatsApp and Telegram open
  inside the app.
* The "واحد" command was migrated to the user's "رياضة" chat in Claude.

## Advanced features

* **Variables in triggers**: `ابعت لـ {اسم} {رسالة}` matches "ابعت لأحمد أنا جاي". Steps can use
  `{اسم}` / `{رسالة}` in *Type text*, *Click on text*, the photo text, or system-command arguments.
* **Automations**: a command can run by itself every day at a time (AlarmManager), or when a
  chosen app comes to the foreground (via the accessibility service, at most once per minute).
* **Hardware buttons**: double, triple, or long press on volume up/down can run any command
  or start listening. This uses the accessibility key filter; single presses still change
  the volume.
* **More system actions**: media play/pause/next/previous, power menu, split screen,
  swipe left/right, and an *Open link* step.
* **Execution panel**: while a command or the AI runs, a small overlay at the top shows the
  current step and a **Stop** button.
* **Voice lock**: the Vosk speaker model (`vosk-model-spk-0.4`, 13 MB) creates a voice print.
  Enroll your voice 3 times; after that, the wake word and direct triggers only work for
  similar voices (cosine similarity with an adjustable threshold).
* **On-device AI**: a MediaPipe LLM Inference provider runs a local Gemma `.task` model on the
  phone, with no internet and no Termux.
* **Battery**: when commands use the online engine and the Vosk model supports grammars,
  Vosk only listens for the wake word, your triggers, and "وقف".
* **Size**: the build is arm64-only and runs R8 shrinking (no obfuscation).

## Speed and accuracy

* The wake word triggers from **partial** Vosk results, while you are still saying it,
  instead of waiting for end-of-utterance silence.
* **Instant triggers**: a direct trigger such as "واحد" fires as soon as the partial result
  equals it. An optional 0/150/350 ms stability delay is available, and the final result is
  then ignored.
* **Early accept**: if a partial Google result is already an exact trigger or system
  command, listening stops and the command runs right away.
* The Google recognizer instance is **kept warm**, the end-of-speech silence is shorter
  (700 ms), and commands, apps and bias phrases are cached in memory.
* **Matching**: digits are mapped to Egyptian number words ("1" → "واحد"), pronunciation
  is folded (ث→ت, ذ→د, ظ→ض), and the highest-scoring of the 5 alternatives wins.
* UI steps poll every 80 ms. *Press send* waits until the button is enabled (photos uploaded),
  so the demo's fixed wait is only 0.3 s.
* **⏱ readout**: Home and the Log show the response time, from end of speech to action.

## How it works

```
"يا مساعد"  →  vibrate/beep  →  ~6 s command window  →  resolve:
   1. Custom commands   (exact / fuzzy match on all recorded trigger variants)
   2. System commands   (رجوع، الرئيسية، انزل، سكرين شوت، علّي الصوت، الكشاف…)
   3. "افتح <app>"       (labels + your aliases + built-in Egyptian names + transliteration)
   4. AI mode           (only if enabled and available)
   5. "مفهمتش"          + a notification showing what was heard
```

You can also say it in one breath: **"يا مساعد افتح يوتيوب"**. The Home screen has a
**"try a typed command"** box for testing without speaking.

### System commands
رجوع • الرئيسية • التطبيقات الأخيرة • الإشعارات • الإعدادات السريعة •
سكرين شوت (API 28+) • اقفل الشاشة (API 28+) • انزل / اطلع •
اضغط على <نص> • اكتب <نص> • علّي / وطّي الصوت • شغّل / اطفي الكشاف •
واي فاي / بلوتوث (these open the system panel, because direct toggling is restricted).

If you say "اكتب" or "اضغط على" without text, Nabra asks for the text and listens
in free-form mode.

## Adding commands (the main feature)

**My commands → New command**

1. **Trigger**: tap *Record with your voice* and say the word 2–3 times. Every
   recognized variant is saved as an accepted trigger. You can edit the text or add
   it manually.
2. **Steps**: add as many as you want, and drag ☰ to reorder them:
   * Open app (searchable list with icons)
   * **Attach last N photos to app**: uses MediaStore + `ACTION_SEND(_MULTIPLE)` with the
     target's share activity directly. This is more reliable than tapping through the UI.
   * Type text • Press send (finds Send / إرسال / Invia, or the clickable node nearest
     the input field) • Click on text • Wait (ms) • Back / Home / Scroll •
     any system command
3. **Save**. The grammar rebuilds immediately. Use **Test (▶)** to run a command without
   speaking.

Every UI step **waits for its target** by polling the accessibility tree (5 s
timeout) instead of using fixed sleeps. If a step fails, the chain stops, Nabra says
which step failed, posts a notification naming it, and writes an entry to
*Settings → Log*.

The demo command **"واحد"** is created on first launch. The Claude package is resolved
at runtime through PackageManager, not hardcoded.

**Import / export**: *Settings → Data* saves or loads all commands and aliases as JSON.

**App names**: the *Apps* tab lets you give any app extra spoken names (typed or
recorded), for example يوتيوب → YouTube or كلود → Claude.

## AI mode (optional, off by default)

Used only when no offline command matched.

* **Providers**
  * **Claude (Anthropic)** through the official `anthropic-java` SDK. The default model
    is `claude-opus-5-5`; you can change it in settings. Server-side refusal fallbacks
    are enabled.
  * **Any OpenAI-compatible API** (OpenAI, OpenRouter, Groq, Gemini's OpenAI endpoint…)
    or a **local model on the phone**: run `llama.cpp`'s server or Ollama in Termux, and
    set the Base URL to `http://127.0.0.1:8080/v1`. A local model works offline.
* The API key is encrypted with an AES-GCM key stored in the **Android Keystore**.
* **Agent loop** (max 15 steps):
  1. The visible accessibility tree is serialized as a compact numbered list
     (`index|class|text|desc|flags|bounds`).
  2. The model replies with **one** strict-JSON action: `click`, `type`, `open_app`,
     `scroll`, `back`, `home`, `wait` or `done`.
  3. Nabra runs the action, waits for the UI to settle, and repeats.
* **Safety**
  * Saying **"وقف"** cancels immediately.
  * Sending, posting, buying, paying, deleting, or anything inside system settings
    needs **confirmation**, by voice ("أيوه" / "لا") or with the notification buttons.
  * Nabra **never runs inside banking, wallet or password-manager apps**.

In grammar mode, Nabra first says "قول الأمر" and then records your AI command in
free-form mode, for that one utterance only.

## Always on

* Listening starts **automatically when the app opens**, with no tap needed, and keeps running in the
  background, including after you swipe the app away from Recents.
* The accessibility service, which Android keeps alive and re-binds after reboot, starts the
  listener when it connects and acts as a **watchdog**: if the system killed the listener, it is
  brought back within about 30 s.
* *Start on boot* is on by default.
* Pressing **Stop**, in the app or in the notification, is respected until the app is opened again.
* If Android blocks a background microphone start, a notification asks you to tap it to resume.
  This notification appears at most every 30 minutes.

## Battery and reliability

* One recognizer, 16 kHz mono. Listening pauses while TTS speaks and while the screen
  is off (configurable).
* A foreground service (`microphone` type) with Pause / Stop actions, `START_STICKY`.
* **Start on boot** (optional). On Android 14+, the system may block starting the
  microphone from boot. In that case Nabra posts a notification; tap it to start.
* If the Accessibility service gets disabled, Nabra posts a notification that links
  straight to the settings page.

## Project structure

```
app/src/main/java/com/daniel/assistant/
├── ui/             Compose screens (intro, onboarding, home, commands, aliases, logs, settings, theme)
├── service/        ListeningService (FGS), AssistantBrain, Notifier, BootReceiver
├── voice/          VoiceEngine (Vosk), ModelManager, GrammarBuilder, ArabicNormalizer, FuzzyMatcher, Feedback (TTS)
├── commands/       Step model, SysAction, CommandResolver, aliases, import/export, demo seeder
├── executor/       CommandExecutor, PhotoSharer, AppLauncher
├── accessibility/  AssistantAccessibilityService, NodeActions, ScreenSerializer
├── ai/             AiAgent, providers (Anthropic SDK / OpenAI-compatible), SafetyGuard, confirmations
└── data/           Room (commands, aliases, logs), DataStore settings, Keystore crypto, AppIndex
```

Unit tests (`app/src/test`) cover normalization, fuzzy matching, resolution priority,
the grammar, step JSON round-trips, AI action parsing, and import/export.
