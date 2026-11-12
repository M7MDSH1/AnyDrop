package com.anydrop.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {

    @Test
    fun adServersAreBlockedWithTheirSubdomains() {
        assertTrue(AdBlocker.isAd("doubleclick.net", "example.com"))
        assertTrue(AdBlocker.isAd("securepubads.g.doubleclick.net", "news.example.com"))
        assertTrue(AdBlocker.isAd("PAGEAD2.googlesyndication.com", "example.com"))
        assertTrue(AdBlocker.isAd("cdn.taboola.com", null))
    }

    @Test
    fun selfHostedAdServersAreBlockedOnlyOnOtherSites() {
        assertTrue(AdBlocker.isAd("ads.somesite.com", "example.com"))
        assertTrue(AdBlocker.isAd("adservice.google.de", "example.com"))
        // A site's own "ads." host is part of that site.
        assertFalse(AdBlocker.isAd("ads.somesite.com", "www.somesite.com"))
        // "ad.nl" is a newspaper, not an ad server.
        assertFalse(AdBlocker.isAd("ad.nl", "example.com"))
    }

    @Test
    fun mediaAndEverydayHostsAreLeftAlone() {
        for (host in listOf(
            "www.youtube.com", "rr3---sn-4g5e6nzl.googlevideo.com", "scontent.cdninstagram.com", "v16-webapp.tiktok.com",
            "www.gstatic.com", "fonts.googleapis.com", "imasdk.googleapis.com", "www.google.com", "video.twimg.com",
            "www.googletagmanager.com", "www.google-analytics.com", "upload.wikimedia.org", "example.com", ""
        )) {
            assertFalse(host, AdBlocker.isAd(host, "somepage.org"))
        }
        assertFalse(AdBlocker.isAd(null, "somepage.org"))
    }

    @Test
    fun anAdCompanysOwnSiteStillWorks() {
        assertFalse(AdBlocker.isAd("static.criteo.com", "www.criteo.com"))
        assertTrue(AdBlocker.isAd("static.criteo.com", "shop.example.com"))
    }

    @Test
    fun siteOfKeepsTwoPartEndingsTogether() {
        assertEquals("example.com", AdBlocker.siteOf("a.b.example.com"))
        assertEquals("example.co.uk", AdBlocker.siteOf("www.example.co.uk"))
        assertEquals("example.com", AdBlocker.siteOf("example.com"))
    }

    @Test
    fun testedForAdScriptsGetAStandIn() {
        assertNotNull(AdBlocker.standIn("pagead2.googlesyndication.com", "/pagead/js/adsbygoogle.js"))
        assertNotNull(AdBlocker.standIn("securepubads.g.doubleclick.net", "/tag/js/gpt.js"))
        assertNotNull(AdBlocker.standIn("c.amazon-adsystem.com", "/aax2/apstag.js"))
        assertEquals("window.google_ad_status = 1;", AdBlocker.standIn("static.doubleclick.net", "/instream/ad_status.js"))
        assertNull(AdBlocker.standIn("cdn.taboola.com", "/libtrc/loader.js"))
    }
}
