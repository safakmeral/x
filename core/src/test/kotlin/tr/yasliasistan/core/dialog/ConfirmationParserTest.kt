package tr.yasliasistan.core.dialog

import org.junit.Assert.assertEquals
import org.junit.Test
import tr.yasliasistan.core.dialog.ConfirmationParser.Answer

class ConfirmationParserTest {

    private fun check(expected: Answer, vararg inputs: String) {
        for (input in inputs) assertEquals("input='$input'", expected, ConfirmationParser.parse(input))
    }

    /** V-12: only the explicit words "evet" / "onaylıyorum" (and "onayla") are a full yes. */
    @Test
    fun yesWords() = check(
        Answer.YES,
        "evet", "Evet.", "EVET", "evet evet", "onaylıyorum", "Onaylıyorum!", "onayla",
        "evet lütfen", "İyi, evet",
    )

    /** Natural short answers of older users; the dialog accepts them only for Low risk (SAPMALAR). */
    @Test
    fun softYesWords() = check(Answer.SOFT_YES, "tamam", "olur", "peki", "Tamam.", "olur olur", "tamam lütfen", "tabii")

    @Test
    fun noWords() = check(
        Answer.NO,
        "hayır", "Hayır.", "HAYIR", "hayir", "vazgeç", "vazgec", "Vazgeçtim", "istemiyorum",
        "iptal", "yapma", "yok", "dur", "olmaz", "değil", "yanlış", "yapmayın", "yapmasın", "açma",
    )

    @Test
    fun noWinsOverYes() = check(
        Answer.NO,
        "evet, yok hayır", "tamam vazgeç", "hayır evet", "tamam ama hayır", "evet değil", "olur mu hiç, olmaz",
    )

    /** A yes with a "but" or with other words is not an explicit answer: ask once more. */
    @Test
    fun unclear() = check(
        Answer.UNCLEAR,
        "", "   ", "bilmiyorum", "ne dedin", "evetten önce", "peki nasıl", "tamam ama", "evet ama bekle",
        "yap", "onay", "peki ne yapıyorsun", "tamam hatırladım",
    )
}
