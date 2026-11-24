package app.dastyar.agent.providers

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import app.dastyar.agent.ui.SectionCard
import app.dastyar.agent.ui.t
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(vm: ProvidersViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = state.lang
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t(lang, "ارائه‌دهندگان", "Providers"), style = MaterialTheme.typography.headlineSmall)
        Text(
            t(lang, "کلید مال شماست و روی همین گوشی می‌ماند. دستیار هزینهٔ مدل را صورتحساب نمی‌کند.", "The key is yours and stays on this phone. Dastyar does not bill model usage."),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.freeTab, onClick = { vm.setFreeTab(true) }, label = { Text(t(lang, "رایگان / آزمایشی", "Free / trial")) })
            FilterChip(selected = !state.freeTab, onClick = { vm.setFreeTab(false) }, label = { Text(t(lang, "کلید پولی", "Paid keys")) })
        }
        if (state.notice != null) {
            Text(state.notice ?: "", color = MaterialTheme.colorScheme.primary)
        }
        if (state.saved.isNotEmpty()) {
            Text(t(lang, "ذخیره‌شده", "Saved"), style = MaterialTheme.typography.titleMedium)
            state.saved.forEach { entity ->
                SectionCard(
                    title = entity.label + if (entity.isDefault) t(lang, " · پیش‌فرض", " · default") else "",
                    body = "${entity.kind}\n${entity.model}",
                    footer = {
                        Row {
                            TextButton(onClick = { vm.setDefault(entity.id) }) { Text(t(lang, "پیش‌فرض", "Default")) }
                            TextButton(onClick = { vm.edit(entity) }) { Text(t(lang, "ویرایش", "Edit")) }
                            TextButton(onClick = { vm.delete(entity.id) }) { Text(t(lang, "حذف", "Delete")) }
                        }
                    },
                )
            }
        }
        Catalog.forTab(state.freeTab).forEach { card ->
            val badge = if (lang.startsWith("fa")) card.badgeFa else card.badgeEn
            val summary = if (lang.startsWith("fa")) card.summaryFa else card.summaryEn
            SectionCard(
                title = card.name,
                body = "$badge — $summary",
                footer = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!card.signupUrl.isNullOrBlank()) {
                            TextButton(onClick = { openLink(context, card.signupUrl) }) { Text(t(lang, "ثبت‌نام", "Sign up")) }
                        }
                        if (!card.docsUrl.isNullOrBlank()) {
                            TextButton(onClick = { openLink(context, card.docsUrl) }) { Text(t(lang, "مستندات", "Docs")) }
                        }
                        if (!card.discontinued && card.needsKey) {
                            Button(onClick = { vm.openCard(card) }) { Text(t(lang, "افزودن کلید", "Add key")) }
                        }
                    }
                },
            )
        }
        Text(
            if (lang.startsWith("fa")) Catalog.FOOTNOTE_FA else Catalog.FOOTNOTE_EN,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            if (lang.startsWith("fa")) Catalog.PROXY_FA else Catalog.PROXY_EN,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    val form = state.form
    if (form != null) {
        val card = Catalog.cards.first { it.id == form.cardId }
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = vm::dismiss, sheetState = sheet) {
            KeySheet(card, form, state, vm)
        }
    }
}

@Composable
private fun KeySheet(card: ProviderCard, form: KeyForm, state: ProvidersUi, vm: ProvidersViewModel) {
    val lang = state.lang
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stepsOpen by remember(card.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(card.name, style = MaterialTheme.typography.titleLarge)
        AssistChip(onClick = {}, label = { Text(if (lang.startsWith("fa")) card.badgeFa else card.badgeEn) })
        Text(if (lang.startsWith("fa")) card.limitsFa else card.limitsEn, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { stepsOpen = !stepsOpen }) {
            Text(if (stepsOpen) t(lang, "بستن گام‌ها", "Hide steps") else t(lang, "چطور کلید بگیرم", "How to get a key"))
        }
        if (stepsOpen) {
            (if (lang.startsWith("fa")) card.stepsFa else card.stepsEn).forEachIndexed { index, step ->
                Text("${index + 1}. $step")
            }
        }
        if (!card.signupUrl.isNullOrBlank()) {
            OutlinedButton(onClick = { openLink(context, card.signupUrl) }) { Text(t(lang, "باز کردن صفحهٔ رسمی", "Open official page")) }
        }
        OutlinedTextField(
            value = form.label,
            onValueChange = { value -> vm.update { it.copy(label = value) } },
            label = { Text(t(lang, "نام", "Label")) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = form.model,
            onValueChange = { value -> vm.update { it.copy(model = value) } },
            label = { Text(t(lang, "شناسهٔ مدل", "Model id")) },
            supportingText = { Text(t(lang, "اگر بازنشسته شده، شناسهٔ فعلی مستندات را بگذارید.", "If this id was retired, paste the current one from the docs.")) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = form.baseUrl,
            onValueChange = { value -> vm.update { it.copy(baseUrl = value) } },
            label = { Text(t(lang, "آدرس پایه", "Base URL")) },
            enabled = card.editableBase,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (card.needsAccountId) {
            OutlinedTextField(
                value = form.accountId,
                onValueChange = { value -> vm.update { it.copy(accountId = value) } },
                label = { Text(t(lang, "شناسهٔ حساب", "Account ID")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = if (form.keyDirty) form.apiKey else if (state.reveal) form.apiKey else form.keyHint,
            onValueChange = vm::onKeyChange,
            label = { Text(t(lang, "کلید API", "API key")) },
            visualTransformation = if (state.reveal || form.keyDirty) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                if (focus.isFocused && !form.keyDirty) {
                    scope.launch { vm.tryReveal() }
                } else if (!focus.isFocused) {
                    vm.hideKey()
                }
            },
            singleLine = true,
        )
        if (card.id == "custom") {
            OutlinedTextField(
                value = form.headers,
                onValueChange = { value -> vm.update { it.copy(headers = value) } },
                label = { Text(t(lang, "سرآیند اضافه", "Extra headers")) },
                supportingText = { Text("Name: Value") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = form.makeDefault, onCheckedChange = { checked -> vm.update { it.copy(makeDefault = checked) } })
            Text(t(lang, "پیش‌فرض گفتگو", "Default for chat"))
        }
        if (state.testMessage != null) {
            Text(state.testMessage ?: "")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::test, enabled = !state.testing) {
                Text(if (state.testing) t(lang, "در حال آزمایش…", "Testing…") else t(lang, "آزمایش اتصال", "Test connection"))
            }
            Button(onClick = vm::save) { Text(t(lang, "ذخیره", "Save")) }
        }
        Text(
            t(lang, "کلید در گزارش، پشتیبان، یا تصویر این صفحه ذخیره نمی‌شود.", "The key is not written to logs, backups, or screenshots of this screen."),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

private fun openLink(context: android.content.Context, url: String) {
    val uri = Uri.parse(url)
    val tabs = CustomTabsIntent.Builder().build()
    try {
        tabs.launchUrl(context, uri)
    } catch (_: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
