# Dastyar (دستیار)

On-device AI agent for Android. The app does not ship a central model. You paste your own API key. Keys stay in the Android Keystore and an encrypted file that is excluded from backup. Dastyar does not bill usage.

Persian guide: [README.fa.md](README.fa.md).

Package: `app.dastyar.agent`. Minimum Android 8.0 (API 26). Target API 35.

## Install

Download the signed APK from [GitHub Releases](https://github.com/mohsenone/dastyar-android/releases). A Telegram post is optional: the release workflow uploads the same APK when `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` are set, and prints a warning and continues when they are not. There is no Google Play or Cafe Bazaar listing.

Android will ask you to allow installs from the browser or file manager. After install, open Dastyar. A new install is English and left to right. Onboarding asks for **System**, **English**, or **فارسی** before the rest of the intro. The same switch is in Settings and applies immediately through Android’s per-app language. **System** follows the device locale. **English** forces English. **فارسی** forces Persian, right to left, with Vazirmatn. English uses Plus Jakarta Sans. A phone that already stored `fa` stays Persian; a missing language key does not. Settings also has theme (System / Light / Dark, system by default) and colors (Brand by default, or System dynamic on Android 12+). The splash screen uses the teal brand background.

On Android 13 and later, a sideloaded APK cannot turn on Accessibility, notification access, SMS, or call log until you allow restricted settings. There is no button the app can press for you. Dastyar’s Permissions screen (end of the intro, and again in Settings) shows the live state of each grant and this path:

1. Try once to grant SMS or turn Accessibility on. The system may refuse or grey out the switch. That is expected.
2. Tap **Open app info** (or Settings → Apps → Dastyar).
3. Open the **⋮** menu in the top corner.
4. Choose **Allow restricted settings**. The label depends on the phone language.
5. Return to Permissions and turn on SMS, Accessibility (after the disclosure), and notification access.
6. On Android 15 the ⋮ item can stay hidden until you try the blocked switch once more.

Mail can still open in the Gmail app or another email app. WhatsApp opens through `wa.me`. Sending still asks you first. Version 0.2.0 also has an optional Google sign-in under Connectors; see below. It is not a fake login, and some scopes need Google’s own verification.

## 0.6.0

`versionCode` 8. The look is the same teal, clay, and paper, drawn tighter: a 48dp top bar (on Chat, no large wordmark), a quiet background wash, distinct chat bubbles, a welcome state, and a drawer with the same five destinations. Light and dark both use the brand palette. Persian stays on Vazirmatn and right to left.

You can talk to Dastyar. The mic in the composer writes one phrase into the field so you can review it, then send. Hands-free listens, sends that turn, speaks the reply you can already see, and listens again until you stop. Language in Settings is Auto, Persian (`fa-IR`), or English (`en-US`). Auto follows the app language. If a Persian voice is not installed, the reply stays on screen and the app points at system text-to-speech data. It does not silently switch that reply to English. When the phone has no recognizer, and the saved key is official OpenAI or another OpenAI-compatible base that actually answers, Dastyar may use that provider’s audio routes and says so. Groq, OpenRouter, Gemini, and the other built-in hosts are not treated as voice engines. The microphone is requested only when you start voice.

Settings can set Dastyar as the default digital assistant (`ROLE_ASSISTANT` on Android 10+, otherwise the system voice-input screen). A long-press of Home is meant to open Dastyar’s voice overlay. The in-app recognizer service is only so the system can list the app. Dictation uses the phone’s speech recognizer. This build was not installed on a phone or an emulator, and the assist gesture was not tried here. There is no `v0.6.0` tag yet.

## 0.5.0

`versionCode` 7. Chat is the home screen and fills the screen. The old bottom bar (Chat, Providers, Connectors, Routines, Settings) is a menu drawer opened from the menu icon. In Persian the drawer is right to left. Connectors keeps that name (اتصال‌ها). Opening Connectors no longer builds the Google sign-in service up front: AppAuth 0.11.1 can throw when a real phone’s browser has a null `PackageInfo.signatures` array, which a fresh emulator often does not. A broken connector card and a corrupt connector file stay on screen with a retry, and they do not wipe the vault or chats. Room is still version 3. There is no `v0.5.0` tag yet. This build was not run on a phone or an emulator.

## 0.4.0

`versionCode` 6. Permissions are the focus. The Permissions screen is a live status list, a setup wizard (Messages, Calls, Contacts, Apps control, Notifications, Files, Location, Camera and microphone, System), and **Fix everything**, which walks only what is still missing. All-files access and Shizuku are optional and are not in that walk. A grant Android will not ask for again opens app settings. Background location is a second step. Persian restricted-settings steps stay right-to-left even when the app is in English. Settings has a Privacy screen. The bottom bar says **Connectors**. A home-screen widget and a quick-settings tile open the current chat. They do not start a new one. Share-in of text and images was already supported. The full permission table is in [CHANGELOG.md](CHANGELOG.md). This build was not run on a phone. It was later tagged `v0.4.0`.

## Add a key

Free or trial options are on the first Providers tab. Paid keys are on the second. Every limit line is dated **24 Nov 2026 (3 Azar 1405)**. Check the official page again before you rely on a number.

| Source | What it is | Start here |
|---|---|---|
| Google Gemini | AI Studio free tier. Free-tier content may be used to improve Google products. | https://aistudio.google.com/ |
| OpenRouter | `:free` models, or `openrouter/free`. 20 RPM; 50 RPD under 10 credits, 1,000 RPD after about $10. | https://openrouter.ai/settings/keys |
| Groq | Free plan at $0. Example published caps are about 30 RPM and 1,000 RPD for some models. Your org table wins. | https://console.groq.com/docs/quickstart |
| Cloudflare Workers AI | Token plus Account ID. Free Neuron quota exists; this app does not invent the number. | https://developers.cloudflare.com/workers-ai/get-started/rest-api/ |
| NVIDIA NIM | Prototyping key. Not a production quota. | https://build.nvidia.com/ |
| Cerebras | **Trial (card required).** $5 after a verified card, expires in 30 days. Not unlimited free. | https://cloud.cerebras.ai/ |
| Custom / Proxy | Any OpenAI-compatible base URL. This is the path when an official host is blocked. | — |

GitHub Models was retired on 30 July 2026. The app links to https://docs.github.com/en/github-models and does not offer a key form. Mistral is listed under Paid: Studio “free mode” is not API access.

9Router is a router you run yourself (https://www.9router.com/ , https://github.com/decolua/9router/). Paste that base URL into Custom / Proxy. Do not scrape cookies or borrow another person’s subscription.

Tap **Test connection**. The result shows success, latency, or a localized error (401, 403, 429, or a blocked network). The key is not written to logcat.

## Iran and other blocked networks

Official hosts are often unreachable. Add **Custom / Proxy**, set the base URL of a relay you control (for example a VPS), the key that relay expects, and the model id. Dastyar does not include a VPN client.

## Permissions

Permissions are requested when a tool needs them, or from the Permissions screen, with a short explanation. The first launch does not ask for the whole list. Deny means the tool names the missing access in English and Persian and points at Fix. It does not crash and it does not pretend the call worked. On Android 13+, SMS, call log, accessibility, and notification access can also need Allow restricted settings. If Android will not show the dialog again, the app opens the right settings page instead of launching a dead dialog. Status is read again when you come back.

Vendor skins differ. Samsung can hide the ⋮ item until you try the blocked switch once. Xiaomi / MIUI Autostart and battery saver are separate from Android permissions. Huawei app launch and battery can be locked on their own. That is not a promise that every skin shows the same menu.

The audit table (permission, why, request, deep link, gated tools) is in [CHANGELOG.md](CHANGELOG.md). `READ_PHONE_STATE`, `BLUETOOTH_CONNECT`, and `MODIFY_AUDIO_SETTINGS` were removed. There is no default SMS role and no `USE_EXACT_ALARM`.

- Notifications: a listener can read what is already on the shade. One-time codes and banking-looking texts are redacted before they reach the model. Dismissing a notification asks first.
- SMS send and phone calls show the recipient and the body, and wait for **Do it**.
- Files: pick a folder with the system picker. All-files access is a separate system screen.
- Camera and screen capture show a preview and ask again before the image is sent to the provider you chose.
- Accessibility can read on-screen text and tap other apps. The disclosure screen explains this before the system settings page opens. Gestures ask for confirmation. Turn it off under system Settings → Accessibility → Dastyar.
- The bubble that says Dastyar is only a status chip. Touches outside it go to the app underneath.
- Routines run from an alarm under a foreground notification (“Dastyar is running a routine”). Unattended mode is off until you turn it on for that routine.

**Pause tools** is on the notification and in Settings. It stops the current run and denies anything waiting for confirmation.

The audit log in Settings can be filtered, exported, and deleted. It stores a short summary, not API keys.

## Voice, memory, offline models

Speech recognition and text-to-speech use the engines installed on the phone. Settings chooses Auto, Persian, or English. One tap of the mic fills the composer. Hands-free speaks the visible reply and keeps listening until you stop. If Persian speech data is missing, the reply stays as text and a button opens the system install-speech screen. English is not used as a silent stand-in for a missing Persian voice. A provider audio fallback, when it runs, uses the key you already saved and is labeled in the voice stage.

Conversations can be pinned, renamed, and searched. The chat bar opens the saved providers and that provider’s model list. Free models are first. Refresh asks the provider’s live catalog and keeps the last list if the network fails. Anthropic uses its models endpoint when the key can call it, and an offline list when it cannot. GitHub Models stays discontinued and is not fetched. Choosing a model applies to this chat. Save as app default does not rewrite chats that already stored their own model. Replies render headings, lists, links, inline code, and fenced code blocks with a language label. Tool calls sit in a card. A typing indicator shows while a reply is still empty. Saved facts are a list the model can read and write. This version does not bundle an embedding model or Gemini Nano / a GGUF file. Settings explains that, and you can still save facts by hand.

## Connectors (0.2.0)

Open **Connectors** from the menu, or **Connectors** in Settings. A connector that is off adds no tools. **Connected** means a real sign-in or a successful test, not a placeholder. Secrets stay in the same Keystore vault as LLM keys. They are not Room columns and they are not written to the audit log. The Connectors screen blocks screenshots.

| Connector | What you paste | Official page |
|---|---|---|
| Google | OAuth client id. A client secret is optional and only for a confidential/dev client. Prefer an Android or installed-app client with PKCE. | https://console.cloud.google.com/apis/credentials |
| GitHub | Personal access token (classic or fine-grained) | https://github.com/settings/tokens |
| Telegram bot | Bot token from BotFather. This is the **bot** API, not your personal Telegram account. | https://t.me/BotFather |
| Notion | Internal integration token | https://www.notion.so/my-integrations |
| Web search | Tavily key, Brave Search key, or a SearXNG base URL | https://tavily.com/ · https://brave.com/search/api/ · https://docs.searxng.org/ |
| Weather | Nothing for Open-Meteo. OpenWeather only if you choose that provider and paste a key. Prices are on their site; this app does not state them. | https://open-meteo.com/ · https://openweathermap.org/api |
| RSS | Feed URLs. No secret. | — |
| Webhook | A URL, plus an optional header secret. HTTP is allowed only when that host is on the web allowlist. | — |
| MCP | One or more remote server URLs (Streamable HTTP, with SSE as a fallback) and an optional bearer token. | https://modelcontextprotocol.io/specification/2025-03-26 |
| Shizuku | Optional. Install and authorize it yourself. Dastyar does not require it and does not root the phone. | https://shizuku.rikka.app/introduction/ |

### Google Cloud Console

1. Create a project and enable the Gmail API and the Google Calendar API. Enable Drive and People only if you turn on the extra-scopes switch.
2. Create an OAuth client. For an Android client, the package name is `app.dastyar.agent` and the SHA-1 is the certificate of the APK you installed. The v0.2.0 release certificate SHA-1 is `F2:EA:B9:74:BA:09:9E:B2:5A:0B:97:0D:15:6F:88:AE:BF:5F:D9:50`. The Connectors screen also shows the SHA-1 of whatever build is installed. The redirect URI is `app.dastyar.agent://oauth2redirect`.
3. Google’s **Web** client type often rejects a custom-scheme redirect. If the token endpoint returns an error, the connector stays in an error state. It does not pretend to be signed in.
4. `gmail.send`, Drive, and Contacts can require Google’s app verification before they work for accounts other than the test users you added. The app says so in the guide and returns the API error instead of hiding it.
5. Requested by default: `openid`, `email`, `profile`, `gmail.readonly`, `gmail.send`, `calendar`. Drive and Contacts are off until you opt in.

Gmail read/search and confirmed send, and Calendar list/create/update/delete, call the real APIs after a successful token exchange. Drive list/download/upload and Contacts read/write are implemented and depend on the granted scopes. Upload, send, update, and delete still ask you first.

### MCP, search, and device control

On enable, Dastyar initializes the MCP server, lists tools, and registers them as `mcp_<server>_<tool>`. A call goes over that session and the text comes back to the agent. Streamable HTTP is tried first. If the server answers 404, 405, or 400, one SSE request is used for that call. There is no permanent SSE socket, and `tools/list` pagination is a single page.

Settings has three confirmation modes: always, sensitive (the default), and auto. Auto still asks before a message, email, call, delete, payment, or anything else that leaves the phone or destroys data. The only opt-out is the per-routine **Act without asking** switch, which is off by default.

Tool categories (and individual tool names) can be turned off. Disabled tools are left out of the model’s list.

Routines can run at a clock time, on an incoming SMS, or when a notification arrives. A filter is an optional substring. Triggers still obey the confirmation policy. The notification listener and SMS permission are the same ones already on the Permissions screen.

Accessibility can dump a capped screen tree, wait for an element, scroll, swipe, and use global actions (back, home, recents, notifications, quick settings, screenshot on newer Android, lock screen when the platform allows it). After each step of a multi-step plan the tree is read again. A screenshot from the accessibility service can be attached when the model accepts images. Pause and the single confirmation sheet are unchanged.

Helpers for WhatsApp, the Telegram **app** (`org.telegram.messenger` and the official variants), Gmail, Instagram, and Chrome open the right screen. Vendor UIs change; the tool text says when a send is only best-effort and when you should use `operate_app`. Media, volume, brightness, and Do Not Disturb change when Android still allows it. Wi-Fi and Bluetooth on Android 10+ open the system panel and do not claim the radio toggled. Shizuku tools (`settings` get/put, `pm` grant/revoke, start or force-stop a named component) appear only when that connector is on, and each one is confirmed.

## Build

Install JDK 17, Android SDK 35, and build-tools 35.0.1. `local.properties` is gitignored:

```
sdk.dir=/path/to/android-sdk
```

```
./gradlew :app:assembleDebug
./gradlew :core:network:testDebugUnitTest :feature:agent:testDebugUnitTest :feature:tools:testDebugUnitTest
```

A signed release needs these environment variables and a keystore that is **not** in git:

```
SIGNING_STORE_FILE
SIGNING_STORE_PASSWORD
SIGNING_KEY_ALIAS
SIGNING_KEY_PASSWORD
./gradlew :app:assembleRelease
```

Without them the release build is unsigned and CI will not attach it to a Release.

### GitHub Actions secrets

| Secret | Purpose |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Base64 of the `.jks` file |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |
| `TELEGRAM_BOT_TOKEN` | Optional |
| `TELEGRAM_CHAT_ID` | Optional |

Push a tag `v*` (or run the workflow by hand) on `main`. Missing signing secrets fail the job. Missing Telegram secrets skip that step with a warning.

## Manual checks

These need a phone. An emulator is not required to compile.

1. Fresh install, English UI, left to right. Switch to فارسی and back, and try System. Direction and font should follow. A phone that already saved Persian should stay Persian after updating.
2. Add a Groq, OpenRouter, Gemini, or custom base URL, test the connection, and send a chat. Tokens should stream.
3. Ask for a calendar event and confirm the sheet before it is written.
4. Ask to send an SMS and confirm the number and the full body.
5. Grant notification access and ask what notifications are showing. On Android 13+, if the toggle is grey, follow Allow restricted settings, then retry. Ask the assistant to run `permissions_diagnostic`.
6. Ask for a screenshot. Android shows the MediaProjection consent. Confirm again before the image is sent.
7. Ask to open a URL. The browser opens and the address is visible.
8. Accessibility: after the disclosure, enable the service. Open a notes app, ask Dastyar to read the screen, then ask for one tap on a button you name. Confirm the sheet. The audit log should show `read_screen` and `accessibility_action`.
9. Kill the app and reopen. The saved key still tests. Delete all keys in Settings and the test then fails with a missing key.
10. Schedule a routine a minute ahead. Leave the app. A notification should say a routine is running.

The sandbox shell runs only as the app user, with the working directory set to the app’s private files. `su` and `sudo` are rejected.

## Privacy

There is no Dastyar account and no Dastyar server. Traffic goes to the provider or proxy whose URL you saved. Do not point Custom / Proxy at a host you do not trust. Screenshots of the key screen are blocked. Keys are not in backups.

## License of the font

Plus Jakarta Sans (English) and Vazirmatn (Persian) are under the SIL Open Font License. See `third_party/fonts/OFL-plus-jakarta-sans.txt` and `third_party/fonts/OFL-vazirmatn.txt`.
