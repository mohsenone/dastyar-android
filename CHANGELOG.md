# Changelog

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
| `RECORD_AUDIO` | Voice input | Runtime dialog | App info | chat microphone |
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
