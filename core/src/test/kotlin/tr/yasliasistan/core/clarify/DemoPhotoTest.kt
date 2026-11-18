package tr.yasliasistan.core.clarify

import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.decision.Action
import tr.yasliasistan.core.masking.ContactName

/** Demo 18.11.2026: "fotoğraf gönder" in many word orders and endings decides SendPhoto (made-up name). */
class DemoPhotoTest {
    private val safak = ContactName("9", "Şafak Deneme")

    @Test
    fun photoCommandsDecideSendPhoto() {
        listOf(
            "WhatsApp'tan Şafak'a fotoğraf gönder",
            "whatsapptan şafağa fotoğraf gönder",
            "şafağa whatsapptan son fotoğrafı gönder",
            "fotoğraf gönder şafağa",
            "son çektiğim fotoğrafı şafağa gönderir misin",
            "şafak denemeye resim yolla",
            "Şafak'a bir fotoğraf at",
            "bana şafağa fotoğraf gönder",
        ).forEachIndexed { i, spoken ->
            val h = Harness(contacts = People.all + safak, apps = listOf(Apps.settings, Apps.whatsapp))
            val event = h.coordinator.decide(i + 1, spoken)
            val action = h.coordinator.takeAction(i + 1)
            assertTrue("'$spoken' -> $event / $action", action is Action.SendPhoto && action.contact == safak && !action.guide)
        }
    }

    @Test
    fun guideWordsChooseBenYapayim() {
        listOf(
            "Şafak'a fotoğraf göndermeyi göster",
            "şafağa nasıl fotoğraf gönderilir",
            "bana şafağa fotoğraf göndermeyi öğret",
            "şafağa fotoğrafı ben göndereyim göster",
            "bana WhatsApp'tan Şafak'a fotoğraf atmayı öğret",
        ).forEachIndexed { i, spoken ->
            val h = Harness(contacts = People.all + safak, apps = listOf(Apps.settings, Apps.whatsapp))
            val event = h.coordinator.decide(i + 1, spoken)
            val action = h.coordinator.takeAction(i + 1)
            assertTrue("'$spoken' -> $event / $action", action is Action.SendPhoto && action.contact == safak && action.guide)
        }
    }
}
