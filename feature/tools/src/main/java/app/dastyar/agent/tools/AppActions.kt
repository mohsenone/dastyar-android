package app.dastyar.agent.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/** Launches apps the user already installed. Does not grant anything. */
internal class AppActions(private val context: Context) {
    fun launcherApps(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
        return resolved.map { it.loadLabel(context.packageManager).toString() to it.activityInfo.packageName }
    }

    fun openLabelOrPackage(query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.contains(' ') && trimmed.contains('.')) {
            val direct = context.packageManager.getLaunchIntentForPackage(trimmed)
            if (direct != null) {
                context.startActivity(direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return trimmed
            }
        }
        val match = AppLabels.match(trimmed, launcherApps()) ?: return null
        val intent = context.packageManager.getLaunchIntentForPackage(match.second) ?: return null
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "${match.first} (${match.second})"
    }

    fun listInstalled(): List<Pair<String, String>> = launcherApps().distinctBy { it.second }.sortedBy { it.first.lowercase() }

    fun openDeepLink(uri: String, packageName: String?): String? {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        if (parsed.scheme.isNullOrBlank()) return null
        val intent = Intent(Intent.ACTION_VIEW, parsed)
        if (!packageName.isNullOrBlank()) intent.setPackage(packageName)
        if (tryStart(intent)) return packageName ?: uri
        if (!packageName.isNullOrBlank() && tryStart(Intent(Intent.ACTION_VIEW, parsed))) return uri
        return null
    }

    fun openHttps(url: String, packageName: String?): String? {
        val parsed = Uri.parse(url)
        if (parsed.scheme !in setOf("https", "http")) return null
        val intent = Intent(Intent.ACTION_VIEW, parsed)
        if (!packageName.isNullOrBlank()) intent.setPackage(packageName)
        if (tryStart(intent)) return packageName ?: url
        if (!packageName.isNullOrBlank() && tryStart(Intent(Intent.ACTION_VIEW, parsed))) return url
        return null
    }

    fun openWhatsApp(digits: String, text: String): String? {
        val https = Uri.parse(WhatsAppLinks.waMe(digits, text))
        val scheme = Uri.parse(WhatsAppLinks.scheme(digits, text))
        for (pkg in WhatsAppLinks.PACKAGES) {
            if (tryStart(Intent(Intent.ACTION_VIEW, https).setPackage(pkg))) return pkg
            if (tryStart(Intent(Intent.ACTION_VIEW, scheme).setPackage(pkg))) return pkg
        }
        if (tryStart(Intent(Intent.ACTION_VIEW, https))) return "https://wa.me"
        if (tryStart(Intent(Intent.ACTION_VIEW, scheme))) return "whatsapp://"
        return null
    }

    /**
     * Opens the user's mail app. Gmail is preferred when it is installed.
     * Official Gmail API OAuth is not part of this release.
     */
    fun openMail(draft: MailDraft): String? {
        val sendto = MailLinks.sendto(draft)
        val send = MailLinks.send(draft)
        val mailto = Intent(sendto.action, Uri.parse(draft.mailto))
        val message = Intent(send.action).apply {
            type = send.mime
            putExtra(Intent.EXTRA_EMAIL, draft.recipients.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, draft.subject)
            putExtra(Intent.EXTRA_TEXT, draft.body)
        }
        if (tryStart(Intent(mailto).setPackage(MailLinks.GMAIL))) return MailLinks.GMAIL
        if (tryStart(Intent(message).setPackage(MailLinks.GMAIL))) return MailLinks.GMAIL
        if (tryStart(mailto)) return "mailto"
        val chooser = Intent.createChooser(message, "Email").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (tryStart(chooser)) "chooser" else null
    }

    private fun tryStart(intent: Intent): Boolean {
        val ready = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!canResolve(ready)) return false
        return runCatching {
            context.startActivity(ready)
            true
        }.getOrDefault(false)
    }

    private fun canResolve(intent: Intent): Boolean {
        val manager = context.packageManager
        return if (Build.VERSION.SDK_INT >= 33) {
            manager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0)) != null
        } else {
            @Suppress("DEPRECATION")
            manager.resolveActivity(intent, 0) != null
        }
    }
}
