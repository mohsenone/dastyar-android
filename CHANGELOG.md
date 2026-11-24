# Changelog

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
