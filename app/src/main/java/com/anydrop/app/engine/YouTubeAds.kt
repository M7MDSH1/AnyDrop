package com.anydrop.app.engine

/**
 * YouTube serves its video ads from its own servers, inside the same answers that carry the
 * video, so [AdBlocker]'s "refuse the ad server" has nothing to refuse. This script, run on
 * YouTube pages only (before the page's own scripts), deals with them in three layers:
 *
 *  1. The ads are taken out of what the player is told to play (the "player response").
 *  2. An ad that starts anyway is jumped to its end at once, silenced meanwhile, and its
 *     Skip button pressed.
 *  3. Ad cards in the feed and around the player are hidden, and YouTube's pop-ups over the
 *     player ("Watch in the YouTube app", "Ad blockers are not allowed") are closed.
 *
 * YouTube changes this often and fights ad blockers on purpose: expect it to need updates.
 */
object YouTubeAds {

    /** The pages the script is added to (rules for WebView's document-start scripts). */
    val ORIGINS = setOf(
        "https://youtube.com", "https://*.youtube.com",
        "https://youtube-nocookie.com", "https://*.youtube-nocookie.com"
    )

    /** True for YouTube's own pages (the same ones [ORIGINS] names). */
    fun appliesTo(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return listOf("youtube.com", "youtube-nocookie.com").any { h == it || h.endsWith(".$it") }
    }

    // test: tools/yttest.py serves a page that runs this script against a stand-in player.
    val JS = """
(() => {
  if (window.__ytq) return;
  Object.defineProperty(window, '__ytq', { value: true });

  // 1. The player is told what to play, ads included: take the ads out of that answer.
  const AD_KEYS = ['adPlacements', 'playerAds', 'adSlots'];
  const clean = (data) => {
    if (!data || typeof data !== 'object') return data;
    if (Array.isArray(data)) {
      // The "get_watch" answer is a short list with the player response in one item.
      if (data.length <= 8) for (const part of data) if (part && part.playerResponse) clean(part.playerResponse);
      return data;
    }
    for (const key of AD_KEYS) if (key in data) delete data[key];
    const messages = data.auxiliaryUi && data.auxiliaryUi.messageRenderers;
    if (messages && messages.enforcementMessageViewModel) delete messages.enforcementMessageViewModel;
    if (data.playerResponse) clean(data.playerResponse);
    return data;
  };
  const safe = (data) => { try { return clean(data); } catch (e) { return data; } };
  // Proxies of the built-in functions still read as native code to the page.
  JSON.parse = new Proxy(JSON.parse, {
    apply: (target, self, args) => safe(Reflect.apply(target, self, args))
  });
  if (window.Response) {
    Response.prototype.json = new Proxy(Response.prototype.json, {
      apply: (target, self, args) => Reflect.apply(target, self, args).then(safe)
    });
  }
  // The first video's answer is written into the page itself.
  let first;
  try {
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      configurable: true,
      enumerable: true,
      get: () => first,
      set: (value) => { first = safe(value); }
    });
  } catch (e) {}

  // 2. An ad that starts anyway is jumped to its end, silenced meanwhile.
  let silenced = null;
  const skipAd = () => {
    // YouTube's own pop-ups over the player ("Watch in the YouTube app", "Ad blockers are not allowed").
    const close = document.querySelector('yt-enforcement-message-view-model .enforcementMessageViewModelDismissButton :is(button, a, [role="button"])');
    if (close) close.click();
    const player = document.querySelector('.html5-video-player.ad-showing');
    if (!player) {
      if (silenced) { silenced.muted = false; silenced = null; }
      return;
    }
    const video = player.querySelector('video');
    if (video) {
      if (!video.muted) { video.muted = true; silenced = video; }
      if (isFinite(video.duration) && video.duration > 0 && video.currentTime < video.duration - 0.1) {
        video.currentTime = video.duration;
      }
    }
    const button = player.querySelector(
      '.ytp-skip-ad-button, .ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-ad-skip-button-slot button'
    );
    if (button) button.click();
  };
  let queued = false;
  const check = () => { queued = false; skipAd(); };
  new MutationObserver(() => {
    if (!queued) { queued = true; requestAnimationFrame(check); }
  }).observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
  // Frames stop while the page is in the background; this keeps looking.
  setInterval(skipAd, 500);

  // 3. Ad cards in the feed and around the player.
  const css = document.createElement('style');
  css.textContent = [
    'ad-slot-renderer', 'ytm-promoted-sparkles-web-renderer', 'ytm-promoted-sparkles-text-search-renderer',
    'ytm-promoted-video-renderer', 'ytm-companion-ad-renderer', 'ytm-companion-slot',
    'ytm-in-feed-ad-layout-renderer', 'ytm-rich-item-renderer:has(ad-slot-renderer)',
    'ytm-item-section-renderer:has(> lazy-list > ad-slot-renderer:only-child)',
    'ytd-ad-slot-renderer', 'ytd-in-feed-ad-layout-renderer', 'ytd-promoted-sparkles-web-renderer',
    'ytd-display-ad-renderer', 'ytd-action-companion-ad-renderer', 'ytd-banner-promo-renderer',
    'ytd-rich-item-renderer:has(ytd-ad-slot-renderer)', '#player-ads', '#masthead-ad',
    '.ytp-ad-overlay-container', '.ytp-ad-image-overlay',
    '#dialog-container:has(yt-enforcement-message-view-model)'
  ].join(',') + '{display:none!important}';
  const addCss = () => (document.head || document.documentElement).appendChild(css);
  if (document.documentElement) addCss(); else document.addEventListener('DOMContentLoaded', addCss);
})();
""".trimIndent()
}
