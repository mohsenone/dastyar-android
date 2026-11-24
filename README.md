# Dastyar (دستیار)

On-device AI agent for Android. The app does not ship a central model. You paste your own API key. Keys stay in the Android Keystore and an encrypted file that is excluded from backup. Dastyar does not bill usage.

Persian guide: [README.fa.md](README.fa.md).

Package: `app.dastyar.agent`. Minimum Android 8.0 (API 26). Target API 35.

## Install

Download the signed APK from [GitHub Releases](https://github.com/mohsenone/dastyar-android/releases). A Telegram post is optional: the release workflow uploads the same APK when `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` are set, and prints a warning and continues when they are not. There is no Google Play or Cafe Bazaar listing.

Android will ask you to allow installs from the browser or file manager. After install, open Dastyar. The first screen is Persian. Switch to English there, or later in Settings. Layout direction follows the language.

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

Permissions are requested when a tool needs them, with a short explanation. Deny means the tool returns an error. It does not crash.

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

Speech recognition and text-to-speech use the engines installed on the phone, for Persian and English when those voices exist. Stop speech from the chat bar. If Persian recognition is missing, the error says so and English is the fallback for speech output.

Conversations can be pinned, renamed, and searched. Saved facts are a list the model can read and write. This version does not bundle an embedding model or Gemini Nano / a GGUF file. Settings explains that, and you can still save facts by hand.

## Build

Install JDK 17, Android SDK 35, and build-tools 35.0.1. `local.properties` is gitignored:

```
sdk.dir=/path/to/android-sdk
```

```
./gradlew :app:assembleDebug
./gradlew :core:network:testDebugUnitTest :feature:agent:testDebugUnitTest
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

1. Fresh install, Persian UI. Switch to English and back. Text direction should follow.
2. Add a Groq, OpenRouter, Gemini, or custom base URL, test the connection, and send a chat. Tokens should stream.
3. Ask for a calendar event and confirm the sheet before it is written.
4. Ask to send an SMS and confirm the number and the full body.
5. Grant notification access and ask what notifications are showing.
6. Ask for a screenshot. Android shows the MediaProjection consent. Confirm again before the image is sent.
7. Ask to open a URL. The browser opens and the address is visible.
8. Accessibility: after the disclosure, enable the service. Open a notes app, ask Dastyar to read the screen, then ask for one tap on a button you name. Confirm the sheet. The audit log should show `read_screen` and `accessibility_action`.
9. Kill the app and reopen. The saved key still tests. Delete all keys in Settings and the test then fails with a missing key.
10. Schedule a routine a minute ahead. Leave the app. A notification should say a routine is running.

The sandbox shell runs only as the app user, with the working directory set to the app’s private files. `su` and `sudo` are rejected.

## Privacy

There is no Dastyar account and no Dastyar server. Traffic goes to the provider or proxy whose URL you saved. Do not point Custom / Proxy at a host you do not trust. Screenshots of the key screen are blocked. Keys are not in backups.

## License of the font

Vazirmatn is under the SIL Open Font License. See `third_party/fonts/OFL-vazirmatn.txt`.
