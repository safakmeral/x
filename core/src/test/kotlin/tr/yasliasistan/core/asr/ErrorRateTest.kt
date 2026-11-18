package tr.yasliasistan.core.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tr.yasliasistan.core.asr.NormalizationLevel.BASIC
import tr.yasliasistan.core.asr.NormalizationLevel.RAW
import tr.yasliasistan.core.asr.NormalizationLevel.TURKISH

class ErrorRateTest {

    @Test
    fun identicalTextHasNoErrors() {
        val c = ErrorRate.words("Ayarları aç", "Ayarları aç", RAW)
        assertEquals(EditCounts(0, 0, 0, 2), c)
        assertEquals(0.0, c.rate!!, 0.0)
    }

    @Test
    fun countsSubstitutionDeletionInsertion() {
        // "the cat sat on the mat" vs "the cat sit on mat today": 3 edits (ties go to substitutions)
        val c = ErrorRate.align("the cat sat on the mat".split(" "), "the cat sit on mat today".split(" "))
        assertEquals(3, c.errors)
        assertEquals(6, c.referenceLength)
        assertEquals(0.5, c.rate!!, 1e-9)
        assertEquals(EditCounts(1, 1, 0, 4), ErrorRate.align("a b c d".split(" "), "a x d".split(" ")))
        assertEquals(EditCounts(0, 0, 2, 2), ErrorRate.align("a b".split(" "), "x a b y".split(" ")))
    }

    @Test
    fun emptyHypothesisIsAllDeletions() {
        val c = ErrorRate.words("Mehmet'e mesaj gönder", "", BASIC)
        assertEquals(EditCounts(0, 3, 0, 3), c)
        assertEquals(1.0, c.rate!!, 0.0)
    }

    @Test
    fun emptyReferenceHasNoRateButCountsInsertions() {
        val c = ErrorRate.words("", "altyazı M.K.", BASIC) // -> "altyazı m k"
        assertEquals(3, c.insertions)
        assertNull(c.rate)
    }

    @Test
    fun werCanExceedOne() {
        val c = ErrorRate.words("dur", "dur dur dur dur", BASIC)
        assertEquals(3.0, c.rate!!, 1e-9)
    }

    @Test
    fun levelsGetMoreLenient() {
        val ref = "Ayşe'yi ara."
        val hyp = "ayseyi ara"
        assertEquals(2, ErrorRate.words(ref, hyp, RAW).errors) // case/punctuation + diacritics
        assertEquals(1, ErrorRate.words(ref, hyp, BASIC).errors) // only ş vs s
        assertEquals(0, ErrorRate.words(ref, hyp, TURKISH).errors)
    }

    @Test
    fun numbersWrittenAsDigitsMatchWordsAtTurkishLevel() {
        val ref = "Saat on beş otuzda ilacımı hatırlat"
        val hyp = "Saat 15.30'da ilacımı hatırlat."
        assertEquals(0, ErrorRate.words(ref, hyp, TURKISH).errors)
    }

    @Test
    fun charErrorRateCountsLetters() {
        val c = ErrorRate.chars("sesi kıs", "sesi kis", BASIC)
        assertEquals(1, c.errors)
        assertEquals(8, c.referenceLength)
        assertEquals(0, ErrorRate.chars("sesi kıs", "sesi kis", TURKISH).errors)
    }

    @Test
    fun countsAddUpForCorpusRate() {
        val total = ErrorRate.words("bir iki", "bir", BASIC) + ErrorRate.words("a b c d", "a b c d", BASIC)
        assertEquals(EditCounts(0, 1, 0, 6), total)
    }
}
