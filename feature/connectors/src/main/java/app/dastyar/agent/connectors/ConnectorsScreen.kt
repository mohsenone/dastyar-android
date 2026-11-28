package app.dastyar.agent.connectors

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.res.stringResource
import app.dastyar.agent.connectors.R
import app.dastyar.agent.ui.BackNavigation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.dastyar.agent.ui.DastyarSnackbarHost
import app.dastyar.agent.ui.DastyarTopBar
import app.dastyar.agent.ui.EmptyState
import app.dastyar.agent.ui.StatusChip
import app.dastyar.agent.ui.StatusTone
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dastyar.agent.ui.SectionCard
import app.dastyar.agent.ui.t
import kotlinx.coroutines.launch

@Composable
fun ConnectorsScreen(
    onPermissions: () -> Unit,
    onBack: () -> Unit,
    vm: ConnectorsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = state.lang
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onGoogleActivity(result.data)
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.clearMessage()
    }
    val anyConnected = state.rows.any { it.status == LinkStatus.CONNECTED }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DastyarTopBar(
            stringResource(R.string.connector_title),
            navigation = { BackNavigation(onBack) },
        )
        if (state.loadError || state.unreadable) {
            SectionCard(
                title = stringResource(R.string.connector_screen_error_title),
                body = stringResource(R.string.connector_screen_error_body),
                footer = {
                    Button(onClick = vm::retry) { Text(stringResource(R.string.connector_retry)) }
                },
            )
        }
        Text(
            t(
                lang,
                "هر اتصال خاموش، ابزارش را به مدل نمی‌دهد. «متصل» فقط بعد از ورود یا آزمایش واقعی است. رمزها در Keystore می‌مانند و در گزارش نوشته نمی‌شوند.",
                "A connector that is off does not add tools. Connected means a real sign-in or a successful test. Secrets stay in the Keystore and are not written to the log.",
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.rows.isEmpty()) {
            Text(
                stringResource(R.string.connector_loading),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (!anyConnected) {
            EmptyState(
                icon = Icons.Outlined.Hub,
                title = t(lang, "هنوز چیزی متصل نیست", "Nothing connected yet"),
                body = t(lang, "یک اتصال را روشن کنید و آزمایش کنید. اتصال خاموش ابزاری اضافه نمی‌کند.", "Turn one on and test it. A connector that is off adds no tools."),
            )
        }
        OutlinedButton(onClick = onPermissions, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.connector_permissions))
        }
        CatalogOrError(state, ConnectorIds.GOOGLE, "Google") {
        GoogleCard(state, vm, lang) { clientId, redirect, secret, extra ->
            scope.launch {
                vm.prepareGoogle(clientId, redirect, secret, extra)?.let { googleLauncher.launch(it) }
            }
        }
        }
        CatalogOrError(state, ConnectorIds.GITHUB, "GitHub") {
        TokenCatalogCard(state, lang, vm, ConnectorIds.GITHUB, "GitHub", t(lang, "توکن شخصی کلاسیک یا fine-grained. ساختن issue و نظر تأیید می‌خواهد.", "A classic or fine-grained personal access token. Creating an issue or comment asks first."), "https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens", "https://github.com/settings/tokens")
        }
        CatalogOrError(state, ConnectorIds.TELEGRAM, "Telegram Bot") {
        TokenCatalogCard(state, lang, vm, ConnectorIds.TELEGRAM, "Telegram Bot", t(lang, "این API ربات است، نه کنترل خاموش حساب شخصی. توکن را از @BotFather بگیرید. ارسال تأیید می‌خواهد.", "This is the bot API, not silent control of your personal account. Get the token from @BotFather. Sending asks first."), "https://core.telegram.org/bots/api", "https://t.me/BotFather")
        }
        CatalogOrError(state, ConnectorIds.NOTION, "Notion") {
        TokenCatalogCard(state, lang, vm, ConnectorIds.NOTION, "Notion", t(lang, "توکن یکپارچه‌سازی. صفحه را با همان integration به اشتراک بگذارید. نوشتن تأیید می‌خواهد.", "An integration token. Share the page with that integration. Writes ask first."), "https://developers.notion.com/docs/create-a-notion-integration", "https://www.notion.so/my-integrations")
        }
        CatalogOrError(state, ConnectorIds.WEBHOOK, "Webhook") { WebhookCard(state, vm, lang) }
        CatalogOrError(state, ConnectorIds.SEARCH, "Search") { SearchCard(state, vm, lang) }
        CatalogOrError(state, ConnectorIds.RSS, "RSS") { RssCard(state, vm, lang) }
        CatalogOrError(state, ConnectorIds.WEATHER, "Weather") { WeatherCard(state, vm, lang) }
        CatalogOrError(state, ConnectorIds.MCP, "MCP") { McpCard(state, vm, lang) }
        CatalogOrError(state, ConnectorIds.SHIZUKU, "Shizuku") {
            ShizukuCard(state, vm, lang, signingSha1(context.packageManager, context.packageName))
        }
    }
    DastyarSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun CatalogOrError(
    state: ConnectorsUi,
    id: String,
    title: String,
    content: @Composable () -> Unit,
) {
    val row = connectorRow(state.rows, id)
    if (row?.broken == true) {
        SectionCard(title = title, body = stringResource(R.string.connector_card_error))
    } else {
        content()
    }
}

@Composable
private fun GoogleCard(
    state: ConnectorsUi,
    vm: ConnectorsViewModel,
    lang: String,
    onSignIn: (String, String, String, Boolean) -> Unit,
) {
    val row = connectorRow(state.rows, ConnectorIds.GOOGLE) ?: return
    val context = LocalContext.current
    var clientId by remember(state.snap.googleClientId) { mutableStateOf(state.snap.googleClientId) }
    var redirect by remember(state.snap.googleRedirect) { mutableStateOf(state.snap.googleRedirect) }
    var secret by remember { mutableStateOf("") }
    var extra by remember(state.snap.googleExtraScopes) { mutableStateOf(state.snap.googleExtraScopes) }
    val sha = remember { signingSha1(context.packageManager, context.packageName) }
    SectionCard(
        title = "Google",
        badge = { LinkBadge(lang, row.status) },
        body = t(
            lang,
            "ورود با OAuth و AppAuth (Custom Tabs) و PKCE. شناسهٔ کلاینت را خودتان در Google Cloud می‌سازید. درایو و مخاطبین اختیاری‌اند و ممکن است تأیید گوگل بخواهند. اگر scope داده نشود، همان ابزار خطای API را برمی‌گرداند.\nبسته: app.dastyar.agent\nSHA-1 این نصب: $sha\nنشانی بازگشت: ${OAuthRedirect.DEFAULT}",
            "Sign-in uses OAuth, AppAuth (Custom Tabs), and PKCE. You create the client id in Google Cloud. Drive and Contacts are optional and may need Google verification. If a scope was not granted, that tool returns the API error.\nPackage: app.dastyar.agent\nSHA-1 of this install: $sha\nRedirect: ${OAuthRedirect.DEFAULT}",
        ),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t(
                        lang,
                        "در Google Cloud یک کلاینت OAuth بسازید. اگر نوع Android است، بسته و SHA-1 نسخهٔ انتشار را بگذارید. اگر کنسول نشانی بازگشت سفارشی را فقط برای کلاینت وب می‌پذیرد، همان نشانی بالا را اضافه کنید. gmail.send و Drive و Contacts برای برنامه‌های تأییدنشده هشدار نشان می‌دهند. سهمیهٔ رایگان ابدی ادعا نشده است.",
                        "In Google Cloud, create an OAuth client. For an Android client, set package app.dastyar.agent and the release SHA-1. If the console only accepts a custom redirect on a Web client, add the redirect above. gmail.send, Drive, and Contacts show a warning on unverified apps. No forever-free claim.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t(lang, "روشن", "On"), modifier = Modifier.weight(1f))
                    Switch(checked = ConnectorIds.GOOGLE in state.snap.enabled, onCheckedChange = { vm.setEnabled(ConnectorIds.GOOGLE, it) })
                }
                OutlinedTextField(clientId, { clientId = it }, label = { Text("Client ID") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(redirect, { redirect = it }, label = { Text("Redirect URI") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    secret,
                    { secret = it },
                    label = { Text(t(lang, "رمز کلاینت، اختیاری", "Client secret, optional")) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = extra, onCheckedChange = { extra = it })
                    Text(t(lang, "درایو و مخاطبین هم درخواست شود", "Also request Drive and Contacts"), modifier = Modifier.padding(start = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        vm.saveGoogle(clientId, redirect, extra)
                        onSignIn(clientId, redirect, secret, extra)
                    }) { Text(t(lang, "ورود گوگل", "Sign in with Google")) }
                    OutlinedButton(onClick = { vm.test(ConnectorIds.GOOGLE) }) { Text(t(lang, "آزمایش", "Test")) }
                }
                TextButton(onClick = { open(context, "https://console.cloud.google.com/apis/credentials") }) {
                    Text(t(lang, "کنسول گوگل", "Google Cloud Console"))
                }
                TextButton(onClick = { open(context, "https://developers.google.com/identity/protocols/oauth2") }) {
                    Text(t(lang, "مستندات OAuth", "OAuth docs"))
                }
            }
        },
    )
}

@Composable
private fun TokenCatalogCard(
    state: ConnectorsUi,
    lang: String,
    vm: ConnectorsViewModel,
    id: String,
    title: String,
    body: String,
    docs: String,
    signup: String,
) {
    val row = connectorRow(state.rows, id) ?: return
    TokenCard(
        lang = lang,
        title = title,
        body = body,
        docs = docs,
        signup = signup,
        row = row,
        hasSecret = id in state.secrets,
        onToggle = { vm.setEnabled(id, it) },
        onSave = { vm.saveSecret(id, it) },
        onClear = { vm.clearSecret(id) },
        onTest = { vm.test(id) },
    )
}

@Composable
private fun TokenCard(
    lang: String,
    title: String,
    body: String,
    docs: String,
    signup: String,
    row: ConnectorRow,
    hasSecret: Boolean,
    onToggle: (Boolean) -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onTest: () -> Unit,
) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    SectionCard(
        title = title,
        badge = { LinkBadge(lang, row.status) },
        body = body + if (hasSecret) t(lang, "\nرمز ذخیره شده است.", "\nA secret is saved.") else t(lang, "\nهنوز رمزی نیست.", "\nNo secret yet."),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t(lang, "روشن", "On"), modifier = Modifier.weight(1f))
                    Switch(checked = row.status != LinkStatus.OFF, onCheckedChange = onToggle)
                }
                OutlinedTextField(
                    token,
                    { token = it },
                    label = { Text(t(lang, "توکن", "Token")) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(token); token = "" }) { Text(t(lang, "ذخیره", "Save")) }
                    OutlinedButton(onClick = onTest) { Text(t(lang, "آزمایش", "Test")) }
                    TextButton(onClick = onClear) { Text(t(lang, "حذف رمز", "Clear")) }
                }
                Row {
                    TextButton(onClick = { open(context, signup) }) { Text(t(lang, "ثبت‌نام / ساخت", "Sign up")) }
                    TextButton(onClick = { open(context, docs) }) { Text(t(lang, "مستندات", "Docs")) }
                }
            }
        },
    )
}

@Composable
private fun WebhookCard(state: ConnectorsUi, vm: ConnectorsViewModel, lang: String) {
    val row = connectorRow(state.rows, ConnectorIds.WEBHOOK) ?: return
    var url by remember(state.snap.webhookUrl) { mutableStateOf(state.snap.webhookUrl) }
    var header by remember(state.snap.webhookHeader) { mutableStateOf(state.snap.webhookHeader) }
    var secret by remember { mutableStateOf("") }
    SectionCard(
        title = "Webhook",
        badge = { LinkBadge(lang, row.status) },
        body = t(lang, "درخواست HTTP تأییدشده. https آزاد است. http فقط اگر میزبان در فهرست مجاز وب باشد.", "A confirmed HTTP request. https is allowed. http only when the host is on the web allowlist."),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t(lang, "روشن", "On"), modifier = Modifier.weight(1f))
                    Switch(checked = ConnectorIds.WEBHOOK in state.snap.enabled, onCheckedChange = { vm.setEnabled(ConnectorIds.WEBHOOK, it) })
                }
                OutlinedTextField(url, { url = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(header, { header = it }, label = { Text(t(lang, "نام سرآیند", "Header name")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(secret, { secret = it }, label = { Text(t(lang, "رمز سرآیند، اختیاری", "Header secret, optional")) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    vm.saveWebhook(url, header)
                    if (secret.isNotBlank()) vm.saveSecret(ConnectorIds.WEBHOOK, secret)
                    secret = ""
                }) { Text(t(lang, "ذخیره", "Save")) }
            }
        },
    )
}

@Composable
private fun SearchCard(state: ConnectorsUi, vm: ConnectorsViewModel, lang: String) {
    val row = connectorRow(state.rows, ConnectorIds.SEARCH) ?: return
    val context = LocalContext.current
    var kind by remember(state.snap.searchKind) { mutableStateOf(state.snap.searchKind) }
    var base by remember(state.snap.searxngBase) { mutableStateOf(state.snap.searxngBase) }
    var tavily by remember { mutableStateOf("") }
    var brave by remember { mutableStateOf("") }
    SectionCard(
        title = t(lang, "جستجوی وب", "Web search"),
        badge = { LinkBadge(lang, row.status) },
        body = t(lang, "Tavily یا Brave با کلید خودتان، یا یک SearXNG. سقف رایگان اینجا ساخته نشده است. web_fetch برای صفحه‌ای که انتخاب شده می‌ماند.", "Tavily or Brave with your own key, or a SearXNG base URL. This app does not invent a free quota. web_fetch still opens a page you already chose."),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t(lang, "روشن", "On"), modifier = Modifier.weight(1f))
                    Switch(checked = ConnectorIds.SEARCH in state.snap.enabled, onCheckedChange = { vm.setEnabled(ConnectorIds.SEARCH, it) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(kind == "tavily", { kind = "tavily" }, { Text("Tavily") })
                    FilterChip(kind == "brave", { kind = "brave" }, { Text("Brave") })
                    FilterChip(kind == "searxng", { kind = "searxng" }, { Text("SearXNG") })
                }
                OutlinedTextField(tavily, { tavily = it }, label = { Text("Tavily key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(brave, { brave = it }, label = { Text("Brave key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(base, { base = it }, label = { Text("SearXNG base URL") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    vm.saveSearch(kind, base)
                    if (tavily.isNotBlank()) vm.saveSecret("tavily", tavily)
                    if (brave.isNotBlank()) vm.saveSecret("brave", brave)
                    tavily = ""
                    brave = ""
                }) { Text(t(lang, "ذخیره", "Save")) }
                OutlinedButton(onClick = { vm.test(ConnectorIds.SEARCH) }) { Text(t(lang, "آزمایش", "Test")) }
                Row {
                    TextButton(onClick = { open(context, "https://tavily.com") }) { Text("Tavily") }
                    TextButton(onClick = { open(context, "https://brave.com/search/api/") }) { Text("Brave") }
                    TextButton(onClick = { open(context, "https://docs.searxng.org/") }) { Text("SearXNG") }
                }
            }
        },
    )
}

@Composable
private fun RssCard(state: ConnectorsUi, vm: ConnectorsViewModel, lang: String) {
    val row = connectorRow(state.rows, ConnectorIds.RSS) ?: return
    var text by remember(state.snap.rss) { mutableStateOf(state.snap.rss.joinToString("\n")) }
    SectionCard(
        title = "RSS",
        badge = { LinkBadge(lang, row.status) },
        body = t(lang, "هر خط یک نشانی خوراک. رمز لازم نیست.", "One feed URL per line. No secret."),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t(lang, "روشن", "On"), modifier = Modifier.weight(1f))
                    Switch(checked = ConnectorIds.RSS in state.snap.enabled, onCheckedChange = { vm.setEnabled(ConnectorIds.RSS, it) })
                }
                OutlinedTextField(text, { text = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Row {
                    Button(onClick = { vm.saveRss(text) }) { Text(t(lang, "ذخیره", "Save")) }
                    OutlinedButton(onClick = { vm.test(ConnectorIds.RSS) }) { Text(t(lang, "آزمایش", "Test")) }
                }
            }
        },
    )
}

@Composable
private fun WeatherCard(state: ConnectorsUi, vm: ConnectorsViewModel, lang: String) {
    val row = connectorRow(state.rows, ConnectorIds.WEATHER) ?: return
    val context = LocalContext.current
    var kind by remember(state.snap.weatherKind) { mutableStateOf(state.snap.weatherKind) }
    var key by remember { mutableStateOf("") }
    SectionCard(
        title = t(lang, "هوا", "Weather"),
        badge = { LinkBadge(lang, row.status) },
        body = t(lang, "Open-Meteo کلید نمی‌خواهد. OpenWeather فقط اگر کلید خودتان را بگذارید. قیمت رسمی اینجا نوشته نشده است.", "Open-Meteo needs no key. OpenWeather is used only with a key you provide. Official prices are not stated here."),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t(lang, "روشن", "On"), modifier = Modifier.weight(1f))
                    Switch(checked = ConnectorIds.WEATHER in state.snap.enabled, onCheckedChange = { vm.setEnabled(ConnectorIds.WEATHER, it) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(kind == "open-meteo", { kind = "open-meteo" }, { Text("Open-Meteo") })
                    FilterChip(kind == "openweather", { kind = "openweather" }, { Text("OpenWeather") })
                }
                OutlinedTextField(key, { key = it }, label = { Text("OpenWeather key") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    vm.saveWeather(kind)
                    if (key.isNotBlank()) vm.saveSecret("openweather", key)
                    key = ""
                }) { Text(t(lang, "ذخیره", "Save")) }
                OutlinedButton(onClick = { vm.test(ConnectorIds.WEATHER) }) { Text(t(lang, "آزمایش", "Test")) }
                Row {
                    TextButton(onClick = { open(context, "https://open-meteo.com/") }) { Text("Open-Meteo") }
                    TextButton(onClick = { open(context, "https://openweathermap.org/api") }) { Text("OpenWeather") }
                }
            }
        },
    )
}

@Composable
private fun McpCard(state: ConnectorsUi, vm: ConnectorsViewModel, lang: String) {
    val row = connectorRow(state.rows, ConnectorIds.MCP) ?: return
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("auto") }
    var token by remember { mutableStateOf("") }
    SectionCard(
        title = "MCP",
        badge = { LinkBadge(lang, row.status) },
        body = t(lang, "سرور MCP را با Streamable HTTP اضافه کنید. اگر سرور هنوز SSE قدیمی است، حمل‌ونقل sse یا auto را بگذارید. ابزارها با نام mcp_<سرور>_<ابزار> ثبت می‌شوند. توکن Bearer در Keystore است.", "Add an MCP server with Streamable HTTP. If the server still uses the older SSE transport, choose sse or auto. Tools are registered as mcp_<server>_<tool>. The bearer token stays in the Keystore."),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t(lang, "روشن", "On"), modifier = Modifier.weight(1f))
                    Switch(checked = ConnectorIds.MCP in state.snap.enabled, onCheckedChange = { vm.setEnabled(ConnectorIds.MCP, it) })
                }
                state.snap.mcp.forEach { server ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${server.name}\n${server.url} · ${server.transport}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.deleteMcp(server.id) }) { Text(t(lang, "حذف", "Delete")) }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text(t(lang, "نام", "Name")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(transport == "auto", { transport = "auto" }, { Text("auto") })
                    FilterChip(transport == "http", { transport = "http" }, { Text("HTTP") })
                    FilterChip(transport == "sse", { transport = "sse" }, { Text("SSE") })
                }
                OutlinedTextField(token, { token = it }, label = { Text(t(lang, "Bearer، اختیاری", "Bearer, optional")) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Row {
                    Button(onClick = {
                        vm.addMcp(name, url, transport, token)
                        name = ""; url = ""; token = ""
                    }) { Text(t(lang, "افزودن", "Add")) }
                    OutlinedButton(onClick = { vm.test(ConnectorIds.MCP) }) { Text(t(lang, "آزمایش", "Test")) }
                }
                TextButton(onClick = { open(context, "https://modelcontextprotocol.io/") }) { Text(t(lang, "مشخصات MCP", "MCP spec")) }
                if (row.note.isNotBlank()) Text(row.note, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
private fun ShizukuCard(state: ConnectorsUi, vm: ConnectorsViewModel, lang: String, sha: String) {
    val row = connectorRow(state.rows, ConnectorIds.SHIZUKU) ?: return
    val context = LocalContext.current
    SectionCard(
        title = "Shizuku",
        badge = { LinkBadge(lang, row.status) },
        body = t(
            lang,
            "اختیاری است. اگر Shizuku نصب و مجاز باشد، تنظیمات، grant/revoke یک اجازه، و شروع یا توقف یک جزء که خودتان نام می‌برید با تأیید انجام می‌شود. اگر نباشد، ابزارها می‌گویند Shizuku اجرا نیست. روت و بالا بردن پنهانی اجازه نیست.\nSHA-1 برای کلاینت اندروید گوگل: $sha",
            "Optional. If Shizuku is installed and authorized, confirmed helpers can read or write one setting, grant or revoke one permission, and start or stop a component you name. If it is missing, those tools say Shizuku is not running. No root and no hidden privilege escalation.\nSHA-1 for a Google Android client: $sha",
        ),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t(lang, "روشن", "On"), modifier = Modifier.weight(1f))
                    Switch(checked = ConnectorIds.SHIZUKU in state.snap.enabled, onCheckedChange = { vm.setEnabled(ConnectorIds.SHIZUKU, it) })
                }
                Text(row.note.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                Button(onClick = vm::requestShizuku) { Text(t(lang, "درخواست اجازه", "Request permission")) }
                OutlinedButton(onClick = { vm.test(ConnectorIds.SHIZUKU) }) { Text(t(lang, "آزمایش", "Test")) }
                TextButton(onClick = { open(context, "https://shizuku.rikka.app/introduction/") }) {
                    Text(t(lang, "نصب و اجازهٔ Shizuku", "Install and authorize Shizuku"))
                }
            }
        },
    )
}

@Composable
private fun LinkBadge(lang: String, status: LinkStatus) {
    val tone = when (status) {
        LinkStatus.CONNECTED -> StatusTone.Positive
        LinkStatus.NEEDS_AUTH -> StatusTone.Warning
        LinkStatus.ERROR -> StatusTone.Danger
        LinkStatus.OFF -> StatusTone.Neutral
    }
    StatusChip(statusLabel(lang, status), tone)
}

private fun statusLabel(lang: String, status: LinkStatus): String = when (status) {
    LinkStatus.OFF -> t(lang, "خاموش", "Off")
    LinkStatus.NEEDS_AUTH -> t(lang, "نیاز به راه‌اندازی", "Needs setup")
    LinkStatus.CONNECTED -> t(lang, "متصل", "Connected")
    LinkStatus.ERROR -> t(lang, "خطا", "Error")
}

private fun open(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun signingSha1(pm: PackageManager, packageName: String): String = runCatching {
    val bytes = if (Build.VERSION.SDK_INT >= 28) {
        val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info.signingInfo
        val current = runCatching { signing?.apkContentsSigners }.getOrNull().orEmpty()
        val signers = if (current.isNotEmpty()) current else runCatching { signing?.signingCertificateHistory }.getOrNull().orEmpty()
        signers.firstOrNull()?.toByteArray()
    } else {
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        @Suppress("DEPRECATION")
        info.signatures?.firstOrNull()?.toByteArray()
    }
    SigningSha1.format(bytes)
}.getOrDefault("—")
