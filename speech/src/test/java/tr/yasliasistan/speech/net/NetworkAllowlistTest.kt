package tr.yasliasistan.speech.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.speech.whisper.WhisperModelCatalog

class NetworkAllowlistTest {

    @Test
    fun officialModelUrlsAndTheirDownloadServerAreAllowed() {
        assertTrue(NetworkAllowlist.isAllowed(WhisperModelCatalog.BASE.url))
        assertTrue(NetworkAllowlist.isAllowed(WhisperModelCatalog.TINY.url))
        assertTrue(NetworkAllowlist.isAllowed("https://us.aws.cdn.hf.co/xet-bridge-us/abc?Expires=1"))
        assertTrue(NetworkAllowlist.isAllowed("https://cdn-lfs.huggingface.co/repos/x"))
    }

    @Test
    fun onlyTheEnteredWorkersDevProxyHostIsAdded() {
        val endpoint = "https://vekil.ornek.workers.dev/v1/generate"
        try {
            assertFalse("closed until configured", NetworkAllowlist.isAllowed(endpoint))
            assertTrue(NetworkAllowlist.setProxyHost("vekil.ornek.workers.dev"))
            assertTrue(NetworkAllowlist.isAllowed(endpoint))
            assertFalse("only that one host", NetworkAllowlist.isAllowed("https://baska.ornek.workers.dev/v1/generate"))
            assertFalse("no cleartext", NetworkAllowlist.isAllowed("http://vekil.ornek.workers.dev/v1/generate"))

            assertFalse("no free domain", NetworkAllowlist.setProxyHost("example.com"))
            assertFalse("a rejected host also closes the old one", NetworkAllowlist.isAllowed(endpoint))
            assertFalse(NetworkAllowlist.isAllowed("https://example.com/v1/generate"))
        } finally {
            NetworkAllowlist.setProxyHost(null)
        }
        assertFalse(NetworkAllowlist.isAllowed(endpoint))
    }

    @Test
    fun everythingElseIsRejected() {
        listOf(
            "http://huggingface.co/ggerganov/whisper.cpp", // no cleartext
            "https://example.com/model.bin",
            "https://huggingface.co.evil.com/x",
            "https://evilhf.co/x",
            "https://evilhuggingface.co/x",
            "https://user@huggingface.co/x",
            "https://huggingface.co:8443/x",
            "ftp://huggingface.co/x",
            "not a url",
            "",
        ).forEach { assertFalse(it, NetworkAllowlist.isAllowed(it)) }
    }
}
