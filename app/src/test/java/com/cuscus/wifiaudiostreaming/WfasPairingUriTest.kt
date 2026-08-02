package com.cuscus.wifiaudiostreaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WfasPairingUriTest {

    private val key = "N1e4Yx7Qp2Rk9Tz0AbCdEfGhIjKlMnOpQrStUvWxYz0"
    private val now = 1_800_000_000L

    @Test
    fun buildAndParseUnicastRoundTrip() {
        val uri = WfasPairingUri.build(
            ip = "192.168.1.10",
            port = 50005,
            mode = WfasPairingUri.MODE_UNICAST,
            keyBase64 = key,
            expEpochSeconds = now + 120
        )
        val p = WfasPairingUri.parse(uri, now)
        assertNotNull(p)
        assertEquals("192.168.1.10", p!!.ip)
        assertEquals(50005, p.port)
        assertEquals(WfasPairingUri.MODE_UNICAST, p.mode)
        assertEquals(key, p.keyBase64)
        assertEquals(now + 120, p.expEpochSeconds)
        assertNull(p.mcastEpoch)
        assertEquals(2, p.version)
    }

    @Test
    fun buildAndParseMulticastWithEpoch() {
        val uri = WfasPairingUri.build(
            ip = "239.255.0.1",
            port = 50005,
            mode = WfasPairingUri.MODE_MULTICAST,
            keyBase64 = key,
            expEpochSeconds = now + 120,
            mcastEpoch = 42L
        )
        val p = WfasPairingUri.parse(uri, now)
        assertNotNull(p)
        assertTrue(p!!.isMulticast)
        assertEquals(42L, p.mcastEpoch)
        assertEquals("239.255.0.1", p.ip)
    }

    @Test
    fun unicastIgnoresEpoch() {
        val uri = WfasPairingUri.build(
            ip = "192.168.1.10",
            port = 50005,
            mode = WfasPairingUri.MODE_UNICAST,
            keyBase64 = key,
            expEpochSeconds = now + 120,
            mcastEpoch = 7L
        )
        assertTrue(!uri.contains("epoch="))
        assertNull(WfasPairingUri.parse(uri, now)!!.mcastEpoch)
    }

    @Test
    fun ipv6AddressSurvivesEncoding() {
        val uri = WfasPairingUri.build(
            ip = "[fd00::1]",
            port = 50005,
            mode = WfasPairingUri.MODE_UNICAST,
            keyBase64 = key,
            expEpochSeconds = now + 120
        )
        assertEquals("[fd00::1]", WfasPairingUri.parse(uri, now)!!.ip)
    }

    @Test
    fun expiredCodeIsRejected() {
        val uri = WfasPairingUri.build(
            ip = "192.168.1.10",
            port = 50005,
            mode = WfasPairingUri.MODE_UNICAST,
            keyBase64 = key,
            expEpochSeconds = now - 120
        )
        assertNull(WfasPairingUri.parse(uri, now))
        assertTrue(WfasPairingUri.isExpiredUri(uri, now))
    }

    @Test
    fun clockSkewToleranceAccepted() {
        val uri = WfasPairingUri.build(
            ip = "192.168.1.10",
            port = 50005,
            mode = WfasPairingUri.MODE_UNICAST,
            keyBase64 = key,
            expEpochSeconds = now - 10
        )
        assertNotNull(WfasPairingUri.parse(uri, now))
    }

    @Test
    fun unsupportedVersionIsRejected() {
        val uri = "wifiaudio://pair?ip=192.168.1.10&port=50005&mode=unicast&key=$key&exp=${now + 120}&v=99"
        assertNull(WfasPairingUri.parse(uri, now))
        assertTrue(!WfasPairingUri.isExpiredUri(uri, now))
    }

    @Test
    fun wrongSchemeIsRejected() {
        val uri = "wifiaudio2://pair?ip=192.168.1.10&port=50005&mode=unicast&key=$key&exp=${now + 120}&v=2"
        assertNull(WfasPairingUri.parse(uri, now))
    }

    @Test
    fun wrongHostIsRejected() {
        val uri = "wifiaudio://connect?ip=192.168.1.10&port=50005&mode=unicast&key=$key&exp=${now + 120}&v=2"
        assertNull(WfasPairingUri.parse(uri, now))
    }

    @Test
    fun missingKeyIsRejected() {
        val uri = "wifiaudio://pair?ip=192.168.1.10&port=50005&mode=unicast&exp=${now + 120}&v=2"
        assertNull(WfasPairingUri.parse(uri, now))
    }

    @Test
    fun missingIpIsRejected() {
        val uri = "wifiaudio://pair?port=50005&mode=unicast&key=$key&exp=${now + 120}&v=2"
        assertNull(WfasPairingUri.parse(uri, now))
    }

    @Test
    fun missingExpIsRejected() {
        val uri = "wifiaudio://pair?ip=192.168.1.10&port=50005&mode=unicast&key=$key&v=2"
        assertNull(WfasPairingUri.parse(uri, now))
    }

    @Test
    fun unknownModeIsRejected() {
        val uri = "wifiaudio://pair?ip=192.168.1.10&port=50005&mode=broadcast&key=$key&exp=${now + 120}&v=2"
        assertNull(WfasPairingUri.parse(uri, now))
    }

    @Test
    fun outOfRangePortIsRejected() {
        val uri = "wifiaudio://pair?ip=192.168.1.10&port=70000&mode=unicast&key=$key&exp=${now + 120}&v=2"
        assertNull(WfasPairingUri.parse(uri, now))
    }

    @Test
    fun keyWithIllegalCharactersIsRejected() {
        val uri = "wifiaudio://pair?ip=192.168.1.10&port=50005&mode=unicast&key=short%2Fkey&exp=${now + 120}&v=2"
        assertNull(WfasPairingUri.parse(uri, now))
    }

    @Test
    fun malformedGarbageIsRejected() {
        assertNull(WfasPairingUri.parse("not a uri at all", now))
        assertNull(WfasPairingUri.parse("", now))
        assertNull(WfasPairingUri.parse("wifiaudio://pair", now))
    }

    @Test
    fun httpsAppLinkIsAccepted() {
        val uri = WfasPairingUri.buildAppLink(
            ip = "192.168.1.10",
            port = 50005,
            mode = WfasPairingUri.MODE_UNICAST,
            keyBase64 = key,
            expEpochSeconds = now + 120
        )
        val p = WfasPairingUri.parse(uri, now)
        assertNotNull(p)
        assertEquals("192.168.1.10", p!!.ip)
    }

    @Test
    fun appLinkCarriesTheFieldsInTheFragment() {
        val uri = WfasPairingUri.buildAppLink(
            ip = "192.168.1.10",
            port = 50005,
            mode = WfasPairingUri.MODE_UNICAST,
            keyBase64 = key,
            expEpochSeconds = now + 120,
            italian = false
        )
        assertTrue(uri.contains('#'))
        assertFalse(uri.substringBefore('#').contains('?'))
        assertEquals(
            "https://${WfasPairingUri.APPLINK_HOST}${WfasPairingUri.APPLINK_PATH}",
            uri.substringBefore('#')
        )
        assertFalse(uri.substringBefore('#').contains(key))
    }

    @Test
    fun legacyQueryStringAppLinkStillParses() {
        val uri = "https://${WfasPairingUri.APPLINK_HOST}${WfasPairingUri.APPLINK_PATH}" +
            "?ip=192.168.1.10&port=50005&mode=unicast&key=$key&exp=${now + 120}&v=2"
        val p = WfasPairingUri.parse(uri, now)
        assertNotNull(p)
        assertEquals(50005, p!!.port)
    }

    @Test
    fun italianAppLinkFragmentParses() {
        val uri = WfasPairingUri.buildAppLink(
            ip = "239.255.0.1",
            port = 50005,
            mode = WfasPairingUri.MODE_MULTICAST,
            keyBase64 = key,
            expEpochSeconds = now + 120,
            mcastEpoch = 7L,
            italian = true
        )
        assertTrue(uri.startsWith("https://${WfasPairingUri.APPLINK_HOST}${WfasPairingUri.APPLINK_PATH_IT}#"))
        val p = WfasPairingUri.parse(uri, now)
        assertNotNull(p)
        assertEquals(7L, p!!.mcastEpoch)
        assertTrue(p.isMulticast)
    }

    @Test
    fun apexHostIsAcceptedLikeWww() {
        val fields = "ip=192.168.1.10&port=50005&mode=unicast&key=$key&exp=${now + 120}&v=2"
        assertNotNull(WfasPairingUri.parse("https://marcomorosi.eu${WfasPairingUri.APPLINK_PATH}#$fields", now))
        assertNotNull(WfasPairingUri.parse("https://www.marcomorosi.eu${WfasPairingUri.APPLINK_PATH}#$fields", now))
    }

    @Test
    fun trailingSlashIsAccepted() {
        val fields = "ip=192.168.1.10&port=50005&mode=unicast&key=$key&exp=${now + 120}&v=2"
        val p = WfasPairingUri.parse("https://www.marcomorosi.eu${WfasPairingUri.APPLINK_PATH}/#$fields", now)
        assertNotNull(p)
        assertEquals("192.168.1.10", p!!.ip)
    }

    @Test
    fun foreignHttpsHostIsRejected() {
        val uri = "https://evil.example/wifi-audio-streaming/pair?ip=192.168.1.10&port=50005&mode=unicast&key=$key&exp=${now + 120}&v=2"
        assertNull(WfasPairingUri.parse(uri, now))
    }
}
