package app.dastyar.agent.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.dastyar.agent.tools.PermissionDiagnostics
import app.dastyar.agent.ui.DastyarTopBar
import app.dastyar.agent.ui.SectionCard
import app.dastyar.agent.ui.Space

@Composable
fun PrivacyScreen(lang: String, safGranted: Boolean, onBack: () -> Unit) {
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
    val fa = lang.startsWith("fa")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.s16),
        verticalArrangement = Arrangement.spacedBy(Space.s12),
    ) {
        DastyarTopBar(stringResource(R.string.privacy_title), navigation = {
            androidx.compose.material3.TextButton(onClick = onBack) {
                Text(stringResource(R.string.perm_back))
            }
        })
        Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyMedium)
        SectionCard(
            title = stringResource(R.string.privacy_confirm_title),
            body = stringResource(R.string.privacy_confirm_body),
        )
        SectionCard(
            title = stringResource(R.string.privacy_revoke_title),
            body = stringResource(R.string.privacy_revoke_body),
        )
        Text(stringResource(R.string.privacy_not_logged), style = MaterialTheme.typography.bodySmall)
        report.items.forEach { item ->
            SectionCard(
                title = if (fa) item.titleFa else item.titleEn,
                body = stringResource(R.string.privacy_used_for) + "\n" + if (fa) item.whyFa else item.whyEn,
            )
        }
    }
}
