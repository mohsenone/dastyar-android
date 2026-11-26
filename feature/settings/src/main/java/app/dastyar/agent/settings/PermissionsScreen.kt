package app.dastyar.agent.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.dastyar.agent.tools.AccessGroups
import app.dastyar.agent.tools.AccessItem
import app.dastyar.agent.tools.AccessState
import app.dastyar.agent.tools.PermissionDiagnostics
import app.dastyar.agent.tools.PermissionPlanner
import app.dastyar.agent.tools.RestrictedSettings
import app.dastyar.agent.ui.DirectedText
import app.dastyar.agent.ui.DastyarTopBar
import app.dastyar.agent.ui.PrimaryButton
import app.dastyar.agent.ui.SecondaryButton
import app.dastyar.agent.ui.SectionCard
import app.dastyar.agent.ui.SectionHeader
import app.dastyar.agent.ui.Space
import app.dastyar.agent.ui.StatusChip
import app.dastyar.agent.ui.StatusTone

private enum class PermPage { Status, Wizard, Fix }

@Composable
fun PermissionsScreen(
    lang: String,
    safGranted: Boolean,
    onAction: (String) -> Unit,
    onAppInfo: () -> Unit,
    showOnboardingChoices: Boolean,
    onProviders: () -> Unit,
    onChat: () -> Unit,
    onPrivacy: () -> Unit,
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
    val report = remember(tick, safGranted) { PermissionDiagnostics.inspect(context, safGranted) }
    var page by remember { mutableStateOf(if (showOnboardingChoices) PermPage.Wizard else PermPage.Status) }
    var groupIndex by remember { mutableIntStateOf(0) }
    var fixIds by remember { mutableStateOf<List<String>?>(null) }
    var fixIndex by remember { mutableIntStateOf(0) }
    val fa = lang.startsWith("fa")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.s16),
        verticalArrangement = Arrangement.spacedBy(Space.s12),
    ) {
        DastyarTopBar(stringResource(R.string.perm_title))
        Text(stringResource(R.string.perm_intro), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
            FilterChip(
                selected = page == PermPage.Status,
                onClick = { page = PermPage.Status; fixIds = null },
                label = { Text(stringResource(R.string.perm_status)) },
            )
            FilterChip(
                selected = page == PermPage.Wizard,
                onClick = { page = PermPage.Wizard; fixIds = null },
                label = { Text(stringResource(R.string.perm_wizard)) },
            )
        }
        PrimaryButton(stringResource(R.string.perm_fix_all), {
            val queue = PermissionPlanner.fixQueue(report.items)
            fixIds = queue.map { it.id }
            fixIndex = 0
            page = PermPage.Fix
        }, Modifier.fillMaxWidth(), enabled = PermissionPlanner.fixQueue(report.items).isNotEmpty() || page == PermPage.Fix)
        SecondaryButton(stringResource(R.string.perm_privacy_link), onPrivacy, Modifier.fillMaxWidth())
        if (report.restrictedLikely || page != PermPage.Status) {
            RestrictedGuide(fa = fa, onAppInfo = onAppInfo, emphasize = report.restrictedLikely)
        }
        when (page) {
            PermPage.Status -> report.items.forEach { item ->
                AccessRow(item, fa, onAction, onAppInfo)
            }
            PermPage.Wizard -> WizardGroup(
                group = PermissionPlanner.wizardGroups[groupIndex.coerceIn(0, PermissionPlanner.wizardGroups.lastIndex)],
                index = groupIndex,
                count = PermissionPlanner.wizardGroups.size,
                items = report.items,
                fa = fa,
                onAction = onAction,
                onAppInfo = onAppInfo,
                onBack = { if (groupIndex > 0) groupIndex-- },
                onSkip = { if (groupIndex < PermissionPlanner.wizardGroups.lastIndex) groupIndex++ },
            )
            PermPage.Fix -> FixStep(
                ids = fixIds.orEmpty(),
                index = fixIndex,
                items = report.items,
                fa = fa,
                onAction = onAction,
                onAppInfo = onAppInfo,
                onNext = {
                    val last = (fixIds?.lastIndex ?: 0)
                    if (fixIndex < last) fixIndex++ else page = PermPage.Status
                },
            )
        }
        if (showOnboardingChoices) {
            PrimaryButton(stringResource(R.string.perm_continue_key), onProviders, Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.perm_skip_chat), onChat, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RestrictedGuide(fa: Boolean, onAppInfo: () -> Unit, emphasize: Boolean) {
    SectionCard(
        title = stringResource(R.string.perm_restricted_title),
        body = if (emphasize) stringResource(R.string.perm_restricted_lead) else "",
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
                DirectedText(
                    if (fa) RestrictedSettings.guideFa() else RestrictedSettings.guideEn(),
                    rtl = fa,
                )
                Text(stringResource(R.string.perm_other_language), style = MaterialTheme.typography.labelLarge)
                DirectedText(
                    if (fa) RestrictedSettings.guideEn() else RestrictedSettings.guideFa(),
                    rtl = !fa,
                )
                SectionHeader(stringResource(R.string.perm_vendor_title))
                DirectedText(RestrictedSettings.vendorNotes(if (fa) "fa" else "en"), rtl = fa)
                PrimaryButton(stringResource(R.string.perm_app_info), onAppInfo, Modifier.fillMaxWidth())
            }
        },
    )
}

@Composable
private fun AccessRow(item: AccessItem, fa: Boolean, onAction: (String) -> Unit, onAppInfo: () -> Unit) {
    SectionCard(
        title = if (fa) item.titleFa else item.titleEn,
        badge = { StatusChip(stateLabel(item.state), toneFor(item.state)) },
        body = (if (fa) item.whyFa else item.whyEn) + "\n" + (if (fa) item.nextFa else item.nextEn),
        footer = {
            if (item.state != AccessState.GRANTED) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                    PrimaryButton(actionLabel(item), { onAction(item.id) }, Modifier.weight(1f))
                    if (item.state == AccessState.MAYBE_RESTRICTED || item.state == AccessState.NEEDS_SETTINGS) {
                        SecondaryButton(stringResource(R.string.perm_app_info), onAppInfo, Modifier.weight(1f))
                    }
                }
            }
        },
    )
}

@Composable
private fun WizardGroup(
    group: String,
    index: Int,
    count: Int,
    items: List<AccessItem>,
    fa: Boolean,
    onAction: (String) -> Unit,
    onAppInfo: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val rows = items.filter { it.group == group }
    SectionHeader(groupTitle(group))
    Text(stringResource(R.string.perm_fix_progress, index + 1, count), style = MaterialTheme.typography.labelLarge)
    Text(groupWhy(group), style = MaterialTheme.typography.bodyMedium)
    rows.forEach { AccessRow(it, fa, onAction, onAppInfo) }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s8), modifier = Modifier.fillMaxWidth()) {
        if (index > 0) SecondaryButton(stringResource(R.string.perm_back), onBack, Modifier.weight(1f))
        if (index < count - 1) {
            SecondaryButton(stringResource(R.string.perm_skip), onSkip, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FixStep(
    ids: List<String>,
    index: Int,
    items: List<AccessItem>,
    fa: Boolean,
    onAction: (String) -> Unit,
    onAppInfo: () -> Unit,
    onNext: () -> Unit,
) {
    if (ids.isEmpty()) {
        Text(stringResource(R.string.perm_done), style = MaterialTheme.typography.titleMedium)
        return
    }
    val item = items.firstOrNull { it.id == ids.getOrElse(index) { ids.last() } } ?: return
    Text(stringResource(R.string.perm_fix_progress, index + 1, ids.size), style = MaterialTheme.typography.labelLarge)
    if (item.state == AccessState.MAYBE_RESTRICTED) {
        Text(stringResource(R.string.perm_restricted_lead), color = MaterialTheme.colorScheme.error)
    }
    AccessRow(item, fa, onAction, onAppInfo)
    PrimaryButton(
        if (index < ids.lastIndex) stringResource(R.string.perm_next) else stringResource(R.string.perm_done),
        onNext,
        Modifier.fillMaxWidth(),
    )
}

@Composable
private fun toneFor(state: AccessState): StatusTone = when (state) {
    AccessState.GRANTED -> StatusTone.Positive
    AccessState.MISSING -> StatusTone.Warning
    AccessState.NEEDS_SETTINGS -> StatusTone.Warning
    AccessState.MAYBE_RESTRICTED -> StatusTone.Danger
}

@Composable
private fun stateLabel(state: AccessState): String = when (state) {
    AccessState.GRANTED -> stringResource(R.string.perm_state_granted)
    AccessState.MISSING -> stringResource(R.string.perm_state_missing)
    AccessState.NEEDS_SETTINGS -> stringResource(R.string.perm_state_settings)
    AccessState.MAYBE_RESTRICTED -> stringResource(R.string.perm_state_restricted)
}

@Composable
private fun actionLabel(item: AccessItem): String = when {
    item.id == "accessibility" -> stringResource(R.string.perm_open)
    item.state == AccessState.MAYBE_RESTRICTED -> stringResource(R.string.perm_try_again)
    item.state == AccessState.NEEDS_SETTINGS || item.target != "runtime" -> stringResource(R.string.perm_fix)
    else -> stringResource(R.string.perm_grant)
}

@Composable
private fun groupTitle(group: String): String = stringResource(
    when (group) {
        AccessGroups.MESSAGES -> R.string.perm_group_messages
        AccessGroups.CALLS -> R.string.perm_group_calls
        AccessGroups.CONTACTS -> R.string.perm_group_contacts
        AccessGroups.APPS -> R.string.perm_group_apps
        AccessGroups.NOTIFICATIONS -> R.string.perm_group_notifications
        AccessGroups.FILES -> R.string.perm_group_files
        AccessGroups.LOCATION -> R.string.perm_group_location
        AccessGroups.CAPTURE -> R.string.perm_group_capture
        else -> R.string.perm_group_system
    },
)

@Composable
private fun groupWhy(group: String): String = stringResource(
    when (group) {
        AccessGroups.MESSAGES -> R.string.perm_why_messages
        AccessGroups.CALLS -> R.string.perm_why_calls
        AccessGroups.CONTACTS -> R.string.perm_why_contacts
        AccessGroups.APPS -> R.string.perm_why_apps
        AccessGroups.NOTIFICATIONS -> R.string.perm_why_notifications
        AccessGroups.FILES -> R.string.perm_why_files
        AccessGroups.LOCATION -> R.string.perm_why_location
        AccessGroups.CAPTURE -> R.string.perm_why_capture
        else -> R.string.perm_why_system
    },
)
