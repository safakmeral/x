package tr.yasliasistan.core.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Privacy by type (PLAN G-18 acceptance): metric types have no free-text field, so a transcript,
 * contact name, app content or screen text cannot be put into a metric.
 */
class MetricRecordTypeTest {

    @Test
    fun metricRecordHasOnlyNumberAndEnumFields() {
        assertOnlyNumbersAndEnums(MetricRecord::class.java)
    }

    @Test
    fun metricRowHasOnlyNumbersAndTheRecord() {
        val fields = MetricRow::class.java.instanceFields()
        assertEquals(setOf("session", "appVersion", "record"), fields.map { it.name }.toSet())
        fields.filter { it.name != "record" }.forEach { assertTrue(it.name, isNumberOrEnum(it.type)) }
        assertEquals(MetricRecord::class.java, fields.single { it.name == "record" }.type)
    }

    @Test
    fun recordFieldsAreExactlyTheKnownColumns() {
        // A new field must be reviewed for privacy and added to the CSV header on purpose.
        assertEquals(
            setOf("kind", "atMs", "durationMs", "source", "outcome", "errorCode", "count"),
            MetricRecord::class.java.instanceFields().map { it.name }.toSet(),
        )
    }

    @Test
    fun enumsCarryNoTextProperties() {
        val enums = MetricRecord::class.java.instanceFields().map { it.type }.filter { it.isEnum }
        assertTrue(enums.isNotEmpty())
        enums.forEach { type ->
            val extra = type.instanceFields().filter { it.name != "name" && it.name != "ordinal" }
            extra.forEach { assertTrue("${type.simpleName}.${it.name}", isNumberOrEnum(it.type)) }
        }
    }

    @Test
    fun sinkAcceptsOnlyARecord() {
        val method = MetricsSink::class.java.declaredMethods.single { it.name == "record" }
        assertEquals(listOf(MetricRecord::class.java), method.parameterTypes.toList())
    }

    companion object {
        private val NUMBER_TYPES = setOf(
            Int::class.javaPrimitiveType, Long::class.javaPrimitiveType,
            Int::class.javaObjectType, Long::class.javaObjectType,
        )

        fun isNumberOrEnum(type: Class<*>): Boolean = type in NUMBER_TYPES || type.isEnum

        fun Class<*>.instanceFields() = declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }

        fun assertOnlyNumbersAndEnums(type: Class<*>) {
            val fields = type.instanceFields()
            assertTrue("no fields found", fields.isNotEmpty())
            fields.forEach {
                assertTrue("${type.simpleName}.${it.name} is ${it.type.simpleName}; only numbers and enums allowed", isNumberOrEnum(it.type))
            }
        }
    }
}
