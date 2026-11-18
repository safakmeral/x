package tr.yasliasistan.core.context

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.text.TurkishText

class ContextWordsTest {

    private fun t(text: String) = TurkishText.tokens(text)

    @Test
    fun whyQuestions() {
        listOf("Neden?", "niye", "Niçin?", "neden böyle yaptın", "peki neden sordun", "bunu neden açtın", "nedenini söyler misin")
            .forEach { assertTrue(it, ContextWords.isWhy(t(it))) }
        // A real command that starts with "neden" is not a question about the last answer.
        listOf("neden ekran karanlık", "Ayşe'yi ara", "")
            .forEach { assertFalse(it, ContextWords.isWhy(t(it))) }
    }

    @Test
    fun detailRequests() {
        listOf("biraz daha anlat", "ayrıntılı anlat", "açıklar mısın", "daha açık söyle")
            .forEach { assertTrue(it, ContextWords.isDetail(t(it))) }
        listOf("baştan anlat", "daha", "sesi aç")
            .forEach { assertFalse(it, ContextWords.isDetail(t(it))) }
    }

    @Test
    fun repeatRequests() {
        listOf("tekrar", "Aynısını yap", "bir daha", "bir daha yap", "yine yap", "tekrar et", "aynı şeyi yap", "tekrarla lütfen")
            .forEach { assertTrue(it, ContextWords.isRepeat(t(it))) }
        // "tekrar söyle" belongs to the fixed commands (G-17); "yapma" is a refusal.
        listOf("tekrar söyle", "yapma", "daha", "tekrar ara", "Ayşe'yi tekrar ara", "")
            .forEach { assertFalse(it, ContextWords.isRepeat(t(it))) }
    }
}
