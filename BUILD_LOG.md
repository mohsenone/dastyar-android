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
