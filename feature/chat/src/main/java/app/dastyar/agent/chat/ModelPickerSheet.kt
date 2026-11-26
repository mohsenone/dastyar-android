package app.dastyar.agent.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dastyar.agent.data.ProviderEntity
import app.dastyar.agent.network.CatalogModel
import app.dastyar.agent.network.ModelOrder
import app.dastyar.agent.ui.t

fun chatModelLabel(state: ChatUi): String {
    val lang = state.lang
    val conv = state.conversations.firstOrNull { it.id == state.activeId }
    val explicit = conv?.providerId?.let { id -> state.providers.firstOrNull { it.id == id } }
    val provider = explicit ?: state.providers.firstOrNull { it.isDefault } ?: state.providers.firstOrNull()
    val name = when {
        provider == null -> t(lang, "مدل", "Model")
        explicit == null -> t(lang, "پیش‌فرض", "Default") + " · " + provider.label.ifBlank { provider.kind }
        else -> provider.label.ifBlank { provider.kind }
    }
    val model = conv?.modelOverride?.takeIf { it.isNotBlank() } ?: provider?.model.orEmpty()
    return if (model.isBlank()) name else "$name · $model"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    state: ChatUi,
    onClose: () -> Unit,
    onOpenProviders: () -> Unit,
    onFollowDefault: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onQuery: (String) -> Unit,
    onRefresh: () -> Unit,
    onApply: (String) -> Unit,
    onSaveDefault: (String) -> Unit,
) {
    val lang = state.lang
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(t(lang, "ارائه‌دهنده و مدل", "Provider and model"), style = MaterialTheme.typography.titleLarge)
            if (state.providers.isEmpty()) {
                Text(
                    t(lang, "هنوز ارائه‌دهنده‌ای ذخیره نشده.", "No provider is saved yet."),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = {
                    onClose()
                    onOpenProviders()
                }) {
                    Text(t(lang, "ارائه‌دهندگان", "Providers"))
                }
                return@Column
            }
            ProviderChips(state, onFollowDefault, onSelectProvider)
            if (state.toolWarning) {
                Text(
                    t(
                        lang,
                        "این مدل ابزار را پشتیبانی نمی‌کند. ابزارها ممکن است در دسترس یا قابل‌اتکا نباشند.",
                        "This model does not support tool calling. Tools may be unavailable or unreliable.",
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.pickerStatus == "discontinued") {
                Text(
                    t(
                        lang,
                        "GitHub Models متوقف شده است و فهرست زنده‌ای ندارد.",
                        "GitHub Models is discontinued. There is no live model list.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.pickerStatus == "builtin") {
                Text(
                    t(
                        lang,
                        "فهرست آفلاین / ساخته‌شده. اگر فهرست زنده نیاید همین نشان داده می‌شود.",
                        "Offline / built-in list. This is what you see when the live catalog does not load.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!state.pickerError.isNullOrBlank()) {
                Text(state.pickerError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.pickerQuery,
                    onValueChange = onQuery,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(t(lang, "جستجوی مدل", "Search models")) },
                )
                IconButton(onClick = onRefresh, enabled = !state.pickerBusy) {
                    Icon(Icons.Outlined.Refresh, contentDescription = t(lang, "تازه‌سازی مدل‌ها", "Refresh models"))
                }
            }
            val shown = ModelOrder.filter(state.pickerModels, state.pickerQuery)
            val effective = effectiveSelection(state)
            PullToRefreshBox(
                isRefreshing = state.pickerBusy,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (shown.isEmpty() && !state.pickerBusy && state.pickerStatus != "discontinued") {
                    Column(Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            t(lang, "مدلی در این فهرست نیست.", "No model is in this list."),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TextButton(onClick = onRefresh) { Text(t(lang, "تلاش دوباره", "Try again")) }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(shown, key = { it.id }) { model ->
                            ModelRow(
                                model = model,
                                lang = lang,
                                selected = state.pickerProviderId == effective.first && model.id == effective.second,
                                onClick = { onApply(model.id) },
                            )
                        }
                    }
                }
            }
            ManualModelField(state, effective.second, onApply, onSaveDefault)
        }
    }
}

@Composable
private fun ProviderChips(
    state: ChatUi,
    onFollowDefault: () -> Unit,
    onSelectProvider: (String) -> Unit,
) {
    val lang = state.lang
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = state.pickerFollowDefault,
            onClick = onFollowDefault,
            label = { Text(t(lang, "پیش‌فرض", "Default")) },
        )
        state.providers.forEach { provider ->
            FilterChip(
                selected = !state.pickerFollowDefault && state.pickerProviderId == provider.id,
                onClick = { onSelectProvider(provider.id) },
                label = { Text(provider.label.ifBlank { provider.kind }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun ModelRow(model: CatalogModel, lang: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                model.displayName.ifBlank { model.id },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            if (model.free) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        t(lang, "رایگان", "Free"),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (model.note == "trial") {
                Text(t(lang, "آزمایشی", "Trial"), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (model.displayName != model.id) {
            Text(model.id, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        val facts = modelFacts(model, lang)
        if (facts.isNotEmpty()) {
            Text(facts.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun modelFacts(model: CatalogModel, lang: String): List<String> {
    val facts = mutableListOf<String>()
    model.contextLength?.let { facts += t(lang, "بافت $it", "$it ctx") }
    when (model.vision) {
        true -> facts += t(lang, "بینایی", "Vision")
        false -> facts += t(lang, "بدون بینایی", "No vision")
        null -> Unit
    }
    when (model.tools) {
        true -> facts += t(lang, "ابزار", "Tools")
        false -> facts += t(lang, "بدون ابزار", "No tools")
        null -> Unit
    }
    return facts
}

@Composable
private fun ManualModelField(
    state: ChatUi,
    currentModel: String,
    onApply: (String) -> Unit,
    onSaveDefault: (String) -> Unit,
) {
    val lang = state.lang
    var typed by rememberSaveable(state.pickerProviderId, currentModel) { mutableStateOf(currentModel) }
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(t(lang, "شناسه‌ای که در فهرست نیست", "Id not in the list")) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onApply(typed) }, enabled = typed.isNotBlank()) {
            Text(t(lang, "اعمال روی این گفتگو", "Apply to this chat"))
        }
        TextButton(
            onClick = { onSaveDefault(typed.ifBlank { currentModel }) },
            enabled = state.pickerProviderId != null && (typed.isNotBlank() || currentModel.isNotBlank()),
        ) {
            Text(t(lang, "پیش‌فرض برنامه شود", "Save as app default"))
        }
    }
    Text(
        t(
            lang,
            "گفتگویی که مدل خودش را جدا ذخیره کرده همان می‌ماند.",
            "A chat that saved its own model stays on that model.",
        ),
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun effectiveSelection(state: ChatUi): Pair<String?, String> {
    val conv = state.conversations.firstOrNull { it.id == state.activeId }
    val providerId = conv?.providerId ?: state.providers.firstOrNull { it.isDefault }?.id
    val provider: ProviderEntity? = state.providers.firstOrNull { it.id == providerId }
    val model = conv?.modelOverride?.takeIf { it.isNotBlank() } ?: provider?.model.orEmpty()
    return providerId to model
}
