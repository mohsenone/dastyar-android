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

## 2026-11-26 — v0.4.0 (local APK, no GitHub release yet)

- `versionCode` 6, `versionName` 0.4.0. Permission status, a capability wizard, and Fix everything. Fix everything walks only missing rows and skips all-files access and Shizuku. Status refreshes on resume. A denial Android will not ask again opens app info (or the restricted-settings guide) and does not launch a dead dialog. Background location is a second step after fine or coarse. Persian restricted-settings text is RTL even when the app language is English. Privacy screen explains use, on-device storage, the user’s chosen provider or connector, revoke, and the confirmation floor.
- Least privilege: removed `READ_PHONE_STATE`, `BLUETOOTH_CONNECT`, and `MODIFY_AUDIO_SETTINGS`. Did not add a default SMS role, `USE_EXACT_ALARM`, `BLUETOOTH_SCAN`, `NEARBY_WIFI_DEVICES`, or `WRITE_CALL_LOG`. Added `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`, and `READ_MEDIA_VISUAL_USER_SELECTED` for `list_recent_media`. `READ_EXTERNAL_STORAGE` stays `maxSdkVersion` 32. The chat photo uses the system photo picker and does not need those grants. Shizuku is detected only. It is not in Fix everything and it is not a substitute for the grants above.
- Widget and quick-settings tile open the current chat (`ACTION_OPEN_CHAT`). They do not start a new chat. Share-in for text and images was already present. The agent retries a transient network, rate-limit, or HTTP 5xx error up to three times when nothing has streamed. Cancel clears the typing indicator.
- Providers catalog is no longer `FLAG_SECURE`. Only the key sheet is, and not when `SecureSurfaces.blocksDrawing()` sees an emulator or software-renderer fingerprint. The black framebuffer was not reproduced on this server. There is no emulator here.
- Honest partials: diagnostic titles stay in Kotlin so unit tests keep their text. Older screens still use `t()`. Providers, connectors, routines, the action log, and memories already used the 0.3.0 design system; spacing was not fully swept onto `Space` tokens. SMS and call-log rows stay `MAYBE_RESTRICTED` on API 33+ and `MISSING` below that until granted, because those two group rows do not feed the permission list into the don’t-ask-again check. The request path still refuses the dead dialog. No `androidTest`. Vendor notes name Samsung, Xiaomi/MIUI, and Huawei and say they are not a promise. All-files access stays optional and is not nagged.
- Unit tests before the signed assemble: 64 tests, 0 failures, 0 errors, 0 skipped. Suites: `AppLanguageTest` 4, `ModelCatalogTest` 15, `SseParserTest` 4, `ConfirmPolicyTest` 5, `TransientRetryTest` 2, `MarkdownTest` 2, `ConnectorLogicTest` 7, `MessagingLinksTest` 5, `PermissionDiagnosticsTest` 10, `RemoteInputSelectionTest` 3, `RestrictedSettingsTest` 7. The only edit after that run was two R8 keep rules for `DastyarWidget` and `DastyarTileService`.
- The first `:app:assembleRelease` died when the Gradle daemon (pid 930907, 7th build, up for about 1 hour 55 minutes, `-Xmx1536m`) disappeared during release KSP. The last `dmesg` window showed UFW blocks, not an OOM line. Swap was already about 3.1 GiB of 4.0 GiB. The retry compiled `:app:compileReleaseKotlin` with a temporary Gradle heap of 896m and a Kotlin daemon heap of 640m (2m 36s), stopped both daemons, then ran `:app:assembleRelease` with the configured 1536m heap (6m 4s, R8). `gradle.properties` was restored. The lower heap was not committed.
- Signed APK: `/tmp/dastyar-0.4.0.apk` (3,238,439 bytes). SHA-256 `787cd3d2411f6b77711fc11c064bb143594afaefcaee5f248997f6ada33102c6`. `apksigner verify` succeeded with APK Signature Scheme v2. Certificate DN `CN=Dastyar, OU=Mobile, O=Dastyar, C=IR`. SHA-1 `f2eab974ba099eb25a0b970d156f88aebf5fd950`. SHA-256 `9796073f30fec3e58e3fb9931103815b0205caa4060a4adc53fbc2b9ab19e453`. Same certificate as v0.3.0.
- `aapt dump badging` reports package `app.dastyar.agent`, `versionCode` 6, `versionName` 0.4.0, minSdk 26, targetSdk 35, compileSdk 35. The badging list includes the media reads and `READ_EXTERNAL_STORAGE` with `maxSdkVersion` 32. It does not include `READ_PHONE_STATE`, `BLUETOOTH_CONNECT`, `MODIFY_AUDIO_SETTINGS`, `USE_EXACT_ALARM`, `BLUETOOTH_SCAN`, or `NEARBY_WIFI_DEVICES`.
- No tag `v0.4.0`, no `git push --tags`, and no GitHub Release in this pass. A supervisor will install this APK before any tag. This server has no emulator. Nothing was sent to Telegram.

## 2026-11-26 — v0.4.0 follow-up (local APK, still no GitHub release)

Still `versionCode` 6 and `versionName` 0.4.0. No tag and no GitHub Release. A supervisor will re-check Connectors on the emulator before any release prompt.

- Connectors stays open when `state.rows` is empty or is missing a catalog id. Row lookup uses `firstOrNull` (`connectorRow`). The screen shows a loading line until the snapshot arrives, and a missing card returns before it composes. The previous build threw `NoSuchElementException` from `rows.first { it.id == ConnectorIds.… }` on first composition.
- Restricted-settings copy (English guide, Persian guide, the other-language block, and the Samsung note) says "three-dot menu" or "منوی سه نقطه". Plus Jakarta Sans and Vazirmatn were drawing U+22EE as a colon. Xiaomi and Huawei notes never used that character.
- Notification access that is listed but not connected is no longer a generic "Needs settings" on Android 13+. On API 33+ that state is `MAYBE_RESTRICTED` and names Allow restricted settings, then a toggle off and on. On API 26–32 it stays "Needs settings" and says to toggle it off and on. Granted still requires the secure setting and a live `DastyarNotificationListener` connection. `ComponentLists.listed` accepts the flattened `package/.tools.Class` form `ComponentName.flattenToString()` stores on API 26–35, plus the fully qualified form.
- `./gradlew test --offline` succeeded in 11m 39s: 66 tests, 0 failures, 0 errors, 0 skipped, run on both debug and release unit-test tasks (132 executions). Suites: `AppLanguageTest` 4, `ModelCatalogTest` 15, `SseParserTest` 4, `ConfirmPolicyTest` 5, `TransientRetryTest` 2, `MarkdownTest` 2, `ConnectorLogicTest` 8, `MessagingLinksTest` 5, `PermissionDiagnosticsTest` 10, `RemoteInputSelectionTest` 3, `RestrictedSettingsTest` 8.
- `:app:assembleRelease --offline` succeeded in 5m 50s on the configured 1536m heap after the previous daemon was stopped. The heap in `gradle.properties` was not lowered. R8 ran inside that assemble. Known warnings: SDK XML version 4, and strip skipped for `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so`.
- Signed APK: `/tmp/dastyar-0.4.0.apk` (3,238,439 bytes). SHA-256 `f430a4976d0d567a4885e845093d27879bb0f04b2ea30d7fda4bb26e3c08196a`. This replaces the earlier 0.4.0 file (`787cd3d2411f6b77711fc11c064bb143594afaefcaee5f248997f6ada33102c6`). `apksigner verify` succeeded with APK Signature Scheme v2. Certificate DN `CN=Dastyar, OU=Mobile, O=Dastyar, C=IR`. SHA-1 `f2eab974ba099eb25a0b970d156f88aebf5fd950`. SHA-256 `9796073f30fec3e58e3fb9931103815b0205caa4060a4adc53fbc2b9ab19e453`. Same certificate as the first 0.4.0 assemble.
- `aapt dump badging` reports package `app.dastyar.agent`, `versionCode` 6, `versionName` 0.4.0, minSdk 26, targetSdk 35, compileSdk 35.
- No tag `v0.4.0` and no GitHub Release. This server has no emulator. Nothing was sent to Telegram. The Connectors crash was not re-run on a device here.

## 2026-11-26 — v0.4.0 publish

The sections above are the pre-tag local builds. The first 0.4.0 APK (`787cd3d2…`) was replaced on this machine by the Connectors fix (`f430a497…`) before the tag. This follow-up does not move `v0.4.0`.

### Publish

- Commit tagged: `0e6a8f7` — Fix the Connectors tab crash on an empty connector snapshot. That commit is on top of `b229320`. Both are `versionCode` 6 and `versionName` 0.4.0.
- Annotated tag `v0.4.0` points at `0e6a8f75126c14d18eb1431933aeb72cc3f0628b`. It was not force-pushed.
- Release: https://github.com/mohsenone/dastyar-android/releases/tag/v0.4.0
- APK: https://github.com/mohsenone/dastyar-android/releases/download/v0.4.0/app-release.apk (3,238,439 bytes, state uploaded). A locally signed APK of the same size was attached first so the release was not empty. Workflow run [45857297862](https://github.com/mohsenone/dastyar-android/actions/runs/45857297862) then deleted that asset and uploaded its own `app-release.apk`. Both files are 3,238,439 bytes and the same signing certificate. Zip SHA-256 differs because Actions rebuilt the APK from the tag: local (the file the supervisor installed) `f430a4976d0d567a4885e845093d27879bb0f04b2ea30d7fda4bb26e3c08196a`, release asset `89bedabe40e5299080b827217e2f4e614d9016b7a75f775cbad171fd87ce8117`.
- `aapt dump badging` on the release asset reports `versionCode` 6 and `versionName` 0.4.0, package `app.dastyar.agent`, compileSdk 35, minSdk 26, targetSdk 35. `apksigner verify` succeeded with APK Signature Scheme v2. Certificate DN is `CN=Dastyar, OU=Mobile, O=Dastyar, C=IR`. SHA-1 `f2eab974ba099eb25a0b970d156f88aebf5fd950`. SHA-256 `9796073f30fec3e58e3fb9931103815b0205caa4060a4adc53fbc2b9ab19e453`. Same certificate as v0.3.0 and the local 0.4.0 APK.
- Nothing was sent to Telegram from this machine. The tag workflow’s existing Telegram step ran and logged `TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is absent. Skipping Telegram upload.` This pass did not call the Telegram API itself.
- Actions replaced the release body with the generic workflow text (`Dastyar (دستیار) — on-device AI agent`). The bilingual notes were written back with `gh release edit`. The asset stayed `app-release.apk` at 3,238,439 bytes.

### Emulator QA

- A supervisor installed the local APK (`f430a497…`, built from `0e6a8f7`) on an official Android SDK emulator: API 35 `google_apis` x86_64, Pixel 6 profile, headless TCG with SwiftShader. KVM is not usable on that host. It was not tested on a physical phone.
- They walked onboarding, chat, Providers (opened twice), Connectors (after the fix, opened twice), Routines, Settings, Privacy, Saved facts, Tool audit, and the Permissions wizard, Status, Fix everything, and Privacy screens, in English and in Persian (right to left).
- Runtime and special access were granted and revoked with `adb pm grant`, `pm revoke`, `appops`, and `settings put secure` for `enabled_accessibility_services` and `enabled_notification_listeners`. The Status screen was checked again on resume. SMS, contacts, phone, call log, calendar, camera, microphone, location, background location, notifications, overlay, photos and media, all-files, and Accessibility updated as expected. Notification access stayed Needs settings after `enabled_notification_listeners` was written. That row now explains Android 13+ restricted settings. `ACCESS_NOTIFICATION_POLICY` is not a valid `appops` name on that system image.
- A live tool call was not driven with an API key. The per-tool “missing permission and one-tap Fix” path was checked as Status-row copy and buttons, not through the agent loop.
- After `0e6a8f7`, opening Connectors no longer crashed. `NoSuchElementException` is gone. The process stayed alive. No `AndroidRuntime` FATAL for `app.dastyar.agent` was seen on the second install. Providers did not go black on a second open. That is the `FLAG_SECURE` change from `b229320`.
- The home-screen widget and the quick-settings tile were not pinned or tapped on the emulator. Share-in was already in the tree and was not re-tested as its own pass. Diagnostic titles still live in Kotlin. Some older screens still use `t()`.
- Unit tests: 66, 0 failures (debug and release unit-test tasks, 132 executions) after the fix. No instrumented tests. The build server has no emulator.

## 2026-11-26 — v0.5.0 (local APK, no GitHub release)

`versionCode` 7, `versionName` 0.5.0. No tag `v0.5.0`, no `git push --tags`, and no GitHub Release. A supervisor will install this APK, including an upgrade from 0.3.0 to 0.4.0 to 0.5.0, before any tag. This server has no emulator. Nothing was sent to Telegram.

### Connections crash

- The empty-list `NoSuchElementException` from `rows.first { }` was real on the first 0.4.0 tree (`b229320`). Shipped `0e6a8f7` already uses `connectorRow` / `firstOrNull`, a loading line, and skips a missing card. A fresh emulator install of that fix did not crash. That fix is kept. Connectors is still a destination.
- A separate hazard matches “fresh emulator ok, real phone dies when Connectors opens.” Opening the screen constructs `GoogleAuth`. v0.4.0 created `AuthorizationService` in the field initializer. AppAuth 0.11.1 `BrowserSelector.getAllBrowsers` calls `PackageManager.getPackageInfo` with the deprecated `GET_SIGNATURES` flag and then reads `PackageInfo.signatures.length`. On API 28+ that array is often null (API 34/35 phones, v3-signed browsers). The library only catches `NameNotFoundException`, so the constructor throws and the process dies. Emulator images often still fill the deprecated field. The service is now created lazily inside `runCatching`. Sign-in returns a message if it cannot start. `vault.put` of the Google token is also inside `runCatching`. The manifest queries `android.support.customtabs.action.CustomTabsService`. That helps package visibility. It does not itself fix the null-signatures NPE.
- Other hazards closed in the same screen, not device-tested: corrupt or non-object `dastyar_connectors` JSON (including a JSON array) does not throw and is not overwritten (`ConnectorDocs.read`, `recognized=false`); a Keystore read or write that throws marks rows or the screen instead of killing the coroutine; Shizuku status and the signing SHA-1 are not called raw during composition (`GET_SIGNING_CERTIFICATES` on API 28+, inside `runCatching`). A row that fails to build sets `ConnectorRow.broken` (“Couldn’t load this connector”). A failed snapshot shows a retry card. Compose still cannot catch a throw from inside a composable call. A native crash inside Shizuku class init cannot be caught either.
- Room stays version 3. There is no 0.5.0 schema change. `MIGRATION_1_2` and `MIGRATION_2_3` stay, driven by `DastyarMigrations`. `fallbackToDestructiveMigration()` is replaced by `fallbackToDestructiveMigrationOnDowngrade()` only. `exportSchema = true`. Schema file: `core/data/schemas/app.dastyar.agent.data.DastyarDatabase/3.json`. `upgradeSql` returns SQL for 1→2, 2→3, and 1→3, an empty list when versions match, and null for an unknown pair (no silent wipe). Migration tests use sqlite-jdbc on the JVM. They are not a Room open on Android.

### Navigation and visual

- The five bottom tabs moved into one modal drawer opened from the menu icon. Chat is home and fills the screen. Persian uses the existing `LocalLayoutDirection`, so the menu icon sits on the start side. Menu navigation does not restore the old bottom-bar saved state. The chat conversation drawer does not take the edge swipe. Widget, tile, share-in, and deep link still open the current chat unless the user asked for a new chat. Settings shortcuts still use `navigate` so back returns to Settings.
- Visual pass stays on the teal, clay, and paper tokens: outlined rounded cards, chat bubbles with a short tail, a bordered composer that still has the model chip, photo, and voice, and bordered markdown code and tool-call cards. Screen changes use a short fade. Titles touched in this pass use `values` / `values-fa`. Many body strings still use `t()`. Stored `language=fa` is unchanged. The confirmation floor is unchanged.

### Verify

- `./gradlew testDebugUnitTest --offline` succeeded: 76 tests, 0 failures, 0 errors, 0 skipped, 14 suites. Suites: `AppLanguageTest` 4, `AppRoutesTest` 2, `ConfirmPolicyTest` 5, `ConnectorDocsTest` 4, `ConnectorLogicTest` 9, `DastyarMigrationsTest` 3, `MarkdownTest` 2, `MessagingLinksTest` 5, `ModelCatalogTest` 15, `PermissionDiagnosticsTest` 10, `RemoteInputSelectionTest` 3, `RestrictedSettingsTest` 8, `SseParserTest` 4, `TransientRetryTest` 2. The release-variant unit-test task was not re-run in this pass.
- `:app:assembleRelease --offline` succeeded in 15m 25s (299 tasks: 223 executed, 76 from cache) on the configured 1536m heap after the previous daemon was stopped. The heap in `gradle.properties` was not lowered. R8 ran inside that assemble (`minifyReleaseWithR8`). Known warnings: SDK XML version 4, and strip skipped for `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so`.
- Signed APK: `/tmp/dastyar-0.5.0.apk` (3,245,011 bytes). SHA-256 `12e1ad75d3b49d59e35dae5db4bbe6557ea5ad6170a77ec655bd4a21f2c3489b`. `apksigner verify` succeeded with APK Signature Scheme v2. Certificate DN `CN=Dastyar, OU=Mobile, O=Dastyar, C=IR`. SHA-1 `f2eab974ba099eb25a0b970d156f88aebf5fd950`. SHA-256 `9796073f30fec3e58e3fb9931103815b0205caa4060a4adc53fbc2b9ab19e453`. Same certificate as v0.4.0. `/tmp/dastyar-0.4.0.apk` and `/tmp/dastyar-0.3.0.apk` were left in place.
- `aapt dump badging` reports package `app.dastyar.agent`, `versionCode` 7, `versionName` 0.5.0, minSdk 26, targetSdk 35, compileSdk 35.
- This server has no emulator. The APK was not installed here. Nothing was sent to Telegram.

## 2026-11-26 — v0.5.0 publish

The section above is the pre-tag local build. This follow-up does not move `v0.5.0`.

### Publish

- Commit tagged: `fba63b9` — Ship Dastyar 0.5.0 with a chat-first menu and a safer Connectors screen. That commit is `versionCode` 7 and `versionName` 0.5.0.
- Annotated tag `v0.5.0` points at `fba63b9230f0455d0bb9578adcb7c127b2115a1a` (tag object `0d59e534ca26a75f1f88be8c8d7fa800b59e2953`). It was not force-pushed. This docs commit does not move the tag.
- Release: https://github.com/mohsenone/dastyar-android/releases/tag/v0.5.0
- APK: https://github.com/mohsenone/dastyar-android/releases/download/v0.5.0/app-release.apk (3,245,011 bytes, state uploaded). A locally signed APK of the same size was attached first so the release was not empty. Workflow run [45901840532](https://github.com/mohsenone/dastyar-android/actions/runs/45901840532) then deleted that asset and uploaded its own `app-release.apk`. Both files are 3,245,011 bytes and the same signing certificate. Zip SHA-256 differs because Actions rebuilt the APK from the tag: local (the file the supervisor installed) `12e1ad75d3b49d59e35dae5db4bbe6557ea5ad6170a77ec655bd4a21f2c3489b`, release asset `9ff886da3c70b1fa985f85e03bf3e6ab10eceaa9f8a884af0786760498e9bae2`.
- `aapt dump badging` on the release asset reports package `app.dastyar.agent`, `versionCode` 7, `versionName` 0.5.0, compileSdk 35, minSdk 26, targetSdk 35. `apksigner verify` succeeded with APK Signature Scheme v2. Certificate DN is `CN=Dastyar, OU=Mobile, O=Dastyar, C=IR`. SHA-1 `f2eab974ba099eb25a0b970d156f88aebf5fd950`. SHA-256 `9796073f30fec3e58e3fb9931103815b0205caa4060a4adc53fbc2b9ab19e453`. Same certificate as v0.4.0 and the local 0.5.0 APK.
- Nothing was sent to Telegram from this machine. The tag workflow’s existing Telegram step ran and logged `TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is absent. Skipping Telegram upload.` This pass did not call the Telegram API itself.
- Actions replaced the release body with the generic workflow text (`Dastyar (دستیار) — on-device AI agent`, plus a generated Full Changelog link). The bilingual notes were written back with `gh release edit` after the workflow finished. The asset stayed `app-release.apk` at 3,245,011 bytes.

### Emulator QA

A supervisor installed the local APK (`12e1ad75…`, built from `fba63b9`) on an official Android SDK emulator: API 35 `google_apis` x86_64, Pixel 6 profile, headless TCG with SwiftShader. KVM is not usable on that host. It was not tested on a physical phone.

Fresh install: onboarding, chat (no bottom tab bar), the hamburger drawer, Providers, Connectors, Routines, Settings, then Persian and right-to-left (Settings, chat, the drawer, and Connectors). The process stayed alive. No `AndroidRuntime` FATAL for `app.dastyar.agent`.

Upgrade path: uninstall, install official v0.3.0, open Links. That still crashes on 0.3.0 with the known empty-snapshot `NoSuchElementException` (`state.rows.first { … }`). Official v0.4.0 (`0e6a8f7`) already fixed that. Install official v0.4.0 over that profile: Connectors opened, the process stayed alive, no FATAL. Install v0.5.0 over that: the drawer and Connectors opened, the process stayed alive, no FATAL. Room data from the 0.3.0 profile survived. Room stays at version 3.

The remaining real-phone crash, reported on official v0.4.0 even after `0e6a8f7`: opening Connectors constructed AppAuth’s `AuthorizationService` immediately. AppAuth 0.11.1 reads `PackageInfo.signatures` via deprecated `GET_SIGNATURES` and only catches `NameNotFoundException`. On API 28+ that array is often null, so the constructor throws and the process dies. Fresh emulator images often still fill the field, which is why the earlier fix looked fine there. v0.5.0 creates the service lazily inside `runCatching`. Corrupt connector JSON, vault throws, and Shizuku or the signing SHA-1 during composition were also hardened. That specific null-signatures crash was diagnosed from code and was not reproduced on this emulator. Someone on a real Android 14 or 15 phone should open Connectors again after installing 0.5.0.

The first launch of a newly installed APK on this TCG emulator can show “Dastyar isn't responding” with reason `No response to onStartJob` (WorkManager). That is emulator speed, not a Connectors crash. After `cmd package compile -m speed`, the walk had no ANR in the cleared logcat. This may not appear on a real phone.

A live tool call was not driven with an API key. The home-screen widget and the quick-settings tile were not pinned or tapped. Share-in was not re-tested as its own pass. The v0.4.0 grant and revoke permission matrix was not re-run in full for 0.5.0. The permission model was kept as it was (wizard, Fix everything, live status, restricted-settings guide, confirmation floor).

Chat is the home screen and fills the screen. The bottom tab bar is gone. The hamburger opens a modal drawer: Chat, Providers, Connectors, Routines, Settings. Material 3 polish in this release is rounded cards, a chat empty state, an input bar with the model picker, consistent drawer destinations, English by default, and Persian right to left.

Unit tests at `fba63b9`: 76 debug unit tests, 0 failures, 14 suites. Release-variant unit tests were not re-run in this pass. There are no instrumented tests. The build server has no emulator.
