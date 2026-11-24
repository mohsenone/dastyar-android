package app.dastyar.agent.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.dastyar.agent.tools.AccessState
import app.dastyar.agent.tools.PermissionDiagnostics
import app.dastyar.agent.tools.RestrictedSettings
import app.dastyar.agent.ui.SectionCard
import app.dastyar.agent.ui.t

@Composable
fun PermissionsScreen(
    lang: String,
    safGranted: Boolean,
    onAction: (String) -> Unit,
    onAppInfo: () -> Unit,
    showOnboardingChoices: Boolean,
    onProviders: () -> Unit,
    onChat: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val report = remember(tick, safGranted, lang) { PermissionDiagnostics.inspect(context, safGranted) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t(lang, "اجازه‌ها", "Permissions"), style = MaterialTheme.typography.headlineSmall)
        Text(
            t(
                lang,
                "هر مورد را خودتان روشن می‌کنید. دستیار چیزی را پنهانی نمی‌گیرد. می‌توانید رد شوید و بعداً از تنظیمات برگردید.",
                "You turn each item on. Dastyar never grants them silently. You can skip and come back from Settings.",
            ),
        )
        if (report.restrictedLikely) {
            Text(
                t(
                    lang,
                    "روی اندروید ۱۳ و جدیدتر، نصب از بیرون فروشگاه تا «اجازه به تنظیمات محدود» پیامک، گزارش تماس، دسترس‌پذیری و اعلان را نمی‌دهد.",
                    "On Android 13 and later, an install from outside the store cannot receive SMS, call log, accessibility, or notification access until you allow restricted settings.",
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        SectionCard(
            title = t(lang, "اجازه به تنظیمات محدود", "Allow restricted settings"),
            body = RestrictedSettings.guideFa() + "\n\n" + RestrictedSettings.guideEn(),
            footer = {
                Button(onClick = onAppInfo, modifier = Modifier.fillMaxWidth()) {
                    Text(t(lang, "باز کردن اطلاعات برنامه", "Open app info"))
                }
            },
        )
        report.items.forEach { item ->
            val title = if (lang.startsWith("fa")) item.titleFa else item.titleEn
            val why = if (lang.startsWith("fa")) item.whyFa else item.whyEn
            val next = if (lang.startsWith("fa")) item.nextFa else item.nextEn
            SectionCard(
                title = "$title · ${stateLabel(lang, item.state)}",
                body = "$why\n$next",
                footer = {
                    if (item.state != AccessState.GRANTED) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onAction(item.id) }, modifier = Modifier.weight(1f)) {
                                Text(actionLabel(lang, item.id, item.state))
                            }
                            if (item.state == AccessState.MAYBE_RESTRICTED) {
                                OutlinedButton(onClick = onAppInfo, modifier = Modifier.weight(1f)) {
                                    Text(t(lang, "اطلاعات برنامه", "App info"))
                                }
                            }
                        }
                    }
                },
            )
        }
        if (showOnboardingChoices) {
            Button(onClick = onProviders, modifier = Modifier.fillMaxWidth()) {
                Text(t(lang, "ادامه: افزودن کلید", "Continue: add a key"))
            }
            OutlinedButton(onClick = onChat, modifier = Modifier.fillMaxWidth()) {
                Text(t(lang, "فعلاً رد شو و برو به گفتگو", "Skip for now and open chat"))
            }
        }
    }
}

private fun stateLabel(lang: String, state: AccessState): String = when (state) {
    AccessState.GRANTED -> t(lang, "داده شده", "Granted")
    AccessState.MISSING -> t(lang, "داده نشده", "Missing")
    AccessState.NEEDS_SETTINGS -> t(lang, "نیاز به تنظیمات", "Needs settings")
    AccessState.MAYBE_RESTRICTED -> t(lang, "شاید تنظیمات محدود مانع است", "Possibly blocked by restricted settings")
}

private fun actionLabel(lang: String, id: String, state: AccessState): String {
    if (id == "accessibility") return t(lang, "توضیح و روشن کردن", "Read disclosure and enable")
    if (state == AccessState.MAYBE_RESTRICTED) return t(lang, "دوباره تلاش کن", "Try again")
    if (state == AccessState.NEEDS_SETTINGS || id == "saf") return t(lang, "باز کردن", "Open")
    return t(lang, "اجازه", "Grant")
}
