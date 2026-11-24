package app.dastyar.agent.tools

import java.net.URLEncoder

/** Phone numbers for wa.me. Country code stays; spaces and dashes go. */
object PhoneNumbers {
    sealed class Normalized {
        data class Ok(val digits: String) : Normalized()
        data object Empty : Normalized()
        data object NeedsCountryCode : Normalized()
        data object Invalid : Normalized()
    }

    fun normalize(raw: String): Normalized {
        var text = raw.trim()
        if (text.isEmpty()) return Normalized.Empty
        text = text.replace(Regex("[\\s\\-().]"), "")
        if (text.startsWith("+")) text = text.drop(1)
        else if (text.startsWith("00")) text = text.drop(2)
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return Normalized.Empty
        if (digits.startsWith("0")) return Normalized.NeedsCountryCode
        if (digits.length < 8 || digits.length > 15) return Normalized.Invalid
        return Normalized.Ok(digits)
    }
}

object WhatsAppLinks {
    val PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun waMe(digits: String, text: String): String {
        val base = "https://wa.me/$digits"
        if (text.isBlank()) return base
        return "$base?text=${encode(text)}"
    }

    fun scheme(digits: String, text: String): String {
        val base = "whatsapp://send?phone=$digits"
        if (text.isBlank()) return base
        return "$base&text=${encode(text)}"
    }
}

data class MailDraft(
    val recipients: List<String>,
    val subject: String,
    val body: String,
    val mailto: String,
)

/** Fields the mail intents copy into ACTION_SENDTO / ACTION_SEND. */
data class MailIntentExtras(
    val action: String,
    val data: String?,
    val mime: String?,
    val extraEmail: List<String>,
    val extraSubject: String,
    val extraText: String,
)

object MailLinks {
    const val GMAIL = "com.google.android.gm"

    fun draft(to: String, subject: String, body: String): MailDraft? {
        val recipients = to.split(',', ';').map { it.trim() }.mapNotNull(::extractEmail).distinct()
        if (recipients.isEmpty()) return null
        val query = buildList {
            if (subject.isNotBlank()) add("subject=${encode(subject)}")
            if (body.isNotBlank()) add("body=${encode(body)}")
        }.joinToString("&")
        val path = recipients.joinToString(",")
        val mailto = if (query.isEmpty()) "mailto:$path" else "mailto:$path?$query"
        return MailDraft(recipients, subject, body, mailto)
    }

    fun sendto(draft: MailDraft) = MailIntentExtras(
        action = "android.intent.action.SENDTO",
        data = draft.mailto,
        mime = null,
        extraEmail = draft.recipients,
        extraSubject = draft.subject,
        extraText = draft.body,
    )

    fun send(draft: MailDraft) = MailIntentExtras(
        action = "android.intent.action.SEND",
        data = null,
        mime = "message/rfc822",
        extraEmail = draft.recipients,
        extraSubject = draft.subject,
        extraText = draft.body,
    )

    private fun extractEmail(raw: String): String? {
        val angle = Regex("<([^>]+)>").find(raw)?.groupValues?.getOrNull(1)
        val candidate = (angle ?: raw).trim()
        if (!candidate.contains("@") || candidate.any { it.isWhitespace() }) return null
        return candidate
    }
}

object AppLabels {
    fun match(query: String, apps: List<Pair<String, String>>): Pair<String, String>? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        apps.firstOrNull { it.second.equals(trimmed, ignoreCase = true) }?.let { return it }
        apps.firstOrNull { it.first.equals(trimmed, ignoreCase = true) }?.let { return it }
        apps.firstOrNull { it.first.contains(trimmed, ignoreCase = true) }?.let { return it }
        return apps.firstOrNull { it.second.contains(trimmed, ignoreCase = true) }
    }
}

internal val EMAIL_PACKAGES = setOf(
    "com.google.android.gm",
    "com.microsoft.office.outlook",
    "com.yahoo.mobile.client.android.mail",
    "com.samsung.android.email.provider",
    "ch.protonmail.android",
    "com.fsck.k9",
)

internal fun isEmailPackage(pkg: String): Boolean {
    if (pkg in EMAIL_PACKAGES) return true
    return pkg.contains("email", true) || pkg.contains("outlook", true) || pkg.endsWith(".gm")
}

internal fun isWhatsAppPackage(pkg: String): Boolean = pkg in WhatsAppLinks.PACKAGES

@Suppress("DEPRECATION")
internal fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
