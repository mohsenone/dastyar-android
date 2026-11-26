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

## 2026-11-26 — v0.2.0

- `versionCode` 3, `versionName` 0.2.0. New module `:feature:connectors`.
- Connectors: Google OAuth (AppAuth PKCE, redirect scheme `app.dastyar.agent`, placeholder `appAuthRedirectScheme`), GitHub PAT, Telegram Bot API (text and a file already in app storage), Notion, webhook with the web allowlist, Tavily / Brave / SearXNG, RSS, Open-Meteo or OpenWeather, remote MCP (Streamable HTTP, one-shot SSE fallback), optional Shizuku 13.1.5 via reflection on private `newProcess`. Secrets stay in the Keystore vault. A connector that is off contributes no tools. Status is connected only after a token or a successful test.
- Device control: capped accessibility tree, wait-for-element, global actions, observe loop with optional accessibility screenshot, helpers for WhatsApp, the Telegram app, Gmail, Instagram, and Chrome, media session, volume, brightness, DND, and system panels for Wi-Fi and Bluetooth. Silent radio toggles are not claimed.
- Dastyar control: tool categories and individual tool names, confirmation modes with a safety floor (send, email, call, delete, pay, and similar still ask in auto), audit time and denied/done/failed, per-chat provider and model, routines on a clock or on SMS or a notification (WorkManager keep-alive unchanged; exact alarms only for clock triggers).
- Release certificate SHA-1 (public fingerprint, for an Android OAuth client): `F2:EA:B9:74:BA:09:9E:B2:5A:0B:97:0D:15:6F:88:AE:BF:5F:D9:50`.
- Unit tests: `:feature:connectors:testDebugUnitTest`, `:feature:agent:testDebugUnitTest`, `:feature:tools:testDebugUnitTest`, `:core:network:testDebugUnitTest`. `:app:assembleRelease` signed with the existing keystore outside git. `apksigner verify` reports APK Signature Scheme v2 and CN=Dastyar. `aapt dump badging` reports package `app.dastyar.agent`, `versionCode` 3, `versionName` 0.2.0. No emulator run.
- Partial on purpose: Google verification for sensitive scopes; Drive and Contacts depend on the extra-scopes switch; Instagram is intents plus `operate_app`; the Telegram app is not MTProto; MCP SSE is per call and `tools/list` is one page; Shizuku is reflection on a private method; webhook Test connection checks that a URL is set and does not POST; this server did not upload the APK to Telegram.

### Publish

- Commit: `f599a8a` — Ship Dastyar 0.2.0 with connectors and deeper device control.
- Annotated tag `v0.2.0` points at that commit.
- Release: https://github.com/mohsenone/dastyar-android/releases/tag/v0.2.0
- APK: https://github.com/mohsenone/dastyar-android/releases/download/v0.2.0/app-release.apk (2,886,685 bytes, state uploaded). The same bytes are also at https://github.com/mohsenone/dastyar-android/releases/download/v0.2.0/dastyar-0.2.0.apk
- `aapt dump badging` reports `versionCode` 3 and `versionName` 0.2.0, package `app.dastyar.agent`. `apksigner verify` succeeded with APK Signature Scheme v2 and certificate CN=Dastyar.
- Nothing was sent to Telegram from this machine. The tag workflow (run 45787227870) was started by the tag push and was still running when this note was written. Its Telegram step is the existing workflow step; it was not invoked by hand. If that job replaces the release body, the bilingual notes in `/tmp` are applied again with `gh release edit`.
