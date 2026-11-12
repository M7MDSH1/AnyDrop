package com.anydrop.app.engine

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * The in-app browser's ad blocker. It works on the network only and adds nothing to
 * the page, which is what keeps it quiet:
 *
 *  - Requests to ad servers are answered here with an empty "200 OK" of the kind the
 *    page asked for, so the page sees a script that loaded, not a failed request.
 *  - The few ad scripts pages commonly test for (AdSense, Google Publisher Tag,
 *    Amazon bids, the YouTube embed's ad_status) get a small stand-in that defines
 *    what the real script would, without showing an ad.
 *  - Nothing on the page is hidden or injected, so "bait element" checks find the
 *    page untouched.
 *
 * Pages that measure the ad itself (its size or what is drawn in it) can still tell.
 */
object AdBlocker {

    /** True when [host] is an ad server and the page is not that company's own site. */
    fun isAd(host: String?, pageHost: String?): Boolean {
        val h = host?.lowercase()?.trimEnd('.') ?: return false
        if (h.isEmpty()) return false
        val page = pageHost?.lowercase()
        if (page != null && siteOf(page) == siteOf(h)) return false
        if (matchesList(h)) return true
        // Self-hosted ad servers: ads.example.com, adserver.example.com...
        val first = h.substringBefore('.')
        return h.count { it == '.' } >= 2 && first in AD_LABELS
    }

    private fun matchesList(host: String): Boolean {
        var domain = host
        while (true) {
            if (domain in HOSTS) return true
            val dot = domain.indexOf('.')
            if (dot < 0) return false
            domain = domain.substring(dot + 1)
        }
    }

    /**
     * The site a host belongs to ("a.b.example.com" -> "example.com").
     * shortcut: knows only the common two-part endings (co.uk, com.au...), switch to
     * the public suffix list if a wrong match ever matters.
     */
    fun siteOf(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val twoPart = labels.last().length == 2 && labels[labels.size - 2] in SECOND_LEVEL
        return labels.takeLast(if (twoPart) 3 else 2).joinToString(".")
    }

    /** The reply that stands in for a blocked request. */
    fun blockedResponse(request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        val path = url.path.orEmpty().lowercase()
        val accept = request.requestHeaders.entries
            .firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value.orEmpty().lowercase()
        val stub = standIn(url.host.orEmpty().lowercase(), path)
        val (mime, body) = when {
            stub != null -> "application/javascript" to stub.toByteArray()
            accept.startsWith("text/html") -> "text/html" to EMPTY_PAGE
            accept.startsWith("image/") || IMAGE_ENDINGS.any { path.endsWith(it) } -> "image/gif" to CLEAR_GIF
            accept.startsWith("text/css") || path.endsWith(".css") -> "text/css" to NOTHING
            path.endsWith(".json") -> "application/json" to "{}".toByteArray()
            else -> "application/javascript" to NOTHING
        }
        // Scripts read these replies across sites, so answer like a server that allows it.
        val headers = HashMap<String, String>()
        val origin = request.requestHeaders.entries.firstOrNull { it.key.equals("Origin", ignoreCase = true) }?.value
        if (origin.isNullOrBlank() || origin == "null") {
            headers["Access-Control-Allow-Origin"] = "*"
        } else {
            headers["Access-Control-Allow-Origin"] = origin
            headers["Access-Control-Allow-Credentials"] = "true"
        }
        return WebResourceResponse(mime, "utf-8", 200, "OK", headers, ByteArrayInputStream(body))
    }

    /** A stand-in script for the ad scripts pages check for, or null. */
    internal fun standIn(host: String, path: String): String? = when {
        path.endsWith("/adsbygoogle.js") || path.endsWith("/show_ads.js") -> ADSENSE_JS
        path.endsWith("/gpt.js") && (host.endsWith("doubleclick.net") || host.endsWith("googletagservices.com")) -> GPT_JS
        path.endsWith("/apstag.js") -> APSTAG_JS
        path.endsWith("/instream/ad_status.js") -> "window.google_ad_status = 1;"
        else -> null
    }

    private val NOTHING = ByteArray(0)
    private val EMPTY_PAGE = "<!doctype html><html><head></head><body></body></html>".toByteArray()
    private val IMAGE_ENDINGS = listOf(".gif", ".png", ".jpg", ".jpeg", ".webp")

    // A 1x1 transparent GIF.
    private val CLEAR_GIF = byteArrayOf(
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x21, 0xF9.toByte(), 0x04, 0x01, 0x00,
        0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02,
        0x44, 0x01, 0x00, 0x3B
    )

    // AdSense: the queue object exists and every ad slot is marked as answered, empty.
    private val ADSENSE_JS = """
(function () {
  var w = window, queued = w.adsbygoogle;
  if (queued && queued.loaded) return;
  var n = 0;
  var settle = function () {
    var slots = document.querySelectorAll('ins.adsbygoogle:not([data-adsbygoogle-status])');
    for (var i = 0; i < slots.length; i++) {
      var slot = slots[i];
      slot.setAttribute('data-adsbygoogle-status', 'done');
      slot.setAttribute('data-ad-status', 'unfilled');
      var frame = document.createElement('iframe');
      frame.id = 'aswift_' + (n++);
      frame.setAttribute('data-load-complete', 'true');
      frame.style.cssText = 'width:1px!important;height:1px!important;border:0!important;';
      slot.appendChild(frame);
    }
  };
  w.adsbygoogle = { loaded: true, length: 0, push: function () { settle(); return 1; } };
  if (queued && queued.length) for (var i = 0; i < queued.length; i++) w.adsbygoogle.push(queued[i]);
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', settle); else settle();
})();
""".trimIndent()

    // Google Publisher Tag: the whole API answers, every slot "renders" empty.
    private val GPT_JS = """
(function () {
  var w = window, g = w.googletag = w.googletag || {};
  if (g.apiReady) return;
  var queue = g.cmd || [];
  var listeners = {};
  var slots = [];
  var chain = function (target, names) {
    names.forEach(function (name) { target[name] = function () { return target; }; });
    return target;
  };
  var fire = function (name, slot) {
    (listeners[name] || []).slice().forEach(function (fn) {
      try {
        fn({ isEmpty: true, slot: slot, serviceName: 'publisher_ads', size: null, advertiserId: null,
          campaignId: null, creativeId: null, lineItemId: null, sourceAgnosticCreativeId: null,
          sourceAgnosticLineItemId: null, isBackfill: false });
      } catch (e) {}
    });
  };
  var render = function (list) {
    (list || slots).forEach(function (slot) {
      setTimeout(function () {
        fire('slotRequested', slot); fire('slotResponseReceived', slot); fire('slotRenderEnded', slot);
      }, 1);
    });
  };
  var makeSlot = function (path, size, id) {
    var targeting = {};
    var slot = chain({}, ['addService', 'defineSizeMapping', 'setCollapseEmptyDiv', 'setCategoryExclusion',
      'clearCategoryExclusions', 'setClickUrl', 'setForceSafeFrame', 'setSafeFrameConfig', 'set', 'setConfig',
      'updateTargetingFromMap']);
    slot.setTargeting = function (k, v) { targeting[k] = [].concat(v); return slot; };
    slot.clearTargeting = function (k) { if (k) delete targeting[k]; else targeting = {}; return slot; };
    slot.getTargeting = function (k) { return targeting[k] || []; };
    slot.getTargetingKeys = function () { return Object.keys(targeting); };
    slot.getTargetingMap = function () { return targeting; };
    slot.getAdUnitPath = function () { return path || ''; };
    slot.getSlotElementId = function () { return id || ''; };
    slot.getSlotId = function () { return { getId: function () { return id || ''; }, getName: function () { return path || ''; }, getDomId: function () { return id || ''; } }; };
    slot.getSizes = function () { return []; };
    slot.getCategoryExclusions = function () { return []; };
    slot.getAttributeKeys = function () { return []; };
    slot.getResponseInformation = function () { return null; };
    slot.getHtml = function () { return ''; };
    slot.get = function () { return null; };
    slots.push(slot);
    return slot;
  };
  var service = function () {
    var targeting = {};
    var s = chain({}, ['enableSingleRequest', 'enableLazyLoad', 'disableInitialLoad', 'collapseEmptyDivs',
      'setCentering', 'setPrivacySettings', 'setRequestNonPersonalizedAds', 'setPublisherProvidedId',
      'updateCorrelator', 'enableVideoAds', 'setVideoContent', 'setLocation', 'setForceSafeFrame',
      'setSafeFrameConfig', 'set', 'setCookieOptions', 'setTagForChildDirectedTreatment',
      'setTagForUnderAgeOfConsent', 'enableAsyncRendering', 'enableSyncRendering', 'setCategoryExclusion',
      'clearCategoryExclusions', 'setContent', 'setImaContent', 'clearTagForChildDirectedTreatment']);
    s.addEventListener = function (name, fn) { (listeners[name] = listeners[name] || []).push(fn); return s; };
    s.removeEventListener = function (name, fn) {
      var l = listeners[name] || [], i = l.indexOf(fn);
      if (i >= 0) l.splice(i, 1);
      return i >= 0;
    };
    s.setTargeting = function (k, v) { targeting[k] = [].concat(v); return s; };
    s.clearTargeting = function (k) { if (k) delete targeting[k]; else targeting = {}; return s; };
    s.getTargeting = function (k) { return targeting[k] || []; };
    s.getTargetingKeys = function () { return Object.keys(targeting); };
    s.getSlots = function () { return slots.slice(); };
    s.getSlotIdMap = function () { return {}; };
    s.getAttributeKeys = function () { return []; };
    s.get = function () { return null; };
    s.isInitialLoadDisabled = function () { return false; };
    s.isSRA = function () { return true; };
    s.refresh = function (list) { render(list); };
    s.clear = function () { return true; };
    s.display = function () { render(); };
    s.defineOutOfPagePassback = function () { return null; };
    s.definePassback = function () { return null; };
    return s;
  };
  var pubads = service(), companion = service(), content = service();
  g.pubads = function () { return pubads; };
  g.companionAds = function () { return companion; };
  g.content = function () { return content; };
  g.defineSlot = makeSlot;
  g.defineOutOfPageSlot = function (path, id) { return makeSlot(path, [], id); };
  g.defineUnit = makeSlot;
  g.destroySlots = function (list) {
    (list || slots.slice()).forEach(function (slot) { var i = slots.indexOf(slot); if (i >= 0) slots.splice(i, 1); });
    return true;
  };
  g.display = function () { render(); };
  g.enableServices = function () {};
  g.disablePublisherConsole = function () {};
  g.openConsole = function () {};
  g.setAdIframeTitle = function () {};
  g.setConfig = function () {};
  g.getVersion = function () { return '2025010101'; };
  g.sizeMapping = function () {
    var m = { addSize: function () { return m; }, build: function () { return []; } };
    return m;
  };
  g.enums = { OutOfPageFormat: { REWARDED: 4, TOP_ANCHOR: 2, BOTTOM_ANCHOR: 3, INTERSTITIAL: 5 } };
  g.pubadsReady = true;
  g.apiReady = true;
  g.cmd = { push: function () {
    for (var i = 0; i < arguments.length; i++) { try { arguments[i](); } catch (e) {} }
    return 1;
  } };
  for (var i = 0; i < queue.length; i++) g.cmd.push(queue[i]);
})();
""".trimIndent()

    // Amazon bids: pages wait for the "no bids" answer before they carry on.
    private val APSTAG_JS = """
(function () {
  var w = window;
  if (w.apstag && w.apstag.fetchBids && !w.apstag._Q) return;
  w.apstag = {
    init: function () {},
    fetchBids: function (config, done) { if (typeof done === 'function') setTimeout(function () { done([]); }, 1); },
    setDisplayBids: function () {},
    targetingKeys: function () { return []; },
    renderImp: function () {},
    debug: function () {},
    rpa: function () {}, upa: function () {}, dpa: function () {}
  };
})();
""".trimIndent()

    private val SECOND_LEVEL = setOf("co", "com", "net", "org", "gov", "edu", "ac")

    private val AD_LABELS = setOf(
        "ads", "ad", "adserver", "adservice", "adsrv", "adserv", "adx", "banners", "adimg", "adcdn", "pagead", "adclick"
    )

    /** Ad servers, matched with all their subdomains. Analytics and media hosts are left alone. */
    private val HOSTS: Set<String> = """
        doubleclick.net googlesyndication.com googleadservices.com googletagservices.com 2mdn.net admob.com
        adservice.google.com adnxs.com adnxs-simple.com adsrvr.org rubiconproject.com pubmatic.com openx.net
        casalemedia.com indexww.com criteo.com criteo.net taboola.com outbrain.com outbrainimg.com
        amazon-adsystem.com media.net adform.net smartadserver.com sascdn.com teads.tv moatads.com
        advertising.com adtechus.com adtech.de yieldmo.com sharethrough.com 3lift.com triplelift.com
        lijit.com sovrn.com bidswitch.net contextweb.com gumgum.com zedo.com revcontent.com mgid.com
        adblade.com serving-sys.com adsafeprotected.com doubleverify.com innovid.com spotxchange.com
        spotx.tv springserve.com tremorhub.com adcolony.com applovin.com applvn.com unityads.unity3d.com
        vungle.com inmobi.com mopub.com smaato.net chartboost.com startappservice.com propellerads.com
        propellerclick.com popads.net popcash.net adsterra.com adsterratech.com exoclick.com exosrv.com
        exdynsrv.com juicyads.com trafficjunky.net trafficjunky.com tsyndicate.com trafficstars.com
        adxpansion.com ero-advertising.com plugrush.com hilltopads.net hilltopads.com clickadu.com
        onclickads.net onclkds.com ad-maven.com a-ads.com bidvertiser.com infolinks.com kontera.com
        chitika.com adroll.com buysellads.com carbonads.com carbonads.net yieldlove.com yieldlab.net
        adition.com ligatus.com plista.com nativo.com connatix.com vidazoo.com primis.tech sekindo.com
        aniview.com undertone.com conversantmedia.com emxdgt.com 33across.com rhythmone.com 1rx.io
        onetag-sys.com e-planning.net adkernel.com adhese.com 360yield.com improvedigital.com smartclip.net
        stickyadstv.com fwmrm.net adswizz.com tapad.com rlcdn.com crwdcntrl.net krxd.net agkn.com
        mathtag.com turn.com simpli.fi eyeota.net adsymptotic.com zemanta.com admixer.net adriver.ru
        yandexadexchange.net adfox.ru an.yandex.ru revenuehits.com adcash.com galaksion.com monetag.com
        propu.sh highperformanceformat.com highrevenuegate.com profitablegatecpm.com effectivegatecpm.com
        creativecdn.com adskeeper.com adskeeper.co.uk adnow.com engageya.com content.ad zergnet.com
        dianomi.com lockerdome.com sonobi.com districtm.io adyoulike.com omnitagjs.com seedtag.com ogury.com
        justpremium.com brealtime.com rtbhouse.com id5-sync.com liadm.com pippio.com adgrx.com bttrack.com
        betweendigital.com adtelligent.com vertamedia.com lkqd.net lkqd.com optimatic.com bfmio.com
        ads.yahoo.com gemini.yahoo.com ads.msn.com an.facebook.com ads-twitter.com ads.linkedin.com adsco.re
        adscale.de adspirit.de adtng.com adzerk.net aerserv.com atdmt.com atwola.com bannerflow.com bidr.io
        blismedia.com bluekai.com exelator.com quantserve.com scorecardresearch.com everesttech.net
        flashtalking.com gwallet.com iasds01.com intentiq.com ipredictive.com jivox.com kargo.com ladsp.com
        loopme.me mediavine.com adthrive.com mfadsrvr.com mookie1.com netmng.com nexac.com perfectmarket.com
        playground.xyz postrelease.com pro-market.net rfihub.com richaudience.com rqtrk.eu servebom.com
        servedby-buysellads.com sitescout.com smartyads.com socdm.com sonobi.net steelhousemedia.com
        stackadapt.com tidaltv.com tribalfusion.com trustx.org tynt.com unrulymedia.com w55c.net weborama.fr
        yieldoptimizer.com zeotap.com admanmedia.com adpushup.com setupad.net pubgalaxy.com snigelweb.com
        adnium.com adspyglass.com clickaine.com clickadilla.com dtscout.com popmyads.com realsrv.com
        twinrdsrv.com vdo.ai adsupply.com bidgear.com coinad.com cointraffic.io coinzilla.com
        coinzillatag.com rollerads.com richads.com evadav.com datspush.com megapu.sh notix.io adoperator.com
        trafficforce.com
    """.split(Regex("\\s+")).filter { it.isNotBlank() }.toHashSet()
}
