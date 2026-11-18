package tr.yasliasistan.core.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandsTest {

    private fun assertCommand(expected: VoiceCommand, vararg texts: String) {
        texts.forEach { assertEquals("\"$it\"", expected, VoiceCommands.match(it)) }
    }

    @Test
    fun stopVariants() = assertCommand(VoiceCommand.STOP, "dur", "Dur!", "DUR", "dur dur", "Durdur.", "lütfen dur", "sus", "yeter artık", "vazgeç", "iptal et")

    @Test
    fun rememberedVariants() = assertCommand(
        VoiceCommand.REMEMBERED,
        "Tamam, hatırladım.", "tamam hatirladim", "hatırladım", "tamam ben hatırladım", "tamam hatırladın",
    )

    @Test
    fun restartVariants() = assertCommand(VoiceCommand.RESTART, "baştan anlat", "en baştan anlat", "Baştan başla.", "lütfen baştan anlat", "bastann anlat")

    @Test
    fun repeatVariants() = assertCommand(
        VoiceCommand.REPEAT,
        "tekrar söyle", "Tekrar söyler misin?", "bir daha söyle", "tekrarla", "ne dedin", "anlamadım", "efendim?",
    )

    @Test
    fun whyVariants() = assertCommand(VoiceCommand.WHY, "neden", "Neden?", "niye", "niçin", "neden ki", "peki neden")

    @Test
    fun slowerVariants() = assertCommand(VoiceCommand.SLOWER, "yavaş konuş", "biraz yavaş", "daha yavaş konuşur musun", "yavaş oku")

    @Test
    fun ordinaryCommandsAreNotFixedCommands() {
        listOf(
            "", "  ", "durumu söyle", "annemi ara", "tamam", "evet", "hayır", "ayarları aç", "tekrar et",
            "aynısını tekrar yap", "biraz daha anlat", "neden aradın kızımı bilmiyorum ki ben", "durum",
            "dur bakalım annemi ara", "kesinlikle", "yavaşça kapat",
        ).forEach { assertNull("\"$it\"", VoiceCommands.match(it)) }
    }

    @Test
    fun shortWordsMustBeExact() {
        assertNull(VoiceCommands.match("bur"))
        assertNull(VoiceCommands.match("durun da"))
        assertNull(VoiceCommands.match("nedem"))
    }

    @Test
    fun oneEditDistance() {
        assertTrue(VoiceCommands.withinOneEdit("hatirladim", "hatirladin"))
        assertTrue(VoiceCommands.withinOneEdit("bastan", "bastann"))
        assertTrue(VoiceCommands.withinOneEdit("anlat", "anat"))
        assertFalse(VoiceCommands.withinOneEdit("anlat", "anla t x"))
        assertFalse(VoiceCommands.withinOneEdit("hatirladim", "hatirladik k"))
    }
}
