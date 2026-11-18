package tr.yasliasistan.speech.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileTest {

    private val helioG99 = """
        processor	: 0
        BogoMIPS	: 26.00
        Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp
        CPU part	: 0xd05

        processor	: 6
        Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp
        CPU part	: 0xd0b
    """.trimIndent()

    private val cortexA53 = """
        processor	: 0
        Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 cpuid
    """.trimIndent()

    @Test
    fun modernPhoneGetsFastLibrary() {
        val features = DeviceProfile.cpuFeatures(helioG99)
        assertTrue("asimddp" in features)
        assertEquals(DeviceProfile.FAST_LIBRARY, DeviceProfile.libraryFor(features))
    }

    @Test
    fun oldArmv8PhoneGetsBaselineLibrary() {
        assertEquals(DeviceProfile.BASE_LIBRARY, DeviceProfile.libraryFor(DeviceProfile.cpuFeatures(cortexA53)))
    }

    @Test
    fun mixedCoresUseCommonFeaturesOnly() {
        val mixed = helioG99 + "\nprocessor : 7\nFeatures : fp asimd asimdhp\n"
        val features = DeviceProfile.cpuFeatures(mixed)
        assertFalse("asimddp" in features)
        assertEquals(DeviceProfile.BASE_LIBRARY, DeviceProfile.libraryFor(features))
    }

    @Test
    fun unreadableCpuinfoFallsBackToBaseline() {
        assertEquals(emptySet<String>(), DeviceProfile.cpuFeatures(""))
        assertEquals(DeviceProfile.BASE_LIBRARY, DeviceProfile.libraryFor(emptySet()))
    }

    @Test
    fun threadCountIsHalfTheCoresBetweenOneAndFour() {
        assertEquals(1, DeviceProfile.threadsFor(1))
        assertEquals(1, DeviceProfile.threadsFor(2))
        assertEquals(2, DeviceProfile.threadsFor(4))
        assertEquals(3, DeviceProfile.threadsFor(6))
        assertEquals(4, DeviceProfile.threadsFor(8))
        assertEquals(4, DeviceProfile.threadsFor(12))
    }

    @Test
    fun memoryPressureUnloadsOnlyOnRealPressure() {
        assertTrue(MemoryPolicy.shouldUnload(10)) // RUNNING_LOW
        assertTrue(MemoryPolicy.shouldUnload(15)) // RUNNING_CRITICAL
        assertTrue(MemoryPolicy.shouldUnload(40)) // BACKGROUND
        assertTrue(MemoryPolicy.shouldUnload(80)) // COMPLETE
        assertFalse(MemoryPolicy.shouldUnload(5))  // RUNNING_MODERATE
        assertFalse(MemoryPolicy.shouldUnload(20)) // UI_HIDDEN: just no visible activity
    }
}
