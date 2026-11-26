package app.dastyar.agent.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dastyar.agent.data.AuditEntity
import app.dastyar.agent.tools.ToolCategories
import app.dastyar.agent.ui.SectionCard
import app.dastyar.agent.ui.t
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    vm: SettingsViewModel,
    onDone: (openProviders: Boolean) -> Unit,
    onLanguage: (String) -> Unit,
    onPermissions: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = state.lang
    var page by remember { mutableStateOf(0) }
    val pages = listOf(
        t(lang, "دستیار روی خود گوشی کار می‌کند. کلید را شما می‌آورید. سرور دستیار کلید، پیامک یا فایل شما را نمی‌بیند.", "Dastyar runs on this phone. You bring the key. No Dastyar server sees your key, messages, or files."),
        t(lang, "اعلان، پیامک، تماس، تقویم، فایل، دوربین، موقعیت و برنامه‌های دیگر فقط بعد از اجازهٔ شما در دسترس‌اند. اگر برنامه را از بیرون فروشگاه نصب کرده‌اید، در اندروید ۱۳ و جدیدتر اول «اجازه به تنظیمات محدود» را در اطلاعات برنامه روشن کنید.", "Notifications, SMS, calls, calendar, files, camera, location, and other apps are available only after you allow them. If you installed from outside the store, on Android 13 and later allow restricted settings in App info first."),
        t(lang, "فرستادن پیامک، تماس، پاک کردن فایل، یا لمس برنامهٔ دیگر همیشه دکمهٔ تأیید می‌خواهد. از اعلان یا تنظیمات می‌توانید همهٔ ابزارها را متوقف کنید.", "Sending a text, placing a call, deleting a file, or tapping another app always needs a confirm button. Pause every tool from the notification or from Settings."),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(t(lang, "دستیار", "Dastyar"), style = MaterialTheme.typography.headlineMedium)
        Text(t(lang, "عامل هوش مصنوعی روی دستگاه", "On-device AI agent"), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = lang.startsWith("fa"), onClick = { onLanguage("fa"); vm.setLanguage("fa") }, label = { Text("فارسی") })
            FilterChip(selected = !lang.startsWith("fa"), onClick = { onLanguage("en"); vm.setLanguage("en") }, label = { Text("English") })
        }
        SectionCard(title = "${page + 1} / ${pages.size}", body = pages[page])
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (page > 0) OutlinedButton(onClick = { page -= 1 }) { Text(t(lang, "قبلی", "Back")) }
            if (page < pages.lastIndex) {
                Button(onClick = { page += 1 }) { Text(t(lang, "بعدی", "Next")) }
            }
        }
        Button(onClick = onPermissions, modifier = Modifier.fillMaxWidth()) {
            Text(t(lang, "اجازه‌ها و تنظیمات محدود", "Permissions and restricted settings"))
        }
        Button(onClick = { vm.finishOnboarding(); onDone(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(t(lang, "افزودن یک کلید رایگان", "Add a free key"))
        }
        TextButton(onClick = { vm.finishOnboarding(); onDone(false) }) { Text(t(lang, "رفتن به گفتگو", "Skip to chat")) }
    }
}

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    versionName: String,
    onLanguage: (String) -> Unit,
    onProviders: () -> Unit,
    onAudit: () -> Unit,
    onMemories: () -> Unit,
    onDisclosure: () -> Unit,
    onPermissions: () -> Unit,
    onPickFolder: () -> Unit,
    onConnectors: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = state.lang
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t(lang, "تنظیمات", "Settings"), style = MaterialTheme.typography.headlineSmall)
        if (state.message != null) {
            TextButton(onClick = vm::clearMessage) { Text(state.message ?: "") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = lang.startsWith("fa"), onClick = { onLanguage("fa"); vm.setLanguage("fa") }, label = { Text("فارسی") })
            FilterChip(selected = !lang.startsWith("fa"), onClick = { onLanguage("en"); vm.setLanguage("en") }, label = { Text("English") })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(t(lang, "توقف همهٔ ابزارها", "Pause all tools"), modifier = Modifier.weight(1f))
            Switch(checked = state.paused, onCheckedChange = vm::setPaused)
        }
        Text(t(lang, "تأیید عمل‌ها", "Confirmation"), style = MaterialTheme.typography.titleMedium)
        Text(
            t(
                lang,
                "حالت خودکار هم فرستادن پیام، ایمیل، تماس، حذف، پرداخت و هر چیزی که از گوشی بیرون می‌رود را می‌پرسد. روالِ «بدون پرسش» تنها استثناست و پیش‌فرضش خاموش است.",
                "Auto still asks before a message, email, call, delete, payment, or anything that leaves the phone. An unattended routine is the only exception, and it is off by default.",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.confirmMode == "always", onClick = { vm.setConfirmMode("always") }, label = { Text(t(lang, "همیشه", "Always")) })
            FilterChip(selected = state.confirmMode != "always" && state.confirmMode != "auto", onClick = { vm.setConfirmMode("sensitive") }, label = { Text(t(lang, "حساس", "Sensitive")) })
            FilterChip(selected = state.confirmMode == "auto", onClick = { vm.setConfirmMode("auto") }, label = { Text(t(lang, "خودکار", "Auto")) })
        }
        Text(t(lang, "دسته‌های ابزار", "Tool categories"), style = MaterialTheme.typography.titleMedium)
        Text(
            t(lang, "دستهٔ خاموش به مدل داده نمی‌شود.", "A category that is off is omitted from the model."),
            style = MaterialTheme.typography.bodySmall,
        )
        CategoryToggles(lang, state.disabledCategories, vm::setCategoryEnabled)
        OutlinedTextField(
            value = state.disabledTools,
            onValueChange = vm::setDisabledTools,
            label = { Text(t(lang, "ابزارهای خاموش", "Disabled tools")) },
            supportingText = { Text(t(lang, "نام ابزارها با ویرگول. خالی یعنی همهٔ دسته‌های روشن.", "Tool names separated by commas. Empty means every enabled category.")) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onConnectors, modifier = Modifier.fillMaxWidth()) {
            Text(t(lang, "اتصال‌ها", "Connectors"))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(t(lang, "قفل زیست‌سنجی برای کلیدها", "Biometric gate for keys"))
                Text(
                    if (state.hardware) t(lang, "کلید سخت‌افزاری در دسترس است.", "A hardware-backed key is available.")
                    else t(lang, "کلید نرم‌افزاری Keystore. باز هم از پشتیبان کنار گذاشته می‌شود.", "Software-backed Keystore key. It is still excluded from backup."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = state.biometric, onCheckedChange = vm::setBiometric)
        }
        Button(onClick = onPermissions, modifier = Modifier.fillMaxWidth()) {
            Text(t(lang, "اجازه‌ها و تنظیمات محدود", "Permissions and restricted settings"))
        }
        Button(onClick = onProviders, modifier = Modifier.fillMaxWidth()) { Text(t(lang, "ارائه‌دهندگان", "Providers")) }
        OutlinedButton(onClick = onDisclosure, modifier = Modifier.fillMaxWidth()) {
            Text(t(lang, "دسترس‌پذیری و افشا", "Accessibility disclosure"))
        }
        OutlinedButton(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
            Text(t(lang, "انتخاب پوشه (SAF)", "Choose a folder (SAF)"))
        }
        if (!state.saf.isNullOrBlank()) {
            Text(t(lang, "پوشه ذخیره شد.", "A folder is saved."), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = state.allowlist,
            onValueChange = vm::setAllowlist,
            label = { Text(t(lang, "فهرست مجاز وب", "Web allowlist")) },
            supportingText = { Text(t(lang, "خالی یعنی هر https. هر خط یک میزبان.", "Empty allows any https host. One host per line.")) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        OutlinedButton(onClick = onMemories, modifier = Modifier.fillMaxWidth()) { Text(t(lang, "یادداشت‌ها", "Saved facts")) }
        OutlinedButton(onClick = onAudit, modifier = Modifier.fillMaxWidth()) { Text(t(lang, "گزارش ابزارها", "Tool audit log")) }
        OutlinedButton(onClick = vm::deleteAllKeys, modifier = Modifier.fillMaxWidth()) { Text(t(lang, "حذف همهٔ کلیدها", "Delete all keys")) }
        OutlinedButton(onClick = vm::deleteAllChats, modifier = Modifier.fillMaxWidth()) { Text(t(lang, "حذف همهٔ گفتگوها", "Delete all chats")) }
        SectionCard(
            title = t(lang, "مدل آفلاین", "Offline model"),
            body = t(
                lang,
                "این نسخه مدل Gemini Nano یا فایل GGUF را همراه ندارد. گفتگو با کلید خودتان کار می‌کند. مدل روی دستگاه را بعداً می‌توان در حافظهٔ برنامه گذاشت.",
                "This build does not bundle Gemini Nano or a GGUF file. Chat uses the key you add. A later build can load a model you place in app storage.",
            ),
        )
        SectionCard(
            title = t(lang, "حافظه", "Memory"),
            body = t(
                lang,
                "گفتگوها در Room می‌مانند. یادداشت‌هایی که مدل ذخیره می‌کند در همین گوشی است. جاسازی برداری در این نسخه نیست.",
                "Conversations stay in Room. Facts the model saves stay on this phone. Vector embeddings are not in this version.",
            ),
        )
        Text(versionName, style = MaterialTheme.typography.bodySmall)
        Text(vm.packageLabel(), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryToggles(lang: String, disabled: String, onChange: (String, Boolean) -> Unit) {
    val off = disabled.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolCategories.ALL.forEach { category ->
            val label = categoryLabel(lang, category)
            FilterChip(
                selected = category !in off,
                onClick = { onChange(category, category in off) },
                label = { Text(label) },
            )
        }
    }
}

private fun categoryLabel(lang: String, category: String): String = when (category) {
    "device" -> t(lang, "دستگاه", "Device")
    "messaging" -> t(lang, "پیام", "Messages")
    "phone" -> t(lang, "تلفن", "Phone")
    "files" -> t(lang, "فایل", "Files")
    "media" -> t(lang, "رسانه", "Media")
    "location" -> t(lang, "موقعیت", "Location")
    "accessibility" -> t(lang, "دسترس‌پذیری", "Accessibility")
    "web" -> t(lang, "وب", "Web")
    "memory" -> t(lang, "حافظه", "Memory")
    "routines" -> t(lang, "روال", "Routines")
    "apps" -> t(lang, "برنامه‌ها", "Apps")
    "connectors" -> t(lang, "اتصال‌ها", "Connectors")
    else -> category
}

@Composable
fun RoutinesScreen(vm: SettingsViewModel, onExactAlarm: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = state.lang
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("8") }
    var minute by remember { mutableStateOf("0") }
    var daily by remember { mutableStateOf(true) }
    var unattended by remember { mutableStateOf(false) }
    var trigger by remember { mutableStateOf("schedule") }
    var filter by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(t(lang, "روال‌ها", "Routines"), style = MaterialTheme.typography.headlineSmall)
        Text(
            t(lang, "روال در پس‌زمینه با یک خدمت پیش‌زمینه اجرا می‌شود و اعلان «دستیار در حال اجرای روال است» را نشان می‌دهد. زمان‌بندی، پیامک ورودی، یا یک اعلان می‌تواند آن را شروع کند. پیامک، تماس و خودکارسازی برنامه همچنان تأیید می‌خواهند مگر این کلید را روشن کنید. پیش‌فرض خاموش است. فرستادن، حذف و تماس حتی در حالت خودکار تأیید می‌خواهند مگر همین کلید روشن باشد.", "A routine runs in the background with a foreground service and the notification “Dastyar is running a routine”. A clock, an incoming SMS, or a notification can start it. SMS, calls, and other-app automation still ask unless you turn this on. The default is off. Sends, deletes, and calls still confirm in auto mode unless this switch is on."),
        )
        TextButton(onClick = onExactAlarm) { Text(t(lang, "اجازهٔ زنگ دقیق", "Allow exact alarms")) }
        state.routines.forEach { routine ->
            SectionCard(
                title = routine.title,
                body = "%s · %02d:%02d · %s\n%s\n%s".format(
                    triggerLabel(lang, routine.triggerKind),
                    routine.hour,
                    routine.minute,
                    if (routine.allowUnattended) t(lang, "بدون پرسش", "unattended") else t(lang, "با تأیید", "asks first"),
                    routine.triggerFilter.ifBlank { "" },
                    routine.prompt,
                ),
                footer = {
                    Row {
                        Text(t(lang, "روشن", "On"))
                        Switch(checked = routine.enabled, onCheckedChange = { vm.setRoutineEnabled(routine, it) })
                        TextButton(onClick = { vm.deleteRoutine(routine) }) { Text(t(lang, "حذف", "Delete")) }
                    }
                },
            )
        }
        OutlinedTextField(title, { title = it }, label = { Text(t(lang, "عنوان", "Title")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(prompt, { prompt = it }, label = { Text(t(lang, "دستور", "Prompt")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text(t(lang, "ساعت", "Hour")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text(t(lang, "دقیقه", "Minute")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = trigger == "schedule", onClick = { trigger = "schedule" }, label = { Text(t(lang, "ساعت", "Clock")) })
            FilterChip(selected = trigger == "sms", onClick = { trigger = "sms" }, label = { Text(t(lang, "پیامک", "SMS")) })
            FilterChip(selected = trigger == "notification", onClick = { trigger = "notification" }, label = { Text(t(lang, "اعلان", "Notification")) })
        }
        if (trigger != "schedule") {
            OutlinedTextField(
                filter,
                { filter = it },
                label = { Text(t(lang, "فیلتر (اختیاری)", "Filter (optional)")) },
                supportingText = { Text(t(lang, "فقط اگر متن پیامک یا اعلان این عبارت را داشته باشد.", "Runs only when the SMS or notification contains this text.")) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(daily, { daily = it })
            Text(if (trigger == "schedule") t(lang, "هر روز", "Every day") else t(lang, "ادامه بدهد", "Keep listening"))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(unattended, { unattended = it })
            Text(t(lang, "بدون پرسش عمل کند (خطرناک)", "Act without asking (dangerous)"))
        }
        Button(onClick = {
            vm.addRoutine(title, prompt, hour.toIntOrNull() ?: 8, minute.toIntOrNull() ?: 0, daily, unattended, trigger, filter)
            title = ""
            prompt = ""
            filter = ""
            unattended = false
        }) { Text(t(lang, "افزودن", "Add")) }
    }
}

@Composable
fun AuditScreen(vm: SettingsViewModel, onExport: suspend (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = state.lang
    val scope = rememberCoroutineScope()
    var outcome by remember { mutableStateOf("all") }
    val rows = state.audit.filter { row ->
        val denied = isDenied(row)
        when (outcome) {
            "ok" -> row.success && !denied
            "fail" -> !row.success && !denied
            "denied" -> denied
            else -> true
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(t(lang, "گزارش ابزارها", "Tool audit"), style = MaterialTheme.typography.headlineSmall)
        Text(
            t(lang, "زمان، ابزار، نتیجه و آرگومان‌های سانسورشده. رمز در گزارش نوشته نمی‌شود.", "Time, tool, result, and redacted arguments. Tokens are not written here."),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = state.auditTool,
            onValueChange = vm::setAuditTool,
            label = { Text(t(lang, "نام ابزار", "Tool name")) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.auditSuccess == null, onClick = { vm.setAuditSuccess(null) }, label = { Text(t(lang, "همه", "All")) })
            FilterChip(selected = state.auditSuccess == true, onClick = { vm.setAuditSuccess(true) }, label = { Text(t(lang, "موفق", "Ok")) })
            FilterChip(selected = state.auditSuccess == false, onClick = { vm.setAuditSuccess(false) }, label = { Text(t(lang, "ناموفق", "Failed")) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = outcome == "all", onClick = { outcome = "all" }, label = { Text(t(lang, "نتیجه: همه", "Outcome: all")) })
            FilterChip(selected = outcome == "ok", onClick = { outcome = "ok" }, label = { Text(t(lang, "انجام شد", "Done")) })
            FilterChip(selected = outcome == "denied", onClick = { outcome = "denied" }, label = { Text(t(lang, "رد شد", "Denied")) })
            FilterChip(selected = outcome == "fail", onClick = { outcome = "fail" }, label = { Text(t(lang, "خطا", "Error")) })
        }
        Row {
            TextButton(onClick = { scope.launch { onExport(vm.exportAudit()) } }) { Text(t(lang, "خروجی", "Export")) }
            TextButton(onClick = vm::deleteAudit) { Text(t(lang, "پاک کردن", "Delete")) }
        }
        rows.take(200).forEach { row ->
            Text(
                "${auditTime(row.createdAt)} · ${row.toolName} · ${auditOutcome(lang, row)} · ${row.risk}\n${row.argsSummary}\n${row.resultSummary}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun isDenied(row: AuditEntity): Boolean {
    val text = row.resultSummary
    return text.contains("denied", true) || text.contains("رد شد") || text.contains("رد کرد")
}

private fun auditOutcome(lang: String, row: AuditEntity): String = when {
    isDenied(row) -> t(lang, "رد شد", "Denied")
    row.success -> t(lang, "انجام شد", "Done")
    else -> t(lang, "ناموفق", "Failed")
}

private fun auditTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))

private fun triggerLabel(lang: String, kind: String): String = when (kind) {
    "sms" -> t(lang, "پیامک", "SMS")
    "notification" -> t(lang, "اعلان", "Notification")
    else -> t(lang, "ساعت", "Clock")
}

@Composable
fun MemoriesScreen(vm: SettingsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = state.lang
    var fact by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(t(lang, "یادداشت‌ها", "Saved facts"), style = MaterialTheme.typography.headlineSmall)
        state.memories.forEach { memory ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(memory.fact, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.deleteMemory(memory.id) }) { Text(t(lang, "حذف", "Delete")) }
            }
        }
        OutlinedTextField(fact, { fact = it }, label = { Text(t(lang, "یادداشت تازه", "New fact")) }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.addMemory(fact); fact = "" }) { Text(t(lang, "ذخیره", "Save")) }
    }
}

@Composable
fun DisclosureScreen(vm: SettingsViewModel, onOpenSettings: () -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = state.lang
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t(lang, "افشای دسترس‌پذیری", "Accessibility disclosure"), style = MaterialTheme.typography.headlineSmall)
        Text(t(lang, "خدمت دسترس‌پذیری دستیار می‌تواند متن و ساختار صفحهٔ برنامه‌های دیگر را بخواند و ضربه، پیمایش و نوشتن انجام دهد. این یعنی کنترل برنامه‌های دیگر، از جمله دیدن نوشته‌ای که روی صفحه است.", "Dastyar's accessibility service can read the text and structure of other apps and can tap, scroll, and type. That means it can control other apps, including content visible on screen."))
        Text(t(lang, "هر حرکتی که وضعیت برنامهٔ دیگر را عوض کند خطرناک است و قبل از اجرا تأیید می‌خواهد. اگر مدل چند گام را با هم طرح کند، یک تأیید با فهرست گام‌ها نشان داده می‌شود.", "Every gesture that changes another app is dangerous and needs confirmation. If the model planned several steps, one confirmation lists them."))
        Text(t(lang, "از اعلان «توقف ابزارها» می‌توانید خودکارسازی را فوراً متوقف کنید. برای خاموش کردن همیشگی: تنظیمات سیستم، دسترس‌پذیری، دستیار، خاموش.", "Pause automation immediately from the “Pause tools” notification. To turn it off for good: system Settings, Accessibility, Dastyar, off."))
        Text(t(lang, "این خدمت رمز را پنهانی نمی‌خواند و به سرور دستیار نمی‌فرستد. متن صفحه فقط اگر خودتان بخواهید، به ارائه‌دهنده‌ای که کلیدش را گذاشته‌اید می‌رود.", "The service does not quietly steal passwords or send them to a Dastyar server. Screen text goes to the provider whose key you saved only when you ask."))
        Button(onClick = { vm.markA11yDisclosed(); onOpenSettings() }, modifier = Modifier.fillMaxWidth()) {
            Text(t(lang, "فهمیدم؛ باز کردن تنظیمات", "I understand; open settings"))
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(t(lang, "حالا نه", "Not now")) }
    }
}
