package com.nuancun.jmcomic.mobile.source

import org.junit.Assert.assertEquals
import org.junit.Test

class JmCryptoTest {
    @Test fun legacyTokenMatchesAuditedImplementation() {
        assertEquals("81498a20feea7fbb7149c637e49702e3", JmCrypto.token(1_700_566_805L, "18comicAPP"))
    }

    @Test fun currentTokenProfileIsStable() {
        assertEquals("cce2cb071cd0cf371a2e34fd5ad66fd6", JmCrypto.token(1_700_566_805L, "185Hcomic3PAPP7R"))
    }

    @Test fun segmentationMatchesThresholdRules() {
        assertEquals(0, ImageScrambler.segmentationCount(220980, "220979", "00001"))
        assertEquals(10, ImageScrambler.segmentationCount(220980, "220980", "00001"))
        assertEquals(14, ImageScrambler.segmentationCount(220980, "421926", "00001"))
    }
}
