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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import app.dastyar.agent.data.AppLanguage
import app.dastyar.agent.data.Appearance
import app.dastyar.agent.ui.BackNavigation
import app.dastyar.agent.ui.DastyarTopBar
import app.dastyar.agent.ui.MenuNavigation
import app.dastyar.agent.ui.EmptyState
import app.dastyar.agent.ui.PrimaryButton
import app.dastyar.agent.ui.SecondaryButton
import app.dastyar.agent.ui.SectionHeader
import app.dastyar.agent.ui.SettingsRow
import app.dastyar.agent.ui.Space
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
    var page by rememberSaveable { mutableStateOf(0) }
    val pages = listOf(
        stringResource(R.string.onboarding_value_title) to stringResource(R.string.onboarding_value_body),
        stringResource(R.string.onboarding_privacy_title) to stringResource(R.string.onboarding_privacy_body),
    )
    fun choose(code: String) {
        onLanguage(code)
        vm.setLanguage(code)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.s24),
        verticalArrangement = Arrangement.spacedBy(Space.s16),
    ) {
        Box(
            Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
        }
        Text(stringResource(R.string.onboarding_kicker), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onboarding_language), style = MaterialTheme.typography.titleSmall)
        LanguageChips(state.languagePref, ::choose)
        SectionCard(title = pages[page].first, body = pages[page].second)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
            repeat(pages.size) { index ->
                Box(
                    Modifier
                        .size(if (index == page) 10.dp else 8.dp)
                        .background(
                            if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
            if (page > 0) SecondaryButton(stringResource(R.string.onboarding_back), { page -= 1 })
            if (page < pages.lastIndex) PrimaryButton(stringResource(R.string.onboarding_next), { page += 1 })
        }
        if (page == pages.lastIndex) {
            TextButton(onClick = onPermissions) { Text(stringResource(R.string.onboarding_permissions)) }
            PrimaryButton(stringResource(R.string.onboarding_add_key), { vm.finishOnboarding(); onDone(true) }, Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.onboarding_skip), { vm.finishOnboarding(); onDone(false) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LanguageChips(pref: String, onChoose: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
        FilterChip(selected = pref == AppLanguage.SYSTEM, onClick = { onChoose(AppLanguage.SYSTEM) }, label = { Text(stringResource(R.string.language_system)) })
        FilterChip(selected = pref == AppLanguage.ENGLISH, onClick = { onChoose(AppLanguage.ENGLISH) }, label = { Text(stringResource(R.string.language_english)) })
        FilterChip(selected = pref == AppLanguage.PERSIAN, onClick = { onChoose(AppLanguage.PERSIAN) }, label = { Text(stringResource(R.string.language_persian)) })
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
    onPrivacy: () -> Unit,
    onPickFolder: () -> Unit,
    onConnectors: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = state.lang
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DastyarTopBar(stringResource(R.string.settings_title), navigation = { MenuNavigation(onOpenMenu) })
        SectionHeader(stringResource(R.string.onboarding_language))
        LanguageChips(state.languagePref) { code ->
            onLanguage(code)
            vm.setLanguage(code)
        }
        SectionHeader(stringResource(R.string.settings_appearance))
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
            FilterChip(selected = state.themeMode == Appearance.SYSTEM, onClick = { vm.setThemeMode(Appearance.SYSTEM) }, label = { Text(stringResource(R.string.theme_system)) })
            FilterChip(selected = state.themeMode == Appearance.LIGHT, onClick = { vm.setThemeMode(Appearance.LIGHT) }, label = { Text(stringResource(R.string.theme_light)) })
            FilterChip(selected = state.themeMode == Appearance.DARK, onClick = { vm.setThemeMode(Appearance.DARK) }, label = { Text(stringResource(R.string.theme_dark)) })
        }
        Text(stringResource(R.string.settings_color), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
            FilterChip(selected = state.colorMode != Appearance.DYNAMIC, onClick = { vm.setColorMode(Appearance.BRAND) }, label = { Text(stringResource(R.string.color_brand)) })
            FilterChip(selected = state.colorMode == Appearance.DYNAMIC, onClick = { vm.setColorMode(Appearance.DYNAMIC) }, label = { Text(stringResource(R.string.color_dynamic)) })
        }
        Text(stringResource(R.string.color_dynamic_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsRow(title = t(lang, "توقف همهٔ ابزارها", "Pause all tools")) {
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
            Text(stringResource(R.string.perm_title))
        }
        OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_privacy))
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
fun RoutinesScreen(vm: SettingsViewModel, onExactAlarm: () -> Unit, onOpenMenu: () -> Unit) {
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
        DastyarTopBar(stringResource(R.string.routines_title), navigation = { MenuNavigation(onOpenMenu) })
        if (state.routines.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.empty_routines_title),
                body = stringResource(R.string.empty_routines_body),
            )
        }
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
fun AuditScreen(vm: SettingsViewModel, onExport: suspend (String) -> Unit, onBack: () -> Unit) {
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
        DastyarTopBar(stringResource(R.string.audit_title), navigation = { BackNavigation(onBack) })
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
        if (rows.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.empty_audit_title),
                body = stringResource(R.string.empty_audit_body),
            )
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
fun MemoriesScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = state.lang
    var fact by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DastyarTopBar(stringResource(R.string.memories_title), navigation = { BackNavigation(onBack) })
        if (state.memories.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.Notes,
                title = stringResource(R.string.empty_memories_title),
                body = stringResource(R.string.empty_memories_body),
            )
        }
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
        DastyarTopBar(t(lang, "افشای دسترس‌پذیری", "Accessibility disclosure"), navigation = { BackNavigation(onBack) })
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
