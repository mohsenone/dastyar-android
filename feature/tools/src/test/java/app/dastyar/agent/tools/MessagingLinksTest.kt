package app.dastyar.agent.tools

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagingLinksTest {
    @Test
    fun normalizesCountryCodeAndStripsSeparators() {
        val plus = PhoneNumbers.normalize("+98 912-345-6789")
        val zero = PhoneNumbers.normalize("0098 (912) 345.6789")
        assertEquals("989123456789", (plus as PhoneNumbers.Normalized.Ok).digits)
        assertEquals("989123456789", (zero as PhoneNumbers.Normalized.Ok).digits)
    }

    @Test
    fun nationalTrunkNeedsACountryCode() {
        assertEquals(PhoneNumbers.Normalized.NeedsCountryCode, PhoneNumbers.normalize("0912 345 6789"))
        assertEquals(PhoneNumbers.Normalized.Empty, PhoneNumbers.normalize("  "))
        assertEquals(PhoneNumbers.Normalized.Invalid, PhoneNumbers.normalize("+123"))
    }

    @Test
    fun waMeKeepsDigitsAndEncodesText() {
        val uri = WhatsAppLinks.waMe("989123456789", "سلام Hello")
        assertTrue(uri.startsWith("https://wa.me/989123456789?text="))
        val text = uri.substringAfter("text=")
        assertEquals("سلام Hello", URLDecoder.decode(text, "UTF-8"))
        assertEquals("whatsapp://send?phone=989123456789", WhatsAppLinks.scheme("989123456789", ""))
    }

    @Test
    fun mailtoAndSendExtrasMatchTheDraft() {
        val draft = MailLinks.draft("Ada <ada@example.com>; bob@example.com", "Hello there", "Line 1")
        requireNotNull(draft)
        assertEquals(listOf("ada@example.com", "bob@example.com"), draft.recipients)
        assertTrue(draft.mailto.startsWith("mailto:ada@example.com,bob@example.com?"))
        assertEquals("Hello there", URLDecoder.decode(draft.mailto.substringAfter("subject=").substringBefore("&"), "UTF-8"))
        val sendto = MailLinks.sendto(draft)
        assertEquals("android.intent.action.SENDTO", sendto.action)
        assertEquals(draft.mailto, sendto.data)
        assertNull(sendto.mime)
        assertEquals(draft.recipients, sendto.extraEmail)
        assertEquals("Hello there", sendto.extraSubject)
        assertEquals("Line 1", sendto.extraText)
        val send = MailLinks.send(draft)
        assertEquals("android.intent.action.SEND", send.action)
        assertEquals("message/rfc822", send.mime)
        assertEquals(draft.recipients, send.extraEmail)
        assertNull(MailLinks.draft("not-an-email", "", ""))
    }

    @Test
    fun launcherLabelMatchesPersianAndEnglish() {
        val apps = listOf("واتساپ" to "com.whatsapp", "Gmail" to "com.google.android.gm")
        assertEquals("com.whatsapp", AppLabels.match("واتساپ", apps)?.second)
        assertEquals("com.google.android.gm", AppLabels.match("gmail", apps)?.second)
        assertEquals("com.whatsapp", AppLabels.match("com.whatsapp", apps)?.second)
        assertNull(AppLabels.match("   ", apps))
    }
}
