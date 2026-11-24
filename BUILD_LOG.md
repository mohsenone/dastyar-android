# Dastyar build log

## 2026-11-24

- Toolchain already on the machine: JDK 21 (bytecode target 17), Android SDK 35 at `/root/android-sdk`, `gh` as mohsenone.
- Multi-module Gradle project: app, core (ui, data, security, network), features (chat, agent, providers, settings, tools, voice).
- Keystore AES-GCM vault in `noBackupFilesDir`. Room for chats, audit, memories, routines, provider metadata. API keys are not columns.
- LLM clients: OpenAI-compatible (Groq, OpenRouter, NVIDIA, Cerebras, xAI, DeepSeek, Mistral, Custom), Gemini REST, Anthropic Messages, Cloudflare Workers AI.
- Agent loop caps at 12 tool steps. Risky calls wait on a confirmation sheet. Pause denies them.
- Android tools: device context, notifications, SMS, calls, contacts, calendar, alarms, files, camera, location, screen capture, accessibility, web, intents, clipboard, routines, sandbox shell.
- UI: Persian default, English switch, Vazirmatn, providers catalog, chat streaming, routines, audit, accessibility disclosure.
- Release signing is environment-driven. The keystore is outside the git worktree at `/root/android-agent-secrets/` (root-only). PKCS12 uses one password for the store and the key.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Signed release APK: `app/build/outputs/apk/release/app-release.apk` (v2 signature, certificate CN=Dastyar). `apksigner verify` succeeded. R8 needs about 1.5 GB of heap. On this 4 GB machine the release compile was split across processes because an in-process Kotlin compiler exhausted metaspace. `kotlin.compiler.execution.strategy=out-of-process` is set for that reason.
- Unit tests: `SseParserTest` (SSE frames, OpenAI tool-call assembly, Cloudflare JSON tool fallback, secret redaction) and `ConfirmPolicyTest`. Android unit tests need the real `org.json` jar; the SDK stub does not parse JSON.
- GitHub Models is a discontinued note, not a signup. Mistral is paid. Cerebras is trial-with-card. Cloudflare Neuron amounts are not invented.

### Known limits

- No on-device embedding index and no bundled GGUF / Gemini Nano. Settings says so.
- SAF writes materialize a cache copy; all-files or the app private directory are the reliable write paths.
- Speech depends on the engines installed on the phone.
- Exact alarms need the system grant on Android 12+. Otherwise the alarm is inexact, and if the OS blocks a background foreground-service start the app posts a notification to open Dastyar.
- Screenshot, accessibility tap, and SMS confirmation need a physical device. This server has no emulator image.

### Publish

- Repository: https://github.com/mohsenone/dastyar-android
- Release v0.1.0: https://github.com/mohsenone/dastyar-android/releases/tag/v0.1.0
- Signing secrets are set on the repo. Telegram secrets are not set, so that job warns and skips.

## 2026-11-24 — v0.1.1

- Root cause of the sideload reports: Android 13+ blocks Accessibility, notification listener, SMS, and call log until the user allows restricted settings. v0.1.0 never detected that and answered with “Permission denied”.
- v0.1.1 (`versionCode` 2) adds the Permissions screen (Persian and English steps), a restricted-settings dialog after a blocked SMS/call-log grant, `RECEIVE_SMS` / `RECEIVE_MMS` / `RECEIVE_WAP_PUSH`, and package queries for WhatsApp and Gmail.
- Agent tools: `permissions_diagnostic`, `send_whatsapp`, `read_whatsapp_notifications`, `compose_email`, `read_email_notifications`, `reply_notification` (RemoteInput, wearable actions included, no auto-reply), `operate_app`. `open_app` accepts a launcher label. Accessibility uses `typeAllMask`, `canTakeScreenshot`, child scroll, and long-press.
- Gmail API OAuth is not in this release. The composer is the mail app the user already has.
- No Telegram upload was run for this release. The existing CI step is unchanged.

### Publish

- Commit: `eb89bed` — Ship Dastyar 0.1.1 with restricted-settings help and app tools.
- Annotated tag `v0.1.1` points at that commit.
- Release: https://github.com/mohsenone/dastyar-android/releases/tag/v0.1.1
- APK: https://github.com/mohsenone/dastyar-android/releases/download/v0.1.1/app-release.apk (2,699,585 bytes, state uploaded). `aapt dump badging` reports `versionCode='2'` and `versionName='0.1.1'`, package `app.dastyar.agent`. `apksigner verify` succeeded with APK Signature Scheme v2 and certificate CN=Dastyar.
- Unit tests before the signed assemble: `:feature:tools:testDebugUnitTest`, `:feature:agent:testDebugUnitTest`, `:core:network:testDebugUnitTest`.
- Nothing was sent to Telegram from this machine. The tag workflow (run 45420199885) finished successfully. Its Telegram step logged `TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is absent. Skipping Telegram upload.` The release APK stayed `app-release.apk` at 2,699,585 bytes. CI replaced the release body with the generic workflow text; the bilingual restricted-settings / WhatsApp / Gmail notes were written back with `gh release edit`.
