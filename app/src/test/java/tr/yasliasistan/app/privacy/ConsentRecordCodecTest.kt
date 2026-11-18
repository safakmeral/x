package tr.yasliasistan.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tr.yasliasistan.core.consent.ConsentDecision
import tr.yasliasistan.core.consent.ConsentPurpose
import tr.yasliasistan.core.consent.ConsentRecord

class ConsentRecordCodecTest {

    @Test
    fun roundTripKeepsTimeAndVersion() {
        val record = ConsentRecord(ConsentPurpose.CLOUD_TRANSFER, ConsentDecision.GRANTED, 1_763_400_000_000L, 2)
        assertEquals(record, ConsentRecordCodec.decode(record.purpose, ConsentRecordCodec.encode(record)))
    }

    @Test
    fun malformedValuesMeanNoConsent() {
        val p = ConsentPurpose.CLOUD_TRANSFER
        listOf(null, "", "GRANTED", "GRANTED|x|1", "GRANTED|1|y", "YES|1|1", "GRANTED|1|1|extra").forEach {
            assertNull(it, ConsentRecordCodec.decode(p, it))
        }
    }
}
