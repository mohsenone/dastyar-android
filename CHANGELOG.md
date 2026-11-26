# Changelog

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
