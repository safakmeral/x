package tr.yasliasistan.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN G-35: broken, extra-field and reasoning-carrying answers (NFG-31, NFG-44). */
class ModelReplyParserTest {

    private fun step(kind: String = "tap", oge: String = "2", text: String = "null", expected: String = "\"Açıldı\"") =
        """{"eylem":"$kind","oge":$oge,"yon":null,"uzunluk":null,"metin":$text,"uygulama":null,""" +
            """"bekleme_ms":null,"neden":null,"beklenen_durum":$expected}"""

    private fun answer(
        readback: String = "\"Ekranı açayım mı?\"",
        question: String = "null",
        cannot: String = "null",
        confidence: String = "\"yuksek\"",
        steps: String = "[${step()}]",
        extra: String = "",
    ) = """{"geri_okuma":$readback,"netlestirme_sorusu":$question,"yapamam":$cannot,"guven":$confidence,"adimlar":$steps$extra}"""

    private fun rejected(text: String): ReplyError = (ModelReplyParser.parse(text) as ParseResult.Rejected).error
    private fun ok(text: String): ModelReply = (ModelReplyParser.parse(text) as ParseResult.Ok).reply

    @Test
    fun validPlanQuestionAndCannotDo() {
        val plan = ok(answer()) as ModelReply.Plan
        assertEquals("Ekranı açayım mı?", plan.readback)
        assertTrue(plan.confident)
        assertEquals("tap", plan.steps.single().action.kind)
        assertEquals(2, plan.steps.single().action.element)
        assertEquals("Açıldı", plan.steps.single().expected)

        val q = ok(answer(readback = "\"\"", question = "\"Hangi [KISI_1]?\"", steps = "[]")) as ModelReply.Question
        assertEquals("Hangi [KISI_1]?", q.text)

        val no = ok(answer(cannot = "\"Bunu yapamam.\"", steps = "[]", confidence = "\"dusuk\""))
        assertEquals(ModelReply.CannotDo("Bunu yapamam."), no)
    }

    @Test
    fun blankOrMissingNullableFieldsCountAsNull() {
        val text = """{"geri_okuma":"Açayım mı?","guven":"dusuk","netlestirme_sorusu":"","adimlar":[${step()}]}"""
        assertFalse((ok(text) as ModelReply.Plan).confident)
    }

    @Test
    fun brokenAnswersProduceNoAction() {
        assertEquals(ReplyError.NOT_AN_OBJECT, rejected("Tamam, ekranı açıyorum."))
        assertEquals(ReplyError.NOT_AN_OBJECT, rejected("""{"geri_okuma":"x","""))
        assertEquals(ReplyError.NOT_AN_OBJECT, rejected("""[1,2]"""))
        assertEquals(ReplyError.MISSING_FIELD, rejected("""{"guven":"yuksek","adimlar":[]}"""))
        assertEquals(ReplyError.WRONG_TYPE, rejected(answer(steps = "\"tap 2\"")))
        assertEquals(ReplyError.WRONG_TYPE, rejected(answer(steps = "[${step(oge = "\"2\"")}]")))
        assertEquals(ReplyError.WRONG_TYPE, rejected(answer(steps = "[${step(oge = "2.5")}]")))
        assertEquals(ReplyError.WRONG_TYPE, rejected(answer(steps = "[${step(oge = "-1")}]")))
        assertEquals(ReplyError.WRONG_TYPE, rejected(answer(readback = "42")))
        assertEquals(ReplyError.BAD_CONFIDENCE, rejected(answer(confidence = "\"orta\"")))
        assertEquals(ReplyError.MISSING_FIELD, rejected(answer(steps = "[${step(expected = "null")}]")))
        assertEquals(ReplyError.TOO_LONG, rejected(answer(readback = "\"${"a".repeat(301)}\"")))
        assertEquals(ReplyError.TOO_MANY_STEPS, rejected(answer(steps = (1..11).joinToString(",", "[", "]") { step() })))
    }

    @Test
    fun extraFieldsAreRejected() {
        assertEquals(ReplyError.UNKNOWN_FIELD, rejected(answer(extra = ""","komut":"tümünü sil"""")))
        val stepWithExtra = step().removeSuffix("}") + ""","x":10,"y":20}"""
        assertEquals(ReplyError.UNKNOWN_FIELD, rejected(answer(steps = "[$stepWithExtra]")))
    }

    @Test
    fun exactlyOneKindAndAtMostOneQuestion() {
        assertEquals(ReplyError.NOT_EXACTLY_ONE, rejected(answer(question = "\"Kime?\"")))
        assertEquals(ReplyError.NOT_EXACTLY_ONE, rejected(answer(cannot = "\"Olmaz\"")))
        assertEquals(ReplyError.NOT_EXACTLY_ONE, rejected(answer(question = "\"Kime?\"", cannot = "\"Olmaz\"", steps = "[]")))
        assertEquals(ReplyError.NOT_EXACTLY_ONE, rejected(answer(steps = "[]")))
        assertEquals(ReplyError.MORE_THAN_ONE_QUESTION, rejected(answer(question = "\"Kime? Ne yazayım?\"", steps = "[]")))
        assertEquals(ReplyError.EMPTY_TEXT, rejected(answer(readback = "\" \"")))
    }

    @Test
    fun reasoningIsDroppedUnreadAndNeverReachesTheReply() {
        val secretThought = "Kullanıcı muhtemelen yalnız yaşıyor"
        val text = answer(extra = ""","reasoning":"$secretThought","dusunce":"$secretThought"""")
        val plan = ok(text) as ModelReply.Plan
        assertFalse(secretThought in plan.toString())
        assertFalse(secretThought in plan.readback)
        assertTrue(plan.steps.none { secretThought in it.expected })

        val stepWithThought = step().removeSuffix("}") + ""","thought":"$secretThought"}"""
        assertTrue(ok(answer(steps = "[$stepWithThought]")) is ModelReply.Plan)
    }
}
