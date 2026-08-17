package com.glownote.mobile.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavClientTest {
    @Test
    fun acceptsTeraCloudMkcolSuccessResponse() {
        assertTrue(isMkcolSuccessStatus(200))
        assertTrue(isMkcolSuccessStatus(201))
        assertTrue(isMkcolSuccessStatus(204))
        assertTrue(isMkcolSuccessStatus(301))
        assertTrue(isMkcolSuccessStatus(405))
    }

    @Test
    fun rejectsMkcolErrorResponses() {
        assertFalse(isMkcolSuccessStatus(401))
        assertFalse(isMkcolSuccessStatus(403))
        assertFalse(isMkcolSuccessStatus(500))
    }
}
