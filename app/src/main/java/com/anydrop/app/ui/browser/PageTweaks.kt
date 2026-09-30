package com.anydrop.app.ui.browser

import android.content.Intent
import android.net.Uri

/**
 * Small fixes the in-app browser applies to every page (injected at document start):
 *
 *  - Pop-ups pushing the app. TikTok's mobile site covers the feed with "Get the
 *    full app experience", and its desktop site with a "What do you want to watch"
 *    picker. Both are closed automatically (their own "Not now" / X is pressed) and
 *    hidden so they never flash. Rules are per site and keyed on the site's test
 *    hooks and component names, which change far less often than its class hashes.
 *  - Desktop mode. Desktop pages that declare `width=device-width` get laid out at
 *    phone width and come out cut off. As in Chrome's "Desktop site", the layout is
 *    pinned to [DESKTOP_WIDTH] and zoomed out to fit the screen (pinch to zoom in).
 *    Only active while the desktop user agent is set.
 *
 * TikTok's mobile site also stops loading new videos for visitors without the app and
 * turns swipes into app-install redirects ([isAppInstallRedirect] refuses those, so
 * the page at least stays put); its desktop site has no such limit, so desktop mode
 * is the way to scroll TikTok.
 */
object PageTweaks {

    const val DESKTOP_WIDTH = 1024

    val JS = """
(() => {
  if (window.__anydropTweaks) return;
  window.__anydropTweaks = true;

  const NAGS = [
    {
      host: /(^|\.)tiktok\.com$/,
      hide: [
        '[class*="DivModalContainer"]:has([data-e2e="middle-cta-container"])',
        '[role="dialog"]:has([class*="DivInterestSelectorContainer"])'
      ],
      dismiss: [
        '[data-e2e="alt-middle-cta-cancel-btn"]',
        '[role="dialog"]:has([class*="DivInterestSelectorContainer"]) [class*="DivCloseButtonWrapper"]'
      ]
    }
  ];
  const nag = NAGS.find((r) => r.host.test(location.hostname));
  if (nag) {
    const css = document.createElement('style');
    css.textContent = nag.hide.join(',') + '{display:none!important}';
    const addCss = () => (document.head || document.documentElement).appendChild(css);
    if (document.documentElement) addCss(); else document.addEventListener('DOMContentLoaded', addCss);
    let queued = false;
    const sweep = () => {
      queued = false;
      for (const selector of nag.dismiss) {
        const button = document.querySelector(selector);
        if (button) button.click();
      }
    };
    new MutationObserver(() => {
      if (!queued) { queued = true; requestAnimationFrame(sweep); }
    }).observe(document, { childList: true, subtree: true });
  }

  if (/Windows NT|Macintosh/.test(navigator.userAgent)) {
    // Fit the desktop layout to the screen; the user can still pinch to zoom in.
    const viewport = () => {
      const fit = Math.min(1, screen.width / $DESKTOP_WIDTH).toFixed(3);
      return 'width=$DESKTOP_WIDTH, initial-scale=' + fit + ', minimum-scale=' + fit;
    };
    const pin = () => {
      const head = document.head;
      if (!head) return;
      let meta = head.querySelector('meta[name="viewport"]');
      if (!meta) {
        meta = document.createElement('meta');
        meta.name = 'viewport';
        head.prepend(meta);
      }
      const want = viewport();
      if (meta.content !== want) meta.content = want;
    };
    const watchHead = () => {
      pin();
      new MutationObserver(pin).observe(document.head, {
        childList: true, subtree: true, attributes: true, attributeFilter: ['content', 'name']
      });
    };
    if (document.head) watchHead();
    else {
      const wait = new MutationObserver(() => {
        if (document.head) { wait.disconnect(); watchHead(); }
      });
      wait.observe(document, { childList: true, subtree: true });
    }
    // Rotating the phone changes the screen width: refit.
    addEventListener('orientationchange', () => setTimeout(pin, 300));
  }
})();
""".trimIndent()

    /**
     * App-install redirect services (AppsFlyer OneLink, Adjust). Pages send you through
     * them to reach the Play Store; TikTok does it on a swipe in its feed. They never
     * lead to web content, so the browser just stays on the page instead.
     */
    fun isAppInstallRedirect(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return host == "onelink.me" || host.endsWith(".onelink.me") ||
            host == "app.adjust.com" || host == "adj.st" || host.endsWith(".adj.st")
    }

    /**
     * Where to go for an `intent://` link a page opens. Sites use these to push you
     * into their app; only a web fallback is followed. App-store fallbacks (market://,
     * Play Store pages) are dropped, so the page simply stays where it is.
     */
    fun webFallbackFor(intentUri: String): String? {
        val fallback = runCatching { Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME) }.getOrNull()
            ?.getStringExtra("browser_fallback_url") ?: return null
        val uri = Uri.parse(fallback)
        if (uri.scheme != "https" && uri.scheme != "http") return null
        val host = uri.host.orEmpty()
        if (host == "play.google.com" || host == "apps.apple.com" || host == "itunes.apple.com") return null
        return fallback
    }
}
