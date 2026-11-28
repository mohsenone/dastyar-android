# Changelog

## 0.6.1 — 2026-11-28

`versionCode` 9, `versionName` 0.6.1. Tagged `v0.6.1` on `da41686`. Release: https://github.com/mohsenone/dastyar-android/releases/tag/v0.6.1. APK: https://github.com/mohsenone/dastyar-android/releases/download/v0.6.1/app-release.apk (3,326,127 bytes). A supervisor installed the local APK on an API 35 emulator; the build server did not. SpeechRecognizer and TextToSpeech were not run here, and a live talk-and-hear loop was not exercised. See BUILD_LOG.md.

English

- Back. Every screen except Chat and onboarding has an up arrow in the same ~48dp bar. The arrow mirrors in Persian. Chat keeps the menu icon. System Back and the predictive-back gesture close the menu drawer first, then a confirmation or permission dialog, then the voice stage, then the previous screen. If a section was opened with nothing under it, Back returns to Chat. Back does not leave the app from Providers, Connectors, Routines, Settings, or a sub-screen, and it does not leave while the drawer or the voice stage is open. The chat history drawer closes on Back before the activity does. There is still no bottom tab bar.
- Voice errors leave. A recognizer, permission, network, provider, or speech error stays on the stage for a few seconds (4.5), with Try again and Dismiss. It then returns to listening when hands-free can safely restart (at most once), or closes the stage and leaves a short banner you can dismiss. Retry, leaving voice, sending a turn, a recognized phrase, and a spoken reply clear the old error. “Voice paused” is no longer a permanent Stop-only card.
- Voice pipeline. The app no longer treats its own listing stub as a recognizer. That stub answers every session with `ERROR_CLIENT`, and `isRecognitionAvailable` is true when the stub is the only service the app can see. The recognizer is created on the main thread, bound to a visible external engine (Google, then Google speech services, Android System Intelligence, Samsung, Xiaomi), and passed `EXTRA_CALLING_PACKAGE`. If that engine is missing or fails with a client, server, or language error, API 31+ tries the on-device recognizer. Final text is taken from the results list, the unstable extra, or the last partial, and a hands-free result is sent as a normal chat turn. One-shot still fills the composer. Text-to-speech waits for `OnInitListener` success, speaks only the visible reply, and does not fall back from a missing Persian voice to English. After speech finishes, hands-free listens again unless you stopped or the error needs a manual retry.
- If there is still no usable engine, Dastyar records a short clip and sends it to a saved key that exposes OpenAI-compatible `POST /v1/audio/transcriptions`. Groq is included (`whisper-large-v3-turbo` at `api.groq.com`). Official OpenAI uses `whisper-1`. A custom OpenAI-compatible base is tried only until a probe fails. Gemini and OpenRouter are not assumed to transcribe. The stage says “Listening with this phone” or “Transcribing with Groq (your key)” (and the Persian lines). A missing key, 401, or an unsupported endpoint is a transient message. This is not a free voice plan. Groq is still not used to speak the reply.
- Settings has Voice diagnostics, refreshed when you return to the app: system recognizer, on-device recognizer (or “not on this Android version”), the speech engine, whether a Persian voice and an English voice are installed, and microphone granted / denied / needs settings.
- Package visibility already had the recognition and TTS intents in 0.6.0. Those intents were not enough to see the Google, Samsung, and Xiaomi packages on Android 11+, so the queries now also name those packages. `QUERY_ALL_PACKAGES` was not added.

فارسی

- بازگشت. هر صفحه‌ای جز گفتگو و معرفی، در همان نوار حدود ۴۸dp یک پیکان بازگشت دارد که در فارسی جهت درست را می‌گیرد. گفتگو همان منو را دارد. دکمهٔ بازگشت سیستم و حرکت پیش‌بینی‌شده اول کشو را می‌بندد، بعد گفتگوی تأیید یا اجازه، بعد صحنهٔ صدا، بعد صفحهٔ قبلی. اگر زیر صفحه چیزی نباشد، به گفتگو برمی‌گردد. از کلیدها، اتصال‌ها، روال، تنظیمات یا زیرصفحه‌ها برنامه بسته نمی‌شود، و با کشو یا صحنهٔ صدا باز هم نه. نوار پایین برنگشته است.
- خطای صدا نمی‌ماند. خطا حدود ۴٫۵ ثانیه روی صحنه است، با «دوباره» و «بستن». بعد یا یک‌بار دیگر گوش می‌دهد، یا صحنه را می‌بندد و یک نوار کوتاه می‌ماند که می‌توانید ببندید. تلاش دوباره، خروج از صدا، فرستادن، شناخت جمله، و خواندن پاسخ، خطای قبلی را پاک می‌کند. «صدا متوقف شد» دیگر کارت دائمی فقط با توقف نیست.
- مسیر صدا. برنامه دیگر سرویس فهرست خودش را تشخیص‌دهنده حساب نمی‌کند. آن سرویس همیشه `ERROR_CLIENT` می‌دهد و اگر تنها سرویسِ دیده‌شده باشد، سیستم می‌گوید تشخیص هست. تشخیص‌دهنده روی نخ اصلی ساخته می‌شود، به موتور بیرونیِ دیده‌شده وصل می‌شود، و نام بستهٔ برنامه را می‌فرستد. اگر آن موتور نباشد یا خطای کلاینت، سرور، یا زبان بدهد، از API ۳۱ تشخیص روی دستگاه امتحان می‌شود. متن نهایی از نتیجه، متن ناپایدار، یا آخرین متن جزئی می‌آید و در حالت آزاد مثل یک نوبت گفتگو فرستاده می‌شود. یک‌بار زدن میکروفون هنوز متن را در فیلد می‌گذارد. خواندن متن صبر می‌کند تا موتور آماده شود، فقط پاسخِ روی صفحه را می‌خواند، و اگر صدای فارسی نباشد انگلیسیِ بی‌صدا نمی‌خواند. بعد از تمام شدن خواندن، حالت آزاد دوباره گوش می‌دهد مگر اینکه متوقف کرده باشید یا خطا نیاز به تلاش دستی داشته باشد.
- اگر هنوز موتور قابل استفاده نباشد، یک برش کوتاه ضبط می‌شود و به کلیدی فرستاده می‌شود که `POST /v1/audio/transcriptions` سازگار با OpenAI دارد. گروک هم هست (`whisper-large-v3-turbo`). OpenAI رسمی `whisper-1` است. پایهٔ سفارشی فقط تا وقتی آزمایشش رد نشده. جمینی و اوپن‌روتر فرض نمی‌شوند که رونویسی دارند. صحنه می‌گوید «شنیدن با همین گوشی» یا «رونویسی با گروک (کلید شما)». کلید نبودن، ۴۰۱، یا مسیر پشتیبانی‌نشده یک پیام گذرا است. طرح رایگان صدا اختراع نشده. گروک هنوز پاسخ را نمی‌خواند.
- تنظیمات «عیب‌یابی صدا» دارد و با برگشتن به برنامه تازه می‌شود: تشخیص‌گر سیستم، تشخیص روی دستگاه، موتور گفتار، نصب بودن صدای فارسی و انگلیسی، و وضعیت میکروفون.
- در ۰.۶.۰ قصدهای تشخیص و متن‌به‌گفتار در `<queries>` بودند، ولی برای دیدن بستهٔ گوگل، سامسونگ و شیائومی در اندروید ۱۱ کافی نبودند. همان بسته‌ها اضافه شدند. `QUERY_ALL_PACKAGES` اضافه نشد.

## 0.6.0 — 2026-11-27

`versionCode` 8, `versionName` 0.6.0. No tag and no GitHub Release. This server has no emulator and did not install the APK. The assist picker and the Home long-press were not exercised here.

English

- Visual pass on the same teal, clay, and paper identity. Surfaces are deeper in dark mode and warmer on paper in light mode, with a quiet vertical wash behind every screen. The top bar is a compact 48dp row. On Chat it has no large wordmark: menu, chats, a one-line model chip, hands-free, and new chat. The transcript starts higher. There is still no bottom tab bar. Chat bubbles are distinct (user wash versus an outlined assistant card), tool calls stay in cards, and the empty chat is a short welcome with suggestions. The add-key action remains when no key is saved. The drawer has the same five destinations, a selected state, and the version in the footer. Motion is a short fade, a bubble appear, and a listening pulse. With animator scale at 0 the screens stay complete; nothing waits on an animation.
- Voice conversation in Persian and English. The composer mic is one shot: the words land in the field and you send them (the placeholder says to review, then send). Hands-free is a full-screen stage: listen, send that turn through the same agent, speak the visible reply, then listen again until you stop, go back, or leave the screen. States are idle, listening, processing, speaking, error, missing microphone permission, and missing speech data. Spoken text is the visible reply only. Keys, bearer tokens, tool JSON, and confirmation payloads are not read aloud. Messages, email, calls, deletes, and payments still ask first, including when the request was spoken.
- On-device speech uses `SpeechRecognizer` and `TextToSpeech`. The Settings voice language is Auto, Persian, or English. Auto follows the app language and may ask the recognizer to detect language. Persian maps to `fa-IR` and English to `en-US`. A Persian reply, or Persian as the voice language, picks an installed `fa` voice. If the engine returns missing data, unsupported language, or a voice list with no Persian voice, the app does not crash and does not silently switch that reply to English. It shows an install hint (Google Text-to-speech and a Persian voice) and a button for `TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA`, then the system TTS settings, then voice-input settings. The reply stays on screen. A phone with no recognizer gets a clear message. If the recognizer is missing and the saved provider is official OpenAI (`api.openai.com`) or a custom / non-official OpenAI-compatible base that has not already failed a probe, Dastyar may record a short clip and call that provider’s audio routes with the saved key. Groq, OpenRouter, Gemini, and the other built-in hosts are not assumed to have those routes. A successful fallback says it was spoken or transcribed with your provider. A failed fallback keeps the text and the install hint. There is no bundled free voice plan.
- `RECORD_AUDIO` is still runtime and is asked only when you tap the mic or start voice mode, not when Chat opens. Deny, revoke, and don’t-ask-again use the existing Fix / app-settings path. The microphone status row describes voice conversation.
- Default digital assistant. `DastyarVoiceInteractionService` is bound with `BIND_VOICE_INTERACTION` and `supportsAssist`. The session service draws a compact overlay in the same light, dark, and RTL look and can hand the utterance to the main chat. `DastyarRecognitionService` is a listing stub: it reports a client error and does not perform dictation. Dictation uses the phone’s `SpeechRecognizer`. Settings has “Set as default digital assistant” / «تنظیم به عنوان دستیار دیجیتال پیش‌فرض». On API 29+ that requests `RoleManager.ROLE_ASSISTANT` when the role intent resolves, and otherwise opens voice-input settings, then the default-apps screen. The button shows whether the role is held when the platform reports it. Below API 29 the status says the role is not reported. The overlay does not crash if the microphone is denied; it shows the permission path. This server did not open Settings → Digital assistant app and did not fire the assist gesture.

فارسی

- ظاهر همان فیروزه، خاک رس و کاغذ است، با سطح تیره‌تر در حالت شب و کاغذ گرم‌تر در روز، و یک سایهٔ آرام پشت صفحه‌ها. نوار بالا حدود ۴۸dp است. در گفتگو عنوان درشت نیست: منو، گفتگوها، مدل در یک خط، گفتگوی آزاد، و گفتگوی تازه. متن گفتگو بالاتر می‌آید. نوار پایین برنگشته است. حباب کاربر و دستیار جدا هستند، فراخوانی ابزار کارت است، و گفتگوی خالی یک خوشامد کوتاه با پیشنهاد است. اگر کلیدی نباشد، افزودن کلید سر جایش است. کشو همان پنج مقصد را دارد، حالت انتخاب، و نسخه در پایین. حرکت کوتاه است و با خاموش بودن انیمیشن صفحه کامل می‌ماند.
- گفتگوی صوتی فارسی و انگلیسی. میکروفون نوار ورودی یک‌بار است: متن در فیلد می‌نشیند و خودتان می‌فرستید. گفتگوی آزاد یک صحنهٔ تمام‌صفحه است: می‌شنود، همان عامل را صدا می‌زند، پاسخِ روی صفحه را می‌خواند، و دوباره گوش می‌دهد تا توقف یا برگشت یا خروج. وضعیت‌ها: آماده، در حال شنیدن، در حال فکر، در حال گفتن، خطا، نبود اجازهٔ میکروفون، و نبود دادهٔ گفتار. فقط پاسخِ دیده‌شده خوانده می‌شود. کلید، توکن، JSON ابزار و برگهٔ تأیید خوانده نمی‌شود. پیام، ایمیل، تماس، حذف و پرداخت حتی اگر گفته شده باشند باز هم تأیید می‌خواهند.
- تشخیص و خواندن با موتور خود گوشی است. زبان صدا در تنظیمات: خودکار، فارسی، یا انگلیسی. خودکار زبان برنامه را دنبال می‌کند. فارسی `fa-IR` و انگلیسی `en-US` است. اگر پاسخ یا زبان صدا فارسی باشد، صدای `fa` نصب‌شده انتخاب می‌شود. اگر داده نباشد، زبان پشتیبانی نشود، یا فهرست صداها فارسی نداشته باشد، برنامه نمی‌ترکد و بی‌صدا به انگلیسی برنمی‌گردد. راهنمای نصب متن‌به‌گفتار گوگل و صدای فارسی می‌آید، با دکمه‌ای که تنظیم نصب دادهٔ گفتار را باز می‌کند. پاسخ روی صفحه می‌ماند. اگر تشخیص‌دهنده نباشد، پیام روشن است. اگر تشخیص‌دهنده نباشد و کلید ذخیره‌شده مال OpenAI رسمی یا یک پایهٔ سازگار باشد که هنوز رد نشده، ممکن است یک برش کوتاه با همان کلید به مسیر صدای همان ارائه‌دهنده برود. گروک، اوپن‌روتر، جمینی و میزبان‌های ساخته‌شدهٔ دیگر فرض نمی‌شوند که آن مسیر را دارند. اگر این راه موفق شود، می‌گوید با ارائه‌دهندهٔ شما گفته یا نوشته شده. اگر شکست بخورد، متن و راهنمای نصب می‌ماند. طرح رایگان صدا اختراع نشده است.
- `RECORD_AUDIO` فقط با زدن میکروفون یا شروع حالت صوتی پرسیده می‌شود، نه با باز شدن گفتگو. رد، پس‌گرفتن، و «دیگر نپرس» همان مسیر رفع و تنظیمات است.
- دستیار دیجیتال پیش‌فرض. سرویس تعامل صوتی با `supportsAssist` ثبت شده است. نشست یک روکش جمع‌وجور هم‌خوان با ظاهر جدید نشان می‌دهد و گفته را به گفتگوی اصلی می‌سپارد. سرویس تشخیص فقط برای فهرست شدن است و خودش دیکته نمی‌کند. دیکته با `SpeechRecognizer` گوشی است. در تنظیمات دکمهٔ «تنظیم به عنوان دستیار دیجیتال پیش‌فرض» هست. از API ۲۹ نقش `ROLE_ASSISTANT` درخواست می‌شود و اگر آن قصد باز نشود، تنظیم ورودی صدا و بعد برنامه‌های پیش‌فرض. وضعیت نقش وقتی سیستم بگوید نشان داده می‌شود. اگر میکروفون رد شود روکش نمی‌ترکد. این سرور صفحهٔ دستیار دیجیتال و حرکت کمکی را باز نکرده است.

## 0.5.0 — 2026-11-26

`versionCode` 7, `versionName` 0.5.0. No tag and no GitHub Release. This server has no emulator and did not install the APK.

English

- Connections crash, real-phone upgrade. The empty-list `NoSuchElementException` from `rows.first { }` was already fixed in shipped `0e6a8f7` and is still `connectorRow` / `firstOrNull`. A fresh emulator of that build did not crash. A phone that already had v0.4.0 still could, because opening Connectors constructed `GoogleAuth`, and AppAuth 0.11.1 builds `AuthorizationService` by hashing `PackageInfo.signatures` from the deprecated `GET_SIGNATURES` flag. On API 28+ that array is often null (real browsers, API 34/35). The library only catches `NameNotFoundException`, so the constructor throws and the process dies. Emulator images often still fill the old field, which is why a fresh profile missed it. The service is now created lazily inside `runCatching`. If it cannot start, Google sign-in returns a message and the list stays up. The manifest also queries `CustomTabsService` so a browser can be seen when package visibility hides it.
- Other upgrade hazards on the same screen, closed in this pass: corrupt or non-object `dastyar_connectors` JSON (including a JSON array) is not thrown and is not overwritten; a Keystore read or write that throws marks the screen unreadable instead of killing the coroutine; Shizuku status and the signing SHA-1 are not called raw during composition; a row that fails to build shows “Couldn’t load this connector” and the rest of the list stays. A failed snapshot shows a retry card. Compose still cannot catch an exception thrown from inside a composable call, so this is not a universal error boundary. A native crash inside Shizuku’s class init cannot be caught either.
- Room stays version 3 (`model_cache` from v0.2.1). There is no 0.5.0 schema change. Upgrades 1→2 and 2→3 stay explicit. `fallbackToDestructiveMigration()` is gone, so a known upgrade does not wipe the vault, chats, or connector settings. Downgrade still uses destructive fallback. `exportSchema` is on. Version 3 opens as version 3 with an empty migration. An unknown version is not given a silent wipe path.
- Chat is the home screen and fills the screen. The five bottom tabs (Chat, Providers, Connectors, Routines, Settings) moved into a modal drawer opened from the menu icon. Persian layout stays right to left, so the icon sits on the start side. The chat’s own conversation list is a separate drawer, opened from the chats icon, and it does not take the edge swipe. Deep links, the widget, the quick-settings tile, and share-to-Dastyar still open the current chat. A new chat happens only when the user asks for one. Settings shortcuts to Providers, Connectors, Permissions, Privacy, Saved facts, and Tool audit still open those screens. Restored routes that are not in the menu go home, and menu navigation does not restore the old bottom-bar state.
- Visual pass on the existing teal, clay, and paper system: outlined rounded cards, chat bubbles with a short tail, a bordered composer that still has the model chip, photo, and voice controls, and bordered markdown code and tool-call cards. Light, dark, brand, and dynamic color are unchanged. Motion is a short fade between screens. Screen titles that this pass touched use `values` / `values-fa`. Stored `language=fa` still stays Persian. The confirmation floor is unchanged.

فارسی

- ترکیدن اتصال‌ها روی گوشی واقعی بعد از به‌روزرسانی. خطای فهرست خالی (`first { }`) در `0e6a8f7` مانده و همان `firstOrNull` است. نصب تازهٔ همان ساخت روی شبیه‌ساز نترکید. گوشی‌ای که ۰.۴.۰ را از قبل داشت هنوز می‌توانست بترکد، چون باز کردن اتصال‌ها `GoogleAuth` را می‌ساخت و AppAuth ۰.۱۱.۱ در `AuthorizationService` امضای `PackageInfo.signatures` را با پرچم قدیمی `GET_SIGNATURES` می‌خواند. از API ۲۸ آن آرایه اغلب تهی است. کتابخانه فقط `NameNotFoundException` را می‌گیرد، پس سازنده می‌ترکد و فرایند می‌میرد. تصویر شبیه‌ساز اغلب همان فیلد قدیمی را پر می‌کند. سرویس حالا تنبل و داخل `runCatching` ساخته می‌شود. اگر شروع نشود، ورود گوگل یک پیام می‌دهد و فهرست می‌ماند. مانیفست `CustomTabsService` را هم می‌پرسد تا مرورگر پشت محدودیت دید بسته پنهان نماند.
- خطرهای دیگر همان صفحه: JSON خراب یا غیرشیء در `dastyar_connectors` پرتاب نمی‌شود و روی فایل نوشته نمی‌شود. خطای Keystore صفحه را ناخوانا می‌کند و کوروتین را نمی‌کشد. وضعیت شیزوکو و SHA-1 هنگام ترکیب خوانده نمی‌شوند. کارتی که ساخته نشود «این اتصال خوانده نشد» نشان می‌دهد و بقیهٔ فهرست می‌ماند. اگر کل تصویر خوانده نشود، کارت تلاش دوباره هست. Compose هنوز نمی‌تواند استثنای داخل یک فراخوانی composable را بگیرد. سقوط بومی هنگام بار شدن کلاس شیزوکو هم گرفتار نمی‌شود.
- پایگاه Room همان نسخهٔ ۳ است. تغییر طرح در ۰.۵.۰ نیست. مسیر ۱ به ۲ و ۲ به ۳ صریح است. `fallbackToDestructiveMigration()` برداشته شد تا به‌روزرسانی شناخته‌شده خزانه، گفتگو و تنظیم اتصال را پاک نکند. فقط تنزل نسخه هنوز مخرب است. طرح صادر می‌شود. نسخهٔ ۳ روی نسخهٔ ۳ با مهاجرت خالی باز می‌شود.
- گفتگو خانه است و صفحه را پر می‌کند. پنج زبانهٔ پایین (گفتگو، کلیدها، اتصال‌ها، روال، تنظیم) به کشوی منو رفتند و با آیکن منو باز می‌شوند. فارسی راست‌به‌چپ است و آیکن در سمت شروع می‌نشیند. فهرست گفتگوها کشوی جدا است و لبهٔ صفحه را نمی‌گیرد. پیوند عمیق، ویجت، کاشی تنظیمات سریع و اشتراک‌گذاری همان گفتگوی فعلی را باز می‌کنند. گفتگوی تازه فقط وقتی است که کاربر بخواهد. میانبرهای تنظیمات به کلیدها، اتصال‌ها، اجازه‌ها، حریم خصوصی، یادداشت‌ها و گزارش ابزار هنوز کار می‌کنند.
- ظاهر همان فیروزه، خاک رس و کاغذ است: کارت گرد با خط دور، حباب گفتگو، نوار ورودی با انتخاب مدل و عکس و صدا، و کارت کد و ابزار. روشن و تیره و رنگ پویا همان است. حرکت فقط یک محو کوتاه است. عنوان صفحه‌هایی که این مرحله دست زده در منابع رشته است. `language=fa` فارسی می‌ماند. کف تأیید عوض نشده است.

## 0.4.0 — 2026-11-26

Follow-up, still `versionCode` 6 and `versionName` 0.4.0. No tag and no GitHub Release.

English

- Connectors stays open when the snapshot has not loaded or is missing a catalog id. Looking up a row no longer throws.
- The restricted-settings guide, the other-language block, and the Samsung / Xiaomi / Huawei notes say "three-dot menu" or "منوی سه نقطه". The fonts do not draw a vertical ellipsis.
- Notification access that is listed but not connected on Android 13+ says a sideloaded install may still need Allow restricted settings, then a toggle off and on. The component check accepts the flattened `package/.tools.Class` form Android stores on API 26–35.

فارسی

- اتصال‌ها وقتی فهرست هنوز نرسیده یا یک شناسه نیست باز می‌ماند و دیگر نمی‌ترکد.
- راهنمای تنظیمات محدود و یادداشت سامسونگ، شیائومی و هواوی به‌جای نویسه‌ای که فونت ندارد، «منوی سه نقطه» یا three-dot menu می‌گوید.
- اگر دسترسی به اعلان در تنظیم نوشته شده ولی شنونده وصل نشده، در اندروید ۱۳ و جدیدتر می‌گوید نصب از بیرون فروشگاه ممکن است هنوز اجازه به تنظیمات محدود بخواهد، بعد یک‌بار خاموش و روشن. تطبیق جزء، شکل کوتاه‌شدهٔ `package/.tools.Class` را هم می‌پذیرد.

English

- Permission setup is a status list, a capability wizard, and Fix everything. Fix everything walks only missing items and skips all-files access and Shizuku. Status refreshes when the activity resumes. A denial that Android will not ask again opens app settings instead of a dead dialog. Background location is requested only after fine or coarse location. Android 13+ restricted settings stay a manual App info → ⋮ step. Persian guide text is right-to-left even when the app language is English. The other language is shown in its own direction.
- Privacy screen: what each capability is for, that chats and keys stay on the phone except when you send them to the provider you chose or turn on a connector, how to revoke, and the confirmation floor (messages, email, calls, deletes, and payments still ask, even in Auto).
- Least privilege: removed `READ_PHONE_STATE`, `BLUETOOTH_CONNECT`, and `MODIFY_AUDIO_SETTINGS`. Did not add a default SMS role, `USE_EXACT_ALARM`, `BLUETOOTH_SCAN`, or `NEARBY_WIFI_DEVICES`. Added media reads (`READ_MEDIA_IMAGES`, `VIDEO`, `AUDIO`, and partial visual access on API 34+) for `list_recent_media`. `READ_EXTERNAL_STORAGE` stays `maxSdkVersion` 32. All-files access stays optional and is not nagged. Shizuku is detected and is not a substitute for the grants above.
- Tools that need access name the missing permission in English and Persian and point at Fix. They do not return an empty success.
- Providers no longer marks the whole catalog with `FLAG_SECURE`. Only the key sheet does, and not on emulator or software-renderer fingerprints, where that flag has drawn a black window while the accessibility tree still listed the catalog. This server could not reproduce the black frame.
- Bottom navigation says Connectors (اتصال‌ها). Wizard, privacy, and navigation chrome use string resources. Diagnostic titles stay in Kotlin so unit tests and `permissions_diagnostic` keep their text. Older screens still use `t()`.
- The agent retries a transient network, rate-limit, or 5xx failure up to three attempts when nothing has streamed, with backoff. Cancel clears the typing indicator and says Stopped. Chat can attach a photo from the system picker and warns when the selected model has no vision. A home-screen widget and a quick-settings tile open the current chat. Share-in for text and images was already present.
- No tag and no GitHub Release in this pass. This build was not installed on a phone or an emulator.

فارسی

- راه‌اندازی اجازه‌ها فهرست وضعیت، راهنمای گروه‌ها، و «رفع همه» است. رفع همه فقط موردهای داده‌نشده را می‌رود و دسترسی به همهٔ فایل‌ها و شیزوکو را رد می‌کند. وضعیت با برگشتن به برنامه تازه می‌شود. اگر اندروید دیگر نپرسد، به‌جای گفتگوی مرده، اطلاعات برنامه باز می‌شود. موقعیت پس‌زمینه فقط بعد از موقعیت دقیق یا تقریبی خواسته می‌شود. تنظیمات محدود اندروید ۱۳ به بعد همان مسیر دستی اطلاعات برنامه → ⋮ است. متن فارسی راهنما حتی وقتی زبان برنامه انگلیسی است راست‌به‌چپ است. زبان دیگر در جهت خودش می‌آید.
- صفحهٔ حریم خصوصی: هر قابلیت برای چیست، گفتگو و کلید روی گوشی می‌ماند مگر وقتی به ارائه‌دهندهٔ انتخابی بفرستید یا اتصالی را روشن کنید، چطور پس بگیرید، و کف تأیید (پیام، ایمیل، تماس، حذف و پرداخت حتی در حالت خودکار هم می‌پرسند).
- کمترین اجازه: `READ_PHONE_STATE` و `BLUETOOTH_CONNECT` و `MODIFY_AUDIO_SETTINGS` حذف شدند. نقش پیامک پیش‌فرض، `USE_EXACT_ALARM`، `BLUETOOTH_SCAN` و `NEARBY_WIFI_DEVICES` اضافه نشدند. خواندن رسانه (`READ_MEDIA_IMAGES` و `VIDEO` و `AUDIO` و دسترسی جزئی عکس در API ۳۴) برای `list_recent_media` اضافه شد. `READ_EXTERNAL_STORAGE` همچنان `maxSdkVersion` ۳۲ است. دسترسی به همهٔ فایل‌ها اختیاری است و در رفع همه نیست. شیزوکو فقط شناسایی می‌شود و جای اجازه‌های بالا نیست.
- ابزاری که اجازه ندارد، نام اجازه را به فارسی و انگلیسی می‌گوید و به رفع اشاره می‌کند. موفقیتِ خالی برنمی‌گرداند.
- فهرست ارائه‌دهندگان دیگر با `FLAG_SECURE` قفل نمی‌شود. فقط برگهٔ کلید قفل است، و آن هم نه روی اثرانگشت شبیه‌ساز یا رندر نرم‌افزاری، جایی که این پرچم پنجره را سیاه کرده در حالی که درخت دسترس‌پذیری هنوز فهرست را نشان می‌داده. این سرور نتوانست قاب سیاه را بازتولید کند.
- برچسب نوار پایین Connectors (اتصال‌ها) است. متن راهنما، حریم خصوصی و ناوبری در منابع رشته است. عنوان تشخیص اجازه در کاتلین مانده تا آزمون و `permissions_diagnostic` متنشان را از دست ندهند. صفحه‌های قدیمی هنوز از `t()` استفاده می‌کنند.
- عامل خطای گذرای شبکه، محدودیت نرخ، یا ۵xx را تا سه بار، وقتی هنوز چیزی نرسیده، با فاصلهٔ بیشتر دوباره می‌فرستد. لغو، نشان تایپ را برمی‌دارد و «متوقف شد» می‌گوید. گفتگو می‌تواند از انتخابگر سیستم عکس پیوست کند و اگر مدل بینایی نداشته باشد هشدار می‌دهد. ویجت صفحهٔ خانه و کاشی تنظیمات سریع گفتگوی فعلی را باز می‌کنند. اشتراک‌گذاری متن و تصویر از قبل بود.
- در این مرحله برچسب و انتشار گیت‌هاب ساخته نشده است. این ساخت روی گوشی یا شبیه‌ساز نصب نشده است.

### Permission audit

| Access | Why | How requested | Deep link | Tools gated |
|---|---|---|---|---|
| `POST_NOTIFICATIONS` (API 33+) | Show background work | Runtime dialog after onboarding or from the wizard. Don’t-ask-again opens app info. Below 33 it is not a grant. | `ACTION_APPLICATION_DETAILS_SETTINGS` | Foreground routine notification |
| SMS group: `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` | Read and send SMS. Not the default SMS role. | Runtime dialog. Android 13+ may also need restricted settings. | App info, then ⋮ Allow restricted settings | `read_sms`, `send_sms`, SMS routine trigger |
| `READ_CONTACTS`, `WRITE_CONTACTS` | Search and save a contact | Runtime dialog | App info if the dialog will not show again | `search_contacts`, `write_contact` |
| `READ_CALL_LOG` | Read the call log. Nothing writes it. | Runtime dialog plus restricted settings on Android 13+ | App info → ⋮ | call-log read |
| `CALL_PHONE` | Place a call after you confirm | Runtime dialog | App info | `place_call` |
| `READ_CALENDAR`, `WRITE_CALENDAR` | List, create, and delete events | Runtime dialog | App info | `list_events`, `create_event`, `delete_event` |
| `CAMERA` | Photo or video you take | Runtime dialog | App info | `take_photo`, `take_video`, chat camera |
| `RECORD_AUDIO` | Voice conversation. Asked only when you tap the microphone or start voice mode, not when Chat opens. | Runtime dialog. Don’t-ask-again opens app info. | App info | chat microphone, hands-free, assist overlay |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | Location when a tool asks | Runtime dialog first | App info | `get_location` |
| `ACCESS_BACKGROUND_LOCATION` (API 29+) | Second step only, after foreground location | Separate request. Never on the first prompt. | App info | background location for a routine |
| `READ_EXTERNAL_STORAGE` (`maxSdkVersion` 32) | Library names on older Android | Runtime dialog | App info | `list_recent_media` |
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` (API 33+), `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+) | List photo, video, and audio names. Partial photo access counts as granted. The chat picker does not need this. | Runtime dialog | App info | `list_recent_media` |
| Accessibility service | Read the screen and confirmed taps | System settings, after the disclosure. Restricted on Android 13+. | `ACTION_ACCESSIBILITY_SETTINGS` | `read_screen`, `accessibility_action`, `operate_app` |
| Notification listener | Read alerts you can already see. No auto-reply. | System settings. Restricted on Android 13+. | `ACTION_NOTIFICATION_LISTENER_SETTINGS` | notification read and `reply_notification` |
| `SYSTEM_ALERT_WINDOW` | Status bubble | Special settings, not a runtime dialog | `ACTION_MANAGE_OVERLAY_PERMISSION` | overlay bubble |
| `SCHEDULE_EXACT_ALARM` | Routines on time. `USE_EXACT_ALARM` is not declared. | Special settings on API 31+. Older versions count as granted. | `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` | routines |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep a routine from being stopped | Request intent, then the battery list if that intent fails | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, else `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` | routines |
| `PACKAGE_USAGE_STATS` | Usage stats | Special settings | `ACTION_USAGE_ACCESS_SETTINGS` | usage tools |
| `WRITE_SETTINGS` | Change a system setting such as brightness | Special settings | `ACTION_MANAGE_WRITE_SETTINGS` | settings writes |
| `ACCESS_NOTIFICATION_POLICY` | Do Not Disturb | Special settings | `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` | `set_dnd` |
| `MANAGE_EXTERNAL_STORAGE` | Optional all-files. Prefer the folder picker. Fix everything does not ask. | Special settings. Not nagged. | `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` | all-files writes |
| Storage Access Framework | Files in a folder you pick | System folder picker | `ACTION_OPEN_DOCUMENT_TREE` | file tools |
| `QUERY_ALL_PACKAGES` | See installed apps. No extra dialog. | Install-time declaration | App info | list apps |
| Shizuku | Optional. Missing or unauthorized is said plainly. Not in Fix everything. | Shizuku’s own permission if it is installed | Shizuku app | Shizuku connector tools |
| Restricted settings (API 33+) | Sideload block on SMS, call log, accessibility, notification listener | No public intent. Try once, App info, ⋮, Allow restricted settings, return. | `ACTION_APPLICATION_DETAILS_SETTINGS` | the sensitive rows above |
| Removed: `READ_PHONE_STATE`, `BLUETOOTH_CONNECT`, `MODIFY_AUDIO_SETTINGS` | Declared in 0.3.0 and unused by a tool | Not requested | — | — |
| Not added: default SMS role, `USE_EXACT_ALARM`, `BLUETOOTH_SCAN`, `NEARBY_WIFI_DEVICES`, `WRITE_CALL_LOG` | No tool needs them | Not requested | — | — |

## 0.3.0 — 2026-11-26

English

- Visual redesign on the same teal, clay, and warm-paper palette: full Material 3 color scheme, rounded shapes, a spacing scale, and shared bars, rows, chips, buttons, empty states, and snackbars. Light and dark follow the system, with an in-app override. Brand colors are the default. On Android 12 and later, Settings can switch to wallpaper dynamic color.
- English is the language of a new install, left to right, set with Plus Jakarta Sans. Persian still uses Vazirmatn and a right-to-left layout. Onboarding and Settings offer System, English, and فارسی, applied with Android per-app locales before the first screen. A phone that already stored Persian stays Persian. A missing language key does not fall back to Persian.
- Chat shows distinct user and assistant bubbles, markdown (headings, lists, links, inline code, fenced code with a language label), tool-call cards, a typing indicator, and a rounded composer. The provider and model chip still opens the same picker: configured providers only, free models first, search, refresh, and the advanced model id.
- Onboarding is a short English-first intro with the language choice up front. Permissions, including Allow restricted settings, stay on their own screen. Empty states cover chat, providers, connectors, routines, the action log, and memories.
- Much of the older screen copy still goes through the in-code `t()` helper, with a Persian and an English string. New onboarding, language, and appearance copy lives in string resources. This build was not run on a phone or an emulator.

فارسی

- بازطراحی روی همان پالت سبزآبی، گِل و کاغذ گرم: طرح رنگ کامل Material 3، گوشه‌های گرد، مقیاس فاصله، و نوار و ردیف و نشان و دکمه و حالت خالی و پیام مشترک. روشن و تیره سیستم را دنبال می‌کنند و از داخل برنامه هم عوض می‌شوند. رنگ نشان پیش‌فرض است. در اندروید ۱۲ به بعد، تنظیمات می‌تواند رنگ پویای کاغذدیواری را روشن کند.
- زبان نصب تازه انگلیسی و چپ‌به‌راست است و قلمش Plus Jakarta Sans است. فارسی همچنان وزیرمتن و راست‌به‌چپ است. معرفی و تنظیمات System و English و فارسی را دارند و با زبانِ هر برنامهٔ اندروید پیش از نخستین صفحه اعمال می‌شوند. گوشی‌ای که فارسی ذخیره کرده فارسی می‌ماند. نبودن کلید زبان به فارسی برنمی‌گردد.
- گفتگو حباب جدا برای کاربر و دستیار، مارک‌داون (عنوان، فهرست، پیوند، کد درون‌خط، قطعهٔ کد با نام زبان)، کارت فراخوانی ابزار، نشان تایپ، و جعبهٔ نوشتن گرد دارد. تراشهٔ ارائه‌دهنده و مدل همان انتخابگر را باز می‌کند: فقط ارائه‌دهنده‌های ذخیره‌شده، مدل رایگان اول، جستجو، تازه‌سازی، و شناسهٔ پیشرفته.
- معرفی کوتاه و اول انگلیسی است و انتخاب زبان همان اول است. اجازه‌ها، از جمله تنظیمات محدود، در صفحهٔ خودشان می‌مانند. حالت خالی برای گفتگو، ارائه‌دهنده، اتصال، روال، گزارش عمل و خاطره‌ها هست.
- بخش زیادی از متن قدیمی هنوز از تابع `t()` می‌آید، با رشتهٔ فارسی و انگلیسی. متن تازهٔ معرفی، زبان و ظاهر در منابع رشته است. این ساخت روی گوشی یا شبیه‌ساز اجرا نشده است.

## 0.2.1 — 2026-11-26

English

- The chat bar shows the current provider and model. Tap it to open a list of saved providers and that provider’s models. Free models are listed first, with a free badge only when a pricing rule or a documented free tier says so.
- Refresh loads the live catalog (OpenAI-compatible `/models`, OpenRouter, Gemini, Cloudflare Workers AI, and Anthropic’s model list). The last list is kept on the phone. If the network fails, the saved list or a short built-in list is shown. Cerebras stays in the non-free section unless the payload is priced at zero. GitHub Models stays discontinued and is not fetched.
- Search filters the list. Context length, vision, and tool calling are shown when the payload includes them. Choosing a model applies to this chat immediately. Save as app default does not rewrite chats that already stored their own model. A model known to lack tool calling shows a warning.

فارسی

- نوار گفتگو نام ارائه‌دهنده و مدل را نشان می‌دهد. با لمس آن، ارائه‌دهنده‌های ذخیره‌شده و فهرست مدل همان ارائه‌دهنده باز می‌شود. مدل‌های رایگان اول می‌آیند و نشان رایگان فقط وقتی است که قاعدهٔ قیمت یا سهمیهٔ رایگانِ مستند بگوید.
- تازه‌سازی، فهرست زنده را می‌گیرد (سازگار با OpenAI، OpenRouter، Gemini، Cloudflare Workers AI، و فهرست مدل Anthropic). آخرین فهرست روی گوشی می‌ماند. اگر شبکه نباشد، فهرست ذخیره‌شده یا یک فهرست کوتاهِ ساخته‌شده نشان داده می‌شود. Cerebras در بخش غیررایگان می‌ماند مگر اینکه پاسخ قیمت صفر بدهد. GitHub Models متوقف است و دریافت نمی‌شود.
- جستجو فهرست را صافی می‌کند. طول بافت، بینایی و فراخوانی ابزار وقتی در پاسخ باشند نشان داده می‌شوند. انتخاب مدل همان لحظه برای این گفتگو ذخیره می‌شود. «پیش‌فرض برنامه» گفتگوهایی را که مدل جدا دارند بازنویسی نمی‌کند. اگر مدل ابزار را پشتیبانی نکند، هشدار می‌آید.

## 0.2.0 — 2026-11-26

English

- Connectors screen (Persian and English): Google OAuth with PKCE, GitHub, Telegram Bot API, Notion, webhook, web search (Tavily, Brave, or SearXNG), RSS, weather (Open-Meteo or a key you add for OpenWeather), remote MCP servers, and optional Shizuku. A connector that is off adds no tools. Connected means a real token or a successful test.
- Google: Gmail read/search and confirmed send, Calendar list/create/update/delete. Drive and Contacts are implemented and stay off the default scopes until you opt in. Scopes that need Google verification return the API error. There is no fake sign-in.
- Deeper accessibility: full screen tree, find, click, long-press, set text, scroll, swipe, global actions, wait-for-element, and a re-read after each step. Helpers for WhatsApp, the Telegram app, Gmail, Instagram, and Chrome. Media, volume, brightness, DND, and honest Wi-Fi/Bluetooth panels.
- Settings: tool categories, confirmation modes with a safety floor (send, email, call, delete, and pay still ask in auto), a clearer audit log, a per-chat provider and model, and routines on a clock, an SMS, or a notification.
- Shizuku is optional. If it is missing, the tools say so. No root exploit.

Not finished, or only partial: Google’s own verification for sensitive scopes; Drive and Contacts depend on the scopes you grant; Instagram has no stable UI API; the Telegram app helper is intents plus accessibility, not MTProto; the bot can send a file only from app storage; MCP SSE is one call at a time and `tools/list` is a single page; Shizuku uses the private `newProcess` method of version 13.1.5; this build was not run on a phone.

فارسی

- صفحهٔ اتصال‌ها (فارسی و انگلیسی): OAuth گوگل با PKCE، گیت‌هاب، رابط ربات تلگرام، نوشن، وب‌هوک، جستجوی وب (Tavily یا Brave یا SearXNG)، RSS، هوا (Open-Meteo یا کلیدی که خودتان برای OpenWeather می‌گذارید)، سرور MCP، و شیزوکوی اختیاری. اتصال خاموش ابزاری اضافه نمی‌کند. «متصل» یعنی توکن واقعی یا آزمایش موفق.
- گوگل: خواندن و جستجوی جیمیل و ارسالِ تأییدشده، فهرست و ساخت و ویرایش و حذف تقویم. درایو و مخاطبین پیاده شده‌اند و تا وقتی خودتان روشن نکنید در دامنه‌های پیش‌فرض نیستند. دامنه‌ای که تأیید گوگل بخواهد خطای API را برمی‌گرداند. ورود ساختگی نیست.
- دسترس‌پذیری عمیق‌تر: درخت کامل صفحه، پیدا کردن، کلیک، لمس طولانی، نوشتن، پیمایش، کشیدن، عمل سراسری، انتظار برای عنصر، و خواندن دوباره بعد از هر گام. کمک‌کننده برای واتساپ، برنامهٔ تلگرام، جیمیل، اینستاگرام و کروم. رسانه، صدا، روشنایی، مزاحم‌نشوید، و پنل صادقانهٔ وای‌فای و بلوتوث.
- تنظیمات: دستهٔ ابزار، حالت تأیید با کف ایمنی (ارسال، ایمیل، تماس، حذف و پرداخت در حالت خودکار هم می‌پرسند)، گزارش روشن‌تر، مدل جدا برای هر گفتگو، و روال با ساعت یا پیامک یا اعلان.
- شیزوکو اختیاری است. اگر نباشد ابزارها همین را می‌گویند. بهره‌جویی روت نیست.

آنچه ناتمام یا ناقص است: تأیید خود گوگل برای دامنه‌های حساس؛ درایو و مخاطبین به دامنه‌ای که می‌دهید وابسته‌اند؛ اینستاگرام رابط پایدار ندارد؛ کمک‌کنندهٔ برنامهٔ تلگرام اینتنت و دسترس‌پذیری است نه MTProto؛ ربات فایل را فقط از حافظهٔ برنامه می‌فرستد؛ SSE در MCP برای هر فراخوانی جداست و `tools/list` یک صفحه است؛ شیزوکو متد خصوصی `newProcess` نسخهٔ ۱۳.۱.۵ را صدا می‌زند؛ این ساخت روی گوشی اجرا نشده است.

## 0.1.1 — 2026-11-24

English

- Sideloaded installs on Android 13+ now get a Permissions screen and a Persian/English guide for Allow restricted settings (App info → ⋮). SMS and call-log denials explain that step instead of stopping at “Permission denied”.
- The manifest declares the SMS group (`RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`) and package visibility for WhatsApp and Gmail.
- The agent can send a confirmed WhatsApp message, reply to a notification, compose an email in Gmail or another mail app, and operate an app by its Persian or English name. Accessibility listens for all events, can scroll a child, and can long-press.
- `permissions_diagnostic` reports what is granted and the next tap. There is still no Gmail API account login.

فارسی

- نصب از بیرون فروشگاه در اندروید ۱۳ و جدیدتر صفحهٔ اجازه‌ها و راهنمای «اجازه به تنظیمات محدود» (اطلاعات برنامه → ⋮) را دارد. رد شدن پیامک و گزارش تماس دیگر فقط «اجازه داده نشد» نیست.
- مانیفست گروه پیامک (`RECEIVE_SMS` و `RECEIVE_MMS` و `RECEIVE_WAP_PUSH`) و دیده شدن واتساپ و جیمیل را اعلام می‌کند.
- عامل می‌تواند پیام تأییدشدهٔ واتساپ بفرستد، به اعلان جواب بدهد، ایمیل را در جیمیل یا برنامهٔ دیگر باز کند، و برنامه‌ای را با نام فارسی یا انگلیسی باز کند و لمس کند. دسترس‌پذیری همهٔ رویدادها را می‌شنود، فرزند قابل پیمایش را پیدا می‌کند و لمس طولانی دارد.
- `permissions_diagnostic` می‌گوید چه چیزی داده شده و قدم بعدی چیست. هنوز ورود به حساب جیمیل با API رسمی نیست.

## 0.1.0 — 2026-11-24

English

- First public build of Dastyar, an on-device assistant. You bring the API key.
- OpenAI-compatible providers, Gemini, Anthropic, and Cloudflare Workers AI, plus Custom / Proxy.
- Free, trial, and paid catalog with official links. GitHub Models is marked discontinued.
- Tool calls ask before SMS, calls, file writes, screen capture, and accessibility gestures.
- Persian by default, English switch, RTL layout, Vazirmatn.

فارسی

- نخستین انتشار عمومی دستیار، روی خود گوشی. کلید را شما می‌آورید.
- ارائه‌دهنده‌های سازگار با OpenAI، Gemini، Anthropic و Cloudflare Workers AI، به‌اضافهٔ سفارشی / پروکسی.
- فهرست رایگان، آزمایشی و پولی با پیوند رسمی. GitHub Models متوقف‌شده است.
- فراخوانی ابزار پیش از پیامک، تماس، نوشتن فایل، نماگرفت و حرکت دسترس‌پذیری تأیید می‌خواهد.
- فارسی پیش‌فرض است، انگلیسی سوییچ دارد، چیدمان راست‌به‌چپ است، قلم وزیرمتن.
