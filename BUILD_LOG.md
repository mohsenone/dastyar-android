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
- Nothing was sent to Telegram from this machine. The tag workflow (run 45787227870) finished successfully. Its Telegram step logged `TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is absent. Skipping Telegram upload.` CI replaced the release body with the generic workflow text; the bilingual connector notes were written back with `gh release edit`. Both APK assets stayed at 2,886,685 bytes.

## 2026-11-26 — v0.2.1

- `versionCode` 4, `versionName` 0.2.1. Chat bar opens a provider and model sheet. Free models sort first. Live fetch covers OpenAI-compatible `/models` (OpenAI, Groq, Cerebras, NVIDIA NIM, xAI, DeepSeek, Mistral, Custom), OpenRouter pricing, Gemini `models.list`, Cloudflare `ai/models/search`, and Anthropic `GET /v1/models`. Results are cached in Room `model_cache`. A failed refresh shows the cache, then a short built-in list (catalog defaults only: Gemini Flash, OpenRouter `openrouter/free`, the three documented Groq ids, the Cloudflare Llama id, `deepseek-chat`). NVIDIA and Cerebras have no catalog default id, so their offline list is the saved model id when one exists.
- Free badge rules: OpenRouter `:free`, `openrouter/free`, or both prices zero. Gemini Flash/Lite only; names containing pro, ultra, or flagship stay unpaid. Groq only the three catalog free-plan ids unless the payload is zero or `:free`. NVIDIA models from `integrate.api.nvidia.com` count as the rate-limited prototyping catalog; a custom base does not. Cloudflare `@cf/` ids count as the free Neuron allocation; other authors do not. Cerebras is trial, not free, unless the payload prices both sides at zero. OpenAI, Anthropic, xAI, DeepSeek, Mistral, and custom stay paid/unknown unless the payload is zero or the id ends with `:free`. GitHub Models is not fetched.
- Tool-calling warning only when the payload says tools are unsupported. Unknown capability stays unlabeled. This server has no emulator. Nothing was sent to Telegram from this machine.

### Publish

- Commit: `3ee31a2` — Ship Dastyar 0.2.1 with a chat provider and model picker.
- Annotated tag `v0.2.1` points at that commit. This follow-up only records the URLs. The tag was not moved.
- Release: https://github.com/mohsenone/dastyar-android/releases/tag/v0.2.1
- APK: https://github.com/mohsenone/dastyar-android/releases/download/v0.2.1/app-release.apk (2,919,453 bytes, state uploaded). A locally signed APK of the same size was attached first so the release was not empty. Workflow run [45806096496](https://github.com/mohsenone/dastyar-android/actions/runs/45806096496) then published `app-release.apk` and replaced the asset list. The two files are the same size and the same signing certificate. Their zip hashes differ because Actions rebuilt the APK.
- `aapt dump badging` on the release asset reports `versionCode` 4 and `versionName` 0.2.1, package `app.dastyar.agent`, compileSdk 35, minSdk 26, targetSdk 35. `apksigner verify` succeeded with APK Signature Scheme v2. Certificate DN is `CN=Dastyar, OU=Mobile, O=Dastyar, C=IR`. SHA-1 `f2eab974ba099eb25a0b970d156f88aebf5fd950`. SHA-256 `9796073f30fec3e58e3fb9931103815b0205caa4060a4adc53fbc2b9ab19e453`. That is the same certificate as v0.2.0.

## 2026-11-26 — v0.3.0 (local APK, no GitHub release yet)

- `versionCode` 5, `versionName` 0.3.0. Visual redesign on the teal, clay, and warm-paper palette, plus English as the default language for a new install. A stored `language = fa` stays Persian. A missing language key does not fall back to Persian.
- Per-app locales: System, English, and فارسی via `AppCompatDelegate.setApplicationLocales`, applied in `Application.onCreate` before the first activity. English uses bundled Plus Jakarta Sans. Persian uses Vazirmatn and RTL. Theme is System / Light / Dark (system by default). Colors are Brand by default, or System dynamic on Android 12+.
- Splash uses AndroidX SplashScreen with the teal brand background and the adaptive icon (foreground, background, monochrome). Chat adds bubbles, a small markdown renderer, fenced code with a language label, tool-call cards, a typing indicator, and the existing provider/model sheet restyled. Onboarding is two short steps after the language choice. Empty states cover chat, providers, connectors, routines, the action log, and memories.
- Honest limit: most older screen copy still uses the `t()` helper (Persian and English strings in code). New onboarding, language, and appearance strings are in `feature/settings` resources. Providers, connectors, permissions, and settings rows use the shared components but are not a full rewrite of every control. This server has no emulator. Nothing was sent to Telegram.
- Unit tests before the signed assemble, 55 tests, 0 failures: `AppLanguageTest` (4), `MarkdownTest` (2), `ModelCatalogTest` (15), `SseParserTest` (4), `ConfirmPolicyTest` (5), `ConnectorLogicTest` (7), `MessagingLinksTest` (5), `PermissionDiagnosticsTest` (4), `RemoteInputSelectionTest` (3), `RestrictedSettingsTest` (6).
- `:app:assembleRelease` signed with the existing keystore outside git. `apksigner verify` succeeded with APK Signature Scheme v2. Certificate DN `CN=Dastyar, OU=Mobile, O=Dastyar, C=IR`. SHA-1 `f2eab974ba099eb25a0b970d156f88aebf5fd950`. SHA-256 `9796073f30fec3e58e3fb9931103815b0205caa4060a4adc53fbc2b9ab19e453`. Same certificate as v0.2.0 and v0.2.1.
- Local APK: `/tmp/dastyar-0.3.0.apk` (3,169,019 bytes). SHA-256 `ee7f9485d45f243600e43a67f6b6606c5b763c5f094561d6b60a725e6b6372c1`. `aapt dump badging` reports package `app.dastyar.agent`, `versionCode` 5, `versionName` 0.3.0, minSdk 26, targetSdk 35, compileSdk 35.
- No tag `v0.3.0`, no `git push --tags`, and no GitHub Release in this pass. A supervisor will install this APK before any tag.
- Unit-test XML on this machine: 49 tests, 0 failures, 0 errors (ModelCatalogTest 15, SseParserTest 4, ConfirmPolicyTest 5, ConnectorLogicTest 7, MessagingLinksTest 5, PermissionDiagnosticsTest 4, RemoteInputSelectionTest 3, RestrictedSettingsTest 6). This server has no emulator. The APK was not installed on a phone.
- Nothing was sent to Telegram from this machine. The tag workflow finished successfully. Its Telegram step logged `TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is absent. Skipping Telegram upload.` Actions replaced the release body with the generic workflow text. The bilingual picker notes were written back with `gh release edit`. The APK stayed at 2,919,453 bytes.

## 2026-11-26 — v0.3.0 publish

The section above is the pre-tag local build. Its last bullet repeats the v0.2.1 Telegram outcome (2,919,453 bytes). This release’s APK is 3,169,019 bytes. The tag was not moved by this follow-up.

### Publish

- Commit tagged: `5a7542e` — Ship Dastyar 0.3.0 with a visual redesign and English as the default.
- Annotated tag `v0.3.0` points at that commit.
- Release: https://github.com/mohsenone/dastyar-android/releases/tag/v0.3.0
- APK: https://github.com/mohsenone/dastyar-android/releases/download/v0.3.0/app-release.apk (3,169,019 bytes, state uploaded). A locally signed APK of the same size was attached first so the release was not empty. Workflow run [45829720783](https://github.com/mohsenone/dastyar-android/actions/runs/45829720783) then published `app-release.apk` and replaced the asset. Both files are 3,169,019 bytes and the same signing certificate. Zip SHA-256 differs because Actions rebuilt the APK: local `ee7f9485d45f243600e43a67f6b6606c5b763c5f094561d6b60a725e6b6372c1`, release asset `505a2a139ae9f77ec3897bb29850b5f2fffe46d845dc46ee5fa742b666212491`.
- `aapt dump badging` on the release asset reports `versionCode` 5 and `versionName` 0.3.0, package `app.dastyar.agent`, compileSdk 35, minSdk 26, targetSdk 35. `apksigner verify` succeeded with APK Signature Scheme v2. Certificate DN is `CN=Dastyar, OU=Mobile, O=Dastyar, C=IR`. SHA-1 `f2eab974ba099eb25a0b970d156f88aebf5fd950`. SHA-256 `9796073f30fec3e58e3fb9931103815b0205caa4060a4adc53fbc2b9ab19e453`. Same certificate as v0.2.0 and v0.2.1.
- Nothing was sent to Telegram from this machine. The tag workflow finished successfully. Its Telegram step logged `TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is absent. Skipping Telegram upload.` Actions replaced the release body with the generic workflow text. The bilingual redesign notes were written back with `gh release edit`. The asset stayed `app-release.apk` at 3,169,019 bytes.

### Emulator QA

- A supervisor installed this APK on an emulator (API 35, headless TCG on a nested-virt host; KVM is not usable there) and walked onboarding, chat, the empty model picker, permissions, and settings, in English and Persian, light and dark. It was not tested on a physical phone.
- Most older copy still uses the in-code `t()` helper.
- The populated free-first model list was not exercised (no API key on the emulator). Only the empty picker state was.
- On the English Permissions screen, the Persian «اجازه به تنظیمات محدود» (Allow restricted settings) steps are laid out LTR, so punctuation and numbering wrap incorrectly. The English steps on that screen, and the rest of the Persian UI in the fa locale, looked correct.
- Opening the Providers tab left the framebuffer black twice while `uiautomator` still showed the provider catalog. Force-stopping the app restored drawing. No `AndroidRuntime` FATAL for `app.dastyar.agent` was seen. This may be the slow software emulator. It was not confirmed as an app crash.
- Unit tests: 55, 0 failures (as already recorded before the signed assemble).
