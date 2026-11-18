package tr.yasliasistan.core.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.decision.rules.OpenAppRule

class OpenAppRuleTest {

    private val settings = app("Ayarlar", "com.android.settings")
    private val whatsapp = app("WhatsApp", "com.whatsapp")
    private val whatsappBusiness = app("WhatsApp Business", "com.whatsapp.w4b")
    private val maps = app("Google Haritalar", "com.google.android.apps.maps")
    private val camera = app("Kamera", "com.sec.android.app.camera")
    private val weather = app("Hava Durumu", "com.sec.android.daemonapp")
    private val phone = app("Telefon", "com.samsung.android.dialer", "DialtactsActivity")
    private val contacts = app("Rehber", "com.samsung.android.dialer", "PeopleActivity")
    private val edevlet = app("e-Devlet", "tr.gov.turkiye.edevlet.kapisi")
    private val instagram = app("Instagram", "com.instagram.android")

    private val apps = listOf(settings, whatsapp, whatsappBusiness, maps, camera, weather, phone, contacts, edevlet, instagram)
    private val rule = OpenAppRule { apps }

    private fun decide(text: String) = rule.apply(Command(text))

    private fun assertOpens(text: String, expected: InstalledApp) {
        val decision = decide(text)
        assertEquals("command: $text", Decision.Act(Action.OpenApp(expected), certain = true), decision)
    }

    @Test
    fun opensSettingsForCommonPhrasings() {
        listOf(
            "ayarları aç", "Ayarları aç.", "AYARLARI AÇ", "ayarlara gir", "ayarlar açar mısın",
            "ayarları açabilir misin", "ayarlara git", "ayarları açın lütfen", "aç ayarları",
            "ayarlar uygulamasını aç", "Ayarlar'ı açsana", "ayarlar'a girer misin",
        ).forEach { assertOpens(it, settings) }
    }

    @Test
    fun opensAppsWithSuffixesAndApostrophes() {
        assertOpens("WhatsApp'ı aç", whatsapp)
        assertOpens("whatsappı aç", whatsapp)
        assertOpens("whats app aç", whatsapp)
        assertOpens("whatsapp business'ı aç", whatsappBusiness)
        assertOpens("kamerayı aç", camera)
        assertOpens("hava durumunu aç", weather)
        assertOpens("telefonu aç", phone)
        assertOpens("rehberi aç", contacts)
        assertOpens("e-devleti aç", edevlet)
        assertOpens("INSTAGRAM'I AÇ", instagram)
        assertOpens("google haritaları başlat", maps)
    }

    @Test
    fun partialNameGivesUncertainSingleMatch() {
        assertEquals(Decision.Act(Action.OpenApp(maps), certain = false), decide("haritaları aç"))
    }

    @Test
    fun exactMatchWinsOverPartialMatches() {
        // "WhatsApp" is the full name of one app and a part of "WhatsApp Business".
        assertOpens("whatsapp aç", whatsapp)
    }

    @Test
    fun severalPartialMatchesOfferChoice() {
        val twoWhatsapps = OpenAppRule { listOf(whatsappBusiness, app("WhatsApp Web", "web.app")) }
        val decision = twoWhatsapps.apply(Command("whatsapp aç"))
        assertTrue(decision is Decision.Choose)
        assertEquals(2, (decision as Decision.Choose).options.size)
    }

    @Test
    fun sameLabelTwiceOffersChoice() {
        val dual = OpenAppRule { listOf(whatsapp, app("WhatsApp", "com.whatsapp", "DualActivity")) }
        assertTrue(dual.apply(Command("whatsapp aç")) is Decision.Choose)
    }

    @Test
    fun noVerbOrNegationIsNotAnOpenCommand() {
        assertNull(decide("ayarlar"))
        assertNull(decide("ayarları açma"))
        assertNull(decide("acil durum"))
        assertNull(decide("aç"))
        assertNull(decide("lütfen aç"))
    }

    @Test
    fun unknownAppIsNotMatched() {
        assertNull(decide("tiktok aç"))
        assertNull(decide("annemi ara"))
    }
}

internal fun app(label: String, pkg: String, activity: String = "Main") = InstalledApp(label, pkg, activity)
