import http.server, sys, urllib.parse

PAGE = '''<!doctype html><html><head><meta charset="utf-8"><title>Ad test</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>body{font:15px sans-serif;margin:12px} li{margin:4px 0} button,a{display:block;margin:10px 0;font-size:16px;padding:10px}</style>
</head><body>
<h3>Ad test</h3>
<ins class="adsbygoogle" style="display:block" data-ad-client="ca-pub-0" data-ad-slot="1"></ins>
<div id="gpt-slot"></div>
<ul id="out"></ul>
<button id="pop">POPUP-OTHER-SITE</button>
<button id="popad">POPUP-AD-HOST</button>
<a id="blank" target="_blank" href="https://example.org/">LINK-NEW-TAB</a>
<a id="adlink" href="https://ad.doubleclick.net/ddm/clk/1">LINK-AD-CLICK</a>
<a id="same" href="/page2">LINK-SAME-SITE</a>
<a id="other" href="https://example.org/">LINK-OTHER-SITE</a>
<a id="poster" href="/poster"><img alt="POSTER" width="160" height="90" style="background:#c33"
  src="data:image/gif;base64,R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw=="></a>
<a id="bounce" href="/out?bounce=1">LINK-BOUNCE</a>
<button id="jsgo">BUTTON-GO-OTHER-SITE</button>
<a id="goad" target="_blank" href="/go?id=7"><img alt="AD-GO-PICTURE" width="160" height="90" style="background:#36c"
  src="data:image/gif;base64,R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw=="></a>
<a id="gopic" href="/go?url=https%3A%2F%2Fexample.org%2F%3Fnamed-picture"><img alt="PIC-GO-NAMED" width="160" height="90" style="background:#3a6"
  src="data:image/gif;base64,R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw=="></a>
<a id="gotext" href="/go?id=8">LINK-GO-SAME-TAB</a>
<a id="gonamed" href="/go?url=https%3A%2F%2Fexample.org%2F%3Fnamed">LINK-GO-NAMED</a>
<script>
var out = document.getElementById('out');
function say(k, v) { var li = document.createElement('li'); li.textContent = k + '=' + v; out.appendChild(li); }
function script(name, src, after) {
  var s = document.createElement('script'); s.src = src;
  s.onload = function () { say(name, 'LOADED' + (after ? ':' + after() : '')); };
  s.onerror = function () { say(name, 'FAILED'); };
  document.head.appendChild(s);
}
// 1. the scripts sites test for
(window.adsbygoogle = window.adsbygoogle || []).push({});
script('adsense', 'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js', function () {
  var ins = document.querySelector('ins.adsbygoogle');
  return 'loaded-' + !!(window.adsbygoogle && window.adsbygoogle.loaded) + ',status-' + ins.getAttribute('data-adsbygoogle-status') + ',inner-' + ins.innerHTML.length;
});
window.googletag = window.googletag || { cmd: [] };
googletag.cmd.push(function () {
  var slot = googletag.defineSlot('/1/test', [300, 250], 'gpt-slot').addService(googletag.pubads());
  googletag.pubads().addEventListener('slotRenderEnded', function (e) { say('gpt-render', 'isEmpty-' + e.isEmpty); });
  googletag.pubads().enableSingleRequest(); googletag.enableServices(); googletag.display('gpt-slot');
});
script('gpt', 'https://securepubads.g.doubleclick.net/tag/js/gpt.js', function () { return 'apiReady-' + !!googletag.apiReady; });
script('taboola', 'https://cdn.taboola.com/libtrc/test/loader.js');
script('adstatus', 'https://static.doubleclick.net/instream/ad_status.js', function () { return 'google_ad_status-' + window.google_ad_status; });
// 2. bait element (FuckAdBlock style)
var bait = document.createElement('div');
bait.className = 'pub_300x250 pub_300x250m pub_728x90 text-ad textAd text_ad text_ads text-ads text-ad-links adsbox ad-banner';
bait.style.cssText = 'width:1px;height:1px;position:absolute;left:-10000px;top:-1000px;';
document.body.appendChild(bait);
setTimeout(function () {
  var hidden = bait.offsetParent === null || bait.offsetHeight === 0 || bait.clientHeight === 0 || getComputedStyle(bait).display === 'none';
  say('bait', hidden ? 'HIDDEN' : 'UNTOUCHED');
}, 300);
// 3. fetches
fetch('https://cdn.taboola.com/x.js', { mode: 'no-cors' }).then(function () { say('fetch-nocors', 'OK'); }, function () { say('fetch-nocors', 'FAILED'); });
fetch('https://ib.adnxs.com/ut/v3', { credentials: 'include' }).then(function (r) { say('fetch-cors', 'OK-' + r.status); }, function () { say('fetch-cors', 'FAILED'); });
var img = new Image(); img.onload = function () { say('pixel', 'LOADED-' + img.width + 'x' + img.height); }; img.onerror = function () { say('pixel', 'FAILED'); };
img.src = 'https://ad.doubleclick.net/pixel.gif';
say('injected', Object.keys(window).filter(function (k) { return /anydrop|adblock/i.test(k); }).join(',') || 'none');
// 6. a link-out page (?bounce=1) that sends you on by itself, like a social site's.
if (/bounce=1/.test(location.search)) setTimeout(function () { location.replace('https://example.org/?bounced'); }, 300);
say('loads', sessionStorage.n = (+sessionStorage.n || 0) + 1);
// 5. pop-under (?popunder=1): the page opens itself in a new tab and sends this one to the ad.
if (/popunder=1/.test(location.search)) {
  var fired = false;
  document.addEventListener('click', function () {
    if (fired) return;
    fired = true;
    var w = window.open(location.href, '_blank');
    say('popunder', w ? 'WINDOW' : 'NULL');
    // A moment later, so the new tab is open first.
    var go = function () { location.href = 'https://example.com/?popunder-ad'; };
    if (/sync=1/.test(location.search)) go(); else setTimeout(go, 400);
  }, true);
}
// 8. an ad script that sends the page away on any touch (?touchad=start: as the finger lands,
// ?touchad=end: as it lifts, scrolls included). The page must stay; a tapped link still opens.
var touchad = /touchad=(start|end)/.exec(location.search);
if (touchad) document.addEventListener('touch' + touchad[1], function () {
  location.href = 'https://example.com/?touch-ad';
}, true);
document.getElementById('jsgo').onclick = function () { location.href = 'https://example.org/?js'; };
// 4. windows
document.getElementById('pop').onclick = function () { var w = window.open('https://example.org/?popup'); say('open-other', w ? 'WINDOW' : 'NULL'); };
document.getElementById('popad').onclick = function () { var w = window.open('https://www.popads.net/'); say('open-ad', w ? 'WINDOW' : 'NULL'); };
</script><div style="height:1500px"></div></body></html>'''.encode()


class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        # 7. the site's own ad links: /go?id=… forwards to another site without naming it,
        # /go?url=… names where it goes. In a new window (the ad picture) the first is refused;
        # followed in place both must load, like a download that forwards to its file server.
        if self.path.startswith('/go?'):
            named = urllib.parse.parse_qs(self.path[4:]).get('url')
            self.send_response(302)
            self.send_header('Location', named[0] if named else 'https://example.com/?hidden-ad')
            self.end_headers()
            return
        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', str(len(PAGE)))
        self.end_headers()
        self.wfile.write(PAGE)

    def log_message(self, *a):
        pass


http.server.ThreadingHTTPServer(('127.0.0.1', int(sys.argv[1])), H).serve_forever()
