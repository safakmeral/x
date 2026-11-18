package tr.yasliasistan.core.clarify

import tr.yasliasistan.core.contacts.ContactPhrases

/**
 * Sentences of the clarification rules (G-37). `:core` has no Android resources; the app implements
 * this with strings.xml. Intended Turkish wording in each comment (NFG-05: short, one question).
 * Names are placed so that no Turkish case ending has to be attached to them.
 */
interface ClarifyPhrases : ContactPhrases {
    /** "%1$s kişisini arayayım mı?" */
    fun confirmCall(name: String): String

    /** "Rehberde en yakın kişi %1$s. Onu arayayım mı?" (inexact match must be confirmed explicitly). */
    fun confirmCallInexact(name: String): String

    /** "%1$s için arama ekranını açıyorum." (said if a call is ever not asked first). */
    fun callingAnnouncement(name: String): String

    /** "%1$s kişisine %2$s ile mesaj yazayım mı?" */
    fun confirmMessage(name: String, app: String): String

    /** "Rehberde en yakın kişi %1$s. Ona %2$s ile mesaj yazayım mı?" */
    fun confirmMessageInexact(name: String, app: String): String

    /** "%1$s kişisine mesaj ekranını açıyorum." */
    fun messageAnnouncement(name: String): String

    /** "Son fotoğrafınızı WhatsApp'tan %1$s kişisine göndereyim mi?" (High risk: always asked). */
    fun confirmPhoto(name: String): String = "WhatsApp'tan $name kişisine fotoğraf göndereyim mi?"

    /** "Rehberde en yakın kişi %1$s. Ona fotoğraf göndereyim mi?" */
    fun confirmPhotoInexact(name: String): String = "Rehberde en yakın kişi $name. Ona fotoğraf göndereyim mi?"

    /** "Ben yapayım": "%1$s kişisine WhatsApp'tan fotoğraf göndermeyi size göstereyim mi?" */
    fun confirmPhotoGuide(name: String): String = "$name kişisine WhatsApp'tan fotoğraf göndermeyi size göstereyim mi?"

    /** "Fotoğraf seçme ekranını açıyorum." */
    fun photoAnnouncement(name: String): String = "Fotoğraf seçme ekranını açıyorum."

    /** "Kime fotoğraf göndereyim?" */
    fun askWhomToSendPhoto(): String = "Kime fotoğraf göndereyim?"

    /** Label of the SMS channel: "Mesajlar". */
    fun smsLabel(): String

    /** "Kimi arayayım?" (the person is truly missing: one question, DEG-31). */
    fun askWhomToCall(): String

    /** "Kime mesaj yazayım?" */
    fun askWhomToMessage(): String

    /** "Mesajı nereden yazayım: %1$s?" (options read by name). */
    fun chooseMessageApp(labels: List<String>): String

    /** "%1$s telefonunuzda yüklü değil." */
    fun appNotInstalled(label: String): String

    /** "Kişiyi yine bulamadım. Baloncuğa dokunup adını ve soyadını söyleyerek yeniden deneyin." */
    fun stillNotFound(): String

    /** "Kişiyi bulmam için rehber izni gerekiyor. İzin ekranını açıyorum." */
    fun openingContactsPermission(): String

    /** "Rehber izni ekranını açayım mı?" (readback when the transcript was only partial). */
    fun confirmContactsPermission(): String

    /** "Sesi açayım mı?" / "Sesi kısayım mı?" / "Ses ayarını göstereyim mi?" */
    fun confirmVolume(up: Boolean?): String

    /** "Ekran ayarlarını açayım mı?" */
    fun confirmDisplaySettings(): String

    /** "Sesi açıyorum." / "Sesi kısıyorum." / "Ses ayarını gösteriyorum." */
    fun volumeAnnouncement(up: Boolean?): String

    /** "Ekran ayarlarını açıyorum. Parlaklığı oradaki çubukla ayarlayabilirsiniz." */
    fun brightnessAnnouncement(): String

    /** "Ekran ayarlarını açıyorum. Yazı boyutunu "Yazı tipi boyutu" bölümünden değiştirebilirsiniz." */
    fun textSizeAnnouncement(): String

    /** "Tamam, yapmıyorum." */
    fun cancelled(): String
}
