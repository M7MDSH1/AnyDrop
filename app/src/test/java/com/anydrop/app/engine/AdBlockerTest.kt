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
    fun aTapThatLeavesTheSiteOnlyCountsWhenItIsNotTheLinkTapped() {
        // A pop-under's redirect: the tap was on nothing, or on a link to somewhere else.
        assertTrue(AdBlocker.leavesSite("web6.topcinema.fan", "ad.randomhost.com", null))
        assertTrue(AdBlocker.leavesSite("web6.topcinema.fan", "ad.randomhost.com", "web6.topcinema.fan"))
        // The link tapped, another page of the same site, or nothing known to compare.
        assertFalse(AdBlocker.leavesSite("news.example.com", "www.wikipedia.org", "en.wikipedia.org"))
        assertFalse(AdBlocker.leavesSite("web6.topcinema.fan", "www.topcinema.fan", null))
        assertFalse(AdBlocker.leavesSite(null, "ad.randomhost.com", null))
        assertFalse(AdBlocker.leavesSite("example.com", null, null))
    }

    @Test
    fun aLinkOnTheSiteThatForwardsElsewhereWithoutSayingSo_isAnAd() {
        val page = "https://videos.example.com/watch/42"
        // The site's own ad link: an id, and the advertiser's address as the answer.
        assertTrue(AdBlocker.hiddenForward(page, "https://videos.example.com/api/go?t=eyJhIjo3OH0&s=ad7bbf26", "https://203.0.113.9:63776/?cid=1591905"))
        assertTrue(AdBlocker.hiddenForward(page, "https://example.com/go/78", "https://www.casino-ad.vip/?cid=5"))
    }

    @Test
    fun honestForwardsPass() {
        val page = "https://www.example.com/results"
        // It names where it goes, as search and social sites write their outbound links.
        assertFalse(AdBlocker.hiddenForward(page, "https://www.example.com/url?q=https%3A%2F%2Fen.wikipedia.org%2Fwiki%2FCat", "https://en.wikipedia.org/wiki/Cat"))
        assertFalse(AdBlocker.hiddenForward(page, "https://l.example.com/l.php?u=https://news.site.org/a", "https://www.site.org/a"))
        // A sign-in: the other site is told where to come back to.
        assertFalse(AdBlocker.hiddenForward(page, "https://www.example.com/auth/start", "https://accounts.idp.net/o/oauth2?redirect_uri=https%3A%2F%2Fwww.example.com%2Fcallback"))
        // The same name under another ending, or as a part of the other address.
        assertFalse(AdBlocker.hiddenForward("https://www.bbc.com/news", "https://www.bbc.com/go/1", "https://www.bbc.co.uk/news"))
        assertFalse(AdBlocker.hiddenForward("https://www.google.com/", "https://www.google.com/x", "https://www.google.com.sa/"))
        assertFalse(AdBlocker.hiddenForward(page, "https://www.example.com/blog", "https://example.substack.com/"))
    }

    @Test
    fun forwardsThatAreNotTheSitesOwnDoing_areNotJudged() {
        val page = "https://www.example.com/post"
        // Still on the site.
        assertFalse(AdBlocker.hiddenForward(page, "https://www.example.com/a", "https://m.example.com/a"))
        // A link to another site that forwards on (a short link): leaving was the tap's choice.
        assertFalse(AdBlocker.hiddenForward(page, "https://sho.rt/abc", "https://www.wikipedia.org/"))
        // No link the page started (an address typed, Back, a form): nothing to compare.
        assertFalse(AdBlocker.hiddenForward(page, null, "https://www.wikipedia.org/"))
        assertFalse(AdBlocker.hiddenForward(null, "https://www.example.com/go/1", "https://www.wikipedia.org/"))
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
